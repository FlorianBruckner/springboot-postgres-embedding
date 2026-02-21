package com.example.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.ImageFromDockerfile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> embeddingApi = new GenericContainer<>(new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> builder
                    .from("python:3.11-slim")
                    .run("pip install --no-cache-dir fastapi==0.115.6 uvicorn==0.32.1")
                    .copy("server.py", "/app/server.py")
                    .workDir("/app")
                    .cmd("uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8080")
                    .build())
            .withFileFromString("server.py", """
                    from fastapi import FastAPI
                    from pydantic import BaseModel
                    import hashlib
                    
                    app = FastAPI()
                    DIMS = 1536
                    
                    class EmbeddingRequest(BaseModel):
                        model: str
                        input: str
                    
                    @app.post('/v1/embeddings')
                    def embed(req: EmbeddingRequest):
                        seed = hashlib.sha256(req.input.encode('utf-8')).digest()
                        vec = [((seed[i % len(seed)] + i) % 255) / 255.0 for i in range(DIMS)]
                        return {"data": [{"embedding": vec}]}
                    """))
            .withExposedPorts(8080);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("sample-loader.enabled", () -> "false");

        registry.add("embedding.api.base-url", () -> "http://" + embeddingApi.getHost() + ":" + embeddingApi.getMappedPort(8080));
        registry.add("embedding.api.path", () -> "/v1/embeddings");
        registry.add("embedding.api.model", () -> "test-embedding-model");
        registry.add("embedding.api.api-key", () -> "");
        registry.add("embedding.api.dimensions", () -> 1536);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void shouldCreateUpdateAndSearchDocumentsUsingContainerizedEmbeddingApi() throws Exception {
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
