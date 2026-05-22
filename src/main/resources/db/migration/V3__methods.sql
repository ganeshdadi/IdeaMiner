CREATE TABLE methods (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    file_id TEXT REFERENCES source_files(id) ON DELETE CASCADE,
    method_name TEXT NOT NULL,
    signature TEXT NOT NULL,
    return_type TEXT,
    parameters JSONB NOT NULL DEFAULT '[]'::jsonb,
    annotations JSONB NOT NULL DEFAULT '[]'::jsonb,
    cyclomatic_complexity INTEGER NOT NULL DEFAULT 0,
    source_span JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (class_id, signature)
);

CREATE INDEX idx_methods_repository_id ON methods(repository_id);
CREATE INDEX idx_methods_class_id ON methods(class_id);
CREATE INDEX idx_methods_file_id ON methods(file_id);
CREATE INDEX idx_methods_complexity ON methods(cyclomatic_complexity DESC);
