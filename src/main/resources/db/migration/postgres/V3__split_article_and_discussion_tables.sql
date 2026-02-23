CREATE TABLE IF NOT EXISTS article_documents (
    document_id BIGINT PRIMARY KEY,
    CONSTRAINT fk_article_documents_document_id
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS discussion_documents (
    document_id BIGINT PRIMARY KEY,
    article_document_id BIGINT NOT NULL,
    parent_document_id BIGINT,
    discussion_section VARCHAR(255),
    sentiment VARCHAR(32) NOT NULL DEFAULT 'unknown',
    response_depth VARCHAR(32) NOT NULL DEFAULT 'unknown',
    CONSTRAINT fk_discussion_documents_document_id
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_documents_article_document_id
        FOREIGN KEY (article_document_id) REFERENCES article_documents (document_id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_documents_parent_document_id
        FOREIGN KEY (parent_document_id) REFERENCES discussion_documents (document_id) ON DELETE SET NULL
);

INSERT INTO article_documents (document_id)
SELECT d.id
FROM documents d
WHERE d.document_type = 'article'
ON CONFLICT (document_id) DO NOTHING;

INSERT INTO discussion_documents (document_id, article_document_id, parent_document_id, discussion_section, sentiment, response_depth)
SELECT d.id,
       d.article_document_id,
       d.parent_document_id,
       d.discussion_section,
       COALESCE(d.sentiment, 'unknown'),
       COALESCE(d.response_depth, 'unknown')
FROM documents d
JOIN article_documents a ON a.document_id = d.article_document_id
WHERE d.document_type = 'discussion'
ON CONFLICT (document_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_discussion_documents_article_document_id ON discussion_documents (article_document_id);
CREATE INDEX IF NOT EXISTS idx_discussion_documents_parent_document_id ON discussion_documents (parent_document_id);
