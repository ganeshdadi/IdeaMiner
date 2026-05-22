package com.ideaminer.service;

import com.ideaminer.model.ClassMetadata;
import com.ideaminer.util.JavaParserUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
@Lazy
public class IngestionService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public IngestionService(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public void ingestRepository(String repoPath) {
        Path startPath = Paths.get(repoPath);
        String repoName = startPath.getFileName().toString();
        
        System.out.println("Starting ingestion for repo: " + repoName);

        try (Stream<Path> paths = Files.walk(startPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> processFile(p.toFile(), repoName));
                 
            // Persist the vector store to disk
            if (embeddingStore instanceof InMemoryEmbeddingStore) {
                ((InMemoryEmbeddingStore<TextSegment>) embeddingStore).serializeToFile("ideaminer_vectors.json");
                System.out.println("Vector store saved to disk.");
            }
            
            System.out.println("Ingestion completed for " + repoName);
        } catch (IOException e) {
            System.err.println("Error walking repository path: " + e.getMessage());
        }
    }

    private void processFile(File file, String repoName) {
        System.out.println("  Parsing: " + file.getName());
        List<ClassMetadata> metadataList = JavaParserUtil.parseFile(file, repoName);
        
        for (ClassMetadata meta : metadataList) {
            // 1. Save to SQLite
            saveToSqlite(meta);
            
            // 2. Save to Vector Store
            saveToVectorStore(meta);
        }
    }

    private void saveToSqlite(ClassMetadata meta) {
        String sql = "INSERT INTO classes (id, repo_name, class_name, package_name, file_path, class_type, summary, cyclomatic_complexity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, 
            meta.getId(), 
            meta.getRepoName(), 
            meta.getClassName(), 
            meta.getPackageName(), 
            meta.getFilePath(), 
            meta.getClassType(), 
            meta.getSummary(), 
            meta.getCyclomaticComplexity()
        );
    }

    private void saveToVectorStore(ClassMetadata meta) {
        // We embed a combination of the summary and the source code
        String textToEmbed = "Class: " + meta.getClassName() + "\n" +
                             "Type: " + meta.getClassType() + "\n" +
                             "Summary: " + meta.getSummary() + "\n" +
                             "Code:\n" + meta.getSourceCode();
                             
        // Max token limit for minilm is ~512 tokens. For large classes, this might truncate.
        // In a production system, we'd chunk this intelligently. For now, we embed it directly.
        TextSegment segment = TextSegment.from(textToEmbed, Metadata.from("id", meta.getId())
                                                                    .put("className", meta.getClassName())
                                                                    .put("repo", meta.getRepoName()));
                                                                    
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);
    }
}
