# Embedding Mode Configuration

## Overview

The Data-Cloud semantic search supports two embedding modes:

1. **DETERMINISTIC_HASH** (HASH_MODE - default): Uses a hash-based embedding function for text similarity. This mode is deterministic and does not require external AI services, but provides limited semantic understanding.

2. **REAL_EMBEDDING**: Uses the AI Inference Service to generate real semantic embeddings. This mode provides better semantic similarity but requires the AI Inference Service to be available.

## HASH_MODE Limitations

The `DETERMINISTIC_HASH` mode (also referred to as HASH_MODE) has the following limitations:

- **No True Semantic Understanding**: The embedding function uses token hash codes to populate vector dimensions, not actual semantic analysis. Two semantically similar texts with different tokenizations may have very different vectors.
- **Fixed Dimensionality**: Hash embeddings use a fixed 128-dimensional vector, which may not capture the nuance needed for complex semantic queries.
- **Deterministic but Not Meaningful**: The same text always produces the same vector, but the vector space does not reflect semantic relationships between concepts.
- **No Language Awareness**: Hash-based embeddings do not account for language structure, syntax, or meaning.
- **Limited Recall**: Semantic search with hash embeddings may miss relevant results that would be found with true semantic embeddings.

## Configuration

Set the embedding mode using the `EMBEDDING_MODE` environment variable:

```bash
export EMBEDDING_MODE=DETERMINISTIC_HASH  # Default (HASH_MODE)
# or
export EMBEDDING_MODE=REAL_EMBEDDING
```

## AI Inference Service Configuration

When using `REAL_EMBEDDING` mode, configure the AI Inference Service endpoint:

```bash
export AI_INFERENCE_SERVICE_URL=http://localhost:8083
export INTERNAL_API_KEY=your-internal-api-key  # Optional, for service-to-service auth
```

## Fallback Behavior

When `REAL_EMBEDDING` mode is configured and the AI Inference Service is unavailable, the system automatically falls back to `DETERMINISTIC_HASH` mode (HASH_MODE) to ensure continued operation.

## Health Indicator

The health endpoint includes the current embedding mode and AI Inference Service URL:

```json
{
  "semantic_search": {
    "status": "UP",
    "embeddingMode": "REAL_EMBEDDING",
    "aiInferenceServiceUrl": "http://localhost:8083"
  }
}
```

When in HASH_MODE:
```json
{
  "semantic_search": {
    "status": "UP",
    "embeddingMode": "DETERMINISTIC_HASH",
    "aiInferenceServiceUrl": "http://localhost:8083"
  }
}
```

## Migration Path from HASH_MODE to REAL_EMBEDDING

1. **Start with HASH_MODE** (default) - This provides basic functionality without external dependencies
2. **Deploy and configure the AI Inference Service** - Set up the service with appropriate model and capacity
3. **Configure environment variables** - Set `AI_INFERENCE_SERVICE_URL` and optionally `INTERNAL_API_KEY`
4. **Switch to REAL_EMBEDDING mode** - Set `EMBEDDING_MODE=REAL_EMBEDDING`
5. **Monitor health endpoint** - Confirm the embedding mode is `REAL_EMBEDDING`
6. **Validate semantic search quality** - Test queries to ensure improved relevance
7. **Gradual rollout** - Consider gradual rollout to monitor performance and costs

The system will automatically fall back to HASH_MODE if the AI service becomes unavailable, ensuring continued operation.

## Implementation Notes

- HASH_MODE uses a 128-dimensional vector populated by token hash codes
- REAL_EMBEDDING uses the dimensionality returned by the AI Inference Service (typically 1536 for OpenAI embeddings)
- The embedding function is called synchronously; for high-volume scenarios, consider batching or async patterns
- HASH_MODE is suitable for development and testing where semantic quality is not critical
- REAL_EMBEDDING is recommended for production use where semantic search quality is important
