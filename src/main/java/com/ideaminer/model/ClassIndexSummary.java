package com.ideaminer.model;

public record ClassIndexSummary(
        String repositoryId,
        String repositoryName,
        int filesScanned,
        int filesParsed,
        int filesFailed,
        int classesIndexed
) {
}
