package com.ideaminer.service;

import com.ideaminer.model.DiscoveredSourceFile;
import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileRecord;
import com.ideaminer.model.SourceFileScanSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SourceFileScanService {

    private final RepositoryRegistryService repositoryRegistryService;
    private final SourceFileDiscoveryService discoveryService;
    private final ContentHashService contentHashService;
    private final JdbcTemplate jdbcTemplate;

    public SourceFileScanService(RepositoryRegistryService repositoryRegistryService,
                                 SourceFileDiscoveryService discoveryService,
                                 ContentHashService contentHashService,
                                 JdbcTemplate jdbcTemplate) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.discoveryService = discoveryService;
        this.contentHashService = contentHashService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public SourceFileScanSummary scan(String repositoryIdentifier) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        List<DiscoveredSourceFile> discoveredFiles = discoveryService.discover(Path.of(repository.localPath()));
        Map<String, SourceFileRecord> existingByPath = loadExistingFiles(repository.id()).stream()
                .collect(Collectors.toMap(SourceFileRecord::path, Function.identity()));

        int created = 0;
        int changed = 0;
        int unchanged = 0;
        int reactivated = 0;
        Set<String> seenPaths = new HashSet<>();
        Timestamp now = Timestamp.from(OffsetDateTime.now().toInstant());

        for (DiscoveredSourceFile file : discoveredFiles) {
            String contentHash = contentHashService.sha256(file.absolutePath());
            SourceFileRecord existing = existingByPath.get(file.relativePath());
            seenPaths.add(file.relativePath());

            if (existing == null) {
                created++;
                upsertFile(repository.id(), file, contentHash, now);
            } else if (!existing.active()) {
                reactivated++;
                upsertFile(repository.id(), file, contentHash, now);
            } else if (!contentHash.equals(existing.contentHash())) {
                changed++;
                upsertFile(repository.id(), file, contentHash, now);
            } else {
                unchanged++;
                markIndexed(repository.id(), file.relativePath(), now);
            }
        }

        int deleted = markDeleted(repository.id(), existingByPath, seenPaths, now);

        return new SourceFileScanSummary(
                repository.id(),
                repository.name(),
                discoveredFiles.size(),
                created,
                changed,
                unchanged,
                reactivated,
                deleted
        );
    }

    private List<SourceFileRecord> loadExistingFiles(String repositoryId) {
        String sql = """
                SELECT id, path, content_hash, active
                FROM source_files
                WHERE repository_id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SourceFileRecord(
                rs.getString("id"),
                rs.getString("path"),
                rs.getString("content_hash"),
                rs.getBoolean("active")
        ), repositoryId);
    }

    private void upsertFile(String repositoryId, DiscoveredSourceFile file, String contentHash, Timestamp now) {
        String sql = """
                INSERT INTO source_files (id, repository_id, path, language, content_hash, last_indexed_at, active)
                VALUES (?, ?, ?, ?, ?, ?, true)
                ON CONFLICT (repository_id, path) DO UPDATE SET
                    language = EXCLUDED.language,
                    content_hash = EXCLUDED.content_hash,
                    last_indexed_at = EXCLUDED.last_indexed_at,
                    active = true
                """;
        jdbcTemplate.update(sql, stableFileId(repositoryId, file.relativePath()), repositoryId, file.relativePath(),
                file.language(), contentHash, now);
    }

    private void markIndexed(String repositoryId, String relativePath, Timestamp now) {
        jdbcTemplate.update(
                "UPDATE source_files SET last_indexed_at = ? WHERE repository_id = ? AND path = ?",
                now,
                repositoryId,
                relativePath
        );
    }

    private int markDeleted(String repositoryId,
                            Map<String, SourceFileRecord> existingByPath,
                            Set<String> seenPaths,
                            Timestamp now) {
        int deleted = 0;
        for (SourceFileRecord existing : existingByPath.values()) {
            if (existing.active() && !seenPaths.contains(existing.path())) {
                deleted += jdbcTemplate.update(
                        "UPDATE source_files SET active = false, last_indexed_at = ? WHERE repository_id = ? AND path = ?",
                        now,
                        repositoryId,
                        existing.path()
                );
            }
        }
        return deleted;
    }

    private String stableFileId(String repositoryId, String relativePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((repositoryId + ":" + relativePath).getBytes(StandardCharsets.UTF_8));
            return "file_" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
