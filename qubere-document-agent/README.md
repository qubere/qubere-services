# Qubere Document Agent

Runnable Spring Boot service for document-focused agents migrated from `app-frontend`.

Initial agents:

- `document.intake` — Java scaffold for the TypeScript `DocumentIntakeAgent`.
- `document.intelligence` — Java scaffold for the TypeScript `DocumentIntelligenceAgent`.

The first cut intentionally preserves the agent contracts and framework wiring before moving all domain persistence and parser/provider adapters. The next migration step is to port the document parser/context layer from `app-frontend/apps/custom/src/modules/documents` into Java tools/services.

## Run locally

```powershell
mvn -pl qubere-document-agent -am spring-boot:run
```

Health check:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/actuator/health"
```

List agents:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/agents"
```
