# P3-7: Enrichment Worker Dead-Letter Queue and Alerting

**Status:** Completed ✅

## Implementation Summary

Added dead-letter queue (DLQ) integration and alerting to the AI enrichment worker to handle failed enrichment operations and notify operations teams of issues.

## Changes Made

### 1. Enhanced Configuration (enrichment-worker.service.ts)

Added new configuration options:
- `dlqEnabled`: Enable/disable DLQ publishing (default: true)
- `alertThreshold`: Failure rate threshold for alerts (default: 0.1 = 10%)

### 2. Added New Interfaces

- `EnrichmentDlqEntry`: Represents a failed enrichment in the DLQ
- `EnrichmentAlert`: Represents an alert event for monitoring

### 3. DLQ Integration

**DLQ Table Schema:**
```sql
CREATE TABLE IF NOT EXISTS enrichment_dlq (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  item_id TEXT NOT NULL UNIQUE,
  error TEXT NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolved BOOLEAN NOT NULL DEFAULT FALSE,
  resolved_at TIMESTAMPTZ
)
```

**Features:**
- Automatic table creation on first use
- Publishes to DLQ when retries are exhausted
- Upsert behavior (update if item already in DLQ)
- Tracks retry count

### 4. Alerting System

**Alert Types:**
- `HIGH_FAILURE_RATE`: Triggered when failure rate exceeds threshold
- `DLQ_THRESHOLD_EXCEEDED`: Triggered when DLQ count exceeds 100
- `WORKER_DISABLED`: Triggered when worker is disabled but has work

**Alert Callbacks:**
```typescript
worker.onAlert((alert) => {
  console.error(`ALERT: ${alert.type} - ${alert.message}`);
  // Send to monitoring system, Slack, PagerDuty, etc.
});
```

### 5. DLQ Management Methods

- `getDlqEntries(limit)`: Fetch unresolved DLQ entries
- `retryDlqEntry(itemId)`: Retry a failed enrichment from DLQ

### 6. Metrics Enhancement

Added `dlqCount` to metrics to track DLQ size.

## Usage Example

```typescript
import { createEnrichmentWorker } from './services/ai/enrichment-worker.service';

const worker = createEnrichmentWorker(prisma);

// Register alert callback
worker.onAlert((alert) => {
  // Send to monitoring system
  monitoringService.emitAlert(alert);
});

// Enrich an item (will publish to DLQ if retries exhausted)
await worker.enrichItem(itemId);

// Check DLQ entries
const dlqEntries = await worker.getDlqEntries(50);

// Retry a failed item
await worker.retryDlqEntry(itemId);
```

## Environment Variables

```bash
# Enable/disable DLQ
AI_ENRICHMENT_DLQ_ENABLED=true

# Alert threshold (0-1, default 0.1 for 10%)
AI_ENRICHMENT_ALERT_THRESHOLD=0.15
```

## Benefits

1. **Visibility**: Failed enrichments are tracked in DLQ for inspection
2. **Recovery**: Failed items can be retried manually or automatically
3. **Alerting**: Operations team is notified of issues proactively
4. **Metrics**: DLQ size and failure rate are tracked for monitoring

## Future Enhancements

- Automatic DLQ retry with exponential backoff
- DLQ entry TTL (auto-expire old entries)
- Integration with centralized alerting system (PagerDuty, Slack)
- DLQ entry dashboard in admin UI
- Batch retry for multiple DLQ entries
