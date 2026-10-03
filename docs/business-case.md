# InnovationHub — Business Case and Domain Rules

## 1. Problema de negócio

Áreas de Pesquisa, Desenvolvimento e Inovação (PD&I) precisam acompanhar projetos com responsáveis, prazos, orçamento, status e indicadores. Quando essas informações ficam dispersas em planilhas, e-mails ou ferramentas desconectadas, surgem problemas de visibilidade, retrabalho, inconsistência e perda de histórico.

O InnovationHub centraliza o portfólio de inovação e oferece uma fundação evolutiva para projetos, atividades, equipes, orçamento, riscos, indicadores, auditoria e inteligência artificial.

## 2. Usuários e necessidades

- **Gestor de inovação:** visualizar o portfólio e identificar projetos que exigem atenção.
- **Gerente de projeto:** manter dados, prazo, orçamento e responsáveis atualizados sem perder alterações concorrentes.
- **Equipe de projeto:** trabalhar sobre informações consistentes e versionadas.
- **Executivo / patrocinador:** consultar uma visão consolidada para tomada de decisão.

## 3. Riscos do domínio

- dois usuários sobrescreverem alterações uns dos outros;
- projetos com período inconsistente;
- orçamento negativo;
- acoplamento entre contrato HTTP e entidade de persistência;
- evolução descontrolada do schema;
- complexidade distribuída prematura sem necessidade operacional.

## 4. Regras de negócio

### BR-001 — Estado inicial
Todo projeto nasce em `DRAFT`.

### BR-002 — Código corporativo
Cada projeto recebe um código corporativo gerado pelo sistema.

### BR-003 — Período válido
A data final não pode ser anterior à data inicial.

### BR-004 — Orçamento válido
O orçamento não pode ser negativo.

### BR-005 — Atualização concorrente não pode causar lost update
Uma atualização baseada em uma versão obsoleta não pode sobrescrever silenciosamente uma versão mais recente do projeto.

O contrato `PUT /api/v1/projects/{id}` recebe a `version` esperada pelo cliente. O serviço rejeita uma versão já obsoleta antes de mutar o agregado; JPA `@Version` e SQL Server permanecem como arbitragem final para corridas que ocorram entre leitura e commit. Ambos os conflitos são traduzidos para `409 Conflict`.

### BR-006 — Contrato de API separado da persistência
Entidades JPA não são expostas diretamente; DTOs representam entrada e saída da API.

### BR-007 — Migração controlada
Flyway é a autoridade de evolução do schema; Hibernate valida o mapeamento.

### BR-008 — Dashboard como leitura agregada
O dashboard entrega visão consolidada sem obrigar o frontend a reconstruir regras de agregação sobre entidades internas.

## 5. Requisitos funcionais

- **FR-001** Criar projeto de PD&I.
- **FR-002** Consultar projeto por identificador.
- **FR-003** Listar projetos com paginação e ordenação.
- **FR-004** Disponibilizar resumo agregado do portfólio.
- **FR-005** Validar datas e orçamento.
- **FR-006** Atualizar os campos editáveis de um projeto por `PUT`, exigindo `version` esperada e retornando `409` quando ela estiver obsoleta ou ocorrer optimistic locking no commit.
- **FR-007** Retornar erros em formato HTTP consistente com `ProblemDetail`.

## 6. Requisitos não funcionais

- **NFR-001 — Simplicidade operacional:** evitar complexidade distribuída que o domínio atual não exige.
- **NFR-002 — Modularidade:** manter limites explícitos entre projetos, dashboard e módulos futuros.
- **NFR-003 — Testabilidade:** regras, contrato HTTP e persistência possuem testes automatizados.
- **NFR-004 — Banco-alvo real:** comportamentos críticos de SQL Server e concorrência são validados com Testcontainers.
- **NFR-005 — Observabilidade:** expor health check e métricas operacionais.
- **NFR-006 — Evolução:** adicionar módulos futuros sem reescrever a fundação.

## 7. Decisão arquitetural: monólito modular

O domínio inicial usa uma única base operacional e não exige deploy independente, escala desigual ou ownership separado por equipes. O monólito modular reduz custo de infraestrutura, chamadas remotas, versionamento de contratos internos e observabilidade distribuída desnecessária.

A extração futura de módulos só deve ocorrer quando existirem requisitos concretos de escala, autonomia, isolamento de falha, dados ou ciclos de release.

## 8. Fluxo crítico de concorrência

```text
GET /projects/10 -> version 5

Gestor A envia PUT version 5
-> service valida versão
-> altera estado
-> flush SQL Server / @Version
-> 200 OK, version 6

Gestor B envia PUT ainda com version 5
-> versão atual é 6
-> nenhuma mutação é aplicada
-> 409 Conflict
```

Há ainda uma segunda defesa: se duas requisições carregarem a mesma versão antes de qualquer commit, o `@Version` faz o `UPDATE` condicionado à versão e uma delas falha por optimistic locking. Assim, a checagem de aplicação melhora a resposta para stale requests conhecidos, mas não substitui o controle do banco.

## 9. Critérios de aceitação

- **AC-001:** criação válida recebe id, código corporativo e `DRAFT`.
- **AC-002:** `endDate < startDate` é rejeitado.
- **AC-003:** orçamento negativo é rejeitado por validação.
- **AC-004:** `PUT` com versão atual atualiza e devolve uma versão maior.
- **AC-005:** `PUT` com versão obsoleta retorna `409` sem sobrescrever o estado recente.
- **AC-006:** duas transações com a mesma versão não conseguem confirmar silenciosamente dois estados concorrentes.
- **AC-007:** conflitos expõem `ProblemDetail` sem detalhes de Hibernate, SQL ou stack trace.

## 10. Rastreabilidade de regras e evidências

| Regra | Requisito | Implementação | Evidência |
|---|---|---|---|
| BR-001 | FR-001 | `Project` inicializa `DRAFT` | `ProjectServiceTest` |
| BR-002 | FR-001 | geração de código no `ProjectService` | `ProjectServiceTest` |
| BR-003 | FR-005/FR-006 | validação de período no service | `ProjectServiceTest`, `ProjectControllerTest` |
| BR-004 | FR-005/FR-006 | Bean Validation `@PositiveOrZero` | `ProjectControllerTest` |
| BR-005 | FR-006/FR-007 | `version` no DTO, stale check, `@Version`, `flush()`, `409` | `ProjectServiceTest`, `ProjectControllerTest`, `ProjectUpdateApiIntegrationTest`, `ProjectOptimisticLockingIntegrationTest` |
| BR-006 | FR-001..FR-007 | request/response DTOs | testes de service e controller |
| BR-007 | NFR-004 | Flyway + `ddl-auto=validate` | `SqlServerPersistenceIntegrationTest` |
| BR-008 | FR-004 | módulo Dashboard | `DashboardServiceTest` |

## 11. Tecnologia como consequência do problema

| Tecnologia / padrão | Problema resolvido |
|---|---|
| Java 21 + Spring Boot | casos de uso e API corporativa |
| SQL Server | persistência transacional alvo |
| JPA `@Version` | impedir lost update em corrida real |
| `version` no PUT | tornar o estado esperado explícito no contrato |
| DTOs + Bean Validation | proteger a borda da API |
| `ProblemDetail` | contrato de erro consistente |
| Flyway | schema reproduzível e versionado |
| Testcontainers | validar o comportamento no banco real |
| Actuator + Prometheus | diagnóstico operacional básico |

## 12. Limites atuais

O projeto não afirma uso multiusuário em produção, taxa de contenção, SLO, throughput ou latência de produção. JWT/RBAC, auditoria completa, mensageria, PATCH/ETag e microsserviços permanecem fora desta versão.

## 13. Como apresentar em entrevista

> O InnovationHub resolve um problema de gestão de portfólio de PD&I. Eu mantive um monólito modular porque o domínio atual não justificava microsserviços. No fluxo de edição, tratei lost update como regra de negócio: o cliente envia a versão que leu, stale requests retornam 409 e o `@Version` no SQL Server continua protegendo a corrida real no commit. Isso é comprovado por MockMvc e Testcontainers contra SQL Server real.
