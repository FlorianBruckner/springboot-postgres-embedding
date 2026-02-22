package com.dreikraft.ai.embedding.postgres.repository.impl;

import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
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

    public PostgresDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(DocumentCreateRequest request) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO documents (title, content)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                request.title(),
                request.content()
        );
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
}
