Below is an execution-ready implementation plan for YAPPC based on the corrected latest head: `719869ab1ac1bdef9608c8646079d96d28441c5b`. That commit is a changelog update pointing to the substantive YAPPC refactor `e1248a9e6e31fb2c78f48011747a2bc4161d21b2`. 

# YAPPC UI/UX + Builder Hardening Implementation Plan

## 0. Target outcome

YAPPC should become a **low-cognitive-load, canvas-native product creation system** where users always see the next best action, while the system handles planning, UI/page building, design-system consistency, compiler/decompiler flows, preview, validation, audit, privacy, security, and observability underneath.

The target experience:

```text
Workspace → Project → Current Phase Cockpit
  → one primary next action
  → canvas/page builder only when useful
  → live preview always nearby
  → suggestions are outcome-oriented, not “AI-branded”
  → governance/audit available on demand
  → advanced power tools progressively disclosed
```

---

# Milestone 1 — Correctness and persistence hardening

## Goal

Move the page/UI builder from “works locally and through HTTP API proof” to **durable, authorized, observable, audited persistence**.

The latest head now includes `PageArtifactController` with `PUT` and `GET` document APIs, tenant/workspace/project headers, `If-Match`, `ETag`, and `409` conflict handling.  The route registration also exists in `PageArtifactRoutes`. 

## Tasks

### YAPPC-PERSIST-001 — Replace in-memory repository with durable repository

Implement a DB-backed `PageArtifactRepository`.

Suggested files/modules:

```text
products/yappc/core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/pageartifact/
  PageArtifactRepository.java
  InMemoryPageArtifactRepository.java
  DbPageArtifactRepository.java        new
  PageArtifactDocument.java
```

Required behavior:

* Persist by `tenantId`, `workspaceId`, `projectId`, `artifactId`.
* Store `documentId` as optimistic concurrency version.
* Store serialized builder document payload.
* Store sync status, trust level, data classification, validation summary, residual islands, round-trip fidelity, and governance records.
* Preserve historical document versions or snapshots for rollback.

Acceptance criteria:

* `GET` returns latest persisted document with `ETag`.
* `PUT` with matching `If-Match` saves and returns new persisted state.
* `PUT` with stale `If-Match` returns `409` and `X-Current-Version`.
* Cross-tenant/workspace/project access is impossible.
* Repository is deterministic under concurrent updates.

### YAPPC-PERSIST-002 — Add authorization and capability checks

The controller validates required tenant/workspace/project headers today.  Add explicit authorization.

Required checks:

* User can read project.
* User can edit project.
* User can edit page artifacts.
* User can force-save over conflict.
* User can import/decompile artifacts.
* User can approve generated/suggested changes.

Acceptance criteria:

* Unauthorized read returns `403`.
* Unauthorized write returns `403`.
* Missing/invalid tenant/workspace/project scope returns safe `400`/`403`.
* Audit entry records denied access attempts.

### YAPPC-PERSIST-003 — Add server-side validation

Before saving a page artifact:

* Deserialize `PageArtifactDocument`.
* Validate `BuilderDocument`.
* Validate design-system contract references.
* Validate trust policy.
* Validate data classification.
* Validate residual island metadata.
* Validate no unsupported executable payload enters persisted document.

Acceptance criteria:

* Invalid document returns `422`.
* Trust-violating preview/runtime document cannot be saved as trusted.
* Unknown component renders through fallback but is marked review-required.
* Validation errors are returned in a structured response the UI can display.

### YAPPC-PERSIST-004 — Add audit and OTel instrumentation

Each page artifact operation should emit:

* Audit event.
* OTel span.
* Metrics.

Events:

```text
page_artifact.loaded
page_artifact.saved
page_artifact.save_conflict
page_artifact.validation_failed
page_artifact.force_saved
page_artifact.imported
page_artifact.decompiled
page_artifact.codegen_requested
```

Metrics:

```text
page_artifact_save_latency_ms
page_artifact_load_latency_ms
page_artifact_conflict_count
page_artifact_validation_error_count
page_artifact_force_save_count
```

Acceptance criteria:

* Every API request has correlation ID.
* Audit event includes actor, tenant, workspace, project, artifact, operation, result.
* Metrics can be viewed by project/workspace/tenant.

---

# Milestone 2 — Full YAPPC phase-specific UX

## Goal

Stop making multiple phases render the same generic route. Keep the 8-phase lifecycle, but give each phase a **distinct cockpit** with one primary outcome and low cognitive load.

Current route structure is strong, but several phase routes reuse canvas or lifecycle views. Shape and Generate route to canvas; Validate, Observe, Learn, and Evolve route to lifecycle; Run is disabled.       

## Tasks

### YAPPC-UX-001 — Add phase cockpit shell

Create reusable phase cockpit layout:

```text
products/yappc/frontend/web/src/components/phase/
  PhaseCockpitLayout.tsx
  PhasePrimaryActionCard.tsx
  PhaseBlockerPanel.tsx
  PhaseEvidencePanel.tsx
  PhaseSuggestedNextStep.tsx
  PhaseAdvancedDisclosure.tsx
  PhaseGovernanceTrace.tsx
```

Each phase page should show:

```text
1. Current phase purpose
2. One primary next action
3. Current blockers
4. Evidence supporting the next action
5. Suggested automation
6. Governance/provenance on demand
7. Advanced tools collapsed by default
```

Acceptance criteria:

* User can understand “what to do next” in under 5 seconds.
* Every phase has exactly one primary CTA.
* Advanced panels are collapsed by default.
* Empty states are actionable and truthful.

### YAPPC-UX-002 — Implement phase-specific cockpits

Create:

```text
IntentCockpit
ShapeCockpit
ValidateCockpit
GenerateCockpit
RunCockpit
ObserveCockpit
LearnCockpit
EvolveCockpit
```

Phase behavior:

| Phase    | Primary action                                           | Default surface                  |
| -------- | -------------------------------------------------------- | -------------------------------- |
| Intent   | Clarify problem and user outcome                         | Intent capture + evidence        |
| Shape    | Convert intent into requirements and UI/page structure   | Canvas/page builder              |
| Validate | Review and approve requirements/design/generation packet | Approval/gate cockpit            |
| Generate | Prepare implementation package                           | Generator cockpit + code preview |
| Run      | Prepare/execute safe run path                            | Capability-gated run cockpit     |
| Observe  | Review health, preview, telemetry, incidents             | Observability cockpit            |
| Learn    | Capture lessons and reusable patterns                    | Retrospective cockpit            |
| Evolve   | Plan next cycle                                          | Roadmap/backlog cockpit          |

Acceptance criteria:

* No phase is just a blind alias to generic lifecycle unless wrapped in phase-specific framing.
* Each phase has phase-specific copy, CTA, blockers, and evidence.
* Run phase no longer shows only a disabled construction page; it shows useful readiness/planning while execution is feature-gated.

### YAPPC-UX-003 — Add provenance badges everywhere

Every lifecycle item should show provenance:

```text
Backed        persisted API data
Derived       computed from backed data
Suggested     generated recommendation requiring review
Preview       non-production / preview-only
Unavailable   missing capability or integration
```

Acceptance criteria:

* Seeded/suggested content is never displayed as persisted truth.
* Users can tell what is safe to act on.
* Governance trace shows source, timestamp, actor/system, confidence, and review state.

---

# Milestone 3 — Design-system consistency and low cognitive load

## Goal

Make YAPPC visually and behaviorally consistent by using `@ghatana/design-system` across the entire app instead of raw controls and ad-hoc Tailwind patterns.

The app already uses the design system in important shell/canvas areas, but major routes still use raw controls and custom styling: dashboard, workspaces, projects, login, onboarding, preview, and parts of lifecycle.      

## Tasks

### YAPPC-DS-001 — Create product UI primitive mapping

Map raw elements to design-system primitives:

| Raw usage         | Replace with          |
| ----------------- | --------------------- |
| `button`          | `Button`              |
| `input`           | `TextField` / `Input` |
| `select`          | `Select`              |
| custom card div   | `Card`                |
| status badge span | `Badge` / `Chip`      |
| modal/dialog      | `Dialog`              |
| table             | `DataTable`           |
| alert div         | `Alert`               |
| skeleton div      | `Skeleton`            |

Acceptance criteria:

* No raw form controls in product routes unless inside design-system components.
* Common variants are standardized: primary, secondary, destructive, ghost, subtle.
* Loading, empty, error, and unavailable states use shared components.

### YAPPC-DS-002 — Migrate key routes

Migrate in this order:

1. Login.
2. Onboarding.
3. Dashboard.
4. Workspaces.
5. Projects.
6. Project overview.
7. Preview.
8. Lifecycle/phase cockpits.
9. Canvas side panels.
10. Page builder inspector.

Acceptance criteria:

* Visual density is consistent.
* Dark mode works without route-specific patches.
* Keyboard focus states are consistent.
* All controls have accessible labels.

### YAPPC-DS-003 — Add enforcement

Add lint/check rules:

```text
No raw button/input/select/textarea in product routes.
No hardcoded color tokens outside approved theme files.
No unapproved ad-hoc card classes.
No direct status color mapping outside status-token helpers.
```

Acceptance criteria:

* CI fails when new raw controls are introduced.
* Exceptions require explicit comments and design-system issue references.
* Storybook/visual snapshots cover route-level states.

---

# Milestone 4 — Canvas + page builder integration

## Goal

Make page/UI builder a first-class canvas capability, not a separate editor embedded in a node.

At latest head, `ComponentRenderer` now uses a renderer manifest registry and fallback rendering rather than the prior hardcoded map.  The renderer manifest system supports dynamic registration, prop adapters, preview policy, and fallback renderers. 

## Tasks

### YAPPC-CANVAS-001 — Add semantic page-builder commands

Current page-node updates still risk being too coarse. Add semantic commands:

```text
InsertComponentCommand
MoveComponentCommand
ReorderComponentCommand
UpdateComponentPropsCommand
DeleteComponentCommand
SetResponsiveVariantCommand
AddActionBindingCommand
AddDataBindingCommand
ImportSemanticModelCommand
ApplySuggestedImprovementCommand
```

Each command must emit:

```text
undo/redo delta
audit event
telemetry event
validation result
autosave request
governance record if generated/suggested
```

Acceptance criteria:

* Undo/redo works at component-operation level, not only whole page-doc replacement.
* Audit log says exactly what changed.
* Persistence saves after command commit.
* Validation runs after each semantic operation.

### YAPPC-CANVAS-002 — Add phase-aware canvas defaults

Canvas should automatically adapt by phase:

| Phase    | Default mode | Visible tools                               |
| -------- | ------------ | ------------------------------------------- |
| Intent   | Brainstorm   | notes, problem, research, persona           |
| Shape    | Design       | requirements, page builder, diagrams        |
| Validate | Review       | approvals, comments, gates                  |
| Generate | Code         | implementation plan, generated files, tests |
| Run      | Deploy       | run plan, pipeline readiness                |
| Observe  | Observe      | metrics, preview, incidents                 |
| Learn    | Knowledge    | retrospectives, learnings                   |
| Evolve   | Roadmap      | next cycle, backlog, capability plan        |

Acceptance criteria:

* Canvas opens with phase-relevant tools only.
* Advanced tools are available through command palette or disclosure.
* User can override mode, but the UI explains the current default.

### YAPPC-CANVAS-003 — Add bidirectional preview selection

Preview protocol already has typed host/preview messaging, and preview-builder security has improved. 

Add:

* Click in preview selects component in page builder.
* Hover in preview highlights component in builder.
* Selecting builder node highlights preview element.
* Preview emits interaction telemetry.
* Unsupported components show fallback review state.

Acceptance criteria:

* Builder selection and preview selection stay in sync.
* Preview remains sandboxed and origin-safe.
* Hover/click events do not mutate data unless explicitly intended.

---

# Milestone 5 — Renderer/plugin extensibility hardening

## Goal

Turn the new renderer manifest and plugin loader into a safe, flexible design-system extension model.

`ComponentPluginLoader` now validates package name, version, builder compatibility, renderers, and elevated-permission allowlisting. 

## Tasks

### YAPPC-PLUGIN-001 — Add runtime policy enforcement

Current plugin loader validates metadata, but runtime behavior still needs enforcement.

Add:

```text
PluginRuntimePolicy
PluginSandboxBoundary
PluginNetworkPolicy
PluginStoragePolicy
PluginBrowserApiPolicy
PluginTelemetryPolicy
```

Acceptance criteria:

* Plugin cannot access localStorage unless allowed.
* Plugin cannot perform network calls outside allowlist.
* Plugin cannot execute unsafe script payloads.
* Plugin preview policy is enforced in preview mode.
* Elevated plugin requires explicit workspace/project approval.

### YAPPC-PLUGIN-002 — Add component compatibility validation

Before loading renderer package:

* Validate renderer contract exists or is imported as custom contract.
* Validate prop adapter outputs match design-system prop schema.
* Validate fallback renderer is used for unknown components.
* Validate preview policy compatibility with document trust level.

Acceptance criteria:

* Invalid renderer package cannot load.
* Unknown custom components render as review-required, not broken UI.
* Contract mismatch produces actionable validation error.

### YAPPC-PLUGIN-003 — Add design-system generator handoff

When compiler/decompiler sees custom components:

```text
custom component
→ inferred contract candidate
→ design-system generator
→ review
→ registry entry
→ renderer manifest
→ codegen mapping
```

Acceptance criteria:

* Decompiled custom components become registry candidates.
* Candidate has confidence, source location, props, slots, events, styles.
* Human review can accept, edit, or reject.
* Accepted candidate becomes reusable design-system component.

---

# Milestone 6 — Artifact compiler/decompiler full integration

## Goal

Move from pasted JSON import bridge to a true artifact compiler/decompiler workflow.

Current bridge is useful but shallow. The product needs:

```text
source files/routes/stories
→ extractors
→ artifact graph
→ semantic product model
→ page artifact documents
→ canvas nodes
→ residual review
→ codegen/diff/merge
```

## Tasks

### YAPPC-COMPILER-001 — Add “Import from source” workflow

UI entry points:

```text
Canvas toolbar → Import
Command palette → Import existing UI/page
Page builder → Import from source
Project phase Generate → Decompile existing app
```

Import options:

* TSX component.
* Route file.
* Storybook story.
* Existing generated artifact.
* Zip/project folder later.

Acceptance criteria:

* User does not paste raw JSON for primary workflow.
* Pasted JSON remains developer/debug import only.
* Import produces one or more `PageArtifactDocument`s.
* Multi-page imports create multiple page nodes.

### YAPPC-COMPILER-002 — Add residual island review UI

Residual islands should be visible but not overwhelming.

Create:

```text
ResidualIslandPanel.tsx
ResidualIslandCard.tsx
RoundTripFidelityBadge.tsx
CompilerLossPointList.tsx
SourceLocationLink.tsx
```

Acceptance criteria:

* Residuals show source location, reason, severity, and required action.
* Low-fidelity imports require review.
* User can accept fallback, map to design-system component, or mark as custom.
* No unmodeled logic is silently dropped.

### YAPPC-COMPILER-003 — Add codegen preview and diff/merge

Add workflow:

```text
BuilderDocument
→ generate React code
→ show generated files
→ show ownership regions
→ compare with existing source
→ safe apply / create PR / export patch
```

Acceptance criteria:

* Generated code shows imports, components, tests, and ownership regions.
* Loss points are visible.
* Unsafe overwrite is blocked.
* Merge requires review for residual or low-confidence areas.

---

# Milestone 7 — Full registry-driven property inspector

## Goal

Make the page builder inspector truly driven by design-system contracts, not hand-coded fields.

## Tasks

### YAPPC-INSPECTOR-001 — Build real configurator groups

Use registry metadata to group fields:

```text
Content
Appearance
Layout
Behavior
Accessibility
Data binding
Actions
Responsive
Governance
```

Acceptance criteria:

* Field grouping comes from contract metadata.
* Empty/irrelevant groups are hidden.
* Required fields are surfaced first.
* Advanced groups are collapsed by default.

### YAPPC-INSPECTOR-002 — Add rich field controls

Support:

```text
text
number
boolean
enum/select
token reference
color token
spacing token
component reference
slot reference
action binding
data binding
object editor
array editor
responsive override
state variant
```

Acceptance criteria:

* Invalid values cannot be entered through normal UI.
* Token pickers use active design-system theme.
* Action/data binding changes create semantic commands.
* Responsive/state edits are visible in preview.

### YAPPC-INSPECTOR-003 — Enforce governance metadata

For every selected component, show and enforce:

* Required accessibility props.
* Privacy classification.
* Telemetry sensitivity.
* Review-required props.
* Preview trust restrictions.
* Suggested improvements.

Acceptance criteria:

* Missing required a11y props produce validation errors.
* Input-like components default to privacy-sensitive.
* Telemetry-sensitive components require consent policy.
* Review-required changes cannot be promoted silently.

---

# Milestone 8 — Implicit AI/ML and automation UX

## Goal

Make AI/ML pervasive internally but invisible as a product concept unless governance requires disclosure.

Current UI still exposes explicit AI wording in onboarding, action registry, and builder lineage/review surfaces. The command/action registry has explicit AI actions and categories.  Onboarding uses explicit AI suggestion wording. 

## Tasks

### YAPPC-AUTO-001 — Rename visible AI surfaces

Replace:

| Current        | New                           |
| -------------- | ----------------------------- |
| AI is thinking | Finding a good starting point |
| AI suggests    | Suggested                     |
| AI changes     | Suggested improvements        |
| AI lineage     | Governance trace              |
| Ask AI         | Help me move forward          |
| Generate Code  | Prepare implementation        |
| Open AI Chat   | Open guidance                 |
| /fix           | Resolve issue                 |
| /improve       | Improve selection             |

Acceptance criteria:

* Users do not need to know AI exists to use the product.
* Governance trace still records AI/model involvement internally.
* Review-required suggestions explain impact, not model mechanics.

### YAPPC-AUTO-002 — Add next-best-action service

Create a service that ranks actions by:

```text
current phase
current project state
selected canvas/artifact node
blockers
role/persona
permissions
recent activity
capability availability
validation errors
```

Acceptance criteria:

* Dashboard shows one primary next action.
* Phase cockpit shows one primary next action.
* Command palette shows best actions first.
* Advanced actions require search or disclosure.

### YAPPC-AUTO-003 — Auto-apply safe improvements

Safe improvements:

* Add missing accessible labels.
* Normalize spacing/token values.
* Fix invalid enum prop.
* Suggest responsive defaults.
* Map obvious imported component to known design-system contract.
* Resolve simple validation warnings.

Risky improvements require review:

* Data binding.
* Action binding.
* Security/privacy changes.
* Generated code merge.
* Deleting/replacing components.
* Low-confidence decompiler mappings.

Acceptance criteria:

* Safe changes are reversible and audited.
* Risky changes are review-gated.
* Every automated change has confidence, source, rationale, and rollback.

---

# Milestone 9 — Preview and runtime security

## Goal

Harden preview beyond current postMessage improvements.

The latest preview runtime now derives expected parent origin, validates source and origin, and posts back to explicit origin.  Security tests now cover spoofed origins, wrong source, explicit origin targeting, malformed messages, unknown types, and sandbox/referrer behavior. 

## Tasks

### YAPPC-SEC-001 — Add signed preview session

Instead of relying only on referrer:

```text
host creates previewSessionId
host signs/records allowed origin + document id + trust level
iframe loads /preview/builder?session=...
runtime validates session before accepting messages
```

Acceptance criteria:

* Preview rejects messages without valid session.
* Session expires.
* Session scoped to project/artifact/document.
* Session trust level controls preview capabilities.

### YAPPC-SEC-002 — Add CSP and iframe sandbox policy

Define:

```text
Content-Security-Policy
frame-ancestors
script-src
connect-src
style-src
img-src
sandbox attributes
```

Acceptance criteria:

* Preview cannot navigate top frame.
* Preview cannot call unapproved domains.
* Custom components cannot escape sandbox.
* Security tests verify blocked behavior.

### YAPPC-SEC-003 — Add unsafe custom component handling

For custom/decompiled components:

* Render fallback by default.
* Require review for interactive behavior.
* Block browser APIs unless approved.
* Block network/storage unless approved.

Acceptance criteria:

* Unknown component never executes arbitrary code.
* Review can map to approved design-system component.
* Approved plugin policy is explicit and audited.

---

# Milestone 10 — Full-flow E2E and anti-test-theater cleanup

## Goal

Replace skeletal tests with real, behavior-proving tests.

The new `pageBuilderE2E.test.tsx` describes the right scenarios, but many tests are comments/placeholders and it includes a local `vi` mock instead of using Vitest normally.  This needs to be fixed before claiming E2E confidence.

## Tasks

### YAPPC-TEST-001 — Fix page builder E2E test

Replace placeholder tests with real interactions:

* Render page designer.
* Drag component from palette.
* Drop into root.
* Drop into `Card.actions`.
* Drag existing component before/after another.
* Update properties.
* Validate document.
* Assert `onDocumentChange`.
* Assert governance record created for suggested import.
* Assert fallback renderer for unknown component.

Acceptance criteria:

* No comment-only tests.
* No local fake `vi`.
* Tests fail if interaction does not work.
* Tests assert real DOM and document mutations.

### YAPPC-TEST-002 — Add Playwright full-flow tests

Add real browser E2E:

```text
onboarding → workspace → project → intent → shape canvas
shape canvas → create page node → add components → preview
validate → approval cockpit
generate → codegen preview
run → capability-gated run cockpit
observe → project preview
learn → retrospective
evolve → next cycle plan
```

Acceptance criteria:

* Tests use real routes.
* Tests mock only external integrations at network boundary.
* No route components are replaced with fake components.
* Accessibility checks run in each major route.

### YAPPC-TEST-003 — Add security and accessibility tests

Security:

* Preview spoofed origin rejected.
* Unknown message rejected.
* Invalid session rejected.
* Unsafe plugin rejected.
* Cross-tenant artifact load denied.

Accessibility:

* Keyboard-only onboarding.
* Keyboard-only command palette.
* Keyboard-only phase navigation.
* Keyboard-only page builder selection and inspector.
* Focus trap in dialogs.
* ARIA labels for icon buttons.
* Color contrast for statuses.

---

# Milestone 11 — Full YAPPC UI/UX consolidation

## Goal

Make YAPPC feel like one coherent product, not a collection of screens.

## Tasks

### YAPPC-UX-010 — Dashboard-first next-action system

Dashboard should show:

```text
Primary: Continue recommended work
Secondary: Review blocker
Tertiary: Create new project
Quiet: workspace/project health
```

Acceptance criteria:

* Dashboard does not expose unnecessary controls.
* User can resume meaningful work in one click.
* Blockers explain exactly what to do.

### YAPPC-UX-011 — Project overview as command center

Project overview should show:

```text
Current phase
Current readiness
Primary next action
Current blocker
Recent backed activity
Suggested improvement
Governance trace link
```

Acceptance criteria:

* No synthetic activity appears as real activity.
* Promotion readiness explains blocker/evidence.
* Clicking next action takes user to exact phase/surface.

### YAPPC-UX-012 — Command palette as action discovery, not second nav

Command palette should default to:

```text
Best next actions
Recent actions
Current selection actions
Navigation actions
Advanced search
```

Acceptance criteria:

* Only context-relevant actions show first.
* AI category removed/reworded.
* Disabled actions explain why.
* Dangerous actions require confirmation.

---

# Release gates

## Gate A — Persistence readiness

Required before enabling production save:

* Durable repository implemented.
* Tenant/workspace/project authz.
* Optimistic concurrency.
* Server-side validation.
* Audit events.
* OTel spans/metrics.
* DB integration tests.

## Gate B — Builder readiness

Required before calling builder production-grade:

* Semantic commands.
* Registry-driven inspector.
* Renderer manifest compatibility tests.
* Slot-aware drag/drop real tests.
* Live preview selection sync.
* Fallback renderer for unknown components.
* No placeholder E2E tests.

## Gate C — Security readiness

Required before custom/decompiled components:

* Signed preview session.
* CSP/sandbox policy.
* Runtime plugin policy enforcement.
* Unknown component review gate.
* Spoofing and unsafe-code tests.

## Gate D — UX readiness

Required before broader user testing:

* Phase-specific cockpits.
* No visible AI-first wording.
* Design-system migration for core routes.
* Provenance badges.
* Dashboard/project next-best-action flow.
* Keyboard/accessibility validation.

---

# Suggested implementation order

## Sprint 1 — Correct latest-head hardening gaps

1. Implement DB-backed `PageArtifactRepository`.
2. Wire repository into YAPPC runtime.
3. Add authz checks to page artifact controller.
4. Add server-side validation.
5. Fix page-builder E2E test from placeholder to real interactions.
6. Add audit + OTel for save/load/conflict.

## Sprint 2 — Phase cockpit and low-cognitive-load UX

1. Build `PhaseCockpitLayout`.
2. Implement `IntentCockpit`, `ShapeCockpit`, `ValidateCockpit`.
3. Replace generic phase route aliases with cockpit wrappers.
4. Add provenance badges.
5. Rename AI-visible wording to outcome-oriented wording.

## Sprint 3 — Canvas/page builder integration

1. Add semantic page-builder commands.
2. Add operation-level audit.
3. Add phase-aware canvas defaults.
4. Add bidirectional preview selection.
5. Add slot/drop visual polish and keyboard reorder.

## Sprint 4 — Design-system and inspector

1. Migrate login/onboarding/dashboard/workspaces/projects to design-system controls.
2. Build grouped property inspector.
3. Add token pickers, binding editors, action editors.
4. Enforce privacy/a11y/telemetry metadata.

## Sprint 5 — Compiler/decompiler and generator workflow

1. Replace pasted JSON import with source import workflow.
2. Add residual island review UI.
3. Add design-system component candidate flow.
4. Add codegen preview/diff/merge.
5. Add round-trip fidelity release gate.

## Sprint 6 — Plugin/runtime security and full E2E

1. Add signed preview sessions.
2. Add CSP/sandbox enforcement.
3. Add runtime plugin policy enforcement.
4. Add Playwright full-flow tests.
5. Add accessibility/security regression suite.

---

# Final prioritized backlog

| Priority | ID                  | Work item                                                      |
| -------- | ------------------- | -------------------------------------------------------------- |
| P0       | YAPPC-PERSIST-001   | DB-backed page artifact repository                             |
| P0       | YAPPC-PERSIST-002   | Authz and scoping for page artifact API                        |
| P0       | YAPPC-TEST-001      | Replace skeletal page-builder E2E with real tests              |
| P0       | YAPPC-UX-001        | Phase cockpit shell                                            |
| P0       | YAPPC-UX-003        | Provenance badges for backed/derived/suggested/preview content |
| P0       | YAPPC-AUTO-001      | Remove visible AI-first wording                                |
| P1       | YAPPC-CANVAS-001    | Semantic page-builder commands                                 |
| P1       | YAPPC-INSPECTOR-001 | Full registry-driven inspector                                 |
| P1       | YAPPC-COMPILER-001  | Source-based compiler/decompiler import                        |
| P1       | YAPPC-SEC-001       | Signed preview sessions                                        |
| P1       | YAPPC-PLUGIN-001    | Runtime plugin policy enforcement                              |
| P1       | YAPPC-DS-002        | Design-system migration of core routes                         |
| P2       | YAPPC-AUTO-002      | Next-best-action ranking service                               |
| P2       | YAPPC-CANVAS-002    | Phase-aware canvas defaults                                    |
| P2       | YAPPC-TEST-002      | Full Playwright lifecycle tests                                |

This plan shifts the work from “add more UI” to **make the existing system truthful, durable, extensible, secure, observable, and cognitively light**.
