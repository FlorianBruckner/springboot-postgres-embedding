package com.dreikraft.ai.embedding.postgres.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
public class DiscussionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_document_id")
    private ArticleEntity article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_document_id")
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

    @Column(name = "classification_status", length = 32)
    private String classificationStatus;

    @Column(name = "sentiment_confidence")
    private Double sentimentConfidence;

    @Column(name = "classification_source", length = 64)
    private String classificationSource;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public ArticleEntity getArticle() { return article; }
    public void setArticle(ArticleEntity article) { this.article = article; }
    public DiscussionEntity getParentDiscussion() { return parentDiscussion; }
    public void setParentDiscussion(DiscussionEntity parentDiscussion) { this.parentDiscussion = parentDiscussion; }
    public List<DiscussionEntity> getResponses() { return responses; }
    public void setResponses(List<DiscussionEntity> responses) { this.responses = responses; }
    public String getDiscussionSection() { return discussionSection; }
    public void setDiscussionSection(String discussionSection) { this.discussionSection = discussionSection; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public String getResponseDepth() { return responseDepth; }
    public void setResponseDepth(String responseDepth) { this.responseDepth = responseDepth; }
    public OffsetDateTime getClassifiedAt() { return classifiedAt; }
    public void setClassifiedAt(OffsetDateTime classifiedAt) { this.classifiedAt = classifiedAt; }
    public String getClassificationStatus() { return classificationStatus; }
    public void setClassificationStatus(String classificationStatus) { this.classificationStatus = classificationStatus; }
    public Double getSentimentConfidence() { return sentimentConfidence; }
    public void setSentimentConfidence(Double sentimentConfidence) { this.sentimentConfidence = sentimentConfidence; }
    public String getClassificationSource() { return classificationSource; }
    public void setClassificationSource(String classificationSource) { this.classificationSource = classificationSource; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
