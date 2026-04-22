# YAPPC Artifact Compiler — Implementation Plan

> **Package**: `@yappc/artifact-compiler` (TypeScript) + `yappc-artifact-analyzer` (Java)
> **Location**: `products/yappc/frontend/libs/yappc-artifact-compiler` + `products/yappc/services/artifact-analyzer`
> **Principle**: TypeScript handles lightweight extraction and orchestration; Java handles computation-heavy graph, merge, and indexing workloads.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           YAPPC Artifact Compiler                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  TypeScript Layer (Lightweight)          │  Java Layer (Compute-Heavy)      │
│  ─────────────────────────────────────   │  ───────────────────────────────   │
│  Scanner, Extractors, Orchestrator       │  Graph Engine, Merge, Storage    │
│  @yappc/artifact-compiler                │  yappc-artifact-analyzer         │
│                                          │                                   │
│  • File inventory + classification       │  • Tree-sitter indexing           │
│  • TSX/Prisma/CSF extraction             │  • OpenRewrite LST (Java)         │
│  • Residual island tagging               │  • Graph algorithms (cycles,      │
│  • Synthesis orchestration               │    centrality, communities)       │
│  • Merge result application              │  • Three-way semantic merge       │
│  • API client to Java service            │  • PostgreSQL JSONB storage       │
│                                          │  • SQL canonicalization           │
│                                          │  • CI/CD workflow parsing         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Bridge Contracts (Protobuf/JSON)                     │
│  ArtifactGraph, SemanticProductModel, ExtractionResult, MergeRequest, etc.   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 0 — Foundation (Completed)

### Task 0.1: TypeScript Package Scaffold
- **Location**: `products/yappc/frontend/libs/yappc-artifact-compiler/`
- **Deliverables**:
  - `package.json` with `@yappc/artifact-compiler` name, zod + nanoid deps
  - `tsconfig.json` with strict mode, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`
  - `vitest.config.ts` with `pool: 'forks'` and `deps.interopDefault: true`
- **Status**: ✅ Complete
- **Verification**: `tsc --noEmit` passes, `pnpm install` succeeds

### Task 0.2: Core Schema Definitions
- **Location**: `src/{inventory,graph,model,provenance,residual,merge,synthesis,extractors}/types.ts`
- **Deliverables**:
  - `ArtifactRecord` with SHA-256 checksum, language/framework/kind classification
  - `ArtifactGraph` with typed `GraphNode` and `GraphEdge`
  - `SemanticProductModel` union of `ComponentModel`, `PageModel`, `DataModel`, `ApiModel`, `StateModel`, `TokenModel`
  - `Provenance` and `ConfidenceScore` schemas
  - `ResidualIsland` with `RegenerationStrategy`
  - `MergeResult` and `ConflictResolution` types
  - `ExtractionResult` and `ExtractionContext`
- **Status**: ✅ Complete
- **Verification**: Zod schemas parse without errors, TypeScript strict mode satisfied

### Task 0.3: Inventory Scanner
- **Location**: `src/inventory/scanner.ts`
- **Deliverables**:
  - `scanRepository(config)` — recursive file traversal with `.gitignore` respect
  - `classifyArtifact(path, content, language, framework)` — deterministic kind detection
  - `detectLanguage(ext)`, `detectFramework(content, ext, dir)` — heuristic detection
  - Import/export summary via `detective-es6`
- **Status**: ✅ Complete
- **Verification**: Scans 111 files in test repo, classifications accurate

### Task 0.4: Lightweight Extractors
- **Location**: `src/extractors/{typescript,storybook,prisma}/`
- **Deliverables**:
  - **Component Extractor** (`component-extractor.ts`): TS Compiler API — props, slots, events, accessibility, JSX usage
  - **Page Extractor** (`page-extractor.ts`): Route derivation, layout hierarchy, auth guard detection
  - **State Store Extractor** (`state-extractor.ts`): Redux Toolkit, Zustand, Jotai, React Context patterns
  - **Storybook CSF Extractor** (`csf-extractor.ts`): CSF meta/story extraction, variant mapping
  - **Prisma Schema Extractor** (`schema-extractor.ts`): Model, field, relation, index extraction
- **Status**: ✅ Complete
- **Verification**: All runtime smoke tests pass (component: 1, prisma: 2 models with relations)

### Task 0.5: Barrel Exports & README
- **Location**: `src/index.ts`, `README.md`
- **Deliverables**:
  - Subpath exports for `inventory`, `graph`, `model`, `extractors`, `provenance`, `residual`, `merge`, `synthesis`
  - Architecture documentation, usage examples, API reference
- **Status**: ✅ Complete

---

## Phase 1 — Bridge Contracts & Communication Layer

### Task 1.1: Define Protobuf/JSON Contract Schemas
- **Location**: `products/yappc/platform/contracts/artifact-analyzer/` or `products/yappc/services/artifact-analyzer/src/main/proto/`
- **Owner**: Shared (TS types + Java classes)
- **Deliverables**:
  - `artifact_graph.proto` — `GraphNode`, `GraphEdge`, `ArtifactGraph` messages
  - `semantic_model.proto` — `ComponentModel`, `PageModel`, `DataModel`, `ApiModel`, `StateModel`, `TokenModel` messages
  - `extraction.proto` — `ExtractionRequest`, `ExtractionResult`, `ExtractionError`
  - `merge.proto` — `MergeRequest`, `MergeResult`, `Conflict`, `ConflictResolution`
  - `query.proto` — `GraphQuery`, `QueryResult`, `PathRequest`
  - `residual.proto` — `ResidualAnalysisRequest`, `ResidualAnalysisResult`
- **Acceptance Criteria**:
  - Java classes generated via protobuf-gradle-plugin
  - TypeScript types generated via protobuf-ts or ts-proto
  - Round-trip serialization/deserialization tests pass for all message types
- **Dependencies**: None (greenfield)
- **Estimated Effort**: 2-3 days

### Task 1.2: Java Service Skeleton (Spring Boot / ActiveJ)
- **Location**: `products/yappc/services/artifact-analyzer/`
- **Owner**: Java
- **Deliverables**:
  - Gradle module with `build.gradle.kts`
  - Java 21 toolchain
  - HTTP server (ActiveJ or Spring Boot WebFlux) with health/readiness endpoints
  - gRPC service definitions matching protobuf contracts
  - Structured logging (SLF4J + Logback/Log4j2)
  - OpenTelemetry tracing integration
- **Acceptance Criteria**:
  - `/health` returns 200 OK
  - `/ready` returns 200 after all dependencies initialized
  - gRPC reflection endpoint available
  - Service starts in <3 seconds
- **Dependencies**: Task 1.1
- **Estimated Effort**: 2-3 days

### Task 1.3: TypeScript API Client
- **Location**: `src/bridge/client.ts`
- **Owner**: TypeScript
- **Deliverables**:
  - `ArtifactAnalyzerClient` class with HTTP and gRPC transports
  - Configurable base URL, timeout, retry policy (exponential backoff)
  - Request/response interceptors for authentication (JWT/API key)
  - Circuit breaker for resilience
  - Type-safe wrappers around protobuf-generated TypeScript types
- **Acceptance Criteria**:
  - Client can connect to local Java service
  - Timeout and retry policies configurable via environment variables
  - Circuit breaker opens after 5 consecutive failures, closes after 30s
- **Dependencies**: Task 1.1, Task 1.2
- **Estimated Effort**: 1-2 days

### Task 1.4: Integration Tests for Bridge
- **Location**: `src/bridge/client.test.ts`, `services/artifact-analyzer/src/test/`
- **Owner**: Both
- **Deliverables**:
  - Docker Compose setup: Java service + PostgreSQL (for later phases)
  - End-to-end test: TS client sends `ArtifactGraph`, Java service responds with `QueryResult`
  - Contract tests ensuring protobuf schema compatibility
- **Acceptance Criteria**:
  - `docker compose up` brings up full stack
  - Integration test passes: TS → Java → TS round-trip
- **Dependencies**: Task 1.2, Task 1.3
- **Estimated Effort**: 1-2 days

---

## Phase 2 — Java Graph Computation Engine

### Task 2.1: Graph Data Structure & Storage
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/graph/`
- **Owner**: Java
- **Deliverables**:
  - `ArtifactGraph` domain model (immutable, using records where possible)
  - In-memory adjacency list representation with indexed lookups
  - `GraphNode` with metadata map, `GraphEdge` with relationship type and weight
  - Import from protobuf `ArtifactGraph` message
- **Acceptance Criteria**:
  - Graph with 10,000 nodes and 50,000 edges loads in <1s
  - Node lookup by ID is O(1)
  - Edge traversal is O(degree)
- **Dependencies**: Task 1.1
- **Estimated Effort**: 2 days

### Task 2.2: Graph Algorithms Library
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/graph/algorithms/`
- **Owner**: Java
- **Deliverables**:
  - **Cycle Detection**: Tarjan's strongly connected components (SCC)
  - **Centrality**: Betweenness centrality (Brandes' algorithm), PageRank
  - **Community Detection**: Louvain modularity optimization
  - **Path Finding**: Shortest path (Dijkstra), all-pairs shortest path (Floyd-Warshall for small graphs)
  - **Dependency Analysis**: Topological sort, transitive closure for import graphs
- **Acceptance Criteria**:
  - Cycle detection on 1,000-node graph completes in <100ms
  - PageRank converges in <50 iterations for 10,000-node graph
  - Algorithm results exposed via gRPC/HTTP endpoints
- **Dependencies**: Task 2.1
- **Estimated Effort**: 5-7 days
- **Libraries**: JGraphT, Apache Commons Math, or custom implementations

### Task 2.3: Graph Query Engine
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/query/`
- **Owner**: Java
- **Deliverables**:
  - GraphQL-like query DSL for ArtifactGraph traversal
  - Cypher-like pattern matching for node/edge queries
  - Pagination and streaming for large result sets
  - Query caching with TTL
- **Acceptance Criteria**:
  - Query: "find all components imported by pages in /admin" returns correct subgraph
  - Complex pattern query completes in <200ms on 5,000-node graph
  - Cache hit rate >80% for repeated queries
- **Dependencies**: Task 2.1, Task 2.2
- **Estimated Effort**: 4-5 days

### Task 2.4: Graph Persistence Layer
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/persistence/`
- **Owner**: Java
- **Deliverables**:
  - Neo4j or PostgreSQL JSONB adapter for graph persistence
  - Graph serialization to JSONB with GIN indexes on node properties
  - Incremental updates (delta sync) from TS scanner
  - Versioned snapshots of graph state
- **Acceptance Criteria**:
  - Full graph persistence in <5s for 50,000 edges
  - Incremental update (100 new edges) in <200ms
  - Version rollback to any previous snapshot
- **Dependencies**: Task 2.1
- **Estimated Effort**: 3-4 days

---

## Phase 3 — Synthesis Engine (TypeScript Orchestrator + Java Worker)

### Task 3.1: TypeScript Synthesis Orchestrator
- **Location**: `src/synthesis/engine.ts`
- **Owner**: TypeScript
- **Deliverables**:
  - `SynthesisPipeline` class that coordinates extraction → graph building → semantic model generation
  - Calls Java Graph Engine for heavy graph computation (centrality, communities, cycles)
  - Receives `GraphAnalysisResult` from Java, applies heuristics to generate `SemanticModelElement`s
  - Confidence scoring per element based on extraction source and graph position
- **Acceptance Criteria**:
  - Pipeline processes 100 files end-to-end in <10s (with Java delegation)
  - Each generated element has provenance traceable to source file + line
  - Confidence scores follow defined thresholds (HIGH >0.8, MEDIUM 0.5-0.8, LOW <0.5)
- **Dependencies**: Task 1.3, Task 2.2
- **Estimated Effort**: 3-4 days

### Task 3.2: Java Synthesis Worker
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/synthesis/`
- **Owner**: Java
- **Deliverables**:
  - gRPC endpoint: `SynthesizeSemanticModel(ArtifactGraph) → SemanticProductModel`
  - Graph-based heuristics for component/page/state detection
  - Community-based clustering for feature/module boundaries
  - Centrality-based importance scoring for elements
- **Acceptance Criteria**:
  - Correctly identifies 95%+ of components from TSX extraction + graph context
  - Feature boundaries (community clusters) align with directory structure >80% of time
  - Importance scoring ranks entry-point components highest
- **Dependencies**: Task 2.2, Task 3.1
- **Estimated Effort**: 4-5 days

### Task 3.3: Residual Island Analysis Delegation
- **Location**: `src/residual/analyzer.ts` (TS), `services/artifact-analyzer/src/main/java/com/ghatana/artifact/residual/` (Java)
- **Owner**: Both
- **Deliverables**:
  - TS: Tag unextractable blocks (inline styles, dynamic imports, `eval`, regex constructions)
  - TS: Send `ResidualAnalysisRequest` to Java with code snippet + context
  - Java: Deep AST analysis for recovery strategies (deobfuscation, pattern matching, manual flag)
  - Java: Return `ResidualAnalysisResult` with `RegenerationStrategy` recommendation
- **Acceptance Criteria**:
  - 90%+ of inline styles detected and flagged
  - Dynamic import patterns resolved to static imports where possible
  - Manual flag set for true runtime-only constructs (e.g., `eval`, `Function constructor`)
- **Dependencies**: Task 1.3
- **Estimated Effort**: 3-4 days

---

## Phase 4 — Merge Engine (Java Core + TS Client)

### Task 4.1: Java Semantic Merge Engine
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/merge/`
- **Owner**: Java
- **Deliverables**:
  - Three-way merge algorithm for `SemanticProductModel` trees
  - Conflict detection: simultaneous edits, type mismatches, deletion conflicts
  - Conflict resolution strategies: auto-resolve (same value), manual-review (different values), last-write-wins (configurable)
  - Merge provenance: track which branch contributed each field
  - gRPC endpoint: `MergeSemanticModels(base, left, right) → MergeResult`
- **Acceptance Criteria**:
  - Auto-resolves >70% of non-conflicting changes
  - Detects 100% of true conflicts (no silent overwrites)
  - Merge result is associative and commutative where possible
  - Large model merge (1,000 elements) completes in <2s
- **Dependencies**: Task 1.1
- **Estimated Effort**: 5-7 days
- **Libraries**: Custom tree-diff + merge algorithm (no suitable open-source library for semantic product models)

### Task 4.2: TypeScript Merge Client
- **Location**: `src/merge/client.ts`
- **Owner**: TypeScript
- **Deliverables**:
  - `MergeClient` class that calls Java merge endpoint
  - Displays merge results with conflict visualization
  - Applies merged model to local frontend state
  - Queues manual-review conflicts for user intervention
- **Acceptance Criteria**:
  - Auto-resolved changes applied immediately
  - Conflicts presented in structured diff format
  - User can accept/resolve conflicts via API
- **Dependencies**: Task 4.1, Task 1.3
- **Estimated Effort**: 2-3 days

### Task 4.3: Versioning & History
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/history/`
- **Owner**: Java
- **Deliverables**:
  - Git-like versioning for `SemanticProductModel` (commits, branches, tags)
  - Diff generation between any two versions
  - Blame/annotation for each model element
  - Pruning old versions with retention policy
- **Acceptance Criteria**:
  - Diff between versions shows added/removed/modified elements
  - Blame traces each field to originating commit
  - Old versions pruned after configurable retention (default: 90 days)
- **Dependencies**: Task 4.1, Task 2.4
- **Estimated Effort**: 3-4 days

---

## Phase 5 — Language-Specific Heavy Extractors (Java)

### Task 5.1: Tree-sitter Structural Indexing Service
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/parser/treesitter/`
- **Owner**: Java
- **Deliverables**:
  - JNI or JNA bridge to Tree-sitter C library (version 0.19.0)
  - Incremental parsing: only re-parse changed files
  - Multi-language support: TypeScript, Python, Rust, Go, Java, C++
  - Parse tree to `ArtifactGraph` node mapping
  - Query engine using Tree-sitter query DSL
- **Acceptance Criteria**:
  - Parse 1,000 TypeScript files in <10s (cold start)
  - Incremental re-parse of single file in <50ms
  - Query: "find all function declarations named `useAuth`" returns in <100ms
  - JNI bridge stable (no memory leaks in 24h stress test)
- **Dependencies**: Task 2.1
- **Estimated Effort**: 7-10 days
- **Libraries**: tree-sitter JNI bindings (custom), JNR-FFI, or Panama Foreign Function API

### Task 5.2: OpenRewrite Lossless Semantic Tree Integration
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/parser/openrewrite/`
- **Owner**: Java
- **Deliverables**:
  - OpenRewrite LST parsing for Java source files
  - Type attribution via Java compiler (javac) integration
  - Method/class dependency extraction from LST
  - Mapping OpenRewrite types to `SemanticProductModel` `DomainServiceCode` elements
- **Acceptance Criteria**:
  - Correctly extracts all public methods, fields, and inner classes
  - Type attribution resolves 95%+ of imported types
  - Dependency graph between Java classes accurate
  - Processing 10,000 Java files in <30s
- **Dependencies**: Task 2.1
- **Estimated Effort**: 5-7 days
- **Libraries**: OpenRewrite (rewrite-java), rewrite-maven, rewrite-gradle

### Task 5.3: SQL Canonicalization & Schema Diff
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/parser/sql/`
- **Owner**: Java
- **Deliverables**:
  - SQL parser supporting PostgreSQL, MySQL, SQLite dialects
  - Canonicalization: normalize formatting, resolve aliases, standardize types
  - Semantic diff: compare two schemas, generate migration script
  - Mapping to `SemanticProductModel` `DataModel` elements
- **Acceptance Criteria**:
  - Correctly parses 99%+ of SQL in existing migrations
  - Diff detects added/removed/modified tables, columns, indexes, constraints
  - Generated migration script is syntactically valid
  - Processing 1,000 migration files in <5s
- **Dependencies**: None (can run standalone)
- **Estimated Effort**: 5-7 days
- **Libraries**: jOOQ (parser), or Apache Calcite (SQL parser + optimizer)

---

## Phase 6 — Persistent Storage & Query Layer

### Task 6.1: PostgreSQL JSONB Storage Service
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/storage/`
- **Owner**: Java
- **Deliverables**:
  - Spring Data JDBC or jOOQ repository layer
  - `SemanticProductModel` JSONB serialization/deserialization
  - GIN indexes on frequently queried fields (name, kind, framework)
  - Tenant-scoped tables (for multi-tenancy)
  - Optimistic locking for concurrent edits
- **Acceptance Criteria**:
  - Insert 1,000 model elements in <1s
  - Query by name with GIN index in <10ms
  - Concurrent edits handled without lost updates
  - JSONB size <10MB for typical product model
- **Dependencies**: Task 2.4
- **Estimated Effort**: 3-4 days

### Task 6.2: Caching Layer
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/cache/`
- **Owner**: Java
- **Deliverables**:
  - Caffeine in-memory cache for hot queries
  - Redis cluster for distributed caching (multi-instance deployment)
  - Cache invalidation on model updates
  - Cache warming on service startup
- **Acceptance Criteria**:
  - Cache hit rate >90% for repeated queries
  - P99 latency <5ms for cached queries
  - Cache invalidated within 100ms of model update
- **Dependencies**: Task 6.1
- **Estimated Effort**: 2-3 days

---

## Phase 7 — Cross-Cutting Concerns

### Task 7.1: Observability & Metrics
- **Location**: Both layers
- **Owner**: Both
- **Deliverables**:
  - Java: Micrometer metrics (extraction time, merge conflicts, graph size)
  - Java: OpenTelemetry traces (end-to-end pipeline tracing)
  - TS: Structured logging with correlation IDs
  - TS: Client-side metrics (request latency, error rate, circuit breaker state)
  - Grafana dashboards for pipeline health
- **Acceptance Criteria**:
  - All gRPC/HTTP calls traced with correlation ID
  - Metrics exported in Prometheus format
  - Alert on extraction failure rate >1%
- **Dependencies**: Task 1.2
- **Estimated Effort**: 2-3 days

### Task 7.2: Security & Privacy Validation
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/security/`
- **Owner**: Java
- **Deliverables**:
  - Secret detection in code (API keys, passwords, tokens) using regex + entropy analysis
  - PII detection in model elements (names, emails, phone numbers)
  - Data classification tags (public, internal, confidential, restricted)
  - Automated masking/quarantining of detected secrets
- **Acceptance Criteria**:
  - Detects 95%+ of hardcoded secrets (GitLeaks-like accuracy)
  - False positive rate <5%
  - PII detected in model fields with appropriate classification
- **Dependencies**: Task 3.2
- **Estimated Effort**: 3-4 days

### Task 7.3: CI/CD Workflow Extraction
- **Location**: `services/artifact-analyzer/src/main/java/com/ghatana/artifact/parser/cicd/`
- **Owner**: Java
- **Deliverables**:
  - GitHub Actions workflow parser (YAML → `WorkflowModel`)
  - GitLab CI parser (YAML → `WorkflowModel`)
  - Jenkins pipeline parser (Groovy → `WorkflowModel`)
  - Dependency graph: workflow → jobs → steps → actions/commands
  - Mapping to `SemanticProductModel` `WorkflowCI_CD` elements
- **Acceptance Criteria**:
  - Parses 90%+ of workflows in target repositories
  - Extracts job dependencies, environment variables, secrets usage
  - Identifies reusable workflow references
- **Dependencies**: Task 2.1
- **Estimated Effort**: 4-5 days
- **Libraries**: SnakeYAML, Groovy compiler (for Jenkins)

---

## Phase 8 — Integration & Hardening

### Task 8.1: End-to-End Pipeline Test
- **Location**: `e2e/` directory in both packages
- **Owner**: Both
- **Deliverables**:
  - Docker Compose: PostgreSQL + Redis + Java service + mock TS client
  - E2E test: Clone repo → scan → extract → build graph → synthesize → merge → persist → query
  - Performance benchmarks for each phase
  - Chaos test: network partition, Java service restart, high load
- **Acceptance Criteria**:
  - Full pipeline completes in <60s for 1,000-file repo
  - No data loss on Java service restart
  - Circuit breaker prevents cascade failure
  - Performance degradation <20% under 10x load
- **Dependencies**: All previous tasks
- **Estimated Effort**: 5-7 days

### Task 8.2: Documentation & Developer Experience
- **Location**: `docs/`, `README.md`, API reference
- **Owner**: Both
- **Deliverables**:
  - Architecture Decision Records (ADRs) for key design choices
  - API reference (OpenAPI for HTTP, proto docs for gRPC)
  - Developer setup guide (local development with Docker)
  - Troubleshooting runbook
  - Contribution guidelines
- **Acceptance Criteria**:
  - New developer can set up full stack in <30 minutes
  - API reference covers all endpoints with examples
  - ADRs reviewed and approved by architecture team
- **Dependencies**: All previous tasks
- **Estimated Effort**: 3-4 days

---

## Resource Allocation Summary

| Phase | TypeScript Effort | Java Effort | Total |
|---|---|---|---|
| Phase 0 (Foundation) | 10 days | 0 days | 10 days ✅ |
| Phase 1 (Bridge) | 4 days | 5 days | 9 days |
| Phase 2 (Graph Engine) | 1 day | 14 days | 15 days |
| Phase 3 (Synthesis) | 6 days | 9 days | 15 days |
| Phase 4 (Merge) | 3 days | 12 days | 15 days |
| Phase 5 (Heavy Extractors) | 2 days | 19 days | 21 days |
| Phase 6 (Storage) | 1 day | 6 days | 7 days |
| Phase 7 (Cross-Cutting) | 2 days | 10 days | 12 days |
| Phase 8 (Integration) | 4 days | 8 days | 12 days |
| **Total** | **33 days** | **83 days** | **116 days** |

---

## Key Decisions & Rationale

1. **Tree-sitter in Java (not TS)**: JNI bridge is resource-heavy; Java's memory management and threading model better suited for long-lived native processes.

2. **OpenRewrite in Java (not TS)**: OpenRewrite is a Java-native ecosystem; no viable TypeScript port exists.

3. **Graph algorithms in Java (not TS)**: JGraphT, parallel streams, and optimized JVM garbage collection outperform Node.js for large graph workloads.

4. **Merge engine in Java (not TS)**: Three-way tree merge requires complex data structures and algorithms; Java's type system and concurrency primitives safer for this.

5. **PostgreSQL in Java service (not TS)**: Connection pooling, transaction management, and JSONB optimization are production-grade in Java (HikariCP, jOOQ).

6. **TS Compiler API stays in TS**: Single-file AST extraction is lightweight; no need to delegate to Java.

7. **Protobuf over REST**: Strong typing, backward compatibility, streaming support for large graphs.

---

## Risk Register

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Tree-sitter JNI bridge unstable | Medium | High | Use JNR-FFI as fallback; isolate in separate process |
| OpenRewrite LST incompatible with our model | Low | High | Build adapter layer; fallback to regex extraction |
| Java service OOM on large repos | Medium | High | Streaming processing, backpressure, pagination |
| Network latency TS ↔ Java unacceptable | Low | Medium | Co-locate services; use Unix domain sockets |
| PostgreSQL JSONB performance poor | Low | Medium | Add materialized views; shard by tenant |

---

## Next Actions

1. **Immediate**: Define protobuf contracts (Task 1.1) — unblocks all subsequent work
2. **Week 1-2**: Java service skeleton + TS client (Tasks 1.2, 1.3)
3. **Week 3-4**: Graph engine foundation (Tasks 2.1, 2.2)
4. **Week 5-6**: Synthesis pipeline (Tasks 3.1, 3.2)
5. **Week 7-8**: Merge engine (Tasks 4.1, 4.2)

**Which task should we begin implementing first?**
