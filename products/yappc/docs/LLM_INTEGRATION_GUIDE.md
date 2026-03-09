# LLM Integration Guide - langchain4j

**Status:** Infrastructure Complete, Dependencies Required  
**Priority:** High  
**Estimated Effort:** 2-3 days

---

## Overview

Complete LLM integration infrastructure has been implemented with:
- ✅ Prompt template registry with versioning
- ✅ AI fallback strategy with circuit breaker
- ✅ LLM observability tracking
- ✅ Production-ready LLM service
- ⏳ **Requires:** langchain4j dependencies in build.gradle.kts

---

## Implementation Status

### ✅ Completed Components

1. **PromptTemplate.java** - Versioned templates with variable substitution
2. **PromptTemplateRegistry.java** - A/B testing, version management
3. **AIFallbackStrategy.java** - Circuit breaker, retry, caching
4. **AIResponseCache.java** - Response caching
5. **LLMService.java** - Production service with multi-provider support
6. **LLMObservabilityTracker.java** - Cost and latency tracking

### ⏳ Required Dependencies

Add to `products/yappc/backend/api/build.gradle.kts`:

```kotlin
dependencies {
    // LangChain4J Core
    implementation("dev.langchain4j:langchain4j:0.27.1")
    
    // OpenAI Integration
    implementation("dev.langchain4j:langchain4j-open-ai:0.27.1")
    
    // Anthropic Integration
    implementation("dev.langchain4j:langchain4j-anthropic:0.27.1")
    
    // Optional: Azure OpenAI
    // implementation("dev.langchain4j:langchain4j-azure-open-ai:0.27.1")
    
    // Optional: Local models via Ollama
    // implementation("dev.langchain4j:langchain4j-ollama:0.27.1")
}
```

### ⏳ Required Environment Variables

```bash
# OpenAI Configuration
OPENAI_API_KEY=sk-...

# Anthropic Configuration
ANTHROPIC_API_KEY=sk-ant-...

# Optional: Azure OpenAI
# AZURE_OPENAI_KEY=...
# AZURE_OPENAI_ENDPOINT=https://...
```

---

## Usage Examples

### Basic LLM Call

```java
// Initialize service
LLMService llmService = new LLMService(
    System.getenv("OPENAI_API_KEY"),
    System.getenv("ANTHROPIC_API_KEY"),
    PromptTemplateRegistry.getInstance(),
    new AIFallbackStrategy(new AIResponseCache(3600000), 3, 60000)
);

// Generate AI suggestion
Map<String, String> variables = Map.of(
    "requirement", "User authentication with OAuth2",
    "context", "Microservices architecture with JWT tokens"
);

String suggestion = llmService.generate(
    "ai-suggestion",
    variables,
    "tenant-123",
    "user-456"
);
```

### A/B Testing

```java
// Test two prompt variants
String response = llmService.generateWithABTest(
    "code-review",
    variables,
    "user-456",
    0.5,  // 50% split
    "tenant-123"
);

// Check which variant was used
PromptTemplateRegistry.TemplateUsageStats stats = 
    PromptTemplateRegistry.getInstance()
        .getUsageStats("code-review-v1");
```

### Custom Template

```java
// Register new template
PromptTemplate customTemplate = new PromptTemplate(
    "custom-analysis-v1",
    "custom-analysis",
    "1.0.0",
    """
    Analyze the following code for security vulnerabilities:
    
    Code: {{code}}
    Language: {{language}}
    
    Provide:
    1. Security risk assessment
    2. Specific vulnerabilities found
    3. Remediation recommendations
    """,
    "Custom security analysis",
    Map.of("category", "security", "model", "gpt-4"),
    Instant.now(),
    true
);

PromptTemplateRegistry.getInstance().register(customTemplate);
```

---

## Supported Models

### OpenAI Models

| Model | Use Case | Cost | Speed |
|-------|----------|------|-------|
| `gpt-4` | Complex reasoning, code review | High | Slow |
| `gpt-4-turbo` | Faster GPT-4 with larger context | Medium | Fast |
| `gpt-3.5-turbo` | Simple tasks, suggestions | Low | Very Fast |

### Anthropic Models

| Model | Use Case | Cost | Speed |
|-------|----------|------|-------|
| `claude-3-opus` | Highest quality, complex tasks | High | Slow |
| `claude-3-sonnet` | Balanced quality and speed | Medium | Fast |
| `claude-3-haiku` | Fast, simple tasks | Low | Very Fast |

---

## Fallback Strategy

The LLM service automatically handles failures:

1. **Primary:** Call LLM provider
2. **Retry:** Exponential backoff (1s, 2s, 4s)
3. **Cache:** Return cached response if available
4. **Fallback:** Rule-based response

### Circuit Breaker

- Opens after **5 consecutive failures**
- Resets after **60 seconds**
- Prevents cascading failures

---

## Observability

All LLM calls are automatically tracked:

```java
// Get metrics summary
LLMObservabilityTracker tracker = LLMObservabilityTracker.getInstance();
tracker.logMetricsSummary();

// Check cost by tenant
Map<String, Double> costs = tracker.getCostByTenant();
System.out.println("Tenant tenant-123 cost: $" + costs.get("tenant-123"));

// Check latency
Map<String, Double> latencies = tracker.getAverageLatencyByFeature();
System.out.println("AI suggestion avg latency: " + latencies.get("ai-suggestion") + "ms");
```

### Tracked Metrics

- **Cost:** Per tenant, user, feature, model
- **Latency:** Average, P95, P99
- **Tokens:** Prompt, completion, total
- **Errors:** Rate, types, codes
- **Cache:** Hit rate, savings

---

## Testing

### Unit Tests

```java
@Test
void testPromptTemplateRendering() {
    PromptTemplate template = new PromptTemplate(
        "test-1", "test", "1.0.0",
        "Hello {{name}}, you are {{age}} years old",
        "Test template",
        Map.of(),
        Instant.now(),
        true
    );
    
    String result = template.render(Map.of(
        "name", "Alice",
        "age", "30"
    ));
    
    assertEquals("Hello Alice, you are 30 years old", result);
}
```

### Integration Tests

```java
@Test
void testLLMServiceWithFallback() {
    // Mock LLM failure
    LLMService service = new LLMService(
        null,  // No API key - will fail
        null,
        PromptTemplateRegistry.getInstance(),
        new AIFallbackStrategy(new AIResponseCache(3600000), 1, 1000)
    );
    
    // Should return fallback response
    String response = service.generate(
        "ai-suggestion",
        Map.of("requirement", "Test"),
        "tenant-1",
        "user-1"
    );
    
    assertNotNull(response);
    assertTrue(response.contains("Please review"));
}
```

---

## Performance Optimization

### Caching Strategy

```java
// Cache responses for 1 hour
AIResponseCache cache = new AIResponseCache(3600000);

// Cache hit reduces cost and latency
String cached = cache.get("ai-suggestion:12345").orElse(null);
```

### Batch Processing

```java
// Process multiple requests in parallel
List<CompletableFuture<String>> futures = requirements.stream()
    .map(req -> CompletableFuture.supplyAsync(() -> 
        llmService.generate("ai-suggestion", req, tenantId, userId)
    ))
    .collect(Collectors.toList());

List<String> results = futures.stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toList());
```

---

## Cost Management

### Budget Alerts

```java
// Set cost threshold
double dailyBudget = 100.0;  // $100/day

// Check if budget exceeded
double todayCost = tracker.getTotalCostToday();
if (todayCost > dailyBudget) {
    logger.warn("Daily budget exceeded: ${}", todayCost);
    // Disable AI features or switch to cheaper model
}
```

### Model Selection

```java
// Use cheaper model for simple tasks
String model = complexity > 0.7 ? "gpt-4" : "gpt-3.5-turbo";

// Override model in template metadata
template.getMetadata().put("model", model);
```

---

## Security

### API Key Management

- Store API keys in environment variables
- Never commit keys to version control
- Rotate keys regularly
- Use separate keys for dev/staging/prod

### Rate Limiting

```java
// Implement per-tenant rate limiting
RateLimiter rateLimiter = RateLimiter.create(10.0);  // 10 requests/second

if (!rateLimiter.tryAcquire()) {
    throw new RateLimitExceededException("Too many requests");
}
```

---

## Troubleshooting

### Common Issues

**Issue:** `OpenAiChatModel cannot be resolved`  
**Solution:** Add langchain4j dependencies to build.gradle.kts

**Issue:** `API key not configured`  
**Solution:** Set OPENAI_API_KEY or ANTHROPIC_API_KEY environment variable

**Issue:** `Circuit breaker open`  
**Solution:** Check LLM provider status, wait 60s for reset

**Issue:** `High latency`  
**Solution:** Use faster model (gpt-3.5-turbo), enable caching, batch requests

---

## Next Steps

1. **Add Dependencies:** Update build.gradle.kts with langchain4j
2. **Configure API Keys:** Set environment variables
3. **Test Integration:** Run unit and integration tests
4. **Monitor Costs:** Set up budget alerts
5. **Optimize Prompts:** A/B test different prompt variants

---

## References

- [LangChain4J Documentation](https://docs.langchain4j.dev/)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
- [Anthropic API Reference](https://docs.anthropic.com/claude/reference)
- [LLM Observability Guide](./LLM_OBSERVABILITY.md)
- [Prompt Template Registry](../backend/api/src/main/java/com/ghatana/yappc/api/ai/PromptTemplateRegistry.java)
