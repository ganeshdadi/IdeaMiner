package com.ideaminer.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Lazy
public class VectorStoreConfig {

    private static final String STORE_PATH = "ideaminer_vectors.json";

    @Bean
    @Lazy
    public EmbeddingModel embeddingModel() {
        // This runs locally in-process!
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    @Lazy
    public EmbeddingStore<TextSegment> embeddingStore() {
        Path path = Paths.get(STORE_PATH);
        if (Files.exists(path)) {
            return InMemoryEmbeddingStore.fromFile(path);
        } else {
            return new InMemoryEmbeddingStore<>();
        }
    }
}
