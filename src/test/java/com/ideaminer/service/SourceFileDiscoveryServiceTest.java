package com.ideaminer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFileDiscoveryServiceTest {

    @TempDir
    Path repositoryRoot;

    @Test
    void discoversOnlyProductionJavaFiles() throws Exception {
        write("src/main/java/com/example/OrderService.java", "class OrderService {}\n");
        write("src/main/java/com/example/Order.java", "class Order {}\n");
        write("src/main/java/com/example/config/AlsoIncluded.java", "class AlsoIncluded {}\n");
        write("src/test/java/com/example/OrderServiceTest.java", "class OrderServiceTest {}\n");
        write("test/Fixture.java", "class Fixture {}\n");
        write("tests/Fixture.java", "class Fixture {}\n");
        write("__tests__/Fixture.java", "class Fixture {}\n");
        write("src/integration/java/com/example/NotIncluded.java", "class NotIncluded {}\n");
        write("src/main/kotlin/com/example/OrderService.kt", "class OrderService\n");
        write("src/main/resources/application.properties", "name=value\n");
        write("build/generated/Generated.java", "class Generated {}\n");
        write("build.gradle", "plugins {}\n");
        write("README.md", "# Sample\n");

        SourceFileDiscoveryService service = new SourceFileDiscoveryService();

        assertThat(service.discover(repositoryRoot))
                .extracting(file -> file.relativePath())
                .containsExactly(
                        "src/main/java/com/example/Order.java",
                        "src/main/java/com/example/OrderService.java",
                        "src/main/java/com/example/config/AlsoIncluded.java"
                );
    }

    @Test
    void excludesJavaOutsideSrcMainJava() throws Exception {
        write("src/main/java/com/example/OrderService.java", "class OrderService {}\n");
        write("src/legacy/java/com/example/Legacy.java", "class Legacy {}\n");
        write("generated-sources/com/example/Generated.java", "class Generated {}\n");
        SourceFileDiscoveryService service = new SourceFileDiscoveryService();

        assertThat(service.discover(repositoryRoot))
                .extracting(file -> file.relativePath())
                .containsExactly("src/main/java/com/example/OrderService.java");
    }

    private void write(String relativePath, String content) throws Exception {
        Path path = repositoryRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
