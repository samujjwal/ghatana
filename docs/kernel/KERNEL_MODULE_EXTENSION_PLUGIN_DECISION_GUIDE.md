# Kernel Module vs Extension vs Plugin — Decision Guide

> **Status:** Canonical reference · April 2026
> **Audience:** Platform engineers, product engineers adding new features to Ghatana products
> **Last Updated:** 2026-04-16

---

## 1. Quick Reference Table

| Criterion | `KernelModule` | `KernelExtension` | `KernelPlugin` | `KernelLifecycleAware` |
|-----------|---------------|-------------------|----------------|------------------------|
| **Interface** | `KernelModule` | `KernelExtension` | `KernelPlugin extends KernelModule` | `KernelLifecycleAware` |
| **Package** | `kernel.module` | `kernel.extension` | `kernel.plugin` | `kernel.service` |
| **Owns a full domain** | ✅ Yes | ❌ No | ✅ Yes | ❌ No |
| **Registers capabilities** | ✅ Yes | ✅ Contributes to host | ✅ Yes | ❌ No |
| **Hot-swap / uninstall at runtime** | ❌ No | ❌ No | ✅ Yes | ❌ No |
| **Needs `KernelContext`** | ✅ Yes | ✅ Via host | ✅ Yes | Service-injection only |
| **Stand-alone lifecycle** | Full init/start/stop | Piggybacks on host | Full + install/uninstall/reload | start/stop only |
| **Typical scope** | A product (PHR, Finance) or platform layer | Domain enrichment add-on | Cross-product platform feature | HTTP server, scheduler, adapter |

---

## 2. `KernelModule` — Domain Composition Root

### When to use
Use `KernelModule` when you are building or owning **a complete product domain or platform layer** that:
- Has a stable identity in the product catalogue (e.g., `finance`, `phr`, `aep`)
- Owns its own capabilities, lifecycle, and dependency declarations
- Registers services into the `KernelContext` for other modules to consume
- Is loaded at product start-time (not hot-swapped)

### When NOT to use
- Do not use `KernelModule` for a small domain enrichment that just adds one or two capabilities to an already-existing module.
- Do not use `KernelModule` for a platform-level adapter that does not represent a product capability.

### Abstract base
Use `AbstractKernelModule` (available in `kernel-core`) as the base class when you need standard init/start/stop wiring. Only override what is needed; avoid duplicating wiring logic.

### Example
```java
/**
 * @doc.type class
 * @doc.purpose Finance product entry point — owns trading, risk, and compliance capabilities.
 * @doc.layer product
 * @doc.pattern Module
 */
public final class FinanceProductModule implements KernelModule {
    @Override public String getModuleId() { return "finance"; }
    @Override public Set<KernelCapability> getCapabilities() { return FinanceCapabilities.ALL; }
    @Override public Set<KernelDependency> getDependencies() { return ...; }
    @Override public void initialize(KernelContext context) { ... }
}
```

---

## 3. `KernelExtension` — Domain Enrichment Add-on

### When to use
Use `KernelExtension` when you need to **add behaviour to an existing module without creating a new module**. An extension:
- Piggybacks on a host `KernelModule`'s lifecycle
- Contributes additional capabilities to that host
- Does not own a full domain

### Lifecycle
Extensions are invoked by the hosting module during transitions:
- `onModuleInitialized(KernelContext)` — after module init
- `onModuleStarted(KernelContext)` — after module start
- `onModuleStopped(KernelContext)` — before module stop

### Typical patterns
| Scenario | Extension name (example) |
|----------|--------------------------|
| Healthcare consent rules on top of core kernel | `HealthcareConsentKernelExtension` |
| Dual-calendar support added to scheduling | `DualCalendarKernelExtension` |
| Finance-specific audit policy enrichment | `FinanceAuditKernelExtension` |

### Example
```java
/**
 * @doc.type class
 * @doc.purpose Adds healthcare consent enforcement to the kernel's data-access layer.
 * @doc.layer product
 * @doc.pattern Extension
 */
public final class HealthcareConsentKernelExtension implements KernelExtension {
    @Override public String getExtensionId() { return "healthcare-consent"; }
    @Override public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(KernelCapability.of("consent.healthcare"));
    }
    @Override public void onModuleInitialized(KernelContext context) {
        context.registerService(ConsentPolicyEvaluator.class,
                new DefaultConsentPolicyEvaluator());
    }
    // ...
}
```

---

## 4. `KernelPlugin` — Hot-Swappable Cross-Product Module

### When to use
Use `KernelPlugin` (which extends `KernelModule`) when the feature:
- Needs to be **installed, uninstalled, or reloaded at runtime** without kernel restart
- Is a cross-product, reusable platform capability (not tied to a single product)
- Ships with a `PluginManifest` that declares exported and required contracts

### What `KernelPlugin` adds over `KernelModule`
| Operation | `KernelModule` | `KernelPlugin` |
|-----------|---------------|----------------|
| init/start/stop | ✅ | ✅ |
| `install()` | ❌ | ✅ — one-time setup (schema, config) |
| `uninstall()` | ❌ | ✅ — clean teardown |
| `reload()` | ❌ | ✅ — config refresh without service interruption |
| Contract declarations | via capabilities | via exported/required contract names |
| Health check | N/A | `getHealthStatus()` |

### Platform-provided plugins (under `platform-plugins/`)
| Plugin | Purpose |
|--------|---------|
| `plugin-audit-trail` | Immutable audit logging (in-memory + durable JDBC variants) |
| `plugin-ledger` | Double-entry ledger with idempotency |
| `plugin-consent` | Cross-product consent management |
| `plugin-compliance` | Regulatory compliance checks |
| `plugin-fraud-detection` | Real-time fraud scoring |
| `plugin-risk-management` | Risk factor evaluation |

### Example
```java
/**
 * @doc.type class
 * @doc.purpose Finance-specific kernel plugin for AEP-central registry.
 * @doc.layer product
 * @doc.pattern Plugin
 */
public class FinanceKernelPlugin implements KernelPlugin {
    @Override public PluginManifest getManifest() { return manifest; }
    @Override public Set<String> getExportedContracts() { return Set.of("finance.trade.api"); }
    @Override public Set<String> getRequiredContracts() { return Set.of("kernel.audit"); }
    @Override public Promise<Void> install() { /* first-time schema setup */ }
    @Override public Promise<Void> uninstall() { /* cleanup */ }
    @Override public Promise<Void> reload() { /* config refresh */ }
}
```

---

## 5. `KernelLifecycleAware` — Service Start/Stop Contract

### When to use
Use `KernelLifecycleAware` for services that **need lifecycle management but are NOT modules or plugins**:
- HTTP servers bound inside a product (e.g., `FinanceHttpServer`, `PhrFhirR4Server`)
- Scheduled background jobs (e.g., `PhrRetentionScheduler`)
- Adapters that must open/close connections (e.g., `FinanceBFF`)

### What it provides
```java
public interface KernelLifecycleAware {
    Promise<Void> start();
    Promise<Void> stop();
    boolean isHealthy();
    String getName();
}
```

### Example
```java
public final class PhrRetentionScheduler implements KernelLifecycleAware {
    @Override public Promise<Void> start() { /* schedule retention jobs */ }
    @Override public Promise<Void> stop() { /* cancel scheduled jobs */ }
    @Override public boolean isHealthy() { return running; }
    @Override public String getName() { return "phr-retention-scheduler"; }
}
```

### Registration
Register `KernelLifecycleAware` instances with the kernel context so the kernel can manage their lifecycle:
```java
context.registerService(FinanceHttpServer.class, httpServer);
httpServer.start().whenComplete(...);
```

---

## 6. Decision Flowchart

```
Is this a full, named product domain with its own capabilities?
  └─ YES → KernelModule  (or AbstractKernelModule)

Is this enriching an existing module without owning a domain?
  └─ YES → KernelExtension

Is this a cross-product reusable capability that must support
hot-swap (install/uninstall/reload) at runtime?
  └─ YES → KernelPlugin (extends KernelModule)

Is this a service that just needs start/stop lifecycle but
is NOT a domain owner, extension, or plugin?
  └─ YES → KernelLifecycleAware
```

---

## 7. Naming Conventions

| Type | Naming Pattern | Examples |
|------|---------------|---------|
| `KernelModule` | `{ProductOrDomain}Module` or `{ProductOrDomain}KernelModule` | `PhrKernelModule`, `FinanceProductModule` |
| `KernelExtension` | `{Domain}{Feature}KernelExtension` | `HealthcareConsentKernelExtension`, `DualCalendarKernelExtension` |
| `KernelPlugin` | `{ProductOrDomain}KernelPlugin` | `FinanceKernelPlugin`, `AepKernelPlugin` |
| `KernelLifecycleAware` | Domain-specific noun — describes what it IS | `FinanceHttpServer`, `PhrRetentionScheduler` |

---

## 8. Common Mistakes

### ❌ Creating a `KernelModule` for a single-file adapter
If you are only wrapping one service with start/stop, use `KernelLifecycleAware`.

### ❌ Creating a `KernelPlugin` for something that will never be hot-swapped
Plugins carry extra interface surface (install/uninstall/reload/manifest). If your feature is always deployed as part of a stable product, use `KernelModule` or `KernelLifecycleAware`.

### ❌ Extending `KernelModule` when adding to an existing module
If PHR already exists and you want to add healthcare consent enforcement, create a `KernelExtension`, not a second `KernelModule`.

### ❌ Using `KernelExtension` to own independent domain state
Extensions should not hold long-lived stateful domain models independently. That state belongs in the hosting module's domain.

---

## 9. Platform Plugin Choice: Standard vs Durable

Platform plugins (`plugin-audit-trail`, `plugin-ledger`, `plugin-consent`) come in two variants:

| Variant | Class Prefix | Backing | Use When |
|---------|-------------|---------|----------|
| In-memory | `Standard` | HashMap/CopyOnWriteArrayList | Unit tests, prototyping, non-critical local dev |
| JDBC-backed | `Durable` | PostgreSQL / H2-compatible SQL | Production, staging, compliance-critical paths |

**Rule:** Production product wiring must use `Durable*Plugin` variants. Standard variants are only for test harnesses and exploratory usage.

---

_See also: [KERNEL_PLUGIN_MULTI_PRODUCT_EXECUTION_PLAN_2026-04-16.md](KERNEL_PLUGIN_MULTI_PRODUCT_EXECUTION_PLAN_2026-04-16.md) for the full multi-product kernel design execution history._
