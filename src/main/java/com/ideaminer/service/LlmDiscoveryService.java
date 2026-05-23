package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LlmDiscoveryService {
    private static final int SOURCE_CONTEXT_PREVIEW_CHARS = 1200;
    private static final int MAX_FULL_SOURCE_CONTEXT_CHARS = 30000;
    private static final int MAX_PARTIAL_SOURCE_CONTEXT_CHARS = 18000;

    private final RepositoryRegistryService repositoryRegistryService;
    private final JdbcTemplate jdbcTemplate;
    private final WorkspaceService workspaceService;
    private final ClassCapabilityLlmClient classCapabilityLlmClient;
    private final SecretRedactionService secretRedactionService;

    public LlmDiscoveryService(RepositoryRegistryService repositoryRegistryService,
                               JdbcTemplate jdbcTemplate,
                               WorkspaceService workspaceService,
                               ClassCapabilityLlmClient classCapabilityLlmClient) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.jdbcTemplate = jdbcTemplate;
        this.workspaceService = workspaceService;
        this.classCapabilityLlmClient = classCapabilityLlmClient;
        this.secretRedactionService = new SecretRedactionService(jdbcTemplate);
    }

    public String startRepositoryRun(String repositoryIdentifier, String promptVersion, String provider, String model) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        String runId = StableId.of("llm_discovery_run_", "repository:" + repository.id() + ":" + System.currentTimeMillis());
        jdbcTemplate.update("""
                INSERT INTO llm_discovery_runs (id, scope_type, repository_id, workspace_id, status, prompt_version, provider, model)
                VALUES (?, 'repository', ?, null, 'running', ?, ?, ?)
                """, runId, repository.id(), promptVersion, provider, model);
        executeRunAsync(runId);
        return runId;
    }

    public String startWorkspaceRun(String workspaceName, String promptVersion, String provider, String model) {
        String workspaceId = workspaceId(workspaceName);
        String runId = StableId.of("llm_discovery_run_", "workspace:" + workspaceId + ":" + System.currentTimeMillis());
        jdbcTemplate.update("""
                INSERT INTO llm_discovery_runs (id, scope_type, repository_id, workspace_id, status, prompt_version, provider, model)
                VALUES (?, 'workspace', null, ?, 'running', ?, ?, ?)
                """, runId, workspaceId, promptVersion, provider, model);
        executeRunAsync(runId);
        return runId;
    }

    public String retryFailedRun(String runId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT scope_type, repository_id, workspace_id, prompt_version, provider, model, status
                FROM llm_discovery_runs
                WHERE id = ?
                """, runId);
        if (!"failed".equals(String.valueOf(row.get("status")))) {
            throw new IllegalArgumentException("Run is not failed: " + runId);
        }
        String scopeType = String.valueOf(row.get("scope_type"));
        String promptVersion = String.valueOf(row.get("prompt_version"));
        String provider = String.valueOf(row.get("provider"));
        String model = String.valueOf(row.get("model"));
        if ("workspace".equals(scopeType)) {
            String workspaceId = String.valueOf(row.get("workspace_id"));
            String workspaceName = jdbcTemplate.queryForObject("SELECT name FROM workspaces WHERE id = ?", String.class, workspaceId);
            return startWorkspaceRun(workspaceName, promptVersion, provider, model);
        }
        String repositoryId = String.valueOf(row.get("repository_id"));
        return startRepositoryRun(repositoryId, promptVersion, provider, model);
    }

    @Async("onboardingExecutor")
    public void executeRunAsync(String runId) {
        try {
            Map<String, Object> run = jdbcTemplate.queryForMap("""
                    SELECT scope_type, repository_id, workspace_id, prompt_version, provider, model
                    FROM llm_discovery_runs
                    WHERE id = ?
                    """, runId);
            String scopeType = String.valueOf(run.get("scope_type"));
            String promptVersion = String.valueOf(run.get("prompt_version"));
            String provider = String.valueOf(run.get("provider"));
            String model = String.valueOf(run.get("model"));

            stageStart(runId, "collect-context");
            String details = buildContextDetails(scopeType, run.get("repository_id"), run.get("workspace_id"));
            stageDone(runId, "collect-context", details);

            stageStart(runId, "initialize-artifacts");
            stageDone(runId, "initialize-artifacts", "Schema ready for class summaries, workflow maps, opportunities, and reviews.");

            stageStart(runId, "summarize-classes");
            int summaries = summarizeClasses(runId, scopeType, run.get("repository_id"), run.get("workspace_id"), promptVersion, provider, model);
            stageDone(runId, "summarize-classes", "class summaries=" + summaries);

            stageStart(runId, "method-deep-dive");
            int methodDeepDives = deepDiveMethods(runId, scopeType, run.get("repository_id"), run.get("workspace_id"), promptVersion, provider, model);
            stageDone(runId, "method-deep-dive", "method deep dives=" + methodDeepDives);

            stageStart(runId, "group-workflows");
            int workflowMaps = groupWorkflows(runId, scopeType, run.get("repository_id"), run.get("workspace_id"), promptVersion, provider, model);
            stageDone(runId, "group-workflows", "workflow maps=" + workflowMaps);

            stageStart(runId, "ideate-opportunities");
            int opportunities = ideateOpportunities(runId, scopeType, run.get("repository_id"), run.get("workspace_id"), promptVersion, provider, model);
            stageDone(runId, "ideate-opportunities", "opportunities=" + opportunities);

            stageStart(runId, "validate-opportunities");
            int validations = validateOpportunities(runId, promptVersion, provider, model);
            stageDone(runId, "validate-opportunities", "validations=" + validations);

            stageStart(runId, "skeptic-review");
            int skepticReviews = skepticReview(runId, promptVersion, provider, model);
            stageDone(runId, "skeptic-review", "skeptic reviews=" + skepticReviews);

            jdbcTemplate.update("""
                    UPDATE llm_discovery_runs
                    SET status = 'completed', response_json = ?::jsonb, ended_at = now(), error_details = null
                    WHERE id = ?
                    """, "{"
                    + "\"message\":\"LLM discovery slices L2-L5 completed\","
                    + "\"classSummaries\":" + summaries + ","
                    + "\"methodDeepDives\":" + methodDeepDives + ","
                    + "\"workflowMaps\":" + workflowMaps + ","
                    + "\"opportunities\":" + opportunities + ","
                    + "\"validations\":" + validations + ","
                    + "\"skepticReviews\":" + skepticReviews
                    + "}", runId);
        } catch (Exception exception) {
            failRunningStage(runId, exception.getMessage());
            jdbcTemplate.update("""
                    UPDATE llm_discovery_runs
                    SET status = 'failed', error_details = ?, ended_at = now()
                    WHERE id = ?
                    """, exception.getMessage(), runId);
        }
    }

    public Map<String, Object> status(String runId) {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList("""
                SELECT id, scope_type, repository_id, workspace_id, status, prompt_version, provider, model, response_json, error_details, started_at, ended_at
                FROM llm_discovery_runs
                WHERE id = ?
                """, runId);
        if (runs.isEmpty()) {
            return Map.of("status", "not-found", "runId", runId);
        }
        Map<String, Object> run = new LinkedHashMap<>(runs.get(0));
        List<Map<String, Object>> stages = jdbcTemplate.queryForList("""
                SELECT stage_name, status, details, started_at, ended_at
                FROM llm_discovery_run_stages
                WHERE run_id = ?
                ORDER BY started_at
                """, runId);
        run.put("stages", stages);
        return run;
    }

    public List<Map<String, Object>> capabilitySummaries(String runId) {
        return jdbcTemplate.queryForList("""
                SELECT s.id, s.repository_id, r.name AS repository_name, s.class_id, c.class_name, c.package_name, c.file_path, s.status, s.summary_json, s.created_at
                FROM llm_capability_summaries s
                JOIN repositories r ON r.id = s.repository_id
                LEFT JOIN classes c ON c.id = s.class_id
                WHERE s.run_id = ?
                ORDER BY repository_name, c.file_path, c.class_name
                """, runId);
    }

    public List<Map<String, Object>> methodDeepDives(String runId) {
        return jdbcTemplate.queryForList("""
                SELECT d.id, d.repository_id, r.name AS repository_name, c.class_name, m.method_name, d.trigger_reason, d.status, d.summary_json, d.created_at
                FROM llm_method_deep_dives d
                JOIN repositories r ON r.id = d.repository_id
                LEFT JOIN classes c ON c.id = d.class_id
                LEFT JOIN methods m ON m.id = d.method_id
                WHERE d.run_id = ?
                ORDER BY repository_name, c.class_name, m.method_name
                """, runId);
    }

    public List<Map<String, Object>> workflowMaps(String runId) {
        return jdbcTemplate.queryForList("""
                SELECT id, scope_type, repository_id, workspace_id, workflow_name, status, workflow_json, created_at
                FROM llm_workflow_maps
                WHERE run_id = ?
                ORDER BY workflow_name
                """, runId);
    }

    public List<Map<String, Object>> opportunityCandidates(String runId) {
        return jdbcTemplate.queryForList("""
                SELECT id, scope_type, repository_id, workspace_id, candidate_type, title, status, ranking_score, summary, candidate_json, created_at
                FROM llm_opportunity_candidates
                WHERE run_id = ?
                ORDER BY ranking_score DESC, candidate_type, title
                """, runId);
    }

    public List<Map<String, Object>> opportunityReviews(String runId) {
        return jdbcTemplate.queryForList("""
                SELECT r.id, r.candidate_id, r.review_type, r.status, r.review_json, r.created_at
                FROM llm_opportunity_reviews r
                JOIN llm_opportunity_candidates c ON c.id = r.candidate_id
                WHERE c.run_id = ?
                ORDER BY r.created_at
                """, runId);
    }

    public Path generateDiscoveryReport(String runId) {
        Map<String, Object> run = jdbcTemplate.queryForMap("""
                SELECT scope_type, repository_id, workspace_id
                FROM llm_discovery_runs
                WHERE id = ?
                """, runId);
        List<Map<String, Object>> opportunities = opportunityCandidates(runId);
        List<Map<String, Object>> reviews = opportunityReviews(runId);
        Path reportsDir = workspaceService.initializeWorkspace().resolve("reports");
        try {
            Files.createDirectories(reportsDir);
            Path reportPath = reportsDir.resolve("llm-discovery-" + runId + ".md");
            StringBuilder markdown = new StringBuilder();
            markdown.append("# LLM Discovery Report\n\n");
            markdown.append("Run: `").append(runId).append("`\n\n");
            markdown.append("Scope: ").append(run.get("scope_type")).append("\n\n");
            markdown.append("## Opportunities\n\n");
            if (opportunities.isEmpty()) {
                markdown.append("No opportunities generated.\n");
            } else {
                for (Map<String, Object> row : opportunities) {
                    markdown.append("### ").append(row.get("title")).append("\n\n");
                    markdown.append("- Type: `").append(row.get("candidate_type")).append("`\n");
                    markdown.append("- Ranking score: `").append(row.get("ranking_score")).append("`\n");
                    markdown.append("- Status: `").append(row.get("status")).append("`\n");
                    markdown.append("- Summary: ").append(row.get("summary")).append("\n\n");
                }
            }
            markdown.append("## Reviews\n\n");
            if (reviews.isEmpty()) {
                markdown.append("No reviews available.\n");
            } else {
                for (Map<String, Object> review : reviews) {
                    markdown.append("- `").append(review.get("review_type")).append("` for `")
                            .append(review.get("candidate_id")).append("`: ")
                            .append(review.get("status")).append("\n");
                }
            }
            Files.writeString(reportPath, markdown.toString());
            jdbcTemplate.update("""
                    INSERT INTO generated_reports (id, repository_id, workspace_id, report_type, file_path)
                    VALUES (?, ?, ?, 'llm-discovery', ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    StableId.of("generated_report_", "llm-discovery:" + runId),
                    run.get("repository_id"),
                    run.get("workspace_id"),
                    reportPath.toString());
            return reportPath;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate discovery report for run " + runId, exception);
        }
    }

    public String recordOpportunityFeedback(String candidateId, String decision, String notes) {
        String id = StableId.of("llm_feedback_", candidateId + ":" + decision + ":" + System.currentTimeMillis());
        jdbcTemplate.update("""
                INSERT INTO llm_opportunity_feedback (id, candidate_id, decision, notes)
                VALUES (?, ?, ?, ?)
                """, id, candidateId, decision, notes);
        return id;
    }

    public int rerank(String runId) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT id, candidate_type, summary
                FROM llm_opportunity_candidates
                WHERE run_id = ?
                """, runId);
        int updates = 0;
        for (Map<String, Object> candidate : candidates) {
            String candidateId = String.valueOf(candidate.get("id"));
            double score = baseScoreFromType(String.valueOf(candidate.get("candidate_type")), String.valueOf(candidate.get("summary")));
            List<String> feedbacks = jdbcTemplate.queryForList("""
                    SELECT decision
                    FROM llm_opportunity_feedback
                    WHERE candidate_id = ?
                    """, String.class, candidateId);
            for (String decision : feedbacks) {
                if ("accepted".equalsIgnoreCase(decision)) score += 1.5;
                if ("rejected".equalsIgnoreCase(decision)) score -= 1.0;
                if ("duplicate".equalsIgnoreCase(decision)) score -= 0.8;
                if ("speculative".equalsIgnoreCase(decision)) score -= 0.4;
            }
            jdbcTemplate.update("UPDATE llm_opportunity_candidates SET ranking_score = ?, updated_at = now() WHERE id = ?", score, candidateId);
            updates++;
        }
        return updates;
    }

    private String buildContextDetails(String scopeType, Object repositoryId, Object workspaceId) {
        if ("workspace".equals(scopeType)) {
            long repositories = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM workspace_repositories WHERE workspace_id = ?", Long.class, workspaceId);
            return "workspace repositories=" + repositories;
        }
        long classes = jdbcTemplate.queryForObject("SELECT count(*) FROM classes WHERE repository_id = ?", Long.class, repositoryId);
        long methods = jdbcTemplate.queryForObject("SELECT count(*) FROM methods WHERE repository_id = ?", Long.class, repositoryId);
        long chunks = jdbcTemplate.queryForObject("SELECT count(*) FROM code_chunks WHERE repository_id = ?", Long.class, repositoryId);
        return "classes=" + classes + ", methods=" + methods + ", chunks=" + chunks;
    }

    private void stageStart(String runId, String stageName) {
        String id = StableId.of("llm_discovery_stage_", runId + ":" + stageName);
        jdbcTemplate.update("""
                INSERT INTO llm_discovery_run_stages (id, run_id, stage_name, status, started_at)
                VALUES (?, ?, ?, 'running', now())
                ON CONFLICT (run_id, stage_name) DO UPDATE SET status = 'running', details = null, started_at = now(), ended_at = null
                """, id, runId, stageName);
    }

    private void stageDone(String runId, String stageName, String details) {
        jdbcTemplate.update("""
                UPDATE llm_discovery_run_stages
                SET status = 'completed', details = ?, ended_at = now()
                WHERE run_id = ? AND stage_name = ?
                """, details, runId, stageName);
    }

    private void stageFailed(String runId, String stageName, String details) {
        jdbcTemplate.update("""
                UPDATE llm_discovery_run_stages
                SET status = 'failed', details = ?, ended_at = now()
                WHERE run_id = ? AND stage_name = ?
                """, details, runId, stageName);
    }

    private void failRunningStage(String runId, String details) {
        List<String> stage = jdbcTemplate.queryForList("""
                SELECT stage_name
                FROM llm_discovery_run_stages
                WHERE run_id = ? AND status = 'running'
                ORDER BY started_at DESC
                LIMIT 1
                """, String.class, runId);
        if (!stage.isEmpty()) {
            stageFailed(runId, stage.get(0), details);
        }
    }

    private int summarizeClasses(String runId, String scopeType, Object repositoryId, Object workspaceId, String promptVersion, String provider, String model) {
        List<String> repositoryIds;
        if ("workspace".equals(scopeType)) {
            repositoryIds = jdbcTemplate.queryForList(
                    "SELECT repository_id FROM workspace_repositories WHERE workspace_id = ? ORDER BY repository_id",
                    String.class, workspaceId);
        } else {
            repositoryIds = List.of(String.valueOf(repositoryId));
        }

        int count = 0;
        for (String repoId : repositoryIds) {
            List<Map<String, Object>> classes = jdbcTemplate.queryForList("""
                    SELECT id, class_name, package_name, file_path, class_type,
                           COALESCE(cyclomatic_complexity, 0) AS cyclomatic_complexity,
                           COALESCE((source_span->>'beginLine')::int, 0) AS begin_line,
                           COALESCE((source_span->>'endLine')::int, 0) AS end_line
                    FROM classes
                    WHERE repository_id = ?
                    ORDER BY file_path, class_name
                    """, repoId);
            for (Map<String, Object> cls : classes) {
                String classId = String.valueOf(cls.get("id"));
                long methodCount = jdbcTemplate.queryForObject("SELECT count(*) FROM methods WHERE class_id = ?", Long.class, classId);
                long endpoints = jdbcTemplate.queryForObject("SELECT count(*) FROM endpoints WHERE class_id = ?", Long.class, classId);
                long jobs = jdbcTemplate.queryForObject("SELECT count(*) FROM scheduled_jobs WHERE class_id = ?", Long.class, classId);
                long dbAccess = jdbcTemplate.queryForObject("SELECT count(*) FROM database_access WHERE class_id = ?", Long.class, classId);
                List<String> roles = jdbcTemplate.queryForList(
                        "SELECT role FROM role_inference WHERE class_id = ? ORDER BY confidence DESC", String.class, classId);
                List<String> terms = jdbcTemplate.queryForList(
                        "SELECT term FROM domain_terms WHERE source_type = 'class' AND source_id = ? ORDER BY weight DESC, term LIMIT 12",
                        String.class, classId);
                List<String> methodSignatures = jdbcTemplate.queryForList("""
                        SELECT method_name || '(' || COALESCE(signature, '') || ') -> ' || COALESCE(return_type, '')
                        FROM methods
                        WHERE class_id = ?
                        ORDER BY method_name, signature
                        LIMIT 80
                        """, String.class, classId);
                int complexity = ((Number) cls.get("cyclomatic_complexity")).intValue();
                String filePath = String.valueOf(cls.get("file_path"));
                SourceFileContext sourceContext = sourceFileContext(repoId, classId, filePath);
                List<String> sourceSignals = sourceSignals(sourceContext.source());
                String purpose = inferCodeAwarePurpose(
                        String.valueOf(cls.get("class_name")),
                        String.valueOf(cls.get("class_type")),
                        endpoints,
                        jobs,
                        dbAccess,
                        sourceSignals);
                String fallbackSummaryJson = "{"
                        + "\"classPurpose\":" + JsonSupport.quote(purpose) + ","
                        + "\"businessCapability\":" + JsonSupport.quote(inferBusinessCapability(terms, roles, sourceSignals)) + ","
                        + "\"domainConcepts\":" + JsonSupport.array(domainConcepts(terms, sourceSignals)) + ","
                        + "\"businessRules\":" + JsonSupport.array(businessRules(complexity, sourceSignals)) + ","
                        + "\"decisionsMade\":" + JsonSupport.array(decisionsMade(complexity, sourceSignals)) + ","
                        + "\"workflowsTouched\":" + JsonSupport.array(List.of(workflowHints(endpoints, jobs, dbAccess))) + ","
                        + "\"dataTouched\":" + JsonSupport.array(dataTouched(dbAccess, sourceSignals)) + ","
                        + "\"externalSystemsTouched\":" + JsonSupport.array(externalSystemsTouched(sourceSignals)) + ","
                        + "\"sideEffects\":" + JsonSupport.array(sideEffects(endpoints, jobs, dbAccess, sourceSignals)) + ","
                        + "\"opportunityHints\":" + JsonSupport.array(List.of(opportunityHints(endpoints, jobs, dbAccess, complexity))) + ","
                        + "\"confidence\":" + confidenceFor(roles, complexity, sourceContext, sourceSignals) + ","
                        + "\"evidence\":{"
                        + "\"classId\":" + JsonSupport.quote(classId) + ","
                        + "\"className\":" + JsonSupport.quote(String.valueOf(cls.get("class_name"))) + ","
                        + "\"packageName\":" + JsonSupport.quote(String.valueOf(cls.get("package_name"))) + ","
                        + "\"filePath\":" + JsonSupport.quote(filePath) + ","
                        + "\"classType\":" + JsonSupport.quote(String.valueOf(cls.get("class_type"))) + ","
                        + "\"methodCount\":" + methodCount + ","
                        + "\"endpointCount\":" + endpoints + ","
                        + "\"jobCount\":" + jobs + ","
                        + "\"databaseAccessCount\":" + dbAccess + ","
                        + "\"roles\":" + JsonSupport.array(roles) + ","
                        + "\"sourceContextMode\":" + JsonSupport.quote(sourceContext.mode()) + ","
                        + "\"sourceContextChars\":" + sourceContext.source().length() + ","
                        + "\"sourceContextTruncated\":" + sourceContext.truncated() + ","
                        + "\"sourceOriginalChars\":" + sourceContext.originalChars() + ","
                        + "\"sourceContextHash\":" + JsonSupport.quote(sourceContext.hash()) + ","
                        + "\"sourceSignals\":" + JsonSupport.array(sourceSignals) + ","
                        + "\"sourceContextPreview\":" + JsonSupport.quote(preview(sourceContext.source())) + ","
                        + "\"sourceSpan\":" + JsonSupport.sourceSpan(
                                ((Number) cls.get("begin_line")).intValue(),
                                ((Number) cls.get("end_line")).intValue())
                        + "}"
                        + "}";
                String metadata = classSummaryMetadata(cls, classId, methodCount, endpoints, jobs, dbAccess, roles, terms, methodSignatures, complexity, sourceContext);
                ClassCapabilityLlmClient.ClassCapabilitySummaryResult result = classCapabilityLlmClient.summarize(
                        new ClassCapabilityLlmClient.ClassCapabilityPromptInput(metadata, sourceContext.source(), sourceContext.mode()),
                        fallbackSummaryJson);
                String id = StableId.of("llm_capability_", runId + ":" + classId);
                jdbcTemplate.update("""
                        INSERT INTO llm_capability_summaries
                        (id, run_id, repository_id, class_id, summary_json, status, prompt_version, provider, model, error_details, updated_at)
                        VALUES (?, ?, ?, ?, ?::jsonb, 'completed', ?, ?, ?, ?, now())
                        ON CONFLICT (run_id, class_id) DO UPDATE SET
                            summary_json = EXCLUDED.summary_json,
                            status = EXCLUDED.status,
                            prompt_version = EXCLUDED.prompt_version,
                            provider = EXCLUDED.provider,
                            model = EXCLUDED.model,
                            error_details = EXCLUDED.error_details,
                            updated_at = now()
                        """, id, runId, repoId, classId, result.summaryJson(), promptVersion, provider, model, result.error());
                count++;
            }
        }
        return count;
    }

    private int deepDiveMethods(String runId, String scopeType, Object repositoryId, Object workspaceId, String promptVersion, String provider, String model) {
        List<String> repositoryIds = scopedRepositoryIds(scopeType, repositoryId, workspaceId);
        int count = 0;
        for (String repoId : repositoryIds) {
            List<Map<String, Object>> methods = jdbcTemplate.queryForList("""
                    SELECT m.id AS method_id, m.class_id, m.method_name, m.signature, m.return_type, m.cyclomatic_complexity,
                           c.class_name, c.package_name, c.file_path
                    FROM methods m
                    JOIN classes c ON c.id = m.class_id
                    WHERE m.repository_id = ?
                    ORDER BY c.file_path, m.method_name
                    """, repoId);
            for (Map<String, Object> method : methods) {
                String trigger = triggerReasonForMethod(method, runId);
                if (trigger == null) {
                    continue;
                }
                String methodId = String.valueOf(method.get("method_id"));
                int complexity = ((Number) method.get("cyclomatic_complexity")).intValue();
                String summaryJson = "{"
                        + "\"methodId\":" + JsonSupport.quote(methodId) + ","
                        + "\"classId\":" + JsonSupport.quote(String.valueOf(method.get("class_id"))) + ","
                        + "\"className\":" + JsonSupport.quote(String.valueOf(method.get("class_name"))) + ","
                        + "\"packageName\":" + JsonSupport.quote(String.valueOf(method.get("package_name"))) + ","
                        + "\"filePath\":" + JsonSupport.quote(String.valueOf(method.get("file_path"))) + ","
                        + "\"methodName\":" + JsonSupport.quote(String.valueOf(method.get("method_name"))) + ","
                        + "\"signature\":" + JsonSupport.quote(String.valueOf(method.get("signature"))) + ","
                        + "\"returnType\":" + JsonSupport.quote(String.valueOf(method.get("return_type"))) + ","
                        + "\"triggerReason\":" + JsonSupport.quote(trigger) + ","
                        + "\"analysis\":\"Method selected for focused deep dive due to " + JsonSupport.quote(trigger).replace("\"", "") + ".\","
                        + "\"complexity\":" + complexity
                        + "}";
                String id = StableId.of("llm_method_deep_dive_", runId + ":" + methodId + ":" + trigger);
                jdbcTemplate.update("""
                        INSERT INTO llm_method_deep_dives
                        (id, run_id, repository_id, class_id, method_id, trigger_reason, summary_json, status, prompt_version, provider, model, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'completed', ?, ?, ?, now())
                        ON CONFLICT (run_id, method_id, trigger_reason) DO UPDATE SET
                            summary_json = EXCLUDED.summary_json,
                            status = EXCLUDED.status,
                            prompt_version = EXCLUDED.prompt_version,
                            provider = EXCLUDED.provider,
                            model = EXCLUDED.model,
                            updated_at = now()
                        """, id, runId, repoId, String.valueOf(method.get("class_id")), methodId, trigger, summaryJson, promptVersion, provider, model);
                count++;
            }
        }
        return count;
    }

    private int groupWorkflows(String runId, String scopeType, Object repositoryId, Object workspaceId, String promptVersion, String provider, String model) {
        List<String> repositoryIds = scopedRepositoryIds(scopeType, repositoryId, workspaceId);
        int count = 0;
        for (String repoId : repositoryIds) {
            List<Map<String, Object>> summaries = jdbcTemplate.queryForList("""
                    SELECT class_id, summary_json
                    FROM llm_capability_summaries
                    WHERE run_id = ? AND repository_id = ?
                    """, runId, repoId);
            Map<String, List<String>> workflowToClasses = new LinkedHashMap<>();
            for (Map<String, Object> row : summaries) {
                String classId = String.valueOf(row.get("class_id"));
                String json = String.valueOf(row.get("summary_json"));
                String workflow = workflowKeyFromSummary(json);
                workflowToClasses.computeIfAbsent(workflow, key -> new ArrayList<>()).add(classId);
            }

            for (Map.Entry<String, List<String>> entry : workflowToClasses.entrySet()) {
                String workflowName = entry.getKey();
                List<String> classIds = entry.getValue();
                String workflowJson = "{"
                        + "\"workflowName\":" + JsonSupport.quote(workflowName) + ","
                        + "\"classIds\":" + JsonSupport.array(classIds) + ","
                        + "\"classCount\":" + classIds.size() + ","
                        + "\"confidence\":" + (classIds.size() >= 3 ? "0.82" : "0.72")
                        + "}";
                String id = StableId.of("llm_workflow_map_", runId + ":" + repoId + ":" + workflowName);
                jdbcTemplate.update("""
                        INSERT INTO llm_workflow_maps
                        (id, run_id, scope_type, repository_id, workspace_id, workflow_name, workflow_json, status, prompt_version, provider, model, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'completed', ?, ?, ?, now())
                        ON CONFLICT (id) DO UPDATE SET
                            workflow_json = EXCLUDED.workflow_json,
                            status = EXCLUDED.status,
                            prompt_version = EXCLUDED.prompt_version,
                            provider = EXCLUDED.provider,
                            model = EXCLUDED.model,
                            updated_at = now()
                        """, id, runId, "repository", repoId, null, workflowName, workflowJson, promptVersion, provider, model);
                count++;
            }
        }
        if ("workspace".equals(scopeType)) {
            List<String> classIds = jdbcTemplate.queryForList("""
                    SELECT class_id
                    FROM llm_capability_summaries
                    WHERE run_id = ?
                    ORDER BY class_id
                    """, String.class, runId);
            String workflowJson = "{"
                    + "\"workflowName\":\"workspace_cross_repo\","
                    + "\"classIds\":" + JsonSupport.array(classIds) + ","
                    + "\"classCount\":" + classIds.size() + ","
                    + "\"confidence\":0.74"
                    + "}";
            String id = StableId.of("llm_workflow_map_", runId + ":workspace_cross_repo");
            jdbcTemplate.update("""
                    INSERT INTO llm_workflow_maps
                    (id, run_id, scope_type, repository_id, workspace_id, workflow_name, workflow_json, status, prompt_version, provider, model, updated_at)
                    VALUES (?, ?, 'workspace', null, ?, 'workspace_cross_repo', ?::jsonb, 'completed', ?, ?, ?, now())
                    ON CONFLICT (id) DO UPDATE SET workflow_json = EXCLUDED.workflow_json, updated_at = now()
                    """, id, runId, workspaceId, workflowJson, promptVersion, provider, model);
            count++;
        }
        return count;
    }

    private int ideateOpportunities(String runId, String scopeType, Object repositoryId, Object workspaceId, String promptVersion, String provider, String model) {
        List<Map<String, Object>> workflows = jdbcTemplate.queryForList("""
                SELECT id, workflow_name, workflow_json, repository_id, workspace_id
                FROM llm_workflow_maps
                WHERE run_id = ?
                ORDER BY workflow_name
                """, runId);
        int count = 0;
        for (Map<String, Object> workflow : workflows) {
            String workflowName = String.valueOf(workflow.get("workflow_name"));
            List<String> types = candidateTypesForWorkflow(workflowName, String.valueOf(workflow.get("workflow_json")));
            for (String type : types) {
                String title = titleFor(type, workflowName);
                String summary = "Opportunity derived from workflow " + workflowName + " using class-level capability summaries.";
                String candidateJson = "{"
                        + "\"workflowName\":" + JsonSupport.quote(workflowName) + ","
                        + "\"candidateType\":" + JsonSupport.quote(type) + ","
                        + "\"benefit\":" + JsonSupport.quote(benefitFor(type)) + ","
                        + "\"implementationNotes\":" + JsonSupport.quote("Start with pilot in one workflow path before broad rollout.") + ","
                        + "\"risks\":" + JsonSupport.array(List.of("evidence_gap_possible", "requires_domain_validation")) + ","
                        + "\"requiredData\":" + JsonSupport.array(List.of("workflow_metrics", "domain_outcome_signals")) + ","
                        + "\"evidenceRefs\":" + JsonSupport.array(List.of(String.valueOf(workflow.get("id")))) + ","
                        + "\"contributingRepositories\":" + contributingRepositoriesJson(scopeType, workflow.get("repository_id"), workflow.get("workspace_id"))
                        + "}";
                String id = StableId.of("llm_opp_candidate_", runId + ":" + workflowName + ":" + type);
                jdbcTemplate.update("""
                        INSERT INTO llm_opportunity_candidates
                        (id, run_id, scope_type, repository_id, workspace_id, candidate_type, title, summary, candidate_json, status, prompt_version, provider, model, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'generated', ?, ?, ?, now())
                        ON CONFLICT (id) DO UPDATE SET
                            summary = EXCLUDED.summary,
                            candidate_json = EXCLUDED.candidate_json,
                            status = EXCLUDED.status,
                            prompt_version = EXCLUDED.prompt_version,
                            provider = EXCLUDED.provider,
                            model = EXCLUDED.model,
                            updated_at = now()
                        """, id, runId, scopeType, workflow.get("repository_id"), workflow.get("workspace_id"),
                        type, title, summary, candidateJson, promptVersion, provider, model);
                count++;
            }
        }
        return count;
    }

    private int validateOpportunities(String runId, String promptVersion, String provider, String model) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT id, summary, candidate_json
                FROM llm_opportunity_candidates
                WHERE run_id = ?
                """, runId);
        int count = 0;
        for (Map<String, Object> candidate : candidates) {
            String candidateId = String.valueOf(candidate.get("id"));
            String summary = String.valueOf(candidate.get("summary"));
            String candidateJson = String.valueOf(candidate.get("candidate_json"));
            boolean supported = summary.length() > 20 && candidateJson.contains("evidenceRefs");
            double confidence = supported ? 0.79 : 0.52;
            String reviewJson = "{"
                    + "\"verdict\":" + JsonSupport.quote(supported ? "supported" : "needs_evidence") + ","
                    + "\"confidence\":" + confidence + ","
                    + "\"missingEvidence\":" + JsonSupport.array(supported ? List.of() : List.of("runtime metrics", "domain KPI linkage")) + ","
                    + "\"recommendedNextStep\":" + JsonSupport.quote(supported ? "pilot implementation design" : "collect additional evidence")
                    + "}";
            upsertReview(candidateId, "validation", reviewJson, "completed", promptVersion, provider, model, null);
            count++;
        }
        return count;
    }

    private int skepticReview(String runId, String promptVersion, String provider, String model) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT c.id, c.summary,
                       COALESCE((SELECT review_json FROM llm_opportunity_reviews r WHERE r.candidate_id = c.id AND r.review_type = 'validation' ORDER BY created_at DESC LIMIT 1), '{}'::jsonb) AS validation_json
                FROM llm_opportunity_candidates c
                WHERE c.run_id = ?
                """, runId);
        int count = 0;
        for (Map<String, Object> candidate : candidates) {
            String candidateId = String.valueOf(candidate.get("id"));
            String summary = String.valueOf(candidate.get("summary")).toLowerCase();
            String validation = String.valueOf(candidate.get("validation_json")).toLowerCase();
            boolean challenged = summary.contains("emerging") || validation.contains("needs_evidence");
            String reviewJson = "{"
                    + "\"skepticVerdict\":" + JsonSupport.quote(challenged ? "challenge" : "pass") + ","
                    + "\"riskFlags\":" + JsonSupport.array(challenged ? List.of("insufficient_outcome_evidence", "adoption_risk") : List.of("standard_delivery_risk")) + ","
                    + "\"notes\":" + JsonSupport.quote(challenged ? "Request stronger KPI and feasibility evidence before escalation." : "Evidence appears sufficient for next phase.")
                    + "}";
            upsertReview(candidateId, "skeptic", reviewJson, "completed", promptVersion, provider, model, null);
            count++;
        }
        return count;
    }

    private void upsertReview(String candidateId, String reviewType, String reviewJson, String status, String promptVersion, String provider, String model, String errorDetails) {
        String id = StableId.of("llm_opp_review_", candidateId + ":" + reviewType);
        jdbcTemplate.update("""
                INSERT INTO llm_opportunity_reviews
                (id, candidate_id, review_type, review_json, status, prompt_version, provider, model, error_details, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                    review_json = EXCLUDED.review_json,
                    status = EXCLUDED.status,
                    prompt_version = EXCLUDED.prompt_version,
                    provider = EXCLUDED.provider,
                    model = EXCLUDED.model,
                    error_details = EXCLUDED.error_details,
                    updated_at = now()
                """, id, candidateId, reviewType, reviewJson, status, promptVersion, provider, model, errorDetails);
    }

    private double baseScoreFromType(String type, String summary) {
        double score = switch (type) {
            case "revenue_growth_opportunity", "new_business_use_case", "data_product_opportunity" -> 7.5;
            case "automation_opportunity", "operational_improvement" -> 7.0;
            case "risk_compliance_improvement", "fraud_risk_detection" -> 7.2;
            default -> 6.4;
        };
        if (summary != null && summary.toLowerCase().contains("cross-repo")) score += 0.8;
        return score;
    }

    private String contributingRepositoriesJson(String scopeType, Object repositoryId, Object workspaceId) {
        if ("workspace".equals(scopeType)) {
            List<String> repos = jdbcTemplate.queryForList("""
                    SELECT r.name
                    FROM workspace_repositories wr
                    JOIN repositories r ON r.id = wr.repository_id
                    WHERE wr.workspace_id = ?
                    ORDER BY r.name
                    """, String.class, workspaceId);
            return JsonSupport.array(repos);
        }
        String repoName = jdbcTemplate.queryForObject("SELECT name FROM repositories WHERE id = ?", String.class, repositoryId);
        return JsonSupport.array(List.of(repoName));
    }

    private String inferPurpose(String className, String classType, long endpoints, long jobs, long dbAccess) {
        String lower = (className + " " + classType).toLowerCase();
        if (lower.contains("controller") || endpoints > 0) return "expose customer or API entry points";
        if (lower.contains("service")) return "orchestrate business logic";
        if (lower.contains("repository") || lower.contains("dao") || dbAccess > 0) return "access and manage persisted data";
        if (lower.contains("job") || jobs > 0) return "run scheduled or batch workflows";
        return "support domain workflows";
    }

    private String inferCodeAwarePurpose(String className, String classType, long endpoints, long jobs, long dbAccess, List<String> sourceSignals) {
        if (sourceSignals.contains("external_http_call") && sourceSignals.contains("persistence_access")) {
            return "coordinate integration-heavy workflows that exchange data with external systems and persisted state";
        }
        if (sourceSignals.contains("business_rule_logic") && sourceSignals.contains("decision_logic")) {
            return "apply business rules and decision logic for a domain workflow";
        }
        if (sourceSignals.contains("request_mapping") && sourceSignals.contains("persistence_access")) {
            return "handle request workflows that read or change persisted business data";
        }
        if (sourceSignals.contains("scheduled_execution")) {
            return "run scheduled or batch processing over business data";
        }
        return inferPurpose(className, classType, endpoints, jobs, dbAccess);
    }

    private String inferBusinessCapability(List<String> terms, List<String> roles, List<String> sourceSignals) {
        if (!terms.isEmpty()) return "domain capability around " + terms.get(0);
        if (sourceSignals.contains("request_mapping")) return "API/request handling capability";
        if (sourceSignals.contains("persistence_access")) return "data management capability";
        if (sourceSignals.contains("external_http_call")) return "external integration capability";
        if (!roles.isEmpty()) return "capability aligned with role " + roles.get(0);
        return "general platform capability";
    }

    private List<String> domainConcepts(List<String> terms, List<String> sourceSignals) {
        List<String> concepts = new ArrayList<>(terms);
        if (sourceSignals.contains("persistence_access") && !concepts.contains("data")) concepts.add("data");
        if (sourceSignals.contains("request_mapping") && !concepts.contains("request")) concepts.add("request");
        if (sourceSignals.contains("scheduled_execution") && !concepts.contains("batch")) concepts.add("batch");
        return concepts;
    }

    private List<String> businessRules(int complexity, List<String> sourceSignals) {
        List<String> rules = new ArrayList<>();
        if (sourceSignals.contains("business_rule_logic")) rules.add("contains explicit rule/validation/business condition terms");
        if (complexity >= 5 || sourceSignals.contains("decision_logic")) rules.add("contains branching logic that may encode domain decisions");
        if (rules.isEmpty()) rules.add("no strong business-rule signal detected from source context");
        return rules;
    }

    private List<String> decisionsMade(int complexity, List<String> sourceSignals) {
        List<String> decisions = new ArrayList<>();
        if (sourceSignals.contains("decision_logic")) decisions.add("branches on conditional or switch logic");
        if (complexity >= 8) decisions.add("high complexity suggests multiple decision paths");
        else if (complexity >= 5) decisions.add("moderate complexity suggests some rule-driven behavior");
        if (decisions.isEmpty()) decisions.add("low-to-moderate decision complexity");
        return decisions;
    }

    private List<String> dataTouched(long dbAccess, List<String> sourceSignals) {
        List<String> data = new ArrayList<>();
        if (dbAccess > 0 || sourceSignals.contains("persistence_access")) data.add("persistent application data");
        if (sourceSignals.contains("query_logic")) data.add("query result data");
        if (sourceSignals.contains("dto_or_model_mapping")) data.add("DTO/model data");
        if (data.isEmpty()) data.add("not detected");
        return data;
    }

    private List<String> externalSystemsTouched(List<String> sourceSignals) {
        List<String> systems = new ArrayList<>();
        if (sourceSignals.contains("external_http_call")) systems.add("external HTTP/API system");
        if (sourceSignals.contains("messaging")) systems.add("messaging/event system");
        if (sourceSignals.contains("file_io")) systems.add("local or shared file system");
        if (systems.isEmpty()) systems.add("not detected");
        return systems;
    }

    private List<String> sideEffects(long endpoints, long jobs, long dbAccess, List<String> sourceSignals) {
        List<String> effects = new ArrayList<>();
        if (endpoints > 0) effects.add("serves API/request responses");
        if (jobs > 0) effects.add("executes scheduled work");
        if (dbAccess > 0 || sourceSignals.contains("persistence_access")) effects.add("reads or writes persisted data");
        if (sourceSignals.contains("external_http_call")) effects.add("calls external systems");
        if (sourceSignals.contains("messaging")) effects.add("publishes or consumes messages");
        if (sourceSignals.contains("file_io")) effects.add("reads or writes files");
        if (effects.isEmpty()) effects.add("not detected");
        return effects;
    }

    private String[] workflowHints(long endpoints, long jobs, long dbAccess) {
        if (endpoints > 0 && dbAccess > 0) return new String[]{"request-processing", "state-change"};
        if (jobs > 0 && dbAccess > 0) return new String[]{"batch-orchestration", "state-update"};
        if (endpoints > 0) return new String[]{"request-processing"};
        if (jobs > 0) return new String[]{"batch-orchestration"};
        return new String[]{"internal-support"};
    }

    private String[] opportunityHints(long endpoints, long jobs, long dbAccess, int complexity) {
        if (jobs > 0) return new String[]{"automation_opportunity", "operational_improvement"};
        if (complexity >= 5) return new String[]{"modernization_opportunity", "decision_intelligence_opportunity"};
        if (endpoints > 0 && dbAccess > 0) return new String[]{"customer_experience_improvement", "process_simplification"};
        return new String[]{"operational_improvement"};
    }

    private double confidenceFor(List<String> roles, int complexity) {
        double base = roles.isEmpty() ? 0.62 : 0.75;
        if (complexity >= 8) return Math.min(0.9, base + 0.08);
        if (complexity >= 5) return Math.min(0.86, base + 0.04);
        return base;
    }

    private double confidenceFor(List<String> roles, int complexity, SourceFileContext sourceContext, List<String> sourceSignals) {
        double base = confidenceFor(roles, complexity);
        if ("full_file_source".equals(sourceContext.mode())) base += 0.08;
        if (!sourceSignals.isEmpty() && !sourceSignals.contains("metadata_only")) base += 0.04;
        return Math.min(0.94, base);
    }

    private SourceFileContext sourceFileContext(String repositoryId, String classId, String filePath) {
        if (filePath == null || filePath.isBlank() || "null".equals(filePath)) {
            return SourceFileContext.empty();
        }
        try {
            List<String> localPaths = jdbcTemplate.queryForList(
                    "SELECT local_path FROM repositories WHERE id = ? AND local_path IS NOT NULL",
                    String.class,
                    repositoryId);
            if (localPaths.isEmpty()) {
                return SourceFileContext.empty();
            }
            Path root = Path.of(localPaths.get(0));
            Path sourcePath = Path.of(filePath);
            Path resolved = sourcePath.isAbsolute() ? sourcePath.normalize() : root.resolve(sourcePath).normalize();
            if (!Files.isRegularFile(resolved)) {
                return SourceFileContext.empty();
            }
            String source = Files.readString(resolved);
            String redacted = secretRedactionService.redact("llm-class-source", classId, source);
            String promptSource = promptSourceContext(redacted);
            String mode = promptSource.length() == redacted.length() ? "full_class_source" : "partial_class_source";
            return new SourceFileContext(promptSource, mode, StableId.of("source_context_", redacted), promptSource.length() != redacted.length(), redacted.length());
        } catch (Exception exception) {
            return SourceFileContext.empty();
        }
    }

    private String promptSourceContext(String source) {
        if (source.length() <= MAX_FULL_SOURCE_CONTEXT_CHARS) {
            return source;
        }

        List<String> selected = new ArrayList<>();
        int selectedChars = 0;
        String[] lines = source.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ")
                    || trimmed.startsWith("import ")
                    || trimmed.startsWith("@")
                    || trimmed.contains(" class ")
                    || trimmed.contains(" interface ")
                    || trimmed.contains(" enum ")
                    || looksLikeField(trimmed)
                    || looksLikeMethodSignature(trimmed)
                    || containsOpportunitySignal(trimmed)) {
                selected.add(line);
                selectedChars += line.length() + 1;
            }
            if (selectedChars >= MAX_PARTIAL_SOURCE_CONTEXT_CHARS) {
                break;
            }
        }

        String reduced = String.join("\n", selected);
        if (reduced.isBlank()) {
            return source.substring(0, Math.min(source.length(), MAX_PARTIAL_SOURCE_CONTEXT_CHARS));
        }
        if (reduced.length() > MAX_PARTIAL_SOURCE_CONTEXT_CHARS) {
            return reduced.substring(0, MAX_PARTIAL_SOURCE_CONTEXT_CHARS);
        }
        return reduced;
    }

    private boolean looksLikeField(String trimmed) {
        return trimmed.endsWith(";")
                && !trimmed.startsWith("import ")
                && !trimmed.contains("(")
                && (trimmed.startsWith("private ") || trimmed.startsWith("protected ") || trimmed.startsWith("public "));
    }

    private boolean looksLikeMethodSignature(String trimmed) {
        return trimmed.contains("(")
                && (trimmed.endsWith("{") || trimmed.endsWith(";"))
                && (trimmed.startsWith("public ")
                || trimmed.startsWith("protected ")
                || trimmed.startsWith("private ")
                || trimmed.startsWith("static ")
                || trimmed.startsWith("final "));
    }

    private boolean containsOpportunitySignal(String trimmed) {
        String lower = trimmed.toLowerCase();
        return lower.contains("if (")
                || lower.contains("if(")
                || lower.contains("switch")
                || lower.contains("validate")
                || lower.contains("eligible")
                || lower.contains("approve")
                || lower.contains("reject")
                || lower.contains("risk")
                || lower.contains("fraud")
                || lower.contains("limit")
                || lower.contains("resttemplate")
                || lower.contains("webclient")
                || lower.contains("jdbctemplate")
                || lower.contains("entitymanager")
                || lower.contains("@query")
                || lower.contains("@scheduled");
    }

    private List<String> sourceSignals(String source) {
        if (source == null || source.isBlank()) {
            return List.of("metadata_only");
        }
        String lower = source.toLowerCase();
        List<String> signals = new ArrayList<>();
        addSignal(signals, lower.contains("@requestmapping") || lower.contains("@getmapping")
                || lower.contains("@postmapping") || lower.contains("@putmapping") || lower.contains("@deletemapping"), "request_mapping");
        addSignal(signals, lower.contains("@scheduled") || lower.contains("cron"), "scheduled_execution");
        addSignal(signals, lower.contains("jdbctemplate") || lower.contains("entitymanager") || lower.contains("crudrepository")
                || lower.contains("jparepository") || lower.contains("@repository"), "persistence_access");
        addSignal(signals, lower.contains("@query") || lower.contains("select ") || lower.contains("insert ")
                || lower.contains("update ") || lower.contains("delete "), "query_logic");
        addSignal(signals, lower.contains("resttemplate") || lower.contains("webclient") || lower.contains("httpclient")
                || lower.contains("openfeign") || lower.contains("@feignclient"), "external_http_call");
        addSignal(signals, lower.contains("kafkatemplate") || lower.contains("@kafkalistener") || lower.contains("jms")
                || lower.contains("rabbittemplate"), "messaging");
        addSignal(signals, lower.contains("files.") || lower.contains("path.of(") || lower.contains("fileinputstream")
                || lower.contains("fileoutputstream"), "file_io");
        addSignal(signals, lower.contains("if (") || lower.contains("if(") || lower.contains("switch (")
                || lower.contains("switch("), "decision_logic");
        addSignal(signals, lower.contains("validate") || lower.contains("rule") || lower.contains("eligible")
                || lower.contains("approve") || lower.contains("reject") || lower.contains("limit"), "business_rule_logic");
        addSignal(signals, lower.contains("mapper") || lower.contains("dto") || lower.contains("model"), "dto_or_model_mapping");
        addSignal(signals, lower.contains("try {") || lower.contains("catch ("), "exception_handling");
        if (signals.isEmpty()) {
            signals.add("general_code_context");
        }
        return signals;
    }

    private void addSignal(List<String> signals, boolean present, String signal) {
        if (present && !signals.contains(signal)) {
            signals.add(signal);
        }
    }

    private String preview(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        if (source.length() <= SOURCE_CONTEXT_PREVIEW_CHARS) {
            return source;
        }
        return source.substring(0, SOURCE_CONTEXT_PREVIEW_CHARS);
    }

    private String classSummaryMetadata(Map<String, Object> cls,
                                        String classId,
                                        long methodCount,
                                        long endpoints,
                                        long jobs,
                                        long dbAccess,
                                        List<String> roles,
                                        List<String> terms,
                                        List<String> methodSignatures,
                                        int complexity,
                                        SourceFileContext sourceContext) {
        return "{"
                + "\"classId\":" + JsonSupport.quote(classId) + ","
                + "\"className\":" + JsonSupport.quote(String.valueOf(cls.get("class_name"))) + ","
                + "\"packageName\":" + JsonSupport.quote(String.valueOf(cls.get("package_name"))) + ","
                + "\"filePath\":" + JsonSupport.quote(String.valueOf(cls.get("file_path"))) + ","
                + "\"classType\":" + JsonSupport.quote(String.valueOf(cls.get("class_type"))) + ","
                + "\"methodCount\":" + methodCount + ","
                + "\"methodSignatures\":" + JsonSupport.array(methodSignatures) + ","
                + "\"endpointCount\":" + endpoints + ","
                + "\"jobCount\":" + jobs + ","
                + "\"databaseAccessCount\":" + dbAccess + ","
                + "\"roles\":" + JsonSupport.array(roles) + ","
                + "\"domainTerms\":" + JsonSupport.array(terms) + ","
                + "\"cyclomaticComplexity\":" + complexity + ","
                + "\"sourceContextMode\":" + JsonSupport.quote(sourceContext.mode()) + ","
                + "\"sourceContextChars\":" + sourceContext.source().length() + ","
                + "\"sourceContextTruncated\":" + sourceContext.truncated()
                + "}";
    }

    private List<String> scopedRepositoryIds(String scopeType, Object repositoryId, Object workspaceId) {
        if ("workspace".equals(scopeType)) {
            return jdbcTemplate.queryForList(
                    "SELECT repository_id FROM workspace_repositories WHERE workspace_id = ? ORDER BY repository_id",
                    String.class, workspaceId);
        }
        return List.of(String.valueOf(repositoryId));
    }

    private String triggerReasonForMethod(Map<String, Object> method, String runId) {
        int complexity = ((Number) method.get("cyclomatic_complexity")).intValue();
        String classId = String.valueOf(method.get("class_id"));
        String className = String.valueOf(method.get("class_name")).toLowerCase();
        String methodName = String.valueOf(method.get("method_name")).toLowerCase();
        if (complexity >= 8) {
            return "high_complexity";
        }
        if (className.contains("controller") || methodName.contains("review") || methodName.contains("approve")) {
            return "candidate_supporting_method";
        }
        Map<String, Object> classSummary = jdbcTemplate.queryForMap("""
                SELECT summary_json
                FROM llm_capability_summaries
                WHERE run_id = ? AND class_id = ?
                """, runId, classId);
        String json = String.valueOf(classSummary.get("summary_json"));
        if (json.contains("\"confidence\":0.62") || json.contains("\"confidence\":0.66")) {
            return "ambiguous_class_summary";
        }
        return null;
    }

    private String workflowKeyFromSummary(String summaryJson) {
        String lower = summaryJson.toLowerCase();
        if (lower.contains("request-processing")) return "customer_request_flow";
        if (lower.contains("batch-orchestration")) return "batch_orchestration_flow";
        if (lower.contains("database access present")) return "data_processing_flow";
        return "internal_support_flow";
    }

    private List<String> candidateTypesForWorkflow(String workflowName, String workflowJson) {
        Set<String> types = new java.util.LinkedHashSet<>();
        String normalized = (workflowName + " " + workflowJson).toLowerCase();
        if (normalized.contains("batch")) {
            types.add("automation_opportunity");
            types.add("operational_improvement");
        }
        if (normalized.contains("request")) {
            types.add("customer_experience_improvement");
            types.add("process_simplification");
        }
        if (normalized.contains("data")) {
            types.add("data_product_opportunity");
            types.add("analytics_reporting_opportunity");
        }
        types.add("modernization_opportunity");
        types.add("new_business_use_case");
        return new ArrayList<>(types);
    }

    private String titleFor(String type, String workflowName) {
        return switch (type) {
            case "automation_opportunity" -> "Automate workflow steps in " + workflowName;
            case "operational_improvement" -> "Operational tuning for " + workflowName;
            case "customer_experience_improvement" -> "Reduce customer friction in " + workflowName;
            case "process_simplification" -> "Simplify process path in " + workflowName;
            case "data_product_opportunity" -> "Data product opportunity from " + workflowName;
            case "analytics_reporting_opportunity" -> "Analytics insight opportunity in " + workflowName;
            case "new_business_use_case" -> "Emerging business use case around " + workflowName;
            default -> "Modernization opportunity in " + workflowName;
        };
    }

    private String benefitFor(String type) {
        return switch (type) {
            case "automation_opportunity" -> "Lower manual effort and faster SLA.";
            case "operational_improvement" -> "More predictable operations and fewer incidents.";
            case "customer_experience_improvement" -> "Faster and clearer customer outcomes.";
            case "process_simplification" -> "Reduced handoffs and simpler execution path.";
            case "data_product_opportunity" -> "New value from existing operational data.";
            case "analytics_reporting_opportunity" -> "Better visibility for business decisions.";
            case "new_business_use_case" -> "Potential new product/service extension.";
            default -> "Incremental reliability and maintainability gains.";
        };
    }

    private String workspaceId(String workspaceName) {
        List<String> ids = jdbcTemplate.queryForList("SELECT id FROM workspaces WHERE name = ?", String.class, workspaceName);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Unknown workspace: " + workspaceName);
        }
        return ids.get(0);
    }

    private record SourceFileContext(String source, String mode, String hash, boolean truncated, int originalChars) {
        private static SourceFileContext empty() {
            return new SourceFileContext("", "metadata_only", "", false, 0);
        }
    }
}
