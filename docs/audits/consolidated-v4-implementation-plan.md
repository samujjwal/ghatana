# Consolidated V4 Implementation Plan

## Scope

- products: `data-cloud`, `audio-video`, `security-gateway`, `aep`, `yappc`, `tutorputor`
- shared layers: `platform`, `libs`, `shared-services`
- intent: convert the audit findings into a day-by-day execution plan with concrete file targets, test additions, and cleanup work

## Execution Principles

- fix correctness and security before new feature work
- eliminate duplicate contracts and helper code before extending surfaces
- prefer moving code to canonical shared modules instead of adding more product-local wrappers
- pair every meaningful logic change with tests at the closest useful level
- do not keep backward-compatibility shims unless they are explicitly required for rollout

## Day 1

- objective: lock down shared auth and security contract ownership
- primary files:
  - `shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthService.java`
  - `shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthGatewayLauncher.java`
  - `products/security-gateway/src/main/java/com/ghatana/security/service/impl/AuthorizationServiceImpl.java`
  - `products/security-gateway/README.md`
  - `docs/SHARED_SERVICES_STRATEGY.md`
  - `docs/audits/shared-services-runtime-v4-audit.md`
- implementation detail:
  - define exact responsibility split between `auth-gateway` and `security-gateway`
  - document canonical token issuance and validation path
  - identify any product-local JWT providers to route back to `platform/java/security`
- test files:
  - add or expand auth compatibility tests in `shared-services/auth-gateway/src/test/java/**`
  - add integration tests in `products/security-gateway/src/test/java/**` for cross-service token validation
- exit criteria: one documented shared auth runtime contract and a failing test added for any overlap not yet removed

## Day 2

- objective: eliminate platform-level auth and utility duplication
- primary files:
  - `platform/java/security/src/main/java/com/ghatana/platform/security/port/JwtTokenProvider.java`
  - `platform/typescript/utils/**`
  - `platform/typescript/theme/**`
  - `libs/typescript/yappc/**`
  - `eslint-rules/**`
- implementation detail:
  - remove duplicate `cn()`, ErrorBoundary, theme, and WebSocket client copies by updating imports to canonical packages
  - add or strengthen lint rules to block future duplication
- test files:
  - TypeScript package tests under `platform/typescript/**/__tests__`
  - architecture rules in lint and validation scripts
- exit criteria: canonical package usage enforced by lint for targeted duplicated helpers

## Day 3

- objective: harden shared-services posture and remove historical ambiguity
- primary files:
  - `shared-services/README.md`
  - `shared-services/ai-inference-service/README.md`
  - `shared-services/user-profile-service/OWNER.md`
  - `shared-services/build.gradle.kts`
  - any historical `shared-services/feature-store-ingest/**` residue that still influences docs or build metadata
- implementation detail:
  - decide keep-or-retire path for `ai-inference-service`
  - confirm `feature-store-ingest` is only owned from Data-Cloud
  - verify every live service has `OWNER.md` and explicit operational contract
- test files:
  - add smoke tests or health tests for retained live services
- exit criteria: no ambiguity in shared-services ownership or runtime purpose

## Day 4

- objective: break up Data-Cloud launcher concentration and narrow storage contracts
- primary files:
  - `products/data-cloud/platform-launcher/build.gradle.kts`
  - `products/data-cloud/feature-store-ingest/build.gradle.kts`
  - `products/data-cloud/agent-registry/src/main/java/**/DataCloudAgentRegistry.java`
  - `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/**`
  - `products/data-cloud/spi/**`
- implementation detail:
  - extract warm-tier storage interface used by feature-store ingest
  - introduce a narrow registry-store port to remove launcher implementation coupling
  - group handlers by domain rather than by incremental feature additions
- test files:
  - storage and registry integration tests under `products/data-cloud/**/src/test/java/**`
- exit criteria: feature-store ingest no longer depends on the full launcher graph for warm-tier access

## Day 5

- objective: unify Data-Cloud client contracts and query correctness
- primary files:
  - `products/data-cloud/ui/src/api/**`
  - `products/data-cloud/sdk/**`
  - `products/data-cloud/docs/openapi.yaml`
  - `products/data-cloud/REST_API_DOCUMENTATION.md`
  - `products/data-cloud/ui/src/__tests__/**`
- implementation detail:
  - choose generated or hand-authored client as the single source of truth, then remove the parallel path
  - align UI pages to the chosen contract layer
  - add stable pagination/filter/sort integration cases
- test files:
  - UI integration tests against real backend fixtures
  - API contract drift tests
- exit criteria: one canonical Data-Cloud client surface with contract verification in CI

## Day 6

- objective: secure and unify Audio-Video protobuf and auth behavior
- primary files:
  - `products/audio-video/modules/**/src/main/proto/*.proto`
  - `products/audio-video/apps/desktop/src-tauri/proto/*.proto`
  - `products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/security/JwtServerInterceptor.java`
  - `products/audio-video/apps/desktop/src/**`
- implementation detail:
  - move shared proto models to one canonical package/module
  - regenerate desktop/client bindings from shared proto sources
  - make production startup fail when required JWT configuration is absent
- test files:
  - proto compatibility tests
  - interceptor startup/config tests
- exit criteria: desktop and services consume the same generated schema source and JWT permissive production mode is impossible

## Day 7

- objective: add Audio-Video desktop resilience and UX correctness coverage
- primary files:
  - `products/audio-video/apps/desktop/src/App.tsx`
  - `products/audio-video/apps/desktop/src/components/**`
  - `products/audio-video/libs/audio-video-client/src/index.ts`
  - `products/audio-video/tests/load/**`
- implementation detail:
  - add explicit error, retry, and circuit-breaker state rendering
  - align client-side breaker defaults with server-side expectations
  - automate load-test threshold parsing in CI
- test files:
  - new React tests under `products/audio-video/apps/desktop/src/**/__tests__`
- exit criteria: major desktop panels have working state coverage for success, loading, and failure

## Day 8

- objective: simplify AEP public-edge topology and auth guarantees
- primary files:
  - `products/aep/README.md`
  - `products/aep/server/src/main/java/**`
  - `products/aep/gateway/src/**`
  - `products/aep/api/**`
  - `products/aep/contracts/**`
- implementation detail:
  - pick `server` as the canonical backend surface or formally narrow `gateway` to BFF-only behavior
  - deprecate or rename ambiguous `api` ownership if it is not the public REST edge
  - ensure auth is enforced consistently whether requests pass through the gateway or not
- test files:
  - end-to-end auth parity tests across gateway and direct server access
- exit criteria: one documented and tested public API topology

## Day 9

- objective: harden AEP orchestrator durability and runtime invariants
- primary files:
  - `products/aep/orchestrator/src/main/java/**/PostgresqlCheckpointStore.java`
  - `products/aep/orchestrator/src/main/java/**/CheckpointAwareExecutionQueue.java`
  - `products/aep/server/src/main/java/**/HitlController.java`
  - `products/aep/aep-engine/src/main/java/**/CircuitBreakerOperator.java`
- implementation detail:
  - replace fragile in-memory review assumptions with bounded durable queueing
  - make resilience operators mandatory or centrally enforced
  - add checkpoint replay and crash-recovery invariants
- test files:
  - restart/replay integration tests under `products/aep/**/src/test/java/**`
- exit criteria: queue backpressure, restart, and replay correctness are covered by automated tests

## Day 10

- objective: remove YAPPC refactorer runtime violations and duplicate DTOs
- primary files:
  - `products/yappc/core/refactorer/**`
  - `products/yappc/libs/java/yappc-domain/src/main/java/**/CreateFindingRequest.java`
  - `products/yappc/libs/java/yappc-domain/src/main/java/**/CreateScanRequest.java`
  - duplicate request files outside the canonical domain package
- implementation detail:
  - replace raw thread and sleep behavior with Promise/event-loop patterns
  - consolidate all public DTOs into the canonical domain package
  - add missing JavaDoc and `@doc.*` tags where required
- test files:
  - refactorer async tests extending `EventloopTestBase`
- exit criteria: no raw-thread refactorer execution remains and duplicate DTOs are removed

## Day 11

- objective: complete YAPPC domain contracts and strengthen error handling
- primary files:
  - `products/yappc/libs/java/yappc-domain/src/main/java/**/model/**`
  - `products/yappc/core/ai/src/main/java/**`
  - `products/yappc/core/scaffold/**`
  - `products/yappc/frontend/**`
- implementation detail:
  - complete placeholder domain models with validation and ownership
  - replace broad exception handling with typed domain and infrastructure errors
  - add richer telemetry around AI/refactor/generation flows
- test files:
  - domain-model validation tests
  - generation and refactor integration tests
- exit criteria: core domain types are complete and error propagation is explicit

## Day 12

- objective: build TutorPutor learner profile and mastery backbone
- primary files:
  - `products/tutorputor/libs/**/prisma/schema.prisma`
  - `products/tutorputor/services/tutorputor-platform/src/modules/learning/**`
  - `products/tutorputor/contracts/**`
  - `products/tutorputor/apps/**`
- implementation detail:
  - add `LearnerProfile`, `LearnerPreferences`, and `MasteryRecord` persistence
  - wire real learner state into recommendation and progress services
  - update contracts and UI calls to use persisted learner state
- test files:
  - schema migration tests
  - learning module unit and integration tests
- exit criteria: adaptive decisions are backed by real persisted learner data

## Day 13

- objective: remove TutorPutor high-value `any` usage and consolidate repeated local patterns
- primary files:
  - `products/tutorputor/services/tutorputor-platform/src/modules/**`
  - `products/tutorputor/libs/**`
  - repeated pagination, tenant-validator, error, and AI-client setup files
- implementation detail:
  - eliminate `any` in API, learning, assessment, content generation, and tenancy-critical files first
  - centralize pagination, tenant validation, error types, and AI client creation
- test files:
  - type-check, lint, and regression tests for refactored modules
- exit criteria: critical TutorPutor modules compile without `any` escape hatches and repeated patterns are centralized

## Day 14

- objective: harden TutorPutor security and adaptive correctness
- primary files:
  - `products/tutorputor/services/tutorputor-lti/src/routes/launch.ts`
  - `products/tutorputor/services/tutorputor-platform/src/modules/assessment/**`
  - `products/tutorputor/tests/**`
  - `products/tutorputor/apps/tutorputor-student/**`
  - `products/tutorputor/apps/tutorputor-admin/**`
- implementation detail:
  - add strict LTI signature validation
  - expand assessment scoring and mastery-edge tests
  - add learner and educator E2E tests for assessment and recommendation flows
- exit criteria: LTI launch is cryptographically validated and adaptive assessment flows are covered by tests

## Day 15

- objective: repo-wide tenant isolation and policy consistency pass
- primary files:
  - `products/data-cloud/**/repository or query files`
  - `products/aep/**/query and controller files`
  - `products/yappc/**/repository and service files`
  - `products/tutorputor/**/tenant-aware modules`
  - `platform/java/security/**`
- implementation detail:
  - add or verify tenant-scoped query constraints, row-level checks where appropriate, and policy enforcement consistency
  - document any required DB constraints or migration changes
- test files:
  - multi-tenant integration tests across products
- exit criteria: each product has at least one automated tenant isolation proof on critical data paths

## Day 16

- objective: repo-wide observability and alerting normalization
- primary files:
  - `platform/java/observability/**`
  - `shared-services/infrastructure/monitoring/**`
  - product launcher/server startup classes in each audited product
- implementation detail:
  - standardize metric names, span tags, correlation ID propagation, and dashboard expectations across critical workflows
  - ensure business metrics exist for auth, event ingestion, pipeline execution, media processing, and adaptive learning
- test files:
  - startup/observability smoke tests where practical
- exit criteria: every audited product has a documented critical-path telemetry set

## Day 17

- objective: deployment and runtime hardening pass
- primary files:
  - `products/data-cloud/helm/**`, `products/data-cloud/k8s/**`, `products/data-cloud/terraform/**`
  - `products/audio-video/infra/**`
  - `products/aep/helm/**`, `products/aep/k8s/**`
  - `products/yappc/deployment/**`
  - `products/tutorputor/monitoring/**`, `products/tutorputor/ci/**`
- implementation detail:
  - add or verify secret-management posture, network policies, readiness/liveness, and rollout/rollback guidance
  - delete duplicate infra definitions where one canonical path exists
- exit criteria: each product has one clear deployment path and no known unsafe defaults remain in manifests

## Day 18

- objective: deletion and cleanup day
- primary files:
  - stale duplicate DTOs, utilities, protos, controllers, and historical service residue identified in the audits
  - update `docs/audits/*.md` to reflect completed cleanup
- implementation detail:
  - remove dead code, deprecated wrappers, and redundant docs
  - run targeted build, test, lint, and typecheck for touched products
- exit criteria: the highest-priority duplicate and dead-code findings are removed, and the remaining backlog is smaller and clearly documented

## Cross-Cutting Test Matrix

- Data-Cloud: contract drift, query semantics, event ingestion to UI visibility, registry/storage decoupling
- Audio-Video: proto sync, desktop error states, client/server breaker parity, JWT startup validation
- Security-Gateway: dynamic policy changes, key rotation windows, WAF plus IP blocking, auth-gateway compatibility
- AEP: gateway/server auth parity, checkpoint replay, deployment rollback, durable HITL review flow
- YAPPC: refactorer async behavior, canonical DTO imports, generation validation, refactor failure handling
- TutorPutor: learner profile persistence, assessment scoring, LTI security, recommendation correctness, content generation failover

## Acceptance Criteria for the Full Plan

- every P0/P1 blocker called out in the audits is either fixed or reduced to a bounded, documented rollout risk
- duplicated contract surfaces are removed where canonical shared or product-local shared modules already exist
- each audited product has at least one end-to-end or integration suite covering its most critical workflow
- each audited product has a documented critical-path observability checklist
- deployment paths no longer rely on silent permissive defaults for auth, secrets, or tenancy

## Sequencing Notes

- do not start broad feature additions during this plan
- complete Days 1 through 5 before expanding product-specific scope, because shared auth, contract, and platform cleanup reduce downstream rework
- complete Days 6 through 14 in parallel by product only after shared guardrails are in place
- reserve Days 15 through 18 for repo-wide consistency, telemetry, and deletion cleanup
