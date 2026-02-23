package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {
    private final DocumentService documentService;
    private final ChatClient chatClient;

    public RagService(DocumentService documentService, ChatClient.Builder chatClientBuilder) {
        this.documentService = documentService;
        this.chatClient = chatClientBuilder.build();
    }

    public String answer(String question) {
        List<Document> references = documentService.semanticSearch(question, DocumentService.ARTICLE_FILTER_EXPRESSION);
        StringBuilder context = new StringBuilder();
        for (Document reference : references) {
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
