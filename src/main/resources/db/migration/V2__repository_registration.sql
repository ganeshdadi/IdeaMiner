ALTER TABLE repositories
    ADD COLUMN local_path TEXT,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX idx_repositories_local_path
    ON repositories(local_path)
    WHERE local_path IS NOT NULL;
