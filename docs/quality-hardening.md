# Quality Hardening — SQL Server, Concurrency and Observability

## Goal

Increase confidence that the InnovationHub modular monolith behaves correctly against its target database, protects concurrent project updates and exposes a practical diagnostic baseline without adding unnecessary distributed complexity.

## 1. Real SQL Server integration tests

Integration tests use Testcontainers with Microsoft SQL Server because H2 does not reproduce SQL Server DDL, identity, timestamp and locking/version behavior exactly.

The suite verifies:
- SQL Server is the actual test database;
- Flyway applies the versioned migrations;
- JPA persists and reloads `Project`;
- optimistic versioning works against the target engine;
- the HTTP update path persists changes and increments the version;
- a stale HTTP update returns conflict without overwriting the latest committed state.

Run:

```bash
cd backend
mvn clean verify
```

Docker must be available for Testcontainers.

## 2. Optimistic locking at persistence level

`Project` uses JPA `@Version`.

`ProjectOptimisticLockingIntegrationTest` opens two independent persistence contexts:
1. A reads version N;
2. B reads version N;
3. A commits its update;
4. B tries to commit the stale version;
5. SQL/JPA rejects B;
6. A remains authoritative.

This proves that the database/JPA boundary protects against a real lost-update race.

## 3. Optimistic locking through the HTTP use case

`ProjectUpdateApiIntegrationTest` exercises the real application path with MockMvc + Spring + JPA + SQL Server.

### Successful update

```text
Project version N
-> PUT with version N
-> service mutates allowed fields
-> repository.flush()
-> SQL Server update guarded by @Version
-> 200 OK
-> response and database contain version N+1
```

The test also confirms that `code` and `status` are preserved.

### Stale request

```text
PUT version N -> success -> version N+1
PUT again with stale version N
-> 409 Conflict
-> first committed state remains unchanged
```

This complements the persistence-level race test: application-level version checking rejects conflicts already visible when the request begins, while `@Version` still protects the narrower race window between load and commit.

## 4. Stable conflict contract

`ProjectControllerTest` covers both `ProjectVersionConflictException` and Spring `OptimisticLockingFailureException`. Both become the same client-facing `ProblemDetail`:

```text
409 Conflict
Conflito de versão
O projeto foi alterado por outro usuário. Recarregue os dados antes de tentar novamente.
```

Persistence exception details are not returned to clients.

## 5. Flyway as schema authority

The integration environment keeps:

```text
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

Hibernate validates; Flyway evolves the schema. A mapping/schema drift should fail instead of being silently corrected at runtime.

## 6. Observability baseline

InnovationHub exposes:
- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

Prometheus/Grafana provide HTTP volume/status/latency and JVM/process diagnostics. This is an operational baseline, not a production SLO claim.

## 7. Trade-offs

### Testcontainers
Real SQL Server tests are heavier than unit tests, so they are reserved for behavior that depends on the actual database. Fast service/controller tests remain the first feedback layer.

### Optimistic locking
Conflicts can surface at flush/commit time. This is acceptable while writes to the same project are not known to be highly contended. A different locking strategy should require production evidence.

### Metrics
Metrics improve diagnosis but are not complete distributed observability. Structured tracing is not required by the current monolithic architecture.

## Evidence

- `ProjectServiceTest`
- `ProjectControllerTest`
- `SqlServerPersistenceIntegrationTest`
- `ProjectOptimisticLockingIntegrationTest`
- `ProjectUpdateApiIntegrationTest`
- Flyway migrations under `backend/src/main/resources/db/migration`
- `mvn verify` in GitHub Actions
- Actuator + Micrometer Prometheus
- provisioned Prometheus/Grafana configuration
- Docker Compose + Prometheus configuration validation in CI

## Evidence limits

These tests prove behavior in the repository's controlled integration environment. They do not prove production concurrency rates, throughput, latency, availability or user traffic characteristics.
