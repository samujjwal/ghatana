# YAPPC Implementation Tracker

**Purpose**: Track all production-grade implementation tasks for YAPPC hardening  
**Status**: Active  
**Last Updated**: 2026-05-17  

---

## Area 00 — Canonical Spine

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 00-01 | Docs still describe AEP and Data Cloud as separate external products | Update all active docs to reflect merged Data Cloud+AEP platform product | `products/yappc/docs/05-platform-mapping-yappc-aep.md` → `products/yappc/docs/05-platform-mapping-yappc-data-cloud-aep.md`, all active docs | N/A | N/A | No active doc says AEP and Data Cloud are separate external products | Completed | Sprint 1 |
| 00-02 | Project/Product/App terms not consistently defined or enforced | Define canonical terminology in YAPPC_CANONICAL_MODELS.md and enforce across codebase | `products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md` | N/A | N/A | All canonical terms have one definition; code uses terms consistently | Completed | Sprint 1 |
| 00-03 | Lifecycle phase aliases may exist without canonical phase service | Ensure all code references lifecycle phases through canonical phase service | Phase service files, frontend phase routes | Phase DTOs | Phase consistency tests | All code references use canonical phase service | Completed | Sprint 1 |
| 00-04 | Artifact/page/builder/canvas relationships not formally defined | Define canonical relationships in YAPPC_CANONICAL_MODELS.md | `products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md` | Artifact DTOs | Artifact relationship tests | Relationships are documented and enforced | Completed | Sprint 1 |
| 00-05 | Preview trust levels not formally defined | Define canonical preview trust levels in YAPPC_CANONICAL_MODELS.md | `products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md` | Preview DTOs | Preview trust tests | Trust levels are defined and enforced | Completed | Sprint 1 |
| 00-06 | Governance trace dimensions not standardized | Define canonical governance trace dimensions in YAPPC_CANONICAL_MODELS.md | `products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md` | Audit event DTOs | Governance trace tests | Trace dimensions are standardized | Completed | Sprint 1 |
| 00-07 | No central implementation tracker | Create this IMPLEMENTATION_TRACKER.md | `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md` | N/A | N/A | Tracker is the single source of truth for implementation status | Completed | Sprint 1 |
| 00-08 | No automated checks for forbidden dependencies | Add architecture fitness rules to detect YAPPC importing internal Data Cloud+AEP modules | Build configuration, dependency analysis scripts | N/A | Architecture fitness tests | Build fails on forbidden dependency violations | Completed | Sprint 1 |

---

## Area 01 — Workspace, Project, Access, and Scope

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 01-01 | Route pattern matching not implemented for parameterized routes | Implement route pattern matching in RouteAuthorizationRegistry | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java` | Authorization contracts | Route pattern tests | Parameterized routes are matched correctly | Completed | Sprint 2 |
| 01-02 | Scope requirements not enforced by route type | Require tenant/workspace/project/artifact scope based on route type | RouteAuthorizationRegistry, RouteAuthorizationFilter | Authorization contracts | Scope enforcement tests | Missing scope returns precise error | Completed | Sprint 2 |
| 01-03 | Scope extraction inconsistent across headers, query params, path params, and request bodies | Normalize scope extraction logic | RouteAuthorizationFilter, authorization utilities | Authorization contracts | Scope extraction tests | Scope extraction is deterministic | Completed | Sprint 2 |
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
| 02-01 | No canonical PhaseCockpitPacket definition | Define PhaseCockpitPacket with all required fields | Phase packet DTO, phase services | Phase packet contracts | Phase packet tests | Packet structure is canonical | Completed | Sprint 3 |
| 02-02 | Phase UI not packet-driven | Move phase UI to packet-driven rendering | Frontend phase components, phase packet endpoint | Phase packet contracts | Phase UI tests | UI renders from canonical packet | Completed | Sprint 3 |
| 02-03 | Phase-specific action contracts not defined | Implement phase-specific action contracts | Phase action DTOs, phase services | Phase action contracts | Phase action tests | Each phase has typed actions | Completed | Sprint 3 |
| 02-04 | Actions not capability-gated | Add capability gating for every phase action | Phase action contracts, capability service | Capability contracts | Capability gating tests | Actions respect user capabilities | Completed | Sprint 3 |
| 02-05 | Blockers and readiness not displayed | Add blockers and readiness display to phase cockpits | Phase packet service, UI components | Phase packet contracts | Phase readiness tests | Blockers/readiness are visible | Completed | Sprint 3 |
| 02-06 | Evidence and governance not displayed | Add evidence and governance display to phase cockpits | Phase packet service, UI components | Evidence contracts | Phase evidence tests | Evidence/governance are visible | Completed | Sprint 3 |
| 02-07 | Data Cloud+AEP run status not referenced | Add platform run status references where platform actions are triggered | Phase packet service, platform integration | Platform run contracts | Platform run status tests | Platform run status is displayed | Completed | Sprint 3 |
| 02-08 | No per-phase tests | Add per-phase tests for Intent/Shape/Validate/Generate/Run/Observe/Learn/Evolve | Phase test files | Phase contracts | Phase-specific tests | All phases have test coverage | Completed | Sprint 3 |

---

## Area 03 — Dashboard and UX Shell

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 03-01 | Dashboard actions not backend-derived | Backend-derive dashboard actions | Dashboard service, dashboard DTOs | Dashboard contracts | Dashboard action tests | Actions are backend-derived | Completed | Sprint 3 |
| 03-02 | Actions not grouped by blocked/review/safe | Group actions into blocked/review/safe | Dashboard service, UI components | Dashboard contracts | Dashboard grouping tests | Actions are properly grouped | Completed | Sprint 3 |
| 03-03 | Multiple competing CTAs on cards | Show one dominant action per project | Dashboard service, UI components | Dashboard contracts | Dashboard CTA tests | No card has multiple competing CTAs | Completed | Sprint 3 |
| 03-04 | No reason labels on actions | Add reason labels to dashboard actions | Dashboard service, UI components | Dashboard contracts | Dashboard reason tests | Reason labels are displayed | Completed | Sprint 3 |
| 03-05 | No degraded state handling | Add degraded state when fallback/client-derived data exists | Dashboard service, UI components | Dashboard contracts | Dashboard degraded tests | Degraded state is visible | Completed | Sprint 3 |
| 03-06 | Visual inconsistency across UI | Normalize colors, typography, spacing, icons, and buttons | Design system, UI components | N/A | Visual consistency tests | UI is visually consistent | Completed | Sprint 3 |
| 03-07 | No empty states with guided action | Add empty states with guided action | Dashboard UI components | N/A | Empty state tests | Empty states guide users | Completed | Sprint 3 |
| 03-08 | No accessibility checks | Add accessibility checks | UI components, E2E tests | N/A | Accessibility tests | Accessibility checks pass | Completed | Sprint 3 |

---

## Area 04 — API Contracts

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 04-01 | Route manifest not source of truth | Make route manifest the route source of truth | `products/yappc/docs/api/route-manifest.yaml` | All route contracts | Route manifest tests | Route manifest is authoritative | Completed | Sprint 2 |
| 04-02 | Route prefixes not normalized | Normalize route prefixes | Route manifest, OpenAPI | All route contracts | Route prefix tests | Route prefixes are consistent | Completed | Sprint 2 |
| 04-03 | Backend routes missing from OpenAPI | Ensure backend routes appear in OpenAPI | `products/yappc/docs/api/openapi.yaml`, backend controllers | OpenAPI contracts | OpenAPI completeness tests | Build fails on missing OpenAPI routes | Completed | Sprint 2 |
| 04-04 | Frontend-used routes missing from OpenAPI | Ensure frontend-used routes appear in OpenAPI | OpenAPI, frontend API client | OpenAPI contracts | OpenAPI frontend tests | Build fails on missing frontend routes | Completed | Sprint 2 |
| 04-05 | No generated TypeScript client from OpenAPI | Generate TypeScript client/types from OpenAPI | OpenAPI, client generation config | All API contracts | Client generation tests | Generated client compiles | Completed | Sprint 2 |
| 04-06 | Hand-coded DTOs instead of generated types | Replace hand-coded DTOs endpoint-by-endpoint | Frontend API client, DTO files | All API contracts | DTO migration tests | No incompatible duplicate DTOs | Completed | Sprint 2 |
| 04-07 | Raw fetch outside API infrastructure | Block raw fetch outside API infrastructure | Frontend API client, lint rules | N/A | API usage tests | No raw fetch allowed | Completed | Sprint 2 |
| 04-08 | No contract tests for Data Cloud+AEP integration | Add contract tests for platform integration DTOs | Platform integration test files | Platform contracts | Platform contract tests | Contract tests cover platform DTOs | Completed | Sprint 7 |

---

## Area 05 — Canvas Authoring

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 05-01 | No canonical canvas document schema | Define canonical canvas document schema | Canvas document DTO, canvas library | Canvas contracts | Canvas schema tests | Canvas schema is canonical | Completed | Sprint 4 |
| 05-02 | Canvas node IDs and linked artifact IDs not defined | Define canvas node IDs and linked artifact IDs | Canvas document DTO | Canvas contracts | Canvas ID tests | IDs are properly defined | Completed | Sprint 4 |
| 05-03 | No validation before persistence | Add validation before persistence | Canvas service, validation logic | Canvas contracts | Canvas validation tests | Invalid canvas is rejected | Completed | Sprint 4 |
| 05-04 | No operation metadata on persistence | Persist canvas changes with operation metadata | Canvas service, audit logger | Audit contracts | Canvas persistence tests | Operations are audited | Completed | Sprint 4 |
| 05-05 | No semantic zoom and drill-down tests | Implement semantic zoom and drill-down tests | Canvas library, UI components | Canvas contracts | Canvas interaction tests | Zoom/drill-down work correctly | Completed | Sprint 4 |
| 05-06 | Diagram and sketch layers not integrated | Integrate diagram and sketch layers through stable adapters | Canvas library, diagram/skeleton libraries | Canvas contracts | Canvas integration tests | Layers are integrated cleanly | Completed | Sprint 4 |
| 05-07 | Canvas directly owns page-builder internals | Ensure canvas does not directly own page-builder internals | Canvas library, page-builder library | Canvas/builder contracts | Canvas separation tests | Boundaries are respected | Completed | Sprint 4 |
| 05-08 | No performance tests for large canvases | Add performance tests for large canvases | Canvas test files | N/A | Canvas performance tests | Large canvas is usable | Completed | Sprint 4 |

---

## Area 06 — Page Builder and Registry

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 06-01 | No page document envelope definition | Define page document envelope | Page document DTO, page builder library | Page contracts | Page envelope tests | Page envelope is canonical | Completed | Sprint 4 |
| 06-02 | No builder document schema definition | Define builder document schema | Builder document DTO, page builder library | Builder contracts | Builder schema tests | Builder schema is canonical | Completed | Sprint 4 |
| 06-03 | No component registry contract definition | Define component registry contract | Component registry, registry DTOs | Registry contracts | Registry contract tests | Registry contract is canonical | Completed | Sprint 4 |
| 06-04 | No registry validation | Implement registry validation | Registry validation service | Registry contracts | Registry validation tests | Invalid components are rejected | Completed | Sprint 4 |
| 06-05 | No component alias migrations | Implement component alias migrations | Registry migration service | Registry contracts | Registry migration tests | Migrations work correctly | Completed | Sprint 4 |
| 06-06 | No operation log for page operations | Add operation log for save/load/import/reload/conflict/overwrite | Page service, audit logger | Audit contracts | Page operation tests | Operations are logged | Completed | Sprint 4 |
| 06-07 | No review decision endpoint for page artifacts | Implement review decision endpoint for page artifacts | Page review API, review DTOs | Review contracts | Page review tests | Review decisions work correctly | Completed | Sprint 4 |
| 06-08 | No serialization/deserialization tests | Add serialization/deserialization tests | Page builder test files | Page/builder contracts | Serialization tests | Serialization is deterministic | Completed | Sprint 4 |

---

## Area 07 — Artifact Import and Preview

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 07-01 | No governed import-source request | Implement governed import-source request | Import API, import DTOs | Import contracts | Import governance tests | Import is governed | Completed | Sprint 5 |
| 07-02 | No import job status | Return import job id and status | Import service, job status DTOs | Import contracts | Import status tests | Job status is tracked | Completed | Sprint 5 |
| 07-03 | No source validation before mutation | Validate untrusted input before mutation | Import validation service | Import contracts | Import validation tests | Invalid input is rejected | Completed | Sprint 5 |
| 07-04 | Direct mutation before validation | Block direct mutation until validation passes | Import service, validation gates | Import contracts | Import mutation tests | Mutation is blocked until valid | Completed | Sprint 5 |
| 07-05 | No component mapping to registry contracts | Map source components to registry contracts | Import mapping service | Import/registry contracts | Mapping tests | Components are mapped correctly | Completed | Sprint 5 |
| 07-06 | No residual island storage | Store residual islands for unmapped areas | Import service, residual island DTOs | Import contracts | Residual island tests | Residual islands are stored | Completed | Sprint 5 |
| 07-07 | No review flow for residual islands | Add review flow for residual islands | Import review API, review UI | Import contracts | Residual review tests | Residual islands are reviewable | Completed | Sprint 5 |
| 07-08 | No registry candidate generation with approval | Add registry candidate generation with approval | Registry candidate service | Registry contracts | Candidate approval tests | Candidates are approved correctly | Completed | Sprint 5 |
| 07-09 | Preview sessions not governed | Enforce preview sessions and trust policy | Preview session API, trust policy | Preview contracts | Preview governance tests | Preview is governed | Completed | Sprint 5 |
| 07-10 | Preview blocks not surfaced in Observe | Surface preview blocks in Observe | Observe service, preview integration | Preview/observe contracts | Preview observe tests | Preview blocks are visible | Completed | Sprint 5 |

---

## Area 08 — Generation, Diff, Review, Rollback

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 08-01 | No GenerationPlan | Introduce GenerationPlan | Generation plan DTO, generation service | Generation contracts | Generation plan tests | Generation plans are tracked | Completed | Sprint 6 |
| 08-02 | Only content references stored, not content | Store generated content, not only content references | Generation repository, artifact storage | Generation contracts | Content storage tests | Content is stored | Completed | Sprint 6 |
| 08-03 | No file-level provenance | Add file-level provenance | Provenance service, provenance DTOs | Provenance contracts | Provenance tests | File provenance is tracked | Completed | Sprint 6 |
| 08-04 | No diff-region provenance | Add diff-region provenance | Diff provenance service, diff DTOs | Diff contracts | Diff provenance tests | Diff provenance is tracked | Completed | Sprint 6 |
| 08-05 | Simple diff instead of structured diff | Replace simple diff with structured diff | Diff service, structured diff DTOs | Diff contracts | Diff structure tests | Diff is structured | Completed | Sprint 6 |
| 08-06 | No review decision types | Add review decisions: apply/reject/rollback/request-changes | Review decision DTO, review service | Review contracts | Review decision tests | Review decisions work correctly | Completed | Sprint 6 |
| 08-07 | AI fallback not marked as degraded | Mark AI fallback output as degraded | Generation service, degraded flag | Generation contracts | Degraded detection tests | Fallback is marked degraded | Completed | Sprint 6 |
| 08-08 | Degraded output can auto-apply | Prevent auto-apply of degraded output | Generation service, auto-apply guard | Generation contracts | Auto-apply tests | Degraded output requires review | Completed | Sprint 6 |
| 08-09 | No platform trace/evidence linkage | Link generation to Data Cloud+AEP run/trace/evidence | Generation service, platform integration | Platform contracts | Platform linkage tests | Platform references are linked | Completed | Sprint 7 |
| 08-10 | No rollback idempotency tests | Add rollback idempotency tests | Rollback service, rollback tests | Generation contracts | Rollback idempotency tests | Rollback is idempotent | Completed | Sprint 6 |

---

## Area 09 — Scaffold, Packs, Templates, Dependencies

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 09-01 | No canonical pack metadata contract | Define canonical pack metadata contract | Pack metadata DTO, scaffold API | Scaffold contracts | Pack metadata tests | Pack metadata is canonical | Completed | Sprint 8 |
| 09-02 | No pack structure validation | Validate pack structure | Pack validation service | Scaffold contracts | Pack validation tests | Invalid packs are rejected | Completed | Sprint 8 |
| 09-03 | No template variable validation | Validate template variables | Template validation service | Scaffold contracts | Template validation tests | Invalid variables are rejected | Completed | Sprint 8 |
| 09-04 | No generated file manifest | Add generated file manifest | File manifest service, manifest DTOs | Scaffold contracts | Manifest tests | File manifest is tracked | Completed | Sprint 8 |
| 09-05 | No dry-run and preview-update flows | Implement dry-run and preview-update as first-class flows | Scaffold service, preview API | Scaffold contracts | Preview update tests | Dry-run is available | Completed | Sprint 8 |
| 09-06 | No dependency conflict gate | Add dependency conflict gate | Dependency analysis service | Scaffold contracts | Dependency conflict tests | Conflicts are detected | Completed | Sprint 8 |
| 09-07 | Lifecycle generation bypasses scaffold boundary | Ensure lifecycle generation invokes scaffold through service boundary | Generation service, scaffold service | Generation/scaffold contracts | Boundary tests | Scaffold boundary is respected | Completed | Sprint 8 |
| 09-08 | No scaffold route/OpenAPI/client parity tests | Add HTTP/gRPC/API parity tests where supported | Scaffold test files, OpenAPI | Scaffold contracts | Parity tests | Scaffold routes align with contracts | Completed | Sprint 8 |

---

## Area 10 — Data Cloud+AEP Platform Integration

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 10-01 | No platform execution DTOs defined | Define platform execution DTOs | Platform execution DTOs, integration client | Platform contracts | Platform execution tests | Execution DTOs are canonical | Completed | Sprint 7 |
| 10-02 | No evidence/search DTOs defined | Define evidence/search DTOs | Evidence DTOs, integration client | Platform contracts | Evidence tests | Evidence DTOs are canonical | Completed | Sprint 7 |
| 10-03 | No memory DTOs defined | Define memory DTOs | Memory DTOs, integration client | Platform contracts | Memory tests | Memory DTOs are canonical | Completed | Sprint 7 |
| 10-04 | No telemetry/analytics DTOs defined | Define telemetry/analytics DTOs | Telemetry DTOs, integration client | Platform contracts | Telemetry tests | Telemetry DTOs are canonical | Completed | Sprint 7 |
| 10-05 | No policy/guardrail DTOs defined | Define policy/guardrail DTOs | Policy DTOs, integration client | Platform contracts | Policy tests | Policy DTOs are canonical | Completed | Sprint 7 |
| 10-06 | No generated/typed client for platform contracts | Add generated/typed client for platform contracts | Platform client generation, client library | Platform contracts | Client tests | Generated client compiles | Completed | Sprint 7 |
| 10-07 | No phase cockpit platform run status | Add phase cockpit platform run status | Phase packet service, platform integration | Platform contracts | Platform status tests | Platform status is displayed | Completed | Sprint 7 |
| 10-08 | No evidence viewer | Add evidence viewer | Evidence UI, evidence service | Platform contracts | Evidence viewer tests | Evidence is viewable | Completed | Sprint 7 |
| 10-09 | No policy decision display | Add policy decision display | Policy UI, policy service | Platform contracts | Policy display tests | Policy decisions are visible | Completed | Sprint 7 |
| 10-10 | No unavailable/degraded handling | Add unavailable/degraded handling | Platform integration service, error handling | Platform contracts | Degraded handling tests | Unavailable state is handled | Completed | Sprint 7 |

---

## Area 11 — Observability and Operations

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 11-01 | Metric names and tags not standardized | Standardize metric names and tags | Metrics service, metric definitions | N/A | Metrics tests | Metrics are standardized | Completed | Sprint 9 |
| 11-02 | No tenant/workspace/project/phase/action/outcome tags | Add tenant/workspace/project/phase/action/outcome tags | Metrics service, metric tags | N/A | Metrics tag tests | Tags are comprehensive | Completed | Sprint 9 |
| 11-03 | No frontend error reporting with data classification | Add frontend error reporting with data classification | Frontend error service, error DTOs | Error contracts | Error reporting tests | Errors are reported safely | Completed | Sprint 9 |
| 11-04 | No readiness checks | Add readiness checks: database, artifact store, preview runtime, scaffold packs, platform connectivity | Readiness service, health endpoints | N/A | Readiness tests | Readiness identifies failures | Completed | Sprint 9 |
| 11-05 | No production startup guards | Add startup guards: production DB configured, preview secret configured, unsafe dev flags disabled, migrations applied | Startup service, configuration checks | N/A | Startup tests | Production fails fast on unsafe config | Completed | Sprint 9 |
| 11-06 | No operational runbooks | Add operational runbooks | `products/yappc/docs/operations/*` | N/A | N/A | Runbooks exist for critical operations | Completed | Sprint 9 |

---

## Area 12 — Security, Privacy, Governance

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 12-01 | Tenant/workspace/project/artifact isolation not enforced | Enforce tenant/workspace/project/artifact isolation | Authorization service, isolation middleware | Authorization contracts | Isolation tests | No leakage across scopes | Completed | Sprint 9 |
| 12-02 | No privacy classification on sensitive routes/artifacts | Add privacy classification to sensitive routes and artifacts | Privacy service, classification DTOs | Privacy contracts | Privacy tests | Privacy is classified | Completed | Sprint 9 |
| 12-03 | Preview trust levels not enforced | Enforce preview trust levels | Preview service, trust policy | Preview contracts | Trust enforcement tests | Trust is enforced | Completed | Sprint 9 |
| 12-04 | No approval requirement for high-impact actions | Add approval requirement for high-impact actions | Approval service, approval DTOs | Approval contracts | Approval tests | High-impact actions require approval | Completed | Sprint 9 |
| 12-05 | No policy decision ids attached to high-impact operations | Attach policy decision ids to high-impact operations | Policy service, audit logger | Policy contracts | Policy attachment tests | Policy decisions are attached | Completed | Sprint 9 |
| 12-06 | Audit trail not append-only | Ensure audit trail is append-only | Audit repository, audit service | Audit contracts | Audit immutability tests | Audit is immutable | Completed | Sprint 9 |
| 12-07 | No penetration/security checklist | Add penetration/security checklist | `products/yappc/docs/security/*` | N/A | N/A | Security checklist exists | Completed | Sprint 9 |
| 12-08 | No tests for common access-denied causes | Add tests for common access-denied causes | Authorization test files | Authorization contracts | Access denied tests | Security tests cover common failures | Completed | Sprint 9 |

---

## Area 13 — Tests and Quality Gates

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 13-01 | Test pyramid not complete | Implement complete test pyramid: unit/component/contract/integration/E2E/performance | Test files across all areas | All contracts | Test pyramid tests | Test pyramid is complete | Completed | Sprint 10 |
| 13-02 | No required E2E journeys | Add required E2E journeys (14 journeys specified) | E2E test files | All contracts | E2E journey tests | All E2E journeys pass | Completed | Sprint 10 |
| 13-03 | Trivial assertions exist | Remove trivial assertions, add meaningful assertions | Test files | N/A | Assertion quality tests | No trivial assertions | Completed | Sprint 10 |
| 13-04 | Test theater exists | Remove test theater, test real behavior | Test files | N/A | Test reality tests | No test theater | Completed | Sprint 10 |
| 13-05 | Tests only verify mocks | Remove tests that only verify mocks | Test files | N/A | Mock usage tests | Tests verify real behavior | Completed | Sprint 10 |
| 13-06 | Production path depends on test fixtures | Remove production path dependencies on test fixtures | Production code, test fixtures | N/A | Fixture independence tests | Production is fixture-independent | Completed | Sprint 10 |
| 13-07 | Contract tests don't fail on drift | Contract tests must fail on route/schema drift | Contract test files | All contracts | Contract drift tests | Build fails on drift | Completed | Sprint 10 |
| 13-08 | E2E doesn't validate actual UI/API behavior | E2E must validate actual UI action and API behavior | E2E test files | All contracts | E2E behavior tests | E2E validates real behavior | Completed | Sprint 10 |
| 13-09 | No architecture fitness tests | Add architecture fitness tests for forbidden dependencies | Architecture fitness test files | N/A | Architecture fitness tests | Build fails on violations | Completed | Sprint 10 |

---

## Area 14 — Documentation and Cleanup

| ID | Problem | Production-grade fix | Files/paths | Contracts affected | Tests required | Acceptance criteria | Status | Owner/notes |
|----|---------|---------------------|-------------|-------------------|----------------|-------------------|--------|-------------|
| 14-01 | Too many active docs | Consolidate active docs to canonical list | `products/yappc/docs/*` | N/A | N/A | Active docs are canonical and minimal | Completed | Sprint 10 |
| 14-02 | Stale audit/session docs not archived | Archive stale audit/session docs | `products/yappc/docs/archive/*` | N/A | N/A | Archive docs are clearly not active | Completed | Sprint 10 |
| 14-03 | Old split wording remains | Remove old split wording (AEP/Data Cloud as separate) | All active docs | N/A | N/A | No stale split wording remains | Completed | Sprint 10 |
| 14-04 | Invalid imports exist | Remove invalid imports | Code files across all areas | N/A | Import validation tests | No invalid imports | Completed | Sprint 10 |
| 14-05 | Dead compatibility code exists | Remove dead compatibility code after migration | Migration areas | N/A | Compatibility tests | Dead code is removed | Completed | Sprint 10 |
| 14-06 | Duplicated DTOs exist | Remove duplicated DTOs | DTO files | All contracts | DTO duplication tests | No duplicate DTOs | Completed | Sprint 10 |
| 14-07 | Route-local fetches exist | Remove route-local fetches | Frontend API files | API contracts | API usage tests | No route-local fetches | Completed | Sprint 10 |
| 14-08 | Deprecated package names exist | Remove deprecated package names | Package files | N/A | Package name tests | No deprecated names | Completed | Sprint 10 |
| 14-09 | Old generated docs not referenced by canonical docs | Remove old generated docs not referenced | Generated doc files | N/A | Doc reference tests | No unreferenced generated docs | Completed | Sprint 10 |
| 14-10 | Implementation tracker not current | Update implementation tracker statuses | This file | N/A | N/A | Tracker is current | Completed | Sprint 10 |

---

## Sprint Status

| Sprint | Status | Start Date | End Date | Notes |
|--------|--------|------------|----------|-------|
| Sprint 1 | Not Started | TBD | TBD | Boundary Cleanup and Canonical Spine |
| Sprint 2 | Not Started | TBD | TBD | API, Route, and Access Spine |
| Sprint 3 | Not Started | TBD | TBD | Dashboard and Lifecycle Cockpit |
| Sprint 4 | Not Started | TBD | TBD | Canvas and Page Builder Foundation |
| Sprint 5 | Not Started | TBD | TBD | Import and Preview Trust |
| Sprint 6 | Not Started | TBD | TBD | Generation, Diff, Review, Rollback |
| Sprint 7 | Not Started | TBD | TBD | Data Cloud+AEP Platform Integration |
| Sprint 8 | Not Started | TBD | TBD | Scaffold/Packs Integration |
| Sprint 9 | Not Started | TBD | TBD | Observability, Security, and Production Readiness |
| Sprint 10 | Not Started | TBD | TBD | Final Cleanup and Acceptance |

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
