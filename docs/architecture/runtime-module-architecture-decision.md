# Runtime Module Architecture Decision

## Context

The canonical product registry marks `runtimeModule: false` for PHR and Digital Marketing products. This document explains the architectural rationale and provides explicit lifecycle architecture proof.

## Decision

**Status**: `runtimeModule: false` is correct and intentional for PHR and Digital Marketing.

## Rationale

### Kernel Lifecycle Architecture

The Ghatana platform uses a **kernel-centric lifecycle architecture** where:

1. **Kernel Core** (`platform-kernel/kernel-core`) provides the lifecycle orchestration engine
2. **Kernel Bridge Adapters** provide product-specific integration points
3. **Business Products** (PHR, Digital Marketing) consume kernel lifecycle via their bridge adapters

### Why runtimeModule: false is Correct

#### 1. PHR (Personal Health Records)

- **Lifecycle Integration**: PHR uses `PhrKernelModule` which implements `KernelModule` interface
- **Bridge Pattern**: PHR's lifecycle is managed through the kernel bridge pattern, not a standalone runtime module
- **Healthcare Gates**: PHR enforces healthcare-specific gates (consent, PII classification, audit evidence, FHIR validation, tenant data sovereignty) through gate packs
- **Evidence Collection**: PHR publishes lifecycle events (`PhrLifecycleEvent`, `PhrAuditEvent`, `PhrConsentEvent`) to the kernel event bus for evidence collection

**Architecture Proof**:
- `products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java` - Implements `KernelModule` interface
- `products/phr/lifecycle/readiness-evidence.yaml` - Documents lifecycle readiness with healthcare gates
- `products/phr/lifecycle/gate-packs/` - Contains all required gate packs
- `.kernel/evidence/phr/phr-lifecycle-evidence-pack.json` - Proves lifecycle execution produces evidence

#### 2. Digital Marketing (DMOS)

- **Lifecycle Integration**: DMOS uses `DigitalMarketingKernelBridge` for kernel lifecycle integration
- **Bridge Pattern**: DMOS's lifecycle is managed through the kernel bridge adapter
- **Marketing Gates**: DMOS enforces marketing-specific gates (registry validation, manifest validation, lifecycle contract validation, bridge compliance, marketing consent boundary, persistence proof, Google Ads connector proof)
- **Test Coverage**: DMOS has comprehensive lifecycle gate tests in `DigitalMarketingLifecycleGateTest.java`

**Architecture Proof**:
- `products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/adapter/DigitalMarketingKernelBridge.java` - Kernel bridge implementation
- `products/digital-marketing/lifecycle/readiness-evidence.yaml` - Documents lifecycle readiness
- `products/digital-marketing/lifecycle/gate-packs/` - Contains all required gate packs
- `products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingLifecycleGateTest.java` - Proves gates are executable

### When runtimeModule: true Would Be Required

A product would need `runtimeModule: true` if it:

1. **Provides a custom runtime module** that other products consume
2. **Implements a lifecycle extension** beyond the standard kernel bridge pattern
3. **Requires runtime module registration** in the kernel module registry

Neither PHR nor Digital Marketing require this - they are **consumers** of the kernel lifecycle, not **providers** of runtime modules.

## Conclusion

The `runtimeModule: false` setting is correct for PHR and Digital Marketing because:

1. Both products use the **kernel bridge pattern** for lifecycle integration
2. Both products have **comprehensive gate packs** with executable tests
3. Both products have **lifecycle evidence** proving correct integration
4. Neither product provides a **custom runtime module** for other products to consume

## References

- `config/canonical-product-registry.json` - Product registry with conformance settings
- `products/phr/lifecycle/readiness-evidence.yaml` - PHR lifecycle readiness evidence
- `products/digital-marketing/lifecycle/readiness-evidence.yaml` - DMOS lifecycle readiness evidence
- `platform-kernel/kernel-core/` - Kernel lifecycle orchestration engine
