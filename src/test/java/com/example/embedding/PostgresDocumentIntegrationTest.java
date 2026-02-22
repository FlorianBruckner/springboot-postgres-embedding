package com.example.embedding;

import com.example.AbstractOllamaTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PostgresDocumentIntegrationTest extends AbstractOllamaTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");


    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("sample-loader.enabled", () -> "false");
        registry.add("app.database.vendor", () -> "postgres");

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgres");

        registry.add("spring.ai.openai.base-url", () -> ollama.getEndpoint() /*"http://" + ollama.getHost() + ":" + ollama.getMappedPort(8080)*/);
        registry.add("spring.ai.openai.embedding.options.model", () -> "all-minilm");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreAutoConfiguration");

        registry.add("spring.ai.vectorstore.pgvector.enabled", () -> "true");
        registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "true");
        registry.add("spring.ai.vectorstore.pgvector.dimensions", () -> "384");
    }

    @LocalServerPort
    int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldCreateUpdateAndSearchDocumentsUsingContainerizedEmbeddingApi() throws IOException, InterruptedException {
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
