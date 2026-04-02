# Module Coverage Backlog

Date: 2026-04-02

Scope:
- `products/audio-video`
- `products/data-cloud`
- `products/aep`
- `products/yappc`

Purpose:
- Extend the strict audit into a leaf-by-leaf execution backlog
- Cover every buildable module/package discovered in scope
- Prioritize where coverage work should happen first

How to read this file:
- `Code Files` is a rough size signal from non-generated source files in the module.
- `Tests` is a rough detectable-test signal from in-module test files.
- `Status` is intentionally conservative:
  - `No detectable tests`
  - `Thin tests`
  - `Some tests present`
- `Priority` is execution priority for closing the strict-coverage gap, not a claim that lower-priority modules are acceptable.

Heuristic limits:
- This backlog is inventory-driven and does not claim semantic completeness from file counts alone.
- Aggregate/container entries like `.` or `frontend` are retained for ownership/build visibility, but leaf modules should drive actual implementation work.

## Execution Rules

- Treat every `No detectable tests` row as uncovered until proven otherwise.
- Treat every `Thin tests` row as high risk if the module contains business logic, contracts, queries, orchestration, auth, state management, or externally visible flows.
- Require every module to map its requirements to unit, integration, and API/E2E layers where applicable.

## audio-video

| Module | Languages | Code Files | Tests | Status | Priority | Backlog Focus |
|---|---|---:|---:|---|---|---|
| `.` | java, ts/js, rust, python | 210 | 62 | Some tests present | P0 | Create a product-level requirement map and aggregate coverage gate across Java, TS, Rust, and Python surfaces. |
| `apps/desktop` | ts/js, rust | 21 | 1 | Thin tests | P2 | Add UI journey coverage for panel routing, settings, service health, error banners, and result rendering. |
| `apps/desktop/src-tauri` | rust | 5 | 0 | No detectable tests | P1 | Add Rust command, validation, and error-mapping tests for the desktop bridge. |
| `libs/audio-video-client` | ts/js | 1 | 0 | No detectable tests | P1 | Add request/response, timeout, retry, and transport-failure contract tests. |
| `libs/audio-video-types` | ts/js | 3 | 0 | No detectable tests | P1 | Add schema/contract compatibility tests against service payloads and generated interfaces. |
| `libs/audio-video-ui` | ts/js | 8 | 2 | Thin tests | P2 | Expand component and hook behavior tests for loading, success, error, and disabled states. |
| `libs/common` | java | 13 | 12 | Some tests present | P2 | Harden invariants around interceptors, metrics, auth, and proto compatibility. |
| `modules/intelligence/ai-voice/apps/desktop` | ts/js, rust, python | 102 | 12 | Some tests present | P0 | Add end-to-end coverage for cloning, synthesis, model flow, storage, and failure handling. |
| `modules/intelligence/ai-voice/apps/desktop/src-tauri` | rust, python | 41 | 0 | No detectable tests | P0 | Add bridge, orchestration, subprocess/model integration, and failure-path tests. |
| `modules/intelligence/ai-voice/libs/ai-voice-ui-react` | ts/js | 10 | 0 | No detectable tests | P1 | Add feature-level UI tests for model selection, controls, validation, and user feedback. |
| `modules/intelligence/multimodal-service` | java | 20 | 4 | Some tests present | P2 | Add fusion-rule, partial-input, and invalid-input behavior tests. |
| `modules/intelligence/speech/libs/speech-audio-rust` | rust | 1 | 0 | No detectable tests | P1 | Add minimal Rust coverage for audio helper correctness and boundary handling. |
| `modules/speech/stt-service` | java | 2 | 3 | Some tests present | P2 | Verify transcription failure paths, invalid audio handling, and contract semantics. |
| `modules/speech/tts-service` | java | 14 | 3 | Some tests present | P2 | Add speech synthesis parameter, invalid input, and output integrity checks. |
| `modules/vision/vision-service` | java | 11 | 10 | Some tests present | P2 | Strengthen detection math, thresholding, and invalid image behavior assertions. |

## data-cloud

| Module | Languages | Code Files | Tests | Status | Priority | Backlog Focus |
|---|---|---:|---:|---|---|---|
| `agent-registry` | java | 3 | 4 | Some tests present | P2 | Add full CRUD, tenant isolation, and stale-read behavior validation. |
| `feature-store-ingest` | java | 5 | 6 | Some tests present | P2 | Add malformed-event, duplicate-event, out-of-order, and downstream persistence failure tests. |
| `launcher` | java | 46 | 58 | Some tests present | P1 | Add externally visible API and bootstrapping behavior checks with persistence side effects. |
| `platform-analytics` | java | 12 | 8 | Some tests present | P2 | Add computation-correctness and edge-case analytics tests. |
| `platform-config` | java | 62 | 2 | Thin tests | P1 | Add config parsing, invalid config rejection, defaults, and override precedence tests. |
| `platform-entity` | java | 137 | 8 | Thin tests | P0 | Add validation, schema diff, pagination, entity rule, and query-model correctness tests. |
| `platform-event` | java | 27 | 4 | Some tests present | P2 | Add append semantics, ordering, tenant partitioning, and delivery expectation tests. |
| `platform-launcher` | java | 333 | 117 | Some tests present | P0 | Raise from broad but insufficient coverage to full behavior coverage across storage, APIs, queries, and plugins. |
| `spi` | java | 30 | 4 | Some tests present | P1 | Add contract/invariant tests for extension points and capability interfaces. |
| `ui` | ts/js | 283 | 42 | Some tests present | P0 | Add full UI flow coverage for query builder, storage admin, loading/error states, and contract fidelity. |

## aep

| Module | Languages | Code Files | Tests | Status | Priority | Backlog Focus |
|---|---|---:|---:|---|---|---|
| `aep-agent-runtime` | java | 157 | 12 | Thin tests | P0 | Add agent execution, turn lifecycle, error handling, and tenant isolation tests. |
| `aep-analytics` | java | 86 | 8 | Some tests present | P1 | Add forecast, defaults, and edge-case analytics coverage. |
| `aep-api` | java | 10 | 2 | Thin tests | P2 | Add endpoint contract, validation, and error response tests. |
| `aep-central-runtime` | java | 4 | 0 | No detectable tests | P1 | Add runtime wiring and orchestration tests or collapse dead surface if obsolete. |
| `aep-compliance` | java | 4 | 2 | Thin tests | P2 | Add rule enforcement and invalid state tests. |
| `aep-connectors` | java | 24 | 14 | Some tests present | P2 | Restore connector config and DLT path coverage where currently excluded or stale. |
| `aep-engine` | java | 177 | 55 | Some tests present | P0 | Add missing routing, operator selection, invalid pipeline, and side-effect correctness tests. |
| `aep-event-cloud` | java | 14 | 12 | Some tests present | P2 | Strengthen event-cloud interaction and failure-mode coverage. |
| `aep-identity` | java | 2 | 2 | Thin tests | P2 | Add auth edge cases and tenant/security invariants. |
| `aep-operator-contracts` | java | 37 | 6 | Some tests present | P1 | Add contract and compatibility tests for operator SPI and event abstractions. |
| `aep-registry` | java | 85 | 5 | Thin tests | P1 | Add pipeline registry CRUD, migration, and consistency checks. |
| `aep-runtime-core` | n/a | 0 | 89 | Some tests present | P2 | Repair excluded tests and validate this suite still maps to live production behaviors. |
| `aep-scaling` | java | 15 | 2 | Thin tests | P2 | Add valid/invalid transition, rebalance, and rollback behavior tests. |
| `aep-security` | java | 4 | 2 | Thin tests | P2 | Add auth failure, secret-management, and filter semantics coverage. |
| `gateway` | ts/js | 5 | 2 | Thin tests | P2 | Add JWT, CORS, websocket forwarding, and timeout propagation tests. |
| `orchestrator` | java | 60 | 34 | Some tests present | P1 | Add cross-module orchestration flows and failure handling. |
| `server` | java | 42 | 68 | Some tests present | P1 | Add boot, routing, and infra-failure coverage against externally visible contracts. |
| `ui` | ts/js | 70 | 10 | Some tests present | P1 | Add full operator/pipeline management UI journey coverage. |

## yappc

| Module | Languages | Code Files | Tests | Status | Priority | Backlog Focus |
|---|---|---:|---:|---|---|---|
| `.` | java, ts/js, python | 4931 | 1425 | Some tests present | P0 | Add product-level traceability and unified coverage gating across backend and frontend. |
| `core/agents` | java | 474 | 234 | Some tests present | P0 | Map agent responsibilities to behavior coverage and close remaining orchestration gaps. |
| `core/agents/architecture-specialists` | java | 62 | 17 | Some tests present | P1 | Add specialist rule and output-contract coverage. |
| `core/agents/code-specialists` | java | 132 | 2 | Thin tests | P0 | Add core generation/editing behavior, failure, and constraint tests. |
| `core/agents/common` | java | 2 | 0 | No detectable tests | P1 | Add shared agent utility and contract coverage. |
| `core/agents/delivery-specialists` | java | 63 | 0 | No detectable tests | P0 | Add end-to-end delivery workflow and recommendation logic tests. |
| `core/agents/runtime` | java | 62 | 59 | Some tests present | P1 | Add remaining lifecycle and failure-path coverage. |
| `core/agents/testing-specialists` | java | 69 | 53 | Some tests present | P1 | Add behavior correctness tests for audit/coverage agent outputs. |
| `core/agents/workflow` | java | 61 | 84 | Some tests present | P1 | Add invalid workflow transition and rollback tests. |
| `core/ai` | java | 125 | 47 | Some tests present | P0 | Add prompt-building, suggestion, ranking, and safety/constraint validation. |
| `core/cli-tools` | java | 6 | 1 | Thin tests | P2 | Add CLI argument and error-path tests. |
| `core/knowledge-graph` | java | 10 | 2 | Thin tests | P2 | Add graph query, traversal, and invariant tests. |
| `core/refactorer/api` | java | 115 | 110 | Some tests present | P0 | Audit for requirement completeness across auth, KG queries, and API-side orchestration. |
| `core/refactorer/engine` | java, ts/js, python | 116 | 125 | Some tests present | P0 | Add cross-language refactor correctness and tool-failure handling tests. |
| `core/scaffold` | java | 326 | 92 | Some tests present | P0 | Add framework matrix, generation invariants, and invalid template path coverage. |
| `core/scaffold/api` | java | 70 | 16 | Some tests present | P1 | Add API validation, contract, and persistence-effect coverage. |
| `core/scaffold/core` | java | 6 | 53 | Some tests present | P2 | Confirm tests assert behavior rather than only fixture execution. |
| `core/scaffold/core/src/test/resources/packs` | java | 6 | 5 | Some tests present | P2 | Add template-pack invariant and golden-output validation. |
| `core/scaffold/engine` | java | 43 | 2 | Thin tests | P1 | Add generation branch, error, and edge-case coverage. |
| `core/scaffold/generators` | java | 142 | 15 | Some tests present | P0 | Add generator-specific golden tests across all supported targets. |
| `core/scaffold/templates` | java | 65 | 5 | Thin tests | P1 | Add template rendering, variable substitution, and invalid-input tests. |
| `core/services-lifecycle` | java | 67 | 64 | Some tests present | P1 | Add lifecycle invalid transition and rollback semantics coverage. |
| `core/services-platform` | java | 11 | 9 | Some tests present | P2 | Add service integration and persistence failure tests. |
| `core/yappc-agents` | java | 23 | 8 | Some tests present | P2 | Add coordinator behavior and failure routing tests. |
| `core/yappc-api` | java | 6 | 0 | No detectable tests | P1 | Add API contract, auth, and validation coverage. |
| `core/yappc-domain-impl` | java | 76 | 26 | Some tests present | P1 | Add domain rule and state transition coverage. |
| `core/yappc-infrastructure` | java | 28 | 14 | Some tests present | P2 | Add adapter failure and retry semantics tests. |
| `core/yappc-services` | java | 86 | 43 | Some tests present | P1 | Add service orchestration, persistence, and error contract tests. |
| `core/yappc-shared` | java | 55 | 8 | Some tests present | P1 | Add shared invariant and serialization tests. |
| `examples/sample-build-generator-plugin` | java | 1 | 2 | Thin tests | P2 | Add plugin compatibility smoke checks. |
| `frontend` | java, ts/js, python | 3276 | 514 | Some tests present | P0 | Add unified frontend requirement map and close uncovered library surfaces. |
| `frontend/apps/api` | ts/js, python | 169 | 30 | Some tests present | P0 | Add API proxy contract, auth, and failure-mode tests. |
| `frontend/apps/web` | ts/js | 5 | 0 | No detectable tests | P1 | Add application-shell routing, bootstrap, and integration tests. |
| `frontend/docs-site` | ts/js | 5 | 0 | No detectable tests | P1 | Add docs build/link integrity and critical page smoke checks. |
| `frontend/eslint-local-rules` | ts/js | 8 | 0 | No detectable tests | P1 | Add rule behavior and autofix tests. |
| `frontend/libs/api` | ts/js | 23 | 0 | No detectable tests | P1 | Add request construction, error mapping, and response contract tests. |
| `frontend/libs/auth` | ts/js | 11 | 0 | No detectable tests | P1 | Add login/session/guard/expiry behavior tests. |
| `frontend/libs/chat` | ts/js | 4 | 0 | No detectable tests | P1 | Add chat state and message-flow tests. |
| `frontend/libs/code-editor` | ts/js | 17 | 0 | No detectable tests | P1 | Add editing, validation, and save/export interaction tests. |
| `frontend/libs/collab` | ts/js | 18 | 0 | No detectable tests | P1 | Add presence, reconnect, conflict, and selection sync tests. |
| `frontend/libs/config` | ts/js | 5 | 0 | No detectable tests | P1 | Add config parsing, defaults, and invalid-state tests. |
| `frontend/libs/ide` | ts/js | 71 | 6 | Thin tests | P1 | Add workspace, panel, and editing workflow coverage. |
| `frontend/libs/shortcuts` | ts/js | 8 | 0 | No detectable tests | P1 | Add keybinding registration and conflict tests. |
| `frontend/libs/testing` | ts/js | 26 | 1 | Thin tests | P2 | Add harness correctness tests to avoid false confidence from broken helpers. |
| `frontend/libs/yappc-ai` | ts/js | 110 | 9 | Thin tests | P0 | Add AI interaction, result mapping, error, and cancellation behavior coverage. |
| `frontend/libs/yappc-canvas` | ts/js | 549 | 160 | Some tests present | P0 | Add remaining flow/state-transition coverage for canvas editing and export behavior. |
| `frontend/libs/yappc-core` | ts/js | 24 | 0 | No detectable tests | P1 | Add shared state/model contract coverage. |
| `frontend/libs/yappc-state` | ts/js | 62 | 7 | Some tests present | P1 | Add store transition, derived state, and persistence tests. |
| `frontend/libs/yappc-ui` | ts/js | 896 | 68 | Thin tests | P0 | Add component behavior coverage across critical user journeys and state variants. |
| `frontend/packages/eslint-config-custom` | ts/js | 3 | 0 | No detectable tests | P1 | Add config smoke tests against representative files. |
| `frontend/packages/vite-plugin-live-edit` | ts/js | 3 | 0 | No detectable tests | P1 | Add plugin hook and live-edit behavior tests. |
| `frontend/scripts` | ts/js | 51 | 6 | Some tests present | P1 | Add script argument and failure-path validation. |
| `frontend/web` | ts/js, python | 1063 | 140 | Some tests present | P0 | Add full web-app flow coverage across lifecycle, canvas, export, auth, and collaboration. |
| `infrastructure/datacloud` | java | 34 | 16 | Some tests present | P1 | Add adapter contract, tenant isolation, and failure recovery tests. |
| `libs/java/yappc-domain` | java | 61 | 41 | Some tests present | P1 | Add domain invariant and serialization edge-case tests. |
| `platform` | java, ts/js | 11 | 8 | Some tests present | P2 | Add cross-layer contract and utility invariant tests. |
| `tools/live-preview-server` | ts/js | 2 | 0 | No detectable tests | P1 | Add server startup, websocket/live-preview, and error-path tests. |
| `tools/validation-tests` | n/a | 0 | 2 | Thin tests | P2 | Confirm tool-only validation coverage still reflects real product expectations. |
| `tools/vscode-extension` | ts/js | 6 | 2 | Thin tests | P2 | Add extension activation, command, and editor-integration tests. |

## Recommended First Wave

If we want the fastest path to reducing the biggest blind spots, start here:

1. `products/audio-video/modules/intelligence/ai-voice/apps/desktop/src-tauri`
2. `products/audio-video/apps/desktop`
3. `products/data-cloud/platform-launcher`
4. `products/data-cloud/platform-entity`
5. `products/data-cloud/ui`
6. `products/aep/aep-agent-runtime`
7. `products/aep/aep-engine`
8. `products/aep/aep-registry`
9. `products/yappc/core/agents/code-specialists`
10. `products/yappc/core/agents/delivery-specialists`
11. `products/yappc/core/scaffold/generators`
12. `products/yappc/frontend/libs/yappc-ui`
13. `products/yappc/frontend/libs/yappc-ai`
14. `products/yappc/frontend/libs/api`
15. `products/yappc/frontend/libs/auth`
16. `products/yappc/frontend/libs/collab`
17. `products/yappc/frontend/web`

## Closing Rule

This backlog should be considered complete only when each row has:
- requirement mapping
- unit coverage
- integration coverage where boundaries exist
- API/E2E coverage where externally visible behavior exists
- explicit failure-path coverage
- structural coverage evidence, not just assumptions
