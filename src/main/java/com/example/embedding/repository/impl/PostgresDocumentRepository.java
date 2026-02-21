package com.example.embedding.repository.impl;

import com.example.embedding.model.Document;
import com.example.embedding.model.DocumentCreateRequest;
import com.example.embedding.repository.DocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "vendor", havingValue = "postgres", matchIfMissing = true)
public class PostgresDocumentRepository implements DocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public PostgresDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(DocumentCreateRequest request, float[] embedding) {
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

    @Override
    public void update(long id, String content, float[] embedding) {
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

    @Override
    public Optional<Document> findById(long id) {
        return jdbcTemplate.query("SELECT id, title, content, updated_at FROM documents WHERE id = ?", rowMapper(), id)
                .stream()
                .findFirst();
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
    public List<Document> semanticSearch(float[] queryEmbedding, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, title, content, updated_at
                        FROM documents
                        ORDER BY embedding <-> CAST(? AS vector)
                        LIMIT ?
                        """,
                rowMapper(), toVectorLiteral(queryEmbedding), limit
        );
    }

    @Override
    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
    }

    @Override
    public void createSeedDocument(String title, String content, float[] embedding) {
        jdbcTemplate.update(
                """
                        INSERT INTO documents (title, content, embedding)
                        VALUES (?, ?, CAST(? AS vector))
                        """,
                title, content, toVectorLiteral(embedding)
        );
    }

    private RowMapper<Document> rowMapper() {
        return (rs, rowNum) -> new Document(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
