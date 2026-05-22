package com.ideaminer.model;

public record SourceFileIndexEntry(
        String id,
        String repositoryId,
        String path,
        String language
) {
}
