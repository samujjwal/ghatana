# Deployment Models Specification

**Document Type**: Normative Reference  
**Authority Level**: 3 — Normative Reference  
**Version**: 1.0.0 | **Status**: Active | **Date**: 2026-01-19  
**Owner**: AppPlatform Architecture Council  
**Canonical Path**: `products/app-platform/docs/DEPLOYMENT_MODELS.md`

---

## Purpose

This document specifies the supported deployment topologies for the AppPlatform Kernel and Domain Packs. Each model defines the resource isolation boundaries, data residency guarantees, scale characteristics, and applicable use cases. Platform operators must select one model per deployment environment; hybrid configurations within a single environment are not supported.

---

## Table of Contents

1. [Model 1 — Single-Domain Dedicated](#1-model-1--single-domain-dedicated)
2. [Model 2 — Multi-Domain Shared Kernel](#2-model-2--multi-domain-shared-kernel)
3. [Model 3 — Federated (Domain-Owned Kernel Instances)](#3-model-3--federated-domain-owned-kernel-instances)
4. [Model 4 — Hybrid Cloud / Air-Gap](#4-model-4--hybrid-cloud--air-gap)
5. [Isolation Guarantees Matrix](#5-isolation-guarantees-matrix)
6. [Config Pack Activation per Model](#6-config-pack-activation-per-model)
7. [Data Residency Compliance](#7-data-residency-compliance)
8. [Upgrade & Rollback Procedures](#8-upgrade--rollback-procedures)
9. [Model Selection Guide](#9-model-selection-guide)

---

## 1. Model 1 — Single-Domain Dedicated

### 1.1 Description

A single domain pack (e.g., Capital Markets / Siddhanta) is the sole consumer of one AppPlatform Kernel instance. All kernel resources are shared among tenants of that domain only.

### 1.2 Topology

```
┌──────────────────────────────────────────────────┐
│           AppPlatform Kernel Instance             │
│  K-01 IAM  │  K-02 Config  │  K-05 Event Bus     │
│  K-07 Audit│  K-15 Calendar│  K-16 Ledger  │ ... │
│─────────────────────────────────────────────────│
│            Domain Pack: Capital Markets           │
│  D-01 OMS │ D-02 EMS │ D-05 Pricing │ ...        │
└──────────────────────────────────────────────────┘
         │Tenant A   │Tenant B   │Tenant C
```

### 1.3 Isolation Guarantees

| Resource  | Isolation Level                                                                  |
| --------- | -------------------------------------------------------------------------------- |
| Data      | Row-level `tenant_id` isolation in all tables; no cross-tenant queries possible  |
| Event Bus | Kafka topics partitioned by `tenant_id`; no cross-tenant event delivery          |
| Config    | Per-tenant config namespace; global config readable by all tenants               |
| Secrets   | Tenant-scoped KMS keys; vault path namespaced by `tenant_id`                     |
| Compute   | Shared JVM process; tenant CPU/memory quotas enforced via K-04 resource governor |
| Network   | Single ingress; per-tenant JWT scope enforcement at K-11 API Gateway             |

### 1.4 Use Cases

- Single regulator/country broker-dealer platform
- Fintech startup with one domain (< 50 tenants)
- Development/staging environments

### 1.5 Resource Baseline

| Component         | Minimum       | Production Target |
| ----------------- | ------------- | ----------------- |
| Kernel JVM        | 4 vCPU / 8 GB | 8 vCPU / 16 GB    |
| Event Store DB    | 50 GB         | 1 TB (SSD)        |
| Ledger DB         | 50 GB         | 500 GB            |
| Event Bus (Kafka) | 3-node        | 5-node            |
| Cache (Redis)     | 2-node HA     | 3-node cluster    |

---

## 2. Model 2 — Multi-Domain Shared Kernel

### 2.1 Description

Multiple domain packs (e.g., Capital Markets + Banking + Insurance) share a single AppPlatform Kernel instance. The kernel serves all domains and their respective tenants simultaneously.

### 2.2 Topology

```
┌────────────────────────────────────────────────────────────────────┐
│                 AppPlatform Kernel Instance                        │
│  K-01 IAM │ K-02 Config │ K-05 EventBus │ K-07 Audit │ K-16 │ …  │
│────────────────────────────────────────────────────────────────────│
│  Domain Pack A         │  Domain Pack B       │  Domain Pack C     │
│  Capital Markets       │  Banking             │  Insurance         │
│  Tenants: A1, A2, A3   │  Tenants: B1, B2     │  Tenants: C1       │
└────────────────────────────────────────────────────────────────────┘
```

### 2.3 Isolation Guarantees

| Resource  | Isolation Level                                                                                                                                              |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Data      | Row-level `(tenant_id, domain_pack_id)` compound key isolation                                                                                               |
| Event Bus | Topics namespaced by `{source_domain_pack_id}.{event_type}`; no cross-domain routing unless `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md` compliant subscription |
| Config    | Per-domain-pack config namespace; cross-domain config access forbidden                                                                                       |
| Secrets   | Per-domain-pack vault namespace + per-tenant KMS key                                                                                                         |
| Compute   | Per-domain-pack Pod/thread-pool isolation; resource quotas enforced per domain                                                                               |
| Network   | Shared ingress; JWT scope includes `domain_pack_id` claim                                                                                                    |

### 2.4 Use Cases

- Financial conglomerates operating multiple regulated entities
- Platform-as-a-Service operators offering multiple domains to enterprise clients
- Production environments with > 3 domain packs

### 2.5 Additional Requirements

- Each domain pack must declare `crossPackDependencies` in its `DomainManifest` (see `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md §8`).
- Cross-domain event routing only permitted via K-05 with explicit subscription.
- Domain packs may not share database schemas or table namespaces.

---

## 3. Model 3 — Federated (Domain-Owned Kernel Instances)

### 3.1 Description

Each domain pack runs with its own dedicated AppPlatform Kernel instance. A Federation Mesh (control plane) coordinates cross-domain identity, event routing, and observability.

### 3.2 Topology

```
                    ┌─────────────────────┐
                    │  Federation Mesh     │
                    │  (Control Plane)     │
                    │  - Cross-domain IAM  │
                    │  - Topology registry │
                    │  - Health aggregator │
                    └──────┬────────┬──────┘
                           │        │
         ┌─────────────────┘        └─────────────────┐
         │                                             │
┌────────▼──────────┐                   ┌─────────────▼──────────┐
│  Kernel A         │                   │  Kernel B               │
│  Domain: Cap. Mkt │◄──VerifiedEvent──►│  Domain: Banking        │
│  Tenants: A1..A5  │                   │  Tenants: B1..B3        │
└───────────────────┘                   └────────────────────────┘
```

### 3.3 Isolation Guarantees

| Resource  | Isolation Level                                                                        |
| --------- | -------------------------------------------------------------------------------------- |
| Data      | Full database-level isolation per domain kernel; no shared tables                      |
| Event Bus | Separate Kafka clusters per domain; bridged via verifiable cross-domain relay          |
| Config    | Fully separate config stores; Federation Mesh propagates global platform policies only |
| Secrets   | Separate vault instances per domain; Federation Mesh federated identity only           |
| Compute   | Separate Kubernetes namespaces/clusters per domain                                     |
| Network   | Separate ingress per domain; cross-domain traffic over mTLS verified channels          |

### 3.4 Cross-Domain Event Routing (Federated)

In Model 3, cross-domain events are relayed via the Federation Mesh:

1. Domain A signs and publishes an event to its local K-05.
2. K-05 forwards to the Federation Mesh relay if the topic has registered cross-domain subscribers.
3. The Mesh verifies the event signature and `source_domain_pack_id`.
4. The Mesh delivers to Domain B's K-05 over mTLS with a `federation_relay_id` envelope field.
5. Domain B's K-05 validates the relay signature before delivering to the subscribing pack.

### 3.5 Use Cases

- Complex regulatory separation requirements (e.g., different regulators for banking vs. capital markets in the same enterprise)
- Independent upgrade cycles per domain
- Different uptime/compliance tiers per domain
- Air-gap scenarios for specific high-security domains

---

## 4. Model 4 — Hybrid Cloud / Air-Gap

### 4.1 Description

The AppPlatform Kernel runs partially on-premises (air-gapped from the internet) for sensitive data, with non-sensitive components in the cloud. Config packs are deployed as signed air-gap bundles.

### 4.2 Topology

```
┌────────────────────────────────────┐    ┌──────────────────────────────────────┐
│        On-Premises (Air-Gap)        │    │         Cloud (Managed)              │
│  ┌─────────────────────────────┐   │    │  ┌───────────────────────────────┐   │
│  │  K-05 Event Store           │   │    │  │  K-06 Observability (metrics) │   │
│  │  K-07 Audit Ledger          │   │    │  │  K-13 Admin Portal (UI)       │   │
│  │  K-16 Ledger (PII data)     │   │    │  │  K-09 AI Governance           │   │
│  │  K-01 IAM (identity store)  │   │    │  └───────────────────────────────┘   │
│  └───────────────────────────┬─┘   │    │                                      │
│                               │     │    │    Metrics/Telemetry only            │
│  Domain Packs (data layer)    │     │    │    No PII egress                     │
└───────────────────────────────┼─────┘    └──────────────────────────────────────┘
                                │
                         Outbound only:
                         Metrics, anonymized traces,
                         compressed audit summaries
```

### 4.3 Air-Gap Requirements

| Requirement           | Specification                                                                |
| --------------------- | ---------------------------------------------------------------------------- |
| Config pack delivery  | Via signed air-gap bundle (`.pgk` file, Ed25519 signature) generated by K-02 |
| No outbound internet  | Kernel operates with zero inbound/outbound internet access at data layer     |
| NTP                   | Internal NTP server required; K-15 calendar data updated via air-gap bundle  |
| Certificate lifecycle | Internal PKI; certs renewed via bundle import, not ACME                      |
| Observability         | Metrics exported to on-premises Prometheus/Grafana; SIEM for audit events    |
| Upgrades              | Platform upgrades delivered as signed bundles via USB/secure transfer        |

### 4.4 Use Cases

- Nepal Rastra Bank (NRB) compliant deployments requiring on-premises data residency
- Defence/government-linked regulated entities
- Offline-capable branch deployments with periodic sync

---

## 5. Isolation Guarantees Matrix

| Guarantee                         | Model 1     | Model 2           | Model 3              | Model 4       |
| --------------------------------- | ----------- | ----------------- | -------------------- | ------------- |
| Tenant data row isolation         | ✅          | ✅                | ✅                   | ✅            |
| Domain data schema isolation      | ❌ (shared) | ✅                | ✅                   | ✅            |
| Separate Kafka cluster per domain | ❌          | ❌                | ✅                   | ✅            |
| Separate Kubernetes namespace     | ❌          | Partial           | ✅                   | ✅            |
| Full database instance isolation  | ❌          | ❌                | ✅                   | ✅            |
| Air-gap capable                   | ❌          | ❌                | Partial              | ✅            |
| Cross-domain event relay          | N/A         | K-05 subscription | Federated mTLS relay | N/A           |
| Resource quota enforcement        | Soft (JVM)  | Pod-level         | Cluster-level        | Cluster-level |

---

## 6. Config Pack Activation per Model

| Activation Trigger        | Model 1 | Model 2 | Model 3 | Model 4          |
| ------------------------- | ------- | ------- | ------- | ---------------- |
| Hot reload via API        | ✅      | ✅      | ✅      | ❌ (bundle only) |
| Air-gap bundle import     | ✅      | ✅      | ✅      | ✅ (mandatory)   |
| Canary rollout            | ✅      | ✅      | ✅      | ❌               |
| Maker-checker enforcement | ✅      | ✅      | ✅      | ✅               |

---

## 7. Data Residency Compliance

| Jurisdiction      | Required Model | Rationale                                             |
| ----------------- | -------------- | ----------------------------------------------------- |
| Nepal (NRB/SEBON) | Model 1, 4     | Financial data must reside on-premises in Nepal       |
| EU (GDPR)         | Model 1, 2, 3  | With EU-region cloud deployment; air-gap not required |
| India (RBI)       | Model 1, 4     | Core banking data must reside within India            |
| Singapore (MAS)   | Model 1, 2     | Cloud allowed with MAS-approved cloud providers       |

Data residency rules are enforced via K-06 telemetry routing and K-08 data classification. The jurisdiction's T1 Config Pack declares data residency zones.

---

## 8. Upgrade & Rollback Procedures

### 8.1 Kernel Upgrade

1. **Validate**: Run `platform upgrade-check --model {model}` to verify pack compatibility.
2. **Stage**: Deploy the new kernel version alongside the current one using blue-green traffic split (Models 1/2/3) or bundle pre-load (Model 4).
3. **Migrate**: Run any DB migrations with backward-compatible schema changes only.
4. **Cut-Over**: Shift traffic to new kernel; monitor for 15 minutes.
5. **Rollback Trigger**: Any P0/P1 alert within 15-minute window auto-triggers rollback.

### 8.2 Domain Pack Upgrade

Domain packs are independently upgradeable per the K-04 Plugin Runtime lifecycle (see `EPIC-K-04-Plugin-Runtime.md`). Cross-pack subscription compatibility is validated before activation (see `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md §12`).

### 8.3 Config Pack Rollback (All Models)

```
POST /api/v1/kernel/k02/packs/{pack_id}/rollback
Body: { "target_version": "v2.0.3", "reason": "...", "approver_id": "..." }
```

Rollback is maker-checker enforced. All tenants using the rolled-back pack receive a `ConfigRolledBackEvent`.

---

## 9. Model Selection Guide

Use this decision tree when choosing a deployment model:

```
Q1: Do any regulatory requirements mandate full DB isolation per domain?
  YES → Model 3 or 4
  NO  → Q2

Q2: Is internet connectivity prohibited for the data layer?
  YES → Model 4
  NO  → Q3

Q3: Are there multiple domain packs sharing the same kernel?
  YES → Model 2
  NO  → Model 1
```

When in doubt, start with **Model 1** and migrate to **Model 2** as additional domain packs are onboarded. The platform's event-sourced architecture makes inter-model migration straightforward — all state is reconstructible from the K-05 Event Store.
