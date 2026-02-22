package com.dreikraft.ai.embedding.postgres;

import com.dreikraft.ai.embedding.postgres.service.WikipediaClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WikipediaClientTest {

    @Test
    void shouldParseWikipediaPagesWithExtracts() throws Exception {
        String payload = """
                {
                  "query": {
                    "pages": {
                      "123": {"title": "Java (Programmiersprache)", "extract": "Java ist eine objektorientierte Programmiersprache."},
                      "456": {"title": "Leerer Artikel", "extract": ""}
                    }
                  }
                }
                """;

        List<WikipediaClient.WikipediaArticle> articles = WikipediaClient.parseArticles(new ObjectMapper().readTree(payload));

        assertEquals(1, articles.size());
        assertEquals("Java (Programmiersprache)", articles.getFirst().title());
    }
    @Test
    void shouldParseDiscussionItemsWithParentReferences() {
        String wikiText = """
                == Abschnitt ==
                Kommentar --[[Benutzer:Alpha]] 12:10, 1. Januar 2024 (CET)
                :Antwort --[[Benutzer:Beta]] 12:12, 1. Januar 2024 (CET)
                """;

        List<WikipediaClient.WikipediaDiscussionItem> items = WikipediaClient.parseDiscussionItems(wikiText);

        assertEquals(2, items.size());
        assertEquals("discussion-item-1", items.getFirst().itemId());
        assertEquals("discussion-item-1", items.get(1).parentItemId());
    }

}
