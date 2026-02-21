package com.example.embedding;

import com.example.embedding.service.WikipediaClient;
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
}
