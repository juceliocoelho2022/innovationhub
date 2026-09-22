# Quality Hardening — SQL Server, Concurrency and Observability

This document records the quality-hardening work for InnovationHub.

## Goal

The objective is not to add architectural complexity. It is to increase confidence that the existing modular monolith behaves correctly against its **target database**, under **concurrent updates**, and exposes enough runtime telemetry for diagnosis.

## 1. Real SQL Server integration tests

Integration tests use Testcontainers with Microsoft SQL Server.

Why this matters:

- H2 and other in-memory databases do not reproduce SQL Server behavior exactly;
- Flyway migrations contain SQL Server-specific DDL;
- timestamp, identity, constraint and locking behavior should be validated against the target engine.

The integration test verifies:

- the running database identifies itself as Microsoft SQL Server;
- Flyway applies the existing migrations;
- JPA can persist and reload a Project;
- the entity version starts consistently.

Run:

```bash
cd backend
mvn clean verify
```

Docker must be available because Testcontainers starts the database container.

## 2. Optimistic locking

`Project` already uses JPA `@Version`.

The integration scenario loads the same row in two independent persistence contexts:

1. transaction A reads version N;
2. transaction B reads version N;
3. transaction A updates and commits;
4. transaction B attempts to commit stale version N;
5. SQL/JPA rejects the stale update;
6. the first committed state remains authoritative.

This demonstrates protection against lost updates without pessimistically locking every read.

## 3. Flyway as the schema authority

The test environment keeps:

```text
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

Hibernate therefore validates the schema instead of generating it. Flyway remains responsible for schema evolution.

This is intentional: application startup should fail when entity mappings and the versioned database schema drift apart.

## 4. Observability baseline

InnovationHub exposes:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

The Prometheus registry provides a baseline for:

- HTTP request volume;
- HTTP status distribution;
- latency metrics;
- JVM/process telemetry;
- datasource/pool telemetry when available.

The Docker Compose stack now includes Prometheus and Grafana. Grafana provisions the **InnovationHub Backend Overview** dashboard automatically with:

- HTTP throughput;
- HTTP 5xx error rate;
- mean HTTP latency;
- request rate by endpoint;
- mean latency by endpoint.

The project does not claim production SLOs yet. The dashboard is a diagnostic baseline; workload-based SLI/SLO targets should only be defined after representative measurements exist.

## 5. Trade-offs

### Testcontainers cost

Real SQL Server integration tests are slower and heavier than unit tests.

Decision:

- keep domain/service/controller unit tests fast;
- use a small number of database integration tests for behaviors that depend on the actual engine.

### Optimistic locking cost

Optimistic locking allows conflicts to reach commit time.

It is a good fit when concurrent writes to the same project are possible but not dominant. If contention becomes consistently high, the concurrency strategy should be revisited based on production evidence.

### Metrics exposure

Prometheus adds operational visibility with low application complexity, but metrics alone are not complete observability. Structured logs and distributed traces would be separate future decisions if the application becomes distributed.

## Evidence

- `SqlServerPersistenceIntegrationTest`
- `ProjectOptimisticLockingIntegrationTest`
- Flyway migrations under `src/main/resources/db/migration`
- `mvn verify` in GitHub Actions
- Actuator + Micrometer Prometheus endpoint
- Prometheus scrape configuration
- provisioned Grafana datasource/dashboard
- Docker Compose validation + `promtool` validation in GitHub Actions
