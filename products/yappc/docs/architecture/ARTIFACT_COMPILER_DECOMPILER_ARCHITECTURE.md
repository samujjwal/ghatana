# Artifact Compiler/Decompiler Architecture

> **Package**: `@yappc/artifact-compiler` (TypeScript) + `products/yappc/core/knowledge-graph` (Java)
> **Location**: `products/yappc/frontend/libs/yappc-artifact-compiler` + `products/yappc/core/knowledge-graph/`
> **Principle**: TypeScript handles lightweight extraction and orchestration; Java handles computation-heavy graph, merge, and indexing workloads using the existing Knowledge Graph infrastructure.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          YAPPC Artifact Compiler                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  TypeScript Layer (products/yappc/frontend/libs/yappc-artifact-compiler)        │
│  ─────────────────────────────────────────────────────────────────────────────  │
│  • Scanner (file traversal + classification)                                    │
│  • TSX/Prisma/CSF Extractors (local AST/regex)                                  │
│  • Synthesis Orchestrator — delegates to KG HTTP API                             │
│  • Residual Island Tagger — flags for KG analysis                              │
│  • TS Client — reuses platform/typescript/api ApiClient                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Java Layer (products/yappc/core/knowledge-graph/) — EXTEND EXISTING MODULE     │
│  ─────────────────────────────────────────────────────────────────────────────  │
│  Existing:                                                                      │
│    • KnowledgeGraph, YAPPCGraphService, KGQueryService                          │
│    • KGNodeRepository, KGEdgeRepository (JDBC + Jackson persistence)             │
│    • KGConflictResolver (entity dedup/merge)                                    │
│    • KGUpdatePipeline (event → extract → resolve → embed → persist)             │
│    • EntityExtractor (AI-based text extraction)                                 │
│                                                                                 │
│  New additions within this module:                                             │
│    • ArtifactGraphMapper — maps TS extraction results to YAPPCGraphNode/Edge    │
│    • ArtifactGraphController — HTTP routes for artifact-specific operations     │
│    • JGraphTAlgorithmService — centrality, SCC, community detection             │
│    • ThreeWayMergeEngine — extends KGConflictResolver for semantic models       │
│    • Protobuf service definitions (optional) for high-volume streaming           │
│    • Flyway migrations for artifact-specific node/edge types                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## TypeScript Layer (Lightweight Extraction & Orchestration)

### Completed Foundation (Phase 0)

**Location**: `products/yappc/frontend/libs/yappc-artifact-compiler/`

**Deliverables**:
- `package.json` with `@yappc/artifact-compiler` name, zod + nanoid deps
- `tsconfig.json` with strict mode, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`
- `vitest.config.ts` with `pool: 'forks'` and `deps.interopDefault: true`
- Core schema definitions in `src/{inventory,graph,model,provenance,residual,merge,synthesis,extractors}/types.ts`:
  - `ArtifactRecord` with SHA-256 checksum, language/framework/kind classification
  - `ArtifactGraph` with typed `GraphNode` and `GraphEdge`
  - `SemanticProductModel` union of `ComponentModel`, `PageModel`, `DataModel`, `ApiModel`, `StateModel`, `TokenModel`
  - `Provenance` and `ConfidenceScore` schemas
  - `ResidualIsland` with `RegenerationStrategy`
  - `MergeResult` and `ConflictResolution` types
  - `ExtractionResult` and `ExtractionContext`
- Inventory Scanner in `src/inventory/scanner.ts`:
  - `scanRepository(config)` — recursive file traversal with `.gitignore` respect
  - `classifyArtifact(path, content, language, framework)` — deterministic kind detection
  - `detectLanguage(ext)`, `detectFramework(content, ext, dir)` — heuristic detection
  - Import/export summary via `detective-es6`
- Lightweight Extractors in `src/extractors/{typescript,storybook,prisma}/`:
  - **Component Extractor**: TS Compiler API — props, slots, events, accessibility, JSX usage
  - **Page Extractor**: Route derivation, layout hierarchy, auth guard detection
  - **State Store Extractor**: Redux Toolkit, Zustand, Jotai, React Context patterns
  - **Storybook CSF Extractor**: CSF meta/story extraction, variant mapping
  - **Prisma Schema Extractor**: Model, field, relation, index extraction
- Barrel exports in `src/index.ts` with subpath exports for `inventory`, `graph`, `model`, `extractors`, `provenance`, `residual`, `merge`, `synthesis`

### Synthesis Orchestrator

**Location**: `src/synthesis/engine.ts`

**Deliverables**:
- `SynthesisPipeline` class that coordinates extraction → graph building → semantic model generation
- Calls Java Knowledge Graph HTTP API for heavy graph computation (centrality, communities, cycles)
- Receives `GraphAnalysisResult` from Java, applies heuristics to generate `SemanticModelElement`s
- Confidence scoring per element based on extraction source and graph position
- Uses `platform/typescript/api` `ApiClient` for HTTP communication (reuses existing client with retry, timeout, circuit breaker)

**Acceptance Criteria**:
- Pipeline processes 100 files end-to-end in <10s (with Java delegation)
- Each generated element has provenance traceable to source file + line
- Confidence scores follow defined thresholds (HIGH >0.8, MEDIUM 0.5-0.8, LOW <0.5)

---

## Java Layer (Compute-Heavy Operations)

### Framework: ActiveJ (NOT Spring Boot)

**Critical Decision**: The entire YAPPC backend uses **ActiveJ exclusively**. All new Java services must extend `UnifiedApplicationLauncher` and use ActiveJ patterns (`io.activej.promise.Promise`, `io.activej.inject.module.ModuleBuilder`, `io.activej.http.RoutingServlet`).

**Evidence**:
- `platform/java/runtime/src/main/java/com/ghatana/core/activej/launcher/UnifiedApplicationLauncher.java` — canonical service bootstrap
- `products/yappc/core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java` — extends `UnifiedApplicationLauncher`
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcHttpServer.java` — ActiveJ `HttpServerLauncher` + `RoutingServlet`

### HTTP API Extension (Phase 1)

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/api/`

**Deliverables**:
- `ArtifactGraphController` — ActiveJ HTTP routes:
  - `POST /api/v1/artifact/graph/ingest` — ingest `ArtifactGraph` from TS scanner
  - `POST /api/v1/artifact/graph/analyze` — run JGraphT algorithms (centrality, cycles, communities)
  - `POST /api/v1/artifact/graph/merge` — three-way merge for `SemanticProductModel`
  - `GET /api/v1/artifact/graph/query` — graph traversal query
  - `POST /api/v1/artifact/residual/analyze` — analyze residual islands
- Reuse `YappcHttpServer` routing pattern from `core/yappc-services/`

**Dependencies**: Existing `YAPPCGraphService`, `KGQueryService`

### Artifact-to-Graph Mapper

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/mapper/ArtifactGraphMapper.java`

**Deliverables**:
- Map `ComponentModel` → `YAPPCGraphNode` (type: `CODE_MODULE`)
- Map `PageModel` → `YAPPCGraphNode` (type: `CODE_MODULE`)
- Map `DataModel` (Prisma) → `YAPPCGraphNode` (type: new `DATA_MODEL`)
- Map import relationships → `YAPPCGraphEdge` (type: `USES`)
- Map component-to-page usage → `YAPPCGraphEdge` (type: new `RENDERS_IN`)

**Dependencies**: Existing `YAPPCGraphMapper.java`

### Graph Algorithms (JGraphT Integration)

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/algorithm/JGraphTAlgorithmService.java`

**Deliverables**:
- Convert JDBC-fetched subgraph to JGraphT `DirectedMultigraph`
- **Cycle Detection**: Tarjan SCC via `KosarajuStrongConnectivityInspector`
- **Centrality**: Betweenness via `BetweennessCentrality`
- **Community Detection**: Label propagation or Louvain via JGraphT contrib
- **Dependency Analysis**: Topological sort for build order
- Expose via `POST /api/v1/artifact/graph/analyze`

**Dependencies**: `libs.jgrapht-core` (already available in version catalog)

**Acceptance Criteria**:
- Cycle detection on 1,000-node graph completes in <100ms
- PageRank converges in <50 iterations for 10,000-node graph
- Algorithm results exposed via HTTP endpoints

### Graph Query DSL

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/query/`

**Deliverables**:
- Extend `KGQueryService` with pattern-matching queries:
  - "Find all components used by pages in /admin"
  - "Find all orphaned components (no incoming edges)"
  - "Find circular dependency chains"
- Cypher-like query parser (lightweight, no full Cypher)
- Pagination and streaming

**Dependencies**: JGraphT algorithms

**Acceptance Criteria**:
- Complex pattern query completes in <200ms on 5,000-node graph
- Cache hit rate >80% for repeated queries

### Persistence: JDBC + Jackson (NOT Neo4j or JSONB Adapter)

**Critical Decision**: The knowledge-graph module uses **plain JDBC** with `PreparedStatement`, raw SQL with text blocks, and **Jackson `ObjectMapper`** for serializing `properties`, `tags`, and `labels` to VARCHAR/JSON-like columns. There is no Neo4j dependency and no explicit JSONB type usage.

**Evidence**: `KGNodeRepository.java` uses:
- `java.sql.Connection`, `PreparedStatement`, `ResultSet`
- `com.fasterxml.jackson.databind.ObjectMapper` for `writeJson()` / `readJson()`
- `ON CONFLICT (tenant_id, node_id) DO UPDATE SET` (PostgreSQL-specific upsert)

**Deliverables**:
- Extend `KGNodeRepository` for artifact-specific queries (by framework, by language, by kind)
- Add GIN index on `properties_json` for PostgreSQL (via Flyway migration)
- Batch insert optimization for large artifact ingestion

---

## Merge Engine (Extend KGConflictResolver)

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/merge/`

**Existing Foundation**: `KGConflictResolver.java` already implements:
- Entity deduplication by `type + name` key
- Description merge (longest wins)
- Relation set union via `LinkedHashSet`
- Tenant-scoped resolution

**New Deliverables**:
- `SemanticMergeEngine` class extending `KGConflictResolver` patterns
- Three-way diff (base / left / right) for `SemanticProductModel` trees
- Field-level conflict detection (not just entity-level)
- Configurable resolution strategies (auto-resolve, manual-review, last-write-wins)
- Merge provenance tracking (which source contributed each field)
- HTTP endpoint: `POST /api/v1/artifact/graph/merge`

**Acceptance Criteria**:
- Auto-resolves >70% of non-conflicting changes
- Detects 100% of true conflicts (no silent overwrites)
- Merge result is associative and commutative where possible
- Large model merge (1,000 elements) completes in <2s

### Versioning & History

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/history/`

**Deliverables**:
- Git-like versioning for `SemanticProductModel` (commits, branches, tags)
- Diff generation between any two versions
- Blame/annotation for each model element
- Flyway migration for `artifact_versions` table
- Pruning old versions with retention policy (default: 90 days)

---

## Language-Specific Heavy Extractors

### Java Source Analysis

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/parser/java/`

**Decision**: Evaluate `org.openrewrite:rewrite-java` vs `com.github.javaparser:javaparser-core` (lighter-weight). Add chosen library to `gradle/libs.versions.toml`.

**Deliverables**:
- Extract public methods, fields, inner classes, annotations
- Map to `YAPPCGraphNode` (type: `CODE_MODULE`)
- Extract class-level dependencies (import graph)

### SQL Schema Analysis

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/parser/sql/`

**Decision**: Use JOOQ SQL parser (already in version catalog: `libs.jooq`). NOT SQLGlot (Python library).

**Deliverables**:
- Parse CREATE TABLE, ALTER TABLE, INDEX, CONSTRAINT statements
- Generate schema diff between versions
- Map tables/columns to `YAPPCGraphNode` (type: `DATA_MODEL`)

### CI/CD Workflow Parser

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/parser/cicd/`

**Deliverables**:
- GitHub Actions YAML parser (SnakeYAML, already transitive)
- GitLab CI parser
- Jenkinsfile Groovy parser (if feasible; otherwise regex/heuristics)
- Extract job dependencies, secrets usage, environment variables
- Map to `YAPPCGraphNode` (type: new `WORKFLOW`)

### Tree-sitter Integration (Deferred)

**Decision**: **Defer or replace with simpler approach**

**Rationale**: Tree-sitter JNI is high-risk for JVM stability. The TS side already parses TypeScript/TSX via the TypeScript Compiler API. For other languages (Python, Rust, Go), consider:
- Option A: Run Tree-sitter CLI as a subprocess (`ProcessBuilder`) and parse JSON output
- Option B: Use language-specific Java parsers (JavaParser for Java, Antlr grammars for others)
- Option C: Parse only file-level metadata (imports, class names) via regex for low-priority languages

---

## Cross-Cutting Concerns

### Observability & Metrics

**Pattern**: Micrometer + Prometheus + OpenTelemetry (existing platform pattern)

**Deliverables**:
- Java: Micrometer metrics (extraction time, merge conflicts, graph size)
- Java: OpenTelemetry traces (end-to-end pipeline tracing)
- TS: Structured logging with correlation IDs
- TS: Client-side metrics (request latency, error rate, circuit breaker state)
- Grafana dashboards for pipeline health

**Acceptance Criteria**:
- All HTTP calls traced with correlation ID
- Metrics exported in Prometheus format
- Alert on extraction failure rate >1%

### Security & Privacy Validation

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/security/`

**Deliverables**:
- Secret detection in code (API keys, passwords, tokens) using regex + entropy analysis
- PII detection in model elements (names, emails, phone numbers)
- Data classification tags (public, internal, confidential, restricted)
- Automated masking/quarantining of detected secrets

**Acceptance Criteria**:
- Detects 95%+ of hardcoded secrets (GitLeaks-like accuracy)
- False positive rate <5%
- PII detected in model fields with appropriate classification

### Caching Layer

**Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/cache/`

**Deliverables**:
- Caffeine in-memory cache for hot queries (tenant-scoped)
- Cache invalidation on artifact ingestion
- Cache warming on service startup

**Dependencies**: `libs.caffeine` (already available in version catalog)

**Acceptance Criteria**:
- Cache hit rate >90% for repeated queries
- P99 latency <5ms for cached queries
- Cache invalidated within 100ms of model update

---

## Testing Pattern

**Pattern**: `platform:java:testing` + Custom Integration Test Source Sets

**Evidence**: The knowledge-graph module uses a specific pattern:
- `val integrationTest by sourceSets.creating` — separate `src/integrationTest/java` source set
- `tasks.register<Test>("integrationTest")` — custom Gradle task
- `testImplementation(project(":platform:java:testing"))` — shared test utilities
- `testImplementation(libs.testcontainers.core)`, `(libs.testcontainers.postgresql)`, `(libs.testcontainers.junit.jupiter)` — PostgreSQL test containers

**Required**: All new Java modules must follow this pattern. Reuse `PostgresTestContainer` from `platform/java/testing/src/main/java/com/ghatana/platform/testing/internal/containers/`.

---

## Key Decisions & Rationale

1. **Extend existing Knowledge Graph module (NOT create new service)**: Substantial graph infrastructure already exists in `products/yappc/core/knowledge-graph/`. Creating a new service would duplicate effort and create architectural inconsistency.

2. **ActiveJ ONLY (No Spring Boot)**: The entire YAPPC backend uses ActiveJ exclusively. All new services must follow this pattern.

3. **JDBC + Jackson (NOT Neo4j or JSONB adapter)**: Existing persistence pattern uses plain JDBC with Jackson for JSON serialization. Align with this pattern.

4. **Reuse platform/typescript/api ApiClient**: Production-grade HTTP client already exists with retry, timeout, circuit breaker. Do not build a new client.

5. **JGraphT for graph algorithms**: Available in version catalog (`libs.jgrapht-core`). Use for centrality, SCC, community detection.

6. **Caffeine for caching**: Available in version catalog (`libs.caffeine`). Use for in-memory caching.

7. **JOOQ for SQL parsing**: Available in version catalog (`libs.jooq`). Use for SQL parsing and diff instead of SQLGlot (Python library).

8. **Tree-sitter deferred**: JNI bridge is high-risk for JVM stability. Use subprocess or language-specific parsers instead.

9. **TS Compiler API stays in TS**: Single-file AST extraction is lightweight; no need to delegate to Java.

10. **Protobuf optional**: Use existing `ProtobufModuleConventionPlugin` if needed for high-volume streaming. HTTP JSON is sufficient for initial implementation.

---

## Risk Register

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| JGraphT performance on 50K+ node graphs | Medium | Medium | Test with `kgScaleValidation` task; add pagination |
| OpenRewrite version conflicts with Jackson | Low | High | Pin versions in `libs.versions.toml`; test in isolation |
| JOOQ SQL parser dialect coverage gaps | Medium | Medium | Fallback to regex heuristics for unsupported DDL |
| TS→Java HTTP latency for large payloads | Medium | Medium | Use streaming JSON; compress; batch requests |
| Knowledge-graph module becoming too large | Medium | Medium | Split into `knowledge-graph-core` and `knowledge-graph-artifact` if needed |
| ActiveJ event loop blocking during graph computation | Medium | High | Use `Promise.ofBlocking(executor, ...)` for all CPU-heavy work; always use dedicated executor |

---

## Files to Study Before Implementation

| File | Why |
|---|---|
| `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/YAPPCGraphService.java` | Existing graph operations facade |
| `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/query/KGQueryService.java` | Traversal and path discovery patterns |
| `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/persistence/KGNodeRepository.java` | JDBC + Jackson persistence pattern |
| `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/pipeline/KGUpdatePipeline.java` | Event-driven ingestion pipeline |
| `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/pipeline/KGConflictResolver.java` | Existing merge/dedup logic |
| `platform/java/runtime/src/main/java/com/ghatana/core/activej/launcher/UnifiedApplicationLauncher.java` | Service bootstrap pattern |
| `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcHttpServer.java` | HTTP routing pattern |
| `platform/typescript/api/src/client.ts` | Canonical TS HTTP client |
| `gradle/libs.versions.toml` | Available library versions |
| `build-logic/conventions/src/main/kotlin/.../ProtobufModuleConventionPlugin.kt` | Proto generation setup |

---

## Documentation Tags

All new Java classes must include structured doc tags (enforced by Gradle doc-tag checks):

```java
/**
 * @doc.type class
 * @doc.purpose Maps TypeScript extraction results to Knowledge Graph nodes and edges
 * @doc.layer product
 * @doc.pattern Mapper
 */
public class ArtifactGraphMapper {
    // ...
}
```

---

## Historical Notes

This document merges and supersedes:
- `docs/implementation-plans/ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md` (original implementation plan)
- `docs/implementation-plans/ARTIFACT_COMPILER_IMPLEMENTATION_PLAN_REVIEW.md` (codebase alignment review)

The review identified critical inconsistencies between the original plan and the actual codebase, particularly around:
- Use of ActiveJ instead of Spring Boot
- Extension of existing Knowledge Graph module instead of creating a new service
- JDBC + Jackson persistence instead of Neo4j
- Reuse of existing platform abstractions instead of building new ones

This canonical architecture document reflects the corrected approach that aligns with existing Ghatana patterns.
