CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE repositories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    remote_url TEXT,
    branch TEXT,
    commit_sha TEXT,
    indexed_at TIMESTAMPTZ
);

CREATE TABLE source_files (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    language TEXT NOT NULL,
    content_hash TEXT,
    last_indexed_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (repository_id, path)
);

CREATE TABLE classes (
    id TEXT PRIMARY KEY,
    repository_id TEXT REFERENCES repositories(id) ON DELETE CASCADE,
    file_id TEXT REFERENCES source_files(id) ON DELETE CASCADE,
    repo_name TEXT,
    class_name TEXT NOT NULL,
    package_name TEXT,
    file_path TEXT,
    class_type TEXT,
    annotations JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary TEXT,
    cyclomatic_complexity INTEGER NOT NULL DEFAULT 0,
    source_span JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE code_edges (
    id TEXT PRIMARY KEY,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    edge_type TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL DEFAULT 1.0,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_source_files_repository_id ON source_files(repository_id);
CREATE INDEX idx_classes_repository_id ON classes(repository_id);
CREATE INDEX idx_classes_file_id ON classes(file_id);
CREATE INDEX idx_code_edges_source ON code_edges(source_type, source_id);
CREATE INDEX idx_code_edges_target ON code_edges(target_type, target_id);
