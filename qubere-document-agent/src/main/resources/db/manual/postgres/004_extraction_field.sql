-- Qubere Document Agent manual DDL for PostgreSQL.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Holds every stored reading of every extracted field (ExtractionFieldEntity), ported from
-- extractionReview.ts's ExtractionField row shape. Append-only: a correction is a NEW row, never an
-- update to an existing one -- the machine's original reading must never be overwritten.

create table if not exists extraction_field (
    id varchar(64) primary key,
    document_id varchar(64) not null,
    field_name varchar(128) not null,
    field_value text not null,
    confidence integer,
    page_number integer,
    bbox_json text,
    source varchar(32) not null,
    created_at timestamp with time zone not null
);

-- Backs ExtractionReviewService.reviewFieldsFor()'s full-history load per document.
create index if not exists idx_extraction_field_document
    on extraction_field (document_id, created_at);
