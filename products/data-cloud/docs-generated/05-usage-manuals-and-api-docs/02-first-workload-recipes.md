# Data Cloud First-Workload Recipes

**Document ID:** DC-RECIPES-001  
**Version:** 1.0  
**Date:** 2026-04-13  
**Purpose:** Turn the platform and strategy docs into concrete first-adoption paths  
**Evidence Base:** Product vision, ICP/JTBD, API reference, system architecture, and capability map

---

## Executive Summary

These recipes define realistic first workloads for Data Cloud based on the current documented product shape. They are not full deployment tutorials. They are adoption patterns that help a team start with one narrow, high-value workload and expand from there.

### Recommended First Workloads

1. **Tenant-aware operational entity service**
2. **Event-backed activity stream and notifications**
3. **Real-time operational insights view**

These recipes intentionally describe backend and integration-first adoption. They do not imply that the Data Cloud UI already ships a fully supported alert-management console; current alert UI remains a boundary-only operator surface.

These recipes align with the recommended ICP of engineering-led SaaS teams replacing a fragmented internal data-serving stack.

---

## 1. Recipe Selection Guidance

| If the team's first pain is...               | Start with...                                  |
| -------------------------------------------- | ---------------------------------------------- |
| Too much custom CRUD and metadata plumbing   | Tenant-aware operational entity service        |
| Too much glue code around events and updates | Event-backed activity stream and notifications |
| Slow or inconsistent operational visibility  | Real-time operational insights view            |

---

## 2. Recipe A: Tenant-Aware Operational Entity Service

### When to Use It

Use this recipe when a team has a product domain such as accounts, projects, cases, messages, or assets and wants one tenant-aware service layer for CRUD, query, and schema-governed metadata.

### Why It Is a Good First Workload

- It exercises the platform's core entity capabilities.
- It validates tenant-aware API behavior quickly.
- It gives the team a low-risk starting point before eventing or ML workflows.

### Core Platform Surfaces Used

- Entity CRUD APIs
- Collection and schema metadata
- Multi-tenant request model
- Query and filtering support

### Minimal Adoption Path

1. Define one collection for the operational domain.
2. Route application writes and reads through the entity APIs.
3. Use tenant headers consistently in every request path.
4. Validate query, pagination, and lifecycle expectations for one product surface.
5. Add one downstream consumer, query workflow, or operator insight handoff only after the core entity path is stable.

### Success Criteria

- One product domain is fully running through Data Cloud entity APIs.
- The team can demonstrate tenant-scoped CRUD and query behavior.
- Operational metadata is centralized instead of distributed across custom code.

### Expansion Path

- Add event emission on entity changes.
- Add analytical views on top of the same domain.
- Add feature extraction or model-support workflows later.

---

## 3. Recipe B: Event-Backed Activity Stream and Notifications

### When to Use It

Use this recipe when the team already has an operational domain and needs a consistent stream of events for notifications, activity feeds, or downstream consumers.

### Why It Is a Good First Workload

- It exercises the event-native side of the platform.
- It creates user-visible value quickly through downstream updates.
- It is a natural second step after a CRUD-based domain model.

### Core Platform Surfaces Used

- Event append and query APIs
- Event metadata and replay behavior
- SSE or WebSocket delivery
- Consumer or workflow integration

### Minimal Adoption Path

1. Select one event-producing product workflow.
2. Publish domain events through the documented event surface.
3. Stand up one consumer path for notifications, feed updates, or downstream processing.
4. Add real-time delivery for one user-visible stream.
5. Validate lag, ordering expectations, and tenant scoping.

### Success Criteria

- One workflow emits and consumes domain events end to end.
- The team can replay or inspect the event stream for that workflow.
- One downstream product feature depends on the Data Cloud event path.

### Expansion Path

- Add more consumers.
- Add aggregation or activity scoring.
- Add feature-store ingestion from the same events.

---

## 4. Recipe C: Real-Time Operational Insights View

### When to Use It

Use this recipe when a team needs fast operational visibility for a live workflow, such as status tracking, queue health, user activity, or incident views.

### Why It Is a Good First Workload

- It demonstrates the operational plus analytical value of the platform.
- It connects storage, query, and real-time delivery in one visible feature.
- It gives stakeholders a concrete reason to expand platform usage.

### Core Platform Surfaces Used

- Query and analytics APIs
- Real-time interfaces
- Query or operator-insight hooks
- Event-fed or entity-fed operational data

### Minimal Adoption Path

1. Define one operational metric view tied to a live workflow.
2. Source data from one entity domain and, if relevant, one event stream.
3. Expose the view through the canonical query surface and only later decide whether an operator insight view is justified.
4. Add real-time refresh or subscription behavior.
5. Validate the usefulness of the view with one internal operator or product team.

### Success Criteria

- A team can observe one live workflow through Data Cloud without custom stitched reporting logic.
- The view updates reliably enough to replace an existing manual process or ad hoc query path.

### Expansion Path

- Add alerting.
- Add historical trend views.
- Add ML-supported prioritization or anomaly detection later.

---

## 5. Recommended Order of Adoption

| Order | Recipe                                         | Why                                                     |
| ----- | ---------------------------------------------- | ------------------------------------------------------- |
| 1     | Tenant-aware operational entity service        | Lowest-friction entry into the platform                 |
| 2     | Event-backed activity stream and notifications | Natural extension once the core domain is on-platform   |
| 3     | Real-time operational insights view            | Converts platform adoption into clear operational value |

---

## 6. What Not to Start With

Avoid making the first workload:

- a broad, cross-domain platform migration
- a compliance-heavy enterprise rollout
- a full ML platform adoption
- a warehouse-replacement initiative

The fastest way to prove value is one narrow workload with clear tenant-aware operational value.

---

## 7. Companion Documents

- `01-product-vision.md`
- `04-icp-and-jtbd.md`
- `05-competitive-positioning.md`
- `04-api-reference.md`
- `07-readiness-scorecard.md`
