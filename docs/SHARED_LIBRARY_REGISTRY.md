# Shared Library Registry

> Central registry of all shared libraries in the Ghatana monorepo.
> Updated: 2026-03-21 (post-consolidation)

## Classification

| Type | Description | Approval Required |
|------|-------------|-------------------|
| **Global Shared** | Used by 3+ products | Platform Team + Architecture Board |
| **Product-Owned Shared** | Used by 1-2 products | Product Team Lead |
| **Platform Internal** | Platform infrastructure | Platform Team |

## Global Shared Libraries

| Library | Location | Owner | Consumers | Status | Notes |
|---------|----------|-------|-----------|--------|-------|
| core | `platform/java/core` | Platform Team | AEP, YAPPC, Data-Cloud, Finance, VirtualOrg | ✅ Active | Base types, utils |
| domain | `platform/java/domain` | Platform Team | AEP, YAPPC, Data-Cloud, Finance | ✅ Active | Domain primitives |
| observability | `platform/java/observability` | Platform Team | AEP, YAPPC, Data-Cloud, Finance | ✅ Active | Micrometer + OTel |
| security | `platform/java/security` | Platform Team | AEP, YAPPC, VirtualOrg | ✅ Active | Auth, RBAC |
| database | `platform/java/database` | Platform Team | AEP, YAPPC, Data-Cloud, Finance | ✅ Active | DB abstractions |
| event-cloud | `platform/java/event-cloud` | Platform Team | AEP, YAPPC, Data-Cloud | ✅ Active | Event streaming |
| contracts | `platform/contracts` | Platform Team | AEP, YAPPC, Data-Cloud | ✅ Active | Protobuf + OpenAPI |
| testing | `platform/java/testing` | Platform Team | All products | ✅ Active | EventloopTestBase, fixtures |
| agent-core | `platform/java/agent-core` | Platform Team | AEP, YAPPC, Data-Cloud, Finance, TutorPutor, VirtualOrg, SoftwareOrg (7 products) | ✅ Active | TypedAgent, CatalogRegistry. Verified 2026-03-21 |
| agent-runtime | `platform/java/agent-runtime` | Platform Team | AEP, YAPPC, Data-Cloud, Finance, TutorPutor, VirtualOrg, SoftwareOrg (7 products) | ✅ Active | **Merged** from agent-memory + agent-learning + agent-dispatch + agent-resilience (2026-03-21) |
| agent-registry | `platform/java/agent-registry` | Platform Team | AEP, YAPPC | ✅ Active | Model registry, AB testing |
| ai-integration | `platform/java/ai-integration` | Platform Team | AEP, Data-Cloud, DCMAAR, TutorPutor, VirtualOrg, YAPPC (6 products) | ✅ Active | **Merged** registry + observability + feature-store submodules (2026-03-21). Genuinely cross-product — stays in platform |
| ai-experimental | `platform/java/ai-experimental` | Platform Team | ai-integration (internal) | ⚠️ Merge candidate | 6 files; exposed via ai-integration API dep only. No direct product consumers. See ADR-014 |
| plugin | `platform/java/plugin` | Platform Team | AEP, Finance (3 modules), App-Platform, Data-Cloud, YAPPC (5 modules) — 7+ products | ✅ Active | Plugin registry; widely used |
| workflow | `platform/java/workflow` | Platform Team | YAPPC, VirtualOrg, App-Platform (3 products) | ✅ Active | Workflow execution; genuinely cross-product — stays in platform |
| yaml-template | `platform/java/yaml-template` | Platform Team | agent-core, YAPPC (services/lifecycle, backend/api) | ✅ Active | YAML template engine used by agent scaffolding |
| kernel | `platform/java/kernel` | Platform Team | Finance, PHR, App-Platform | ✅ Active | Kernel abstractions, lifecycle |
| kernel-capabilities | `platform/java/kernel-capabilities` | Platform Team | Finance, PHR | ✅ Active | **Merged** from 7 kernel/modules/* (auth, config, event-store, audit, resilience, observability, secrets) 2026-03-21 |
| canvas | `platform/typescript/capabilities/canvas-core` | Data-Cloud + YAPPC | YAPPC (3 packages), Data-Cloud | ✅ Active | Package: `@ghatana/canvas`. See `docs/platform-libraries/LIBRARY_canvas.md`. BDY-2 clarified ownership (2026-03-21) |

## Product-Owned Shared Libraries

| Library | Location | Owner | Consumers | Status |
|---------|----------|-------|-----------|--------|
| @tutorputor/contracts | `products/tutorputor/contracts` | TutorPutor Team | All TutorPutor packages | ✅ Active |
| @tutorputor/db | `products/tutorputor/libs/tutorputor-db` | TutorPutor Team | TutorPutor services | ✅ Active |
| @tutorputor/ui-shared | `products/tutorputor/libs/tutorputor-ui-shared` | TutorPutor Team | TutorPutor apps | ✅ Active |
| @tutorputor/testing | `products/tutorputor/libs/testing` | TutorPutor Team | TutorPutor tests | ✅ Active |

## Deprecated / Deleted Libraries

| Library | Location | Replacement | Deleted Date |
|---------|----------|-------------|--------------|
| agent-memory | `platform/java/agent-memory` | `agent-runtime` | 2026-03-21 |
| agent-learning | `platform/java/agent-learning` | `agent-runtime` | 2026-03-21 |
| agent-dispatch | `platform/java/agent-dispatch` | `agent-runtime` | 2026-03-21 |
| agent-resilience | `platform/java/agent-resilience` | `agent-runtime` | 2026-03-21 |
| ai-integration:registry | `platform/java/ai-integration/registry` | `ai-integration` (parent) | 2026-03-21 |
| ai-integration:observability | `platform/java/ai-integration/observability` | `ai-integration` (parent) | 2026-03-21 |
| ai-integration:feature-store | `platform/java/ai-integration/feature-store` | `ai-integration` (parent) | 2026-03-21 |
| kernel:modules:authentication | `platform/java/kernel/modules/authentication` | `kernel-capabilities` | 2026-03-21 |
| kernel:modules:config | `platform/java/kernel/modules/config` | `kernel-capabilities` | 2026-03-21 |
| kernel:modules:event-store | `platform/java/kernel/modules/event-store` | `kernel-capabilities` | 2026-03-21 |
| kernel:modules:audit | `platform/java/kernel/modules/audit` | `kernel-capabilities` | 2026-03-21 |
| kernel:modules:resilience | `platform/java/kernel/modules/resilience` | `kernel-capabilities` | 2026-03-21 |
| kernel:modules:observability | `platform/java/kernel/modules/observability` | `kernel-capabilities` | 2026-03-21 |
| kernel:modules:secrets | `platform/java/kernel/modules/secrets` | `kernel-capabilities` | 2026-03-21 |
| app-platform | `products/app-platform/` | YAPPC + Finance | ❌ DEPRECATED (not yet deleted) |
| shared:metrics | (deleted) | `libs:observability` | 2026-01 |
| shared:exception | (deleted) | `libs:common-utils` | 2026-01 |
| shared:test-utils | (deleted) | `libs:activej-test-utils` | 2026-01 |

## Shared Services Registry

| Service | Location | Owner | Decision | Status |
|---------|----------|-------|----------|--------|
| auth-gateway | `shared-services/auth-gateway` | Security Team | ADR-013: Keep + develop | ✅ Active |
| user-profile-service | `shared-services/user-profile-service` | Platform Team | ADR-013: Keep + develop | ⚠️ Early dev |
| auth-service | `shared-services/auth-service` | — | ADR-013: **DELETE** — consolidate into auth-gateway | ❌ To delete |
| ai-registry | `shared-services/ai-registry` | — | ADR-013: **DELETE** — merge into `platform/java/ai-integration` | ❌ To delete |
| ai-inference-service | `shared-services/ai-inference-service` | Platform Team | ADR-013: Fix build or delete by 2026-04-30 | ⚠️ Build broken |
| feature-store-ingest | `shared-services/feature-store-ingest` | Data-Cloud | ADR-013: Move to `products/data-cloud/` | ⚠️ To move |

## Governance Rules

1. **Max global shared libraries:** Target <20 (currently 15 active)
2. **New global shared library:** Requires Architecture Board approval via `docs/MODULE_ADMISSION_CHECKLIST.md`
3. **Quarterly audit:** Review unused/underused shared libraries — see `docs/QUARTERLY_BOUNDARY_AUDIT_CHECKLIST.md`
4. **Ownership transfer:** Documented in PR with both teams' approval
5. **Cross-product deps:** Tracked via `scripts/check-cross-product-deps.sh` — all new cross-product deps require approval
