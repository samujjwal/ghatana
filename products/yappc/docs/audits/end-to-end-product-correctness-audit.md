
## 1. Verified baseline

The requested commit `b104716d78970ef6184fedb96b4cea7717f3bfea` is **changelog-only**. It updates `products/yappc/CHANGELOG.md` and points to the substantive implementation commit `210d4de1a0efb8ca7fcde4c8ff84deec4c39fae7`. 

I compared the previous reviewed head `719869ab1ac1bdef9608c8646079d96d28441c5b` against `210d4de1a0efb8ca7fcde4c8ff84deec4c39fae7`. The delta is significant for YAPPC: it adds phase cockpits, provenance badges, next-action dashboard, action discovery palette, semantic page-builder commands, source-import workflow, residual island review UI, DB-backed page artifact persistence, server validation, preview sessions, unsafe component handling, plugin runtime policy, and additional tests.

---

# 2. Executive summary

YAPPC has improved materially since the previous review. The product now has real movement toward the desired vision:

```text
low cognitive load
+ phase-specific UX
+ page-builder/canvas integration
+ durable page artifact backend
+ provenance
+ action discovery
+ preview/runtime security
+ plugin policy utilities
+ compiler/decompiler workflow scaffolding
```

However, the implementation is still uneven. The new work often creates **good architectural entry points**, but several are not yet wired deeply enough to be production-grade.

## Biggest improvements

1. **Phase-specific cockpits now exist.** Phase routes no longer simply export canvas/lifecycle directly; they wrap each phase in `_phaseCockpit.tsx` with primary action, blockers, evidence, suggestions, governance trace, and supporting surface. 

2. **Run phase is no longer just a disabled placeholder.** `run.tsx` now routes to `RunCockpitRoute`, preserving a useful cockpit surface even while execution capabilities remain constrained. 

3. **Backend page artifact persistence has become real.** `DbPageArtifactRepository` adds database-backed persistence, optimistic concurrency, version archiving, tenant/workspace/project scoping, and JSON persistence for builder documents and governance records. 

4. **Server-side validation exists.** `PageArtifactValidator` checks structure, root references, slot references, sync/trust/data-classification values, residual island limits, round-trip fidelity, governance requirements, and simple executable payload patterns. 

5. **Page artifact controller now has authz, metrics, tracing, validation, audit, conflict handling, and tenant mismatch checks.** 

6. **Semantic page-builder commands now exist.** `PageBuilderCommands` supports insert, move, update props, delete, import, autosave, undo/redo, audit callback, telemetry callback, validation, and autosave scheduling. 

7. **Compiler/decompiler UX is starting to become real.** `ImportSourceWorkflow` supports TSX, route, Storybook, artifact, and ZIP source types and calls artifact compiler extractors. 

8. **Preview/session/runtime security is stronger.** Signed preview sessions, scoped artifacts/projects, expiration, HMAC signatures, and validation helpers now exist. 

9. **Unsafe custom component handling exists.** Static analysis now detects risky browser APIs, network/storage use, inline scripts, `eval`, `Function`, and can apply policy transforms. 

10. **Design-system enforcement files exist.** Custom ESLint rules now attempt to block raw `button`, `input`, `select`, hardcoded colors, and ad-hoc card classes. 

## Biggest risks

1. **Many new systems are scaffolds or wrappers, not yet complete product-grade flows.**
2. **Phase cockpits still embed old supporting surfaces, so cognitive load is reduced but not eliminated.**
3. **Design-system enforcement rules exist but are not wired into the main ESLint config.**
4. **Page-builder “E2E” tests are still not true E2E; they mock core internals.**
5. **Preview session signing appears client-side capable, which is risky if a secret key ever reaches browser code.**
6. **Plugin/runtime policy is utility-level; enforcement integration is not proven.**
7. **Compiler/decompiler flow imports files but does not yet fully convert source → semantic model → BuilderDocument → page artifact → canvas nodes → codegen/diff/merge.**
8. **Server validation is useful but not design-system-contract-aware enough.**
9. **Authorization is permission-based, but resource-level workspace/project ownership checks are not obvious from the controller.**
10. **Audit is emitted asynchronously and failures do not block sensitive operations.**

---

# 3. Full user journey review

## 3.1 Login → onboarding → workspace

The prior issues still largely stand: onboarding is useful and guided, but historically used explicit “AI” wording. The new codebase starts moving toward next-action and cockpit patterns, but I did not see enough evidence that login/onboarding/workspace were fully migrated to design-system primitives or fully purged of visible AI wording.

**Needed:** login/onboarding/workspace should be migrated to design-system components, with outcome-oriented suggestion language and no visible AI-first wording.

---

## 3.2 Dashboard

A new `NextActionDashboard` exists and matches the desired UX direction: primary action, secondary action, tertiary action, and quiet health indicators. 

### Strength

This directly supports the desired “dashboard-first, next best action” model.

### Problems

`NextActionDashboard` uses raw clickable `<div role="button">`, raw classes, hardcoded status colors, and direct icon/color logic instead of design-system `Card`, `Button`, `Alert`, `Chip`, and status-token helpers. 

### Required fix

Convert `NextActionDashboard` to design-system primitives and use actual `button`/`Button` semantics rather than clickable divs.

---

## 3.3 Project phase journey

The biggest improvement is `_phaseCockpit.tsx`. It creates phase-specific config for Intent, Shape, Validate, Generate, Run, Observe, Learn, and Evolve, with phase-specific primary actions, descriptions, blockers, evidence, suggestions, governance trace, and embedded supporting surfaces. 

`intent.tsx` now imports `IntentCockpitRoute`, which is a real upgrade from the earlier route alias model. 

`run.tsx` now renders `RunCockpitRoute`, replacing the old hard-disabled placeholder with a cockpit route. 

### Strength

The phase cockpit structure directly addresses the prior cognitive-load gap:

* One primary action.
* Blocker panel.
* Evidence panel.
* Suggested next step.
* Governance trace.
* Supporting surface below.

The `PhaseCockpitLayout` explicitly encodes this structure. 

### Critical issue

The cockpit still embeds old surfaces below the new cockpit:

* Intent embeds project overview.
* Shape and Generate embed canvas.
* Validate/Learn/Evolve embed lifecycle.
* Observe embeds preview plus lifecycle.
* Run embeds deploy route. 

This is better than aliasing, but still not a full phase-specific experience. The cockpit is currently a **wrapper layer**, not yet a fully decomposed phase UX.

### Required fix

Each phase should gradually replace generic embedded surfaces with phase-specific panels. The embedded old route can remain as an advanced/supporting surface, but the primary workflow should be native to the cockpit.

---

# 4. Area-by-area review

## 4.1 Information architecture

### Current state

YAPPC now has a better IA because phase routes are cockpit-backed. The phase config is explicit and readable, and each phase has its own purpose and action labels. 

### Problems

The config is hardcoded in one large route file. That makes it harder to localize, personalize by role, gate by tier, or adapt by workspace/org policy.

### Required improvement

Move `PHASE_CONFIG`, phase blocker generation, evidence generation, and suggested steps into a typed phase domain service:

```text
src/services/phase/PhaseCockpitConfigService.ts
src/services/phase/PhaseEvidenceBuilder.ts
src/services/phase/PhaseBlockerBuilder.ts
src/services/phase/PhaseSuggestionBuilder.ts
```

---

## 4.2 Cognitive load

### Current state

Phase cockpits, next-action dashboard, action discovery, provenance badges, and governance trace are all aligned with low cognitive load.    

### Problems

Several new components still expose implementation-level or generic language:

* “Supporting surface”
* “Mounted from the existing project route”
* “Generated output details will appear once preview planning is available”
* `aiHealthScore`
* `aiNextActions`

These may be okay internally, but visible copy should be outcome-oriented and user-centered.

### Required improvement

Replace “supporting surface” with task-specific names:

| Current                                 | Better                       |
| --------------------------------------- | ---------------------------- |
| Supporting surface                      | Details                      |
| Mounted from existing route             | Review details below         |
| aiHealthScore                           | Health score                 |
| aiNextActions                           | Suggested next actions       |
| Generated output details will appear... | Output plan is not ready yet |

---

## 4.3 Design system

### Current state

Custom ESLint rules exist for raw buttons, raw inputs, raw selects, hardcoded colors, and ad-hoc card classes. 

### Critical issue

The main ESLint config does **not** appear to import or register the custom `design-system-enforcement.js` plugin/rules. It imports standard plugins like `react`, `jsx-a11y`, `security`, `sonarjs`, etc., but not the local design-system enforcement rules. 

So design-system enforcement currently appears to be **implemented but not active**.

### Additional issue

Many newly added components still use raw buttons, raw inputs, raw details/summary, hardcoded colors, and ad-hoc cards:

* `PhaseCockpitLayout` uses raw classes and native `details`. 
* `NextActionDashboard` uses clickable divs and raw colors. 
* `ActionDiscoveryPalette` uses raw `button`, `input`, and hardcoded colors. 
* `ResidualIslandReviewPanel` imports from `yappc-ui/components`, not consistently from `@ghatana/design-system`. 

### Required fix

1. Wire the custom ESLint plugin into `eslint.config.js`.
2. Start in warning mode, then move to error mode.
3. Replace new raw controls in the new components before migrating older routes.

---

## 4.4 Canvas

### Current state

The new phase cockpit structure makes canvas phase-aware at the route layer. Shape and Generate mount canvas as the supporting surface. 

A new `PhaseCanvasConfig.ts` file exists in the delta, but I did not inspect enough evidence to conclude it is fully wired into the canvas runtime.

### Risk

Canvas may still behave similarly across Shape and Generate, because `_phaseCockpit.tsx` embeds the same `CanvasRoute` for both. 

### Required fix

Canvas must adapt palette/tools/mode by phase:

* Shape: requirements, components, page builder, layout.
* Generate: codegen plan, output artifacts, validation gates, diff readiness.
* Validate: review overlays and approval comments.
* Observe: preview and telemetry nodes.

---

## 4.5 Page/UI builder

### Current state

`PageBuilderCommands` is a strong improvement. It introduces semantic commands with validation, audit, telemetry, autosave, and undo/redo. 

### Strengths

* Semantic command types exist.
* Undo/redo stacks serialize before/after.
* Audit callback exists.
* Telemetry callback exists.
* Autosave exists.
* Validation callback exists.

### Problems

The command set is still incomplete compared with the product vision.

Missing command types:

```text
ReorderComponentCommand
SetResponsiveVariantCommand
SetStateVariantCommand
AddActionBindingCommand
AddDataBindingCommand
ApplySuggestedImprovementCommand
ReviewResidualIslandCommand
MapComponentToDesignSystemCommand
```

Also, autosave is added into `commandHistory`, which can pollute meaningful user operation history with internal persistence operations. 

### Required fix

Separate:

```text
User operation history
System operation history
Persistence/sync history
Audit trail
```

Autosave should not be treated as a normal user command.

---

## 4.6 Compiler/decompiler

### Current state

`ImportSourceWorkflow` supports `tsx`, `route`, `storybook`, `artifact`, and `zip` import source types and uses `yappc-artifact-compiler` extractors for components, pages, and CSF. 

### Strength

This is a major improvement over pasted JSON-only import.

### Critical issue

The workflow still primarily returns imported files and metadata, not full page artifacts or BuilderDocuments. It does not yet complete:

```text
source
→ extractors
→ ArtifactGraph
→ SemanticProductModel
→ BuilderDocument
→ PageArtifactDocument
→ canvas nodes
→ residual review
→ codegen/diff/merge
```

### Browser/runtime concern

`ImportSourceWorkflow` attempts local `node:fs/promises` reads when it detects Node, otherwise fetches the source.  This may be fine for tests/dev tools, but product web runtime needs a clearer server-backed source import model. Browser UI cannot safely read arbitrary local repo paths.

### Required fix

Move source import to a backend/source-service boundary and make the frontend call a governed import API.

---

## 4.7 Residual islands

### Current state

`ResidualIslandReviewPanel` exists and supports severity, required action, code snippet, source path/line, accept/reject/review, and review notes. 

### Strength

This is exactly the kind of UI needed for decompiler transparency.

### Problems

* It does not show round-trip fidelity.
* It does not show provenance badges.
* It does not distinguish blocking vs advisory residuals strongly enough.
* It uses `yappc-ui/components`, not the canonical design system.
* Accept/reject callbacks are local; no persistence/audit integration is shown in this component.

### Required fix

Residual review must integrate with:

```text
governance records
source locations
fidelity score
codegen blocking
audit trail
review persistence
design-system mapping
```

---

## 4.8 Backend persistence and audit

### Current state

Backend persistence is now substantially better:

* `DbPageArtifactRepository` persists artifacts and archives versions. 
* `PageArtifactValidator` validates server-side documents. 
* `PageArtifactController` enforces authz, validates tenant mismatch, runs validation, emits metrics, and records audit. 

### Critical risks

#### Risk 1 — authorization appears permission-only, not resource-specific

The controller checks `authorizationService.hasPermission(user, requiredPermission)`, but it does not visibly check whether the user has access to this specific workspace/project/artifact. 

Required:

```text
can user edit artifact in this workspace/project?
can user read this project?
can user force-save this artifact?
can user import/decompile in this workspace?
```

#### Risk 2 — audit is asynchronous and non-blocking

`emitAuditEvent` calls `auditRepository.record(...).whenException(...)`, but save/load can continue even if audit persistence fails. 

For first-class auditability, sensitive operations should either:

* write audit synchronously in the same transaction, or
* write to a durable outbox in the same transaction.

#### Risk 3 — validation is structural, not contract-aware enough

The validator checks `contractName` exists, but does not verify:

* contract exists in design-system registry
* props match contract schema
* required props exist
* slots are valid for the component
* data bindings/actions are policy-compliant
* duplicate child refs across slots
* cycles in component tree
* orphan nodes
* privacy classification propagation



---

## 4.9 Preview security

### Current state

`PreviewSession` adds HMAC-signed sessions, scope, expiration, project/artifact restrictions, and validation. 

### Strength

The model is correct directionally.

### Critical risk

`createPreviewSession` takes `secretKey` in frontend TypeScript. If this is ever called in browser code with a real secret, the signing model is compromised. 

### Required fix

Preview sessions must be created and signed server-side only.

Frontend should only receive:

```text
previewSessionId
signedToken
expiresAt
allowedArtifactIds
allowedProjectIds
```

It should never receive the signing secret.

---

## 4.10 Unsafe component and plugin runtime policy

### Current state

`UnsafeComponentHandler` statically analyzes component code and can detect dangerous APIs. 

`PluginRuntimePolicy` defines network/storage/browser API/telemetry policies and enforcement helpers. 

### Strength

The security model is getting richer and closer to the desired “custom/decompiled components are safe by default” posture.

### Critical issues

#### Issue 1 — utility-level enforcement is not enough

I saw policy helpers, but not evidence that rendering/import/plugin execution paths always invoke them before execution.

#### Issue 2 — domain allowlist uses substring matching

`enforceNetworkPolicy` checks `url.includes(domain)`.  This can be bypassed by malicious domains containing the allowed substring.

Use URL parsing and exact origin/hostname matching.

#### Issue 3 — static scanning is not a sandbox

`UnsafeComponentHandler` is useful as a preflight, but static analysis cannot be the only runtime security mechanism. 

Required:

* iframe/sandbox execution
* CSP
* import map restrictions
* network proxy/allowlist
* server-side review gates
* no arbitrary runtime code in the main app context

---

# 5. P0 / P1 / P2 findings

## P0-001 — Design-system enforcement exists but is not active

**Evidence:** Custom ESLint rules exist in `design-system-enforcement.js`, but `eslint.config.js` does not register that plugin/rules.  

**Impact:** Raw controls, hardcoded colors, and ad-hoc cards will continue to enter the codebase.

**Fix:** Import and register local ESLint plugin in `eslint.config.js`.

**Acceptance criteria:**

* CI reports raw controls in product UI.
* New raw `button/input/select` use fails in error mode.
* Approved exceptions are explicit.

---

## P0-002 — Phase cockpits reduce cognitive load but still embed generic route surfaces

**Evidence:** `_phaseCockpit.tsx` renders old route components as supporting surfaces: project overview, canvas, lifecycle, deploy, preview. 

**Impact:** Users still face dense generic screens after the cockpit wrapper.

**Fix:** Gradually decompose embedded surfaces into phase-native panels.

**Acceptance criteria:**

* Validate shows approval/gate workflow first, lifecycle details second.
* Generate shows codegen package/diff first, canvas second.
* Observe shows metrics/preview/incidents first, lifecycle second.
* Learn shows retrospective/patterns first.
* Evolve shows roadmap/backlog first.

---

## P0-003 — Page-builder E2E still mocks core internals

**Evidence:** `pageBuilderE2E.test.tsx` mocks `artifactCompilerBridge`, `rendererManifest`, and `ComponentRenderer`; it tests some behavior but not real drag/drop/render/compiler paths. 

**Impact:** Tests still do not prove the actual builder workflow.

**Fix:** Add real Playwright E2E and component integration tests without mocking renderer/compiler.

**Acceptance criteria:**

* Real drag from palette.
* Real drop into slot.
* Real reorder.
* Real property edit.
* Real preview update.
* Real save/reload/conflict.

---

## P0-004 — Preview signing must not be client-secret based

**Evidence:** `createPreviewSession` accepts `secretKey` in frontend security module. 

**Impact:** If used in browser, signing is meaningless.

**Fix:** Move signing server-side; frontend validates/uses token only.

**Acceptance criteria:**

* No secret key in frontend bundle.
* Server issues preview sessions.
* Preview route validates token/session.
* Expired/forged sessions fail.

---

## P0-005 — Page artifact authorization is not resource-specific enough

**Evidence:** Controller checks permission strings with `authorizationService.hasPermission`, but resource-level workspace/project/artifact checks are not evident. 

**Impact:** A user with generic permission may access artifacts outside allowed workspace/project if upstream routing does not prevent it.

**Fix:** Add resource-scoped authorization.

**Acceptance criteria:**

* User with edit permission in workspace A cannot edit workspace B.
* Tenant/project mismatch returns 403.
* Tests cover cross-tenant, cross-workspace, cross-project cases.

---

## P0-006 — Audit must be durable for sensitive operations

**Evidence:** Audit write is asynchronous and failure does not block the page artifact save/load response. 

**Impact:** Audit can be lost while mutation succeeds.

**Fix:** Use same-transaction audit write or durable outbox.

**Acceptance criteria:**

* Save and audit are atomic, or outbox event is atomic.
* Audit write failure is visible.
* Compliance-critical operations cannot silently lose audit.

---

## P1-001 — Server validation lacks design-system contract enforcement

**Evidence:** `PageArtifactValidator` checks node `contractName` but not registry contract existence, prop schemas, required props, slot validity, cycles, orphan nodes, or privacy propagation. 

**Fix:** Add contract-aware validation.

---

## P1-002 — Compiler workflow still returns files, not full BuilderDocument/page artifact graph

**Evidence:** `ImportSourceWorkflow` calls extractors and returns files/metadata, not complete semantic product model → BuilderDocument → canvas artifact flow. 

**Fix:** Integrate artifact compiler pipeline all the way into page artifacts and canvas nodes.

---

## P1-003 — Plugin network allowlist is bypassable

**Evidence:** `enforceNetworkPolicy` uses `url.includes(domain)`. 

**Fix:** Parse URL and compare exact host/origin.

---

## P1-004 — Action discovery is useful but still raw UI

**Evidence:** `ActionDiscoveryPalette` uses raw `button`, `input`, hardcoded colors, and local confirmation UI. 

**Fix:** Rebuild with design-system `Dialog`, `CommandList`, `Button`, `Alert`, `Badge`, and focus trap.

---

## P1-005 — Next-best-action service is deterministic but simplistic

**Evidence:** `NextBestActionService` ranks actions based on hardcoded phase actions, blockers, state, activity, permissions, and capabilities. 

**Fix:** Feed actual project health, validation, artifact readiness, role/persona, feature flags, recent user actions, and policy gates.

---

# 6. Implementation plan

## Milestone 1 — Activate design-system enforcement

### Tasks

1. Register `design-system-enforcement.js` in `eslint.config.js`.
2. Add warning mode for all product UI files.
3. Add error mode for new/changed files.
4. Migrate new components first:

   * `NextActionDashboard`
   * `ActionDiscoveryPalette`
   * `PhaseCockpitLayout`
   * `ResidualIslandReviewPanel`
5. Add CI rule to fail on raw controls in changed files.

### Acceptance criteria

* CI catches raw `button/input/select`.
* New components use design-system primitives.
* No hardcoded colors in new product UI.

---

## Milestone 2 — Make phase cockpits truly phase-native

### Tasks

1. Split `_phaseCockpit.tsx` into smaller services/components.
2. Replace generic embedded route content with phase-native panels.
3. Keep old routes under “Advanced details.”
4. Add phase-specific empty states and errors.
5. Add provenance badges to all evidence, blockers, suggestions, and governance records.

### Acceptance criteria

* Each phase has one clear primary CTA.
* Each phase is useful without opening the embedded old route.
* Supporting surface is secondary, not primary.

---

## Milestone 3 — Harden page artifact backend

### Tasks

1. Add resource-scoped authorization.
2. Add contract-aware validation.
3. Add cycle/orphan/duplicate-child validation.
4. Add atomic audit/outbox.
5. Add test cases for cross-tenant/workspace/project access.
6. Add metrics cardinality review; avoid artifact IDs in high-cardinality metric labels.

### Acceptance criteria

* Cross-scope access fails.
* Invalid contract/props/slots fail server validation.
* Audit is durable.
* Metrics are safe for production cardinality.

---

## Milestone 4 — Complete semantic page-builder commands

### Tasks

Add commands:

```text
ReorderComponentCommand
SetResponsiveVariantCommand
SetStateVariantCommand
AddActionBindingCommand
AddDataBindingCommand
ApplySuggestedImprovementCommand
ReviewResidualIslandCommand
MapComponentToDesignSystemCommand
```

Refactor autosave into system sync history.

### Acceptance criteria

* Undo/redo works at semantic operation level.
* Audit says exactly what changed.
* Autosave does not pollute user history.
* All commands are tested.

---

## Milestone 5 — Real compiler/decompiler flow

### Tasks

1. Move source import to backend/source service.
2. Convert source → semantic model → BuilderDocument.
3. Create PageArtifactDocuments from imports.
4. Create canvas page nodes for each imported page.
5. Preserve residual islands and source locations.
6. Add codegen preview/diff/merge.
7. Block unsafe low-fidelity generation.

### Acceptance criteria

* Import TSX/route/story produces reviewable page artifacts.
* Multi-page imports become multiple canvas page nodes.
* Residuals require review.
* Codegen cannot silently overwrite unsupported logic.

---

## Milestone 6 — Preview and plugin runtime hardening

### Tasks

1. Move preview signing server-side.
2. Validate preview token in `/preview/builder`.
3. Add CSP and sandbox headers.
4. Replace substring domain checks with exact URL parsing.
5. Ensure unsafe component handler is invoked in import/render/plugin paths.
6. Add runtime sandbox for custom components.

### Acceptance criteria

* No preview secret in frontend.
* Forged/expired session fails.
* Unknown custom component never runs arbitrary code.
* Plugin network/storage/browser APIs are enforced at runtime.

---

## Milestone 7 — Test quality upgrade

### Tasks

1. Rewrite `pageBuilderE2E.test.tsx` as real integration tests.
2. Add Playwright browser tests for:

   * onboarding → project → phase cockpit
   * shape → page builder → preview
   * validate → approval gate
   * generate → codegen preview
   * run → readiness plan
   * observe → preview/metrics
   * learn/evolve flow
3. Add backend integration tests for page artifact DB repository/controller.
4. Add security tests for preview sessions and plugin policies.
5. Add accessibility tests for keyboard/focus/ARIA.

### Acceptance criteria

* No placeholder tests.
* No route component stubbing for critical flow tests.
* Tests fail when real UI breaks.
* Security tests cover malicious origin, forged token, unsafe component, cross-tenant access.

---

# 7. Test plan

## Unit tests

* Phase config builders.
* Provenance badge variants.
* Next-best-action ranking.
* PageBuilderCommands.
* PageArtifactValidator.
* PreviewSession.
* UnsafeComponentHandler.
* PluginRuntimePolicy.

## Integration tests

* Page artifact controller + DB repository.
* Phase cockpit with real project/activity/preview APIs.
* Page builder with real renderer manifests.
* Import source workflow with real compiler fixture inputs.

## E2E tests

* Full lifecycle route flow.
* Canvas/page-builder/preview flow.
* Save/reload/conflict flow.
* Import/decompile/review/codegen flow.

## Security tests

* Cross-tenant artifact access.
* Preview session forgery/expiry.
* Plugin network allowlist bypass.
* Unsafe component execution.
* CSP/sandbox behavior.

## Accessibility tests

* Keyboard-only command palette.
* Keyboard-only phase cockpit.
* Keyboard-only page builder.
* Focus trap in dialogs.
* Status/provenance labels.

---

# 8. Readiness score

| Area                            | Score | Notes                                                                             |
| ------------------------------- | ----: | --------------------------------------------------------------------------------- |
| Full UI/UX flow                 |    68 | Phase cockpit improves flow, but old surfaces remain embedded.                    |
| Cognitive-load reduction        |    70 | Much better, but copy and supporting surfaces still need refinement.              |
| Phase cockpit maturity          |    72 | Real layer exists; needs decomposition and phase-native panels.                   |
| Canvas maturity                 |    68 | Powerful, but phase-aware behavior is not fully proven.                           |
| Page builder maturity           |    72 | Semantic commands and builder foundation exist; still incomplete commands/tests.  |
| Design-system consistency       |    45 | Enforcement rules exist but are not wired; raw UI persists.                       |
| Compiler/decompiler integration |    58 | Import workflow exists but not full artifact graph/page artifact/codegen loop.    |
| Live preview                    |    72 | Stronger security/session direction; server-side signing still needed.            |
| Persistence                     |    78 | DB repository and controller exist; resource auth/audit atomicity need hardening. |
| Security/privacy                |    62 | Many utilities exist; runtime enforcement integration not fully proven.           |
| Auditability                    |    60 | Audit exists but non-blocking/async.                                              |
| Observability                   |    65 | Metrics/tracing added in controller; broader UI/canvas OTel incomplete.           |
| AI/automation implicitness      |    63 | Better next-action direction; AI naming still leaks in fields/legacy components.  |
| Test confidence                 |    52 | More tests, but still mocks critical pieces.                                      |
| Production readiness            |    61 | Solid progress but not yet production-grade.                                      |

---

# 9. Prioritized backlog

| Priority | ID         | Work item                                                         | Area                | Dependency                        | Acceptance criteria                                               |
| -------- | ---------- | ----------------------------------------------------------------- | ------------------- | --------------------------------- | ----------------------------------------------------------------- |
| P0       | DS-001     | Wire design-system ESLint rules into CI                           | UI/UX               | existing rule file                | Raw controls flagged in changed product UI                        |
| P0       | AUTH-001   | Add resource-scoped page artifact authz                           | Backend security    | PageArtifactController            | Cross-workspace/project artifact access denied                    |
| P0       | AUDIT-001  | Make audit durable/atomic                                         | Audit               | Db repositories                   | Mutations cannot succeed without durable audit/outbox             |
| P0       | TEST-001   | Replace mocked page-builder E2E with real interactions            | Testing             | PageBuilderCommands, PageDesigner | Drag/drop/edit/preview/save tested without core mocks             |
| P0       | SEC-001    | Move preview session signing server-side                          | Preview security    | PreviewSession                    | No secret in frontend bundle                                      |
| P1       | PHASE-001  | Decompose `_phaseCockpit.tsx` into phase services                 | UI architecture     | Phase cockpit                     | Config/evidence/blockers/suggestions testable separately          |
| P1       | PHASE-002  | Replace generic embedded surfaces with native phase panels        | UX                  | Phase services                    | Each phase useful without opening old route                       |
| P1       | VALID-001  | Add contract-aware server validation                              | Backend validation  | design-system registry            | Invalid props/slots/contracts rejected                            |
| P1       | CANVAS-001 | Complete semantic builder command set                             | Canvas/page builder | PageBuilderCommands               | Responsive/actions/bindings/residual review are command-backed    |
| P1       | COMP-001   | Convert import workflow into full artifact graph flow             | Compiler            | yappc-artifact-compiler           | Source import creates BuilderDocument/page artifacts/canvas nodes |
| P1       | SEC-002    | Replace plugin URL substring checks                               | Plugin security     | PluginRuntimePolicy               | Exact origin/domain matching                                      |
| P1       | SEC-003    | Wire unsafe component handler into import/render/plugin paths     | Plugin security     | UnsafeComponentHandler            | Unsafe components never execute by default                        |
| P2       | NBA-001    | Connect next-best-action service to real project signals          | Automation          | APIs/phase state                  | Dashboard/command palette show real ranked actions                |
| P2       | DS-002     | Migrate new cockpit/action components to design-system primitives | Design system       | DS lint                           | No raw controls/hardcoded status colors                           |
| P2       | A11Y-001   | Add keyboard/focus tests for all cockpits and command palette     | Accessibility       | UI routes                         | Keyboard-only use works                                           |

---

# Bottom line

At `b104716d`, YAPPC is no longer just adding plans; it has real implementation movement in the right places: phase cockpits, backend page-artifact persistence, server validation, semantic builder commands, source import workflow, provenance, preview sessions, unsafe component analysis, plugin policy, and design-system enforcement scaffolding.

The critical next step is **integration hardening**:

```text
activate enforcement
+ make phase cockpits native
+ make persistence resource-secure and audit-atomic
+ make tests real
+ move preview signing server-side
+ make compiler/decompiler produce actual page artifacts
+ enforce plugin/runtime policy in execution paths
```

The product is moving toward the right architecture, but the review should stay intentionally strict: several improvements are currently **valid architecture foundations**, not yet full production-grade user flows.