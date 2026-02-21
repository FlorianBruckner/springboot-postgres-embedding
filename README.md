# springboot-postgres-embedding

Spring Boot (Maven) web app that stores large text documents (up to 10 MB), generates embeddings using an OpenAI-compatible endpoint, and supports keyword + semantic search with pluggable repository implementations for PostgreSQL and MySQL.

## Features
- Store/retrieve up to 10 MB text in SQL databases.
- Flyway migrations per vendor (`postgres` / `mysql`).
- On document create/update, embeddings are recalculated via configurable API endpoint.
- Simple Thymeleaf UI at `/` for keyword and semantic querying.
- Seeds up to ~1000 random German Wikipedia articles at startup if the table is empty.
- Integration testing with Testcontainers for both PostgreSQL and MySQL, plus a containerized embedding API.

## Configuration
Set in `src/main/resources/application.yml`:

- `app.database.vendor` (`postgres` or `mysql`)
- `spring.datasource.*`
- `embedding.api.base-url`
- `embedding.api.path`
- `embedding.api.model`
- `embedding.api.api-key`
- `embedding.api.dimensions`
- `sample-loader.enabled` (optional, default: `true`)

## Run
```bash
mvn spring-boot:run
```

## Test
```bash
mvn test
```
