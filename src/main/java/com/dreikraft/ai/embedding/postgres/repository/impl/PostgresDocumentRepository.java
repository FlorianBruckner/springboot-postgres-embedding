package com.dreikraft.ai.embedding.postgres.repository.impl;

import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "vendor", havingValue = "postgres", matchIfMissing = true)
public class PostgresDocumentRepository implements DocumentRepository {
    private static final String TYPE_ARTICLE = "article";
    private static final String TYPE_DISCUSSION = "discussion";
    private static final String TYPE_GENERIC = "generic";

    private final JdbcTemplate jdbcTemplate;

    public PostgresDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(DocumentCreateRequest request) {
        Map<String, Object> properties = request.propertiesOrEmpty();
        String documentType = resolveDocumentType(properties);
        Long articleDocumentId = toLong(properties.get("relatedArticleDocumentId"));
        Long parentDocumentId = toLong(properties.get("respondsToDocumentId"));
        String discussionSection = toStringValue(properties.get("discussionSection"));

        Long id = jdbcTemplate.queryForObject(
                """
                        INSERT INTO documents (title, content, document_type, article_document_id, parent_document_id, discussion_section)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                request.title(),
                request.content(),
                documentType,
                articleDocumentId,
                parentDocumentId,
                discussionSection
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
    public void updateDiscussionClassification(long id, String sentiment, String responseDepth) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE documents
                        SET sentiment = ?, response_depth = ?
                        WHERE id = ?
                        """,
                sentiment,
                responseDepth,
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
                        SELECT id, title, content, updated_at
                        FROM documents
                        WHERE document_type = ?
                          AND to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', ?)
                        ORDER BY updated_at DESC
                        LIMIT ?
                        """,
                rowMapper(), sampleType, term, limit
        );
    }

    @Override
    public List<DiscussionDocument> findDiscussionsByArticleId(long articleDocumentId) {
        return jdbcTemplate.query(
                """
                        SELECT id,
                               title,
                               content,
                               updated_at,
                               parent_document_id,
                               discussion_section,
                               sentiment,
                               response_depth
                        FROM documents
                        WHERE document_type = 'discussion'
                          AND article_document_id = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> new DiscussionDocument(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getObject("parent_document_id", Long.class),
                        rs.getString("discussion_section"),
                        rs.getString("sentiment"),
                        rs.getString("response_depth")
                ),
                articleDocumentId
        );
    }

    @Override
    public Map<String, Object> findVectorMetadataById(long id) {
        return jdbcTemplate.query(
                        """
                                SELECT document_type, article_document_id, parent_document_id, discussion_section
                                FROM documents
                                WHERE id = ?
                                """,
                        (rs, rowNum) -> {
                            Map<String, Object> metadata = new LinkedHashMap<>();
                            metadata.put("sampleType", rs.getString("document_type"));

                            Long articleId = rs.getObject("article_document_id", Long.class);
                            if (articleId != null) {
                                metadata.put("relatedArticleDocumentId", articleId);
                            }

                            Long parentId = rs.getObject("parent_document_id", Long.class);
                            if (parentId != null) {
                                metadata.put("respondsToDocumentId", parentId);
                            }

                            String section = rs.getString("discussion_section");
                            if (section != null && !section.isBlank()) {
                                metadata.put("discussionSection", section);
                            }

                            return metadata;
                        },
                        id
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
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

    private String resolveDocumentType(Map<String, Object> properties) {
        String typeFromProperties = toStringValue(properties.get("sampleType"));
        if (TYPE_ARTICLE.equals(typeFromProperties) || TYPE_DISCUSSION.equals(typeFromProperties) || TYPE_GENERIC.equals(typeFromProperties)) {
            return typeFromProperties;
        }
        return properties.containsKey("relatedArticleDocumentId") ? TYPE_DISCUSSION : TYPE_GENERIC;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
