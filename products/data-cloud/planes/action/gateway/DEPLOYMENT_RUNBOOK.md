# AEP Gateway — Deployment Runbook

This document covers operational procedures for the AEP API Gateway
(`@ghatana/aep-gateway`). It is the canonical reference for configuring,
deploying, monitoring, and debugging the gateway in production.

---

## 1. Environment Variables

All configuration is provided through environment variables. No defaults are
safe for production; review every value before deployment.

| Variable | Required | Default (dev) | Description |
|---|---|---|---|
| `JWT_SECRET` | **Yes** | — | Shared secret used to verify HS256 JWT tokens. Minimum 32 characters. Rotate via secret manager. |
| `AEP_BACKEND_URL` | **Yes** | `http://localhost:8090` | Base URL of the Java AEP backend (no trailing slash). |
| `ALLOWED_ORIGINS` | **Yes** | `http://localhost:5173` | Comma-separated list of allowed CORS origins (e.g., `https://app.example.com`). |
| `PORT` | No | `3002` | Port the gateway listens on. |
| `REQUEST_BODY_LIMIT_BYTES` | No | `1048576` (1 MiB) | Maximum accepted request body size in bytes. Requests exceeding this are rejected with 413. |
| `RATE_LIMIT_WINDOW_MS` | No | `60000` (60 s) | Sliding window duration for rate-limit counters, per tenant or IP. |
| `RATE_LIMIT_MAX_REQUESTS` | No | `300` | Maximum requests allowed per window. Exceed → 429. |
| `BACKEND_TIMEOUT_MS` | No | `10000` (10 s) | Per-attempt timeout for backend HTTP calls. |
| `BACKEND_RETRY_ATTEMPTS` | No | `3` | Maximum retry attempts for transient backend failures (502/503/504). |
| `BACKEND_RETRY_INITIAL_BACKOFF_MS` | No | `150` | Initial exponential-backoff delay in milliseconds. |
| `BACKEND_RETRY_MAX_BACKOFF_MS` | No | `2000` | Maximum backoff cap in milliseconds. |
| `BACKEND_BREAKER_FAILURE_THRESHOLD` | No | `5` | Number of consecutive backend failures before the circuit breaker opens. |
| `BACKEND_BREAKER_OPEN_MS` | No | `30000` (30 s) | Duration the circuit breaker remains open before allowing a probe request. |

### Secret Handling

- **`JWT_SECRET`** must be injected from a secrets manager (e.g., AWS Secrets Manager, HashiCorp Vault, Kubernetes Secret). Never commit it to source control or pass it through CI logs.
- Rotate the secret by deploying a new gateway instance with the updated secret and performing a rolling restart.

---

## 2. Startup and Health Checks

### Startup

```bash
node dist/index.js
```

The gateway starts on `0.0.0.0:${PORT}`. Fastify logs a startup message:
```json
{"level":"info","msg":"Server listening at http://0.0.0.0:3002"}
```

A missing `JWT_SECRET` causes an immediate `FATAL` log and `process.exit(1)`.

### Health Probes

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /health` | None | Liveness — returns `{ status: "ok" }`. Use as Kubernetes `livenessProbe`. |
| `GET /ready` | None | Readiness — probes the AEP backend `/health`. Returns `{ status: "ready" }` (200) or `{ status: "not-ready" }` (503). Use as Kubernetes `readinessProbe`. |

Both endpoints always return an `x-correlation-id` response header.

### Kubernetes Probe Example

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 3002
  initialDelaySeconds: 10
  periodSeconds: 15
readinessProbe:
  httpGet:
    path: /ready
    port: 3002
  initialDelaySeconds: 5
  periodSeconds: 10
  failureThreshold: 3
```

---

## 3. Log Format

The gateway uses Fastify's built-in pino logger in JSON format when `logger: true` is passed (always set in production via `index.ts`).

Every request completion log includes these fields:

```json
{
  "level": "info",
  "time": 1704067200000,
  "msg": "Gateway request completed",
  "correlationId": "3f2a1b4c-...",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "method": "GET",
  "path": "/api/v1/events",
  "statusCode": 200,
  "durationMs": 45
}
```

`traceId` and `spanId` are extracted from the incoming W3C `traceparent` header
(or generated if absent), enabling log-to-trace correlation in Jaeger/Tempo.

Rate-limit violations emit a `warn` log with `rateLimitKey` for capacity analysis.

### Log Shipping

Configure Loki or a log forwarder to scrape stdout. Use the `correlationId` and
`traceId` fields to correlate requests across gateway and backend service logs.

---

## 4. Metrics

The gateway exposes in-process counters accessible via `GatewayMetrics.snapshot()`.
In production these should be scraped by the `GET /metrics` endpoint you wire into
your monitoring stack.

| Metric | Description |
|---|---|
| `httpProxyRequestsByStatus` | Proxy response counts by HTTP status code |
| `authFailuresByReason` | Auth rejections by reason: `missing_token`, `invalid_token`, `tenant_mismatch`, `rate_limited` |
| `tenantMismatchTotal` | JWT-vs-header tenant mismatch count |
| `sseAcceptedTotal` / `sseRejectedTotal` | SSE connection counts |
| `wsAcceptedTotal` / `wsRejectedTotal` / `wsClosedTotal` | WebSocket session counts |
| `backendUnreachableTotal` | Backend connection failure count |
| `backendLatencyMs` | Histogram: count, sum, and bucket distribution of backend latency |

Configure alerts for:
- `authFailuresByReason['rate_limited']` increasing rapidly → possible DDoS.
- `backendUnreachableTotal` increasing → check backend health.
- P99 backend latency > 5 s → escalate to backend team.

---

## 5. Rate Limiting

Rate limiting is applied per-tenant (keyed by `X-Tenant-Id` header) or per-IP
when no tenant header is present.

**Default**: 300 requests per 60-second window.

Tune via `RATE_LIMIT_WINDOW_MS` and `RATE_LIMIT_MAX_REQUESTS`. For high-traffic
tenants, widen the window or raise the limit. Rate-limited requests return 429
with a `correlationId` for tracing.

**Note**: Rate-limit state is in-process only. In a multi-replica deployment,
tenants may receive up to `limit × replica_count` requests before being blocked.
Use a Redis-backed rate limiter (e.g., `@fastify/rate-limit` with Redis store)
for multi-replica enforcement.

---

## 6. Circuit Breaker

The circuit breaker protects against cascading failures when the backend is
unreliable.

**State machine**:
1. **Closed** (normal) — requests flow through. Failures are counted.
2. **Open** — after `BACKEND_BREAKER_FAILURE_THRESHOLD` consecutive failures, the
   breaker opens for `BACKEND_BREAKER_OPEN_MS` milliseconds. All requests return
   503 immediately (`BACKEND_CIRCUIT_OPEN`).
3. **Half-open** — after the open duration elapses, the next request probes the
   backend. Success resets the breaker; failure reopens it.

Tune thresholds for your backend SLA. Lower `BACKEND_BREAKER_FAILURE_THRESHOLD`
to trip faster during brownouts; raise `BACKEND_BREAKER_OPEN_MS` to allow more
recovery time.

---

## 7. WebSocket and SSE

- **WebSocket idle timeout**: 5 minutes by default. Sessions with no messages
  from either side are closed with code 1001.
- **WebSocket heartbeat**: 30-second ping interval. No pong within the interval
  terminates the session.
- **WebSocket message limit**: 1 MiB per message. Oversized messages close the
  connection with code 1009 (Message Too Big).
- **SSE proxy**: Streams backend SSE events verbatim; connection is torn down
  when the backend closes or errors.

---

## 8. OpenTelemetry

The gateway propagates W3C Trace Context via `traceparent` headers:
- Incoming `traceparent` is forwarded to the backend unchanged.
- If absent, a new `traceparent` is synthesised (random 128-bit trace ID +
  64-bit span ID).
- All structured log entries include `traceId` and `spanId` fields extracted from
  the `traceparent` for Loki → Jaeger/Tempo trace linking.

To enable full OTel SDK instrumentation, add `@opentelemetry/sdk-node` and
configure the `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 node --require @opentelemetry/auto-instrumentations-node/register dist/index.js
```

Jaeger UI is available at `http://localhost:16686` in the local monitoring stack.

---

## 9. Rolling Restart / Zero-Downtime Deployment

1. Deploy the new version as a new replica (Kubernetes rolling update or blue/green).
2. Wait for `/ready` to return 200 on the new replica.
3. Drain old replicas gracefully: Fastify respects `SIGTERM` and closes open connections.
4. Verify metrics and error rates are stable before completing the rollout.

**Secret rotation**: Deploy with the new `JWT_SECRET`, wait for existing sessions
to expire (TTL is determined by JWT `exp`), then remove the old replicas.

---

## 10. Incident Response

| Symptom | Likely Cause | Action |
|---|---|---|
| Spike in 429 responses | Traffic burst or attack | Raise `RATE_LIMIT_MAX_REQUESTS`; inspect `rateLimitKey` in logs |
| All `/api/*` return 503 | Circuit breaker open | Check backend health; inspect `BACKEND_CIRCUIT_OPEN` logs; restart backend |
| `/ready` returns 503 | Backend unreachable | Check `AEP_BACKEND_URL`; inspect backend logs |
| 401 spike with `invalid_token` | JWT_SECRET mismatch after rotation | Ensure all clients have updated tokens before completing secret rotation |
| 403 spike with `tenant_mismatch` | Client sending mismatched `X-Tenant-Id` | Audit client configuration; correlate with `correlationId` in logs |
| Memory growth | WebSocket/SSE connection leak | Inspect `wsAcceptedTotal` vs `wsClosedTotal`; check idle timeout config |
