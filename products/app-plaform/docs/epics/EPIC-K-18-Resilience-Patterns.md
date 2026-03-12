EPIC-ID: EPIC-K-18
EPIC NAME: Resilience Patterns Library
LAYER: KERNEL
MODULE: K-18 Resilience Patterns Library
VERSION: 1.0.1
ARB-REF: P0-02

---

#### Section 1 — Objective

Deliver the K-18 Resilience Patterns Library, providing a standardized SDK for circuit breakers, bulkheads, retry policies, and timeout management across all platform modules. This epic directly remediates ARB finding P0-02 (No Circuit Breaker for K-03 Rules Engine Failures) and ensures that all inter-module dependencies have well-defined failure boundaries and degraded operation modes.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Circuit Breaker pattern with configurable thresholds, half-open state, and health probes.
  2. Bulkhead pattern for resource isolation between service calls.
  3. Retry policies with exponential backoff, jitter, and max attempts.
  4. Timeout management with per-call and aggregate budgets.
  5. Degraded mode framework with cached fallback support.
  6. SDK integration for all kernel and domain modules.
- **Out-of-Scope:**
  1. Infrastructure-level resilience (Kubernetes pod restarts, node failover) — handled by K-10 Deployment Abstraction.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-06 (Observability), EPIC-K-07 (Audit Framework)
- **Kernel Readiness Gates:** N/A (Kernel Epic)
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Circuit Breaker:** Provide a configurable circuit breaker with three states: CLOSED (normal), OPEN (failing fast), HALF_OPEN (testing recovery). Configuration: failure threshold (default: 3 consecutive or 50% in 30s window), recovery timeout (default: 30s), health probe interval (default: 5s). State transitions emit events to K-06.
2. **FR2 Bulkhead Isolation:** Provide thread/connection pool isolation per dependency. Each outbound dependency gets a dedicated resource pool with configurable max concurrency (default: 50). Overflow requests are rejected immediately with `BulkheadFullException`.
3. **FR3 Retry Policies:** Provide configurable retry with: max attempts (default: 3), backoff strategy (exponential with jitter), retryable exception list, and circuit breaker integration (no retry if circuit is OPEN).
4. **FR4 Timeout Management:** Enforce per-call timeouts (configurable per dependency) and aggregate timeout budgets per request. If aggregate budget exhausted, remaining calls are cancelled.
5. **FR5 Degraded Mode Framework:** When a circuit breaker opens, the SDK must: (a) invoke a registered fallback function if available, (b) tag the response with `degraded=true` metadata, (c) emit `DegradedModeActivatedEvent`, (d) log the fallback decision to K-07 for post-recovery audit.
6. **FR6 Centralized Configuration:** All resilience parameters are configurable per dependency and per module via K-02 Config Engine. Hot-reloadable without service restart.
7. **FR7 Pre-Defined Profiles:** Provide pre-defined resilience profiles for common patterns: (a) `CRITICAL_PATH` (fail-fast, no retry, strict timeout), (b) `BEST_EFFORT` (retry with backoff, longer timeout), (c) `COMPLIANCE_SENSITIVE` (fail-closed, no fallback, audit all failures).
8. **FR8 Dependency Health Dashboard:** Expose a health status API returning the circuit breaker state of all registered dependencies per module, enabling the Admin Portal to display a real-time dependency health map.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** All resilience patterns are jurisdiction-agnostic.
2. **Jurisdiction Plugin:** N/A.
3. **Resolution Flow:** N/A.
4. **Hot Reload:** All thresholds and timeouts hot-reloadable.
5. **Backward Compatibility:** N/A.
6. **Future Jurisdiction:** No changes needed.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `CircuitBreakerState`: `{ dependency_id: String, module_id: String, state: Enum(CLOSED, OPEN, HALF_OPEN), failure_count: Int, last_failure_at: Timestamp, last_success_at: Timestamp }`
  - `ResilienceConfig`: `{ dependency_id: String, profile: String, failure_threshold: Int, recovery_timeout_ms: Int, retry_max: Int, timeout_ms: Int }`
- **Dual-Calendar Fields:** N/A (infrastructure timestamps).
- **Event Schema Changes:** `CircuitBreakerStateChangedEvent`, `DegradedModeActivatedEvent`, `DegradedModeDeactivatedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                            |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `CircuitBreakerStateChangedEvent`                                                                                                      |
| Schema Version    | `v1.0.0`                                                                                                                               |
| Trigger Condition | A circuit breaker transitions between CLOSED, OPEN, or HALF_OPEN states.                                                               |
| Payload           | `{ "dependency_id": "K-03", "module_id": "D-01", "from_state": "CLOSED", "to_state": "OPEN", "failure_count": 3, "timestamp": "..." }` |
| Consumers         | K-06 Observability, Admin Portal, K-07 Audit                                                                                           |
| Idempotency Key   | `hash(dependency_id + module_id + to_state + timestamp)`                                                                               |
| Replay Behavior   | Updates the materialized view of dependency health.                                                                                    |
| Retention Policy  | 90 days (operational data).                                                                                                            |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                                  |
| ---------------- | ---------------------------------------------------------------------------- |
| Command Name     | `ForceCircuitBreakerCommand`                                                 |
| Schema Version   | `v1.0.0`                                                                     |
| Validation Rules | Dependency exists, requester authorized (admin/SRE role), target state valid |
| Handler          | `ResilienceCommandHandler` in K-18                                           |
| Success Event    | `CircuitBreakerForcedEvent`                                                  |
| Failure Event    | `CircuitBreakerForceFailed`                                                  |
| Idempotency      | Command ID must be unique                                                    |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Circuit breaker state monitoring.
- **Model Registry Usage:** `dependency-health-predictor-v1`
- **Explainability Requirement:** AI predicts upcoming dependency failures based on latency trends and preemptively adjusts thresholds.
- **Human Override Path:** SRE can manually open/close circuit breakers.
- **Drift Monitoring:** N/A.
- **Fallback Behavior:** Standard threshold-based circuit breaker.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                                    |
| ------------------------- | --------------------------------------------------------------------------------------------------- |
| Latency / Throughput      | SDK overhead < 0.1ms per call; zero allocation in hot path                                          |
| Scalability               | In-process library; no external dependencies                                                        |
| Availability              | 99.999% (in-process, no network calls)                                                              |
| Consistency Model         | Eventual consistency for circuit breaker state across instances (shared state optional)             |
| Security                  | N/A (library)                                                                                       |
| Data Residency            | N/A                                                                                                 |
| Data Retention            | Circuit breaker metrics retained per K-06 retention policy                                          |
| Auditability              | Degraded mode activations logged to K-07                                                            |
| Observability             | Metrics: `circuit_breaker.state`, `circuit_breaker.rejection.count`, `retry.count`, `timeout.count` |
| Extensibility             | Custom resilience patterns via plugin interface                                                     |
| Upgrade / Compatibility   | Backward compatible SDK API                                                                         |
| On-Prem Constraints       | In-process library; no external dependencies                                                        |
| Ledger Integrity          | N/A                                                                                                 |
| Dual-Calendar Correctness | N/A                                                                                                 |

---

#### Section 10 — Acceptance Criteria

1. **Given** K-03 Rules Engine returns 3 consecutive errors, **When** the circuit breaker opens, **Then** subsequent calls from D-01 OMS fail fast (< 1ms) and the fallback cached evaluation is returned with `degraded=true`.
2. **Given** the circuit breaker is OPEN for 30 seconds, **When** the half-open probe succeeds, **Then** the circuit transitions to CLOSED and normal traffic resumes.
3. **Given** a bulkhead pool of 50 for K-03, **When** 51 concurrent requests arrive, **Then** the 51st request is immediately rejected without waiting.
4. **Given** degraded mode activates for compliance evaluation, **When** the dependency recovers, **Then** all degraded decisions are flagged and queued for re-evaluation.
5. **Given** an SRE force-opens a circuit breaker via Admin Portal, **Then** the action is audited in K-07 and the circuit remains OPEN until manually closed.

---

#### Section 11 — Failure Modes & Resilience

- **SDK Initialization Failure:** Module starts with default resilience profiles; alert raised.
- **Config Refresh Failure:** Last-known-good configuration retained; alert raised.
- **Cascading Failure Prevention:** Bulkheads prevent failure in one dependency from exhausting resources for other dependencies.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Metrics             | `circuit_breaker.state` (gauge), `circuit_breaker.trips` (counter), `retry.attempts` (histogram), `timeout.breaches` (counter) |
| Logs                | Structured: `dependency_id`, `state_change`, `fallback_used`                                                                   |
| Traces              | Span annotations for retry attempts and circuit breaker state                                                                  |
| Audit Events        | `DegradedModeActivated`, `DegradedModeDeactivated`, `CircuitBreakerForced`                                                     |
| Regulatory Evidence | Degraded operation records for compliance audit                                                                                |

---

#### Section 13 — Compliance & Regulatory Traceability

- Operational resilience evidence [LCA-OPS-001]
- Degraded mode audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `ResilienceClient.withCircuitBreaker(dependencyId, call)`, `ResilienceClient.withRetry(policy, call)`, `ResilienceClient.withBulkhead(poolId, call)`, `ResilienceClient.getHealthStatus()`.
- **Jurisdiction Plugin Extension Points:** N/A.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                     |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, infrastructure is jurisdiction-agnostic.                                                       |
| Can new resilience patterns be added?                                 | Yes, via SDK extension interface.                                                                   |
| Can this run in an air-gapped deployment?                             | Yes, in-process library with no external dependencies.                                              |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Circuit breakers and retry policies protect blockchain RPC calls and token transfer endpoints. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Bulkhead isolation and adaptive timeouts prevent T+0 settlement failures from cascading.       |

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
