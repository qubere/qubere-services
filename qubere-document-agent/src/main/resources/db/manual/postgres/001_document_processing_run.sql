-- Qubere Document Agent manual DDL for PostgreSQL.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Generic agent-runtime tables (agent_execution_record, agent_tool_call, agent_checkpoint, etc.)
-- live in qubere-agent-storage's own manual DDL -- this file only covers tables specific to the
-- document-processing subsystem owned by this module.

create table if not exists document_processing_run (
    id varchar(64) primary key,
    idempotency_key varchar(128) not null,
    document_id varchar(64) not null,
    shipment_id varchar(64),
    content_sha256 varchar(64),
    tenant_id varchar(64) not null,
    actor_id varchar(64),
    correlation_id varchar(128),
    state varchar(32) not null,
    reason varchar(32) not null,
    profile varchar(32) not null,
    external_task_id varchar(200),
    attempt_count integer not null default 0,
    poll_attempt_count integer not null default 0,
    next_poll_at timestamp with time zone,
    next_retry_at timestamp with time zone,
    heartbeat_at timestamp with time zone,
    error_code varchar(64),
    error_message varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

-- Backs ProcessingRunService.enqueue()'s idempotent create-or-find: a duplicate submission for
-- the same (document, profile, reason) must resolve to the existing run, not a second row.
create unique index if not exists ux_document_processing_run_idempotency
    on document_processing_run (idempotency_key);

-- Backs ProcessingRunRepository.findAwaitingSubmission().
create index if not exists idx_document_processing_run_submission
    on document_processing_run (state, next_retry_at);

-- Backs ProcessingRunRepository.findAwaitingPoll().
create index if not exists idx_document_processing_run_poll
    on document_processing_run (state, next_poll_at);

-- Backs ProcessingRunRepository.findStale() (reclaim of runs with a stalled worker heartbeat).
create index if not exists idx_document_processing_run_heartbeat
    on document_processing_run (state, heartbeat_at);

create index if not exists idx_document_processing_run_document
    on document_processing_run (document_id, created_at);

create index if not exists idx_document_processing_run_shipment
    on document_processing_run (shipment_id, created_at);

create index if not exists idx_document_processing_run_tenant
    on document_processing_run (tenant_id, created_at);

-- Backs DuplicateDetectionService's cross-shipment lookup by content checksum.
create index if not exists idx_document_processing_run_checksum
    on document_processing_run (tenant_id, content_sha256, created_at);
