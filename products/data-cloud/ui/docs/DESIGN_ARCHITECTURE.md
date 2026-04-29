# Data Cloud UI – Design & Architecture

> **See the canonical architecture document**: [`ui/ARCHITECTURE.md`](../ARCHITECTURE.md)
>
> This file is kept as a navigation aid. All architectural decisions, module boundaries,
> component inventories, and testing strategy are maintained in `ARCHITECTURE.md`.

## Summary

The Data Cloud frontend is split into two modules:

- **`@data-cloud/ui`** (`products/data-cloud/ui/`) — application layer: pages, routing, stores, feature components.
- **`@data-cloud/ui-components`** (`products/data-cloud/libs/ui-components/`) — presentational library: reusable primitives with no app-level dependencies.

For full details see [`ARCHITECTURE.md`](../ARCHITECTURE.md).
