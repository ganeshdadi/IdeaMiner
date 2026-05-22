CREATE TABLE role_inference (
    id TEXT PRIMARY KEY,
    repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    class_id TEXT REFERENCES classes(id) ON DELETE CASCADE,
    method_id TEXT REFERENCES methods(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL,
    source TEXT NOT NULL,
    signals JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, role, class_id, method_id)
);

CREATE INDEX idx_role_inference_repository_id ON role_inference(repository_id);
CREATE INDEX idx_role_inference_class_id ON role_inference(class_id);
CREATE INDEX idx_role_inference_method_id ON role_inference(method_id);
