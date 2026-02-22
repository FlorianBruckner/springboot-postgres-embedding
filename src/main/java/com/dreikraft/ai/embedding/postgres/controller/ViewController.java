package com.dreikraft.ai.embedding.postgres.controller;

import com.dreikraft.ai.embedding.postgres.service.DocumentService;
import com.dreikraft.ai.embedding.postgres.service.RagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
            if ("rag".equals(mode)) {
                model.addAttribute("ragAnswer", ragService.answer(q));
                model.addAttribute("results", documentService.semanticSearch(q));
            } else if ("semantic".equals(mode)) {
                model.addAttribute("results", documentService.semanticSearch(q));
            } else {
                model.addAttribute("results", documentService.keywordSearch(q));
            }
        }
        model.addAttribute("count", documentService.count());
        return "index";
    }
}
