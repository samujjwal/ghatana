# YAPPC On-Call Runbook

**Service**: YAPPC Lifecycle Service  
**Grafana Dashboard**: http://localhost:3001/d/yappc-lifecycle-kpi-001  
**Prometheus**: http://localhost:9090  
**Jaeger Traces**: http://localhost:16686 → service = `yappc-lifecycle`  
**Loki Logs**: http://localhost:3001 → datasource = Loki, label = `{job="yappc-lifecycle"}`

---

## 1. Quick-Reference Alert Triage

| Alert | Severity | First Action | Escalate If |
|-------|----------|--------------|-------------|
| `YappcSloFastBurnRate` | critical | § 2.1 | Error rate still high after 10 min |
| `YappcSloSlowBurnRate` | warning | § 2.2 | Rate still elevated after 30 min |
| `YappcSloErrorBudgetNearlyExhausted` | warning | § 2.3 | — |
| `YappcLifecycleLatencySloBreached` | warning | § 3 | p99 > 5 s for any single endpoint |
| `YappcPhaseGateLatencyHigh` | warning | § 3.2 | Latency growing monotonically |
| `YappcAiSuggestionErrorRateHigh` | warning | § 4.1 | Circuit breaker opens |
| `YappcAiCircuitBreakerOpen` | critical | § 4.2 | All providers OPEN simultaneously |
| `YappcApprovalBacklogHigh` | warning | § 5 | Backlog > 500 |

---

## 2. Availability Incidents

### 2.1 Fast Burn Rate (critical — page immediately)

**Symptoms**: `YappcSloFastBurnRate` firing; error budget < 2 hours remaining at current burn.

**Diagnostic Steps**

1. Check error breakdown in Grafana → **Phase Transition Health** row → error rate by `target_phase`.
2. Identify the failing endpoint:
   ```
   sum(rate(http_server_requests_seconds_count{job="yappc-lifecycle",status=~"5.."}[5m])) by (uri)
   ```
3. Pull recent error logs:
   ```
   {job="yappc-lifecycle"} |= "ERROR" | json | line_format "{{.message}}"
   ```
4. Open a trace for a failing request via Jaeger; filter `error=true`.

**Common Causes & Fixes**

| Cause | Fix |
|-------|-----|
| Database connection pool exhausted | Restart the lifecycle service pod; verify DB connection limits |
| LLM provider down → unhandled exception leaking to HTTP 500 | Verify circuit breaker state (§ 4.2); check `DefaultAIFallbackService` logs |
| Kubernetes OOM kill | `kubectl describe pod`; increase memory limits in the deployment manifest |
| Bad deploy (rollback) | `kubectl rollout undo deployment/yappc-lifecycle` |

**Escalation**: If not resolved in 10 minutes, escalate to service owner and bridge Data Cloud on-call (shared DB infrastructure).

---

### 2.2 Slow Burn Rate (warning)

**Symptoms**: `YappcSloSlowBurnRate` firing; sustained elevated error rate over a 6-hour window.

**Diagnostic Steps**

1. Plot error rate trend to determine if the rate is increasing, steady, or decreasing.
2. Correlate with recent deployments: `git log --oneline products/yappc/ -20`.
3. Check if a specific lifecycle phase or tenant is responsible:
   ```
   sum(rate(yappc_lifecycle_phase_advances_total{status="error"}[15m])) by (target_phase, tenant_id)
   ```

**Common Causes & Fixes**

| Cause | Fix |
|-------|-----|
| Gradual database degradation | Check slow-query log; run `ANALYZE` on hot tables |
| Memory leak (heap pressure) | Monitor heap metrics; trigger a rolling restart |
| Increased load against fixed resources | Scale out lifecycle service replicas |

---

### 2.3 Error Budget Nearly Exhausted (warning)

No immediate action required if service is currently healthy. Schedule a reliability review within 48 hours. Check CHANGELOG for any recent regressions and open a follow-up issue.

---

## 3. Latency Incidents

### 3.1 Lifecycle API Latency SLO Breached (p99 > 2 s)

**Diagnostic Steps**

1. Identify the slow endpoint:
   ```
   histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job="yappc-lifecycle"}[5m])) by (le, uri))
   ```
2. Check Jaeger for slow traces on that endpoint; look for DB spans > 500 ms.
3. Check AI supplier latency:
   ```
   histogram_quantile(0.99, sum(rate(yappc_ai_suggestion_duration_ms_bucket[5m])) by (le, suggestion_type))
   ```

**Common Causes & Fixes**

| Cause | Fix |
|-------|-----|
| Slow LLM response | Reduce max-token budget; add a 5 s timeout to the AI client |
| N+1 DB query in phase gate validation | Add index; batch the collection query |
| Unindexed tenant-scoped query | Run `EXPLAIN ANALYZE`; add composite index |

### 3.2 Phase Gate Validation Latency High (p95 > 200 ms)

1. Check which validation rules are slow via the `rule_id` label on the validation histogram.
2. If a single deterministic rule is slow, it may be executing a long DB read — cache the result.

---

## 4. AI Incidents

### 4.1 AI Suggestion Error Rate High (> 5%)

1. Check which suggestion type is failing:
   ```
   sum(rate(yappc_ai_suggestion_errors_total[5m])) by (suggestion_type)
   ```
2. Look for LLM gateway timeout errors in logs:
   ```
   {job="yappc-lifecycle"} |= "LLM" |= "timeout"
   ```
3. Verify the LLM provider is reachable from the pod:
   ```
   kubectl exec -it <pod> -- curl -s -o /dev/null -w "%{http_code}" https://<llm-provider-endpoint>/health
   ```

### 4.2 AI Circuit Breaker OPEN

The `DefaultAIFallbackService` opens the circuit after 5 consecutive failures. It automatically probes the provider after 60 seconds.

**Manual steps**

1. Confirm circuit state in logs:
   ```
   {job="yappc-lifecycle"} |= "CIRCUIT_BREAKER_OPEN"
   ```
2. Verify the provider status page and internal connectivity.
3. If the primary provider is down for > 5 minutes, update the `AIFallbackService` configuration to promote the secondary provider.
4. If all providers are OPEN, the service will return HTTP 503 for AI endpoints — notify stakeholders that AI features are degraded.

---

## 5. Approval Workflow Backlog High (> 100 items)

**Diagnostic Steps**

1. Check pending approvals by type and requester:
   ```
   sum(yappc_approvals_pending_total) by (approval_type, requester_role)
   ```
2. Determine if the backlog is growing or stable.
3. Check for stuck approvals (pending > 30 min, no reviewer assigned).

**Resolution Options**

| Severity | Action |
|----------|--------|
| Backlog 100–500, stable | Notify approval team leads |
| Backlog 100–500, growing | Temporarily enable auto-approve for low-risk phase transitions (`DRAFT → REVIEW`) |
| Backlog > 500 | Trigger incident; coordinate with product team to resolve bottleneck |

A feature flag `auto_approve_low_risk_transitions` controls auto-approval behaviour. Set via the flag API:
```
POST /api/v1/flags/auto_approve_low_risk_transitions  { "enabled": true, "rollout": 100 }
```

---

## 6. GDPR Incidents

If a GDPR deletion/export request returns an error:

1. Check `GdprController` logs for the `tenantId` or `userId` in question.
2. Verify that all registered `DeletableCollection` beans responded correctly.
3. If a partial deletion occurred, identify which collections succeeded via the deletion summary object in the response body.
4. Partial deletions must be retried; the API is idempotent (double-deletion is safe).

---

## 7. Key Contacts & Escalation Path

| Role | When to Contact |
|------|----------------|
| YAPPC Service Owner | Any critical alert unresolved after 10 min |
| Data Platform On-Call | DB-related incidents, shared infrastructure |
| LLM Gateway Team | AI provider outages |
| Security On-Call | Any suspected auth bypass or tenant data leak |

---

## 8. Useful Commands

```bash
# Check pod health
kubectl get pods -l app=yappc-lifecycle -n yappc

# Tail service logs
kubectl logs -f -l app=yappc-lifecycle -n yappc --tail=200

# Rolling restart
kubectl rollout restart deployment/yappc-lifecycle -n yappc

# Watch rollout status
kubectl rollout status deployment/yappc-lifecycle -n yappc

# Force immediate circuit breaker metric scrape
curl -s http://<pod-ip>:8082/metrics | grep yappc_ai_circuit_breaker

# Health check
curl http://<pod-ip>:8082/health/readiness
```

## 9. Release Rollout And Rollback Checks

Before promoting a release candidate:

1. Verify `/health/liveness`, `/health/readiness`, and authenticated `/metrics` on the candidate instance.
2. Confirm AI metrics are present in `/metrics`:
   - `yappc_ai_llm_latency_seconds`
   - `yappc_ai_fallback_total`
   - `yappc_ai_inference_failed_total`
3. Confirm traces and logs preserve `X-Correlation-ID` across auth, AI, and workflow failures.
4. Review the `yappc-release-evidence-bundle` artifact for missing journey evidence.

If rollback is required:

1. `kubectl rollout undo deployment/yappc-lifecycle -n yappc`
2. Re-run liveness, readiness, and metrics checks against the restored replica set.
3. Verify the fallback alert rate returns to baseline and no `YappcAiInferenceFailureRateHigh` alert remains firing.
