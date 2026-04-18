# Data Cloud Packaging and Pricing Framework

**Document ID:** DC-PACKAGING-001  
**Version:** 1.0  
**Date:** 2026-04-13  
**Status:** Strategic proposal pending business validation  
**Evidence Base:** current product documentation, ICP/JTBD hypothesis, and platform architecture

---

## Executive Summary

The current Data Cloud docs define a large capability surface but no commercial model. This document proposes a packaging framework that matches the product's likely adoption pattern: one team starts with one workload, then expands usage across more tenants, workflows, and services.

This is a working model, not a finalized price sheet.

---

## 1. Packaging Principles

1. Package around **adoption stage and operational scope**, not just feature count.
2. Keep the entry motion simple enough for platform evaluation.
3. Reserve enterprise trust, governance, and support elements for higher tiers.
4. Avoid pricing that punishes the integrated platform story by charging independently for every subsystem.
5. Use usage dimensions that map to real platform cost drivers.

---

## 2. Recommended Packaging Model

### 2.1 Developer Tier

**Audience:** evaluation users, internal platform prototyping, single-team experimentation

**Included**

- Core entity and event APIs
- Basic analytics surface
- Limited real-time interfaces
- Local or non-production deployment support

**Constraints**

- Single environment
- Lower throughput and storage ceilings
- No formal SLA
- Limited collaboration and governance controls

### 2.2 Team Tier

**Audience:** one product team or domain team running an early production workload

**Included**

- Core operational platform capabilities
- Real-time features
- Team collaboration and shared configurations
- Basic monitoring and operational guidance

**Constraints**

- Moderate limits on tenants, throughput, or retained data
- Standard support only
- Limited advanced governance and enterprise controls

### 2.3 Platform Tier

**Audience:** platform engineering groups standardizing multiple workloads or product surfaces

**Included**

- Full platform runtime surface
- Feature-store and ML-support capabilities
- Expanded observability and admin controls
- Multi-workload and multi-team deployment patterns

**Commercial intent**

- This should be the primary target tier for the recommended ICP.

### 2.4 Enterprise Tier

**Audience:** larger customers with formal compliance, procurement, and support requirements

**Included**

- Enterprise support and onboarding
- Advanced governance and audit workflows
- Higher trust commitments and negotiated commercial terms
- Deployment and operational advisory support

**Dependency**

- This tier is only credible once readiness claims are reconciled and validated.
- Current UI trust posture is driven more by Trust Center plus boundary-guarded admin pages than by a fully mature settings or alerting console.

---

## 3. Metering Dimensions

### Recommended Metering Dimensions

| Dimension                             | Why It Fits                                                    |
| ------------------------------------- | -------------------------------------------------------------- |
| Active workloads or deployed services | Maps to platform value and adoption breadth                    |
| Event throughput                      | Maps to runtime and streaming cost                             |
| Retained storage volume               | Maps to multi-tier storage cost                                |
| Managed tenants                       | Fits the multi-tenant platform story                           |
| Enabled advanced capabilities         | Useful only for enterprise add-ons, not the main pricing model |

### Dimensions to Avoid as Primary Price Anchor

| Dimension                           | Why to Avoid                                          |
| ----------------------------------- | ----------------------------------------------------- |
| Per-seat as the main metric         | Undervalues the infrastructure nature of the product  |
| Per-feature à la carte pricing      | Breaks the integrated platform story                  |
| Overly fine-grained compute billing | Recreates the unpredictability buyers already dislike |

---

## 4. Packaging by Buyer Outcome

| Buyer Goal                                      | Best Initial Package |
| ----------------------------------------------- | -------------------- |
| Evaluate architectural fit                      | Developer            |
| Replace one fragmented workload stack           | Team                 |
| Standardize multiple internal services          | Platform             |
| Roll out with formal governance and procurement | Enterprise           |

---

## 5. What Must Be True Before Packaging Is Finalized

1. The team agrees on the primary ICP.
2. The first three launch workloads are identified.
3. Usage telemetry exists for storage, event volume, and workload breadth.
4. Readiness claims for security and scale are supported.
5. Support and deployment obligations are operationally defined.

---

## 6. Immediate Commercial Decisions Required

| Decision                                                 | Why It Matters                                |
| -------------------------------------------------------- | --------------------------------------------- |
| Self-hosted, managed, or hybrid commercial motion        | Changes pricing, support, and onboarding      |
| Which capabilities are available in the entry tier       | Determines evaluation friction                |
| Whether ML-support capabilities are bundled or premium   | Affects differentiation strategy              |
| What counts as a billable workload                       | Determines predictability and value alignment |
| Which enterprise controls require validation before sale | Prevents over-promising                       |

---

## 7. Recommended Next Step

Run pricing and packaging validation against the ICP document using three concrete launch workloads and one model per tier. Until that happens, packaging should remain a proposal, not external messaging.
