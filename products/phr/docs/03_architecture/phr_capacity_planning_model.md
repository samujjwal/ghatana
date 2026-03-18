# PHR Platform — Capacity Planning Model

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** DevOps Lead  
**Approval status:** Draft for planning  
**Classification:** Internal

This document tracks expected growth and the operational signals that should trigger capacity changes.

---

## 1. Growth targets

| Year | Target users | Operational focus |
| --- | --- | --- |
| Year 1 | 10K | establish stable MVP operations |
| Year 2 | 50K | scale storage, worker throughput, and observability |
| Year 3 | 100K | scale database, caching, and multi-tenant operational isolation |

---

## 2. Capacity triggers

| Resource | Trigger | Action |
| --- | --- | --- |
| API nodes | CPU above 65% sustained or p95 latency drift | scale horizontally |
| workers | OCR or reminder lag above SLA | add worker concurrency or nodes |
| Postgres | connection saturation or slow query growth | tune queries, add read replica, scale primary |
| Ceph | storage above 70% | add capacity and review retention |
| observability stack | log or trace retention pressure | adjust retention or scale storage |

---

## 3. Planning review cadence

- monthly during MVP rollout
- quarterly after operations stabilize
- immediately after a major partner onboarding or nationwide campaign event

This model should be updated with production telemetry once the platform is live.