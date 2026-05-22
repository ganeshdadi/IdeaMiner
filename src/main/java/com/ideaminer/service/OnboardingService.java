package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OnboardingService {
    private static final List<String> STAGES = List.of(
            "register", "scan-files", "index-classes", "index-methods", "infer-roles", "detect-all", "chunks", "embed", "report-no-llm"
    );

    private final RepositoryRegistryService repositoryRegistryService;
    private final SourceFileScanService sourceFileScanService;
    private final ClassIndexService classIndexService;
    private final MethodIndexService methodIndexService;
    private final FeaturePipelineService featurePipelineService;
    private final JdbcTemplate jdbcTemplate;

    public OnboardingService(RepositoryRegistryService repositoryRegistryService,
                             SourceFileScanService sourceFileScanService,
                             ClassIndexService classIndexService,
                             MethodIndexService methodIndexService,
                             FeaturePipelineService featurePipelineService,
                             JdbcTemplate jdbcTemplate) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.sourceFileScanService = sourceFileScanService;
        this.classIndexService = classIndexService;
        this.methodIndexService = methodIndexService;
        this.featurePipelineService = featurePipelineService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> onboard(String repositoryPathOrId, String fromStage, boolean resume) {
        String runId = startOnboarding(repositoryPathOrId, fromStage, resume);
        return List.of("Onboarding started. runId=" + runId);
    }

    public String startOnboarding(String repositoryPathOrId, String fromStage, boolean resume) {
        String startStage = fromStage == null || fromStage.isBlank() ? STAGES.get(0) : fromStage;
        if (!STAGES.contains(startStage)) {
            throw new IllegalArgumentException("Unsupported stage: " + startStage);
        }
        RepositoryRegistration registration = repositoryRegistryService.register(Path.of(repositoryPathOrId));
        String runId = StableId.of("onboard_run_", registration.id() + ":" + System.currentTimeMillis());
        jdbcTemplate.update("INSERT INTO onboarding_runs (id, repository_id, status, started_at) VALUES (?, ?, 'running', now())",
                runId, registration.id());
        executeRunAsync(runId, registration.id(), startStage, resume);
        return runId;
    }

    @Async("onboardingExecutor")
    public void executeRunAsync(String runId, String repositoryId, String startStage, boolean resume) {
        RepositoryRegistration registration = repositoryRegistryService.resolve(repositoryId);
        Set<String> completed = resume ? completedStages(registration.id()) : Set.of();
        boolean started = false;
        for (String stage : STAGES) {
            if (!started && !stage.equals(startStage)) {
                continue;
            }
            started = true;
            if (resume && completed.contains(stage)) {
                continue;
            }
            executeStage(runId, stage, registration);
        }
        jdbcTemplate.update("UPDATE onboarding_runs SET status = 'completed', ended_at = now() WHERE id = ?", runId);
    }

    public Map<String, Object> status(String repositoryIdentifier) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("repository", repository.name());
        status.put("repositoryId", repository.id());
        status.put("counts", counts(repository.id()));
        status.put("lastRun", latestRun(repository.id()));
        return status;
    }

    private void executeStage(String runId, String stage, RepositoryRegistration repository) {
        String stageId = StableId.of("onboard_stage_", runId + ":" + stage);
        jdbcTemplate.update("INSERT INTO onboarding_run_stages (id, run_id, stage_name, status, started_at) VALUES (?, ?, ?, 'running', now())",
                stageId, runId, stage);
        try {
            switch (stage) {
                case "register" -> {}
                case "scan-files" -> sourceFileScanService.scan(repository.id());
                case "index-classes" -> classIndexService.index(repository.id());
                case "index-methods" -> methodIndexService.index(repository.id());
                case "infer-roles" -> featurePipelineService.inferRoles(repository.id());
                case "detect-all" -> featurePipelineService.detect("all", repository.id());
                case "chunks" -> featurePipelineService.buildChunks(repository.id());
                case "embed" -> featurePipelineService.embed(repository.id());
                case "report-no-llm" -> featurePipelineService.report(repository.id(), false);
                default -> throw new IllegalArgumentException("Unsupported stage: " + stage);
            }
            jdbcTemplate.update("UPDATE onboarding_run_stages SET status = 'completed', ended_at = now() WHERE id = ?", stageId);
        } catch (Exception exception) {
            jdbcTemplate.update("UPDATE onboarding_run_stages SET status = 'failed', details = ?, ended_at = now() WHERE id = ?",
                    exception.getMessage(), stageId);
            jdbcTemplate.update("UPDATE onboarding_runs SET status = 'failed', ended_at = now() WHERE id = ?", runId);
            throw exception;
        }
    }

    public Map<String, Object> runStatus(String runId) {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList("SELECT id, repository_id, status, started_at, ended_at FROM onboarding_runs WHERE id = ?", runId);
        if (runs.isEmpty()) {
            return Map.of("status", "not-found", "runId", runId);
        }
        Map<String, Object> run = new LinkedHashMap<>(runs.get(0));
        List<Map<String, Object>> stages = jdbcTemplate.queryForList("""
                SELECT stage_name, status, details, started_at, ended_at
                FROM onboarding_run_stages
                WHERE run_id = ?
                ORDER BY started_at
                """, runId);
        run.put("stages", stages);
        return run;
    }

    private Set<String> completedStages(String repositoryId) {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT ors.stage_name
                FROM onboarding_run_stages ors
                JOIN onboarding_runs oru ON oru.id = ors.run_id
                WHERE oru.repository_id = ? AND ors.status = 'completed'
                """, String.class, repositoryId);
        return Set.copyOf(rows);
    }

    private Map<String, Object> counts(String repositoryId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("source_files", queryCount("source_files", repositoryId));
        counts.put("classes", queryCount("classes", repositoryId));
        counts.put("methods", queryCount("methods", repositoryId));
        counts.put("role_inference", queryCount("role_inference", repositoryId));
        counts.put("opportunity_candidates", queryCount("opportunity_candidates", repositoryId));
        counts.put("code_chunks", queryCount("code_chunks", repositoryId));
        counts.put("chunk_embeddings", queryCount("chunk_embeddings", repositoryId));
        return counts;
    }

    private long queryCount(String table, String repositoryId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE repository_id = ?", Long.class, repositoryId);
    }

    private Map<String, Object> latestRun(String repositoryId) {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList("""
                SELECT id, status, started_at, ended_at
                FROM onboarding_runs
                WHERE repository_id = ?
                ORDER BY started_at DESC
                LIMIT 1
                """, repositoryId);
        if (runs.isEmpty()) {
            return Map.of("status", "never-run");
        }
        Map<String, Object> run = new LinkedHashMap<>(runs.get(0));
        String runId = String.valueOf(run.get("id"));
        List<Map<String, Object>> stages = jdbcTemplate.queryForList("""
                SELECT stage_name, status, details, started_at, ended_at
                FROM onboarding_run_stages
                WHERE run_id = ?
                ORDER BY started_at
                """, runId);
        run.put("stages", stages);
        return run;
    }
}
