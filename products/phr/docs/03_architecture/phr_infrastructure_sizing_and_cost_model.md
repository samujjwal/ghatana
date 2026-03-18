# PHR Platform — Infrastructure Sizing and Cost Model

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** DevOps Lead  
**Approval status:** Draft for budget review  
**Classification:** Internal

This document provides a planning-level sizing and cost model for the Core MVP and early scale phases.

---

## 1. Sizing assumptions

- active users grow from 10K to 100K over 3 years
- average document storage per active patient starts at 50 MB and grows over time
- peak read traffic is timeline and profile heavy
- OCR and ASR jobs are bursty and better handled by worker concurrency than oversized always-on compute

---

## 2. Planning model by user count

| Scale point | API compute | Worker compute | Postgres | Ceph usable storage | Monthly bandwidth planning |
| --- | --- | --- | --- | --- | --- |
| 10K users | 2 small app nodes | 1 medium worker node | 1 primary + 1 replica | 1 TB | 1 to 2 TB |
| 50K users | 4 medium app nodes | 2 medium worker nodes | 1 stronger primary + 1 replica | 5 TB | 5 to 8 TB |
| 100K users | 6 to 8 medium app nodes | 3 to 4 worker nodes | HA primary plus replicas | 10 TB+ | 10 to 15 TB |

---

## 3. Main cost drivers

- document and media storage
- backup retention and off-site copies
- OCR and ASR API usage
- observability retention and tracing volume
- staging and pre-production environments kept close enough to production to validate failover and security scans

---

## 4. Optimization guidance

- keep timeline and summary queries indexed before scaling API nodes
- quarantine and retention policies should remove unnecessary duplicate document versions
- run OCR and ASR in queued bursts where latency is not user-blocking
- align log retention with compliance rather than indefinite storage

This model is planning guidance and should be revised with actual telemetry after staging traffic tests.