package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmEnrichmentService {
    private final RepositoryRegistryService repositoryRegistryService;
    private final FeaturePipelineService featurePipelineService;
    private final JdbcTemplate jdbcTemplate;

    public LlmEnrichmentService(RepositoryRegistryService repositoryRegistryService,
                                FeaturePipelineService featurePipelineService,
                                JdbcTemplate jdbcTemplate) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.featurePipelineService = featurePipelineService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String startValidateCandidates(String repositoryIdentifier) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        String runId = StableId.of("llm_run_", repository.id() + ":validate:" + System.currentTimeMillis());
        jdbcTemplate.update("INSERT INTO llm_runs (id, repository_id, job_type, status, started_at) VALUES (?, ?, 'validate-candidates', 'running', now())",
                runId, repository.id());
        validateAsync(runId, repository.id());
        return runId;
    }

    public String startLlmReport(String repositoryIdentifier) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        String runId = StableId.of("llm_run_", repository.id() + ":llm-report:" + System.currentTimeMillis());
        jdbcTemplate.update("INSERT INTO llm_runs (id, repository_id, job_type, status, started_at) VALUES (?, ?, 'llm-report', 'running', now())",
                runId, repository.id());
        reportAsync(runId, repository.id());
        return runId;
    }

    @Async("onboardingExecutor")
    public void validateAsync(String runId, String repositoryId) {
        try {
            writeStageStart(runId, "validate-candidates");
            List<String> candidateIds = jdbcTemplate.queryForList(
                    "SELECT id FROM opportunity_candidates WHERE repository_id = ? ORDER BY score DESC, created_at DESC",
                    String.class, repositoryId);
            for (String candidateId : candidateIds) {
                featurePipelineService.validate(candidateId);
            }
            writeStageDone(runId, "validate-candidates", "Validated " + candidateIds.size() + " candidates");
            jdbcTemplate.update("UPDATE llm_runs SET status = 'completed', ended_at = now() WHERE id = ?", runId);
        } catch (Exception exception) {
            writeStageFailed(runId, "validate-candidates", exception.getMessage());
            jdbcTemplate.update("UPDATE llm_runs SET status = 'failed', ended_at = now() WHERE id = ?", runId);
        }
    }

    @Async("onboardingExecutor")
    public void reportAsync(String runId, String repositoryId) {
        try {
            writeStageStart(runId, "generate-llm-report");
            featurePipelineService.report(repositoryId, true);
            writeStageDone(runId, "generate-llm-report", "LLM report generated");
            jdbcTemplate.update("UPDATE llm_runs SET status = 'completed', ended_at = now() WHERE id = ?", runId);
        } catch (Exception exception) {
            writeStageFailed(runId, "generate-llm-report", exception.getMessage());
            jdbcTemplate.update("UPDATE llm_runs SET status = 'failed', ended_at = now() WHERE id = ?", runId);
        }
    }

    public Map<String, Object> runStatus(String runId) {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT id, repository_id, job_type, status, started_at, ended_at FROM llm_runs WHERE id = ?", runId);
        if (runs.isEmpty()) {
            return Map.of("status", "not-found", "runId", runId);
        }
        Map<String, Object> run = new LinkedHashMap<>(runs.get(0));
        List<Map<String, Object>> stages = jdbcTemplate.queryForList("""
                SELECT stage_name, status, details, started_at, ended_at
                FROM llm_run_stages
                WHERE run_id = ?
                ORDER BY started_at
                """, runId);
        run.put("stages", stages);
        return run;
    }

    private void writeStageStart(String runId, String stage) {
        String id = StableId.of("llm_stage_", runId + ":" + stage);
        jdbcTemplate.update("INSERT INTO llm_run_stages (id, run_id, stage_name, status, started_at) VALUES (?, ?, ?, 'running', now())",
                id, runId, stage);
    }

    private void writeStageDone(String runId, String stage, String details) {
        jdbcTemplate.update("UPDATE llm_run_stages SET status = 'completed', details = ?, ended_at = now() WHERE run_id = ? AND stage_name = ?",
                details, runId, stage);
    }

    private void writeStageFailed(String runId, String stage, String details) {
        jdbcTemplate.update("UPDATE llm_run_stages SET status = 'failed', details = ?, ended_at = now() WHERE run_id = ? AND stage_name = ?",
                details, runId, stage);
    }
}
