# Owner: Action Plane Runtime Docs

**Product:** Data Cloud  
**Plane:** Action Plane  
**Runtime implementation:** AEP  
**Canonical architecture:** `../architecture/PLANE_ARCHITECTURE.md`

## Responsibility

This folder owns implementation-level documentation for the current AEP runtime behind the Data Cloud Action Plane.

These docs may describe AEP internals, operational commands, runtime APIs, benchmarks, topology, and tracing. They must not position AEP as a separate product or redefine the Data Cloud plane architecture.

## Boundary

- Data Cloud is the customer-facing product.
- Action Plane is the product plane.
- AEP is the current runtime implementation.
- Public contract truth lives in `products/data-cloud/contracts`.
- Runtime docs must defer to `docs/architecture/PLANE_ARCHITECTURE.md` for product hierarchy and dependency rules.
