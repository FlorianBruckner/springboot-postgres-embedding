# springboot-postgres-embedding

Spring Boot (Maven) web app that stores large text documents (up to 10 MB), uses Spring AI Vector Databases for semantic retrieval, and supports keyword search plus a RAG answer endpoint.

## Features
- Store/retrieve up to 10 MB text in SQL databases.
- Flyway migrations per vendor (`postgres` / `mariadb`) for application document metadata.
- Semantic indexing/search through Spring AI vector stores:
  - Pgvector for PostgreSQL
  - MariaDB Vector Store for MariaDB
- Embedding and chat generation through Spring AI OpenAI-compatible integration.
- Keyword and semantic search endpoints for document retrieval.
- RAG pipeline endpoint (`POST /api/rag/ask`) that uses semantic retrieval + LLM answer generation.
- Simple Thymeleaf UI at `/` for keyword and semantic querying.
- Seeds up to ~100 random German Wikipedia articles at startup if the table is empty.

## Configuration
Set in `src/main/resources/application.yml`:

- `app.database.vendor` (`postgres` or `mariadb`)
- `spring.datasource.*`
- `spring.ai.openai.base-url`
- `spring.ai.openai.api-key`
- `spring.ai.openai.embedding.options.model`
- `spring.ai.openai.chat.options.model`
- `spring.ai.vectorstore.pgvector.*`
- `spring.ai.vectorstore.mariadb.*`
- `sample-loader.enabled` (optional, default: `true`)

## Run
```bash
mvn spring-boot:run
```

## Test
```bash
mvn test
```
