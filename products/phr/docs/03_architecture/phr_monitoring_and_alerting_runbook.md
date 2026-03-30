# PHR Platform — Monitoring and Alerting Runbook

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** DevOps Lead  
**Approval status:** Draft for production readiness  
**Classification:** Internal

**Canonical URL:** https://docs.ghatana.io/runbooks/phr-monitoring-alerting

This runbook defines the minimum observable signals and first-response actions for PHR production readiness.

---

## 1. Core alerts

| Alert                 | Threshold                                    | Initial action                                        |
| --------------------- | -------------------------------------------- | ----------------------------------------------------- |
| API error rate        | greater than 1% for 5 minutes                | inspect recent deploy, dependency health, and traces  |
| API p99 latency       | greater than 2 seconds for 5 minutes         | inspect slow routes, DB saturation, cache health      |
| database disk usage   | greater than 80%                             | scale storage and review retention jobs               |
| worker queue lag      | greater than 10 minutes for OCR or reminders | inspect worker concurrency and upstream errors        |
| openIMIS circuit open | 3 consecutive minutes                        | verify upstream status and fallback behavior          |
| auth failures spike   | 5x baseline for 10 minutes                   | inspect brute-force activity and auth provider health |

Prometheus rule source for the integrated PHR-Finance billing path:

- `monitoring/prometheus/rules/phr-finance-kernel.yml`

Alertmanager routes for these alerts:

- `monitoring/alertmanager/alertmanager.yml` via `product=phr|finance`
- critical alerts -> `#phr-finance-oncall` + PagerDuty
- warning alerts -> `#phr-finance-alerts`

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

---

## 4. PHR-Finance Billing Bridge Triage

Use this section when any of the following alerts fire:

- `PhrApiHighErrorRate`
- `PhrApiP99LatencyHigh`
- `FinanceLedgerPostingErrorRate`
- `FinanceLedgerUnbalancedEntries`
- `PhrFinanceBillingBridgeFailures`

Immediate triage sequence:

1. Validate whether failures correlate to recent deploys in PHR, Finance, or `platform:java:billing`.
2. Check billing bridge retry/error counters and ledger post/reverse counts.
3. Confirm circuit-breaker state transitions in finance billing adapter logs.
4. Verify reconciliation and unbalanced-entry counters before resuming normal traffic.

---

## 5. Clinical Workflow Triage

Use this section when any of the following clinical-path alerts fire:

- PhrConsentDenialRateHigh
- PhrEmergencyBreakGlassSpike
- PhrLabResultIngestionFailuresHigh
- PhrAppointmentReminderQueueLagHigh

Immediate triage sequence:

1. Identify affected tenant IDs and facilities from logs and alert labels.
2. Validate whether policy, identity, or consent cache drift caused elevated denials.
3. For break-glass spikes, confirm each emergency justification and page security duty lead.
4. For lab ingestion failures, inspect upstream integration health and message retry/dead-letter queues.
5. For reminder lag, check worker concurrency, queue depth, and scheduler heartbeat.
6. Record blast radius and mitigation actions in the incident timeline before closing.
