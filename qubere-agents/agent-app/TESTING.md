# Agent App End-to-End Testing Guide

`agent-app` is the runnable reference host for validating the generic agent framework. It is intentionally small: one sample agent, REST endpoints, configuration-driven runtime behavior, async approval, durable persistence, governance, evaluation, replay, and secured admin operations.

This guide describes the scenarios that should be tested before building the next phase.

Important security rule: never commit real database passwords, admin tokens, provider API keys, or customer data into this repository. Use environment variables or your local shell profile.

## 1. What `agent-app` validates

| Area | Scenario | Expected result |
| --- | --- | --- |
| Agent registry | List registered agents | `generic.echo-analysis` is returned |
| Sync runtime | Run sample agent synchronously | Execution succeeds and persists an execution record |
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

## 3. Oracle setup

The Oracle profile is already configured in `agent-app/src/main/resources/application-oracle.yml`.

Use environment variables. Do not edit `application-oracle.yml` with real credentials.

PowerShell example:

```powershell
$env:AGENT_DB = "oracle"
$env:AGENT_DATASOURCE_URL = "<oracle-jdbc-url>"
$env:AGENT_DATASOURCE_USERNAME = "<oracle-username>"
$env:AGENT_DATASOURCE_PASSWORD = "<oracle-password>"
$env:AGENT_JPA_DDL_AUTO = "validate"
$env:SPRING_AI_MODEL_CHAT = "none"
$env:AGENT_SPRING_AI_ENABLED = "false"
```

For the development Oracle database provided outside this file:

- set `AGENT_DATASOURCE_URL` to the provided Oracle JDBC URL
- set `AGENT_DATASOURCE_USERNAME` to the provided Oracle username
- set `AGENT_DATASOURCE_PASSWORD` locally to the provided Oracle password

Do not commit these values.

## 4. Manual DDL

Before starting the app with `AGENT_JPA_DDL_AUTO=validate`, execute:

```text
agent-storage/src/main/resources/db/manual/oracle/001_agent_execution_record.sql
```

The script creates:

- `agent_execution_record`
- `agent_tool_audit_event`
- `agent_prompt_template`
- `agent_approval_request`
- `agent_pending_command`
- `agent_evaluation_result`

If the tables already exist, do not rerun the script as-is because Oracle does not support `create table if not exists`.

## 5. Start `agent-app` with Oracle

From the project root:

```bash
mvn -pl agent-app -am spring-boot:run -Poracle
```

The app starts on:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected:

```json
{"status":"UP"}
```

## 6. Scenario 1: list agents

```bash
curl http://localhost:8080/api/agents
```

Expected:

- response contains `generic.echo-analysis`
- version is `0.1.0`
- risk level is `LOW`

## 7. Scenario 2: synchronous run

```bash
curl -X POST http://localhost:8080/api/agents/generic.echo-analysis/runs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-test-1" \
  -H "X-Actor-Id: actor-test-1" \
  -H "X-Correlation-Id: corr-sync-1" \
  -d '{
    "agentVersion": "0.1.0",
    "input": {
      "message": "hello from sync test"
    },
    "options": {
      "mode": "RECOMMEND",
      "maxSteps": 4,
      "timeoutSeconds": 30
    }
  }'
```

Expected:

- HTTP 200
- `status` is `SUCCEEDED`
- response includes an `executionId`
- output contains the echoed input
- output contains resolved policy values

Save the `executionId` for later scenarios.

## 8. Scenario 3: get execution record

```bash
curl http://localhost:8080/api/agents/runs/<executionId>
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
```

## 9. Scenario 4: unknown agent

```bash
curl -X POST http://localhost:8080/api/agents/unknown.agent/runs \
  -H "Content-Type: application/json" \
  -d '{"input":{"message":"should fail"}}'
```

Expected:

- HTTP 404
- error code is `AGENT_NOT_FOUND`

## 10. Scenario 5: async run without approval

```bash
curl -X POST http://localhost:8080/api/agents/generic.echo-analysis/runs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-test-1" \
  -H "X-Actor-Id: actor-test-1" \
  -d '{
    "agentVersion": "0.1.0",
    "async": true,
    "input": {
      "message": "hello from async test"
    }
  }'
```

Expected:

- response status is `QUEUED`
- response includes `executionId`

Process the queue:

```bash
curl -X POST http://localhost:8080/api/agents/async/process-next
```

Expected:

- HTTP 202 if a queued item was processed
- execution record becomes `SUCCEEDED`

## 11. Scenario 6: async run with human approval

```bash
curl -X POST http://localhost:8080/api/agents/generic.echo-analysis/runs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-test-1" \
  -H "X-Actor-Id: actor-test-1" \
  -d '{
    "agentVersion": "0.1.0",
    "async": true,
    "input": {
      "message": "approval required test"
    },
    "options": {
      "requireHumanApproval": true
    }
  }'
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

```bash
curl -X POST http://localhost:8080/api/agents/approvals/<approvalId>/approve \
  -H "Content-Type: application/json" \
  -d '{"decidedBy":"test-approver"}'
```

Expected:

- response status is `QUEUED`

Process queue:

```bash
curl -X POST http://localhost:8080/api/agents/async/process-next
```

Expected:

- final execution status is `SUCCEEDED`

## 12. Scenario 7: approval rejection

Submit another approval-required async run, then reject:

```bash
curl -X POST http://localhost:8080/api/agents/approvals/<approvalId>/reject \
  -H "Content-Type: application/json" \
  -d '{"decidedBy":"test-approver","reason":"negative path test"}'
```

Expected:

- approval status becomes `REJECTED`
- execution enters a terminal rejected/cancelled state
- pending command is not processed

## 13. Scenario 8: governance rate limit

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

## 14. Scenario 9: admin API is disabled by default

With default config:

```bash
curl http://localhost:8080/api/agents/admin/observability/summary
```

Expected:

- HTTP 404 because admin endpoints are not registered

## 15. Scenario 10: admin API token enforcement

Restart with admin enabled:

```powershell
$env:AGENT_ADMIN_ENABLED = "true"
$env:AGENT_ADMIN_TOKEN = "<local-admin-token>"
```

Without token:

```bash
curl http://localhost:8080/api/agents/admin/observability/summary
```

Expected:

- HTTP 403
- error code `AUTHORIZATION_DENIED`

With token:

```bash
curl http://localhost:8080/api/agents/admin/observability/summary \
  -H "X-Agent-Admin-Token: <local-admin-token>"
```

Expected:

- HTTP 200
- response includes `observedEvents`, `eventsByStep`, `eventsByAgent`, and `generatedAt`

## 16. Scenario 11: admin observability events

```bash
curl "http://localhost:8080/api/agents/admin/observability/events?limit=20" \
  -H "X-Agent-Admin-Token: <local-admin-token>"
```

Expected:

- HTTP 200
- list contains recent lifecycle events after running agent scenarios
- limit is bounded server-side

## 17. Scenario 12: replay existing execution

Use an existing succeeded `executionId`:

```bash
curl -X POST http://localhost:8080/api/agents/admin/runs/replay \
  -H "Content-Type: application/json" \
  -H "X-Agent-Admin-Token: <local-admin-token>" \
  -d '{
    "sourceExecutionId": "<executionId>",
    "inputOverride": {
      "message": "replay override"
    }
  }'
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

## 18. Scenario 13: evaluation result persistence

The admin endpoint can run evaluations only when a dataset exists in `GoldenDatasetRepository`. The current default repository is in-memory, so production-grade dataset loading is a future extension point.

For now, evaluation persistence is covered by H2 tests:

```bash
mvn -pl agent-storage -am test
```

Expected:

- `AgentPersistenceH2Test` persists a result into `agent_evaluation_result`
- `cases_json` contains the evaluated case details

Oracle table verification after a custom evaluation runner is added:

```sql
select evaluation_id, dataset_name, status, total_count, passed_count, failed_count, evaluated_at
from agent_evaluation_result
order by evaluated_at desc;
```

## 19. Scenario 14: database persistence smoke checklist

After running the scenarios, verify these tables:

```sql
select count(*) from agent_execution_record;
select count(*) from agent_approval_request;
select count(*) from agent_pending_command;
select count(*) from agent_tool_audit_event;
select count(*) from agent_prompt_template;
select count(*) from agent_evaluation_result;
```

Expected:

- `agent_execution_record` count increases after sync/async/replay runs
- `agent_approval_request` count increases after approval-required runs
- `agent_pending_command` temporarily increases for queued/waiting async work
- `agent_evaluation_result` is populated by evaluation tests or future dataset endpoint runs

## 20. Recommended manual test order

Run in this order:

1. `mvn test`
2. execute Oracle DDL manually
3. start app with Oracle env vars
4. health check
5. list agents
6. sync run
7. get execution record
8. unknown agent negative test
9. async run without approval
10. async approval-required run
11. approve and process
12. reject path
13. governance rate limit
14. admin disabled check
15. admin token enforcement
16. admin observability
17. replay
18. Oracle SQL verification

## 21. Cleanup

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
