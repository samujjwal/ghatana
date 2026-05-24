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
 *  - Action Plane pattern match counts (AEP-006)
 *  - Action Plane agent execution metrics (AEP-006)
 *  - Action Plane evidence write metrics (AEP-006)
 *  - Action Plane policy evaluation metrics (AEP-006)
 *  - Action Plane commit SHA validation metrics (AEP-006)
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
  kernelLifecycleRequestsByOperation: Record<string, number>;
  agenticActionsByStatus: Record<string, number>;
  authFailuresByReason: Record<string, number>;
  tenantMismatchTotal: number;
  sseAcceptedTotal: number;
  sseRejectedTotal: number;
  wsAcceptedTotal: number;
  wsRejectedTotal: number;
  wsClosedTotal: number;
  backendUnreachableTotal: number;
  backendLatencyMs: LatencyHistogram;
  // AEP-006: Action Plane metrics
  patternMatchesByType: Record<string, number>;
  agentExecutionsByStatus: Record<string, number>;
  evidenceWritesByStatus: Record<string, number>;
  policyEvaluationsByDecision: Record<string, number>;
  commitShaValidationsByResult: Record<string, number>;
}

export class GatewayMetrics {
  private readonly _httpProxyRequestsByStatus = new Map<string, number>();
  private readonly _kernelLifecycleRequestsByOperation = new Map<string, number>();
  private readonly _agenticActionsByStatus = new Map<string, number>();
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
  // AEP-006: Action Plane metrics
  private readonly _patternMatchesByType = new Map<string, number>();
  private readonly _agentExecutionsByStatus = new Map<string, number>();
  private readonly _evidenceWritesByStatus = new Map<string, number>();
  private readonly _policyEvaluationsByDecision = new Map<string, number>();
  private readonly _commitShaValidationsByResult = new Map<string, number>();

  /** Record an HTTP proxy response status code (2xx, 4xx, 5xx, etc.). */
  recordHttpProxyRequest(status: number): void {
    const key = String(status);
    this._httpProxyRequestsByStatus.set(key, (this._httpProxyRequestsByStatus.get(key) ?? 0) + 1);
  }

  /** Record an injected Kernel lifecycle API operation by operation and status code. */
  recordKernelLifecycleRequest(
    operation: string,
    status: number,
    providerMode?: string,
    reasonCode?: string,
  ): void {
    const key = `${operation}:${status}`;
    this._kernelLifecycleRequestsByOperation.set(
      key,
      (this._kernelLifecycleRequestsByOperation.get(key) ?? 0) + 1,
    );
    // Track providerMode and reasonCode separately for richer observability
    if (providerMode) {
      const providerModeKey = `${operation}:providerMode:${providerMode}`;
      this._kernelLifecycleRequestsByOperation.set(
        providerModeKey,
        (this._kernelLifecycleRequestsByOperation.get(providerModeKey) ?? 0) + 1,
      );
    }
    if (reasonCode) {
      const reasonCodeKey = `${operation}:reasonCode:${reasonCode}`;
      this._kernelLifecycleRequestsByOperation.set(
        reasonCodeKey,
        (this._kernelLifecycleRequestsByOperation.get(reasonCodeKey) ?? 0) + 1,
      );
    }
  }

  /** Record an agentic lifecycle action by status and optional duration. */
  recordAgenticAction(status: string, durationMs?: number): void {
    this._agenticActionsByStatus.set(status, (this._agenticActionsByStatus.get(status) ?? 0) + 1);
    if (durationMs !== undefined) {
      this.recordBackendLatency(durationMs);
    }
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
      kernelLifecycleRequestsByOperation: Object.fromEntries(this._kernelLifecycleRequestsByOperation),
      agenticActionsByStatus: Object.fromEntries(this._agenticActionsByStatus),
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
      // AEP-006: Action Plane metrics
      patternMatchesByType: Object.fromEntries(this._patternMatchesByType),
      agentExecutionsByStatus: Object.fromEntries(this._agentExecutionsByStatus),
      evidenceWritesByStatus: Object.fromEntries(this._evidenceWritesByStatus),
      policyEvaluationsByDecision: Object.fromEntries(this._policyEvaluationsByDecision),
      commitShaValidationsByResult: Object.fromEntries(this._commitShaValidationsByResult),
    };
  }

  /** Reset all counters. Useful in tests. */
  reset(): void {
    this._httpProxyRequestsByStatus.clear();
    this._kernelLifecycleRequestsByOperation.clear();
    this._agenticActionsByStatus.clear();
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
    // AEP-006: Reset Action Plane metrics
    this._patternMatchesByType.clear();
    this._agentExecutionsByStatus.clear();
    this._evidenceWritesByStatus.clear();
    this._policyEvaluationsByDecision.clear();
    this._commitShaValidationsByResult.clear();
  }

  // AEP-006: Action Plane metrics methods

  /** Record a pattern match by type. */
  recordPatternMatch(patternType: string): void {
    this._patternMatchesByType.set(patternType, (this._patternMatchesByType.get(patternType) ?? 0) + 1);
  }

  /** Record an agent execution by status. */
  recordAgentExecution(status: string): void {
    this._agentExecutionsByStatus.set(status, (this._agentExecutionsByStatus.get(status) ?? 0) + 1);
  }

  /** Record an evidence write by status. */
  recordEvidenceWrite(status: string): void {
    this._evidenceWritesByStatus.set(status, (this._evidenceWritesByStatus.get(status) ?? 0) + 1);
  }

  /** Record a policy evaluation by decision. */
  recordPolicyEvaluation(decision: string): void {
    this._policyEvaluationsByDecision.set(decision, (this._policyEvaluationsByDecision.get(decision) ?? 0) + 1);
  }

  /** Record a commit SHA validation by result. */
  recordCommitShaValidation(result: string): void {
    this._commitShaValidationsByResult.set(result, (this._commitShaValidationsByResult.get(result) ?? 0) + 1);
  }
}
