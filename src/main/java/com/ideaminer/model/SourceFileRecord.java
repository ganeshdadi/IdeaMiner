package com.ideaminer.model;

public record SourceFileRecord(
        String id,
        String path,
        String contentHash,
        boolean active
) {
}
