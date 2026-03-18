# PHR Platform — Monitoring and Alerting Runbook

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** DevOps Lead  
**Approval status:** Draft for production readiness  
**Classification:** Internal

This runbook defines the minimum observable signals and first-response actions for PHR production readiness.

---

## 1. Core alerts

| Alert | Threshold | Initial action |
| --- | --- | --- |
| API error rate | greater than 1% for 5 minutes | inspect recent deploy, dependency health, and traces |
| API p99 latency | greater than 2 seconds for 5 minutes | inspect slow routes, DB saturation, cache health |
| database disk usage | greater than 80% | scale storage and review retention jobs |
| worker queue lag | greater than 10 minutes for OCR or reminders | inspect worker concurrency and upstream errors |
| openIMIS circuit open | 3 consecutive minutes | verify upstream status and fallback behavior |
| auth failures spike | 5x baseline for 10 minutes | inspect brute-force activity and auth provider health |

---

## 2. Required dashboards

- request rate, latency, and error rate by route
- DB connections, slow queries, and storage growth
- Ceph request health and capacity
- worker queue depth and retry counts
- security events and auth failures
- tenant isolation and consent denial metrics

---

## 3. On-call checklist

1. confirm alert validity
2. identify blast radius and affected tenants or routes
3. apply containment or rollback if needed
4. preserve evidence for post-incident review
5. update incident channel and ticket timeline

This runbook should be used with the incident response playbook for any `SEV-1` or `SEV-2` event.