# InnovationHub — Business Case and Domain Rules

## 1. Problema de negócio

Áreas de Pesquisa, Desenvolvimento e Inovação (PD&I) precisam acompanhar projetos com responsáveis, prazos, orçamento, status e indicadores. Quando essas informações ficam dispersas em planilhas, e-mails ou ferramentas desconectadas, surgem problemas de visibilidade, retrabalho, inconsistência e perda de histórico.

O InnovationHub foi projetado para centralizar o portfólio de inovação e oferecer uma base evolutiva para gestão de projetos, atividades, equipes, orçamento, riscos, indicadores, auditoria e inteligência artificial.

## 2. Usuários e necessidades

- **Gestor de inovação**: precisa visualizar o portfólio e identificar projetos que exigem atenção.
- **Gerente de projeto**: precisa manter dados, prazo, orçamento e status atualizados.
- **Equipe de projeto**: precisa trabalhar sobre informações consistentes e versionadas.
- **Executivo / patrocinador**: precisa de visão consolidada para tomada de decisão.

## 3. Riscos do domínio

- dois usuários sobrescreverem alterações uns dos outros;
- projeto criado com datas inconsistentes;
- orçamento inválido ou negativo;
- API expor diretamente o modelo de persistência;
- crescimento prematuro para microsserviços sem necessidade operacional;
- perda de rastreabilidade sobre mudanças futuras de orçamento, risco e status.

## 4. Regras de negócio

### BR-001 — Estado inicial

Todo projeto nasce no estado `DRAFT`.

### BR-002 — Código corporativo

Cada projeto recebe um código corporativo gerado pelo sistema para identificação única no portfólio.

### BR-003 — Período válido

A data final de um projeto não pode ser anterior à data inicial.

### BR-004 — Orçamento válido

O orçamento de um projeto não pode ser negativo.

### BR-005 — Concorrência otimista

Atualizações concorrentes não podem sobrescrever silenciosamente uma versão mais recente do projeto. A entidade utiliza controle de versão otimista por `@Version`.

### BR-006 — Contrato de API separado da persistência

Entidades JPA não são expostas diretamente. DTOs representam os contratos de entrada e saída da API.

### BR-007 — Migração controlada

A evolução do schema é explícita e versionada por Flyway. O estado do banco não depende de criação automática de tabelas em produção.

### BR-008 — Dashboard como leitura agregada

O dashboard deve responder perguntas de portfólio sem obrigar o frontend a reconstruir regras de agregação a partir de entidades internas.

## 5. Requisitos funcionais da versão inicial

- **FR-001** Criar projeto de PD&I.
- **FR-002** Consultar projeto por identificador.
- **FR-003** Listar projetos com paginação e ordenação.
- **FR-004** Disponibilizar resumo agregado do portfólio.
- **FR-005** Validar datas e orçamento antes da persistência.
- **FR-006** Detectar conflito de atualização concorrente.
- **FR-007** Retornar erros em formato HTTP consistente.

## 6. Requisitos não funcionais

- **NFR-001 — Simplicidade operacional:** a primeira versão deve evitar complexidade distribuída que o domínio ainda não exige.
- **NFR-002 — Modularidade:** limites entre projetos, dashboard e módulos futuros devem permanecer explícitos.
- **NFR-003 — Testabilidade:** regras de domínio, API e persistência devem possuir testes automatizados.
- **NFR-004 — Banco real:** comportamentos relevantes do SQL Server e de concorrência devem ser validados com Testcontainers.
- **NFR-005 — Observabilidade:** a API deve expor health check e métricas operacionais.
- **NFR-006 — Evolução:** módulos futuros devem poder ser adicionados sem exigir reescrita da fundação.

## 7. Por que monólito modular

O domínio inicial ainda é pequeno, possui uma única base operacional e não exige autonomia de deploy entre equipes independentes.

A decisão de usar monólito modular reduz:

- custo de infraestrutura;
- comunicação remota desnecessária;
- complexidade de observabilidade distribuída;
- versionamento de contratos internos;
- falhas de rede entre componentes que podem permanecer no mesmo processo.

Ao mesmo tempo, os módulos mantêm fronteiras claras para permitir extração futura caso escala, autonomia ou isolamento de dados justifiquem microsserviços.

## 8. Cenário crítico — concorrência

Imagine dois gestores abrindo o mesmo projeto simultaneamente.

```text
Usuário A lê versão 5
Usuário B lê versão 5

Usuário A altera orçamento
-> salva versão 6

Usuário B altera prazo usando a versão antiga 5
-> tentativa deve ser detectada como conflito
```

Sem controle de versão, a atualização de B poderia sobrescrever silenciosamente a alteração de A.

Com `@Version`, a aplicação trata a concorrência como uma regra explícita do sistema.

## 9. Critérios de aceitação

### AC-001 — Criação válida

**Given** nome, período e orçamento válidos  
**When** um projeto é criado  
**Then** ele recebe um identificador  
**And** um código corporativo  
**And** nasce em `DRAFT`.

### AC-002 — Período inválido

**Given** uma data final anterior à inicial  
**When** o projeto é enviado  
**Then** a requisição deve ser rejeitada antes de persistir estado inválido.

### AC-003 — Orçamento negativo

**Given** orçamento menor que zero  
**When** o projeto é enviado  
**Then** a API deve retornar erro de validação.

### AC-004 — Atualização concorrente

**Given** duas leituras da mesma versão do projeto  
**When** a primeira atualização é confirmada  
**And** a segunda tenta persistir a versão obsoleta  
**Then** a aplicação deve detectar conflito em vez de sobrescrever silenciosamente o registro.

## 10. Mapeamento tecnologia -> problema

| Tecnologia / padrão | Problema resolvido |
|---|---|
| Java 21 + Spring Boot | API e regras de aplicação |
| Spring Data JPA | persistência e modelagem relacional |
| SQL Server | armazenamento transacional corporativo |
| `@Version` | proteção contra lost update |
| DTOs | desacoplamento entre API e entidade |
| Bean Validation | rejeição antecipada de dados inválidos |
| ProblemDetail | contrato padronizado de erro |
| Flyway | evolução controlada do schema |
| Testcontainers | teste com SQL Server real |
| Actuator + Prometheus | saúde e métricas operacionais |
| Docker Compose | ambiente reproduzível |

## 11. Evolução orientada ao negócio

As próximas versões devem adicionar capacidades na ordem das necessidades do domínio, não apenas por tecnologia:

1. **Segurança e RBAC** — quem pode visualizar ou alterar cada informação.
2. **Atividades e equipes** — execução operacional do projeto.
3. **Orçamento e despesas** — acompanhamento financeiro.
4. **Riscos e indicadores** — governança do portfólio.
5. **Auditoria** — histórico imutável de alterações relevantes.
6. **Innovation AI** — apoio à análise somente após o domínio e os dados estarem estruturados.

## 12. Como apresentar em entrevista

> O InnovationHub resolve um problema de gestão de portfólio de PD&I. Eu optei por monólito modular porque o domínio inicial ainda não justificava microsserviços. Uma decisão importante foi tratar concorrência como regra real: dois gestores podem editar o mesmo projeto, então usei optimistic locking com `@Version` para impedir lost updates. A API usa DTOs, validação, ProblemDetail, Flyway e testes com SQL Server real para tornar essas regras verificáveis.
