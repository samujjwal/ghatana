# Search Architecture Documentation

**Last Updated:** 2026-04-17  
**Version:** 1.0

---

## Overview

TutorPutor uses a vector-based semantic search approach for advanced search capabilities. This provides superior semantic understanding compared to traditional full-text search engines like Elasticsearch.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Search Layer                               │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │  Search Service   │  │  Semantic Search │                │
│  │  (keyword + filters)│  │  (embeddings)    │                │
│  └────────┬─────────┘  └────────┬─────────┘                │
│           │                     │                             │
│           └──────────┬──────────┘                             │
│                      ▼                                        │
│           ┌──────────────────┐                               │
│           │  Hybrid Search    │                               │
│           │  (keyword + semantic)                           │
│           └────────┬─────────┘                               │
│                    │                                        │
│                    ▼                                        │
│           ┌──────────────────┐                               │
│           │  Vector Store    │                               │
│           │  (embeddings)    │                               │
│           └──────────────────┘                               │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Search Approaches

### 1. Keyword Search
**Location:** `services/tutorputor-platform/src/modules/search/service.ts`

**Implementation:**
- Full-text search on content
- Metadata filters (domain, difficulty, type)
- Pagination
- Relevance scoring

**Use Cases:**
- Exact phrase matching
- Boolean operators
- Specific field search

---

### 2. Semantic Search
**Location:** `services/tutorputor-platform/src/modules/content/semantic/semantic-search-service.ts`

**Implementation:**
- Vector embeddings for content
- Cosine similarity for relevance
- Natural language query support
- Multilingual capabilities

**Use Cases:**
- Concept-based search
- Natural language queries
- Content recommendations
- Similar content discovery

---

### 3. Hybrid Search
**Location:** `services/tutorputor-platform/src/modules/content/semantic/hybrid-search-service.ts`

**Implementation:**
- Combines keyword and semantic search
- Weighted relevance scoring
- Best of both approaches
- Configurable weights

**Use Cases:**
- General search queries
- Mixed exact and semantic needs
- Optimized relevance

---

## Content Chunking

**Location:** `services/tutorputor-platform/src/modules/content/semantic/chunk-service.ts`

**Implementation:**
- Content chunking for embeddings
- Chunk size optimization
- Overlap for context preservation
- Metadata preservation

**Strategy:**
- Chunk size: 500-1000 tokens
- Overlap: 100 tokens
- Preserve headings and structure
- Include metadata in chunks

---

## Search Analytics

**Location:** `services/tutorputor-platform/src/modules/content/telemetry/`

**Implementation:**
- Search query logging
- Click-through tracking
- Result engagement metrics
- Search performance metrics

**Metrics:**
- Query frequency
- Result click-through rate
- Zero-result queries
- Search latency

---

## Search Schema

### Vector Embeddings

**Embedding Model:**
- Model: OpenAI text-embedding-3-small or equivalent
- Dimensions: 1536
- Normalization: L2 normalization

**Indexing:**
- Vector store for embeddings
- Metadata index for filters
- Hybrid index for keyword + semantic

---

## Faceted Search

**Filters:**
- Domain (Physics, Chemistry, Biology, Business)
- Difficulty (Intro, Intermediate, Advanced)
- Content Type (Lesson, Simulation, Assessment)
- Duration
- Language

**Implementation:**
- Metadata-based filtering
- Facet count aggregation
- Multi-select support

---

## Future Enhancements

- Query expansion
- Search suggestions
- Recent searches
- Personalized search results
- Search result highlighting
- Voice search
- Image search

---

## Performance

**Metrics:**
- Search latency: <100ms (95th percentile)
- Indexing time: <1 second per document
- Query throughput: >100 queries/second
- Cache hit ratio: >80%

**Optimizations:**
- Vector index caching
- Query result caching
- Batch embedding generation
- Parallel search execution

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
