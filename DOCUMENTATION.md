# SecureBank - Digital Banking Platform

A production-grade digital banking backend system built with Java 21 and Spring Boot 3.4.1. This project mirrors real-world banking microservices used at Canadian banks (TD, RBC, Scotiabank, BMO, CIBC) and covers the full Java/Spring ecosystem relevant to senior developer roles.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Module Documentation](#module-documentation)
  - [common-lib](#common-lib)
  - [account-service](#account-service)
- [API Reference](#api-reference)
- [Request/Response Examples](#requestresponse-examples)
- [Error Handling](#error-handling)
- [Validation Rules](#validation-rules)
- [Database Schema](#database-schema)
- [Testing](#testing)
- [Configuration Reference](#configuration-reference)
- [Design Decisions & Patterns](#design-decisions--patterns)
- [Development Roadmap](#development-roadmap)

---

## Architecture Overview

SecureBank follows a microservices architecture with a shared library for cross-cutting concerns:

```
                    ┌──────────────────┐
                    │   API Gateway    │  (Planned - Phase 6)
                    └────────┬─────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
┌────────▼───────┐  ┌───────▼────────┐  ┌───────▼──────────┐
│ account-service│  │ auth-service   │  │transaction-service│
│   (Port 8081)  │  │  (Planned)     │  │    (Planned)      │
└────────┬───────┘  └────────────────┘  └──────────────────┘
         │
         │  depends on
         ▼
┌──────────────────┐
│   common-lib     │  Shared DTOs, exceptions, base entity
└──────────────────┘
```

**Layered Architecture within each service:**

```
Controller  →  receives HTTP requests, validates input, delegates to service
    │
Service     →  business logic, transaction management, orchestration
    │
Repository  →  data access via Spring Data JPA
    │
Entity      →  JPA entity mapped to database table
```

DTOs (Data Transfer Objects) are used to decouple the API contract from the internal entity model. MapStruct handles the mapping between entities and DTOs at compile time.

---

## Technology Stack

| Category       | Technology                  | Version   | Purpose                                  |
|----------------|-----------------------------|-----------|------------------------------------------|
| Language       | Java                        | 21 (LTS)  | Core language                            |
| Framework      | Spring Boot                 | 3.4.1     | Application framework                   |
| Cloud          | Spring Cloud                | 2024.0.0  | Microservices infrastructure (planned)   |
| ORM            | Spring Data JPA / Hibernate | (managed) | Database access                          |
| Validation     | Bean Validation (Jakarta)   | (managed) | Input validation                         |
| Database       | PostgreSQL                  | 16        | Primary relational database              |
| Test Database  | H2                          | (managed) | In-memory database for tests             |
| Cache          | Redis                       | 7         | Session management, rate limiting (planned) |
| API Docs       | SpringDoc OpenAPI           | 2.7.0     | Swagger UI & OpenAPI spec               |
| DTO Mapping    | MapStruct                   | 1.6.3     | Compile-time entity-to-DTO mapping       |
| Boilerplate    | Lombok                      | (managed) | Reduces getter/setter/builder boilerplate|
| Testing        | JUnit 5 + Mockito           | (managed) | Unit and integration testing             |
| Assertions     | AssertJ                     | (managed) | Fluent test assertions                   |
| Containers     | Docker Compose              | 3.9       | Local infrastructure (Postgres, Redis)   |
| Build          | Maven                       | 3.9.9     | Build tool (wrapper included)            |
| Monitoring     | Spring Actuator             | (managed) | Health checks, metrics endpoints         |

---

## Project Structure

```
securebank/
├── pom.xml                             # Parent POM - dependency management for all modules
├── docker-compose.yml                  # PostgreSQL 16 + Redis 7
├── .gitignore
├── mvnw.cmd                            # Maven wrapper (no global Maven install needed)
├── .mvn/wrapper/
│   └── maven-wrapper.properties        # Specifies Maven 3.9.9
│
├── common-lib/                         # Shared library (plain JAR, not a Spring Boot app)
│   ├── pom.xml
│   └── src/main/java/com/securebank/common/
│       ├── dto/
│       │   ├── ApiResponse.java        # Standard API response envelope
│       │   ├── ErrorDetail.java        # Structured error information
│       │   └── PagedResponse.java      # Pagination wrapper for list endpoints
│       ├── entity/
│       │   └── BaseEntity.java         # Abstract base: UUID PK, version, audit timestamps
│       └── exception/
│           ├── ResourceNotFoundException.java   # → HTTP 404
│           ├── DuplicateResourceException.java  # → HTTP 409
│           ├── BusinessRuleException.java       # → HTTP 422
│           └── GlobalExceptionHandler.java      # @RestControllerAdvice - central error handling
│
├── account-service/                    # Spring Boot microservice
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/securebank/account/
│       │   │   ├── AccountServiceApplication.java   # Entry point
│       │   │   ├── config/
│       │   │   │   └── JpaConfig.java               # Enables @CreatedDate / @LastModifiedDate
│       │   │   ├── entity/
│       │   │   │   ├── Account.java                 # JPA entity → "accounts" table
│       │   │   │   ├── AccountType.java             # Enum: CHEQUING, SAVINGS, TFSA, RRSP, BUSINESS
│       │   │   │   └── AccountStatus.java           # Enum: ACTIVE, INACTIVE, FROZEN, CLOSED
│       │   │   ├── dto/
│       │   │   │   ├── CreateAccountRequest.java    # Input DTO with Bean Validation
│       │   │   │   ├── UpdateAccountRequest.java    # Partial update DTO
│       │   │   │   └── AccountResponse.java         # Output DTO
│       │   │   ├── mapper/
│       │   │   │   └── AccountMapper.java           # MapStruct interface (generates impl at compile)
│       │   │   ├── repository/
│       │   │   │   └── AccountRepository.java       # Spring Data JPA interface
│       │   │   ├── service/
│       │   │   │   └── AccountService.java          # Business logic layer
│       │   │   └── controller/
│       │   │       └── AccountController.java       # REST API endpoints
│       │   └── resources/
│       │       ├── application.yml                  # Main config (PostgreSQL, port 8081)
│       │       └── application-test.yml             # Test profile (H2 in-memory DB)
│       └── test/java/com/securebank/account/
│           ├── service/
│           │   └── AccountServiceTest.java          # 7 unit tests (Mockito)
│           └── controller/
│               └── AccountControllerTest.java       # 4 MockMvc integration tests
```

---

## Prerequisites

| Tool             | Required Version | Notes                                  |
|------------------|------------------|----------------------------------------|
| JDK              | 21+              | Must be installed and JAVA_HOME set    |
| Docker Desktop   | Latest           | For PostgreSQL and Redis containers     |
| Git              | Latest           | Version control                        |
| Postman / Bruno  | Latest           | API testing (optional)                 |

Maven is **not** required globally -- the project includes a Maven wrapper (`mvnw.cmd`).

---

## Getting Started

### 1. Start Infrastructure

```bash
cd C:\ai-projects\securebank
docker-compose up -d
```

This starts:
- **PostgreSQL 16** on port `5432` (database: `securebank`, user: `securebank`, password: `securebank_dev`)
- **Redis 7** on port `6379`

Verify containers are healthy:
```bash
docker-compose ps
```

### 2. Build the Project

**On Windows (Git Bash):**
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="/c/tools/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$PATH"
cd /c/ai-projects/securebank
mvn clean compile
```

**On Windows (CMD/PowerShell) with Maven installed:**
```cmd
set JAVA_HOME=C:\Program Files\Java\jdk-21
cd C:\ai-projects\securebank
mvn clean compile
```

### 3. Run Tests

```bash
mvn test
```

Expected output: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`

### 4. Start the Account Service

```bash
mvn spring-boot:run -pl account-service
```

The service starts on **http://localhost:8081**.

### 5. Access Swagger UI

Open in your browser: **http://localhost:8081/swagger-ui.html**

This provides an interactive API documentation page where you can try out all endpoints.

---

## Module Documentation

### common-lib

A plain Java library (not a runnable Spring Boot app) that provides shared components consumed by all services.

**Important:** This module does **not** use `spring-boot-maven-plugin` because it is a library JAR, not an executable.

#### ApiResponse\<T\>

The standard envelope for all API responses. Every endpoint returns this wrapper so clients get a predictable structure.

```java
public class ApiResponse<T> {
    boolean success;        // true for 2xx, false for errors
    T data;                 // the payload (null on errors)
    ErrorDetail error;      // error details (null on success)
    Instant timestamp;      // when the response was generated
}
```

**Factory methods:**
- `ApiResponse.ok(data)` -- wraps successful responses
- `ApiResponse.created(data)` -- alias for ok (used with HTTP 201)
- `ApiResponse.error(status, code, message)` -- wraps error responses
- `ApiResponse.error(errorDetail)` -- wraps error responses with field-level details

**Why a standard wrapper?** In banking, API governance is strict. Every team must follow the same response format so API consumers (mobile apps, other services) can parse responses uniformly. This is a pattern used across TD, RBC, and other major banks.

#### ErrorDetail

Structured error information returned within `ApiResponse.error`.

```java
public class ErrorDetail {
    int status;                       // HTTP status code
    String code;                      // Machine-readable error code (e.g., "RESOURCE_NOT_FOUND")
    String message;                   // Human-readable description
    Map<String, String> fieldErrors;  // Per-field validation errors (null if not applicable)
}
```

#### PagedResponse\<T\>

Wraps paginated list results.

```java
public class PagedResponse<T> {
    List<T> content;      // items in the current page
    int page;             // current page number (0-based)
    int size;             // page size
    long totalElements;   // total items across all pages
    int totalPages;       // total number of pages
    boolean last;         // true if this is the last page
}
```

#### BaseEntity

Abstract JPA entity that all domain entities extend. Provides:

| Field       | Type      | Purpose                                                      |
|-------------|-----------|--------------------------------------------------------------|
| `id`        | `UUID`    | Primary key. Auto-generated. UUIDs prevent sequential ID guessing (security) and work in distributed systems without DB coordination. |
| `version`   | `Long`    | Optimistic locking. JPA checks this on UPDATE and throws `OptimisticLockException` if another transaction modified the row. |
| `createdAt` | `Instant` | Auto-populated by JPA Auditing on INSERT. Immutable.         |
| `updatedAt` | `Instant` | Auto-populated by JPA Auditing on every UPDATE.              |

Enabled by `@EnableJpaAuditing` in `JpaConfig.java`.

#### Exception Classes

| Exception                    | HTTP Status | Error Code           | When Used                                              |
|------------------------------|-------------|----------------------|--------------------------------------------------------|
| `ResourceNotFoundException`  | 404         | `RESOURCE_NOT_FOUND` | Entity not found by ID or unique field                |
| `DuplicateResourceException` | 409         | `DUPLICATE_RESOURCE` | Creating a resource that already exists (e.g., duplicate email) |
| `BusinessRuleException`      | 422         | Custom (e.g., `ACCOUNT_CLOSED`) | Business rule violation (e.g., closing account with balance) |

#### GlobalExceptionHandler

A `@RestControllerAdvice` that intercepts exceptions from all controllers and converts them into structured `ApiResponse` error payloads. It handles:

1. **`ResourceNotFoundException`** -- 404 with `RESOURCE_NOT_FOUND` code
2. **`DuplicateResourceException`** -- 409 with `DUPLICATE_RESOURCE` code
3. **`BusinessRuleException`** -- 422 with the custom code from the exception
4. **`MethodArgumentNotValidException`** -- 400 with `VALIDATION_FAILED` code and per-field errors
5. **`MethodArgumentTypeMismatchException`** -- 400 with `TYPE_MISMATCH` code
6. **`Exception` (catch-all)** -- 500 with `INTERNAL_ERROR` code (stack trace logged server-side, never exposed to the client)

---

### account-service

Spring Boot microservice for managing bank accounts. Runs on **port 8081**.

#### Entity: Account

Mapped to the `accounts` database table. Extends `BaseEntity` (inheriting id, version, createdAt, updatedAt).

| Column              | Type           | Constraints                     | Description                              |
|---------------------|----------------|---------------------------------|------------------------------------------|
| `account_number`    | `VARCHAR(20)`  | NOT NULL, UNIQUE                | Bank-style 13-digit account number       |
| `customer_id`       | `UUID`         | NOT NULL                        | Reference to the customer who owns this account |
| `account_holder_name` | `VARCHAR(100)` | NOT NULL                      | Full name of the account holder          |
| `account_type`      | `VARCHAR(20)`  | NOT NULL, enum as STRING        | One of: CHEQUING, SAVINGS, TFSA, RRSP, BUSINESS |
| `status`            | `VARCHAR(20)`  | NOT NULL, enum as STRING        | One of: ACTIVE, INACTIVE, FROZEN, CLOSED |
| `balance`           | `DECIMAL(19,2)` | NOT NULL                       | Current account balance (BigDecimal for precision) |
| `currency`          | `VARCHAR(3)`   | NOT NULL                        | ISO 4217 currency code (e.g., CAD, USD) |
| `branch_code`       | `VARCHAR(50)`  | nullable                        | Bank branch identifier                   |

**Account Types** (Canada-specific):
- `CHEQUING` -- Day-to-day banking (note Canadian spelling)
- `SAVINGS` -- Interest-bearing savings account
- `TFSA` -- Tax-Free Savings Account (Canadian tax-advantaged)
- `RRSP` -- Registered Retirement Savings Plan (Canadian pension)
- `BUSINESS` -- Business banking account

**Account Statuses:**
- `ACTIVE` -- Normal operating state
- `INACTIVE` -- Dormant, can be reactivated
- `FROZEN` -- Temporarily restricted (fraud investigation, legal hold)
- `CLOSED` -- Permanently closed, no further operations allowed

#### Repository: AccountRepository

Extends `JpaRepository<Account, UUID>` and provides:

| Method                                            | Returns                  | Description                        |
|---------------------------------------------------|--------------------------|------------------------------------|
| `findByAccountNumber(String)`                     | `Optional<Account>`      | Look up by unique account number   |
| `findByCustomerId(UUID)`                          | `List<Account>`          | All accounts for a customer        |
| `findByStatus(AccountStatus, Pageable)`           | `Page<Account>`          | Paginated filter by status         |
| `findByAccountType(AccountType, Pageable)`        | `Page<Account>`          | Paginated filter by type           |
| `existsByAccountNumber(String)`                   | `boolean`                | Uniqueness check for account number generation |

Plus all inherited methods from `JpaRepository`: `save()`, `findById()`, `findAll()`, `deleteById()`, etc.

#### Service: AccountService

Business logic layer. All public methods are `@Transactional(readOnly = true)` by default; write methods override with `@Transactional`.

| Method                                   | Description                                                    |
|------------------------------------------|----------------------------------------------------------------|
| `createAccount(CreateAccountRequest)`    | Creates a new account with auto-generated account number, ACTIVE status, and zero balance. |
| `getAccountById(UUID)`                   | Retrieves an account or throws `ResourceNotFoundException`.    |
| `getAccountByNumber(String)`             | Retrieves an account by its account number.                    |
| `getAccountsByCustomerId(UUID)`          | Returns all accounts belonging to a customer.                  |
| `getAllAccounts(Pageable)`               | Returns paginated list of all accounts.                        |
| `updateAccount(UUID, UpdateAccountRequest)` | Updates mutable fields. Rejects updates on CLOSED accounts. |
| `closeAccount(UUID)`                     | Closes an account. **Business rule: balance must be zero.**    |

**Business Rules Enforced:**
1. New accounts always start with `ACTIVE` status and `BigDecimal.ZERO` balance.
2. Account numbers are auto-generated as random 13-digit numbers with uniqueness checks.
3. Closed accounts cannot be updated (throws `BusinessRuleException` with code `ACCOUNT_CLOSED`).
4. Accounts can only be closed if the balance is exactly zero (throws `BusinessRuleException` with code `BALANCE_NOT_ZERO`).
5. Update is a partial operation -- only non-null fields in the request are applied.

#### Mapper: AccountMapper

A MapStruct interface that generates compile-time mapping code between entities and DTOs.

| Method                       | Description                                                  |
|------------------------------|--------------------------------------------------------------|
| `toEntity(CreateAccountRequest)` | Maps request DTO to entity. Ignores: id, accountNumber, balance, status, version, createdAt, updatedAt (these are set by the service/JPA). |
| `toResponse(Account)`       | Maps entity to response DTO.                                 |

MapStruct is configured with `componentModel = "spring"` so the generated implementation is a Spring bean injectable via `@Autowired` / constructor injection.

**Why MapStruct over manual mapping?** It generates type-safe mapping code at compile time. If a field is added to the entity but not the DTO, MapStruct issues a compile warning. This prevents bugs where fields are silently dropped.

---

## API Reference

Base URL: `http://localhost:8081`

### Account Management Endpoints

#### Create Account
```
POST /api/v1/accounts
```

Creates a new bank account. The account is assigned an auto-generated account number, set to ACTIVE status with a zero balance.

**Request Body:**
```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "accountHolderName": "Jane Doe",
  "accountType": "CHEQUING",
  "currency": "CAD",
  "branchCode": "TO-001"
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "accountNumber": "4829173650284",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "accountHolderName": "Jane Doe",
    "accountType": "CHEQUING",
    "status": "ACTIVE",
    "balance": 0,
    "currency": "CAD",
    "branchCode": "TO-001",
    "createdAt": "2026-03-21T22:30:00Z",
    "updatedAt": "2026-03-21T22:30:00Z"
  },
  "timestamp": "2026-03-21T22:30:00Z"
}
```

---

#### Get Account by ID
```
GET /api/v1/accounts/{id}
```

**Path Parameter:** `id` (UUID)

**Response:** `200 OK` with account data, or `404 Not Found`.

---

#### Get Account by Account Number
```
GET /api/v1/accounts/number/{accountNumber}
```

**Path Parameter:** `accountNumber` (String, 13 digits)

**Response:** `200 OK` with account data, or `404 Not Found`.

---

#### Get Accounts by Customer
```
GET /api/v1/accounts/customer/{customerId}
```

**Path Parameter:** `customerId` (UUID)

**Response:** `200 OK` with a list of all accounts for that customer.

```json
{
  "success": true,
  "data": [
    { "id": "...", "accountType": "CHEQUING", "balance": 1500.00, ... },
    { "id": "...", "accountType": "SAVINGS", "balance": 25000.00, ... }
  ],
  "timestamp": "2026-03-21T22:30:00Z"
}
```

---

#### Get All Accounts (Paginated)
```
GET /api/v1/accounts?page=0&size=20&sort=createdAt,desc
```

**Query Parameters:**

| Parameter | Default      | Description                                    |
|-----------|--------------|------------------------------------------------|
| `page`    | `0`          | Page number (zero-based)                       |
| `size`    | `20`         | Number of items per page                       |
| `sort`    | `createdAt`  | Sort field and direction (e.g., `createdAt,desc`) |

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3,
    "last": false
  },
  "timestamp": "2026-03-21T22:30:00Z"
}
```

---

#### Update Account
```
PUT /api/v1/accounts/{id}
```

Partial update -- only non-null fields in the request body are applied.

**Request Body:**
```json
{
  "accountHolderName": "Jane Smith",
  "status": "FROZEN",
  "branchCode": "VA-002"
}
```

All fields are optional. Only include fields you want to change.

**Response:** `200 OK` with updated account data.

**Error:** `422 Unprocessable Entity` if the account is CLOSED.

---

#### Close Account
```
DELETE /api/v1/accounts/{id}
```

Closes an account by setting its status to CLOSED. **The account balance must be zero.**

**Response:** `200 OK`
```json
{
  "success": true,
  "data": null,
  "timestamp": "2026-03-21T22:30:00Z"
}
```

**Error:** `422 Unprocessable Entity` if balance is non-zero.

---

### Actuator Endpoints

| Endpoint                              | Description              |
|---------------------------------------|--------------------------|
| `GET /actuator/health`                | Application health check |
| `GET /actuator/info`                  | Application info         |
| `GET /actuator/metrics`               | Available metrics list   |
| `GET /actuator/metrics/{metric.name}` | Specific metric value    |

### OpenAPI Endpoints

| Endpoint                     | Description                         |
|------------------------------|-------------------------------------|
| `GET /swagger-ui.html`       | Interactive Swagger UI              |
| `GET /api-docs`              | OpenAPI 3.0 JSON specification      |

---

## Request/Response Examples

### Success Response
```json
{
  "success": true,
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "accountNumber": "4829173650284",
    "accountHolderName": "Jane Doe",
    "accountType": "CHEQUING",
    "status": "ACTIVE",
    "balance": 0,
    "currency": "CAD"
  },
  "timestamp": "2026-03-21T22:30:00Z"
}
```

### Validation Error Response
```json
{
  "success": false,
  "error": {
    "status": 400,
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "fieldErrors": {
      "customerId": "Customer ID is required",
      "accountHolderName": "Account holder name is required",
      "accountType": "Account type is required",
      "currency": "Currency is required"
    }
  },
  "timestamp": "2026-03-21T22:30:00Z"
}
```

### Not Found Error Response
```json
{
  "success": false,
  "error": {
    "status": 404,
    "code": "RESOURCE_NOT_FOUND",
    "message": "Account not found with id: '7c9e6679-7425-40de-944b-e07fc1f90ae7'"
  },
  "timestamp": "2026-03-21T22:30:00Z"
}
```

### Business Rule Error Response
```json
{
  "success": false,
  "error": {
    "status": 422,
    "code": "BALANCE_NOT_ZERO",
    "message": "Account balance must be zero before closing. Current balance: 1500.00"
  },
  "timestamp": "2026-03-21T22:30:00Z"
}
```

---

## Error Handling

All errors are handled centrally by `GlobalExceptionHandler` in common-lib. No service or controller catches exceptions manually -- they propagate up and the handler converts them to structured responses.

| HTTP Status | Error Code           | Trigger                                         |
|-------------|----------------------|-------------------------------------------------|
| 400         | `VALIDATION_FAILED`  | Bean Validation (`@Valid`) failures on request body |
| 400         | `TYPE_MISMATCH`      | Path/query parameter type conversion failure (e.g., invalid UUID) |
| 404         | `RESOURCE_NOT_FOUND` | Entity not found by ID or unique field          |
| 409         | `DUPLICATE_RESOURCE` | Creating a resource that violates a uniqueness constraint |
| 422         | `ACCOUNT_CLOSED`     | Attempting to update a closed account           |
| 422         | `BALANCE_NOT_ZERO`   | Attempting to close an account with remaining balance |
| 500         | `INTERNAL_ERROR`     | Unexpected server error (stack trace logged, not exposed) |

---

## Validation Rules

### CreateAccountRequest

| Field              | Type          | Rules                                                    |
|--------------------|---------------|----------------------------------------------------------|
| `customerId`       | `UUID`        | Required (`@NotNull`)                                    |
| `accountHolderName`| `String`      | Required (`@NotBlank`), 2-100 characters (`@Size`)       |
| `accountType`      | `AccountType` | Required (`@NotNull`), one of: CHEQUING, SAVINGS, TFSA, RRSP, BUSINESS |
| `currency`         | `String`      | Required (`@NotBlank`), exactly 3 characters (`@Size`)   |
| `branchCode`       | `String`      | Optional, max 50 characters (`@Size`)                    |

### UpdateAccountRequest

| Field              | Type            | Rules                                                  |
|--------------------|-----------------|--------------------------------------------------------|
| `accountHolderName`| `String`        | Optional, 2-100 characters if provided (`@Size`)       |
| `status`           | `AccountStatus` | Optional, one of: ACTIVE, INACTIVE, FROZEN, CLOSED     |
| `branchCode`       | `String`        | Optional, max 50 characters if provided (`@Size`)      |

---

## Database Schema

### accounts table

Generated by Hibernate DDL auto-update from the `Account` entity.

```sql
CREATE TABLE accounts (
    id                  UUID            NOT NULL PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    account_number      VARCHAR(20)     NOT NULL UNIQUE,
    customer_id         UUID            NOT NULL,
    account_holder_name VARCHAR(100)    NOT NULL,
    account_type        VARCHAR(20)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    balance             DECIMAL(19,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    branch_code         VARCHAR(50)
);
```

**Notes:**
- Primary key is a UUID (generated by JPA, not a database sequence).
- `version` enables optimistic locking -- JPA increments it on every update and rejects stale writes.
- `created_at` and `updated_at` are managed by JPA Auditing (`@CreatedDate`, `@LastModifiedDate`).
- Enums (`account_type`, `status`) are stored as strings for readability.
- `balance` uses `DECIMAL(19,2)` for financial precision -- never use `float` or `double` for money.

---

## Testing

### Test Summary

| Test Class              | Type       | Tests | Framework          | Description                                    |
|-------------------------|------------|-------|--------------------|------------------------------------------------|
| `AccountServiceTest`    | Unit       | 7     | JUnit 5 + Mockito  | Tests business logic with mocked dependencies  |
| `AccountControllerTest` | Integration| 4     | MockMvc + Spring   | Tests HTTP layer, validation, error handling   |

Total: **11 tests, all passing.**

### Running Tests

```bash
# Run all tests
mvn test

# Run only account-service tests (from project root)
mvn test -pl account-service

# Run a specific test class
mvn test -pl account-service -Dtest=AccountServiceTest

# Run a specific test method
mvn test -pl account-service -Dtest=AccountServiceTest#shouldCreateAccount
```

### Unit Tests (AccountServiceTest)

Uses `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks` -- no Spring context loaded, fast execution.

| Nested Class   | Test                                              | Verifies                                    |
|----------------|---------------------------------------------------|---------------------------------------------|
| CreateAccount  | should create account with ACTIVE status and zero balance | Account creation flow, repository save called |
| GetAccountById | should return account when found                  | Happy path retrieval                        |
| GetAccountById | should throw ResourceNotFoundException when not found | 404 behavior                             |
| UpdateAccount  | should update account fields                      | Partial update, repository save called      |
| UpdateAccount  | should reject update on closed account            | Business rule: ACCOUNT_CLOSED              |
| CloseAccount   | should close account with zero balance            | Status set to CLOSED                       |
| CloseAccount   | should reject closing account with non-zero balance | Business rule: BALANCE_NOT_ZERO           |

### Integration Tests (AccountControllerTest)

Uses `@WebMvcTest(AccountController.class)` with `@Import(GlobalExceptionHandler.class)` -- loads only the web slice (no JPA, no database).

| Test                                   | Verifies                                             |
|----------------------------------------|------------------------------------------------------|
| POST - should create account and return 201 | Happy path: request accepted, 201 returned, response structure correct |
| POST - should return 400 for invalid request | Validation: missing required fields trigger field-level errors |
| GET - should return 404 for non-existent account | Exception handling: ResourceNotFoundException mapped to 404 |
| GET - should return account            | Happy path: account retrieved, response structure correct |

### Testing Patterns Used

- **BDD-style mocking:** `given(...).willReturn(...)` from Mockito BDD API for readable test setup.
- **AssertJ fluent assertions:** `assertThat(result).isNotNull()` for readable assertions.
- **Nested test classes:** `@Nested` groups related tests by method under test.
- **Display names:** `@DisplayName` provides human-readable test names in reports.
- **Test profile:** `application-test.yml` uses H2 in-memory database so tests don't require PostgreSQL.

### Key Testing Decision: @WebMvcTest + @Import

`@WebMvcTest` creates a thin Spring context with only web-related beans. Since `GlobalExceptionHandler` lives in `common-lib` (outside the scanned package), it is explicitly imported with `@Import(GlobalExceptionHandler.class)`.

The `GlobalExceptionHandler` is **not** listed in the `controllers` parameter of `@WebMvcTest` because it is a `@RestControllerAdvice`, not a `@RestController`.

---

## Configuration Reference

### application.yml (account-service)

```yaml
spring:
  application:
    name: account-service                          # Service identity

  datasource:
    url: jdbc:postgresql://localhost:5432/securebank  # PostgreSQL connection
    username: securebank
    password: securebank_dev

  jpa:
    hibernate:
      ddl-auto: update            # Auto-create/update tables from entities
    show-sql: false               # Don't log SQL (set to true for debugging)
    open-in-view: false           # Disabled — prevents lazy loading in controllers (best practice)

server:
  port: 8081                      # Each microservice gets its own port

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics   # Only expose what's needed
```

### application-test.yml

Overrides the main config for test execution:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1   # In-memory H2 database
    driver-class-name: org.h2.Driver

  jpa:
    hibernate:
      ddl-auto: create-drop       # Create tables at start, drop at end
```

### docker-compose.yml

| Service    | Image              | Port | Container Name      | Health Check                  |
|------------|--------------------|------|---------------------|-------------------------------|
| PostgreSQL | `postgres:16-alpine` | 5432 | securebank-postgres | `pg_isready -U securebank`  |
| Redis      | `redis:7-alpine`   | 6379 | securebank-redis    | `redis-cli ping`             |

Both services use named Docker volumes (`postgres_data`, `redis_data`) for data persistence across restarts.

---

## Design Decisions & Patterns

### Why UUID Primary Keys Instead of Auto-Increment?
- **Security:** Sequential IDs are guessable. A user could iterate account IDs to probe for other accounts. UUIDs are unpredictable.
- **Distributed systems:** In a microservices architecture, multiple instances may create records simultaneously. UUIDs don't require a centralized sequence.
- **Banking standard:** Canadian banks use UUIDs (or similar non-sequential identifiers) for account and transaction references.

### Why BigDecimal for Money?
`float` and `double` use binary floating-point, which cannot exactly represent values like `0.10`. In banking, a rounding error of even `$0.01` across millions of transactions is unacceptable. `BigDecimal` provides exact decimal arithmetic.

### Why Optimistic Locking (@Version)?
If two bank tellers update the same account simultaneously (e.g., both freeze it), one update could silently overwrite the other. The `@Version` field causes JPA to check that the version hasn't changed since the entity was read. If it has, `OptimisticLockException` is thrown, preventing lost updates.

### Why DTOs Instead of Exposing Entities?
- **Security:** Entities may contain fields that should never be exposed (internal flags, audit data).
- **Stability:** The API contract (DTOs) can evolve independently from the database schema (entities).
- **Validation:** DTOs carry validation annotations specific to the API contract.
- **Banking compliance:** API governance requires explicit control over what data crosses service boundaries.

### Why MapStruct Over Manual Mapping?
- **Type safety:** Compile-time code generation catches mapping errors before runtime.
- **Performance:** Generated code is plain getter/setter calls -- no reflection overhead.
- **Maintenance:** Adding a field to an entity triggers a compile warning if the mapper isn't updated.

### Why @Transactional(readOnly = true) as Default?
Read-only transactions hint the database to optimize (e.g., skip dirty checking in Hibernate). Write methods explicitly declare `@Transactional` to enable a read-write transaction. This follows the principle of least privilege.

### Why open-in-view: false?
The Open Session in View anti-pattern keeps the Hibernate session open during HTTP response rendering, allowing lazy loading in controllers/views. This causes unpredictable N+1 queries and couples the view layer to the persistence layer. Disabling it forces all data loading to happen in the service layer where it belongs.

---

## Development Roadmap

### Phase 1: Foundations & Project Setup -- COMPLETE
- Maven multi-module project structure
- common-lib: ApiResponse, exceptions, BaseEntity
- account-service: full CRUD REST API
- Bean Validation, GlobalExceptionHandler
- Docker Compose (PostgreSQL + Redis)
- 11 passing tests (unit + integration)

### Phase 2: Database & Persistence (Upcoming)
- Flyway database migrations
- Custom JPQL and native queries
- Pagination and sorting
- Optimistic locking testing
- Database indexing and query performance

### Phase 3: Security
- auth-service with JWT authentication
- Role-based access control (CUSTOMER, TELLER, MANAGER, ADMIN)
- Refresh token rotation
- Rate limiting with Redis
- Audit logging, PII encryption

### Phase 4: Transaction Service
- Deposits, withdrawals, transfers
- Idempotency keys
- Saga pattern for inter-account transfers
- Interac e-Transfer simulation

### Phase 5: Event-Driven Architecture (Kafka)
- notification-service as Kafka consumer
- Event publishing (ACCOUNT_CREATED, TRANSACTION_COMPLETED)
- Dead letter queue
- Fraud detection rules

### Phase 6: API Gateway & Microservices Patterns
- Spring Cloud Gateway
- Circuit breaker (Resilience4j)
- Centralized configuration (config-server)
- Service discovery

### Phase 7: Testing
- Testcontainers integration tests
- REST Assured API tests
- JaCoCo coverage reports (target: >80%)
- Contract tests, performance tests

### Phase 8: DevOps & Monitoring
- Multi-stage Docker builds
- GitHub Actions CI/CD
- Structured logging with correlation IDs
- Prometheus + Grafana dashboards
