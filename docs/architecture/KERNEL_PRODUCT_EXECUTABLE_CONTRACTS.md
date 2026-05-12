# Kernel/Product Executable Contracts

This document consolidates Kernel and product documentation around executable contracts. All Kernel capabilities and product integrations are defined as executable contracts that can be validated automatically.

## Overview

Kernel provides shared platform capabilities through executable contracts. Products consume these capabilities by implementing the contract interfaces and passing contract validation gates.

## Kernel Capabilities Contracts

### Boundary Policy Evaluation
- **Contract**: `com.ghatana.kernel.policy.BoundaryPolicyStore`
- **Purpose**: Evaluates boundary policies for domain-level authorization
- **Validation**: Product must implement `BoundaryPolicyStore` with declared actions/resources
- **Gate**: `productConformanceCheck`

### Audit Trail
- **Contract**: `com.ghatana.kernel.audit.AuditService`
- **Purpose**: Provides auditable event logging for compliance
- **Validation**: Product must emit audit events for all mutations
- **Gate**: `productConformanceCheck`

### Tenant Context
- **Contract**: `com.ghatana.kernel.context.KernelContext`
- **Purpose**: Provides tenant-scoped context with explicit validation
- **Validation**: Product must provide explicit `tenantId` and `principalId`
- **Gate**: `productConformanceCheck`

## Platform Plugin Contracts

### Compliance Plugin
- **Contract**: `com.ghatana.plugin.compliance.CompliancePlugin`
- **Purpose**: Provides behavioral compliance rule evaluation
- **Validation**: Product must register rule sets with behavioral expressions
- **Gate**: `productConformanceCheck`

### Consent Plugin
- **Contract**: `com.ghatana.plugin.consent.ConsentPlugin`
- **Purpose**: Manages data processing consent
- **Validation**: Product must validate consent before processing
- **Gate**: `productConformanceCheck`

### Audit Trail Plugin
- **Contract**: `com.ghatana.plugin.audittrail.AuditTrailPlugin`
- **Purpose**: Provides durable audit trail storage
- **Validation**: Product must store audit events in the outbox
- **Gate**: `productConformanceCheck`

## Product Conformance Gates

### Product Pack Validation
- **Script**: `scripts/check-product-conformance.mjs`
- **Purpose**: Validates product manifest against Kernel requirements
- **Checks**:
  - Required manifest fields
  - Namespaced policy resources
  - Kernel capabilities consumed
  - Plugins consumed

### API Contract Conformance
- **Task**: `checkApiContractConformance`
- **Purpose**: Validates implemented routes match OpenAPI specification
- **Check**: Route contract drift detection

### Design System Conformance
- **Script**: `scripts/check-design-system-conformance.mjs`
- **Purpose**: Validates UI uses design-system tokens
- **Check**: Hardcoded style value detection

## Executable Contract Schema

All executable contracts follow this schema:

```yaml
contract:
  id: string
  version: string
  type: "kernel-capability" | "platform-plugin" | "product-integration"
  purpose: string
  implementation:
    interface: string
    requiredMethods: string[]
    validationRules: string[]
  gates:
    - task: string
      check: string
  dependencies:
    - contractId: string
```

## Usage

### For Product Teams
1. Review required contracts for your product in `config/canonical-product-registry.json`
2. Implement contract interfaces in your product code
3. Run `node scripts/check-product-conformance.mjs` to validate
4. Ensure all contract validation gates pass in CI

### For Kernel Teams
1. Define new contracts in `platform-kernel/kernel-core`
2. Add contract schema to `platform/contracts/`
3. Update `config/canonical-product-registry.json` with contract requirements
4. Run contract validation tests

## Contract Lifecycle

1. **Proposal**: Contract proposed in ADR
2. **Definition**: Contract schema defined in `platform/contracts/`
3. **Implementation**: Kernel implements contract
4. **Product Adoption**: Products implement contract interfaces
5. **Validation**: Contract validation gates added to CI
6. **Deprecation**: Contract deprecated via ADR

## References

- [ADR-001: Typed Agent Framework](../adr/ADR-001-typed-agent-framework.md)
- [ADR-002: DAG Pipeline Execution](../adr/ADR-002-dag-pipeline-execution.md)
- [ADR-003: Four-Tier Event Cloud](../adr/ADR-003-four-tier-event-cloud.md)
- [Canonical Product Registry](../../config/canonical-product-registry.json)
