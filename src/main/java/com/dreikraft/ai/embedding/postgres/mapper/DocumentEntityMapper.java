package com.dreikraft.ai.embedding.postgres.mapper;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DocumentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DocumentEntityMapper {

    ArticleDocument toArticleDocument(DocumentEntity entity);

    DiscussionDocument toDiscussionDocument(DocumentEntity entity);
}
