package com.ideaminer.service;

import com.ideaminer.model.DiscoveredSourceFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class SourceFileDiscoveryService {

    private static final Set<String> DEFAULT_EXCLUDED_SEGMENTS = Set.of(
            ".git",
            ".gradle",
            ".idea",
            ".ideaminer",
            "build",
            "target",
            "out",
            "generated",
            "generated-sources",
            "__tests__",
            "test",
            "tests"
    );

    private final Set<String> includedExtensions;
    private final Set<String> excludedPathSegments;

    public SourceFileDiscoveryService(
            @Value("${ideaminer.scan.include-extensions:.java}") String includedExtensions,
            @Value("${ideaminer.scan.exclude-segments:}") String excludedPathSegments) {
        this.includedExtensions = parseCsv(includedExtensions);
        Set<String> configuredSegments = parseCsv(excludedPathSegments);
        this.excludedPathSegments = configuredSegments.isEmpty() ? DEFAULT_EXCLUDED_SEGMENTS : configuredSegments;
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
        Path relativePath = root.relativize(path);
        if (!includedExtensions.contains(extension(path))) {
            return false;
        }
        for (Path segment : relativePath) {
            String normalizedSegment = segment.toString();
            if (excludedPathSegments.contains(normalizedSegment)) {
                return false;
            }
        }
        return true;
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

    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Stream.of(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalizeRelativePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
