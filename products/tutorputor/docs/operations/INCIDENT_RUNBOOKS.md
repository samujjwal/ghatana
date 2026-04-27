# TutorPutor Incident Runbooks

Date: 2026-04-27  
Scope: queue, gRPC content-generation, LLM provider, Stripe, LTI

## Severity Model

- `SEV-1`: user-facing outage in critical journeys (dashboard, AI tutor, content generation, LTI launch, payment checkout)
- `SEV-2`: degraded performance or partial loss of a critical dependency
- `SEV-3`: non-critical degradation with stable workaround

## Queue / Worker Incident (BullMQ)

### Symptoms
- Growing queue depth (`bullmq_queue_depth`) for `content-generation`
- Low completion throughput (`bullmq_jobs_completed_total`)
- DLQ growth or repeated retries

### Immediate Actions
1. Verify Redis health and connectivity from platform service.
2. Confirm worker mode flags:
   - `CONTENT_WORKER_ENABLED=true`
   - `CONTENT_QUEUE_DISABLED=false`
3. Restart worker process only (avoid full platform restart first).
4. Pause low-priority generation traffic if queue depth exceeds SLO threshold.

### Validation
- Queue depth trend flattens or declines for 10 minutes
- Job completion rate recovers above baseline
- No new DLQ spikes

## gRPC Content-Generation Incident

### Symptoms
- `TutorPutorContentGrpcCircuitOpen` alert firing
- generation requests failing with gRPC transport errors

### Immediate Actions
1. Check content-generation health endpoints (`/health`, `/ready`).
2. Validate endpoint/port (`CONTENT_GENERATION_GRPC_HOST`, `CONTENT_GENERATION_GRPC_PORT`).
3. Verify proto compatibility between platform and content-generation service.
4. Restart content-generation pod/service and re-check readiness.

### Validation
- Circuit closes and remains closed for 15 minutes
- `content generation completion` SLO recovers

## LLM Provider Incident

### Symptoms
- AI tutor latency p95 breach
- elevated timeout/rate-limit responses from model provider

### Immediate Actions
1. Verify local provider availability (Ollama or configured provider endpoint).
2. Shift traffic to fallback model profile if configured.
3. Reduce concurrency and request token limits.
4. Enable degraded-mode messaging for AI tutor responses.

### Validation
- AI tutor success rate and p95 latency return to SLO target
- Rate-limit alerts clear

## Stripe Incident

### Symptoms
- `TutorPutorStripeWebhookFailures` alert firing
- Checkout sessions created but subscription state not updated

### Immediate Actions
1. Verify webhook signing secret and endpoint URL.
2. Inspect failed webhook event IDs and replay via Stripe dashboard/CLI.
3. Confirm billing route feature flags are correct for environment.
4. Reconcile subscription status for impacted tenant/user records.

### Validation
- webhook failure rate returns to baseline
- replayed events process successfully

## LTI Incident

### Symptoms
- `TutorPutorLTILaunchErrors` alert firing
- launch failures from LMS platforms

### Immediate Actions
1. Validate JWKS endpoint availability and key freshness.
2. Verify platform registration (issuer, client ID, deployment ID).
3. Check nonce/state validation logs for replay or mismatch.
4. Confirm LMS callback URLs and clock skew tolerance.

### Validation
- LTI launch success rate stabilizes above SLO threshold
- no new nonce/state mismatch bursts

## Post-Incident Requirements

1. Capture incident timeline and root cause in postmortem.
2. Add regression test for the failure mode.
3. Update related dashboard panel or alert threshold if needed.
4. Link incident report in architecture evidence docs.
