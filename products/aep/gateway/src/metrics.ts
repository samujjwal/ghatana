/**
 * Gateway metrics — simple in-process counters for observability.
 *
 * Tracks:
 *  - HTTP proxy requests by status bucket
 *  - Auth failures by reason
 *  - Tenant mismatch events
 *  - SSE accepted/rejected counts
 *  - WebSocket accepted/rejected/closed counts
 *  - Backend unreachable failures (HTTP, SSE, WS)
 *  - Backend latency distribution (count, sum, buckets)
 *
 * Exposed as a plain `GatewayMetrics` instance so it can be injected into
 * `buildApp` and accessed in tests without module-level singletons.
 */

/** HTTP latency histogram buckets in milliseconds */
const LATENCY_BUCKETS_MS = [10, 25, 50, 100, 250, 500, 1000, 2500, 5000] as const;

export interface LatencyHistogram {
  readonly count: number;
  readonly sumMs: number;
  readonly buckets: Record<string, number>; // bucket label → cumulative count
}

export interface GatewayMetricsSnapshot {
  httpProxyRequestsByStatus: Record<string, number>;
  authFailuresByReason: Record<string, number>;
  tenantMismatchTotal: number;
  sseAcceptedTotal: number;
  sseRejectedTotal: number;
  wsAcceptedTotal: number;
  wsRejectedTotal: number;
  wsClosedTotal: number;
  backendUnreachableTotal: number;
  backendLatencyMs: LatencyHistogram;
}

export class GatewayMetrics {
  private readonly _httpProxyRequestsByStatus = new Map<string, number>();
  private readonly _authFailuresByReason = new Map<string, number>();
  private _tenantMismatchTotal = 0;
  private _sseAcceptedTotal = 0;
  private _sseRejectedTotal = 0;
  private _wsAcceptedTotal = 0;
  private _wsRejectedTotal = 0;
  private _wsClosedTotal = 0;
  private _backendUnreachableTotal = 0;
  private _latencyCount = 0;
  private _latencySumMs = 0;
  private readonly _latencyBuckets = new Map<string, number>(
    LATENCY_BUCKETS_MS.map((b) => [`le_${b}`, 0] as [string, number]),
  );

  /** Record an HTTP proxy response status code (2xx, 4xx, 5xx, etc.). */
  recordHttpProxyRequest(status: number): void {
    const key = String(status);
    this._httpProxyRequestsByStatus.set(key, (this._httpProxyRequestsByStatus.get(key) ?? 0) + 1);
  }

  /**
   * Record an authentication failure.
   * @param reason A short machine-readable label, e.g. 'missing_token', 'invalid_token', 'expired_token'.
   */
  recordAuthFailure(reason: string): void {
    this._authFailuresByReason.set(reason, (this._authFailuresByReason.get(reason) ?? 0) + 1);
  }

  /** Record a tenant mismatch between header and JWT payload. */
  recordTenantMismatch(): void {
    this._tenantMismatchTotal += 1;
  }

  /** Record an accepted SSE connection (2xx forwarded to client). */
  recordSseAccepted(): void {
    this._sseAcceptedTotal += 1;
  }

  /** Record a rejected SSE connection (401/403/502 returned before stream open). */
  recordSseRejected(): void {
    this._sseRejectedTotal += 1;
  }

  /** Record an accepted WebSocket connection (auth passed, backend WS opened). */
  recordWsAccepted(): void {
    this._wsAcceptedTotal += 1;
  }

  /** Record a rejected WebSocket connection (4001/4003 close codes). */
  recordWsRejected(): void {
    this._wsRejectedTotal += 1;
  }

  /** Record a WebSocket session closed (either side). */
  recordWsClosed(): void {
    this._wsClosedTotal += 1;
  }

  /** Record a backend-unreachable failure (any transport). */
  recordBackendUnreachable(): void {
    this._backendUnreachableTotal += 1;
  }

  /**
   * Record a backend proxy round-trip latency.
   * Updates count, sum, and all histogram bucket counters.
   */
  recordBackendLatency(durationMs: number): void {
    this._latencyCount += 1;
    this._latencySumMs += durationMs;
    for (const bucket of LATENCY_BUCKETS_MS) {
      if (durationMs <= bucket) {
        const key = `le_${bucket}`;
        this._latencyBuckets.set(key, (this._latencyBuckets.get(key) ?? 0) + 1);
      }
    }
  }

  /** Return a plain-object snapshot of all current counters. */
  snapshot(): GatewayMetricsSnapshot {
    return {
      httpProxyRequestsByStatus: Object.fromEntries(this._httpProxyRequestsByStatus),
      authFailuresByReason: Object.fromEntries(this._authFailuresByReason),
      tenantMismatchTotal: this._tenantMismatchTotal,
      sseAcceptedTotal: this._sseAcceptedTotal,
      sseRejectedTotal: this._sseRejectedTotal,
      wsAcceptedTotal: this._wsAcceptedTotal,
      wsRejectedTotal: this._wsRejectedTotal,
      wsClosedTotal: this._wsClosedTotal,
      backendUnreachableTotal: this._backendUnreachableTotal,
      backendLatencyMs: {
        count: this._latencyCount,
        sumMs: this._latencySumMs,
        buckets: Object.fromEntries(this._latencyBuckets),
      },
    };
  }

  /** Reset all counters. Useful in tests. */
  reset(): void {
    this._httpProxyRequestsByStatus.clear();
    this._authFailuresByReason.clear();
    this._tenantMismatchTotal = 0;
    this._sseAcceptedTotal = 0;
    this._sseRejectedTotal = 0;
    this._wsAcceptedTotal = 0;
    this._wsRejectedTotal = 0;
    this._wsClosedTotal = 0;
    this._backendUnreachableTotal = 0;
    this._latencyCount = 0;
    this._latencySumMs = 0;
    for (const key of this._latencyBuckets.keys()) {
      this._latencyBuckets.set(key, 0);
    }
  }
}
