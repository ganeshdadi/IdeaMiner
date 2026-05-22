package com.ideaminer.service;

import com.ideaminer.model.ClassFact;
import com.ideaminer.model.ClassIndexSummary;
import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClassIndexService {

    private final RepositoryRegistryService repositoryRegistryService;
    private final SourceFileScanService sourceFileScanService;
    private final JavaClassAnalyzerService analyzerService;
    private final JdbcTemplate jdbcTemplate;

    public ClassIndexService(RepositoryRegistryService repositoryRegistryService,
                             SourceFileScanService sourceFileScanService,
                             JavaClassAnalyzerService analyzerService,
                             JdbcTemplate jdbcTemplate) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.sourceFileScanService = sourceFileScanService;
        this.analyzerService = analyzerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ClassIndexSummary index(String repositoryIdentifier) {
        sourceFileScanService.scan(repositoryIdentifier);
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        List<SourceFileIndexEntry> sourceFiles = activeJavaSourceFiles(repository.id());

        int filesParsed = 0;
        int filesFailed = 0;
        int classesIndexed = 0;

        for (SourceFileIndexEntry sourceFile : sourceFiles) {
            try {
                List<ClassFact> classFacts = analyzerService.analyze(repository, sourceFile);
                for (ClassFact classFact : classFacts) {
                    upsert(classFact);
                    classesIndexed++;
                }
                filesParsed++;
            } catch (RuntimeException e) {
                filesFailed++;
                System.err.println("[ClassIndex] " + e.getMessage());
            }
        }

        return new ClassIndexSummary(
                repository.id(),
                repository.name(),
                sourceFiles.size(),
                filesParsed,
                filesFailed,
                classesIndexed
        );
    }

    public List<ClassFact> listClasses(String repositoryIdentifier) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        String sql = """
                SELECT id, repository_id, file_id, repo_name, class_name, package_name, file_path,
                       class_type, annotations::text, summary, cyclomatic_complexity,
                       COALESCE((source_span->>'beginLine')::int, 0) AS begin_line,
                       COALESCE((source_span->>'endLine')::int, 0) AS end_line
                FROM classes
                WHERE repository_id = ?
                ORDER BY file_path, class_name
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ClassFact(
                rs.getString("id"),
                rs.getString("repository_id"),
                rs.getString("file_id"),
                rs.getString("repo_name"),
                rs.getString("class_name"),
                rs.getString("package_name"),
                rs.getString("file_path"),
                rs.getString("class_type"),
                List.of(),
                rs.getString("summary"),
                rs.getInt("cyclomatic_complexity"),
                rs.getInt("begin_line"),
                rs.getInt("end_line")
        ), repository.id());
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

    private void upsert(ClassFact classFact) {
        String sql = """
                INSERT INTO classes (
                    id, repository_id, file_id, repo_name, class_name, package_name, file_path,
                    class_type, annotations, summary, cyclomatic_complexity, source_span, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, now())
                ON CONFLICT (id) DO UPDATE SET
                    repository_id = EXCLUDED.repository_id,
                    file_id = EXCLUDED.file_id,
                    repo_name = EXCLUDED.repo_name,
                    class_name = EXCLUDED.class_name,
                    package_name = EXCLUDED.package_name,
                    file_path = EXCLUDED.file_path,
                    class_type = EXCLUDED.class_type,
                    annotations = EXCLUDED.annotations,
                    summary = EXCLUDED.summary,
                    cyclomatic_complexity = EXCLUDED.cyclomatic_complexity,
                    source_span = EXCLUDED.source_span,
                    updated_at = now()
                """;
        jdbcTemplate.update(sql,
                classFact.id(),
                classFact.repositoryId(),
                classFact.fileId(),
                classFact.repoName(),
                classFact.className(),
                classFact.packageName(),
                classFact.filePath(),
                classFact.classType(),
                jsonArray(classFact.annotations()),
                classFact.summary(),
                classFact.cyclomaticComplexity(),
                sourceSpanJson(classFact.beginLine(), classFact.endLine())
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
