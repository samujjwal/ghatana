# Plugin Purity Rules

> **Status**: Enforced by `checkPluginPurity` Gradle tasks on platform-plugins modules.
> **Last Updated**: 2026-04-20

## 1. What Is Plugin Purity?

`platform-plugins` modules provide reusable, product-agnostic capabilities:
consent lifecycle, ledger operations, risk scoring, compliance rule evaluation, and observability.

**Plugin purity** means that plugin `src/main/java` trees must contain **no product-domain
identifiers** in production code. Plugins are loaded into the kernel at runtime and must be
capable of serving any product through the pack-driven model.

## 2. Banned Patterns in Plugin Main Source

| Pattern | Rationale |
|---------|-----------|
| `\bPHR\b` | Product acronym — PHR supplies its own compliance rule packs |
| `CLINICAL` | Product-domain terminology — must not appear in generic engine |
| `\bFinance\b` | Product name — Finance supplies its own rule packs |
| `SOX` | Regulatory acronym — belongs in Finance compliance rule pack |
| `HIPAA` | Regulatory acronym — belongs in PHR compliance rule pack |
| `GDPR` | Regulatory acronym — belongs in product compliance packs |
| `PCI-DSS` | Regulatory acronym — belongs in product compliance packs |
| `patient\.records` | PHR-domain dataset name |
| `trade\.records` | Finance-domain dataset name |

## 3. Plugin Architecture: Pack-Driven Design

Plugins are **generic engines**; products supply **rule packs**.

```
plugin-compliance  ← generic CompliancePlugin SPI
     ↑ provides
PhrComplianceRulePack  ← PHR-owned, in products/phr/
FinanceComplianceRulePack  ← Finance-owned, in products/finance/
```

The plugin engine evaluates rules supplied by packs. It must not hardcode rules for any
specific product or regulatory domain.

## 4. Plugin Manifest and Config Schema

Every plugin module must have:
1. A valid `plugin.json` manifest in `src/main/resources/plugin.json`.
2. A `configSchema` block in the manifest with field definitions.
3. The manifest validated at startup by `PluginConfigSchemaValidator`.

## 5. Fixture Neutrality in Tests

Plugin test fixtures must use domain-neutral identifiers:

| Forbidden | Allowed |
|-----------|---------|
| `"healthcare-phr"` | `"domain-a-dataset"` |
| `"finance.transactions"` | `"domain-b.transactions"` |
| `phrConsent`, `financeConsent` | `domainAConsent`, `domainBConsent` |
| `billing_test` (H2 URL) | `ledger_test` |
| `.sourceProductId("finance")` | `.sourceId("domain-b")` |

## 6. Adding New Plugins

1. Create module under `platform-plugins/<plugin-name>/`.
2. Use `id("java-module")` convention plugin in `build.gradle.kts`.
3. Include in `platform-plugins/settings.gradle.kts`.
4. Add `plugin.json` manifest with `configSchema`.
5. Wire `checkPluginPurity` task to `check`.
6. Add README.md.
7. Implement plugin SPI cleanly — no product logic.

---

*Enforced by: `checkPluginPurity` task in each plugin module's `build.gradle.kts`*
