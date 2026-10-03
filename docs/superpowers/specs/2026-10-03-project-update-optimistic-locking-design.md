# InnovationHub — Project Update with Optimistic Locking Design

Date: 2026-10-03  
Status: approved conversational design — written SPEC for review

## 1. Intent

Evoluir o InnovationHub para que a proteção contra *lost update* deixe de ser apenas uma capacidade de persistência demonstrada por `@Version`/Testcontainers e passe a existir como comportamento observável no contrato HTTP.

O caso de uso central é a edição concorrente de um projeto de PD&I por dois usuários. Uma atualização baseada em uma versão obsoleta não pode sobrescrever silenciosamente uma versão mais recente.

A feature também fortalece o posicionamento de portfólio do InnovationHub como um case de domínio corporativo, consistência transacional e simplicidade arquitetural consciente dentro de um monólito modular.

## 2. Current baseline

O repositório já possui:

- `Project` persistido em SQL Server;
- JPA `@Version` na entidade;
- `ProjectResponse` expondo `version`;
- criação e leitura de projetos por API;
- Bean Validation;
- `ProblemDetail` no tratamento global de erros;
- Flyway como autoridade de schema;
- `ProjectOptimisticLockingIntegrationTest` com SQL Server real via Testcontainers;
- documentação de negócio no PR #5 em `docs/business-case.md`;
- arquitetura de monólito modular, sem necessidade atual de microsserviços.

A API atual não possui endpoint de atualização. Portanto, o optimistic locking está validado no nível de persistência, mas ainda não é um contrato HTTP de edição concorrente.

## 3. Business problem

Em um sistema de gestão de PD&I, dois gestores podem abrir o mesmo projeto ao mesmo tempo e editar informações diferentes.

Exemplo:

```text
Project version = 5

Gestor A lê version 5
Gestor B lê version 5

Gestor A altera orçamento
-> atualização confirmada
-> version passa para 6

Gestor B altera prazo usando version 5
-> a atualização não pode sobrescrever silenciosamente o estado mais recente
-> conflito deve ser explicitado ao cliente
```

Sem controle de concorrência, uma atualização antiga pode apagar uma alteração válida já confirmada.

## 4. Canonical business rule

A regra central desta feature é:

> **Uma atualização baseada em uma versão obsoleta de um projeto não pode sobrescrever silenciosamente uma versão mais recente.**

Essa regra complementa a BR-005 do `docs/business-case.md` e transforma a proteção contra lost update em comportamento de aplicação verificável pela API.

## 5. Selected approach

A abordagem aprovada é:

```text
PUT /api/v1/projects/{id}
+ version explícita no request
+ JPA @Version
+ SQL Server
+ HTTP 409 Conflict
```

O cliente envia o estado completo editável do projeto e a versão recebida na leitura anterior.

### 5.1 Por que PUT

`PUT` foi escolhido porque o objetivo da feature é atualizar de forma explícita o conjunto completo de campos editáveis do projeto.

Benefícios:

- contrato simples para explicar e testar;
- sem semântica de merge parcial;
- validação consistente com o fluxo de criação;
- versão esperada visível no mesmo comando de atualização.

### 5.2 Alternativas consideradas

#### PATCH

Permitiria atualização parcial, mas introduziria complexidade adicional de merge, campos opcionais e validação condicional sem benefício necessário para esta versão.

#### HTTP ETag / If-Match

É semanticamente apropriado para controle de versão HTTP, mas adicionaria uma camada conceitual que não é necessária para demonstrar a regra de concorrência deste case.

A versão explícita no body foi escolhida por clareza de domínio e de portfólio.

## 6. HTTP contract

### 6.1 Endpoint

```http
PUT /api/v1/projects/{id}
```

### 6.2 Request

Novo DTO `UpdateProjectRequest`:

```json
{
  "version": 5,
  "name": "Smart Factory AI",
  "description": "Inspeção industrial com visão computacional.",
  "innovationArea": "ARTIFICIAL_INTELLIGENCE",
  "startDate": "2026-10-01",
  "endDate": "2027-03-31",
  "budget": 900000.00,
  "managerName": "Jucelio Coelho"
}
```

Campos:

- `version`: obrigatório e não negativo;
- `name`: obrigatório, não vazio, máximo 150;
- `description`: opcional, máximo 1000;
- `innovationArea`: obrigatório;
- `startDate`: obrigatório;
- `endDate`: obrigatório;
- `budget`: obrigatório e maior ou igual a zero;
- `managerName`: obrigatório, não vazio, máximo 120.

O DTO deve espelhar as validações relevantes de `CreateProjectRequest` e acrescentar `version`.

### 6.3 Response

Em atualização válida:

```http
200 OK
```

A resposta continua utilizando `ProjectResponse`, incluindo a nova `version` persistida após a atualização.

O `code` corporativo, `id`, `createdAt` implícito de persistência e o estado interno de versionamento não são redefinidos pelo cliente.

### 6.4 Status codes

| Situação | HTTP |
|---|---:|
| atualização válida | `200 OK` |
| payload inválido | `400 Bad Request` |
| projeto inexistente | `404 Not Found` |
| violação de regra de datas | `422 Unprocessable Entity` |
| versão obsoleta / optimistic locking conflict | `409 Conflict` |

## 7. Update flow

```text
Client
  ↓
GET /api/v1/projects/{id}
  ↓
ProjectResponse(version = N)
  ↓
Client edits project
  ↓
PUT /api/v1/projects/{id}
UpdateProjectRequest(version = N)
  ↓
ProjectController
  ↓
ProjectService.update(...)
  ↓
load current Project
  ├── missing -> 404
  └── found
       ↓
compare expected version
  ├── request.version != entity.version -> 409
  └── equal
       ↓
validate business rules
       ↓
Project.updateDetails(...)
       ↓
JPA flush / transaction commit
       ↓
SQL Server UPDATE guarded by @Version
  ├── success -> version increments -> 200
  └── concurrent winner changed row -> optimistic lock exception -> 409
```

## 8. Two-layer concurrency protection

A feature deve preservar duas camadas de defesa.

### 8.1 Application-level stale-version check

Depois de carregar o projeto atual, `ProjectService` compara `request.version` com `project.version`.

Se forem diferentes, a requisição é rejeitada imediatamente com conflito de versão.

Isso cobre o caso em que o cliente já envia uma versão conhecida como obsoleta no momento da leitura da transação.

### 8.2 Persistence-level race protection

Mesmo que dois requests carreguem a mesma versão antes de qualquer deles commitar, o `@Version` continua sendo a arbitragem final.

Exemplo:

```text
Transaction A loads version 5
Transaction B loads version 5

A commits first -> row becomes version 6
B tries to commit version 5
-> SQL/JPA update count does not satisfy optimistic version check
-> optimistic locking exception
-> HTTP 409
```

A comparação manual não substitui `@Version`; ela melhora a resposta para conflitos já detectáveis. O SQL Server/JPA continua protegendo a janela de corrida real entre leitura e commit.

## 9. Component responsibilities

### 9.1 `ProjectController`

Responsabilidades:

- expor `PUT /api/v1/projects/{id}`;
- aplicar `@Valid` ao request;
- delegar para `ProjectService`;
- retornar `ProjectResponse`.

O Controller não deve conter regra de concorrência ou lógica de persistência.

### 9.2 `UpdateProjectRequest`

Responsabilidades:

- representar o contrato HTTP de atualização;
- carregar a `version` esperada;
- declarar validações de formato/campo.

Deve ser separado de `CreateProjectRequest` porque criação e atualização possuem contratos distintos.

### 9.3 `ProjectService`

Novo caso de uso conceitual:

```java
@Transactional
public ProjectResponse update(Long id, UpdateProjectRequest request)
```

Responsabilidades:

- carregar o projeto;
- lançar not found quando necessário;
- comparar versão esperada com versão atual;
- validar invariantes da atualização;
- delegar alteração de estado para a entidade;
- converter para response;
- permanecer dentro da fronteira transacional.

### 9.4 `Project`

A entidade receberá uma operação de domínio equivalente a:

```java
project.updateDetails(
    name,
    description,
    innovationArea,
    startDate,
    endDate,
    budget,
    managerName
);
```

O objetivo é evitar setters públicos dispersos e deixar explícito que o próprio agregado controla sua mutação permitida.

Não faz parte desta feature permitir alteração de:

- `id`;
- código corporativo;
- `version` diretamente;
- timestamps de persistência;
- status por meio deste endpoint.

### 9.5 `GlobalExceptionHandler`

O handler deve traduzir conflitos de concorrência para um contrato HTTP estável, sem vazar detalhes internos de JPA, Hibernate ou SQL Server.

## 10. Conflict model

Criar uma exceção de aplicação específica, por exemplo:

```text
ProjectVersionConflictException
```

Ela representa uma versão esperada diferente da versão atualmente persistida.

Além disso, exceções de optimistic locking lançadas durante flush/commit devem ser traduzidas para o mesmo contrato `409 Conflict`.

O mapeamento deverá considerar a exceção Spring/JPA apropriada presente na stack atual, preferencialmente `ObjectOptimisticLockingFailureException` ou a abstração `OptimisticLockingFailureException`, conforme o ponto real de propagação validado pelos testes.

### 10.1 ProblemDetail de conflito

Contrato esperado:

```json
{
  "type": "https://innovationhub.local/problems/version-conflict",
  "title": "Conflito de versão",
  "status": 409,
  "detail": "O projeto foi alterado por outro usuário. Recarregue os dados antes de tentar novamente."
}
```

O response não deve expor:

- stack trace;
- nomes de tabelas;
- SQL;
- classes internas do Hibernate;
- mensagem bruta de exceção de persistência.

## 11. Business validations during update

A atualização deve preservar as mesmas invariantes relevantes já aplicadas na criação.

### 11.1 Período

```text
endDate >= startDate
```

Violação deve retornar `422 Unprocessable Entity` por regra de negócio.

### 11.2 Orçamento

```text
budget >= 0
```

O DTO usa Bean Validation para rejeitar valor negativo.

### 11.3 Text fields

`name` e `managerName` permanecem obrigatórios e devem ser normalizados de forma compatível com a criação, incluindo `trim()` onde o fluxo atual já faz isso.

## 12. Testing strategy

A feature deve ser demonstrada em três níveis.

### 12.1 Application/service tests

Cobrir pelo menos:

- atualização válida;
- projeto inexistente;
- data final anterior à inicial;
- versão do request diferente da versão persistida;
- manutenção dos campos não editáveis.

### 12.2 HTTP/MockMvc tests

Cobrir o novo endpoint com pelo menos:

- `200 OK` em atualização válida;
- `400 Bad Request` para Bean Validation;
- `404 Not Found` para id inexistente;
- `422 Unprocessable Entity` para regra de datas;
- `409 Conflict` para versão obsoleta;
- `ProblemDetail` estável no conflito;
- response contendo a nova versão.

Cenário de demonstração esperado:

```text
GET project -> version 5
PUT version 5 -> 200 -> version 6
PUT version 5 -> 409
```

### 12.3 SQL Server/Testcontainers concurrency tests

O teste existente `ProjectOptimisticLockingIntegrationTest` deve ser preservado.

A suíte deve continuar comprovando que dois persistence contexts independentes não conseguem confirmar silenciosamente atualizações baseadas na mesma versão.

A feature pode adicionar ou adaptar um teste de integração de API/serviço se necessário para comprovar que a exceção do banco é traduzida corretamente no caminho real da aplicação.

Mocks não substituem esta evidência, porque a semântica crítica depende do banco-alvo e do comportamento real do JPA provider.

## 13. Acceptance criteria

### AC-01 — Successful update

**Given** um projeto com `version = N`  
**When** um `PUT` válido é enviado com `version = N`  
**Then** os campos editáveis são atualizados  
**And** a operação retorna `200 OK`  
**And** a resposta contém uma versão maior que `N`.

### AC-02 — Stale request version

**Given** um projeto atual em `version = N + 1`  
**When** um cliente envia `PUT` com `version = N`  
**Then** a API retorna `409 Conflict`  
**And** o estado mais recente não é sobrescrito.

### AC-03 — Real concurrent race

**Given** duas transações carregam a mesma versão do projeto  
**When** ambas tentam confirmar alterações  
**Then** somente uma confirma a atualização correspondente à versão esperada  
**And** a outra falha por optimistic locking  
**And** não ocorre lost update silencioso.

### AC-04 — Invalid period

**Given** `endDate < startDate`  
**When** o update é solicitado  
**Then** a API retorna `422 Unprocessable Entity`.

### AC-05 — Invalid budget

**Given** `budget < 0`  
**When** o update é solicitado  
**Then** Bean Validation rejeita o request.

### AC-06 — Missing project

**Given** um id inexistente  
**When** o update é solicitado  
**Then** a API retorna `404 Not Found`.

### AC-07 — Stable conflict contract

**Given** um conflito de versão  
**When** a API responde  
**Then** retorna `ProblemDetail` com `409` e mensagem orientada ao cliente  
**And** não expõe detalhes internos de persistência.

## 14. Documentation and portfolio repositioning

O PR #5 continuará sendo a branch de trabalho para esta evolução.

Após a implementação, a documentação deve ser atualizada para refletir apenas capacidades comprovadas pelo código e testes.

Arquivos-alvo previstos:

- `README.md`;
- `docs/business-case.md`;
- `docs/engineering-decisions.md`;
- `docs/quality-hardening.md`;
- novo `docs/PORTFOLIO_CASE_STUDY.md`.

A narrativa central deve ser:

```text
problema de negócio
→ edição concorrente
→ risco de lost update
→ versão esperada no contrato
→ optimistic locking
→ SQL Server arbitra a corrida
→ HTTP 409 torna o conflito explícito
→ Testcontainers comprova o comportamento no banco real
```

## 15. Architectural positioning

Esta feature não altera a decisão de arquitetura do InnovationHub.

O sistema continua um **modular monolith** deliberado.

O ponto de portfólio é demonstrar que consistência e concorrência não exigem automaticamente microserviços, Kafka ou infraestrutura distribuída.

A decisão permanece:

> usar a arquitetura mais simples que protege corretamente as regras do domínio atual.

Microsserviços só devem ser considerados se surgirem evidências de necessidade, como:

- escala independente;
- ownership por equipes autônomas;
- releases independentes;
- isolamento de falha necessário;
- isolamento de dados ou requisitos operacionais específicos.

## 16. Out of scope

Esta feature não inclui:

- `PATCH`;
- ETag / `If-Match`;
- alteração de status;
- JWT/RBAC;
- auditoria completa de mudanças;
- event sourcing;
- Kafka ou mensageria;
- microserviços;
- nova tabela de histórico;
- alteração do mecanismo de geração do código corporativo;
- métricas de contenção não medidas;
- afirmação de uso multiusuário em produção.

Qualquer necessidade desses itens deve receber uma decisão/spec separada.

## 17. Files expected to change during implementation

Produção:

```text
backend/src/main/java/com/innovationhub/project/api/ProjectController.java
backend/src/main/java/com/innovationhub/project/api/UpdateProjectRequest.java
backend/src/main/java/com/innovationhub/project/application/ProjectService.java
backend/src/main/java/com/innovationhub/project/domain/Project.java
backend/src/main/java/com/innovationhub/shared/exception/GlobalExceptionHandler.java
backend/src/main/java/com/innovationhub/shared/exception/ProjectVersionConflictException.java
```

Testes, conforme estrutura existente:

```text
backend/src/test/java/com/innovationhub/project/api/
backend/src/test/java/com/innovationhub/project/application/
backend/src/test/java/com/innovationhub/project/infrastructure/
```

Documentação:

```text
README.md
docs/business-case.md
docs/engineering-decisions.md
docs/quality-hardening.md
docs/PORTFOLIO_CASE_STUDY.md
```

O plano de implementação deverá confirmar os nomes exatos dos testes existentes antes de editar ou criar arquivos.

## 18. Success criteria

Depois da implementação, um revisor deverá conseguir responder, com evidência do repositório:

1. qual problema de negócio o optimistic locking resolve;
2. como a versão chega ao cliente e volta no update;
3. por que a aplicação faz uma checagem de versão antes do commit;
4. por que `@Version` ainda é necessário mesmo com essa checagem;
5. como o SQL Server impede o lost update em uma corrida real;
6. por que o conflito é `409` e não `422`;
7. como Testcontainers comprova a semântica no banco-alvo;
8. por que o sistema continua como monólito modular em vez de adotar microsserviços.

A feature estará correta quando a narrativa de portfólio puder ser sustentada por contrato HTTP, código, testes e comportamento real do SQL Server — sem depender de afirmações não verificadas.