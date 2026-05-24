# Owner: AEP Integration Boundary Docs

**Product:** Data Cloud  
**Plane:** Data-Cloud storage and integration boundary
**External platform:** AEP
**Canonical architecture:** `../architecture/PLANE_ARCHITECTURE.md`

## Responsibility

This folder owns Data-Cloud documentation for integration with AEP. It may describe persistence contracts, metadata storage, audit evidence, checkpoint persistence, and storage plugins used by AEP.

These docs must not define AEP-owned EventCloud, CEP, PatternSpec/EPL, operator-runtime, pattern learning/adaptation, or agent orchestration semantics. AEP-owned architecture belongs under `products/aep` and the AEP ADRs.

## Boundary

- Data-Cloud is the governed data/storage product.
- AEP is the adaptive event intelligence platform.
- EventCloud and adaptive event semantics are AEP-owned.
- Data-Cloud may provide EventCloud persistence plugins through stable SPI.
- Public contract truth lives in `products/data-cloud/contracts`.
- Runtime docs must defer to `docs/architecture/PLANE_ARCHITECTURE.md` for product hierarchy and dependency rules.
