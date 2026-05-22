package com.ideaminer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesExpectedWorkspaceDirectories() {
        Path workspace = tempDir.resolve(".ideaminer");

        Path result = new WorkspaceService(workspace.toString()).initializeWorkspace();

        assertThat(result).isEqualTo(workspace);
        assertThat(Files.isDirectory(workspace.resolve("repos"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("reports"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("cache"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("models"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("logs"))).isTrue();
    }
}
