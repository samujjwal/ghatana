# Learning Status State Machine

> **Module**: `products/data-cloud/launcher` — `DataCloudLearningBridge` (DC-7)  
> **Routes**: DC-8 `LearningRoutes` / `LearningHandler`  
> **Last Updated**: 2026-04-28

---

## Overview

The Data Cloud Learning subsystem drives the `DataCloudBrain.learn()` cycle — discovering
patterns in tenant data, computing confidence scores, and queuing low-confidence patterns for
human review. The subsystem exposes two orthogonal state machines:

| Machine | States |
|---------|--------|
| **Cycle** | `NOT_RUN` → `SKIPPED \| COMPLETED \| FAILED` |
| **Review Item** | `PENDING` → `APPROVED \| REJECTED` |

---

## 1. Cycle State Machine

The **cycle** tracks the outcome of each `DataCloudBrain.learn()` invocation.

### States

| State | Meaning | Terminal? |
|-------|---------|-----------|
| `NOT_RUN` | Bridge has started but no learning cycle has executed yet | No |
| `SKIPPED` | Cycle was triggered while another cycle was already running; current request was a no-op | Yes (for that attempt) |
| `COMPLETED` | Brain learning finished successfully; patterns discovered and updated | Yes |
| `FAILED` | An exception was thrown during the learning cycle | Yes |

### Transitions

```
                      ┌──────────┐
          (initial)   │  NOT_RUN │
                      └────┬─────┘
                           │ trigger (manual or scheduled)
                           ▼
                    ┌─────────────┐
                    │  (running)  │  ← AtomicBoolean learning = true
                    └──┬──────┬───┘
          already      │      │
          running ──► SKIPPED │
                               │
                  ┌────────────┴──────────────┐
                  │                           │
               success                    exception
                  │                           │
                  ▼                           ▼
            COMPLETED                      FAILED
```

> **Concurrency guard**: `AtomicBoolean#compareAndSet(false, true)` prevents concurrent cycles.
> If a second trigger arrives while a cycle is running, it immediately returns `SKIPPED` without
> blocking or queuing.

### Fields present in each state

| Field | NOT_RUN | SKIPPED | COMPLETED | FAILED |
|-------|---------|---------|-----------|--------|
| `status` | ✓ | ✓ | ✓ | ✓ |
| `reason` | — | ✓ | — | — |
| `tenantId` | — | — | ✓ | ✓ |
| `manual` | — | — | ✓ | ✓ |
| `durationMs` | — | — | ✓ | — |
| `patternsDiscovered` | — | — | ✓ | — |
| `patternsUpdated` | — | — | ✓ | — |
| `recordsAnalyzed` | — | — | ✓ | — |
| `ranAt` | — | ✓ | ✓ | ✓ |
| `error` | — | — | — | ✓ |

---

## 2. Review-Item State Machine

Patterns with `confidence < 0.7` discovered during a `COMPLETED` cycle are added to the
in-memory **review queue** for human decision. Each queue item has its own status.

### States

| State | Meaning | Terminal? |
|-------|---------|-----------|
| `PENDING` | Item is awaiting a human decision | No |
| `APPROVED` | Reviewer accepted the pattern | Yes |
| `REJECTED` | Reviewer rejected the pattern | Yes |

### Transitions

```
            (pattern confidence < 0.7)
                        │
                        ▼
                    PENDING
                   /       \
                  /         \
          APPROVED         REJECTED
```

> Items are capped at `MAX_REVIEW_QUEUE_SIZE = 1000` to bound memory usage.  
> `putIfAbsent` ensures idempotent re-insertion across cycles (same `reviewId`).

---

## 3. Bridge Status Snapshot (`GET /api/v1/learning/status`)

`DataCloudLearningBridge.getStatus()` assembles a point-in-time snapshot.  
It is called by `LearningHandler.handleLearningStatus()` and returned as JSON.

### Response shape

```json
{
  "running": false,
  "lastRunTime": "2026-04-28T10:15:00Z",
  "nextScheduledRun": "2026-04-28T10:20:00Z",
  "intervalMinutes": 5,
  "pendingReviews": 3,
  "lastResult": {
    "status": "COMPLETED",
    "tenantId": "tenant-abc",
    "manual": false,
    "durationMs": 412,
    "patternsDiscovered": 7,
    "patternsUpdated": 2,
    "recordsAnalyzed": 1500,
    "ranAt": "2026-04-28T10:15:00Z"
  }
}
```

### Field semantics

| Field | Type | Notes |
|-------|------|-------|
| `running` | boolean | `true` while a cycle is executing |
| `lastRunTime` | ISO-8601 string or `"never"` | Start time of the last cycle attempt |
| `nextScheduledRun` | ISO-8601 string or `"not started"` | Wall-clock time of the next automatic trigger |
| `intervalMinutes` | long | Scheduling period; currently `5` |
| `pendingReviews` | long | Count of `PENDING` items in the review queue |
| `lastResult` | object | The result map from the most recent cycle; mirrors one of the `COMPLETED`/`FAILED`/`SKIPPED`/`NOT_RUN` shapes above |

### Example — bridge just started, no cycle run

```json
{
  "running": false,
  "lastRunTime": "never",
  "nextScheduledRun": "2026-04-28T11:05:00Z",
  "intervalMinutes": 5,
  "pendingReviews": 0,
  "lastResult": {
    "status": "NOT_RUN"
  }
}
```

### Example — cycle in progress

```json
{
  "running": true,
  "lastRunTime": "2026-04-28T10:10:00Z",
  "nextScheduledRun": "2026-04-28T10:15:00Z",
  "intervalMinutes": 5,
  "pendingReviews": 1,
  "lastResult": {
    "status": "COMPLETED",
    "tenantId": "tenant-abc",
    "manual": true,
    "durationMs": 330,
    "patternsDiscovered": 3,
    "patternsUpdated": 1,
    "recordsAnalyzed": 800,
    "ranAt": "2026-04-28T10:10:00Z"
  }
}
```

### Example — last cycle failed

```json
{
  "running": false,
  "lastRunTime": "2026-04-28T10:05:00Z",
  "nextScheduledRun": "2026-04-28T10:10:00Z",
  "intervalMinutes": 5,
  "pendingReviews": 0,
  "lastResult": {
    "status": "FAILED",
    "tenantId": "tenant-xyz",
    "manual": false,
    "error": "Connection reset by peer",
    "ranAt": "2026-04-28T10:05:00Z"
  }
}
```

---

## 4. Review Queue (`GET /api/v1/learning/review`)

Returns all items currently in the queue regardless of status.

### Response shape

```json
{
  "items": [
    {
      "reviewId":    "review-pattern-7a2f",
      "patternId":   "pattern-7a2f",
      "patternName": "high-volume-writes",
      "confidence":  0.54,
      "status":      "PENDING",
      "discoveredAt": "2026-04-28T10:15:00Z"
    }
  ],
  "total": 1
}
```

### Submitting a decision (`POST /api/v1/learning/review/{reviewId}`)

Body:
```json
{ "decision": "APPROVED" }
```

Valid `decision` values: `APPROVED`, `REJECTED`.

After decision the item transitions out of `PENDING` and `completed` is `true`. Items with
`APPROVED` or `REJECTED` status are excluded from the `pendingReviews` counter in the status
snapshot.

---

## 5. Manual Trigger (`POST /api/v1/learning/trigger`)

Immediately starts a learning cycle on a virtual-thread executor (never blocks the ActiveJ
event loop). Returns the cycle result map synchronously.

| Scenario | Response `status` |
|----------|------------------|
| Cycle ran successfully | `COMPLETED` |
| Another cycle was in progress | `SKIPPED` |
| Exception during brain.learn() | `FAILED` |

---

## 6. Scheduling

- Automatic interval: every **5 minutes** (see `INTERVAL_MINUTES`).
- The scheduler daemon thread is named `dc-learning-bridge`.
- Lifecycle: started by `DataCloudLauncher` via `bridge.start()`; cleaned up via `bridge.close()`.
- `close()` issues `scheduler.shutdownNow()` to stop the background thread cleanly.

---

## 7. Observability

| Signal | Detail |
|--------|--------|
| Log `INFO` | Cycle start and completion (with tenant, discovered, updated, duration) |
| Log `DEBUG` | Skipped cycles |
| Log `ERROR` | Failed cycles with stack trace |
| Structured fields | `tenantId`, `manual`, `durationMs`, `patternsDiscovered`, `patternsUpdated` |

Correlate with the tenant-id header (`X-Tenant-ID`) forwarded by the `TenantContextFilter`.
