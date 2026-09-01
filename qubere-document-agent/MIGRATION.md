# Document Agent Migration Map

Source project: `C:\WorkSpace\app-frontend`
Target module: `qubere-document-agent`

This module is the Java/Spring Boot home for document-focused agents and document-processing services currently embedded in the TypeScript customs app.

## Migration boundary

Move document agent/runtime responsibilities here:

- document intake/classification
- parser submission and polling
- parser-provider adapters such as Docling/mock provider
- Qubere document-context construction
- document intelligence extraction
- extraction persistence callbacks/tools
- document-agent tests and prompt seeds

Keep these in `app-frontend` unless a later service split says otherwise:

- React/Next.js pages and components
- browser PDF viewer/highlight UI
- app navigation and settings UI
- user-session auth screens
- frontend-only presentation logic

Keep generic runtime concerns in framework modules:

- `agent-api`: stable contracts only
- `agent-core`: generic runtime, tool, prompt, queue, eval, observability abstractions
- `agent-storage`: generic agent runtime persistence

## Source-to-target map

| app-frontend source | qubere-document-agent target |
| --- | --- |
| `apps/custom/src/modules/intake/documentIntakeAgent.ts` | `document/DocumentIntakeAgent.java` |
| `apps/custom/src/modules/intake/documentTypeCatalog.ts` | `document/DocumentTypeCatalog.java` |
| `apps/custom/src/modules/agents/documentIntelligenceAgent.ts` | `document/DocumentIntelligenceAgent.java` |
| `apps/custom/src/modules/documents/processing/documentProcessingWorker.ts` | `document/processing/DocumentProcessingWorker.java` |
| `apps/custom/src/modules/documents/processing/processingRuns.ts` | `document/processing/DocumentProcessingRunService.java` |
| `apps/custom/src/modules/documents/processing/documentSource.ts` | `document/processing/DocumentSourceService.java` |
| `apps/custom/src/modules/documents/processing/malwarePolicy.ts` | `document/security/DocumentMalwarePolicy.java` |
| `apps/custom/src/modules/documents/parser/contracts.ts` | `document/parser/DocumentParserProvider.java` and parser DTOs |
| `apps/custom/src/modules/documents/parser/registry.ts` | `document/parser/DocumentParserProviderRegistry.java` |
| `apps/custom/src/modules/documents/parser/config.ts` | `document/parser/DocumentParserProperties.java` |
| `apps/custom/src/modules/documents/parser/qualityGate.ts` | `document/parser/DocumentQualityGate.java` |
| `apps/custom/src/modules/documents/parser/artifactStore.ts` | `document/parser/DocumentArtifactStore.java` |
| `apps/custom/src/modules/documents/parser/chunking.ts` | `document/parser/DocumentChunkingService.java` |
| `apps/custom/src/modules/documents/parser/ibm/*` | `document/parser/ibm/*` |
| `apps/custom/src/modules/documents/parser/mock/*` | `document/parser/mock/*` |
| `apps/custom/src/modules/documents/context/*` | `document/context/*` |
| `apps/custom/src/lib/documents/extractionSchemas.ts` | `document/extraction/DocumentExtractionSchemas.java` |
| `apps/custom/src/lib/documents/fieldDictionary.ts` | `document/extraction/DocumentFieldDictionary.java` |
| `apps/custom/src/lib/documents/classificationMapping.ts` | `document/classification/DocumentClassificationMapping.java` |

## First committed cut

The first Java cut intentionally adds a runnable service and two agent contracts before porting all side-effecting domain services:

- `document.intake`
- `document.intelligence`
- `document.context.lookup` tool placeholder

Next implementation step: port `DocumentTypeCatalog` and make `DocumentIntakeAgent` use the real catalog instead of filename heuristics.
