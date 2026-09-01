# Agent App End-to-End Testing Guide

`agent-app` is the runnable reference host for validating the generic agent framework. It is intentionally small: one sample agent, REST endpoints, configuration-driven runtime behavior, async approval, durable persistence, governance, evaluation, replay, and secured admin operations.

This guide describes the scenarios that should be tested before building the next phase.

Important security rule: never commit real database passwords, admin tokens, provider API keys, or customer data into this repository. Use environment variables or your local shell profile.

## 1. What `agent-app` validates

| Area | Scenario | Expected result |
| --- | --- | --- |
| Agent registry | List registered agents | `generic.echo-analysis` is returned |
| Sync runtime | Run sample agent synchronously | Execution succeeds and persists an execution record |
| Tool-backed runtime | Run tool-backed sample agent | Execution succeeds and persists tool call/audit rows |
| AI-backed runtime | Run Spring-AI-backed sample agent | Execution succeeds with structured output and persists model usage rows |
| Policy resolution | Override runtime options | Agent output shows resolved policy values |
| Version routing | Request `agentVersion=0.1.0` | Correct agent version executes |
| Durable persistence | Query run by execution ID | Execution record is returned after run |
| Async execution | Submit async run | Run is queued or waits for approval |
| Human approval | Submit approval-required async run | Run returns `WAITING_FOR_APPROVAL` with `approvalId` |
| Approval resume | Approve pending run and process queue | Run completes and execution record becomes `SUCCEEDED` |
| Approval rejection | Reject pending run | Execution record becomes cancelled/terminal |
| Governance | Configure actor/tenant rate limits | Excess runs fail with governance limit error |
| Admin API security | Call admin API without token | Request is denied |
| Admin observability | Call admin API with token | Summary/events are returned |
| Evaluation | Run golden dataset evaluation | Result is persisted in `agent_evaluation_result` |
| Replay | Replay existing execution through admin API | New replay execution is created |
| Local profile | Run app with default H2 profile | App starts without external database setup |
| Oracle profile | Run against Oracle with manual DDL | App starts with `AGENT_DB=oracle` and persists records |

## 2. Local H2 test suite

Use this before testing against Oracle. It verifies the framework and H2-compatible persistence mappings without external infrastructure.

```bash
mvn test
```

Package without tests:

```bash
mvn -DskipTests package
```

## 3. Start `agent-app` locally with H2

The default runtime profile is `local`, backed by H2. No PostgreSQL or Oracle setup is required for a quick local smoke test.

From the project root:

```powershell
mvn -pl agent-app spring-boot:run
```

Equivalent explicit environment variable:

```powershell
$env:AGENT_DB = "local"
```

In STS, use the `agent-app` Spring Boot run configuration and either leave `AGENT_DB` unset or set it to `local`.

Health check:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
```

Expected:

```json
{"status":"UP"}
```

## 4. Oracle setup

The Oracle profile is already configured in `agent-app/src/main/resources/application-oracle.yml`.

Use environment variables. Do not edit `application-oracle.yml` with real credentials.

PowerShell example:

```powershell
$env:AGENT_DB = "oracle"
$env:AGENT_DATASOURCE_URL = "<oracle-jdbc-url>"
$env:AGENT_DATASOURCE_USERNAME = "<oracle-username>"
$env:AGENT_DATASOURCE_PASSWORD = "<oracle-password>"
$env:AGENT_JPA_DDL_AUTO = "none"
$env:SPRING_AI_MODEL_CHAT = "none"
$env:AGENT_SPRING_AI_ENABLED = "false"
```

For the development Oracle database provided outside this file:

- set `AGENT_DATASOURCE_URL` to the provided Oracle JDBC URL
- set `AGENT_DATASOURCE_USERNAME` to the provided Oracle username
- set `AGENT_DATASOURCE_PASSWORD` locally to the provided Oracle password

Do not commit these values.

Use `AGENT_JPA_DDL_AUTO=none` after manually running the DDL scripts. Hibernate `validate` can be slow against Oracle metadata and may delay startup before port `8080` opens.

## 5. Manual DDL

Before starting the app with Oracle, execute:

```text
agent-storage/src/main/resources/db/manual/oracle/001_agent_execution_record.sql
```

The script creates:

- `agent_execution_record`
- `agent_execution_log`
- `agent_tool_call`
- `agent_model_usage`
- `agent_tool_audit_event`
- `agent_prompt_template`
- `agent_approval_request`
- `agent_pending_command`
- `agent_async_queue_command` when `AGENT_PLATFORM_ASYNC_QUEUE_TYPE=database` is enabled
- `agent_evaluation_result`

If the tables already exist, do not rerun the script as-is because Oracle does not support `create table if not exists`.

## 6. Start `agent-app` with Oracle

From the project root:

```bash
mvn -pl agent-app spring-boot:run -Poracle
```

The app starts on:

```text
http://localhost:8080
```

Health check:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
```

Expected:

```json
{"status":"UP"}
```

## 7. Scenario 1: list agents

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/agents"
```

Expected:

- response contains `generic.echo-analysis`
- version is `0.1.0`
- risk level is `LOW`

## 8. Scenario 2: synchronous run

PowerShell native version:

```powershell
$body = @{
  agentVersion = "0.1.0"
  input = @{
    message = "hello from sync test"
  }
  options = @{
    mode = "RECOMMEND"
    maxSteps = 4
    timeoutSeconds = 30
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.echo-analysis/runs" `
  -Headers @{
    "X-Tenant-Id" = "tenant-test-1"
    "X-Actor-Id" = "actor-test-1"
    "X-Correlation-Id" = "corr-sync-1"
  } `
  -ContentType "application/json" `
  -Body $body
```




Expected:

- HTTP 200
- `status` is `SUCCEEDED`
- response includes an `executionId`
- output contains the echoed input
- output contains resolved policy values

Save the `executionId` for later scenarios.

## 8. Scenario 3: get execution record

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/agents/runs/<executionId>"
```

Expected:

- HTTP 200
- `status` is `SUCCEEDED`
- `tenantId` is `tenant-test-1`
- `actorId` is `actor-test-1`
- input/output JSON are persisted

Oracle verification:

```sql
select execution_id, agent_id, agent_version, tenant_id, actor_id, status, created_at, updated_at
from agent_execution_record
order by created_at desc;

select execution_id, agent_id, agent_version, tenant_id, actor_id, correlation_id, step, log_level, message, occurred_at
from agent_execution_log
where execution_id = '<executionId>'
order by occurred_at asc;
```

## 9. Scenario 4: unknown agent

```powershell
$body = @{
  input = @{
    message = "should fail"
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/unknown.agent/runs" `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- HTTP 404
- error code is `AGENT_NOT_FOUND`


## OpenTelemetry observability foundation smoke test

The OpenTelemetry adapter foundation is disabled by default. To verify wiring locally, restart the app with:

```powershell
$env:AGENT_OTEL_ENABLED = "true"
$env:AGENT_OTEL_SERVICE_NAME = "qubere-agents-local"
$env:AGENT_OTEL_INCLUDE_ACTOR = "true"
```

Then run any synchronous or async scenario. Expected behavior:

- application startup succeeds
- normal pipeline events continue to be persisted in `agent_execution_log`
- OpenTelemetry-shaped pipeline events are exported through the framework `AgentTelemetryExporter` extension point
- the current built-in exporter is bounded in-memory for adapter validation only; real OTLP export remains a production adapter step

## Strict authorization smoke test

The app is permissive by default for local development. To test reference authorization, restart with strict mode:

```powershell
$env:AGENT_AUTHORIZATION_MODE = "strict"
$env:AGENT_PLATFORM_SECURITY_REQUIRED_RUN_PERMISSIONS = "agents.run"
```

Missing tenant/actor headers should be rejected:

```powershell
$body = @{
  agentVersion = "0.1.0"
  input = @{
    message = "auth test"
  }
} | ConvertTo-Json -Depth 5

try {
  Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/agents/generic.echo-analysis/runs" `
    -ContentType "application/json" `
    -Body $body
} catch {
  $_.ErrorDetails.Message
}
```

Expected: HTTP 403 with code `AUTHORIZATION_DENIED`.

With tenant, actor, and required permission, the same request should succeed:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.echo-analysis/runs" `
  -Headers @{
    "X-Tenant-Id" = "tenant-test-1"
    "X-Actor-Id" = "actor-test-1"
    "X-Agent-Permissions" = "agents.run"
  } `
  -ContentType "application/json" `
  -Body $body
```

Expected: HTTP 200 and `status=SUCCEEDED`.

## 10. Scenario 5: tool-backed sample agent

This validates the framework tool execution path without requiring an external system. The sample agent calls the read-only `echo.lookup` tool through `ToolExecutionService`.

```powershell
$body = @{
  agentVersion = "0.1.0"
  input = @{
    message = "Tool Test"
  }
  options = @{
    allowedTools = @("echo.lookup")
    maxToolCalls = 3
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.tool-echo-analysis/runs" `
  -Headers @{
    "X-Tenant-Id" = "tenant-test-1"
    "X-Actor-Id" = "actor-test-1"
    "X-Correlation-Id" = "corr-tool-1"
  } `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- HTTP 200
- `status` is `SUCCEEDED`
- response includes `tool.name = echo.lookup`
- response includes `tool.success = true`
- response includes `tool.result.normalizedMessage = tool test`
- one row is written to `agent_tool_call`; by default persisted `output_summary` is `Tool result logging disabled` unless `AGENT_LOG_TOOL_RESULTS=true`
- `STARTED` and `SUCCEEDED` rows are written to `agent_tool_audit_event`

Oracle verification:

```sql
select call_id, execution_id, tool_name, tool_risk_level, side_effects, status, output_summary, latency_ms
from agent_tool_call
where execution_id = '<executionId>'
order by started_at asc;

select execution_id, tool_name, status, message, occurred_at
from agent_tool_audit_event
where execution_id = '<executionId>'
order by occurred_at asc;
```

### Scenario 5a: tool-level approval resume

This validates baseline tool approval resumability. The request asks the tool-backed sample agent to require human approval before tool execution. The initial call should pause before executing the tool and return `TOOL_APPROVAL_REQUIRED` with an `approvalId` in the error message. Approving that id executes the stored approved tool request and marks the execution `SUCCEEDED`.

```powershell
$body = @{
  agentVersion = "0.1.0"
  input = @{
    message = "Tool approval test"
  }
  options = @{
    allowedTools = @("echo.lookup")
    allowToolCalls = $true
    requireHumanApproval = $true
    logToolResults = $true
  }
} | ConvertTo-Json -Depth 5

try {
  Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/agents/generic.tool-echo-analysis/runs" `
    -Headers @{
      "X-Tenant-Id" = "tenant-test-1"
      "X-Actor-Id" = "actor-test-1"
      "X-Correlation-Id" = "corr-tool-approval-1"
    } `
    -ContentType "application/json" `
    -Body $body
} catch {
  $_.ErrorDetails.Message
}
```

Copy the `approvalId` from the error message, then approve it:

```powershell
$approvalBody = @{
  decidedBy = "test-approver"
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/approvals/<approvalId>/approve" `
  -ContentType "application/json" `
  -Body $approvalBody
```

Expected:

- initial response is HTTP 403 with code `TOOL_APPROVAL_REQUIRED`
- one approval row has metadata `approvalType=TOOL_EXECUTION`
- approval response status is `SUCCEEDED`
- `agent_tool_call` contains the original `APPROVAL_REQUIRED` row and approved execution `STARTED` / `SUCCEEDED` rows linked to the same approval id

## 11. Scenario 6: AI-backed sample agent

This validates the framework AI abstraction path. The sample agent `generic.ai-analysis` calls `AgentAiClient` and expects structured output.

For local automated tests, this path is covered with a fake `AgentAiClient`, so no provider key is required. For manual testing against a real OpenAI-compatible Spring AI provider, start the app with the optional AI profile and provider settings.

PowerShell startup example:

```powershell
$env:AGENT_SPRING_AI_ENABLED = "true"
$env:SPRING_AI_MODEL_CHAT = "openai"
$env:SPRING_AI_OPENAI_API_KEY = "<local-api-key>"
$env:AGENT_AI_PROVIDER = "openai"
$env:AGENT_AI_MODEL = "gpt-4.1-mini"

mvn -pl agent-app spring-boot:run -Pai-openai,oracle
```

Request:

```powershell
$body = @{
  agentVersion = "0.1.0"
  input = @{
    message = "Please analyze this diagnostic test message"
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.ai-analysis/runs" `
  -Headers @{
    "X-Tenant-Id" = "tenant-test-1"
    "X-Actor-Id" = "actor-test-1"
    "X-Correlation-Id" = "corr-ai-1"
  } `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- HTTP 200 when a real `AgentAiClient` is configured
- `status` is `SUCCEEDED`
- output contains structured `analysis.summary`, `analysis.sentiment`, `analysis.recommendedAction`, and `analysis.confidence`
- one row is written to `agent_model_usage`
- `agent_model_usage.status` is `SUCCEEDED`

Oracle verification:

```sql
select usage_id, execution_id, agent_id, agent_version, model_provider, model_name, prompt_version, status,
       input_tokens, output_tokens, total_tokens, latency_ms, started_at, completed_at
from agent_model_usage
where execution_id = '<executionId>'
order by started_at asc;
```

If no AI provider is configured and this agent is called, the expected failure is HTTP 503 with error code `AI_PROVIDER_UNAVAILABLE`.
## 12. Scenario 7: async run without approval

```powershell
$body = @{
  agentVersion = "0.1.0"
  async = $true
  input = @{
    message = "hello from async test"
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.echo-analysis/runs" `
  -Headers @{
    "X-Tenant-Id" = "tenant-test-1"
    "X-Actor-Id" = "actor-test-1"
  } `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- response status is `QUEUED`
- response includes `executionId`

Process the queue:

By default this uses the in-memory queue: `AGENT_PLATFORM_ASYNC_QUEUE_TYPE=memory`. To test the durable database queue instead, start the app with `AGENT_PLATFORM_ASYNC_QUEUE_TYPE=database` after adding the `agent_async_queue_command` table from the manual DDL. `kafka`, `rabbitmq`, and `sqs` are reserved for future production adapters or application-provided `AgentAsyncQueue` beans; they are not runnable in the reference app yet.

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/agents/async/process-next"
```

Expected:

- HTTP 202 if a queued item was processed
- execution record becomes `SUCCEEDED`


### Scenario 7a: retry async submit with an idempotency key

Use the same `X-Idempotency-Key` with the same tenant when retrying an async submission. The second response should return the same `executionId` instead of creating another run.

```powershell
$headers = @{
  "X-Tenant-Id" = "tenant-test-1"
  "X-Actor-Id" = "actor-test-1"
  "X-Idempotency-Key" = "idem-async-echo-1"
}

$body = @{
  agentVersion = "0.1.0"
  async = $true
  input = @{
    message = "hello from idempotent async test"
  }
} | ConvertTo-Json -Depth 5

$first = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.echo-analysis/runs" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body

$retry = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.echo-analysis/runs" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body

$first.executionId
$retry.executionId
```

Expected:

- both printed execution ids are the same
- only one execution record is created for that tenant/idempotency key
## 13. Scenario 8: async run with human approval

```powershell
$body = @{
  agentVersion = "0.1.0"
  async = $true
  input = @{
    message = "approval required test"
  }
  options = @{
    requireHumanApproval = $true
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/generic.echo-analysis/runs" `
  -Headers @{
    "X-Tenant-Id" = "tenant-test-1"
    "X-Actor-Id" = "actor-test-1"
  } `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- response status is `WAITING_FOR_APPROVAL`
- response includes `approvalId`
- execution record status is `WAITING_FOR_APPROVAL`
- row exists in `agent_approval_request`

Oracle verification:

```sql
select approval_id, execution_id, agent_id, tenant_id, requested_by, status, created_at, expires_at
from agent_approval_request
order by created_at desc;
```

Approve it:

```powershell
$body = @{
  decidedBy = "test-approver"
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/approvals/<approvalId>/approve" `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- response status is `QUEUED`

Process queue:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/agents/async/process-next"
```

Expected:

- final execution status is `SUCCEEDED`

## 14. Scenario 9: approval rejection

Submit another approval-required async run, then reject:

```powershell
$body = @{
  decidedBy = "test-approver"
  reason = "negative path test"
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/approvals/<approvalId>/reject" `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- approval status becomes `REJECTED`
- execution enters a terminal rejected/cancelled state
- pending command is not processed


## 15. Scenario 10: admin approval listing, detail, and expiry

Enable the admin API before starting the app:

```powershell
$env:AGENT_ADMIN_ENABLED = "true"
$env:AGENT_ADMIN_TOKEN = "<local-admin-token>"
```

List pending approvals:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/agents/admin/approvals?status=PENDING&tenantId=tenant-test-1&limit=25" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Get one approval by id:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/agents/admin/approvals/<approvalId>" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Expire stale approvals:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/admin/approvals/expire" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Expected:

- list returns matching approvals ordered newest first
- detail returns the requested approval record
- expiry returns `expiredCount`
- pending approvals whose `expires_at` is in the past move to `EXPIRED`

Oracle table verification:

```sql
select approval_id, execution_id, agent_id, tenant_id, requested_by, status, created_at, expires_at, decided_at, decided_by
from agent_approval_request
order by created_at desc;
```

## 16. Scenario 11: governance rate limit

Restart the app with a strict actor rate limit:

```powershell
$env:AGENT_MAX_RUNS_PER_ACTOR_PER_MINUTE = "1"
```

Start app and run the sync request twice quickly with the same `X-Actor-Id`.

Expected:

- first run succeeds
- second run is blocked with governance/rate-limit error
- no unsafe side effects occur

Reset after test:

```powershell
Remove-Item Env:\AGENT_MAX_RUNS_PER_ACTOR_PER_MINUTE
```

## 17. Scenario 12: admin API is disabled by default

With default config:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/agents/admin/observability/summary"
```

Expected:

- HTTP 404 because admin endpoints are not registered

## 18. Scenario 13: admin API token enforcement

Restart with admin enabled:

```powershell
$env:AGENT_ADMIN_ENABLED = "true"
$env:AGENT_ADMIN_TOKEN = "<local-admin-token>"
```

Without token:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/agents/admin/observability/summary"
```

Expected:

- HTTP 403
- error code `AUTHORIZATION_DENIED`

With token:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/agents/admin/observability/summary" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Expected:

- HTTP 200
- response includes `observedEvents`, `eventsByStep`, `eventsByAgent`, and `generatedAt`

## 18. Scenario 13: admin observability events

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/agents/admin/observability/events?limit=20" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Expected:

- HTTP 200
- list contains recent lifecycle events after running agent scenarios
- limit is bounded server-side

## 19. Scenario 14: replay existing execution

Use an existing succeeded `executionId`:

```powershell
$body = @{
  sourceExecutionId = "<executionId>"
  inputOverride = @{
    message = "replay override"
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/admin/runs/replay" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  } `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- HTTP 200
- new execution is created with correlation id starting with `replay-of-`
- replay should be admin-only because it can re-trigger agent behavior

Oracle verification:

```sql
select execution_id, agent_id, status, input_json, output_json, created_at
from agent_execution_record
where input_json like '%replay%'
order by created_at desc;
```

## 20. Scenario 15: evaluation result persistence

The app loads golden datasets from `classpath*:agent-evaluation/*.json` by default. The reference app includes `generic-echo-smoke`, and you can add more JSON datasets under `src/main/resources/agent-evaluation/` or point to a file with `AGENT_EVALUATION_DATASET_LOCATION`.

Restart with admin enabled:

```powershell
$env:AGENT_ADMIN_ENABLED = "true"
$env:AGENT_ADMIN_TOKEN = "<local-admin-token>"
```

Run the classpath evaluation dataset:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/admin/evaluations/generic-echo-smoke/run" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Expected:

- HTTP 200
- `datasetName` is `generic-echo-smoke`
- `total` is `1`
- `passed` is `1`
- one row is inserted into `agent_evaluation_result`

Oracle table verification:

```sql
select evaluation_id, dataset_name, status, total_count, passed_count, failed_count, evaluated_at
from agent_evaluation_result
order by evaluated_at desc;
```


## 21. Scenario 16: prompt admin and seed workflow

Enable the admin API before starting the app:

```powershell
$env:AGENT_ADMIN_ENABLED = "true"
$env:AGENT_ADMIN_TOKEN = "<local-admin-token>"
```

List seeded prompt versions for the echo agent:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/agents/admin/prompts/agents/generic.echo-analysis" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Create a draft prompt version:

```powershell
$body = @{
  promptId = "generic.echo-analysis.default"
  agentId = "generic.echo-analysis"
  version = "0.2.0"
  status = "DRAFT"
  systemTemplate = "Reference echo-analysis prompt v0.2.0."
  userTemplate = "Analyze and echo this message: {{message}}"
  metadata = @{
    owner = "agent-team"
    source = "manual-test"
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/admin/prompts" `
  -ContentType "application/json" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  } `
  -Body $body
```

Activate the new version:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agents/admin/prompts/generic.echo-analysis.default/versions/0.2.0/activate" `
  -Headers @{
    "X-Agent-Admin-Token" = "<local-admin-token>"
  }
```

Expected:

- seeded prompt rows exist after startup when `AGENT_PROMPT_SEED_ENABLED=true`
- create returns HTTP 200 with status `DRAFT`
- activate returns HTTP 200 with status `ACTIVE`
- any previous active prompt for the same agent is moved to `DEPRECATED`

Oracle table verification:

```sql
select prompt_id, version, agent_id, status, created_at, updated_at
from agent_prompt_template
where agent_id = 'generic.echo-analysis'
order by version;
```
## 22. Scenario 17: database persistence smoke checklist

After running the scenarios, verify these tables:

```sql
select count(*) from agent_execution_record;
select count(*) from agent_execution_log;
select count(*) from agent_approval_request;
select count(*) from agent_pending_command;
select count(*) from agent_async_queue_command;
select count(*) from agent_tool_call;
select count(*) from agent_model_usage;
select count(*) from agent_tool_audit_event;
select count(*) from agent_prompt_template;
select count(*) from agent_evaluation_result;
```

Expected:

- `agent_execution_record` count increases after sync/async/replay runs
- `agent_execution_log` count increases with lifecycle rows for each executed run
- `agent_tool_call` count increases after running `generic.tool-echo-analysis`; tool-level approval resume adds `APPROVAL_REQUIRED`, `STARTED`, and `SUCCEEDED` rows; it is also covered by H2 persistence tests
- `agent_model_usage` count increases after running `generic.ai-analysis` with an AI provider configured; it is also covered by H2 persistence tests
- `agent_approval_request` count increases after approval-required runs
- `agent_pending_command` temporarily increases for approval-blocked async work
- `agent_async_queue_command` temporarily increases for queued async work only when `AGENT_PLATFORM_ASYNC_QUEUE_TYPE=database` is enabled
- `agent_evaluation_result` is populated by evaluation tests or future dataset endpoint runs

## 23. Recommended manual test order

Run in this order:

1. `mvn test`
2. execute Oracle DDL manually
3. start app with Oracle env vars
4. health check
5. list agents
6. sync run
7. get execution record
8. unknown agent negative test
9. tool-backed sample agent
10. AI-backed sample agent, if provider is configured
11. async run without approval
12. async approval-required run
13. approve and process
14. reject path
15. admin approval listing/detail/expiry
16. governance rate limit
17. admin disabled check
18. admin token enforcement
19. admin observability
19. prompt admin and seed workflow
19. replay
20. Oracle SQL verification

## 23. Cleanup

Unset local secrets after testing.

PowerShell:

```powershell
Remove-Item Env:\AGENT_DB -ErrorAction SilentlyContinue
Remove-Item Env:\AGENT_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:\AGENT_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:\AGENT_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:\AGENT_ADMIN_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:\AGENT_ADMIN_TOKEN -ErrorAction SilentlyContinue
Remove-Item Env:\AGENT_MAX_RUNS_PER_ACTOR_PER_MINUTE -ErrorAction SilentlyContinue
```
