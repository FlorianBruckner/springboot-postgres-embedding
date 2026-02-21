package com.example.embedding.repository;

import com.example.embedding.model.Document;
import com.example.embedding.model.DocumentCreateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(DocumentCreateRequest request, List<Float> embedding) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO documents (title, content, embedding)
                        VALUES (?, ?, CAST(? AS vector))
                        RETURNING id
                        """,
                Long.class,
                request.title(),
                request.content(),
                toVectorLiteral(embedding)
        );
    }

    public void update(long id, String content, List<Float> embedding) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE documents
                        SET content = ?, embedding = CAST(? AS vector), updated_at = NOW()
                        WHERE id = ?
                        """,
                content,
                toVectorLiteral(embedding),
                id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
    }

    public Optional<Document> findById(long id) {
        return jdbcTemplate.query("SELECT id, title, content, updated_at FROM documents WHERE id = ?", documentRowMapper(), id)
                .stream()
                .findFirst();
    }

    public List<Document> keywordSearch(String term, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, title, content, updated_at
                        FROM documents
                        WHERE to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', ?)
                        ORDER BY updated_at DESC
                        LIMIT ?
                        """,
                documentRowMapper(),
                term,
                limit
        );
    }

    public List<Document> semanticSearch(List<Float> queryEmbedding, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, title, content, updated_at
                        FROM documents
                        ORDER BY embedding <-> CAST(? AS vector)
                        LIMIT ?
                        """,
                documentRowMapper(),
                toVectorLiteral(queryEmbedding),
                limit
        );
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
    }

    public void createSeedDocument(String title, String content, List<Float> embedding) {
        jdbcTemplate.update(
                """
                        INSERT INTO documents (title, content, embedding)
                        VALUES (?, ?, CAST(? AS vector))
                        """,
                title,
                content,
                toVectorLiteral(embedding)
        );
    }

    private RowMapper<Document> documentRowMapper() {
        return (rs, rowNum) -> new Document(rs.getLong("id"), rs.getString("title"), rs.getString("content"), rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }

    private String toVectorLiteral(List<Float> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding.get(i));
        }
        sb.append(']');
        return sb.toString();
    }
}
