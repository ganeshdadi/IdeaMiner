package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JavaMethodAnalyzerServiceTest {

    @TempDir
    Path repositoryRoot;

    @Test
    void extractsConstructorsAndOverloadedMethodsWithStableIds() throws Exception {
        Path sourceFile = repositoryRoot.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package com.example;

                import org.springframework.transaction.annotation.Transactional;

                public class OrderService {
                    public OrderService() {
                    }

                    public OrderService(String region) {
                    }

                    @Transactional
                    public String approve(String id) {
                        if (id == null) {
                            return "missing";
                        }
                        return "approved";
                    }

                    public String approve(String id, int amount) {
                        while (amount > 0) {
                            amount--;
                        }
                        return id;
                    }
                }
                """);
        RepositoryRegistration repository = repository();
        SourceFileIndexEntry source = sourceFile();
        JavaMethodAnalyzerService service = new JavaMethodAnalyzerService();

        var firstRun = service.analyze(repository, source);
        var secondRun = service.analyze(repository, source);

        assertThat(firstRun).hasSize(4);
        assertThat(firstRun).extracting(method -> method.signature())
                .containsExactly(
                        "OrderService()",
                        "OrderService(String)",
                        "approve(String)",
                        "approve(String,int)"
                );
        assertThat(firstRun).extracting(method -> method.id())
                .containsExactlyElementsOf(secondRun.stream().map(method -> method.id()).toList());
        assertThat(firstRun)
                .filteredOn(method -> method.signature().equals("approve(String)"))
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.returnType()).isEqualTo("String");
                    assertThat(method.parameters()).containsExactly("String id");
                    assertThat(method.annotations()).containsExactly("Transactional");
                    assertThat(method.cyclomaticComplexity()).isEqualTo(2);
                    assertThat(method.beginLine()).isGreaterThan(0);
                    assertThat(method.endLine()).isGreaterThan(method.beginLine());
                });
    }

    private RepositoryRegistration repository() {
        return new RepositoryRegistration(
                "repo_abc",
                "sample",
                repositoryRoot.toString(),
                null,
                null,
                null,
                OffsetDateTime.now()
        );
    }

    private SourceFileIndexEntry sourceFile() {
        return new SourceFileIndexEntry(
                "file_abc",
                "repo_abc",
                "src/main/java/com/example/OrderService.java",
                "java"
        );
    }
}
