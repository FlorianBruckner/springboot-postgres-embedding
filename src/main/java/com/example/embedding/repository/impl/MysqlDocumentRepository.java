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
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "vendor", havingValue = "mysql")
public class MysqlDocumentRepository implements DocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public MysqlDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(DocumentCreateRequest request, float[] embedding) {
        jdbcTemplate.update(
                "INSERT INTO documents (title, content, embedding, updated_at) VALUES (?, ?, CAST(? AS VECTOR(1536)), CURRENT_TIMESTAMP)",
                request.title(), request.content(), toVectorLiteral(embedding)
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Override
    public void update(long id, String content, float[] embedding) {
        int updated = jdbcTemplate.update(
                "UPDATE documents SET content = ?, embedding = CAST(? AS VECTOR(1536)), updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                content, toVectorLiteral(embedding), id
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
    public List<Document> keywordSearch(String term, int limit) {
        String like = "%" + term + "%";
        return jdbcTemplate.query(
                "SELECT id, title, content, updated_at FROM documents WHERE title LIKE ? OR content LIKE ? ORDER BY updated_at DESC LIMIT ?",
                rowMapper(), like, like, limit
        );
    }

    @Override
    public List<Document> semanticSearch(float[] queryEmbedding, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, title, content, updated_at
                FROM documents
                ORDER BY DISTANCE(embedding, CAST(? AS VECTOR(1536)), 'EUCLIDEAN')
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
                "INSERT INTO documents (title, content, embedding, updated_at) VALUES (?, ?, CAST(? AS VECTOR(1536)), CURRENT_TIMESTAMP)",
                title, content, toVectorLiteral(embedding)
        );
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
