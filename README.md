# Qubere Agent Framework

Qubere Agent Framework is a Java 21, Spring Boot, and Spring AI multi-module framework for building governed agentic services. It gives applications stable agent contracts, runtime policy resolution, tool governance, async approval flows, orchestration, durable memory, evaluation, and observability, with `qubere-echo-agent` and `qubere-document-agent` included as runnable reference hosts.

## Module structure

- `qubere-agent-api` - stable agent contracts and descriptors
- `qubere-agent-core` - runtime engine, policy resolution, tools, orchestration, memory, evaluation, security, observability
- `qubere-agent-storage` - optional JPA persistence, manual PostgreSQL/Oracle DDL
- `qubere-echo-agent` - primary reference host app on port `8080`
- `qubere-document-agent` - second reference service on port `8081`, currently lightly tested
- `agent-framework.md` - full design and standards document

## Quickstart

### Build and test

Use the pinned JDK in this environment and run the full reactor offline:

```powershell
$env:JAVA_HOME = 'C:\Softwares\Softwares\openlogic-openjdk-21.0.10+7-windows-x64\openlogic-openjdk-21.0.10+7-windows-x64'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -o clean test
```

### Run the reference apps

```powershell
# qubere-echo-agent
mvn -o -pl qubere-echo-agent -am spring-boot:run

# qubere-document-agent
mvn -o -pl qubere-document-agent -am spring-boot:run
```

Then call:

- `http://localhost:8080/actuator/health`
- `http://localhost:8081/actuator/health`

### Build container images

Run from the repository root. The build context must stay at the root because the app modules depend on sibling reactor modules.

```powershell
docker build -f qubere-echo-agent/Dockerfile -t qubere-echo-agent:local .
docker build -f qubere-document-agent/Dockerfile -t qubere-document-agent:local .
docker compose up --build
```

## Current capabilities

- Stable `Agent<I,O>` contract and version-aware registry
- Policy resolution with per-agent and per-run controls
- Fail-closed caller identity seam with optional JWT resolver
- Default and composable guardrails
- Governed tool execution with durable `agent_tool_call` audit trail
- `agent.call` for governed agent-as-tool delegation
- `AgentOrchestrator` for sequential, parallel, routing, and supervisor workflows
- Workflow linkage, aggregate workflow budgets, and remote cross-service invocation
- Per-tenant, per-actor, and per-agent rate limiting
- Per-run and per-workflow cost-budget enforcement
- Resilience4j-backed optional circuit breaking and bulkheads
- Spring AI integration with structured output and streaming text support
- Durable vector-store-backed memory through any Spring AI `VectorStore`
- Checkpoint-based resumability across approval pauses
- Async execution with memory or database queues
- Prompt versioning and seed prompts
- Golden-dataset evaluation, replay, and red-team safety testing
- MCP tool bridge for governed external tool access
- Multi-stage Docker images and GitHub Actions CI/CD

## Configuration reference

The block below reflects the full `agent-platform.*` surface supported by `AgentPlatformProperties.java`. Comments call out places where the reference apps intentionally override framework defaults in their own `application.yml`.

```yaml
agent-platform:
  runtime:
    default-mode: recommend
    dry-run: false
    async: false
    streaming: false
    max-steps: 8
    temperature: 0.2
    max-output-tokens: 2048
    allow-tool-calls: true
    require-human-approval: false
    include-evidence: true
    include-recommendations: true
    timeout-seconds: 120
    max-retries: 2
    log-prompts: false
    log-tool-results: false
    response-detail: SUMMARY
    priority: NORMAL
    allowed-tools: []
    executor:
      core-pool-size: 8
      max-pool-size: 32
      queue-capacity: 200
      thread-name-prefix: agent-invoke-
      await-termination-seconds: 30

  registry:
    strict-descriptor-validation: true
    default-versions: {}

  ai:
    default-provider: openai
    default-model: default # reference apps override this to gpt-4.1-mini
    max-estimated-cost-usd: 0
    tariffs: # example keyed by model name
      "[gpt-4.1-mini]":
        input-cost-usd-per-thousand-tokens: 0.00015
        output-cost-usd-per-thousand-tokens: 0.0006

  memory:
    enabled: true
    provider: in-memory
    max-results: 5

  tools:
    enabled: true
    audit-enabled: true
    approval-required-for-destructive: true
    max-tool-calls: 20

  prompts:
    provider: in-memory # reference apps set this to database
    seed-enabled: true
    seeds: # example seed entry
      - prompt-id: example.agent.default
        agent-id: example.agent
        version: 0.1.0
        status: DRAFT
        system-template: You are the example agent.
        user-template: Process the supplied input.
        metadata: {}
        overwrite: false

  async:
    enabled: true
    worker-enabled: false
    poll-interval-millis: 1000
    max-runs-per-poll: 1
    approval-expiration-minutes: 60
    queue:
      type: memory
      max-healthy-depth: 0
    callback:
      enabled: false
      max-attempts: 3
      retry-backoff-millis: 500
      timeout-seconds: 5
      signing-secret: ""

  governance:
    enabled: true
    max-runs-per-tenant-per-minute: 0
    max-runs-per-actor-per-minute: 0
    max-estimated-cost-usd-per-run: 0
    estimated-cost-usd-per-thousand-tokens: 0
    require-approval-for-high-risk: true

  security:
    authorization-mode: permissive
    require-tenant: true
    require-actor: true
    allowed-tenants: []
    required-run-permissions: []
    agent-required-permissions: {}
    trust-inbound-headers: null # derived from authorization-mode when omitted
    jwt:
      enabled: false
      tenant-claim: tenant_id
      actor-claim: sub
      permissions-claim: scope

  observability:
    open-telemetry:
      enabled: false
      service-name: qubere-agents
      include-tenant: true
      include-actor: false
      max-buffered-events: 1000
      otlp:
        enabled: false
        endpoint: http://localhost:4317
        protocol: grpc
        timeout-seconds: 10

  admin:
    enabled: false
    token: ""

  evaluation:
    dataset-locations:
      - classpath*:agent-evaluation/*.json
    fail-on-invalid-dataset: true
    dataset-provider: classpath # classpath | database | database-then-classpath

  guardrails:
    enabled: true
    max-input-size-bytes: 200000
    denylist-patterns:
      - "(?i)ignore (all|any|the)?\\s*previous instructions"
      - "(?i)disregard (all|any|the)?\\s*(system|prior) prompt"
      - "(?i)reveal (your|the) system prompt"
      - "(?i)you are now (in )?dan mode"
      - "(?i)act as if (you have|there are) no restrictions"

  resilience:
    enabled: false
    failure-rate-threshold: 50.0
    sliding-window-size: 10
    wait-duration-in-open-state-seconds: 30
    permitted-number-of-calls-in-half-open-state: 3
    bulkhead-max-concurrent-calls: 10
    bulkhead-max-wait-duration-millis: 0

  orchestration:
    agent-call-tool-enabled: false
    max-agent-invocations-per-workflow: 25
    max-tool-calls-per-workflow: 100
    max-estimated-cost-usd-per-workflow: 0
    max-delegation-depth: 8
    remote:
      enabled: false
      base-url: ""
      timeout-seconds: 60

  mcp:
    enabled: false
    exposed-tools: []

  definitions: # example per-agent entry
    "[example.agent]":
      enabled: true
      model-provider: openai
      model-name: gpt-4.1-mini
      prompt-version: latest
      memory-enabled: true
      max-memory-results: 3
      max-tool-calls: 4
      timeout-seconds: 30
      max-retries: 1
      max-estimated-cost-usd: 0
      require-human-approval: false
      allowed-tools: []
      max-runs-per-minute: 0
```

Reference-app specifics worth knowing:

- `qubere-echo-agent` seeds `generic.echo-analysis`, `generic.tool-echo-analysis`, and `generic.ai-analysis`
- `qubere-document-agent` seeds `document.intake` and `document.intelligence`
- both apps default `spring.profiles.active` to `AGENT_DB=local`
- both apps expose Actuator health/info/metrics

## Multi-agent orchestration usage

### LLM-driven delegation through `agent.call`

Use the governed tool path when the model chooses the sub-agent:

```java
ToolResult result = toolExecutionService.execute(new ToolExecutionRequest(
        AgentCallTool.TOOL_NAME,
        context,
        policy,
        Map.of(
                AgentCallTool.ARG_AGENT_ID, "invoice.review",
                AgentCallTool.ARG_INPUT, Map.of("invoiceId", "inv-1")
        )));
```

This path inherits:

- tool allow-list enforcement
- approval policy
- dry-run safety
- `agent_tool_call` recording
- workflow linkage
- delegation-cycle protection

### Code-declared orchestration through `AgentOrchestrator`

```java
OrchestrationState state = OrchestrationState.withInput(Map.of("invoiceId", "inv-1"));

OrchestrationOutcome sequential = orchestrator.sequential(
        rootContext,
        state,
        FailurePolicy.FAIL_FAST,
        List.of(
                OrchestrationStep.of("extract", "invoice.extract"),
                OrchestrationStep.of("review", "invoice.review",
                        s -> Map.of("extracted", s.result("extract").orElse(Map.of())))));

OrchestrationOutcome fanOut = orchestrator.parallel(
        rootContext,
        state,
        FailurePolicy.CONTINUE,
        List.of(
                OrchestrationStep.of("credit", "credit.check"),
                OrchestrationStep.of("fraud", "fraud.check"),
                OrchestrationStep.of("sanctions", "sanctions.check")));
```

Important behavior:

- `parallel` uses a dedicated orchestration executor
- cycles are checked against the full ancestor path, not only the immediate caller
- `ToolApprovalRequiredException` propagates as control flow
- workflow budgets cap total delegation across the whole tree

## Durable memory (RAG) usage

Add any Spring AI vector-store starter, for example pgvector:

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

Once a `VectorStore` bean exists, `SpringAiVectorMemoryStore` is auto-configured. It stamps stored documents with `tenantId` and `namespace` metadata and filters every search on both. Missing tenant context is rejected rather than falling back to a global search.

## Resumable/checkpointed agents usage

Use `AgentCheckpointScope` inside multi-step agents that may pause for approval:

```java
AgentCheckpointScope checkpoints = AgentCheckpointScope.from(context);

String reservation = checkpoints.step("reserve-stock", String.class,
        () -> inventory.reserve(input));

ToolResult shipped = checkpoints.step("ship-order", ToolResult.class,
        () -> toolExecutionService.execute(shipRequest));
```

Design rules that matter:

- this is step memoization, not stack capture
- step names must be stable and deterministic
- results must be JSON-serializable
- completed side effects are not repeated after resume

## Security

### Strict vs permissive mode

- `authorization-mode: permissive` is the local-development default
- `authorization-mode: strict` is the real deployment default in spirit
- strict mode uses `NoOpCallerIdentityResolver` unless the app supplies a trusted resolver, so it fails closed

### Permissive header trust

`TrustedHeaderCallerIdentityResolver` trusts `X-Tenant-Id`, `X-Actor-Id`, and `X-Agent-Permissions` only for permissive/local scenarios. Do not use it in production.

### OAuth2/JWT setup

Add the optional dependency:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Enable the resolver:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-idp.example.com/

agent-platform:
  security:
    jwt:
      enabled: true
      tenant-claim: tenant_id
      actor-claim: sub
      permissions-claim: scope
```

`JwtCallerIdentityResolver` only activates when both `jwt.enabled=true` and a `JwtDecoder` bean are present. Invalid tokens resolve to unresolved identity; the fail-closed authorization check then rejects the request.

## Container images and CI/CD

Both runnable apps ship multi-stage Dockerfiles:

- build from source with `maven:3.9.9-eclipse-temurin-21`
- run on `eclipse-temurin:21-jre-jammy`
- non-root user
- `HEALTHCHECK` on `/actuator/health`

GitHub Actions:

- `.github/workflows/ci.yml` - full build/test gate on JDK 21, JDK 24 early warning, Docker build validation
- `.github/workflows/docker-publish.yml` - reuses `ci.yml`, pushes to GHCR only after tests pass, runs informational Trivy scan

## End-to-end scenarios

For full run, async approval, admin, replay, and database scenarios, see:

```text
qubere-echo-agent/TESTING.md
```

## Enterprise standards alignment

The framework is aligned with NIST AI RMF, ISO/IEC 42001, OWASP LLM guidance, OpenTelemetry-style observability, and release-governance controls. See `agent-framework.md` for the full architecture, control mapping, and deferred-scope rationale.
