# Qubere Agent Framework

## 1. Purpose and design philosophy

Qubere Agent Framework is a production-oriented Java 21, Spring Boot, and Spring AI foundation for building governed agentic systems. It separates domain reasoning from platform concerns:

- **Agents own domain reasoning, prompts, and decision outputs**
- **The framework owns lifecycle, safety, governance, persistence, orchestration, and observability**

The repository is intentionally multi-module:

- `qubere-agent-api` keeps stable contracts small and Spring-free
- `qubere-agent-core` implements the runtime engine and framework standards
- `qubere-agent-storage` adds optional JPA durability
- `qubere-echo-agent` is the primary runnable reference host on port `8080`
- `qubere-document-agent` is the second runnable reference service on port `8081`

The framework is designed for real multi-tenant services, not single-agent demos: typed contracts, governed tools, human approval, workflow budgets, auditability, and safe defaults are first-class concerns.

## 2. Agentic AI standards embodied by the framework

### 2.1 Stable agent contracts

Every agent implements `Agent<I, O>` and publishes an `AgentDescriptor` with:

- stable `id`
- `version`
- `description`
- `riskLevel`
- `capabilities`

`AgentRegistry` indexes agents by `id + version`, validates descriptors at startup when strict validation is enabled, rejects duplicates, and resolves a default version from configuration or the highest registered version.

### 2.2 Typed input and output

The framework expects typed `AgentInput`/`AgentOutput` contracts. Model output is not trusted as free text; agents are expected to bind to DTOs or structured maps and validate before acting, persisting, or returning results.

### 2.3 Governed tool execution

Tools execute through `ToolExecutionService`, which enforces:

- allow-lists
- caller permission checks
- approval policy
- dry-run blocking for side-effecting tools
- run-budget and workflow-budget consumption
- resilience wrapping
- redaction-aware audit
- durable `agent_tool_call` records

`agent.call` is implemented as a real governed tool (`AgentCallTool`), so LLM-driven delegation cannot bypass tool governance.

### 2.4 Guardrails

The default guardrail is secure by default, not allow-all:

- `DefaultAgentGuardrailService` blocks null input
- enforces `agent-platform.guardrails.max-input-size-bytes`
- blocks configurable prompt-injection denylist patterns

The guardrail library is composable:

- `CompositeAgentGuardrailService`
- `PiiDetectionGuardrailService`
- `OutputSchemaConformanceGuardrail`
- `HallucinatedIdRejectionGuardrail`

### 2.5 Human-in-the-loop execution

High-risk behavior can pause execution for approval before side effects occur. Approval state is durable, queryable, resumable, and applies both to full agent runs and approval-gated tool calls.

### 2.6 Observability and auditability

Every run has durable execution records, execution-log events, tool-call records, optional model-usage rows, and an OpenTelemetry-shaped event pipeline. Optional OTLP export emits real spans over gRPC or HTTP/protobuf.

### 2.7 Explicit, scoped memory

The framework distinguishes:

- request/run context
- retrieved memory
- prompt state
- durable long-term vector memory

`SpringAiVectorMemoryStore` adapts any Spring AI `VectorStore` while enforcing tenant and namespace isolation. Missing tenant context fails closed.

### 2.8 Security and privacy

Caller identity is fail-closed in strict mode. Tenant and actor identity come only from the configured `AgentCallerIdentityResolver`, never from orchestration propagation headers. Invalid JWTs resolve to unresolved identity rather than throwing, so one place decides whether the request is rejected.

### 2.9 Evaluation and safety testing

The framework supports two distinct release-gate styles:

- **golden datasets** for expected correct behavior
- **red-team suites** for adversarial safety behavior

They are intentionally separate because pass conditions are inverted: a guardrail block is a golden-test failure but a red-team success.

### 2.10 Risk-management alignment

The framework maps cleanly to enterprise AI control families:

- NIST AI RMF / NIST AI 600-1
- ISO/IEC 42001
- OWASP LLM Top 10 / GenAI App Top 10
- OpenTelemetry/SRE operational standards
- release governance with approval and evaluation gates

## 3. High-level architecture and module responsibilities

```text
Client / API Consumer
        |
        v
Reference or host application
        |
        +-- AgentController / admin APIs
        +-- AgentRuntimeService
        +-- AgentRegistry
        +-- AgentPolicyResolver
        +-- AgentGuardrailService
        +-- ToolExecutionService
        +-- AgentAiClient
        +-- Memory store / VectorStore adapter
        +-- Async queue / approval / callbacks
        +-- Orchestration / remote delegation
        +-- Observability / evaluation / replay
        |
        v
JPA store / Vector store / External services / Remote agent services
```

### 3.1 `qubere-agent-api`

Stable public contracts:

- `Agent<I, O>`
- `AgentDescriptor`
- `AgentExecutionContext`
- `AgentRiskLevel`
- input/output marker contracts

This module stays intentionally small and Spring-free so applications can depend on the contract without pulling in the full runtime.

### 3.2 `qubere-agent-core`

Framework engine and standards implementation:

- version-aware registry
- runtime lifecycle and policy resolution
- authorization and caller-identity seam
- tool governance and `agent.call`
- Spring AI integration
- memory abstraction and vector-store adapter
- async queue/approval/callback abstractions
- checkpointing
- orchestration and remote delegation
- governance, resilience, health, observability
- evaluation, replay, red-team
- MCP tool bridge

### 3.3 `qubere-agent-storage`

Optional JPA durability:

- execution store
- execution-log listener
- tool-call recorder
- approval store
- pending-command store
- async database queue
- prompt version store
- checkpoint store
- distributed workflow budget store
- evaluation result store
- JPA golden dataset repository
- manual PostgreSQL and Oracle DDL

### 3.4 `qubere-echo-agent`

Reference Spring Boot host application:

- runs on port `8080`
- exposes the standard REST/admin APIs
- ships sample agents and a sample tool
- exercises storage, governance, approval, evaluation, replay, Docker, and CI paths

### 3.5 `qubere-document-agent`

Second reference service:

- runs on port `8081`
- hosts `document.intake` and `document.intelligence`
- includes `document.context.lookup`
- reflects an in-progress migration from a TypeScript application into the framework

It is useful as an architectural proof that the framework can host a second bounded context, but it is not yet a fully exercised second reference app; today it has a single Spring context-load test.

## 4. Execution lifecycle

### 4.1 Synchronous path

1. Resolve caller identity
2. Build `AgentExecutionContext`
3. Resolve agent and version
4. Resolve effective policy
5. Consume workflow budget if applicable
6. Authorize run
7. Apply input guardrails
8. Persist `agent_execution_record` as started
9. Invoke agent with retry and timeout handling
10. Govern and record tool/model activity during the run
11. Persist completion or failure
12. Emit audit and pipeline events

### 4.2 Asynchronous path

The async runtime can queue work in memory or in the database:

- default queue type: `memory`
- durable queue type: `database`

Async submission persists a pending command and/or queue command, returns an execution handle, and later resumes processing through the same runtime.

### 4.3 Approval pause/resume

Approval can be required:

- before an agent run proceeds
- before an approval-gated tool call executes

When approval is required:

- the run moves to `WAITING_FOR_APPROVAL`
- `agent_approval_request` stores the durable approval state
- approval APIs resume, reject, or expire the request

### 4.4 Checkpointing and resumability

`AgentCheckpointScope` implements **durable step memoization**, not stack capture. Java cannot serialize a paused call stack safely, so the framework makes resumability explicit:

- agents express work as named `step(...)` calls
- each completed step result is stored
- after approval resume, the agent re-runs from the top
- completed steps replay stored results instead of re-executing side effects

This prevents duplicate side effects on resume, but it is not a compensation model and does not undo already-completed writes.

## 5. REST API standard

The reference applications expose the framework over REST.

### 5.1 Core endpoints

```http
GET  /api/agents
POST /api/agents/{agentId}/runs
POST /api/agents/async/process-next
POST /api/agents/approvals/{approvalId}/approve
POST /api/agents/approvals/{approvalId}/reject
POST /api/agents/approvals/{approvalId}/decision
GET  /api/agents/runs/{executionId}
```

### 5.2 Admin endpoints

Enabled only when `agent-platform.admin.enabled=true` and guarded by `X-Agent-Admin-Token`.

```http
POST /api/agents/admin/evaluations/{datasetName}/run
GET  /api/agents/admin/evaluations
GET  /api/agents/admin/prompts/agents/{agentId}
GET  /api/agents/admin/prompts/{promptId}/versions/{version}
POST /api/agents/admin/prompts
POST /api/agents/admin/prompts/{promptId}/versions/{version}/activate
POST /api/agents/admin/prompts/{promptId}/versions/{version}/deprecate
POST /api/agents/admin/prompts/{promptId}/versions/{version}/archive
GET  /api/agents/admin/approvals
GET  /api/agents/admin/approvals/{approvalId}
POST /api/agents/admin/approvals/expire
GET  /api/agents/admin/observability/summary
GET  /api/agents/admin/observability/events
POST /api/agents/admin/runs/replay
```

### 5.3 Request semantics

`POST /api/agents/{agentId}/runs` accepts:

- `input`
- optional `agentVersion`
- optional `options`
- optional top-level `async`
- optional `callbackUrl`
- optional `idempotencyKey`

### 5.4 Propagation headers

Cross-service orchestration uses:

- `X-Correlation-Id`
- `X-Idempotency-Key`
- `X-Agent-Workflow-Id`
- `X-Agent-Parent-Execution-Id`

These are **linkage headers only**. They never establish tenant or actor identity.

### 5.5 Error model

`ApiErrorResponse` standardizes errors with:

- `timestamp`
- `status`
- `code`
- `message`
- `path`
- `details`

The reference handlers map 400, 404, 405, 409, 415, 429, 500, 503, and timeout conditions consistently, and log unhandled 500s server-side.

## 6. Database design

`qubere-agent-storage` is optional, but when present it provides the framework's durable operational schema. PostgreSQL and Oracle manual DDL are maintained under `qubere-agent-storage/src/main/resources/db/manual/`.

### 6.1 Execution and observability tables

| Table | Purpose |
| --- | --- |
| `agent_execution_record` | one row per execution; includes `workflow_id` and `parent_execution_id` |
| `agent_execution_log` | timeline of pipeline events per execution |
| `agent_model_usage` | one row per model call attempt, including token/cost metadata when available |

### 6.2 Tool-governance tables

| Table | Purpose |
| --- | --- |
| `agent_tool_call` | durable per-call governance and audit trail |
| `agent_tool_audit_event` | audit-event stream for tool lifecycle events |

`agent_tool_call` is the analytics-grade record of attempted tool execution. `agent_tool_audit_event` remains useful as a simpler audit stream.

### 6.3 Approval and async tables

| Table | Purpose |
| --- | --- |
| `agent_approval_request` | durable approval state |
| `agent_pending_command` | persisted command payload for resumable work |
| `agent_async_queue_command` | durable database-backed queue |

### 6.4 Prompt, checkpoint, and workflow tables

| Table | Purpose |
| --- | --- |
| `agent_prompt_template` | prompt versions and status transitions |
| `agent_checkpoint` | completed step memoization for resumable runs |
| `agent_workflow_budget` | distributed workflow counters with optimistic locking |

### 6.5 Evaluation tables

| Table | Purpose |
| --- | --- |
| `agent_evaluation_result` | persisted evaluation run summaries |
| `agent_evaluation_dataset` | operationally curated golden datasets |

### 6.6 Schema notes that matter

- `agent_execution_record` stores execution lifecycle summary, not every model/tool detail
- `agent_model_usage` exists because one agent run can make zero, one, or many model calls
- `agent_workflow_budget` uses optimistic locking so distributed workflows cannot overspend by concurrent reads
- manual DDL exists for PostgreSQL and Oracle because those deployments may run with `ddl-auto=validate` or `none`

## 7. Spring AI integration standards

The framework uses Spring AI as the provider-facing integration layer and wraps it behind framework abstractions.

### 7.1 `AgentAiClient`

`AgentAiClient` is the contract agents call. It provides:

- `generate(prompt, responseType)`
- `generate(prompt, responseType, metadata)`
- `generateStream(prompt, metadata)` returning `Flux<String>`

Agents do not call `ChatClient` directly.

### 7.2 Structured output first

The intended pattern is:

1. build an `AgentPrompt`
2. invoke `AgentAiClient`
3. bind to a DTO or structured object
4. run schema/business validation
5. return `AgentResult`

### 7.3 Streaming

Streaming is supported for raw text chunks only. It is intentionally distinct from structured output because providers stream incremental text, not partially valid typed objects.

### 7.4 Cost estimation

`agent-platform.ai.tariffs.<model>` defines input/output cost per thousand tokens. `ModelCostBudgetTracker` keeps a bounded LRU of cumulative execution spend so hard per-run budgets apply across multiple model calls within a single execution.

## 8. Development standards: agents, tools, prompts, outputs, approvals, security

### 8.1 Agent checklist

Every production agent should define:

- stable agent ID and semantic version
- descriptor with honest `riskLevel`
- typed input/output contract
- prompt construction strategy
- allowed tools
- output validation
- failure behavior
- tests
- evaluation dataset coverage

### 8.2 Tool checklist

Every tool should define:

- stable tool name
- description
- input and output contract
- risk level
- side-effect classification
- required permissions
- timeout
- approval requirement

### 8.3 Prompt standards

Prompts are versioned artifacts, not string literals scattered through code. Prompt versions can be seeded from configuration and managed through admin APIs with lifecycle states such as active, deprecated, and archived.

### 8.4 Output-validation standards

Use layered validation:

1. DTO/schema binding
2. business-rule checks
3. identifier existence checks
4. authorization before acting on any identifier

`OutputSchemaConformanceGuardrail` and `HallucinatedIdRejectionGuardrail` exist specifically so agents can codify these checks instead of trusting raw model output.

### 8.5 Human-approval standards

Approval must be explicit for high-risk behavior. Approval is treated as control flow, not as a generic failure, so orchestration and async resume can continue correctly.

### 8.6 Security standards

- never trust model output as authorization input
- never trust workflow headers as identity
- never allow cross-tenant memory retrieval
- never log secrets intentionally
- prefer strict mode in real deployments
- keep prompt and tool-result logging off unless there is a justified operational need

## 9. Testing strategy

The repository validates the framework at several layers:

- unit tests for contracts, policy resolution, guardrails, tools, orchestration, resilience, memory, checkpointing, JWT resolution, MCP bridge, and red-team behavior
- Spring/JPA integration tests
- REST and approval flow tests in `qubere-echo-agent`
- Docker build validation in CI
- full-reactor CI on JDK 21, with JDK 24 as non-blocking early warning

Current testing posture of the two runnable applications differs:

- `qubere-echo-agent` is the primary exercised reference host
- `qubere-document-agent` currently has only a context-load smoke test, so it should be documented as an architectural proof app rather than a fully validated second production sample

## 10. Configuration standard and policy-resolution semantics

The full `agent-platform.*` YAML reference lives in `README.md`. This section explains how those properties are interpreted.

### 10.1 Resolution model

The framework resolves behavior by field, not by blunt object merge. The practical rule is:

- **caller options win only for fields that are intentionally caller-overridable**
- **otherwise per-agent settings win**
- **otherwise global defaults apply**

Important cases:

- `model-provider`, `model-name`, and `prompt-version`: per-agent definition, then global AI default
- `memory-enabled`, `max-memory-results`, `max-tool-calls`, `timeout-seconds`, `max-retries`, and `max-estimated-cost-usd`: per-agent definition, then platform default
- `mode`, `max-steps`, `streaming`, `temperature`, `max-output-tokens`, `allow-tool-calls`, and `require-human-approval`: caller option when provided, otherwise resolved defaults
- `allowed-tools`: caller subset if provided, else per-agent allow-list, else runtime-level allow-list

Today the first-class caller override surface is intentionally small because `AgentRunOptions` is narrow by design. In the current codebase, caller options do **not** directly override per-agent timeout/retry settings, and `response-detail` / `priority` resolve from runtime configuration defaults rather than a first-class request field.

### 10.2 Approval semantics

Approval precedence is intentionally special:

1. explicit per-agent `require-human-approval`
2. otherwise, if `agent-platform.governance.require-approval-for-high-risk=true` and the descriptor risk level is `HIGH` or `CRITICAL`, approval is required
3. otherwise the runtime default applies

This means descriptor risk is operational, not decorative.

### 10.3 Forbidden-override principle

The framework does not allow callers to rewrite platform safety policy arbitrarily. Practical examples:

- callers do not declare their own tenant/actor identity
- callers do not bypass guardrails
- callers do not bypass approval by sending workflow headers
- callers do not escape tool governance

### 10.4 Strict vs permissive identity behavior

`agent-platform.security.trust-inbound-headers` is nullable by design:

- in permissive mode it resolves effectively to `true`
- in strict mode it resolves effectively to `false`

That preserves local convenience while keeping strict mode fail-closed.

## 11. Multi-agent orchestration

This is a first-class framework feature, not an add-on.

### 11.1 Two delegation paths

The framework deliberately supports two ways to delegate:

- `AgentCallTool` for **LLM-chosen** delegation at runtime
- `AgentOrchestrator` for **code-declared** orchestration patterns

Both share:

- `DelegationGuard`
- workflow linkage
- aggregate workflow budgets
- sub-agent runtime governance

They differ only because an LLM-chosen target must pass through tool allow-list and approval checks, while a code-reviewed orchestration plan does not need the tool-layer allow-list re-check.

### 11.2 Patterns

`AgentOrchestrator` supports:

- sequential chains
- parallel fan-out/fan-in
- routing
- bounded supervisor loops

All patterns operate on a shared `OrchestrationState` blackboard so sibling steps can consume prior results explicitly.

### 11.3 Why orchestration uses a separate executor

Parallel fan-out uses `agentOrchestrationExecutor`, separate from `agentInvocationExecutor`. This avoids a classic bounded-pool deadlock: parent orchestration tasks wait on child runs, while child runs themselves need invocation threads.

When no orchestration executor is present, parallel orchestration degrades to sequential execution instead of silently spawning unmanaged threads.

### 11.4 Delegation safety

Cycle detection uses the **full ancestor path**, stored in `AgentWorkflowContext.agentPath`, not only the immediate caller. That is what catches `A -> B -> A`; an immediate-caller check only catches `A -> A`.

`agent-platform.orchestration.max-delegation-depth` defaults to `8` and caps long chains of distinct agents. Depth `0` disables the depth limit, but cycle detection still applies.

### 11.5 Workflow linkage

`AgentWorkflowContext` propagates:

- `workflowId`
- `parentExecutionId`
- `agentDelegationDepth`
- `agentPath`
- optional shared `AgentWorkflowBudget`

`agent_execution_record` persists `workflow_id` and `parent_execution_id`, and `AgentWorkflowService` rolls linked executions into `AgentWorkflowSummary` / `AgentWorkflowStatus`.

### 11.6 Aggregate workflow budgets

`AgentWorkflowBudget` limits:

- agent invocations
- tool calls
- accumulated estimated cost

It can run:

- in-process
- or distributed via `DistributedWorkflowBudgetStore` / `JpaDistributedWorkflowBudgetStore`

The distributed path exists so a cross-service workflow enforces one ceiling in total, not one ceiling per JVM.

### 11.7 Cross-service invocation

`RemoteAgentClient` / `HttpRemoteAgentClient` call another Qubere-based service through the same REST contract and propagate:

- correlation ID
- workflow ID
- parent execution ID

Tenant and actor still come only from the receiving service's authenticated identity resolver.

## 12. Identity and security

### 12.1 Fail-closed identity seam

`AgentCallerIdentityResolver` is the framework seam for trusted caller identity.

Built-in behaviors:

- `TrustedHeaderCallerIdentityResolver` for permissive/local development only
- `NoOpCallerIdentityResolver` for strict mode by default
- `JwtCallerIdentityResolver` as an optional OAuth2/JWT adapter

### 12.2 Strict mode

In strict mode, if the application does not provide a real resolver, `NoOpCallerIdentityResolver` returns unresolved identity and all runs fail closed under the existing tenant/actor requirements. That is intentional.

### 12.3 Optional JWT adapter

`JwtCallerIdentityResolver` activates only when:

1. `agent-platform.security.jwt.enabled=true`
2. a `JwtDecoder` bean exists
3. the optional Spring Security resource-server dependency is present

It:

- validates bearer tokens
- maps configurable tenant/actor/permissions claims
- accepts string or array permission claims
- falls back from a misconfigured actor claim to JWT `sub`

Invalid tokens return unresolved identity rather than throwing so the authorization layer remains the single place that rejects unauthenticated traffic.

### 12.4 Callback signing

When callbacks are enabled, `AgentSecretResolver` / `EnvironmentAgentSecretResolver` support HMAC-signed outbound HTTP callbacks.

## 13. Evaluation and safety testing

### 13.1 Golden datasets

The framework supports:

- classpath/file JSON datasets by default
- database-backed datasets via `JpaGoldenDatasetRepository`
- layered `database-then-classpath` via `CompositeGoldenDatasetRepository`

Database-backed datasets are **opt-in** through `agent-platform.evaluation.dataset-provider`. They are not auto-registered simply because storage is present, because that would silently displace classpath loading in applications that only wanted durable execution storage.

### 13.2 Prompt regression and replay

Prompt versions can be replayed against saved inputs, and evaluation results can be persisted and reviewed via the admin APIs.

### 13.3 Red-team runner

`RedTeamRunner` validates safety behavior such as:

- refusal/blocking
- forbidden tool invocation
- forbidden output content
- required approval

`RedTeamResult.isClean()` is intentionally all-or-nothing: one reproducible safety failure should block release.

## 14. Deployment

### 14.1 Containerization

Both runnable reference apps ship multi-stage Dockerfiles:

- build stage: `maven:3.9.9-eclipse-temurin-21`
- runtime stage: `eclipse-temurin:21-jre-jammy`
- non-root runtime user
- `HEALTHCHECK` on `/actuator/health`

The build context must be the **repository root**, because the app modules depend on sibling reactor modules that are not published externally.

`docker-compose.yml` runs both services together.

### 14.2 CI/CD

`ci.yml` is the main quality gate:

- full build/test gate on JDK 21
- JDK 24 run as non-blocking early warning
- Docker build validation for both reference apps

`docker-publish.yml`:

- reuses `ci.yml`
- publishes to GHCR only after tests pass
- runs a Trivy scan informationally, not as a publish blocker

## 15. Enterprise standards mapping and current implementation status

| Control family | Current framework reality | Status |
| --- | --- | --- |
| Stable agent contracts and versioned registry | `Agent`, `AgentDescriptor`, `AgentRegistry`, default-version resolution, duplicate rejection | Implemented |
| Typed I/O and structured output discipline | `AgentAiClient`, DTO-based responses, output-validation guardrails | Implemented |
| Tool governance and side-effect control | allow-list, permission checks, approval policy, dry-run blocking, resilience, `agent_tool_call` | Implemented |
| Human approval and resumability | durable approvals, resume/reject/expire APIs, checkpoint-based continuation | Implemented |
| Memory isolation | provider-neutral memory plus tenant/namespace-enforced `SpringAiVectorMemoryStore` | Implemented |
| Observability and audit | execution records/logs, model usage, tool calls, pipeline listeners, OTLP exporter | Implemented |
| Rate limits and spend governance | tenant, actor, and per-agent rate limits; per-run and per-workflow budget enforcement | Implemented |
| Multi-agent orchestration | `agent.call`, `AgentOrchestrator`, workflow linkage, distributed workflow budgets, remote invocation | Implemented |
| Security identity seam | permissive resolver, strict fail-closed resolver, optional JWT adapter | Implemented |
| Evaluation and red-team | classpath/database datasets, replay, evaluation store, `RedTeamRunner` | Implemented |
| MCP interoperability | governed MCP tool bridge only; transport owned by host application | Implemented |
| Cloud-native packaging | Dockerfiles, compose, CI reuse, GHCR publish workflow | Implemented |
| A2A interoperability | intentionally deferred in favor of `RemoteAgentClient` until a concrete need exists | Deferred |
| Built-in admin dashboard UI | APIs exist; UI deliberately left to host applications | Deferred |
| Saga/compensation workflow model | checkpointing avoids repeats on resume but does not undo completed writes | Deferred |
| Queue adapters for Kafka/RabbitMQ/SQS | async abstraction exists; database queue is production-viable; extra adapters not bundled | Deferred |
| IdP-specific onboarding and non-JWT auth variants | JWT adapter exists; tenant setup, opaque token introspection, refresh-token flows, mTLS client-cert identity are outside framework scope | Deferred |

This is the only implementation-status matrix in this document and it reflects the repository as it exists today.

## 16. What is deferred and why

These items are deliberately out of framework scope or deferred for cost/value reasons:

- **Kafka/RabbitMQ/SQS adapters**: the async abstraction is already stable and the JPA queue is production-viable; broker-specific adapters are deployment-specific
- **A2A adapter**: cross-service delegation is already covered by `RemoteAgentClient`; A2A remains a moving interoperability target
- **Admin dashboard UI**: the framework exposes authenticated admin APIs, but UI composition belongs to the host application
- **Kubernetes manifests / Helm / cloud-specific deployment targets**: too organization-specific for a generic framework
- **Saga/compensation model**: checkpointing prevents repeated side effects after resume, but rollback semantics are domain-specific and not bundled
- **IdP-specific onboarding**: Okta/Auth0/Entra ID tenant setup, opaque token introspection, refresh-token handling, and mTLS identity remain application concerns

Known limitation worth stating plainly:

- `qubere-document-agent` proves the framework can host a second service and migration workload, but it is not yet a fully tested second reference app; today it has one context-load smoke test

## 17. External references

- NIST AI Risk Management Framework: https://www.nist.gov/itl/ai-risk-management-framework
- NIST AI 600-1 Generative AI Profile: https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence
- ISO/IEC 42001: https://www.iso.org/standard/42001
- OWASP Top 10 for LLM and GenAI Applications: https://genai.owasp.org/llm-top-10/
- OpenTelemetry specification: https://opentelemetry.io/docs/specs/otel/
- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/
- OpenAPI specification: https://spec.openapis.org/oas/
