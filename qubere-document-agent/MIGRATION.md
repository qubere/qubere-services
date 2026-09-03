# Document Agent Migration Plan

Source project: `C:\WorkSpace\app-frontend` (TypeScript/Next.js customs-brokerage platform)
Target module: `qubere-document-agent` (Java 21 / Spring Boot / Spring AI, built on `qubere-agents`)

This is the second, expanded revision of this plan. The first revision scoped only the
document-intake/parsing subsystem and undercounted it; this revision was produced by a full
re-read of the source project and corrects both the document-subsystem scope and a much larger
finding: **`app-frontend` runs a 10-agent orchestrated compliance pipeline, of which only 2 agents
are document-related.** That distinction changes the shape of this plan and is called out
explicitly in section 1.

---

## 1. Scope: what belongs in `qubere-document-agent` vs. what does not

`app-frontend` has a full multi-agent trade-compliance pipeline at
`apps/custom/src/modules/agents/`, orchestrated by `pipelineOrchestrator.ts`. It runs **10 agents**
across 10 pipeline stages:

| # | Agent | Stage | LLM? |
|---|---|---|---|
| 1 | Document Intelligence | 2 | Yes (Gemini Vision) + deterministic fallback |
| 2 | Normalization | 2b | Yes (Gemini) + deterministic fallback |
| 3 | Product Intelligence | 3 | Yes (Gemini) + Product Master matching |
| 4 | HTS Classification | 4 | Yes (Gemini, GRI/legal reasoning) + DB grounding |
| 5 | Origin Rules | 5 | No — deterministic rules engine |
| 6 | Valuation Assists | 6 | No — deterministic arithmetic |
| 7 | Compliance Audit | 7 | Optional (Gemini synthesis) + 7 deterministic screening modules |
| 8 | Filing Readiness | 8 | No — deterministic validation |
| 9 | Customs Filing | 9 | No — deterministic ACE-transmission simulation |
| 10 | Response Management | 10 | No — deterministic placeholder (no live integration yet) |

Plus `documentIntakeAgent.ts`, which runs **before** stage 1 as a separate pipeline
(`intakePipelineRouter.ts`) and is not part of `pipelineOrchestrator.ts`'s numbered stages.

**Only Document Intake and Document Intelligence (agent 1) are document-processing agents.**
Agents 2–10 are a shipment-level trade-compliance/customs-filing pipeline: product enrichment, HTS
tariff classification, country-of-origin/FTA rules, customs valuation, sanctions/denied-party
screening, filing readiness, and (simulated) ACE transmission. They read documents' *extracted
output* but are not themselves document agents.

**Decision — confirmed:** `qubere-document-agent` owns only document intake and document
intelligence/extraction — the two agents whose subject matter is literally "given file bytes,
understand the document." The other 8 agents (and the orchestrator that sequences all 10) will be
built as a separate service, working name `qubere-compliance-agent`, calling into
`qubere-document-agent` over the network via `RemoteAgentClient`, the same way the TypeScript
orchestrator calls `documentIntelligenceAgent.ts` today. See section 7 for the consequences of this
decision.

The remainder of this document is scoped to what `qubere-document-agent` itself owns: document
intake, document intelligence/extraction, and the parsing/processing/context subsystem that feeds
them. Section 6 records the other 8 agents' shapes for when that follow-on module is planned, since
the same exploration already produced accurate detail for them.

---

## 2. Current state of `qubere-document-agent` (Java)

Honest status: **early scaffold, not yet functionally equivalent to the TypeScript agents.**

- `DocumentIntakeAgent.java` — classifies by **filename substring heuristics only**
  (`invoice`→`COMMERCIAL_INVOICE`, `packing`→`PACKING_LIST`, etc.), with an `docTypeOverride` escape
  hatch. No real document catalog, no vision/LLM classification, no parser integration. Confidence
  is a hand-picked constant (0.75 detected / 0.45 unknown), not computed from any real signal.
- `DocumentIntelligenceAgent.java` — if an `AgentAiClient` bean is present, sends a prompt built from
  a caller-supplied `documentContext` string and gets back a structured
  `DocumentExtractionResponse` (invoice number, currency, total value, exporter/importer name,
  country of origin, confidence); otherwise returns an all-blank deterministic placeholder. There is
  no parser, no context builder, no line-item extraction, no persistence of results.
- `DocumentContextLookupTool.java` — a placeholder tool that echoes back whatever `documentContext`
  string was already supplied as an argument. It does not look anything up; the real parser/DB
  lookup is entirely unimplemented.
- No persistence: no document/processing-run/fact tables, no repositories, nothing written to
  `qubere-agent-storage`-backed storage beyond what the generic framework already persists
  (`agent_execution_record`, etc.).
- No parser provider integration (IBM Docling or otherwise), no quality gate, no chunking, no
  malware screening, no duplicate detection, no currency normalization, no review workflow.

Everything below this line is what closes that gap.

---

## 3. Document-subsystem source inventory (verified against current source, not the old doc)

The first revision of this plan named 19 TypeScript files. Re-reading the actual source found
**8 additional files containing real, non-trivial business logic that the first revision omitted
entirely**, plus one partial-coverage issue and several under-specified behaviors. All are listed
below so nothing already found gets lost again.

### 3.1 Already-scoped files (mapping confirmed accurate, still unimplemented in Java)

| `app-frontend` source | `qubere-document-agent` target | Notes |
|---|---|---|
| `modules/intake/documentIntakeAgent.ts` | `document/DocumentIntakeAgent.java` | Gemini Vision path uses the real catalog; **fallback path (no AI client) still uses filename heuristics** — see 3.3 |
| `modules/intake/documentTypeCatalog.ts` | `document/DocumentTypeCatalog.java` | Document type taxonomy — not yet ported at all |
| `modules/intake/documentIntake.service.ts` | `document/DocumentIntakeService.java` | |
| `modules/intake/intakePipelineRouter.ts` | `document/IntakePipelineRouter.java` | |
| `modules/intake/unassignedIntake.ts` | `document/UnassignedIntakeRecorder.java` | |
| `modules/agents/documentIntelligenceAgent.ts` | `document/DocumentIntelligenceAgent.java` | TS file is 900+ lines; Java port is currently ~130 lines with no parser/context/line-items |
| `modules/documents/processing/documentProcessingWorker.ts` | `document/processing/DocumentProcessingWorker.java` | State-machine correctness is the highest-risk part of this file — see 3.4 |
| `modules/documents/processing/processingRuns.ts` | `document/processing/ProcessingRunRepository.java` | Idempotency + stale-run protection — see 3.4 |
| `modules/documents/processing/documentSource.ts` | `document/processing/DocumentSourceValidator.java` | |
| `modules/documents/processing/malwarePolicy.ts` | `document/security/MalwareScreeningPolicy.java` | |
| `modules/documents/parser/contracts.ts` | `document/parser/DocumentParserProvider.java` + DTOs | |
| `modules/documents/parser/registry.ts` | `document/parser/ParserProviderRegistry.java` | |
| `modules/documents/parser/config.ts` | `document/parser/ParserProperties.java` | |
| `modules/documents/parser/qualityGate.ts` | `document/parser/QualityGateEvaluator.java` | 60% text-coverage threshold; OCR retry escalation — see 3.4 |
| `modules/documents/parser/artifactStore.ts` | `document/parser/ArtifactStorageService.java` | |
| `modules/documents/parser/chunking.ts` | `document/parser/DocumentChunkingService.java` | Ordering-before-budget rule — see 3.4 |
| `modules/documents/parser/ibm/*` | `document/parser/ibm/*` | SSRF/artifact-host allowlist validation — see 3.4 |
| `modules/documents/parser/mock/*` | `document/parser/mock/*` | Must refuse to start in production (constructor + registry guard) |
| `modules/documents/context/*` | `document/context/*` | Feeds `QubereDocumentContext`; chunk ordering-by-purpose lives here |
| `lib/documents/extractionSchemas.ts` | `document/extraction/ExtractionSchemas.java` | |
| `lib/documents/fieldDictionary.ts` | `document/extraction/FieldDictionary.java` | |
| `lib/documents/classificationMapping.ts` | `document/classification/DocumentTypeMapper.java` | |
| `modules/documents/documentQuery.ts` | `document/query/DocumentQuery.java` + `DocumentQueryBuilder.java` | Filtering/sorting/pagination for the document list API — see 3.2 |

### 3.2 Complete omissions in the first revision (real logic, not yet scoped anywhere)

These files exist, contain real business rules, and were not mentioned in the first migration
plan at all:

1. **`documents/duplicateDetection.ts`** (58 lines) — cross-shipment duplicate detection via
   SHA-256 checksum indexing; returns up to 5 most recent duplicates as a non-blocking signal.
   Without this, users get no warning that an identical document already exists on a different
   shipment.
2. **`documents/extractedCurrency.ts`** (76 lines) — currency-code extraction/normalization.
   Searches up to 6 JSON paths in extraction output in priority order
   (`tradeMetadata.currency` → `currency` → `keyValuePairs.currency`/`Currency`/`"Invoice
   Currency"`/`"Currency Code"`), normalizes symbols (`£`→GBP, `€`→EUR, `$`→USD, plus CAD/AUD/JPY
   variants) to ISO 4217 codes. **Returns `null`, not a guess, when zero or two-or-more distinct
   currencies are found across a shipment's documents** — callers render a bare number rather than
   inventing a symbol. This null-on-disagreement rule is the kind of detail that's easy to silently
   drop in a port and must be preserved exactly.
3. **`documents/extractionReview.ts`** (217 lines) — human review/correction workflow. Tracks full
   field revision history; a human correction always wins over any machine read, newest correction
   wins among corrections. Review-flag threshold is **80% confidence**, deliberately different from
   Document Intake's own 70% threshold — do not conflate the two. Reports current value, original
   (first machine) value, best-machine confidence, and a `correctionFlag`.
4. **`documents/loadDocumentBytes.ts`** (136 lines) — unifies document byte retrieval across three
   storage routes that exist because uploads reach the DB via three different paths: local disk
   (`uploads/quarantine/<documentId>/<fileName>`, used by the portal app), object storage (GCS via a
   real `fileUrl`), and `rawContent` stored directly in Postgres (base64 or UTF-8) as a last resort.
   Tries them in that order. **Contains a path-traversal guard** that must be preserved exactly:
   document id is validated against `^[a-z0-9]{16,40}$`, filename is reduced to its basename, and
   the resolved path is asserted to stay under the intended root directory before any read.
5. **Document Intelligence's real LLM orchestration** (referred to as `classificationExtraction.ts`
   in the codebase) — the actual extraction-agent LLM call construction, prompt building, and
   context assembly for stage-1 extraction. The current Java `DocumentIntelligenceAgent` is a
   ~130-line scaffold; the TypeScript equivalent is 900+ lines. This is the single largest specific
   gap in the document subsystem.
6. **`inboundEmailWorker.ts`** — inbound email attachment ingestion: downloads attachments from the
   email provider, screens them for malware, and routes to the document pipeline or a quarantine
   path depending on the scan result. An entire ingestion channel, currently unscoped.
7. **`advanceProcessing.ts`** — a request-scoped `after()` hook that drains the processing queue
   after an HTTP response is sent, so documents don't sit idle for up to the next cron tick. Without
   this, a straight cron-only port would add up to 24h of avoidable processing latency depending on
   the configured interval.
8. **`documents/parser/fallbackProvider.ts`** — automatic primary→backup parser failover on
   non-retryable primary-provider failures. Without this, a single parser provider outage takes
   down all document processing instead of degrading to a backup.

### 3.3 Partial/incomplete coverage in the first revision

- **`documentIntakeAgent.ts`**: the first plan's note ("make `DocumentIntakeAgent` use the real
  catalog instead of filename heuristics") is only half the picture. In the actual TypeScript, the
  **Gemini-backed path already uses the real document-type catalog**; it's specifically the
  **no-AI-client fallback path** that still falls back to filename heuristics. ~~The Java port
  currently has no catalog integration on either path.~~ **Corrected — this note was stale by the
  time §14 was written.** The Java `DocumentIntakeAgent` was fixed to be catalog-first on every path
  (an explicit override is an exact catalog lookup; the classification signal, whether header text
  or filename, always goes through `DocumentTypeCatalog.matchDocumentType()`) — see
  `DocumentIntakeAgentTest`. There is no vision-model path to be "Gemini-backed" or "fallback" in the
  first place yet (§12 Q2's open LLM-provider question), so the TS source's two-path distinction
  doesn't have a Java equivalent to diverge from at this stage.
- **Parser provider implementations**: IBM Docling adapter SSRF protection and artifact-host
  allowlist validation, and the specific error-code/retry contract, were named in the first plan's
  file list but not described in enough detail to implement against. Detailed in 3.4.
- **Quality gate**: the first plan named the file but not its actual thresholds. Detailed in 3.4.

### 3.3.1 Corrected in this pass — Phase 3 file inventory

Re-reading `intakePipelineRouter.ts`/`unassignedIntake.ts`/`documentIntake.service.ts` (all in
Phase 3, none previously assessed against actual source) found:

- `documentIntake.service.ts` is a 9-line pass-through (`ingestDocumentPacket` just calls
  `DocumentIntakeAgent.execute`) — no real logic, and this framework's `AgentRuntimeService.run(
  "document.intake", ...)` already is the equivalent seam. **Not ported**; there is nothing to port.
- `intakePipelineRouter.ts` does two genuinely different things bundled into one function: (1) for
  `sourceApp=TMS/MOVE`, dynamically imports and calls a **different application's** TMS
  freight-extraction module by relative path; (2) for everything else, calls Document Intelligence.
  Only (2) is in scope — (1) reaches into a sibling Next.js app's module tree, which is exactly what
  a separate agent service should not do (§7's module-boundary decision). See §14.
- `unassignedIntake.ts` (58 lines) has real, important logic that was never called out before:
  recording a document intake that arrived without a resolvable shipment id, specifically so nothing
  falls back to guessing "the account's newest shipment." See §14.

### 3.4 Business rules that are easy to lose in translation (preserve exactly)

- **Quality gate**: 60% text-coverage threshold to pass; below it, an OCR retry-escalation strategy
  runs (`OCR_FALLBACK` → `FULL_PAGE_OCR`) before a document is finally marked terminal. Terminal
  states must not re-qualify once reached.
- **Chunking**: chunk **ordering happens before budget selection**, via a purpose-specific
  `orderChunksForPurpose()`, and chunks must be taken **in the order given**, not re-ranked by
  relevance inside the budget-truncation step. Truncation must always be reported, never silent.
- **Run state machine** (`processingRuns.ts` / `documentProcessingWorker.ts`): every transition is
  guarded by a conditional DB update (not a read-then-write), terminal states
  (`SUCCEEDED`/`NEEDS_REVIEW`) never transition again, a version number prevents a stale run from
  reclaiming the "active" pointer, and duplicate submissions are made idempotent by a unique
  constraint that finds the existing run rather than creating a second one. This is the highest
  concurrency-risk area in the whole subsystem and deserves dedicated state-machine tests, not just
  happy-path coverage.
- **Confidence scale consistency**: Document Intake uses a 0–100 scale with `null` for "unknown"
  (never a fabricated default); Extraction Review's flag threshold is a separate 80% cutoff. Do not
  let these collapse into a single inconsistent 0–1 vs 0–100 representation during the port.
- **Null-vs-default discipline, project-wide**: absence is preserved, never fabricated — no bounding
  box becomes `null` (not an empty box), no confidence becomes `null` (not `0` or `50`), no
  currency-agreement becomes `null` (not a guessed default currency). A fabricated bounding box cited
  in an audit trail is considered worse than no bounding box at all. This principle should be
  enforced the same way in the Java port, including in the DTOs (nullable boxed types, not
  primitives defaulting to zero).
- **Mock provider production guard**: the mock parser provider must refuse to start when running in
  production, enforced in both its constructor and the provider registry — a second, independent
  check, not just one.
- **SSRF protection**: the IBM Docling provider validates artifact URLs against an allowlist before
  fetching; this must carry over identically, not be weakened to "trust the config value."
  Signed/credentialed URLs must never be logged.
- **Audit-trail completeness**: every agent decision must carry agent name, model version, prompt
  version (hash), regulations cited (e.g. `19 CFR § 141.86`), rules applied (e.g. "Multi-Page
  Document Stitching Rule"), and data sources (e.g. "Google Vision OCR Engine", provider name) — not
  just a pass/fail flag.

---

## 4. Document-subsystem porting plan (phased, dependency-ordered)

**Phase 1 — Foundation (no LLM calls, no I/O):** `DocumentParserProvider` contracts/DTOs,
`ParserProperties`, `DocumentTypeCatalog`, `FieldDictionary`, `DocumentTypeMapper`,
`ExtractionSchemas`, `ParserProviderRegistry`, `DocumentChunkingService`, `QualityGateEvaluator`.

**Phase 2 — Parser integration (depends on Phase 1):** `DocumentSourceValidator`,
`ArtifactStorageService`, IBM Docling provider (with SSRF/allowlist checks), mock provider (with
the production-refusal guard), **fallback/failover provider** (§3.2 item 8),
`MalwareScreeningPolicy`, `ProcessingRunRepository` (state machine + idempotency), the run/processing
worker itself.

**Phase 3 — Intake pipeline (depends on 1, 2):** `DocumentIntakeAgent` rewritten to be catalog-first
on both the AI and fallback paths (§3.3), `DocumentIntakeService`, `UnassignedIntakeRecorder`,
`IntakePipelineRouter`.

**Phase 4 — Context and extraction (depends on 1, 2, 3):** the document-context builder (feeding
`QubereDocumentContext`, including chunk-ordering-by-purpose), the real Document Intelligence
extraction agent (replacing the current scaffold), `DocumentQuery`/`DocumentQueryBuilder`.

**Phase 5 — Support services (depends on 1–4):** duplicate detection, currency extraction, the
extraction-review workflow, multi-source byte loading (with the path-traversal guard), the
request-driven processing-advancement hook, and — as a distinct, larger effort requiring an email
provider client — inbound email ingestion.

Each phase should land with its own tests before the next starts; the state machine and quality-gate
logic in particular need dedicated tests beyond the happy path, since both are named above as
highest-risk.

---

## 5. How this fits the `qubere-agents` framework

The document subsystem maps cleanly onto existing framework primitives, with one deliberate
absence:

- **Fact-based cross-agent state** (`FactService`, `{shipmentId, field, value, sourceType,
  documentId, createdAt}`, namespaced as `line_N.field` for per-line facts): this is a durable,
  DB-backed blackboard the whole 10-agent pipeline reads/writes between stages. The framework's
  closest equivalent is `AgentOrchestrator`'s in-memory `OrchestrationState` blackboard (see
  `agent-framework.md` §11), which is scoped to a single orchestrated run, not a
  durable-across-triggers store. **This is a real gap, not a naming difference**: a durable,
  queryable fact store keyed by shipment is domain-specific persistence that
  `qubere-document-agent` (or the future compliance-agent module) will need to build on top of
  `qubere-agent-storage`, analogous to how `agent_checkpoint` was added for step memoization. It is
  not provided by the framework today.
- **Per-run audit** (`agentExecutionLog`, `agentDecision`): maps directly to the framework's
  `agent_execution_record` / `agent_execution_log` / `agent_tool_call` tables plus
  `AgentDecisionDraft`/`AgentEvidenceDraft` already in the agent contract — no gap here.
- **Serial orchestration with short-circuit and a per-stage circuit breaker**: this is exactly what
  `AgentOrchestrator.sequential(...)` with `FailurePolicy.FAIL_FAST` already provides (see
  `agent-framework.md` §11.2–11.3), including the "remaining steps reported, not silently dropped"
  behavior the TypeScript orchestrator also has. The 3-consecutive-stage-failure circuit breaker has
  no direct framework equivalent yet and would be a thin addition on top of `AgentWorkflowStatus`.
- **Per-account auto-approval policy** (`getAgentPolicyConfig`, confidence thresholds, "AUTO" vs
  "NEEDS_REVIEW"): maps to `AgentPolicyResolver` plus `agent-platform.governance.require-approval-for-high-risk`,
  though the TypeScript version is more granular (per-account, per-agent policy documents) than the
  framework's current global/per-agent-id config — likely an extension point, not a rewrite.
- **Billing events with idempotency keys**: no framework equivalent; would need a small
  domain-specific addition, same pattern as the fact store above.
- **Post-pipeline unconditional reconciliation and stage advancement**: domain-specific behavior
  with no generic framework equivalent, and none is needed — this belongs in application code that
  calls the framework, not in the framework itself.

None of the above blocks starting the document subsystem port. They matter once the wider
compliance pipeline (section 6) is planned, since that pipeline is where the fact store and
circuit breaker actually get exercised.

---

## 6. The other 8 agents (recorded for future scoping, not in this module's plan)

For when a compliance-pipeline module is planned, here is what the same exploration found, so this
detail isn't lost and doesn't need re-discovering:

| Agent | Purpose | Reads (from Fact store / context) | Writes | Approval gate |
|---|---|---|---|---|
| Normalization | Canonicalizes Document Intelligence output into an enterprise model (parties, products, financials, relationships) | Doc Intelligence output | Output stored in `agentDecision.evidenceItems` only (no Fact writes) | Gemini or deterministic fallback mapper (zero-data-loss) |
| Product Intelligence | Enriches line items: material composition, essential character, end-use, finish, CAS number | Line items, descriptions | `line_N.*` facts | Product Master deterministic match can override the LLM |
| HTS Classification | 10-digit HTS codes, GRI citations, CROSS ruling citations, duty rates | Enriched profiles, country | Direct `shipmentLineItem` update (not via Fact) | Confidence threshold policy (`hts-classification-confidence-threshold`) |
| Origin Rules | Country-of-origin qualification, USMCA/FTA candidate flags | Line items, HTS, country | `line_N.*` facts | Deterministic only |
| Valuation Assists | Entered customs value, freight/buyer-assist adjustments | Invoice, freight, assists | `valuationAssists.*` shipment-level facts | Deterministic only |
| Compliance Audit | Runs 7 deterministic screening modules (embargo, forced labor, end-use, end-user, anti-boycott, military end-use, restricted-party) plus optional LLM synthesis for flag prioritization | Line items, parties, country | Screening findings (not namespaced facts) | Optional LLM synthesis, always reviewed if any hit |
| Filing Readiness | Binary readiness score + Form 7501 preview + missing-requirement list | All prior stage outputs | None (read-only gate) | Deterministic only |
| Customs Filing | **Simulated** ACE transmission (never real CBP submission in current codebase) | Entry data, `authorized` flag | `customsFilingId`, `aceResponse` | Requires explicit `authorized=true`; still marked `NEEDS_REVIEW` even on simulated success |
| Response Management | Post-entry refund-opportunity scanner (Section 301 exclusions, duty drawback) | Entry number, invoice | None — placeholder; always `COMPLETED_NO_ACTION` since no live USTR/CBP API is wired up | N/A |

The **7 compliance screening modules** (`agents/compliance/{embargo,forcedLabor,endUse,endUser,
antiBoycott,militaryEndUse,restrictedParty}/`) are individually deterministic and reference
OFAC/BIS watchlists, an `EmbargoRule` table, and hard-coded ADD/CVD/FDA/USMCA reference lists —
straightforward to port but each needs its own real reference-data source in Java (the TypeScript
version already depends on the same DB-backed reference tables, not external live APIs, for most of
these).

**Trigger-based agent selection** (from `pipelineOrchestrator.ts`) — worth carrying forward exactly,
since it's the shape any Java `AgentOrchestrator` usage for this pipeline should replicate:

| Trigger | Agents run |
|---|---|
| `DOCUMENT_UPLOADED` | Full pipeline: Document Intake → Document Intelligence → Product Intelligence → HTS Classification → Origin Rules → Valuation → Compliance Audit → Filing Readiness |
| `USER_FIELD_UPDATED` (origin field) | Origin Rules → Compliance Audit → Filing Readiness |
| `USER_FIELD_UPDATED` (HTS code) | HTS Classification → Origin Rules → Valuation → Compliance Audit → Filing Readiness |
| `USER_FIELD_UPDATED` (line item) | Product Intelligence → HTS Classification → Origin Rules → Valuation → Compliance Audit → Filing Readiness |
| `USER_FIELD_UPDATED` (incoterm/value/currency) | Valuation → Compliance Audit → Filing Readiness |
| `USER_FIELD_UPDATED` (other) | Compliance Audit → Filing Readiness |
| `RECONCILIATION_REQUESTED` | Compliance Audit → Filing Readiness |

Customs Filing and Response Management are **deliberately excluded** from every auto-triggered
path in the TypeScript system — Customs Filing only fires from an explicit authorized user action
(it simulates a real regulatory submission), and Response Management is a placeholder with no new
logic on re-run. Preserve this exclusion; it is a safety property, not an oversight.

---

## 7. Module boundary — decided

**Confirmed:** `qubere-document-agent` stays scoped to Document Intake and Document
Intelligence/extraction only. The other 8 agents (Product Intelligence, HTS Classification, Origin
Rules, Valuation Assists, Compliance Audit, Filing Readiness, Customs Filing, Response Management)
will be built as a **separate Spring Boot service** — working name `qubere-compliance-agent` —
that calls `qubere-document-agent` across the network for document-derived facts, using the
framework's existing cross-service orchestration support (`RemoteAgentClient`,
`AgentPropagationHeaders`, distributed workflow budget) rather than an in-process call. This
mirrors the TypeScript system's own boundary of "documents are understood in one place, consumed by
compliance/filing elsewhere" — the pipeline orchestrator's stage 1 is the only stage that reads raw
document bytes; every later stage reads only structured facts.

Consequences of this decision, to plan against explicitly:

- The trigger-based sequencing table in section 6 (`DOCUMENT_UPLOADED` → full pipeline, field-update
  triggers → partial re-runs) becomes the responsibility of `qubere-compliance-agent`'s own
  orchestrator, built with `AgentOrchestrator` (sequential + `FailurePolicy.FAIL_FAST`, matching the
  TypeScript short-circuit behavior) calling back into `qubere-document-agent` via
  `RemoteAgentClient` only for the stages that need it (effectively just re-reading already-extracted
  facts in most cases, not re-invoking Document Intelligence).
- `qubere-document-agent`'s public contract — the shape of what Document Intelligence returns — is
  now a cross-service contract, not an internal implementation detail. Its output schema should be
  finalized with that in mind (stable field names, versioned, documented) before
  `qubere-compliance-agent` is started, since changing it later means coordinating two services
  instead of one function signature.
- The durable fact store (open question below) becomes more clearly *shared* infrastructure, since
  both services will read/write it, which argues for building it once in `qubere-agent-storage`
  rather than duplicating it per-service.

## 8. Progress: Phase 1 and Phase 2 implementation status

### 8.1 Phase 1 — complete except one deliberately deferred item

- **Done**: `DocumentTypeCatalog` (25-entry catalog + exact 3-tier matching), `DocumentIntakeAgent`
  rewired to be catalog-first on both paths, `DocumentTypeMapper` (the persisted `DocumentType`
  vocabulary + confidence normalization), `ExtractionSchemaCatalog` (11 document types' required/
  optional field schemas), the full parser contract layer (`DocumentParserProvider`, error model,
  state machine, DTOs — see §8.2), `ParserProperties` (Spring `@ConfigurationProperties`),
  `QualityGateEvaluator`, `DocumentChunkingService`.
- **Deliberately not ported**: `fieldDictionary.ts`. Its real dependency is
  `modules/hydration/inventory/fieldInventory.ts` and `modules/hydration/registry/canonicalRegistryV1.ts`
  — a large, cross-cutting canonical-field-registry subsystem used throughout the TypeScript app,
  not something scoped to documents specifically. Porting it properly means first deciding whether
  and how that hydration/canonical-registry concept exists in this framework at all, which is a
  bigger question than this module should answer unilaterally. Flagged here rather than either
  silently skipped or half-ported against a registry that doesn't exist yet.

### 8.2 Phase 2 — provider layer complete; worker/state-machine not yet started

Done: `MockDoclingProvider`, `FallbackDoclingProvider`, `IbmDoclingProvider` (real HTTP submit/
status/result against the documented `/convert/source/...` JSON contract), `DoclingAdapter`
(section/table normalization, deterministic ids, confidence mapping — all null-preserving exactly
as the source requires), `SsrfArtifactHostValidator`, `MalwareScreeningPolicy` abstraction plus a
permissive `NoOpMalwareScreeningPolicy` default, and `DocumentParserProviderAutoConfiguration`
(the Spring-idiomatic replacement for `registry.ts`'s factory function).

**Scoped out of the IBM provider port, documented rather than silently dropped:**

- **`multipart/form-data` submission encoding.** Only the JSON `/convert/source/...` encoding is
  implemented. A hosted deployment that exposes only `/convert/file/...` is not yet supported.
- **The batch/presigned-artifact-URL result shape.** Some hosted deployments answer with content
  behind short-lived artifact URLs rather than inlining it (`doclingBatchResultSchema`); only the
  inline shape (`doclingResultSchema`) is normalized. `SsrfArtifactHostValidator` — the allowlist
  check that shape would need before fetching an artifact URL — is already ported and tested, ready
  to wire in when this is picked up.

**A real bug found and fixed during this port, not present in a way that would have been obvious
from reading the TypeScript alone:** the first version of `DocumentParserProviderAutoConfiguration`
threw `PARSER_NOT_CONFIGURED` directly inside the `@Bean` method for the default
`document-agent.parser.provider=none` configuration. Spring beans that throw during construction
fail application startup, not just the feature that needed them — so the *default, no-configuration
state* (exactly what local development and CI start from) crashed the whole app instead of merely
leaving document parsing unavailable. Fixed with `NotConfiguredDocumentParserProvider`, which defers
the failure to first actual use (`submit`/`getStatus`/`getResult`), matching what the source's own
`getDocumentParserProvider()` actually does — it is a plain function called by request-handling
code, so it only ever fails a request that needed a provider, never module load.

**Not yet started: the processing-run state machine and worker** (`processingRuns.ts`,
`documentProcessingWorker.ts`). This is intentionally the last piece, because it is where the
framework-fit question in §9 below actually gets decided, not assumed.

## 9. Where the TypeScript source falls short of this framework's standards

This was checked deliberately, not assumed. The TypeScript processing worker is functionally
complete — durable state machine, idempotent work claiming, retry with backoff, audit logging — but
an explicit comparison against what `qubere-agents` already provides elsewhere in this repository
found real gaps:

| Capability | TypeScript worker | `qubere-agents` framework |
| --- | --- | --- |
| Rate limiting (per-tenant/actor/agent) | None | Built in (`agent-platform.governance.*`, proven with a real HTTP 429 test) |
| Circuit breaking on external calls | None | `AgentResilienceGateway` (Resilience4j-backed), already reused for `IbmDoclingProvider` in this port (see §8.2) |
| Cost/budget tracking | None | `ModelCostBudgetTracker`, per-run and per-workflow budgets |
| Structured, queryable audit trail | Ad hoc `AuditLog` rows, no standard schema across features | `agent_execution_record` / `agent_execution_log` / `agent_tool_call`, uniform across every agent |
| Tenant isolation guarantee at the worker layer | Not enforced at the worker; relies on callers scoping queries correctly | Fail-closed `AgentCallerIdentityResolver` seam, tenant/actor carried on every `AgentExecutionContext` |
| Approval/human-in-the-loop gating | All automated; no pause/resume | Durable approval workflow, tool-level approval resumability |

**This is the concrete argument for routing document processing through the framework's existing
primitives instead of building a bespoke, parallel worker loop that would inherit the TypeScript
worker's gaps.** The processing-run state machine itself (conditional-update guards, idempotency
key, stale-run reclaim) is sound and should be ported faithfully — it solves a real problem
(concurrent workers safely claiming work) that the framework does not currently have a generic
answer for. But the *loop that drives it* should call into the parser provider through
`AgentResilienceGateway` (already true for `IbmDoclingProvider`), record audit through the same
execution-record path every other agent uses instead of a separate ad hoc log table, and resolve
tenant/actor through `AgentCallerIdentityResolver` rather than trusting whatever the enqueuing code
passed in. This is a design decision to confirm before implementation, not a small detail — see §10.

**One additional finding, preserved rather than silently fixed:** `FallbackDoclingProvider` (already
ported, §8.2) can fail over to the backup provider mid-flight, after `submit` already succeeded
against the primary — the backup has no knowledge of the primary's `externalTaskId`. This is a real
latent inconsistency in the source, ported faithfully because that is what a migration is, but
flagged here as a known limitation rather than quietly carried forward as if it were correct. A
circuit-breaker-based design (fail the run, do not silently switch providers mid-poll) would close
this properly; see the class's own javadoc for detail.

**Also preserved as an inherited design gap, not fixed silently:** `DocumentType` (this module) and
`DocumentTypeCatalog` (already ported) are two vocabularies that do not map 1:1, exactly mirroring
the five-vocabulary problem the source's own `fieldDictionary.ts` docstring admits to. See
`DocumentType`'s javadoc for the specific code mismatches. Unifying them is real future work, not
attempted here because the reconciliation engine that would consume a unified mapping does not
exist in this module yet.

## 10. Processing-run state machine and worker — done, with a course-correction along the way

The first design considered was routing the worker through the framework's existing
`AgentAsyncQueue`. **That was rejected before being built**: that queue is shaped for "queue one
request, execute one complete governed agent run, dispatch a callback," and a parser run's actual
shape — submit once, poll an external system an unknown number of times with backoff, then complete
— does not fit it. Forcing the fit would have meant either treating every poll tick as its own
governed agent execution (flooding the audit trail with meaningless "still polling" rows) or having
the run silently re-enqueue itself every worker tick regardless of whether a poll was actually due,
bypassing the intended backoff. Recognizing that mismatch mattered more than mechanically wiring the
existing abstraction in because it existed — the same discipline this whole document has tried to
apply to the TypeScript source, applied here to this framework's own code.

**What was actually built**, confirmed with the user before starting:

- `ProcessingRunEntity` + `ProcessingRunRepository` + `ProcessingRunService` — the durable state
  machine (`ProcessingRunState`'s legal-transition table), idempotent enqueue (SHA-256 key over
  document+profile+reason, with a unique-constraint fallback for a genuine concurrent race), and
  stale-run reclaim, all ported faithfully from `processingRuns.ts`.
- **Concurrency correctness uses JPA's built-in `@Version` optimistic locking**, not the source's
  hand-rolled `WHERE state = expected` conditional-update SQL. Reimplementing that SQL by hand would
  have been its own small "blind migration" mistake — porting a mechanism the target platform
  already has a first-class, better-tested answer for.
- `DocumentProcessingWorker` — a plain Spring `@Scheduled` poller (not `AgentAsyncQueue`) driving
  submit → poll → complete. Every parser call goes through `DocumentParserProvider`, whose
  `IbmDoclingProvider` already routes through `AgentResilienceGateway`. Every terminal outcome
  (accepted or needs-review) is recorded via `DocumentProcessingOutcomeAgent`, a real governed
  `AgentRuntimeService.run(...)` invocation — a proper `agent_execution_record` row, not an ad hoc
  log table, and exactly the framework-fit argument from §9 actually delivered rather than just
  proposed.
- `DocumentBytesSource` — a small pluggable seam, not a real implementation. Byte retrieval
  (`loadDocumentBytes.ts`) remains Phase 5, deliberately deferred; the worker depends only on this
  interface so the state machine and poll loop could be built and tested now, with a
  `NotConfiguredDocumentBytesSource` default that fails clearly at first use rather than pretending
  bytes exist.

**Two real bugs found and fixed while building this, both instructive beyond this one module:**

1. A version of `ProcessingRunService` caught `OptimisticLockingFailureException` inside a shared
   `@Transactional` method, expecting the catch to let the method carry on normally. It does not:
   per the JPA spec, an exception during flush leaves the persistence context unusable for the rest
   of that transaction regardless of whether application code catches it, so the transaction still
   came back `rollback-only` and callers saw an opaque `UnexpectedRollbackException`. This is the
   same class of bug already hit once elsewhere in this framework
   (`JpaDistributedWorkflowBudgetStore`) — fixed the same way, with a `TransactionTemplate` using
   `PROPAGATION_REQUIRES_NEW` so a conflicting attempt only rolls back its own isolated transaction.
2. Once that was fixed, a second, subtler bug surfaced: `saveAndFlush` inside a `REQUIRES_NEW`
   transaction returns a *merged copy* of the entity carrying the post-save `@Version`, not the same
   Java object passed in. `markFailed`'s two sequential transitions (FAILED, then QUEUED for a
   retryable failure) kept mutating the *original* (now version-stale) instance for the second
   transition, so it silently lost an optimistic-lock race against its own first save and the run
   stuck at FAILED instead of re-queuing. Fixed by threading the returned, freshly-merged entity
   through to the next transition rather than reusing the original reference.

## 11. Deployment gap closed: manual DDL for `document_processing_run`

An assessment of this module found the new `document_processing_run` table had no manual DDL —
every other table in this framework has hand-written Postgres/Oracle scripts because
`ddl-auto=validate` is the standing discipline for non-local profiles, but this one only ever
existed via H2's `ddl-auto=update` in the local profile. Concretely, the `postgres`/`oracle`
profiles would have failed to start the moment this entity was scanned.

Fixed: `db/manual/postgres/001_document_processing_run.sql` and
`db/manual/oracle/001_document_processing_run.sql`, in this module's own resources (not
`qubere-agent-storage`, since this table is document-agent business data, not generic
agent-runtime persistence), following the exact column/index shape `ProcessingRunEntity` requires.
Registered in the root `ddl-scripts.md` alongside the existing scripts.

**Verified, not just written to look plausible**: `ProcessingRunDdlValidationTest` boots a minimal
JPA-only Spring context against a schema created *solely* by running the manual PostgreSQL script
(never Hibernate's own `ddl-auto`), with `ddl-auto=validate`, then performs a real save+read. If
`ProcessingRunEntity`'s mapping ever drifts from the DDL file, this test fails Hibernate's schema
validation immediately rather than the drift only being discovered against a real Postgres
deployment. Getting this test working also surfaced a real, pre-existing, unrelated finding: the
framework's own `agent_execution_record` manual DDL uses a Postgres partial index
(`create unique index ... where ...`), which is valid, correct PostgreSQL but not something H2's
PostgreSQL-compatibility mode can execute — not a defect in that script, just a limit of testing
against H2, and the reason this test is scoped to a bespoke minimal context rather than the full
application.

## 12. Remaining open questions for you to confirm

1. **Fact store**: confirm building a durable per-shipment fact store on `qubere-agent-storage` is
   in scope now or deferred until `qubere-compliance-agent` is actually started. Section 5 flags it
   as a real, not-yet-built gap either way, and section 7 above notes it's now clearly shared
   infrastructure between two services rather than one.
2. **LLM provider**: the TypeScript system is built entirely on Gemini (`@google/genai`, structured
   output via its `Type`/`Schema` API). `qubere-agents`' `AgentAiClient` is provider-neutral via
   Spring AI; confirm whether Gemini specifically needs to be wired in (Spring AI has a Vertex AI
   Gemini starter) or whether an equivalent OpenAI/Anthropic model is an acceptable substitute for
   the Java port.
3. ~~**Document byte retrieval**~~ — **done, see §14**: `document-agent.storage.type=local-disk`
   plus `POST /api/documents` closes this; cloud object storage remains a deliberately deferred
   extension.
4. **Email ingestion** (§3.2 item 6): confirm whether this is in scope for the initial document-agent
   port or a later phase — it requires a Java email-provider client, which is new infrastructure.

## 13. Extraction pipeline wired end-to-end: parse → active result → `document.intelligence`

The single biggest gap from the last assessment (`DocumentIntelligenceAgent` and
`DocumentContextLookupTool` were untouched scaffolds, never reachable from a real parse) is now
closed for the "documents already exist somewhere reachable" case. What was built:

- **`DocumentContext` / `QubereDocumentContextBuilder`** (`document/context/`): assembles a
  `NormalizedParserResult`'s sections/tables into budgeted extraction context, reusing the existing
  `DocumentChunkingService`. Deliberately **not** equivalent to the source's
  `qubereDocumentContext.ts`: the source orders chunks by purpose-specific relevance
  (`orderChunksForPurpose()`) before budget selection; this builder selects in document order
  (sections then tables) since purpose-aware ranking wasn't ported. A documented, deliberate scope
  reduction, not a silent behavioral drift.
- **`DocumentParseResultEntity`/`Repository`/`Service`** (`document/processing/`): the current
  active normalized parse result per document, keyed by `document_id` — each new qualifying parse
  simply replaces the row, mirroring `promoteToActive()` without a separate version pointer.
  Deliberately a single artifact type, not the source's six-artifact `artifactStore.ts` (canonical
  JSON, normalized JSON, markdown, tables JSON, per-table HTML, quality report) — only what the
  context builder needs.
- **`DocumentContextLookupTool`** rewired from an echo-back placeholder to a real lookup: fetches
  the active parse result and builds real context via the two pieces above.
- **`DocumentProcessingWorker`** now, on a qualifying parse: promotes the result to active via
  `DocumentParseResultService`, then immediately invokes `document.intelligence` with the
  assembled context — closing the loop from raw bytes to an extraction attempt without needing a
  separate poll/trigger step. Extraction failures are logged, never allowed to retract the
  processing run's own already-persisted `SUCCEEDED` state — the parse succeeded; failure to
  *extract* from it is a distinct, later concern.

**A real, framework-wide finding surfaced while building this, not specific to this module**:
verifying `DocumentParseResultEntity`'s DDL the same way `ProcessingRunEntity`'s was verified
(§11) exposed that Hibernate 6's `PostgreSQLDialect` maps a plain `@Lob` `String` field to
Postgres's `oid` large-object type, **not** `text` — so the `@Lob` + `text`-column convention
already used for roughly a dozen JSON columns across `qubere-agent-storage`'s persistence entities
(`AgentExecutionRecordEntity.inputJson`/`outputJson` and similar) would fail `ddl-auto=validate` at
startup against a **real** PostgreSQL database, not just in an H2 test. This was previously
unverified because no existing test ran `ddl-auto=validate` against a `PostgreSQLDialect` for any
`@Lob` column — `AgentPersistenceH2Test` uses `ddl-auto=create-drop`, which never exercises this
path. `DocumentParseResultEntity.normalizedResultJson` was fixed to use
`@JdbcTypeCode(SqlTypes.LONGVARCHAR)` instead of `@Lob`, which validates correctly against `text`
on both `PostgreSQLDialect` and H2's PostgreSQL-compatibility mode; `ProcessingRunDdlValidationTest`
now proves this for the new entity. **The dozen pre-existing entities were not changed** — that is
a broader, out-of-scope fix with no live PostgreSQL instance available in this environment to
verify against, but it is a real production-readiness risk worth a dedicated follow-up before the
`postgres` profile is used for real.

**Still not done, and the one remaining hard blocker to running this against a real uploaded
document**: `DocumentBytesSource` is still `NotConfiguredDocumentBytesSource` — nothing implements
actually fetching a document's raw bytes for submission to the parser. Everything from submission
through extraction now works against a `DocumentBytesSource` implementation once one exists; that
implementation itself is unstarted (see question 3 above).

## 14. `DocumentBytesSource` implemented: local-disk storage plus a real submission endpoint

Closes the last hard blocker from §13. Two things were missing, not one: an actual byte-storage
backend, and any way at all to get bytes and a processing run into the system in the first place —
`ProcessingRunService.enqueue()` previously had no caller except tests.

- **`document/storage/LocalDiskDocumentStorage`** implements both `DocumentBytesSource` (read side,
  consumed by `DocumentProcessingWorker`) and a new `DocumentBytesWriter` interface (write side,
  consumed by the submission endpoint below) from a single instance: one directory per document id
  under a configured root, holding the raw bytes plus a small metadata file for filename/MIME type.
  Deliberately **not** a port of the source's `loadDocumentBytes.ts`, which reads from a sibling
  Next.js app's own quarantine directories, a database-recorded object-storage URL, and a database
  `rawContent` column, in that order — none of which this module owns or has reason to replicate,
  since this framework's agents are separate services that receive documents through their own API
  rather than reaching into another application's database and filesystem layout. What *is*
  preserved from the source is the shape of the safety guard: a validated document id and a
  filename reduced to its basename, with the resolved path asserted to stay inside the configured
  root.
- Cloud object storage (S3/GCS/Azure Blob) is a **deliberately deferred extension**, not built —
  this framework has no existing object-storage abstraction, and inventing one with no live
  bucket/credentials to verify against would be exactly the kind of speculative infrastructure this
  project avoids elsewhere (same reasoning already recorded for the deferred Kafka/RabbitMQ/SQS
  async-queue adapters in the production-readiness roadmap). A deployment that needs it supplies its
  own `DocumentBytesSource`/`DocumentBytesWriter` bean; `document-agent.storage.type=local-disk`
  (real and testable, viable for single-instance or shared-volume deployments) is the default,
  alongside `type=none` which keeps today's `NotConfiguredDocumentBytesSource` behavior.
- **`DocumentStorageAutoConfiguration`** resolves the configured backend — the single place a
  `DocumentBytesSource` bean is constructed, mirroring `DocumentParserProviderAutoConfiguration`'s
  existing convention. Only one bean is registered (not a separate `DocumentBytesWriter` bean too):
  Spring resolves `getBeanNamesForType` against an already-created singleton's actual class, so a
  second bean method returning the same instance cast to `DocumentBytesWriter` made both beans match
  lookups for either interface, an ambiguous-bean error hit and fixed during this work. Callers that
  need to write (the submission controller) check `instanceof DocumentBytesWriter` instead.
- **`DocumentSubmissionController`** (`POST /api/documents`) is the new entry point: accepts raw
  document bytes as the request body (not `multipart/form-data`, to stay trivially callable from any
  HTTP client without a multipart codec — filename/MIME type move to `X-Document-Filename`/
  `X-Document-Mime-Type` headers instead of form fields), stores them via `DocumentBytesWriter`, and
  calls `ProcessingRunService.enqueue(...)`. The document id is **generated here**, not accepted from
  the caller — unlike the TS source, where `documentId` is an existing database primary key created
  earlier in that system's own upload flow. This module does not own a "document" domain entity
  upstream of processing, so there is no pre-existing id to accept; an orchestrating caller that
  needs to correlate this id with its own record captures it from the response.
- No new manual DDL was needed for this: local-disk storage has no database table of its own.

With this, the full path — `POST /api/documents` → `DocumentProcessingWorker` submit/poll/complete →
quality gate → `DocumentParseResultService.promoteToActive` → `document.intelligence` — is real and
exercised end-to-end by `DocumentSubmissionControllerTest`, not just unit-tested piece by piece. The
two flagged, deliberately out-of-scope follow-ups remain: cloud object storage as an alternative
backend, and the broader `@Lob`+`text` Postgres-validation finding from §13 across
`qubere-agent-storage`'s other entities.

## 15. Phase 3 (Intake pipeline): `document.intake` wired into submission, unassigned-intake guard

Closed the remaining Phase 3 scope. Two findings first (§3.3.1): the catalog-first fix for
`DocumentIntakeAgent` was already done in an earlier pass and this document's §3.3 note calling it
outstanding was simply stale — corrected in place rather than redone. `documentIntake.service.ts`
turned out to be a 9-line pass-through with nothing to port. That left two real gaps:

1. **`document.intake` was never actually invoked by anything except tests.** `DocumentSubmissionController`
   enqueued a processing run directly; the classification agent and the extraction pipeline were two
   islands that happened to share a module, never connected. The controller now calls
   `document.intake` (best-effort — a classification failure is logged and never blocks enqueueing;
   intake is a signal for operators, not a gate on whether extraction proceeds) immediately after
   storing an upload's bytes, before enqueueing the processing run.
2. **No caller could say which shipment a document belonged to at all.** `ProcessingRunEntity` had no
   `shipment_id` column, and the submission endpoint had no way to accept one. Added `shipment_id` to
   `ProcessingRunEntity` (nullable, migrated in place in `001_document_processing_run.sql` since
   nothing has been deployed against it yet — indexed for future shipment-scoped queries) and
   threaded it through `ProcessingRunService.enqueue(...)`, the OCR-retry re-enqueue path in
   `DocumentProcessingWorker`, and the `document.intelligence` input map built in `runExtraction(...)`.

**`UnassignedIntakeEntity`/`Repository`/`Recorder`** (`document/intake/`) port `unassignedIntake.ts`
faithfully, including its central rule verbatim from the source comment: *"The resolver used to
guess the account's newest shipment. A document filed against the wrong shipment is worse than one
that was never filed, because nothing in the record says the target was a guess."* Accordingly,
`DocumentSubmissionController` now requires an `X-Shipment-Id` header to proceed to processing —
when it is absent, the upload's bytes are still stored (nothing is lost) but no `ProcessingRunEntity`
is created and `document.intake` is not invoked; an `UnassignedIntakeEntity` is recorded instead, for
an operator to resolve. A dedicated table in this module (`unassigned_document_intake`,
`003_unassigned_document_intake.sql`), not a shared "exceptions" table in `qubere-agent-storage` —
no shared exception-queue concept exists in this framework yet, and inventing one speculatively for
a single caller would repeat the same mistake already avoided for cloud object storage (§14) and the
still-open fact-store question (§12 Q1). Promoting it to shared infrastructure is a deliberate future
decision if `qubere-compliance-agent` ends up needing an equivalent queue, not something to guess at
now.

**`intakePipelineRouter.ts`'s TMS/MOVE branch was deliberately not ported.** It dynamically imports
and calls a *different application's* TMS freight-extraction module by relative path — reaching into
a sibling Next.js app's module tree, which is exactly what a separate agent service should not do
(§7's module boundary decision). Only its Customs/default branch (dispatch to Document Intelligence)
had a real Java equivalent to build, and that is now item 1 above. If TMS-sourced documents ever need
handling here, that is a `qubere-compliance-agent`-or-later question, not a reason to reach across
applications from this module.

Full reactor (`mvn -o clean test`) is green across all 5 modules after this batch.

## 16. What's left in the phased plan

- **Phase 4 remainder — the real `document.intelligence` extraction logic.** Still the single
  largest specific gap (§3.2 item 5): the current agent is a ~130-line scaffold against a 900+ line
  TS source (prompt construction, structured output schema, line-item extraction). The context is
  now real (§13); the extraction reasoning itself is not.
- **Phase 5 remainder**: duplicate detection (`duplicateDetection.ts`), currency
  extraction/normalization (`extractedCurrency.ts`, including its null-on-disagreement rule),
  the extraction-review workflow (`extractionReview.ts`, 80% review threshold, human-correction-wins
  semantics), and the `advanceProcessing.ts` request-driven queue-drain hook (currently the worker
  only runs on its fixed schedule tick, so a document can sit for up to one tick's worth of avoidable
  latency after upload).
- **Email ingestion** — deliberately last; the source's own "distinct, larger effort requiring new
  infrastructure" characterization (§3.2 item 6) still holds; needs a Java email-provider client.
- **Three open questions (§12), still unresolved**: the fact store, the LLM provider (no vision-model
  integration exists yet at all, which is also why §3.3's Gemini-vs-fallback distinction has no Java
  analogue currently), and email ingestion's scope/timing.

## 17. Phase 4 completed: real `document.intelligence` extraction logic

Replaced the ~130-line scaffold with a real, evidence-grounded extraction agent — closing §3.2 item
5, previously the single largest specific gap. Not a line-for-line port of the 900+ line TypeScript
source; three capabilities are deliberately absent, each because it depends on something this pass
does not build (all documented in the class javadoc, not just here):

1. **Visual/layout analysis** (stamps, seals, handwriting, bounding boxes) — the source calls
   Gemini's multimodal vision API directly on the document image. This pipeline's context is parsed
   OCR text and tables (`QubereDocumentContextBuilder`), not raw image bytes, and no vision-capable
   model is wired in yet (§12 Q2). The new prompt is honest about reading text, not instructing a
   text model to pretend it saw a stamp.
2. **Filing/agency determination** (CBP/FDA/USDA/etc. routing) — the source itself says this belongs
   to downstream agents (Product Intelligence, HTS Classification, Compliance & Audit Risk) that
   this framework has not built; fabricating an agency determination with nothing to consume it
   would be evidence with no purpose.
3. **Entity/relationship graph extraction** — the source's `EntityResolutionService`/
   `ShipmentPartyService` consumers do not exist here either.

What **is** preserved: the non-negotiable grounding rules (ground every value in supplied context;
null beats a guess; an unreadable document is reported `failed`, never padded with placeholder data;
low-confidence values are reported, not discarded), the document-type taxonomy, the full 28-field
`TradeMetadata` shape, and structured line items — through `AgentAiClient`'s provider-neutral
structured output rather than a Gemini-specific `responseSchema`.

**A real bug fixed along the way, not just a rewrite**: the previous scaffold's no-AI-client
placeholder returned `""` for every unknown field. That is exactly the fabricated-absence
anti-pattern §3.4 warns against ("no confidence becomes 0 or 50 ... absence is preserved, never
fabricated") — an unconfigured AI provider is not evidence of an empty document, it is evidence of
nothing at all. The placeholder (and every unset AI-returned field) is now `null` throughout,
enforced by nullable boxed types (`Integer`/`Double`, not primitives) in every response record.

**Review threshold**: reuses `DocumentIntakeAgent`'s own 70% cutoff for internal consistency —
deliberately *not* the separate 80% correction-flag threshold Extraction Review uses (§3.4's
explicit warning against conflating the two remains honored; Extraction Review itself is still
unbuilt, see §16).

**Tests**: `DocumentIntelligenceAgentTest` covers the not-configured placeholder (nulls, not empty
strings), a blank-context guard that never calls the model at all, high- and low-confidence
extraction gating, and `extractionStatus=failed` forcing review regardless of a high confidence
score. `DocumentProcessingWorkerTest` was extended to assert, after a full submit→poll→complete
cycle, that the parse result was actually promoted to active *and* that a real
`document.intelligence` execution record exists — the full pipeline wiring from §13/§14, not just
each piece in isolation. Full reactor (`mvn -o clean test`) is green across all 5 modules.

Phase 4 is now complete. What remains is Phase 5 (§16) and the three open questions.

## 18. Phase 5 completed: duplicate detection, currency agreement, extraction review, immediate processing

Closes the last named item in §4's phased plan except email ingestion (§16 explicitly deferred that
as its own larger effort). Four pieces, each ported with a deliberate adaptation to how this module
actually stores things — none of them a blind line-for-line port:

**Duplicate detection** (`document/duplicate/`) — `duplicateDetection.ts` ported as
`DuplicateDetectionService`. Reuses a new `ProcessingRunEntity.contentSha256` column (SHA-256
computed once at submission time in `DocumentSubmissionController`) rather than hashing bytes twice
or inventing a separate "document" table this module doesn't otherwise need.
`CrossShipmentDuplicate` deliberately omits the source's `shipmentNumber`/`fileName` (resolved there
via joins into `Shipment`/`ShipmentDocument` tables this module does not own) — a caller that does
own that data can resolve a label from the returned `documentId`/`shipmentId`. One correctness fix
beyond the source: results are de-duplicated by `documentId` before the same-shipment filter, so a
document with more than one `ProcessingRunEntity` row (an OCR-retry re-enqueue creates a new run for
the same document) is never reported as its own duplicate. Still a non-blocking signal only — the
upload always proceeds regardless — surfaced in `DocumentSubmissionResponse.crossShipmentDuplicates`.

**Currency agreement** (`document/currency/`) — `extractedCurrency.ts` ported as
`CurrencyNormalizer` (pure symbol/word → ISO-4217 normalization) and `CurrencyAgreement` (pure
null-on-disagreement logic), with `CurrencyExtractionService` as the one storage-touching caller.
Adapted, not copied: the source reads a raw `extractedJson` blob per document and searches six JSON
paths for a currency value; this reads the same currency value from
`ExtractionReviewService`'s per-field storage instead (below), since extraction output is already
recorded field-by-field here rather than as one opaque blob per document. The null-on-disagreement
rule is preserved exactly: zero or two-or-more distinct currencies across documents returns `null`,
never a guessed code.

**Extraction review** (`document/review/`) — `extractionReview.ts` ported field-for-field as
`ExtractionReviewFields` (pure: grouping, newest-human-correction-wins precedence, best-machine-read
tie-breaking, bounding-box parsing, wrap-around review navigation, correction validation — no
database, exercised directly by tests, same as the source's own design note), backed by a new
append-only `ExtractionFieldEntity`/`extraction_field` table and `ExtractionReviewService`. A
correction is always a **new row**, never an update — the machine's original reading is never
overwritten. `DocumentIntelligenceAgent` now calls `ExtractionReviewService.recordMachineReadings`
(best-effort; a recording failure never fails the extraction itself) with its 28 trade-metadata
fields flattened into individual readings — a real, acknowledged simplification: there is no
field-level confidence in this pass's extraction schema (only the overall
`documentClassification.confidence`), so every field's confidence is recorded as that same overall
score rather than an independently-scored value the source's vision-based extraction could produce.
`DocumentReviewController` (`GET`/`POST /api/documents/{documentId}/review`) is the human-facing
surface the pure logic needed.

**Immediate processing** (`advanceProcessing.ts`'s intent) — adapted, not copied: the source defers
a queue drain until after the HTTP response is flushed, via a Next.js request-scoped `after()` hook,
which Spring MVC has no equivalent primitive for. `DocumentSubmissionController` instead runs one
`DocumentProcessingWorker.tick()` synchronously right after enqueueing, before the response returns
— trading a little response latency (one submission attempt) for the same underlying guarantee: a
newly uploaded document is not left waiting for the next `@Scheduled` interval. A tick failure here
is caught and logged, never allowed to fail the upload response — the run is already durably
enqueued regardless, and the scheduled tick remains the reliable fallback.

**A real bug avoided, not introduced**: `ExtractionFieldEntity`'s value column could not be named
`value` — H2 (and effectively every SQL dialect) treats it as a reserved word, which surfaced
immediately as every review-service test failing with a SQL syntax error. Renamed to `field_value`
in the entity, both DDL scripts, and re-verified.

New DDL: `db/manual/{postgres,oracle}/004_extraction_field.sql` (`ExtractionFieldDdlValidationTest`),
plus `content_sha256` added in place to `001_document_processing_run.sql` (no new file, since nothing
has been deployed against the old shape yet). Full reactor (`mvn -o clean test`) is green across all
5 modules.

What remains: email ingestion (deliberately last, needs a new Java email-provider client) and the
three still-open questions in §12 (fact store, LLM provider, email ingestion's scope/timing).
