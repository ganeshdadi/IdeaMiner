package com.ideaminer.service;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.ideaminer.model.IndexedClassRef;
import com.ideaminer.model.IndexedMethodRef;
import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeaturePipelineService {

    private static final Set<String> DOMAIN_DICTIONARY = Set.of(
            "loan", "loans", "dispute", "disputes", "payment", "payments", "card", "cards",
            "fraud", "onboarding", "statement", "statements", "limit", "limits", "offer", "offers",
            "notification", "notifications", "account", "accounts", "customer", "eligibility", "approval",
            "risk", "pricing", "score", "fee", "manual", "review", "case", "ticket", "queue", "hold",
            "pending", "exception", "status"
    );
    private static final Set<String> RULE_TERMS = Set.of("risk", "fraud", "eligibility", "approval", "limit", "pricing", "score", "offer", "fee");
    private static final Set<String> BATCH_TERMS = Set.of("notification", "status", "payment", "dispute", "loan", "onboarding", "account");
    private static final Set<String> MANUAL_TERMS = Set.of("manual", "review", "case", "ticket", "queue", "hold", "pending", "exception", "approval", "ops");

    private final MethodIndexService methodIndexService;
    private final CodeFactQueryService queryService;
    private final WorkspaceService workspaceService;
    private final JdbcTemplate jdbcTemplate;

    public FeaturePipelineService(MethodIndexService methodIndexService,
                                  CodeFactQueryService queryService,
                                  WorkspaceService workspaceService,
                                  JdbcTemplate jdbcTemplate) {
        this.methodIndexService = methodIndexService;
        this.queryService = queryService;
        this.workspaceService = workspaceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> indexEndpoints(String repositoryIdentifier) {
        methodIndexService.index(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        Map<String, IndexedMethodRef> methodsByClassAndSignature = methodsByClassAndSignature(repository.id());
        List<String> output = new ArrayList<>();

        parseRepository(repository).forEach(parsed -> {
            String classId = stableClassId(repository.id(), parsed.file().path(), parsed.packageName(), parsed.type().getNameAsString());
            List<String> classRoutes = routes(parsed.type().getAnnotations(), "ANY");
            if (classRoutes.isEmpty()) {
                classRoutes = List.of("");
            }
            for (MethodDeclaration method : parsed.type().getMethods()) {
                List<EndpointMapping> mappings = endpointMappings(method.getAnnotations());
                for (EndpointMapping mapping : mappings) {
                    IndexedMethodRef methodRef = methodsByClassAndSignature.get(classId + "|" + signature(method.getNameAsString(), method.getParameters()));
                    for (String classRoute : classRoutes) {
                        for (String methodRoute : mapping.routes()) {
                            String route = combineRoute(classRoute, methodRoute);
                            String endpointId = StableId.of("endpoint_", repository.id() + ":" + mapping.httpMethod() + ":" + route + ":" + (methodRef == null ? method.getNameAsString() : methodRef.id()));
                            jdbcTemplate.update("""
                                            INSERT INTO endpoints (id, repository_id, class_id, method_id, http_method, route, class_name, method_name, customer_facing, source_span, updated_at)
                                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, ?::jsonb, now())
                                            ON CONFLICT (id) DO UPDATE SET
                                                class_id = EXCLUDED.class_id,
                                                class_name = EXCLUDED.class_name,
                                                method_name = EXCLUDED.method_name,
                                                customer_facing = true,
                                                source_span = EXCLUDED.source_span,
                                                updated_at = now()
                                            """,
                                    endpointId,
                                    repository.id(),
                                    classId,
                                    methodRef == null ? null : methodRef.id(),
                                    mapping.httpMethod(),
                                    route,
                                    parsed.type().getNameAsString(),
                                    method.getNameAsString(),
                                    JsonSupport.sourceSpan(lineStart(method), lineEnd(method)));
                            output.add(mapping.httpMethod() + " " + route + " -> " + parsed.type().getNameAsString() + "#" + method.getNameAsString());
                        }
                    }
                }
            }
        });
        return output;
    }

    public List<String> listEndpoints(String repositoryIdentifier) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        return jdbcTemplate.query("""
                SELECT http_method, route, class_name, method_name
                FROM endpoints
                WHERE repository_id = ?
                ORDER BY route, http_method
                """, (rs, rowNum) -> rs.getString("http_method") + " " + rs.getString("route") + " -> " +
                rs.getString("class_name") + "#" + rs.getString("method_name"), repository.id());
    }

    public List<String> indexJobs(String repositoryIdentifier) {
        methodIndexService.index(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        Map<String, IndexedMethodRef> methodsByClassAndSignature = methodsByClassAndSignature(repository.id());
        List<String> output = new ArrayList<>();

        parseRepository(repository).forEach(parsed -> {
            String classId = stableClassId(repository.id(), parsed.file().path(), parsed.packageName(), parsed.type().getNameAsString());
            String batchType = batchType(parsed.type());
            if (batchType != null) {
                String id = StableId.of("job_", repository.id() + ":batch:" + classId + ":" + batchType);
                jdbcTemplate.update("""
                                INSERT INTO scheduled_jobs (id, repository_id, class_id, method_id, job_type, name, schedule_expression, metadata, updated_at)
                                VALUES (?, ?, ?, null, ?, ?, null, '{}'::jsonb, now())
                                ON CONFLICT (id) DO UPDATE SET updated_at = now()
                                """, id, repository.id(), classId, batchType, parsed.type().getNameAsString());
                output.add(batchType + " " + parsed.type().getNameAsString());
            }
            for (MethodDeclaration method : parsed.type().getMethods()) {
                Optional<AnnotationExpr> scheduled = method.getAnnotationByName("Scheduled");
                if (scheduled.isPresent()) {
                    IndexedMethodRef methodRef = methodsByClassAndSignature.get(classId + "|" + signature(method.getNameAsString(), method.getParameters()));
                    String expression = annotationValue(scheduled.get(), "cron")
                            .or(() -> annotationValue(scheduled.get(), "fixedRate"))
                            .or(() -> annotationValue(scheduled.get(), "fixedDelay"))
                            .orElse("");
                    String id = StableId.of("job_", repository.id() + ":scheduled:" + classId + ":" + method.getNameAsString() + ":" + expression);
                    jdbcTemplate.update("""
                                    INSERT INTO scheduled_jobs (id, repository_id, class_id, method_id, job_type, name, schedule_expression, metadata, updated_at)
                                    VALUES (?, ?, ?, ?, 'Scheduled', ?, ?, ?::jsonb, now())
                                    ON CONFLICT (id) DO UPDATE SET
                                        schedule_expression = EXCLUDED.schedule_expression,
                                        metadata = EXCLUDED.metadata,
                                        updated_at = now()
                                    """, id, repository.id(), classId, methodRef == null ? null : methodRef.id(),
                            parsed.type().getNameAsString() + "#" + method.getNameAsString(), expression,
                            JsonSupport.object("annotation", "Scheduled"));
                    output.add("Scheduled " + parsed.type().getNameAsString() + "#" + method.getNameAsString() + " " + expression);
                }
            }
        });
        return output;
    }

    public List<String> listJobs(String repositoryIdentifier) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        return jdbcTemplate.query("""
                SELECT job_type, name, COALESCE(schedule_expression, '') AS schedule_expression
                FROM scheduled_jobs
                WHERE repository_id = ?
                ORDER BY job_type, name
                """, (rs, rowNum) -> rs.getString("job_type") + " | " + rs.getString("name") + " | " + rs.getString("schedule_expression"), repository.id());
    }

    public List<String> indexDatabaseAccess(String repositoryIdentifier) {
        methodIndexService.index(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        Map<String, IndexedMethodRef> methodsByClassAndSignature = methodsByClassAndSignature(repository.id());
        List<String> output = new ArrayList<>();

        parseRepository(repository).forEach(parsed -> {
            String classId = stableClassId(repository.id(), parsed.file().path(), parsed.packageName(), parsed.type().getNameAsString());
            if (hasAnnotation(parsed.type(), "Entity")) {
                String tableName = parsed.type().getAnnotationByName("Table")
                        .flatMap(annotation -> annotationValue(annotation, "name"))
                        .orElse(parsed.type().getNameAsString());
                writeDbAccess(repository.id(), classId, null, "entity", tableName, "unknown", null);
                output.add("entity " + tableName + " -> " + parsed.type().getNameAsString());
            }
            if (hasAnnotation(parsed.type(), "Repository") || isRepositoryInterface(parsed.type())) {
                writeDbAccess(repository.id(), classId, null, "repository", parsed.type().getNameAsString(), "unknown", null);
                output.add("repository " + parsed.type().getNameAsString());
            }
            for (MethodDeclaration method : parsed.type().getMethods()) {
                IndexedMethodRef methodRef = methodsByClassAndSignature.get(classId + "|" + signature(method.getNameAsString(), method.getParameters()));
                Optional<String> query = method.getAnnotationByName("Query").flatMap(annotation -> annotationValue(annotation, "value"));
                if (query.isPresent()) {
                    writeDbAccess(repository.id(), classId, methodRef == null ? null : methodRef.id(), "query",
                            parsed.type().getNameAsString() + "#" + method.getNameAsString(), operationType(query.get()), query.get());
                    output.add("query " + operationType(query.get()) + " " + parsed.type().getNameAsString() + "#" + method.getNameAsString());
                } else if (isRepositoryMethod(method.getNameAsString())) {
                    writeDbAccess(repository.id(), classId, methodRef == null ? null : methodRef.id(), "repository_method",
                            parsed.type().getNameAsString() + "#" + method.getNameAsString(), operationFromMethodName(method.getNameAsString()), null);
                    output.add("repository_method " + operationFromMethodName(method.getNameAsString()) + " " + parsed.type().getNameAsString() + "#" + method.getNameAsString());
                }
            }
        });
        return output;
    }

    public List<String> listDatabaseAccess(String repositoryIdentifier) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        return jdbcTemplate.query("""
                SELECT access_type, target_name, operation_type
                FROM database_access
                WHERE repository_id = ?
                ORDER BY access_type, target_name
                """, (rs, rowNum) -> rs.getString("access_type") + " | " + rs.getString("operation_type") + " | " + rs.getString("target_name"), repository.id());
    }

    public List<String> buildGraph(String repositoryIdentifier) {
        indexEndpoints(repositoryIdentifier);
        indexJobs(repositoryIdentifier);
        indexDatabaseAccess(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        int edges = 0;
        for (SourceFileIndexEntry file : queryService.activeJavaFiles(repository.id())) {
            edges += edge("repository", repository.id(), "CONTAINS", "file", file.id(), JsonSupport.object("path", file.path()));
        }
        for (IndexedClassRef cls : queryService.classes(repository.id())) {
            edges += edge("file", cls.fileId(), "CONTAINS", "class", cls.id(), JsonSupport.object("className", cls.className()));
        }
        for (IndexedMethodRef method : queryService.methods(repository.id())) {
            edges += edge("class", method.classId(), "CONTAINS", "method", method.id(), JsonSupport.object("signature", method.signature()));
        }
        edges += jdbcTemplate.queryForList("SELECT id, method_id, route FROM endpoints WHERE repository_id = ? AND method_id IS NOT NULL", repository.id()).stream()
                .mapToInt(row -> edge("endpoint", (String) row.get("id"), "EXPOSES_ENDPOINT", "method", (String) row.get("method_id"), JsonSupport.object("route", (String) row.get("route"))))
                .sum();
        edges += jdbcTemplate.queryForList("SELECT id, method_id, name FROM scheduled_jobs WHERE repository_id = ? AND method_id IS NOT NULL", repository.id()).stream()
                .mapToInt(row -> edge("job", (String) row.get("id"), "SCHEDULED_BY", "method", (String) row.get("method_id"), JsonSupport.object("name", (String) row.get("name"))))
                .sum();
        edges += jdbcTemplate.queryForList("SELECT id, method_id, target_name, operation_type FROM database_access WHERE repository_id = ? AND method_id IS NOT NULL", repository.id()).stream()
                .mapToInt(row -> edge("method", (String) row.get("method_id"), dbEdgeType((String) row.get("operation_type")), "database_access", (String) row.get("id"), JsonSupport.object("target", String.valueOf(row.get("target_name")))))
                .sum();
        return List.of("Graph edges upserted: " + edges);
    }

    public List<String> graph(String repositoryIdentifier, String from) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        if (from == null || from.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT edge_type, count(*) AS count
                    FROM code_edges e
                    WHERE EXISTS (
                        SELECT 1 FROM repositories r WHERE r.id = ? AND (e.source_id LIKE '%' OR e.target_id LIKE '%')
                    )
                    GROUP BY edge_type
                    ORDER BY edge_type
                    """, (rs, rowNum) -> rs.getString("edge_type") + ": " + rs.getLong("count"), repository.id());
        }
        String sourceId = resolveGraphSource(repository.id(), from);
        return jdbcTemplate.query("""
                SELECT source_type, source_id, edge_type, target_type, target_id
                FROM code_edges
                WHERE source_id = ? OR target_id = ?
                ORDER BY edge_type
                """, (rs, rowNum) -> rs.getString("source_type") + ":" + rs.getString("source_id") + " -" +
                rs.getString("edge_type") + "-> " + rs.getString("target_type") + ":" + rs.getString("target_id"), sourceId, sourceId);
    }

    public List<String> extractDomainTerms(String repositoryIdentifier) {
        indexEndpoints(repositoryIdentifier);
        indexJobs(repositoryIdentifier);
        indexDatabaseAccess(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        int count = 0;
        for (IndexedClassRef cls : queryService.classes(repository.id())) {
            count += writeTerms(repository.id(), "class", cls.id(), cls.packageName() + " " + cls.className() + " " + cls.classType());
        }
        for (IndexedMethodRef method : queryService.methods(repository.id())) {
            count += writeTerms(repository.id(), "method", method.id(), method.className() + " " + method.methodName() + " " + method.signature());
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT id, route, class_name, method_name FROM endpoints WHERE repository_id = ?", repository.id())) {
            count += writeTerms(repository.id(), "endpoint", (String) row.get("id"), row.get("route") + " " + row.get("class_name") + " " + row.get("method_name"));
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT id, name FROM scheduled_jobs WHERE repository_id = ?", repository.id())) {
            count += writeTerms(repository.id(), "job", (String) row.get("id"), (String) row.get("name"));
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT id, target_name FROM database_access WHERE repository_id = ?", repository.id())) {
            count += writeTerms(repository.id(), "database_access", (String) row.get("id"), String.valueOf(row.get("target_name")));
        }
        return List.of("Domain term links upserted: " + count);
    }

    public List<String> searchDomain(String repositoryIdentifier, String term) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        return jdbcTemplate.query("""
                SELECT source_type, source_id, term, weight
                FROM domain_terms
                WHERE repository_id = ? AND term = lower(?)
                ORDER BY weight DESC, source_type
                """, (rs, rowNum) -> rs.getString("source_type") + ":" + rs.getString("source_id") + " term=" +
                rs.getString("term") + " weight=" + rs.getInt("weight"), repository.id(), term);
    }

    public List<String> detect(String detector, String repositoryIdentifier) {
        extractDomainTerms(repositoryIdentifier);
        buildGraph(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        return switch (detector) {
            case "all" -> {
                List<String> result = new ArrayList<>();
                result.addAll(detect("rule-heavy", repositoryIdentifier));
                result.addAll(detect("batch-realtime", repositoryIdentifier));
                result.addAll(detect("manual-review", repositoryIdentifier));
                yield result;
            }
            case "rule-heavy" -> detectRuleHeavy(repository);
            case "batch-realtime" -> detectBatchRealtime(repository);
            case "manual-review" -> detectManualReview(repository);
            default -> List.of("Unknown detector: " + detector);
        };
    }

    public List<String> candidates(String repositoryIdentifier) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        return jdbcTemplate.query("""
                SELECT c.id, c.detector, c.title, c.score,
                       COALESCE((SELECT state FROM review_feedback rf WHERE rf.candidate_id = c.id ORDER BY created_at DESC LIMIT 1), c.status) AS state
                FROM opportunity_candidates c
                WHERE c.repository_id = ?
                ORDER BY c.score DESC, c.title
                """, (rs, rowNum) -> rs.getString("id") + " | " + rs.getString("detector") + " | score=" +
                rs.getBigDecimal("score") + " | " + rs.getString("state") + " | " + rs.getString("title"), repository.id());
    }

    public List<String> evidence(String candidateId, boolean semantic, boolean promptSafe) {
        List<String> rows = jdbcTemplate.query("""
                SELECT description, source_type, source_id, COALESCE(file_path, '') AS file_path, source_span::text AS source_span
                FROM opportunity_evidence
                WHERE candidate_id = ?
                ORDER BY source_type, description
                """, (rs, rowNum) -> rs.getString("source_type") + ":" + rs.getString("source_id") + " | " +
                rs.getString("description") + " | " + rs.getString("file_path") + " " + rs.getString("source_span"), candidateId);
        if (semantic) {
            rows = new ArrayList<>(rows);
            Map<String, Object> candidate = jdbcTemplate.queryForMap("SELECT repository_id, title, summary FROM opportunity_candidates WHERE id = ?", candidateId);
            rows.addAll(semanticSearch((String) candidate.get("repository_id"), candidate.get("title") + " " + candidate.get("summary"), 3));
        }
        if (promptSafe) {
            SecretRedactionService redactionService = new SecretRedactionService(jdbcTemplate);
            return rows.stream().map(row -> redactionService.redact("evidence", candidateId, row)).toList();
        }
        return rows;
    }

    public Path report(String repositoryIdentifier, boolean llm) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        List<String> candidateRows = candidates(repositoryIdentifier);
        Path reportsDir = workspaceService.initializeWorkspace().resolve("reports");
        try {
            Files.createDirectories(reportsDir);
            Path reportPath = reportsDir.resolve("ideaminer-" + repository.name() + (llm ? "-llm" : "-no-llm") + ".md");
            StringBuilder markdown = new StringBuilder();
            markdown.append("# IdeaMiner Report: ").append(repository.name()).append("\n\n");
            markdown.append("Report mode: ").append(llm ? "LLM-enhanced from stored validations" : "deterministic no-LLM").append("\n\n");
            markdown.append("## Candidates\n\n");
            if (candidateRows.isEmpty()) {
                markdown.append("No candidates found.\n");
            } else {
                for (String row : candidateRows) {
                    markdown.append("- ").append(row).append("\n");
                }
            }
            markdown.append("\n## Evidence Appendix\n\n");
            for (Map<String, Object> candidate : jdbcTemplate.queryForList("SELECT id, title FROM opportunity_candidates WHERE repository_id = ? ORDER BY score DESC", repository.id())) {
                markdown.append("### ").append(candidate.get("title")).append("\n\n");
                for (String item : evidence((String) candidate.get("id"), false, true)) {
                    markdown.append("- ").append(item).append("\n");
                }
                markdown.append("\n");
            }
            Files.writeString(reportPath, markdown.toString());
            jdbcTemplate.update("""
                            INSERT INTO generated_reports (id, repository_id, report_type, file_path)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (id) DO NOTHING
                            """,
                    StableId.of("report_", repository.id() + ":" + llm + ":" + reportPath), repository.id(), llm ? "llm" : "no-llm", reportPath.toString());
            return reportPath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write report", e);
        }
    }

    public List<String> buildChunks(String repositoryIdentifier) {
        methodIndexService.index(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        int count = 0;
        for (IndexedMethodRef method : queryService.methods(repository.id())) {
            String text = method.packageName() + "." + method.className() + "#" + method.signature() +
                    " returns " + method.returnType() + " complexity " + method.complexity();
            String id = StableId.of("chunk_", repository.id() + ":method:" + method.id());
            jdbcTemplate.update("""
                            INSERT INTO code_chunks (id, repository_id, source_type, source_id, chunk_type, text, metadata, updated_at)
                            VALUES (?, ?, 'method', ?, 'method_summary', ?, ?::jsonb, now())
                            ON CONFLICT (repository_id, source_type, source_id, chunk_type) DO UPDATE SET
                                text = EXCLUDED.text,
                                metadata = EXCLUDED.metadata,
                                updated_at = now()
                            """, id, repository.id(), method.id(), text,
                    JsonSupport.object("filePath", method.filePath(), "className", method.className(), "signature", method.signature()));
            count++;
        }
        return List.of("Chunks upserted: " + count);
    }

    public List<String> embed(String repositoryIdentifier) {
        buildChunks(repositoryIdentifier);
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        int count = 0;
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT id, text FROM code_chunks WHERE repository_id = ?", repository.id())) {
            jdbcTemplate.update("""
                            INSERT INTO chunk_embeddings (chunk_id, repository_id, embedding, provider, updated_at)
                            VALUES (?, ?, ?::vector, 'deterministic-local', now())
                            ON CONFLICT (chunk_id) DO UPDATE SET
                                embedding = EXCLUDED.embedding,
                                provider = EXCLUDED.provider,
                                updated_at = now()
                            """, row.get("id"), repository.id(), vector16(String.valueOf(row.get("text"))));
            count++;
        }
        return List.of("Embeddings upserted: " + count);
    }

    public List<String> semanticSearch(String repositoryIdentifier, String query) {
        RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
        return semanticSearch(repository.id(), query, 10);
    }

    public List<String> validate(String candidateId) {
        List<String> evidence = evidence(candidateId, false, true);
        String verdict = evidence.isEmpty() ? "unsupported" : "supported";
        String json = "{\"verdict\":\"" + verdict + "\",\"evidenceCount\":" + evidence.size() + "}";
        String id = StableId.of("validation_", candidateId + ":" + verdict);
        jdbcTemplate.update("""
                INSERT INTO candidate_validations (id, candidate_id, provider, verdict, response_json)
                VALUES (?, ?, 'fake-local', ?, ?::jsonb)
                ON CONFLICT (id) DO NOTHING
                """, id, candidateId, verdict, json);
        return List.of("Validation " + verdict + " for " + candidateId + " with " + evidence.size() + " evidence items");
    }

    public List<String> workspace(String action, String name, String repositoryIdentifier) {
        if ("create".equals(action)) {
            String id = StableId.of("workspace_", name);
            jdbcTemplate.update("""
                    INSERT INTO workspaces (id, name)
                    VALUES (?, ?)
                    ON CONFLICT (name) DO NOTHING
                    """, id, name);
            return List.of("Workspace ready: " + name);
        }
        if ("add".equals(action)) {
            RepositoryRegistration repository = queryService.repository(repositoryIdentifier);
            String workspaceId = workspaceId(name);
            jdbcTemplate.update("""
                    INSERT INTO workspace_repositories (workspace_id, repository_id)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, workspaceId, repository.id());
            return List.of("Added " + repository.name() + " to workspace " + name);
        }
        return jdbcTemplate.query("""
                SELECT w.name, count(wr.repository_id) AS repo_count
                FROM workspaces w
                LEFT JOIN workspace_repositories wr ON wr.workspace_id = w.id
                GROUP BY w.name
                ORDER BY w.name
                """, (rs, rowNum) -> rs.getString("name") + " repositories=" + rs.getLong("repo_count"));
    }

    public List<String> feedback(String candidateId, String state, String notes) {
        String id = StableId.of("feedback_", candidateId + ":" + state + ":" + System.currentTimeMillis());
        jdbcTemplate.update("INSERT INTO review_feedback (id, candidate_id, state, notes) VALUES (?, ?, ?, ?)",
                id, candidateId, state, notes);
        jdbcTemplate.update("UPDATE opportunity_candidates SET status = ?, updated_at = now() WHERE id = ?", state, candidateId);
        return List.of("Feedback recorded: " + candidateId + " -> " + state);
    }

    public String promptSafe(String value) {
        return new SecretRedactionService(jdbcTemplate).redact("adhoc", "prompt", value);
    }

    private List<String> semanticSearch(String repositoryId, String query, int limit) {
        return jdbcTemplate.query("""
                SELECT c.id, c.source_type, c.source_id, c.text
                FROM chunk_embeddings e
                JOIN code_chunks c ON c.id = e.chunk_id
                WHERE e.repository_id = ?
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """, (rs, rowNum) -> "semantic " + rs.getString("source_type") + ":" + rs.getString("source_id") + " | " +
                rs.getString("text"), repositoryId, vector16(query), limit);
    }

    private List<String> detectRuleHeavy(RepositoryRegistration repository) {
        List<String> results = new ArrayList<>();
        for (IndexedMethodRef method : queryService.methods(repository.id())) {
            Set<String> terms = termsFor(repository.id(), "method", method.id());
            if (method.complexity() >= 5 && !intersection(terms, RULE_TERMS).isEmpty()) {
                String title = "Rule-heavy decisioning in " + method.className() + "#" + method.methodName();
                String candidateId = candidate(repository.id(), "rule-heavy", title,
                        "High-complexity method contains decisioning terms: " + intersection(terms, RULE_TERMS), method.complexity() + terms.size());
                evidence(candidateId, repository.id(), "method", method.id(), "High-complexity decisioning method", method.filePath(), method.beginLine(), method.endLine());
                results.add(candidateId + " " + title);
            }
        }
        return results;
    }

    private List<String> detectBatchRealtime(RepositoryRegistration repository) {
        List<String> results = new ArrayList<>();
        for (Map<String, Object> job : jdbcTemplate.queryForList("SELECT id, name FROM scheduled_jobs WHERE repository_id = ?", repository.id())) {
            Set<String> terms = termsFor(repository.id(), "job", (String) job.get("id"));
            if (!intersection(terms, BATCH_TERMS).isEmpty() || String.valueOf(job.get("name")).toLowerCase(Locale.ROOT).contains("status")) {
                String title = "Batch-to-real-time opportunity in " + job.get("name");
                String candidateId = candidate(repository.id(), "batch-realtime", title,
                        "Scheduled or batch workflow touches customer-visible domains.", 8 + terms.size());
                evidence(candidateId, repository.id(), "job", (String) job.get("id"), "Scheduled/batch workflow", null, 0, 0);
                results.add(candidateId + " " + title);
            }
        }
        return results;
    }

    private List<String> detectManualReview(RepositoryRegistration repository) {
        List<String> results = new ArrayList<>();
        for (IndexedMethodRef method : queryService.methods(repository.id())) {
            Set<String> terms = termsFor(repository.id(), "method", method.id());
            if (!intersection(terms, MANUAL_TERMS).isEmpty()) {
                String title = "Manual review flow near " + method.className() + "#" + method.methodName();
                String candidateId = candidate(repository.id(), "manual-review", title,
                        "Method contains manual-review workflow terms: " + intersection(terms, MANUAL_TERMS), 6 + method.complexity());
                evidence(candidateId, repository.id(), "method", method.id(), "Manual review term evidence", method.filePath(), method.beginLine(), method.endLine());
                results.add(candidateId + " " + title);
            }
        }
        return results;
    }

    private String candidate(String repositoryId, String detector, String title, String summary, double score) {
        String id = StableId.of("candidate_", repositoryId + ":" + detector + ":" + title);
        jdbcTemplate.update("""
                INSERT INTO opportunity_candidates (id, repository_id, detector, title, summary, score, metadata, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, now())
                ON CONFLICT (repository_id, detector, title) DO UPDATE SET
                    summary = EXCLUDED.summary,
                    score = EXCLUDED.score,
                    updated_at = now()
                """, id, repositoryId, detector, title, summary, score);
        return id;
    }

    private void evidence(String candidateId, String repositoryId, String sourceType, String sourceId, String description,
                          String filePath, int beginLine, int endLine) {
        String id = StableId.of("evidence_", candidateId + ":" + sourceType + ":" + sourceId + ":" + description);
        jdbcTemplate.update("""
                INSERT INTO opportunity_evidence (id, candidate_id, repository_id, source_type, source_id, description, file_path, source_span, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, '{}'::jsonb)
                ON CONFLICT (candidate_id, source_type, source_id, description) DO UPDATE SET
                    file_path = EXCLUDED.file_path,
                    source_span = EXCLUDED.source_span
                """, id, candidateId, repositoryId, sourceType, sourceId, description, filePath, JsonSupport.sourceSpan(beginLine, endLine));
    }

    private int writeTerms(String repositoryId, String sourceType, String sourceId, String text) {
        int count = 0;
        for (String term : extractTerms(text)) {
            String id = StableId.of("term_", repositoryId + ":" + sourceType + ":" + sourceId + ":" + term);
            count += jdbcTemplate.update("""
                    INSERT INTO domain_terms (id, repository_id, source_type, source_id, term, weight, metadata)
                    VALUES (?, ?, ?, ?, ?, 1, '{}'::jsonb)
                    ON CONFLICT (repository_id, source_type, source_id, term) DO UPDATE SET weight = domain_terms.weight + 1
                    """, id, repositoryId, sourceType, sourceId, term);
        }
        return count;
    }

    private Set<String> extractTerms(String text) {
        String spaced = text == null ? "" : text.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Arrays.stream(spaced.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 2)
                .filter(DOMAIN_DICTIONARY::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> termsFor(String repositoryId, String sourceType, String sourceId) {
        return new HashSet<>(jdbcTemplate.queryForList("""
                SELECT term FROM domain_terms
                WHERE repository_id = ? AND source_type = ? AND source_id = ?
                """, String.class, repositoryId, sourceType, sourceId));
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> copy = new LinkedHashSet<>(left);
        copy.retainAll(right);
        return copy;
    }

    private int edge(String sourceType, String sourceId, String edgeType, String targetType, String targetId, String metadata) {
        String id = StableId.of("edge_", sourceType + ":" + sourceId + ":" + edgeType + ":" + targetType + ":" + targetId);
        return jdbcTemplate.update("""
                INSERT INTO code_edges (id, source_type, source_id, edge_type, target_type, target_id, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO NOTHING
                """, id, sourceType, sourceId, edgeType, targetType, targetId, metadata);
    }

    private List<ParsedType> parseRepository(RepositoryRegistration repository) {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        List<ParsedType> parsedTypes = new ArrayList<>();
        for (SourceFileIndexEntry file : queryService.activeJavaFiles(repository.id())) {
            try {
                CompilationUnit unit = StaticJavaParser.parse(Path.of(repository.localPath()).resolve(file.path()));
                String packageName = unit.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                unit.findAll(TypeDeclaration.class).stream()
                        .filter(type -> type instanceof ClassOrInterfaceDeclaration)
                        .map(type -> new ParsedType(file, packageName, (ClassOrInterfaceDeclaration) type))
                        .forEach(parsedTypes::add);
            } catch (Exception e) {
                System.err.println("[FeatureIndex] Failed to parse " + file.path() + ": " + e.getMessage());
            }
        }
        return parsedTypes;
    }

    private Map<String, IndexedMethodRef> methodsByClassAndSignature(String repositoryId) {
        Map<String, IndexedMethodRef> result = new HashMap<>();
        for (IndexedMethodRef method : queryService.methods(repositoryId)) {
            result.put(method.classId() + "|" + method.signature(), method);
        }
        return result;
    }

    private List<EndpointMapping> endpointMappings(NodeList<AnnotationExpr> annotations) {
        List<EndpointMapping> mappings = new ArrayList<>();
        for (AnnotationExpr annotation : annotations) {
            String name = annotation.getNameAsString();
            String httpMethod = switch (name) {
                case "GetMapping" -> "GET";
                case "PostMapping" -> "POST";
                case "PutMapping" -> "PUT";
                case "PatchMapping" -> "PATCH";
                case "DeleteMapping" -> "DELETE";
                case "RequestMapping" -> annotationValue(annotation, "method").map(this::httpMethodFromRequestMapping).orElse("ANY");
                default -> null;
            };
            if (httpMethod != null) {
                mappings.add(new EndpointMapping(httpMethod, routes(List.of(annotation), httpMethod)));
            }
        }
        return mappings;
    }

    private List<String> routes(List<AnnotationExpr> annotations, String ignored) {
        return annotations.stream()
                .filter(annotation -> annotation.getNameAsString().endsWith("Mapping"))
                .flatMap(annotation -> annotationValue(annotation, "value")
                        .or(() -> annotationValue(annotation, "path"))
                        .stream())
                .map(this::cleanAnnotationValue)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Optional<String> annotationValue(AnnotationExpr annotation, String name) {
        if (annotation instanceof SingleMemberAnnotationExpr single && ("value".equals(name) || "path".equals(name))) {
            return Optional.of(cleanAnnotationValue(single.getMemberValue().toString()));
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(name)) {
                    return Optional.of(cleanAnnotationValue(pair.getValue().toString()));
                }
            }
        }
        return Optional.empty();
    }

    private String cleanAnnotationValue(String value) {
        String cleaned = value.replace("{", "").replace("}", "").replace("\"", "").replace("RequestMethod.", "");
        int comma = cleaned.indexOf(',');
        return comma >= 0 ? cleaned.substring(0, comma).trim() : cleaned.trim();
    }

    private String httpMethodFromRequestMapping(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
            if (upper.contains(method)) {
                return method;
            }
        }
        return "ANY";
    }

    private String combineRoute(String classRoute, String methodRoute) {
        String combined = ("/" + nullToEmpty(classRoute) + "/" + nullToEmpty(methodRoute)).replaceAll("/+", "/");
        return combined.length() > 1 && combined.endsWith("/") ? combined.substring(0, combined.length() - 1) : combined;
    }

    private String signature(String methodName, NodeList<Parameter> parameters) {
        String parameterTypes = parameters.stream()
                .map(Parameter::getTypeAsString)
                .collect(Collectors.joining(","));
        return methodName + "(" + parameterTypes + ")";
    }

    private String stableClassId(String repositoryId, String filePath, String packageName, String className) {
        return StableId.of("class_", repositoryId + ":" + filePath + ":" + packageName + ":" + className);
    }

    private int lineStart(MethodDeclaration method) {
        return method.getRange().map(range -> range.begin.line).orElse(0);
    }

    private int lineEnd(MethodDeclaration method) {
        return method.getRange().map(range -> range.end.line).orElse(0);
    }

    private boolean hasAnnotation(TypeDeclaration<?> type, String annotationName) {
        return type.getAnnotationByName(annotationName).isPresent();
    }

    private boolean isRepositoryInterface(ClassOrInterfaceDeclaration type) {
        return type.isInterface() && type.getExtendedTypes().stream().anyMatch(extended ->
                extended.getNameAsString().contains("Repository"));
    }

    private boolean isRepositoryMethod(String methodName) {
        String lower = methodName.toLowerCase(Locale.ROOT);
        return lower.startsWith("find") || lower.startsWith("save") || lower.startsWith("delete")
                || lower.startsWith("count") || lower.startsWith("exists") || lower.startsWith("update");
    }

    private String operationFromMethodName(String methodName) {
        String lower = methodName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("save") || lower.startsWith("update")) {
            return "write";
        }
        if (lower.startsWith("delete")) {
            return "delete";
        }
        if (lower.startsWith("find") || lower.startsWith("count") || lower.startsWith("exists")) {
            return "read";
        }
        return "unknown";
    }

    private String operationType(String query) {
        String lower = query.toLowerCase(Locale.ROOT).stripLeading();
        if (lower.startsWith("select")) {
            return "read";
        }
        if (lower.startsWith("insert") || lower.startsWith("update")) {
            return "write";
        }
        if (lower.startsWith("delete")) {
            return "delete";
        }
        return "unknown";
    }

    private void writeDbAccess(String repositoryId, String classId, String methodId, String accessType,
                               String targetName, String operationType, String queryText) {
        String id = StableId.of("db_", repositoryId + ":" + accessType + ":" + targetName + ":" + classId + ":" + methodId);
        jdbcTemplate.update("""
                INSERT INTO database_access (id, repository_id, class_id, method_id, access_type, target_name, operation_type, query_text, metadata, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, now())
                ON CONFLICT (id) DO UPDATE SET
                    operation_type = EXCLUDED.operation_type,
                    query_text = EXCLUDED.query_text,
                    updated_at = now()
                """, id, repositoryId, classId, methodId, accessType, targetName, operationType, queryText);
    }

    private String batchType(ClassOrInterfaceDeclaration type) {
        String name = type.getNameAsString();
        if (name.endsWith("Tasklet")) {
            return "Tasklet";
        }
        return type.getImplementedTypes().stream()
                .map(implemented -> implemented.getNameAsString())
                .filter(value -> value.contains("Tasklet") || value.contains("ItemReader") || value.contains("ItemProcessor") || value.contains("ItemWriter"))
                .findFirst()
                .orElse(null);
    }

    private String dbEdgeType(String operationType) {
        return "write".equals(operationType) || "delete".equals(operationType) ? "WRITES_TABLE" : "READS_TABLE";
    }

    private String resolveGraphSource(String repositoryId, String from) {
        if (from.startsWith("endpoint:")) {
            String route = from.substring("endpoint:".length());
            List<String> ids = jdbcTemplate.queryForList("SELECT id FROM endpoints WHERE repository_id = ? AND route = ? LIMIT 1", String.class, repositoryId, route);
            return ids.isEmpty() ? route : ids.get(0);
        }
        int colon = from.indexOf(':');
        return colon >= 0 ? from.substring(colon + 1) : from;
    }

    private String workspaceId(String name) {
        List<String> ids = jdbcTemplate.queryForList("SELECT id FROM workspaces WHERE name = ?", String.class, name);
        if (ids.isEmpty()) {
            workspace("create", name, null);
            return StableId.of("workspace_", name);
        }
        return ids.get(0);
    }

    private String vector16(String text) {
        double[] values = new double[16];
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int index = 0; index < bytes.length; index++) {
            values[index % values.length] += Byte.toUnsignedInt(bytes[index]) / 255.0;
        }
        double norm = Math.sqrt(Arrays.stream(values).map(value -> value * value).sum());
        if (norm > 0) {
            for (int index = 0; index < values.length; index++) {
                values[index] = values[index] / norm;
            }
        }
        return Arrays.stream(values)
                .mapToObj(value -> String.format(Locale.ROOT, "%.6f", value))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ParsedType(SourceFileIndexEntry file, String packageName, ClassOrInterfaceDeclaration type) {
    }

    private record EndpointMapping(String httpMethod, List<String> routes) {
    }
}
