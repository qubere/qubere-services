-- Qubere Agent Platform Phase 1 manual DDL for PostgreSQL.
-- Execute manually before running agent-app with spring.jpa.hibernate.ddl-auto=validate.

create table if not exists agent_execution_record (
    execution_id varchar(64) primary key,
    agent_id varchar(128) not null,
    agent_version varchar(64) not null,
    tenant_id varchar(128),
    actor_id varchar(128),
    status varchar(32) not null,
    input_json text,
    output_json text,
    error_message text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_agent_execution_record_agent
    on agent_execution_record (agent_id, agent_version);

create index if not exists idx_agent_execution_record_tenant
    on agent_execution_record (tenant_id, created_at);

create table if not exists agent_tool_audit_event (
    id bigserial primary key,
    execution_id varchar(64) not null,
    tenant_id varchar(128),
    actor_id varchar(128),
    tool_name varchar(128) not null,
    status varchar(32) not null,
    message varchar(1000),
    metadata_json text,
    occurred_at timestamp with time zone not null
);

create index if not exists idx_agent_tool_audit_execution
    on agent_tool_audit_event (execution_id, occurred_at);

create index if not exists idx_agent_tool_audit_tenant
    on agent_tool_audit_event (tenant_id, occurred_at);

create table if not exists agent_prompt_template (
    prompt_id varchar(128) not null,
    version varchar(64) not null,
    agent_id varchar(128) not null,
    status varchar(32) not null,
    system_template text,
    user_template text,
    metadata_json text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint pk_agent_prompt_template primary key (prompt_id, version)
);

create index if not exists idx_agent_prompt_template_agent
    on agent_prompt_template (agent_id, status, version);

create table if not exists agent_approval_request (
    approval_id varchar(64) primary key,
    execution_id varchar(64) not null,
    agent_id varchar(128) not null,
    agent_version varchar(64),
    tenant_id varchar(128),
    requested_by varchar(128),
    status varchar(32) not null,
    reason text,
    metadata_json text,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    decided_at timestamp with time zone,
    decided_by varchar(128)
);

create index if not exists idx_agent_approval_execution
    on agent_approval_request (execution_id, status, created_at);

create index if not exists idx_agent_approval_tenant
    on agent_approval_request (tenant_id, status, created_at);

create table if not exists agent_pending_command (
    execution_id varchar(64) primary key,
    agent_id varchar(128) not null,
    agent_version varchar(64),
    input_type varchar(512) not null,
    input_json text not null,
    options_json text,
    tenant_id varchar(128),
    actor_id varchar(128),
    correlation_id varchar(128),
    requested_at timestamp with time zone,
    context_attributes_json text,
    callback_url varchar(1000),
    created_at timestamp with time zone not null
);

create index if not exists idx_agent_pending_command_agent
    on agent_pending_command (agent_id, agent_version, created_at);

create index if not exists idx_agent_pending_command_tenant
    on agent_pending_command (tenant_id, created_at);

create table if not exists agent_evaluation_result (
    evaluation_id varchar(64) primary key,
    dataset_name varchar(256) not null,
    status varchar(32) not null,
    total_count integer not null,
    passed_count integer not null,
    failed_count integer not null,
    cases_json text,
    evaluated_at timestamp with time zone not null
);

create index if not exists idx_agent_evaluation_result_dataset
    on agent_evaluation_result (dataset_name, evaluated_at);

create index if not exists idx_agent_evaluation_result_status
    on agent_evaluation_result (status, evaluated_at);
