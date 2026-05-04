implement all the tasks from # 1. Verified baseline

I reviewed commit head `b90f644a9575c3c19d6ae29ee71bf907a2230146`.

Important clarification: `b90f644a...` itself is a merge/changelog-facing commit whose direct diff shown by the connector updates `products/yappc/CHANGELOG.md` and references `8e27816d76271c52ed4f35cd083db2e441f82340`.  However, comparing from the previous reviewed head to `b90f644a...` shows a large YAPPC delta, including DB page-artifact persistence, validation, phase cockpit components, design-system enforcement, compiler workflow shells, security utilities, semantic command services, and E2E specs.

Key files reviewed include:

* Page artifact backend: `DbPageArtifactRepository`, `PageArtifactValidator`, `PageArtifactController`, `PageArtifactRoutes`
* Phase UX: `PhaseCockpitLayout`, `IntentCockpit`, other phase cockpit components
* Project UX: `CommandCenter`, `ProvenanceBadge`
* Canvas/page builder: `PageBuilderCommands`, `PageDesigner`, `ComponentRenderer`, renderer/plugin infrastructure
* Compiler/decompiler: `ImportSourceWorkflow`, residual review components
* Security: `PreviewSession`, `UnsafeComponentHandler`
* Design system: ESLint enforcement rule and primitive mapping docs
* Tests: `pageBuilderE2E.test.tsx`, `yappc-full-flow.spec.ts`

---

# 2. Executive summary

YAPPC at `b90f644a...` has improved significantly since `719869a`. The repo now contains many of the architectural pieces requested in the previous implementation plan:

* DB-backed page artifact repository with optimistic concurrency and historical version archiving. 
* Server-side page artifact validation. 
* Phase cockpit component family for low-cognitive-load phase UX. 
* Provenance badge component for backed/derived/suggested/preview/unavailable data truth. 
* Project command center pattern with readiness, next action, backed activity, suggested improvement, and governance trace. 
* Next-best-action service. 
* Design-system ESLint enforcement rule. 
* Preview session and unsafe component security utilities.  
* Playwright full-flow and accessibility E2E spec. 

The biggest remaining concern: many of these additions are **service/component shells or partially wired implementations**, while the mounted app routes still largely point to the older generic project overview, canvas, or lifecycle surfaces. For example, `intent.tsx` still exports project overview, `shape.tsx` and `generate.tsx` still export canvas, and `validate.tsx` still exports lifecycle.    

## Top critical findings

| Severity | Finding                                                                                                                                                   |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P0       | Phase cockpit components exist but are not yet mounted into the actual phase routes.                                                                      |
| P0       | Compiler/import workflow is still placeholder-heavy and does not yet perform real TSX/route/story extraction.                                             |
| P0       | Page builder “E2E” test remains partly test-theater; it still contains comments instead of real interactions and locally mocks `vi`.                      |
| P0       | DB repository exists, but persistence still needs production concurrency, authz, transaction, testcontainer, and audit/O11y hardening.                    |
| P0       | Server-side validation is structural and string-based, not yet full design-system/BuilderDocument/schema/trust validation.                                |
| P1       | Next-best-action service exists but appears rule-based and likely not yet wired to real project state and route surfaces.                                 |
| P1       | Governance metadata enforcement exists as a service, but enforcement must be integrated into the inspector, save path, preview path, and promotion gates. |
| P1       | Security utilities exist, but signed preview sessions use a demo hash with a comment saying production should use HMAC.                                   |
| P1       | Design-system lint rule exists, but routes still need migration and CI enforcement proof.                                                                 |
| P1       | Full-flow Playwright spec exists but likely targets test IDs/routes that are not mounted yet.                                                             |

---

# 3. Full flow analysis

## Login → onboarding

The prior concerns still mostly stand. The onboarding flow was already guided, durable, and useful, but it contained explicit AI wording. The new delta only shows small wording changes in onboarding, not a full design-system migration or phase-aware onboarding model. The UX should continue moving toward “suggested starting point” rather than “AI is thinking.”

## Workspace → projects

The new `CommandCenter` and `NextActionDashboard` additions suggest the product is moving from list-heavy navigation toward next-action UX. `CommandCenter` has the right structure: current phase, readiness, next action, recent backed activity, suggested improvement, and governance trace. 

Gap: I did not find strong evidence that this command center has replaced the existing route-level overview in the mounted flow. The component is valuable, but it must be wired into `app/project/index.tsx` or the relevant shell to change actual UX.

## Project overview / Intent

`IntentCockpit` exists and correctly frames Intent around defining the goal, blockers, evidence, suggested steps, and governance records. 

But `intent.tsx` still exports `./index`, meaning the Intent phase still renders the project overview route rather than the new `IntentCockpit`. 

Verdict: **component implemented, route integration incomplete.**

## Shape

`ShapeCockpit` exists in the repo, but `shape.tsx` still exports `./canvas`. 

Verdict: Shape still drops users into generic canvas rather than a shape-specific cockpit that explains requirements, design artifacts, page builder entry point, blockers, and evidence.

## Validate

`ValidateCockpit` exists, but `validate.tsx` still exports `./lifecycle`. 

Verdict: Validate is still lifecycle-generic rather than approval/gate-first.

## Generate

`GenerateCockpit` exists, but `generate.tsx` still exports `./canvas`. 

Verdict: Generate still lacks mounted codegen/implementation cockpit.

## Run / Observe / Learn / Evolve

New cockpit components exist for these phases, but route integration was not evident in the files inspected. The Playwright spec expects direct `/run`, `/observe`, `/learn`, `/evolve` cockpit test IDs.  The existing route structure historically used project-scoped routes such as `/p/:projectId/run`, so these tests may not reflect the actual route model.

Verdict: **good target UX components exist; mounted route/user-flow correctness still needs proof.**

---

# 4. Area-by-area review

## A. Page artifact persistence

### Current state

The new `DbPageArtifactRepository` is a major improvement. It uses JDBC, tenant/workspace/project/artifact scoping, optimistic concurrency with `document_id`, historical version archiving, JSON serialization, load/save/delete, and conflict exceptions. 

### Strengths

* Durable repository exists.
* Version table archive flow exists.
* Conflict detection exists.
* Tenant/workspace/project scoping is part of every query.
* Builder document, validation summary, governance records, residual count, and fidelity are persisted.

### Problems

1. `BLOCKING_EXECUTOR = Executors.newCachedThreadPool()` inside repository is risky. It creates an unbounded executor per class lifecycle and does not show managed shutdown or platform-level executor reuse. 

2. Save logic checks existing version, archives, and updates across multiple statements. Without explicit transaction boundaries shown in the file, archive/update consistency can fail under partial failure.

3. The update writes `document_id = document.documentId()` while the WHERE clause also checks `existingDoc.documentId()`. If the frontend does not create a new document/version ID before save, the persisted version ID may not advance, weakening concurrency semantics.

4. JSON mapping for governance records uses `objectMapper.readValue(json, java.util.List.class)`, which returns raw maps, not typed `GovernanceRecord` instances. 

### Required fixes

* Inject a managed blocking executor.
* Wrap insert/update/archive in transactions.
* Define version semantics: client-provided version vs server-generated version.
* Use typed Jackson `TypeReference<List<GovernanceRecord>>`.
* Add integration tests with real Postgres/Testcontainers and concurrent writers.

---

## B. Server-side validation

### Current state

`PageArtifactValidator` validates builder document root fields, nodes map, max node count, sync status, trust level, data classification, residual count, round-trip fidelity, governance record presence, and potential executable payloads by scanning string content. 

### Strengths

* Server-side validation now exists.
* It explicitly recognizes trust, classification, residuals, fidelity, governance, and executable content as validation concerns.
* It sets thresholds such as max residual islands and minimum round-trip fidelity.

### Problems

1. Validation is too shallow for production. It only checks `rootNodes`, `nodes`, and whether `nodes` is a map. It does not validate component instances, slots, contract names, prop schemas, data bindings, actions, responsive variants, or state variants. 

2. Executable payload checking is string-based (`eval(`, `function(`, `script`) and will miss many unsafe cases while producing false positives. 

3. Trust/data classification enums may not align with frontend/platform trust values unless there is a shared canonical enum.

4. “No governance records” is only a warning. For generated/decompiled/imported documents, missing governance should likely be a blocking error.

### Required fixes

* Reuse shared BuilderDocument schema validation.
* Validate design-system contract compatibility.
* Validate slots and required props.
* Validate action/data bindings.
* Validate trust policy against preview/runtime security rules.
* Replace string scanning with AST/schema-based unsafe payload detection.
* Make governance records mandatory for non-manual/generated/imported sources.

---

## C. Phase-specific UX and cognitive load

### Current state

The new `PhaseCockpitLayout` encodes the right structure: phase purpose, primary next action, blockers, evidence, suggested automation, governance/provenance, and advanced tools collapsed by default. 

`IntentCockpit` is a good example of the intended phase-specific pattern. 

### Strengths

* The UX model is now represented in code.
* Advanced/governance sections are collapsed by default.
* Each phase cockpit can express one primary action.

### Problems

1. Route integration appears incomplete. Existing phase route files still export old generic pages.    

2. Cockpit layout uses raw `div`, `header`, `section`, `details`, and Tailwind classes directly rather than design-system layout/disclosure components. 

3. `PhaseCockpitLayout` has internal `showGovernance` and `showAdvanced` state variables that are declared but not used; native `<details>` handles state instead. 

### Required fixes

* Wire all phase routes to cockpit wrappers.
* Use design-system components for cards, disclosure, alerts, badges, and buttons.
* Add `data-testid`, ARIA roles, and keyboard/focus behavior.
* Remove unused state.
* Drive cockpit content from real project/phase API data.

---

## D. Project command center and provenance

### Current state

`CommandCenter` is directionally strong: current phase, readiness, primary next action, recent backed activity, suggested improvement, governance trace link. 

`ProvenanceBadge` supports backed, derived, suggested, preview, and unavailable states with accessible label/title. 

### Strengths

* Exactly matches the desired “always know next action and truth source” model.
* Provenance taxonomy is now explicitly reusable.
* Suggested improvement wording avoids AI-first language.

### Problems

1. `CommandCenter` duplicates provenance badge logic internally rather than using `ProvenanceBadge`.  

2. It uses raw `div`, `span`, `a`, Tailwind, and hardcoded colors rather than design-system components. 

3. `role="button"` on a `div` for next action is less ideal than using a real design-system `Button`/`CardAction`.

### Required fixes

* Replace internal provenance badge code with `ProvenanceBadge`.
* Use `Card`, `Button`, `Badge`, `Alert`, and layout primitives.
* Wire CommandCenter into actual project overview.
* Add backed/derived/suggested/preview source metadata from real API.

---

## E. Canvas/page-builder command system

### Current state

`PageBuilderCommands` exists and provides command history, undo/redo stacks, audit callback, telemetry callback, and autosave scheduling. 

### Strengths

* Moves toward semantic operations.
* Defines audit and telemetry hooks.
* Has autosave concept.

### Problems

1. Command types are still generic canvas/document actions (`add-node`, `remove-node`, `save-document`) rather than page-builder semantic actions such as `insert-component`, `move-component-to-slot`, `update-prop`, `set-responsive-variant`, `add-action-binding`. 

2. Undo implementation simply re-executes the same command with an `undo-` ID; it does not compute or apply inverse operations. 

3. Calling `execute()` from `undo()` pushes the undo command back into undo history, which can pollute command stacks. 

4. Autosave uses command history as data, not necessarily the actual current BuilderDocument/page artifact state. 

### Required fixes

* Replace generic command types with builder-specific semantic commands.
* Define inverse operation for each command.
* Keep undo/redo stack transitions separate from normal execute.
* Persist actual page artifact document state.
* Emit audit/telemetry with artifactId, nodeId, contractName, operation, before/after deltas.

---

## F. Compiler/decompiler

### Current state

`ImportSourceWorkflow` defines a nice API shape for TSX, route, Storybook, artifact, and zip imports. But most helpers are explicitly placeholder implementations returning empty strings, empty arrays, or dummy metadata. 

### Strengths

* The desired import workflow types are now codified.
* Source types and metadata model are reasonable.
* It gives a future integration point for the existing artifact compiler.

### Problems

This is still not a working compiler/decompiler integration:

* `fetchTSXContent` returns `''`.
* `fetchRouteContent` returns `''`.
* `fetchStorybookStory` returns `''`.
* `fetchStorybookComponent` returns `null`.
* `unzipArchive` returns `[]`.
* `extractComponentName` returns `'Component'`.
* `extractDependencies` returns `[]`. 

### Required fixes

* Wire to actual `yappc-artifact-compiler` extractors.
* Use TypeScript AST extraction.
* Convert semantic model into `PageArtifactDocument[]`.
* Preserve source locations.
* Surface residual islands and fidelity.
* Add codegen/diff/merge.
* Remove placeholder implementations or put behind feature flag.

---

## G. Security

### Current state

`PreviewSession` adds a session model, expiry, scoping, signature, and validation.  `UnsafeComponentHandler` assesses component code for risky browser APIs, eval, network, and storage patterns and can apply policy through string replacement. 

### Strengths

* Security concepts are now first-class in code.
* Preview sessions are scoped to project/artifact/user.
* Unsafe component review/fallback/block model exists.
* Default component policy is restrictive.

### Problems

1. `PreviewSession` signature is explicitly a simple hash with a comment saying production should use HMAC.  This is not production-grade.

2. `UnsafeComponentHandler` uses string includes/replacements; it can be bypassed and can break code. 

3. These services need proof of integration with preview route, plugin loader, compiler import, renderer fallback, and save/promotion gates.

### Required fixes

* Replace hash with server-side HMAC/JWT-like signed preview session.
* Store preview sessions server-side or validate against a server secret.
* Use AST/static analysis for unsafe code.
* Enforce runtime sandbox and CSP.
* Block unsafe custom component execution by default.
* Add tests proving integration, not only utility-level behavior.

---

## H. Governance / privacy / accessibility

### Current state

`GovernanceMetadataEnforcer` can validate metadata for a11y, privacy, telemetry, review, preview trust, and suggestions. 

### Strengths

* Good metadata model.
* Suggestions are outcome-oriented.
* Missing a11y/privacy/trust fields can produce errors/warnings.

### Problems

1. It operates on a custom `GovernanceMetadata` shape. Need to verify alignment with actual design-system component contract metadata and BuilderDocument node metadata.

2. It is a service; integration into `PropertyForm`, save validation, preview trust, command execution, and promotion gates is not proven.

3. It may generate generic suggestions for every component, producing noise if not context-aware.

### Required fixes

* Use one canonical governance metadata schema across ds-registry, BuilderDocument, inspector, backend validation, preview, and audit.
* Apply enforcement on insert/update/save/preview/promote.
* Only show top actionable governance suggestions; keep everything else in trace.

---

## I. Design-system enforcement

### Current state

A custom ESLint rule file exists to ban raw `button`, `input`, `select`, hardcoded colors, and ad-hoc card classes. 

### Strengths

* Enforcement direction is correct.
* Messages point developers to primitive mapping docs.
* Includes hardcoded color detection.

### Problems

1. Need proof this custom rule is actually wired into CI/lint script.

2. Rules are simplistic; they may miss JSX member expressions and design-system wrappers, and may over-report in legitimate cases.

3. Many newly added components still use raw HTML and hardcoded Tailwind color classes, including cockpit and command center files.  

### Required fixes

* Wire `.eslintrc.design-system.json` into package scripts and CI.
* Add allowed wrappers and migration exceptions.
* Gradually turn warning into error.
* Refactor phase/command-center components to design-system primitives.

---

## J. Testing

### Current state

There is now a Playwright full-flow spec.  Page-builder “E2E” test was improved but still contains placeholder comments and a local `vi` mock. 

### Strengths

* Full-flow test intent is now captured.
* The Playwright spec covers onboarding, workspace, project, phase cockpits, canvas, preview, validate, generate, run, observe, learn, evolve, and accessibility. 

### Problems

1. The Playwright spec likely references routes/test IDs that are not mounted yet (`/intent`, `/shape`, `intent-cockpit`, etc.), while actual route files are project-scoped and still alias older pages.   

2. `pageBuilderE2E.test.tsx` is not true E2E. It uses unit rendering, calls callbacks directly, and comments say real behavior would be tested in Playwright. 

3. It still defines local `const vi = { fn: ... }`, despite importing from Vitest not including `vi`. 

### Required fixes

* Convert placeholder tests to real interactions.
* Align Playwright routes with actual app route structure.
* Ensure cockpits are mounted before tests expect them.
* Remove fake local `vi`; import `vi` from Vitest.
* Add backend API + frontend integration tests for DB page artifact persistence and conflicts.

---

# 5. P0 / P1 / P2 findings

## P0 findings

### P0-001 — Phase cockpits exist but are not wired into routes

Evidence: `PhaseCockpitLayout` and `IntentCockpit` exist, but `intent.tsx`, `shape.tsx`, `generate.tsx`, and `validate.tsx` still export old route surfaces.      

Impact: The user does not actually get the intended no-cognitive-load phase UX.

Fix: Replace route aliases with phase cockpit wrappers that embed canvas/lifecycle/preview panels as needed.

Acceptance criteria: Each phase route renders its cockpit with one primary action, blockers, evidence, provenance, and advanced disclosure.

---

### P0-002 — Compiler/import workflow is mostly placeholder

Evidence: `ImportSourceWorkflow` helper implementations return empty content, empty arrays, nulls, or `'Component'`. 

Impact: Users cannot import real TSX/routes/stories or rely on decompiler/codegen workflow.

Fix: Wire workflow to actual artifact compiler extractors and remove placeholders.

Acceptance criteria: Importing a TSX route produces real page artifacts, residual islands, source locations, and fidelity score.

---

### P0-003 — Page-builder E2E remains test-theater risk

Evidence: Test comments repeatedly state real E2E would be done in Playwright; tests call callbacks directly; local `vi` mock remains. 

Impact: Test suite may pass without proving product behavior.

Fix: Replace with real interactions using Testing Library for unit/integration and Playwright for actual E2E.

Acceptance criteria: Tests fail if drag/drop, save/reload, conflict, slot drop, and preview update do not work.

---

### P0-004 — DB repository needs transaction/concurrency hardening

Evidence: Repository performs select/archive/update statements but file does not show explicit transaction boundaries; uses class-level cached thread pool. 

Impact: Partial writes, version inconsistency, or resource exhaustion under load.

Fix: Use managed executor, transaction wrapper, server-generated versions, and concurrent writer tests.

Acceptance criteria: Concurrent updates deterministically produce one success and one conflict, with no partial archive/update state.

---

## P1 findings

### P1-001 — Server-side validator is too shallow

Evidence: It checks presence of `rootNodes`/`nodes`, enum strings, residual count, fidelity, and string patterns. 

Fix: Add full BuilderDocument + design-system contract validation.

---

### P1-002 — Semantic page-builder commands are generic and undo is not true inverse

Evidence: `PageBuilderCommands` uses generic command types and undo re-executes a modified command. 

Fix: Add true page-builder semantic commands and inverse deltas.

---

### P1-003 — Preview sessions are not production-signed

Evidence: `PreviewSession` uses a simple hash with comment that production should use HMAC. 

Fix: Server-side HMAC/JWT-style signing and validation.

---

### P1-004 — Unsafe component handling is string-based

Evidence: `UnsafeComponentHandler` uses `includes` checks and regex replacement. 

Fix: AST/static analysis + sandbox enforcement.

---

### P1-005 — Design-system enforcement exists but code still bypasses it

Evidence: ESLint rule exists, but new cockpit/command center components use raw HTML/Tailwind.   

Fix: Wire lint to CI and migrate new components.

---

# 6. Implementation plan

## Milestone 1 — Mount the phase cockpit UX

Tasks:

1. Replace `intent.tsx` export with `IntentCockpitRoute`.
2. Replace `shape.tsx` export with `ShapeCockpitRoute` that embeds canvas/page-builder.
3. Replace `validate.tsx` export with `ValidateCockpitRoute`.
4. Replace `generate.tsx` export with `GenerateCockpitRoute`.
5. Add `RunCockpitRoute`, `ObserveCockpitRoute`, `LearnCockpitRoute`, `EvolveCockpitRoute`.
6. Feed each cockpit from backed lifecycle/project APIs.

Acceptance criteria:

* Every phase route renders `data-testid="<phase>-cockpit"`.
* Each phase has exactly one primary CTA.
* Generic lifecycle/canvas is embedded as a panel, not used as the entire page.
* Playwright full-flow route expectations match real routes.

## Milestone 2 — Make persistence production-grade

Tasks:

1. Add transaction support to `DbPageArtifactRepository`.
2. Inject managed executor.
3. Use server-generated next `documentId` or explicit revision field.
4. Add typed JSON mapping.
5. Add authz checks to controller save/load/delete.
6. Add audit + OTel events.
7. Add Testcontainers integration tests.

Acceptance criteria:

* Save/load/conflict/delete work against real DB.
* Cross-tenant reads/writes fail.
* Conflicts preserve prior version.
* Audit trail records actor and operation.
* OTel metrics are emitted.

## Milestone 3 — Replace compiler placeholders

Tasks:

1. Wire `ImportSourceWorkflow` to artifact compiler extractors.
2. Implement source fetching through project/repo artifact access, not arbitrary client fetch.
3. Extract TSX AST, dependencies, styles, tests, stories.
4. Convert semantic model to `PageArtifactDocument[]`.
5. Surface residual islands.
6. Add codegen preview/diff/merge.

Acceptance criteria:

* TSX import produces real BuilderDocument.
* Route import produces multiple page artifacts when needed.
* Residuals are visible and reviewable.
* Placeholder helper returns are removed.

## Milestone 4 — Make commands semantic

Tasks:

1. Replace generic `CommandType` with page-builder operations.
2. Add inverse command definitions.
3. Integrate with `PageDesigner`.
4. Emit audit/telemetry/validation/autosave after each command.
5. Add tests for undo/redo of insert, move, prop update, delete, slot move.

Acceptance criteria:

* Undo truly reverses each operation.
* Redo reapplies it.
* Audit shows exact change.
* Autosave persists document state.

## Milestone 5 — Hard security and plugin/runtime enforcement

Tasks:

1. Replace preview hash with server HMAC/JWT-like signed session.
2. Enforce preview session in preview route.
3. Add CSP/sandbox headers.
4. Replace string unsafe-component checks with AST-based analysis.
5. Enforce plugin runtime policy at load and render time.

Acceptance criteria:

* Spoofed session fails.
* Expired session fails.
* Unsafe component falls back/blocks.
* Plugin cannot access forbidden APIs.
* Security tests prove enforcement.

## Milestone 6 — Design-system migration

Tasks:

1. Wire design-system ESLint config into CI.
2. Migrate new phase components to design-system primitives.
3. Migrate command center/provenance usage.
4. Migrate route pages.
5. Replace hardcoded colors with tokens.

Acceptance criteria:

* CI fails on raw controls outside allowlist.
* Core routes use design-system components.
* Dark mode and accessibility states are consistent.

## Milestone 7 — Real tests

Tasks:

1. Fix `pageBuilderE2E.test.tsx`.
2. Align Playwright route paths/test IDs with mounted app.
3. Add API integration tests.
4. Add accessibility tests.
5. Add visual regression for phase cockpits.
6. Add security tests for preview sessions and unsafe components.

Acceptance criteria:

* No comment-only tests.
* No fake local `vi`.
* Tests fail when routes/cockpits are not mounted.
* Tests cover real user interaction.

---

# 7. Test plan

## Unit tests

* `PageArtifactValidator`
* `DbPageArtifactRepository` JSON mapping
* `NextBestActionService`
* `PageBuilderCommands`
* `PreviewSession`
* `UnsafeComponentHandler`
* `GovernanceMetadataEnforcer`
* `ProvenanceBadge`

## Integration tests

* Page artifact save/load/conflict/delete with DB.
* Controller authz and tenant isolation.
* Phase cockpit route data loading.
* Page builder command → document mutation → autosave.
* Compiler import workflow → artifact compiler → BuilderDocument.

## E2E tests

* Onboarding → workspace → project → IntentCockpit.
* ShapeCockpit → canvas → page node → add component → preview.
* ValidateCockpit → approval.
* GenerateCockpit → codegen preview.
* RunCockpit → capability-gated plan.
* Observe → preview/metrics.
* Learn → retrospective.
* Evolve → next cycle.

## Security tests

* Preview session invalid/expired/spoofed.
* Cross-tenant artifact access.
* Unsafe component fallback/block.
* Plugin permission denial.
* CSP/sandbox behavior.

## Accessibility tests

* Keyboard-only full phase navigation.
* Focus management in dialogs/disclosures.
* Screen reader labels for primary actions.
* Color contrast for badges/statuses.
* Command palette keyboard use.

---

# 8. Readiness score

| Area                            | Score | Reason                                                                                |
| ------------------------------- | ----: | ------------------------------------------------------------------------------------- |
| Full UI/UX flow                 |    58 | Good components added, but route mounting incomplete.                                 |
| Cognitive-load reduction        |    65 | Cockpit model exists; actual phase routes still generic.                              |
| Phase cockpit maturity          |    45 | Components exist but not wired.                                                       |
| Canvas maturity                 |    70 | Strong prior canvas foundation; phase-aware defaults still need integration.          |
| Page builder maturity           |    68 | Improved renderer/persistence/drag-drop; commands/tests still weak.                   |
| Design-system consistency       |    45 | Enforcement exists, migration incomplete.                                             |
| Compiler/decompiler integration |    35 | Service shell exists; placeholder implementation.                                     |
| Live preview                    |    72 | Preview security improved previously; signed sessions need production implementation. |
| Persistence                     |    72 | DB repo exists; transaction/authz/O11y hardening needed.                              |
| Security/privacy                |    58 | Utilities exist; runtime enforcement incomplete.                                      |
| Auditability                    |    55 | Governance/audit concepts exist; operation-level persistence incomplete.              |
| Observability                   |    45 | Telemetry hooks exist; end-to-end OTel proof weak.                                    |
| AI/automation implicitness      |    60 | Wording improvements and NBA service exist; integration incomplete.                   |
| Test confidence                 |    38 | New specs exist but many are likely not executable against mounted routes.            |
| Production readiness            |    55 | Strong progress but too many shell/placeholder/unwired pieces.                        |

---

# 9. Prioritized backlog

| Priority | ID          | Work item                                                       | Owner area        | Dependencies                | Acceptance criteria                                 |
| -------- | ----------- | --------------------------------------------------------------- | ----------------- | --------------------------- | --------------------------------------------------- |
| P0       | UX-001      | Wire phase cockpits into actual phase routes                    | Frontend          | Cockpit components          | Each phase route renders cockpit with primary CTA   |
| P0       | TEST-001    | Fix page-builder E2E test theater                               | Frontend test     | Mounted builder             | Real drag/drop/save/preview assertions              |
| P0       | COMP-001    | Replace compiler import placeholders                            | Compiler/frontend | Artifact compiler           | TSX/route import produces real PageArtifactDocument |
| P0       | PERSIST-001 | Transactional DB page artifact persistence                      | Backend           | DB repo                     | Concurrent writes produce safe conflict behavior    |
| P0       | VALID-001   | Full server-side BuilderDocument validation                     | Backend/shared    | Contract schemas            | Invalid contracts/slots/props rejected              |
| P1       | CMD-001     | Semantic page-builder commands                                  | Canvas/builder    | Builder ops                 | True undo/redo and audit deltas                     |
| P1       | SEC-001     | Production signed preview sessions                              | Security          | Server secret/session store | Invalid/expired/spoofed sessions rejected           |
| P1       | DS-001      | Wire design-system ESLint into CI                               | Frontend tooling  | Rule file                   | CI fails on raw controls outside allowlist          |
| P1       | DS-002      | Migrate cockpits/command center to design-system primitives     | Frontend          | DS components               | No raw controls/hardcoded colors in new core UX     |
| P1       | AUTO-001    | Wire next-best-action service to dashboard/project/phase routes | Frontend/services | Project state APIs          | One best action shown per route                     |
| P1       | GOV-001     | Enforce governance metadata in inspector/save/preview           | Builder/security  | Metadata schema             | Risky/missing governance blocks promotion           |
| P2       | O11Y-001    | Add OTel metrics/spans for builder/persistence/preview          | Backend/frontend  | Telemetry infra             | Metrics visible by artifact/project                 |
| P2       | VIS-001     | Add visual regression for phase cockpits                        | Frontend test     | Cockpit routes              | Snapshot coverage for all major states              |
| P2       | PERF-001    | Large canvas/page builder performance tests                     | Frontend          | Test fixtures               | 1k/10k nodes measured and budgeted                  |

---

# Bottom line

`b90f644a...` is a meaningful improvement over the last reviewed state. The product now has many of the right architectural pieces: DB persistence, validation, phase cockpit components, provenance badges, command center, next-best-action service, security utilities, design-system enforcement rules, and E2E specs.

But the current state is still **not production-complete**. The most important distinction is:

```text id="72s3q7"
Many capabilities now exist as files/components/services.
Fewer are proven to be fully mounted, integrated, enforced, and tested end to end.
```

The next push should focus on wiring and proof, not adding more shells:

1. Mount phase cockpits into real routes.
2. Replace compiler placeholders with actual artifact compiler integration.
3. Make DB persistence transactional/authorized/observable.
4. Replace page-builder test theater with real tests.
5. Enforce design-system and governance rules in CI/runtime.
6. Align Playwright specs with actual mounted route paths and test IDs.