package com.ideaminer.model;

import java.nio.file.Path;

public record DiscoveredSourceFile(
        Path absolutePath,
        String relativePath,
        String language
) {
}
