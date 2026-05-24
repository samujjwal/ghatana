# CanonicalEvent

**Status:** Target specification  
**Owner:** AEP maintainers
**Current code contract:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/model/CanonicalEvent.java`

`CanonicalEvent` is the standard AEP event envelope. Agent outputs use the same envelope when they emit events.

## Fields

```json
{
  "eventId": "uuid",
  "tenantId": "string",
  "eventType": "string",
  "schemaVersion": "string",
  "eventTime": "instant",
  "processingTime": "instant",
  "detectionTime": "instant",
  "interval": {"start": "instant", "end": "instant"},
  "source": {},
  "entityRefs": [],
  "correlationId": "uuid",
  "causationId": "uuid",
  "payload": {},
  "confidence": {},
  "provenance": {},
  "policyTags": [],
  "idempotencyKey": "string"
}
```

Tenant ID, schema version, event time, correlation ID, and idempotency key are mandatory. Confidence and provenance are first-class.
