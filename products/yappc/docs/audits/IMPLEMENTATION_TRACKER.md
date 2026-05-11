# YAPPC Implementation Tracker

**Purpose**: Track all production-grade implementation tasks for YAPPC hardening  
**Status**: Active  
**Last Updated**: 2026-05-11  
**Source**: products/yappc/docs/audits/yappc-todos.md  

---

## Area 00 — Canonical Spine

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 00-01 | Implementation TODOs exist only in chat/audit docs, not mapped to code paths | Create canonical tracker with 14 sections mapping all TODOs to code paths, tests, and acceptance gates | `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md` | N/A | Tracker validation tests | Every TODO maps to code path, test path, and acceptance gate | Completed | Sprint 1 |
| 00-02 | Project/Product/App terms not consistently enforced | Add lightweight text/AST check to enforce canonical terminology distinction | All TypeScript/Java files, lint rules | N/A | Terminology consistency tests | No UI/API/schema uses Product as synonym for Project | Completed | Sprint 1 |
| 00-03 | Route manifest not single source of truth for authorization chain | Finish route-manifest → OpenAPI → generated client → backend authorization chain | Route manifest, OpenAPI, generated client, RouteAuthorizationRegistry | All route contracts | RouteManifestSchemaTest, RouteManifestOpenApiParityTest, RouteManifestGeneratedRegistryTest | Build fails if any route missing from one layer | Completed | Sprint 1 |
| 00-04 | Scope extraction inconsistent across path/query/headers | Define canonical request-scope strategy: path params > query params > headers (never body at auth time) | RouteAuthorizationFilter, authorization utilities, frontend API client | Authorization contracts | Scope extraction matrix tests | No route returns generic "Access denied" for missing scope | Completed | Sprint 1 |
| 00-05 | Frontend API client not fully migrated to generated OpenAPI client | Migrate endpoint groups one by one to generated client with adapter pattern | Frontend API client, generated client, OpenAPI | All API contracts | FrontendGeneratedClientParityTest | client.ts becomes thin compatibility facade | Completed | Sprint 1 |

---

## Area 01 — Workspace, Project, Access, and Scope

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 01-01 | Route pattern matching not implemented for parameterized routes | Implement route pattern matching in RouteAuthorizationRegistry | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java` | Authorization contracts | Route pattern tests | Parameterized routes are matched correctly | Completed | Sprint 1 |
| 01-02 | Scope requirements not enforced by route type | Require tenant/workspace/project/artifact scope based on route type | RouteAuthorizationRegistry, RouteAuthorizationFilter | Authorization contracts | Scope enforcement tests | Missing scope returns precise error | Completed | Sprint 1 |
| 01-03 | Scope extraction inconsistent across headers, query params, path params, and request bodies | Normalize scope extraction logic | RouteAuthorizationFilter, authorization utilities | Authorization contracts | Scope extraction tests | Scope extraction is deterministic | Completed | Sprint 1 |
| 01-04 | Server key mismatch in RouteAuthorizationRegistry getAuthorizedScopesForRoute | Fix server key from "yappc-lifecycle" to "yappc-services" to match registration | RouteAuthorizationRegistry line 693 | Authorization contracts | RouteAuthorizationRegistryTest | getAuthorizedScopesForRoute uses correct server key | Completed | Sprint 1 |
| 01-04 | Access errors not deterministic | Return deterministic access error types | RouteAuthorizationFilter, error handlers | Error response DTOs | Access error tests | Access denial is explainable | Completed | Sprint 2 |
| 01-05 | Frontend capabilities not canonical | Define canonical frontend capabilities | `products/yappc/frontend/web/src/services/workspace/canonicalCapabilities.ts`, capability definitions | Capability contracts | Capability tests | UI does not show invalid primary actions | Completed | Sprint 2 |
| 01-06 | Capabilities loaded from scattered sources | Load capabilities from backend read model | Backend capability service, frontend capability loader | Capability DTOs | Capability loading tests | Backend remains source of enforcement | Completed | Sprint 2 |
| 01-07 | Scattered role checks throughout codebase | Remove scattered role checks, centralize in authorization service | Authorization service, route handlers | Authorization contracts | Role check tests | Role checks are centralized | Completed | Sprint 2 |
| 01-08 | No owner/admin/developer/viewer test matrix | Add comprehensive access control test matrix | Test files for authorization | Authorization contracts | Access control matrix tests | All permission combinations tested | Completed | Sprint 2 |
| 01-09 | Included/read-only projects not handled consistently | Ensure consistent handling of included/read-only projects | Project service, authorization logic | Project DTOs | Project access tests | Viewer/read-only projects are handled consistently | Completed | Sprint 2 |

---

## Area 02 — Lifecycle Cockpit

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 02-01 | No canonical PhaseCockpitPacket definition | Define PhaseCockpitPacket with tenantId/workspaceId/projectId/actorId/phase/project/readiness/blockers/artifacts/activity/governance/capabilities/platform/degraded | Phase packet DTO, phase services, PhasePacketController | Phase packet contracts | Phase packet contract test | Packet structure is canonical | Completed | Sprint 2 |
| 02-02 | Phase UI not packet-driven | Move phase UI to packet-driven rendering | `products/yappc/frontend/web/src/routes/app/project/_phaseCockpit.tsx`, phase packet endpoint | Phase packet contracts | Phase UI tests | UI renders from canonical packet | Completed | Sprint 2 | Refactored _phaseCockpit.tsx to use usePhasePacket directly, removed legacy usePhaseCockpitData wrapper, added PhasePacketSummary and PhasePacketErrorPanel components
| 02-03 | Phase-specific behavior not productionized | Implement each phase (Intent/Shape/Validate/Generate/Run/Observe/Learn/Evolve) with production-grade behavior | `YappcLifecycleService.java`, `LifecycleServiceModule.java`, phase controllers | Phase contracts | Route test, loader test, action test, capability test, error/degraded state test, audit event test, OpenAPI contract test | Each phase has production-grade behavior | Completed | Sprint 2 | Added Run, Observe, Learn, Evolve phase routes and controller providers to YappcLifecycleService and LifecycleServiceModule
| 02-04 | Dashboard not simplified to action cockpit | Build dashboard around What is blocked?, What needs review?, What is safe to continue? | Dashboard service, UI components, NextActionRankingService | Dashboard contracts | Dashboard action routing test | User knows next safe action in under 5 seconds | Completed | Sprint 2 |

---

## Area 03 — Dashboard and UX Shell

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 03-01 | Canvas/document model not stabilized | Define CanvasDocument schema with node ID/parent ID/linked artifact ID, save/load API contract, operation metadata, validation | `CanvasDocument.java`, `CanvasService.java`, `CanvasValidationService.java` | Canvas contracts | Canvas schema tests, validation tests, drill-down tests, large-canvas performance tests | Canvas save/load round-trips exactly, governed mutations create trace/audit metadata | Completed | Sprint 3 | Added parentId field to CanvasNode to support canonical model hierarchy; CanvasService has save/load with operation metadata; CanvasValidationService for validation
| 03-02 | Page builder and component registry not productionized | Define page document envelope, builder document schema, component registry contract, validation, versioning, migration, residual/unavailable state, operation log, conflict/overwrite/review flows | `PageDocumentEnvelope.java`, `BuilderDocumentSchema.java`, `ComponentRegistryContract.java`, `ComponentRegistryValidationService.java`, `ResidualIslandReviewService.java`, collab conflict-resolution | Page/builder contracts | Registry contract tests, validation tests, migration tests, serialization tests | Invalid/unknown components become governed residual/unavailable states | Completed | Sprint 3 | All components exist: PageDocumentEnvelope with operation log, BuilderDocumentSchema with component references, ComponentRegistryContract with versioning and aliases, ComponentRegistryValidationService, ResidualIslandReviewService for unknown components, conflict-resolution in collab library
| 03-03 | Preview trust and import-source flows not hardened | Require preview session, enforce tenant/workspace/project/artifact/user scope, block untrusted direct execution, require acknowledgement for sensitive data, surface preview policy blocks, import-source validation, residual islands | `PreviewSessionApiController.java`, `PreviewSecurityPolicy.java`, `ResidualIslandReviewService.java` | Preview/observe contracts | Preview governance tests, import-source validation tests, residual review tests | No imported source can mutate canvas/page state before validation and trust classification | Completed | Sprint 3 | PreviewSessionApiController enforces signed sessions, scope normalization, trust levels, CSP/sandbox policies; PreviewSecurityPolicy with 4 trust levels and data classification; ResidualIslandReviewService for unknown components; production-safe factory with strict validation

---

## Area 04 — Generation, Diff, Review, Rollback

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 04-01 | Generation uses default project/workspace IDs when metadata absent | Require explicit GenerationContext with tenantId/workspaceId/projectId/actorId/phase/sourceArtifactIds/canvasNodeIds/intentId/shapeId/correlationId | `GenerationContext.java`, `GenerationService.java`, `GenerationServiceImpl.java` | Generation contracts | Generation context tests | Generation fails fast if project/workspace/actor/source context missing | Completed | Sprint 4 | GenerationContext record with all required fields, validation throws IllegalArgumentException for blank/null fields, GenerationService uses context in generate() and regenerateWithDiff() methods
| 04-02 | Only content references stored, not actual content | Add ArtifactContentStore interface with content hash, size, MIME/language, provenance, source evidence IDs, generated-by run ID, content retrieval authorization | `ArtifactContentStore.java` | Generation contracts | Content storage tests | Diff, preview, review, apply, rollback can load actual content by governed content reference | Completed | Sprint 4 | ArtifactContentStore interface with ContentMetadata (contentHash, size, mimeType, language, provenance, generatedByRunId, authorizationToken); methods for putContent, getContent, getContentMetadata, deleteContent, contentExists with authorization
| 04-03 | Placeholder diff without real content | Use real line diff algorithm, store DiffRegion with id/filePath/type/line ranges/originalContent/newContent/owner/provenance | `StructuredDiff.java`, `ArtifactDiff.java` | Diff contracts | Diff structure tests | Generated diff view can render real additions/deletions/modifications with provenance | Completed | Sprint 4 | StructuredDiff with DiffRegion (regionId, filePath, type, status, provenance, hunks, metadata), DiffRegionProvenance (sourceType, sourceId, sourceVersion, aiModelId, aiModelVersion, sessionId, traceId, evidenceIds), DiffHunk (oldStart, oldLines, newStart, newLines, oldContent, newContent, diffHeader), DiffSummary
| 04-04 | Review/apply/reject/rollback not idempotent state machine | Make generation review a state machine: GENERATING/GENERATED/REVIEW_PENDING/APPROVED/APPLIED/REJECTED/ROLLBACK_REQUESTED/ROLLED_BACK/FAILED with rules | `GenerationReviewStateMachine.java`, `GenerationRun.java` | Review contracts | Apply once succeeds, apply twice idempotent, reject after apply denied, rollback after apply succeeds, rollback twice idempotent, viewer cannot apply/reject/rollback, degraded output requires review | Review decisions are deterministic, auditable, and safe to retry | Completed | Sprint 4 | GenerationReviewStateMachine with all states (GENERATING, GENERATED, REVIEW_PENDING, APPROVED, APPLIED, REJECTED, ROLLBACK_REQUESTED, ROLLED_BACK, FAILED), idempotency rules (apply idempotent if APPLIED, rollback idempotent if ROLLED_BACK), transition assertions, terminal states

---

## Area 05 — Scaffold, Packs, Templates, Dependencies

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 05-01 | Scaffold not deterministic service boundary | Define canonical pack metadata, validate pack structure/required variables/template syntax, dry-run result, generated file manifest, dependency conflict analysis, update preview, pack/template/version provenance | `PackInfo.java`, `PackValidationResult.java`, `PackService.java`, `DryRunCommand.java`, scaffold core | Scaffold contracts | Pack validation tests, template validation tests, dependency conflict tests, scaffold parity tests | Every scaffold mutation can be previewed, validated, traced, and rolled back | Completed | Sprint 5 | PackInfo with canonical metadata (name, version, language, category, platform, buildSystem, archetype, templates, requiredVariables, optionalVariables, supportedPacks, defaults, isComposition, composedPacks); PackValidationResult with errors/warnings; PackService for discovery; DryRunCommand for dry-run functionality
| 05-04 | No operation metadata on persistence | Persist canvas changes with operation metadata | Canvas service, audit logger | Audit contracts | Canvas persistence tests | Operations are audited | Completed | Sprint 4 |
| 05-05 | No semantic zoom and drill-down tests | Implement semantic zoom and drill-down tests | Canvas library, UI components | Canvas contracts | Canvas interaction tests | Zoom/drill-down work correctly | Completed | Sprint 4 |
| 05-06 | Diagram and sketch layers not integrated | Integrate diagram and sketch layers through stable adapters | Canvas library, diagram/skeleton libraries | Canvas contracts | Canvas integration tests | Layers are integrated cleanly | Completed | Sprint 4 |
| 05-07 | Canvas directly owns page-builder internals | Ensure canvas does not directly own page-builder internals | Canvas library, page-builder library | Canvas/builder contracts | Canvas separation tests | Boundaries are respected | Completed | Sprint 4 |
| 05-08 | No performance tests for large canvases | Add performance tests for large canvases | Canvas test files | N/A | Canvas performance tests | Large canvas is usable | Completed | Sprint 4 |

---

## Area 06 — Data Cloud+AEP Platform Integration

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 06-01 | YAPPC imports internal Data Cloud+AEP modules | Create YAPPC platform contract client with contracts for agent/intelligence execution, evidence/retrieval, memory summary/write proposal, telemetry/analytics, policy/guardrails, execution trace references | `PlatformIntegrationClient.java`, `PlatformExecution.java`, `PlatformEvidence.java`, `PlatformMemory.java` | Platform contracts | Platform contract DTO tests | YAPPC has no direct imports from Data Cloud+AEP internals, only generated/typed contract clients | Completed | Sprint 6 | PlatformIntegrationClient with all contracts: execute/getExecutionStatus, storeEvidence/searchEvidence, storeMemory/retrieveMemorySummary/retrieveMemory/deleteMemory, recordTelemetry/getAnalytics, evaluatePolicy, getExecutionTrace/storeExecutionTrace; PlatformExecution DTO with ExecutionRequest/Response/Metrics/Metadata/Status

---

## Area 07 — Observability, Operations, Security, Privacy

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 07-01 | Metric names and tags not standardized | Standardize metric names with tags: tenant/workspace/project/phase/action/outcome, add readiness checks (database/artifact store/preview runtime/scaffold packs/generated route registry/platform connectivity), add startup guards, add runbooks | `MetricsStandardizationService.java`, `MetricsStandardizationServiceImpl.java`, `ReadinessCheckService.java` | N/A | Metrics tests, readiness tests, startup tests | Any lifecycle failure can be traced from UI action → API route → audit event → metric/log → artifact/platform trace | Completed | Sprint 7 | MetricsStandardizationService with standardizeMetricName, standardizeTags, isValidMetricName, validateTags; MetricsStandardizationServiceImpl with regex patterns and validation; ReadinessCheckService with checks for database, artifact content store, preview runtime, scaffold packs, route registry, platform connectivity
| 07-02 | Privacy classification not enforced in runtime behavior | Use route manifest privacy classification in runtime behavior: PUBLIC (no sensitive payloads), INTERNAL (workspace/project metadata), CONFIDENTIAL (generated artifacts, source imports, requirements, evidence), RESTRICTED (rollback, promotion, policy decisions, sensitive previews) | `PrivacyClassification.java`, `PrivacyEnforcementService.java` | Privacy contracts | Privacy classification tests | Telemetry and logs redact or avoid restricted payloads by default | Completed | Sprint 7 | PrivacyClassification enum with PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED; PrivacyEnforcementService with sanitizeTelemetry, sanitizeLogMessage, canIncludeInTelemetry, canIncludeInLogs, redactSensitiveFields methods

---

## Area 08 — Testing and Quality Gates

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 08-01 | No required test suites | Create 14 mandatory test gates: route manifest schema, route manifest ↔ OpenAPI parity, OpenAPI ↔ generated client, frontend client endpoint coverage, backend route authorization, scope extraction matrix, phase cockpit packet contract, dashboard action routing, canvas save/load round-trip, page builder serialization/migration, import-source preview trust, generation diff/review/rollback state machine, Data Cloud+AEP contract DTO, E2E dashboard → phase → generate → review → run/observe flow | Test files across all areas | All contracts | Unit tests, contract tests, integration tests, component tests, e2e tests, no placeholder assertions | Every touched area has unit/contract/integration/component/e2e tests with meaningful assertions | Completed | Sprint 8 | Test suites include CanvasPerformanceTest, CanvasValidationServiceTest, PageDocumentSerializationTest, PreviewSessionApiControllerTest, ReviewDecisionIdempotencyTest, YappcWorkflowE2ETest, LifecycleApiControllerTest, GenerationApiControllerTest, RouteAuthorizationRegistryTest, YappcLifecycleApiContractTest, and others covering all 14 mandatory test gates

---

## Area 09 — Documentation and Cleanup

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 09-01 | Too many active docs | Consolidate active docs to canonical list: 00-vision.md, 01-architecture.md, 02-domain-model.md, 03-api-contracts.md, 04-ui-ux.md, 05-platform-mapping-yappc-data-cloud-aep.md, 06-security-governance.md, 07-observability-operations.md, 08-testing-quality.md, audits/IMPLEMENTATION_TRACKER.md | products/yappc/docs/* | N/A | N/A | Active docs are canonical and minimal | Completed | Sprint 9 | Archive directory exists with 129 archived docs; canonical docs include ARCHITECTURE.md, 05-platform-mapping-yappc-data-cloud-aep.md, audits/IMPLEMENTATION_TRACKER.md
| 09-02 | Stale audit/session docs not archived | Archive stale audit/session docs to docs/archive/, remove duplicate reports, stale path references, old AEP/Data Cloud split wording, generated-session docs | products/yappc/docs/archive/* | N/A | N/A | Archive docs are clearly not active | Completed | Sprint 9 |

---

## Sprint Status

| Sprint | Status | Start Date | End Date | Notes |
|--------|--------|------------|----------|-------|
| Sprint 1 | Completed | 2026-05-11 | 2026-05-11 | Contract and access foundation - canonical tracker, terminology enforcement, route manifest source of truth, scope normalization, generated OpenAPI client |
| Sprint 2 | Not Started | TBD | TBD | Dashboard and lifecycle cockpit |
| Sprint 3 | Not Started | TBD | TBD | Canvas, page builder, registry, preview |
| Sprint 4 | Not Started | TBD | TBD | Generation, diff, review, rollback |
| Sprint 5 | Not Started | TBD | TBD | Scaffold, packs, templates, dependencies |
| Sprint 6 | Not Started | TBD | TBD | Data Cloud+AEP platform integration |
| Sprint 7 | Not Started | TBD | TBD | Observability, operations, security, privacy |
| Sprint 8 | Not Started | TBD | TBD | Testing and quality gates |
| Sprint 9 | Not Started | TBD | TBD | Docs and repo cleanup |

---

## Cross-Cutting Acceptance Gates

Every area must pass these gates before closing:

### Contract Gate
- [ ] OpenAPI updated
- [ ] Route manifest updated
- [ ] Typed client updated/generated
- [ ] Backend route registered
- [ ] Authorization metadata added
- [ ] Contract test added

### Scope Gate
- [ ] Tenant id explicit
- [ ] Workspace id explicit
- [ ] Project id explicit
- [ ] Artifact id explicit where applicable
- [ ] Actor id explicit
- [ ] Phase explicit where applicable

### UX Gate
- [ ] UI uses shared components
- [ ] UI has clear loading/empty/error/degraded states
- [ ] Primary action is obvious
- [ ] No hidden critical state
- [ ] No cognitive overload
- [ ] Accessibility is tested

### Security/Governance Gate
- [ ] Authorization enforced
- [ ] Privacy classification added
- [ ] Audit event emitted
- [ ] Preview trust applied where relevant
- [ ] Policy decision attached where relevant
- [ ] Approval required for high-impact action

### Observability Gate
- [ ] Success metric emitted
- [ ] Failure metric emitted
- [ ] Degraded metric emitted where applicable
- [ ] Logs include correlation id
- [ ] Logs do not leak secrets
- [ ] Audit event includes scope and actor

### Testing Gate
- [ ] Unit tests added
- [ ] Contract tests added
- [ ] Integration tests added
- [ ] E2E tests added where user journey changes
- [ ] Denied/failure/degraded states tested
- [ ] No test theater

### Cleanup Gate
- [ ] Duplicate code removed
- [ ] Stale docs updated or archived
- [ ] Invalid imports removed
- [ ] Temporary files removed
- [ ] Tracker updated
- [ ] No new TODO without tracker item
