# YAPPC Onboarding — Product Manager

Welcome to the YAPPC (Yet Another Product / Project Coordinator) Lifecycle Platform. This guide covers everything a Product Manager needs to understand, monitor, and operate the lifecycle service effectively.

---

## What Is YAPPC?

YAPPC is the lifecycle management backbone for Ghatana's product delivery processes. It tracks the state of any work item — a feature, a product, a software component — through a defined set of **lifecycle phases**, enforcing governance gates, approval workflows, and AI-assisted quality suggestions at each transition.

As a PM, you interact with YAPPC through:

- **The Lifecycle Dashboard** — a Grafana view of phase health, approval queues, and AI suggestion quality
- **The Approval API** — reviewing and approving or rejecting phase gate transitions
- **Feature Flag Configuration** — controlling rollout behaviour for individual lifecycle rules
- **SLO and KPI reports** — tracking the health of YAPPC in your roadmap reviews

---

## The Eight Lifecycle Phases

| # | Phase | What it means |
|---|-------|--------------|
| 1 | `CONCEPTION` | Initial idea capture |
| 2 | `DRAFT` | Structured requirements in progress |
| 3 | `REVIEW` | Peer and stakeholder review complete |
| 4 | `APPROVAL` | Formal sign-off obtained |
| 5 | `IMPLEMENTATION` | Active engineering in progress |
| 6 | `VALIDATION` | QA, UAT, and readiness testing |
| 7 | `DEPLOYMENT` | Rolled out to production |
| 8 | `CLOSURE` | Retrospective complete, archived |

Phase **advances require the phase gate rules** for that transition to pass. You can see which rules are active under each gate in the Architecture Decision Records at `docs/adr/`.

---

## Your Daily Workflow

### Morning

1. Open the [YAPPC KPI Grafana Dashboard](http://localhost:3001/d/yappc-lifecycle-kpi-001).
2. Check the **Pending Approvals** stat panel. If the count is > 20, review the queue.
3. Confirm the **SLO Error Budget Burn Rate** panels are green (< 1.0×).

### Reviewing Phase Transitions

A phase advance request lands in your queue when a work item meets all automated gate criteria but requires human approval (configured per phase). You receive a notification via the standard alert channel.

To approve or reject via API:

```bash
# List pending approvals for your tenant
curl -H "Authorization: Bearer $TOKEN" \
     -H "X-Tenant-Id: $TENANT_ID" \
     https://yappc.internal/api/v1/approvals?status=PENDING

# Approve a specific transition
curl -X POST \
     -H "Authorization: Bearer $TOKEN" \
     -H "X-Tenant-Id: $TENANT_ID" \
     https://yappc.internal/api/v1/approvals/{approvalId}/approve \
     -d '{"comment": "LGTM — all quality criteria met"}'
```

### Managing Feature Flags

Feature flags control which lifecycle rules are enforced and what percentage of users receive experimental rules.

```bash
# View all active flags
curl -H "Authorization: Bearer $TOKEN" https://yappc.internal/api/v1/flags

# Enable auto-approve for low-risk transitions (emergency use only)
curl -X POST https://yappc.internal/api/v1/flags/auto_approve_low_risk_transitions \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"enabled": true, "rollout": 100}'
```

---

## SLO Commitments You Own

| SLO | Target | Your Action If Breached |
|-----|--------|------------------------|
| Lifecycle API availability | 99.9% | Notify YAPPC service owner; check approval queue for stuck items |
| Phase gate latency p99 | < 200 ms | No immediate PM action; file engineering follow-up |
| Approval response time (P50) | < 15 min business hours | Review team load; consider escalation |
| AI suggestion acceptance rate | > 40% | Review AI suggestion quality report; adjust prompt config |

---

## GDPR & Data Compliance

If a customer or tenant submits a data deletion or export request under GDPR Articles 17/20:

1. Use the GDPR API hosted on the lifecycle service:

```bash
# Request data export for a tenant
curl -X GET \
     -H "Authorization: Bearer $TOKEN" \
     -H "X-Tenant-Id: $TENANT_ID" \
     https://yappc.internal/api/v1/gdpr/tenant/$TENANT_ID/export

# Request data deletion for a tenant
curl -X DELETE \
     -H "Authorization: Bearer $TOKEN" \
     -H "X-Tenant-Id: $TENANT_ID" \
     https://yappc.internal/api/v1/gdpr/tenant/$TENANT_ID
```

2. Log the request and completion timestamp in your compliance tracker.
3. The deletion API responds with a summary map of deleted record counts per collection.

---

## Key Contacts

| Topic | Contact |
|-------|---------|
| Service outages / SLO incidents | YAPPC service owner (PagerDuty) |
| Lifecycle rule changes | Engineering lead + Architecture review |
| GDPR requests | Compliance officer + YAPPC service owner |
| Feature flag changes | Product Squad + Feature flag owners |
