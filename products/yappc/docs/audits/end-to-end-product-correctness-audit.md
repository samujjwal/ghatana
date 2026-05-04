## 1. Verified baseline

Reviewed commit: `997207aff233cfdfb93fa8edce8c1c12b7497333`.

This is a **substantive commit**, not only a changelog pointer. The commit message is `yappc + build 2344`, and it includes YAPPC changes plus broader platform/Data Cloud/AEP/DMOS changes. 

I reviewed the YAPPC source statically through GitHub. I did **not** clone the repo locally or execute Gradle, pnpm, Vitest, Playwright, DB migrations, or runtime services, so runtime/build conclusions need execution proof.

I also checked current external guidance for two areas: WCAG 2.2 keyboard accessibility expects functionality to be operable through a keyboard, and MDN’s `postMessage` security guidance says to validate sender identity and specify an exact `targetOrigin`, not `*`, where possible. ([W3C GitHub][1])

---

# 2. Executive summary

YAPPC improved significantly at `997207a`.

The latest head now includes real work in the exact areas we previously identified:

* Phase-specific cockpit routing for all eight phases.
* Provenance badges for backed/derived/suggested/preview/unavailable content.
* Next-action dashboard and action-discovery palette.
* DB-backed page artifact repository, migration, validation, permissions, controller metrics, and route wiring.
* Semantic page-builder command service.
* Phase-aware canvas configuration.
* Bidirectional preview-sync service.
* Source import workflow, codegen preview/diff/merge service, and residual island review UI.
* Preview session security, CSP helpers, unsafe component handler, plugin runtime policy, and compatibility validator.
* Design-system enforcement lint rules and Playwright E2E specs.

However, the implementation is still **not fully production-grade**. The main pattern is: many excellent modules now exist, but several are still **service-level scaffolds** rather than fully wired, enforced, observable, and tested in the mounted product path.

## Overall maturity

| Area                            | Score |
| ------------------------------- | ----: |
| Full UI/UX flow                 |    72 |
| Cognitive-load reduction        |    70 |
| Phase cockpit maturity          |    78 |
| Canvas maturity                 |    68 |
| Page builder maturity           |    72 |
| Design-system consistency       |    55 |
| Compiler/decompiler integration |    62 |
| Live preview                    |    70 |
| Persistence                     |    78 |
| Security/privacy                |    67 |
| Auditability                    |    55 |
| Observability                   |    62 |
| Implicit automation             |    58 |
| Test confidence                 |    52 |
| Production readiness            |    64 |

---

# 3. Top critical findings

## P0-1 — Phase cockpits are real now, but still embed older generic surfaces

The new `_phaseCockpit.tsx` defines all eight phase configs and mounts a cockpit with primary action, blockers, evidence, suggestions, governance, and supporting surfaces.  Individual phase routes now point into the cockpit route, for example Intent exports `IntentCockpitRoute`. 

**Remaining issue:** the cockpit still embeds older surfaces: Intent embeds project overview, Shape/Generate embed canvas, Run embeds deploy, Observe embeds preview + lifecycle, and Validate/Learn/Evolve embed lifecycle.  This is better than aliases, but it can still feel like “cockpit wrapper + generic content.”

**Fix:** keep the supporting surfaces, but make each phase’s embedded content phase-filtered and action-scoped.

---

## P0-2 — Design-system enforcement exists, but the UI still violates it widely

There is now an ESLint rule package for raw buttons, raw inputs, raw selects, hardcoded colors, and ad-hoc card classes.  But the rules are configured as `warn`, not fail-the-build. 

Many new components still use raw controls and hardcoded Tailwind colors, including `PhasePrimaryActionCard`, `NextActionDashboard`, and `ActionDiscoveryPalette`.    `DashboardView` imports design-system components but still uses `// @ts-nocheck`, custom classes, hardcoded gradients, and mixed layout patterns. 

**Fix:** move lint rules from `warn` to `error` after migration, and prioritize replacing raw controls in newly added cockpit/action components.

---

## P0-3 — Page artifact persistence is much stronger, but audit is still log-only

The new `DbPageArtifactRepository` provides durable persistence with tenant/workspace/project scoping, optimistic concurrency, document versioning, and historical version archiving.  The migration creates `page_artifacts` and `page_artifact_versions` with constraints and indexes.  The HTTP controller now performs auth checks, validation, metrics, and conflict handling.  The server wires the page artifact routes through `authFilter.secure`. 

**Remaining issue:** `emitAuditEvent` only logs audit messages; it does not persist them as first-class audit records. 

**Fix:** replace log-only audit with a real audit repository/event bus entry.

---

## P0-4 — Authorization is present, but identity source needs hardening

The controller checks permissions using `SyncAuthorizationService`, `X-User-ID`, and `X-User-Role` headers.  Permission constants are defined for read, edit, force save, import/decompile, approve changes, delete, and manage. 

**Risk:** unless `YappcApiAuthFilter` reliably injects and validates those headers server-side, user/role headers can become spoofable. The reviewed controller itself trusts request headers.

**Fix:** derive authenticated principal and permissions from the auth context, not raw client-controllable headers.

---

## P0-5 — Server-side validation exists, but contract/security enforcement is shallow

`PageArtifactValidator` validates root nodes, node maps, slots, sync status, trust level, classification, residual island count, fidelity, and governance record presence. 

**Gaps:**

* It checks `contractName` exists but does not verify contract validity against the design-system registry.
* It treats executable payloads such as `eval(`, `<script`, and `javascript:` as **warnings**, not errors. 
* It does not validate prop schemas, slot constraints, action bindings, or data bindings deeply.

**Fix:** fail closed for executable payloads and verify contract/prop/slot/action/data-binding compliance against the canonical registry.

---

## P0-6 — Preview sessions exist but are not wired into preview runtime

`PreviewSession.ts` implements signed HMAC preview sessions with scope, expiration, signature validation, resource scope checks, and extension.  But `preview-builder.tsx` still uses referrer-origin validation only and does not validate a session query token. 

**Fix:** require `/preview/builder?session=...`, validate the signed preview session before accepting messages, and reject missing/expired/out-of-scope sessions.

---

## P0-7 — Bidirectional preview sync exists but is not integrated into preview-builder

`BidirectionalPreviewSync` models click/hover/focus/blur sync, state, history, and telemetry.  But `preview-builder.tsx` renders `ComponentRenderer` with `selectedNodeId={null}` and no selection callbacks. 

**Fix:** wire preview click/hover messages into `HostToPreviewMessage`/`PreviewToHostMessage`, update `ComponentRenderer` to emit node IDs, and update builder selection from preview events.

---

## P0-8 — Compiler/decompiler workflow improved, but codegen merge logic is not production-grade

`ImportSourceWorkflow` now supports TSX, route, Storybook, artifact, and ZIP imports and calls the artifact compiler extractors.  `ResidualIslandReviewPanel` gives users a review UI for residual islands.  `CodegenPreview` models ownership regions, diffs, and merge conflicts. 

**Gaps:**

* `CodegenPreview` uses a simple line-by-line diff.
* `mergeCode` creates an empty local `conflicts` array, mutates that empty array, then returns `preview.conflicts`; merge options do not actually update returned conflict resolutions. 
* Import workflow returns files/metadata, not fully integrated `PageArtifactDocument[]` canvas nodes.

**Fix:** use a real diff/merge engine, correct conflict resolution logic, and convert import results into BuilderDocument/page-artifact/canvas-node flows.

---

## P0-9 — Plugin policy exists but is not fail-closed enough

`PluginRuntimePolicy` defines network, storage, browser API, telemetry, sandbox, and resource policies. 

**Critical issue:** `enforceBrowserAPIPolicy` allows unknown APIs by default.  For plugin security, unknown APIs should be blocked or require explicit allowlisting.

**Fix:** make all unknown APIs deny-by-default. Add runtime enforcement, not just policy evaluation helpers.

---

## P0-10 — Tests are better but still have weak/assumption-heavy areas

The page-builder test is no longer the placeholder-only test from the prior review, and it now verifies mount validation, import parse errors, residuals, fidelity, and governance panel visibility. 

But it mocks `ComponentRenderer`, `rendererManifest`, and `artifactCompilerBridge`, so it does not prove real drag/drop, rendering, manifest loading, or compiler behavior. 

The new Playwright full-flow spec validates the 8-phase cockpit sweep.  But the security/accessibility E2E spec references paths like `/shape`, `/validate`, `/artifacts/other-tenant/...` and test IDs like `open-preview`, `preview-iframe`, `session-invalid`, and `plugin-rejected`; these need verification against actual mounted routes and DOM. 

**Fix:** reduce mocks in builder tests and align Playwright specs with actual mounted routes and real test IDs.

---

# 4. Full flow analysis

## Login

**Current state:** Login remains a conventional form with email/password, session-expired state, and demo login behavior from earlier review.

**Main gap:** design-system consistency should be enforced here; login must also support accessible auth flows and clear error distinction.

**Priority:** P1.

---

## Onboarding

**Improved direction:** the previous AI-visible language should be reduced. At this commit, auto/next-action services exist, but onboarding still needs a full wording pass if old “AI suggests/thinking” language remains in mounted components.

**Needed behavior:** onboarding should say “Suggested workspace name,” not “AI suggests.”

**Priority:** P1.

---

## Dashboard

The new `NextActionDashboard` aligns strongly with the goal: primary action, secondary action, tertiary action, and quiet health indicators. 

But `DashboardView` is still not clean: it has `// @ts-nocheck`, mixed design-system and custom styling, hardcoded gradients, and a “Collaborative IDE” card that may increase cognitive load if not tied to the project’s next action. 

**Recommendation:** use `NextActionDashboard` as the primary dashboard and demote IDE/projects/workflows into secondary progressive disclosure.

---

## Project phase flow

This is the largest improvement.

Each phase now has a cockpit pattern:

* Phase purpose.
* Primary action.
* Blockers.
* Evidence.
* Suggested automation.
* Governance trace.
* Supporting route surface.

`PhaseCockpitLayout` explicitly encodes that structure.  `PhasePrimaryActionCard` states the goal that users should understand the next action in under five seconds. 

**Remaining gap:** many cockpit components use raw controls and hardcoded colors, so the UX architecture improved faster than design-system execution. 

---

## Canvas

`PhaseCanvasConfig` now defines phase-aware modes, visible tools, editing permissions, validation, and preview behavior. 

**Concern:** the config uses `LifecyclePhase.IMPROVE` and `LifecyclePhase.INSTITUTIONALIZE`, while the product UI route model uses Learn and Evolve.  This may be valid if the enum maps those concepts, but it should be verified because route labels and lifecycle enum concepts must not drift.

**Needed:** actually apply `PhaseCanvasConfig` to canvas toolbar/mode/tool visibility and test that Shape/Validate/Generate/Run/Observe expose different toolsets.

---

## Page/UI builder

The builder now has a semantic command service: insert, move, update props, delete, import, autosave, undo, redo, audit callback, telemetry callback, validation callback, and autosave scheduling. 

**Gaps:**

* Missing command types for responsive variants, state variants, action bindings, data bindings, and apply-suggestion workflows.
* Commands are service-level; verify PageDesigner actually routes user edits through them.
* Autosave history is added but not necessarily correlated to server artifact save/versioning.

---

## Preview

`preview-builder.tsx` has secure origin/source validation and explicit target origin messaging.  That aligns with MDN guidance to validate sender identity and avoid wildcard target origins. ([MDN Web Docs][2])

**Remaining gaps:**

* Signed preview session not integrated.
* Bidirectional preview sync not integrated.
* CSP helpers exist but need application proof.
* Preview still does not emit selection/hover back into builder.

---

## Compiler/decompiler

Import, residual review, and codegen preview services now exist.   

**Main gap:** the services are not yet a complete mounted workflow from source import → BuilderDocument → canvas node → residual review → codegen diff → safe merge/apply.

---

# 5. Area-by-area review

## UI/UX

**Strengths**

* Phase cockpit architecture now directly targets cognitive load.
* Provenance badge model is strong.
* Next-action dashboard concept is correct.
* Command palette moved toward action discovery.

**Problems**

* Raw controls and hardcoded colors persist.
* Some primary actions still use technical labels like “Generate Code.”
* Some supporting surfaces are generic.
* Dashboard still mixes next-action and development hub patterns.
* `// @ts-nocheck` in dashboard is unacceptable for production-grade UI. 

---

## Design system

**Strengths**

* Design-system lint rules exist. 
* Design-system mapping docs were added according to the commit delta, and `DashboardView` uses design-system imports. 

**Problems**

* Enforcement is warning-only. 
* New cockpit/action components violate the spirit of the rule.
* Hardcoded status colors and Tailwind classes remain widespread.

---

## Security/privacy/o11y/audit

**Strengths**

* Page artifact API now has auth checks, metrics, tracing, validation. 
* Preview session signing exists. 
* CSP/sandbox helpers exist. 
* Unsafe component handler uses TypeScript AST analysis. 

**Problems**

* Audit is log-only in `PageArtifactController`. 
* Preview session not integrated with preview route.
* CSP helper exists, but application proof is not shown.
* `DEFAULT_CSP` allows `'unsafe-inline'` and `'unsafe-eval'` for development; production must never use that. 
* Plugin unknown APIs are allowed by default. 

---

# 6. P0 / P1 / P2 findings

| ID           | Severity | Area          | Finding                                                               | Fix                                                            |
| ------------ | -------- | ------------- | --------------------------------------------------------------------- | -------------------------------------------------------------- |
| YAPPC-P0-001 | P0       | Audit         | Page artifact audit is log-only.                                      | Persist audit events to audit trail/event bus.                 |
| YAPPC-P0-002 | P0       | Auth          | Controller trusts user/role headers.                                  | Derive principal from auth context.                            |
| YAPPC-P0-003 | P0       | Security      | Executable payloads are warnings.                                     | Treat script/eval/javascript payloads as save-blocking errors. |
| YAPPC-P0-004 | P0       | Preview       | Signed preview sessions exist but are not enforced.                   | Require and validate preview session in `/preview/builder`.    |
| YAPPC-P0-005 | P0       | Plugins       | Unknown browser APIs allowed.                                         | Deny unknown APIs by default.                                  |
| YAPPC-P0-006 | P0       | Design system | Lint rules are warn-only and violations remain.                       | Migrate components, then change to errors.                     |
| YAPPC-P1-001 | P1       | Phase UX      | Cockpits still embed generic routes.                                  | Phase-filter embedded surfaces.                                |
| YAPPC-P1-002 | P1       | Canvas        | Phase config may not align with Learn/Evolve route model.             | Align enum and route lifecycle terminology.                    |
| YAPPC-P1-003 | P1       | Builder       | Missing semantic commands for bindings/responsive/state/suggestions.  | Extend `PageBuilderCommands`.                                  |
| YAPPC-P1-004 | P1       | Preview       | Bidirectional sync service is not wired.                              | Add preview click/hover message flow.                          |
| YAPPC-P1-005 | P1       | Compiler      | Import/codegen services not fully mounted.                            | Integrate source import → canvas node → diff/merge.            |
| YAPPC-P1-006 | P1       | Tests         | E2E specs may reference non-mounted routes/test IDs.                  | Align Playwright specs to actual app routes.                   |
| YAPPC-P2-001 | P2       | Next action   | Next-best-action logic is hardcoded and duplicated with phase config. | Centralize ranking from real project state.                    |

---

# 7. Implementation plan

## Milestone 1 — Make persistence and audit production-grade

Tasks:

1. Replace `emitAuditEvent` logging with persistent audit events.
2. Derive authenticated principal from `authFilter` context, not raw `X-User-ID` / `X-User-Role`.
3. Make executable payload validation fail-closed.
4. Validate component contracts against the design-system registry.
5. Add integration tests for cross-tenant access, stale ETag, validation failure, persisted audit, and version history.

Acceptance criteria:

* Every save/load/conflict/validation failure creates an audit record.
* Spoofed user headers cannot grant access.
* Unsafe documents cannot be persisted.
* Version history supports rollback.

---

## Milestone 2 — Complete phase cockpit UX

Tasks:

1. Convert each cockpit’s supporting surface into phase-specific content.
2. Remove or hide generic lifecycle sections unless relevant.
3. Ensure one primary action per phase.
4. Use provenance badges for all evidence and suggestions.
5. Replace “Generate Code” with “Prepare Implementation” where user-facing.

Acceptance criteria:

* Each phase has a unique purpose and primary action.
* Users can tell backed vs derived vs suggested vs preview content.
* Advanced panels are collapsed by default.

---

## Milestone 3 — Enforce design-system consistency

Tasks:

1. Replace raw buttons/inputs in `PhasePrimaryActionCard`, `NextActionDashboard`, and `ActionDiscoveryPalette`.
2. Remove `// @ts-nocheck` from `DashboardView`.
3. Replace hardcoded gradients/colors with tokens.
4. Move `.eslintrc.design-system.json` rules from `warn` to `error` after migration.
5. Add CI gate for design-system enforcement.

Acceptance criteria:

* No raw controls in product routes except approved low-level primitives.
* No hardcoded colors outside theme/token files.
* CI fails on violations.

---

## Milestone 4 — Wire canvas/page-builder command system

Tasks:

1. Route PageDesigner insert/move/update/delete through `PageBuilderCommands`.
2. Add commands for responsive variants, state variants, action bindings, data bindings, and apply-suggestion.
3. Correlate command audit with server page-artifact audit.
4. Add command-level undo/redo tests using real BuilderDocument operations.

Acceptance criteria:

* Every builder action is semantic, undoable, auditable, observable, and autosaved.
* No whole-document replacement for simple prop changes unless import/restore requires it.

---

## Milestone 5 — Complete live preview security and sync

Tasks:

1. Require signed preview session for `/preview/builder`.
2. Wire `BidirectionalPreviewSync` into preview route and LivePreviewPanel.
3. Add `ELEMENT_CLICK` and `ELEMENT_HOVER` message handling.
4. Apply CSP/sandbox helpers in actual iframe/server response.
5. Add tests for missing/expired/out-of-scope sessions.

Acceptance criteria:

* Preview cannot run without a valid scoped session.
* Preview click selects builder node.
* Builder selection highlights preview node.
* CSP and sandbox are verified in E2E.

---

## Milestone 6 — Make compiler/decompiler a full mounted workflow

Tasks:

1. Add mounted source import UI using `ImportSourceWorkflow`.
2. Convert imported files/extracted data into `PageArtifactDocument[]`.
3. Show `ResidualIslandReviewPanel` after import.
4. Fix `CodegenPreview.mergeCode`.
5. Replace simple line diff with a real diff algorithm.
6. Add safe apply/export/PR workflow.

Acceptance criteria:

* TSX/route/storybook import produces canvas page nodes.
* Residuals block unsafe generation.
* Codegen diff/merge preserves user-owned regions.
* Merge conflicts are accurately reported and resolvable.

---

## Milestone 7 — Harden plugin/runtime policy

Tasks:

1. Change unknown browser API behavior to deny-by-default.
2. Enforce plugin network/storage/browser/telemetry policy at runtime.
3. Connect `UnsafeComponentHandler` to plugin/component loading.
4. Require elevated plugin approval.
5. Add sandbox boundary tests.

Acceptance criteria:

* Unsafe custom components render fallback by default.
* Plugin cannot access network/storage/browser APIs without policy approval.
* Policy violations are audited.

---

## Milestone 8 — Strengthen tests

Tasks:

1. Reduce mocks in `pageBuilderE2E.test.tsx`.
2. Add real drag/drop tests.
3. Align Playwright route paths to `/p/:projectId/:phase`.
4. Add real test data setup for project/activity/phase preview.
5. Add DB-backed page artifact integration tests.
6. Add accessibility tests aligned with WCAG keyboard operability expectations. ([W3C GitHub][1])

Acceptance criteria:

* E2E tests fail when mounted UI breaks.
* Security/accessibility tests use actual mounted DOM.
* No placeholder or assumption-only tests.

---

# 8. Test plan

## Unit tests

* `PageArtifactValidator`
* `PageBuilderCommands`
* `NextBestActionService`
* `AutoApplyImprovements`
* `PreviewSession`
* `UnsafeComponentHandler`
* `PluginRuntimePolicy`
* `ComponentCompatibilityValidator`
* `CodegenPreview`

## Integration tests

* Page artifact DB save/load/conflict/version history.
* Page artifact authz/tenant isolation.
* Builder command → autosave → API persistence.
* Source import → residuals → page artifact.
* Plugin package → policy → renderer manifest.

## Browser E2E

* Login/onboarding/workspace/project.
* 8-phase cockpit sweep.
* Shape canvas page builder.
* Builder live preview update.
* Validate approval gate.
* Generate codegen preview.
* Run capability gate.
* Observe project preview.
* Learn retrospective.
* Evolve roadmap/backlog.

## Security tests

* Spoofed preview origin.
* Missing/expired preview session.
* Unsafe plugin.
* Cross-tenant artifact read/write.
* CSP header present.
* Iframe sandbox denies top navigation.

## Accessibility tests

* Keyboard-only onboarding.
* Keyboard-only command palette.
* Keyboard-only phase navigation.
* Keyboard-only page builder selection/inspector.
* Focus traps.
* Accessible labels.
* Heading hierarchy.
* Skip link.
* Drag alternatives, because WCAG 2.2 expects keyboard operability and includes newer focus/dragging-related expectations. ([W3C GitHub][1])

---

# 9. Final prioritized backlog

| Priority | Task                                                  | Area                | Dependency                   | Acceptance criteria                           |
| -------- | ----------------------------------------------------- | ------------------- | ---------------------------- | --------------------------------------------- |
| P0       | Persist page artifact audit events                    | Backend/audit       | Audit repository/event bus   | Audit no longer log-only                      |
| P0       | Use authenticated principal for page artifact authz   | Security            | Auth filter context          | Client headers cannot spoof identity          |
| P0       | Fail closed on executable payloads                    | Validation/security | Validator update             | Unsafe document save returns 422              |
| P0       | Enforce signed preview sessions                       | Preview/security    | `PreviewSession` integration | Missing/invalid session blocks preview        |
| P0       | Deny unknown plugin APIs                              | Plugin security     | `PluginRuntimePolicy`        | Unknown APIs blocked by default               |
| P0       | Replace raw controls in new cockpit/action components | UI/design system    | DS components                | Lint passes as error                          |
| P1       | Wire bidirectional preview sync                       | Preview/builder     | Protocol event support       | Preview click selects builder node            |
| P1       | Extend semantic builder commands                      | Builder/canvas      | `PageBuilderCommands`        | Bindings/responsive/state supported           |
| P1       | Fix codegen merge logic                               | Compiler            | `CodegenPreview`             | Conflicts resolved accurately                 |
| P1       | Mount source import workflow                          | Compiler/UI         | Import service + canvas      | TSX/route/story import creates page artifacts |
| P1       | Align phase canvas config with route lifecycle        | Canvas/IA           | Lifecycle enum review        | Learn/Evolve behavior unambiguous             |
| P1       | Correct E2E route/test-id assumptions                 | Tests               | Mounted app setup            | E2E proves real UI                            |

---

# 10. Bottom line

At `997207aff233cfdfb93fa8edce8c1c12b7497333`, YAPPC is clearly moving from “planned architecture” toward a real, integrated product system. The biggest improvements are phase cockpits, durable page artifact persistence, semantic builder commands, source import/codegen services, provenance, plugin/security helpers, and E2E coverage.

The remaining work is not about adding more files. It is about **wiring, enforcement, and proof**:

```text id="6x6ela"
log-only audit → persisted audit
header-based auth context → authenticated principal
warning-level security → fail-closed validation
security helpers → enforced runtime policy
service-level compiler → mounted source-to-canvas workflow
design-system warnings → CI-blocking consistency
E2E assumptions → real mounted journey proof
```

YAPPC is now around **64% production-ready** overall. The fastest path to a strong product is to harden the new foundations rather than introduce new surface area.

[1]: https://w3c.github.io/wcag/guidelines/22/?utm_source=chatgpt.com "Web Content Accessibility Guidelines (WCAG) 2.2"
[2]: https://developer.mozilla.org/en-US/docs/Web/API/Window/postMessage.?utm_source=chatgpt.com "Window: postMessage() method - Web APIs | MDN"
