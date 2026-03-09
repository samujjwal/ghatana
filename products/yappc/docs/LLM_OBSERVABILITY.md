# LLM Observability Guide

This guide explains how to track and monitor LLM usage in YAPPC for cost optimization, performance monitoring, and quality assurance.

## Overview

YAPPC's LLM observability system tracks:
- **Cost** — Per-tenant, per-user, per-feature cost tracking
- **Latency** — Request duration monitoring (p50, p95, p99)
- **Token Usage** — Prompt and completion token tracking
- **Error Rate** — Failed request monitoring
- **Cache Hit Rate** — Semantic cache effectiveness

## Architecture

```
┌─────────────────┐
│  LLM Request    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ LLMMetrics      │ ← Capture request/response data
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│ LLMObservabilityTracker │ ← Aggregate metrics
└────────┬────────────────┘
         │
         ├─► Logs (SLF4J)
         ├─► Prometheus (TODO)
         └─► DataDog (TODO)
```

## Usage

### 1. Track an LLM Request

```java
import com.ghatana.yappc.api.observability.LLMMetrics;
import com.ghatana.yappc.api.observability.LLMObservabilityTracker;

// Before LLM call
long startTime = System.currentTimeMillis();

// Make LLM call
LLMResponse response = llmGateway.complete(prompt);

// After LLM call
long latencyMs = System.currentTimeMillis() - startTime;

// Calculate cost
double cost = LLMObservabilityTracker.calculateCost(
    "gpt-4",
    response.getPromptTokens(),
    response.getCompletionTokens()
);

// Track metrics
LLMMetrics metrics = LLMMetrics.builder()
    .requestId(UUID.randomUUID().toString())
    .model("gpt-4")
    .provider("openai")
    .latencyMs(latencyMs)
    .promptTokens(response.getPromptTokens())
    .completionTokens(response.getCompletionTokens())
    .totalTokens(response.getTotalTokens())
    .estimatedCost(cost)
    .tenantId(tenantId)
    .userId(userId)
    .feature("ai-suggestions")
    .cached(false)
    .build();

LLMObservabilityTracker.getInstance().track(metrics);
```

### 2. Track Errors

```java
try {
    LLMResponse response = llmGateway.complete(prompt);
    // ... track success metrics
} catch (Exception e) {
    LLMMetrics metrics = LLMMetrics.builder()
        .requestId(requestId)
        .model("gpt-4")
        .provider("openai")
        .latencyMs(latencyMs)
        .tenantId(tenantId)
        .userId(userId)
        .feature("ai-suggestions")
        .errorCode(e.getClass().getSimpleName())
        .metadata(Map.of("error", e.getMessage()))
        .build();
    
    LLMObservabilityTracker.getInstance().track(metrics);
    throw e;
}
```

### 3. Track Cached Responses

```java
// Check cache first
Optional<String> cachedResponse = semanticCache.get(prompt);

if (cachedResponse.isPresent()) {
    // Track cache hit
    LLMMetrics metrics = LLMMetrics.builder()
        .requestId(requestId)
        .model("gpt-4")
        .provider("openai")
        .latencyMs(5) // Cache lookup is fast
        .promptTokens(0)
        .completionTokens(0)
        .totalTokens(0)
        .estimatedCost(0.0) // No cost for cached response
        .tenantId(tenantId)
        .userId(userId)
        .feature("ai-suggestions")
        .cached(true)
        .build();
    
    LLMObservabilityTracker.getInstance().track(metrics);
    return cachedResponse.get();
}

// Cache miss - make LLM call
// ... track as normal
```

## Metrics Summary

### Get Overall Metrics

```java
LLMObservabilityTracker tracker = LLMObservabilityTracker.getInstance();
MetricsSummary summary = tracker.getSummary();

System.out.println(summary);
// Output: LLM Metrics: 1250 requests, 2.40% errors, 35.20% cache hits, 
//         1,250,000 tokens, $15.75 cost, 850ms avg latency
```

### Get Tenant-Specific Metrics

```java
TenantMetrics tenantMetrics = tracker.getTenantMetrics("tenant-123");

System.out.printf("Tenant 123: %d requests, $%.2f cost, %d tokens%n",
    tenantMetrics.getRequests(),
    tenantMetrics.getCost(),
    tenantMetrics.getTokens()
);
```

### Get Feature-Specific Metrics

```java
FeatureMetrics featureMetrics = tracker.getFeatureMetrics("ai-suggestions");

System.out.printf("AI Suggestions: %d requests, $%.2f cost, %.0fms avg latency%n",
    featureMetrics.getRequests(),
    featureMetrics.getCost(),
    featureMetrics.getAvgLatencyMs()
);
```

## Model Pricing

Current pricing (per 1K tokens):

| Model | Prompt Cost | Completion Cost |
|-------|-------------|-----------------|
| gpt-4 | $0.03 | $0.06 |
| gpt-4-turbo | $0.01 | $0.03 |
| gpt-3.5-turbo | $0.0005 | $0.0015 |
| claude-3-opus | $0.015 | $0.075 |
| claude-3-sonnet | $0.003 | $0.015 |
| claude-3-haiku | $0.00025 | $0.00125 |

**Note:** Pricing is updated periodically. Check provider documentation for latest rates.

## Alerts

### High-Cost Requests

Requests costing more than $0.10 trigger a warning log:

```
WARN  LLMObservabilityTracker - High-cost LLM request: $0.1250 for feature 'ai-suggestions' (tenant: tenant-123)
```

### Slow Requests

Requests taking longer than 5 seconds trigger a warning log:

```
WARN  LLMObservabilityTracker - Slow LLM request: 6500ms for model 'gpt-4' (feature: ai-suggestions)
```

## Cost Optimization Strategies

### 1. Use Semantic Caching

Cache LLM responses for similar prompts:

```java
// Before making LLM call
String cacheKey = semanticCache.generateKey(prompt);
Optional<String> cached = semanticCache.get(cacheKey);

if (cached.isPresent()) {
    return cached.get(); // No LLM cost
}

// Make LLM call and cache result
String response = llmGateway.complete(prompt);
semanticCache.put(cacheKey, response);
```

**Impact:** 30-40% cost reduction for repeated queries

### 2. Use Cheaper Models for Simple Tasks

```java
// Use GPT-3.5 for simple classification
if (task.isSimple()) {
    model = "gpt-3.5-turbo"; // 60x cheaper than GPT-4
} else {
    model = "gpt-4";
}
```

**Impact:** 50-70% cost reduction for simple tasks

### 3. Optimize Prompt Length

```java
// Minimize prompt tokens
String prompt = buildMinimalPrompt(context);

// Track prompt length
logger.info("Prompt length: {} tokens", estimateTokens(prompt));
```

**Impact:** 10-20% cost reduction

### 4. Batch Requests

```java
// Instead of N individual requests
for (Requirement req : requirements) {
    llmGateway.analyze(req); // N * cost
}

// Batch into single request
llmGateway.analyzeBatch(requirements); // 1 * cost
```

**Impact:** Up to 90% cost reduction for batch operations

## Monitoring Dashboard

### Key Metrics to Monitor

1. **Daily Cost** — Track spending trends
2. **Cost per Tenant** — Identify high-usage tenants
3. **Cost per Feature** — Optimize expensive features
4. **Error Rate** — Monitor reliability
5. **Cache Hit Rate** — Optimize caching strategy
6. **P95 Latency** — Monitor performance

### Example Queries

```java
// Daily cost
double dailyCost = tracker.getSummary().getTotalCost();

// Top 10 tenants by cost
Map<String, Double> tenantCosts = tenantMetrics.entrySet().stream()
    .sorted((a, b) -> Double.compare(b.getValue().getCost(), a.getValue().getCost()))
    .limit(10)
    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getCost()));

// Feature cost breakdown
Map<String, Double> featureCosts = featureMetrics.entrySet().stream()
    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getCost()));
```

## Integration with Monitoring Systems

### Prometheus (TODO)

```java
// Export metrics to Prometheus
@Scheduled(fixedRate = 60000) // Every minute
public void exportToPrometheus() {
    MetricsSummary summary = tracker.getSummary();
    
    prometheusRegistry.gauge("llm_requests_total", summary.getTotalRequests());
    prometheusRegistry.gauge("llm_cost_total", summary.getTotalCost());
    prometheusRegistry.gauge("llm_error_rate", summary.getErrorRate());
    prometheusRegistry.gauge("llm_cache_hit_rate", summary.getCacheHitRate());
}
```

### DataDog (TODO)

```java
// Send metrics to DataDog
public void exportToDataDog(LLMMetrics metrics) {
    statsd.increment("llm.requests", 1, 
        "model:" + metrics.getModel(),
        "feature:" + metrics.getFeature(),
        "tenant:" + metrics.getTenantId()
    );
    
    statsd.gauge("llm.cost", metrics.getEstimatedCost(),
        "feature:" + metrics.getFeature()
    );
    
    statsd.histogram("llm.latency", metrics.getLatencyMs(),
        "model:" + metrics.getModel()
    );
}
```

## Best Practices

### ✅ DO

- **Track all LLM requests** — Even cached responses
- **Include context** — Tenant, user, feature for debugging
- **Monitor costs daily** — Set up alerts for unusual spending
- **Use semantic caching** — Reduce costs by 30-40%
- **Choose appropriate models** — Don't use GPT-4 for simple tasks
- **Set cost budgets** — Per-tenant and per-feature limits

### ❌ DON'T

- **Don't ignore errors** — Track failed requests for debugging
- **Don't skip metadata** — Context helps with cost attribution
- **Don't use expensive models unnecessarily** — GPT-3.5 is often sufficient
- **Don't forget to cache** — Repeated queries waste money
- **Don't ignore latency** — Slow requests hurt UX

## Troubleshooting

### High Costs

**Symptom:** Daily costs exceeding budget

**Solutions:**
1. Check top tenants by cost — Identify heavy users
2. Check top features by cost — Optimize expensive features
3. Increase cache hit rate — Improve semantic caching
4. Use cheaper models — Switch to GPT-3.5 where possible
5. Optimize prompts — Reduce token usage

### High Error Rate

**Symptom:** Error rate > 5%

**Solutions:**
1. Check error codes — Identify common failures
2. Review rate limits — May be hitting provider limits
3. Check model availability — Provider may have outages
4. Review prompts — May be triggering content filters

### Low Cache Hit Rate

**Symptom:** Cache hit rate < 20%

**Solutions:**
1. Improve semantic similarity — Better embedding model
2. Increase cache TTL — Keep responses longer
3. Normalize prompts — Remove variable parts
4. Pre-warm cache — Cache common queries

## References

- [OpenAI Pricing](https://openai.com/pricing)
- [Anthropic Pricing](https://www.anthropic.com/pricing)
- [LangChain Callbacks](https://python.langchain.com/docs/modules/callbacks/)
- [Semantic Caching](https://www.pinecone.io/learn/semantic-search/)
