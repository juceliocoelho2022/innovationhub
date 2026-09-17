# InnovationHub v0.1 — Design

## Objetivo

Entregar uma base executável e escalável de um sistema corporativo de gestão de PD&I, com foco inicial em Projetos e Dashboard.

## Escopo da v0.1

- Cadastro de projetos.
- Consulta paginada de projetos.
- Consulta de projeto por ID.
- Resumo do portfólio.
- Persistência SQL Server.
- Migração de schema com Flyway.
- Validação de regras de domínio.
- Tratamento padronizado de erros.
- Concorrência otimista com `@Version`.
- Frontend React com dashboard.
- Docker Compose para banco, backend e frontend.
- Testes unitários e de controller.
- Pipeline CI.

## Arquitetura

Monólito modular organizado por feature. Módulos iniciais:

- `project`: entidade, regras, persistência e REST.
- `dashboard`: agregação de indicadores.
- `shared`: tratamento transversal de erros e configuração.

A arquitetura evita dependência do frontend em entidades JPA por meio de DTOs.

## Modelo de dados inicial

`projects`:
- id
- code
- name
- description
- status
- start_date
- end_date
- budget
- manager_name
- version
- created_at
- updated_at

## Regras

1. Projeto inicia em `DRAFT`.
2. `endDate >= startDate`.
3. `budget >= 0`.
4. Código de projeto é gerado pelo backend.
5. Consultas de lista são paginadas.
6. Atualizações futuras usarão `@Version` para detectar escrita concorrente.

## API

- `POST /api/v1/projects`
- `GET /api/v1/projects`
- `GET /api/v1/projects/{id}`
- `GET /api/v1/dashboard/summary`

## Erros

Erros de validação retornam HTTP 400. Recurso não encontrado retorna 404. Regras de negócio retornam 422. O payload segue `ProblemDetail`.

## Testes

- `ProjectServiceTest`: criação e regra de datas.
- `DashboardServiceTest`: agregação de indicadores.
- `ProjectControllerTest`: contrato HTTP de criação.
- CI executa `mvn verify` e build do frontend.

## Fora de escopo

JWT/RBAC, atividades, equipes, despesas, riscos, auditoria, Kafka, Redis e IA entram em versões seguintes.
