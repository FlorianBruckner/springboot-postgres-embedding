CREATE TABLE IF NOT EXISTS document_properties (
    document_id BIGINT PRIMARY KEY REFERENCES documents (id) ON DELETE CASCADE,
    properties JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_document_properties_sample_type
    ON document_properties ((properties ->> 'sampleType'));

CREATE INDEX IF NOT EXISTS idx_document_properties_related_article
    ON document_properties (((properties ->> 'relatedArticleDocumentId')::BIGINT));
