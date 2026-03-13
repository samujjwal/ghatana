# Aura Event Architecture

## Purpose

The event architecture enables asynchronous ingestion, personalization refresh, recommendation learning, agent coordination, and governance audit — without tight coupling between services. All events are immutable, versioned, and designed for idempotent consumption.

---

## Design Principles

1. **Immutability:** Events are never mutated after publication. Corrections are new events.
2. **Idempotency:** Every consumer must handle duplicate delivery without side effects. Use event IDs for deduplication.
3. **Versioning:** Every event schema carries a `schemaVersion` field. Breaking changes require a new version.
4. **Minimal payload:** Events carry just enough to process the event; consumers fetch additional data via API if needed.
5. **PII discipline:** Events do not carry raw PII. Profile events use user IDs; sensitive data is fetched from the profile service by authorized consumers.
6. **At-least-once delivery:** Consumers are built for at-least-once semantics. Use idempotency keys for operations with side effects.

---

## Event Topics

| Topic                 | Category              | Key Events                                                                 |
| --------------------- | --------------------- | -------------------------------------------------------------------------- |
| `aura.ingestion`      | Source & Ingestion    | `ProductDiscovered`, `ProductUpdated`, `SourceFetched`, `IngestionFailed`  |
| `aura.catalog`        | Knowledge Layer       | `ProductEnriched`, `BrandMerged`, `IngredientResolved`, `ShadesMapped`     |
| `aura.profile`        | Personal Intelligence | `ProfileUpdated`, `ConsentGranted`, `ConsentRevoked`, `PreferenceInferred` |
| `aura.recommendation` | Decision Layer        | `RecommendationGenerated`, `RecommendationServed`, `RecommendationExpired` |
| `aura.feedback`       | Learning              | `FeedbackCaptured` (click, save, dismiss, purchase, rating)                |
| `aura.governance`     | Observability         | `AuditEvent`, `ModelDeployed`, `DriftDetected`, `FairnessAlertTriggered`   |

---

## Canonical Event Schemas

### ProductDiscovered

Emitted when a new product reference is found by an ingestion source adapter.

```json
{
  "eventType": "ProductDiscovered",
  "schemaVersion": "1.0",
  "eventId": "evt_abc123",
  "productRef": "ext_123",
  "source": "brand_site",
  "sourceUrl": "https://example-brand.com/products/foundation-001",
  "occurredAt": "2026-03-12T12:00:00Z"
}
```

### ProductEnriched

Emitted after enrichment workers have resolved ingredients, shades, and metadata for a product.

```json
{
  "eventType": "ProductEnriched",
  "schemaVersion": "1.0",
  "eventId": "evt_def456",
  "productId": "prd_001",
  "enrichments": ["ingredients", "shade_metadata", "sentiment"],
  "ingredientCount": 22,
  "shadeCount": 14,
  "occurredAt": "2026-03-12T12:05:00Z"
}
```

### ProfileUpdated

Emitted when a user's profile attributes change — whether declared, inferred, or imported.

```json
{
  "eventType": "ProfileUpdated",
  "schemaVersion": "1.0",
  "eventId": "evt_ghi789",
  "userId": "usr_001",
  "changedKeys": ["skinType", "undertone"],
  "origin": "DECLARED",
  "occurredAt": "2026-03-12T12:10:00Z"
}
```

### ConsentGranted / ConsentRevoked

Emitted when a user grants or revokes consent for a specific scope.

```json
{
  "eventType": "ConsentGranted",
  "schemaVersion": "1.0",
  "eventId": "evt_jkl012",
  "userId": "usr_001",
  "scope": "wellness_integration",
  "occurredAt": "2026-03-12T12:10:30Z"
}
```

### RecommendationGenerated

Emitted when the recommendation engine produces a new recommendation batch for a user.

```json
{
  "eventType": "RecommendationGenerated",
  "schemaVersion": "1.0",
  "eventId": "evt_mno345",
  "recommendationId": "rec_001",
  "userId": "usr_001",
  "productId": "prd_001",
  "score": 0.91,
  "confidence": 0.84,
  "modelVersion": "ranker-v3",
  "reasonCodes": ["SHADE_MATCH", "INGREDIENT_SAFE"],
  "occurredAt": "2026-03-12T12:11:00Z"
}
```

### FeedbackCaptured

Emitted when a user interacts with a recommendation or product card.

```json
{
  "eventType": "FeedbackCaptured",
  "schemaVersion": "1.0",
  "eventId": "evt_pqr678",
  "userId": "usr_001",
  "productId": "prd_001",
  "recommendationId": "rec_001",
  "feedbackType": "SAVE",
  "occurredAt": "2026-03-12T12:12:00Z"
}
```

### AuditEvent

Emitted for all sensitive data access or mutation events for compliance and governance.

```json
{
  "eventType": "AuditEvent",
  "schemaVersion": "1.0",
  "eventId": "evt_stu901",
  "actorId": "usr_001",
  "action": "PROFILE_UPDATE",
  "resourceType": "UserProfile",
  "resourceId": "profile_001",
  "outcome": "SUCCESS",
  "occurredAt": "2026-03-12T12:10:00Z"
}
```

### DriftDetected

Emitted by observability systems when feature or score distribution drift exceeds defined thresholds.

```json
{
  "eventType": "DriftDetected",
  "schemaVersion": "1.0",
  "eventId": "evt_vwx234",
  "metricName": "recommendation_score_distribution",
  "driftScore": 0.18,
  "threshold": 0.1,
  "modelVersion": "ranker-v3",
  "occurredAt": "2026-03-12T18:00:00Z"
}
```

---

## Error Handling

| Scenario                    | Strategy                                                                                 |
| --------------------------- | ---------------------------------------------------------------------------------------- |
| Consumer processing failure | Event remains in queue; retried with exponential backoff                                 |
| Persistent failure          | Event moved to dead-letter topic (`*.dlq`) after max retries                             |
| Duplicate events            | Consumers deduplicate using `eventId` with a short-lived seen-IDs cache                  |
| Schema version mismatch     | Consumer flags `UNSUPPORTED_SCHEMA_VERSION` error; event routed to DLQ for investigation |

---

## Consumer Responsibilities

| Consumer                    | Subscribes To                          | Purpose                                                                     |
| --------------------------- | -------------------------------------- | --------------------------------------------------------------------------- |
| Enrichment Worker           | `aura.ingestion`                       | Triggers ingredient, shade, and sentiment enrichment                        |
| Recommendation Engine       | `aura.profile`, `aura.catalog`         | Invalidates and refreshes recommendation cache on profile or catalog change |
| Preference Learning Service | `aura.feedback`                        | Updates user preference vectors from interaction signals                    |
| Governance Service          | `aura.profile`, `aura.feedback`        | Writes audit trail; checks consent state                                    |
| Analytics Pipeline          | `aura.recommendation`, `aura.feedback` | Feeds quality and funnel metrics (tokenized user IDs only)                  |
| ML Training Pipeline        | `aura.feedback`                        | Accumulates labeled examples for next model training run                    |
| Observability Service       | `aura.governance`                      | Alerts on drift, fairness, and model events                                 |
