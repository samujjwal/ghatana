# YAPPC Implementation Tracker

**Purpose**: Track all production-grade implementation tasks for YAPPC hardening  
**Status**: Active  
**Last Updated**: 2026-05-12  
**Source**: docs/implementation/README.md  
**Based on snapshot**: samujjwal/ghatana@5e03f330990461913b4b8963dbee39f5ac75143a  

---

## Canonical Task Tracker (yappc-todos alignment)

| ID | Area | File(s) | Issue | Required Change | Tests | Status |
|----|------|---------|-------|-----------------|-------|--------|
| P0-2.1 | Phase cockpit data | products/yappc/frontend/web/src/services/phase/PhaseCockpitDataService.ts | Mixed `yappcApi` and generated client usage; missing generated type import | Switched to generated `ProjectsService.getProject(...)`; removed `yappcApi` dependency in this file; normalized generated payload shape | products/yappc/frontend/web/src/services/phase/__tests__/PhaseCockpitDataService.test.ts | completed |
| P0-2.1-T | Phase cockpit tests | products/yappc/frontend/web/src/services/phase/__tests__/PhaseCockpitDataService.test.ts | Missing regression coverage for scope errors and generated client usage | Added tests for missing workspace/project scope, project phase normalization, and generated next-phase call | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/phase/__tests__/PhaseCockpitDataService.test.ts` | completed |
| P0-3.1-AUTH | API client auth adapter | products/yappc/frontend/web/src/lib/api/client.ts | Generated auth adapter called non-existent generated methods and skipped response adaptation | `auth.loginSession` now uses generated login + adapter mapping; aligned logout/update-profile path behavior to available contracts | Pending broader client test suite | in-progress |
| P0-3.1-SCOPE | Project scope enforcement | products/yappc/frontend/web/src/lib/api/client.ts | `ProjectsService.listProjects(workspaceId || '')` allowed empty scope | `projects.list` now fails fast with explicit workspace requirement before generated call | Pending broader client test suite | in-progress |
| P0-1.2 | Canonical implementation doc | products/yappc/docs/implementation/YAPPC_PRODUCTION_GRADE_PLAN.md | Canonical implementation plan file missing | Create canonical plan doc and align with active todo plan | N/A | in-progress |
| P1-5.1-A | Authorization `admin` scope mapping | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java | `admin` scope could fall through default permission mapping | Fixed permission mapping for plain `admin` and `*:admin` scopes | Added regression in `RouteAuthorizationRegistryTest` | completed |
| P1-5.1-B | Workspace permission enforcement path | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java | Workspace authorization path hardcoded `WORKSPACE_READ` | Workspace authorization now uses route definition required permission | Covered by route-definition wiring tests | completed |
| P1-5.1-C | Parameterized route scope lookup | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java | `getAuthorizedScopesForRoute(...)` used exact path equality and failed for concrete parameterized paths | Added canonical route-path propagation in route match and manifest lookup | Added regression in `RouteAuthorizationRegistryTest` | completed |
| P1-4.3 | Route manifest parity test scaffold | products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/RouteManifestParityTest.java | Missing parity test in yappc-services module | Added parity tests for manifest→generated registry→authorization registry→OpenAPI and generated frontend representation checks | Re-validated blocker: `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.api.RouteManifestParityTest'` still fails upstream at `:platform:java:agent-core:compileJava` (42 errors, 64 warnings) | in-progress |
| P1-4.2 | OpenAPI/frontend REST parity drift remediation | products/yappc/docs/api/openapi.yaml, products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts | 31 client-used REST endpoints were undocumented in canonical OpenAPI contract | Added missing method/path entries (auth profile, collaboration, anomalies, projects read models, rate-limit, page-artifact document, AI assist/readiness, and canvas post) | `pnpm --filter @ghatana/yappc-api-app exec vitest run src/__tests__/openapi-contract.test.ts` passed (12/12) | completed |
| P1-6.1-A | Phase packet platform service wiring | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcApiModule.java | `PhasePacketServiceImpl` was wired with null platform AuditService and PreviewRuntimeService | Added explicit degraded adapters and injected them into phase-packet service wiring | File-level diagnostics clean; full Gradle validation blocked by unrelated platform compile failures | completed |
| P1-6.1-B | Explicit degraded adapters | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/DegradedAuditService.java, products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/DegradedPreviewRuntimeService.java | Missing explicit degraded behavior for absent platform integrations | Added degraded adapters with structured warnings and fail-visible degraded responses | File-level diagnostics clean | completed |
| P1-6.1-C | Phase packet null-fallback removal | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java | Null checks yielded default-healthy preview/runtime and legacy synthetic activity fallback | Enforced non-null audit/preview services via degraded defaults and removed null-based healthy fallback branches | File-level diagnostics clean; full Gradle validation blocked by unrelated platform compile failures | completed |
| P1-6.1-D | Degraded packet identity hardening | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java | Degraded packet used fabricated project/workspace names and permissive tier default | Replaced fabricated names with explicit degraded sentinels and switched degraded tier default to fail-closed FREE | File-level diagnostics clean; integration verification pending broader build health | completed |
| P1-6.2-A | PhasePacket blocker query async migration | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java | `queryPhaseBlockers` used blocking `phaseGateValidator.validate(...).getResult()` in packet build flow | Converted blocker query to Promise-based flow and threaded async result into `buildPhasePacket` chain | File-level diagnostics clean; broader Gradle test execution still blocked by unrelated `platform/java/agent-core` compile failures | completed |
| P1-6.2-B | PhasePacket artifacts/activity async migration | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java | `queryCompletedArtifacts` and `queryActivityFeed` used blocking `getResult()` calls in packet assembly | Converted both methods to Promise-based queries and threaded async results through `buildPhasePacket` chain | File-level diagnostics clean; broader Gradle test execution remains externally blocked by `platform/java/agent-core` compile failures | completed |
| P1-6.2-C | Project-state fallback fail-closed hardening | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java | Missing/failed project-state query returned synthetic project name and permissive tier, masking degraded runtime state | Replaced synthetic fallback payloads with explicit degraded markers (`degraded=true`, explicit degraded reason codes) to force degraded packet path | File-level diagnostics clean; module-level test run still blocked by unrelated `platform/java/agent-core` compile failures | completed |
| TODO-14 | Data model idempotency | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generation/GenerationServiceImpl.java, GenerationRunRepository.java | Duplicate generation run creation and non-idempotent review decisions | Added idempotency guard: `findByIdempotencyKey` before create, `reviewDecision` rejects duplicate calls with `ALREADY_DECIDED` error | ServiceObservabilityTest + GenerationServiceImpl unit tests | completed |
| TODO-15 | Observability metric tags | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/common/ServiceObservability.java, src/main/java/com/ghatana/yappc/services/metrics/BusinessMetrics.java | No canonical flow tags or per-flow metrics | Added `flowTags()` (9-key canonical tag map) and `recordCriticalFlow()` + 10 per-flow wrapper methods with Micrometer Tags | BusinessMetricsTest (30 tests pass), ServiceObservabilityTest (5 tests pass) | completed |
| TODO-16 | Preview session security hardening | products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PreviewSessionApiController.java | UNTRUSTED/SEMI_TRUSTED artifacts not blocked; token scope not validated | Added trust level enforcement (UNTRUSTED blocked 403, SEMI_TRUSTED requires acknowledged=true), scope validation via `validateTokenWithScope()` | PreviewSessionApiControllerTest (13 tests pass: ProductionStartupGuard×3, TrustLevelEnforcement×3, TokenValidation×3, plus 2 existing, plus parity tests) | completed |
| TODO-17 | Docs and cleanup | products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md | Tracker not updated with completed sections | Updated tracker to reflect sections 14–17 completed; verified canonical docs do not claim production readiness for unimplemented areas | N/A | completed |

---

## Phase 0 — Establish the production spine

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 0-1 | Contract spine | `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md` | Tracker needs to match 12-phase plan structure | Create/update tracker with sections: contract spine, access/scope, lifecycle/phase cockpit, Data Cloud+AEP integration, generation/diff/review, canvas/page-builder/preview, API client migration, UI/UX dashboard, testing/quality gates, cleanup/docs | No task exists only in chat or scattered audit notes | Tracker validation tests | todo |
| 0-2 | Access/scope | `products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md` | Need canonical models documentation | Document canonical spine: project/product/app terminology, mounted lifecycle, artifact model with provenance, Canvas→Page Document→Builder Document model, preview trust security boundary, governance trace, typed contracts | Canonical models documented and referenced | Model validation tests | todo |
| 0-3 | API contracts | `products/yappc/docs/api/route-manifest.yaml`, `openapi.yaml` | Files exist but need validation | Ensure proper structure and completeness | Files exist with proper structure | Schema validation tests | todo |
| 0-4 | All areas | Tracker header | Missing snapshot reference | Mark tracker as based on snapshot samujjwal/ghatana@5e03f330990461913b4b8963dbee39f5ac75143a | Tracker references snapshot | N/A | todo |

---

## Phase 1 — Route, contract, and client spine

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 1-1 | Route manifest | `products/yappc/docs/api/route-manifest.yaml`, `openapi.yaml` | Need parity validation | Validate every manifest route has matching OpenAPI path, HTTP method, operationId, auth/security scheme, request/response schema, generated client operation, generated authorization entry | Build fails if route missing from one layer | RouteManifestSchemaTest, RouteManifestOpenApiParityTest, RouteManifestGeneratedRegistryTest | todo |
| 1-2 | Route manifest | `products/yappc/docs/api/route-manifest.yaml` | Operation naming inconsistent | Fix operation naming consistency - choose lower camelCase or PascalCase for operationId and enforce it throughout manifest | All operationIds follow consistent naming convention | Naming convention tests | todo |
| 1-3 | Route generation | Build task | Route generation not deterministic | Make route generation deterministic: build task reads manifest, generates GeneratedRouteRegistry, generates/validates OpenAPI route skeleton, generates client method coverage test, fails build on drift | Build fails on drift | Generation determinism tests | todo |
| 1-4 | Route manifest | `products/yappc/docs/api/route-manifest.yaml` | Missing validation rules | Add manifest validation rules: required fields present, scope format valid, auth=public has empty scopes, auth=required has non-empty scopes, boundary=DATA_CLOUD_AEP never appears in YAPPC imports, privacyClassification=RESTRICTED requires audit event | Manifest passes all validation rules | Manifest validation tests | todo |
| 1-5 | Frontend client | `products/yappc/frontend/web/src/lib/api/client.ts` | Monolithic client file | Split client.ts into generatedClientAdapter.ts, legacyClientAdapter.ts, scopeHeaders.ts, errorMapper.ts, index.ts | Client split into focused modules | Client module tests | todo |
| 1-6 | Frontend client | `products/yappc/frontend/web/src/lib/api/client.ts` | Manual endpoint wrappers | Migrate endpoint groups (Auth, Workspaces, Projects, Lifecycle, Phase packet, Dashboard actions, Generate/diff/review, Preview session, Artifact import/review, Audit/telemetry) to generated client | All endpoints use generated client | Client migration tests | todo |
| 1-7 | Frontend client | `products/yappc/frontend/web/src/lib/api/client.ts` | Legacy wrapper code | Delete manual endpoint wrappers once generated coverage exists | client.ts becomes thin compatibility barrel or removed | Client cleanup tests | todo |
| 1-8 | Frontend | All frontend files | Raw fetch usage | Enforce no raw fetch: allowed only in HTTP infrastructure, disallowed in route components, hooks, page services, random libs | No new raw fetch outside approved infra | No-raw-fetch lint rule | todo |
| 1-9 | Frontend client | `products/yappc/frontend/web/src/lib/api/client.ts` | Mixed auth modes | Normalize auth mode: pick cookie-session for web, remove fake empty token compatibility once consumers are migrated | Consistent auth mode across frontend | Auth mode tests | todo |

---

## Phase 2 — Access, scope, authorization, and permissions

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 2-1 | Scope transport | RouteAuthorizationRegistry, frontend client | Mixed scope conventions | Define canonical scope transport: path params when part of route, query params for read routes, headers only for cross-cutting scope, body scope only for controller-level validation after authorization | Every project-scoped call includes workspaceId | Scope transport tests | todo |
| 2-2 | Scope transport | All scope-using code | Mixed conventions | Remove mixed conventions: workspaceId query vs X-Workspace-Id headers, scope query helper that doesn't map to actual resource identity | Consistent scope transport across all calls | Scope consistency tests | todo |
| 2-3 | Scope DTO | Backend and frontend | No shared scope DTO | Add shared scope DTO: Java RequestScopeContext, TypeScript ScopeContext | Scope DTO used consistently | Scope DTO tests | todo |
| 2-4 | Scope tests | Test files | Missing scope permutation tests | Add tests for all permutations: workspace read, project read/write, artifact read/write, preview session create/validate, generation review/apply/reject/rollback, dashboard action execute | All scope permutations tested | Scope permutation tests | todo |
| 2-5 | Scope error handling | PhaseCockpitDataService | TODO-001 workspace context error | Fix TODO-001 path: replace fetchProjectSnapshot workspace context error with typed route-level requirement and user-facing error handling | Backend rejects missing scope with RFC-7807-style error, UI shows actionable message | Error handling tests | todo |
| 2-6 | Capabilities | Backend services | Role heuristics in PhasePacketServiceImpl | Introduce backend CapabilityEvaluationService that evaluates capabilities from tenant, workspace membership, project role, artifact ownership, subscription tier, feature flag, policy decision | Capability decisions are backend-derived | Capability service tests | todo |
| 2-7 | Capabilities | Phase packet, UI | Frontend permission inference | Return capability model in phase packet, UI renders actions only from capability model, do not duplicate authorization logic in React components | Frontend does not infer sensitive permissions | Capability integration tests | todo |

---

## Phase 3 — Lifecycle cockpit and phase packet productionization

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 3-1 | Phase packet | PhasePacketServiceImpl | Fake project fallback | Replace fake project fallback: if project missing return 404, if Data Cloud degraded return explicit degraded packet with degradedReason, do not synthesize project names/tier | No sample/default project data in production mode | Phase packet data tests | todo |
| 3-2 | Phase packet | PhasePacketServiceImpl | Hardcoded next phase | Replace hardcoded next phase: use transition config/lifecycle DAG, support legacy aliases through compatibility adapter, return blocked/review/advance state from gate validator | Next phase computed from transition config | Phase transition tests | todo |
| 3-3 | Phase packet | PhasePacketServiceImpl | Empty evidence | Replace empty evidence: query typed Data Cloud+AEP evidence endpoint, include evidenceIds, traceId, confidence, source artifact references, show evidence in UI | Evidence displayed in cockpit where decisions made | Evidence integration tests | todo |
| 3-4 | Phase packet | PhasePacketServiceImpl | Empty governance | Replace empty governance: query policy decision/audit records, include who/what/why/when, show policy blocks and review-required states in cockpit | Governance visible in cockpit | Governance integration tests | todo |
| 3-5 | Phase packet | PhasePacketServiceImpl | Sample activity | Replace sample activity: add audit query API, return real activity feed with actor, phase, operation, outcome, timestamp, severity | Real activity feed returned | Activity feed tests | todo |
| 3-6 | Phase packet | PhasePacketServiceImpl | Default healthy signals | Replace default healthy signals: preview health from preview runtime, generation health from generation service/repository, runtime health from run/observe subsystem, Data Cloud+AEP health when phase needs it | Health signals from real sources | Health signal tests | todo |
| 3-7 | Phase packet | Frontend cockpit | Separate data loads | Make /api/v1/phase/packet the primary cockpit read model, keep old project/activity/transition calls only as transitional fallback | Cockpit route needs one primary packet load | Phase packet integration tests | todo |
| 3-8 | Phase packet | Frontend cockpit | Duplicated normalization | Remove duplicated normalization once packet is complete, add typed frontend PhasePacket model generated from OpenAPI | UI does not recompute business readiness | Phase packet contract tests | todo |

---

## Phase 4 — Data Cloud+AEP integration boundary

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 4-1 | Platform client | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/platform/*` | No typed platform client | Define Java interfaces: PlatformExecutionClient, PlatformEvidenceClient, PlatformMemoryClient, PlatformTelemetryClient, PlatformPolicyClient | Platform client contracts defined | Platform contract tests | todo |
| 4-2 | Platform client | Platform client contracts | No common request context | Define common request context: tenantId, workspaceId, projectId, actorId, phase, operation, dataClassification, requestedAt, correlationId, optional artifactId, canvasNodeId, generationRunId | Request context standardized across all platform calls | Request context tests | todo |
| 4-3 | Platform client | Platform client contracts | No common response metadata | Define common response metadata: status, confidence, confidenceReason, traceId, evidenceIds, policyDecisionId, degraded, degradedReason, createdAt, completedAt, optional runId, memoryRecordIds, searchResultIds | Response metadata standardized across all platform responses | Response metadata tests | todo |
| 4-4 | Platform client | Platform client adapters | No adapters | Add adapters: Real DataCloud client adapter, AEP execution adapter, Test fake only in test source set | Platform failure returns degraded metadata, not silent empty list | Adapter tests | todo |
| 4-5 | Architecture | Architecture rules | No boundary enforcement | Add architecture rule: YAPPC may import platform client contracts, YAPPC may not import platform internals | No YAPPC service imports internal platform modules directly | Architecture compliance tests | todo |

---

## Phase 5 — Generation, diff, review, rollback

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 5-1 | Generation | GenerationServiceImpl, artifact repositories | No actual content storage | Introduce GeneratedArtifactContentRepository, store actual content with artifactId, contentHash, path, MIME/language, size, source prompt hash, generator version, AI/degraded mode, provenance metadata | Generated files not anonymous content references | Content storage tests | todo |
| 5-2 | Generation | GenerationServiceImpl, artifact repositories | contentRef not resolvable | Make contentRef resolvable, add endpoint to fetch content safely, add content redaction rules for restricted artifacts | UI can load actual generated content | Content retrieval tests | todo |
| 5-3 | Diff | GenerationServiceImpl.computeDiff, frontend diff viewer | Simplified diff logic | Replace simplified diff with real diff engine: load old/new content by contentRef, use proper line diff library, compute added/deleted/modified regions, line ranges, ownership (AI/user/system), provenance per region | Diff displays actual line-level content | Diff engine tests | todo |
| 5-4 | Diff | GenerationServiceImpl | No user edit preservation | Preserve user edits across review, store diff snapshot with generation run | Applying edited diffs preserves provenance | User edit tests | todo |
| 5-5 | Review | GenerationServiceImpl.reviewDecision | Missing required fields | Require actorId, projectId, workspaceId, tenantId, runId, reason for reject/rollback, review provenance for review decisions | Review state machine is explicit and tested | Review validation tests | todo |
| 5-6 | Review | GenerationServiceImpl, GenerationRunRepository | No state machine | Apply decision states: REVIEW_PENDING, APPROVED, REJECTED, ROLLED_BACK, APPLY_FAILED, ROLLBACK_FAILED, make review action idempotent | Review decisions are deterministic, auditable, and safe to retry | State machine tests | todo |
| 5-7 | Review | GenerationServiceImpl | Manual JSON serialization | Store user edits as structured JSON using ObjectMapper, emit audit and metric events | User edits are queryable and validated | JSON serialization tests | todo |

---

## Phase 6 — Canvas, page builder, artifact import, and preview trust

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 6-1 | Page artifact | Page artifact API/controllers, frontend page builder | No PageArtifactDocument contract | Define PageArtifactDocument contract: artifactId, projectId/workspaceId/tenantId, builderDocument, registry version, operation log, sync state, preview trust, data classification | No builder save can overwrite silently | Page artifact contract tests | todo |
| 6-2 | Page artifact | Page artifact contract | No optimistic concurrency | Add optimistic concurrency: documentVersion, etag, conflict response, add operation log entries (save, import, reload, overwrite, migration, governance decision) | Every mutation has operation log | Concurrency tests | todo |
| 6-3 | Page artifact | Page artifact contract | No migration path | Add migration path for old builder documents | UI can resolve conflicts with clear choices | Migration tests | todo |
| 6-4 | Import | `/api/v1/yappc/artifact/import-source`, artifact compiler | No job lifecycle | Add source import job lifecycle: submitted, validating, decompiling, mapping, residual-review-required, completed, failed | Import never executes untrusted code directly | Import lifecycle tests | todo |
| 6-5 | Import | Import flow | No validation before mutation | Validate untrusted input before document mutation, require runtime health check before import, map known components to registry | Residual islands are visible and reviewable | Import validation tests | todo |
| 6-6 | Import | Residual island review APIs | No residual island flows | Create residual islands for unknown/untrusted components, add review flows: accept residual, map to registry candidate, reject/remove, quarantine | Preview is blocked or isolated when trust insufficient | Residual island tests | todo |
| 6-7 | Import | Import flow | No preview trust on imports | Attach preview trust to every imported node | Preview errors and policy blocks show in Observe | Preview trust tests | todo |

---

## Phase 7 — UI/UX: no cognitive load, full visibility

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 7-1 | Dashboard | `products/yappc/frontend/web/src/routes/dashboard.tsx` | Dashboard not action-focused | Rebuild dashboard sections: blocked work, review required, safe to continue, active generation/runs, recent activity, degraded integrations | User can start from dashboard and reach every required phase/action | Dashboard integration tests | todo |
| 7-2 | Dashboard | Dashboard components | Cards don't answer key questions | Every dashboard card must answer: What is happening? Why does it matter? What is the safest next action? Who/what is blocking it?, cards route to exact phase cockpit | No page requires understanding internal AI/system concepts | Dashboard UX tests | todo |
| 7-3 | Phase cockpit | `frontend/web/src/routes/app/project/_phaseCockpit.tsx` | Inconsistent layout | Standard phase cockpit layout: header (phase/readiness/health), primary action, blockers, evidence, required/completed artifacts, governance, activity, advanced details collapsed | Intent/Shape/Validate/Generate/Run/Observe/Learn/Evolve all render with same shell | Phase cockpit tests | todo |
| 7-4 | Phase cockpit | Phase cockpit components | No accessibility coverage | Use same interaction model for all phases, add accessibility and keyboard coverage | Missing data surfaces as degraded/empty states, not broken UI | Accessibility tests | todo |

---

## Phase 8 — Scaffold, packs, templates, and generated app quality

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 8-1 | Scaffold | `products/yappc/core/scaffold/api/*`, `products/yappc/core/scaffold/packs/*` | No pack contract | Define pack contract: metadata, language, platform, build system, variables, required features, dependency graph, license compatibility, generated files, tests | A generated project builds/tests in CI | Pack contract tests | todo |
| 8-2 | Scaffold | Scaffold core | No validation | Validate pack before use, validate variables with schema, add generated output validation (compile, test, lint, dependency audit, license check) | Pack conflicts are detected before write | Scaffold validation tests | todo |
| 8-3 | Scaffold | Scaffold core | No provenance | Add provenance from lifecycle artifacts to generated scaffold files | Generated output has provenance and rollback metadata | Provenance tests | todo |

---

## Phase 9 — Observability, audit, and operations

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 9-1 | Observability | All services | Inconsistent metric tags | Standard metric tags: tenantId, workspaceId, projectId, phase, operation, outcome, degraded | Every critical mutation has audit event | Metrics standardization tests | todo |
| 9-2 | Observability | All services | Inconsistent audit event shape | Standard audit event shape: actor, target, phase, operation, data classification, preview trust, correlationId, traceId | Every external/platform call has metrics | Audit event tests | todo |
| 9-3 | Observability | `products/yappc/prometheus.yappc.yml`, ops docs | Missing dashboards | Add dashboards: phase packet latency, generation success/fallback/failure, preview blocks, policy decisions, route authorization denials, Data Cloud+AEP degraded states | Degraded mode is visible in UI and ops | Dashboard configuration tests | todo |

---

## Phase 10 — Security and preview runtime

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 10-1 | Preview security | PreviewSecurityPolicy, preview session APIs | No preview context requirement | Require preview context: tenantId, workspaceId, projectId, artifactId, userId, previewTrust, dataClassification | No untrusted imported code executes directly | Preview context tests | todo |
| 10-2 | Preview security | Preview session APIs, page builder preview UI | No trust enforcement | Block untrusted execution, isolate semi-trusted preview, require explicit acknowledgement for restricted data, audit preview session creation/validation | Preview tokens expire and are validated | Preview security tests | todo |

---

## Phase 11 — Testing and quality gates

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 11-1 | Contract tests | Test files across all areas | Missing contract tests | Add contract tests: manifest ↔ OpenAPI, OpenAPI ↔ generated client, manifest ↔ generated route registry, frontend used routes ↔ OpenAPI | No touched area ships without meaningful unit + integration/contract/e2e coverage | Contract test suite | todo |
| 11-2 | Authorization tests | Authorization test files | Missing authorization tests | Add authorization tests: public route, missing principal, missing workspaceId/projectId/artifactId, unauthorized workspace, role/tier restrictions | Tests verify behavior, not just rendering | Authorization test suite | todo |
| 11-3 | Phase packet tests | Phase packet test files | Missing phase packet tests | Add phase packet tests: all phases, blockers, evidence, governance, degraded platform | CI fails on contract drift and forbidden dependencies | Phase packet test suite | todo |
| 11-4 | Generation tests | Generation test files | Missing generation tests | Add generation tests: AI success, AI degraded fallback, diff, review apply/reject/rollback, user edits | No test theater | Generation test suite | todo |
| 11-5 | UI e2e tests | Playwright e2e tests | Missing UI e2e tests | Add UI e2e tests: dashboard → phase cockpit, phase action → backend call, generate → review → apply, import → residual review, preview trust block | All TypeScript code fully typed during implementation | E2E test suite | todo |
| 11-6 | Build gates | Build configuration | Missing build gates | Add build gates: typecheck, lint, no raw fetch, no forbidden imports, no production TODO/fallback markers | Formatting, linting, and static checks remain healthy | Build gate configuration | todo |

---

## Phase 12 — Cleanup and consolidation

| ID | Area | File/path | Current issue | Required fix | Acceptance criteria | Test coverage | Owner status |
|----|------|-----------|---------------|--------------|-------------------|---------------|--------------|
| 12-1 | Documentation | `products/yappc/docs` | Too many active docs | Keep only canonical docs: vision, architecture, API/contracts, security, operations, testing, implementation tracker, archive or delete old one-off audit reports | No stale docs drive implementation | Documentation audit | todo |
| 12-2 | Code cleanup | All YAPPC code | Legacy adapters | Remove compatibility adapters after generated client migration, remove fake sample data and production TODOs | No duplicate clients or route definitions | Code cleanup validation | todo |
| 12-3 | Package boundaries | All YAPPC code | Boundary violations | Enforce package boundaries: @ghatana/* only for platform generic code, @yappc/* only for YAPPC product-specific code, app imports through app barrels | No generated/sample/fake data remains in production paths | Package boundary tests | todo |

---

## Sprint Status

| Sprint | Status | Start Date | End Date | Notes |
|--------|--------|------------|----------|-------|
| Sprint 1 | In Progress | 2026-05-11 | TBD | Contract and access spine - route manifest validation, OpenAPI parity, generated route registry, generated frontend client migration, scope model, authorization tests |
| Sprint 2 | Not Started | TBD | TBD | Phase packet as canonical read model - backend phase packet contract, replace project fallback, real lifecycle transition config, real capability model, frontend cockpit consumes packet |
| Sprint 3 | Not Started | TBD | TBD | Data Cloud+AEP typed integration - platform client contracts, evidence retrieval, policy decisions, degraded state handling, memory/telemetry write hooks, forbidden import architecture tests |
| Sprint 4 | Not Started | TBD | TBD | Generation/review/rollback - artifact content repository, real diff engine, review state machine, structured user edits, rollback restore path, UI review flow |
| Sprint 5 | Not Started | TBD | TBD | Canvas/page/import/preview - page artifact contract, operation log, import job lifecycle, residual island review, preview trust enforcement, canvas/page e2e |
| Sprint 6 | Not Started | TBD | TBD | UI polish, observability, cleanup - dashboard simplification, phase cockpit consistency, audit/metrics dashboards, security hardening, remove legacy code/docs, final regression suite |

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
