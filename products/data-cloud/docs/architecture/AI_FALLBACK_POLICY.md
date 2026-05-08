# Data Cloud AI Fallback Policy

This document explains the AI behavior difference between local preview and production deployments, and defines the fallback contract that every AI-dependent feature in Data Cloud must implement.

---

## The Two Modes

### Local Preview Mode (`local`, `test` profiles)

When running locally or in test environments, AI capabilities operate in **preview mode**:

- **Embedding**: A deterministic stub generates a fixed-size zero vector (or a hash-based vector) for all inputs. No external LLM/embedding provider is called.
- **LLM completions** (analytics, RAG, suggestions): Return a clearly-marked preview response such as `"[AI Preview — configure provider for real results]"`. Never return a hallucinated real-looking response.
- **Alert suggestions**: Return a static or empty suggestion list.
- **Semantic search**: Falls back to keyword/exact-match search using the configured entity store.

This mode allows UI development and integration testing without an AI provider configured.

### Production Mode (`sovereign`, `production` profiles)

- **Embedding**: A real embedding model is called via `platform/java/ai-integration`. Provider is configured by `AI_PROVIDER`, `AI_API_KEY`, and `AI_EMBEDDING_MODEL` env vars.
- **LLM completions**: Real completion requests are sent to the configured provider. Responses are returned to callers.
- **Semantic search**: Vector similarity search uses real embeddings against the production index.

If the AI provider is unavailable or returns an error, production mode falls back to the same degraded response as local preview mode, with a `503` HTTP status and a `X-AI-Fallback: true` response header.

---

## Fallback Contract

Every AI-integrated feature MUST:

1. **Detect provider availability** at startup via the `AiGatingFilter` / `AiCapabilityGate`.
2. **Return a valid non-error response** when AI is unavailable — never propagate an AI provider error directly to the end user.
3. **Include a `X-AI-Fallback: true` header** when serving a degraded response.
4. **Log** the fallback with `WARN` level including the reason and the feature that fell back.
5. **Expose a metric** `data_cloud_ai_fallback_total{feature="<name>"}` incremented on each fallback.

---

## Feature Matrix

| Feature | Local Preview | Production (AI up) | Production (AI down) |
| --- | --- | --- | --- |
| Entity embedding on write | Stub vector | Real embedding | Skip embedding, log warn |
| Similarity search | Keyword fallback | Vector search | Keyword fallback + header |
| RAG context retrieval | Empty context | Real vector retrieval | Empty context + header |
| Analytics natural-language | Preview stub | Real completion | Preview stub + header |
| Alert AI suggestions | Static empty list | Real suggestions | Static empty list + header |
| Data quality scoring | Preview score (0.5) | Real model score | Preview score + header |

---

## Configuration

```properties
# Required for production AI
AI_PROVIDER=openai
AI_API_KEY=<secret>
AI_EMBEDDING_MODEL=text-embedding-3-small
AI_COMPLETION_MODEL=gpt-4o

# Optional: disable AI entirely (forces local preview behavior)
AI_ENABLED=false
```

Setting `AI_ENABLED=false` in production is allowed for deployments that do not require AI features. All AI-dependent features must degrade gracefully.

---

## Testing

- Unit tests: mock `AiCapabilityGate.isAiEnabled()` returning `false` to exercise the fallback path.
- Integration tests: use `@ActiveProfiles("local")` — the stub AI provider is active by default.
- E2E tests: set `AI_ENABLED=false` in the test environment to verify graceful degradation.
- Never configure a real AI provider in CI pipelines. Test against the stub only.
