# Java Libraries and Modules Audit Report

## 1. Executive Summary
- Overall health: acceptable only with focused cleanup; architecture is productive but fragmented in multiple high-impact areas.
- Main risks:
  - Duplicate source-of-truth contracts and validators across platform and product modules.
  - Deprecated modules still containing live Java code and tests.
  - Domain modules (notably Finance domains) carrying infra/transport dependencies directly.
  - Event/messaging concerns split across multiple owners without one canonical contract model.
  - Existing test failure backlog across critical shared modules.
- Main strengths:
  - Clear Gradle include graph in settings.gradle.kts.
  - Strong test presence in core shared modules (core, domain, messaging, kernel, security, agent-core).
  - Platform libraries exist for most cross-cutting concerns (core, config, database, security, observability, messaging, governance).
- Source-of-truth risks:
  - Byte-identical canonical contracts duplicated in separate modules.
  - Multiple validators with same functional purpose in platform and product scopes.
- Shared abstraction quality:
  - Mixed. Some shared modules are strong foundations, but some abstractions are duplicated or redefined by consumers.
- Dependency/layering risks:
  - Finance domain modules depend on HTTP, DB, migration, Kafka, Redis, and runtime primitives directly.
  - Package ownership drift (kernel module publishing com.ghatana.platform.* types).
- Release/maintainability concerns:
  - Current failure backlog in shared Java modules increases regression and release risk.
- Top priority actions:
  - Eliminate duplicate canonical contract classes.
  - Complete deletion or re-activation decision for deprecated modules.
  - Re-layer Finance domains to isolate infra adapters from domain APIs.
  - Define one canonical event contract ownership model and map all event modules to it.

## 2. Full Module Inventory
Audit inventory scope: Java modules with src/main/java and/or src/test/java under included Gradle modules, plus source-bearing modules present in repo but not included.

Legend:
- Canonical ownership: concern this module should own by design.
- Verdict: keep / merge / split / rename / remove / refactor.

### Platform Kernel and Plugins
| Name | Path | Purpose | Primary Consumers | Major Dependencies | Canonical Ownership | Overlap Notes | Verdict |
|---|---|---|---|---|---|---|---|
| platform-kernel:kernel-core | platform-kernel/kernel-core | Kernel runtime core, contracts, registry | kernel-plugin, product runtimes | platform core contracts, security, observability | Kernel lifecycle and scope/runtime contracts | Publishes com.ghatana.platform.* classes also present in platform/java/core | refactor |
| platform-kernel:kernel-plugin | platform-kernel/kernel-plugin | Plugin SPI and lifecycle | platform plugins, product plugins | kernel-core | Plugin SPI | Overlaps with kernel-core plugin abstractions | refactor |
| platform-kernel:kernel-persistence | platform-kernel/kernel-persistence | Kernel persistence support | kernel-core | database/runtime | Kernel persistence abstractions | Low overlap observed | keep |
| platform-kernel:kernel-testing | platform-kernel/kernel-testing | Kernel test harness | kernel modules/tests | junit/mockito | Kernel testing utilities | Low overlap observed | keep |
| platform-plugins:* (audit-trail/billing-ledger/compliance/consent/fraud-detection/risk-management) | platform-plugins/* | Cross-platform plugin implementations | kernel/plugin consumers | kernel-plugin, platform libs | Plugin feature behavior | Reasonable composition via plugin SPI | keep |

### Platform Java Shared Libraries
| Name | Path | Purpose | Primary Consumers | Major Dependencies | Canonical Ownership | Overlap Notes | Verdict |
|---|---|---|---|---|---|---|---|
| platform:contracts | platform/contracts | Shared contract tooling/schema generation | platform/product build flows | protobuf/json tooling | Contract generation utilities | No major overlap found | keep |
| platform:java:core | platform/java/core | Foundational types (validation, health, client, errors, utils) | almost all Java modules | slf4j/jackson/activej | Canonical core contracts and primitives | Duplicate classes found in kernel-core | refactor |
| platform:java:domain | platform/java/domain | Shared domain models/validators | aep, agents, platform services | core/runtime | Platform domain contracts | Validator overlap with product modules | refactor |
| platform:java:database | platform/java/database | DB, tx, migration, cache/pubsub integrations | product domains and services | postgres/flyway/jedis | Shared DB abstractions | Some consumers bypass boundaries with direct DB deps | refactor |
| platform:java:http | platform/java/http | HTTP wrappers and adapters | finance domains, products | activej/http libs | Shared HTTP primitives | Pulled directly into domain modules | refactor |
| platform:java:observability | platform/java/observability | Metrics, tracing, health instrumentation | platform/product services | otel/micrometer | Shared observability contracts | Broad but coherent | keep |
| platform:java:testing | platform/java/testing | Shared test utilities | platform/product tests | junit/mockito | Shared Java testing primitives | Some duplicate test utilities still local | refactor |
| platform:java:runtime | platform/java/runtime | Runtime/eventloop/async bridges | platform modules | activej | Runtime primitives | Healthy; should remain low-level | keep |
| platform:java:config | platform/java/config | Config models, validation, loading | products and shared services | core, validation libs | Canonical config SPI/contracts | Duplicated product validators | refactor |
| platform:java:workflow | platform/java/workflow | Workflow operators and orchestration primitives | products/aep | runtime/core | Workflow contracts | Some product-specific variants in products | refactor |
| platform:java:ai-integration | platform/java/ai-integration | AI gateway integration primitives | agent modules/services | runtime/http | Shared AI integration contracts | Low overlap verified | keep |
| platform:java:governance | platform/java/governance | Governance controls/rules | product governance modules | core/security | Governance shared primitives | Used directly in business/domain modules | refactor |
| platform:java:security | platform/java/security | AuthN/AuthZ/crypto/rate-limit | platform/product services | jwt/crypto libs | Shared security contracts/services | Overlap with identity and security-analytics packages | refactor |
| platform:java:agent-core | platform/java/agent-core | Canonical agent framework, specs, memory/contracts | AEP, YAPPC, products | core/runtime/security | Canonical agent contracts and runtime | Validator duplication in AEP registry | refactor |
| platform:java:messaging | platform/java/messaging | Connector strategies and messaging adapters | AEP/Data Cloud integrations | kafka/sqs/rabbit/http/s3 | Canonical connector abstractions | Event ownership split with product event modules | refactor |
| platform:java:audit | platform/java/audit | Audit abstractions | finance and services | core/security | Shared audit primitives | Some product local audit variants exist | refactor |
| platform:java:identity | platform/java/identity | Identity/auth service abstractions | services/products | security/core | Shared identity contracts | Overlap with security module behavior | refactor |
| platform:java:data-governance | platform/java/data-governance | Data policy/governance primitives | data-cloud/finance | governance/security | Shared governance policy model | Some product-level duplication | refactor |
| platform:java:tool-runtime | platform/java/tool-runtime | Tool execution governance/approval runtime | agent/platform flows | runtime/security | Tool runtime contracts | Low overlap verified | keep |
| platform:java:policy-as-code | platform/java/policy-as-code | Policy engine | products/services | governance/security | Shared policy contracts | No major duplication found | keep |
| platform:java:ds-cli | platform/java/ds-cli | design-system CLI tooling | build/tooling | cli libs | DS token tooling | Specialized but bounded | keep |

### Product Java Modules (high-impact set)
| Name | Path | Purpose | Primary Consumers | Major Dependencies | Canonical Ownership | Overlap Notes | Verdict |
|---|---|---|---|---|---|---|---|
| products:aep:contracts | products/aep/contracts | AEP contracts | AEP modules | platform core/domain | AEP-specific contracts | Mostly coherent | keep |
| products:aep:aep-operator-contracts | products/aep/aep-operator-contracts | AEP operator and event SPI contracts | AEP runtime/event-cloud | platform contracts | Operator contracts | Event contract overlap with platform messaging/data-cloud event models | refactor |
| products:aep:aep-engine | products/aep/aep-engine | AEP engine runtime | AEP API/runtime | platform libs | AEP execution behavior | Duplicated PipelineSpecValidator and config validation patterns | refactor |
| products:aep:aep-registry | products/aep/aep-registry | Agent registry | AEP API/engine | agent-core/platform core | AEP registry behavior | AgentSpecValidator duplicates platform agent-core semantics | refactor |
| products:aep:aep-event-cloud | products/aep/aep-event-cloud | AEP event cloud runtime | AEP orchestration | aep-operator-contracts/data-cloud | Event cloud orchestration | Ownership overlap with platform messaging + data-cloud platform-event | refactor |
| products:data-cloud:spi | products/data-cloud/spi | Data-cloud SPI contracts | data-cloud modules | platform libs | Data-cloud extension points | Mostly coherent | keep |
| products:data-cloud:platform-config | products/data-cloud/platform-config | Data-cloud configuration | data-cloud launcher/services | platform config/core | Data-cloud config implementation | Duplicated ConfigValidator concept | refactor |
| products:data-cloud:platform-event | products/data-cloud/platform-event | Data-cloud event storage/model | data-cloud launcher/analytics | data-cloud core/platform libs | Data-cloud event persistence model | Competes with event ownership in platform messaging/aep-event-cloud | refactor |
| products:data-cloud:platform-entity | products/data-cloud/platform-entity | Data-cloud entity model | platform-event/platform-api | platform/database | Entity contracts | Reasonably scoped | keep |
| products:data-cloud:platform-api | products/data-cloud/platform-api | Data-cloud API layer | launcher/clients | http/security | API transport contracts | Needs strict boundary checks vs domain/persistence | refactor |
| products:data-cloud:platform-client | products/data-cloud/platform-client | Data-cloud client | products/services | http/core | Client contracts | Low overlap | keep |
| products:data-cloud:platform-analytics | products/data-cloud/platform-analytics | Analytics/reporting layer | data-cloud launcher | data modules | Analytics behavior | Potential duplication in query/report models | refactor |
| products:data-cloud:launcher | products/data-cloud/launcher | Runtime bootstrap | platform modules | many | Runtime composition | Contains another config-validator flavor | refactor |
| products:finance:domains:* (oms/ems/pms/risk/compliance/rules/corporate-actions/market-data/post-trade/pricing/reconciliation/reference-data/regulatory-reporting/sanctions/surveillance) | products/finance/domains/* | Finance business capabilities | finance platform and workflows | platform-kernel + platform java + direct DB/Kafka/Redis/HTTP | Finance domain logic | Domain modules include infra dependencies directly | split |
| products:finance:platform-sdk | products/finance/platform-sdk | Finance SDK surface | finance services/clients | finance domains/platform libs | Finance integration contracts | Should stay API-focused | refactor |
| products:finance:operator-workflows | products/finance/operator-workflows | Workflow orchestration for finance ops | finance runtime | workflow/platform libs | Workflow composition | Keep orchestration separate from domain | keep |
| products:finance:data-governance | products/finance/data-governance | Finance governance policies | finance domains | governance/security | Finance-specific governance | Some overlap with platform data-governance | refactor |
| products:finance:ledger-framework | products/finance/ledger-framework | Ledger framework | finance domains | database/audit | Ledger abstractions | Verify overlap with billing-ledger plugin | refactor |
| products:finance:incident-management | products/finance/incident-management | Finance incident runtime | finance services | shared incident + platform | Finance incident flows | Potential overlap with shared incident-service | refactor |
| products:yappc:* (core/services/agents/scaffold/refactorer/platform/infra/libs) | products/yappc/* | YAPPC product ecosystem | YAPPC runtime/tools | broad | Product-specific framework | Significant module fan-out; enforce documented dependency topology | refactor |
| products:audio-video:libs:java:common | products/audio-video/libs/java/common | Audio/video shared java library | audio-video modules | platform/security/observability | Audio-video shared contracts | Duplication history with deprecated platform/java/audio-video | keep |
| shared-services:auth-gateway | shared-services/auth-gateway | Shared auth gateway | products/services | security/identity/http/db | Shared auth service | test-only dep from platform governance indicates inversion risk | refactor |
| shared-services:incident-service | shared-services/incident-service | Shared incident capability | products/services | core/security | Incident service source of truth | Old platform incident module still present | keep |

### Source-bearing, not included or stale-shadow modules (high risk)
Observed build scripts not included in settings (excluding node_modules) include:
- platform/java/agent-memory
- platform/java/audio-video
- platform/java/distributed-cache
- platform/java/incident-response
- platform/java/security-analytics
- shared-services/ai-inference-service
- shared-services/feature-store-ingest
- products/yappc/core/yappc-domain-api
- products/yappc/tools/validation-tests
- products/yappc/examples/sample-build-generator-plugin
- products/flashit/backend/agent
- plus mobile/android build roots not part of Java library governance.

Verdict for this group: remove/merge or explicitly include; current state creates ownership ambiguity and hidden drift.

## 3. Source-of-Truth and Shared Contract Review
Canonical modules that should own shared contracts:
- Core primitives: platform/java/core
- Config contracts and validator SPI: platform/java/config
- Agent framework contracts/specs: platform/java/agent-core
- Messaging connector abstractions: platform/java/messaging
- Shared security contracts: platform/java/security + platform/java/identity (needs consolidation boundaries)

Fragmented or duplicate sources of truth (validated):
- Canonical core contracts duplicated in kernel-core:
  - platform/java/core/src/main/java/com/ghatana/platform/core/client/AsyncClient.java
  - platform-kernel/kernel-core/src/main/java/com/ghatana/platform/core/client/AsyncClient.java
  - platform/java/core/src/main/java/com/ghatana/platform/health/HealthStatus.java
  - platform-kernel/kernel-core/src/main/java/com/ghatana/platform/health/HealthStatus.java
  - platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationResult.java
  - platform-kernel/kernel-core/src/main/java/com/ghatana/platform/validation/ValidationResult.java
  - Hash and line counts match exactly for these pairs.
- Validator ownership drift:
  - platform/java/core/src/main/java/com/ghatana/platform/validation/Validator.java
  - platform/java/core/src/main/java/com/ghatana/platform/core/validation/Validator.java
  - products/data-cloud/platform-config/src/main/java/com/ghatana/datacloud/config/ConfigValidator.java
  - products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudConfigValidator.java
  - products/aep/aep-engine/src/main/java/com/ghatana/aep/config/AepConfigValidator.java
  - products/aep/aep-registry/src/main/java/com/ghatana/agent/registry/validation/AgentSpecValidator.java
- Event ownership fragmentation:
  - platform/java/messaging (connector abstraction + IngestEvent)
  - products/data-cloud/platform-event (event storage/domain model)
  - products/aep/aep-event-cloud (event cloud orchestration)
  - products/aep/aep-operator-contracts (event cloud SPI)

Shared abstractions that are valid:
- platform/java/core utility and error contracts.
- platform/java/messaging connector strategy pattern.
- platform/java/agent-core typed agent taxonomy and tests.

Shared abstractions that are fake/leaky:
- Deprecated shared modules still carrying live code (platform/java/audio-video, platform/java/incident-response).
- Kernel-core exporting package families under com.ghatana.platform.* while platform/java/core owns same families.

Contract violations by consumers:
- Product modules creating local validator contracts instead of implementing platform validator SPI semantics.
- Finance domain modules exposing domain APIs while also directly depending on infrastructure concerns.

Areas needing centralization:
- Validation contracts.
- Event contract model and event metadata envelope.
- Health/validation/client primitives into one canonical owner only.

## 4. Cross-Module Findings
Inconsistency patterns:
- Mixed naming and ownership of validators across platform and products.
- Deprecated-module comments in settings do not match repository reality (source still alive).

Duplication patterns:
- Exact file duplication of core contracts across platform/java/core and platform-kernel/kernel-core.
- Same conceptual validators repeated in platform + AEP + Data Cloud.

Sprawl patterns:
- 170 includes in settings and additional non-included build roots create governance overhead.
- YAPPC and Finance contain high module fan-out with mixed concerns.

Boundary violations:
- Domain modules in Finance directly depend on HTTP/DB/Migration/Kafka/Redis libs and infra modules.

Event/messaging misuse patterns:
- Event concern ownership split among platform messaging, data-cloud platform-event, and AEP event-cloud/operator contracts.

Missing shared abstractions:
- Canonical cross-product event envelope and schema ownership.

Overengineered abstractions:
- Multiple validator abstraction layers for equivalent concerns.

Under-generalized implementations:
- Product-local validator implementations where platform SPI already exists.

Shared module quality issues:
- Core shared abstractions are strong, but canonical ownership is not enforced structurally.

## 5. Detailed Findings by Module
Severity legend: Critical / High / Medium / Low.

1. Critical - Duplicate canonical core contracts in kernel-core
- Modules: platform:java:core, platform-kernel:kernel-core
- Evidence: duplicated AsyncClient, HealthStatus, ValidationResult classes under identical package names and matching hashes.
- Why it matters: two owners for same API surface causes drift, binary ambiguity, and import confusion.
- Recommended fix: keep only platform:java:core as owner; remove duplicates from kernel-core and depend on core.

2. Critical - Deprecated modules with active code
- Modules: platform:java:audio-video, platform:java:incident-response
- Evidence: settings marks deprecation/migration while source and tests remain active in old module paths.
- Why it matters: unresolved migrations create hidden compatibility requirements and contradictory ownership.
- Recommended fix: complete migration and delete old source; or explicitly un-deprecate with owner assignment.

3. High - Validator source-of-truth fragmentation
- Modules: platform:java:core, platform:java:config, products:aep:aep-engine, products:aep:aep-registry, products:data-cloud:platform-config, products:data-cloud:launcher
- Evidence: multiple Validator/ConfigValidator/AgentSpecValidator implementations with overlapping responsibilities.
- Why it matters: behavior divergence and duplicated maintenance.
- Recommended fix: define canonical validator contracts in platform modules and enforce product implementations via SPI naming + package conventions.

4. High - Finance domains violate layering
- Modules: products:finance:domains:* (broadly)
- Evidence: domain build files include platform:java:http, postgres, flyway, kafka, jedis, activej.http/eventloop.
- Why it matters: domain purity and testability degrade; infra changes become business-layer breaking changes.
- Recommended fix: split each domain into domain-core and infra-adapter modules; keep domain-core free of transport/persistence deps.

5. High - Event ownership fragmentation
- Modules: platform:java:messaging, products:data-cloud:platform-event, products:aep:aep-event-cloud, products:aep:aep-operator-contracts
- Evidence: each module defines event-centric contracts/models/runtimes without a single canonical envelope owner.
- Why it matters: schema drift and incompatible routing semantics across products.
- Recommended fix: centralize event envelope contract in platform/contracts or platform/java/domain and enforce adapters in product modules.

6. Medium - Module graph includes stale or non-included source-bearing build roots
- Modules: listed in Section 2 stale-shadow group
- Evidence: build.gradle.kts present but module not included in settings.
- Why it matters: dead code risk, hidden drift, false discoverability.
- Recommended fix: remove stale roots or include intentionally and gate with ownership docs.

7. Medium - Security/Identity overlap
- Modules: platform:java:security, platform:java:identity
- Evidence: both modules own authentication/authorization-related contracts and implementations.
- Why it matters: ambiguous extension points for consumers.
- Recommended fix: enforce strict split (identity model/token contract vs security enforcement/policy runtime).

8. Medium - Testing quality debt in shared modules
- Modules: platform:java:agent-core, platform:java:database, platform:java:identity, platform:java:policy-as-code, platform:java:security-analytics
- Evidence: failure-summary.txt contains repeated failures in these modules.
- Why it matters: reduced confidence in refactors and releases.
- Recommended fix: fix failing suites before major consolidation; add gating to prevent persistent red tests.

## 6. Event and Messaging Module Review
Current state:
- platform/java/messaging owns connector infrastructure and includes IngestEvent.
- products/data-cloud/platform-event owns event persistence/domain models.
- products/aep/aep-event-cloud owns event-cloud runtime behavior.
- products/aep/aep-operator-contracts defines event-cloud SPI.

Assessment:
- Reuse is partial: modules compose at runtime, but contract ownership is fragmented.
- Duplicate event/domain/schema logic risk is high because each layer has its own event-centric types.
- Ownership is misplaced where product modules define generic event contracts that should be canonical shared contracts.

Recommended restructuring:
1. Introduce canonical EventEnvelope and EventMetadata contract in one shared module.
2. Keep platform/java/messaging as transport/connectivity only.
3. Keep data-cloud platform-event as persistence model adapter to canonical contracts.
4. Keep AEP event-cloud as orchestration/runtime adapter to canonical contracts.
5. Keep operator-contracts limited to AEP-operator semantics, not generic event schema ownership.

Canonical ownership recommendations:
- Event envelope schema and validation: platform/contracts or platform/java/domain.
- Transport adapters and retry/circuit semantics: platform/java/messaging.
- Product-specific projections/persistence: product modules.

## 7. Consolidation and Simplification Plan
Modules to merge:
- Merge duplicate core contract classes into platform:java:core ownership.
- Merge/standardize validator implementations through platform validator SPI.

Modules to split:
- products:finance:domains:* split into domain-core and infra-adapter.
- products:data-cloud:launcher separate boot wiring from config-validation logic if still mixed.

Modules to remove:
- platform/java/audio-video after migration verification.
- platform/java/incident-response after migration verification.
- stale non-included build roots with no owner.

Responsibilities to move:
- Move generic event contract ownership out of product modules into shared canonical module.
- Move duplicated agent/config validator semantics to platform contracts.

Common abstractions to centralize:
- Validation contract hierarchy.
- Event envelope and serialization contract.
- Health/validation/client primitives (single owner policy).

Source-of-truth consolidation actions:
1. Add architecture rule check: no com.ghatana.platform.* classes outside canonical module owners.
2. Add lint/gradle check for duplicated fully-qualified type names.
3. Add module ownership metadata file and enforcement in CI.

## 8. Target-State Module Architecture
Module categories:
- Foundation: core, runtime, testing.
- Cross-cutting shared services: security, identity, config, observability, database, messaging, governance.
- Domain contracts: platform domain and contracts.
- Product modules: business/domain/app layers only, composed from shared modules.

Ownership rules:
- One canonical owner per shared concern.
- No product module may publish generic platform contracts.

Source-of-truth rules:
- Fully-qualified contract types must be unique across repo.
- Shared contracts must live in designated owner modules and be consumed, not copied.

Dependency direction rules:
- Product domain-core -> shared contracts only.
- Product infra-adapter -> shared infra modules.
- Shared modules must never depend on product modules.

Event/messaging module rules:
- Canonical event contract in shared layer.
- Messaging module provides transports only.
- Product event modules may define projections, not canonical envelope.

Naming and packaging rules:
- Package roots align with module ownership.
- No com.ghatana.platform.* in non-owner modules.

Public API exposure rules:
- Minimize exported API surface to stable contracts.
- Keep implementation/internal types out of public packages.

Shared abstraction rules:
- Abstraction only after at least two real consumers.
- Remove interface-per-class patterns with no substitution value.

Contract rules:
- Runtime validation must align with static contract definitions.
- Versioned serialization and schema compatibility tests are mandatory for shared contracts.

Testing/documentation expectations:
- Each shared module must have contract tests and usage guidance.
- All public Java APIs include required doc tags and owner documentation.

## 9. Prioritized Action Plan
### Phase 1: Critical source-of-truth and boundary fixes
1. Exact issue: duplicate core contracts (AsyncClient/HealthStatus/ValidationResult).
- Affected modules: platform:java:core, platform-kernel:kernel-core.
- Concrete change: delete duplicate kernel-core package copies; import from platform:java:core.
- Expected benefit: single canonical contract owner, less drift.
- Risk if ignored: API split and subtle behavior mismatch.

2. Exact issue: deprecated modules still active.
- Affected modules: platform:java:audio-video, platform:java:incident-response.
- Concrete change: complete migration and delete old code or un-deprecate explicitly with ownership.
- Expected benefit: removes contradictory ownership.
- Risk if ignored: permanent dual-stack maintenance burden.

### Phase 2: Reuse and shared-contract cleanup
1. Exact issue: validator duplication and naming drift.
- Affected modules: platform core/config + AEP/Data Cloud validators.
- Concrete change: define canonical validator interfaces and product-specific implementations with explicit naming.
- Expected benefit: consistent validation behavior and easier onboarding.
- Risk if ignored: contract drift and repeated bugs.

2. Exact issue: event contract fragmentation.
- Affected modules: platform messaging, data-cloud event, aep event-cloud/operator-contracts.
- Concrete change: central event envelope contract and mapping adapters.
- Expected benefit: reliable interop and schema governance.
- Risk if ignored: cross-product event breakages.

### Phase 3: Simplification and consolidation
1. Exact issue: finance domains mixed with infra concerns.
- Affected modules: finance domains.
- Concrete change: split domain-core from infra-adapter modules.
- Expected benefit: cleaner boundaries, higher testability.
- Risk if ignored: rigid architecture and costly change management.

2. Exact issue: stale non-included build roots.
- Affected modules: stale-shadow list.
- Concrete change: delete or include with owner and CI checks.
- Expected benefit: reduced module sprawl and confusion.
- Risk if ignored: hidden technical debt.

### Phase 4: Long-term hardening
1. Exact issue: boundary violations not automatically enforced.
- Affected modules: all Java modules.
- Concrete change: CI architecture rules for package ownership, duplicate FQCN checks, and dependency direction checks.
- Expected benefit: prevents drift recurrence.
- Risk if ignored: regressions after cleanup.

2. Exact issue: persistent shared-module test failures.
- Affected modules: agent-core/database/identity/policy-as-code/security-analytics.
- Concrete change: module owner SLAs and quality gates (no red shared module tests on main).
- Expected benefit: higher release confidence.
- Risk if ignored: unreliable platform baseline.

## 10. Final Verdict
The current Java library/module ecosystem is acceptable with focused cleanup, but it is currently fragmented and risky in source-of-truth ownership and boundary discipline.

It does not require total redesign, but it does require decisive consolidation of canonical contracts, closure of deprecated-module drift, and stricter layering enforcement (especially in Finance domains and event contract ownership).

Without these actions, module sprawl and contract duplication will continue to increase maintenance cost and release risk.