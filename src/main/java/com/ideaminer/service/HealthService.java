package com.ideaminer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void printHealthReport() {
        String databaseName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        String postgresVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        String pgvectorVersion = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String.class
        );
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class
        );

        System.out.println("[Health] Database connection: OK");
        System.out.println("[Health] Database name: " + databaseName);
        System.out.println("[Health] PostgreSQL version: " + postgresVersion);
        System.out.println("[Health] pgvector extension: OK (" + pgvectorVersion + ")");
        System.out.println("[Health] Applied migrations: " + migrationCount);
    }
}
