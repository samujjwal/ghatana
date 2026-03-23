# Virtual-Org — Ownership & Boundary Policy

## Product Owner

Team: Platform Products  
Contact: #platform-products

## Boundary Decision (P2.4 — 2026-03-22)

**Virtual-Org is treated as a Product with a Product-Owned Shared Framework layer.**

`products/virtual-org/modules/framework` is the shared organisational-graph
primitives module consumed by both Virtual-Org and Software-Org.  It remains
inside `products/virtual-org/` because:

- Virtual-Org is the authoritative owner of the org-graph domain model.
- Software-Org consumes the framework as a read/compose layer; it does not
  own or extend the core abstractions.
- Moving the module to `platform/java/` would widen its implied scope beyond
  the currently justified two-product boundary.

### Approved Cross-Product Consumers

| Consumer | Module | Justification |
|---|---|---|
| `products/software-org` | `modules:framework` | org-graph domain reuse (composing VirtualOrgs into SoftwareOrgs) |

### Rules

1. Any **new** cross-product consumer of `modules:framework` must be approved
   here before the dependency is committed (`OWNER.md` PR review required).
2. If consumer count grows beyond two products, evaluate promotion to
   `platform/java/org-framework`.
3. Internal Virtual-Org modules (`engine`, `launcher`, `modules/*`) may
   depend on `modules:framework` freely.

## Module Inventory

| Module | Scope | Notes |
|---|---|---|
| `modules:framework` | **Product-Owned Shared** | org-graph, VirtualOrg, Operator abstractions. Approved for Software-Org. |
| `modules:integration` | Product-internal | AEP + workflow integration layer |
| `modules:workflow` | Product-internal | Workflow orchestration specific to Virtual-Org |
| `modules:agent` | Product-internal | Agent execution inside virtual orgs |
| `modules:operator-adapter` | Product-internal | Operator adapter for AEP interop |
| `engine/service` | Product-internal | Runtime service assembly |
| `launcher` | Product-internal | Boot entrypoint |
