# Idempotency Inventory for Data Cloud API

## Purpose
This document inventories all mutating API routes in Data Cloud and specifies their idempotency semantics.

## Idempotency Semantics

### Idempotent Operations
- **POST with idempotency key**: Can be safely retried without side effects
- **PUT**: Naturally idempotent (replace entire resource)
- **DELETE**: Naturally idempotent (resource either exists or doesn't)

### Non-Idempotent Operations
- **POST without idempotency key**: Creates new resource on each call
- **PATCH**: Partial updates may not be idempotent
- **Operations with side effects**: Actions that trigger workflows, executions, or state transitions

## Mutation Route Inventory

### Collections
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/collections | POST | Yes (with key) | Optional | N/A | Creates collection - requires key for safe retry |
| /api/v1/collections/{id} | PUT | Yes (natural) | No | N/A | Replaces entire collection |
| /api/v1/collections/{id} | DELETE | Yes (natural) | No | N/A | Deletes collection |

### Entities
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/entities/{collection} | POST | Yes (with key) | Optional | N/A | Creates entity - requires key for safe retry |
| /api/v1/entities/{collection}/{id} | DELETE | Yes (natural) | No | N/A | Deletes entity |
| /api/v1/entities/{collection}/batch | POST | Yes (with key) | Optional | N/A | Batch create - requires key for safe retry |
| /api/v1/entities/{collection}/batch | DELETE | Yes (natural) | No | N/A | Batch delete |

### Events
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/events | POST | Yes (with key) | Optional | N/A | Appends event - requires key for safe retry |

### Pipelines
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/action/pipelines | POST | Yes (with key) | Optional | N/A | Creates pipeline - requires key for safe retry |
| /api/v1/action/pipelines/{id} | PUT | Yes (natural) | No | N/A | Replaces pipeline |
| /api/v1/action/pipelines/{id} | DELETE | Yes (natural) | No | N/A | Deletes pipeline |
| /api/v1/action/pipelines/{id}/execute | POST | **No** | N/A | Triggers workflow execution with side effects and external system calls | Execution creates new process state each call |
| /api/v1/action/pipelines/{id}/executions/{executionId}/cancel | POST | **No** | N/A | State transition from running to cancelled is irreversible | Cannot cancel already cancelled or completed execution |

### Checkpoints
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/checkpoints | POST | Yes (with key) | Optional | N/A | Creates checkpoint - requires key for safe retry |
| /api/v1/checkpoints/{id} | DELETE | Yes (natural) | No | N/A | Deletes checkpoint |

### Alerts
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/alerts/{id}/acknowledge | POST | **No** | N/A | State transition from active to acknowledged is irreversible | Acknowledging already acknowledged alert is no-op but not idempotent by design |
| /api/v1/alerts/{id}/resolve | POST | **No** | N/A | State transition from active/acknowledged to resolved is irreversible | Resolving already resolved alert is no-op but not idempotent by design |
| /api/v1/alerts/{id}/escalate | POST | **No** | N/A | Escalation changes alert severity and may trigger notifications | Repeated escalation may cause duplicate notifications |
| /api/v1/alerts/{id}/auto-remediate | POST | **No** | N/A | Triggers remediation workflow with external system side effects | Each call may execute remediation actions |
| /api/v1/alerts/{id}/remediate | POST | **No** | N/A | Triggers manual remediation workflow with external system side effects | Each call may execute remediation actions |
| /api/v1/alerts/{id}/remediate/rollback | POST | **No** | N/A | Rollback is a state transition that may reverse previous remediation | Cannot rollback already rolled-back remediation |
| /api/v1/alerts/groups/{groupId}/resolve | POST | **No** | N/A | Bulk state transition affecting multiple alerts is irreversible | Resolving already resolved group is no-op but not idempotent by design |
| /api/v1/alerts/suggestions/{suggestionId}/apply | POST | **No** | N/A | Applies suggestion which may trigger configuration changes | Applying same suggestion twice may cause duplicate changes |
| /api/v1/alerts/rules | POST | Yes (with key) | Optional | N/A | Creates rule - requires key for safe retry |
| /api/v1/alerts/rules/{id} | PUT | Yes (natural) | No | N/A | Replaces rule |
| /api/v1/alerts/rules/{id} | DELETE | Yes (natural) | No | N/A | Deletes rule |

### Memory
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/action/memory/{agentId} | POST | Yes (with key) | Optional | N/A | Stores memory - requires key for safe retry |
| /api/v1/action/memory/{agentId}/{memoryId} | DELETE | Yes (natural) | No | N/A | Deletes memory |
| /api/v1/action/memory/{agentId}/{memoryId}/retain | PUT | Yes (natural) | No | N/A | Updates retention |

### Media Artifacts
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/media/artifacts | POST | Yes (with key) | Optional | N/A | Uploads artifact - requires key for safe retry |
| /api/v1/media/artifacts/{id} | DELETE | Yes (natural) | No | N/A | Deletes artifact |

### Mastery
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/mastery | POST | Yes (with key) | Optional | N/A | Creates mastery item - requires key |
| /api/v1/mastery/obsolescence/scan | POST | **No** | N/A | Triggers scan workflow that analyzes current state | Each scan may produce different results as state changes |
| /api/v1/mastery/obsolescence-events/process | POST | **No** | N/A | Processes pending obsolescence events with side effects | Processing same event twice may cause duplicate actions |
| /api/v1/mastery/learning-deltas/{id}/evaluate | POST | **No** | N/A | Evaluation may trigger external analysis and produce different results | Re-evaluation may yield different scores as context changes |
| /api/v1/mastery/learning-deltas/{id}/promote | POST | **No** | N/A | Promotion is a state transition that may trigger downstream workflows | Cannot promote already promoted delta |
| /api/v1/mastery/learning-deltas/{id}/dry-run-promotion | POST | **No** | N/A | Dry run evaluates promotion without side effects but results vary | Each dry run may produce different evaluation results |
| /api/v1/mastery/{id}/transition | POST | **No** | N/A | State transition is irreversible and may trigger governance checks | Cannot transition from already reached state |
| /api/v1/mastery/learning-deltas/{id}/approve | POST | **No** | N/A | Approval is a state transition that may trigger promotion workflow | Cannot approve already approved delta |
| /api/v1/mastery/learning-deltas/{id}/reject | POST | **No** | N/A | Rejection is a state transition that is irreversible | Cannot reject already rejected delta |
| /api/v1/mastery/{id}/mark-maintenance-only | POST | **No** | N/A | State transition that changes item lifecycle status | Cannot mark already maintenance-only item |
| /api/v1/mastery/{id}/mark-obsolete | POST | **No** | N/A | State transition that may trigger deprecation workflows | Cannot mark already obsolete item |
| /api/v1/mastery/{id}/quarantine | POST | **No** | N/A | Quarantine is a state transition that may disable item usage | Cannot quarantine already quarantined item |
| /api/v1/mastery/{id}/retire | POST | **No** | N/A | Retirement is a final state transition that is irreversible | Cannot retire already retired item |

### Webhooks
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/webhooks | POST | Yes (with key) | Optional | N/A | Creates webhook - requires key |
| /api/webhooks/{id} | PUT | Yes (natural) | No | N/A | Replaces webhook |
| /api/webhooks/{id} | DELETE | Yes (natural) | No | N/A | Deletes webhook |

### Brain
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/brain/attention/elevate | POST | **No** | N/A | Elevates attention score which affects downstream processing | Repeated elevation may cause score to exceed intended threshold |
| /api/v1/brain/attention/thresholds | PUT | Yes (natural) | No | N/A | Updates thresholds |
| /api/v1/brain/patterns/match | POST | **No** | N/A | Pattern matching is a read operation but results vary with state | Not idempotent due to varying results, not side effects |

### Learning
| Route | Method | Idempotent | Idempotency Key Required | Reason (if non-idempotent) | Notes |
|-------|--------|------------|--------------------------|---------------------------|-------|
| /api/v1/action/learning/trigger | POST | **No** | N/A | Triggers learning cycle that analyzes data and may produce different patterns | Each trigger may discover new patterns as data changes |
| /api/v1/action/learning/review/{id}/approve | POST | **No** | N/A | Approval is a state transition that triggers pattern promotion workflow | Cannot approve already approved review |
| /api/v1/action/learning/review/{id}/reject | POST | **No** | N/A | Rejection is a state transition that is irreversible | Cannot reject already rejected review |

## Implementation Requirements

### Idempotency Key Header
- Header name: `X-Idempotency-Key`
- Format: UUID v4 string
- Validation: Must be valid UUID, max 255 characters
- TTL: Keys are valid for 24 hours after first use

### Response Headers
- `X-Idempotency-Key`: Echo the key from request
- `X-Idempotency-Replayed`: Set to `true` if this is a replayed request

### Storage
- Idempotency keys stored in durable storage (PostgreSQL)
- Key-value pair: idempotencyKey -> responseHash
- Automatic cleanup after TTL expires

### Non-Idempotent Operations
- Must return HTTP 409 Conflict if called with idempotency key
- Response body: `{"error": "Operation is not idempotent and cannot be retried with idempotency key"}`
