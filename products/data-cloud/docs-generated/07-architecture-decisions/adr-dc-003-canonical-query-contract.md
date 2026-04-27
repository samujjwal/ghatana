# ADR-DC-003: Canonical Query Contract — Filter, Sort, Pagination, Freshness, Cost

**Status:** Accepted
**Date:** 2026-04-26
**Authors:** Data Cloud Architecture Team

## Context

Entity queries across Data Cloud had inconsistent contracts: some endpoints accepted raw SQL strings, others accepted structured filters, and pagination semantics (`hasMore`, `totalCount`) were not uniformly enforced. This made it impossible for the UI to trust table totals, for automation to make safe pagination decisions, and for the query broker to reason about cost.

## Decision

1. The canonical query specification is `EntityStore.QuerySpec` (SPI) and `platform-entity.QuerySpec` (backend-agnostic AST).
2. Every query MUST support:
   - **Filters:** typed predicates (`eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `like`, `in`)
   - **Search:** full-text search string (mapped to connector-native search)
   - **Sort:** multi-field sort with `ASC`/`DESC`
   - **Projection:** field selection to reduce data transfer
   - **Pagination:** `offset` + `limit` with hard ceiling `MAX_LIMIT = 10_000`
   - **Total count:** `QueryResult.totalCount` must be accurate and computed at query time
   - `hasMore`: derived as `offset + entities.size() < totalCount`
   - **Consistency level:** `STRONG` | `EVENTUAL` | `BOUNDED_STALENESS`
   - **Freshness hint:** caller-declared staleness tolerance (e.g., `30s`)
3. Query responses MUST include `total`, `count`, `offset`, `limit`, `hasMore`, and `timestamp`.
4. The query broker (P1.2) will use the canonical spec as its routing contract.

## Consequences

- **Positive:** One query language across all storage tiers. UI tables can compute pagination reliably. Cost estimation has a stable contract.
- **Negative:** All existing storage connectors must be updated to support the new fields. Backward-compatible builder methods are provided.

## Related

- P0.2 in `data-cloud-implementation-tasks.md`
- `EntityStore.java` (SPI)
- `platform-entity/src/.../QuerySpec.java`
