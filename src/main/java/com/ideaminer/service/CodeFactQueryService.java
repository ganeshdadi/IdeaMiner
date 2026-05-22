package com.ideaminer.service;

import com.ideaminer.model.IndexedClassRef;
import com.ideaminer.model.IndexedMethodRef;
import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodeFactQueryService {

    private final RepositoryRegistryService repositoryRegistryService;
    private final JdbcTemplate jdbcTemplate;

    public CodeFactQueryService(RepositoryRegistryService repositoryRegistryService, JdbcTemplate jdbcTemplate) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public RepositoryRegistration repository(String identifier) {
        return repositoryRegistryService.resolve(identifier);
    }

    public List<SourceFileIndexEntry> activeJavaFiles(String repositoryId) {
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

    public List<IndexedClassRef> classes(String repositoryId) {
        String sql = """
                SELECT id, repository_id, file_id, class_name, package_name, file_path, class_type,
                       COALESCE((source_span->>'beginLine')::int, 0) AS begin_line,
                       COALESCE((source_span->>'endLine')::int, 0) AS end_line
                FROM classes
                WHERE repository_id = ?
                ORDER BY file_path, class_name
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new IndexedClassRef(
                rs.getString("id"),
                rs.getString("repository_id"),
                rs.getString("file_id"),
                rs.getString("class_name"),
                rs.getString("package_name"),
                rs.getString("file_path"),
                rs.getString("class_type"),
                rs.getInt("begin_line"),
                rs.getInt("end_line")
        ), repositoryId);
    }

    public List<IndexedMethodRef> methods(String repositoryId) {
        String sql = """
                SELECT m.id, m.repository_id, m.class_id, m.file_id, c.class_name, c.package_name, c.file_path,
                       m.method_name, m.signature, m.return_type, m.cyclomatic_complexity,
                       COALESCE((m.source_span->>'beginLine')::int, 0) AS begin_line,
                       COALESCE((m.source_span->>'endLine')::int, 0) AS end_line
                FROM methods m
                JOIN classes c ON c.id = m.class_id
                WHERE m.repository_id = ?
                ORDER BY c.file_path, m.method_name
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new IndexedMethodRef(
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
                rs.getInt("cyclomatic_complexity"),
                rs.getInt("begin_line"),
                rs.getInt("end_line")
        ), repositoryId);
    }
}
