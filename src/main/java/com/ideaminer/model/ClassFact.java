package com.ideaminer.model;

import java.util.List;

public record ClassFact(
        String id,
        String repositoryId,
        String fileId,
        String repoName,
        String className,
        String packageName,
        String filePath,
        String classType,
        List<String> annotations,
        String summary,
        int cyclomaticComplexity,
        int beginLine,
        int endLine
) {
}
