ALTER TABLE document_indexing_job
    ADD COLUMN IF NOT EXISTS attempt INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE document_indexing_job
SET available_at = COALESCE(available_at, created_at),
    updated_at = COALESCE(updated_at, created_at)
WHERE available_at IS NULL
   OR updated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_indexing_job_status_available_at
    ON document_indexing_job (status, available_at, id);
