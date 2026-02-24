package com.dreikraft.ai.embedding.postgres.mapper;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ArticleEntityMapper {
    ArticleDocument toArticleDocument(ArticleEntity entity);
}
