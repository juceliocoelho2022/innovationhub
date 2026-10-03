# Project Update with Optimistic Locking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expor atualização completa de projetos por `PUT`, usando versão esperada + JPA `@Version` para impedir lost updates e traduzindo conflitos concorrentes para `409 Conflict` com `ProblemDetail`.

**Architecture:** O módulo `project` permanece dentro do monólito modular. `ProjectController` trata HTTP, `ProjectService` coordena o caso de uso transacional, `Project` controla a mutação dos campos editáveis, SQL Server/JPA arbitram a corrida real com `@Version`, e `GlobalExceptionHandler` estabiliza o contrato de erro.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring MVC, Spring Data JPA/Hibernate, Bean Validation, Microsoft SQL Server, Flyway, JUnit 5, Mockito, MockMvc, Testcontainers, Maven.

**Spec:** `docs/superpowers/specs/2026-10-03-project-update-optimistic-locking-design.md`

## Global Constraints

- Usar `PUT /api/v1/projects/{id}` para atualização completa dos campos editáveis.
- `version` é obrigatória e não negativa no request.
- Não permitir alteração de `id`, `code`, `status`, `version` diretamente ou timestamps por este endpoint.
- Preservar JPA `@Version` como arbitragem final de concorrência; a checagem manual de versão não a substitui.
- Executar `repository.flush()` antes de construir o `ProjectResponse`, para materializar a nova versão e detectar optimistic locking ainda dentro do caso de uso.
- Versão obsoleta e optimistic locking devem resultar em `409 Conflict` com `ProblemDetail` estável.
- `endDate < startDate` continua sendo regra de negócio com `422 Unprocessable Entity`.
- `budget < 0` continua sendo Bean Validation com `400 Bad Request`.
- Não adicionar migration, tabela, dependência, Kafka, JWT/RBAC, PATCH, ETag, auditoria completa ou microsserviços nesta feature.
- Preservar o `ProjectOptimisticLockingIntegrationTest` existente como evidência da corrida real no SQL Server.
- Não afirmar uso multiusuário em produção, taxa de contenção, throughput, SLO ou performance sem evidência medida.

## Review Focus

1. `version` ausente ou negativa deve falhar em Bean Validation (`400`) antes de entrar no caso de uso; cobrir no Task 2.
2. Um request com versão obsoleta não pode mutar a entidade nem chamar `flush()`; cobrir no Task 1.
3. `name` e `managerName` com espaços laterais devem continuar normalizados como no fluxo de criação; cobrir no Task 1.
4. Uma `OptimisticLockingFailureException` surgida no `flush()` deve produzir o mesmo `409` genérico da exceção de versão da aplicação, sem vazar detalhes internos; cobrir no Task 2.
5. `code`, `status` e demais campos não editáveis devem permanecer inalterados após update; cobrir no Task 1 e confirmar no Task 3.

---

### Task 1: Implementar o caso de uso versionado no domínio e application service

**Files:**
- Create: `backend/src/main/java/com/innovationhub/project/api/UpdateProjectRequest.java`
- Create: `backend/src/main/java/com/innovationhub/shared/exception/ProjectVersionConflictException.java`
- Modify: `backend/src/main/java/com/innovationhub/project/domain/Project.java`
- Modify: `backend/src/main/java/com/innovationhub/project/application/ProjectService.java`
- Modify/Test: `backend/src/test/java/com/innovationhub/project/application/ProjectServiceTest.java`

**Interfaces:**
- Produces: `UpdateProjectRequest(Long version, String name, String description, InnovationArea innovationArea, LocalDate startDate, LocalDate endDate, BigDecimal budget, String managerName)`.
- Produces: `Project.updateDetails(String name, String description, InnovationArea innovationArea, LocalDate startDate, LocalDate endDate, BigDecimal budget, String managerName)`.
- Produces: `ProjectService.update(Long id, UpdateProjectRequest request) -> ProjectResponse` com `@Transactional`.
- Produces: `ProjectVersionConflictException(Long projectId, Long expectedVersion, Long currentVersion)`.

- [ ] **Step 1: Escrever testes de serviço que falham para update válido e normalização**

Em `ProjectServiceTest`, adicionar `shouldUpdateProjectAndReturnVersionAfterFlush()`:
- repository retorna um `Project` existente com `id`, `code`, `status=DRAFT` e `version=5` preparados para o teste;
- request envia `version=5`, nome e manager com espaços laterais e novos valores editáveis;
- `repository.flush()` simula o incremento JPA para `version=6`;
- assert response com valores atualizados, `name`/`managerName` normalizados e `version=6`;
- assert `code` e `status` preservados;
- verify `repository.flush()` foi chamado.

- [ ] **Step 2: Executar o teste para confirmar RED**

Run from `backend`:

```bash
mvn -Dtest=ProjectServiceTest#shouldUpdateProjectAndReturnVersionAfterFlush test
```

Expected: FAIL porque `UpdateProjectRequest`, `ProjectService.update` e/ou `Project.updateDetails` ainda não existem.

- [ ] **Step 3: Escrever testes de serviço para stale version, not found e data inválida**

Adicionar:
- `shouldRejectStaleProjectVersionWithoutMutatingOrFlushing()` — `version` esperada 5, atual 6; lança `ProjectVersionConflictException`; valores originais permanecem; `flush()` não é chamado.
- `shouldRejectUpdateWhenProjectDoesNotExist()` — repository vazio; lança `ProjectNotFoundException`; `flush()` não é chamado.
- `shouldRejectUpdateWhenEndDateIsBeforeStartDate()` — versões iguais, período inválido; lança `BusinessRuleException`; `flush()` não é chamado.

- [ ] **Step 4: Implementar o DTO de update**

Criar `UpdateProjectRequest` como `record` com as mesmas validações de criação e:

```java
@NotNull @PositiveOrZero Long version
```

Manter `@NotBlank/@Size` para `name`/`managerName`, `@Size(max=1000)` para `description`, `@NotNull` para `innovationArea/startDate/endDate/budget` e `@PositiveOrZero` para `budget`.

- [ ] **Step 5: Implementar a exceção de conflito de versão**

Criar `ProjectVersionConflictException extends RuntimeException` com construtor:

```java
ProjectVersionConflictException(Long projectId, Long expectedVersion, Long currentVersion)
```

A mensagem interna pode incluir id/versões para diagnóstico; o contrato HTTP será genérico no Task 2.

- [ ] **Step 6: Implementar a mutação explícita no domínio**

Adicionar em `Project`:

```java
public void updateDetails(
        String name,
        String description,
        InnovationArea innovationArea,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        String managerName)
```

Esse método altera somente os campos editáveis. Não criar setters públicos nem alterar `code`, `status`, `version`, `createdAt` ou `id`.

- [ ] **Step 7: Implementar `ProjectService.update` e reutilizar a validação de período**

Assinatura:

```java
@Transactional
public ProjectResponse update(Long id, UpdateProjectRequest request)
```

Sequência obrigatória:
1. `findById` ou `ProjectNotFoundException`;
2. comparar `request.version()` com `project.getVersion()` usando igualdade null-safe;
3. se diferente, lançar `ProjectVersionConflictException` antes de mutar;
4. validar `endDate >= startDate`;
5. chamar `project.updateDetails(...)`, usando `trim()` em `name` e `managerName` como na criação;
6. chamar `repository.flush()`;
7. somente depois do flush chamar `toResponse(project)`.

Refatorar a validação de datas de criação apenas o necessário para reutilizar a mesma regra, sem alterar comportamento existente.

- [ ] **Step 8: Executar todos os testes de service**

```bash
mvn -Dtest=ProjectServiceTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/innovationhub/project/api/UpdateProjectRequest.java \
        backend/src/main/java/com/innovationhub/shared/exception/ProjectVersionConflictException.java \
        backend/src/main/java/com/innovationhub/project/domain/Project.java \
        backend/src/main/java/com/innovationhub/project/application/ProjectService.java \
        backend/src/test/java/com/innovationhub/project/application/ProjectServiceTest.java
git commit -m "feat: add versioned project update"
```

---

### Task 2: Expor `PUT` e estabilizar o contrato HTTP de conflito

**Files:**
- Modify: `backend/src/main/java/com/innovationhub/project/api/ProjectController.java`
- Modify: `backend/src/main/java/com/innovationhub/shared/exception/GlobalExceptionHandler.java`
- Modify/Test: `backend/src/test/java/com/innovationhub/project/api/ProjectControllerTest.java`

**Interfaces:**
- Consumes: `ProjectService.update(Long, UpdateProjectRequest)` do Task 1.
- Consumes: `ProjectVersionConflictException` do Task 1.
- Produces: `PUT /api/v1/projects/{id}` retornando `ProjectResponse` com `200 OK`.
- Produces: `ProblemDetail` de conflito com type `https://innovationhub.local/problems/version-conflict`, title `Conflito de versão`, status `409` e detail orientado a recarregar os dados.

- [ ] **Step 1: Escrever teste MockMvc RED para update bem-sucedido**

Adicionar `shouldUpdateProject()` em `ProjectControllerTest`:
- mock `service.update(1L, request)` retorna `ProjectResponse` com `version=6`;
- executar `PUT /api/v1/projects/1`;
- assert `200`, campos atualizados e `$.version == 6`;
- verificar que o service recebeu `id=1` e o request esperado.

- [ ] **Step 2: Executar o teste para confirmar RED**

```bash
mvn -Dtest=ProjectControllerTest#shouldUpdateProject test
```

Expected: FAIL porque o `@PutMapping` ainda não existe.

- [ ] **Step 3: Escrever testes de validação para `version`**

Adicionar:
- `shouldRejectUpdateWithoutVersion()` -> `400 Bad Request`;
- `shouldRejectUpdateWithNegativeVersion()` -> `400 Bad Request`.

Não mockar uma resposta de service nesses casos; confirmar que Bean Validation bloqueia o request.

- [ ] **Step 4: Escrever testes para os contratos `404`, `422` e `409`**

Adicionar:
- `shouldReturnNotFoundWhenUpdatingMissingProject()` — service lança `ProjectNotFoundException`, assert `404`;
- `shouldReturnUnprocessableEntityForInvalidProjectPeriod()` — service lança `BusinessRuleException`, assert `422`;
- `shouldReturnConflictForStaleProjectVersion()` — service lança `ProjectVersionConflictException`, assert `409`, type/title/detail da SPEC e ausência de detalhes internos;
- `shouldReturnSameConflictContractForOptimisticLockingFailure()` — service lança `ObjectOptimisticLockingFailureException`, assert o mesmo `409`/ProblemDetail.

- [ ] **Step 5: Implementar o endpoint**

Adicionar em `ProjectController`:

```java
@PutMapping("/{id}")
public ResponseEntity<ProjectResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateProjectRequest request)
```

Retornar `ResponseEntity.ok(service.update(id, request))`.

- [ ] **Step 6: Implementar o mapeamento de conflito**

Em `GlobalExceptionHandler`, adicionar handler para:
- `ProjectVersionConflictException`;
- `org.springframework.dao.OptimisticLockingFailureException` (inclui `ObjectOptimisticLockingFailureException`).

Ambos devem retornar o mesmo `ProblemDetail`:
- status `409 CONFLICT`;
- title `Conflito de versão`;
- type `https://innovationhub.local/problems/version-conflict`;
- detail `O projeto foi alterado por outro usuário. Recarregue os dados antes de tentar novamente.`

Não retornar mensagem bruta da exceção de persistência.

- [ ] **Step 7: Executar os testes MockMvc**

```bash
mvn -Dtest=ProjectControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Executar unitários do módulo afetado**

```bash
mvn -Dtest=ProjectServiceTest,ProjectControllerTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/innovationhub/project/api/ProjectController.java \
        backend/src/main/java/com/innovationhub/shared/exception/GlobalExceptionHandler.java \
        backend/src/test/java/com/innovationhub/project/api/ProjectControllerTest.java
git commit -m "feat: expose project update conflict contract"
```

---

### Task 3: Provar a feature ponta a ponta contra SQL Server real

**Files:**
- Create/Test: `backend/src/test/java/com/innovationhub/project/infrastructure/ProjectUpdateApiIntegrationTest.java`
- Verify unchanged behavior: `backend/src/test/java/com/innovationhub/project/infrastructure/ProjectOptimisticLockingIntegrationTest.java`

**Interfaces:**
- Consumes: endpoint `PUT /api/v1/projects/{id}` do Task 2.
- Consumes: `ProjectRepository` e o SQL Server configurado por `AbstractSqlServerIntegrationTest`.
- Produces evidence: update válido incrementa versão real; request obsoleto retorna 409 sem sobrescrever; teste de corrida real existente continua verde.

- [ ] **Step 1: Criar teste de integração RED para update real**

Criar `ProjectUpdateApiIntegrationTest extends AbstractSqlServerIntegrationTest` com `@AutoConfigureMockMvc`, injetando `MockMvc`, `ObjectMapper` e `ProjectRepository`.

Adicionar `shouldUpdateProjectAndIncrementVersionInSqlServer()`:
- limpar repository e inserir projeto via `saveAndFlush`;
- capturar `version` inicial;
- executar PUT com essa versão;
- assert `200` e response `version = initial + 1`;
- recarregar do repository e confirmar novos campos, nova versão e `code/status` preservados.

- [ ] **Step 2: Executar o teste de integração para confirmar RED se houver lacuna**

```bash
mvn -Dtest=ProjectUpdateApiIntegrationTest#shouldUpdateProjectAndIncrementVersionInSqlServer test
```

Expected before Tasks 1/2: RED; durante execução sequencial após Tasks 1/2, o teste deve passar ou revelar uma lacuna real de flush/contrato que deve ser corrigida com a menor mudança possível.

- [ ] **Step 3: Adicionar cenário stale request no SQL Server**

Adicionar `shouldReturnConflictWithoutOverwritingLatestState()`:
1. inserir projeto e capturar `version=N`;
2. primeiro PUT com `version=N` muda orçamento/nome e retorna `200`/`N+1`;
3. segundo PUT com a versão antiga `N` tenta escrever valores diferentes;
4. assert `409` com ProblemDetail de conflito;
5. recarregar banco e confirmar que os valores do primeiro PUT continuam autoritativos.

- [ ] **Step 4: Executar os testes de integração da feature e da corrida existente**

```bash
mvn -Dtest=ProjectUpdateApiIntegrationTest,ProjectOptimisticLockingIntegrationTest test
```

Expected: PASS com Docker/Testcontainers disponível.

- [ ] **Step 5: Executar verificação Maven completa do backend**

```bash
mvn clean verify
```

Expected: BUILD SUCCESS; testes unitários, MockMvc, SQL Server/Testcontainers e geração JaCoCo concluídos.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/com/innovationhub/project/infrastructure/ProjectUpdateApiIntegrationTest.java
git commit -m "test: verify project updates against SQL Server"
```

---

### Task 4: Atualizar o case de negócio e a documentação de portfólio

**Files:**
- Modify: `README.md`
- Modify: `docs/business-case.md`
- Modify: `docs/engineering-decisions.md`
- Modify: `docs/quality-hardening.md`
- Create: `docs/PORTFOLIO_CASE_STUDY.md`

**Interfaces:**
- Consumes: comportamento e evidência verdes dos Tasks 1–3.
- Produces: narrativa verificável `problema -> lost update -> version -> PUT -> @Version/SQL Server -> 409 -> Testcontainers`.

- [ ] **Step 1: Atualizar `docs/business-case.md` com rastreabilidade da BR-005**

Manter BR-001..BR-008 e ajustar BR-005 para distinguir claramente:
- regra: versão obsoleta não sobrescreve estado recente;
- contrato: PUT leva `version` esperada;
- mecanismo: checagem de versão + JPA `@Version`;
- resultado: `409 Conflict`;
- evidência: `ProjectServiceTest`, `ProjectControllerTest`, `ProjectUpdateApiIntegrationTest`, `ProjectOptimisticLockingIntegrationTest`.

Atualizar FR-006/AC-004 apenas conforme a capacidade implementada, sem inventar PUT/PATCH adicionais.

- [ ] **Step 2: Atualizar `docs/engineering-decisions.md`**

Fortalecer a seção de optimistic locking com:
- risco de lost update;
- por que versão explícita no PUT;
- por que a checagem manual não substitui `@Version`;
- por que `409` é conflito de estado e não `422` de regra de domínio;
- trade-off: cliente precisa recarregar/reconciliar após conflito;
- preservar a decisão de monólito modular.

- [ ] **Step 3: Atualizar `docs/quality-hardening.md`**

Registrar que a evidência agora cobre:
- conflito de persistência real existente;
- update HTTP usando SQL Server/Testcontainers;
- incremento real de versão;
- stale request sem lost update;
- sem alegar carga/contensão de produção.

- [ ] **Step 4: Criar `docs/PORTFOLIO_CASE_STUDY.md`**

Incluir:
- pitch de 60 segundos;
- explicação técnica de 3–5 minutos;
- cenário `GET version 5 -> PUT version 5 -> version 6 -> PUT stale 5 -> 409`;
- Q&A: por que monólito modular, por que optimistic locking, por que não lock pessimista, por que 409, por que Testcontainers, por que não ETag/PATCH agora;
- Evidence Map com links para código/testes/documentos existentes;
- limites atuais e roadmap sem transformar JWT/auditoria/microservices em capacidades atuais.

- [ ] **Step 5: Reorganizar a primeira parte do `README.md` em ordem business-first**

Antes da stack, o leitor deve encontrar:
1. problema de gestão de PD&I;
2. risco de concorrência/lost update;
3. regra de versão;
4. fluxo crítico de update;
5. decisão de monólito modular;
6. evidência de testes/SQL Server;
7. links para business case e portfolio case study.

Preservar endpoints existentes e adicionar somente o novo `PUT` comprovado.

- [ ] **Step 6: Verificar links e afirmações contra o branch**

Confirmar que todos os paths citados existem e que a documentação não afirma:
- `PATCH`/ETag;
- autenticação/RBAC concluídos;
- auditoria completa;
- microsserviços;
- SLO/performance/uso real em produção.

- [ ] **Step 7: Commit**

```bash
git add README.md docs/business-case.md docs/engineering-decisions.md docs/quality-hardening.md docs/PORTFOLIO_CASE_STUDY.md
git commit -m "docs: position InnovationHub around concurrency control"
```

---

### Task 5: Verificação final e preparação do PR #5

**Files:**
- Verify: all changed files against `main`
- PR: `#5 docs: reforçar InnovationHub como case de domínio e concorrência`

**Interfaces:**
- Consumes: todos os Tasks anteriores.
- Produces: branch verificável, PR atualizado e pronto para decisão de integração.

- [ ] **Step 1: Executar verificação completa fresca**

From `backend`:

```bash
mvn clean verify
```

Expected: BUILD SUCCESS.

Se Docker/Testcontainers não estiver disponível, parar e reportar explicitamente quais testes não puderam ser executados; não declarar suíte completa como verde.

- [ ] **Step 2: Revisar diff contra `main`**

Confirmar que mudanças de produção estão limitadas ao fluxo de update/erro aprovado e que não existem migrations/dependências/infra não previstas.

- [ ] **Step 3: Revisar os cinco itens de `Review Focus`**

Checar os testes que demonstram cada item e corrigir qualquer lacuna antes de concluir.

- [ ] **Step 4: Rodar revisão do branch contra a SPEC**

Validar AC-01..AC-07, status HTTP, `flush()` antes do response, campos não editáveis, comportamento real de `@Version` e documentação sem claims não suportadas.

- [ ] **Step 5: Atualizar a descrição do PR #5**

A descrição deve registrar:
- novo `PUT /api/v1/projects/{id}`;
- versão esperada no request;
- proteção em duas camadas contra lost update;
- `409 ProblemDetail`;
- evidência MockMvc + SQL Server/Testcontainers;
- documentação business-first;
- ausência de migration/dependência/microservices/JWT nesta mudança.

- [ ] **Step 6: Verificar CI do head final**

Esperar os workflows do PR e confirmar resultado. Se falhar, investigar antes de apresentar opções de integração.

- [ ] **Step 7: Não mergear automaticamente**

Após verificação e revisão, apresentar ao usuário as opções de finalização do branch/PR conforme `superpowers:finishing-a-development-branch` e aguardar decisão explícita.
