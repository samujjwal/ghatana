## Verified baseline

Canonical unified remaining-items tracker: `docs/audit/unified-remaining-tracker.md`.

I reviewed `samujjwal/ghatana` at commit `9e87a778d5a5b0761f44daaf8b537aa85650f351`. The commit exists and is a merge commit titled “Merge branch 'main' of [https://github.com/samujjwal/ghatana”](https://github.com/samujjwal/ghatana”); the visible diff is mostly CI/security and digital-marketing related, so I inspected YAPPC source files at that exact ref rather than relying on the merge diff alone. 

Key YAPPC files inspected include route configuration, project shell, shared phase cockpit, phase data services, canonical typed API client, page builder, page builder commands, renderer, property inspector, plugin loader, live preview, preview runtime, preview session API, page designer canvas node, page artifact persistence, and artifact compiler bridge. The earlier pasted prompt asked for a broad/deep YAPPC review across UI/UX, page builder, canvas, design system, compiler/decompiler, live preview, AI/ML automation, security/privacy/o11y, and testing, which I used as the audit frame. 

---

# Executive audit result

YAPPC has a strong architectural skeleton: 8 lifecycle routes exist, phase cockpits are centralized, builder documents use `@ghatana/ui-builder`, page builder commands are semantic and typed, preview messaging is typed, plugin loading has explicit policy gates, and page artifact persistence has scoped adapters and conflict handling.    

The main issue is not “nothing exists.” The main issue is **production coherence**: correct patterns exist but are not enforced everywhere. Some routes still use raw `fetch` despite a canonical typed REST client; phase data still has hardcoded feature/tier flags; preview session behavior has a likely mismatch between default preview URL and token-required preview runtime; page builder logic is powerful but monolithic; design-system usage is mixed; compiler/decompiler integration is partial; and automation/AI terminology leaks into user-facing or API-facing concepts.     

Overall release readiness estimate: **48/100** for production-grade YAPPC as a full product creation operating system.

## Progress updates (2026-05-08)

- Strict Copilot fix-first guidance was added to the repo instructions so defect correction is anchored before broader cleanup in future code passes.
- YAPPC typed-client enforcement advanced: preview session issuance/validation now uses `yappcApi.previewSessions`, project shell prefetches use `yappcApi.projects.*`, and project export / AI assist now route through typed API helpers.
- YAPPC auth/session cleanup also moved to the typed client: auth refresh/logout and browser session clearing now route through `yappcApi.auth` instead of raw fetch.
- Validation evidence for the typed-client migration slice: `src/components/studio/__tests__/LivePreviewPanel.test.tsx`, `src/routes/app/project/__tests__/shell.test.tsx`, and `src/routes/__tests__/preview-builder-security.test.tsx` (`20/20`).
- Validation evidence for the auth slice: `src/services/auth/__tests__/AuthService.test.ts` (`5/5`).

---

# Highest-priority TODO backlog

## P0 — Must fix before claiming end-to-end production readiness

| ID           | Area                         | TODO                                                                                                                                                                                                        | Evidence                                                                                                                                                                                                                                             | Acceptance criteria                                                                                                                                                                                                                                                                                     |
| ------------ | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| YAPPC-P0-001 | Type-safe API usage          | Replace all raw REST `fetch` calls in YAPPC frontend with the canonical typed `yappcApi` client or a typed domain client exported through it.                                                               | The API client explicitly says all new REST endpoint calls must use this client, not raw `fetch`; preview session issuance/validation now use `yappcApi.previewSessions`, project shell prefetch/export/AI-assist paths route through typed `yappcApi` helpers, and auth refresh/logout/session clearing now route through `yappcApi.auth`.    | No raw `fetch(`/api...`)` remains in product UI/services except inside canonical API transport modules. ESLint rule blocks new raw API fetches. All migrated calls have typed request/response DTOs, typed errors, tests for success/failure/401/403/422/500.                                           |
| YAPPC-P0-002 | Preview correctness/security | Fix live preview session flow so every `/preview/builder` iframe gets a valid server-issued session token, or introduce an explicit dev-only same-origin preview mode behind a feature flag.                | `LivePreviewPanel` defaults to `/preview/builder` when no `previewContext` exists, but `preview-builder.tsx` denies access when no `session` query token is present.                                                                                 | Builder preview works on first load, refresh, and document update. Missing/expired/invalid token denies access. E2E verifies builder → preview mount/update/select/hover. Security tests verify spoofed origins/messages are rejected.                                                                  |
| YAPPC-P0-003 | Backend source of truth      | Make workspace/project/artifact/page-builder state backend-authoritative, with frontend as a typed client only.                                                                                             | Page artifact persistence sends tenant/workspace/project headers and supports HTTP + local fallback, but source-of-truth enforcement depends on backend endpoints and headers; project shell has raw client-side prefetches.                         | Server enforces tenant/workspace/project/user permissions for every read/write. Frontend cannot broaden scope via headers. API tests prove cross-tenant, cross-workspace, cross-project access is denied.                                                                                               |
| YAPPC-P0-004 | Phase IA/product flow        | Convert the 8 phase routes from mostly shared/generic cockpit surfaces into phase-specific product cockpits with shared layout but distinct contracts, data, actions, blockers, evidence, and review gates. | Routes define 8 phases, but `shape.tsx` simply exports `ShapeCockpitRoute`; `_phaseCockpit.tsx` centralizes all phases through one generic route component.                                                                                          | Each phase has a typed backend contract, one primary action, specific blockers/evidence, specific advanced surface, and role-aware action policy. E2E covers Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve.                                                                     |
| YAPPC-P0-005 | Capability/feature gating    | Replace hardcoded phase flags and hardcoded tier with backend-provided capabilities/entitlements.                                                                                                           | `usePhaseCockpitData` hardcodes `DEFAULT_ENABLED_PHASE_FLAGS` and passes tier `'starter'`.                                                                                                                                                           | Feature flags, tier, role, workspace capabilities, and project capabilities come from a typed backend endpoint. UI gates reflect backend authorization and cannot enable forbidden actions client-side.                                                                                                 |
| YAPPC-P0-006 | User-facing AI wording       | Remove user-facing “AI” phrasing and normalize to outcome-oriented language while preserving internal lineage/provenance.                                                                                   | Routes include “AI-powered code and doc generation”; shell has `VITE_FEATURE_AI_ASSIST`; phase data maps `aiHealthScore` and `aiNextActions`; page builder uses AI lineage records.                                                                  | UI copy uses “Suggested improvement,” “Review required,” “Prepare implementation,” “Resolve issue,” and “Governance trace.” Internal DTOs may keep model provenance, but public UI avoids “Ask AI / AI assistant / AI changes.” Copy tests and snapshot tests enforce terminology.                      |
| YAPPC-P0-007 | Design-system consistency    | Remove mixed local/ad-hoc UI primitives and hardcoded visual styles from builder/inspector routes; use `@ghatana/design-system` tokens/components consistently.                                             | `PageDesigner` and `PropertyForm` mix local `Input/Select/Textarea` with design-system components; `PropertyForm` has hardcoded borders/background/text colors in inline styles.                                                                     | No raw/hardcoded color, spacing, border, or typography styles outside design-system internals. Visual regression verifies cockpit, builder, inspector, preview, empty/error/loading states.                                                                                                             |
| YAPPC-P0-008 | Page artifact persistence    | Move page artifact save/load/graph ingest behind canonical typed API services and ensure atomic persistence semantics.                                                                                      | `HttpPageArtifactPersistenceAdapter` directly performs fetches to `/api/v1/page-artifacts` and graph ingest, then `ResilientPageArtifactPersistenceAdapter` falls back to local storage for allowed classifications.                                 | Save document + graph ingest is idempotent and transactionally safe server-side, or uses durable outbox. Conflicts return typed 409 with current version. Local fallback never stores sensitive/regulated data. Tests cover save, load, 401, 403, 409, offline, fallback TTL, sensitive-data rejection. |
| YAPPC-P0-009 | Preview trust boundary       | Harden preview CSP/sandbox/session handling as a product security boundary, not just UI behavior.                                                                                                           | Preview runtime sets security headers and validates origin/source/session; LivePreviewPanel also sets iframe sandbox and CSP attribute.                                                                                                              | Server emits CSP headers for preview route. Host and iframe validate origins and target origins. Tests cover malicious `postMessage`, missing session, expired session, wrong origin, invalid document, invalid sandbox, and blocked network/plugin calls.                                              |
| YAPPC-P0-010 | End-to-end E2E coverage      | Add real Playwright E2E for the complete YAPPC product loop, not only component-level tests.                                                                                                                | The code has rich flows across routes, phase cockpit, page builder, persistence, and preview; current inspected source does not prove these are mounted end-to-end.                                                                                  | E2E covers login/onboarding → workspace → project → phase cockpit → canvas/page node → edit/save/conflict → preview → validate/generate review → run/observe handoff. CI fails on broken flow.                                                                                                          |

---

## P1 — Production hardening and architectural cleanup

| ID           | Area                              | TODO                                                                                                                                                                             | Evidence                                                                                                                                                                                                       | Acceptance criteria                                                                                                                                                                                                                      |
| ------------ | --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| YAPPC-P1-001 | Type-safe boundaries              | Introduce runtime schema validation for all API responses and persisted documents using the existing typed DTOs plus schema validators.                                          | API client is typed but currently trusts parsed JSON as `T`; multiple APIs return `unknown` or partially typed payloads.                                                                                       | Every API domain has request/response schema validation. Invalid server payloads fail with typed recoverable errors. No direct `as T` at untrusted boundaries except in schema adapters.                                                 |
| YAPPC-P1-002 | Builder command discipline        | Standardize every builder mutation through `PageBuilderCommands`; forbid direct `BuilderDocument` mutation in UI components.                                                     | `PageBuilderCommands` already provides typed semantic commands, validation, undo/redo, audit, telemetry, and exhaustive command handling.                                                                      | Lint/test rule ensures page-builder document changes go through commands. Every command has unit tests, validation tests, undo/redo tests, and audit/telemetry assertions.                                                               |
| YAPPC-P1-003 | Undo/redo auditability            | Persist audit events for undo and redo, not only telemetry.                                                                                                                      | `execute()` calls `onAudit`, while `undo()` and `redo()` emit telemetry but do not call `onAudit`.                                                                                                             | Undo/redo produce immutable audit records with actor, command id, before/after doc ids, changed nodes, and review status.                                                                                                                |
| YAPPC-P1-004 | PageDesigner decomposition        | Split `PageDesigner.tsx` into focused modules: palette, document controller, import wizard, inspector host, governance panel, command toolbar, review queue, and preview bridge. | `PageDesigner` currently owns palette search, import wizard, compiler health, import review queue, registry candidate promotion, graph merge review, command audit, selection/hover, and document publishing.  | No page-builder component exceeds agreed complexity threshold. Each submodule has typed props and isolated tests.                                                                                                                        |
| YAPPC-P1-005 | Registry-driven inspector         | Replace raw JSON text areas for responsive/state variants and bindings with registry-derived typed controls.                                                                     | `PropertyForm` supports responsive variants, state variants, data bindings, action bindings, privacy classification, and governance acknowledgements, but uses JSON text areas for complex editing.            | Inspector fields are generated from registry schema. Invalid token/binding/variant values are prevented or explained before submit. Keyboard and a11y tests cover inspector forms.                                                       |
| YAPPC-P1-006 | Policy enforcement                | Move privacy, telemetry consent, review-required props, and a11y requirements from “UI acknowledgement” to enforceable policy at command/API/runtime layers.                     | `PropertyForm` blocks submit for missing a11y props and requires privacy/telemetry acknowledgements in the inspector.                                                                                          | Commands reject policy-violating mutations. Server rejects forbidden persisted artifacts. Preview blocks unsafe documents. Tests prove bypassing UI still fails.                                                                         |
| YAPPC-P1-007 | Compiler/decompiler fidelity      | Upgrade compiler bridge from flat extracted-component import to real JSX tree preservation, source locations, tokens, props, data/action bindings, and residual islands.         | Current bridge explicitly creates a flat layout because extracted component data does not expose JSX nesting.                                                                                                  | TSX/route/story import preserves hierarchy, slots, props, source locations, tokens, bindings, and residual islands. Low-confidence imports require review. Round-trip tests prove fidelity.                                              |
| YAPPC-P1-008 | Residual island UX                | Make residual islands first-class review items with source preview, impact, options, and promotion path to registry.                                                             | PageDesigner builds residual/loss-point review queues and registry candidate promotions.                                                                                                                       | User can accept, reject, map to design-system contract, or promote candidate. Every decision is persisted and auditable.                                                                                                                 |
| YAPPC-P1-009 | Plugin runtime hardening          | Harden plugin guard against async/network escape and global mutation.                                                                                                            | Plugin loader validates package manifests and temporarily intercepts `globalThis.fetch` during guarded execution.                                                                                              | Plugins must use provided runtime environment wrappers. Lint/build blocks direct global fetch/storage APIs in plugin renderers. Tests cover async fetch, localStorage, telemetry allowlist, browser APIs, unsigned marketplace packages. |
| YAPPC-P1-010 | Bidirectional preview reliability | Ensure builder selection, preview click, preview hover, viewport/theme/locale, and validation blocking work consistently.                                                        | `LivePreviewPanel` sends selection, viewport, theme, locale and handles preview click/hover callbacks; preview runtime emits click/hover messages.                                                             | E2E verifies selecting in builder highlights preview, clicking preview selects builder node, hover sync works, invalid docs pause preview, updates are debounced.                                                                        |
| YAPPC-P1-011 | Conflict resolution safety        | Replace raw serialized node merge logic with schema-validated semantic merge operations.                                                                                         | `PageDesignerNode` supports conflict reload, overwrite with reason, and merge choices over serialized node dictionaries.                                                                                       | Conflict resolution validates every selected local/remote node, preserves root/slot invariants, records audit reason, and rejects invalid merges.                                                                                        |
| YAPPC-P1-012 | Backend next-best-action service  | Move suggested actions/blockers/evidence/governance derivation into a backend service with typed response contracts.                                                             | `usePhaseCockpitData` builds blockers, evidence, governance, suggestions, and contracts client-side from project/activity/preview.                                                                             | Backend returns canonical next-best-action, blockers, evidence, governance trace, and review requirements. Frontend only renders typed contract.                                                                                         |
| YAPPC-P1-013 | Generation pipeline               | Connect Generate cockpit to real diff/apply/reject/rollback pipeline with typed provenance and file-level review.                                                                | API client defines generate run/diff/review DTOs and `_phaseCockpit` renders Apply/Reject/Roll back controls when action result is `generate-review`.                                                          | Generate produces typed diff, provenance, confidence, tests, validation result, and review gate. Apply/reject/rollback is atomic and auditable.                                                                                          |
| YAPPC-P1-014 | Run/Observe lifecycle handoff     | Make Run → Observe handoff real and backend-backed.                                                                                                                              | `_phaseCockpit` has Run post-actions: Roll back, Promote, Hand off to Observe.                                                                                                                                 | Run result has deployment id, environment, health signal, rollback target, promotion eligibility. Observe receives run context and live signals.                                                                                         |
| YAPPC-P1-015 | Multi-page product modeling       | Treat imported/generated pages as product-level artifacts, not isolated page nodes.                                                                                              | Multi-page import creates additional page-designer nodes from imported artifacts.                                                                                                                              | Product has explicit page graph, route graph, navigation model, shared layout, shared data/API contracts, and generated routes.                                                                                                          |
| YAPPC-P1-016 | Import/export maturity            | Formalize import/export schemas and migrations.                                                                                                                                  | Source import supports semantic JSON, zip, repo, storybook, route, and artifact sources; persistence has serialized documents and graph ingest.                                                                | Versioned schemas, migration tests, import validation, export reproducibility, and low-confidence review gates exist.                                                                                                                    |
| YAPPC-P1-017 | Product concept cleanup           | Standardize product terms: workspace, project, lifecycle phase, artifact, page artifact, builder document, canvas node, generated output, run, observation.                      | Routes and services use many overlapping concepts: project, artifact, page document, builder document, lifecycle, canvas, generated file, run.                                                                 | A canonical glossary exists and names are reflected in types, routes, API DTOs, labels, docs, tests.                                                                                                                                     |

---

## P2 — UX, performance, extensibility, and maintainability

| ID           | Area                       | TODO                                                                                                                                                                            | Acceptance criteria                                                                                                                                    |
| ------------ | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| YAPPC-P2-001 | Cognitive load             | Create a consistent phase cockpit information hierarchy: status → primary action → blockers → evidence → suggestions → governance → advanced workspace.                         | Every phase page visually answers: “what is this for, what next, what is blocked, what is backed truth, what needs review.”                            |
| YAPPC-P2-002 | Empty/loading/error states | Add complete state coverage for dashboard, workspace, project, phase cockpit, canvas, page builder, preview, imports, generation, run, observe.                                 | Storybook/visual regression covers all states. E2E verifies recoverability.                                                                            |
| YAPPC-P2-003 | Canvas phase awareness     | Make canvas tools adapt by lifecycle phase using backend policy plus `PhaseCanvasConfig`.                                                                                       | Intent has capture tools, Shape has edit tools, Validate is review-focused, Generate is diff/review-focused, Observe is read-only/live-signal focused. |
| YAPPC-P2-004 | DnD type safety            | Replace stringly typed drag payloads with validated typed payloads.                                                                                                             | Invalid MIME payloads and invalid drop targets return typed errors with user feedback. Tests cover before/after/slot/reorder/move/invalid/nesting.     |
| YAPPC-P2-005 | Keyboard-first builder     | Complete keyboard-only add/move/reorder/nest/delete/select/inspect flows.                                                                                                       | Keyboard E2E covers builder operations with no mouse.                                                                                                  |
| YAPPC-P2-006 | Visual consistency         | Add design-system lint rules for raw controls, raw colors, raw borders, raw spacing, raw typography.                                                                            | CI blocks new violations outside design-system internals.                                                                                              |
| YAPPC-P2-007 | Performance                | Add large-canvas performance budget and tests.                                                                                                                                  | 500-node canvas remains interactive; preview updates are debounced; no memory leak after repeated mount/update/teardown.                               |
| YAPPC-P2-008 | Observability              | Add frontend spans/events for phase action, builder command, preview update, import, persistence, conflict, generate review.                                                    | OTel/metrics correlate user action → API → persistence/generation result.                                                                              |
| YAPPC-P2-009 | Collaboration              | Add versions, locks, comments, reviewer assignments, and conflict-aware multi-user editing.                                                                                     | Two users editing same page get deterministic conflict/review flow.                                                                                    |
| YAPPC-P2-010 | Docs/dev experience        | Create canonical YAPPC architecture docs and developer guides for adding routes, phase actions, registry components, builder commands, plugins, compiler extractors, and tests. | A new contributor can add a component contract + renderer + inspector fields + codegen metadata + tests using documented steps.                        |

---

# Type-safety and “use existing correct patterns strictly” plan

## 1. Canonical API rule

Use `products/yappc/frontend/web/src/lib/api/client.ts` as the only REST API entry point. It already states that all new REST endpoint calls must use this client, and it separates REST-owned domains from GraphQL-owned domains. 

Concrete TODOs:

1. Add missing domains to `yappcApi`: preview sessions, page artifacts, artifact graph ingest, project export, project prefetch bundles, AI/automation assist renamed as suggested-action service.
2. Replace raw calls in `_shell.tsx`, `PreviewSessionApi.ts`, and `pageArtifactPersistence.ts`.
3. Add a lint rule: no `fetch('/api` outside `src/lib/api/**`, GraphQL client modules, or explicitly approved transport adapters.
4. Add typed error envelopes and correlation IDs to every client call.
5. Add contract tests for every API method.

## 2. Builder mutation rule

Use `PageBuilderCommands` as the only mutation path for builder documents. It already provides discriminated command types, immutable cloning, validation, undo/redo, audit, telemetry, autosave, and exhaustive switch handling. 

Concrete TODOs:

1. Forbid direct mutation of `BuilderDocument` in UI components.
2. Add command types only through discriminated unions.
3. Keep `assertNever` exhaustive checks.
4. Every command must define changed nodes, validation behavior, undo/redo behavior, audit event, telemetry event, and tests.
5. Add audit for undo/redo.

## 3. Builder document schema rule

Use `@ghatana/ui-builder` types and `validateDocument`, `serializeDocument`, and `deserializeDocument` everywhere a builder document crosses a boundary. `PageDesigner` already uses these APIs. 

Concrete TODOs:

1. Validate documents on import, persistence, preview mount, update, generation, export, and merge.
2. Add runtime schemas for serialized documents.
3. Never accept unvalidated `unknown` as a document.
4. Treat `Map` vs serialized object shape explicitly; preview runtime currently validates runtime `BuilderDocument` with `nodes instanceof Map`. 

## 4. Phase contract rule

Use a typed phase cockpit contract as the render contract for all phase pages. `_phaseCockpit.tsx` already exposes a `PhaseContractSummary` with persisted, derived, suggested, and review sections. 

Concrete TODOs:

1. Move phase contract construction to backend or shared domain service.
2. Frontend renders the contract; it should not invent blockers, suggestions, or review state independently.
3. Each phase gets a specific contract extension.
4. Add phase contract fixture tests and E2E flow tests.

## 5. Persistence adapter rule

Keep the existing adapter boundary, but make the HTTP implementation use canonical typed API methods. `PageArtifactDocumentPersistenceAdapter`, `PageArtifactScope`, conflict errors, and resilient fallback are good boundaries. 

Concrete TODOs:

1. Keep `PageArtifactDocumentPersistenceAdapter`.
2. Move HTTP calls into `yappcApi.pageArtifacts`.
3. Validate local draft policy for every save/load.
4. Make graph ingest idempotent and server-managed.
5. Add tests for offline, forbidden, conflict, invalid document, and sensitive classification.

## 6. Preview protocol rule

Use the typed preview protocol from `@ghatana/ui-builder/preview` consistently. `LivePreviewPanel` and `preview-builder.tsx` already use typed host/preview messages, sandbox profiles, viewport/theme/locale messages, and origin validation.  

Concrete TODOs:

1. Always issue preview sessions through typed API.
2. Never use wildcard target origins.
3. Test all message types.
4. Test invalid origins and invalid document payloads.
5. Add a preview session expiry/refresh path.

## 7. Plugin safety rule

Keep `ComponentPluginLoader` as the plugin boundary. It already validates manifest fields, builder version compatibility, renderer source safety, marketplace signatures, elevated permissions, network policy, storage/browser API policy, and telemetry allowlist. 

Concrete TODOs:

1. Require every plugin renderer to use runtime environment wrappers.
2. Block direct global browser APIs in plugin packages.
3. Add async escape tests.
4. Add marketplace signature integration tests.
5. Add telemetry consent enforcement tests.

---

# Release readiness scorecard

| Area                            | Score | Main blocker                                                                                                 |
| ------------------------------- | ----: | ------------------------------------------------------------------------------------------------------------ |
| Product coherence               |    68 | Strong lifecycle skeleton, but product concepts and source-of-truth model need consolidation.                |
| Full UI/UX flow                 |    65 | Routes exist, but full end-to-end flow needs E2E proof.                                                      |
| Cognitive-load reduction        |    55 | Cockpit pattern helps, but builder/import/inspector surfaces are still dense.                                |
| Phase cockpit maturity          |    62 | Shared cockpit is good; phase-specific depth is incomplete.                                                  |
| Canvas maturity                 |    60 | Phase-aware page node exists, but collaboration, large-canvas performance, and policy enforcement need work. |
| Page builder maturity           |    67 | Strong command/document foundation; monolithic implementation and inspector UX need hardening.               |
| Design-system consistency       |    45 | Mixed UI primitives and hardcoded styles remain.                                                             |
| Compiler/decompiler integration |    45 | Partial bridge; JSX nesting and round-trip fidelity are not production-grade yet.                            |
| Live preview                    |    58 | Strong protocol/security intent; session default mismatch must be fixed.                                     |
| Persistence                     |    62 | Good adapter/conflict foundation; backend atomicity and typed API integration needed.                        |
| Security/privacy                |    58 | Good modeling; enforcement must move deeper than UI acknowledgements.                                        |
| Auditability                    |    55 | Commands audit execute path; undo/redo, phase actions, persistence, generation need complete audit coverage. |
| Observability                   |    50 | Telemetry exists in command paths; full correlated product traces are incomplete.                            |
| AI/automation implicitness      |    45 | Internal lineage is useful, but “AI” terminology and client-side suggestion derivation leak through.         |
| Test confidence                 |    40 | Need mounted E2E, security, a11y, preview, persistence, and round-trip tests.                                |
| Production readiness            |    48 | Correct patterns exist but are not enforced consistently end-to-end.                                         |

---

# Recommended execution order

1. **Type-safe API migration first**: move every raw API call behind `yappcApi` and add lint enforcement.
2. **Preview session fix next**: resolve `/preview/builder` token mismatch and add preview security tests.
3. **Backend source-of-truth hardening**: tenant/workspace/project authorization, page artifact persistence, graph ingest, conflict handling.
4. **Phase contract hardening**: backend-backed phase cockpit contracts and real phase-specific actions.
5. **Design-system cleanup**: remove mixed primitives and hardcoded visual styles.
6. **Builder modularization**: split PageDesigner and enforce command-only mutations.
7. **Compiler/decompiler fidelity**: preserve JSX tree, source locations, tokens, bindings, residuals.
8. **E2E test suite**: prove the full YAPPC product loop from project creation to preview/generate/run/observe.
