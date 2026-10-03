# Engineering Decisions — InnovationHub

Este documento registra decisões centrais do InnovationHub e os trade-offs que as motivam.

## 1. Contexto

O InnovationHub gerencia projetos de PD&I. A arquitetura busca proteger consistência de dados e regras do domínio com o menor custo operacional necessário para a fase atual.

## 2. Modular Monolith em vez de Microservices

**Problema:** introduzir serviços independentes sem necessidade concreta adicionaria rede, deploys, contratos internos, tracing distribuído e operação sem resolver um requisito atual.

**Decisão:** iniciar como monólito modular.

**Trade-off:** módulos compartilham processo e release. Em troca, o sistema preserva transações locais, operação simples e fronteiras de código explícitas.

Critérios para reconsiderar a decisão:
- escala independente comprovada;
- ciclos de release distintos;
- isolamento de falha necessário;
- ownership por equipes autônomas;
- isolamento de dados/requisitos operacionais específicos.

## 3. DTOs na borda

Entidades JPA não são contratos HTTP. `CreateProjectRequest`, `UpdateProjectRequest` e `ProjectResponse` mantêm API e persistência desacopladas, permitem validação explícita e evitam exposição acidental do modelo interno.

## 4. ProblemDetail

Erros HTTP são padronizados para distinguir validação (`400`), recurso inexistente (`404`), conflito de estado (`409`) e regra de negócio (`422`). O contrato não deve expor stack trace, SQL ou detalhes de Hibernate.

## 5. Optimistic Locking como proteção de domínio

**Problema:** dois usuários podem editar a mesma versão de um projeto. Sem controle de concorrência, a última gravação poderia apagar silenciosamente uma alteração já confirmada.

**Regra:** uma atualização baseada em uma versão obsoleta não pode sobrescrever uma versão mais recente.

**Decisão:** `PUT /api/v1/projects/{id}` recebe a `version` que o cliente leu; o service compara essa versão com o estado atual antes de mutar. Se já estiver obsoleta, lança conflito. Depois da mutação, `repository.flush()` força o SQL/JPA a executar o update ainda dentro do caso de uso. `@Version` permanece como arbitragem final se duas transações tiverem carregado a mesma versão antes do primeiro commit.

```text
request version != current version
-> reject before mutation
-> HTTP 409

request version == current version
-> apply update
-> flush
-> SQL UPDATE guarded by version
-> race winner succeeds
-> stale concurrent writer fails
-> HTTP 409
```

**Por que `409` e não `422`:** o payload pode ser semanticamente válido; o problema é que a representação usada pelo cliente não corresponde mais ao estado atual do recurso.

**Por que não lock pessimista:** o projeto não possui evidência de contenção alta que justifique manter locks de banco durante leituras/edições. Optimistic locking mantém o fluxo comum leve e transforma colisões em conflitos explícitos.

**Trade-off:** após `409`, o cliente precisa recarregar o estado atual e decidir como reconciliar a edição. Se dados futuros mostrarem contenção elevada, a estratégia deverá ser reavaliada.

## 6. PUT com versão explícita

**Decisão:** usar atualização completa por `PUT` e carregar a versão no body.

**Alternativas consideradas:**
- `PATCH`: flexível, mas adicionaria merge parcial e validação condicional desnecessários agora;
- ETag/`If-Match`: apropriado no protocolo HTTP, porém adicionaria uma camada conceitual sem necessidade para o escopo atual.

A versão explícita torna a regra simples de ler, testar e explicar. ETag pode ser considerado em uma evolução compatível se houver necessidade real.

## 7. Flyway como autoridade de schema

Migrações acompanham o código e Hibernate opera com `ddl-auto=validate`. O banco faz parte do artefato versionado e a aplicação deve falhar se mappings e schema divergirem.

## 8. SQL Server como banco-alvo

O SQL Server representa o mecanismo transacional real do projeto. Testes que dependem de versão, locking, identity e DDL são executados contra o engine alvo via Testcontainers, não substituídos por H2.

## 9. Estratégia de testes orientada ao risco

- **JUnit/Mockito:** regras e orquestração do service;
- **MockMvc:** contrato HTTP, Bean Validation e `ProblemDetail`;
- **Testcontainers + SQL Server:** incremento real de `@Version`, stale update e corrida entre persistence contexts;
- **GitHub Actions:** `mvn verify`, frontend e validações de Compose/Prometheus.

Cobertura não é tratada como prova suficiente por si só; o objetivo é exercer os modos de falha relevantes.

## 10. Observabilidade

Actuator, Micrometer/Prometheus e Grafana oferecem uma baseline diagnóstica. O projeto não define SLOs de produção sem workload representativo e não afirma observabilidade distribuída para uma aplicação que continua monolítica.

## 11. Regra de evolução

JWT/RBAC, auditoria completa, mensageria e microsserviços só devem entrar quando um requisito ou risco concreto justificar o custo. O princípio continua sendo: **usar a arquitetura mais simples que protege corretamente o domínio atual.**
