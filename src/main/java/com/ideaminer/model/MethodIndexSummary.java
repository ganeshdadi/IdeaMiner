package com.ideaminer.model;

public record MethodIndexSummary(
        String repositoryId,
        String repositoryName,
        int filesScanned,
        int filesParsed,
        int filesFailed,
        int methodsIndexed
) {
}
