package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryRegistryServiceTest {

    @Test
    void registersRepositoryWithUpsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GitMetadataService gitMetadataService = mock(GitMetadataService.class);
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        RepositoryRegistration registration = new RepositoryRegistration(
                "repo_abc",
                "sample",
                "/tmp/sample",
                "https://example.com/sample.git",
                "main",
                "1234567890123456789012345678901234567890",
                OffsetDateTime.now()
        );
        when(workspaceService.initializeWorkspace()).thenReturn(Path.of(".ideaminer"));
        when(gitMetadataService.inspect(Path.of("/tmp/sample"))).thenReturn(registration);

        RepositoryRegistryService service = new RepositoryRegistryService(jdbcTemplate, gitMetadataService, workspaceService);

        RepositoryRegistration result = service.register(Path.of("/tmp/sample"));

        assertThat(result).isEqualTo(registration);
        verify(workspaceService).initializeWorkspace();
        verify(jdbcTemplate).update(
                any(String.class),
                eq("repo_abc"),
                eq("sample"),
                eq("/tmp/sample"),
                eq("https://example.com/sample.git"),
                eq("main"),
                eq("1234567890123456789012345678901234567890"),
                any()
        );
    }

    @Test
    void resolvesRepositoryByIdNameOrPath() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GitMetadataService gitMetadataService = mock(GitMetadataService.class);
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        RepositoryRegistration registration = new RepositoryRegistration(
                "repo_abc",
                "sample",
                "/tmp/sample",
                null,
                null,
                null,
                OffsetDateTime.now()
        );
        when(jdbcTemplate.query(any(String.class), org.mockito.ArgumentMatchers.<RowMapper<RepositoryRegistration>>any(),
                eq("sample"), eq("sample"), eq("sample"), eq("sample"), eq("sample")))
                .thenReturn(List.of(registration));

        RepositoryRegistryService service = new RepositoryRegistryService(jdbcTemplate, gitMetadataService, workspaceService);

        assertThat(service.resolve("sample")).isEqualTo(registration);
    }
}
