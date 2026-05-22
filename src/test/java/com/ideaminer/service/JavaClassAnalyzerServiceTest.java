package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JavaClassAnalyzerServiceTest {

    @TempDir
    Path repositoryRoot;

    @Test
    void extractsClassFactsFromRepresentativeJavaFile() throws Exception {
        Path sourceFile = repositoryRoot.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package com.example;

                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {
                    public void approve(int amount) {
                        if (amount > 100) {
                            for (int i = 0; i < amount; i++) {
                                System.out.println(i);
                            }
                        }
                    }
                }

                interface OrderPort {
                    void send();
                }

                record OrderId(String value) {
                }

                enum OrderStatus {
                    NEW, APPROVED
                }
                """);
        RepositoryRegistration repository = new RepositoryRegistration(
                "repo_abc",
                "sample",
                repositoryRoot.toString(),
                null,
                null,
                null,
                OffsetDateTime.now()
        );
        SourceFileIndexEntry source = new SourceFileIndexEntry(
                "file_abc",
                "repo_abc",
                "src/main/java/com/example/OrderService.java",
                "java"
        );

        var facts = new JavaClassAnalyzerService().analyze(repository, source);

        assertThat(facts).hasSize(4);
        assertThat(facts)
                .filteredOn(fact -> fact.className().equals("OrderService"))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.id()).startsWith("class_");
                    assertThat(fact.packageName()).isEqualTo("com.example");
                    assertThat(fact.classType()).isEqualTo("Service");
                    assertThat(fact.annotations()).containsExactly("Service");
                    assertThat(fact.filePath()).isEqualTo("src/main/java/com/example/OrderService.java");
                    assertThat(fact.cyclomaticComplexity()).isGreaterThanOrEqualTo(3);
                    assertThat(fact.beginLine()).isGreaterThan(0);
                    assertThat(fact.endLine()).isGreaterThan(fact.beginLine());
                });
        assertThat(facts).extracting(fact -> fact.classType())
                .contains("Interface", "Record", "Enum");
    }

    @Test
    void producesStableClassIdsAcrossRuns() throws Exception {
        Path sourceFile = repositoryRoot.resolve("src/main/java/com/example/Order.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "package com.example; public class Order {}\n");
        RepositoryRegistration repository = new RepositoryRegistration(
                "repo_abc",
                "sample",
                repositoryRoot.toString(),
                null,
                null,
                null,
                OffsetDateTime.now()
        );
        SourceFileIndexEntry source = new SourceFileIndexEntry(
                "file_abc",
                "repo_abc",
                "src/main/java/com/example/Order.java",
                "java"
        );
        JavaClassAnalyzerService service = new JavaClassAnalyzerService();

        String firstId = service.analyze(repository, source).get(0).id();
        String secondId = service.analyze(repository, source).get(0).id();

        assertThat(secondId).isEqualTo(firstId);
    }
}
