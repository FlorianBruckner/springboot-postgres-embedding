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
import java.util.Comparator;
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
    public long create(DocumentCreateRequest request, List<Float> embedding) {
        jdbcTemplate.update(
                "INSERT INTO documents (title, content, embedding, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                request.title(), request.content(), toVectorLiteral(embedding)
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Override
    public void update(long id, String content, List<Float> embedding) {
        int updated = jdbcTemplate.update(
                "UPDATE documents SET content = ?, embedding = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
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
    public List<Document> semanticSearch(List<Float> queryEmbedding, int limit) {
        List<DocumentWithEmbedding> docs = jdbcTemplate.query(
                "SELECT id, title, content, updated_at, embedding FROM documents",
                (rs, rowNum) -> new DocumentWithEmbedding(
                        new Document(rs.getLong("id"), rs.getString("title"), rs.getString("content"), toOffsetDateTime(rs.getTimestamp("updated_at"))),
                        parseVector(rs.getString("embedding"))
                )
        );

        return docs.stream()
                .sorted(Comparator.comparingDouble(d -> l2Distance(d.embedding(), queryEmbedding)))
                .limit(limit)
                .map(DocumentWithEmbedding::document)
                .toList();
    }

    @Override
    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
    }

    @Override
    public void createSeedDocument(String title, String content, List<Float> embedding) {
        jdbcTemplate.update(
                "INSERT INTO documents (title, content, embedding, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
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

    private List<Float> parseVector(String vectorLiteral) {
        String cleaned = vectorLiteral.replace("[", "").replace("]", "").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        String[] parts = cleaned.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            values.add(Float.parseFloat(part.trim()));
        }
        return values;
    }

    private double l2Distance(List<Float> a, List<Float> b) {
        int size = Math.min(a.size(), b.size());
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            double diff = a.get(i) - b.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private record DocumentWithEmbedding(Document document, List<Float> embedding) {
    }
}
