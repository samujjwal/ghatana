# ADR-022: Data Cloud Data Product Lifecycle — Publish, Discover, Subscribe, SLA, Deprecate

**Status:** Accepted  
**Date:** 2026-04-28  
**Decision Makers:** Data Cloud Platform Team  
**Phase:** 1 — Data Fabric Foundation  

## Context

Data Cloud needs a first-class data product lifecycle so that collections, queries, and streams can be published as reusable, governed, and discoverable products. Without a structured lifecycle, data assets remain ad-hoc, undocumented, and ungoverned, which prevents safe cross-team and cross-tenant consumption.

## Decision

Adopt a five-state lifecycle model with explicit contract enforcement:

### Lifecycle States

| State | Meaning | Allowed Actions |
|---|---|---|
| **DRAFT** | Product definition in progress; not yet discoverable | Edit schema, SLA, policy; discard |
| **PUBLISHED** | Discoverable and consumable; contract is frozen | Subscribe, monitor SLA, list |
| **DEPRECATED** | Still consumable but scheduled for retirement; warnings on new subscriptions | Subscribe (with warning), migrate |
| **ARCHIVED** | No new subscriptions; existing consumers read-only | Read existing subscriptions |
| **RETIRED** | Fully removed; metadata retained for audit only | No consumption; audit only |

### Contract Enforcement

Every published product carries a frozen **schema contract** and **SLA contract**.

- **Schema contract**: Exact field names, types, and nullability. Any upstream schema change that removes a field or changes its type is a **breaking change** and requires deprecation or a new product version.
- **SLA contract**: Freshness (staleness threshold), completeness (null-ratio threshold), and quality (anomaly-rate threshold). Breaches emit `data-product.sla-breach` events.

**Contract compatibility** is checked by `POST /api/v1/data-products/{productId}/contract-check`, which compares `proposedSchema` and `proposedSla` against the published contract and reports breaking vs non-breaking changes.

### Subscription and Access Control

- `POST /api/v1/data-products/{productId}/subscribe` creates a subscription after `isConsumerAllowed()` validation.
- Access is governed by the product's `access.visibility` (`PUBLIC`, `TENANT`, `PRIVATE`) and `access.allowedSubscribers` list.
- Subscriptions are stored in `dc_data_product_subscriptions` and include `consumerId`, `status` (`ACTIVE`, `EXPIRED`, `REVOKED`), and `subscribedAt`.

### SLA Monitoring

- `POST /api/v1/data-products/{productId}/sla-monitor` samples the backing collection, computes a `ProductQualitySnapshot`, evaluates it against the published SLA, and returns `slaStatus` (`HEALTHY`, `AT_RISK`, `BREACHED`).
- If degraded, it emits a `data-product.sla-breach` event to the event log for downstream alerting.

### Deprecation and Retirement

- A product can be deprecated by updating its `lifecycleStatus` to `DEPRECATED`. Existing subscriptions continue; new subscriptions receive a warning.
- A deprecated product can be archived (`ARCHIVED`), making it read-only for existing consumers.
- A retired product (`RETIRED`) has no active data but retains its descriptor and subscription history for compliance audit.

## Consequences

- **Positive**: Teams can discover, trust, and reuse data products with guaranteed contracts. SLA monitoring enables proactive quality governance.
- **Positive**: Breaking changes are caught before they affect consumers.
- **Negative**: Every published product requires schema and SLA snapshotting, which adds storage overhead.
- **Negative**: Subscription access control adds latency to every new subscription request.

## Related

- `DataProductHandler.java` — `handlePublishDataProduct`, `handleListDataProducts`, `handleSubscribe`, `handleMonitorSla`, `handleCheckContractCompatibility`
- `DataCloudRouterBuilder.java` — routes for `POST /api/v1/data-products`, `GET /api/v1/data-products`, `POST /api/v1/data-products/{productId}/subscribe`, `POST /api/v1/data-products/{productId}/sla-monitor`, `POST /api/v1/data-products/{productId}/contract-check`
- `docs/adr/ADR-021-data-cloud-autonomy.md` — autonomy levels for product retirement and breaking-change approval
