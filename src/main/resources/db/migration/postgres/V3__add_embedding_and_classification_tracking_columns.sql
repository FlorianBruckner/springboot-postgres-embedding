ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS content_hash TEXT,
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128),
    ADD COLUMN IF NOT EXISTS embedding_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS classification_model VARCHAR(128),
    ADD COLUMN IF NOT EXISTS classification_prompt_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS classified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS classification_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS sentiment_confidence NUMERIC,
    ADD COLUMN IF NOT EXISTS classification_source VARCHAR(64);
