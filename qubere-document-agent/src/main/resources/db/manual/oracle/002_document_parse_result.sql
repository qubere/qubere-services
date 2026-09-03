-- Qubere Document Agent manual DDL for Oracle.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Holds the current active parse result per document (DocumentParseResultEntity), keyed by
-- document_id: each new qualifying parse (see QualityGateEvaluator#qualifiesAsActive) simply
-- replaces the previous row -- mirroring the source's promoteToActive() concept without needing a
-- separate "which version is active" pointer. Deliberately a single row per document holding only
-- the normalized parse result, not the source's full six-artifact-type artifactStore.ts scheme --
-- see MIGRATION.md for the documented scope reduction.

create table document_parse_result (
    document_id varchar2(64 char) not null,
    processing_run_id varchar2(64 char) not null,
    normalized_result_json clob not null,
    quality_outcome varchar2(32 char) not null,
    created_at timestamp with time zone not null,
    constraint pk_document_parse_result primary key (document_id)
);
