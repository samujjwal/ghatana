# LOW-LEVEL DESIGN: K-18 RESILIENCE PATTERNS LIBRARY

**Module**: K-18 Resilience Patterns Library  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

K-18 provides a **shared, composable library of resilience patterns** — circuit breakers, bulkheads, retry policies, and timeout managers — ensuring every service enforces consistent fault isolation boundaries without duplicating logic.

**Core Responsibilities**:
- Circuit breakers with half-open probing and sliding-window metrics
- Bulkhead isolation (thread-pool and semaphore modes)
- Retry with configurable backoff (exponential, linear, constant) plus jitter
- Timeout management with hierarchical cascading
- Pre-defined resilience profiles (CRITICAL_PATH, BEST_EFFORT, COMPLIANCE_SENSITIVE)
- Decorator / annotation-based integration for minimal code changes
- Dual-calendar timestamps on all resilience events

**Invariants**:
1. Circuit-breaker state transitions MUST emit K-05 events
2. Bulkhead rejection MUST increment a counter metric
3. Retry attempts MUST NOT exceed the configured max
4. All resilience events MUST carry `tenant_id` and `trace_id`
5. Profiles are immutable at runtime — changes require config reload via K-02
6. Timeout values MUST cascade: inner timeout < outer timeout

### 1.2 Explicit Non-Goals

- ❌ Network-level resilience (load balancing, DNS failover) — infra layer
- ❌ Service mesh sidecar — K-18 is an in-process library
- ❌ Rate limiting — handled by K-11 API Gateway
- ❌ DLQ management — handled by K-19

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Configuration Engine | Profile definitions and dynamic tuning | K-02 stable |
| K-05 Event Bus | Resilience event publication | K-05 stable |
| K-06 Observability | Metrics and tracing | K-06 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 SDK Method Signatures (TypeScript)

```typescript
import { ResilienceBuilder, CircuitBreakerState } from '@siddhanta/resilience';

// Builder API
const resilientCall = ResilienceBuilder.create('order-placement')
  .withCircuitBreaker({ failureThreshold: 5, resetTimeoutMs: 30000, halfOpenMaxCalls: 3 })
  .withBulkhead({ mode: 'SEMAPHORE', maxConcurrent: 50, maxWaitMs: 200 })
  .withRetry({ maxAttempts: 3, backoffMs: 100, strategy: 'EXPONENTIAL', jitter: true })
  .withTimeout({ durationMs: 5000 })
  .build();

// Execution
const result = await resilientCall.execute(() => orderService.placeOrder(order));

// Decorator API (annotations)
class OrderService {
  @Resilient('CRITICAL_PATH')
  async placeOrder(order: Order): Promise<OrderResult> { ... }
}
```

### 2.2 SDK Method Signatures (Java)

```java
// Builder API
ResiliencePolicy policy = ResilienceBuilder.create("order-placement")
    .circuitBreaker(cb -> cb
        .failureThreshold(5)
        .resetTimeoutMs(30_000)
        .halfOpenMaxCalls(3))
    .bulkhead(bh -> bh
        .mode(BulkheadMode.THREAD_POOL)
        .maxConcurrent(50)
        .maxWaitMs(200))
    .retry(r -> r
        .maxAttempts(3)
        .backoffMs(100)
        .strategy(RetryStrategy.EXPONENTIAL)
        .jitter(true))
    .timeout(Duration.ofMillis(5000))
    .build();

CompletableFuture<OrderResult> result = policy.execute(
    () -> orderService.placeOrder(order));
```

### 2.3 Pre-Defined Profiles

| Profile | Circuit Breaker | Retry | Timeout | Bulkhead | Use Cases |
|---------|----------------|-------|---------|----------|-----------|
| CRITICAL_PATH | 5 failures / 30s reset | 1 retry / 50ms backoff | 2000ms | 100 concurrent | Order placement, trade execution |
| BEST_EFFORT | 10 failures / 60s reset | 3 retries / 500ms backoff | 10000ms | 20 concurrent | Report generation, notifications |
| COMPLIANCE_SENSITIVE | 3 failures / 120s reset | 2 retries / 200ms backoff | 5000ms | 50 concurrent | Sanctions screening, KYC checks |
| REAL_TIME_DATA | 3 failures / 10s reset | 0 retries | 500ms | 200 concurrent | Market data, price feeds |
| SETTLEMENT | 5 failures / 60s reset | 5 retries / 1000ms backoff | 30000ms | 30 concurrent | End-of-day settlement, reconciliation |

### 2.4 REST API (Management)

```yaml
GET /api/v1/resilience/circuit-breakers
Authorization: Bearer {admin_token}

Response 200:
{
  "circuit_breakers": [
    {
      "name": "order-placement",
      "state": "CLOSED",
      "failure_count": 2,
      "success_count": 1450,
      "last_failure_at": "2025-03-02T10:28:00Z",
      "last_failure_at_bs": "2081-11-18 10:28:00"
    }
  ]
}
```

```yaml
POST /api/v1/resilience/circuit-breakers/{name}/reset
Authorization: Bearer {admin_token}

Response 200:
{
  "name": "order-placement",
  "state": "CLOSED",
  "message": "Circuit breaker manually reset"
}
```

### 2.5 Error Model

| Error Code | Retryable | Description |
|------------|-----------|-------------|
| RES_E001 | No (wait) | Circuit breaker OPEN — request rejected |
| RES_E002 | Yes | Retry exhausted — all attempts failed |
| RES_E003 | No (wait) | Bulkhead full — request rejected |
| RES_E004 | No | Timeout exceeded |
| RES_E005 | No | Unknown resilience profile |

---

## 3. DATA MODEL

### 3.1 Circuit Breaker State Machine

```
     succeed / threshold OK
  ┌─────────────────────────┐
  │                         ▼
CLOSED ──(failure threshold)──► OPEN ──(reset timeout)──► HALF_OPEN
  ▲                                                         │
  │                                ┌────────────────────────┘
  │                                │ success threshold met
  └────────────────────────────────┘
                                   │ any failure
                                   └──► OPEN
```

### 3.2 In-Memory Structures

```typescript
interface CircuitBreakerState {
  name: string;
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  failureCount: number;
  successCount: number;
  halfOpenCallCount: number;
  slidingWindow: SlidingWindowMetrics;
  lastStateChange: Date;
  lastFailure?: Date;
  config: CircuitBreakerConfig;
}

interface SlidingWindowMetrics {
  windowType: 'COUNT_BASED' | 'TIME_BASED';
  windowSize: number;          // count or seconds
  failures: number[];          // ring buffer of timestamps
  successes: number[];
  slowCalls: number[];         // calls > slow threshold
}

interface BulkheadState {
  name: string;
  mode: 'THREAD_POOL' | 'SEMAPHORE';
  maxConcurrent: number;
  currentConcurrent: number;
  waitingCount: number;
  rejectedCount: number;
}
```

### 3.3 Configuration Store (K-02)

```json
{
  "config_path": "kernel.resilience.profiles",
  "content_pack_type": "T1",
  "profiles": {
    "CRITICAL_PATH": {
      "circuit_breaker": {
        "failure_rate_threshold": 50,
        "slow_call_rate_threshold": 80,
        "slow_call_duration_ms": 2000,
        "sliding_window_type": "COUNT_BASED",
        "sliding_window_size": 100,
        "minimum_number_of_calls": 10,
        "wait_duration_in_open_state_ms": 30000,
        "permitted_number_of_calls_in_half_open": 3
      },
      "retry": {
        "max_attempts": 1,
        "initial_backoff_ms": 50,
        "strategy": "EXPONENTIAL",
        "jitter_factor": 0.1,
        "retryable_exceptions": ["TimeoutException", "TransientException"]
      },
      "timeout": {
        "duration_ms": 2000
      },
      "bulkhead": {
        "mode": "SEMAPHORE",
        "max_concurrent": 100,
        "max_wait_ms": 200
      }
    }
  }
}
```

---

## 4. CONTROL FLOW

### 4.1 Execution Pipeline

```
Request → Timeout Guard
  → Bulkhead Gate
    → Retry Loop
      → Circuit Breaker Check
        → IF CLOSED or HALF_OPEN: Execute target function
          → IF success: Record success, return result
          → IF failure: Record failure, check threshold
            → IF threshold crossed: OPEN circuit, emit event
        → IF OPEN: Reject immediately (RES_E001)
      → IF failure AND retries remaining: backoff + retry
      → IF failure AND retries exhausted: return RES_E002
    → IF bulkhead full: reject (RES_E003)
  → IF timeout exceeded: cancel, return RES_E004
```

### 4.2 Half-Open Probing

```
OPEN state active for >= resetTimeout:
  → Transition to HALF_OPEN
  → Allow up to halfOpenMaxCalls
  → IF all succeed: Transition to CLOSED
  → IF any fail: Transition back to OPEN
  → Emit K-05 event: CircuitBreakerStateChanged
```

### 4.3 Cascading Timeout Example

```
API Gateway timeout: 12000ms (≤12ms e2e target for P99)
  └── Service timeout: 5000ms
        └── DB query timeout: 2000ms
        └── External API timeout: 3000ms

Inner timeouts MUST be strictly less than outer timeouts.
K-18 validates this at build time and logs warnings.
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Exponential Backoff with Jitter

```python
def calculate_backoff(attempt: int, initial_ms: float, jitter_factor: float = 0.1) -> float:
    """Exponential backoff with decorrelated jitter."""
    base = initial_ms * (2 ** attempt)
    jitter = base * jitter_factor * random.uniform(-1, 1)
    return min(base + jitter, 60_000)  # cap at 60s
```

### 5.2 Sliding Window Failure Rate

```python
def check_failure_rate(window: SlidingWindow) -> bool:
    """Return True if failure rate exceeds threshold."""
    total = len(window.failures) + len(window.successes)
    if total < window.minimum_calls:
        return False
    rate = len(window.failures) / total * 100
    return rate >= window.failure_rate_threshold
```

### 5.3 Thread Pool Bulkhead

```java
public class ThreadPoolBulkhead implements Bulkhead {
    private final ExecutorService executor;
    private final Semaphore waitSemaphore;
    
    public <T> CompletableFuture<T> execute(Supplier<T> action) {
        if (!waitSemaphore.tryAcquire(maxWaitMs, TimeUnit.MILLISECONDS)) {
            metrics.incrementRejected();
            throw new BulkheadFullException("RES_E003");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } finally {
                waitSemaphore.release();
            }
        }, executor);
    }
}
```

---

## 6. NFR BUDGETS

### 6.1 Latency Overhead

| Pattern | Overhead (P99) | Max Overhead |
|---------|----------------|--------------|
| Circuit breaker check | 0.01ms | 0.05ms |
| Bulkhead acquire | 0.01ms | 0.05ms |
| Retry (per attempt) | 0ms (just delay) | — |
| Timeout wrapper | 0.01ms | 0.05ms |
| Full pipeline overhead | 0.05ms | 0.2ms |

### 6.2 Memory Budget

| Component | Per Instance | Typical Count | Total |
|-----------|-------------|---------------|-------|
| Circuit breaker | 8KB (sliding window) | 50 | 400KB |
| Bulkhead (semaphore) | 256 bytes | 50 | 12.5KB |
| Bulkhead (thread pool) | 2KB + threads | 10 | 20KB + threads |
| Total per service | — | — | < 1MB |

---

## 7. SECURITY DESIGN

### 7.1 Admin API Access

- Circuit breaker reset: `resilience:admin:reset` permission
- Metrics view: `resilience:metrics:view` permission
- Profile modification: via K-02 config engine ONLY (maker-checker)

### 7.2 DoS Protection

- Bulkhead prevents cascade failures
- Circuit breaker prevents thundering herd on recovery
- Thread pool bulkhead isolates slow endpoints from fast ones

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```
siddhanta_resilience_cb_state{name, state}                          gauge
siddhanta_resilience_cb_calls_total{name, result}                   counter
siddhanta_resilience_cb_failure_rate{name}                          gauge
siddhanta_resilience_cb_state_transitions_total{name, from, to}     counter
siddhanta_resilience_bulkhead_concurrent{name}                      gauge
siddhanta_resilience_bulkhead_rejected_total{name}                  counter
siddhanta_resilience_bulkhead_wait_ms{name}                         histogram
siddhanta_resilience_retry_total{name, attempt}                     counter
siddhanta_resilience_retry_exhausted_total{name}                    counter
siddhanta_resilience_timeout_total{name}                            counter
```

### 8.2 K-05 Events

```json
{
  "event_type": "siddhanta.resilience.circuit_breaker.state_changed",
  "event_version": "1.0",
  "aggregate_type": "CircuitBreaker",
  "aggregate_id": "order-placement",
  "timestamp_bs": "2081-11-18 10:30:00",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "metadata": {
    "trace_id": "abc123",
    "tenant_id": "tenant_np_1"
  },
  "data": {
    "previous_state": "CLOSED",
    "new_state": "OPEN",
    "failure_rate": 65.2,
    "trigger": "failure_rate_threshold",
    "window_snapshot": { "successes": 35, "failures": 65 }
  }
}
```

### 8.3 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| Circuit breaker OPEN | Any CB transitions to OPEN | P2 |
| Sustained OPEN | CB in OPEN > 5 minutes | P1 |
| Bulkhead saturation | >80% concurrent capacity | P2 |
| Retry exhaustion rate | >10% calls exhaust retries in 5min | P1 |
| Timeout spike | >5% calls timing out in 5min | P2 |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Resilience Profiles (T1)

```json
{
  "content_pack_type": "T1",
  "jurisdiction": "NP",
  "name": "nepal-compliance-profile",
  "profiles": {
    "SEBON_REPORTING": {
      "circuit_breaker": { "failure_rate_threshold": 30, "wait_duration_in_open_state_ms": 120000 },
      "retry": { "max_attempts": 5, "initial_backoff_ms": 2000, "strategy": "LINEAR" },
      "timeout": { "duration_ms": 30000 },
      "bulkhead": { "mode": "SEMAPHORE", "max_concurrent": 10 }
    }
  }
}
```

### 9.2 Custom Failure Classifiers (T3)

```typescript
interface FailureClassifier {
  classify(error: Error): FailureType;
}

enum FailureType {
  TRANSIENT,    // retry-eligible
  PERMANENT,    // no retry, open circuit
  TIMEOUT,      // retry with increased timeout
  IGNORED       // not counted as failure
}
```

### 9.3 Future: Adaptive Resilience

- ML-driven circuit breaker thresholds based on traffic patterns
- Predictive bulkhead sizing based on load forecasting
- Coordinated circuit breaking across service mesh

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-RES-001 | CB opens at failure threshold | State = OPEN after N failures |
| UT-RES-002 | CB half-open probing | Transitions to HALF_OPEN after timeout |
| UT-RES-003 | CB closes on successful probing | State = CLOSED after N successes |
| UT-RES-004 | Retry with backoff | Attempts match config, delays correct |
| UT-RES-005 | Bulkhead rejects over limit | RES_E003 thrown |
| UT-RES-006 | Timeout cancellation | RES_E004 after duration |
| UT-RES-007 | Profile loading from K-02 | All profiles correctly loaded |
| UT-RES-008 | Cascading timeout validation | Warning on inner > outer |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-RES-001 | CB state change → K-05 event | Event published on transition |
| IT-RES-002 | Full pipeline: timeout + retry + CB | Correct execution order |
| IT-RES-003 | Profile hot-reload via K-02 | New config takes effect without restart |
| IT-RES-004 | Metrics emission to K-06 | All metrics visible in Prometheus |
| IT-RES-005 | Multi-service bulkhead isolation | Failure in service A does not affect B |

### 10.3 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-RES-001 | Downstream service 50% failure | CB opens, half-open probes, recovers |
| CT-RES-002 | Network partition (split-brain) | CB opens, zero requests to failed partition |
| CT-RES-003 | Sudden load spike (10x traffic) | Bulkhead rejects excess, core requests served |
| CT-RES-004 | Slow response (latency injection) | Timeout triggers, CB registers slow calls |

---

**END OF K-18 RESILIENCE PATTERNS LIBRARY LLD**
