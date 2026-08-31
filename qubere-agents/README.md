# Qubere Agent Platform

Generic Java 21, Spring Boot, and Spring AI framework for creating agentic AI systems.

Architecture reference:

`generic-agent-framework-spring-ai.md`

## Overview

Qubere Agent Platform is a compact Maven framework for building reusable, governed agents. It keeps the public contract jar small, places most framework capabilities in `agent-core`, and keeps database/storage infrastructure in `agent-storage`.

`agent-app` is not the framework itself. It is a reference Spring Boot host with REST APIs and a sample generic agent. Real applications can depend on `agent-api`, `agent-core`, and optionally `agent-storage`, or they can use `agent-app` while developing examples.

## Current Capabilities

- Version-aware agent registry using `agentId + version`.
- Startup descriptor validation and duplicate registration detection.
- Configurable default agent versions and per-run version selection.
- Runtime execution pipeline with authorization, guardrail, audit, governance, and execution-store hooks.
- Spring AI adapter through `AgentAiClient` and `SpringAiAgentClient`.
- Generic tool registry, tool execution, risk classification, audit events, and approval policy.
- Memory abstraction with in-memory vector-style retrieval support.
- Prompt template and prompt version management.
- Async queue, pending command store, approval lifecycle, resume/reject flows, and optional HTTP callbacks.
- Evaluation support for golden datasets, prompt regression checks, replay, observability summaries, and governance limits.
- JPA persistence for execution records, approvals, pending commands, tool audits, prompt templates, and evaluation results.
- PostgreSQL and Oracle manual DDL scripts, with H2-backed local tests.
- Secured opt-in admin endpoints for observability, evaluation, replay, and governance.

## Enterprise standards alignment

The framework design follows a composite production-agent standard rather than a single vendor-specific pattern:

- NIST AI RMF / NIST AI 600-1 for AI risk governance, evaluation, monitoring, and lifecycle controls.
- ISO/IEC 42001 for AI management-system discipline: ownership, documented controls, release gates, review, and continuous improvement.
- OWASP Top 10 for LLM / GenAI Applications for prompt injection, excessive agency, sensitive-data leakage, supply-chain, vector, output-handling, and cost-exhaustion risks.
- OpenTelemetry for vendor-neutral traces, metrics, logs, and context propagation.
- MCP as a future adapter standard for governed external tools, resources, and context.
- A2A as a future adapter standard for secure agent discovery and inter-agent task exchange.
- CloudEvents as a future event format for lifecycle, approval, tool, governance, and evaluation events.
- OpenAPI for REST API contract documentation and client generation.

See `generic-agent-framework-spring-ai.md` sections `21` and `22` for the detailed standards mapping, checklists, release gates, and implementation-status matrix.

## Modules

- `agent-api` - stable public contracts for agents, inputs, outputs, descriptors, and execution context.
- `agent-core` - runtime models, orchestration, registry, policy resolution, AI adapter, tools, memory, prompts, async approval, observability, security, evaluation, governance, and replay.
- `agent-storage` - portable JPA storage for execution records, approvals, pending commands, tool audits, prompt templates, and evaluation results.
- `agent-app` - optional runnable Spring Boot API shell and sample generic agent.

## Build and test

Run the full test suite:

```bash
mvn test
```

If Maven resolves `user.home` to `C:\` on Windows, use the workspace-local repository:

```powershell
mvn "-Duser.home=C:\WorkSpace\qubere-agent-platform" "-Dmaven.repo.local=C:\WorkSpace\qubere-agent-platform\.m2\repository" test
```

Package with PostgreSQL runtime driver:

```bash
mvn -Ppostgres package
```

Package with Oracle runtime driver:

```bash
mvn -Poracle package
```

## Database switching

The reference app supports PostgreSQL and Oracle via Maven/runtime profiles.

PostgreSQL runtime:

```bash
AGENT_DB=postgres
AGENT_DATASOURCE_URL=jdbc:postgresql://localhost:5432/agents
AGENT_DATASOURCE_USERNAME=agents
AGENT_DATASOURCE_PASSWORD=agents
```

Oracle runtime:

```bash
AGENT_DB=oracle
AGENT_DATASOURCE_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1
AGENT_DATASOURCE_USERNAME=agents
AGENT_DATASOURCE_PASSWORD=agents
```

Manual DDL scripts are under:

- `agent-storage/src/main/resources/db/manual/postgres/001_agent_execution_record.sql`
- `agent-storage/src/main/resources/db/manual/oracle/001_agent_execution_record.sql`

See `docs/database/manual-ddl.md`.

## Runtime behavior controls

Framework defaults can be controlled through `application.yml` / `application.properties`:

```yaml
spring:
  ai:
    model:
      chat: none # opt in to a real provider, e.g. openai, when credentials are configured

agent-platform:
  ai:
    default-provider: openai
    default-model: gpt-4.1-mini
    spring:
      enabled: false
  registry:
    strict-descriptor-validation: true
    default-versions:
      generic.echo-analysis: 0.1.0
  runtime:
    default-mode: recommend
    max-steps: 8
    temperature: 0.2
    max-output-tokens: 2048
    allow-tool-calls: true
    require-human-approval: false
    allowed-tools: []
  async:
    enabled: true
    worker-enabled: false # enable in deployed apps that should process queued runs in-process
    poll-interval-millis: 1000
    max-runs-per-poll: 1
    approval-expiration-minutes: 60
    callback:
      enabled: false
      max-attempts: 3
      retry-backoff-millis: 500
      timeout-seconds: 5
      signing-secret: ""
  governance:
    enabled: true
    max-runs-per-tenant-per-minute: 0 # 0 disables this limiter
    max-runs-per-actor-per-minute: 0 # 0 disables this limiter
    max-estimated-cost-usd-per-run: 0 # 0 disables this limiter
    estimated-cost-usd-per-thousand-tokens: 0
  admin:
    enabled: false # disabled by default; enable only behind trusted/admin boundaries
    token: "" # required when admin.enabled=true
  definitions:
    "[generic.echo-analysis]": # bracket notation preserves dots in map keys
      enabled: true
      model-provider: openai
      model-name: gpt-4.1-mini
      prompt-version: 0.1.0
      memory-enabled: true
      max-memory-results: 3
      max-tool-calls: 4
      timeout-seconds: 30
```

Callers can override these per run through `AgentRunOptions`. Null option values mean "use configured default".

## First API in reference app

For complete end-to-end testing scenarios, including Oracle profile setup, synchronous runs, async approval, governance, admin observability, replay, and database verification, see:

```text
agent-app/TESTING.md
```

```http
GET /api/agents
POST /api/agents/{agentId}/runs
POST /api/agents/async/process-next
POST /api/agents/approvals/{approvalId}/approve
POST /api/agents/approvals/{approvalId}/reject
POST /api/agents/approvals/{approvalId}/decision
GET /api/agents/runs/{executionId}
```

Example run request with an explicit version:

```json
{
  "agentVersion": "0.1.0",
  "input": {
    "message": "hello"
  },
  "options": {
    "mode": "RECOMMEND",
    "maxSteps": 4
  }
}
```

Example async request:

```json
{
  "agentVersion": "0.1.0",
  "async": true,
  "callbackUrl": "https://example.com/agent-callback",
  "input": {
    "message": "hello"
  },
  "options": {
    "requireHumanApproval": true
  }
}
```

If approval is required, the run returns `WAITING_FOR_APPROVAL` with an `approvalId`. Approve it with:

```http
POST /api/agents/approvals/{approvalId}/approve
```
