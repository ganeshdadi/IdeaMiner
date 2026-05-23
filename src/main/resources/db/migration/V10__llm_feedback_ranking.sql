ALTER TABLE llm_opportunity_candidates
    ADD COLUMN ranking_score NUMERIC(8, 3) NOT NULL DEFAULT 0;

CREATE TABLE llm_opportunity_feedback (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES llm_opportunity_candidates(id) ON DELETE CASCADE,
    decision TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_llm_feedback_candidate ON llm_opportunity_feedback(candidate_id);
