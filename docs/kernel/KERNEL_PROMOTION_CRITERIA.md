# Kernel Promotion / De-Promotion Criteria

> **Status**: Enforced policy — gate all `platform-kernel/*` additions and removals against these criteria.
> **Authority**: This document is authoritative for kernel scope decisions. Section 33 of
> [copilot-instructions.md](../../.github/copilot-instructions.md) delegates scope enforcement to this file.
> **Last Updated**: 2026-05-08

---

## 1. Purpose

The Kernel (`platform-kernel/`) is the platform's stability surface. Its scope must be controlled
explicitly — abstractions that enter the kernel too early pollute the shared boundary and are costly
to remove later. Abstractions that should be in the kernel but live in products become duplicated
across teams.

This document defines when a capability *may* be promoted into the kernel, when an existing kernel
capability *must* be de-promoted to a product, and the governance steps for each case.

---

## 2. Kernel Promotion Criteria

An abstraction may be promoted from a product (or a shared-services module) into `platform-kernel/`
only when **all** of the following criteria are satisfied.

### 2.1 Multi-Product Reuse Proof

> **Minimum:** the abstraction must be in active use by **at least two distinct, non-experimental
> products** before a kernel promotion proposal is raised.

Evidence required (submitted with the kernel PR):

- Source file references showing both products consuming the abstraction.
- Test evidence from each consuming product demonstrating non-trivial use
  (not a single trivial call in one test file).
- Confirmation that no product has a meaningfully different version of the same concept.

One product using a capability does not constitute a reuse proof.  A prototype or
experimental product does not count.

### 2.2 Domain-Neutrality

The abstraction must contain **no product-domain identifiers** (see
[KERNEL_PURITY_RULES.md](./KERNEL_PURITY_RULES.md) for the banned-term list).

- No PHR, Finance, FlashIt, DMOS, AEP, or Data-Cloud-specific terms.
- No HIPAA, SOX, PCI-DSS in the kernel main source.
- No regulatory-framework-specific defaults — those belong in product compliance packs.

### 2.3 Stable Interface Contract

The API surface must be stable enough to absorb two product teams' usage patterns
without breaking changes within the first two kernel minor versions.

- Public API shape defined before promotion (not "we'll figure it out after").
- No `@Experimental` or `@Beta` annotations remaining when the promotion PR is merged to main.
- Semantic-versioned contract documented in the module's `README.md`.

### 2.4 Kernel Team Architecture Review

A kernel team architecture review must be completed before merging:

Checklist:
- [ ] Multi-product reuse proof present (§ 2.1)
- [ ] Zero domain-term violations in `src/main/java/**` (§ 2.2)
- [ ] Stable interface defined and documented (§ 2.3)
- [ ] Build convention applied: `id("java-module")` with no dual-convention (§ 22 of copilot-instructions.md)
- [ ] JavaDoc with `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` present on all public classes
- [ ] `kernel-core` purity tasks pass: `./gradlew :platform-kernel:kernel-core:checkKernelPurity`
- [ ] Integration tests present for the new capability in `platform-kernel/kernel-testing/`
- [ ] Observability hooks (metrics + structured log) for critical paths
- [ ] Product consuming teams have reviewed the API contract
- [ ] Deprecation path planned for any product-local duplicate that will be removed

### 2.5 "Do Not Promote Yet" Signals

The following signals indicate the abstraction is **not ready** for kernel promotion:

| Signal | Rationale |
|--------|-----------|
| Only one product uses it | Insufficient evidence of generality |
| Abstraction has product-specific logic (special-casing product name, id format, etc.) | Domain pollution risk |
| Interface is still changing week-to-week | Stability contract cannot be guaranteed |
| No integration test coverage in the kernel test harness | Regression risk |
| "We'll clean it up after promotion" comments in the code | Stub promotion (Section 31 of copilot-instructions.md) |
| The only consumer is the same team proposing promotion | Conflict of interest without external reuse proof |
| SPI/plugin approach would suffice to give the product what it needs | Product concern dressed as kernel concern |

---

## 3. Kernel De-Promotion Criteria

An existing kernel capability **must be de-promoted** to a product or to `shared-services/` when
**any** of the following conditions are discovered during audit or review.

### 3.1 Product-Specific Semantics Crept In

The capability contains logic that applies to exactly one product:

- A conditional branch gating on a product name or enum.
- A default value that only makes sense for one product's domain.
- A hardcoded resource type, action string, or event name that belongs to one product only.

**Action**: Extract the product-specific portion to that product's module. Keep the kernel part
generic or remove it if nothing generic remains.

### 3.2 Under-Used — Only One Active Consumer

If a kernel capability has only one remaining active consumer:

- The abstraction should move to that product's module.
- If the abstraction is genuinely product-agnostic but not widely needed, consider `shared-services/`.

**Action**: File a de-promotion issue, migrate within one sprint, delete from kernel.

### 3.3 Stability Contract Was Not Met

If a kernel capability has had multiple breaking changes within two minor kernel versions:

- It is not stable enough for the kernel boundary.

**Action**: Move to `shared-services/<name>/` with semantic versioning until stability is proven.

### 3.4 Duplicate of a Lower-Level Primitive

If a kernel capability is functionally equivalent to something already in:
- `platform/java/core/`
- `platform/java/http/`
- `platform/java/database/`
- etc.

it must be deleted from the kernel and all callers migrated to the canonical platform primitive.

**Action**: Use [MIGRATION_GUIDES.md](../MIGRATION_GUIDES.md) to document the migration path.
Complete migration in one PR (fix-forward, no aliases — see Section 25 of copilot-instructions.md).

---

## 4. Promotion / De-Promotion Governance Steps

### Promoting a capability into the kernel

1. Open a **kernel scope proposal** issue with label `kernel:scope-change`.
2. Attach reuse proof (source links, test evidence) as described in § 2.1.
3. Assign to kernel team for architecture review.
4. Kernel team completes the review checklist (§ 2.4).
5. PR merged only after all checklist items are checked.
6. Product-local duplicates removed in the same PR or in a follow-up PR within the same sprint.

### De-promoting a capability out of the kernel

1. Open a **kernel de-promotion** issue with label `kernel:de-promote`.
2. Identify the target home (`products/<product>/`, `shared-services/`, or deletion).
3. Migrate all consumers in one atomic PR (fix-forward — no compatibility shims).
4. Delete the kernel code.
5. Update [KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md](./KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md)
   to reflect the new ownership.

---

## 5. Architecture Review Fast-Path ("Obvious" Cases)

To reduce overhead on clear-cut decisions:

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| New utility used only by FlashIt | Stay in product | Single consumer |
| Tracing context struct used by 3+ products | Promote | Multi-product proof |
| Product-specific status enum added to kernel enum | De-promote immediately | Domain pollution |
| Shared retry helper currently copied in 2 products | Promote to `platform/java/core/` | Not kernel — use platform libs |
| A bridge port SPI with only one product provider | Keep in kernel only if the SPI is stable; consider moving to shared-services otherwise | |

---

## 6. Relationship to Existing Docs

| Document | Relationship |
|----------|-------------|
| [KERNEL_PURITY_RULES.md](./KERNEL_PURITY_RULES.md) | Defines what content is banned from kernel source (de-promotion trigger §3.1) |
| [KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md](./KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md) | Records current ownership; updated when promotions/de-promotions complete |
| [PRODUCT_DEVELOPMENT_GUIDE.md](./PRODUCT_DEVELOPMENT_GUIDE.md) | Shows how products integrate with the kernel via SPI; the preferred alternative to promotion |
| [PRODUCT_KERNEL_AUDIT_PROGRESS.md](./PRODUCT_KERNEL_AUDIT_PROGRESS.md) | Tracks historical promotion and hardening actions |

---

*Owned by: Kernel Platform Team*
*Review cadence: Per PR that touches `platform-kernel/` scope*
