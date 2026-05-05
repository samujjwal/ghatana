# AEP Gateway Deployment Runbook

## Purpose

Operate the AEP Fastify gateway safely in production with explicit controls for rate limiting, request-size limits, backend resilience, and trace propagation.

## Environment Variables

- `PORT`: Gateway listen port. Default `3002`.
- `AEP_BACKEND_URL`: Backend upstream base URL.
- `JWT_SECRET`: Required JWT verification secret.
- `ALLOWED_ORIGINS`: Comma-separated CORS allowlist.
- `REQUEST_BODY_LIMIT_BYTES`: Max accepted request body bytes. Default `1048576` (1 MiB).
- `RATE_LIMIT_WINDOW_MS`: Rate-limit window in milliseconds. Default `60000`.
- `RATE_LIMIT_MAX_REQUESTS`: Max requests per key per window. Default `300`.
- `BACKEND_TIMEOUT_MS`: Upstream timeout per attempt. Default `10000`.
- `BACKEND_RETRY_ATTEMPTS`: Retry attempts for transient failures. Default `3`.
- `BACKEND_RETRY_INITIAL_BACKOFF_MS`: Initial retry delay. Default `150`.
- `BACKEND_RETRY_MAX_BACKOFF_MS`: Max retry delay. Default `2000`.
- `BACKEND_BREAKER_FAILURE_THRESHOLD`: Consecutive failures before opening breaker. Default `5`.
- `BACKEND_BREAKER_OPEN_MS`: Breaker open duration in milliseconds. Default `30000`.

## Startup Checklist

1. Confirm `JWT_SECRET` is set and sourced from secret management.
2. Confirm `AEP_BACKEND_URL` resolves and `/health` is reachable.
3. Set `ALLOWED_ORIGINS` to the exact deployment origins (no wildcards in production).
4. Set rate-limit and body-limit values appropriate for expected traffic profile.
5. Start the gateway and verify `/health` and `/ready`.

## Runtime Signals

- `GET /health`: gateway process liveness.
- `GET /ready`: backend dependency readiness.
- `GET /metrics`: gateway in-process metrics snapshot.

All responses include `x-correlation-id`. Gateway forwards `traceparent` and `x-correlation-id` to backend.

## Failure Handling

### Backend outage

- Symptoms: `/ready` returns `503`, `/api/*` responses return `502` or breaker-open failures.
- Action:
  1. Verify backend health.
  2. Confirm network path from gateway to backend.
  3. Check breaker settings if short outages are causing prolonged open state.

### Excess 429s

- Symptoms: clients receive `429 Too Many Requests` from gateway.
- Action:
  1. Confirm request burst pattern.
  2. Increase `RATE_LIMIT_MAX_REQUESTS` only after abuse analysis.
  3. Increase `RATE_LIMIT_WINDOW_MS` only when traffic profile is legitimate.

### Request-size rejections

- Symptoms: client requests fail due to body size constraints.
- Action:
  1. Validate request payload size expectations.
  2. Increase `REQUEST_BODY_LIMIT_BYTES` only if endpoint contract allows larger payloads.

## Safe Rollback

1. Revert to previous known-good gateway image/version.
2. Keep `JWT_SECRET` and CORS config unchanged during rollback.
3. Validate `/health` and `/ready` before restoring full traffic.
4. Confirm trace/correlation headers still propagate end-to-end.

## Security Notes

- Do not run with default local origins in production.
- Do not log JWTs, secrets, or raw credentials.
- Keep rate limiting enabled in production.
- Keep request size limits enabled in production.
