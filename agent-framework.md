# Generic Agent Framework

## Java Spring Boot + Spring AI Technical Design and Agentic AI Standards

## 1. Purpose

This document defines a generic, reusable, production-grade framework for building Agentic AI agents using Java, Spring Boot, and Spring AI.

The framework is intentionally domain-neutral. It must support agents for any business domain, such as compliance, logistics, finance, customs, customer support, document intelligence, product intelligence, operations, analytics, engineering automation, or future domains that do not exist yet.

The framework should provide the common operating system for agents:

- agent contracts
- execution runtime
- tool calling
- memory and retrieval
- prompt/version management
- structured outputs
- guardrails
- observability
- persistence
- human approval workflows
- security and authorization
- evaluation and testing standards
- asynchronous execution support

Individual agents should focus on domain logic. They should not reinvent execution lifecycle, retries, logging, audit, tool safety, output validation, or AI provider integration.

---

## 2. Design Philosophy

Spring AI should be treated as the AI integration engine.

The generic agent framework should be treated as the application-level agent operating system.

```txt
Spring AI
  â†’ model clients
  â†’ ChatClient
  â†’ tool calling
  â†’ advisors
  â†’ memory abstractions
  â†’ vector store integrations

Generic Agent Framework
  â†’ agent contracts
  â†’ runtime lifecycle
  â†’ tool governance
  â†’ security
  â†’ tenant/account/project context
  â†’ persistence
  â†’ auditability
  â†’ evidence
  â†’ approval workflows
  â†’ evaluation
  â†’ domain extension points
```

The framework must not be tightly coupled to any one business domain. Any domain-specific logic belongs in domain modules or individual agent modules.

---

## 3. Agentic AI Standards

There is no single universal industry standard that fully defines Agentic AI systems end to end. However, mature agent systems consistently follow several emerging standards and best practices.

This framework should follow these standards by design.

### 3.1 Clear Agent Contract

Every agent must have a stable contract:

- unique agent ID
- semantic version
- domain/category
- declared capabilities
- typed input
- typed output
- allowed tools
- permissions/scopes
- timeout policy
- retry policy
- risk classification
- human approval requirements

No production agent should exist as an ad hoc service method without metadata.

### 3.2 Typed Input and Output

Agents must use explicit input and output types.

Model output must be parsed into structured DTOs and validated before being persisted, shown to users, or used by other systems.

Do not directly trust raw LLM text for business decisions.

### 3.3 Tool Calling Governance

Tools must be explicitly registered and governed.

Each tool must define:

- tool name
- description
- input schema
- output schema or result contract
- side-effect classification
- authorization requirements
- context requirements
- audit policy
- approval requirement

Tool categories:

```txt
READ_ONLY
CONTEXT_READ
TENANT_READ
TENANT_WRITE
EXTERNAL_CALL
HIGH_RISK_ACTION
DESTRUCTIVE_ACTION
```

Examples:

```txt
SearchKnowledgeBase       â†’ READ_ONLY
LoadCustomerProfile       â†’ TENANT_READ
UpdateCaseStatus          â†’ TENANT_WRITE
SendEmail                 â†’ EXTERNAL_CALL
SubmitRegulatoryFiling    â†’ HIGH_RISK_ACTION
DeleteRecord              â†’ DESTRUCTIVE_ACTION
```

### 3.4 Guardrails

Guardrails must exist at multiple layers:

```txt
Input guardrails
  â†’ validate user input
  â†’ validate request context
  â†’ reject unsupported actions

Tool guardrails
  â†’ validate tool input
  â†’ enforce authorization
  â†’ require account/project/tenant context
  â†’ block unsafe side effects

Output guardrails
  â†’ validate structured output
  â†’ reject hallucinated identifiers
  â†’ enforce business constraints
  â†’ classify risk

Execution guardrails
  â†’ timeout
  â†’ retry limit
  â†’ max tool calls
  â†’ max cost
  â†’ human approval interruption
```

### 3.5 Human-in-the-Loop

High-risk actions must support human approval.

Examples of actions requiring approval:

- external submission
- external communication
- payment or billing action
- compliance exception waiver
- destructive database mutation
- regulatory filing
- irreversible status change
- identity/permission change
- cross-tenant/platform-admin action

The framework must be able to pause an execution and resume after approval.

### 3.6 Observability and Tracing

Every agent execution must be traceable.

Minimum trace data:

- execution ID
- correlation ID
- agent ID
- agent version
- user ID
- context ID such as tenant/account/project/workspace
- model provider
- model name
- prompt version
- tool calls
- tool results summary
- latency
- token usage
- estimated cost
- final status
- error code
- error message
- evidence references
- decision references

### 3.7 Explicit Memory

Memory must be explicit and scoped.

The framework should distinguish:

```txt
Chat history
  Complete conversation transcript.

Chat memory
  Selected context included in the model prompt.

Long-term memory
  Persisted facts, summaries, embeddings, prior decisions, or domain records.

Retrieval context
  Data retrieved for a specific run from vector search, lexical search, database, or external tools.
```

Memory must be scoped by security boundary:

- tenant
- account
- workspace
- project
- user
- domain
- entity type
- entity ID

### 3.8 Security and Privacy

Agents must not bypass application security.

Requirements:

- authenticated caller
- authorized agent invocation
- scoped context
- tool-level authorization
- no unscoped database access
- no raw secret logging
- no sensitive prompt logging by default
- no cross-tenant memory retrieval
- no accidental data exposure through tool outputs

### 3.9 Evaluation and Testability

Every agent must be testable.

Minimum testing requirements:

- unit tests for prompt factories
- unit tests for rules and validators
- unit tests for tools
- integration tests for runtime execution
- contract tests for API request/response
- golden test cases for model outputs where possible
- regression tests for previously found failures
- security tests for cross-context access

### 3.10 Risk Management Alignment

The framework should align with enterprise AI risk principles such as:

- valid and reliable
- safe
- secure and resilient
- accountable and transparent
- explainable and interpretable
- privacy-enhanced
- fair and harmful-bias managed

These concepts align with NIST AI Risk Management Framework guidance.

References:

- NIST AI RMF: https://www.nist.gov/itl/ai-risk-management-framework
- NIST Generative AI Profile: https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence
- Spring AI Tool Calling: https://docs.spring.io/spring-ai/reference/api/tools.html
- Spring AI Chat Memory: https://docs.spring.io/spring-ai/reference/api/chat-memory.html
- OpenAI Agents SDK Tracing: https://openai.github.io/openai-agents-python/tracing/
- OpenAI Agents SDK Guardrails: https://openai.github.io/openai-agents-python/guardrails/

---

## 4. High-Level Architecture

```txt
Client Application / API Consumer
        |
        | REST / Event / Queue
        v
Agent Application
        |
        +--> Agent Runtime
        +--> Agent Registry
        +--> Authorization Service
        +--> Context Builders
        +--> Spring AI Adapter
        +--> Tool Registry
        +--> Memory / RAG Service
        +--> Guardrail Service
        +--> Observability Service
        +--> Persistence Layer
        |
        v
Database / Vector Store / Object Store / External APIs
```

The framework should support three execution modes:

```txt
Synchronous
  Request waits for result.

Asynchronous
  Request creates queued execution and returns execution ID.

Interactive / Human Approval
  Execution pauses for human approval and resumes later.
```

---

## 5. Maven Multi-Module Project Structure

Current compact structure:

```txt
qubere-agents/
|-- pom.xml
|-- README.md
|-- docs/
|-- agent-api/
|-- agent-core/
|-- agent-storage/
`-- agent-app/
```

This compact split is intentional for the current framework stage:

- `agent-api` keeps stable public contracts small and Spring-free.
- `agent-core` contains framework behavior: runtime, policy, AI adapter, tools, memory, prompts, async, evaluation, replay, governance, and observability abstractions.
- `agent-storage` contains optional durable JPA persistence and manual DDL scripts.
- `agent-app` is a runnable reference host with REST APIs and sample agents.

Future domain or real-agent modules can be added later when the platform has multiple production agents and the extra boundaries start paying for themselves:

```txt
qubere-agents/
`-- agents/
    |-- invoice-review-agent/
    |-- contract-analysis-agent/
    `-- shipment-planning-agent/
```

Domain-specific deployments can also add domain modules when shared domain models become stable:

```txt
domain-healthcare/
domain-finance/
domain-logistics/
domain-compliance/
domain-customer-support/
domain-customs/
domain-tms/
```

---

## 6. Module Responsibilities

## 6.1 `agent-api`

Pure interfaces and contracts. Avoid Spring dependencies.

Responsibilities:

- base agent interface
- agent input/output marker interfaces
- execution context
- agent descriptors
- capabilities
- domains
- risk classification

```java
public interface AgentInput {
}
```

```java
public interface AgentOutput {
}
```

```java
public interface Agent<I extends AgentInput, O extends AgentOutput> {

    AgentDescriptor descriptor();

    O run(I input, AgentExecutionContext context);
}
```

```java
public record AgentDescriptor(
    String id,
    String name,
    String version,
    String description,
    AgentRiskLevel riskLevel,
    Set<String> capabilities
) {
}
```

```java
public enum AgentRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

```java
public record AgentExecutionContext(
    String executionId,
    String tenantId,
    String actorId,
    String correlationId,
    Instant requestedAt,
    Map<String, Object> attributes
) {
}
```

## 6.2 `agent-core`

Framework primitives and shared utilities.

Responsibilities:

- result model
- status enums
- decision model
- evidence model
- validation utilities
- standard exceptions
- retry and timeout policy models

```java
public enum AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
```

```java
public record AgentResult<T>(
    T value,
    AgentDecisionDraft decision,
    List<AgentEvidenceDraft> evidence,
    Map<String, Object> metadata
) implements AgentOutput {
}
```

```java
public record AgentDecisionDraft(
    String decisionType,
    String status,
    String summary,
    String rationale,
    double confidence,
    Map<String, Object> metadata
) {
}
```

```java
public record AgentEvidenceDraft(
    String evidenceType,
    String source,
    String summary,
    Map<String, Object> payload
) {
}
```

```java
public enum AgentErrorCode {
    NOT_FOUND,
    AGENT_NOT_FOUND,
    AGENT_DISABLED,
    AUTHORIZATION_DENIED,
    GUARDRAIL_BLOCKED,
    VALIDATION_FAILED,
    EXECUTION_FAILED,
    AI_PROVIDER_UNAVAILABLE,
    AI_PROVIDER_FAILURE,
    GOVERNANCE_LIMIT_EXCEEDED,
    TOOL_NOT_FOUND,
    TOOL_NOT_ALLOWED,
    TOOL_APPROVAL_REQUIRED,
    TOOL_FAILED,
    TIMEOUT
}
```

```java
public class AgentExecutionException extends RuntimeException {

    private final AgentErrorCode errorCode;

    public AgentExecutionException(
        AgentErrorCode errorCode,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AgentErrorCode getErrorCode() {
        return errorCode;
    }
}
```

## 6.3 `agent-core` runtime package

Central execution engine.

Responsibilities:

- agent discovery
- registry lookup
- execution lifecycle
- authorization integration
- guardrail orchestration
- timeout/retry handling
- execution persistence
- error normalization
- event publishing

```java
@Component
public class AgentRegistry {

    private final Map<String, Agent<?, ?>> agents;

    public AgentRegistry(List<Agent<?, ?>> registeredAgents) {
        this.agents = registeredAgents.stream()
            .collect(Collectors.toUnmodifiableMap(
                agent -> agent.descriptor().id(),
                Function.identity()
            ));
    }

    public List<AgentDescriptor> listAgents() {
        return agents.values().stream()
            .map(Agent::descriptor)
            .toList();
    }

    @SuppressWarnings("unchecked")
    public <I extends AgentInput, O extends AgentOutput> Agent<I, O> get(String agentId) {
        Agent<?, ?> agent = agents.get(agentId);
        if (agent == null) {
            throw new AgentExecutionException(
                AgentErrorCode.AGENT_NOT_FOUND,
                "Agent not found: " + agentId,
                null
            );
        }
        return (Agent<I, O>) agent;
    }
}
```

```java
@Service
public class AgentRuntimeService {

    public AgentOutput run(
        String agentId,
        String agentVersion,
        AgentInput input,
        AgentExecutionContext context,
        AgentRunOptions options
    ) {
        RegisteredAgent registeredAgent = findRegisteredAgent(agentId, agentVersion)
            .orElseThrow(() -> new AgentExecutionException(
                AgentErrorCode.AGENT_NOT_FOUND,
                "Agent not found"
            ));

        Agent<?, ?> agent = registeredAgent.agent();
        ResolvedAgentPolicy policy = policyResolver.resolve(agentId, options);
        AgentExecutionContext executionContext = withResolvedPolicy(context, policy, agent.descriptor());

        authorizationService.canRun(executionContext, agent.descriptor());
        governanceServices.forEach(governance -> governance.beforeRun(executionContext, agent.descriptor(), policy));
        guardrailService.evaluateBeforeRun(executionContext, agent.descriptor(), input);

        executionStore.markStarted(executionContext, agent.descriptor(), input);

        try {
            AgentOutput output = invokeWithRetry(agent, input, executionContext, policy);
            executionStore.markCompleted(executionContext.executionId(), output);
            governanceServices.forEach(governance -> governance.afterRun(executionContext, agent.descriptor(), output));
            return output;
        } catch (RuntimeException ex) {
            executionStore.markFailed(executionContext.executionId(), ex);
            throw ex;
        }
    }
}
```

## 6.4 `agent-core` AI package

Spring AI integration layer.

Responsibilities:

- wrap `ChatClient`
- support structured output
- support text generation through structured DTOs or `String`
- support tool calling and advisors in later provider-specific adapters
- support model/provider configuration
- record model usage attempts
- hide raw Spring AI details from agents

Agents should not directly instantiate or call `ChatClient`. They should call `AgentAiClient` and pass run metadata when they want model usage persisted against an execution.

```java
public interface AgentAiClient {

    <T> T generate(AgentPrompt prompt, Class<T> responseType);

    default <T> T generate(
        AgentPrompt prompt,
        Class<T> responseType,
        AgentAiRequestMetadata metadata
    ) {
        return generate(prompt, responseType);
    }
}
```

```java
public record AgentPrompt(
    String system,
    String user,
    Map<String, Object> variables
) {
}
```

```java
public record AgentAiRequestMetadata(
    String executionId,
    String tenantId,
    String actorId,
    String correlationId,
    String agentId,
    String agentVersion,
    String modelProvider,
    String modelName,
    String promptVersion,
    Map<String, Object> metadata
) {
    public static AgentAiRequestMetadata from(AgentExecutionContext context) {
        // Extract execution context, agent attributes, and resolved policy.
    }
}
```

```java
public interface ModelUsageRecorder {

    void record(ModelUsageRecord record);

    static ModelUsageRecorder noop() {
        return record -> {
        };
    }
}
```

`SpringAiAgentClient` records one `STARTED` usage row before calling Spring AI and updates the same `usageId` to `SUCCEEDED` or `FAILED` after the call. Token and cost fields are nullable until provider-specific usage metadata extraction is added.
## 6.5 `agent-core` tools package

Tool framework.

Responsibilities:

- register tools
- expose Spring AI `@Tool` methods or `ToolCallback` beans
- enforce tool context
- validate tool inputs
- log tool calls
- classify side effects
- require approval for high-risk tools

```java
public record ToolDescriptor(
    String name,
    String description,
    ToolRiskLevel riskLevel,
    Set<ToolSideEffect> sideEffects,
    Set<String> requiredPermissions,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    Duration timeout,
    boolean humanApprovalRequired
) {
}
```

```java
public enum ToolRiskLevel {
    READ_ONLY,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    DESTRUCTIVE
}
```

```java
public enum ToolSideEffect {
    NONE,
    READ_EXTERNAL,
    WRITE_INTERNAL,
    CALL_EXTERNAL,
    WRITE_EXTERNAL,
    DESTRUCTIVE
}
```

Example tool:

```java
@Component
public class RecordLookupTools {

    private final RecordLookupService recordLookupService;

    @Tool(description = "Look up a business record by ID inside the current tenant context.")
    public RecordSummary lookupRecord(
        String tenantId,
        String recordType,
        String recordId
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new AgentExecutionException(
                AgentErrorCode.VALIDATION_FAILED,
                "tenantId is required for record lookup",
                null
            );
        }
        return recordLookupService.lookup(tenantId, recordType, recordId);
    }
}
```

## 6.6 `agent-core` memory package

Memory and retrieval abstraction.

Responsibilities:

- vector retrieval
- lexical retrieval
- prior decision retrieval
- document retrieval
- scoped chat memory
- long-term memory
- Spring AI vector store integration

```java
public interface AgentMemoryService {

    List<MemoryHit> retrieve(AgentMemoryQuery query);
}
```

```java
public record AgentMemoryQuery(
    String tenantId,
    String namespace,
    String query,
    int limit,
    Map<String, Object> filters
) {
}
```

```java
public record MemoryHit(
    String id,
    String source,
    String content,
    double score,
    Map<String, Object> metadata
) {
}
```

## 6.7 `agent-core` observability package

Tracing, metrics, audit, and usage tracking.

Responsibilities:

- execution traces
- spans
- tool call logs
- model usage
- token/cost accounting
- error classification
- structured logging
- metrics export

Trace model:

```txt
AgentRunTrace
  â”œâ”€â”€ AgentExecutionSpan
  â”œâ”€â”€ ContextBuildSpan
  â”œâ”€â”€ MemoryRetrievalSpan
  â”œâ”€â”€ ModelCallSpan
  â”œâ”€â”€ ToolCallSpan
  â”œâ”€â”€ GuardrailSpan
  â””â”€â”€ PersistenceSpan
```

## 6.8 `agent-storage`

Persistence entities, repositories, JPA-backed stores/listeners, and manual DDL.

Currently implemented tables:

```txt
agent_execution_record
agent_execution_log
agent_tool_call
agent_tool_audit_event
agent_prompt_template
agent_approval_request
agent_pending_command
agent_async_queue_command
agent_model_usage
agent_evaluation_result
```

Potential future tables, only when the framework needs independent analytics/querying for them:

```txt
agent_decision
agent_evidence
agent_evaluation_dataset
```

## 6.9 `agent-core` security package

Security and authorization.

Responsibilities:

- authenticate service/API callers
- authorize agent execution
- authorize tool execution
- enforce context boundaries
- support tenant/workspace/project scopes
- support human approval policy decisions

```java
public interface AgentAuthorizationService {

    void authorizeRun(
        AgentDescriptor descriptor,
        AgentExecutionContext context
    );

    void authorizeTool(
        ToolDescriptor descriptor,
        AgentExecutionContext context
    );
}
```

## 6.10 `agent-core` evaluation package

Evaluation framework.

Responsibilities:

- golden datasets
- deterministic fixture tests
- LLM-as-judge evaluations when appropriate
- regression tests
- prompt version evaluation
- tool-call correctness evaluation
- output schema validation
- safety evaluation

Evaluation dimensions:

```txt
accuracy
completeness
faithfulness
tool correctness
policy compliance
latency
cost
security
stability
human approval correctness
```

---

## 7. Execution Lifecycle Standard

Every agent execution must follow this lifecycle:

```txt
1. Receive request
2. Authenticate caller
3. Build AgentExecutionContext
4. Resolve agent from registry
5. Authorize run
6. Validate input
7. Create AgentExecutionRecord
8. Build domain/context data
9. Retrieve memory or reference data
10. Prepare prompt
11. Run rules, model calls, and tools
12. Apply output guardrails
13. Validate structured output
14. Persist decisions and evidence
15. Persist execution result
16. Emit audit/trace/metrics
17. Return result or execution status
```

For async executions:

```txt
1. Receive request
2. Validate and authorize
3. Create QUEUED execution
4. Publish job
5. Worker executes lifecycle
6. Client polls or receives callback/event
```

For human approval:

```txt
1. Agent requests high-risk tool/action
2. Runtime creates approval request
3. Execution moves to WAITING_FOR_APPROVAL
4. Human approves/rejects
5. Runtime resumes or cancels execution
```

---

## 8. REST API Standard

## 8.1 List Agents

```http
GET /api/agents
```

Response:

```json
[
  {
    "id": "example-analysis-agent",
    "name": "Example Analysis Agent",
    "version": "1.0.0",
    "domain": "example",
    "capabilities": ["record.analyze"],
    "riskLevel": "LOW"
  }
]
```

## 8.2 Run Agent

```http
POST /api/agents/{agentId}/runs
```

Request:

```json
{
  "tenantId": "tenant_123",
    "userId": "user_123",
  "input": {
    "recordId": "record_123"
  }
}
```

Response:

```json
{
  "executionId": "agent_exec_123",
  "agentId": "example-analysis-agent",
  "status": "SUCCEEDED",
  "result": {
    "summary": "Analysis completed",
    "confidence": 0.91
  }
}
```

## 8.3 Get Execution

```http
GET /api/agents/runs/{executionId}
```

Response:

```json
{
  "executionId": "agent_exec_123",
  "agentId": "example-analysis-agent",
  "status": "SUCCEEDED",
  "startedAt": "2026-08-24T10:00:00Z",
  "completedAt": "2026-08-24T10:00:05Z"
}
```

## 8.4 Approve or Reject Action

```http
POST /api/agents/approvals/{approvalId}/decision
```

Request:

```json
{
  "decision": "APPROVED",
  "reason": "Reviewed and approved by supervisor"
}
```

---

## 9. Database Design

## 9.1 `agent_execution_record`

```sql
CREATE TABLE agent_execution_record (
    execution_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(128) NOT NULL,
    agent_version VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128),
    actor_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

`agent_execution_record` is intentionally limited to the run lifecycle summary. `idempotency_key` is used to make async submission retry-safe per tenant. Model provider, model name, prompt version, token counts, and cost estimates belong to `agent_model_usage`, because one agent run may perform zero, one, or many model calls.

## 9.2 `agent_execution_log`

```sql
CREATE TABLE agent_execution_log (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    agent_version VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128),
    actor_id VARCHAR(128),
    correlation_id VARCHAR(128),
    step VARCHAR(64) NOT NULL,
    log_level VARCHAR(16) NOT NULL,
    message VARCHAR(1000),
    attributes_json TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

## 9.3 `agent_tool_call`

```sql
CREATE TABLE agent_tool_call (
    call_id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128),
    actor_id VARCHAR(128),
    correlation_id VARCHAR(128),
    tool_name VARCHAR(128) NOT NULL,
    tool_risk_level VARCHAR(32) NOT NULL,
    side_effects VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    input_json TEXT,
    output_summary VARCHAR(1000),
    error_message TEXT,
    approval_id VARCHAR(64),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    latency_ms BIGINT
);
```

## 9.4 `agent_approval_request`

```sql
CREATE TABLE agent_approval_request (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    requested_action VARCHAR(128) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    request_payload JSONB,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(64),
    decided_by VARCHAR(64),
    decision_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP
);
```

## 9.5 `agent_model_usage`

```sql
CREATE TABLE agent_model_usage (
    usage_id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64),
    tenant_id VARCHAR(128),
    actor_id VARCHAR(128),
    correlation_id VARCHAR(128),
    agent_id VARCHAR(128),
    agent_version VARCHAR(64),
    model_provider VARCHAR(64),
    model_name VARCHAR(128),
    prompt_version VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    input_tokens BIGINT,
    output_tokens BIGINT,
    total_tokens BIGINT,
    estimated_cost_usd NUMERIC(19, 6),
    latency_ms BIGINT,
    error_message TEXT,
    metadata_json TEXT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
```
---

## 10. Spring AI Standards

This section describes the target framework standard. Section 22 tracks what is already implemented in the current codebase versus what is still roadmap. When there is a difference, section 22 is the source of truth for current implementation status.

Use Spring AI for:

- `ChatClient`
- tool calling
- advisors
- memory
- vector store integration
- structured outputs
- model provider abstraction

Framework rules:

```txt
1. Agents must not directly construct ChatClient.
2. Agents should call AgentAiClient.
3. Tool execution must be observable.
4. Tool calls must be authorized.
5. Prompt version must be recorded.
6. Structured output must be validated.
7. Memory must use scoped conversation/entity IDs.
```

Recommended dependency management:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>${spring-ai.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Example dependencies:

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>

<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

---

## 11. Agent Development Checklist

Every new agent must provide:

```txt
[ ] Agent ID
[ ] Agent version
[ ] Descriptor
[ ] Capabilities
[ ] Risk level
[ ] Typed input
[ ] Typed output
[ ] Prompt factory
[ ] Output validator
[ ] Allowed tools
[ ] Required permissions
[ ] Timeout policy
[ ] Retry policy
[ ] Human approval policy
[ ] Unit tests
[ ] Integration tests
[ ] Evaluation cases
[ ] Observability mapping
[ ] Failure mode documentation
```

---

## 12. Tool Development Checklist

Every new tool must provide:

```txt
[ ] Tool name
[ ] Description
[ ] Input type/schema
[ ] Output type/schema
[ ] Required context
[ ] Required permissions
[ ] Side-effect classification
[ ] Risk level
[ ] Approval requirement
[ ] Input validation
[ ] Output summarization
[ ] Audit logging
[ ] Unit tests
[ ] Security tests
```

---

## 13. Prompt Standards

Prompts must be treated as versioned artifacts.

Each prompt must define:

- prompt ID
- prompt version
- agent ID
- model family compatibility
- system message
- user message template
- required variables
- output schema
- safety instructions
- evaluation dataset

Prompt logs:

```txt
Development
  Prompt logging may be enabled with redaction.

Production
  Prompt logging disabled by default.
  Store prompt version and variable summary.
  Do not store raw sensitive documents unless explicitly approved.
```

---

## 14. Output Validation Standards

Every structured AI output must be validated.

Validation layers:

```txt
1. JSON parse validation
2. DTO binding validation
3. Bean Validation annotations
4. Business rule validation
5. Identifier existence validation
6. Authorization validation before action
```

Example:

```java
public record AnalysisOutput(
    @NotBlank String summary,
    @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
    @NotNull List<String> recommendations
) {
}
```

---

## 15. Human Approval Standards

The framework must support approval interruptions.

High-risk actions must not execute automatically unless policy explicitly permits it.

Approval states:

```txt
PENDING
APPROVED
REJECTED
CANCELLED
EXPIRED
```

Execution state transition:

```txt
RUNNING
  â†’ WAITING_FOR_APPROVAL
  â†’ RUNNING
  â†’ SUCCESS
```

or:

```txt
RUNNING
  â†’ WAITING_FOR_APPROVAL
  â†’ CANCELLED
```

---

## 16. Security Standards

Security requirements:

```txt
1. Authenticate all API calls.
2. Authorize every agent run.
3. Authorize every tool call.
4. Require scoped context for data access.
5. Never trust model output as authorization input.
6. Never allow model to invent tenant/account/user IDs.
7. Never log secrets.
8. Redact sensitive fields in logs.
9. Validate all external calls.
10. Require approval for high-risk actions.
```

---

## 17. Testing Strategy

## 17.1 Unit Tests

Required for:

- agent logic
- prompt factories
- validators
- tools
- context builders
- memory retrieval filters
- authorization policies

## 17.2 Integration Tests

Required for:

- Spring context load
- agent registry
- runtime execution
- persistence
- REST API
- tool execution
- tenant scoping

## 17.3 Evaluation Tests

Required for:

- prompt quality
- output correctness
- regression safety
- hallucination resistance
- tool call correctness
- cost/latency thresholds

## 17.4 Security Tests

Required for:

- missing context rejection
- cross-tenant denial
- unauthorized tool denial
- high-risk action approval requirement
- prompt injection resistance for tool use

---

## 18. Configuration Standard

Example:

```yaml
server:
  port: 8085

agent:
  runtime:
    default-timeout-seconds: 120
    max-tool-calls: 20
    max-retries: 2
    log-prompts: false
    log-tool-results: true

  approval:
    enabled: true
    default-expiration-minutes: 60

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4.1
```

Agent-specific:

```yaml
agent:
  definitions:
    example-analysis-agent:
      enabled: true
      model: gpt-4.1
      timeout-seconds: 90
      prompt-version: "1.0.0"
      max-retries: 1
      risk-level: LOW
```

---

## 19. Agent Behavior Configuration and Runtime Control

The framework must support behavior control from two layers:

```txt
Application configuration
  Defines platform defaults, agent defaults, governance policies, model choices,
  tool policies, memory policies, retry policies, timeout policies, and logging rules.

Caller runtime options
  Defines request-specific preferences such as dry-run mode, response detail,
  correlation ID, idempotency key, async execution preference, and safe bounded overrides.
```

The core rule is:

```txt
Everything can be configurable.
Not everything can be caller-overridable.
```

The framework must resolve final behavior through a controlled policy resolver. Agents and tools must use resolved policy, not raw caller options.

## 19.1 Configuration-Controlled Behavior

`application.yml` or `application.properties` should control the default operating policy of the framework.

Configuration should support:

- enable/disable framework features
- enable/disable individual agents
- model provider selection
- model name
- temperature
- max output tokens
- timeout
- retry count
- max tool calls
- max model calls
- max estimated cost
- prompt version
- memory enabled/disabled
- memory retrieval limit
- memory minimum score
- allowed tools
- tool risk policy
- human approval policy
- logging policy
- prompt logging policy
- tool result logging policy
- audit policy
- async execution policy
- streaming policy
- fallback model policy
- output validation strictness
- evaluation mode

Example:

```yaml
agent:
  runtime:
    enabled: true
    default-timeout-seconds: 120
    max-tool-calls: 20
    max-model-calls: 5
    max-retries: 2
    max-estimated-cost-usd: 1.00
    async-enabled: true
    streaming-enabled: false
    log-prompts: false
    log-tool-results: true

  ai:
    default-provider: openai
    default-model: gpt-4.1
    fallback-model: gpt-4.1-mini
    temperature: 0.1
    max-output-tokens: 4000

  memory:
    enabled: true
    max-results: 8
    min-score: 0.75
    include-prior-decisions: true

  approval:
    enabled: true
    require-approval-for-high-risk-tools: true
    default-expiration-minutes: 60

  overrides:
    allow-caller-timeout: true
    max-caller-timeout-seconds: 180
    allow-caller-model: false
    allow-caller-temperature: false
    allow-caller-tool-selection: false
    allow-caller-memory-toggle: true
    allow-caller-debug-mode: false

  definitions:
    example-analysis-agent:
      enabled: true
      model: gpt-4.1
      timeout-seconds: 90
      max-retries: 1
      max-tool-calls: 5
      max-estimated-cost-usd: 0.25
      prompt-version: "1.0.0"
      risk-level: LOW
      memory-enabled: true
      allowed-tools:
        - search-knowledge-base
        - load-record
```

## 19.2 Caller-Controlled Runtime Options

Callers should be allowed to pass request-specific options, but only within the boundaries configured by the platform.

Example request:

```json
{
  "tenantId": "tenant_123",
    "userId": "user_123",
  "input": {
    "recordId": "record_123"
  },
  "options": {
    "mode": "VALIDATE_ONLY",
    "dryRun": true,
    "async": false,
    "streaming": false,
    "includeEvidence": true,
    "includeRecommendations": true,
    "responseDetail": "FULL",
    "memoryEnabled": true,
    "maxMemoryResults": 5,
    "maxToolCalls": 4,
    "timeoutSeconds": 60,
    "priority": "NORMAL",
    "correlationId": "corr_123",
    "idempotencyKey": "idem_123",
    "approvalMode": "REQUIRE_FOR_HIGH_RISK"
  }
}
```

Safe caller options:

```txt
dryRun
mode
async
streaming
responseDetail
includeEvidence
includeRecommendations
memoryEnabled
maxMemoryResults
maxToolCalls
timeoutSeconds
priority
correlationId
idempotencyKey
approvalMode
debug
```

Caller options must be validated and bounded. For example, a caller may request `timeoutSeconds = 600`, but the framework should cap it using configured policy.

## 19.3 Forbidden or Restricted Caller Overrides

Callers must not freely control:

- system prompt
- hidden developer prompt
- allowed tools
- tool risk classification
- model provider unless explicitly allowed
- model temperature unless explicitly allowed
- max cost above configured limit
- tenant ID without authorization
- user ID without authentication
- cross-tenant memory
- approval bypass
- prompt logging in production
- raw sensitive document logging
- destructive tool execution
- external side effects
- platform-admin mode

Restricted overrides may be allowed only for internal/admin callers with explicit permissions.

## 19.4 Policy Resolution Order

The framework must resolve final behavior in this order:

```txt
1. Platform defaults
2. Environment profile overrides
3. Agent-specific configuration
4. Tool-specific configuration
5. Caller runtime options
6. Caller authorization and override policy
7. Runtime safety caps
8. Final ResolvedAgentPolicy
```

Agents must receive only the final resolved policy.

They must not inspect raw `application.yml` values or raw caller options directly.

```java
ResolvedAgentPolicy policy = agentPolicyResolver.resolve(
    agentId,
    callerOptions,
    executionContext
);

agentRuntime.run(agentId, input, executionContext, policy);
```

## 19.5 Java Configuration Model

Recommended Spring Boot configuration class:

```java
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private RuntimeProperties runtime = new RuntimeProperties();
    private AiProperties ai = new AiProperties();
    private MemoryProperties memory = new MemoryProperties();
    private ApprovalProperties approval = new ApprovalProperties();
    private OverrideProperties overrides = new OverrideProperties();
    private Map<String, AgentDefinitionProperties> definitions = new HashMap<>();

    // getters and setters
}
```

Agent definition:

```java
public class AgentDefinitionProperties {

    private boolean enabled = true;
    private String model;
    private String promptVersion;
    private String riskLevel;
    private int timeoutSeconds = 120;
    private int maxRetries = 2;
    private int maxToolCalls = 20;
    private BigDecimal maxEstimatedCostUsd;
    private boolean memoryEnabled = true;
    private List<String> allowedTools = new ArrayList<>();

    // getters and setters
}
```

Override policy:

```java
public class OverrideProperties {

    private boolean allowCallerTimeout = true;
    private int maxCallerTimeoutSeconds = 180;
    private boolean allowCallerModel = false;
    private boolean allowCallerTemperature = false;
    private boolean allowCallerToolSelection = false;
    private boolean allowCallerMemoryToggle = true;
    private boolean allowCallerDebugMode = false;

    // getters and setters
}
```

## 19.6 Caller Options Model

```java
public record AgentRunOptions(
    Boolean dryRun,
    Boolean async,
    Boolean streaming,
    Boolean memoryEnabled,
    Boolean includeEvidence,
    Boolean includeRecommendations,
    Integer maxMemoryResults,
    Integer maxToolCalls,
    Integer timeoutSeconds,
    String mode,
    String responseDetail,
    String approvalMode,
    String priority,
    String idempotencyKey,
    String correlationId,
    Boolean debug
) {
}
```

## 19.7 Resolved Agent Policy

The runtime should use a resolved immutable policy.

```java
public record ResolvedAgentPolicy(
    boolean enabled,
    boolean dryRun,
    boolean async,
    boolean streaming,
    boolean memoryEnabled,
    boolean includeEvidence,
    boolean includeRecommendations,
    int maxMemoryResults,
    int maxToolCalls,
    int timeoutSeconds,
    int maxRetries,
    BigDecimal maxEstimatedCostUsd,
    String modelProvider,
    String modelName,
    double temperature,
    int maxOutputTokens,
    String promptVersion,
    AgentRiskLevel riskLevel,
    boolean approvalRequired,
    boolean logPrompts,
    boolean logToolResults,
    Set<String> allowedTools,
    String responseDetail,
    String priority
) {
}
```

## 19.8 Policy Resolver

The resolver is responsible for merging defaults, agent config, caller options, and security rules.

```java
@Service
public class AgentPolicyResolver {

    private final AgentProperties properties;
    private final AgentOverrideAuthorizationService overrideAuthorizationService;

    public ResolvedAgentPolicy resolve(
        String agentId,
        AgentRunOptions options,
        AgentExecutionContext context
    ) {
        AgentDefinitionProperties agentConfig = properties
            .getDefinitions()
            .getOrDefault(agentId, new AgentDefinitionProperties());

        if (!agentConfig.isEnabled()) {
            throw new AgentExecutionException(
                AgentErrorCode.AUTHORIZATION_DENIED,
                "Agent is disabled: " + agentId,
                null
            );
        }

        int timeoutSeconds = resolveTimeout(agentConfig, options, context);
        int maxToolCalls = resolveMaxToolCalls(agentConfig, options);
        boolean memoryEnabled = resolveMemoryEnabled(agentConfig, options, context);

        return new ResolvedAgentPolicy(
            true,
            Boolean.TRUE.equals(options.dryRun()),
            Boolean.TRUE.equals(options.async()),
            Boolean.TRUE.equals(options.streaming()),
            memoryEnabled,
            Boolean.TRUE.equals(options.includeEvidence()),
            Boolean.TRUE.equals(options.includeRecommendations()),
            options.maxMemoryResults() != null ? options.maxMemoryResults() : properties.getMemory().getMaxResults(),
            maxToolCalls,
            timeoutSeconds,
            agentConfig.getMaxRetries(),
            agentConfig.getMaxEstimatedCostUsd(),
            properties.getAi().getDefaultProvider(),
            agentConfig.getModel() != null ? agentConfig.getModel() : properties.getAi().getDefaultModel(),
            properties.getAi().getTemperature(),
            properties.getAi().getMaxOutputTokens(),
            agentConfig.getPromptVersion(),
            AgentRiskLevel.valueOf(agentConfig.getRiskLevel()),
            resolveApprovalRequired(agentConfig),
            properties.getRuntime().isLogPrompts(),
            properties.getRuntime().isLogToolResults(),
            Set.copyOf(agentConfig.getAllowedTools()),
            options.responseDetail() != null ? options.responseDetail() : "SUMMARY",
            options.priority() != null ? options.priority() : "NORMAL"
        );
    }

    private int resolveTimeout(
        AgentDefinitionProperties agentConfig,
        AgentRunOptions options,
        AgentExecutionContext context
    ) {
        int configured = agentConfig.getTimeoutSeconds();

        if (options.timeoutSeconds() == null) {
            return configured;
        }

        if (!properties.getOverrides().isAllowCallerTimeout()) {
            return configured;
        }

        return Math.min(
            options.timeoutSeconds(),
            properties.getOverrides().getMaxCallerTimeoutSeconds()
        );
    }
}
```

## 19.9 Dry Run Semantics

`dryRun = true` means:

```txt
Allowed:
  build context
  retrieve memory
  call read-only tools
  call AI model
  generate recommendations
  validate output
  persist execution logs

Blocked:
  business data mutation
  external side effects
  destructive tool calls
  final status-changing writes
```

Tools with side effects must check the resolved policy before executing.

```java
if (policy.dryRun() && toolDescriptor.sideEffect() != ToolSideEffect.NONE) {
    throw new AgentExecutionException(
        AgentErrorCode.TOOL_NOT_ALLOWED,
        "Tool is not allowed in dry-run mode: " + toolDescriptor.name(),
        null
    );
}
```

## 19.10 Mode Semantics

Recommended standard modes:

```txt
ANALYZE_ONLY
  Agent analyzes and returns findings.

VALIDATE_ONLY
  Agent validates input/context and returns validation results.

RECOMMEND
  Agent produces recommendations but does not execute changes.

EXECUTE
  Agent may execute approved write tools.

AUTO_REMEDIATE
  Agent may remediate issues within configured safety boundaries.
```

Mode must be constrained by agent risk level, caller permissions, and configured policy.

## 19.11 Response Detail Semantics

Recommended values:

```txt
MINIMAL
  Return execution ID and status.

SUMMARY
  Return status, short summary, and major warnings.

FULL
  Return structured output, evidence summaries, decisions, and recommendations.

DEBUG
  Return additional diagnostics. Must be restricted to development/admin callers.
```

## 19.12 Cost and Budget Control

The framework should support:

- per-run cost cap
- per-agent cost cap
- per-tenant/workspace daily budget
- per-user budget
- fallback model when budget is tight
- hard failure when budget is exceeded

Example:

```yaml
agent:
  runtime:
    max-estimated-cost-usd: 1.00

  definitions:
    expensive-research-agent:
      max-estimated-cost-usd: 5.00
```

If the model call estimate exceeds policy:

```txt
Execution fails with `GOVERNANCE_LIMIT_EXCEEDED` before invocation when the configured estimated cost budget is exceeded. Actual cost reconciliation remains roadmap until provider tariff/cost configuration is added.
No model call is made.
```

## 19.13 Tool Control

Tool availability should be resolved from:

```txt
1. framework-level registered tools
2. agent allowed tools
3. caller requested tool constraints
4. caller permissions
5. risk/approval policy
6. dry-run policy
```

If the model requests a disallowed tool, the runtime must reject the tool call and record the rejection.

```txt
ToolCall.status = REJECTED
ToolCall.errorCode = TOOL_NOT_ALLOWED
```

## 19.14 Runtime Control Examples

Disable an agent:

```yaml
agent:
  definitions:
    example-analysis-agent:
      enabled: false
```

Run safely:

```json
{
  "input": {
    "recordId": "record_123"
  },
  "options": {
    "dryRun": true,
    "mode": "RECOMMEND",
    "responseDetail": "FULL"
  }
}
```

Limit memory:

```json
{
  "options": {
    "memoryEnabled": true,
    "maxMemoryResults": 3
  }
}
```

Request async execution:

```json
{
  "options": {
    "async": true,
    "priority": "HIGH",
    "idempotencyKey": "case-123-review"
  }
}
```

## 19.15 Framework Requirement

The runtime must expose the resolved policy to:

- agent implementation
- AI client
- memory service
- tool service
- guardrail service
- persistence service
- observability service

The resolved policy should be recorded with the execution record, either fully or as a summarized snapshot.

This makes agent behavior reproducible and auditable.

---

## 20. Phase Plan

## Phase 1: Framework Foundation

Deliver:

```txt
agent-api
agent-core
agent-storage
agent-app
one example read-only analysis agent
```

## Phase 2: Memory and Tool Governance

Deliver:

```txt
agent-core memory package
vector store integration
tool registry
tool audit
tool approval policy
prompt version store
```

Phase 2 implementation baseline:

- The registry must index agents by `agentId + version`.
- Each descriptor must be validated at startup in strict mode.
- Duplicate `agentId + version` registrations must fail fast.
- A configured default version may be selected per agent.
- If no default version is configured, the runtime may select the highest registered version.
- Callers may request a specific agent version per run.
- Runtime lifecycle must emit pipeline events so observability, audit, and future async orchestration can subscribe without coupling to agent implementation code.
- Tool contracts must stay generic: descriptor, input, result, and registry first; provider-specific execution and approval policy can be layered on top later.
- Memory must be scoped by tenant and namespace.
- Vector memory must be exposed through a provider-neutral store contract.
- Tool execution must flow through a governance service that checks runtime policy, tool allow-lists, permissions, approval requirements, and audit events.
- Prompt templates must be versioned independently from agent code, with a way to resolve the active prompt for an agent.
- Framework modules should provide Spring Boot auto-configuration so deployed agent applications can opt into runtime, memory, tools, prompt, and persistence capabilities by adding module dependencies.
- Persistence must be testable against H2 for fast CI feedback and portable to PostgreSQL and Oracle through explicit runtime datasource/profile configuration plus manually executed DDL.
- Per-agent runtime behavior should be configuration-driven through `agent-platform.definitions[...]`; use bracket notation for dotted agent ids such as `[generic.echo-analysis]`.
- Spring AI provider starters must be opt-in for deployed applications. The framework adapter should not force OpenAI, Azure OpenAI, Anthropic, or any other provider into every agent service.

## Phase 3: Async and Human Approval

Deliver:

```txt
async execution
queue/worker support
approval request lifecycle
execution resume
webhook/callback events
```

Phase 3 implementation baseline:

- Async orchestration must live behind provider-neutral ports so teams can use the default in-memory queue, the JPA database queue, or replace both with Kafka, RabbitMQ, SQS, or another enterprise queue.
- Submitted async executions are persisted as `QUEUED` before dispatch.
- If the resolved policy requires human approval, execution moves to `WAITING_FOR_APPROVAL` and creates an approval request before any agent invocation.
- Approval requests must be queryable, auditable, and persisted independently from execution records.
- Approval resume must enqueue the original pending command and preserve execution id, tenant id, actor id, correlation id, runtime options, and callback URL.
- Rejected or expired approvals must cancel the execution and emit a callback event when configured.
- The reference app may expose a manual worker-tick endpoint, but production deployments should use a real worker process, scheduler, or external queue consumer.
- In-process workers must be opt-in through configuration.
- Callback delivery must be abstracted behind `AgentCallbackDispatcher`; the framework provides an optional HTTP/webhook dispatcher with timeout, retry, and HMAC signing controls while still allowing deployed applications to replace the transport.
- Persistence must remain portable across PostgreSQL, Oracle, and H2 tests through explicit JPA mappings and manual DDL.
- Pending commands must be durable enough to resume after process restart. The default JPA pending-command store persists the typed input payload, run options, tenant, actor, correlation id, context attributes, and callback URL.
- Tool-level approval is integrated through a framework approval-request sink so high-risk tool calls are governed and auditable before side effects occur. Baseline resumability stores the paused tool request in approval metadata and executes that approved tool request exactly once on approval. Full checkpointed continuation of an arbitrary multi-step agent workflow remains a later workflow-engine enhancement.

## Phase 4: Evaluation and Production Hardening

Deliver:

```txt
agent-core evaluation package
golden datasets
prompt regression tests
cost governance
rate limits
admin observability dashboard
execution replay
```

Phase 4 implementation baseline:

- Evaluation must live in the generic `agent-core` evaluation package so any agent can be tested against golden datasets without domain coupling.
- Golden datasets should be explicit, versionable, and executable through the same runtime path as production runs.
- Prompt regression checks must validate prompt invariants independently from agent code so prompt edits can be reviewed safely.
- Cost governance and rate limits must be enforced through runtime governance hooks, not inside individual agents.
- Default governance should be safe for local development: limits are configurable and disabled when set to zero.
- Observability dashboard support starts with framework services and pipeline-event collection. Deployed applications may expose dashboards only through their own authenticated/admin boundary.
- Execution replay must be a framework service that reuses stored input and execution metadata. Public replay endpoints are intentionally not part of the generic baseline because replay can re-trigger side effects unless the deployed application gates it with authorization and idempotency rules.
- Production implementations may replace in-memory evaluation, observability, and governance stores with database or telemetry-backed implementations.

Phase 4 production-ready completion criteria:

- Golden dataset evaluation must persist summary results through `EvaluationResultStore` so CI, dashboards, and release gates can compare historical evaluation outcomes.
- The framework must provide a safe in-memory evaluation-result store for local use and allow deployed applications to replace it with a durable implementation.
- The storage module must provide a JPA-backed evaluation-result store with portable mappings and matching manual PostgreSQL/Oracle DDL.
- Admin operations such as evaluation execution, observability queries, and replay must be opt-in, disabled by default, and protected by an explicit admin boundary.
- Reference admin endpoints must require `X-Agent-Admin-Token` when enabled and must not expose replay/observability anonymously.
- List-style admin endpoints must enforce bounded result sizes.
- H2 tests must verify persistence mappings, configuration-driven boot, governance, replay, evaluation, and admin security behavior.

---

## 21. Enterprise Agentic AI Standards Mapping

There is no single standard that fully defines production agentic AI systems end to end. A production-grade framework should instead combine the best available standards across AI governance, application security, interoperability, observability, and operational reliability.

This framework should align to the following standards and accepted practices.

| Standard / framework | What it contributes | Framework alignment |
| --- | --- | --- |
| NIST AI RMF 1.0 and NIST AI 600-1 GenAI Profile | AI risk governance, lifecycle risk management, trustworthiness, measurement, evaluation, and monitoring | Risk levels, governance hooks, evaluation datasets, audit trail, cost/rate limits, human approval, monitoring, and lifecycle checkpoints |
| ISO/IEC 42001 | AI management system for responsible development, deployment, operation, review, and continuous improvement | Agent lifecycle governance, documented controls, role ownership, risk acceptance, production review gates, and continuous monitoring |
| OWASP Top 10 for LLM / GenAI Applications | Security risks specific to LLM/agentic systems | Prompt-injection controls, sensitive-data controls, tool governance, output validation, memory isolation, supply-chain review, and unbounded-consumption controls |
| OpenTelemetry | Vendor-neutral traces, metrics, logs, baggage, context propagation, and observability integration | Pipeline events, execution IDs, correlation IDs, trace/span model, metrics, logs, and future OTLP export |
| Model Context Protocol (MCP) | Standard integration pattern for external tools, data, and context sources | Future MCP adapter around the generic tool/context interfaces; MCP tools still pass through local authorization, guardrails, approval, and audit |
| Agent2Agent (A2A) | Agent discovery, capability cards, secure inter-agent task exchange, streaming, and async task handling | Future A2A adapter around agent descriptors, run requests, async handles, task lifecycle, and secure agent cards |
| CloudEvents | Common event metadata for interoperable event-driven systems | Future event publishing format for lifecycle events, approvals, callbacks, evaluation results, and governance events |
| OpenAPI | Contract-first REST API documentation and client generation | Reference app REST APIs should publish OpenAPI contracts; deployed agent apps should version API contracts independently from agent versions |

### 21.1 NIST AI RMF / GenAI Profile alignment

The framework should support NIST-style AI risk management across the lifecycle:

```text
govern â†’ map â†’ measure â†’ manage
```

Required controls:

- classify each agent by risk level
- document intended use and disallowed use
- map affected users, tenants, systems, and data classes
- measure quality with golden datasets and regression tests
- measure safety with adversarial tests
- measure reliability with replay and deterministic regression where possible
- monitor production behavior through traces, metrics, logs, and evaluation drift
- manage risk through policy, approvals, rate limits, cost limits, and rollback

Each production agent should maintain an AI risk record:

```yaml
agentId: example.agent
version: 1.0.0
riskLevel: MEDIUM
owner: team-name
intendedUse:
  - supported business use case
disallowedUse:
  - unsupported / unsafe use case
dataClasses:
  - public
  - internal
  - confidential
controls:
  - input validation
  - output validation
  - tool allow-list
  - human approval for high-risk actions
  - evaluation gate
  - monitoring dashboard
review:
  approvedBy: architecture/security/product
  approvedAt: yyyy-mm-dd
```

### 21.2 ISO/IEC 42001 AI management alignment

The organization operating the framework should define an AI management process around agent creation and operation.

Minimum lifecycle:

```text
proposal
  â†’ design review
  â†’ risk classification
  â†’ implementation
  â†’ evaluation
  â†’ security review
  â†’ approval
  â†’ deployment
  â†’ monitoring
  â†’ periodic reassessment
  â†’ retirement
```

Required governance artifacts:

- agent owner
- business purpose
- risk classification
- data classification
- model/provider choice
- tool permissions
- memory policy
- approval policy
- evaluation report
- rollout plan
- rollback plan
- production monitoring plan
- incident response owner

No high-risk agent should move to production without documented approval and measurable acceptance criteria.

### 21.3 OWASP LLM / GenAI security checklist

Each production agent must be reviewed against the following risk classes.

| Risk | Required framework control |
| --- | --- |
| Prompt injection | Input guardrails, instruction hierarchy, tool-call validation, retrieved-content labeling, and adversarial tests |
| Sensitive information disclosure | Redaction, no prompt logging by default, scoped memory, data minimization, and output filtering |
| Supply-chain risk | Approved dependencies, model/provider registry, prompt/tool review, SBOM, and dependency scanning |
| Data/model poisoning | trusted ingestion paths, source metadata, embedding provenance, moderation of memory writes, and retraining/reindex controls |
| Improper output handling | output schemas, validation, escaping, downstream contract checks, and safe rendering |
| Excessive agency | mode limits, tool allow-lists, step limits, approval gates, dry-run mode, and side-effect classification |
| System prompt leakage | prompt secrecy rules, no debug prompt exposure in production, redaction, and restricted diagnostics |
| Vector/embedding weaknesses | tenant-scoped retrieval, metadata filters, minimum score thresholds, source attribution, and deletion support |
| Misinformation / overreliance | confidence scoring, evidence requirements, citations/source references, human review for critical decisions |
| Unbounded consumption | rate limits, token/cost caps, timeout budgets, concurrency limits, and circuit breakers |

### 21.4 Red-team and adversarial testing standard

Golden tests validate expected behavior. Red-team tests validate resilience against hostile or confusing behavior.

Every production agent should include adversarial suites for:

- prompt injection
- tool misuse
- tenant boundary bypass
- memory leakage
- sensitive-data extraction
- unsafe output
- hallucinated authority
- approval bypass
- excessive cost/resource usage
- malicious retrieved documents

Example adversarial test categories:

```yaml
redTeamSuites:
  promptInjection:
    required: true
    threshold: 100% blocked for critical payloads
  tenantIsolation:
    required: true
    threshold: 100% denied cross-tenant access
  toolMisuse:
    required: true
    threshold: no side-effect tool runs without policy approval
  sensitiveData:
    required: true
    threshold: no restricted secrets or credentials in output
```

### 21.5 Model and provider governance

Model choice is a production control, not an implementation detail.

The framework should support an approved model registry:

```yaml
approvedModels:
  openai.gpt-4.1-mini:
    provider: openai
    model: gpt-4.1-mini
    allowedRiskLevels: [LOW, MEDIUM]
    allowedDataClasses: [PUBLIC, INTERNAL]
    maxContextTokens: 1048576
    supportsTools: true
    supportsStructuredOutput: true
    defaultTemperature: 0.2
    status: ACTIVE
  internal.safe-model:
    provider: internal
    model: safe-model
    allowedRiskLevels: [LOW, MEDIUM, HIGH]
    allowedDataClasses: [PUBLIC, INTERNAL, CONFIDENTIAL]
    status: ACTIVE
```

Required controls:

- approved provider list
- approved model list
- model version tracking
- model capability classification
- allowed data classification per model
- provider failover rules
- fallback model restrictions
- model retirement/deprecation plan
- per-model evaluation baselines
- per-model cost and latency monitoring

Fallback model behavior must never silently reduce safety. If fallback changes model family, capability, jurisdiction, or data policy, the run should fail or require explicit approval.

### 21.6 Data governance and privacy standard

Agent inputs, prompts, memory, tool outputs, and execution records may contain sensitive data. The framework should treat all of them as governed data.

Required controls:

- classify data before storage or model use
- minimize data sent to the model
- redact secrets from logs, traces, prompts, and evaluation records
- disable raw prompt logging in production by default
- encrypt data at rest through the database/storage layer
- encrypt data in transit
- scope memory by tenant/account/project/user as required
- support memory deletion and retention policies
- prevent cross-tenant retrieval
- define retention periods for execution records, approvals, tool audits, prompts, memory, and evaluation results
- avoid storing chain-of-thought or hidden reasoning
- store final answer, structured reasoning summaries, evidence, and decision metadata instead

Recommended retention policy model:

```yaml
retention:
  executionRecordsDays: 365
  approvalRecordsDays: 365
  toolAuditDays: 365
  promptVersionsDays: 1825
  evaluationResultsDays: 730
  memoryDefaultTtlDays: 180
  rawPromptLoggingEnabled: false
```

### 21.7 Interoperability standards

The framework should remain provider-neutral and integration-friendly.

#### MCP

MCP should be treated as an adapter into the framework tool/context layer.

Rules:

- MCP-provided tools must be registered as governed `AgentTool` instances.
- MCP tool schemas must be validated before registration.
- MCP calls must use the same authorization, tenant context, approval, audit, and rate-limit controls as local tools.
- MCP resources must be scoped before being added to prompts or memory.

#### A2A

A2A should be treated as an optional adapter for remote agent collaboration.

Rules:

- Agent descriptors should be mappable to A2A Agent Cards.
- A2A tasks should map to framework execution records.
- A2A streaming should map to pipeline events.
- A2A push notifications should use the callback dispatcher and signing controls.
- Remote agents must be authenticated and authorized before task exchange.
- A2A must not expose internal memory, hidden prompts, or private tool implementation details.

#### CloudEvents

Lifecycle events should be publishable as CloudEvents when integrating with enterprise event platforms.

Recommended event types:

```text
ai.qubere.agent.run.created
ai.qubere.agent.run.started
ai.qubere.agent.run.succeeded
ai.qubere.agent.run.failed
ai.qubere.agent.approval.requested
ai.qubere.agent.approval.approved
ai.qubere.agent.approval.rejected
ai.qubere.agent.tool.started
ai.qubere.agent.tool.succeeded
ai.qubere.agent.tool.failed
ai.qubere.agent.evaluation.completed
ai.qubere.agent.governance.blocked
```

Each event should include:

- `id`
- `source`
- `type`
- `specversion`
- `time`
- `subject`
- `datacontenttype`
- tenant/account/workspace context as extension attributes only when allowed
- correlation ID / trace ID

#### OpenAPI

Every deployed REST API should publish an OpenAPI document covering:

- agent discovery
- synchronous execution
- asynchronous execution
- approval decisions
- execution lookup
- admin endpoints, if enabled
- error response schema
- security schemes
- idempotency headers
- pagination and limits

### 21.8 Observability and SRE standard

Production agents need operational controls equal to normal backend services, plus AI-specific signals.

Required telemetry:

- request count
- succeeded/failed count
- latency percentiles
- timeout count
- retry count
- token usage
- estimated cost
- tool-call count
- tool failures
- approval requests
- approval latency
- governance blocks
- guardrail blocks
- evaluation pass/fail trend
- memory retrieval count
- vector retrieval score distribution

Recommended SLOs:

```yaml
slo:
  availability: 99.9%
  p95LatencySeconds: 30
  failedRunRate: < 1%
  timeoutRate: < 0.5%
  unapprovedHighRiskActionRate: 0
  crossTenantAccessViolationRate: 0
```

Required production operations:

- health endpoint
- readiness endpoint
- dependency health checks
- database connectivity check
- model provider connectivity check
- queue depth monitoring
- dead-letter queue for async runs
- retry and backoff policy
- circuit breaker for provider/tool failures
- incident runbook
- rollback procedure
- dashboard for live run status and evaluation trends

### 21.9 Release and deployment governance

Each agent release should pass:

- compile/test
- unit tests
- integration tests
- H2 persistence tests
- security tests
- golden dataset evaluation
- prompt regression tests
- red-team tests for medium/high-risk agents
- OpenAPI contract validation for deployed APIs
- manual approval for high-risk agents

Recommended release gate:

```yaml
releaseGate:
  unitTests: pass
  integrationTests: pass
  securityTests: pass
  goldenEvaluation:
    minPassRate: 0.95
  promptRegression: pass
  highRiskApproval:
    required: true
  rollbackPlan:
    required: true
```

### 21.10 Implementation status classification

Standards should be tracked honestly. Not every standard capability must be fully implemented in the first framework release.

| Capability | Current status |
| --- | --- |
| Agent contracts | Implemented |
| Version-aware registry | Implemented |
| Policy resolution | Implemented; baseline runtime enforcement covers timeout, retry, max steps, max tool calls, and dry-run tool safety |
| Runtime guardrail hooks | Implemented as extension points; default implementation is allow-all |
| Tool governance | Implemented baseline; generic tool registry/execution/approval exists, persisted tool arguments/results are redaction-aware, and the reference app includes a read-only tool-backed sample agent |
| Human approval | Implemented for agent-run approval, durable approval persistence, admin listing/detail, expiry sweep, and baseline tool-level approval resumability |
| Async execution | Implemented with default in-memory queue, optional JPA database queue, and JPA pending-command persistence |
| Callback signing | Implemented for HTTP callbacks when enabled |
| Prompt versioning | Implemented baseline; in-memory/JPA stores, config-driven seed prompts, and admin-gated list/create/activate/deprecate/archive APIs exist |
| Memory abstraction | Implemented as provider-neutral contracts with in-memory default |
| Tenant-scoped memory interface | Implemented at interface level; durable/vector provider integration remains roadmap |
| Evaluation datasets | Implemented for in-memory plus classpath/file JSON loading; database-backed dataset repository remains roadmap |
| Prompt regression | Implemented as framework service; not exposed through reference app workflow |
| Replay service | Implemented and admin-gated in reference app |
| Governance rate/cost hooks | Partially implemented; rate limits work and model usage capture exists, while hard token/cost budget enforcement remains roadmap |
| Durable execution persistence | Implemented |
| Durable approval persistence | Implemented |
| Durable evaluation persistence | Implemented; admin-triggered classpath dataset E2E is covered in the reference app |
| Admin API token gate and disabled-route 404 mapping | Implemented in reference app |
| Execution log table | Implemented in `agent-storage`; pipeline events are persisted by `JpaAgentExecutionLogListener` |
| Tool call table | Implemented in `agent-storage`; `ToolExecutionService` records one row per attempted tool call and the reference app exercises it through `generic.tool-echo-analysis` |
| Model usage table | Implemented in `agent-storage`; `SpringAiAgentClient` records model attempts and captures Spring AI token/model metadata when available; cost enrichment remains roadmap until tariffs are configured |
| OpenTelemetry export | Foundation implemented; OpenTelemetry-shaped pipeline listener and telemetry exporter port exist, real OTLP exporter remains roadmap |
| MCP adapter | Extension point / roadmap |
| A2A adapter | Extension point / roadmap |
| CloudEvents publisher | Extension point / roadmap |
| Model registry | Design standard / roadmap |
| Formal AI risk records | Design standard / roadmap |
| Red-team dataset runner | Design standard / roadmap |
| Enterprise dashboard UI | Design standard / roadmap |

## 22. Current implementation review and design gaps

This section captures the implementation gaps found during source-code review of the current `qubere-agents` modules. It is intended to replace any lost session context and make the remaining design work explicit.

Current modules:

```txt
qubere-agents/
  agent-api      - public agent contract types
  agent-core     - runtime, policy, tools, memory, prompts, async, evaluation, replay, governance
  agent-storage  - JPA persistence for implemented stores
  agent-app      - runnable reference Spring Boot host
```

### 22.1 Database schema gaps

The current JPA implementation and manual Oracle/PostgreSQL DDL create these tables:

```txt
agent_execution_record
agent_execution_log
agent_tool_call
agent_tool_audit_event
agent_prompt_template
agent_approval_request
agent_pending_command
agent_async_queue_command
agent_model_usage
agent_evaluation_result
```

The three core durable observability/accounting tables are now implemented: execution lifecycle events, tool calls, and model usage. The reference app now exercises both tool-backed and AI-backed paths. Remaining work is mostly provider metadata enrichment, policy enforcement hardening, richer evaluation metadata, and production observability.

Recommended implementation:

- Completed: `agent_execution_log` persists structured lifecycle events for each execution through the JPA pipeline listener.
- Completed: `agent_tool_call` persists one row per attempted tool invocation, including input JSON, output summary, status, latency, risk level, side-effect classification, and approval reference if applicable.
- Completed: `agent_model_usage` persists model provider, model name, prompt version, token usage, cost estimate, latency, status, error message, and metadata. `SpringAiAgentClient` records STARTED/SUCCEEDED/FAILED attempts and enriches successful rows with Spring AI provider metadata when available, including prompt/input tokens, completion/output tokens, total tokens, provider model id, native usage type, and cache token metadata. Cost values remain nullable until a provider tariff/cost source is configured.

The existing `agent_tool_audit_event` table should not be confused with `agent_tool_call`. It is useful as an audit-event stream, but a dedicated `agent_tool_call` table is still valuable for analytics, governance, replay review, and SRE dashboards.

### 22.2 Runtime policy enforcement status

The framework now enforces the core runtime safety policy fields that protect an execution from unbounded or unsafe behavior:

- `timeoutSeconds` is enforced around agent invocation.
- `maxRetries` is enforced for retryable runtime failures.
- `maxSteps` is tracked through `AgentRunBudget` and consumed by agent invocation/tool-call steps.
- `maxToolCalls` is enforced by `ToolExecutionService` through the same run budget.
- `dryRun` blocks tools that declare side effects beyond read-only external access.

The following fields now have baseline provider-adapter behavior:

- `modelName` is passed into Spring AI per call when resolved by policy.
- `temperature` is passed into Spring AI `ChatOptions`.
- `maxOutputTokens` is passed into Spring AI as `maxTokens`.
- provider usage metadata is captured after successful Spring AI calls when Spring AI exposes it.

The following fields now have baseline redaction-aware logging behavior:

- `logPrompts` remains disabled by default; when enabled, `SpringAiAgentClient` stores redacted prompt text/variables in model usage metadata.
- `logToolResults` remains disabled by default; when enabled, `ToolExecutionService` stores redacted tool result summaries. Tool input arguments, audit metadata, audit messages, output summaries, and error messages are redacted before durable recording.

The following fields still need additional provider-adapter or API behavior before they should be considered fully enforced:

- `streaming` is resolved and recorded, but synchronous structured `AgentAiClient.generate` does not stream; this needs a dedicated streaming API variant.
- `responseDetail`
- hard token/cost budget limits using provider usage metadata and configured tariffs

Recommended next implementation:

- Continue provider option coverage for future adapters and add a dedicated streaming API variant.
- Continue strengthening redaction hooks for application-specific PII policies.
- Enforce hard token/cost budgets after cost/tariff configuration is added.

### 22.3 Spring AI integration gaps

`SpringAiAgentClient` wraps `ChatClient`, and the reference app now includes `generic.ai-analysis`, an AI-backed sample agent that calls `AgentAiClient` for structured output. Automated tests use a fake `AgentAiClient` so CI does not require a provider key; real provider testing is opt-in through the `ai-openai` Maven profile and Spring AI environment settings.

Recommended implementation:

- Completed: added `generic.ai-analysis`, a sample agent that uses `AgentAiClient` for structured output.
- Completed: capture model-call attempts from the Spring AI response path into `agent_model_usage` through `ModelUsageRecorder`.
- Completed baseline: app-level H2 test proves structured AI output and model-usage persistence through a fake `AgentAiClient`.
- Completed: extract input tokens, output tokens, total tokens, provider model id, native usage type, and cache token metadata from Spring AI `ChatResponse` metadata when available. Remaining: calculate actual/estimated cost once a provider tariff/cost source is configured.
- Remaining: add optional provider-enabled smoke tests outside normal CI.
- Keep provider starters opt-in so the framework does not force OpenAI, Azure OpenAI, Anthropic, or any specific model provider into every application.

### 22.4 Tool execution and audit gaps

The generic tool framework exists, including registry, approval policy, audit service, side-effect classification, required permissions, audit events, and durable `agent_tool_call` persistence. The reference app now includes a harmless read-only `echo.lookup` tool and `generic.tool-echo-analysis` sample agent that invokes it through `ToolExecutionService`.

Recommended implementation:

- Completed: added the harmless read-only `echo.lookup` sample tool.
- Completed: added `generic.tool-echo-analysis`, a sample agent path that invokes the tool through `ToolExecutionService`.
- Completed: persist tool execution into both `agent_tool_call` and `agent_tool_audit_event`.
- Completed baseline: app-level H2 test proves an allowed tool call writes tool-call and audit rows.
- Remaining: add explicit E2E tests for denied tool calls and approval-required tool calls.

### 22.5 Prompt management status

Prompt storage exists through `PromptVersionStore` and JPA persistence. The reference app now provides a baseline prompt authoring and seed workflow.

Completed baseline:

- Config-driven seed prompt templates are loaded at startup through `agent-platform.prompts.seeds` when `agent-platform.prompts.seed-enabled=true`.
- Seeds are idempotent by default: an existing `promptId` + `version` is not overwritten unless the seed sets `overwrite=true`.
- Admin-gated APIs support listing prompt versions by agent, reading one prompt version, creating a new prompt version, activating, deprecating, and archiving.
- Activating one prompt version deprecates other active prompt versions for the same agent.
- The reference app seeds sample prompts for `generic.echo-analysis`, `generic.tool-echo-analysis`, and `generic.ai-analysis`.

Remaining:

- Add prompt regression tests that can be run before activating a new prompt version.
- Define rules for prompt data classification because prompts may contain business-sensitive information.
- Add migration/version promotion conventions for moving prompt versions across environments.

### 22.6 Evaluation gaps

Evaluation services, JPA result persistence, and JSON golden dataset loading now exist. The default repository loads datasets from `classpath*:agent-evaluation/*.json`, and applications can configure file/classpath locations through `agent-platform.evaluation.dataset-locations`.

Recommended implementation:

- Completed: classpath/file-based JSON golden dataset loading is implemented through `ResourceGoldenDatasetRepository`.
- Remaining: add optional database-backed dataset repository later.
- Completed: admin-triggered evaluation results are persisted through `EvaluationResultStore`.
- Add dataset version, agent version, model provider, model name, and prompt version into stored evaluation metadata.

### 22.7 Observability gaps

The current observability service keeps recent pipeline events in memory for admin summaries, and `agent-storage` now persists execution timelines, tool calls, and model usage through `agent_execution_log`, `agent_tool_call`, and `agent_model_usage`. The OpenTelemetry foundation is implemented as an opt-in pipeline listener plus `AgentTelemetryExporter` port, producing OpenTelemetry-shaped span events without forcing OpenTelemetry SDK/OTLP dependencies into the core framework.

Recommended implementation:

- Continue using `agent_execution_log` as the durable source for execution timelines and future dashboards.
- Export metrics for run count, failure count, latency, governance blocks, approval wait time, tool count, token usage, and estimated cost from the durable execution/tool/model records.
- Completed foundation: add an OpenTelemetry-shaped pipeline listener and telemetry exporter port behind `agent-platform.observability.open-telemetry.enabled`.
- Remaining: add a real OpenTelemetry SDK/OTLP bridge for traces, metrics, logs, resource attributes, baggage/context propagation, and exporter health.
- Preserve correlation id and tenant id across sync, async, approval resume, callback, and replay paths.

### 22.8 Admin API and HTTP error handling gaps

Admin APIs are correctly opt-in and token-gated when enabled. When admin APIs are disabled, requests to admin URLs now return a consistent HTTP 404 API error instead of falling through Spring static-resource handling and being mapped as HTTP 500.

Implementation status:

- Completed: `AgentApiExceptionHandler` explicitly handles Spring `NoResourceFoundException` and `NoHandlerFoundException` as HTTP 404 with error code `NOT_FOUND`.
- Completed: `AgentApiExceptionHandlerWebTest` verifies disabled admin URLs return 404 instead of 500.
- Remaining: continue standardizing API errors for 400, 403, 429, and 500 where new framework paths are added.

### 22.9 Configuration status

The reference app now defaults to a local H2 profile through `AGENT_DB:local`, so a developer can start `agent-app` without PostgreSQL or Oracle setup. PostgreSQL and Oracle remain explicit Maven/runtime profiles, and Oracle manual-DDL runs can set `AGENT_JPA_DDL_AUTO=none` to avoid slow Hibernate metadata validation.

Implementation status:

- Completed: default runtime profile is `local`.
- Completed: `application-local.yml` provides an H2 datasource and local-friendly `ddl-auto=update` default.
- Completed: PostgreSQL and Oracle drivers are opt-in Maven profiles instead of active-by-default runtime dependencies.
- Completed: README and `agent-app/TESTING.md` document local H2, PostgreSQL, Oracle, and STS startup paths.
- Remaining: keep real credentials outside YAML files and outside Git history.
- Remaining: add richer Spring Boot configuration metadata if this becomes a published starter.

### 22.10 Async and approval status

Async execution and human approval work in the reference app. The queue provider is explicitly selected through `agent-platform.async.queue.type`. `memory` is the local default, `database` enables the JPA-backed queue from `agent-storage` using `agent_async_queue_command`, and `kafka`, `rabbitmq`, and `sqs` are reserved provider names for future production adapters or application-provided `AgentAsyncQueue` beans. Pending commands and approvals can also be persisted through JPA. The reference app now includes operational admin endpoints for approval review and expiry processing.

Completed baseline:

- Agent-run approval requests are persisted through `AgentApprovalStore` and `agent_approval_request`.
- Public approval decision endpoints support approve/reject/decision for pending runs.
- Admin-gated APIs support listing approvals with optional `status`, `tenantId`, and `limit` filters.
- Admin-gated detail API supports reading one approval request by approval id.
- Admin-gated expiry API marks pending approvals with `expiresAt <= now` as `EXPIRED`.
- Async submission supports tenant-scoped idempotency keys through `X-Idempotency-Key` or request body `idempotencyKey`; retries return the existing execution handle instead of queuing duplicate work.
- Optional database-backed async queue persists queued run commands in `agent_async_queue_command` when `agent-platform.async.queue.type=database` is configured.
- Queue provider selection is formalized with `memory`, `database`, `kafka`, `rabbitmq`, and `sqs` provider names; unsupported production providers fail with an actionable message unless the application contributes its own `AgentAsyncQueue` bean.
- Approval approve/reject retries are idempotent when the approval was already decided the same way; conflicting decisions still fail.

Remaining:

- Implement real Kafka, RabbitMQ, and SQS adapter modules with retry, visibility timeout, dead-letter, serialization/versioning, and metrics behavior.
- Decide whether rejected approvals should delete or retain pending commands for audit/replay analysis.
- Add full checkpointed continuation for complex multi-step workflows that must resume the original agent call stack after an approved tool call.

### 22.11 Security and privacy gaps

The framework has authorization and guardrail extension points, but defaults are permissive for local development.

Recommended implementation:

- Completed: `ReferenceAgentAuthorizationService` provides permissive local mode and strict mode with tenant id, actor id, allowed tenant, global permission, and per-agent permission checks.
- Completed baseline: `AgentRedactionService` provides injectable redaction hooks for prompt metadata, tool arguments/results, tool audit messages, and provider native usage metadata.
- Add OAuth/JWT/RBAC adapters that populate tenant, actor, and permission context from enterprise identity providers.
- Add prompt-injection and unsafe-output guardrail examples.
- Define which fields may contain sensitive data and how they are encrypted, masked, retained, and deleted.

### 22.12 Testing gaps

The current unit tests and Oracle E2E smoke tests are strong for the implemented baseline. Remaining tests should target production-readiness gaps.

Recommended implementation:

- Keep `agent_execution_log`, `agent_tool_call`, and `agent_model_usage` persistence tests enabled.
- Keep the fake `AgentAiClient` app test for provider-free CI and the lower-level mock `ChatClient` adapter test for Spring AI response metadata extraction.
- Add sample-tool E2E tests that populate tool-call and audit tables.
- Completed: app-level admin evaluation E2E test runs a classpath golden dataset and verifies `agent_evaluation_result` persistence.
- Keep missing-route/admin-disabled error-mapping regression tests enabled.
- Add configuration tests for `AGENT_DB`, Oracle `ddl-auto=none`, and provider opt-in behavior.

### 22.13 Recommended implementation order

1. Done: disabled-admin missing-route error mapping now returns HTTP 404 instead of HTTP 500.
2. Done: `agent_execution_log` entity, repository, DDL, and pipeline listener persistence are implemented.
3. Done: `agent_tool_call` entity, repository, DDL, and tool execution persistence are implemented.
4. Done: `agent_model_usage` entity, repository, DDL, and Spring AI usage capture are implemented.
5. Done: added a tool-backed sample agent and read-only sample tool so tool governance is exercised end to end.
6. Done: added a Spring-AI-backed sample agent using structured output and provider-free H2 test coverage.
7. Done: classpath/file golden dataset loading and admin evaluation E2E are implemented.
8. Done: baseline runtime policy enforcement for timeout, retry, max steps, max tool calls, and dry-run tool safety is implemented; hard token/cost budget enforcement remains cost/tariff roadmap.
9. Done: OpenTelemetry observability foundation is implemented with an opt-in pipeline listener, telemetry event model, exporter port, default in-memory exporter for validation, and config/documentation. Real OTLP export remains future adapter work.
10. Done: local H2 is the default startup profile, PostgreSQL/Oracle are explicit profiles, and Oracle manual-DDL startup guidance is documented.
11. Done: Spring AI provider option pass-through for modelName, temperature, and maxOutputTokens is implemented for synchronous structured calls; provider token/model metadata is persisted when available. Dedicated streaming support remains a future API variant.
12. Done: baseline redaction-aware logging is implemented through injectable AgentRedactionService; prompt logging and tool result logging remain disabled by default and are redacted when enabled.
13. Done: baseline prompt admin and seed workflow is implemented through config-driven seeds plus admin-gated list/create/activate/deprecate/archive endpoints.
14. Done: baseline approval listing/detail APIs and expiry sweep are implemented through admin-gated endpoints backed by `AgentApprovalStore`.
15. Done: baseline tool-level approval resumability is implemented; tool approval metadata stores the paused request and `resumeApproved` executes the approved tool through `ToolExecutionService`. Full checkpointed agent continuation remains future workflow-engine work.
16. Done: reference tenant/actor authorization is implemented with permissive local mode, strict mode, `X-Agent-Permissions` context propagation, allowed tenant checks, and global/per-agent run permission checks.
17. Done: async queue provider selection is formalized with `memory` default, `database` JPA support, and reserved `kafka`/`rabbitmq`/`sqs` extension points for future production adapters or custom `AgentAsyncQueue` beans.
18. Done: OpenTelemetry observability foundation is implemented with `AgentTelemetryEvent`, `AgentTelemetryExporter`, `OpenTelemetryAgentPipelineListener`, opt-in configuration, and default bounded in-memory exporter for validation. Real OTLP export remains future adapter work.

## 23. External References

- NIST AI Risk Management Framework: Generative AI Profile, NIST AI 600-1: https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence
- ISO/IEC 42001:2023 AI management systems: https://www.iso.org/standard/42001
- OWASP Top 10 for LLM and GenAI Applications 2025: https://genai.owasp.org/llm-top-10/
- OpenTelemetry Specification: https://opentelemetry.io/docs/specs/otel/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2024-11-05/index
- Agent2Agent Protocol specification: https://google-a2a.github.io/A2A/specification/
- CloudEvents specification: https://github.com/cloudevents/spec
- OpenAPI Specification: https://spec.openapis.org/oas/

## 24. Summary

This framework is intentionally generic.

It should allow teams to create any type of agent:

- document extraction agent
- compliance review agent
- filing validation agent
- customer support agent
- code review agent
- invoice audit agent
- shipment planning agent
- incident response agent
- research assistant agent
- data quality agent

The durable architectural rule is:

```txt
Agents own domain reasoning.
The framework owns lifecycle, safety, tools, memory, observability, persistence, and governance.
```

If this standard is followed, new agents can be added quickly without sacrificing production safety, auditability, or maintainability.
