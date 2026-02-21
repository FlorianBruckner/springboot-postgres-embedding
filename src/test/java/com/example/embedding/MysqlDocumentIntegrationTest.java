package com.example.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MysqlDocumentIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
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
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("sample-loader.enabled", () -> "false");
        registry.add("app.database.vendor", () -> "mysql");

        registry.add("embedding.api.base-url", () -> "http://" + embeddingApi.getHost() + ":" + embeddingApi.getMappedPort(8080));
        registry.add("embedding.api.path", () -> "/v1/embeddings");
        registry.add("embedding.api.model", () -> "test-embedding-model");
        registry.add("embedding.api.api-key", () -> "");
        registry.add("embedding.api.dimensions", () -> 1536);
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldCreateUpdateAndSearchDocumentsUsingMysqlRepository() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/documents",
                new HttpEntity<>("{\"title\":\"Java Memory\",\"content\":\"Java manages heap memory and garbage collection efficiently.\"}", headers),
                String.class
        );
        assertEquals(201, createResponse.getStatusCode().value());
        assertTrue(createResponse.getBody() != null && createResponse.getBody().contains("id"));

        ResponseEntity<String> searchResponse = restTemplate.getForEntity(
                "/api/documents/search?query=garbage%20collection",
                String.class
        );
        assertEquals(200, searchResponse.getStatusCode().value());
        assertTrue(searchResponse.getBody() != null && searchResponse.getBody().contains("Java Memory"));

        ResponseEntity<Void> updateResponse = restTemplate.exchange(
                "/api/documents/1",
                HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Java records provide immutable data carrier semantics.\"}", headers),
                Void.class
        );
        assertEquals(204, updateResponse.getStatusCode().value());

        ResponseEntity<String> semanticResponse = restTemplate.getForEntity(
                "/api/documents/semantic-search?query=immutable%20records",
                String.class
        );
        assertEquals(200, semanticResponse.getStatusCode().value());
        assertTrue(semanticResponse.getBody() != null && semanticResponse.getBody().contains("title"));
    }
}
