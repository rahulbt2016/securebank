# SecureBank — Learning Outcomes

A living document summarizing key concepts and patterns learned after each phase of the SecureBank project.

---

## Phase 1 — Foundations & Project Setup

### Project Structure
- Maven **multi-module** project (`parent pom` → `common-lib` + `account-service`)
- `common-lib` is a plain JAR (no `spring-boot-maven-plugin`), consumed by services as a dependency
- Parent POM centralizes dependency versions; child modules inherit without re-declaring versions

### REST API Design
- Versioned endpoints (`/api/v1/...`) for forward compatibility
- Standard response envelope (`ApiResponse<T>`) wraps all responses so clients get a predictable structure
- DTOs decouple the API contract from JPA entities — never expose entities directly
- `PagedResponse<T>` wraps paginated list results with metadata (`totalElements`, `totalPages`, `last`)
- **Pageable** — Spring auto-converts query params (`?page=0&size=20&sort=createdAt,desc`) into a `Pageable` object via `PageableHandlerMethodArgumentResolver`. `@PageableDefault` sets fallback values when params are omitted.

### Data Layer
- **UUID primary keys** over auto-increment — unpredictable (security) and work without a centralized sequence (distributed systems)
- **BigDecimal for money** — `float`/`double` use binary floating-point and cannot exactly represent decimals like `0.10`. Never use them for financial values.
- **`@Version` optimistic locking** — JPA checks the version field on every UPDATE and throws `OptimisticLockException` if another transaction modified the row first, preventing lost updates
- **JPA Auditing** (`@CreatedDate`, `@LastModifiedDate`) auto-populates timestamps; enabled via `@EnableJpaAuditing` in a `@Configuration` class
- `ddl-auto: update` auto-creates/updates tables from entities in dev (replaced by Flyway migrations in Phase 2)

### Business Logic Patterns
- `@Transactional(readOnly = true)` as class-level default — hints the DB to optimize and skips Hibernate dirty checking. Write methods override with `@Transactional`.
- `open-in-view: false` — disables the Open Session in View anti-pattern, forcing all DB loading into the service layer where it belongs
- **MapStruct** generates compile-time entity ↔ DTO mapping code — type-safe, no reflection overhead, compile warnings if fields go unmapped

### Exception Handling
- Centralized via `@RestControllerAdvice` (`GlobalExceptionHandler`) — no controller catches exceptions manually
- Three custom exceptions map to standard HTTP status codes:
  - `ResourceNotFoundException` → 404
  - `DuplicateResourceException` → 409
  - `BusinessRuleException` → 422
- Bean Validation (`@Valid`) errors are caught and returned as structured field-level error maps
- Catch-all `Exception` handler returns 500 — stack trace is logged server-side, never exposed to the client

### Testing
- **Unit tests** — `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`. No Spring context loaded, fast execution. BDD-style: `given(...).willReturn(...)`
- **Integration tests** — `@WebMvcTest` loads only the web slice (no JPA, no real DB). MockMvc sends HTTP requests through the full controller stack.
- `@Import(GlobalExceptionHandler.class)` is required in `@WebMvcTest` because the handler lives in `common-lib` outside the auto-scanned package
- `application-test.yml` profile switches the datasource to H2 in-memory DB so tests never require a running PostgreSQL instance
- `@Nested` classes group related tests by method under test; `@DisplayName` makes test reports readable

### Infrastructure
- **Docker Compose** — defines PostgreSQL 16 + Redis 7 as local dev infrastructure in a single `docker-compose.yml`
- Docker Desktop (daemon) must be running before any `docker-compose` commands
- `docker-compose up -d` starts containers in the background; `docker-compose down` stops and removes them while preserving named volumes
- `docker-compose down -v` also deletes volumes — use only for a clean slate, as it wipes all DB data
- Named volumes (`postgres_data`, `redis_data`) persist data across container restarts

### Tooling
- **Postman collection** with all 9 endpoints, request bodies, query params, and test assertions (managed via Postman MCP — lives in SparkUp Java workspace)
- Collection variables (`accountId`, `customerId`, `baseUrl`) allow requests to chain — Create Account captures the returned `id` and subsequent requests use it automatically
- **Swagger UI** auto-generated at `/swagger-ui.html` via SpringDoc OpenAPI; OpenAPI JSON spec at `/api-docs`

---

## Phase 2 — Database & Persistence (In Progress)

### Flyway — Versioned Database Migrations

- **Why Flyway replaces `ddl-auto: update`** — Hibernate's `update` mode guesses schema changes and can silently drop columns on renames. It is not repeatable across environments. Flyway uses explicit, versioned SQL scripts that run in order, exactly once, on every environment.
- **`flyway_schema_history` table** — Flyway creates this automatically on first run. It records every migration that has been applied (version, script name, checksum, timestamp, success flag). On every app startup, Flyway compares scripts on disk to this table — already-run scripts are skipped, new ones are applied.
- **Checksum protection** — Flyway checksums each script when it runs. If you edit a script after it has been applied, Flyway throws an error and refuses to start. This enforces immutability of migration history. To change something, write a new `V2` migration instead.
- **Naming convention** — `V{version}__{description}.sql` (double underscore). Flyway sorts and runs these in version order.
- **`ddl-auto: validate`** — With Flyway owning the schema, Hibernate is switched from `update` to `validate`. It only checks that the DB schema matches the entities on startup — it never modifies the schema. Two layers of safety: Flyway owns the shape, Hibernate verifies it.
- **Baselining** — When adopting Flyway on an existing DB (tables already created by Hibernate), use `baseline-on-migrate: true` + `baseline-version` to tell Flyway "everything up to this point is already done". For dev, the simpler approach is to drop existing tables and let Flyway create them fresh from V1.
- **Tests** — Flyway is disabled for tests (`flyway.enabled: false` in `application-test.yml`). H2 in-memory DB continues to use `ddl-auto: create-drop` so Hibernate creates the schema from entities. Avoids maintaining PostgreSQL-specific SQL migrations for H2.
- **Indexes in migrations** — Hibernate does not create indexes automatically. Owning migrations in SQL means you can add indexes from day one (`CREATE INDEX`), giving full control over query performance.

### Custom Queries — JPQL and Native SQL

- **JPQL (Java Persistence Query Language)** — Object-oriented query language that references Java entity class and field names, not table/column names. Works across any DB Hibernate supports. Defined with `@Query` on repository methods.
- **The `IS NULL OR` pattern** — Enables optional filters in a single JPQL query: `WHERE (:param IS NULL OR a.field = :param)`. Passing `null` for a parameter skips that condition. One query handles all filter combinations without building query strings dynamically.
- **Native SQL queries** — `@Query(nativeQuery = true)` executes raw SQL against the specific database. Useful for aggregates, window functions, and DB-specific features that JPQL cannot express. Tied to the DB dialect — not portable.
- **Projection interfaces** — When a native query returns a custom result set (not a full entity), define an interface with getters matching the SQL column aliases. Spring Data generates a proxy at runtime that implements it. Zero boilerplate, fully typed, no need for a `@Entity` class.
  ```java
  // SQL alias "accountCount" → getAccountCount()
  public interface CustomerBalanceSummary {
      Long getAccountCount();
      BigDecimal getTotalBalance();
  }
  ```
- **JPQL vs Native SQL** — Use JPQL for most queries (portable, object-oriented). Use native SQL for aggregates, window functions, or when you need DB-specific features. Native queries require projection interfaces or `Object[]` for custom result mapping.
- **Null safety in projections** — `SUM`, `MAX`, `MIN` return `NULL` from SQL when there are no rows (e.g. customer with no accounts). Always null-check projection values in the service layer and provide sensible defaults (`BigDecimal.ZERO`). `COUNT(*)` always returns a number, never NULL.

### What's Still Remaining in Phase 2
- Optimistic locking testing — write tests that prove `@Version` prevents concurrent lost updates
- Query performance — `EXPLAIN ANALYZE` on key queries, additional indexing if needed
