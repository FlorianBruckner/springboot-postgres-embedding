ALTER TABLE article_documents
    ADD COLUMN IF NOT EXISTS embedding_content_hash TEXT,
    ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS embedding_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128);

ALTER TABLE discussion_documents
    ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS embedding_content_hash TEXT,
    ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS embedding_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128);
