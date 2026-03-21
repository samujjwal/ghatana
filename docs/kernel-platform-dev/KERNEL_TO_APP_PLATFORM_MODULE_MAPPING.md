# Kernel To AppPlatform Module Mapping

**Date**: 2026-03-19  
**Version**: 1.0  
**Status**: Working Mapping Draft  
**Purpose**: Map the canonical kernel model in `platform/java/kernel` to current AppPlatform modules in `products/app-platform/kernel`

---

## 1. Mapping Principles

This mapping follows these rules:

1. `platform/java/kernel` defines canonical runtime contracts
2. AppPlatform modules should be treated as concrete implementations or operational realizations of those contracts
3. If an AppPlatform module contains domain-specific business logic, it should be marked for extraction to a domain pack
4. If a kernel concept exists in `platform/java/kernel` but not yet in AppPlatform, it should be marked as missing/partial
5. Packaging boundary and deployment boundary are separate concerns

---

## 2. Current Canonical Kernel Inventory

### 2.1 Core runtime contracts in `platform/java/kernel`

| Canonical kernel area | Key current assets |
| --- | --- |
| Module lifecycle | `KernelModule` |
| Runtime context | `KernelContext`, `DefaultKernelContext`, `KernelTenantContext` |
| Registry/discovery | `KernelRegistry`, `KernelRegistryImpl` |
| Capabilities/dependencies | `KernelCapability`, `KernelDependency`, descriptors |
| Plugin runtime | `KernelPlugin` |
| Product/plugin convenience model | `ProductPlugin`, `PluginRegistry`, `PluginLoader`, `PluginContext` |
| Cross-product communication | `KernelInterProductBus` |
| Cross-product orchestration | `CrossProductWorkflowEngine` |
| Cross-product audit | `CrossProductAuditService` |
| Boundary enforcement | `ProductBoundaryEnforcer` |
| Infra adapters | `DataCloudKernelAdapter`, `AepKernelAdapter` |
| Implemented kernel submodules | `authentication`, `config`, `event-store`, `audit`, `observability`, `resilience`, `secrets` |

### 2.2 Current AppPlatform kernel inventory

| AppPlatform module |
| --- |
| `ai-governance` |
| `api-gateway` |
| `audit-trail` |
| `calendar-service` |
| `client-onboarding` |
| `config-engine` |
| `data-governance` |
| `deployment-abstraction` |
| `dlq-management` |
| `event-store` |
| `iam` |
| `incident-management` |
| `integration-testing` |
| `ledger-framework` |
| `observability-sdk` |
| `operator-workflows` |
| `pack-certification` |
| `platform-manifest` |
| `platform-sdk` |
| `plugin-runtime` |
| `regulator-portal` |
| `resilience-patterns` |
| `rules-engine` |
| `secrets-management` |
| `workflow-orchestration` |

---

## 3. Primary Mapping Table

| Canonical kernel capability area | `platform/java/kernel` current state | AppPlatform module(s) | Mapping assessment | Required action |
| --- | --- | --- | --- | --- |
| Identity / auth / RBAC / session | implemented module | `iam` | strong conceptual match | align contracts and vocabulary |
| Config resolution / hierarchical config / tenant config | implemented module | `config-engine` | strong conceptual match | make AppPlatform explicitly implement canonical config contracts |
| Event store / event processing baseline | implemented module | `event-store` | strong conceptual match | align capability ids and runtime contracts |
| Audit baseline | implemented module + cross-product audit | `audit-trail` | partial match | keep generic audit in kernel, move domain retention rules to policy layer |
| Observability baseline | implemented module | `observability-sdk` | strong conceptual match | align telemetry and capability contracts |
| Resilience baseline | implemented module | `resilience-patterns` | strong conceptual match | align policy and runtime hooks |
| Secrets baseline | implemented module | `secrets-management` | strong conceptual match | standardize secrets contract |
| Workflow runtime / orchestration contract | partial concept in kernel + cross-product workflow engine | `workflow-orchestration` | partial but conflicted | keep generic workflow primitives; move finance workflows out of kernel |
| Plugin runtime / dynamic loading | split across `KernelPlugin`, `ProductPlugin`, `PluginRegistry` | `plugin-runtime` | overlapping models | unify on one canonical plugin/pack runtime contract |
| API ingress / control-plane API | only implicit in kernel capability model | `api-gateway` | AppPlatform stronger | formalize canonical gateway capability contract in kernel |
| Data governance / policy / classification | only partial hooks today | `data-governance` | AppPlatform stronger | define kernel-level policy hooks and let AppPlatform implement service |
| AI governance / autonomous runtime policy | only AEP-oriented and conceptual in kernel | `ai-governance` | AppPlatform stronger | create canonical AI/agent governance capability contract |
| Deployment activation / topology / rollout | not mature in canonical kernel yet | `deployment-abstraction` | AppPlatform stronger | make deployment/activation a platform implementation concern with kernel contracts |
| DLQ / failed-work management | absent in canonical kernel | `dlq-management` | missing in kernel | add canonical failure-queue contract or event failure handling capability |
| Calendar / locale / schedule primitives | absent in canonical kernel | `calendar-service` | missing in kernel | define canonical schedule/calendar capability |
| Rules / policy execution | absent in canonical kernel | `rules-engine` | missing in kernel | define canonical rules/policy capability and execution contract |
| Pack manifest / pack metadata / activation descriptor | only partially implied by plugin/descriptors | `platform-manifest` | partial match | merge pack/product manifest thinking with canonical kernel descriptors |
| Platform SDK / developer surface | minimal in canonical kernel | `platform-sdk` | AppPlatform stronger | define canonical developer-surface capability groups |
| Pack certification / governance | absent in canonical kernel | `pack-certification` | AppPlatform stronger | keep operationally in AppPlatform with kernel contract hooks |
| Integration testing / topology testing | only basic tests in kernel | `integration-testing` | AppPlatform stronger | align shared test harnesses and contract testing |

---

## 4. AppPlatform Modules That Should Stay Generic

These are good candidates to remain AppPlatform kernel implementations, assuming they are kept domain-neutral:

| AppPlatform module | Recommended role |
| --- | --- |
| `iam` | canonical identity/auth implementation |
| `config-engine` | canonical config implementation |
| `event-store` | canonical event-store implementation |
| `audit-trail` | canonical audit implementation |
| `observability-sdk` | canonical observability implementation |
| `resilience-patterns` | canonical resilience implementation |
| `secrets-management` | canonical secrets implementation |
| `api-gateway` | canonical gateway implementation |
| `data-governance` | canonical governance implementation |
| `ai-governance` | canonical AI/agent governance implementation |
| `deployment-abstraction` | canonical deployment/activation implementation |
| `dlq-management` | canonical failure-path implementation |
| `platform-sdk` | canonical developer platform SDK |
| `platform-manifest` | canonical manifest/control metadata implementation |

---

## 5. AppPlatform Modules That Need Re-Scope Or Extraction

These should not remain in a supposedly generic kernel in their current form:

| AppPlatform module | Problem | Recommended direction |
| --- | --- | --- |
| `workflow-orchestration` | contains finance workflows in kernel | keep engine, move concrete domain workflows to domain packs |
| `operator-workflows` | includes domain-biased operational models | split into generic tenant/policy/locale control primitives plus pack-specific ops |
| `client-onboarding` | likely domain/business-process flavored, not obviously generic kernel | re-scope as shared onboarding pack or product-level module unless proven generic |
| `regulator-portal` | domain-specific regulatory surface | move to compliance/regulatory domain pack or product |
| `ledger-framework` | potentially generic engine but finance-biased naming/docs | retain if generic, rename and document as multi-domain ledger/balance engine |
| `incident-management` | may be platform-ops rather than generic kernel contract | likely keep in AppPlatform operational layer, not canonical kernel core |
| `pack-certification` | operational governance concern | keep in AppPlatform operational layer |
| `integration-testing` | operational/developer platform concern | keep in AppPlatform operational layer |

---

## 6. Missing Canonical Capability Areas

These are areas where AppPlatform is ahead of the canonical kernel and the kernel contract model needs to catch up:

| Missing canonical area | Why it matters |
| --- | --- |
| gateway capability contract | backend/API platform needs a first-class ingress/runtime contract |
| rules/policy engine contract | business policy, validation, and governance need a canonical execution interface |
| data-governance capability contract | products need one policy/data-classification abstraction |
| AI/agent governance contract | autonomous systems need formal policy and lifecycle integration |
| manifest/pack activation contract | pack/product deployment needs a formal contract beyond raw plugins |
| UI contribution contract | UI/UX development should be part of the developer platform surface |
| analytics contract surface | metric/event/data product registration should be first-class |
| deployment activation contract | single-domain and multi-domain shared-kernel deployment both need canonical activation semantics |

---

## 7. Recommended Target Mapping

The target operating model should be:

| Level | Role |
| --- | --- |
| `platform/java/kernel` | canonical contracts and runtime model |
| `products/app-platform/kernel/*` | operational implementations of those contracts |
| `products/app-platform/domain-packs/*` | domain-specific implementations and workflows |
| `products/*` | end-user product composition and UX |

---

## 8. Immediate Mapping Tasks

1. For each AppPlatform kernel module, assign one of:
   - canonical implementation
   - operational platform service
   - misplaced domain logic
2. For each canonical kernel capability, identify:
   - current implementation
   - missing implementation
   - duplicate implementation
3. Publish one normalized kernel capability catalog with stable ids.
4. Publish one normalized plugin/pack manifest model.
5. Rework `workflow-orchestration`, `operator-workflows`, and `ledger-framework` according to the target mapping above.
