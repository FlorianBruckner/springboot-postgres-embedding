CREATE TABLE IF NOT EXISTS article_documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    content_hash TEXT,
    embedded_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS discussion_documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    article_id BIGINT,
    parent_discussion_id BIGINT,
    discussion_section VARCHAR(255),
    sentiment VARCHAR(32),
    response_depth VARCHAR(32),
    classified_at TIMESTAMPTZ,
    classification_status VARCHAR(32),
    sentiment_confidence NUMERIC,
    classification_source VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_discussion_documents_article_id
        FOREIGN KEY (article_id) REFERENCES article_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_documents_parent_discussion_id
        FOREIGN KEY (parent_discussion_id) REFERENCES discussion_documents (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_article_documents_fts
    ON article_documents USING GIN (to_tsvector('english', title || ' ' || content));
CREATE INDEX IF NOT EXISTS idx_discussion_documents_fts
    ON discussion_documents USING GIN (to_tsvector('english', title || ' ' || content));
CREATE INDEX IF NOT EXISTS idx_discussion_documents_article_id ON discussion_documents (article_id);
CREATE INDEX IF NOT EXISTS idx_discussion_documents_parent_discussion_id ON discussion_documents (parent_discussion_id);
