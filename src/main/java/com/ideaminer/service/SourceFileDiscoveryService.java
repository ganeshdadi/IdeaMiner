package com.ideaminer.service;

import com.ideaminer.model.DiscoveredSourceFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SourceFileDiscoveryService {

    public SourceFileDiscoveryService() {
    }

    public List<DiscoveredSourceFile> discover(Path repositoryRoot) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repository path does not exist or is not a directory: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isIncluded(root, path))
                    .map(path -> new DiscoveredSourceFile(
                            path,
                            normalizeRelativePath(root.relativize(path)),
                            languageFor(path)
                    ))
                    .sorted(Comparator.comparing(DiscoveredSourceFile::relativePath))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to discover source files in: " + root, e);
        }
    }

    private boolean isIncluded(Path root, Path path) {
        String normalized = normalizeRelativePath(root.relativize(path));
        if (!normalized.endsWith(".java")) {
            return false;
        }
        return normalized.startsWith("src/main/java/");
    }

    private String extension(Path path) {
        String filename = path.getFileName().toString();
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index) : "";
    }

    private String languageFor(Path path) {
        return switch (extension(path)) {
            case ".java" -> "java";
            case ".kt", ".kts" -> "kotlin";
            default -> extension(path).replace(".", "");
        };
    }

    private String normalizeRelativePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
