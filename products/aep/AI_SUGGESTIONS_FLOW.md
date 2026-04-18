# AI Suggestions Call Chain Documentation

## Overview

The AI suggestions endpoint (`GET /api/v1/ai/suggestions`) provides anomaly-scored pipeline suggestions derived from real analytics data. This document traces the full call chain from HTTP route to the underlying data sources.

## Call Chain

```
HTTP Request
    ↓
GET /api/v1/ai/suggestions?tenantId=&limit=
    ↓
AepHttpServer (line 581)
    ↓
AiSuggestionsController.handleGetSuggestions()
    ↓
├─→ DataCloudAnalyticsStore.queryAnomalies() [if configured]
│   └─→ Returns anomaly records from Data-Cloud
│       └─→ Anomaly-backed suggestions (highest priority)
│
└─→ AepSloMetrics.runCountSnapshot() [if available]
    └─→ Returns pipeline run statistics
        └─→ SLO-backed hints (error rate warnings, throughput recommendations)
```

## Components

### 1. HTTP Route Registration

**Location**: `AepHttpServer.java:581`

```java
.with(HttpMethod.GET, "/api/v1/ai/suggestions", aiSuggestionsController::handleGetSuggestions)
```

### 2. Controller Initialization

**Location**: `AepHttpServer.java:459`

```java
this.aiSuggestionsController = new AiSuggestionsController(this.analyticsStore, this.sloMetrics);
```

The controller is initialized with two optional dependencies:
- `DataCloudAnalyticsStore` - for anomaly detection from Data-Cloud
- `AepSloMetrics` - for SLO-based pipeline statistics

### 3. AiSuggestionsController

**Location**: `AiSuggestionsController.java`

**Dependencies**:
- `DataCloudAnalyticsStore` (optional) - queries anomalies from Data-Cloud
- `AepSloMetrics` (optional) - provides recent run statistics

**Handler Method**: `handleGetSuggestions(HttpRequest request)`

**Flow**:
1. Extract `tenantId` and `limit` from query parameters
2. If `analyticsStore` is null, return fallback response
3. Query anomalies from last hour via `analyticsStore.queryAnomalies()`
4. Build suggestions from anomalies (highest priority)
5. Add SLO-based hints if metrics available
6. Return JSON response with scored suggestions

## Suggestion Types

### 1. Anomaly-Backed Suggestions (Highest Priority)

**Source**: `DataCloudAnalyticsStore.queryAnomalies()`

**Trigger**: Anomalies detected in the last hour

**Fields**:
- `id`: UUID
- `type`: "anomaly" or "warning"
- `severity`: "critical", "high", "medium", "low" (mapped from anomaly severity)
- `message`: Anomaly description with detection timestamp
- `confidence`: Anomaly score (0.0-1.0)
- `resourceType`: "pipeline"
- `resourceId`: Pipeline/entity ID (if available)

**Example**:
```json
{
  "id": "uuid-1",
  "type": "anomaly",
  "severity": "high",
  "message": "HIGH_ERROR_RATE (detected at 2026-04-18T10:30:00Z)",
  "confidence": 0.92,
  "resourceType": "pipeline",
  "resourceId": "pipeline-123"
}
```

### 2. SLO-Backed Hints (Secondary Priority)

**Source**: `AepSloMetrics.runCountSnapshot()`

**Trigger**: Pipeline run statistics exceeding thresholds

**Thresholds**:
- High error rate: ≥ 5%
- Critical error rate: ≥ 20%

**Fields**:
- `id`: UUID
- `type`: "warning" or "recommendation"
- `severity`: "high" (≥20%), "medium" (≥5%), "low"
- `message`: Error rate or throughput recommendation
- `confidence`: 0.85 (error rate), 0.80 (throughput)
- `resourceType`: "pipeline"

**Example**:
```json
{
  "id": "uuid-2",
  "type": "warning",
  "severity": "high",
  "message": "15.0% of 100 recent pipeline runs have failed — investigate failing steps.",
  "confidence": 0.85,
  "resourceType": "pipeline"
}
```

### 3. General Recommendation (No Signals)

**Trigger**: No anomalies found and no SLO metrics available

**Message**: "No active anomalies detected — system is operating normally."

## Degradation Behavior

### When DataCloudAnalyticsStore is Null

**Fallback Response**:
```json
{
  "suggestions": [
    {
      "id": "uuid",
      "type": "recommendation",
      "severity": "low",
      "message": "Connect DataCloud to enable AI-scored anomaly detection and optimisation suggestions.",
      "confidence": 1.0,
      "resourceType": "pipeline"
    }
  ],
  "count": 1,
  "tenantId": "test-tenant",
  "generatedAt": "2026-04-18T10:30:00Z"
}
```

**Behavior**:
- Returns a single recommendation to connect DataCloud
- Does not throw errors
- HTTP 200 response with meaningful degradation message

### When Query Fails

**Error Handling**:
```java
.then(Promise::of, e -> {
    log.error("[ai-suggestions] suggestion generation failed for tenant={}: {}",
            tenantId, e.getMessage(), e);
    return Promise.of(HttpHelper.errorResponse(500,
            "Failed to generate suggestions: " + e.getMessage()));
});
```

**Behavior**:
- Logs error with context
- Returns HTTP 500 with error message
- Does not crash the server

## Integration Testing

**Test File**: `AiSuggestionsIntegrationTest.java`

**Test Coverage**:
1. ✅ AI suggestions calls DataCloudAnalyticsStore when available
2. ✅ AI suggestions degrade gracefully when analytics store is null
3. ✅ AI suggestions return meaningful results when anomalies are found
4. ✅ AI suggestions include SLO-based hints when metrics available

**Run Tests**:
```bash
./gradlew :products:aep:server:test --tests AiSuggestionsIntegrationTest
```

## Configuration

### Required Environment Variables

None - the endpoint works without DataCloud (fallback mode)

### Optional Environment Variables (for DataCloud)

- `DC_SERVER_URL` - Data-Cloud server URL
- `DC_API_KEY` - Data-Cloud API key
- `DC_TENANT_ID` - Default tenant ID

### SLO Metrics Configuration

SLO metrics are automatically collected from pipeline runs. No additional configuration required.

## Performance Considerations

- Anomaly query limited to last hour (reduces data volume)
- Default limit: 10 suggestions
- Anomaly query limit: 50 (internal)
- SLO metrics are in-memory (fast access)

## Security Considerations

- Tenant isolation enforced via `tenantId` parameter
- No direct access to raw anomaly data (filtered through controller)
- No authentication required for suggestions (public endpoint)
- Consider adding authentication for production deployments

## Future Enhancements

- [ ] Add authentication/authorization to suggestions endpoint
- [ ] Support configurable time windows (beyond 1 hour)
- [ ] Add caching for suggestion responses
- [ ] Add suggestion dismissal/acknowledgment tracking
- [ ] Integrate with CompletionService for AI-generated explanations
- [ ] Add multi-tenant anomaly correlation
- [ ] Add suggestion priority weighting based on user role

## Notes

**CompletionService**: The task description mentions CompletionService, but the actual AI suggestions implementation does not use CompletionService. The suggestions are derived from:
- Real anomaly data from DataCloudAnalyticsStore
- SLO metrics from AepSloMetrics

CompletionService is used elsewhere in the AEP system for LLM providers (Anthropic, OpenAI, Ollama) in the agent dispatch stack, but not for AI suggestions.
