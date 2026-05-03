# AEP Pipeline Degraded Mode Runbook

**Document type:** Operational runbook  
**Product scope:** AEP (Agent Execution Platform)  
**Severity classification:** P1 — AEP pipeline execution degraded (not fully down)  
**Last updated:** 2026-05-02  
**Owner:** AEP SRE / Platform Engineering  
**Review cadence:** Quarterly and after every P1 incident involving AEP degradation

---

## Purpose

This runbook guides on-call engineers through diagnosing and recovering from an AEP pipeline degraded state: a condition where AEP can receive events and respond to health probes, but pipeline execution success rates, latency, or throughput fall below SLO targets.

**Degraded mode is NOT a full outage.** The AEP server is reachable, but one or more of the following may be true:
- Pipeline execution success rate < 99.5% (SLO breach)
- Agent execution availability < 99.0%
- Intake API p99 latency > 100 ms
- Review queue age p99 > 5 minutes

---

## 1. Detection Signals

### Grafana alerts

The following alerts in `monitoring/grafana/dashboards/aep-slos.json` indicate degraded mode:

| Alert | Prometheus query | Threshold |
|---|---|---|
| Pipeline availability degraded | `aep_pipeline_availability_ratio` | < 0.995 |
| Agent execution degraded | `aep_agent_execution_availability_ratio` | < 0.990 |
| Intake latency elevated | `histogram_quantile(0.99, aep_intake_request_duration_seconds_bucket)` | > 0.1 |
| Review queue backlog | `histogram_quantile(0.99, aep_review_queue_age_seconds_bucket)` | > 300 |

### Health endpoint

```bash
curl -s https://aep.ghatana.internal/health | jq .components
```

Expected healthy output:
```json
{
  "eventIngestion": "healthy",
  "pipelineExecution": "healthy",
  "agentRuntime": "healthy",
  "reviewQueue": "healthy",
  "governance": "healthy"
}
```

Any component reporting `"degraded"` or `"unavailable"` is the starting point for investigation.

---

## 2. Degradation Classification

Identify which scenario applies before executing steps:

| Scenario | Primary signal | Go to |
|---|---|---|
| A: Event ingestion backlog | `aep_event_intake_queue_depth > 1000` | Section 3A |
| B: Agent execution failures | `aep_agent_execution_failure_rate > 0.01` | Section 3B |
| C: Policy governance slowdown | `aep_governance_evaluation_duration_p99 > 500ms` | Section 3C |
| D: HITL review queue backup | `aep_review_queue_age_p99 > 300s` | Section 3D |
| E: LLM / AI dependency unavailable | `aep_llm_requests_errors_total` increasing | Section 3E |
| F: Data Cloud connectivity lost | `aep_datacloud_requests_errors_total` increasing | Section 3F |

---

## 3A: Event Ingestion Backlog

**Cause**: Events are being received faster than pipelines can process them, or pipeline workers are slow/crashed.

### Diagnose

```bash
# Check event queue depth
kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/metrics | grep aep_event_intake_queue_depth

# Check pipeline worker pod health
kubectl -n aep get pods -l component=pipeline-worker
```

### Remediate

```bash
# Scale up pipeline workers
kubectl -n aep scale deployment/aep-pipeline-worker --replicas=10

# Verify queue depth is decreasing
watch -n 5 'kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/metrics | grep aep_event_intake_queue_depth'
```

**Recovery criterion**: Queue depth < 100 events and trending to zero.

---

## 3B: Agent Execution Failures

**Cause**: Agents are failing due to bad input, LLM errors, or resource exhaustion.

### Diagnose

```bash
# Check recent agent execution errors
kubectl -n aep logs deploy/aep-agent-runtime --since=10m | grep ERROR | tail -50

# Check which agent types are failing
kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/metrics | grep aep_agent_execution_failure

# Check resource usage
kubectl -n aep top pods -l component=agent-runtime
```

### Remediate

```bash
# Restart agent runtime pods if resource-exhausted
kubectl -n aep rollout restart deployment/aep-agent-runtime

# If a specific agent type is failing, disable it via governance kill switch
kubectl -n aep exec deploy/aep-server \
  -- curl -s -X POST localhost:8080/admin/governance/disable-agent-type \
  -H "Content-Type: application/json" \
  -d '{"agentType": "<FAILING_AGENT_TYPE>", "reason": "elevated failure rate — incident #<ID>"}'
```

**Recovery criterion**: `aep_agent_execution_failure_rate < 0.005` for 5 consecutive minutes.

---

## 3C: Policy Governance Slowdown

**Cause**: OPA evaluation is slow, or the policy engine pod is resource-constrained.

### Diagnose

```bash
# Check OPA evaluation latency
kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/metrics | grep aep_governance_evaluation_duration

# Check OPA pod health
kubectl -n aep get pods -l component=opa-engine
kubectl -n aep logs -l component=opa-engine --since=5m | tail -30
```

### Remediate

```bash
# Scale up OPA replicas
kubectl -n aep scale deployment/aep-opa-engine --replicas=3

# If OPA is critically slow, enable governance bypass mode (EMERGENCY ONLY — log the decision)
# This bypasses policy evaluation and allows all executions to proceed unblocked
kubectl -n aep exec deploy/aep-server \
  -- curl -s -X POST localhost:8080/admin/governance/emergency-bypass \
  -H "Content-Type: application/json" \
  -d '{"reason": "OPA critically slow — incident #<ID>", "durationMinutes": 30}'
```

> **WARNING**: Emergency governance bypass removes policy enforcement. Log this decision immediately in the incident channel and restore policy enforcement as soon as OPA recovers. Every bypass must be reviewed in the post-mortem.

**Recovery criterion**: `aep_governance_evaluation_duration_p99 < 100ms` for 5 consecutive minutes.

---

## 3D: HITL Review Queue Backup

**Cause**: Human-in-the-loop review items are accumulating faster than reviewers can process them, or the review queue service is degraded.

### Diagnose

```bash
# Check review queue age
kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/metrics | grep aep_review_queue_age

# Check queue backlog count
kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/review-queue/stats | jq .
```

### Remediate

Option 1 — Alert reviewers (first response):
```
Post in #hitl-review-team: "Review queue backlog — P1 incident active. 
Immediate review triage needed. Incident: #<ID>"
```

Option 2 — Adjust routing to increase auto-approval threshold temporarily:
```bash
kubectl -n aep exec deploy/aep-server \
  -- curl -s -X PATCH localhost:8080/admin/review-queue/config \
  -H "Content-Type: application/json" \
  -d '{"confidenceThresholdForAutoApproval": 0.92, "reason": "queue backlog — incident #<ID>"}'
```

**Recovery criterion**: Queue age p99 < 300 seconds.

---

## 3E: LLM / AI Dependency Unavailable

**Cause**: The configured LLM provider (e.g., OpenAI, internal inference service) is unreachable or returning errors.

### Diagnose

```bash
# Check LLM error rate
kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/metrics | grep aep_llm_requests_errors_total

# Test LLM connectivity
kubectl -n aep exec deploy/aep-agent-runtime \
  -- curl -s -o /dev/null -w "%{http_code}" https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

### Remediate

```bash
# Switch to fallback LLM provider (if configured)
kubectl -n aep set env deployment/aep-agent-runtime \
  LLM_PROVIDER=fallback LLM_ENDPOINT=https://fallback-llm.internal/v1

kubectl -n aep rollout status deployment/aep-agent-runtime
```

If no fallback LLM is configured:
```bash
# Queue LLM-dependent tasks for retry; let non-LLM tasks proceed
kubectl -n aep exec deploy/aep-server \
  -- curl -s -X POST localhost:8080/admin/pipeline/enable-llm-task-queue \
  -H "Content-Type: application/json" \
  -d '{"reason": "LLM provider unavailable — incident #<ID>"}'
```

**Recovery criterion**: `aep_llm_requests_errors_total` rate returns to < 0.1% and LLM connectivity test passes.

---

## 3F: Data Cloud Connectivity Lost

**Cause**: AEP cannot reach the Data Cloud API for storage or retrieval operations.

### Diagnose

```bash
# Check Data Cloud error rate from AEP
kubectl -n aep exec deploy/aep-server \
  -- curl -s localhost:8080/admin/metrics | grep aep_datacloud_requests_errors

# Ping Data Cloud from AEP pod
kubectl -n aep exec deploy/aep-server \
  -- curl -s -o /dev/null -w "%{http_code}" https://data-cloud.ghatana.internal/health
# Expected: 200
```

### Remediate

```bash
# If Data Cloud is undergoing maintenance, enable AEP read-from-cache mode
kubectl -n aep exec deploy/aep-server \
  -- curl -s -X POST localhost:8080/admin/enable-cache-fallback \
  -H "Content-Type: application/json" \
  -d '{"reason": "Data Cloud unavailable — incident #<ID>", "maxCacheAgeSeconds": 300}'
```

> **Note**: Cache fallback serves stale data up to 5 minutes old. New event ingestion continues — events are persisted locally and flushed to Data Cloud upon reconnection.

**Recovery criterion**: `aep_datacloud_requests_errors_rate < 0.005` for 5 consecutive minutes.

---

## 4. Escalation

| Condition | Action |
|---|---|
| Degradation persists > 30 minutes despite steps above | Page Data Platform lead + AEP lead |
| Multiple scenarios active simultaneously | Declare P0, initiate [DR runbook](dr-failover-runbook.md) assessment |
| Emergency governance bypass active > 15 minutes | Page security lead for risk sign-off |
| Data loss suspected | Initiate [DR failover runbook](dr-failover-runbook.md) immediately |

---

## 5. Post-Degradation Actions

After service is fully restored:

- [ ] Restore any temporarily changed thresholds or configuration
- [ ] Remove emergency governance bypass if active
- [ ] Document root cause in the incident post-mortem
- [ ] Review SLO burn rate for the window affected
- [ ] Add a regression test or alert if the issue would not have been caught automatically
- [ ] Review and update this runbook if the scenario was not covered or the steps were insufficient

---

## 6. Related Documents

- [Disaster Recovery Failover Runbook](dr-failover-runbook.md)
- [Rollback Runbook](rollback-runbook.md)
- [AEP SLO Dashboard](../../monitoring/grafana/dashboards/aep-slos.json)
- [AEP Dev-Mode Resilience Test](../../products/aep/server/src/test/java/com/ghatana/aep/server/AepDevModeResilienceTest.java)
- [Incident runbooks overview](README.md)
