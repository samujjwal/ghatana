# Kernel Platform: Product-Category Capability Map

> **Document:** `docs/architecture/PRODUCT_KERNEL_CAPABILITY_MAP.md`
> **Audience:** Platform architects, product engineers, kernel contributors
> **Purpose:** Decision guide mapping product-categories to the kernel support matrix — which plugins, bridge extensions, and contracts apply.

---

## 1. Product Categories

Ghatana products fall into three primary categories, each with distinct kernel integration needs.

| Category | Products | Defining Characteristics |
|----------|----------|--------------------------|
| **Regulated Healthcare** | PHR | Patient data, FHIR interop, consent management, HIPAA/Nepal-2081 compliance |
| **Regulated Finance** | Finance | Trade processing, risk management, billing ledger, SOX/regulatory reporting |
| **Agent / Runtime** | AEP, YAPPC | AI agent deployment, event streaming, code generation, autonomous pipelines |
| **Data Infrastructure** | Data-Cloud | Multi-tier storage, schema management, event log, tenant isolation |

---

## 2. Kernel Support Matrix

### 2.1 Platform Plugins Required by Category

| Plugin | Regulated Healthcare | Regulated Finance | Agent / Runtime | Data Infrastructure |
|--------|:--------------------:|:-----------------:|:---------------:|:-------------------:|
| `plugin-audit-trail` | ✅ Required | ✅ Required | ✅ Recommended | ✅ Recommended |
| `plugin-consent` | ✅ Required | — | ⚠️ Optional | — |
| `plugin-compliance` | ✅ Required | ✅ Required | — | ⚠️ Optional |
| `plugin-billing-ledger` | ⚠️ Optional | ✅ Required | — | — |
| `plugin-fraud-detection` | — | ✅ Required | ⚠️ Optional | — |
| `plugin-risk-management` | — | ✅ Required | — | — |
| `plugin-human-approval` | ✅ Required | ✅ Required | ⚠️ Optional | — |

### 2.2 Kernel Bridge Extensions by Category

| Bridge Extension | Regulated Healthcare | Regulated Finance | Agent / Runtime | Data Infrastructure |
|-----------------|:--------------------:|:-----------------:|:---------------:|:-------------------:|
| `data-cloud-kernel-bridge` | ✅ Required | ✅ Required | ✅ Required | — (owner) |
| `aep-kernel-bridge` | ⚠️ Optional | ⚠️ Optional | ✅ Required | — |
| `yappc-kernel-bridge` | — | — | ✅ Required | — |

### 2.3 Kernel Core Contracts

| Contract | Regulated Healthcare | Regulated Finance | Agent / Runtime | Data Infrastructure |
|----------|:--------------------:|:-----------------:|:---------------:|:-------------------:|
| `DataCloudKernelAdapter` | ✅ Direct use | ✅ Direct use | ✅ Indirect | — |
| `AepKernelAdapter` | Event publishing | ✅ Event publishing | ✅ Agent runtime | — |
| `KernelTenantContext` | ✅ Required | ✅ Required | ✅ Required | ✅ Required |
| `CrossScopeAuditService` | ✅ Required | ✅ Required | ⚠️ Optional | ⚠️ Optional |
| `SecretProvider` | ✅ Required | ✅ Required | ✅ Required | ✅ Required |

---

## 3. per-Category Integration Guidance

### 3.1 Regulated Healthcare (PHR)

**Kernel integration pattern:**
```java
// 1. PHR kernel module declares capabilities and required deps
public class PhrKernelModule extends AbstractKernelModule {
    @Override
    public Set<KernelCapability> getRequiredCapabilities() {
        return Set.of(
            DataCloudBridgeCapabilities.DATA_CLOUD_STORAGE,
            // HIPAA: consent management required before patient data access
            KernelCapability("data-cloud.storage", ...)
        );
    }
}

// 2. PHR services obtain adapters from kernel context
public class PhrPatientDataService extends AbstractDataService {
    public PhrPatientDataService(KernelContext context) {
        super(context); // gets DataCloudKernelAdapter from context
    }
}
```

**Mandatory plugins:** audit-trail, consent, compliance  
**Mandatory bridges:** data-cloud-kernel-bridge  
**Tenant isolation:** Must use `KernelTenantContext` for all data operations  
**Rule library:** Nepal Directive 2081 healthcare rules (KP-033)

---

### 3.2 Regulated Finance

**Kernel integration pattern:**
```java
// Finance depends on both risk and billing plugins
public class FinanceKernelModule extends AbstractKernelModule {
    @Override
    protected void afterInitialized(KernelContext context) {
        RiskManagementPlugin risk = context.getDependency(RiskManagementPlugin.class);
        BillingLedgerPlugin billing = context.getDependency(BillingLedgerPlugin.class);
        registerService(new FinanceRiskService(risk));
        registerService(new FinanceBillingService(billing));
    }
}
```

**Mandatory plugins:** audit-trail, compliance, billing-ledger, fraud-detection, risk-management  
**Mandatory bridges:** data-cloud-kernel-bridge  
**Tenant isolation:** Per-trade tenant scoping required  
**Rule library:** SOX compliance rules (KP-033)

---

### 3.3 Agent / Runtime (AEP, YAPPC)

**Kernel integration pattern:**
```java
// AEP provides a KernelExtension bridge (not a full module consumer)
AepKernelExtension aepBridge = new AepKernelExtension(prodAepClient);
hostModule.registerExtension(aepBridge);

// Other products get AEP capability via context
AepKernelAdapter aep = context.getDependency(AepKernelAdapter.class);
aep.publishEvent("analytics.stream", event);
```

**Platform bridges PROVIDED by this category:** `aep-kernel-bridge`, `yappc-kernel-bridge`  
**Platform bridges CONSUMED by this category:** data-cloud (for agent state/checkpoints)  
**Mandatory plugins:** audit-trail (agent action auditing)

---

### 3.4 Data Infrastructure (Data-Cloud)

**Kernel integration pattern:**
```java
// Data-Cloud provides the kernel bridge extension (not a module consumer)
DataCloudKernelExtension bridge = new DataCloudKernelExtension(liveDataCloudClient);
hostModule.registerExtension(bridge);
// All other products consume DataCloudKernelAdapter via context.getDependency(...)
```

**Platform bridges PROVIDED by this category:** `data-cloud-kernel-bridge`  
**Mandatory plugins:** audit-trail (schema changes, data mutations)

---

## 4. Dependency Direction Rules

```
kernel-core (no product knowledge)
    ↑
platform-plugins (platform-kernel aware, product-agnostic)
    ↑
data-cloud-kernel-bridge / aep-kernel-bridge / yappc-kernel-bridge
    ↑                                   ↑
products/data-cloud (provides bridge)   products/aep (provides bridge)
    ↑
products/phr, products/finance (consume bridges + plugins)
```

**Forbidden dependency directions:**
- `kernel-core` → any product
- `platform-plugins` → any product  
- `data-cloud-kernel-bridge` → any product (besides data-cloud:spi via DI)

---

## 5. Capability Declaration Checklist

When introducing a new product or product feature that uses a kernel capability:

1. **Declare required capabilities** in your `KernelModule.getRequiredCapabilities()`
2. **Request bridge extensions** in your launcher (not as compile-time deps)
3. **Use `context.getDependency()`** at runtime — not constructor injection of product types
4. **Register your own services** via `context.registerService()` for cross-product discovery
5. **Emit audit events** via `CrossScopeAuditService` for all regulated operations

---

*Last updated: 2026-04 | Maintained by: Platform Kernel Team*
