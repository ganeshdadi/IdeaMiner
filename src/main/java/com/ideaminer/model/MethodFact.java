package com.ideaminer.model;

import java.util.List;

public record MethodFact(
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
        List<String> parameters,
        List<String> annotations,
        int cyclomaticComplexity,
        int beginLine,
        int endLine
) {
}
