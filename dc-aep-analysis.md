I re-reviewed against `main`. I could verify the latest YAPPC-related commit as `fb94a5d1418d0b2fce18a217b9c194f06aa3048e` from 2026-05-03 16:41 UTC; that commit only updates `products/yappc/CHANGELOG.md` and references substantive commit `bcc0e31d58af8095317297a8972814b7cb5e7c38`. The referenced `bcc0e31` commit is actually a DMOS document/backlog change, not a YAPPC builder implementation change.  

External web research is disabled in this chat, so I could not perform live industry research. The findings below are repo-backed and supplemented with established product architecture patterns.

## Executive conclusion

The latest YAPPC builder state is **materially better than the previous snapshot**, but still not fully production-grade for the vision of an integrated canvas/page-builder/artifact-compiler/live-preview/design-system-generator platform.

The good news: several prior gaps are now partially addressed. `PageDesignerNode` now uses a serialized `PageArtifactDocument`, dispatches node updates through the canvas command system, schedules autosave, and embeds `LivePreviewPanel` beside the page designer.  `PageArtifactDocument` now explicitly stores serialized `BuilderDocument`, source, sync status, trust level, data classification, round-trip fidelity, residual island IDs, and validation summary.  `PageDesigner` is now registry-driven, validates documents, supports insertion into selected containers, supports a limited “Nest” operation, and uses `validateDocument`. 

The remaining problem: the implementation is still a **starter builder**, not yet the full integrated YAPPC canvas builder. It still has only five starter component contracts, limited property editing, incomplete slot-aware drag/drop, weak server persistence, no true compiler/decompiler UX loop, incomplete live preview runtime, and insufficient use of AI lineage, observability, privacy, security, and audit primitives.

---

## What improved since the prior review

### 1. Page documents are now persistence-safe

Previously the page node stored raw `BuilderDocument` state directly. The latest code introduces `PageArtifactDocument`, using `serializeDocument` and `deserializeDocument`, which is the right direction for persistence and canvas round-tripping. 

This closes a major structural gap. However, the current autosave target is still local storage, not a product/server persistence adapter. `PageDesignerNode` uses `LocalStoragePersistenceAdapter('@ghatana/yappc:page-builder:')`, then marks the node as synced after local save.  That is useful for browser recovery, but it is not enough for multi-user, audit, backend sync, artifact graph persistence, or governed release workflows.

### 2. PageDesigner is now registry-driven

The latest `PageDesigner` uses `getContractMap`, `getBuilderPalette`, `normalizeContractName`, `isContainerContract`, and `getDefaultSlotName`.  The registry helper registers starter contracts and maps legacy types to canonical contract names such as `Button`, `Card`, `TextField`, `Typography`, and `Box`. 

This is a big improvement over the previous hardcoded palette. But it is still only **partially registry-driven** because the renderer still switches manually over five known contracts. 

### 3. Live preview is now embedded into the page node

`PageDesignerNode` now renders `LivePreviewPanel` next to `PageDesigner` when expanded.  `LivePreviewPanel` now supports viewport, theme, locale, validation blocking, debounced update, and mount/update distinction. 

This is the correct UX direction. The next gap is that the iframe still defaults to `about:blank`, and the repo evidence does not show a working preview runtime that actually receives `MOUNT_DOCUMENT` / `UPDATE_DOCUMENT` and renders the document.

### 4. Preview security improved

`PreviewHostService` now imports `createSafeMessageHandler`, validates incoming messages through the trust handler, and no longer posts messages to `"*"` when there is no trusted origin. It returns early when `trustedOrigins[0]` is absent. 

This fixes a serious earlier security concern. But it also means local `about:blank` previews will not receive messages unless a trusted preview origin exists, so the preview panel may look present while not actually live-updating.

### 5. Canvas command usage improved

Page node state changes now dispatch `UpdateNodeDataCommand`.  The canvas command system itself remains delta-based, mergeable, undoable/redoable, and batch-capable. 

This is good. However, page-level edits are still represented as whole `pageDocument` data updates rather than fine-grained builder commands such as “insert component,” “move to slot,” “update prop,” “set responsive variant,” or “add action.” That limits audit detail, collaboration, mergeability, telemetry, and review.

---

## Critical correctness gaps

### P0. Trust-level mismatch likely causes validation problems

`PageArtifactDocument.createEmptyBuilderDocument` sets `metadata.trustLevel` to `GENERATED_TRUSTED`.  But `validatePreviewTrustPolicy` ranks lowercase preview trust levels: `untrusted`, `semi-trusted`, `trusted-controlled`, and `trusted-local`. 

The starter contracts use preview levels like `semi-trusted` and `trusted-controlled`.  If the validator receives `GENERATED_TRUSTED`, it does not map to that ranking and may treat the document as rank `0`, creating false trust errors.

**Fix:** centralize trust conversion between platform trust levels (`GENERATED_TRUSTED`, `TRUSTED_WORKSPACE`, etc.) and preview trust levels (`trusted-controlled`, `trusted-local`, etc.). Use one canonical helper in validation and preview policy.

### P0. Button variant mapping is inconsistent

The starter contract default for `Button.variant` is `contained`.  The latest `ComponentRenderer` passes `instance.props.variant` directly as a design-system `Button` variant typed as `solid | outline | ghost`.  That means `contained`, `outlined`, and `text` can leak into a component expecting `solid`, `outline`, and `ghost`.

**Fix:** move prop adaptation into registry/codegen metadata or a component adapter layer. Do not let BuilderDocument contract props and rendered design-system props drift.

### P0. Property editor does not support contract enum/select metadata

`PropertyForm` only renders text, number, and boolean fields.  The registry helper maps every non-number/non-boolean prop to `text`. 

This means variant, color, size, alignment, token references, action props, and responsive settings are edited as free text rather than safe controls. That will create invalid values.

**Fix:** enhance `ds-schema`/`ds-registry` prop metadata to expose enum values, token refs, component refs, object/array editors, validation constraints, and configurator groups. Generate the property inspector from that metadata.

### P0. New page documents still start with empty `componentContracts`

`createEmptyBuilderDocument` creates a design system with `componentContracts: []`.  Validation currently passes external `contracts` from `getContractMap`, so editing may work. But documents themselves do not carry the active contract set unless converted through the adapter, which now injects registry contracts. 

This inconsistency matters for preview, export, artifact compiler, and persisted artifact portability.

**Fix:** create empty documents through the same registry-aware design-system factory used by the adapter. Every `BuilderDocument` should know which design-system version and component contracts it was authored against.

### P0. Live preview may not actually render BuilderDocument

The preview host sends messages only when there is a trusted origin.  `LivePreviewPanel` defaults to `about:blank` when no `previewUrl` is provided.  With no trusted origin, `send()` returns early. So the page builder can show a preview panel that never receives the document.

**Fix:** provide an actual first-party preview iframe app route, for example `/preview/builder`, add its origin to `trustedOrigins`, and implement the preview-side protocol receiver.

---

## Major functional gaps

### 1. Drag/drop is not yet a full page-builder experience

The latest PageDesigner can add components and insert into the selected container’s default slot.  That is progress, but it is not enough.

Missing:

* Drop-before / drop-after sibling.
* Drop into named slot such as `Card.actions`.
* Visual insertion markers.
* Reorder inside a slot.
* Drag existing component between containers.
* Drag to canvas coordinate and persist layout position.
* Keyboard-accessible move/reorder.
* Container/slot affordances.
* Grid/flex/stack layout authoring.
* Breakpoint-specific layout.

The current “Nest” action chooses a root container automatically, which is too implicit and not predictable enough for production page building. 

### 2. Registry usage is still shallow

Starter contracts are rich: they include builder palette metadata, canvas behavior, codegen, AI policy, accessibility guidance, telemetry, observability, preview restrictions, privacy, configurator groups, and responsive metadata. 

But product usage currently consumes only a small subset: palette, prop names, container check, and default slot. 

Missing:

* Configurator groups.
* Enum/select controls.
* Token picker.
* Responsive editor.
* State variants editor.
* Action binding editor.
* Data binding editor.
* Privacy badges.
* A11y guidance and auto-checks.
* Observability and telemetry indicators.
* AI policy enforcement.
* Preview restriction explanations.

### 3. Artifact compiler/decompiler is still not integrated into the canvas UX

The artifact compiler is conceptually strong. It explicitly targets bidirectional artifact-to-model conversion, with inventory, graph, semantic model, provenance, residuals, extractors, synthesis, and merge. 

But the current `toBuilderDocument` converter still has important limitations: one page is selected as root, additional pages become metadata/loss points, orphan components are appended, and token/theme/style/data/API/state models are not represented as component instances. 

Missing in product UX:

* “Import TSX/page/story into canvas.”
* “Decompile current app route.”
* “Show extracted components as registry candidates.”
* “Show residual islands.”
* “Generate BuilderDocument from decompiled model.”
* “Generate code from selected page.”
* “Diff generated code vs existing code.”
* “Safe merge / apply.”
* “Round-trip confidence panel.”
* “Review required residuals.”

### 4. Code generation exists, but builder does not expose it

`generateReactCode` exists and includes contract-aware loss analysis, import grouping, ownership regions, binding/state/responsive loss points, and round-trip confidence. 

But the page builder UI does not appear to expose code generation, ownership regions, compile diagnostics, generated files, or round-trip fidelity in the canvas workflow.

### 5. AI/ML is modeled but not productized as implicit assistance

The shared `BuilderDocument` model includes AI lineage, review status, pending props, privacy metadata, data classification, provenance, trust, visibility contract, and sync status.  Starter contracts include AI policies with permitted autonomous actions and thresholds. 

But PageDesigner does not yet use those to provide implicit assistance:

* Auto-fix validation errors.
* Recommend better component choices.
* Improve a11y labels.
* Detect PII fields.
* Suggest responsive variants.
* Simplify layouts.
* Map extracted custom components to design-system contracts.
* Auto-create review gates for risky changes.
* Attach AI lineage for every generated/modified node.

The current product still has explicit “AI Assistant” wording in ghost node creation paths.  The desired approach should be outcome-oriented: “Fix layout,” “Improve accessibility,” “Prepare responsive view,” “Resolve validation,” etc., while lineage remains available for audit.

---

## Security, privacy, auditability, and observability review

### Security

Improved: preview messaging no longer posts to `*` and uses a safe message handler. 

Still missing:

* Real preview origin route and CSP enforcement proof.
* Sandbox receiver-side validation.
* Component-level execution capability controls.
* Safe handling for custom/user-authored code.
* Explicit block on unsafe actions in generated pages.
* Security tests for iframe escape, postMessage spoofing, CSP, and untrusted document preview.

### Privacy

Improved: page documents carry `dataClassification`, and component contracts include privacy metadata.  

Still missing:

* Automatic propagation of contract privacy metadata to node metadata.
* UI badges for PII-capable components.
* Consent-aware action and event binding.
* Privacy review gate for input fields.
* Safe telemetry defaults.
* Data binding privacy classification.

### Auditability

Improved: shared operations accept an `OperationEventBus` and emit structured mutation payloads. 

Still missing:

* PageDesigner does not pass a real audit/telemetry event bus to builder operations.
* Updates are stored as large page-document replacement commands rather than semantic operations.
* No evidence of backend audit persistence for page builder actions.
* No user-visible audit/review panel for generated/imported changes.

### Observability

Improved: contracts contain observability metadata and performance marks. 

Still missing:

* OTel spans for builder operations.
* Metrics for page edit latency, preview update latency, validation failures, save failures, codegen failures.
* Preview telemetry wired into product monitoring.
* Canvas performance telemetry for large documents.
* Correlation between artifact, page document, generated code, preview, and audit trail.

---

## Priority remediation plan

### Phase 0 — Stop-the-line correctness

1. Fix trust-level enum mismatch between `BuilderDocument.metadata.trustLevel`, preview policy, and `validatePreviewTrustPolicy`.
2. Fix Button/TextField/Typography prop mapping between contract props and design-system component props.
3. Make `createEmptyBuilderDocument` registry-aware so it includes active component contracts.
4. Provide a real preview iframe route and trusted origin.
5. Add tests proving live preview receives `MOUNT_DOCUMENT` and `UPDATE_DOCUMENT`.
6. Add contract tests for `Button`, `Card`, `TextField`, `Typography`, and `Box` from registry → render → codegen.

### Phase 1 — Make builder truly registry-driven

1. Replace manual `ComponentRenderer` switch with contract/manifest-driven renderer.
2. Replace primitive-only `PropertyForm` with configurator-group-driven inspector.
3. Add enum/select/token/component-ref controls.
4. Use contract `builder.configurator.groups`.
5. Use contract `builderA11y`, `privacy`, `telemetry`, `observability`, and `aiPolicy`.
6. Add extension point for generated/custom components from the artifact compiler.

### Phase 2 — Complete drag/drop and layout authoring

1. Add slot-aware drop targets.
2. Add sibling insertion markers.
3. Add reorder inside slots.
4. Add drag existing component between containers.
5. Add named-slot support.
6. Add layout inspector for Box/Card/Stack/Grid/Flex.
7. Add breakpoint-aware layout editing.
8. Add keyboard-accessible reorder/move/nest/unnest.

### Phase 3 — Integrate artifact compiler/decompiler into canvas

1. Add “Import from code” and “Decompile route/component/story” actions.
2. Convert `SemanticProductModel` into one or more canvas page artifacts, not just one root page.
3. Represent additional pages as page nodes, not just metadata.
4. Surface residual islands as reviewable nodes/panels.
5. Show round-trip fidelity and confidence.
6. Expose generated code with ownership regions.
7. Add safe diff/merge workflow.
8. Feed extracted components into the design-system generator/registry candidate flow.

### Phase 4 — Make AI implicit and governed

1. Use contract `aiPolicy` to determine safe autonomous edits.
2. Add silent suggestions for layout, a11y, responsive variants, and component mapping.
3. Auto-apply only high-confidence safe changes.
4. Route risky changes to review.
5. Record `AIChangeRecord` on every AI-driven node/document mutation.
6. Hide “AI” as a feature label; expose outcomes and governance trace.

### Phase 5 — Production persistence and collaboration

1. Replace local-only autosave with a backend `PageArtifactDocumentPersistenceAdapter`.
2. Keep local storage only as offline/recovery cache.
3. Add optimistic concurrency and conflict handling.
4. Add CRDT or event-sourced collaboration for page docs.
5. Persist operation-level audit events.
6. Add rollback snapshots tied to artifact IDs.
7. Add server-side validation and policy enforcement before save/release.

### Phase 6 — Testing and release gates

Required tests before production readiness:

* Page node create/edit/delete/save/reload.
* Registry palette and property inspector.
* Slot-aware drag/drop and reorder.
* Live preview mount/update/theme/locale/viewport.
* Validation blocking.
* Trust-level security behavior.
* Codegen ownership and loss points.
* Decompile → BuilderDocument → edit → codegen loop.
* Residual island review.
* AI lineage creation.
* Privacy classification on input fields.
* OTel/audit event emission.
* Large-canvas performance.

---

## Updated readiness score

| Capability                       | Current state                                       | Readiness |
| -------------------------------- | --------------------------------------------------- | --------: |
| Page designer embedded in canvas | Implemented and improved                            |       70% |
| Serialized page document model   | Implemented, local persistence only                 |       65% |
| Registry-driven palette          | Implemented for starter contracts                   |       60% |
| Registry-driven property editing | Partial, primitive only                             |       35% |
| Registry-driven rendering        | Partial, still hardcoded switch                     |       35% |
| Slot-aware drag/drop             | Partial, selected-container default slot only       |       30% |
| Live preview                     | UI integrated, runtime still questionable           |       40% |
| Preview security                 | Improved host-side behavior                         |       60% |
| Artifact compiler/decompiler     | Strong library direction, weak UX integration       |       45% |
| Codegen                          | Shared core exists, not surfaced in builder         |       45% |
| Design-system generator          | Architecture exists, not fully connected to builder |       35% |
| AI-native implicit UX            | Metadata exists, product behavior missing           |       25% |
| Privacy/security/audit/o11y      | Metadata exists, enforcement incomplete             |       35% |
| E2E production confidence        | Some unit tests, insufficient end-to-end proof      |       30% |

## Bottom line

The latest implementation is moving in the right direction. It now has serialized page documents, registry-driven palette, validation, autosave, command-based node updates, and embedded live preview. That is a real architectural improvement.

The next work should **not** be more isolated features. The next work should be an integration hardening pass:

**registry-driven everything → slot-aware drag/drop → real live preview runtime → compiler/decompiler canvas workflow → server persistence/audit → implicit AI assistance → privacy/security/o11y enforcement → E2E proof.**

Once those are complete, YAPPC can become the easy-to-use but powerful canvas-native page/UI builder you are aiming for.
