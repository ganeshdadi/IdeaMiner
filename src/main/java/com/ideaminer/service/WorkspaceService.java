package com.ideaminer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class WorkspaceService {

    private final Path workspacePath;

    public WorkspaceService(@Value("${ideaminer.workspace.dir:.ideaminer}") String workspaceDir) {
        this.workspacePath = Paths.get(workspaceDir);
    }

    public Path initializeWorkspace() {
        try {
            Files.createDirectories(workspacePath.resolve("repos"));
            Files.createDirectories(workspacePath.resolve("reports"));
            Files.createDirectories(workspacePath.resolve("cache"));
            Files.createDirectories(workspacePath.resolve("models"));
            Files.createDirectories(workspacePath.resolve("logs"));
            return workspacePath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize workspace at " + workspacePath, e);
        }
    }
}
