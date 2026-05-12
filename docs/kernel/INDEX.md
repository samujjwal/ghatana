# Kernel Platform Contract Index

This is the canonical index for all Kernel platform contracts, governance documents, and architectural decisions.

## Core Kernel Contracts

### Platform Contracts
- [Capability Schema](capability-schema.yaml) - Schema for Kernel capabilities
- [Capability Matrix](CAPABILITY_MATRIX.md) - Matrix of Kernel capabilities across products
- [Product Kernel Capability Map](PRODUCT_KERNEL_CAPABILITY_MAP.md) - Product-specific capability mapping
- [Kernel Module Extension Plugin Decision Guide](KERNEL_MODULE_EXTENSION_PLUGIN_DECISION_GUIDE.md) - Guidance for extending Kernel modules

### Product-Kernel Interface
- [Product Development Guide](PRODUCT_DEVELOPMENT_GUIDE.md) - How to develop products against Kernel
- [Product Kernel Responsibility Matrix](KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md) - Clear separation of responsibilities
- [Product Kernel Audit Progress](PRODUCT_KERNEL_AUDIT_PROGRESS.md) - Audit progress tracking

### Build and CI
- [Build](BUILD.md) - Kernel build process and conventions
- [CI Gate Matrix](CI_GATE_MATRIX.md) - CI/CD gate definitions and requirements
- [Coverage Matrix](COVERAGE_MATRIX.md) - Test coverage requirements

## Governance and Standards

### Purity Rules
- [Kernel Purity Rules](KERNEL_PURITY_RULES.md) - Rules for maintaining Kernel purity
- [Plugin Purity Rules](PLUGIN_PURITY_RULES.md) - Rules for Kernel plugins

### Quality Gates
- [Kernel Promotion Criteria](KERNEL_PROMOTION_CRITERIA.md) - Criteria for promoting changes to Kernel
- [Baseline Hardening Report](BASELINE_HARDENING_REPORT.md) - Security hardening baselines

### Architecture
- [Monorepo Architecture](MONOREPO_ARCHITECTURE.md) - Overall monorepo architecture

## Examples

- [examples/](examples/) - Example implementations and patterns

## Path References

All Kernel contracts should use the following canonical path patterns:
- Platform contracts: `platform/java/{module}/src/main/java/com/ghatana/platform/{module}/`
- TypeScript packages: `platform/typescript/{package}/src/`
- Product integration: `products/{product}/`

## Maintenance

When adding new Kernel contracts:
1. Add the contract to this index
2. Update the capability schema if needed
3. Ensure path references use canonical patterns
4. Run `scripts/check-doc-truth.mjs` to validate claims
