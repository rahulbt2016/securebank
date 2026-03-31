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

### Securing the Registration Endpoint

- **Open registration is a vulnerability** — Allowing any client to pass a `role` field (e.g. `ADMIN`) in the register request body means an attacker can self-elevate to any role. The client should never be trusted to choose their own role.
- **Public registration hardcodes `CUSTOMER`** — The `RegisterRequest` DTO has no `role` field. The service layer calls `user.setRole(Role.CUSTOMER)` unconditionally — the client cannot influence it regardless of what they send.
- **Separate admin endpoint for staff provisioning** — `POST /api/v1/auth/admin/create-user` accepts a `role` field but is locked behind `hasRole("ADMIN")` in `SecurityConfig`. Only an authenticated ADMIN can create TELLER/MANAGER/ADMIN accounts. This mirrors how real banks provision staff — IT/HR systems create staff accounts, customers self-register.
- **Rule ordering in `SecurityFilterChain`** — The admin rule (`.requestMatchers("/api/v1/auth/admin/**").hasRole("ADMIN")`) must be declared **before** the public permit rule. Spring Security evaluates rules top-to-bottom; first match wins. If the broad `permitAll` rule came first, the admin rule would never be reached.
- **401 vs 403 for unauthenticated requests** — Spring Security's default `AuthenticationEntryPoint` for stateless APIs is `Http403ForbiddenEntryPoint`, which returns 403 even for completely unauthenticated requests. This is semantically wrong — 401 means "authenticate first", 403 means "you're authenticated but not allowed". Fix: configure `.exceptionHandling(ex -> ex.authenticationEntryPoint(...))` to return 401.
- **Anonymous users and `AccessDeniedException`** — Spring's `AnonymousAuthenticationFilter` sets an `AnonymousAuthenticationToken` on every request with no credentials. When this anonymous user hits a protected endpoint, Spring throws `AccessDeniedException` (not `AuthenticationException`), which is why the default behaviour is 403. The custom `AuthenticationEntryPoint` intercepts this and returns 401 instead.

### Resource-Level Authorization (IDOR Protection)

- **IDOR (Insecure Direct Object Reference)** — OWASP Top 10 vulnerability. An authenticated user guesses or knows a resource ID (e.g. another customer's account UUID) and accesses it directly. Role-based checks alone don't prevent this — a CUSTOMER role is valid for their own accounts, but must not grant access to all accounts.
- **Three layers of authorization** — (1) Authentication: is the caller who they claim to be? (JWT signature). (2) Role-based: does the caller's role permit this endpoint? (`hasRole` in `SecurityConfig`). (3) Resource-level: does the caller own this specific resource? (`requireOwnershipOrStaff()` in the service layer). All three are needed; layers 1 and 2 don't prevent IDOR.
- **`AuthenticatedUser` record as the JWT principal** — The filter extracts `userId`, `email`, and `role` from the JWT and constructs an `AuthenticatedUser` record stored as the `UsernamePasswordAuthenticationToken` principal. Controllers inject it via `@AuthenticationPrincipal AuthenticatedUser caller` and pass it to the service. The service never trusts a userId from the request body or path — it reads it from the verified JWT principal.
- **`requireOwnershipOrStaff()` pattern** — A private helper in `AccountService` that throws `ForbiddenException` if the caller is a CUSTOMER and the resource's `customerId` doesn't match the caller's `userId`. Staff roles (TELLER, MANAGER, ADMIN) pass through. Called at the start of every read/write operation that touches a specific customer's data.
- **`isStaff()` helper on the principal** — Defined on `AuthenticatedUser` to encapsulate the staff role set. Instead of sprinkling `"TELLER".equals(role) || "MANAGER".equals(role)...` across the codebase, callers check `caller.isStaff()`. The definition lives in one place, easy to update when roles change.
- **`ForbiddenException` → 403** — Distinct from `UnauthorizedException` (401). 401 means "not authenticated". 403 means "authenticated but not permitted". IDOR violations are 403 because the caller is a valid authenticated user — they just don't own that resource.
- **`searchAccounts` forced filter** — Customers can't bypass the filter by passing a different `customerId` as a query param. The service overwrites it: `customerId = caller.userId()`. This means a CUSTOMER always gets back only their own accounts, regardless of what they sent.
- **Testing with `SecurityMockMvcRequestPostProcessors.authentication()`** — `@WithMockUser` sets a `UserDetails` as principal, but our controllers inject `AuthenticatedUser`. When Spring tries to inject the wrong type, the value is null. Fix: build a real `UsernamePasswordAuthenticationToken` carrying an `AuthenticatedUser` and pass it via `.with(authentication(...))` in `mockMvc.perform(...)`. Dedicated `adminAuth()` and `customerAuth(UUID)` helper methods create the correct tokens.

### Rate Limiting with Redis

- **Why Redis for rate limiting** — Rate limit checks must be sub-millisecond (every login request hits them). Redis stores counters in RAM, making reads/writes microsecond-fast. It also has native TTL support — keys auto-delete after a set time with no cleanup code needed. Using PostgreSQL for this would be unnecessarily slow and add DB load.
- **Redis data structures** — Redis is not just a key-value store. It supports Strings, Hashes, Lists, Sets, Sorted Sets, and Streams. We use **String** for counters because Redis's `INCR` command atomically increments a string value as an integer — no race conditions under concurrent traffic.
- **Fixed-window counter pattern** — Each email gets a Redis key `auth:login:attempts:{email}` with a TTL of 15 minutes. `INCR` increments the counter on each failure. TTL is set only on the **first** increment (when `count == 1`) so the 15-minute window starts from the first failure, not from every subsequent attempt. After 5 failures, the next attempt is rejected with 429.
- **`Retry-After` header** — The 429 response includes a `Retry-After` header containing the remaining TTL in seconds from Redis (`getExpire(key, SECONDS)`). This is a standard HTTP header — well-behaved clients read it and wait before retrying. The value also appears in the response body message for human-readable APIs.
- **Per-email vs per-IP** — Tracking by email directly protects the account being targeted. IP-based limiting is a complementary layer better placed at the API Gateway/load balancer level (Phase 6) where `X-Forwarded-For` is reliably available. Behind a proxy, all requests appear to come from the same IP, making service-level IP limiting unreliable.
- **Failure recording logic** — Unknown email counts as a failure (prevents cheap user enumeration). Disabled account does NOT count (admin action, not a brute-force scenario). Successful login clears the counter so a user who previously failed doesn't stay locked out.
- **`StringRedisTemplate`** — Spring's Redis client that serializes both keys and values as plain UTF-8 strings. Auto-created by Spring Boot when `spring-boot-starter-data-redis` is on the classpath and `spring.data.redis.host/port` are configured. No `@Configuration` class needed.
- **Disabling Redis in tests** — `@WebMvcTest` is a web-slice test and doesn't auto-configure Redis. Pure Mockito unit tests have no Spring context at all. `application-test.yml` explicitly excludes `RedisAutoConfiguration` and `RedisRepositoriesAutoConfiguration` as a safety net for any future `@SpringBootTest`.

### Component Scan Pitfall — Multi-Module Spring Boot

- **`@SpringBootApplication` only scans its own package tree** — `@SpringBootApplication` on `com.securebank.auth.AuthServiceApplication` scans `com.securebank.auth.*` only. `GlobalExceptionHandler` lives in `com.securebank.common.exception` — a sibling package tree, not a sub-package. It was on the classpath but Spring never registered it as a bean, so ALL custom exception handlers (401, 403, 404, 409, 429...) were silently broken in the running app. Tests worked because `@WebMvcTest` used `@Import(GlobalExceptionHandler.class)` explicitly.
- **Fix: `scanBasePackages`** — `@SpringBootApplication(scanBasePackages = {"com.securebank.auth", "com.securebank.common"})` tells Spring to scan both package trees. Common-lib beans (`GlobalExceptionHandler`) are now discovered and registered. The same pattern was already correctly applied in `AccountServiceApplication`.
- **How to spot this in future** — If custom exception handlers work in tests but not in the running app, check `scanBasePackages`. The test's `@Import` masks the issue.

### Swagger Bearer Auth

- **`@SecurityScheme`** — SpringDoc OpenAPI annotation that declares an authentication mechanism for the whole API. `type = HTTP, scheme = bearer, bearerFormat = JWT` adds the **Authorize 🔓** button to Swagger UI. Place it on a `@Configuration` class alongside `@OpenAPIDefinition`.
- **`@SecurityRequirement(name = "bearerAuth")`** — Applies the declared scheme to specific endpoints or an entire controller. In Swagger UI, matching endpoints show a padlock icon 🔒 and automatically include the `Authorization: Bearer` header when the user has authorized. Apply at class level for controllers where all endpoints need auth; at method level for mixed controllers (e.g. `AuthController` where only the admin endpoint is secured).
- **Workflow** — Hit `POST /auth/login` in Swagger UI → copy `accessToken` from response → click Authorize → paste token (no `Bearer ` prefix needed, Swagger adds it) → all secured endpoints in that session include the header automatically.

---

## Phase 3 — Complete ✓

---

## Phase 4 — Observability & Data Protection (In Progress)

### Audit Logging

- **Why audit logging is mandatory in banking** — Regulations like SOX, PCI-DSS, GDPR, and OSFI require financial institutions to maintain tamper-evident records of all data mutations. Audit logs answer "who changed what, to which resource, and when" — enabling fraud detection, compliance audits, incident response, and legal non-repudiation.
- **Write-once entity design** — `AuditLog` does not extend `BaseEntity`. `BaseEntity` includes `@Version` (optimistic locking) and `updatedAt` — neither makes sense for an immutable record. Audit entries are never updated; adding those fields would be misleading and wasteful.
- **`@PrePersist` for immutable timestamp** — `occurredAt` is set in a `@PrePersist` method (not via JPA Auditing) and marked `updatable = false`. This guarantees the timestamp is set exactly once at insert time and can never be overwritten by an accidental `save()` later.
- **`Propagation.REQUIRES_NEW`** — The audit `log()` method runs in its own independent transaction. If the outer transaction (e.g. `createAccount`) rolls back due to an error after the account is saved, the audit entry still commits. In production you always want a record that the operation was attempted — even if it ultimately failed.
- **Caller identity from JWT, never from the request** — `AuthenticatedUser caller` flows from the JWT (verified by `JwtAuthFilter`) through `@AuthenticationPrincipal` in the controller to the service. The audit record contains a cryptographically verified `userId` and `email` — the client cannot forge it.
- **`updatedFields` tracking on UPDATE** — Rather than recording just "an update happened", the service builds a list of which fields actually changed (`accountHolderName`, `status`, `branchCode`). This granularity is required for meaningful compliance reports.
- **Indexes for audit queries** — Three indexes cover the three access patterns: by entity (all events on account X), by performer (all actions by user Y), and by time descending (most recent events first). Audit tables grow fast; indexes are non-negotiable.
- **`performed_by` is nullable** — System-triggered operations (scheduled jobs, migrations, future event consumers) have no human caller. The column is nullable by design so the schema does not force a fake userId for automated processes.

### Account Ownership on Creation

- **Never trust the client for identity** — Just as we hardcode `CUSTOMER` role on registration (the client cannot self-elevate), we never let the client decide who owns an account. `customerId` must come from a verified source, not a free-form request field.
- **Role-based `customerId` resolution** — For CUSTOMER callers, `customerId` is always forced to `caller.userId()` from the JWT — the client cannot provide it. For STAFF callers, `customerId` is required in the request body (they are opening an account on behalf of a specific customer). This is enforced in the service layer, not the controller.
- **`@Mapping(target = "customerId", ignore = true)` in MapStruct** — The mapper no longer copies `customerId` from the request DTO to the entity. Ownership is set explicitly in the service after the mapper runs. This keeps the mapping layer free from business logic.
- **Why not validate `customerId` against `auth-service`?** — Account-service has no direct DB access to the `users` table (separate service, separate schema). Cross-service validation via HTTP would introduce coupling and a failure point. For now, staff-provided `customerId` is trusted (they are authenticated + authorized). Full validation via event-driven customer sync is deferred to Phase 6 (Kafka).

### Spring Security Error Responses

- **`GlobalExceptionHandler` never sees 401/403 from Spring Security** — Spring Security's filter chain rejects unauthorized/forbidden requests before the request ever reaches a controller. `@RestControllerAdvice` only handles exceptions thrown inside controllers — it is bypassed entirely for filter-level rejections. This is why custom exception handlers alone are not enough.
- **Two separate hooks for security errors** — Spring Security provides two distinct extension points: `authenticationEntryPoint` (fires on 401 — no token or invalid token) and `accessDeniedHandler` (fires on 403 — valid token but insufficient role). Both must be configured independently to get consistent error responses.
- **`sendError()` vs writing the response directly** — `response.sendError(401, "message")` delegates to the servlet container's default error page, which returns HTML or a plain string with no JSON body. Writing to `response.getWriter()` directly gives full control over the content type and body — the correct approach for a REST API.
- **`@EnableJpaAuditing` must be in every service** — `AuditingEntityListener` on `BaseEntity` is activated by `@EnableJpaAuditing`. This annotation is not inherited from common-lib — each Spring Boot application must declare it in its own `@Configuration` class. Without it, `@CreatedDate` and `@LastModifiedDate` fields silently stay null.

---
