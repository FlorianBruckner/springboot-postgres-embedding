package com.dreikraft.ai.embedding.postgres.controller;

import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.ThreadedDiscussionItem;
import com.dreikraft.ai.embedding.postgres.service.DocumentService;
import com.dreikraft.ai.embedding.postgres.service.RagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ViewController {
    private final DocumentService documentService;
    private final RagService ragService;

    public ViewController(DocumentService documentService, RagService ragService) {
        this.documentService = documentService;
        this.ragService = ragService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "keyword") String mode,
                        Model model) {
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("mode", mode);
        if (q != null && !q.isBlank()) {
            List<Document> results;
            if ("rag".equals(mode)) {
                model.addAttribute("ragAnswer", ragService.answer(q));
                results = documentService.semanticSearch(q, DocumentService.ARTICLE_FILTER_EXPRESSION);
            } else if ("semantic".equals(mode)) {
                results = documentService.semanticSearch(q, DocumentService.ARTICLE_FILTER_EXPRESSION);
            } else {
                results = documentService.keywordArticleSearch(q);
            }
            model.addAttribute("results", results);

            Map<Long, List<ThreadedDiscussionItem>> discussionsByArticleId = new LinkedHashMap<>();
            for (Document article : results) {
                discussionsByArticleId.put(article.id(), documentService.findThreadedDiscussionsByArticleId(article.id()));
            }
            model.addAttribute("discussionsByArticleId", discussionsByArticleId);
        }
        model.addAttribute("count", documentService.count());
        return "index";
    }
}
