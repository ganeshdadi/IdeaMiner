package com.ideaminer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void hashesFileContentDeterministically() throws Exception {
        Path sourceFile = tempDir.resolve("OrderService.java");
        Files.writeString(sourceFile, "class OrderService {}\n");
        ContentHashService service = new ContentHashService();

        String firstHash = service.sha256(sourceFile);
        String secondHash = service.sha256(sourceFile);

        assertThat(firstHash).hasSize(64);
        assertThat(secondHash).isEqualTo(firstHash);
    }

    @Test
    void changesHashWhenContentChanges() throws Exception {
        Path sourceFile = tempDir.resolve("OrderService.java");
        Files.writeString(sourceFile, "class OrderService {}\n");
        ContentHashService service = new ContentHashService();
        String originalHash = service.sha256(sourceFile);

        Files.writeString(sourceFile, "class OrderService { void approve() {} }\n");

        assertThat(service.sha256(sourceFile)).isNotEqualTo(originalHash);
    }
}
