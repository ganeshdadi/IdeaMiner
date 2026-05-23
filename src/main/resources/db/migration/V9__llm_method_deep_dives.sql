CREATE TABLE llm_method_deep_dives (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES llm_discovery_runs(id) ON DELETE CASCADE,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    class_id TEXT REFERENCES classes(id) ON DELETE CASCADE,
    method_id TEXT REFERENCES methods(id) ON DELETE CASCADE,
    trigger_reason TEXT NOT NULL,
    summary_json JSONB NOT NULL,
    status TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    error_details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (run_id, method_id, trigger_reason)
);

CREATE INDEX idx_llm_method_deep_dives_run ON llm_method_deep_dives(run_id);
CREATE INDEX idx_llm_method_deep_dives_repo ON llm_method_deep_dives(repository_id);
