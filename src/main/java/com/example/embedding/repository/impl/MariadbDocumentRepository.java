package com.example.embedding.repository.impl;

import com.example.embedding.model.Document;
import com.example.embedding.model.DocumentCreateRequest;
import com.example.embedding.repository.DocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "vendor", havingValue = "mariadb")
public class MariadbDocumentRepository implements DocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public MariadbDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(DocumentCreateRequest request) {
        jdbcTemplate.update(
                "INSERT INTO documents (title, content, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                request.title(), request.content()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Override
    public void update(long id, String content) {
        int updated = jdbcTemplate.update(
                "UPDATE documents SET content = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                content, id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
    }

    @Override
    public Optional<Document> findById(long id) {
        return jdbcTemplate.query("SELECT id, title, content, updated_at FROM documents WHERE id = ?", rowMapper(), id)
                .stream().findFirst();
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
        String like = "%" + term + "%";
        return jdbcTemplate.query(
                "SELECT id, title, content, updated_at FROM documents WHERE title LIKE ? OR content LIKE ? ORDER BY updated_at DESC LIMIT ?",
                rowMapper(), like, like, limit
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
                toOffsetDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }
}
