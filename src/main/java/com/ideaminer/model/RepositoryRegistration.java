package com.ideaminer.model;

import java.time.OffsetDateTime;

public record RepositoryRegistration(
        String id,
        String name,
        String localPath,
        String remoteUrl,
        String branch,
        String commitSha,
        OffsetDateTime indexedAt
) {
}
