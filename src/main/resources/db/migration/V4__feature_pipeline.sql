CREATE TABLE endpoints (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    class_id TEXT REFERENCES classes(id) ON DELETE CASCADE,
    method_id TEXT REFERENCES methods(id) ON DELETE CASCADE,
    http_method TEXT NOT NULL,
    route TEXT NOT NULL,
    class_name TEXT,
    method_name TEXT,
    customer_facing BOOLEAN NOT NULL DEFAULT TRUE,
    source_span JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, http_method, route, method_id)
);

CREATE TABLE scheduled_jobs (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    class_id TEXT REFERENCES classes(id) ON DELETE CASCADE,
    method_id TEXT REFERENCES methods(id) ON DELETE CASCADE,
    job_type TEXT NOT NULL,
    name TEXT NOT NULL,
    schedule_expression TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, job_type, name, class_id, method_id)
);

CREATE TABLE database_access (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    class_id TEXT REFERENCES classes(id) ON DELETE CASCADE,
    method_id TEXT REFERENCES methods(id) ON DELETE CASCADE,
    access_type TEXT NOT NULL,
    target_name TEXT,
    operation_type TEXT NOT NULL DEFAULT 'unknown',
    query_text TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, access_type, target_name, class_id, method_id)
);

CREATE TABLE domain_terms (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    term TEXT NOT NULL,
    weight INTEGER NOT NULL DEFAULT 1,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, source_type, source_id, term)
);

CREATE TABLE opportunity_candidates (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    detector TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    score NUMERIC(6, 3) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'detected',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, detector, title)
);

CREATE TABLE opportunity_evidence (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES opportunity_candidates(id) ON DELETE CASCADE,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    description TEXT NOT NULL,
    file_path TEXT,
    source_span JSONB,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (candidate_id, source_type, source_id, description)
);

CREATE TABLE generated_reports (
    id TEXT PRIMARY KEY,
    repository_id TEXT REFERENCES repositories(id) ON DELETE CASCADE,
    workspace_id TEXT,
    report_type TEXT NOT NULL,
    file_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE code_chunks (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    chunk_type TEXT NOT NULL,
    text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, source_type, source_id, chunk_type)
);

CREATE TABLE chunk_embeddings (
    chunk_id TEXT PRIMARY KEY REFERENCES code_chunks(id) ON DELETE CASCADE,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    embedding vector(16) NOT NULL,
    provider TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspaces (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace_repositories (
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, repository_id)
);

CREATE TABLE review_feedback (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES opportunity_candidates(id) ON DELETE CASCADE,
    state TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE candidate_validations (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES opportunity_candidates(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    verdict TEXT NOT NULL,
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE redaction_audit (
    id TEXT PRIMARY KEY,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    redaction_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_endpoints_repository_id ON endpoints(repository_id);
CREATE INDEX idx_scheduled_jobs_repository_id ON scheduled_jobs(repository_id);
CREATE INDEX idx_database_access_repository_id ON database_access(repository_id);
CREATE INDEX idx_domain_terms_term ON domain_terms(term);
CREATE INDEX idx_candidates_repository_id ON opportunity_candidates(repository_id);
CREATE INDEX idx_evidence_candidate_id ON opportunity_evidence(candidate_id);
CREATE INDEX idx_chunks_repository_id ON code_chunks(repository_id);
CREATE INDEX idx_chunk_embeddings_vector ON chunk_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);
