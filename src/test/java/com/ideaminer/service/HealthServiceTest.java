package com.ideaminer.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    @Test
    void printsDatabaseAndPgvectorHealth() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT current_database()", String.class)).thenReturn("ideaminer");
        when(jdbcTemplate.queryForObject("SHOW server_version", String.class)).thenReturn("16.3");
        when(jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String.class
        )).thenReturn("0.7.4");
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class
        )).thenReturn(1);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output));
        try {
            new HealthService(jdbcTemplate).printHealthReport();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString())
                .contains("[Health] Database connection: OK")
                .contains("[Health] Database name: ideaminer")
                .contains("[Health] PostgreSQL version: 16.3")
                .contains("[Health] pgvector extension: OK (0.7.4)")
                .contains("[Health] Applied migrations: 1");
    }
}
