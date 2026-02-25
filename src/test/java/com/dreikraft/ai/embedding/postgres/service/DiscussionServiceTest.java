package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.DiscussionEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.DiscussionCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DiscussionJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscussionServiceTest {

    @Test
    void createRunsClassificationAndEmbeddingVariants() {
        DiscussionJpaRepository discussionRepository = mock(DiscussionJpaRepository.class);
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        DiscussionEntityMapper discussionMapper = mock(DiscussionEntityMapper.class);
        DiscussionClassificationService classificationService = mock(DiscussionClassificationService.class);
        EmbeddingTransformationService transformationService = mock(EmbeddingTransformationService.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);

        DiscussionService service = new DiscussionService(
                discussionRepository,
                articleRepository,
                discussionMapper,
                classificationService,
                transformationService,
                vectorStoreService
        );

        ArticleEntity article = new ArticleEntity();
        article.setId(1L);
        article.setTitle("Article");
        article.setContent("Article body");

        DiscussionEntity savedDiscussion = new DiscussionEntity();
        savedDiscussion.setId(11L);
        savedDiscussion.setTitle("Comment");
        savedDiscussion.setContent("Text");
        savedDiscussion.setArticle(article);

        when(articleRepository.findArticleById(1L)).thenReturn(Optional.of(article));
        when(discussionRepository.save(any(DiscussionEntity.class))).thenReturn(savedDiscussion);
        when(discussionRepository.findRootDiscussionsByArticleDocumentIdOrderByIdAsc(1L)).thenReturn(List.of(savedDiscussion));
        when(discussionMapper.toDiscussionDocument(savedDiscussion)).thenReturn(
                new DiscussionDocument(11L, "Comment", "Text", OffsetDateTime.now(), null, "section", null, null));
        when(transformationService.transformForDiscussion("Article", "Comment", "Text")).thenReturn(List.of(
                new EmbeddingTransformationService.EmbeddingVariant("original", "Text"),
                new EmbeddingTransformationService.EmbeddingVariant("stance", "Neutral statement")
        ));
        when(classificationService.classify(any())).thenReturn(Map.of(
                11L, new DiscussionClassificationService.DiscussionClassification("positive", "substantive")
        ));
        when(discussionRepository.findDiscussionById(11L)).thenReturn(Optional.of(savedDiscussion));

        service.create(new DiscussionCreateRequest("Comment", "Text", 1L, null, "section"));

        verify(vectorStoreService).upsertVariants(any(Long.class), any(String.class), any(String.class), any(List.class), any(Map.class));
        verify(classificationService).classify(any());
    }
}
