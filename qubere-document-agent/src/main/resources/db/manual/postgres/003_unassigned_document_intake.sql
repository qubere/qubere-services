-- Qubere Document Agent manual DDL for PostgreSQL.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Holds document intakes whose target shipment could not be determined (UnassignedIntakeEntity),
-- ported from unassignedIntake.ts. Never auto-assigned to a guessed shipment -- see the entity's
-- javadoc for why a wrong guess is considered worse than no assignment at all.

create table if not exists unassigned_document_intake (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    source varchar(32) not null,
    file_name varchar(500),
    doc_type varchar(64),
    requested_shipment_id varchar(64),
    description varchar(1000) not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    resolved_at timestamp with time zone
);

-- Backs the operator-facing "open unassigned intakes for this tenant" queue.
create index if not exists idx_unassigned_document_intake_tenant_status
    on unassigned_document_intake (tenant_id, status, created_at);
