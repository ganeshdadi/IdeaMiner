package com.ideaminer.model;

public record SourceFileScanSummary(
        String repositoryId,
        String repositoryName,
        int discovered,
        int created,
        int changed,
        int unchanged,
        int reactivated,
        int deleted
) {
}
