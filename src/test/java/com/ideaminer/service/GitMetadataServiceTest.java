package com.ideaminer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
class GitMetadataServiceTest {

    @TempDir
    Path tempDir;

    private final GitMetadataService gitMetadataService = new GitMetadataService();

    @Test
    void inspectsGitRepositoryMetadata() throws IOException {
        Path repository = tempDir.resolve("sample-repo");
        Files.createDirectories(repository);
        run(repository, "git", "init");
        run(repository, "git", "config", "user.email", "test@example.com");
        run(repository, "git", "config", "user.name", "Test User");
        run(repository, "git", "remote", "add", "origin", "https://example.com/sample-repo.git");
        Files.writeString(repository.resolve("Example.java"), "class Example {}\n");
        run(repository, "git", "add", "Example.java");
        run(repository, "git", "commit", "-m", "Initial commit");

        var registration = gitMetadataService.inspect(repository);

        assertThat(registration.id()).startsWith("repo_");
        assertThat(registration.name()).isEqualTo("sample-repo");
        assertThat(registration.localPath()).isEqualTo(repository.toRealPath().toString());
        assertThat(registration.remoteUrl()).isEqualTo("https://example.com/sample-repo.git");
        assertThat(registration.branch()).isNotBlank();
        assertThat(registration.commitSha()).hasSize(40);
        assertThat(registration.indexedAt()).isNotNull();
    }

    @Test
    void acceptsNonGitDirectoryWithoutGitMetadata() throws IOException {
        Path directory = tempDir.resolve("not-git");
        Files.createDirectories(directory);

        var registration = gitMetadataService.inspect(directory);

        assertThat(registration.id()).startsWith("repo_");
        assertThat(registration.name()).isEqualTo("not-git");
        assertThat(registration.localPath()).isEqualTo(directory.toRealPath().toString());
        assertThat(registration.remoteUrl()).isNull();
        assertThat(registration.branch()).isNull();
        assertThat(registration.commitSha()).isNull();
        assertThat(registration.indexedAt()).isNotNull();
    }

    private void run(Path directory, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running command: " + String.join(" ", command), e);
        }
    }
}
