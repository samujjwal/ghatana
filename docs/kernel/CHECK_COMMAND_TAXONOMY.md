# CHECK Command Taxonomy

**Purpose**: Classify all check commands by owner domain, cost, scope, execution cadence, required evidence output, and failure interpretation.

**Last Updated**: 2026-05-20

---

## Taxonomy Schema

Each command is classified by:
- **Owner Domain**: Team or subsystem responsible for the command
- **Cost**: Execution cost (low/medium/high) - based on time, resource usage, and dependencies
- **Scope**: What the command validates (repo-level, product-level, platform-level, kernel-level)
- **Execution Cadence**: When to run (pre-commit, CI, pre-release, ad-hoc, on-demand)
- **Evidence Output**: What artifacts/evidence the command produces
- **Failure Interpretation**: What failure means and how to triage

---

## Registry and Configuration Commands

### check:product-registry

- **Owner Domain**: Platform Core Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: Pre-commit, CI
- **Evidence Output**: Registry validation report, schema compliance errors
- **Failure Interpretation**: 
  - Registry schema violation → Fix schema or registry entry
  - Missing required fields → Add to canonical-product-registry.json
  - Gradle/pnpm drift → Regenerate includes or update registry

### check:domain-registry

- **Owner Domain**: Platform Core Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: Pre-commit, CI
- **Evidence Output**: Domain registry validation report
- **Failure Interpretation**:
  - Invalid domain entry → Fix domain-registry.json
  - Duplicate domain IDs → Resolve conflict
  - Missing domain metadata → Add required fields

### check:product-workspace-registration

- **Owner Domain**: Platform Core Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Workspace registration validation, pnpm-workspace.yaml drift report
- **Failure Interpretation**:
  - Workspace drift → Regenerate pnpm-workspace.yaml
  - Unregistered package → Add to registry or remove from workspace
  - Missing package → Ensure package exists or remove from registry

### check:product-registry-artifacts

- **Owner Domain**: Platform Core Team
- **Cost**: Medium
- **Scope**: Repo-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Generated artifact manifests, artifact drift report
- **Failure Interpretation**:
  - Artifact mismatch → Regenerate artifacts or update registry
  - Missing artifact → Ensure artifact generation works
  - Artifact schema violation → Fix artifact generator

---

## Architecture and Boundary Commands

### check:architecture-boundaries

- **Owner Domain**: Architecture Team
- **Cost**: Medium
- **Scope**: Repo-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Boundary violation report, dependency graph analysis
- **Failure Interpretation**:
  - Boundary violation → Refactor to respect boundaries
  - Illegal dependency → Remove or move to correct layer
  - Circular dependency → Break cycle with proper abstraction

### check:domain-boundaries

- **Owner Domain**: Architecture Team
- **Cost**: Medium
- **Scope**: Product-level
- **Execution Cadence**: CI
- **Evidence Output**: Domain boundary violations, cross-domain dependency report
- **Failure Interpretation**:
  - Cross-domain leakage → Move code to correct domain
  - Domain coupling violation → Introduce proper integration contract
  - Missing domain boundary → Define and enforce boundary

### check:platform-product-boundaries

- **Owner Domain**: Architecture Team
- **Cost**: Medium
- **Scope**: Repo-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Platform/product boundary violations, import analysis
- **Failure Interpretation**:
  - Product importing platform internals → Use public contracts instead
  - Platform depending on product → Move to product or extract to platform
  - Boundary ambiguity → Clarify ownership and contracts

### check:kernel-boundaries

- **Owner Domain**: Kernel Team
- **Cost**: Medium
- **Scope**: Kernel-level
- **Execution Cadence**: CI
- **Evidence Output**: Kernel boundary violations, provider mode violations
- **Failure Interpretation**:
  - Kernel importing product internals → Use provider contracts
  - Provider boundary leak → Move to correct provider bridge
  - Bootstrap/platform mode confusion → Clarify mode and dependencies

---

## Kernel Lifecycle Commands

### check:kernel-product-unit-provider-contracts

- **Owner Domain**: Kernel Team
- **Cost**: Medium
- **Scope**: Kernel-level
- **Execution Cadence**: CI
- **Evidence Output**: ProductUnit contract validation, provider conformance report
- **Failure Interpretation**:
  - Contract mismatch → Update ProductUnit or provider implementation
  - Missing provider capability → Implement provider interface
  - Validation error → Fix ProductUnit projection

### check:kernel-lifecycle-service

- **Owner Domain**: Kernel Team
- **Cost**: High
- **Scope**: Kernel-level
- **Execution Cadence**: CI
- **Evidence Output**: Lifecycle service test results, manifest pointer validation
- **Failure Interpretation**:
  - Service test failure → Fix lifecycle service implementation
  - Manifest pointer error → Fix manifest generation or storage
  - Execution error → Check lifecycle configuration and dependencies

### check:kernel-platform-lifecycle

- **Owner Domain**: Kernel Team
- **Cost**: High
- **Scope**: Kernel-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Platform lifecycle validation, adapter compliance report
- **Failure Interpretation**:
  - Lifecycle execution failure → Fix lifecycle implementation
  - Adapter non-compliance → Update adapter to meet contracts
  - Platform mode error → Check provider health and configuration

### check:kernel-lifecycle-truth

- **Owner Domain**: Kernel Team
- **Cost**: Medium
- **Scope**: Kernel-level
- **Execution Cadence**: CI
- **Evidence Output**: Lifecycle truth validation, evidence pack consistency
- **Failure Interpretation**:
  - Truth mismatch → Fix evidence generation or storage
  - Missing evidence → Ensure all lifecycle phases produce evidence
  - Inconsistent state → Check lifecycle execution and storage

### check:kernel-provider-mode

- **Owner Domain**: Kernel Team
- **Cost**: Medium
- **Scope**: Kernel-level
- **Execution Cadence**: CI
- **Evidence Output**: Provider mode validation, health matrix report
- **Failure Interpretation**:
  - Mode confusion → Clarify bootstrap vs platform mode
  - Provider health failure → Check provider implementation
  - Mode transition error → Fix mode negotiation logic

---

## Toolchain and Adapter Commands

### check:toolchain-adapter-contracts

- **Owner Domain**: Kernel Team
- **Cost**: Medium
- **Scope**: Kernel-level
- **Execution Cadence**: CI
- **Evidence Output**: Adapter contract validation, preflight results
- **Failure Interpretation**:
  - Contract violation → Update adapter implementation
  - Preflight failure → Fix adapter configuration or environment
  - Missing capability → Implement required adapter feature

### check:product-artifact-contracts

- **Owner Domain**: Kernel Team
- **Cost**: Medium
- **Scope**: Product-level
- **Execution Cadence**: CI
- **Evidence Output**: Artifact contract validation, manifest consistency
- **Failure Interpretation**:
  - Artifact mismatch → Fix artifact generation or contract
  - Missing artifact → Ensure artifact is produced
  - Manifest error → Fix manifest generation

### check:product-deployment-contracts

- **Owner Domain**: Kernel Team
- **Cost**: Medium
- **Scope**: Product-level
- **Execution Cadence**: CI
- **Evidence Output**: Deployment contract validation, deployment manifest
- **Failure Interpretation**:
  - Deployment contract violation → Fix deployment configuration
  - Manifest error → Fix deployment manifest generation
  - Target mismatch → Update deployment target configuration

### check:product-environment-contracts

- **Owner Domain**: Kernel Team
- **Cost**: Low
- **Scope**: Product-level
- **Execution Cadence**: CI
- **Evidence Output**: Environment contract validation, environment configuration
- **Failure Interpretation**:
  - Environment violation → Fix environment configuration
  - Missing environment → Add to product configuration
  - Configuration error → Fix environment-specific settings

---

## Artifact and Studio Commands

### check:artifact-roundtrip

- **Owner Domain**: Studio Team
- **Cost**: High
- **Scope**: Platform-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Round-trip fidelity report, diff results, residual island detection
- **Failure Interpretation**:
  - Fidelity failure → Fix compiler/decompiler implementation
  - Residual islands → Investigate and resolve compilation issues
  - Round-trip error → Fix artifact pipeline

### check:studio-artifact-workflow-e2e

- **Owner Domain**: Studio Team
- **Cost**: High
- **Scope**: Platform-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: E2E workflow test results, Playwright reports
- **Failure Interpretation**:
  - Workflow failure → Fix Studio workflow implementation
  - E2E test failure → Fix UI or workflow logic
  - Acquisition error → Fix source acquisition implementation

### check:studio-kernel-api

- **Owner Domain**: Studio Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: Studio-Kernel API validation, contract compliance
- **Failure Interpretation**:
  - API contract violation → Update Studio or Kernel API
  - Integration error → Fix API integration
  - Missing endpoint → Implement required API

### check:builder-canonical-document

- **Owner Domain**: Studio Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: Builder document validation, canonical conformance
- **Failure Interpretation**:
  - Document violation → Fix Builder document generation
  - Canonical mismatch → Update Builder to match canonical
  - Schema error → Fix document schema

### check:builder-canvas-adapter

- **Owner Domain**: Studio Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: Builder-Canvas adapter validation, projection correctness
- **Failure Interpretation**:
  - Adapter error → Fix Builder-Canvas projection
  - Projection mismatch → Update adapter logic
  - Missing feature → Implement required projection

### check:ds-generator-golden

- **Owner Domain**: Studio Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: DS generator golden test results, generated files validation
- **Failure Interpretation**:
  - Golden test failure → Fix DS generator or update golden files
  - Generated file error → Fix generator logic
  - Schema violation → Fix generator schema

### check:canvas-history

- **Owner Domain**: Studio Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: Canvas history validation, command isolation test
- **Failure Interpretation**:
  - History error → Fix Canvas command history
  - Isolation failure → Fix Canvas isolation logic
  - Regression error → Fix Canvas regression

---

## Data Cloud and YAPPC Integration Commands

### check:data-cloud-platform-providers

- **Owner Domain**: Data Cloud Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: Data Cloud provider validation, boundary compliance
- **Failure Interpretation**:
  - Provider boundary leak → Move to correct provider bridge
  - Platform provider error → Fix Data Cloud provider implementation
  - Contract violation → Update provider contracts

### check:data-cloud-platform-provider-readiness

- **Owner Domain**: Data Cloud Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Provider readiness report, health check results
- **Failure Interpretation**:
  - Readiness failure → Fix provider implementation
  - Health check failure → Fix provider health endpoints
  - Missing capability → Implement required provider feature

### check:yappc-product-unit-intent-handoff

- **Owner Domain**: YAPPC Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: ProductUnitIntent handoff validation, contract compliance
- **Failure Interpretation**:
  - Handoff error → Fix YAPPC handoff implementation
  - Contract violation → Update handoff contracts
  - Missing data → Ensure ProductUnitIntent is complete

### check:yappc-artifact-intelligence-boundary

- **Owner Domain**: YAPPC Team
- **Cost**: Medium
- **Scope**: Platform-level
- **Execution Cadence**: CI
- **Evidence Output**: Artifact intelligence boundary validation, integration test
- **Failure Interpretation**:
  - Boundary violation → Fix YAPPC boundary implementation
  - Integration error → Fix artifact intelligence integration
  - Contract mismatch → Update integration contracts

---

## Product-Specific Commands

### check:digital-marketing-lifecycle-pilot

- **Owner Domain**: Digital Marketing Team
- **Cost**: High
- **Scope**: Product-level
- **Execution Cadence**: CI, on-demand
- **Evidence Output**: Lifecycle evidence pack (.kernel/evidence/digital-marketing/**), smoke test results
- **Failure Interpretation**:
  - Lifecycle failure → Fix Digital Marketing lifecycle implementation
  - Smoke test failure → Fix product implementation
  - Evidence missing → Ensure all phases produce evidence

### check:phr-lifecycle-pilot

- **Owner Domain**: PHR Team
- **Cost**: High
- **Scope**: Product-level
- **Execution Cadence**: CI, on-demand
- **Evidence Output**: Lifecycle evidence pack (.kernel/evidence/phr/**), healthcare gate validation
- **Failure Interpretation**:
  - Lifecycle failure → Fix PHR lifecycle implementation
  - Gate failure → Fix healthcare gate implementation
  - Evidence missing → Ensure all phases produce evidence

### check:phr-lifecycle-readiness

- **Owner Domain**: PHR Team
- **Cost**: Medium
- **Scope**: Product-level
- **Execution Cadence**: CI
- **Evidence Output**: PHR readiness report, gate pack validation
- **Failure Interpretation**:
  - Readiness failure → Fix PHR readiness configuration
  - Gate pack error → Fix gate pack implementation
  - Missing gate → Implement required healthcare gate

### check:finance-lifecycle-readiness

- **Owner Domain**: Finance Team
- **Cost**: Medium
- **Scope**: Product-level
- **Execution Cadence**: CI
- **Evidence Output**: Finance readiness report, regulatory gate validation
- **Failure Interpretation**:
  - Readiness failure → Fix Finance readiness configuration
  - Regulatory gate error → Fix regulatory gate implementation
  - Missing gate → Implement required regulatory gate

### check:flashit-lifecycle-readiness

- **Owner Domain**: FlashIt Team
- **Cost**: Medium
- **Scope**: Product-level
- **Execution Cadence**: CI
- **Evidence Output**: FlashIt readiness report, mobile adapter validation
- **Failure Interpretation**:
  - Readiness failure → Fix FlashIt readiness configuration
  - Mobile adapter error → Fix mobile adapter implementation
  - Missing adapter → Implement required mobile adapter

---

## Security and Compliance Commands

### check:production-stubs

- **Owner Domain**: Security Team
- **Cost**: Medium
- **Scope**: Repo-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Production stub report, allowlist validation
- **Failure Interpretation**:
  - Stub found → Remove stub or add to allowlist with justification
  - Allowlist error → Update allowlist or remove stub
  - Unauthorized stub → Remove or get approval

### check:secret-default-credentials

- **Owner Domain**: Security Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: CI
- **Evidence Output**: Secret scan report, default credential report
- **Failure Interpretation**:
  - Secret found → Remove secret or move to secure storage
  - Default credential found → Remove or replace with secure default
  - Hardcoded secret → Use environment variable or secret manager

### check:jwt-policy

- **Owner Domain**: Security Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: CI
- **Evidence Output**: JWT dependency report, policy compliance
- **Failure Interpretation**:
  - Policy violation → Update JWT implementation
  - Dependency issue → Update to compliant JWT library
  - Configuration error → Fix JWT configuration

### check:license-policy

- **Owner Domain**: Security Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: CI
- **Evidence Output**: License report, policy compliance
- **Failure Interpretation**:
  - License violation → Replace with compliant library
  - Missing license → Add license to dependency
  - Policy error → Update license policy

---

## Documentation and Evidence Commands

### check:doc-truth

- **Owner Domain**: Documentation Team
- **Cost**: Medium
- **Scope**: Repo-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Documentation truth report, claim validation
- **Failure Interpretation**:
  - Doc mismatch → Update documentation to match code
  - Claim violation → Fix claim or code
  - Missing doc → Add required documentation

### check:current-state-claims

- **Owner Domain**: Documentation Team
- **Cost**: Medium
- **Scope**: Repo-level
- **Execution Cadence**: CI
- **Evidence Output**: Current state validation, claim evidence report
- **Failure Interpretation**:
  - Claim false → Update claim or fix implementation
  - Evidence missing → Add evidence or remove claim
  - State drift → Update state documentation

### check:doc-claims-evidence

- **Owner Domain**: Documentation Team
- **Cost**: Medium
- **Scope**: Repo-level
- **Execution Cadence**: CI
- **Evidence Output**: Doc claims validation, evidence linkage report
- **Failure Interpretation**:
  - Missing evidence → Add evidence for claim
  - Invalid evidence → Fix evidence or remove claim
  - Broken linkage → Fix evidence linkage

---

## Quality and Testing Commands

### check:test-authenticity

- **Owner Domain**: Quality Team
- **Cost**: High
- **Scope**: Repo-level
- **Execution Cadence**: CI, pre-release
- **Evidence Output**: Test authenticity report, mock analysis
- **Failure Interpretation**:
  - Fake test → Replace with real test
  - Mock abuse → Reduce mocking, test real behavior
  - Test theater → Remove or fix test

### check:production-readiness

- **Owner Domain**: Quality Team
- **Cost**: High
- **Scope**: Repo-level
- **Execution Cadence**: Pre-release
- **Evidence Output**: Production readiness report, quality gate results
- **Failure Interpretation**:
  - Readiness failure → Fix blocking issues
  - Quality gate failure → Fix quality issues
  - Missing validation → Add required validation

### check:deprecated-imports

- **Owner Domain**: Platform Core Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: CI
- **Evidence Output**: Deprecated import report, migration path
- **Failure Interpretation**:
  - Deprecated import → Migrate to new API
  - Missing migration → Add migration guide
  - Breaking change → Update deprecation policy

### check:deprecated-packages

- **Owner Domain**: Platform Core Team
- **Cost**: Low
- **Scope**: Repo-level
- **Execution Cadence**: CI
- **Evidence Output**: Deprecated package report, usage analysis
- **Failure Interpretation**:
  - Deprecated package → Migrate to new package
  - Usage found → Update to non-deprecated package
  - Missing alternative → Provide migration path

---

## Phase Gates

### check:phase0

- **Owner Domain**: Platform Core Team
- **Cost**: High
- **Scope**: Repo-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 0 validation report, foundation alignment
- **Failure Interpretation**:
  - Foundation misalignment → Fix registry, workspace, or manifests
  - Configuration error → Fix configuration files
  - Missing evidence → Ensure all Phase 0 checks pass

### check:phase1

- **Owner Domain**: Kernel Team
- **Cost**: High
- **Scope**: Kernel-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 1 validation report, contract hardening
- **Failure Interpretation**:
  - Contract violation → Fix Kernel contracts
  - Boundary ambiguity → Clarify boundaries
  - Failure model error → Enrich failure classification

### check:phase2

- **Owner Domain**: Kernel Team
- **Cost**: High
- **Scope**: Kernel-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 2 validation report, execution semantics
- **Failure Interpretation**:
  - Execution error → Fix lifecycle execution
  - Adapter error → Fix adapter implementation
  - Classification error → Improve failure classification

### check:phase3

- **Owner Domain**: Digital Marketing Team
- **Cost**: High
- **Scope**: Product-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 3 validation report, DMOS completeness
- **Failure Interpretation**:
  - Workflow error → Fix Digital Marketing workflows
  - Connector error → Fix connector implementation
  - Reporting error → Fix reporting implementation

### check:phase4

- **Owner Domain**: PHR Team
- **Cost**: High
- **Scope**: Product-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 4 validation report, PHR completeness
- **Failure Interpretation**:
  - Healthcare gate error → Fix healthcare gate implementation
  - Workflow error → Fix PHR workflows
  - Compliance error → Fix healthcare compliance

### check:phase5

- **Owner Domain**: Studio Team
- **Cost**: High
- **Scope**: Platform-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 5 validation report, Studio workflow
- **Failure Interpretation**:
  - Workflow error → Fix Studio workflow
  - Acquisition error → Fix source acquisition
  - Artifact error → Fix artifact pipeline

### check:phase6

- **Owner Domain**: Data Cloud/YAPPC Teams
- **Cost**: High
- **Scope**: Platform-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 6 validation report, provider integration
- **Failure Interpretation**:
  - Provider error → Fix provider implementation
  - Boundary leak → Fix provider boundary
  - Integration error → Fix integration contracts

### check:phase7

- **Owner Domain**: Platform Core Team
- **Cost**: High
- **Scope**: Repo-level
- **Execution Cadence**: Pre-release, on-demand
- **Evidence Output**: Phase 7 validation report, product shape
- **Failure Interpretation**:
  - Shape error → Fix product shape configuration
  - Capability error → Fix capability matrix
  - Generality error → Improve Kernel generality

### check:phase8

- **Owner Domain**: Platform Core Team
- **Cost**: High
- **Scope**: Repo-level
- **Execution Cadence**: Pre-release
- **Evidence Output**: Phase 8 validation report, no-regression gate
- **Failure Interpretation**:
  - Regression error → Fix regression
  - Coherence error → Fix platform/product coherence
  - Gate failure → Fix blocking issues

### check:world-class-platform-readiness

- **Owner Domain**: Platform Core Team
- **Cost**: High
- **Scope**: Repo-level
- **Execution Cadence**: Pre-release
- **Evidence Output**: World-class readiness report, all phase gates
- **Failure Interpretation**:
  - Readiness failure → Fix all blocking issues
  - Phase failure → Fix failed phase
  - Quality gate failure → Fix quality issues

---

## Full Repo Test

### check:full-repo-test

- **Owner Domain**: Platform Core Team
- **Cost**: Very High
- **Scope**: Repo-level
- **Execution Cadence**: Pre-release, nightly
- **Evidence Output**: Full repo test report, all validation results
- **Failure Interpretation**:
  - Test failure → Fix failing test
  - Validation failure → Fix validation issue
  - Integration error → Fix integration issue

---

## Command Usage Guidelines

### When to Run Commands

**Pre-commit (Low Cost)**: Run on every commit
- check:product-registry
- check:domain-registry
- check:secret-default-credentials
- check:jwt-policy
- check:license-policy
- check:deprecated-imports
- check:deprecated-packages

**CI (Medium/High Cost)**: Run on every PR
- All architecture and boundary checks
- All kernel lifecycle checks
- All toolchain and adapter checks
- All product-specific checks
- All security and compliance checks
- All documentation and evidence checks

**Pre-release (High/Very High Cost)**: Run before release
- All phase gates (phase0-phase8)
- check:world-class-platform-readiness
- check:full-repo-test
- All artifact and Studio E2E checks

**On-demand**: Run as needed
- Product lifecycle pilot checks (smoke tests)
- Product readiness checks
- Specific validation checks

### Failure Triage

1. **Immediate Blockers**: Fix before merge
   - Security violations (secrets, default credentials)
   - Registry drift
   - Boundary violations
   - Contract violations

2. **Warnings**: Document and track
   - Deprecated imports/packages
   - Documentation drift
   - Non-critical configuration issues

3. **Known Issues**: Allow with justification
   - Production stubs in allowlist
   - Temporary workarounds with tickets
   - Migration-in-progress items

### Evidence Retention

- **Latest Evidence**: Keep latest run for each command
- **Release Evidence**: Retain permanently for releases
- **Dev Evidence**: Clean by age (30 days) and size (1GB)
- **Sensitive Data**: Redact absolute paths, secrets, tenant data

---

## Maintenance

This taxonomy should be updated when:
- New check commands are added
- Command ownership changes
- Evidence output changes
- Failure interpretation changes
- Execution cadence changes

Maintain alphabetical ordering within sections for consistency.
