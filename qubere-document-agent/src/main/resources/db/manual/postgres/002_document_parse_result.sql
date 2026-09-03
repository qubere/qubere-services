-- Qubere Document Agent manual DDL for PostgreSQL.
-- Execute manually before running qubere-document-agent with spring.jpa.hibernate.ddl-auto=validate.
--
-- Holds the current active parse result per document (DocumentParseResultEntity), keyed by
-- document_id: each new qualifying parse (see QualityGateEvaluator#qualifiesAsActive) simply
-- replaces the previous row -- mirroring the source's promoteToActive() concept without needing a
-- separate "which version is active" pointer. Deliberately a single row per document holding only
-- the normalized parse result, not the source's full six-artifact-type artifactStore.ts scheme --
-- see MIGRATION.md for the documented scope reduction.

create table if not exists document_parse_result (
    document_id varchar(64) primary key,
    processing_run_id varchar(64) not null,
    normalized_result_json text not null,
    quality_outcome varchar(32) not null,
    created_at timestamp with time zone not null
);
