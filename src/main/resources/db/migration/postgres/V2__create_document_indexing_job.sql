CREATE TABLE IF NOT EXISTS document_indexing_job (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_document_indexing_job_status_created_at
    ON document_indexing_job (status, created_at);
