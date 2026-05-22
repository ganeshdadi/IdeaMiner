package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceFileScanServiceTest {

    @TempDir
    Path repositoryRoot;

    @Test
    void reconcilesCreatedChangedUnchangedReactivatedAndDeletedFiles() throws Exception {
        Path changed = write("src/main/java/com/example/Changed.java", "class Changed { int value = 2; }\n");
        Path unchanged = write("src/main/java/com/example/Unchanged.java", "class Unchanged {}\n");
        write("src/main/java/com/example/NewFile.java", "class NewFile {}\n");
        write("src/main/java/com/example/Inactive.java", "class Inactive {}\n");

        ContentHashService hashService = new ContentHashService();
        RepositoryRegistryService registryService = mock(RepositoryRegistryService.class);
        RepositoryRegistration registration = new RepositoryRegistration(
                "repo_abc",
                "sample",
                repositoryRoot.toString(),
                null,
                null,
                null,
                OffsetDateTime.now()
        );
        when(registryService.resolve("sample")).thenReturn(registration);

        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of(
                new SourceFileRecord("file_changed", relative(changed), "old-hash", true),
                new SourceFileRecord("file_unchanged", relative(unchanged), hashService.sha256(unchanged), true),
                new SourceFileRecord("file_inactive", "src/main/java/com/example/Inactive.java", "old-inactive-hash", false),
                new SourceFileRecord("file_deleted", "src/main/java/com/example/Deleted.java", "deleted-hash", true)
        ));

        SourceFileScanService service = new SourceFileScanService(
                registryService,
                new SourceFileDiscoveryService(),
                hashService,
                jdbcTemplate
        );

        var summary = service.scan("sample");

        assertThat(summary.discovered()).isEqualTo(4);
        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.changed()).isEqualTo(1);
        assertThat(summary.unchanged()).isEqualTo(1);
        assertThat(summary.reactivated()).isEqualTo(1);
        assertThat(summary.deleted()).isEqualTo(1);
        assertThat(jdbcTemplate.updateSql)
                .anyMatch(sql -> sql.contains("INSERT INTO source_files"))
                .anyMatch(sql -> sql.contains("active = false"));
    }

    private Path write(String relativePath, String content) throws Exception {
        Path path = repositoryRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private String relative(Path path) {
        return repositoryRoot.relativize(path).toString().replace('\\', '/');
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<SourceFileRecord> existingFiles;
        private final List<String> updateSql = new ArrayList<>();

        private RecordingJdbcTemplate(List<SourceFileRecord> existingFiles) {
            this.existingFiles = existingFiles;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) existingFiles;
        }

        @Override
        public int update(String sql, Object... args) {
            updateSql.add(sql);
            return 1;
        }
    }
}
