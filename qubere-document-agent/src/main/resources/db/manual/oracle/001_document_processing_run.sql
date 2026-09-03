-- Qubere Document Agent manual DDL for Oracle.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Generic agent-runtime tables (agent_execution_record, agent_tool_call, agent_checkpoint, etc.)
-- live in qubere-agent-storage's own manual DDL -- this file only covers tables specific to the
-- document-processing subsystem owned by this module.

create table document_processing_run (
    id varchar2(64 char) not null,
    idempotency_key varchar2(128 char) not null,
    document_id varchar2(64 char) not null,
    shipment_id varchar2(64 char),
    content_sha256 varchar2(64 char),
    tenant_id varchar2(64 char) not null,
    actor_id varchar2(64 char),
    correlation_id varchar2(128 char),
    state varchar2(32 char) not null,
    reason varchar2(32 char) not null,
    profile varchar2(32 char) not null,
    external_task_id varchar2(200 char),
    attempt_count number(10) default 0 not null,
    poll_attempt_count number(10) default 0 not null,
    next_poll_at timestamp with time zone,
    next_retry_at timestamp with time zone,
    heartbeat_at timestamp with time zone,
    error_code varchar2(64 char),
    error_message varchar2(1000 char),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version number(19) default 0 not null,
    constraint pk_document_processing_run primary key (id)
);

-- Backs ProcessingRunService.enqueue()'s idempotent create-or-find: a duplicate submission for
-- the same (document, profile, reason) must resolve to the existing run, not a second row.
create unique index ux_document_processing_run_idempotency
    on document_processing_run (idempotency_key);

-- Backs ProcessingRunRepository.findAwaitingSubmission().
create index idx_document_processing_run_submission
    on document_processing_run (state, next_retry_at);

-- Backs ProcessingRunRepository.findAwaitingPoll().
create index idx_document_processing_run_poll
    on document_processing_run (state, next_poll_at);

-- Backs ProcessingRunRepository.findStale() (reclaim of runs with a stalled worker heartbeat).
create index idx_document_processing_run_heartbeat
    on document_processing_run (state, heartbeat_at);

create index idx_document_processing_run_document
    on document_processing_run (document_id, created_at);

create index idx_document_processing_run_shipment
    on document_processing_run (shipment_id, created_at);

create index idx_document_processing_run_tenant
    on document_processing_run (tenant_id, created_at);

-- Backs DuplicateDetectionService's cross-shipment lookup by content checksum.
create index idx_document_processing_run_checksum
    on document_processing_run (tenant_id, content_sha256, created_at);
