package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.DiscussionEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.DiscussionCreateRequest;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DiscussionJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscussionServiceTest {

    @Test
    void createPersistsDiscussionAndEnqueuesFollowupJobs() {
        DiscussionJpaRepository discussionRepository = mock(DiscussionJpaRepository.class);
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        DiscussionEntityMapper discussionMapper = mock(DiscussionEntityMapper.class);
        DocumentIndexingJobService documentIndexingJobService = mock(DocumentIndexingJobService.class);

        DiscussionService service = new DiscussionService(
                discussionRepository,
                articleRepository,
                discussionMapper,
                documentIndexingJobService
        );

        ArticleEntity article = new ArticleEntity();
        article.setId(1L);

        DiscussionEntity savedDiscussion = new DiscussionEntity();
        savedDiscussion.setId(11L);
        savedDiscussion.setArticle(article);

        when(articleRepository.findArticleById(1L)).thenReturn(Optional.of(article));
        when(discussionRepository.save(any(DiscussionEntity.class))).thenReturn(savedDiscussion);

        service.create(new DiscussionCreateRequest("Comment", "Text", 1L, null, "section"));

        verify(documentIndexingJobService).enqueue(DocumentIndexingJobType.EMBED_UPSERT, DocumentType.DISCUSSION, 11L);
        verify(documentIndexingJobService).enqueue(
                DocumentIndexingJobType.DISCUSSION_CLASSIFY,
                DocumentType.ARTICLE,
                1L
        );
    }
}
