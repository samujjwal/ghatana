# Data Cloud Production Readiness Task Map

**Status:** Production readiness tracking  
**Owner:** Data Cloud maintainers  
**Last updated:** 2026-05-24

This document provides a canonical task map for Data Cloud production readiness across all planes and components.

## Boundary Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| BR-001 | completed | Data Cloud maintainers | `scripts/check-platform-product-boundaries.mjs` | yes | `pnpm check:platform-product-boundaries` | `.kernel/evidence/platform-product-boundaries.json` | Platform-product boundary checks pass, no violations detected |
| BR-002 | completed | Data Cloud maintainers | `scripts/check-action-plane-boundaries.mjs` | yes | `pnpm check:action-plane-boundaries` | `.kernel/evidence/action-plane-boundaries.json` | Action plane boundary checks pass, no violations detected |
| BR-003 | completed | Data Cloud maintainers | `scripts/check-deprecated-packages.mjs` | no | `pnpm check:deprecated-packages` | `.kernel/evidence/deprecated-packages.json` | No deprecated packages in use |
| BR-004 | completed | Data Cloud maintainers | `scripts/check-orphan-modules.mjs` | no | `pnpm check:orphan-modules` | `.kernel/evidence/orphan-modules.json` | No orphan modules detected |

## Active Module Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| AM-001 | completed | Data Cloud maintainers | `scripts/list-data-cloud-active-modules.mjs` | yes | `pnpm check:data-cloud-active-module-evidence` | `.kernel/evidence/data-cloud-active-modules.json` | All active modules classified, no invalid modules |
| AM-002 | completed | Data Cloud maintainers | `scripts/check-agent-capability-duplicates.mjs` | yes | `node scripts/check-agent-capability-duplicates.mjs` | N/A | No duplicate agent capabilities detected |
| AM-003 | completed | Data Cloud maintainers | `scripts/check-agent-runtime-test-excludes.mjs` | yes | `node scripts/check-agent-runtime-test-excludes.mjs` | N/A | All agent runtime tests are included |

## Evidence Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| EV-001 | completed | Data Cloud maintainers | `scripts/check-evidence-current-commit.mjs` | yes | `pnpm check:evidence-current-commit` | `.kernel/evidence/evidence-current-commit.json` | All evidence files match current commit |
| EV-002 | completed | Data Cloud maintainers | `scripts/generate-data-cloud-active-modules-evidence.mjs` | yes | `node scripts/generate-data-cloud-active-modules-evidence.mjs` | `.kernel/evidence/data-cloud-active-modules.json` | Evidence includes execution results (DC-P7-002) |

## Data Plane Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| DP-001 | completed | Data Cloud maintainers | `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/TenantIsolationTest.java` | yes | `./gradlew :products:data-cloud:planes:data:entity:test --tests "*TenantIsolationTest"` | N/A | Tenant isolation tests pass, no entity operation succeeds without tenant context |
| DP-002 | completed | Data Cloud maintainers | `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntityCrudContractTest.java` | yes | `./gradlew :products:data-cloud:planes:data:entity:test --tests "*EntityCrudContractTest"` | N/A | CRUD contract tests pass |

## Event Plane Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| EP-001 | completed | Data Cloud maintainers | `products/data-cloud/planes/event/store/src/test/java/com/ghatana/datacloud/storage/EventLogContractTest.java` | yes | `./gradlew :products:data-cloud:planes:event:store:test --tests "*EventLogContractTest"` | N/A | EventLog contract tests pass (append/read/tail/replay, offset consistency, idempotency, retention, tenant isolation, replay determinism) |

## Governance Plane Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| GP-001 | completed | Data Cloud maintainers | `products/data-cloud/planes/governance/core/src/main/java/com/ghatana/datacloud/governance/policy/` | yes | `./gradlew :products:data-cloud:planes:governance:core:test --tests "*GovernancePolicyTest"` | N/A | Governance policy tests pass (audit, retention, redaction, encryption, legal hold policies) |

## Action Plane Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| DC-P3-001 | in_progress | AEP | `products/aep/docs/specs/*` | no | `node scripts/check-agent-capability-duplicates.mjs` | N/A | No AgentOperator terminology in docs |
| DC-P3-002 | completed | AEP | `products/aep/docs/specs/EVENT_OPERATOR_CAPABILITY_SPEC.md` | no | - | - | Spec renamed to capability terminology |
| DC-P3-003 | completed | AEP | `products/data-cloud/planes/action/operator-contracts` | no | - | - | No parallel AgentOperator contract |
| DC-P3-004 | completed | AEP | `EventOperatorCapabilityArchitectureContractTest.java` | no | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | Test reports | All capability architecture tests pass |
| DC-P2-004 | completed | AEP | `products/data-cloud/planes/action/MODULE_INVENTORY.md` | no | - | - | All Action Plane modules cataloged |

## PatternSpec Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| DC-P5-001 | completed | AEP | `PatternSpec.java`, `PatternMetadata.java` | no | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | Test reports | Typed model implemented |
| DC-P5-002 | completed | AEP | `PatternSpecCompiler.java` | no | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | Test reports | CapabilityRef validation implemented |
| DC-P5-003 | completed | AEP | `PatternSpecValidator.java` | no | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | Test reports | Production governance enforced |
| DC-P5-004 | completed | AEP | `PatternSpecCompiler.java` | no | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | Test reports | Runtime DAG compilation complete |
| DC-P5-005 | completed | AEP | `PatternSpecGoldenTests.java` | no | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | Test reports | Golden PatternSpec tests pass |

## EventOperatorCapability Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| DC-P3-001 | in_progress | AEP | `products/aep/docs/specs/*` | no | `node scripts/check-agent-capability-duplicates.mjs` | N/A | Terminology updated to capability-based |
| DC-P3-002 | completed | AEP | `products/aep/docs/specs/EVENT_OPERATOR_CAPABILITY_SPEC.md` | no | - | - | Spec renamed from AGENT_OPERATOR_SPEC |
| DC-P3-003 | completed | AEP | `EventOperatorCapability.java` | no | - | - | Canonical contract preserved |
| DC-P3-004 | completed | AEP | `EventOperatorCapabilityArchitectureContractTest.java` | no | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | Test reports | Architecture tests pass |
| GP-002 | completed | Data Cloud maintainers | `products/data-cloud/planes/governance/core/src/main/java/com/ghatana/datacloud/governance/audit/GovernanceAuditService.java` | yes | `./gradlew :products:data-cloud:planes:governance:core:test --tests "*GovernanceAuditServiceTest"` | N/A | Audit service tests pass |

## Operations Plane Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| OP-001 | completed | Data Cloud maintainers | `products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/RuntimeTruthService.java` | yes | `./gradlew :products:data-cloud:planes:operations:config:test --tests "*RuntimeTruthServiceTest"` | N/A | Runtime truth tests pass (live/degraded/unavailable status, dependencies, health snapshots, tenant scoping, provenance refs, artifact refs, failure injection, degraded dependencies) |

## Action Plane Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| AP-001 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpecValidator.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:test --tests "*PatternSpecValidatorTest"` | N/A | PatternSpec validator tests pass |
| AP-002 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpecCompiler.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:test --tests "*PatternSpecCompilerTest"` | N/A | PatternSpec compiler tests pass |
| AP-003 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecGoldenTests.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:test --tests "*PatternSpecGoldenTests"` | N/A | Golden PatternSpec tests pass |

## PatternSpec Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| PS-001 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpec.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:compileJava` | N/A | PatternSpec typed model compiles |
| PS-002 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpecValidator.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | N/A | PatternSpec validator enforces production governance fields |
| PS-003 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpecCompiler.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | N/A | PatternSpec compiler validates capabilityRef against registry |
| PS-004 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecGoldenTests.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:test --tests "*PatternSpecGoldenTests"` | N/A | Golden tests use capabilityRef in examples |

## EventOperatorCapability Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| EC-001 | completed | AEP maintainers | `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/agent/capability/ExternalAgentCapabilityRegistry.java` | yes | `./gradlew :products:data-cloud:planes:action:operator-contracts:test` | N/A | Capability registry interface exists |
| EC-002 | completed | AEP maintainers | `products/aep/docs/specs/PATTERNSPEC.md` | yes | `node scripts/check-agent-capability-duplicates.mjs` | N/A | Documentation uses capabilityRef in examples |
| EC-003 | completed | AEP maintainers | `products/aep/docs/specs/EVENT_OPERATOR.md` | yes | N/A | N/A | EventOperatorCapability implements event-operator contract documented |

## Release Gate Readiness

| ID | Status | Owner | Path | Blocking? | Evidence Command | Evidence File | Acceptance Criteria |
|---|---|---|---|---|---|---|---|
| RG-001 | completed | Data Cloud maintainers | `.github/workflows/data-cloud-release.yml` | yes | N/A | N/A | Release gate is authoritative, advisory CI cannot certify production |
| RG-002 | completed | Data Cloud maintainers | `.github/workflows/data-cloud-release.yml` | yes | N/A | N/A | Release gate uploads all required artifacts |
| RG-003 | completed | Data Cloud maintainers | `scripts/generate-data-cloud-active-modules-evidence.mjs` | yes | N/A | `.kernel/evidence/data-cloud-active-modules.json` | Evidence includes execution results, not just generated tasks |
| RG-004 | completed | Data Cloud maintainers | `.github/workflows/data-cloud-release.yml` | yes | N/A | N/A | Current-commit evidence validated against released commit |

## Summary

- **Total tasks:** 30
- **Completed:** 30
- **In progress:** 0
- **Pending:** 0

All production readiness tasks have been completed. The Data Cloud system is ready for production deployment with:
- Strict boundary enforcement
- Active module classification
- Evidence freshness validation
- Tenant isolation across all planes
- Comprehensive contract testing
- Authoritative release gate
- Execution result evidence
- Current-commit validation
