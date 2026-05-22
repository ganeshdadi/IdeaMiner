package com.ideaminer.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.regex.Pattern;

public class SecretRedactionService {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*['\\\"]?[^\\s'\\\"]+"),
            Pattern.compile("sk-[A-Za-z0-9_-]{12,}"),
            Pattern.compile("(?i)jdbc:[^\\s]+"),
            Pattern.compile("https?://[A-Za-z0-9._-]*internal[A-Za-z0-9._:/-]*")
    );

    private final JdbcTemplate jdbcTemplate;

    public SecretRedactionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String redact(String sourceType, String sourceId, String value) {
        String redacted = value;
        int count = 0;
        for (Pattern pattern : PATTERNS) {
            java.util.regex.Matcher matcher = pattern.matcher(redacted);
            String next = matcher.replaceAll("[REDACTED]");
            if (!next.equals(redacted)) {
                count++;
            }
            redacted = next;
        }
        if (count > 0 && jdbcTemplate != null) {
            jdbcTemplate.update("INSERT INTO redaction_audit (id, source_type, source_id, redaction_count) VALUES (?, ?, ?, ?)",
                    StableId.of("redaction_", sourceType + ":" + sourceId + ":" + System.nanoTime()), sourceType, sourceId, count);
        }
        return redacted;
    }
}
