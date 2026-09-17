# InnovationHub v0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar uma base executável do InnovationHub com Projetos, Dashboard, SQL Server, React, testes e Docker.

**Architecture:** Monólito modular Spring Boot organizado por feature. React consome API REST e SQL Server é versionado via Flyway.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring Data JPA, SQL Server, Flyway, JUnit 5, Mockito, React 19, Vite, Docker.

**Spec:** `docs/superpowers/specs/2026-09-17-innovationhub-v01-design.md`

## Global Constraints

- Java 21.
- API versionada em `/api/v1`.
- Nenhuma entidade JPA exposta diretamente.
- SQL Server como banco de produção.
- TDD para regras de domínio.
- Modular Monolith; não introduzir microsserviços na v0.1.

---

### Task 1: Projeto e domínio

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/innovationhub/project/domain/Project.java`
- Create: `backend/src/main/java/com/innovationhub/project/domain/ProjectStatus.java`
- Test: `backend/src/test/java/com/innovationhub/project/application/ProjectServiceTest.java`

- [x] Escrever teste de criação de projeto.
- [x] Escrever teste para rejeitar data final anterior à inicial.
- [x] Implementar domínio mínimo e serviço.
- [x] Manter DTOs separados da entidade.

### Task 2: Persistência e API REST

**Files:**
- Create: `backend/src/main/java/com/innovationhub/project/infrastructure/ProjectRepository.java`
- Create: `backend/src/main/java/com/innovationhub/project/api/ProjectController.java`
- Create: `backend/src/main/resources/db/migration/V1__create_projects.sql`
- Test: `backend/src/test/java/com/innovationhub/project/api/ProjectControllerTest.java`

- [x] Criar repository JPA.
- [x] Criar migration SQL Server.
- [x] Expor POST/GET paginado/GET por ID.
- [x] Padronizar erros.

### Task 3: Dashboard

**Files:**
- Create: `backend/src/main/java/com/innovationhub/dashboard/DashboardService.java`
- Create: `backend/src/main/java/com/innovationhub/dashboard/DashboardController.java`
- Test: `backend/src/test/java/com/innovationhub/dashboard/DashboardServiceTest.java`

- [x] Agregar KPIs via repository.
- [x] Expor `/api/v1/dashboard/summary`.

### Task 4: Frontend

**Files:**
- Create: `frontend/src/App.jsx`
- Create: `frontend/src/styles.css`
- Create: `frontend/src/api.js`

- [x] Criar shell visual do dashboard.
- [x] Consumir resumo e lista de projetos.
- [x] Exibir estado de erro da API.

### Task 5: Infraestrutura e qualidade

**Files:**
- Create: `docker-compose.yml`
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `.github/workflows/ci.yml`

- [x] Containerizar SQL Server, backend e frontend.
- [x] Configurar JaCoCo.
- [x] Configurar CI backend/frontend.
- [x] Documentar execução.
