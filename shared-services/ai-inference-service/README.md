# AI Inference Service

Production-grade AI Inference Service implementing the AI-First Strategic Framework from `docs/architecture/AI_FIRST_STRATEGIC_FRAMEWORK_IMPLEMENTATION_PLAN.md`.

## Overview

Unified REST API service providing:
- **LLM Gateway**: Embedding and completion generation with provider routing, caching, and fallback
- **Rate Limiting**: Per-tenant token bucket rate limiting
- **Prompt Caching**: Content-addressed caching with HybridStateStore (local + Redis)
- **Cost Tracking**: Token usage metrics per tenant/model
- **Multi-Provider**: Pluggable provider architecture (OpenAI, local models, etc.)

## Architecture

```
HTTP Request → AIInferenceHttpAdapter
                    ↓
              LLMGatewayService
                    ↓
        ┌───────────┴───────────┐
        ↓                       ↓
   ProviderRouter         PromptCache
        ↓                       ↓
   EmbeddingService      HybridStateStore
   CompletionService          ↓
                         Local + Redis
```

## API Endpoints

### POST /ai/infer/embedding
Generate single embedding.

**Request:**
```json
{
  "tenant": "tenant-123",
  "text": "Hello world"
}
```

**Response:**
```json
{
  "vector": [0.123, -0.456, ...],
  "dimensions": 1536,
  "model": "text-embedding-ada-002"
}
```

### POST /ai/infer/embeddings
Generate batch embeddings.

**Request:**
```json
{
  "tenant": "tenant-123",
  "texts": ["Hello", "World"]
}
```

**Response:**
```json
{
  "embeddings": [[...], [...]],
  "count": 2
}
```

### POST /ai/infer/completion
Generate LLM completion.

**Request:**
```json
{
  "tenant": "tenant-123",
  "prompt": "Translate to French: Hello",
  "systemMessage": "You are a translator",
  "maxTokens": 100,
  "temperature": 0.7
}
```

**Response:**
```json
{
  "text": "Bonjour",
  "tokensUsed": 50,
  "promptTokens": 30,
  "completionTokens": 20,
  "finishReason": "stop",
  "model": "gpt-4"
}
```

### GET /health
Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "service": "ai-inference",
  "timestamp": "2025-11-16T12:00:00Z"
}
```

### GET /ai/admin/status
Admin status endpoint.

**Response:**
```json
{
  "service": "ai-inference",
  "gateway": "active",
  "timestamp": "2025-11-16T12:00:00Z"
}
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key (required) | - |
| `OPENAI_MODEL` | OpenAI model name | `gpt-4` |
| `OPENAI_MAX_TOKENS` | Max tokens per request | `2000` |
| `OPENAI_TEMPERATURE` | Sampling temperature | `0.7` |
| `AI_SERVICE_HOST` | HTTP server host | `0.0.0.0` |
| `AI_SERVICE_PORT` | HTTP server port | `8080` |
| `PROMPT_CACHE_TTL` | Cache TTL in seconds | `600` |
| `RATE_LIMIT_CAPACITY` | Rate limit bucket capacity | `1000` |
| `RATE_LIMIT_RATE` | Rate limit refill rate (tokens/sec) | `10.0` |

## Running

### Development
```bash
export OPENAI_API_KEY="your-api-key"
./gradlew :products:shared-services:ai-inference-service:run
```

### Production
```bash
export OPENAI_API_KEY="your-api-key"
export AI_SERVICE_PORT=8080
java -jar ai-inference-service.jar
```

### Docker
```bash
docker build -t ai-inference-service .
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=your-key \
  ai-inference-service
```

## Testing

### Unit Tests
```bash
./gradlew :products:shared-services:ai-inference-service:test
```

### Integration Tests
```bash
# Start service
./gradlew :products:shared-services:ai-inference-service:run &

# Test embedding endpoint
curl -X POST http://localhost:8080/ai/infer/embedding \
  -H "Content-Type: application/json" \
  -d '{"tenant": "test", "text": "Hello world"}'

# Test completion endpoint
curl -X POST http://localhost:8080/ai/infer/completion \
  -H "Content-Type: application/json" \
  -d '{"tenant": "test", "prompt": "Say hello", "maxTokens": 50}'
```

## Metrics

All operations emit metrics via Micrometer:

- `ai.gateway.cache.hit{tenant,operation}` - Cache hit count
- `ai.gateway.cache.miss{tenant,operation}` - Cache miss count
- `ai.gateway.errors{tenant,operation}` - Error count
- `ai.gateway.duration{tenant,operation}` - Operation duration (ms)
- `ai.gateway.cost.tokens{tenant,operation}` - Token usage
- `ai.gateway.ratelimit.exceeded{tenant,operation}` - Rate limit violations
- `ai.gateway.fallback.count{tenant,operation}` - Fallback activations
- `ai.completion.count{provider,model}` - Completion count
- `ai.completion.duration{provider,model}` - Completion duration (ms)
- `ai.completion.tokens{provider,model}` - Token usage
- `ai.completion.errors{provider,model}` - Completion errors

## Performance

### Targets (per Plan)
- Gateway overhead: p95 < 10ms
- Cache hit latency: ~1ms
- Cache miss latency: ~5ms + provider latency
- Fallback overhead: +50-200ms

### Optimization
- Prompt caching reduces API costs by 50-70% for repeated requests
- Rate limiting prevents cost overruns
- Provider routing enables cost-optimized model selection

## Dependencies

### Libraries Used
- `libs:ai-integration` - EmbeddingService, CompletionService, LLMConfiguration
- `libs:ai-platform:gateway` - LLMGatewayService, ProviderRouter, PromptCache, RateLimiter
- `libs:ai-platform:registry` - ModelRegistryService (future)
- `libs:ai-platform:feature-store` - FeatureStoreService (future)
- `libs:http-server` - ResponseBuilder, RoutingServlet (core HTTP abstractions)
- `libs:observability` - MetricsCollector, tracing
- `libs:state` - HybridStateStore for caching

### External Services
- OpenAI API (embeddings, completions)
- Redis (prompt cache central store, future)
- PostgreSQL (model registry, feature store, future)

## Roadmap

### Phase 1 (Completed)
- ✅ LLM Gateway with routing and caching
- ✅ Prompt caching with HybridStateStore
- ✅ Rate limiting per tenant
- ✅ REST API endpoints
- ✅ OpenAI provider integration

### Phase 2 (Next)
- [ ] Redis adapter for central prompt cache
- [ ] Batch inference job scheduler
- [ ] A/B testing service integration
- [ ] Model registry integration
- [ ] Feature store integration

### Phase 3 (Future)
- [ ] Additional providers (Anthropic, local models)
- [ ] Cost optimization recommendations
- [ ] Drift detection (data + model)
- [ ] Quality monitoring dashboard
- [ ] Training pipeline orchestration

## Security

- API keys stored in environment variables (never committed)
- Rate limiting prevents abuse
- Tenant isolation enforced at all layers
- Prompt sanitization (PII removal)
- Audit logging for all operations

## Support

For issues or questions:
- See main documentation: `docs/architecture/AI_FIRST_STRATEGIC_FRAMEWORK_IMPLEMENTATION_PLAN.md`
- Check logs: Service uses Log4j2 with structured logging
- Metrics: Prometheus endpoint at `/metrics` (future)

## License

Internal use only - Ghatana Platform

