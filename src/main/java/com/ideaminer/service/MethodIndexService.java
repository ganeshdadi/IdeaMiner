package com.ideaminer.service;

import com.ideaminer.model.MethodFact;
import com.ideaminer.model.MethodIndexSummary;
import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MethodIndexService {

    private final RepositoryRegistryService repositoryRegistryService;
    private final ClassIndexService classIndexService;
    private final JavaMethodAnalyzerService analyzerService;
    private final JdbcTemplate jdbcTemplate;

    public MethodIndexService(RepositoryRegistryService repositoryRegistryService,
                              ClassIndexService classIndexService,
                              JavaMethodAnalyzerService analyzerService,
                              JdbcTemplate jdbcTemplate) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.classIndexService = classIndexService;
        this.analyzerService = analyzerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public MethodIndexSummary index(String repositoryIdentifier) {
        classIndexService.index(repositoryIdentifier);
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        List<SourceFileIndexEntry> sourceFiles = activeJavaSourceFiles(repository.id());

        int filesParsed = 0;
        int filesFailed = 0;
        int methodsIndexed = 0;

        for (SourceFileIndexEntry sourceFile : sourceFiles) {
            try {
                List<MethodFact> methodFacts = analyzerService.analyze(repository, sourceFile);
                for (MethodFact methodFact : methodFacts) {
                    upsert(methodFact);
                    methodsIndexed++;
                }
                filesParsed++;
            } catch (RuntimeException e) {
                filesFailed++;
                System.err.println("[MethodIndex] " + e.getMessage());
            }
        }

        return new MethodIndexSummary(
                repository.id(),
                repository.name(),
                sourceFiles.size(),
                filesParsed,
                filesFailed,
                methodsIndexed
        );
    }

    public List<MethodFact> listMethods(String repositoryIdentifier, int complexityMin) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        String sql = """
                SELECT m.id, m.repository_id, m.class_id, m.file_id, c.class_name, c.package_name, c.file_path,
                       m.method_name, m.signature, m.return_type, m.parameters::text, m.annotations::text,
                       m.cyclomatic_complexity,
                       COALESCE((m.source_span->>'beginLine')::int, 0) AS begin_line,
                       COALESCE((m.source_span->>'endLine')::int, 0) AS end_line
                FROM methods m
                JOIN classes c ON c.id = m.class_id
                WHERE m.repository_id = ? AND m.cyclomatic_complexity >= ?
                ORDER BY m.cyclomatic_complexity DESC, c.file_path, m.method_name
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MethodFact(
                rs.getString("id"),
                rs.getString("repository_id"),
                rs.getString("class_id"),
                rs.getString("file_id"),
                rs.getString("class_name"),
                rs.getString("package_name"),
                rs.getString("file_path"),
                rs.getString("method_name"),
                rs.getString("signature"),
                rs.getString("return_type"),
                List.of(),
                List.of(),
                rs.getInt("cyclomatic_complexity"),
                rs.getInt("begin_line"),
                rs.getInt("end_line")
        ), repository.id(), complexityMin);
    }

    private List<SourceFileIndexEntry> activeJavaSourceFiles(String repositoryId) {
        String sql = """
                SELECT id, repository_id, path, language
                FROM source_files
                WHERE repository_id = ? AND active = true AND language = 'java'
                ORDER BY path
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SourceFileIndexEntry(
                rs.getString("id"),
                rs.getString("repository_id"),
                rs.getString("path"),
                rs.getString("language")
        ), repositoryId);
    }

    private void upsert(MethodFact methodFact) {
        String sql = """
                INSERT INTO methods (
                    id, repository_id, class_id, file_id, method_name, signature, return_type,
                    parameters, annotations, cyclomatic_complexity, source_span, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, now())
                ON CONFLICT (id) DO UPDATE SET
                    repository_id = EXCLUDED.repository_id,
                    class_id = EXCLUDED.class_id,
                    file_id = EXCLUDED.file_id,
                    method_name = EXCLUDED.method_name,
                    signature = EXCLUDED.signature,
                    return_type = EXCLUDED.return_type,
                    parameters = EXCLUDED.parameters,
                    annotations = EXCLUDED.annotations,
                    cyclomatic_complexity = EXCLUDED.cyclomatic_complexity,
                    source_span = EXCLUDED.source_span,
                    updated_at = now()
                """;
        jdbcTemplate.update(sql,
                methodFact.id(),
                methodFact.repositoryId(),
                methodFact.classId(),
                methodFact.fileId(),
                methodFact.methodName(),
                methodFact.signature(),
                methodFact.returnType(),
                jsonArray(methodFact.parameters()),
                jsonArray(methodFact.annotations()),
                methodFact.cyclomaticComplexity(),
                sourceSpanJson(methodFact.beginLine(), methodFact.endLine())
        );
    }

    private String jsonArray(List<String> values) {
        return values.stream()
                .map(this::quoteJson)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String sourceSpanJson(int beginLine, int endLine) {
        return "{\"beginLine\":" + beginLine + ",\"endLine\":" + endLine + "}";
    }

    private String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
