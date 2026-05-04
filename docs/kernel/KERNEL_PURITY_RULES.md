# Kernel Purity Rules

> **Status**: Enforced by `checkKernelPurity`, `checkKernelResourcePurity`, and `checkKernelDocsPurity` Gradle tasks.
> **Last Updated**: 2026-05-03

## 1. What Is Kernel Purity?

The `platform-kernel` module defines cross-cutting infrastructure: boundary policy evaluation,
plugin lifecycle, audit trail, observability, and bridge ports.

**Kernel purity** means that the kernel's `src/main/java` and `src/main/resources` trees contain
**no product-domain identifiers**. The kernel must not know about PHR, Finance, AEP internals,
Data Cloud schema specifics, or any other product domain.

## 2. Banned Terms in Kernel Main Source

The `checkKernelPurity` task scans `src/main/java/**` for these patterns:

| Pattern | Rationale |
|---------|-----------|
| `\bPHR\b` | Product acronym |
| `CLINICAL` | Product-domain clinical terminology |
| `\bFinance\b` | Product name |
| `FINANCE` | Product name (all caps) |
| `phr-kernel` | Module dependency leak |
| `finance-kernel` | Module dependency leak |
| `SOX` | Regulatory framework — belongs in Finance product pack |
| `HIPAA` | Regulatory framework — belongs in PHR product pack |
| `GDPR` | Regulatory framework — belongs in product compliance packs |
| `PCI-DSS` | Regulatory framework — belongs in product compliance packs |
| `PCIDSS` | Regulatory framework (alternate spelling) |
| `trade\.records` | Finance-domain dataset name |
| `patient\.records` | PHR-domain dataset name |

These patterns are **case-sensitive Kotlin regex** applied via `grep`.

> Note: `com.ghatana.finance.*` in adapter packages is allowed in `src/main/java/com/ghatana/kernel/adapter/**`
> because those adapter packages are the kernel-owned port definitions. The adapter `Impl` classes
> live in the kernel but implement product-agnostic contracts — they must not contain product-domain logic.

## 3. Docs Purity

The `checkKernelDocsPurity` task scans `docs/examples/**` and selected `docs/**` directories.

Prohibited in docs examples:
- Real product domain terms (PHR, Finance, AEP internals, Data Cloud schema)
- Regulatory acronyms listed above

Use **fictional domain examples** instead:

| Real domain | Fictional replacement |
|-------------|----------------------|
| PHR / patient health | Domain-A / subject records |
| Finance / trade records | Domain-B / transactions |
| Clinical document | domain-document |

## 4. How to Fix a Purity Violation

1. Identify the file and line from the Gradle task output.
2. Replace domain-specific text with a domain-neutral equivalent.
3. For Javadoc: use "applicable regulatory frameworks" instead of specific acronyms.
4. For example code: use `domain-a`, `domain-b`, `subject-record`, `transaction` placeholders.
5. Re-run `./gradlew :platform-kernel:kernel-core:checkKernelPurity` to confirm the fix.

## 5. Exemptions

- `src/test/**` — test fixtures may use neutral domain placeholders (`domain-a`, `domain-b`)
  but must NOT import product pack classes directly from product modules.
- `build.gradle.kts` and Gradle scripts — outside purity scan scope.
- Generated protobuf code — governed by `src/main/proto` schema checks, not by this gate.

## 6. Adding New Ban Terms

Edit `platform-kernel/kernel-core/build.gradle.kts` in the `checkKernelPurity` task block.
Submit a kernel team review. New terms must have a documented rationale.

---

*Enforced by: `platform-kernel/kernel-core/build.gradle.kts` → tasks `checkKernelPurity`,
`checkKernelResourcePurity`, `checkKernelDocsPurity`*
