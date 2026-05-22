package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class GitMetadataService {

    public RepositoryRegistration inspect(Path repositoryPath) {
        Path normalizedPath = realPath(repositoryPath);
        if (!Files.isDirectory(normalizedPath)) {
            throw new IllegalArgumentException("Repository path does not exist or is not a directory: " + normalizedPath);
        }

        String localPath = normalizedPath.toString();
        String name = normalizedPath.getFileName().toString();
        String remoteUrl = runGitOptional(normalizedPath, "remote", "get-url", "origin");
        String branch = runGitOptional(normalizedPath, "branch", "--show-current");
        String commitSha = runGitOptional(normalizedPath, "rev-parse", "HEAD");
        String id = stableId(localPath);

        return new RepositoryRegistration(
                id,
                name,
                localPath,
                emptyToNull(remoteUrl),
                emptyToNull(branch),
                commitSha,
                OffsetDateTime.now()
        );
    }

    private Path realPath(Path repositoryPath) {
        try {
            return repositoryPath.toRealPath();
        } catch (IOException e) {
            return repositoryPath.toAbsolutePath().normalize();
        }
    }

    private String runGitOptional(Path workingDirectory, String... args) {
        try {
            return runGit(workingDirectory, args);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String runGit(Path workingDirectory, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command(args));
            processBuilder.directory(workingDirectory.toFile());
            Process process = processBuilder.start();
            process.getInputStream().transferTo(output);
            process.getErrorStream().transferTo(error);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String message = error.toString(StandardCharsets.UTF_8).trim();
                throw new IllegalArgumentException("Git command failed in " + workingDirectory + ": " + message);
            }
            return output.toString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run git. Ensure Git is installed and available on PATH.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git", e);
        }
    }

    private List<String> command(String... args) {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private String stableId(String localPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(localPath.getBytes(StandardCharsets.UTF_8));
            return "repo_" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
