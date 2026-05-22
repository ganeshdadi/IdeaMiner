package com.ideaminer.model;

public record IndexedMethodRef(
        String id,
        String repositoryId,
        String classId,
        String fileId,
        String className,
        String packageName,
        String filePath,
        String methodName,
        String signature,
        String returnType,
        int complexity,
        int beginLine,
        int endLine
) {
}
