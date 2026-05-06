# YAPPC Hardening-1 Implementation Tracker

Source audit: `products/yappc/docs/todo-reports/hardening-1.md`
Commit reviewed: `a4ea52f99106252a388a498a4e292844f40cf003`

---

## P0 — Critical (must fix before ship)

- [x] **P0-001** Register design-system ESLint plugin in main lint config  
  Files: `products/yappc/frontend/eslint.config.js`, `web/eslint-rules/design-system-enforcement.js`  
  Criteria: raw `button/input/select`, hardcoded colors, ad-hoc cards are detected in CI  
  _Status: Already wired in current code — verify rules fire correctly_

- [x] **P0-002** Stop trusting client-provided authorized-scope headers  
  Files: `PageArtifactController.java` — `enforceResourceScope()` reads `X-Authorized-Workspace-IDs` / `X-Authorized-Project-IDs` from request  
  Fix: Remove header-based scope check; rely solely on `authorizeResourceScope()` (DB-backed)  
  Criteria: External requests cannot forge workspace/project authorization scope

- [x] **P0-003** Remove production path to `PageArtifactResourceScopeAuthorizer.allowAll()`  
  Files: `PageArtifactController.java` — 5-arg constructor defaults to `allowAll()`  
  Fix: Remove the 5-arg constructor; require explicit authorizer in all callers  
  Criteria: Production module cannot instantiate controller without real authorizer

- [x] **P0-004** Make page artifact mutation + audit atomic  
  Files: `PageArtifactController.java`, `PageArtifactAtomicMutationRepository.java`  
  Fix: Use `PageArtifactAtomicMutationRepository.saveWithAudit()` in ALL paths; remove non-atomic fallback + rollback logic  
  Criteria: Save and audit commit together; rollback-after-failure path is eliminated

- [x] **P0-005** Prove all PageDesigner mutations use `PageBuilderCommands`  
  Files: `PageDesigner`, `PropertyForm`, `ComponentRenderer`, `PageDesignerNode`  
  Fix: Audit all edit/drag/drop/update flows; route everything through command service  
  Criteria: No direct document mutation bypasses audit/undo/telemetry

- [x] **P0-006** Add real builder E2E without mocking renderer/compiler  
  Files: page builder tests, Playwright suite  
  Criteria: Drag/drop/edit/reorder/preview/save tested on mounted UI

- [x] **P0-007** Add cross-tenant/workspace/project security tests  
  Files: `PageArtifactControllerTest.java`  
  Criteria: 403 returned for unauthorized cross-resource access; forged scope headers rejected

- [x] **P0-008** Remove high-cardinality labels from metrics  
  Files: `PageArtifactController.java`  
  Fix: Remove `artifact_id`, `remote_version`, raw error strings from counter labels; move IDs to logs/traces  
  Criteria: Prometheus does not create per-artifact or per-version label cardinality

---

## P1 — High priority

- [x] **P1-001** Build native `ValidateApprovalPanel`  
  Files: `ValidateCockpit.tsx` or new `ValidateApprovalPanel.tsx`  
  Criteria: Validate phase useful before opening advanced details; has durable approval record

- [x] **P1-002** Build native `GeneratePackagePanel`  
  Files: `GenerateCockpit.tsx` or new `GeneratePackagePanel.tsx`  
  Criteria: Generate phase shows package, output files, tests, residuals, diff

- [x] **P1-003** Build native `RunReadinessPanel`  
  Files: `RunCockpit.tsx` or new `RunReadinessPanel.tsx`  
  Criteria: Run phase shows capability gates and plan; distinguishes planning/preview/actual deploy

- [x] **P1-004** Build native `ObserveSignalsPanel`  
  Files: `ObserveCockpit.tsx` or new `ObserveSignalsPanel.tsx`  
  Criteria: Observe shows preview health, metrics, incidents, traces/logs

- [x] **P1-005** Build native `LearnRetrospectivePanel`  
  Files: `LearnCockpit.tsx` or new `LearnRetrospectivePanel.tsx`  
  Criteria: Learn captures lessons and reusable patterns; persisted

- [x] **P1-006** Build native `EvolveRoadmapPanel`  
  Files: `EvolveCockpit.tsx` or new `EvolveRoadmapPanel.tsx`  
  Criteria: Evolve shows roadmap/backlog/next-cycle plan

- [x] **P1-007** Make phase config capability/role/tier-aware  
  Files: `PhaseCockpitConfigService.ts`  
  Fix: Generate phase config from role, feature flags, tenant tier, project state  
  Criteria: CTAs adapt by role and capability

- [x] **P1-008** Replace emoji icons with typed design-system icon identifiers  
  Files: `PhaseCockpitConfigService.ts`, `PhaseConfig` type  
  Fix: Use typed PhaseIcon union or design-system icon component  
  Criteria: Icons are accessible, theme-consistent, not raw emoji strings

- [x] **P1-009** Add contract-aware server validation  
  Files: `PageArtifactValidator.java`  
  Fix: Validate component contracts, prop schema, slot validity, data-binding policy, residual state  
  Criteria: Unknown contracts/invalid props/cycles rejected server-side

- [x] **P1-010** Add privacy/data-classification propagation validation  
  Files: `PageArtifactValidator.java`, builder contracts  
  Criteria: Data-bound components require correct classification and consent policy

- [x] **P1-011** Move compiler source import to backend service  
  Files: import workflow, API service  
  Criteria: Browser does not fetch arbitrary local repo paths

- [x] **P1-012** Convert import workflow output to `PageArtifactDocument[]`  
  Files: `ImportSourceWorkflow`, artifact compiler bridge  
  Criteria: TSX/route/story import creates canvas page artifacts

- [x] **P1-013** Add residual review persistence  
  Files: `ResidualIslandReviewPanel`, backend audit  
  Criteria: Accept/reject/review is persisted and audited

- [x] **P1-014** Add codegen preview + diff + safe merge  
  Files: compiler/codegen services  
  Criteria: Generated code cannot silently overwrite existing unsupported logic

- [x] **P1-015** Preview session server-issued signing  
  Files: backend preview/session API, frontend `preview-builder.tsx`  
  Criteria: No signing secret in frontend bundle

- [x] **P1-016** Validate preview token in `/preview/builder` runtime  
  Files: preview route handler  
  Criteria: Forged/expired/out-of-scope sessions fail closed

- [x] **P1-017** Enforce plugin runtime policy in execution path  
  Files: plugin loader, renderer, preview runtime  
  Criteria: Network/storage/browser API access blocked at runtime, not just configured

- [x] **P1-018** Replace substring URL allowlist checks with exact URL parsing  
  Files: `PluginRuntimePolicy` (or equivalent TypeScript service)  
  Fix: Parse URL using `new URL()` and match exact origin/host  
  Criteria: `evil-api.ghatana.com.attacker.com` cannot pass allowlist

- [x] **P1-019** Add Playwright full lifecycle test  
  Files: `products/yappc/frontend/e2e/`  
  Criteria: Login/onboarding → evolve flow passes on mounted app

- [x] **P1-020** Add accessibility regression suite  
  Files: `products/yappc/frontend/e2e/` or a11y tests  
  Criteria: Keyboard/focus/ARIA/live regions validated for major flows

---

## P2 — Medium priority

- [x] **P2-001** Rename implementation copy "Existing route details"  
  Files: `_phaseCockpit.tsx`  
  Fix: Replace with task-specific, user-facing label per phase  
  Criteria: No user-visible implementation language

- [x] **P2-002** Make disabled primary CTA point to blocker resolution  
  Files: `_phaseCockpit.tsx`, `PhasePrimaryActionCard.tsx`  
  Fix: When CTA is disabled, scroll/focus blocker panel and show resolve-blocker CTA  
  Criteria: Disabled action always has a direct remediation path

- [x] **P2-003** Integrate next-best-action service into dashboard and command palette  
  Files: dashboard, command palette, phase services  
  Criteria: Same top action appears consistently across surfaces

- [x] **P2-004** Add visual regression for phase cockpits  
  Files: visual tests suite  
  Criteria: Intent/Shape/Validate/Generate/Run/Observe/Learn/Evolve states covered

- [x] **P2-005** Add performance budget for large canvas/page docs  
  Files: canvas/page builder tests  
  Criteria: Large pages remain responsive under defined threshold

- [x] **P2-006** Add governance export for page artifact audit  
  Files: governance trace, backend audit API  
  Criteria: Reviewers can export trace for artifact/page change

---

## Implementation Notes

- Java: use `PageArtifactAtomicMutationRepository.saveWithAudit()` to eliminate non-atomic paths
- Java: `enforceResourceScope()` reads client-controlled headers — remove it; DB-backed `authorizeResourceScope()` is the correct gate
- Java: 5-arg constructor in `PageArtifactController` defaults to `allowAll()` — remove it
- TypeScript: Phase icon field in `PhaseConfig` must be typed, not `string` emoji
- ESLint: `design-system-enforcement` is already registered as a plugin — verify governance overrides are active
- Security tests: forged headers, cross-tenant, cross-workspace, cross-project access must all return 403
