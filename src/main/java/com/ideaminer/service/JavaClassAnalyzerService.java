package com.ideaminer.service;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.ideaminer.model.ClassFact;
import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
public class JavaClassAnalyzerService {

    public List<ClassFact> analyze(RepositoryRegistration repository, SourceFileIndexEntry sourceFile) {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        Path absolutePath = Path.of(repository.localPath()).resolve(sourceFile.path()).normalize();
        CompilationUnit compilationUnit;
        try {
            compilationUnit = StaticJavaParser.parse(absolutePath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse " + sourceFile.path() + ": " + e.getMessage(), e);
        }

        String packageName = compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString())
                .orElse("");

        List<ClassFact> facts = new ArrayList<>();
        compilationUnit.findAll(TypeDeclaration.class).stream()
                .filter(type -> type instanceof ClassOrInterfaceDeclaration
                        || type instanceof EnumDeclaration
                        || type instanceof RecordDeclaration)
                .sorted(Comparator.comparing(type -> type.getRange()
                        .map(range -> range.begin.line)
                        .orElse(Integer.MAX_VALUE)))
                .forEach(type -> facts.add(toFact(repository, sourceFile, packageName, type)));
        return facts;
    }

    private ClassFact toFact(RepositoryRegistration repository,
                             SourceFileIndexEntry sourceFile,
                             String packageName,
                             TypeDeclaration<?> type) {
        String className = type.getNameAsString();
        String classType = classType(type);
        int beginLine = type.getRange().map(range -> range.begin.line).orElse(0);
        int endLine = type.getRange().map(range -> range.end.line).orElse(0);
        List<String> annotations = type.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .sorted()
                .toList();

        return new ClassFact(
                stableClassId(repository.id(), sourceFile.path(), packageName, className),
                repository.id(),
                sourceFile.id(),
                repository.name(),
                className,
                packageName,
                sourceFile.path(),
                classType,
                annotations,
                summary(className, classType, annotations, type),
                complexity(type),
                beginLine,
                endLine
        );
    }

    private String classType(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration declaration) {
            if (declaration.isInterface()) {
                return "Interface";
            }
            if (hasAnnotation(type, "RestController") || hasAnnotation(type, "Controller")) {
                return "Controller";
            }
            if (hasAnnotation(type, "Service")) {
                return "Service";
            }
            if (hasAnnotation(type, "Repository")) {
                return "Repository";
            }
            if (hasAnnotation(type, "Entity") || hasAnnotation(type, "Table")) {
                return "Entity";
            }
            if (hasAnnotation(type, "Configuration")) {
                return "Configuration";
            }
            if (hasAnnotation(type, "Component")) {
                return "Component";
            }
            return "Class";
        }
        if (type instanceof EnumDeclaration) {
            return "Enum";
        }
        if (type instanceof RecordDeclaration) {
            return "Record";
        }
        return "Type";
    }

    private boolean hasAnnotation(TypeDeclaration<?> type, String annotationName) {
        return type.getAnnotationByName(annotationName).isPresent();
    }

    private String summary(String className, String classType, List<String> annotations, TypeDeclaration<?> type) {
        int methodCount = type.getMethods().size();
        String annotationSummary = annotations.isEmpty() ? "no annotations" : "annotations " + String.join(", ", annotations);
        return classType + " " + className + " with " + methodCount + " methods and " + annotationSummary + ".";
    }

    private int complexity(TypeDeclaration<?> type) {
        int methodBase = type.getMethods().size();
        return methodBase
                + type.findAll(IfStmt.class).size()
                + type.findAll(ForStmt.class).size()
                + type.findAll(ForEachStmt.class).size()
                + type.findAll(WhileStmt.class).size()
                + type.findAll(DoStmt.class).size()
                + type.findAll(SwitchEntry.class).size()
                + type.findAll(CatchClause.class).size();
    }

    private String stableClassId(String repositoryId, String filePath, String packageName, String className) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((repositoryId + ":" + filePath + ":" + packageName + ":" + className)
                    .getBytes(StandardCharsets.UTF_8));
            return "class_" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
