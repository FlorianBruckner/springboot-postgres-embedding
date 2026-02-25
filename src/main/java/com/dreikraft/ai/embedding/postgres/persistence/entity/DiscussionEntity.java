package com.dreikraft.ai.embedding.postgres.persistence.entity;

import com.dreikraft.ai.embedding.postgres.service.ClassificationStatus;
import com.dreikraft.ai.embedding.postgres.service.EmbeddingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
@Table(name = "discussion_documents")
public class DiscussionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private ArticleEntity article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_discussion_id")
    private DiscussionEntity parentDiscussion;

    @OneToMany(mappedBy = "parentDiscussion", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<DiscussionEntity> responses = new ArrayList<>();

    @Column(name = "discussion_section", length = 255)
    private String discussionSection;

    @Column(name = "sentiment", length = 32)
    private String sentiment;

    @Column(name = "response_depth", length = 32)
    private String responseDepth;

    @Column(name = "classified_at")
    private OffsetDateTime classifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_status", length = 32)
    private ClassificationStatus classificationStatus;

    @Column(name = "sentiment_confidence")
    private Double sentimentConfidence;

    @Column(name = "classification_source", length = 64)
    private String classificationSource;

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

    public Long getArticleDocumentId() {
        return article == null ? null : article.getId();
    }

    public Long getParentDocumentId() {
        return parentDiscussion == null ? null : parentDiscussion.getId();
    }
}
