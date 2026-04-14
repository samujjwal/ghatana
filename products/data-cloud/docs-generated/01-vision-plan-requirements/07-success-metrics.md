# Data Cloud Success Metrics

**Document ID:** DC-METRICS-001  
**Version:** 1.0  
**Date:** 2026-04-13  
**Status:** Proposed KPI framework  
**Evidence Base:** platform docs, ICP/JTBD proposal, and readiness audit

---

## Executive Summary

Data Cloud currently has no documented KPI framework tying product adoption, platform quality, and business outcomes together. This document defines a metric system that matches the current product shape and the recommended engineering-led adoption model.

---

## 1. Metric Philosophy

The right metrics for Data Cloud should measure four things together:

1. whether teams adopt the platform,
2. whether workloads expand on it,
3. whether the platform performs reliably enough to retain trust, and
4. whether usage turns into durable commercial value.

---

## 2. North-Star Candidate

### Recommended North-Star Metric

**Number of production workloads actively running on Data Cloud each month**

### Why This Works

- It reflects real adoption, not exploration only.
- It maps to the platform nature of the product.
- It supports a land-and-expand motion.
- It is harder to inflate than raw account or user counts.

---

## 3. Leading Indicators

### 3.1 Activation Metrics

| Metric                               | Definition                                               | Why It Matters                   |
| ------------------------------------ | -------------------------------------------------------- | -------------------------------- |
| Time to first connected workload     | Time from environment setup to first live integration    | Measures onboarding friction     |
| Time to first data write             | Time to first persisted entity or event                  | Measures setup clarity           |
| Time to first query result           | Time to first useful operational or analytical output    | Measures initial value           |
| Time to first real-time subscription | Time to first SSE/WebSocket or event consumer connection | Measures event-value realization |

### 3.2 Adoption Breadth Metrics

| Metric                       | Definition                                                                     | Why It Matters                     |
| ---------------------------- | ------------------------------------------------------------------------------ | ---------------------------------- |
| Active workloads per account | Number of distinct workloads using the platform monthly                        | Measures expansion                 |
| Active tenants managed       | Number of tenant contexts actively supported                                   | Matches multi-tenant product story |
| Surface adoption mix         | Percentage of accounts using storage only vs events vs analytics vs ML-support | Shows where the platform is sticky |
| API breadth used             | Number of platform domains used by each account                                | Measures consolidation effect      |

### 3.3 Trust and Reliability Metrics

| Metric                            | Definition                                                             | Why It Matters                  |
| --------------------------------- | ---------------------------------------------------------------------- | ------------------------------- |
| Successful workload runs          | Percentage of production workloads completing without critical failure | Measures stability              |
| p95 API latency by workload class | Tail latency for core platform APIs                                    | Measures performance confidence |
| Event processing lag              | Time between publish and consumable availability                       | Measures real-time value        |
| Severity-1 incidents per month    | Critical production incidents                                          | Measures operational trust      |

---

## 4. Lagging Indicators

| Metric                                          | Definition                                               | Why It Matters                  |
| ----------------------------------------------- | -------------------------------------------------------- | ------------------------------- |
| Monthly production workloads                    | Core north-star outcome                                  | Measures retained adoption      |
| Expansion rate across teams                     | Accounts growing from one workload to multiple           | Measures platform pull          |
| Gross revenue retention / net revenue retention | Commercial durability                                    | Measures whether value persists |
| Renewal / expansion conversion                  | Conversion from evaluation to paid or broader deployment | Measures commercial traction    |

---

## 5. Quality Gates for Product Readiness

These are not KPIs for marketing dashboards. They are release and credibility gates.

| Gate                                       | Suggested Threshold                            |
| ------------------------------------------ | ---------------------------------------------- |
| Documented and reconciled readiness status | 100% of core claims aligned across docs        |
| Load validation coverage                   | Core workloads benchmarked and published       |
| Security posture statement                 | Finalized and internally approved              |
| Test coverage clarity                      | High-risk areas explicitly tagged and tracked  |
| Onboarding path                            | One documented first-workload recipe published |

---

## 6. Instrumentation Requirements

To support this metric framework, the product should emit:

- workload registration events
- workload lifecycle state changes
- tenant-aware API usage metrics
- event throughput and lag metrics
- query latency and failure rates
- feature-store and ML-support usage metrics

---

## 7. Dashboard Structure

### Product Dashboard

- time to first workload
- active workloads
- adoption by platform surface
- expansion by account

### Platform Dashboard

- API latency
- event lag
- workload success rate
- incident rate

### Commercial Dashboard

- evaluation to production conversion
- expansion rate
- retention
- contract mix by package tier

---

## 8. Final Recommendation

Do not measure Data Cloud success by feature count or document count. Measure it by whether teams put real workloads on the platform, expand usage over time, and keep trusting it under production conditions.
