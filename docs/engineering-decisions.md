# Engineering Decisions — InnovationHub

Este documento registra as decisões centrais do InnovationHub para demonstrar raciocínio arquitetural, trade-offs, qualidade e capacidade de evolução.

## 1. Contexto

O InnovationHub é um sistema de gestão de projetos de PD&I. A arquitetura busca equilibrar velocidade de desenvolvimento, baixo custo operacional, separação de responsabilidades, testabilidade e evolução futura.

## 2. Modular Monolith em vez de Microservices

**Decisão:** iniciar como monólito modular.

**Por quê:** o domínio ainda não exige deploy independente por módulo, escala desigual ou ownership por várias equipes.

**Trade-off:** os módulos compartilham processo e release, mas o sistema evita a complexidade prematura de múltiplos serviços.

Critérios que justificariam extração futura:
- escala independente;
- ciclo de release diferente;
- necessidade de isolamento de falha;
- ownership por equipe distinta.

## 3. DTOs na borda

Entidades JPA não são expostas diretamente. O contrato HTTP deve evoluir de forma independente da persistência.

Benefícios:
- menor acoplamento;
- validação explícita;
- prevenção de exposição acidental;
- facilidade para versionar e testar a API.

## 4. ProblemDetail

Erros são padronizados para permitir que consumidores diferenciem validação, recurso inexistente, conflito e falha interna sem depender de mensagens improvisadas.

## 5. Optimistic Locking

**Problema:** dois usuários podem editar o mesmo projeto quase simultaneamente.

**Decisão:** controle de concorrência otimista com @Version.

**Trade-off:** o cliente precisa tratar conflito, mas evita manter locks pessimistas por longos períodos.

## 6. Flyway

Mudanças de schema são versionadas e acompanham o código. O banco faz parte da entrega e deve ser reproduzível.

## 7. SQL Server

O SQL Server aproxima o projeto de ambientes corporativos. JPA reduz parte do acoplamento, mas tipos, migrations e consultas precisam ser validados contra o banco real.

## 8. Estratégia de testes

Prioridades:
- unitários para regras;
- Mockito para isolamento;
- MockMvc para contrato HTTP;
- JaCoCo como indicador auxiliar;
- CI para executar a verificação a cada mudança.

A evolução natural é ampliar integração com banco real/containers em cenários críticos.

## 9. Diagnóstico de falhas

Exemplo: criação de projeto retorna erro.

1. Verificar /actuator/health.
2. Revisar logs.
3. Confirmar conexão com SQL Server.
4. Verificar migrations Flyway.
5. Reproduzir o payload no Swagger.
6. Separar erro de validação, persistência ou concorrência.
7. Consultar métricas quando a observabilidade avançada estiver habilitada.

## 10. Roadmap com gatilhos

JWT/RBAC, observabilidade, mensageria e microsserviços só devem ser adicionados quando existir necessidade real que justifique o custo.
