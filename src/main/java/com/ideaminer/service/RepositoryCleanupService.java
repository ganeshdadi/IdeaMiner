package com.ideaminer.service;

import com.ideaminer.model.RepositoryRegistration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RepositoryCleanupService {
    private final RepositoryRegistryService repositoryRegistryService;
    private final JdbcTemplate jdbcTemplate;

    public RepositoryCleanupService(RepositoryRegistryService repositoryRegistryService, JdbcTemplate jdbcTemplate) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String cleanup(String repositoryIdentifier) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        String repositoryId = repository.id();
        jdbcTemplate.update("DELETE FROM llm_runs WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM onboarding_runs WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM generated_reports WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM workspace_repositories WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM candidate_validations WHERE candidate_id IN (SELECT id FROM opportunity_candidates WHERE repository_id = ?)", repositoryId);
        jdbcTemplate.update("DELETE FROM review_feedback WHERE candidate_id IN (SELECT id FROM opportunity_candidates WHERE repository_id = ?)", repositoryId);
        jdbcTemplate.update("DELETE FROM redaction_audit WHERE source_type = 'evidence' AND source_id IN (SELECT id FROM opportunity_candidates WHERE repository_id = ?)", repositoryId);
        jdbcTemplate.update("DELETE FROM opportunity_candidates WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM domain_terms WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM database_access WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM scheduled_jobs WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM endpoints WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM code_edges WHERE source_id LIKE ? OR target_id LIKE ?", "%" + repositoryId + "%", "%" + repositoryId + "%");
        jdbcTemplate.update("DELETE FROM chunk_embeddings WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM code_chunks WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM role_inference WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM methods WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM classes WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM source_files WHERE repository_id = ?", repositoryId);
        return "Cleanup completed for repository " + repository.name() + " (" + repositoryId + ")";
    }

    public String hardDelete(String repositoryIdentifier) {
        RepositoryRegistration repository = repositoryRegistryService.resolve(repositoryIdentifier);
        cleanup(repositoryIdentifier);
        jdbcTemplate.update("DELETE FROM repositories WHERE id = ?", repository.id());
        return "Repository deleted completely: " + repository.name() + " (" + repository.id() + ")";
    }
}
