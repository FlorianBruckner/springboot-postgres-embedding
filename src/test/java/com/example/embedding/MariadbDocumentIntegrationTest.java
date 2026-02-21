package com.example.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MariadbDocumentIntegrationTest {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4")
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
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("sample-loader.enabled", () -> "false");
        registry.add("app.database.vendor", () -> "mariadb");

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/mariadb");

        registry.add("spring.ai.openai.base-url", () -> "http://" + embeddingApi.getHost() + ":" + embeddingApi.getMappedPort(8080));
        registry.add("spring.ai.openai.embedding.options.model", () -> "test-embedding-model");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration");

        registry.add("spring.ai.vectorstore.pgvector.enabled", () -> "false");
        registry.add("spring.ai.vectorstore.mariadb.enabled", () -> "true");
        registry.add("spring.ai.vectorstore.mariadb.initialize-schema", () -> "true");
        registry.add("spring.ai.vectorstore.mariadb.dimensions", () -> "1536");
    }

    @LocalServerPort
    int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldCreateUpdateAndSearchDocumentsUsingMariadbRepository() throws IOException, InterruptedException {
        HttpResponse<String> createResponse = sendJson("POST", "/api/documents",
                "{\"title\":\"Java Memory\",\"content\":\"Java manages heap memory and garbage collection efficiently.\"}");
        assertEquals(HttpStatus.CREATED.value(), createResponse.statusCode());
        assertTrue(createResponse.body().contains("id"));

        HttpResponse<String> searchResponse = send("GET", "/api/documents/search?query=garbage%20collection");
        assertEquals(HttpStatus.OK.value(), searchResponse.statusCode());
        assertTrue(searchResponse.body().contains("Java Memory"));

        HttpResponse<String> updateResponse = sendJson("PUT", "/api/documents/1",
                "{\"content\":\"Java records provide immutable data carrier semantics.\"}");
        assertEquals(HttpStatus.NO_CONTENT.value(), updateResponse.statusCode());

        HttpResponse<String> semanticResponse = send("GET", "/api/documents/semantic-search?query=immutable%20records");
        assertEquals(HttpStatus.OK.value(), semanticResponse.statusCode());
        assertTrue(semanticResponse.body().contains("title"));
    }

    private HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
