package com.ideaminer;

import com.ideaminer.service.AnalysisService;
import com.ideaminer.service.IngestionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Scanner;

@SpringBootApplication
public class IdeaMinerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdeaMinerApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(JdbcTemplate jdbcTemplate, IngestionService ingestionService, AnalysisService analysisService) {
        return args -> {
            System.out.println("=========================================");
            System.out.println("   IdeaMiner: AI Opportunity Discovery   ");
            System.out.println("=========================================");
            
            // Initialize SQLite DB if not exists
            initializeDatabase(jdbcTemplate);

            if (args.length > 0) {
                String command = args[0];
                if ("index".equalsIgnoreCase(command)) {
                    String path = args.length > 1 ? args[1] : ".";
                    System.out.println("Indexing repository at: " + path);
                    ingestionService.ingestRepository(path);
                } else if ("analyze".equalsIgnoreCase(command)) {
                    System.out.println("Running AI Analysis...");
                    analysisService.runAnalysis();
                } else {
                    System.out.println("Unknown command: " + command);
                    printUsage();
                }
            } else {
                interactiveMode();
            }
        };
    }

    private void initializeDatabase(JdbcTemplate jdbcTemplate) {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS classes (
                id TEXT PRIMARY KEY,
                repo_name TEXT,
                class_name TEXT,
                package_name TEXT,
                file_path TEXT,
                class_type TEXT, -- e.g., Controller, Service, Entity, Batch
                summary TEXT,
                cyclomatic_complexity INTEGER
            );
        """;
        jdbcTemplate.execute(createTableSql);
        System.out.println("[System] SQLite Metadata DB initialized.");
    }

    private void interactiveMode() {
        System.out.println("Starting in interactive mode. Type 'help' for commands.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("ideaminer> ");
            String input = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                break;
            }
            if ("help".equalsIgnoreCase(input)) {
                printUsage();
            } else {
                System.out.println("To be implemented: " + input);
            }
        }
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar ideaminer.jar index <path/to/repo>   # Index a repository");
        System.out.println("  java -jar ideaminer.jar analyze                # Run analysis over indexed repos");
    }
}
