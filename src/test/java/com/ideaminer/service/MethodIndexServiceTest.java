package com.ideaminer.service;

import com.ideaminer.model.MethodFact;
import com.ideaminer.model.RepositoryRegistration;
import com.ideaminer.model.SourceFileIndexEntry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodIndexServiceTest {

    @Test
    void indexesValidFilesAndContinuesWhenAFileFailsToParse() {
        RepositoryRegistryService registryService = mock(RepositoryRegistryService.class);
        ClassIndexService classIndexService = mock(ClassIndexService.class);
        RepositoryRegistration repository = new RepositoryRegistration(
                "repo_abc",
                "sample",
                "/tmp/sample",
                null,
                null,
                null,
                OffsetDateTime.now()
        );
        when(registryService.resolve("sample")).thenReturn(repository);

        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of(
                new SourceFileIndexEntry("file_good", "repo_abc", "src/main/java/Good.java", "java"),
                new SourceFileIndexEntry("file_bad", "repo_abc", "src/main/java/Bad.java", "java")
        ));
        JavaMethodAnalyzerService analyzerService = new JavaMethodAnalyzerService() {
            @Override
            public List<MethodFact> analyze(RepositoryRegistration repository, SourceFileIndexEntry sourceFile) {
                if (sourceFile.id().equals("file_bad")) {
                    throw new IllegalArgumentException("parse failure");
                }
                return List.of(new MethodFact(
                        "method_good",
                        "repo_abc",
                        "class_good",
                        "file_good",
                        "Good",
                        "com.example",
                        "src/main/java/Good.java",
                        "approve",
                        "approve(String)",
                        "String",
                        List.of("String id"),
                        List.of("Transactional"),
                        3,
                        10,
                        20
                ));
            }
        };

        MethodIndexService service = new MethodIndexService(registryService, classIndexService, analyzerService, jdbcTemplate);

        var summary = service.index("sample");

        assertThat(summary.filesScanned()).isEqualTo(2);
        assertThat(summary.filesParsed()).isEqualTo(1);
        assertThat(summary.filesFailed()).isEqualTo(1);
        assertThat(summary.methodsIndexed()).isEqualTo(1);
        assertThat(jdbcTemplate.updateSql).singleElement().satisfies(sql ->
                assertThat(sql).contains("INSERT INTO methods", "ON CONFLICT (id) DO UPDATE"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<SourceFileIndexEntry> sourceFiles;
        private final List<String> updateSql = new ArrayList<>();

        private RecordingJdbcTemplate(List<SourceFileIndexEntry> sourceFiles) {
            this.sourceFiles = sourceFiles;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) sourceFiles;
        }

        @Override
        public int update(String sql, Object... args) {
            updateSql.add(sql);
            return 1;
        }
    }
}
