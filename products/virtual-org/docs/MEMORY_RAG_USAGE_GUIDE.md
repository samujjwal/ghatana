# Memory & Knowledge System - Usage Guide

> **Version:** 1.0.0  
> **Last Updated:** 2025-01-15  
> **Status:** Production Ready

## Table of Contents

1. [Overview](#overview)
2. [Memory System](#memory-system)
   - [Memory Types](#memory-types)
   - [StateStore API](#statestore-api)
   - [HybridStateStore](#hybridstatestore)
   - [Configuration](#memory-configuration)
3. [Knowledge/RAG System](#knowledgerag-system)
   - [Knowledge Sources](#knowledge-sources)
   - [RAG Pipeline](#rag-pipeline)
   - [Embedding Configuration](#embedding-configuration)
4. [Integration Patterns](#integration-patterns)
5. [Best Practices](#best-practices)
6. [Performance Tuning](#performance-tuning)
7. [Troubleshooting](#troubleshooting)

---

## Overview

The Virtual-Org Memory & Knowledge System provides agents with:

- **Short-term Memory**: Working context during task execution
- **Long-term Memory**: Persistent knowledge across sessions
- **RAG (Retrieval-Augmented Generation)**: Context-aware knowledge retrieval
- **Hybrid Storage**: Local + central sync for optimal performance

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Agent Runtime                             │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ Working      │    │ Short-Term   │    │ Long-Term    │       │
│  │ Memory       │    │ Memory       │    │ Memory       │       │
│  │ (5 min TTL)  │    │ (1 hr TTL)   │    │ (7 day TTL)  │       │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘       │
│         │                   │                   │                │
│         └───────────────────┴───────────────────┘                │
│                             │                                    │
│  ┌──────────────────────────┴──────────────────────────┐        │
│  │              HybridStateStore                        │        │
│  │  ┌─────────────────┐    ┌─────────────────┐         │        │
│  │  │  Local Store    │◄──►│  Central Store  │         │        │
│  │  │  (RocksDB)      │    │  (Redis/KVRocks)│         │        │
│  │  └─────────────────┘    └─────────────────┘         │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                  │
│  ┌──────────────────────────────────────────────────────┐        │
│  │              RAG Pipeline                            │        │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐ │        │
│  │  │ Ingest  │──│ Chunk   │──│ Embed   │──│ Store   │ │        │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘ │        │
│  │                                                      │        │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐ │        │
│  │  │ Query   │──│ Search  │──│ Rerank  │──│ Context │ │        │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘ │        │
│  └──────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Memory System

### Memory Types

| Type | TTL | Purpose | Use Case |
|------|-----|---------|----------|
| `WORKING` | 5 minutes | Active task context | Current operation state |
| `SHORT_TERM` | 1 hour | Session context | Conversation history |
| `LONG_TERM` | 7 days | Persistent knowledge | Learned preferences |
| `EPISODIC` | 30 days | Event sequences | Past interactions |
| `SEMANTIC` | 365 days | Factual knowledge | Domain facts |
| `PROCEDURAL` | 90 days | Task procedures | How-to knowledge |

### StateStore API

The `StateStore` interface provides the core memory operations:

```java
import com.ghatana.core.state.StateStore;
import io.activej.promise.Promise;

public interface StateStore {
    /**
     * Store a value with optional TTL.
     */
    <T> Promise<Void> put(String key, T value);
    <T> Promise<Void> put(String key, T value, Duration ttl);

    /**
     * Retrieve a value by key.
     */
    <T> Promise<Optional<T>> get(String key, Class<T> type);

    /**
     * Delete a value.
     */
    Promise<Boolean> delete(String key);

    /**
     * Check if key exists.
     */
    Promise<Boolean> exists(String key);

    /**
     * List keys by pattern.
     */
    Promise<List<String>> keys(String pattern);

    /**
     * Atomic increment.
     */
    Promise<Long> increment(String key);
    Promise<Long> increment(String key, long delta);
}
```

#### Basic Usage

```java
// Store and retrieve values
stateStore.put("agent.123.context", new TaskContext(taskId, params));
Optional<TaskContext> ctx = stateStore.get("agent.123.context", TaskContext.class).getResult();

// With TTL
stateStore.put("session.456.token", accessToken, Duration.ofHours(1));

// Pattern search
List<String> agentKeys = stateStore.keys("agent.*").getResult();

// Atomic counter
long count = stateStore.increment("metrics.requests.count").getResult();
```

### HybridStateStore

The `HybridStateStore` combines local and central storage for optimal performance:

```java
import com.ghatana.virtualorg.memory.state.HybridStateStore;
import com.ghatana.virtualorg.memory.config.HybridStateStoreConfig;

// Configuration
HybridStateStoreConfig config = HybridStateStoreConfig.builder()
    .localStore(LocalStoreConfig.builder()
        .type(LocalStoreType.ROCKSDB)
        .path("/data/agent-memory")
        .maxSizeMb(1024)
        .build())
    .centralStore(CentralStoreConfig.builder()
        .type(CentralStoreType.REDIS)
        .connectionString("redis://localhost:6379")
        .poolSize(10)
        .build())
    .sync(SyncConfig.builder()
        .mode(SyncMode.WRITE_THROUGH_READ_LOCAL)
        .conflictResolution(ConflictResolution.LATEST_WINS)
        .batchSize(100)
        .intervalMs(5000)
        .build())
    .build();

// Create store
HybridStateStore store = new HybridStateStore(config);

// Use like any StateStore
store.put("key", value);
Optional<MyType> result = store.get("key", MyType.class).getResult();
```

#### Sync Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `WRITE_THROUGH_READ_LOCAL` | Write to both, read from local | Low-latency reads |
| `WRITE_LOCAL_SYNC_ASYNC` | Write local, sync async | High write throughput |
| `WRITE_CENTRAL_CACHE_LOCAL` | Write central, cache locally | Strong consistency |
| `LOCAL_ONLY` | No central sync | Development/testing |

### Memory Configuration

Configure memory per memory type:

```java
import com.ghatana.virtualorg.memory.config.HybridStateStoreConfig;

// In agent configuration
Map<MemoryType, MemoryConfig> memoryConfigs = Map.of(
    MemoryType.WORKING, MemoryConfig.builder()
        .ttl(Duration.ofMinutes(5))
        .maxEntries(1000)
        .compressionEnabled(false)
        .build(),
    MemoryType.LONG_TERM, MemoryConfig.builder()
        .ttl(Duration.ofDays(7))
        .maxEntries(100000)
        .compressionEnabled(true)
        .evictionPolicy(EvictionPolicy.LRU)
        .build()
);
```

---

## Knowledge/RAG System

### Knowledge Sources

The system supports multiple knowledge source types:

```java
import com.ghatana.virtualorg.core.config.AgentKnowledgeConfig;

AgentKnowledgeConfig knowledge = AgentKnowledgeConfig.builder()
    // Vector store for semantic search
    .vectorStore(VectorStoreKnowledgeSource.builder()
        .name("product-docs")
        .provider("pgvector")
        .collection("documentation")
        .dimensions(1536)
        .build())
    
    // Document store for structured docs
    .documentStore(DocumentStoreKnowledgeSource.builder()
        .name("policy-docs")
        .provider("elasticsearch")
        .index("company-policies")
        .build())
    
    // SQL database for structured data
    .sqlStore(SQLKnowledgeSource.builder()
        .name("customer-db")
        .connectionString("jdbc:postgresql://...")
        .allowedTables(List.of("customers", "orders", "products"))
        .maxResults(100)
        .build())
    
    // External API
    .apiSource(APIKnowledgeSource.builder()
        .name("weather-api")
        .baseUrl("https://api.weather.com")
        .authentication(ApiAuth.bearer("token"))
        .rateLimitPerMinute(60)
        .build())
    .build();
```

### RAG Pipeline

The RAG (Retrieval-Augmented Generation) pipeline:

```java
import com.ghatana.virtualorg.knowledge.RAGPipeline;
import com.ghatana.virtualorg.knowledge.RAGConfig;

// Configure RAG
RAGConfig ragConfig = RAGConfig.builder()
    .chunkingStrategy(ChunkingStrategy.SEMANTIC)
    .chunkSize(512)
    .chunkOverlap(50)
    .topK(10)
    .similarityThreshold(0.7)
    .rerankEnabled(true)
    .rerankModel("cross-encoder/ms-marco")
    .maxContextTokens(4096)
    .build();

RAGPipeline rag = new RAGPipeline(vectorStore, embeddingService, ragConfig);

// Ingest documents
rag.ingest(documents).getResult();

// Query with context building
RAGResult result = rag.query("What is the return policy?").getResult();
String context = result.getContext();
List<Document> sources = result.getSources();
```

#### Document Ingestion

```java
// Single document
Document doc = Document.builder()
    .id("doc-123")
    .content("Document content here...")
    .metadata(Map.of("source", "manual", "category", "policy"))
    .build();

rag.ingest(doc);

// Batch ingestion
List<Document> batch = loadDocuments("./docs/");
rag.ingestBatch(batch, BatchOptions.builder()
    .parallelism(4)
    .chunkSize(100)
    .build());

// Incremental update
rag.update("doc-123", updatedContent);
```

#### Semantic Search

```java
// Basic search
List<SearchResult> results = rag.search("customer refund process")
    .topK(5)
    .execute()
    .getResult();

// Filtered search
List<SearchResult> policyResults = rag.search("vacation policy")
    .topK(10)
    .filter(Filters.eq("category", "hr"))
    .filter(Filters.gte("updated", "2024-01-01"))
    .execute()
    .getResult();

// Hybrid search (semantic + keyword)
List<SearchResult> hybridResults = rag.hybridSearch("Q4 sales targets")
    .semanticWeight(0.7)
    .keywordWeight(0.3)
    .execute()
    .getResult();
```

### Embedding Configuration

Configure embedding models:

```java
EmbeddingModelConfig embeddingConfig = EmbeddingModelConfig.builder()
    .provider(EmbeddingProvider.OPENAI)
    .model("text-embedding-3-small")
    .dimensions(1536)
    .maxTokens(8191)
    .batchSize(100)
    .rateLimitPerMinute(3000)
    .caching(EmbeddingCacheConfig.builder()
        .enabled(true)
        .ttl(Duration.ofDays(7))
        .maxSize(100000)
        .build())
    .build();
```

---

## Integration Patterns

### Pattern 1: Agent with Memory

```java
public class MemoryAwareAgent extends BaseAgent {
    private final AgentMemoryStateStore memory;
    private final RAGPipeline rag;

    @Override
    public Promise<AgentResult> execute(AgentContext ctx) {
        // Load working context
        Optional<WorkingContext> working = memory.get(
            workingKey(ctx), WorkingContext.class, MemoryType.WORKING
        ).getResult();

        // Retrieve relevant knowledge
        RAGResult knowledge = rag.query(ctx.getQuery())
            .withContext(working.map(WorkingContext::summary).orElse(""))
            .execute()
            .getResult();

        // Process with context
        AgentResult result = processWithKnowledge(ctx, knowledge);

        // Store results in memory
        memory.put(workingKey(ctx), result.toContext(), MemoryType.SHORT_TERM);

        // Learn from interaction (long-term)
        if (result.isSuccessful()) {
            memory.put(episodicKey(ctx), result.toEpisode(), MemoryType.EPISODIC);
        }

        return Promise.of(result);
    }
}
```

### Pattern 2: Conversational Memory

```java
public class ConversationalAgent extends BaseAgent {
    
    @Override
    public Promise<AgentResult> execute(AgentContext ctx) {
        String sessionId = ctx.getSessionId();
        
        // Load conversation history
        List<Message> history = memory.getList(
            "conversation." + sessionId, 
            Message.class, 
            MemoryType.SHORT_TERM
        ).getResult();

        // Build prompt with history
        String prompt = buildPromptWithHistory(ctx.getQuery(), history);

        // Get response
        String response = llm.complete(prompt).getResult();

        // Update history
        history.add(new Message("user", ctx.getQuery()));
        history.add(new Message("assistant", response));
        memory.put("conversation." + sessionId, history, MemoryType.SHORT_TERM);

        return Promise.of(AgentResult.success(response));
    }
}
```

### Pattern 3: Multi-Source RAG

```java
public class MultiSourceRAGAgent extends BaseAgent {
    private final List<KnowledgeSource> sources;

    @Override
    public Promise<AgentResult> execute(AgentContext ctx) {
        // Query all sources in parallel
        List<Promise<List<SearchResult>>> searches = sources.stream()
            .map(source -> source.search(ctx.getQuery()))
            .toList();

        // Merge and rerank results
        return Promise.all(searches)
            .map(results -> {
                List<SearchResult> merged = mergeResults(results);
                List<SearchResult> reranked = rerank(merged, ctx.getQuery());
                return buildContext(reranked);
            })
            .then(context -> generateResponse(ctx, context));
    }
}
```

---

## Best Practices

### Memory Management

1. **Use appropriate TTLs**: Don't store transient data in long-term memory
2. **Namespace keys**: Use `agent.{id}.{type}.{key}` format
3. **Batch operations**: Group related writes for efficiency
4. **Monitor size**: Set up alerts for memory usage

```java
// Good: Namespaced, typed key
memory.put("agent.123.task.current", taskContext, MemoryType.WORKING);

// Bad: Generic key, no namespace
memory.put("task", taskContext);  // Avoid!
```

### RAG Optimization

1. **Chunk appropriately**: Match chunk size to your query patterns
2. **Use metadata**: Filter by category, date, source for precision
3. **Rerank for quality**: Enable reranking for better results
4. **Cache embeddings**: Avoid redundant embedding computations

```java
// Good: Specific query with filters
rag.search("employee benefits enrollment")
    .filter(Filters.eq("category", "hr"))
    .filter(Filters.gte("year", 2024))
    .topK(5)
    .execute();

// Less optimal: Broad query, no filters
rag.search("benefits").execute();  // Too broad
```

### Error Handling

```java
// Handle memory failures gracefully
Promise<TaskContext> contextPromise = memory.get(key, TaskContext.class)
    .then(opt -> opt.orElse(TaskContext.empty()))
    .catch_(ex -> {
        logger.warn("Memory read failed, using empty context", ex);
        return Promise.of(TaskContext.empty());
    });

// Fallback for RAG failures
Promise<RAGResult> ragPromise = rag.query(query)
    .catch_(ex -> {
        logger.warn("RAG query failed, using empty context", ex);
        return Promise.of(RAGResult.empty());
    });
```

---

## Performance Tuning

### Memory Performance

| Setting | Recommendation | Impact |
|---------|----------------|--------|
| Local cache size | 1-4 GB | Higher = better hit rate |
| Sync batch size | 100-500 | Higher = better throughput |
| Sync interval | 1-10 seconds | Lower = more consistency |
| Compression | Enable for long-term | Reduces storage, slight CPU cost |

### RAG Performance

| Setting | Recommendation | Impact |
|---------|----------------|--------|
| Chunk size | 256-1024 tokens | Smaller = more precision |
| Chunk overlap | 10-20% of chunk | More = better context |
| Top-K | 5-20 | Higher = more context |
| Similarity threshold | 0.6-0.8 | Higher = more relevant |
| Reranking | Enable | Better quality, +50ms latency |

### Benchmarks

Target performance (P95):

| Operation | Target | Typical |
|-----------|--------|---------|
| Memory read (local) | <10ms | 2-5ms |
| Memory read (central) | <50ms | 15-30ms |
| Memory write | <20ms | 5-10ms |
| RAG query (simple) | <500ms | 200-400ms |
| RAG query (reranked) | <1s | 400-800ms |
| Document ingestion | <100ms/doc | 30-60ms/doc |

---

## Troubleshooting

### Common Issues

#### Memory Not Persisting

```java
// Check sync mode
if (config.getSyncMode() == SyncMode.LOCAL_ONLY) {
    // Data won't persist across restarts
    config = config.toBuilder()
        .syncMode(SyncMode.WRITE_THROUGH_READ_LOCAL)
        .build();
}
```

#### RAG Returns Poor Results

```java
// Check similarity threshold
if (results.isEmpty()) {
    // Try lowering threshold
    results = rag.search(query)
        .similarityThreshold(0.5)  // Lower from default 0.7
        .execute()
        .getResult();
}

// Check if documents are indexed
long docCount = rag.getDocumentCount().getResult();
if (docCount == 0) {
    // Re-ingest documents
    rag.ingestBatch(documents);
}
```

#### High Memory Latency

```java
// Check local cache hit rate
CacheStats stats = hybridStore.getCacheStats();
if (stats.hitRate() < 0.8) {
    // Increase cache size or adjust access patterns
    config = config.toBuilder()
        .localStore(localConfig.toBuilder()
            .maxSizeMb(2048)  // Increase from 1024
            .build())
        .build();
}
```

### Monitoring

Key metrics to monitor:

```java
// Memory metrics
Metrics.gauge("memory.local.size_bytes", localStore::getSizeBytes);
Metrics.gauge("memory.local.hit_rate", () -> cacheStats.hitRate());
Metrics.counter("memory.operations.total", "operation", "read|write|delete");
Metrics.timer("memory.operation.latency", "operation", "read|write");

// RAG metrics
Metrics.counter("rag.queries.total");
Metrics.timer("rag.query.latency");
Metrics.histogram("rag.results.count");
Metrics.gauge("rag.index.document_count", rag::getDocumentCount);
```

---

## API Reference

### Full API Documentation

- [StateStore API](./api/StateStore.md)
- [HybridStateStore API](./api/HybridStateStore.md)
- [RAGPipeline API](./api/RAGPipeline.md)
- [VectorStore API](./api/VectorStore.md)
- [EmbeddingService API](./api/EmbeddingService.md)

### Configuration Reference

- [HybridStateStoreConfig](./config/HybridStateStoreConfig.md)
- [AgentKnowledgeConfig](./config/AgentKnowledgeConfig.md)
- [RAGConfig](./config/RAGConfig.md)

---

## Changelog

### v1.0.0 (2025-01-15)

- Initial release
- HybridStateStore with RocksDB + Redis support
- RAG pipeline with semantic search
- 6 memory types with configurable TTLs
- Multi-source knowledge integration
