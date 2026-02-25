package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {
    private final ArticleService articleService;
    private final ChatClient chatClient;

    public RagService(ArticleService articleService, ChatClient.Builder chatClientBuilder) {
        this.articleService = articleService;
        this.chatClient = chatClientBuilder.build();
    }

    public String answer(String question) {
        List<ArticleDocument> references = articleService.semanticSearch(question, ArticleService.ARTICLE_FILTER_EXPRESSION);
        StringBuilder context = new StringBuilder();
        for (ArticleDocument reference : references) {
            context.append("Title: ").append(reference.title()).append('\n')
                    .append("Content: ").append(reference.content()).append("\n\n");
        }

        return chatClient.prompt()
                .system("You are a RAG assistant. Answer only with information grounded in the provided context. If not enough context exists, say so clearly.")
                .user(user -> user.text("""
                        Question:
                        {question}

                        Context:
                        {context}
                        """)
                        .param("question", question)
                        .param("context", context.toString()))
                .call()
                .content();
    }
}
