package com.ideaminer.model;

public record IndexedClassRef(
        String id,
        String repositoryId,
        String fileId,
        String className,
        String packageName,
        String filePath,
        String classType,
        int beginLine,
        int endLine
) {
}
