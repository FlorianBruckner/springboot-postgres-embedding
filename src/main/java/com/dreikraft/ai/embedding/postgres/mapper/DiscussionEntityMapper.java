package com.dreikraft.ai.embedding.postgres.mapper;

import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DiscussionEntityMapper {
    @Mapping(target = "section", source = "discussionSection")
    DiscussionDocument toDiscussionDocument(DiscussionEntity entity);
}
