# Product Development Guide: Onboarding onto the Kernel

> **Audience**: Product engineering teams building on Ghatana's platform kernel.
> **Last Updated**: 2026-05-03

## 1. Overview

Products integrate with the kernel through three mechanisms:

1. **Domain Pack Model** — supply boundary policy rules and compliance rule packs.
2. **Plugin Binding** — wire product-specific implementations to plugin SPIs.
3. **Kernel Bridge** — use the `AbstractKernelBridge` extension pattern for adapter integration.

Products must **never import kernel implementation classes** directly. Only kernel-owned
public interfaces and ports are part of the stable API surface.

The canonical ownership map lives in [KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md](./KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md).

## 2. Domain Pack Model

### 2.1 BoundaryPolicyStore

Each product supplies its own `BoundaryPolicyStore` implementation:

```java
/**
 * @doc.type class
 * @doc.purpose Supplies boundary rules for Product-X
 * @doc.layer product
 * @doc.pattern SPI
 */
public class ProductXBoundaryPolicyStore implements BoundaryPolicyStore {

    private static final List<BoundaryPolicyRule> RULES = List.of(
        // ... product-specific rules ...
        // LAST RULE: default-deny
        BoundaryPolicyRule.builder()
            .ruleId("PX-BP-999")
            .sourceScopePattern("**")
            .targetScopePattern("product-x.*")
            .resourcePattern("**")
            .actions("*")
            .effect(Effect.DENY)
            .build()
    );

    @Override
    public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
        return RULES;
    }
}
```

**Rules:**
- The last rule must always be a default-deny covering `"**"` source and your product scope.
- Rule IDs must be prefixed with your product code (e.g., `PX-BP-001`).
- Read operations requiring consent must set `.requiresConsent(true)`.
- Operations requiring an audit trail must set `.requiresAudit(true)`.

### 2.2 ComplianceRulePack

Each product supplies compliance rule packs registered with `CompliancePlugin`:

```java
public final class ProductXComplianceRulePack {

    public static final String DOMAIN_INTEGRITY = "PX_DOMAIN_INTEGRITY";

    private ProductXComplianceRulePack() { /* non-instantiable */ }

    public static List<CompliancePlugin.ComplianceRule> domainIntegrityRules() {
        return List.of(
            new CompliancePlugin.ComplianceRule(
                "PX-DI-001",
                DOMAIN_INTEGRITY,
                "All domain records must have a creation timestamp",
                CompliancePlugin.Severity.HIGH,
                "$.createdAt != null"
            )
            // ...
        );
    }
}
```

**Rules:**
- Rule IDs must be prefixed with your product code.
- Rule set ID constants must be unique across the platform.
- Packs are registered at product startup — never at kernel boot time.

### 2.3 Gradle Validation Tasks

Add validation tasks to your product `build.gradle.kts`:

```kotlin
tasks.register("validateDomainPackManifest") {
    group = "verification"
    description = "Validates that the domain pack manifest is present and well-formed."
    doLast {
        // Check required fields in plugin.json / domain-pack.json
    }
}

tasks.register("validatePolicyPack") {
    group = "verification"
    description = "Validates BoundaryPolicyStore: non-empty, last rule is default-deny."
    doLast { /* ... */ }
}

tasks.register("validateComplianceRulePack") {
    group = "verification"
    description = "Validates compliance rule packs: non-empty rules, unique IDs."
    doLast { /* ... */ }
}

tasks.named("check") {
    dependsOn("validateDomainPackManifest", "validatePolicyPack", "validateComplianceRulePack")
}
```

## 3. Plugin Binding

Products supply SPI implementations for platform plugins:

```java
// Products implement plugin SPIs; the kernel wires them at startup
public class ProductXRiskModelProvider implements RiskPlugin.RiskModelProvider {
    @Override
    public Promise<RiskPlugin.RiskScore> score(String modelId, Map<String, Object> features) {
        // Product-specific ML/rules
    }
}
```

Never hardcode product logic inside a platform plugin module.

## 4. Kernel Bridge Pattern

Products connecting to the kernel use `AbstractKernelBridge` via their adapter implementations:

```java
public class ProductXKernelAdapterImpl
        extends AbstractKernelBridge
        implements ProductXKernelAdapter {

    private final ProductXClient client;

    public ProductXKernelAdapterImpl(
            ProductXClient client,
            BridgeAuthorizationService auth,
            BridgeAuditEmitter auditor,
            BridgeHealthIndicator health) {
        super("product-x-kernel-bridge", auth, auditor, health);
        this.client = Objects.requireNonNull(client);
        markStarted();
    }

    @Override
    public Promise<DataResult> fetchRecord(BridgeContext ctx, String recordId) {
        requireStarted();
        return checkAuthorized(ctx, "record:" + recordId, "read")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized"));
                return executeWithRetry(
                    "fetchRecord", ctx, "record:" + recordId, "read",
                    () -> client.fetch(recordId));
            });
    }
}
```

**Key points:**
- Pass `BridgeContext` through every bridge operation.
- Always call `requireStarted()` at the top of adapter methods.
- Use `checkAuthorized()` before performing sensitive operations.
- Use `executeWithRetry()` for operations that may experience transient failures.
- Use `redact()` before including metadata in log messages.

## 5. Context Propagation

Every product entrypoint must preserve the same platform context contract from API edge
to repository, workflow, and bridge boundaries.

### 5.1 Shared Data-Access Contract

Every product-owned mutation or sensitive query must carry:

| Field | Required for | Notes |
|---|---|---|
| `tenantId` | all reads and writes | Multi-tenant isolation must fail closed |
| `principalId` | all reads and writes | Used for authorization and audit attribution |
| `correlationId` | all reads and writes | Required for distributed tracing and diagnostics |
| `idempotencyKey` | writes | Required for at-most-once mutation semantics |
| `auditClassification` | sensitive reads and writes | Product chooses classification values; platform enforces presence |
| `dataOwnerScope` | all reads and writes | User/patient/account/workspace scope must be explicit |

The repository-level CI gate `check:data-access-contract` verifies that each audited
product exposes these fields through its canonical entrypoint or operation-context
implementation.

### 5.2 Observability Conformance

Every product API or bridge flow must:

- emit a trace or correlation identifier,
- use tenant-safe structured logging,
- register metrics/tracing bootstrap at the product entrypoint, and
- emit audit events for sensitive operations.

The repository-level CI gate `check:observability-conformance` validates these
requirements against the canonical API/bootstrap entrypoints for PHR, Finance,
Digital Marketing, and FlashIt.

### 5.3 In-Memory Policy Store Guardrail

`InMemoryBoundaryPolicyStore` is limited to kernel tests, examples, and local-only
bootstrap scenarios. Product main sources and launchers must not wire it into
runtime composition. CI enforces this via `check:inmemory-policy-store-usage`.

### 5.4 Route Entitlement Contract

Every product UI surface must expose a canonical route manifest that is owned by
the product but shaped by the Kernel shell contract. The manifest must declare
`path`, `label`, `minimumRole`, `personas`, `tiers`, and route/action metadata
that can be returned from backend entitlement APIs. Frontend navigation and
direct URL access must be derived from that manifest rather than hardcoded link
arrays in product-local shells. CI enforces this via
`check:route-entitlement-contracts`.

---

## 6. Reference Consumers

> **PHR and Finance are reference consumers of the platform kernel and plugin framework — they are NOT platform defaults.**

PHR (Personal Health Records) and Finance were the first products to adopt the kernel + plugin model and serve as concrete validation examples in this guide. However, the platform is **product-agnostic** by design:

- **PHR** demonstrates regulated-healthcare adoption: consent management, FHIR interop, HIPAA-aligned compliance rule packs, and subject-record boundary policies. Its domain packs (`products/phr/domain-pack-manifest.yaml`, `products/phr/policy-packs/healthcare-boundary-policy.yaml`) show the pattern, not the template.
- **Finance** demonstrates regulated-finance adoption: trade processing, ledger integration, SOX-aligned compliance rule packs, and market-data boundary policies. Its domain packs (`products/finance/domain-pack-manifest.yaml`, `products/finance/policy-packs/trading-boundary-policy.yaml`) show the pattern, not the template.
- **Any other product** follows the same pattern with its own domain-specific packs. See `docs/examples/product-on-kernel/` for a neutral example using a fictional "Nexus Logistics" product.

**What this means in practice:**

| What you see | What it means |
|---|---|
| PHR compliance rules in `products/phr/` | PHR's own rules, not a platform default |
| Finance boundary policies in `products/finance/` | Finance's own policies, not a platform template |
| `PhrBoundaryPolicyStore` in docs examples | Illustrates the SPI pattern; use your own prefix (e.g. `NexusBoundaryPolicyStore`) |
| `FIN-BP-001` rule IDs in CAPABILITY_MATRIX | Finance-scoped identifiers; your product uses its own prefix |

Platform plugins (`plugin-audit-trail`, `plugin-compliance`, etc.) contain **no PHR or Finance domain terms** in their main sources. The `checkPluginPurity` Gradle gate enforces this on every build.

Every bridge call must carry a `BridgeContext` with:

| Field | Purpose |
|-------|---------|
| `tenantId` | Tenant isolation — **required** |
| `principalId` | Audit and authorization — defaults to "anonymous" |
| `correlationId` | Distributed tracing — defaults to "none" |
| `idempotencyKey` | At-most-once writes — nullable for reads |

```java
BridgeContext ctx = BridgeContext.builder()
    .tenantId(request.getTenantId())
    .principalId(securityContext.getPrincipalId())
    .correlationId(MDC.get("correlationId"))
    .idempotencyKey(command.getIdempotencyKey()) // null for reads
    .build();
```

## 7. Testing Products

### Pack contract tests (required)

Every product must have a `*PackContractTest` verifying:

1. `BoundaryPolicyStore.loadRules()` returns non-empty, well-formed rules.
2. Last rule is default-deny.
3. Key rules have expected effects (e.g., sensitive read requires consent + audit).
4. Compliance rule packs are non-empty with prefixed rule IDs.
5. Store does not extend any kernel implementation class (`getSuperclass() == Object.class`).

### Bridge integration tests (required for Phase 4 completeness)

Bridge integration tests must cover:
- Successful operation (authorized, healthy outcome)
- Denied operation (unauthorized, DENIED audit event emitted)
- Transient failure with retry (bounded retries, degraded health, then unhealthy)
- Timeout path (if the adapter supports configurable timeouts)

## 8. Definition of Done for Product Packs

- [ ] `BoundaryPolicyStore` implemented with default-deny as last rule
- [ ] Ownership declared in the Kernel/Product Responsibility Matrix
- [ ] `ComplianceRulePack` classes with prefixed rule IDs
- [ ] Validation Gradle tasks added and wired to `check`
- [ ] `*PackContractTest` written and passing
- [ ] No kernel implementation classes extended directly
- [ ] Pack classes use only kernel public interfaces (`BoundaryPolicyStore`, `BoundaryPolicyRule`, etc.)
- [ ] Manifest declares `kernelCapabilitiesConsumed`, `pluginsConsumed`, `bridgesConsumed`, `domainPacksProvided`, `uiSurfaces`, `runtimeServices`, and `dataSensitivity`

---

*See also: [KERNEL_PURITY_RULES.md](KERNEL_PURITY_RULES.md), [PLUGIN_PURITY_RULES.md](PLUGIN_PURITY_RULES.md)*
