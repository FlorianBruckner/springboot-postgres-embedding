CREATE TABLE IF NOT EXISTS document_indexing_job (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    job_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    content_hash TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_document_indexing_job_status
        CHECK (status IN ('pending', 'running', 'succeeded', 'failed', 'dead_letter')),
    CONSTRAINT fk_document_indexing_job_document_id
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_document_indexing_job_status_next_attempt_at
    ON document_indexing_job (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_document_indexing_job_document_id
    ON document_indexing_job (document_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_indexing_job_document_job_type_content_hash
    ON document_indexing_job (document_id, job_type, content_hash)
    WHERE content_hash IS NOT NULL;
