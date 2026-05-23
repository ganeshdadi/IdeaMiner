CREATE TABLE llm_discovery_runs (
    id TEXT PRIMARY KEY,
    scope_type TEXT NOT NULL,
    repository_id TEXT REFERENCES repositories(id) ON DELETE CASCADE,
    workspace_id TEXT REFERENCES workspaces(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    response_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_details TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ
);

CREATE TABLE llm_discovery_run_stages (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES llm_discovery_runs(id) ON DELETE CASCADE,
    stage_name TEXT NOT NULL,
    status TEXT NOT NULL,
    details TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    UNIQUE (run_id, stage_name)
);

CREATE TABLE llm_capability_summaries (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES llm_discovery_runs(id) ON DELETE CASCADE,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    class_id TEXT REFERENCES classes(id) ON DELETE CASCADE,
    summary_json JSONB NOT NULL,
    status TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    error_details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (run_id, class_id)
);

CREATE TABLE llm_workflow_maps (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES llm_discovery_runs(id) ON DELETE CASCADE,
    scope_type TEXT NOT NULL,
    repository_id TEXT REFERENCES repositories(id) ON DELETE CASCADE,
    workspace_id TEXT REFERENCES workspaces(id) ON DELETE CASCADE,
    workflow_name TEXT NOT NULL,
    workflow_json JSONB NOT NULL,
    status TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    error_details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE llm_opportunity_candidates (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES llm_discovery_runs(id) ON DELETE CASCADE,
    scope_type TEXT NOT NULL,
    repository_id TEXT REFERENCES repositories(id) ON DELETE CASCADE,
    workspace_id TEXT REFERENCES workspaces(id) ON DELETE CASCADE,
    candidate_type TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    candidate_json JSONB NOT NULL,
    status TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    error_details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE llm_opportunity_reviews (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES llm_opportunity_candidates(id) ON DELETE CASCADE,
    review_type TEXT NOT NULL,
    review_json JSONB NOT NULL,
    status TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    error_details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_llm_discovery_runs_repo ON llm_discovery_runs(repository_id);
CREATE INDEX idx_llm_discovery_runs_workspace ON llm_discovery_runs(workspace_id);
CREATE INDEX idx_llm_discovery_stages_run ON llm_discovery_run_stages(run_id);
CREATE INDEX idx_llm_capability_run ON llm_capability_summaries(run_id);
CREATE INDEX idx_llm_workflow_run ON llm_workflow_maps(run_id);
CREATE INDEX idx_llm_opportunity_run ON llm_opportunity_candidates(run_id);
