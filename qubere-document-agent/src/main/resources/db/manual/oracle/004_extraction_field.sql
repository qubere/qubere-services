-- Qubere Document Agent manual DDL for Oracle.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Holds every stored reading of every extracted field (ExtractionFieldEntity), ported from
-- extractionReview.ts's ExtractionField row shape. Append-only: a correction is a NEW row, never an
-- update to an existing one -- the machine's original reading must never be overwritten.

create table extraction_field (
    id varchar2(64 char) not null,
    document_id varchar2(64 char) not null,
    field_name varchar2(128 char) not null,
    field_value clob not null,
    confidence number(10),
    page_number number(10),
    bbox_json clob,
    source varchar2(32 char) not null,
    created_at timestamp with time zone not null,
    constraint pk_extraction_field primary key (id)
);

-- Backs ExtractionReviewService.reviewFieldsFor()'s full-history load per document.
create index idx_extraction_field_document
    on extraction_field (document_id, created_at);
