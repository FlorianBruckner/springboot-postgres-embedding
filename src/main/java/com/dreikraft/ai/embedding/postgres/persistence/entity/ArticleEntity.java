package com.dreikraft.ai.embedding.postgres.persistence.entity;

import com.dreikraft.ai.embedding.postgres.service.EmbeddingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "article_documents")
public class ArticleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "embedded_at")
    private OffsetDateTime embeddedAt;

    @Column(name = "embedding_content_hash")
    private String embeddingContentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", length = 32)
    private EmbeddingStatus embeddingStatus;

    @Column(name = "embedding_source", length = 64)
    private String embeddingSource;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "article", fetch = FetchType.LAZY)
    private List<DiscussionEntity> discussions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
