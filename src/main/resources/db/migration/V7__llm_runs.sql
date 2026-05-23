CREATE TABLE llm_runs (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ
);

CREATE TABLE llm_run_stages (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES llm_runs(id) ON DELETE CASCADE,
    stage_name TEXT NOT NULL,
    status TEXT NOT NULL,
    details TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    UNIQUE (run_id, stage_name)
);

CREATE INDEX idx_llm_runs_repository_id ON llm_runs(repository_id);
CREATE INDEX idx_llm_run_stages_run_id ON llm_run_stages(run_id);
