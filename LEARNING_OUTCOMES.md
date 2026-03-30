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

### Optimistic Locking — Integration Testing

- **Why unit tests cannot prove optimistic locking** — `@Version` is enforced by JPA/Hibernate at flush time. A Mockito unit test that mocks the repository never touches Hibernate, so it can't prove the locking actually works. You need a real persistence context against a real (or in-memory) DB.
- **`@DataJpaTest`** — loads only the JPA slice (entities, repositories, `EntityManager`). No web layer, no service beans. Uses an H2 in-memory database automatically. Much faster than `@SpringBootTest`.
- **`Propagation.NOT_SUPPORTED` on the test method** — overrides `@DataJpaTest`'s default "wrap every test in a rolled-back transaction". With `NOT_SUPPORTED`, no transaction wraps the test body, so each `TransactionTemplate.execute()` block runs in its own committed transaction — exactly simulating two separate database sessions.
- **`TransactionTemplate`** — programmatic way to run a lambda in a transaction. Injected via `PlatformTransactionManager`. Use `execute()` when you need a return value, `executeWithoutResult()` when you don't.
- **The lost-update proof** — the test loads the same entity twice (each in its own committed transaction), producing two detached copies both at `version=0`. Session 1 saves (`UPDATE WHERE version=0` → succeeds, version becomes 1). Session 2 tries to save its stale copy (`UPDATE WHERE version=0` → 0 rows affected → Hibernate throws `StaleObjectStateException` → Spring wraps it as `ObjectOptimisticLockingFailureException`).
- **Manual `@AfterEach` cleanup** — because `NOT_SUPPORTED` disables the automatic rollback, inserted rows persist after the test. An `@AfterEach` method using `TransactionTemplate` explicitly deletes the test data so subsequent test runs start clean.

### Query Performance — Composite Indexes

- **How PostgreSQL uses single-column indexes for multi-filter queries** — if `searchAccounts` filtered on `customer_id` AND `status`, PostgreSQL would need to either pick one index and re-check the other condition in memory, or do a costly bitmap-AND of two index scans. A composite index eliminates this.
- **Composite index prefix rule** — PostgreSQL can use any *left-prefix* of a composite index. An index on `(customer_id, status, account_type)` covers:
  - `WHERE customer_id = ?` (prefix of 1)
  - `WHERE customer_id = ? AND status = ?` (prefix of 2)
  - `WHERE customer_id = ? AND status = ? AND account_type = ?` (full key)
  It cannot be used for `WHERE status = ?` alone (no left prefix) — the existing `idx_accounts_status` still serves that case.
- **`EXPLAIN ANALYZE`** — PostgreSQL command that shows the query plan *and* runs the query to report actual row counts and timing. Key things to read: `Seq Scan` (full table scan, usually bad at scale) vs `Index Scan` / `Index Only Scan` (good). `rows=` estimates vs actual tell you if table statistics are stale.
- **V2 migration** — added `idx_accounts_customer_status_type ON accounts (customer_id, status, account_type)`. This replaces what would otherwise be three separate lookups for the most common search pattern (a customer's active accounts of a given type).

---

## Phase 2 — Complete ✓

---

## Phase 3 — Security (Complete ✓)

### JWT Authentication

- **Why JWTs for microservices** — Traditional server-side sessions require every service to query a shared session store on every request. JWTs are self-contained: the token carries the user's identity and role, signed by a secret key. Each service verifies the signature locally — no cross-service calls needed.
- **HS256 (HMAC-SHA256)** — The token is signed with a shared secret key. Both auth-service (signing) and account-service (verifying) must use the same key. In production this secret would come from a secrets manager (AWS Secrets Manager, HashiCorp Vault), never hardcoded.
- **Token structure** — Header (algorithm) + Payload (claims: `sub`=userId, `email`, `role`, `iat`, `exp`) + Signature. The payload is Base64-encoded, not encrypted — never put sensitive data in a JWT claim.
- **Access token lifetime** — Short (15 min). If stolen, it expires quickly. The client uses the refresh token to get a new access token without re-entering credentials.
- **`JwtService` in common-lib** — Marked as optional dependencies so JWT/Security classes don't bleed into services that don't need them. Services that use it re-declare the deps explicitly in their own `pom.xml`.

### Refresh Token Rotation

- **Why persist refresh tokens** — Access tokens are stateless (verified by signature alone, cannot be revoked mid-flight). Refresh tokens need to be revocable. By storing them in DB we can mark one revoked on logout, and detect token reuse (a revoked token being replayed signals a theft).
- **Rotation** — On each `/auth/refresh` call: the old refresh token is revoked and a brand new one is issued. If an attacker steals a refresh token and uses it, the legitimate user's next refresh will fail (their token was already rotated away), giving a clear signal of compromise.
- **`revokeAllByUserId` JPQL** — Bulk-revoke all active tokens for a user (e.g. on password change). Uses `@Modifying` which tells Spring Data the query mutates data, required for `UPDATE`/`DELETE` JPQL.

### Spring Security 6 Configuration

- **`SecurityFilterChain` bean** — Replaces the deprecated `WebSecurityConfigurerAdapter`. Lambda-style DSL: `http.authorizeHttpRequests(auth -> auth.requestMatchers(...).hasRole(...))`. Reads left-to-right: first matching rule wins.
- **`SessionCreationPolicy.STATELESS`** — Tells Spring Security not to create or use HTTP sessions. Every request must carry a JWT. Essential for stateless REST APIs.
- **`csrf(AbstractHttpConfigurer::disable)`** — CSRF attacks exploit browser cookie-based auth. Since we use `Authorization: Bearer` headers (not cookies), CSRF doesn't apply. Disabling it is correct and required for REST APIs.
- **`hasRole` vs `hasAuthority`** — `hasRole("ADMIN")` checks for granted authority `ROLE_ADMIN` (auto-prefixed). `hasAuthority("ROLE_ADMIN")` checks for the exact string. They're equivalent when authority is stored with the `ROLE_` prefix. We store `ROLE_` + roleName in `JwtAuthFilter`.
- **`JwtAuthFilter` — NOT a `@Component`** — If registered as `@Component`, Spring Boot auto-registers it as a raw servlet filter AND adds it to the security chain — double execution. Instead, it's a plain class instantiated explicitly via `@Bean` in `SecurityConfig` and added with `http.addFilterBefore()`.

### Testing with Spring Security

- **`@WebMvcTest` doesn't auto-discover `SecurityConfig`** — `@WebMvcTest` loads a web-slice context. Custom `@Configuration @EnableWebSecurity` classes are not always auto-detected. Without it, Spring Boot's default security (CSRF enabled) takes over — POST requests without a CSRF token return 403. Fix: `@Import(SecurityConfig.class)` in controller tests.
- **`@WithMockUser(roles = "ADMIN")`** — From `spring-security-test`. Sets a mock `UsernamePasswordAuthenticationToken` in the `SecurityContext` before the test runs. `roles = "ADMIN"` creates authority `ROLE_ADMIN`. Applied at class level to cover all tests, with individual methods using lower-privilege roles where role enforcement needs testing.
- **`@TestPropertySource`** — Injects properties into the Spring test `Environment`. Used to provide `jwt.secret` and `jwt.access-token-expiry-ms` to `SecurityConfig` in `@WebMvcTest` tests (where `application.yml` may not be loaded).
- **`UnnecessaryStubbingException`** — Mockito strict mode rejects stubs that are never matched. Triggered when a specific stub (e.g. `save(specificObject)`) is shadowed by a broader stub added later (e.g. `save(any(...))`). Fix: remove the redundant specific stub.

### Auth-Service Architecture

- **Separate Flyway history table** — Each service declares its own `flyway.table` (e.g. `flyway_auth_schema_history`) to avoid collision with the default `flyway_schema_history` used by account-service. Both services share the same PostgreSQL DB in dev; separate history tables let Flyway track each service's migrations independently.
- **`JwtService` as a plain POJO** — No `@Component` annotation. Each service instantiates it as a `@Bean` in its own `SecurityConfig`, passing the JWT secret from `application.yml`. This avoids Spring auto-configuration fighting over a singleton and makes the dependency chain explicit.
- **`ReflectionTestUtils.setField()`** — Used in unit tests to inject `@Value` fields that Mockito cannot inject (Mockito sets `@Mock`/`@InjectMocks` but skips `@Value`). Injects `accessTokenExpiryMs` and `refreshTokenExpiryDays` directly into the service instance before tests run.
- **Role hierarchy in JWT claims** — The `role` claim stores the enum name (e.g. `ADMIN`). `JwtAuthFilter` prefixes it to `ROLE_ADMIN` when building the `GrantedAuthority`. This bridges Spring Security's `hasRole("ADMIN")` convention (which expects the `ROLE_` prefix internally) with the cleaner claim value stored in the token.

---

## Phase 3 — Complete ✓

---
