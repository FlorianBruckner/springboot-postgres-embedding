package com.dreikraft.ai.embedding.postgres.repository.impl;

import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "vendor", havingValue = "postgres", matchIfMissing = true)
public class PostgresDocumentRepository implements DocumentRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public long create(DocumentCreateRequest request) {
        Long id = jdbcTemplate.queryForObject(
                """
                        INSERT INTO documents (title, content)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                request.title(),
                request.content()
        );
        jdbcTemplate.update(
                """
                        INSERT INTO document_properties (document_id, properties)
                        VALUES (?, CAST(? AS JSONB))
                        ON CONFLICT (document_id) DO UPDATE SET properties = EXCLUDED.properties
                        """,
                id,
                toJson(request.propertiesOrEmpty())
        );
        return id;
    }

    @Override
    public void update(long id, String content) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE documents
                        SET content = ?, updated_at = NOW()
                        WHERE id = ?
                        """,
                content,
                id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
    }

    @Override
    public Optional<Document> findById(long id) {
        return jdbcTemplate.query("SELECT id, title, content, updated_at FROM documents WHERE id = ?", rowMapper(), id)
                .stream()
                .findFirst();
    }

    @Override
    public List<Document> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Document> docs = jdbcTemplate.query(
                "SELECT id, title, content, updated_at FROM documents WHERE id IN (" + placeholders + ")",
                rowMapper(),
                ids.toArray()
        );
        Map<Long, Document> byId = docs.stream().collect(Collectors.toMap(Document::id, doc -> doc));
        List<Document> ordered = new ArrayList<>();
        for (Long id : ids) {
            Document doc = byId.get(id);
            if (doc != null) {
                ordered.add(doc);
            }
        }
        return ordered;
    }

    @Override
    public List<Document> keywordSearch(String term, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, title, content, updated_at
                        FROM documents
                        WHERE to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', ?)
                        ORDER BY updated_at DESC
                        LIMIT ?
                        """,
                rowMapper(), term, limit
        );
    }

    @Override
    public List<Document> keywordSearchBySampleType(String term, int limit, String sampleType) {
        return jdbcTemplate.query(
                """
                        SELECT d.id, d.title, d.content, d.updated_at
                        FROM documents d
                        JOIN document_properties dp ON dp.document_id = d.id
                        WHERE (dp.properties ->> 'sampleType') = ?
                          AND to_tsvector('english', d.title || ' ' || d.content) @@ plainto_tsquery('english', ?)
                        ORDER BY d.updated_at DESC
                        LIMIT ?
                        """,
                rowMapper(), sampleType, term, limit
        );
    }

    @Override
    public List<DiscussionDocument> findDiscussionsByArticleId(long articleDocumentId) {
        return jdbcTemplate.query(
                """
                        SELECT d.id,
                               d.title,
                               d.content,
                               d.updated_at,
                               CAST(dp.properties ->> 'respondsToDocumentId' AS BIGINT) AS parent_document_id,
                               dp.properties ->> 'discussionSection' AS discussion_section
                        FROM documents d
                        JOIN document_properties dp ON dp.document_id = d.id
                        WHERE (dp.properties ->> 'sampleType') = 'discussion'
                          AND CAST(dp.properties ->> 'relatedArticleDocumentId' AS BIGINT) = ?
                        ORDER BY d.id
                        """,
                (rs, rowNum) -> new DiscussionDocument(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getObject("parent_document_id", Long.class),
                        rs.getString("discussion_section")
                ),
                articleDocumentId
        );
    }

    @Override
    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
    }

    private RowMapper<Document> rowMapper() {
        return (rs, rowNum) -> new Document(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toJson(Map<String, Object> properties) {
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize properties", e);
        }
    }
}
