# AI/LLM Integration & Quality — Detailed Implementation Plan

**Priority:** P0 CRITICAL  
**Current State:** Declared complete but unverified end-to-end; no quality telemetry; no confidence scoring; no graceful degradation  
**Target State:** All AI workflows verified end-to-end with telemetry, confidence scoring, fallback, and real-time cost monitoring  
**Estimated Effort:** 4 sprints (~36 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `AIModelRouter.java` | `core/ai/src/.../router/` | ✅ Exists — dispatches to adapters |
| `OllamaModelAdapter.java` | Same | ✅ Exists |
| `OpenAIModelAdapter.java` | Same | ✅ Exists |
| `AnthropicModelAdapter.java` | Same | ✅ Exists |
| `ModelSelector.java` | Same | ✅ Exists |
| `SemanticCache.java` | Same | ✅ Exists |
| `YAPPCAIService.java` | `core/ai/src/.../service/` | ✅ Top-level service — call paths unverified |
| `AISuggestionService.java` | `core/ai/src/.../suggestion/` | ✅ Exists |
| `VectorSearchService.java` | `core/ai/src/.../vector/` | ✅ Exists |
| `SemanticCacheService.java` | `core/ai/src/.../cache/` | ✅ Exists |
| `CostTrackingService.java` | `core/ai/src/.../cost/` | ✅ Exists — usage unverified |
| `AIMetricsCollector.java` | `core/ai/src/.../metrics/` | ✅ Exists — but not integrated into Prometheus |
| `AISafetyFilter.java` | `core/ai/src/.../safety/` | ✅ Exists |
| `AIFallbackService.java` | `core/ai/src/.../resilience/` | ✅ Exists — wiring unverified |
| `DefaultAIFallbackService.java` | Same | ✅ Exists |
| `WorkflowOrchestrator.java` | `core/ai/src/.../workflow/` | ✅ Exists |
| AI providers (frontend) | `frontend/libs/yappc-ai/src/providers/` | ✅ 3 providers: OpenAI, Anthropic, Local |
| AI services (BFF) | `frontend/apps/api/src/services/ai/` | ✅ 3 services: ai, advanced-ai, resilient-ai |
| Confidence scoring | — | **MISSING** |
| AI quality telemetry | — | **MISSING** — `AIMetricsCollector` not wired to Prometheus |
| Calibration dataset | — | **MISSING** |
| AI call tracing (OpenTelemetry) | — | **MISSING** |

### Verified Gaps

1. **End-to-end path unverified** — no integration test exercises `YAPPCAIService → AIModelRouter → [adapter] → LLM → response`
2. **Confidence scoring absent** — AI outputs have no quality/confidence signal
3. **Fallback not tested** — `AIFallbackService` exists but is not called in all failure paths
4. **`CostTrackingService` not wired** — costs not accumulating in any real store
5. **`AIMetricsCollector` not Prometheus-integrated** — metrics not visible in Grafana
6. **Semantic cache hit rate unknown** — no Prometheus counter for cache hits/misses
7. **Safety filter bypass scenarios** — not tested under load

---

## 2. Target Architecture

```
Consumer (Lifecycle, Requirements, Code Gen, etc.)
  │
  ▼
YAPPCAIService (façade)
  ├── AISafetyFilter.check()          → block harmful prompts
  ├── SemanticCache.lookup()           → return cached if hit
  ├── AIModelRouter.route()            → select adapter
  │     ├── OllamaModelAdapter         → local Ollama
  │     ├── OpenAIModelAdapter         → OpenAI API
  │     └── AnthropicModelAdapter      → Anthropic API  
  ├── [retry / circuit breaker]        → resilience4j / custom
  ├── ConfidenceScorer.score()         → attach confidence to response
  ├── AISafetyFilter.filterOutput()    → filter harmful content from response
  ├── CostTrackingService.record()     → persist cost entry
  ├── AIMetricsCollector.record()      → Prometheus metrics
  └── AIFallbackService (if failure)   → graceful degradation
```

---

## 3. Implementation Tasks

### Sprint 1 — Trace and Verify Every AI Call Path (9 days)

#### T1.1 — Create AI Call Path Integration Tests [NEW] [L]
**File:** `core/ai/src/test/java/com/ghatana/yappc/ai/integration/AICallPathIT.java`

Each test must:
1. Start a local Ollama (via Testcontainers or test profile pointing to `localhost:11434`)
2. Call `YAPPCAIService` with a real (non-mocked) prompt
3. Assert a non-empty response
4. Assert the response was recorded in `CostTrackingService`
5. Assert the Prometheus metric counter incremented

```java
@ActiveJIntegrationTest
class AICallPathIT extends EventloopTestBase {
    
    @Test
    void ollamaAdapterShouldProduceNonEmptyResponse() {
        String result = runPromise(() -> aiService.complete("Say hello in one word."));
        assertThat(result).isNotBlank();
    }
    
    @Test
    void semanticCacheShouldReturnCachedResultOnIdenticalPrompt() {
        String prompt = "What is 2+2?";
        String first = runPromise(() -> aiService.complete(prompt));
        String second = runPromise(() -> aiService.complete(prompt));
        assertThat(second).isEqualTo(first);
        assertThat(cacheMetrics.hitCount()).isGreaterThan(0);
    }
    
    @Test
    void fallbackServiceShouldActivateWhenLLMUnavailable() {
        // Simulate LLM down by using invalid endpoint
        String result = runPromise(() -> aiServiceWithBrokenLLM.complete("test prompt"));
        assertThat(result).isNotBlank();  // fallback content, not exception
        assertThat(metrics.fallbackCount()).isEqualTo(1);
    }
}
```

#### T1.2 — Verify BFF AI Service Path [NEW] [M]
**File:** `frontend/apps/api/src/__tests__/integration/ai-service.integration.test.ts`

```typescript
describe('AI Service Integration', () => {
  it('should route prompt through resilient-ai.service to provider and return response', async () => {
    const service = new ResilientAIService(config);
    const result = await service.complete({ prompt: 'Hello', model: 'llama3' });
    expect(result.content).toBeTruthy();
    expect(result.confidence).toBeGreaterThan(0);
    expect(result.latencyMs).toBeGreaterThan(0);
  });

  it('should use semantic cache for repeated identical prompts', async () => {
    const prompt = 'Describe REST in one sentence.';
    const first = await service.complete({ prompt });
    const second = await service.complete({ prompt });
    expect(cacheService.hitCount).toBeGreaterThan(0);
    expect(second.fromCache).toBe(true);
  });
});
```

#### T1.3 — Wire `CostTrackingService` to Actual Persistence [MOD] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/cost/CostTrackingService.java`

Add a JDBC-backed implementation that persists cost entries to a `ai_cost_events` table:

```sql
CREATE TABLE ai_cost_events (
    event_id      VARCHAR(36) PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL,
    user_id       VARCHAR(36),
    model         VARCHAR(100) NOT NULL,
    provider      VARCHAR(50) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    cost_usd      DECIMAL(10,6) NOT NULL,
    workflow      VARCHAR(100),  -- which workflow triggered this call
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_cost_tenant ON ai_cost_events(tenant_id, created_at DESC);
```

#### T1.4 — Integrate `AIMetricsCollector` with Prometheus [MOD] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/metrics/AIMetricsCollector.java`

Using `io.prometheus.client` (already in `platform:java:observability`), register these metrics at startup:

```java
private final Counter aiCallsTotal = Counter.build()
    .name("yappc_ai_calls_total")
    .help("Total AI LLM calls")
    .labelNames("provider", "model", "workflow", "status")
    .register();

private final Histogram aiCallDuration = Histogram.build()
    .name("yappc_ai_call_duration_seconds")
    .help("AI LLM call latency")
    .labelNames("provider", "model")
    .buckets(0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0)
    .register();

private final Counter cacheHits = Counter.build()
    .name("yappc_ai_cache_hits_total")
    .help("Semantic cache hits")
    .labelNames("workflow")
    .register();

private final Counter cacheMisses = Counter.build()
    .name("yappc_ai_cache_misses_total")
    .help("Semantic cache misses")
    .labelNames("workflow")
    .register();

private final Gauge aiCostRunning = Gauge.build()
    .name("yappc_ai_cost_usd_running_total")
    .help("Running AI cost in USD for current billing period")
    .labelNames("tenant_id", "provider")
    .register();
```

---

### Sprint 2 — Confidence Scoring Layer (9 days)

#### T2.1 — Design `AIResponse` Value Object [NEW] [S]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/model/AIResponse.java`

Replace raw `String` response with a rich value object:

```java
/**
 * @doc.type class
 * @doc.purpose Wraps an AI model response with quality and cost metadata.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record AIResponse(
    String content,
    ConfidenceScore confidence,
    int promptTokens,
    int completionTokens,
    double costUsd,
    String provider,
    String model,
    boolean fromCache,
    Duration latency,
    List<String> safetyFlags   // populated by AISafetyFilter
) {
    public boolean isHighConfidence() { return confidence.level() == ConfidenceLevel.HIGH; }
    public boolean needsHumanReview() { return confidence.level() == ConfidenceLevel.LOW || !safetyFlags.isEmpty(); }
}
```

#### T2.2 — Implement `ConfidenceScorer` [NEW] [L]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/confidence/ConfidenceScorer.java`

```java
/**
 * @doc.type class
 * @doc.purpose Computes a calibrated confidence score for AI-generated outputs using multiple signals.
 * @doc.layer product
 * @doc.pattern Scorer
 */
public final class ConfidenceScorer {
    
    /**
     * Scoring approach (ensemble):
     * 1. Self-consistency: ask the LLM the same question N times, measure variance
     * 2. Logprob signals: if provider exposes token log-probabilities, use them
     * 3. Length heuristic: very short or very long responses score lower
     * 4. Safety filter: flagged content scores 0
     * 5. Domain keywords: does the response contain expected domain terms?
     */
    public Promise<ConfidenceScore> score(String prompt, String response, ModelContext ctx) {
        double selfConsistency = computeSelfConsistency(response);
        double lengthScore = computeLengthScore(response);
        double safetyPenalty = computeSafetyPenalty(response);
        double domainRelevance = computeDomainRelevance(prompt, response, ctx);
        
        double raw = (selfConsistency * 0.4) + (lengthScore * 0.2) + 
                     (domainRelevance * 0.4) - safetyPenalty;
        
        return Promise.of(ConfidenceScore.of(Math.max(0.0, Math.min(1.0, raw)));
    }
}
```

#### T2.3 — Update All Consumers to Use `AIResponse` [MOD] [M]
Update signatures throughout the AI call chain:
- `YAPPCAIService.complete()` returns `Promise<AIResponse>` (was `Promise<String>`)
- `AIModelRouter.route()` returns `Promise<AIResponse>`
- All adapters return `Promise<AIResponse>`

Update all callers (requirements service, code gen, suggestion service) to handle `AIResponse`.

#### T2.4 — Low-Confidence Routing [NEW] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/confidence/LowConfidenceRouter.java`

When confidence falls below threshold:
- `< 0.3` → trigger human-in-the-loop via `ApprovalService` (approval type: `AI_CONFIDENCE_REVIEW`)
- `0.3–0.6` → surface warning to user in UI with "AI is uncertain" indicator
- `> 0.6` → proceed autonomously

```java
public Promise<AIResponse> routeByConfidence(AIResponse response, WorkflowContext ctx) {
    return switch (response.confidence().level()) {
        case LOW -> hitlCoordinator.requestReview(response, ctx)
                       .map(reviewedResponse -> reviewedResponse);
        case MEDIUM -> Promise.of(response.withWarning("AI confidence is medium; recommend review"));
        case HIGH -> Promise.of(response);
    };
}
```

---

### Sprint 3 — Resilience & Graceful Degradation (9 days)

#### T3.1 — Implement Circuit Breaker on `AIModelRouter` [MOD] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/router/AIModelRouter.java`

Add circuit breaker state per provider:

```java
// State: CLOSED (normal) → OPEN (failing) → HALF_OPEN (testing)
public Promise<AIResponse> route(AIRequest request) {
    String provider = modelSelector.select(request);
    if (circuitBreaker.isOpen(provider)) {
        return fallbackService.getFallback(request);  // graceful degradation
    }
    
    return getAdapter(provider).complete(request)
        .then(response -> {
            circuitBreaker.recordSuccess(provider);
            return Promise.of(response);
        })
        .exceptionally(ex -> {
            circuitBreaker.recordFailure(provider);
            metrics.recordProviderFailure(provider);
            if (circuitBreaker.isOpen(provider)) {
                return fallbackService.getFallback(request);
            }
            throw ex;
        });
}
```

#### T3.2 — Implement Retry with Backoff [MOD] [S]
Add exponential backoff on `429 Too Many Requests` and `503 Service Unavailable` from all LLM adapters:

```java
private static final RetryPolicy RETRY_POLICY = RetryPolicy.builder()
    .maxAttempts(3)
    .backoffMs(500, 2.0)           // 500ms, 1s, 2s
    .retryOn(RateLimitException.class, ServiceUnavailableException.class)
    .build();
```

#### T3.3 — Verify and Test `DefaultAIFallbackService` [MOD] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/resilience/DefaultAIFallbackService.java`

The fallback service must return useful, contextual responses rather than generic errors. Implement content-type-specific fallbacks:

```java
public AIResponse getFallback(AIRequest request) {
    return switch (request.intent()) {
        case CODE_GENERATION -> AIResponse.fallback("// AI service unavailable. Please implement manually or retry.", ConfidenceLevel.NONE);
        case REQUIREMENT_ANALYSIS -> AIResponse.fallback("AI analysis unavailable. Proceeding with manual requirement review.", ConfidenceLevel.NONE);
        case TEST_GENERATION -> AIResponse.fallback("// Fallback: generate tests manually. AI service will resume shortly.", ConfidenceLevel.NONE);
        default -> AIResponse.fallback("AI service temporarily unavailable. Response may require human review.", ConfidenceLevel.NONE);
    };
}
```

#### T3.4 — Add OpenTelemetry Tracing to AI Calls [NEW] [M]
Instrument `YAPPCAIService` with distributed tracing spans:

```java
public Promise<AIResponse> complete(AIRequest request) {
    Span span = tracer.spanBuilder("ai.llm.complete")
        .setAttribute("ai.provider", request.preferredProvider())
        .setAttribute("ai.workflow", request.workflow())
        .setAttribute("ai.prompt_length", request.prompt().length())
        .startSpan();
    
    try (Scope scope = span.makeCurrent()) {
        return router.route(request)
            .then(response -> {
                span.setAttribute("ai.confidence", response.confidence().value());
                span.setAttribute("ai.tokens.total", response.promptTokens() + response.completionTokens());
                span.setAttribute("ai.from_cache", response.fromCache());
                span.setStatus(StatusCode.OK);
                return Promise.of(response);
            })
            .exceptionally(ex -> {
                span.recordException(ex);
                span.setStatus(StatusCode.ERROR, ex.getMessage());
                throw ex;
            })
            .whenComplete((r, ex) -> span.end());
    }
}
```

---

### Sprint 4 — BFF AI Quality & Frontend Signals (9 days)

#### T4.1 — Unify BFF AI Services [MOD] [M]
Currently three overlapping services: `ai.service.ts`, `advanced-ai.service.ts`, `resilient-ai.service.ts`.

**Consolidate into one `AIService` facade** that:
1. Tries the primary provider
2. Falls back to secondary on failure
3. Returns `AIResponse` with confidence
4. Logs cost to backend

```typescript
interface AIResponse {
  content: string;
  confidence: number;          // 0.0 - 1.0
  confidenceLevel: 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
  fromCache: boolean;
  latencyMs: number;
  provider: string;
  model: string;
  promptTokens: number;
  completionTokens: number;
  costUsd: number;
  safetyFlags: string[];
  needsHumanReview: boolean;
}

class AIService {
  async complete(request: AIRequest): Promise<AIResponse> { ... }
  async stream(request: AIRequest): AsyncIterable<AIStreamChunk> { ... }
  async embeddings(texts: string[]): Promise<number[][]> { ... }
}
```

#### T4.2 — Add Confidence Indicator to AI UI Outputs [NEW] [M]
**File:** `frontend/libs/yappc-ui/src/components/AIConfidenceIndicator.tsx`

```typescript
interface AIConfidenceIndicatorProps {
  confidence: number;
  confidenceLevel: 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
  needsHumanReview: boolean;
  onRequestReview?: () => void;
}

const AIConfidenceIndicator: React.FC<AIConfidenceIndicatorProps> = ({
  confidence, confidenceLevel, needsHumanReview, onRequestReview
}) => {
  const levelConfig = {
    HIGH: { label: 'High confidence', color: 'text-green-600', icon: CheckCircle },
    MEDIUM: { label: 'Medium confidence', color: 'text-yellow-600', icon: AlertTriangle },
    LOW: { label: 'Low confidence — review recommended', color: 'text-red-600', icon: AlertCircle },
    NONE: { label: 'AI unavailable', color: 'text-gray-400', icon: CloudOff },
  } satisfies Record<typeof confidenceLevel, { label: string; color: string; icon: React.ComponentType }>;

  const config = levelConfig[confidenceLevel];

  return (
    <div className="flex items-center gap-2">
      <config.icon className={`h-4 w-4 ${config.color}`} aria-hidden />
      <span className={`text-xs ${config.color}`}>{config.label} ({(confidence * 100).toFixed(0)}%)</span>
      {needsHumanReview && onRequestReview && (
        <button
          onClick={onRequestReview}
          className="text-xs underline text-blue-600"
          aria-label="Request human review of this AI output"
        >
          Request review
        </button>
      )}
    </div>
  );
};
```

#### T4.3 — Implement AI Cost Dashboard [NEW] [M]
**File:** `frontend/apps/web/src/features/admin/AICostDashboard.tsx`

Query Grafana embed or BFF aggregation endpoint for:
- Cost by workflow (code gen, requirements, test gen) over time
- Cost by provider (Ollama vs OpenAI vs Anthropic)
- Cache hit rate (cost savings from caching)
- Token usage trends

#### T4.4 — AI Quality Grafana Dashboard [NEW] [S]

Dashboard panels:
1. AI calls per minute by provider and workflow
2. Average confidence score by workflow (goal: > 0.7)
3. Cache hit rate (goal: > 40%)
4. Fallback rate (goal: < 5%)
5. P95 latency by provider
6. Cost per hour by tenant

---

## 4. Testing Requirements

### Unit Tests

| Test | Scenarios |
|------|-----------|
| `ConfidenceScorerTest` | High/medium/low scores computed correctly |
| `AIModelRouterTest` | Provider selection, circuit breaker trip, fallback activation |
| `DefaultAIFallbackServiceTest` | All intent types return useful fallback content |
| `CostTrackingServiceTest` | Cost persisted for every LLM call |
| `AIMetricsCollectorTest` | Counters/histograms increment correctly |

### Integration Tests

| Test | Verify |
|------|--------|
| `AICallPathIT` | Ollama → AIModelRouter → YAPPCAIService end-to-end |
| `AICircuitBreakerIT` | Circuit opens after 3 failures; fallback returned |
| `AISemanticCacheIT` | Identical prompts served from cache |
| `AIRetryIT` | 429 response triggers retry with backoff |

---

## 5. Observability

### Prometheus Metrics (Full List)

```
yappc_ai_calls_total{provider, model, workflow, status}
yappc_ai_call_duration_seconds{provider, model}
yappc_ai_confidence_score{workflow}                     histogram
yappc_ai_cache_hits_total{workflow}
yappc_ai_cache_misses_total{workflow}
yappc_ai_fallback_activations_total{provider, workflow}
yappc_ai_circuit_breaker_state{provider}                gauge (0=closed,1=open)
yappc_ai_cost_usd_running_total{tenant_id, provider}
yappc_ai_safety_filter_blocks_total{reason}
yappc_ai_human_review_requests_total{workflow, confidence_level}
```

### Alerts

```yaml
- alert: AIFallbackRateCritical
  expr: rate(yappc_ai_fallback_activations_total[5m]) > 0.2
  for: 2m
  severity: critical

- alert: AIConfidenceDegraded
  expr: avg(yappc_ai_confidence_score) < 0.5
  for: 10m
  severity: warning

- alert: AIProviderCircuitOpen
  expr: yappc_ai_circuit_breaker_state == 1
  for: 1m
  severity: critical
```
