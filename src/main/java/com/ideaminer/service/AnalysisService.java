package com.ideaminer.service;

import com.ideaminer.model.ClassMetadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private final JdbcTemplate jdbcTemplate;
    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public AnalysisService(JdbcTemplate jdbcTemplate, ChatLanguageModel chatLanguageModel, 
                           EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    public void runAnalysis() {
        System.out.println("Starting AI Opportunity Discovery Analysis...");

        // 1. Map Phase: Extract high-level context from SQLite
        String mapPrompt = buildMapPrompt();
        System.out.println("Brainstorming initial opportunities via LLM...");
        String initialIdeas = chatLanguageModel.generate(mapPrompt);
        
        System.out.println("\n--- Initial Ideas ---");
        System.out.println(initialIdeas);
        System.out.println("---------------------\n");

        // 2. Deep Dive Phase: Validate ideas using Vector Store
        System.out.println("Validating ideas against actual source code (Vector Search)...");
        String deepDivePrompt = buildDeepDivePrompt(initialIdeas);
        String finalReport = chatLanguageModel.generate(deepDivePrompt);

        // 3. Reporting Phase
        saveReport(finalReport);
    }

    private String buildMapPrompt() {
        // Query for potentially interesting classes: high complexity or batch jobs
        String sql = "SELECT class_name, class_type, summary, cyclomatic_complexity FROM classes " +
                     "WHERE cyclomatic_complexity > 5 OR class_type IN ('BatchJob', 'Service') " +
                     "ORDER BY cyclomatic_complexity DESC LIMIT 50";
                     
        List<String> classSummaries = jdbcTemplate.query(sql, (rs, rowNum) -> 
            "- " + rs.getString("class_name") + " (" + rs.getString("class_type") + 
            ", Complexity: " + rs.getInt("cyclomatic_complexity") + "): " + rs.getString("summary")
        );

        String context = String.join("\n", classSummaries);

        return """
               You are an expert AI Solutions Architect in the Banking industry.
               I am providing you with a list of complex Java classes extracted from our repositories.
               
               Class Summaries:
               %s
               
               Based ONLY on these class summaries, identify 3 to 5 potential "AI/ML Opportunities" or "Workflow Automation Opportunities" that would directly improve the Customer Experience.
               Look for things like manual rule-based decisioning, batch jobs that could be real-time, or dispute resolution processes.
               
               Return the ideas as a bulleted list.
               """.formatted(context);
    }

    private String buildDeepDivePrompt(String initialIdeas) {
        // Perform a semantic search on the vector store using the initial ideas
        List<EmbeddingMatch<TextSegment>> relevantCode = embeddingStore.findRelevant(
            embeddingModel.embed(initialIdeas).content(), 10, 0.7
        );

        String codeContext = relevantCode.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

        return """
               You previously generated these initial AI opportunities:
               %s
               
               I have searched the codebase and found the following actual implementation details that might be related:
               %s
               
               Synthesize this information into a final, structured Markdown report.
               For each validated opportunity, create an "Opportunity Card" with the following headers:
               - **Opportunity Title**
               - **Customer Benefit**
               - **Current State (Code References)**
               - **Proposed AI/ML Solution**
               
               Only include opportunities that seem plausible based on the code context.
               """.formatted(initialIdeas, codeContext);
    }
    
    private void saveReport(String reportContent) {
        try {
            String fileName = "AI_Opportunity_Report.md";
            Files.writeString(Paths.get(fileName), reportContent);
            System.out.println("Final report saved to: " + fileName);
        } catch (IOException e) {
            System.err.println("Failed to write report: " + e.getMessage());
        }
    }
}
