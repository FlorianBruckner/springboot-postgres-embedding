package com.example.embedding;

import com.example.embedding.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("sample-loader.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EmbeddingService embeddingService;

    @BeforeEach
    void setup() {
        when(embeddingService.embed(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            List<Float> vector = new ArrayList<>(1536);
            float seed = (value.length() % 10) / 10.0f;
            for (int i = 0; i < 1536; i++) {
                vector.add(seed);
            }
            return vector;
        });
    }

    @Test
    void shouldCreateUpdateAndSearchDocuments() throws Exception {
        String payload = """
                {"title":"Java Memory","content":"Java manages heap memory and garbage collection efficiently."}
                """;

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        mockMvc.perform(get("/api/documents/search").param("query", "garbage collection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Java Memory"));

        String updatePayload = """
                {"content":"Java records provide immutable data carrier semantics."}
                """;
        mockMvc.perform(put("/api/documents/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/semantic-search").param("query", "immutable records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").exists());
    }
}
