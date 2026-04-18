# Data Cloud ICP and JTBD Definition

**Document ID:** DC-ICP-001  
**Version:** 1.0  
**Date:** 2026-04-13  
**Evidence Base:** `01-product-vision.md`, `02-capability-map.md`, `03-requirements.md`, API surface, architecture, and audit revision

---

## Executive Summary

The current Data Cloud documentation defines multiple personas but does not prioritize them. This document converts the existing persona material into a working **Ideal Customer Profile (ICP)** and a prioritized **Jobs To Be Done (JTBD)** framework.

This is a strategy document, not a proof document. It translates the existing technical evidence into a product hypothesis that can drive positioning, roadmap choices, packaging, and validation work.

### Recommended Primary ICP

**Engineering-led SaaS organizations building multi-tenant, data-intensive applications that need operational storage, event streaming, analytics, and ML-adjacent capabilities in one platform.**

### Why This ICP Fits the Existing Product

- The architecture emphasizes multi-tenancy, runtime boundaries, and explicit module contracts.
- The API surface is broad enough to support application integration, not only analyst workflows.
- The platform combines entity storage, eventing, analytics, real-time delivery, and feature-store capabilities.
- The operational docs assume a team that can run infrastructure, reason about scale, and integrate platform services into a product stack.

---

## 1. ICP Framework

### 1.1 Primary ICP

| Dimension        | Definition                                                                             |
| ---------------- | -------------------------------------------------------------------------------------- |
| Company Type     | SaaS or platform businesses with product-led or API-led architectures                  |
| Team Shape       | Strong platform, backend, or ML engineering capability                                 |
| Product Shape    | Multi-tenant applications with operational data, event flows, and analytical workloads |
| Technical Need   | Reduce fragmentation across persistence, eventing, analytics, and ML support services  |
| Buying Trigger   | Existing stack is becoming expensive, slow, inconsistent, or hard to operate           |
| Adoption Pattern | Starts with one workload or domain team, expands across more application surfaces      |

### 1.2 Secondary ICPs

| ICP                                                             | Why It Fits                                                          | Why It Is Secondary                                                |
| --------------------------------------------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------ |
| ML engineering teams needing feature and model support          | Feature store, registry, pipelines, and monitoring are documented    | The product story is still broader than ML alone                   |
| Internal platform teams in larger enterprises                   | Isolation, governance, audit, and API breadth fit internal platforms | Validation evidence and enterprise buying artifacts are still thin |
| Product teams needing real-time analytics and operational views | Streaming, canonical query flows, and operator insight surfaces are present | Buyer story is less explicit than the platform story               |

### 1.3 Anti-ICP / Low-Fit Segments

| Segment                                   | Why It Is Low Fit Today                                                                                                 |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Small teams seeking zero-infra simplicity | Current docs assume operational sophistication and infra ownership                                                      |
| BI-only organizations                     | Data Cloud is documented as a runtime platform, not a pure reporting layer                                              |
| Governance-first enterprise buyers        | Governance exists, but validation and enterprise packaging are not yet mature enough to lead with                       |
| Notebook-centric data science teams       | The strongest documented assets are APIs, runtime services, and platform boundaries, not exploratory notebook workflows |

---

## 2. Buyer and User Map

### 2.1 Primary Buyer Hypothesis

**Primary buyer:** Head of Platform Engineering, Director of Data Platform, or equivalent engineering owner.

**Why:** The strongest documented value is consolidation of operational data capabilities under one runtime with explicit multi-tenant and infrastructure-aware design.

### 2.2 Primary Users

| Role                           | What They Want from Data Cloud                                            |
| ------------------------------ | ------------------------------------------------------------------------- |
| Platform engineers             | Fewer systems to operate, consistent interfaces, clear control boundaries |
| Backend application developers | One API surface for CRUD, events, analytics, and real-time behavior       |
| ML engineers                   | Direct access to features, registries, and event-fed pipelines            |
| Product analysts / operators   | Timelier insight without stitching custom access paths                    |

### 2.3 Key Stakeholders in the Deal

| Stakeholder             | Concern                                                              |
| ----------------------- | -------------------------------------------------------------------- |
| Security / compliance   | Isolation clarity, auditability, access control, proof of safeguards |
| SRE / operations        | Readiness, observability, failure recovery, scale behavior           |
| Finance / procurement   | Packaging, deployment cost, operational footprint                    |
| Architecture leadership | Long-term maintainability and escape from stitched point solutions   |

---

## 3. Prioritized Jobs To Be Done

### JTBD 1: Consolidate a fragmented data-serving stack

**When** our product team is maintaining separate systems for operational data, event propagation, analytical views, and ML support,  
**we want** one platform surface that covers those jobs coherently,  
**so we can** reduce operational sprawl, inconsistent patterns, and integration glue.

**Evidence from current docs**

- Entity storage, event streaming, analytics, feature store, and real-time APIs are all documented.
- The platform is positioned around one runtime rather than a loose tool chain.

**Why this should be prioritized**

- It matches the broadest set of documented capabilities.
- It creates a crisp reason for the platform to exist beyond feature-by-feature parity.

### JTBD 2: Build multi-tenant data-intensive application features faster

**When** engineers need to ship tenant-aware application features with data persistence, event propagation, and near-real-time views,  
**we want** an application-facing data platform with consistent APIs and runtime services,  
**so we can** reduce custom platform code and shorten delivery time.

**Evidence from current docs**

- The docs emphasize multi-tenancy, runtime handlers, API breadth, and event-driven flows.
- REST, GraphQL, gRPC, SSE, and WebSocket surfaces are all described.

### JTBD 3: Operationalize ML-adjacent data workflows inside the application stack

**When** ML or recommendation workflows depend on streaming features, registries, and model-aware metadata,  
**we want** those capabilities integrated into the broader data platform,  
**so we can** support ML use cases without a separate infrastructure island.

**Evidence from current docs**

- Feature store, model registry, pipelines, and model monitoring are documented.
- Event ingest and platform runtime integration suggest ML support is part of the same platform story.

### JTBD 4: Operate the platform with confidence in regulated or sensitive environments

**When** teams need strong auditability, access control, and tenant separation,  
**we want** those controls documented and verifiable,  
**so we can** adopt the platform without taking on avoidable compliance or isolation risk.

**Evidence from current docs**

- Governance, privacy, and audit features are documented.
- Risk documents highlight isolation and security gaps that still need proof.

**Current limitation**

- This JTBD is strategically important, but today it is constrained by contradictory isolation language and incomplete validation evidence.

---

## 4. Priority Order and Launch Focus

### Recommended Launch Priority

| Priority | JTBD                                                          | Reason                                                                               |
| -------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| 1        | Consolidate fragmented data-serving stack                     | Broadest match to documented platform shape                                          |
| 2        | Build multi-tenant data-intensive application features faster | Strong API and runtime evidence; clear engineering value                             |
| 3        | Operationalize ML-adjacent workflows                          | Valuable differentiator, but should support rather than dominate the story initially |
| 4        | Operate confidently in sensitive environments                 | Important trust requirement, but must be backed by validation work                   |

### Positioning Implication

Data Cloud should lead with **platform consolidation for engineering teams**, not with broad, abstract "AI/ML-native" messaging alone.

---

## 5. Buying Triggers and Disqualifiers

### 5.1 Likely Buying Triggers

| Trigger                                                                   | Why It Matters                                                  |
| ------------------------------------------------------------------------- | --------------------------------------------------------------- |
| Too many systems in the application data path                             | Supports the consolidation JTBD                                 |
| Inconsistent patterns across storage, events, and analytics               | Strengthens the case for one runtime and API surface            |
| Need to ship tenant-aware real-time features                              | Matches the multi-tenant and real-time documentation            |
| ML support is needed, but the team does not want a separate ML infra silo | Matches the integrated feature-store and registry story         |
| Platform team wants stronger internal standardization                     | Fits the documented emphasis on boundaries and shared contracts |

### 5.2 Likely Disqualifiers

| Disqualifier                                                           | Why It Blocks Adoption                                                   |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Buyer wants a drop-in warehouse replacement only                       | Data Cloud is documented as a broader platform                           |
| Buyer expects consumer-grade self-serve simplicity                     | Current documentation assumes engineering ownership                      |
| Buyer requires validated enterprise-grade isolation claims immediately | Current docs still need claim reconciliation and proof artifacts         |
| Team only needs a lightweight BI dashboard                             | Data Cloud is likely too broad and operationally heavy for that use case |

---

## 6. Decision Criteria the Product Must Satisfy

To win the primary ICP, the documentation and product story must credibly answer these questions:

1. Why is one integrated platform better than a stitched stack for this customer?
2. Which workloads should start on Data Cloud first?
3. What does integration look like in the first 30 to 60 days?
4. What proof exists for multi-tenant safety, operational readiness, and scale?
5. Which capabilities are mature enough to sell today versus roadmap-only?

---

## 7. Documentation Actions Required

| Needed Artifact         | Why                                                             |
| ----------------------- | --------------------------------------------------------------- |
| Competitive positioning | Converts this ICP into a comparison story buyers can understand |
| Packaging and pricing   | Turns ICP into a buyable offer                                  |
| Success metrics         | Turns JTBD into measurable outcomes                             |
| Readiness scorecard     | Separates validated from aspirational claims                    |
| Use-case recipes        | Makes first adoption steps concrete                             |

---

## 8. Final Recommendation

The current docs support a **platform-engineering-led ICP** far more strongly than they support a general-purpose "all data teams" audience.

If Data Cloud tries to speak to every persona equally, it will sound broad but unfocused. If it instead anchors on **engineering teams replacing a fragmented data-serving stack for multi-tenant applications**, the existing technical evidence becomes much easier to explain, justify, and package.

---

**Status:** Working strategic hypothesis for documentation alignment and customer validation  
**Next companion documents:** `05-competitive-positioning.md`, `06-packaging-and-pricing.md`, `07-success-metrics.md`
