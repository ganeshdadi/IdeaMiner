package com.ideaminer.service;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
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
import com.ideaminer.model.MethodFact;
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
public class JavaMethodAnalyzerService {

    public List<MethodFact> analyze(RepositoryRegistration repository, SourceFileIndexEntry sourceFile) {
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

        List<MethodFact> facts = new ArrayList<>();
        compilationUnit.findAll(TypeDeclaration.class).stream()
                .filter(type -> type instanceof ClassOrInterfaceDeclaration
                        || type instanceof EnumDeclaration
                        || type instanceof RecordDeclaration)
                .sorted(Comparator.comparing(type -> type.getRange()
                        .map(range -> range.begin.line)
                        .orElse(Integer.MAX_VALUE)))
                .forEach(type -> addMethodFacts(repository, sourceFile, packageName, type, facts));
        return facts;
    }

    private void addMethodFacts(RepositoryRegistration repository,
                                SourceFileIndexEntry sourceFile,
                                String packageName,
                                TypeDeclaration<?> type,
                                List<MethodFact> facts) {
        String className = type.getNameAsString();
        String classId = stableClassId(repository.id(), sourceFile.path(), packageName, className);

        for (ConstructorDeclaration constructor : type.getConstructors()) {
            facts.add(toFact(repository, sourceFile, packageName, classId, className, constructor, className));
        }
        for (MethodDeclaration method : type.getMethods()) {
            facts.add(toFact(repository, sourceFile, packageName, classId, className, method, method.getTypeAsString()));
        }
    }

    private MethodFact toFact(RepositoryRegistration repository,
                              SourceFileIndexEntry sourceFile,
                              String packageName,
                              String classId,
                              String className,
                              CallableDeclaration<?> callable,
                              String returnType) {
        String methodName = callable.getNameAsString();
        String signature = signature(methodName, callable.getParameters());
        int beginLine = callable.getRange().map(range -> range.begin.line).orElse(0);
        int endLine = callable.getRange().map(range -> range.end.line).orElse(0);
        List<String> parameters = callable.getParameters().stream()
                .map(parameter -> parameter.getTypeAsString() + " " + parameter.getNameAsString())
                .toList();
        List<String> annotations = callable.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .sorted()
                .toList();

        return new MethodFact(
                stableMethodId(repository.id(), classId, methodName, signature),
                repository.id(),
                classId,
                sourceFile.id(),
                className,
                packageName,
                sourceFile.path(),
                methodName,
                signature,
                returnType,
                parameters,
                annotations,
                complexity(callable),
                beginLine,
                endLine
        );
    }

    private String signature(String methodName, NodeList<Parameter> parameters) {
        String parameterTypes = parameters.stream()
                .map(Parameter::getTypeAsString)
                .collect(java.util.stream.Collectors.joining(","));
        return methodName + "(" + parameterTypes + ")";
    }

    private int complexity(CallableDeclaration<?> callable) {
        return 1
                + callable.findAll(IfStmt.class).size()
                + callable.findAll(ForStmt.class).size()
                + callable.findAll(ForEachStmt.class).size()
                + callable.findAll(WhileStmt.class).size()
                + callable.findAll(DoStmt.class).size()
                + callable.findAll(SwitchEntry.class).size()
                + callable.findAll(CatchClause.class).size();
    }

    private String stableClassId(String repositoryId, String filePath, String packageName, String className) {
        return stableId("class_", repositoryId + ":" + filePath + ":" + packageName + ":" + className);
    }

    private String stableMethodId(String repositoryId, String classId, String methodName, String signature) {
        return stableId("method_", repositoryId + ":" + classId + ":" + methodName + ":" + signature);
    }

    private String stableId(String prefix, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
