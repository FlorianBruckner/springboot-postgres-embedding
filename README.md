# springboot-postgres-embedding

Spring Boot (Maven) web app that stores large text documents (up to 10 MB), generates embeddings using Spring AI, and supports keyword search, semantic search, and a RAG answer endpoint.

## Features
- Store/retrieve up to 10 MB text in SQL databases.
- Flyway migrations per vendor (`postgres` / `mysql`).
- Embedding generation through Spring AI OpenAI-compatible integration.
- Keyword and semantic search endpoints for document retrieval.
- RAG pipeline endpoint (`POST /api/rag/ask`) that uses semantic retrieval + LLM answer generation.
- Simple Thymeleaf UI at `/` for keyword and semantic querying.
- Seeds up to ~100 random German Wikipedia articles at startup if the table is empty.
- Integration testing with Testcontainers for both PostgreSQL and MySQL, plus a containerized embedding API.

## Configuration
Set in `src/main/resources/application.yml`:

- `app.database.vendor` (`postgres` or `mysql`)
- `spring.datasource.*`
- `spring.ai.openai.base-url`
- `spring.ai.openai.api-key`
- `spring.ai.openai.embedding.options.model`
- `spring.ai.openai.chat.options.model`
- `sample-loader.enabled` (optional, default: `true`)

## Run
```bash
mvn spring-boot:run
```

## Test
```bash
mvn test
```
