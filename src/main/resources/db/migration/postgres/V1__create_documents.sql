CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    document_type VARCHAR(32) NOT NULL DEFAULT 'generic',
    article_document_id BIGINT,
    parent_document_id BIGINT,
    discussion_section VARCHAR(255),
    sentiment VARCHAR(32),
    response_depth VARCHAR(32),
    content_hash TEXT,
    embedding_model VARCHAR(128),
    embedding_version VARCHAR(64),
    embedded_at TIMESTAMPTZ,
    classification_model VARCHAR(128),
    classification_prompt_version VARCHAR(64),
    classified_at TIMESTAMPTZ,
    classification_status VARCHAR(32),
    sentiment_confidence NUMERIC,
    classification_source VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_documents_article_document_id
        FOREIGN KEY (article_document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_documents_parent_document_id
        FOREIGN KEY (parent_document_id) REFERENCES documents (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_documents_fts ON documents USING GIN (to_tsvector('english', title || ' ' || content));
CREATE INDEX IF NOT EXISTS idx_documents_document_type ON documents (document_type);
CREATE INDEX IF NOT EXISTS idx_documents_article_document_id ON documents (article_document_id);
CREATE INDEX IF NOT EXISTS idx_documents_parent_document_id ON documents (parent_document_id);
