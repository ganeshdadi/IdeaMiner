CREATE TABLE onboarding_runs (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ
);

CREATE TABLE onboarding_run_stages (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES onboarding_runs(id) ON DELETE CASCADE,
    stage_name TEXT NOT NULL,
    status TEXT NOT NULL,
    details TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    UNIQUE (run_id, stage_name)
);

CREATE INDEX idx_onboarding_runs_repository_id ON onboarding_runs(repository_id);
CREATE INDEX idx_onboarding_run_stages_run_id ON onboarding_run_stages(run_id);
