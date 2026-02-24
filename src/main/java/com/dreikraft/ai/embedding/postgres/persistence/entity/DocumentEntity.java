package com.dreikraft.ai.embedding.postgres.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;


    @Column(name = "article_document_id")
    private Long articleDocumentId;

    @Column(name = "parent_document_id")
    private Long parentDocumentId;

    @Column(name = "discussion_section", length = 255)
    private String discussionSection;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "sentiment", length = 32)
    private String sentiment;

    @Column(name = "response_depth", length = 32)
    private String responseDepth;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "embedding_version", length = 64)
    private String embeddingVersion;

    @Column(name = "embedded_at")
    private OffsetDateTime embeddedAt;

    @Column(name = "classification_model", length = 128)
    private String classificationModel;

    @Column(name = "classification_prompt_version", length = 64)
    private String classificationPromptVersion;

    @Column(name = "classified_at")
    private OffsetDateTime classifiedAt;

    @Column(name = "classification_status", length = 32)
    private String classificationStatus;

    @Column(name = "sentiment_confidence")
    private Double sentimentConfidence;

    @Column(name = "classification_source", length = 64)
    private String classificationSource;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getArticleDocumentId() { return articleDocumentId; }
    public void setArticleDocumentId(Long articleDocumentId) { this.articleDocumentId = articleDocumentId; }
    public Long getParentDocumentId() { return parentDocumentId; }
    public void setParentDocumentId(Long parentDocumentId) { this.parentDocumentId = parentDocumentId; }
    public String getDiscussionSection() { return discussionSection; }
    public void setDiscussionSection(String discussionSection) { this.discussionSection = discussionSection; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public String getResponseDepth() { return responseDepth; }
    public void setResponseDepth(String responseDepth) { this.responseDepth = responseDepth; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getEmbeddingVersion() { return embeddingVersion; }
    public void setEmbeddingVersion(String embeddingVersion) { this.embeddingVersion = embeddingVersion; }
    public OffsetDateTime getEmbeddedAt() { return embeddedAt; }
    public void setEmbeddedAt(OffsetDateTime embeddedAt) { this.embeddedAt = embeddedAt; }
    public String getClassificationModel() { return classificationModel; }
    public void setClassificationModel(String classificationModel) { this.classificationModel = classificationModel; }
    public String getClassificationPromptVersion() { return classificationPromptVersion; }
    public void setClassificationPromptVersion(String classificationPromptVersion) { this.classificationPromptVersion = classificationPromptVersion; }
    public OffsetDateTime getClassifiedAt() { return classifiedAt; }
    public void setClassifiedAt(OffsetDateTime classifiedAt) { this.classifiedAt = classifiedAt; }
    public String getClassificationStatus() { return classificationStatus; }
    public void setClassificationStatus(String classificationStatus) { this.classificationStatus = classificationStatus; }
    public Double getSentimentConfidence() { return sentimentConfidence; }
    public void setSentimentConfidence(Double sentimentConfidence) { this.sentimentConfidence = sentimentConfidence; }
    public String getClassificationSource() { return classificationSource; }
    public void setClassificationSource(String classificationSource) { this.classificationSource = classificationSource; }
}
