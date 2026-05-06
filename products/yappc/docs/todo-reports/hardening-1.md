# Deep YAPPC Review — Commit `a4ea52f99106252a388a498a4e292844f40cf003`

I ran the pasted audit criteria against `samujjwal/ghatana`, target `products/yappc`, using the exact commit head you provided. Your pasted prompt requires a source-backed, deep review covering full UI/UX flow, page builder, canvas, design system, compiler/decompiler, preview, implicit AI/ML, security/privacy/o11y/audit, cognitive load, and test quality. 

---

## 1. Verified baseline

**Commit reviewed:** `a4ea52f99106252a388a498a4e292844f40cf003`

**Commit type:** substantive build/governance hardening commit, not a changelog-only commit. It adds governance checks such as shared product shell adoption and product scaffolder checks, removes stale design/layout allowlist entries, updates product/kernel audit progress, and strengthens cross-product shell/layout/design-system expectations. 

**Key YAPPC files inspected:**

* `products/yappc/frontend/web/src/routes/app/project/_phaseCockpit.tsx` 
* `products/yappc/frontend/web/src/services/phase/usePhaseCockpitData.ts` 
* `products/yappc/frontend/web/src/services/phase/PhaseCockpitConfigService.ts` 
* `products/yappc/core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/pageartifact/http/PageArtifactController.java` 
* `products/yappc/core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/pageartifact/PageArtifactPermission.java` 
* `products/yappc/frontend/web/src/services/canvas/commands/PageBuilderCommands.ts` 
* `products/yappc/frontend/web/eslint-rules/design-system-enforcement.js` 
* `products/yappc/frontend/eslint.config.js` 
* Compiler/import, residual review, preview/session, plugin/security files from the current implementation lineage were also reviewed in the previous inspection and remain important context.

---

# 2. Executive summary

YAPPC is moving in the right direction. The product is no longer just a canvas plus builder surface. At this commit, it has a stronger architecture around:

* Phase-specific cockpits
* Next-action-driven UI
* Provenance/governance visibility
* Page artifact persistence and auditability
* Semantic builder commands
* Compiler/decompiler entry points
* Design-system enforcement intent
* Shared product shell governance
* Preview and plugin security hardening

However, it is still not production-ready. The main issue is that many improvements are **architectural foundations**, not fully completed end-to-end product experiences.

## What improved meaningfully

### 1. Phase cockpits are now properly decomposed

Earlier, `_phaseCockpit.tsx` held too much logic directly. At `a4ea52`, the route now delegates to phase services like `usePhaseCockpitData`, `PhaseStatusPanels`, and `PhaseEmbeddedSurface`. 

That is a real architectural improvement. It reduces route complexity and creates reusable seams.

### 2. Phase data is centralized

`usePhaseCockpitData` now fetches project, activity, phase transition preview, and builds blockers, evidence, governance records, and suggested steps through services. 

This is much better than hardcoding everything inside the route.

### 3. Phase config is explicit and phase-specific

`PhaseCockpitConfigService` defines Intent, Shape, Validate, Generate, Run, Observe, Learn, and Evolve as distinct phase experiences, with different primary titles, descriptions, action labels, test IDs, secondary actions, and feedback. 

That directly addresses the earlier concern that phases were just aliases.

### 4. Page artifact API is more serious

`PageArtifactController` now includes RBAC permission checks, tenant mismatch checks, resource scope headers, resource-scope authorizer hooks, validation, metrics, audit recording, conflict handling, and rollback logic after audit failure. 

### 5. Page builder command model is much stronger

`PageBuilderCommands` now includes more semantic command types: reorder, action/data binding, responsive variants, state variants, suggested improvements, design-system mapping, residual island review, autosave separation, undo/redo, audit, telemetry, and validation. 

This is a major improvement over coarse page-document replacement.

### 6. Product-wide shell/design-system governance improved

The commit adds CI checks for shared product shell adoption and removes YAPPC from some stale local layout/style exception lists. 

This is a sign that YAPPC is being pulled toward shared platform/design-system discipline.

## Most critical remaining issues

1. **Phase cockpits are improved but still rely on old “advanced details” surfaces instead of fully native workflows.**
2. **Design-system enforcement rules exist, but the main ESLint config still does not appear to register the custom rule plugin.**
3. **Page artifact resource authorization still depends partly on request-provided authorized-scope headers, which must not be blindly trusted.**
4. **Metrics labels include `artifact_id` and other potentially high-cardinality values.**
5. **Audit rollback logic is complex and risky; mutation + audit should be atomic via transaction/outbox, not rollback after the fact.**
6. **Server validation must become design-system-contract-aware, not just structural.**
7. **Page-builder commands are strong but still not visibly integrated into `PageDesigner` enough to prove every UI interaction uses them.**
8. **Compiler/decompiler workflow still needs proof that source import becomes BuilderDocument/page artifacts/canvas nodes/codegen, not just imported files.**
9. **Preview signing and plugin policy must be enforced in runtime paths, not only provided as utilities.**
10. **Test quality is better, but there is still evidence of mocks/stubs around critical surfaces.**

---

# 3. Full flow analysis

## 3.1 Login → onboarding → workspace → projects

The audit target requires the complete journey from login through evolve. The commit inspected does not directly modify the login/onboarding flow in the fetched snippets, but the broader product still needs this standard:

* Use design-system form controls.
* Avoid visible “AI is thinking” language.
* Keep onboarding minimal.
* Show exactly what will be created.
* Make workspace/project creation durable and recoverable.

**Gap:** I did not see enough evidence in the commit that onboarding has been fully converted to outcome-oriented language and design-system primitives. This remains a TODO.

---

## 3.2 Dashboard

The previous review noted a next-action dashboard. The current commit improves product-shell governance and style allowlists, but there is still a likely gap: dashboard-level “next best action” must be wired to real project signals, not just static/action-prop rendering.

**Expected target:**

```text
Primary: Continue the most important work
Secondary: Resolve current blocker
Tertiary: Create/import something new
Quiet: workspace/project health
```

**Risk:** If the dashboard uses hardcoded actions or locally derived health instead of the same phase/action services, the user may receive conflicting next actions.

---

## 3.3 Project phase flow

This is the strongest current UI improvement.

`_phaseCockpit.tsx` now renders a phase cockpit with:

* Phase name/description
* Primary action card
* Blocker panel
* Evidence panel
* Suggested step panel
* Governance trace
* Advanced details section
* Phase-native summary/status panels
* Existing route details pushed into advanced tools 

The phase data hook builds the cockpit model from project API, activity API, phase transition preview, blocker builder, evidence builder, governance builder, and suggestion builder. 

The phase config is explicit and tailored per phase. 

### Strength

The product now better satisfies:

* “Where am I?”
* “What do I do next?”
* “What is blocked?”
* “What evidence supports this?”
* “Where is governance trace?”

### Gap

The advanced details still embed older route surfaces. This is much better than the previous alias problem, but the old dense surfaces are not gone. They are deferred.

That is acceptable as an intermediate state, but the target must be:

```text
Phase-native cockpit = primary workflow
Old route surface = advanced/supporting only
```

---

## 3.4 Shape → canvas → page builder

The Shape phase config says “Add Components” and points to builder/canvas details. 

The page-builder command model is strong and now covers key operations: insert, move, reorder, bindings, responsive variants, state variants, suggestions, design-system mapping, residual review, update props, delete, import, and autosave. 

### Strength

The model now matches a production-grade builder more closely.

### Gap

The source review proves the service exists, but does not prove that all current drag/drop/property/edit flows in `PageDesigner` route through this command service. This is a critical integration check.

If UI interactions still mutate documents directly, then audit/undo/telemetry/autosave will remain inconsistent.

---

## 3.5 Validate

Validate now has a phase config with “Approve Changes” and “Request Changes.” 

### Strength

The phase-specific label is correct.

### Gap

The fetched implementation shows the cockpit shell, but not a full approval workflow with durable approval records, reviewer assignment, decision comments, rejection reasons, and gate status transitions. This may exist elsewhere, but it is not proven by the inspected phase code.

---

## 3.6 Generate

Generate now has a phase config with “Generate Code” and “Preview Codegen Plan.” 

### Strength

The UX language is closer to an implementation handoff.

### Gap

The actual generate phase must show:

* Inputs being used
* Validation status
* Generated output bundle
* Test plan
* Codegen diff
* Residual/loss points
* Review gate

The current cockpit config alone does not prove that.

---

## 3.7 Run

Run now has “Check Readiness” and “View Run Plan.” 

This is a strong improvement over the previous disabled placeholder. The user can still get value even when execution is not fully enabled.

### Gap

Run must clearly distinguish:

* Capability-gated planning
* Preview execution
* Actual deployment
* Production release

No fake execution should be shown.

---

## 3.8 Observe / Learn / Evolve

Observe, Learn, and Evolve now have distinct cockpit configs. 

### Strength

This prevents phase tabs from feeling identical.

### Gap

The cockpits need real phase-native content:

* Observe: metrics, incidents, preview health, traces/logs.
* Learn: retrospective, reusable lessons, incident summaries.
* Evolve: roadmap, backlog, next cycle proposal.

If these still mostly rely on advanced embedded legacy surfaces, users may still feel like they are seeing a generic lifecycle dashboard.

---

# 4. Area-by-area review

## 4.1 Routes and IA

### Current state

Phase routes now use a reusable cockpit route model. The route-level implementation is cleaner and delegates phase data construction to a service.  

### Problems

* Phase configuration is still static and code-defined.
* Role/persona-specific phase variants are not visible.
* Tenant/tier/capability-specific variants are not visible.
* Some primary labels remain too implementation-oriented: “Generate Code,” “Add Components.”

### Improvement

Convert phase configs into a policy/capability-aware phase registry.

---

## 4.2 UI/UX quality and cognitive load

### Current state

The cockpit layout is the right UX direction. Advanced details are collapsed under `advancedTools`, which is a strong progressive-disclosure pattern. 

### Problems

* Some copy still exposes implementation concepts like “existing route details.”
* Advanced surfaces may still be dense.
* Primary action disables Validate/Generate when blockers exist, but user needs an obvious “resolve blocker” path.

### Improvement

When primary action is disabled, make the blocker panel itself the primary action.

---

## 4.3 Design system

### Current state

Custom ESLint rules exist for:

* raw buttons
* raw inputs
* raw selects
* hardcoded color tokens
* ad-hoc card classes 

The commit also reduces stale hardcoded-style and layout exceptions and adds shared product shell checks. 

### Critical problem

The main `eslint.config.js` imports standard plugins but does not appear to register the local `design-system-enforcement.js` rules. 

That means enforcement may exist as a file but not actually protect CI.

### Required improvement

Wire the custom rules into ESLint and CI.

---

## 4.4 Canvas

### Current state

The phase layer now controls when canvas appears. Shape and Generate use builder/canvas details as part of phase flow. 

### Problems

* Need proof that canvas mode adapts by phase.
* Need proof that page-builder commands are used for all editing.
* Need proof that canvas sync status is real, not local-only/hardcoded.
* Need proof that calm mode exposes only relevant tools per phase.

### Improvement

Add a `PhaseCanvasModeAdapter` that maps phase → canvas mode → visible tools → allowed actions → primary suggestions.

---

## 4.5 Page/UI builder

### Current state

`PageBuilderCommands` is now close to the desired command model. It includes responsive/state variants, bindings, suggestions, residual review, design-system mapping, and system/user history separation. 

### Problems

* The service existence is strong, but UI integration must be proven.
* `applySuggestedImprovement` simply delegates to prop update; it should also write governance/review metadata.
* `map-component-to-design-system` changes contract and props, but should validate contract compatibility.
* `review-residual-island` stores notes in node metadata; residual review likely needs artifact-level governance too.
* Command validation depends on external `validate` callback; failure handling needs stronger UX mapping.

### Improvement

Every `PageDesigner` operation must emit a `PageBuilderCommands` command and no direct document mutation should remain.

---

## 4.6 Compiler/decompiler

### Current state

Prior inspection showed `ImportSourceWorkflow` supports TSX, routes, Storybook, artifacts, and ZIPs and uses artifact compiler extractors.

### Problems

* It still appears file-import oriented.
* Full semantic product model → BuilderDocument → page artifacts → canvas nodes → codegen/diff/merge is not fully proven.
* Browser-side source path fetching must be avoided for production UX.

### Improvement

Move source import to a governed backend import service and return `PageArtifactDocument[]` plus residual islands and design-system candidates.

---

## 4.7 Live preview

### Current state

Preview security has improved in prior commits, and the current commit strengthens build/governance checks rather than preview implementation directly.

### Remaining gaps

* Signed preview session must be server-issued.
* Preview runtime must validate session token, not just origin.
* Bidirectional selection must be proven: preview click ↔ builder selection.
* Preview telemetry must be captured.

---

## 4.8 Plugins/renderers

### Current state

Prior implementation added renderer manifests and plugin policy utilities. Current commit strengthens shared shell/build governance.

### Remaining gaps

* Plugin policy enforcement must be wired into actual plugin execution/render paths.
* Network allowlist logic must use URL parsing and exact host/origin matching.
* Unknown/decompiled components should render fallback and require review by default.

---

## 4.9 Security, privacy, auditability, observability

### Current state

`PageArtifactController` is significantly stronger:

* RBAC permission checks
* tenant mismatch check
* workspace/project scope headers
* resource scope authorizer hook
* document validation
* metrics counters
* audit recording
* conflict handling
* rollback if audit fails after mutation 

Permissions are explicitly defined for read, edit, force save, import/decompile, approve changes, delete, and manage. 

### Critical concerns

#### 1. Request-provided scope headers are risky

The controller checks `X-Authorized-Workspace-IDs` and `X-Authorized-Project-IDs` request headers. 

If these are set by a trusted upstream middleware, fine. If they can be user-controlled, this is a serious security issue.

**Required:** these headers must be injected by authenticated middleware only and stripped from external requests.

#### 2. Default resource scope authorizer is allow-all

Constructor overload defaults to `PageArtifactResourceScopeAuthorizer.allowAll()`. 

That is dangerous unless only used in tests. Production wiring must require a real authorizer.

#### 3. Metrics have high-cardinality labels

Metrics include labels like `artifact_id`, `remote_version`, and possibly error strings. 

This can hurt Prometheus/cardinality.

#### 4. Rollback after audit failure is risky

The controller tries to rollback persisted document after audit write failure. 

Better: mutation + audit should be one transaction or outbox write. Rollback-after-the-fact is error-prone under concurrency.

---

## 4.10 Testing

### Current state

Phase cockpit route tests exist in prior implementation and test stubs around embedded surfaces. Earlier tests used mocks for route surfaces. That is useful at component level, but not E2E proof.

### Remaining problem

The audit standard says to call out test theater. Any test that stubs the actual canvas/lifecycle/preview routes only proves the wrapper route, not full user flow.

**Required:** add Playwright tests using real mounted surfaces.

---

# 5. P0 / P1 / P2 findings

## P0 findings

### P0-001 — Design-system enforcement is not wired into main lint config

**Evidence:** custom design-system rules exist in `design-system-enforcement.js`, but main ESLint config does not register them.  

**Impact:** raw controls and hardcoded styles can continue.

**Fix:** register local ESLint rules and CI check.

**Acceptance criteria:** changed product UI fails CI for raw `button`, `input`, `select`, hardcoded color, and ad-hoc card classes.

---

### P0-002 — Page artifact resource scope can be bypassed if scope headers are user-controlled

**Evidence:** controller checks `X-Authorized-Workspace-IDs` and `X-Authorized-Project-IDs` headers. 

**Impact:** if headers are not stripped/injected by trusted middleware, authorization can be forged.

**Fix:** move scope resolution entirely into server-side authenticated context.

**Acceptance criteria:** external requests cannot set authorization scope; tests prove forged headers are ignored.

---

### P0-003 — Production wiring must not use allow-all resource authorizer

**Evidence:** default constructor uses `PageArtifactResourceScopeAuthorizer.allowAll()`. 

**Impact:** accidental production wiring could bypass resource-level authorization.

**Fix:** make allow-all test-only or remove default constructor in production module.

**Acceptance criteria:** production module cannot instantiate controller without real authorizer.

---

### P0-004 — Audit durability must be atomic, not rollback-after-failure

**Evidence:** controller saves document, then tries audit write, then attempts rollback on audit failure. 

**Impact:** rollback can fail or conflict; audit can still be inconsistent.

**Fix:** use DB transaction or durable outbox.

**Acceptance criteria:** mutation and audit/outbox are committed atomically.

---

### P0-005 — Page-builder command service must be proven as the only mutation path

**Evidence:** `PageBuilderCommands` is strong, but source review only proves service existence. 

**Impact:** direct mutations could bypass undo/redo/audit/telemetry.

**Fix:** refactor `PageDesigner` so all operations call `PageBuilderCommands`.

**Acceptance criteria:** tests fail if direct document mutation bypasses commands.

---

## P1 findings

### P1-001 — Phase cockpits still need deeper native panels

**Evidence:** `_phaseCockpit.tsx` still includes advanced embedded existing route details. 

**Fix:** implement phase-native panels for Validate, Generate, Run, Observe, Learn, Evolve.

---

### P1-002 — Phase config should be capability/role-aware

**Evidence:** config is static by phase. 

**Fix:** generate phase config from role, capability, tenant tier, feature flags, and project state.

---

### P1-003 — Metrics labels need cardinality review

**Evidence:** controller metrics include artifact ID labels. 

**Fix:** remove high-cardinality labels from metrics; move IDs to traces/logs.

---

### P1-004 — Server validation must become design-system-contract-aware

**Evidence:** page artifact validation exists, but inspected server flow references generic validation. 

**Fix:** validate component contracts, prop schema, slot validity, action/data binding policy, privacy classification, and residual review state.

---

### P1-005 — Generate phase needs real codegen package UX

**Evidence:** Generate config exists but does not prove full codegen/diff/merge surface. 

**Fix:** add codegen package panel, output files, tests, residuals, diff, merge, review gate.

---

## P2 findings

### P2-001 — Phase copy should remove implementation language

**Evidence:** `_phaseCockpit.tsx` uses “Existing route details.” 

**Fix:** replace with task-specific labels.

---

### P2-002 — Next action feedback should be persisted or actionable

Current feedback is local state in `_phaseCockpit.tsx`. 

**Fix:** action feedback should either open a real panel/action or persist a task/decision.

---

### P2-003 — Icons/emojis should move to design-system tokens

Phase config uses emoji icons. 

**Fix:** use design-system icon registry for consistent visual language.

---

# 6. Implementation plan

## Milestone 1 — Security and persistence hardening

**Files:**

* `PageArtifactController.java`
* `PageArtifactResourceScopeAuthorizer`
* `PageArtifactModule`
* DB repositories
* auth middleware

**Tasks:**

1. Remove production path to `allowAll()`.
2. Strip client-provided `X-Authorized-*` headers at gateway.
3. Resolve scopes from server-side auth context.
4. Replace rollback-after-audit with atomic mutation + audit/outbox.
5. Remove high-cardinality metric labels.

**Tests:**

* forged scope headers rejected
* cross-workspace denied
* cross-project denied
* cross-tenant denied
* mutation and audit atomic
* rollback/outbox failure behavior

---

## Milestone 2 — Design-system enforcement

**Files:**

* `eslint.config.js`
* `design-system-enforcement.js`
* `NextActionDashboard`
* `ActionDiscoveryPalette`
* phase components
* residual review panel

**Tasks:**

1. Register custom rules.
2. Run in warning mode.
3. Fix new components first.
4. Promote changed-file violations to errors.
5. Add CI gate.

**Tests/checks:**

* lint fails on raw button/input/select
* lint fails on hardcoded colors
* lint fails on ad-hoc card classes

---

## Milestone 3 — Phase-native cockpit completion

**Files:**

* `PhaseCockpitConfigService.ts`
* `usePhaseCockpitData.ts`
* `PhaseStatusPanels`
* `PhaseEmbeddedSurface`
* new phase panel components

**Tasks:**

1. Build `ValidateApprovalPanel`.
2. Build `GeneratePackagePanel`.
3. Build `RunReadinessPanel`.
4. Build `ObserveSignalsPanel`.
5. Build `LearnRetrospectivePanel`.
6. Build `EvolveRoadmapPanel`.
7. Make embedded legacy surfaces advanced-only.

**Acceptance criteria:**

* every phase is useful before opening advanced details
* every phase has blocker/evidence/governance
* every primary action either works or points directly to blocker resolution

---

## Milestone 4 — Page-builder command integration

**Files:**

* `PageDesigner`
* `PropertyForm`
* `ComponentRenderer`
* `PageDesignerNode`
* `PageBuilderCommands`

**Tasks:**

1. Route all page mutations through `PageBuilderCommands`.
2. Remove direct document mutation paths.
3. Emit semantic audit for each command.
4. Bind validation errors to UI.
5. Make undo/redo reflect semantic commands.
6. Persist command history or audit summary where needed.

**Tests:**

* insert command
* reorder command
* responsive command
* action/data binding command
* residual review command
* design-system mapping command
* undo/redo
* autosave not in user history

---

## Milestone 5 — Compiler/decompiler integration

**Tasks:**

1. Move source import to backend service.
2. Return `PageArtifactDocument[]`.
3. Create canvas page nodes.
4. Render residual islands.
5. Generate codegen preview.
6. Show diff/merge.
7. Block unsafe low-fidelity output.

**Tests:**

* TSX import
* route import
* Storybook import
* multi-page import
* residual island review
* codegen diff
* unsafe component fallback

---

## Milestone 6 — Full-flow E2E

**Tests:**

1. Login/onboarding → workspace → project.
2. Intent → define requirements.
3. Shape → canvas → page builder.
4. Page builder → add/edit/reorder/preview/save.
5. Validate → approval gate.
6. Generate → codegen package.
7. Run → readiness plan.
8. Observe → preview/metrics.
9. Learn → retrospective.
10. Evolve → roadmap/backlog.

---

# 7. Test plan

## Unit

* phase config
* phase blocker/evidence/suggestion builders
* page-builder commands
* provenance badges
* design-system enforcement rules
* page artifact validation
* resource scope authorizer

## Integration

* page artifact controller + DB
* phase cockpit data hook with real API mock
* PageDesigner + PageBuilderCommands
* compiler import workflow
* residual review persistence

## E2E

* full mounted phase flow
* canvas/page builder
* live preview
* save/reload/conflict
* source import/decompile/codegen
* approval/generation/run/observe/learn/evolve flow

## Security

* forged scope headers
* cross-tenant access
* preview session forging/expiry
* unsafe component execution
* plugin network allowlist bypass
* CSP/sandbox escape

## Accessibility

* keyboard phase navigation
* keyboard command palette
* keyboard page builder
* focus trap
* live region announcements
* color contrast for status/provenance

---

# 8. Readiness score

| Area                       | Score | Reason                                                                                        |
| -------------------------- | ----: | --------------------------------------------------------------------------------------------- |
| UI/UX flow                 |    74 | Phase cockpit model is now real and decomposed.                                               |
| Cognitive load             |    75 | Primary action, blockers, evidence, governance are surfaced.                                  |
| Phase maturity             |    76 | Strong phase config and data service; native panels still incomplete.                         |
| Canvas                     |    68 | Phase route integration exists; phase-aware runtime behavior still needs proof.               |
| Page builder               |    76 | Command service is much stronger; UI integration needs proof.                                 |
| Design system              |    55 | Governance improved, rules exist, but enforcement wiring is questionable.                     |
| Compiler/decompiler        |    62 | Import/source workflow exists; full artifact graph/codegen flow not proven.                   |
| Preview                    |    72 | Prior security improvements plus session model; runtime/server integration still needs proof. |
| Persistence                |    82 | Controller and DB path are serious; auth/audit/metrics need hardening.                        |
| Security/privacy           |    66 | Stronger but still risky around scope headers/allowAll/signing/runtime policy.                |
| Auditability               |    70 | Audit exists; atomicity must be improved.                                                     |
| Observability              |    66 | Metrics/tracing exist, but cardinality and coverage need hardening.                           |
| AI/automation implicitness |    66 | Next-action UX better; legacy AI wording likely remains in parts.                             |
| Test confidence            |    58 | Better tests, but mocked critical flows remain.                                               |
| Overall                    |    69 | Strong architectural progress; still not production-ready.                                    |

---

# 9. Detailed TODO list

| Priority | TODO                                                                         | Area                | Files / Modules                                                            | Acceptance Criteria                                                                       |
| -------- | ---------------------------------------------------------------------------- | ------------------- | -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| P0       | Register design-system ESLint plugin in main config                          | Design system       | `products/yappc/frontend/eslint.config.js`, `design-system-enforcement.js` | Raw `button/input/select`, hardcoded colors, and ad-hoc cards are detected in CI.         |
| P0       | Remove production access to `PageArtifactResourceScopeAuthorizer.allowAll()` | Security            | `PageArtifactController`, `PageArtifactModule`                             | Production module must require real resource authorizer.                                  |
| P0       | Stop trusting client-provided authorized-scope headers                       | Security            | Auth middleware, `PageArtifactController`                                  | External requests cannot forge `X-Authorized-Workspace-IDs` / `X-Authorized-Project-IDs`. |
| P0       | Make page artifact mutation + audit atomic                                   | Audit/persistence   | `PageArtifactController`, repositories, migrations                         | Save and audit commit together or outbox commits with mutation.                           |
| P0       | Remove high-cardinality metric labels                                        | O11y                | `PageArtifactController`                                                   | Metrics do not label by artifact ID, remote version, or raw error string.                 |
| P0       | Prove all PageDesigner mutations use `PageBuilderCommands`                   | Builder             | `PageDesigner`, `PropertyForm`, `ComponentRenderer`, `PageDesignerNode`    | No direct document mutation path bypasses command audit/undo/telemetry.                   |
| P0       | Add real builder E2E without mocking renderer/compiler                       | Testing             | page builder tests, Playwright                                             | Drag/drop/edit/reorder/preview/save tested on mounted UI.                                 |
| P0       | Add cross-tenant/workspace/project security tests                            | Security            | backend controller tests                                                   | Unauthorized resource access returns 403.                                                 |
| P1       | Build native `ValidateApprovalPanel`                                         | Phase UX            | phase components                                                           | Validate phase is approval-first without opening lifecycle details.                       |
| P1       | Build native `GeneratePackagePanel`                                          | Phase UX            | phase components, compiler services                                        | Generate phase shows package, output files, tests, residuals, diff.                       |
| P1       | Build native `RunReadinessPanel`                                             | Phase UX            | phase components                                                           | Run phase shows capability gates and manual/automated run plan.                           |
| P1       | Build native `ObserveSignalsPanel`                                           | Phase UX            | phase components, preview/o11y services                                    | Observe shows preview health, metrics, incidents, traces/logs.                            |
| P1       | Build native `LearnRetrospectivePanel`                                       | Phase UX            | phase components                                                           | Learn captures lessons and reusable patterns.                                             |
| P1       | Build native `EvolveRoadmapPanel`                                            | Phase UX            | phase components                                                           | Evolve shows roadmap/backlog next-cycle plan.                                             |
| P1       | Make phase config capability/role/tier-aware                                 | UX architecture     | `PhaseCockpitConfigService`                                                | Phase CTAs adapt by role, feature flag, capability, and project state.                    |
| P1       | Replace phase emoji icons with design-system icon registry                   | Design system       | `PhaseCockpitConfigService`                                                | Icons are theme-consistent and accessible.                                                |
| P1       | Add contract-aware server validation                                         | Backend validation  | `PageArtifactValidator`                                                    | Unknown contracts, invalid props, invalid slots, cycles, orphan nodes are rejected.       |
| P1       | Add privacy/data-classification propagation validation                       | Privacy             | `PageArtifactValidator`, builder contracts                                 | Input/data-bound components require correct classification and consent policy.            |
| P1       | Move compiler source import to backend                                       | Compiler/security   | import workflow, API service                                               | Browser does not fetch arbitrary local repo paths.                                        |
| P1       | Convert import workflow output to `PageArtifactDocument[]`                   | Compiler            | `ImportSourceWorkflow`, artifact compiler bridge                           | TSX/route/story import creates canvas page artifacts.                                     |
| P1       | Add residual review persistence                                              | Compiler/governance | `ResidualIslandReviewPanel`, backend audit                                 | Accept/reject/review is persisted and audited.                                            |
| P1       | Add codegen preview + diff + safe merge                                      | Generate            | compiler/codegen services                                                  | Generated code cannot silently overwrite existing unsupported logic.                      |
| P1       | Add preview session server issuance                                          | Preview security    | backend preview/session API, frontend preview route                        | No signing secret in frontend bundle.                                                     |
| P1       | Validate preview token in `/preview/builder`                                 | Preview security    | preview route                                                              | Forged/expired/out-of-scope sessions fail closed.                                         |
| P1       | Enforce plugin runtime policy in execution path                              | Plugin security     | plugin loader, renderer, preview runtime                                   | Network/storage/browser API access is actually blocked at runtime.                        |
| P1       | Replace substring URL allowlist checks with exact URL parsing                | Plugin security     | `PluginRuntimePolicy`                                                      | `evil-api.ghatana.com.attacker.com` cannot pass allowlist.                                |
| P1       | Add Playwright full lifecycle test                                           | Testing             | E2E suite                                                                  | Login/onboarding → evolve flow passes on mounted app.                                     |
| P1       | Add accessibility regression suite                                           | Accessibility       | E2E/a11y tests                                                             | Keyboard/focus/ARIA/live regions validated for major flows.                               |
| P2       | Rename implementation copy like “Existing route details”                     | UX writing          | `_phaseCockpit.tsx`, phase components                                      | User-facing copy is task-oriented and non-technical.                                      |
| P2       | Make disabled primary CTA point to blocker resolution                        | UX                  | `PhasePrimaryActionCard`, blockers                                         | Disabled action always has direct remediation path.                                       |
| P2       | Integrate next-best-action service into dashboard and command palette        | Automation UX       | dashboard, command palette, phase services                                 | Same top action appears consistently across surfaces.                                     |
| P2       | Add visual regression for phase cockpits                                     | UI testing          | visual tests                                                               | Intent/Shape/Validate/Generate/Run/Observe/Learn/Evolve states covered.                   |
| P2       | Add performance budget for large canvas/page docs                            | Performance         | canvas/page builder tests                                                  | Large pages remain responsive under defined threshold.                                    |
| P2       | Add governance export for page artifact audit                                | Audit UX            | governance trace, backend audit API                                        | Reviewers can export trace for artifact/page change.                                      |

---

## Bottom line

At `a4ea52f99106252a388a498a4e292844f40cf003`, YAPPC has made real progress. The biggest improvements are phase cockpit decomposition, stronger page artifact persistence/security/audit, semantic page-builder commands, and shared shell/design-system governance.

The next work should be intentionally strict:

```text
activate enforcement
+ make scope authorization server-owned
+ make audit atomic
+ prove builder commands are the only mutation path
+ convert phase wrappers into true phase-native workflows
+ prove compiler/decompiler end-to-end
+ add real E2E/security/accessibility tests
```

The product is getting closer to the target vision, but the current state is still **foundation-plus-partial-integration**, not yet a fully hardened, production-grade, low-cognitive-load YAPPC experience.
