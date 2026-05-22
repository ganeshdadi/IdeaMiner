package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RepositoryRegistryService {

    private final JdbcTemplate jdbcTemplate;
    private final GitMetadataService gitMetadataService;
    private final WorkspaceService workspaceService;

    public RepositoryRegistryService(JdbcTemplate jdbcTemplate,
                                     GitMetadataService gitMetadataService,
                                     WorkspaceService workspaceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.gitMetadataService = gitMetadataService;
        this.workspaceService = workspaceService;
    }

    public RepositoryRegistration register(Path repositoryPath) {
        workspaceService.initializeWorkspace();
        RepositoryRegistration registration = gitMetadataService.inspect(repositoryPath);

        String sql = """
                INSERT INTO repositories (id, name, local_path, remote_url, branch, commit_sha, indexed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    local_path = EXCLUDED.local_path,
                    remote_url = EXCLUDED.remote_url,
                    branch = EXCLUDED.branch,
                    commit_sha = EXCLUDED.commit_sha,
                    indexed_at = EXCLUDED.indexed_at,
                    updated_at = now()
                """;
        jdbcTemplate.update(
                sql,
                registration.id(),
                registration.name(),
                registration.localPath(),
                registration.remoteUrl(),
                registration.branch(),
                registration.commitSha(),
                Timestamp.from(registration.indexedAt().toInstant())
        );

        return registration;
    }

    public List<RepositoryRegistration> listRepositories() {
        String sql = """
                SELECT id, name, local_path, remote_url, branch, commit_sha, indexed_at
                FROM repositories
                ORDER BY name, local_path
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RepositoryRegistration(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("local_path"),
                rs.getString("remote_url"),
                rs.getString("branch"),
                rs.getString("commit_sha"),
                toOffsetDateTime(rs.getTimestamp("indexed_at"))
        ));
    }

    public RepositoryRegistration resolve(String repositoryIdentifier) {
        Optional<RepositoryRegistration> registered = findByIdNameOrPath(repositoryIdentifier);
        if (registered.isPresent()) {
            return registered.get();
        }

        Path path = Path.of(repositoryIdentifier);
        if (path.isAbsolute() || path.toFile().exists()) {
            return register(path);
        }

        throw new IllegalArgumentException("No registered repository found for: " + repositoryIdentifier);
    }

    private Optional<RepositoryRegistration> findByIdNameOrPath(String repositoryIdentifier) {
        String sql = """
                SELECT id, name, local_path, remote_url, branch, commit_sha, indexed_at
                FROM repositories
                WHERE id = ? OR name = ? OR local_path = ?
                ORDER BY
                    CASE
                        WHEN id = ? THEN 0
                        WHEN local_path = ? THEN 1
                        ELSE 2
                    END,
                    updated_at DESC
                LIMIT 1
                """;
        List<RepositoryRegistration> repositories = jdbcTemplate.query(sql, (rs, rowNum) -> new RepositoryRegistration(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("local_path"),
                rs.getString("remote_url"),
                rs.getString("branch"),
                rs.getString("commit_sha"),
                toOffsetDateTime(rs.getTimestamp("indexed_at"))
        ), repositoryIdentifier, repositoryIdentifier, repositoryIdentifier, repositoryIdentifier, repositoryIdentifier);
        return repositories.stream().findFirst();
    }

    private OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(OffsetDateTime.now().getOffset());
    }
}
