# InnovationHub — Portfolio Case Study

## 60-second explanation

O InnovationHub é um sistema corporativo para gestão de projetos de PD&I. A decisão arquitetural principal foi não introduzir microsserviços sem necessidade: o domínio atual cabe bem em um monólito modular, com transações locais e operação mais simples.

Um risco real do domínio é o *lost update*: dois gestores podem ler o mesmo projeto, editar e tentar salvar versões diferentes. Por isso, o contrato `PUT /api/v1/projects/{id}` exige a `version` que o cliente leu. Se ela já estiver obsoleta, a API devolve `409 Conflict` antes de alterar o projeto. Se duas requisições entrarem realmente em corrida, JPA `@Version` + SQL Server fazem a arbitragem no `flush`/commit. Testes MockMvc e Testcontainers comprovam os dois níveis de proteção.

## Technical walkthrough — 3 to 5 minutes

### 1. Business problem

Projetos de inovação concentram prazo, orçamento, responsáveis e informações executivas. Uma sobrescrita silenciosa pode remover uma alteração válida e gerar decisões baseadas em estado incorreto.

```text
Gestor A -> GET project -> version 5
Gestor B -> GET project -> version 5

A -> PUT version 5 -> success -> version 6
B -> PUT version 5 -> stale -> 409
```

### 2. HTTP contract

```http
PUT /api/v1/projects/{id}
```

`UpdateProjectRequest` carrega todos os campos editáveis e a `version` esperada. O endpoint não permite alterar diretamente `id`, código corporativo, status, timestamps ou versão.

### 3. Application protection

`ProjectService.update()`:
1. carrega o projeto ou retorna `404`;
2. compara a versão esperada com a atual;
3. rejeita stale request antes de qualquer mutação;
4. valida período;
5. delega a mutação permitida a `Project.updateDetails(...)`;
6. executa `repository.flush()`;
7. somente então constrói o `ProjectResponse`.

O `flush()` é importante porque materializa o incremento de versão e faz uma eventual exceção de optimistic locking acontecer dentro do caso de uso.

### 4. Persistence protection

A checagem manual não resolve uma corrida em que duas transações carregam a mesma versão antes do primeiro commit. Por isso `Project` continua usando `@Version`.

Conceitualmente, o banco protege a escrita como:

```text
UPDATE projects
SET ..., version = 6
WHERE id = ? AND version = 5
```

Se outra transação já mudou a versão, o update stale não confirma normalmente e o JPA sinaliza optimistic locking.

### 5. Error contract

Tanto uma versão stale detectada pela aplicação quanto uma falha real de optimistic locking resultam em:

```text
409 Conflict
Conflito de versão
O projeto foi alterado por outro usuário. Recarregue os dados antes de tentar novamente.
```

A API não expõe SQL, Hibernate ou stack trace.

### 6. Why modular monolith

O problema atual não exige deploy independente, comunicação remota, escala desigual por módulo ou ownership por equipes autônomas. Adicionar microservices aumentaria o custo operacional sem proteger melhor esta regra. O monólito modular mantém fronteiras claras e permite extração futura quando existir um requisito concreto.

## Decision / trade-off map

| Decisão | Risco/problema | Trade-off |
|---|---|---|
| Monólito modular | complexidade distribuída prematura | módulos compartilham release/processo |
| PUT + version | lost update e estado esperado implícito | cliente precisa enviar representação completa |
| stale check no service | conflito já conhecido | não elimina a janela de corrida |
| JPA `@Version` | corrida entre load e commit | conflito pode aparecer no flush |
| `409 Conflict` | estado do cliente ficou obsoleto | cliente precisa recarregar/reconciliar |
| Testcontainers SQL Server | diferenças entre banco fake e engine alvo | testes mais lentos/pesados |
| Flyway + validate | drift de schema | mudanças de banco exigem migration explícita |

## Interview Q&A

### Por que não usar microservices?
Porque a arquitetura deve responder a requisitos reais. Nesta versão não há necessidade comprovada de deploy/escala/ownership independentes. O monólito modular reduz custo sem abandonar fronteiras internas.

### Por que optimistic locking?
Porque o risco é impedir que uma edição antiga sobrescreva silenciosamente uma nova. Quando contenção não é conhecida como dominante, optimistic locking protege a consistência sem manter locks pessimistas durante o fluxo comum.

### Por que não apenas comparar `version` no service?
Porque duas transações podem ler a mesma versão antes do primeiro commit. A comparação ajuda stale requests já visíveis, mas `@Version` é a proteção final dessa corrida.

### Por que `409` e não `422`?
O payload pode obedecer às regras de domínio; o conflito surge porque o estado em que o cliente baseou a edição já mudou. Isso é conflito de estado do recurso.

### Por que `repository.flush()` antes da resposta?
Para forçar o update e a verificação de versão dentro do método transacional e obter a nova versão antes de serializar o response.

### Por que Testcontainers?
Porque a regra crítica depende de SQL Server/JPA. Um banco em memória poderia esconder diferenças de locking, DDL ou comportamento do provider.

### Por que não ETag / If-Match?
É uma alternativa válida, mas o projeto escolheu versão explícita no body para manter o contrato e a narrativa de domínio simples nesta versão. ETag pode ser uma evolução futura se houver necessidade.

### Por que não PATCH?
A atualização completa por PUT reduz merge parcial e validação condicional. PATCH só deve entrar se o produto realmente precisar de atualizações parciais.

## Evidence map

| Afirmação | Evidência |
|---|---|
| projeto nasce DRAFT e regras de service | `ProjectServiceTest` |
| PUT e status HTTP | `ProjectControllerTest` |
| versão ausente/negativa -> 400 | `ProjectControllerTest` |
| stale version -> 409 | `ProjectControllerTest`, `ProjectUpdateApiIntegrationTest` |
| update real incrementa versão no SQL Server | `ProjectUpdateApiIntegrationTest` |
| duas persistence contexts não causam lost update silencioso | `ProjectOptimisticLockingIntegrationTest` |
| Flyway e SQL Server reais | `SqlServerPersistenceIntegrationTest` |
| decisão de arquitetura | `docs/engineering-decisions.md` |
| regra e requisitos | `docs/business-case.md` |
| baseline de qualidade | `docs/quality-hardening.md` |

## Current limits

- Não há JWT/RBAC nesta versão.
- Não há auditoria completa de alterações.
- Não há PATCH ou ETag.
- Não há Kafka/event sourcing.
- Não há microservices.
- Não há claims de throughput, latência, disponibilidade ou contenção em produção.

## Closing pitch

> No InnovationHub, a tecnologia segue o risco do negócio. Para impedir lost updates eu torno a versão esperada explícita no contrato, rejeito estado stale com 409 e mantenho `@Version` como proteção final no SQL Server. Ao mesmo tempo, evito microservices porque o domínio atual não justifica essa complexidade. O resultado é um case de consistência transacional, contrato HTTP e decisão arquitetural baseada em necessidade — não em quantidade de tecnologias.
