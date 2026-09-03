# Manual database DDL

The platform intentionally does not auto-create or auto-migrate database objects in deployed environments.

Run the matching script manually before starting `qubere-echo-agent` or `qubere-document-agent` with `spring.jpa.hibernate.ddl-auto=validate`.

Generic agent-runtime tables (`qubere-agent-storage`):

- `agent_execution_record` for execution lifecycle persistence.
- `agent_tool_audit_event` for governed tool-call audit events.
- `agent_prompt_template` for versioned prompt templates.
- `agent_approval_request` for human approval lifecycle persistence.
- `agent_pending_command` for restart-safe resume of approval-blocked executions.
- `agent_async_queue_command` for optional JPA-backed async queue commands when `agent-platform.async.queue.type=database` is enabled.
- `agent_evaluation_result` for durable Phase 4 evaluation run history.

Document-agent-specific tables (`qubere-document-agent`, owned by that module, not `qubere-agent-storage`):

- `document_processing_run` for the document-parsing state machine (`ProcessingRunService`/`DocumentProcessingWorker`): idempotent enqueue, submit/poll/complete tracking, retry backoff, and stale-run reclaim. Also carries `shipment_id` (correlation) and `content_sha256` (backs `DuplicateDetectionService`'s cross-shipment lookup).
- `document_parse_result` for the current active normalized parse result per document (`DocumentParseResultService`), keyed by `document_id` -- backs the extraction context builder handed to `document.intelligence`.
- `unassigned_document_intake` for document intakes whose target shipment could not be determined (`UnassignedIntakeRecorder`) -- never auto-assigned to a guessed shipment.
- `extraction_field` for every stored reading of every extracted field (`ExtractionReviewService`), append-only -- a human correction is a new row, never an overwrite of the machine's original reading.

## PostgreSQL

```bash
psql "$AGENT_DATASOURCE_URL" -f qubere-agent-storage/src/main/resources/db/manual/postgres/001_agent_execution_record.sql
psql "$AGENT_DATASOURCE_URL" -f qubere-document-agent/src/main/resources/db/manual/postgres/001_document_processing_run.sql
psql "$AGENT_DATASOURCE_URL" -f qubere-document-agent/src/main/resources/db/manual/postgres/002_document_parse_result.sql
psql "$AGENT_DATASOURCE_URL" -f qubere-document-agent/src/main/resources/db/manual/postgres/003_unassigned_document_intake.sql
psql "$AGENT_DATASOURCE_URL" -f qubere-document-agent/src/main/resources/db/manual/postgres/004_extraction_field.sql
```

## Oracle

```bash
sqlplus agents/password@//localhost:1521/FREEPDB1 @qubere-agent-storage/src/main/resources/db/manual/oracle/001_agent_execution_record.sql
sqlplus agents/password@//localhost:1521/FREEPDB1 @qubere-document-agent/src/main/resources/db/manual/oracle/001_document_processing_run.sql
sqlplus agents/password@//localhost:1521/FREEPDB1 @qubere-document-agent/src/main/resources/db/manual/oracle/002_document_parse_result.sql
sqlplus agents/password@//localhost:1521/FREEPDB1 @qubere-document-agent/src/main/resources/db/manual/oracle/003_unassigned_document_intake.sql
sqlplus agents/password@//localhost:1521/FREEPDB1 @qubere-document-agent/src/main/resources/db/manual/oracle/004_extraction_field.sql
```

## Runtime setting

The reference app defaults to validation only:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

For quick local experiments only, you can override with:

```bash
AGENT_JPA_DDL_AUTO=update
```
