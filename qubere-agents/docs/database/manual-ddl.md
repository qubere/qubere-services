# Manual database DDL

The platform intentionally does not auto-create or auto-migrate database objects in deployed environments.

Run the matching script manually before starting `agent-app` with `spring.jpa.hibernate.ddl-auto=validate`.

The current manual DDL creates:

- `agent_execution_record` for execution lifecycle persistence.
- `agent_tool_audit_event` for governed tool-call audit events.
- `agent_prompt_template` for versioned prompt templates.
- `agent_approval_request` for human approval lifecycle persistence.
- `agent_pending_command` for restart-safe resume of approval-blocked executions.
- `agent_evaluation_result` for durable Phase 4 evaluation run history.

## PostgreSQL

```bash
psql "$AGENT_DATASOURCE_URL" -f agent-storage/src/main/resources/db/manual/postgres/001_agent_execution_record.sql
```

## Oracle

```bash
sqlplus agents/password@//localhost:1521/FREEPDB1 @agent-storage/src/main/resources/db/manual/oracle/001_agent_execution_record.sql
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
