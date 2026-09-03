-- Qubere Document Agent manual DDL for Oracle.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Holds document intakes whose target shipment could not be determined (UnassignedIntakeEntity),
-- ported from unassignedIntake.ts. Never auto-assigned to a guessed shipment -- see the entity's
-- javadoc for why a wrong guess is considered worse than no assignment at all.

create table unassigned_document_intake (
    id varchar2(64 char) not null,
    tenant_id varchar2(64 char) not null,
    source varchar2(32 char) not null,
    file_name varchar2(500 char),
    doc_type varchar2(64 char),
    requested_shipment_id varchar2(64 char),
    description varchar2(1000 char) not null,
    status varchar2(32 char) not null,
    created_at timestamp with time zone not null,
    resolved_at timestamp with time zone,
    constraint pk_unassigned_document_intake primary key (id)
);

-- Backs the operator-facing "open unassigned intakes for this tenant" queue.
create index idx_unassigned_doc_intake_tenant_status
    on unassigned_document_intake (tenant_id, status, created_at);
