package com.ideaminer.util;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.ideaminer.model.ClassMetadata;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JavaParserUtil {

    /**
     * Parses a Java source file and extracts metadata for each top‑level class.
     * The parser is configured to support Java 17 language features (text blocks, etc.).
     */
    public static List<ClassMetadata> parseFile(File file, String repoName) {
        // Ensure the parser can handle Java 17 constructs
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        List<ClassMetadata> metadataList = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);

            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("default");

            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
            for (ClassOrInterfaceDeclaration cls : classes) {
                if (cls.isInterface()) continue;

                ClassMetadata meta = new ClassMetadata();
                meta.setId(UUID.randomUUID().toString());
                meta.setRepoName(repoName);
                meta.setClassName(cls.getNameAsString());
                meta.setPackageName(packageName);
                meta.setFilePath(file.getAbsolutePath());
                meta.setSourceCode(cls.toString());

                meta.setClassType(determineClassType(cls));
                meta.setCyclomaticComplexity(calculateComplexity(cls));
                meta.setSummary(generateBasicSummary(cls, meta.getClassType()));

                metadataList.add(meta);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
        return metadataList;
    }

    private static String determineClassType(ClassOrInterfaceDeclaration cls) {
        if (cls.getAnnotationByName("RestController").isPresent() || cls.getAnnotationByName("Controller").isPresent()) {
            return "Controller";
        }
        if (cls.getAnnotationByName("Service").isPresent()) {
            return "Service";
        }
        if (cls.getAnnotationByName("Entity").isPresent() || cls.getAnnotationByName("Table").isPresent()) {
            return "Entity";
        }
        if (cls.getAnnotationByName("Repository").isPresent()) {
            return "Repository";
        }
        if (cls.getAnnotationByName("Component").isPresent()) {
            boolean isBatch = cls.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().contains("ItemReader")
                            || t.getNameAsString().contains("ItemWriter")
                            || t.getNameAsString().contains("Tasklet"));
            return isBatch ? "BatchJob" : "Component";
        }
        return "Utility/Other";
    }

    private static int calculateComplexity(ClassOrInterfaceDeclaration cls) {
        int complexity = 0;
        List<MethodDeclaration> methods = cls.findAll(MethodDeclaration.class);
        for (MethodDeclaration method : methods) {
            complexity++; // base for method
            complexity += method.findAll(IfStmt.class).size();
            complexity += method.findAll(SwitchStmt.class).size();
        }
        return complexity;
    }

    private static String generateBasicSummary(ClassOrInterfaceDeclaration cls, String type) {
        List<MethodDeclaration> methods = cls.findAll(MethodDeclaration.class);
        StringBuilder sb = new StringBuilder();
        sb.append("A Spring ").append(type).append(" named ")
                .append(cls.getNameAsString()).append(" containing ")
                .append(methods.size()).append(" methods. ");
        if (!methods.isEmpty()) {
            sb.append("Key methods include: ");
            for (int i = 0; i < Math.min(3, methods.size()); i++) {
                sb.append(methods.get(i).getNameAsString()).append(", ");
            }
        }
        return sb.toString();
    }
}
