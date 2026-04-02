# Owner: Feature Store Ingest Service (Residue)

**Status:** RESIDUE — Canonical location moved to `products/data-cloud/feature-store-ingest`  
**Canonical Path:** `products/data-cloud/feature-store-ingest`  
**Team:** Data-Cloud Team  
**Slack:** #data-cloud-platform  
**Last reviewed:** 2026-03-31 (V4 shared-services audit)

## Status

This directory is **historical residue** from when feature-store-ingest was planned as a
shared-service. The canonical, actively-maintained version lives in:

```
products/data-cloud/feature-store-ingest/
```

This directory is **NOT included** in the Gradle build (see `shared-services/build.gradle.kts`
— the module is commented out).

## Decision

Per the V4 shared-services audit (2026-03-31) and ADR-013:
- `feature-store-ingest` is a **Data-Cloud–specific** capability: it tails the Data-Cloud
  EventLogStore and writes into the Data-Cloud Feature Store (Redis + PostgreSQL).
- It does not meet the bar for a shared runtime service because only Data-Cloud consumes it.
- The canonical home is therefore `products/data-cloud/feature-store-ingest`.

## Do Not Modify This Directory

All future changes to feature ingestion logic must go to:
`products/data-cloud/feature-store-ingest/`

This residue directory will be deleted in the next scheduled cleanup sprint.
