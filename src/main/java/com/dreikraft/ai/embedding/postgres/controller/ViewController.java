package com.dreikraft.ai.embedding.postgres.controller;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.model.ThreadedDiscussionItem;
import com.dreikraft.ai.embedding.postgres.service.ArticleService;
import com.dreikraft.ai.embedding.postgres.service.DiscussionService;
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
    private final ArticleService articleService;
    private final DiscussionService discussionService;
    private final RagService ragService;

    public ViewController(ArticleService articleService, DiscussionService discussionService, RagService ragService) {
        this.articleService = articleService;
        this.discussionService = discussionService;
        this.ragService = ragService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "keyword") String mode,
                        Model model) {
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("mode", mode);
        if (q != null && !q.isBlank()) {
            List<ArticleDocument> results;
            if ("rag".equals(mode)) {
                model.addAttribute("ragAnswer", ragService.answer(q));
                results = articleService.semanticSearch(q, ArticleService.ARTICLE_FILTER_EXPRESSION);
            } else if ("semantic".equals(mode)) {
                results = articleService.semanticSearch(q, ArticleService.ARTICLE_FILTER_EXPRESSION);
            } else {
                results = articleService.keywordSearch(q);
            }
            model.addAttribute("results", results);

            Map<Long, List<ThreadedDiscussionItem>> discussionsByArticleId = new LinkedHashMap<>();
            for (ArticleDocument article : results) {
                discussionsByArticleId.put(article.id(), discussionService.findThreadedDiscussionsByArticleId(article.id()));
            }
            model.addAttribute("discussionsByArticleId", discussionsByArticleId);
        }
        model.addAttribute("count", articleService.count() + discussionService.count());
        return "index";
    }
}
