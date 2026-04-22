# YAPPC Artifact Compiler — Plan Review & Alignment with Existing Codebase

> **Date**: 2026-04-21
> **Scope**: `products/yappc/ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md`
> **Reviewer**: AI Pair Programmer

---

## 1. Executive Summary

The existing plan is **directionally correct** but contains several **inconsistencies with the actual codebase** that must be resolved before implementation begins. The most significant finding is that **a substantial portion of the planned Java backend already exists** in the `products/yappc/core/knowledge-graph/` module. The plan should pivot from "build a new Java service" to **"extend the existing Knowledge Graph module with artifact-specific subgraphs and extractors."

---

## 2. Critical Findings

### 2.1 Java Service Framework: ActiveJ ONLY (No Spring Boot)

**Plan states**: "Java Service Skeleton (Spring Boot / ActiveJ)"

**Reality**: The entire YAPPC backend and platform Java services use **ActiveJ exclusively**. There is **zero Spring Boot** in the repository.

**Evidence**:
- `platform/java/runtime/src/main/java/com/ghatana/core/activej/launcher/UnifiedApplicationLauncher.java` — canonical service bootstrap
- `products/yappc/core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java` — extends `UnifiedApplicationLauncher`
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcHttpServer.java` — ActiveJ `HttpServerLauncher` + `RoutingServlet`
- All services use `io.activej.promise.Promise`, `io.activej.inject.module.ModuleBuilder`, `io.activej.http.RoutingServlet`

**Required change**: Remove all Spring Boot references. All new Java services must extend `UnifiedApplicationLauncher` and use ActiveJ patterns.

---

### 2.2 Graph Engine: Already Exists in `knowledge-graph` Module

**Plan states**: "Create new Java service `yappc-artifact-analyzer` with Graph Data Structure & Storage, Graph Algorithms Library, Graph Query Engine"

**Reality**: `products/yappc/core/knowledge-graph/` already contains:
- `KnowledgeGraph.java`, `KnowledgeGraphNode.java`, `KnowledgeGraphEdge.java` — core graph data structures
- `YAPPCGraphService.java` — graph facade with CRUD, traversal, impact analysis, validation, statistics
- `KGQueryService.java` — BFS traversal (`collectReachable`), path discovery (`findPaths` up to 5 hops), semantic search integration
- `KGNodeRepository.java` / `KGEdgeRepository.java` — JDBC persistence with PostgreSQL, Jackson JSONB serialization, `ON CONFLICT` upserts
- `KGUpdatePipeline.java` — event-driven extraction → conflict resolution → embedding → persistence pipeline
- `KGConflictResolver.java` — entity deduplication and relationship merge

**Evidence** (from `KGNodeRepository.java`):
```java
// Already using JDBC + Jackson + PostgreSQL upserts
public Promise<YAPPCGraphNode> saveNode(YAPPCGraphNode node) {
    return Promise.ofBlocking(executor, () -> {
        String sql = """
            INSERT INTO kg_nodes (node_id, node_type, label, description, ...)
            VALUES (?, ?, ?, ?, ...)
            ON CONFLICT (tenant_id, node_id) DO UPDATE SET ...
        """;
        // ...
    });
}
```

**Required change**: Do NOT create a new graph service. Instead:
1. Add artifact-specific node types (`COMPONENT`, `PAGE`, `STATE_STORE`, `PRISMA_MODEL`, etc.) to `YAPPCGraphNode.YAPPCNodeType`
2. Add artifact-specific relationship types to `YAPPCGraphEdge.YAPPCRelationshipType`
3. Extend `KGQueryService` with JGraphT-backed algorithm calls (centrality, SCC, communities)
4. Add `ArtifactGraphMapper` (similar to `YAPPCGraphMapper`) to map TypeScript extraction results into the existing graph schema

---

### 2.3 Persistence: JDBC + Jackson JSON (Not Neo4j or JSONB Adapter)

**Plan states**: "Neo4j or PostgreSQL JSONB adapter for graph persistence"

**Reality**: The knowledge-graph module uses **plain JDBC** with `PreparedStatement`, raw SQL with text blocks, and **Jackson `ObjectMapper`** for serializing `properties`, `tags`, and `labels` to VARCHAR/JSON-like columns. There is no Neo4j dependency, no jOOQ usage in this module, and no explicit `jsonb` PostgreSQL type usage (it uses plain JSON text columns).

**Evidence**: `KGNodeRepository.java` uses:
- `java.sql.Connection`, `PreparedStatement`, `ResultSet`
- `com.fasterxml.jackson.databind.ObjectMapper` for `writeJson()` / `readJson()`
- `ON CONFLICT (tenant_id, node_id) DO UPDATE SET` (PostgreSQL-specific upsert)

**Required change**: Align persistence strategy with existing pattern — JDBC + Jackson, not Neo4j or dedicated JSONB adapter. If JSONB-specific indexing is needed later, add GIN indexes via Flyway migrations.

---

### 2.4 Merge Engine: Partially Exists (`KGConflictResolver`)

**Plan states**: "Java Semantic Merge Engine — Three-way merge algorithm for SemanticProductModel trees"

**Reality**: `KGConflictResolver.java` already implements:
- Entity deduplication by `type + name` key
- Description merge (longest wins)
- Relation set union via `LinkedHashSet`
- Tenant-scoped resolution

**Required change**: Extend `KGConflictResolver` with:
1. Three-way diff (base / left / right) for `SemanticProductModel` trees
2. Field-level conflict detection (not just entity-level)
3. Configurable resolution strategies (auto-resolve, manual-review, last-write-wins)
4. Merge provenance tracking (which source contributed each field)

---

### 2.5 Protobuf Contracts: Follow Established Pattern

**Plan states**: "Define protobuf/JSON schema contracts between TS frontend and Java backend services"

**Reality**: The platform has a mature protobuf convention:
- `build-logic/conventions/src/main/kotlin/com/ghatana/buildlogic/ProtobufModuleConventionPlugin.kt` configures `protoc:4.34.1` and `grpc-java:1.79.0`
- Proto files live in `src/main/proto/` within each module
- Existing protos: `platform/contracts/src/main/proto/ghatana/contracts/userprofile/v1/user_profile_service.proto`, `products/audio-video/modules/speech/.../stt_service.proto`, `tts_service.proto`
- `platform/contracts/buf.yaml` configures `buf` linting and breaking-change detection
- Java package convention: `com.ghatana.contracts.<domain>.v<N>`

**Required change**: Define artifact compiler protos under `products/yappc/core/knowledge-graph/src/main/proto/ghatana/yappc/artifact/v1/` or a shared `platform/contracts` location. Reuse the existing `ProtobufModuleConventionPlugin`.

---

### 2.6 TypeScript HTTP Client: Reuse `platform/typescript/api`

**Plan states**: "Implement HTTP/gRPC client in TS package for Java artifact-analysis service"

**Reality**: `platform/typescript/api/src/client.ts` contains a production-grade `ApiClient` class with:
- Fetch-based HTTP with `AbortController` timeout
- Request/response middleware pipeline
- Exponential backoff retry (`baseMs * 2^attempt`)
- Zod-compatible schema validation via `ResponseSchema` interface
- Error categorization (`NETWORK`, `CLIENT`, `SERVER`)
- Query param building, header merging

**Evidence**:
```typescript
// From platform/typescript/api/src/client.ts
export class ApiClient {
  async request<T = unknown>(input: ApiRequest): Promise<ApiResponse<T>> {
    const attemptLimit = this.options.retry?.attempts ?? 1;
    // ... retry loop with exponential backoff
  }
}
```

**Required change**: Do NOT build a new TS client. Import from `@ghatana/api` (or `platform/typescript/api`). The artifact-compiler TS package should configure an `ApiClient` instance pointing at the YAPPC knowledge-graph HTTP endpoint.

---

### 2.7 Java Libraries: Version Catalog Alignment

**Plan mentions**: JGraphT, OpenRewrite, SQLGlot

**Reality check against `gradle/libs.versions.toml`**:
- `jgrapht = "1.5.2"` ✅ **Available** — `libs.jgrapht-core`
- `caffeine = "3.1.8"` ✅ **Available** — `libs.caffeine`
- `protobuf = "4.34.1"`, `grpc = "1.79.0"` ✅ **Available**
- `postgresql = "42.7.10"`, `hikari = "6.3.0"`, `flyway = "10.20.1"`, `jooq = "3.20.3"` ✅ **Available**
- **OpenRewrite**: ❌ **NOT in version catalog** — would need to be added
- **SQLGlot**: ❌ **NOT in version catalog** (it's a Python library; Java alternative needed)
- **Tree-sitter**: ❌ **NOT in version catalog** — would need JNI bindings or a separate process

**Required changes**:
1. For graph algorithms: Use `libs.jgrapht-core` (already available)
2. For caching: Use `libs.caffeine` (already available)
3. For Java source analysis: Evaluate adding `org.openrewrite:rewrite-java` to the version catalog, OR use the existing TypeScript Compiler API approach for Java (limited). Alternatively, use `javaparser` or `spoon` which are lighter-weight.
4. For SQL analysis: Instead of SQLGlot (Python), use `libs.jooq` (already available) for SQL parsing and diff, or add `zaxxer:HikariCP` (already present via `libs.hikari`). JOOQ has a SQL parser and meta-model.
5. For Tree-sitter: If needed, run as a separate native process (CLI) invoked via Java `ProcessBuilder`, rather than JNI, to avoid JVM stability risks.

---

### 2.8 Testing Pattern: `platform:java:testing` + Custom Integration Test Source Sets

**Plan states**: Generic integration tests

**Reality**: The knowledge-graph module uses a specific pattern:
- `val integrationTest by sourceSets.creating` — separate `src/integrationTest/java` source set
- `tasks.register<Test>("integrationTest")` — custom Gradle task
- `tasks.register<JavaExec>("kgScaleValidation")` — scale validation via JUnit ConsoleLauncher
- `testImplementation(project(":platform:java:testing"))` — shared test utilities
- `testImplementation(libs.testcontainers.core)`, `(libs.testcontainers.postgresql)`, `(libs.testcontainers.junit.jupiter)` — PostgreSQL test containers

**Required change**: All new Java modules must follow this pattern. Reuse `PostgresTestContainer` from `platform/java/testing/src/main/java/com/ghatana/platform/testing/internal/containers/`.

---

### 2.9 Observability Pattern: Micrometer + Prometheus + OpenTelemetry

**Plan states**: "OpenTelemetry tracing integration"

**Reality**: The YAPPC lifecycle service wires:
- `io.micrometer.prometheusmetrics.PrometheusMeterRegistry`
- `com.ghatana.platform.observability.MetricsCollector` / `SimpleMetricsCollector`
- `io.activej.inject.module.ModuleBuilder` bindings for metrics

**Required change**: New services must bind `MetricsCollector` via DI and emit metrics using the existing platform abstractions. OpenTelemetry API is in the version catalog (`libs.opentelemetry.api`) but verify if it's actively used in the knowledge-graph module.

---

### 2.10 Documentation Tags

**Reality**: All public Java classes in this repo use structured doc tags:
```java
/**
 * @doc.type class
 * @doc.purpose Extracts named entities and graph relationships from domain text
 * @doc.layer product
 * @doc.pattern Extractor
 */
```

**Required change**: All new Java classes must include `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags. This is enforced by Gradle doc-tag checks (`gradle/doc-tag-check.gradle`).

---

## 3. Revised Architecture Recommendation

Instead of building a new `yappc-artifact-analyzer` Java service, the architecture should be:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          YAPPC Artifact Compiler                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  TypeScript Layer (products/yappc/frontend/libs/yappc-artifact-compiler)        │
│  ─────────────────────────────────────────────────────────────────────────────  │
│  • Scanner (file traversal + classification) — ✅ Already built               │
│  • TSX/Prisma/CSF Extractors (local AST/regex) — ✅ Already built               │
│  • Synthesis Orchestrator — delegates to KG HTTP API                           │
│  • Residual Island Tagger — flags for KG analysis                              │
│  • TS Client — reuses platform/typescript/api ApiClient                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Java Layer (products/yappc/core/knowledge-graph/) — EXTEND EXISTING MODULE     │
│  ─────────────────────────────────────────────────────────────────────────────  │
│  Existing:                                                                      │
│    • KnowledgeGraph, YAPPCGraphService, KGQueryService                          │
│    • KGNodeRepository, KGEdgeRepository (JDBC + Jackson persistence)             │
│    • KGConflictResolver (entity dedup/merge)                                    │
│    • KGUpdatePipeline (event → extract → resolve → embed → persist)             │
│    • EntityExtractor (AI-based text extraction)                               │
│                                                                                 │
│  New additions within this module:                                             │
│    • ArtifactGraphMapper — maps TS extraction results to YAPPCGraphNode/Edge   │
│    • ArtifactGraphController — HTTP routes for artifact-specific operations     │
│    • JGraphTAlgorithmService — centrality, SCC, community detection            │
│    • ThreeWayMergeEngine — extends KGConflictResolver for semantic models     │
│    • Protobuf service definitions (optional) for high-volume streaming           │
│    • Flyway migrations for artifact-specific node/edge types                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Revised Task List (Aligned with Codebase)

### Phase 0 — Foundation (COMPLETED ✅)
- TS package `@yappc/artifact-compiler` with schemas, scanner, extractors
- Located at `products/yappc/frontend/libs/yappc-artifact-compiler/`

### Phase 1 — Bridge & HTTP API (NEW PRIORITY)

**Task 1.1: Extend Knowledge Graph HTTP API for Artifacts**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/api/`
- **Owner**: Java (ActiveJ)
- **Deliverables**:
  - `ArtifactGraphController` — ActiveJ HTTP routes:
    - `POST /api/v1/artifact/graph/ingest` — ingest `ArtifactGraph` from TS scanner
    - `POST /api/v1/artifact/graph/analyze` — run JGraphT algorithms (centrality, cycles, communities)
    - `POST /api/v1/artifact/graph/merge` — three-way merge for `SemanticProductModel`
    - `GET /api/v1/artifact/graph/query` — graph traversal query
    - `POST /api/v1/artifact/residual/analyze` — analyze residual islands
  - Reuse `YappcHttpServer` routing pattern from `core/yappc-services/`
- **Dependencies**: Existing `YAPPCGraphService`, `KGQueryService`
- **Estimated Effort**: 3-4 days

**Task 1.2: TypeScript Synthesis Orchestrator**
- **Location**: `src/synthesis/engine.ts` (in `@yappc/artifact-compiler`)
- **Owner**: TypeScript
- **Deliverables**:
  - `SynthesisPipeline` class
  - Calls `POST /api/v1/artifact/graph/ingest` with extracted artifacts
  - Calls `POST /api/v1/artifact/graph/analyze` for graph computation
  - Receives `SemanticProductModel` elements from Java service
  - Assembles final model with provenance
- **Dependencies**: `platform/typescript/api` `ApiClient`
- **Estimated Effort**: 2-3 days

**Task 1.3: Artifact-to-Graph Mapper (Java)**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/mapper/ArtifactGraphMapper.java`
- **Owner**: Java
- **Deliverables**:
  - Map `ComponentModel` → `YAPPCGraphNode` (type: `CODE_MODULE`)
  - Map `PageModel` → `YAPPCGraphNode` (type: `CODE_MODULE`)
  - Map `DataModel` (Prisma) → `YAPPCGraphNode` (type: new `DATA_MODEL`)
  - Map import relationships → `YAPPCGraphEdge` (type: `USES`)
  - Map component-to-page usage → `YAPPCGraphEdge` (type: new `RENDERS_IN`)
- **Dependencies**: Existing `YAPPCGraphMapper.java`
- **Estimated Effort**: 2-3 days

### Phase 2 — Graph Algorithms (REUSE KG + ADD JGraphT)

**Task 2.1: JGraphT Integration for Advanced Algorithms**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/algorithm/JGraphTAlgorithmService.java`
- **Owner**: Java
- **Deliverables**:
  - Convert JDBC-fetched subgraph to JGraphT `DirectedMultigraph`
  - **Cycle Detection**: Tarjan SCC via `KosarajuStrongConnectivityInspector`
  - **Centrality**: Betweenness via `BetweennessCentrality`
  - **Community Detection**: Label propagation or Louvain via JGraphT contrib
  - **Dependency Analysis**: Topological sort for build order
  - Expose via `POST /api/v1/artifact/graph/analyze`
- **Dependencies**: `libs.jgrapht-core`
- **Estimated Effort**: 3-4 days

**Task 2.2: Graph Query DSL**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/query/`
- **Owner**: Java
- **Deliverables**:
  - Extend `KGQueryService` with pattern-matching queries:
    - "Find all components used by pages in /admin"
    - "Find all orphaned components (no incoming edges)"
    - "Find circular dependency chains"
  - Cypher-like query parser (lightweight, no full Cypher)
  - Pagination and streaming
- **Dependencies**: Task 2.1
- **Estimated Effort**: 3-4 days

### Phase 3 — Merge Engine (EXTEND KGConflictResolver)

**Task 3.1: Three-Way Semantic Merge**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/merge/`
- **Owner**: Java
- **Deliverables**:
  - `SemanticMergeEngine` class extending `KGConflictResolver` patterns
  - Diff generation between `SemanticProductModel` versions
  - Conflict detection at field level (not just entity level)
  - Resolution strategies: auto-resolve, manual-review, last-write-wins
  - Merge provenance tracking
  - gRPC/HTTP endpoint: `POST /api/v1/artifact/graph/merge`
- **Dependencies**: Existing `KGConflictResolver`
- **Estimated Effort**: 4-5 days

**Task 3.2: Versioning & History**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/history/`
- **Owner**: Java
- **Deliverables**:
  - Git-like versioning for `SemanticProductModel`
  - Diff/blame generation
  - Flyway migration for `artifact_versions` table
- **Dependencies**: Task 3.1
- **Estimated Effort**: 2-3 days

### Phase 4 — Language-Specific Heavy Extractors (NEW MODULES)

**Task 4.1: Java Source Analysis (OpenRewrite or JavaParser)**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/parser/java/`
- **Owner**: Java
- **Deliverables**:
  - Evaluate `org.openrewrite:rewrite-java` vs `com.github.javaparser:javaparser-core`
  - If using OpenRewrite: add to `gradle/libs.versions.toml`
  - Extract public methods, fields, inner classes, annotations
  - Map to `YAPPCGraphNode` (type: `CODE_MODULE`)
  - Extract class-level dependencies (import graph)
- **Estimated Effort**: 4-5 days

**Task 4.2: SQL Schema Analysis (JOOQ Parser)**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/parser/sql/`
- **Owner**: Java
- **Deliverables**:
  - Use JOOQ SQL parser (already in version catalog: `libs.jooq`)
  - Parse CREATE TABLE, ALTER TABLE, INDEX, CONSTRAINT statements
  - Generate schema diff between versions
  - Map tables/columns to `YAPPCGraphNode` (type: `DATA_MODEL`)
- **Estimated Effort**: 3-4 days

**Task 4.3: CI/CD Workflow Parser**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/parser/cicd/`
- **Owner**: Java
- **Deliverables**:
  - GitHub Actions YAML parser (SnakeYAML, already transitive)
  - GitLab CI parser
  - Jenkinsfile Groovy parser (if feasible; otherwise regex/heuristics)
  - Extract job dependencies, secrets usage, environment variables
  - Map to `YAPPCGraphNode` (type: new `WORKFLOW`)
- **Estimated Effort**: 3-4 days

**Task 4.4: Tree-sitter Integration (Optional / Deferred)**
- **Decision**: **Defer or replace with simpler approach**
- Rationale: Tree-sitter JNI is high-risk for JVM stability. The TS side already parses TypeScript/TSX via the TypeScript Compiler API. For other languages (Python, Rust, Go), consider:
  - Option A: Run Tree-sitter CLI as a subprocess (`ProcessBuilder`) and parse JSON output
  - Option B: Use language-specific Java parsers (JavaParser for Java, Antlr grammars for others)
  - Option C: Parse only file-level metadata (imports, class names) via regex for low-priority languages
- **Estimated Effort**: 5-7 days (if pursued)

### Phase 5 — Storage & Caching

**Task 5.1: Extend KGNodeRepository for Artifact Types**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/persistence/`
- **Owner**: Java
- **Deliverables**:
  - Add `ArtifactNodeRepository` extending patterns from `KGNodeRepository`
  - Artifact-specific queries (by framework, by language, by kind)
  - GIN index on `properties_json` for PostgreSQL (via Flyway migration)
  - Batch insert optimization for large artifact ingestion
- **Dependencies**: Existing `KGNodeRepository`
- **Estimated Effort**: 2-3 days

**Task 5.2: Caffeine In-Memory Cache**
- **Location**: `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/cache/`
- **Owner**: Java
- **Deliverables**:
  - Caffeine cache for hot graph queries (tenant-scoped)
  - Cache invalidation on artifact ingestion
  - Cache warming on service startup
- **Dependencies**: `libs.caffeine`
- **Estimated Effort**: 1-2 days

---

## 5. Deprecated / Removed Tasks from Original Plan

| Original Task | Reason | Replacement |
|---|---|---|
| New Java service `yappc-artifact-analyzer` | Redundant with existing `knowledge-graph` | Extend `knowledge-graph` module |
| Neo4j adapter | Not used anywhere in repo | Use existing JDBC + Jackson pattern |
| Spring Boot skeleton | Not used anywhere in repo | Use ActiveJ `UnifiedApplicationLauncher` |
| Custom TS HTTP/gRPC client | `platform/typescript/api` already exists | Reuse `@ghatana/api` `ApiClient` |
| SQLGlot integration | Python library, no Java equivalent in repo | Use JOOQ SQL parser (`libs.jooq`) |
| Tree-sitter JNI | High JVM stability risk | Defer; use subprocess or language-specific parsers |

---

## 6. Risk Register (Updated)

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| JGraphT performance on 50K+ node graphs | Medium | Medium | Test with `kgScaleValidation` task; add pagination |
| OpenRewrite version conflicts with Jackson | Low | High | Pin versions in `libs.versions.toml`; test in isolation |
| JOOQ SQL parser dialect coverage gaps | Medium | Medium | Fallback to regex heuristics for unsupported DDL |
| TS→Java HTTP latency for large payloads | Medium | Medium | Use streaming JSON; compress; batch requests |
| Knowledge-graph module becoming too large | Medium | Medium | Split into `knowledge-graph-core` and `knowledge-graph-artifact` if needed |
| ActiveJ event loop blocking during graph computation | Medium | High | Use `Promise.ofBlocking(executor, ...)` for all CPU-heavy work; always use dedicated executor |

---

## 7. Next Recommended Actions

1. **Read `YAPPCGraphService.java` thoroughly** to understand existing graph operations before adding new ones
2. **Read `KGUpdatePipeline.java`** to understand the event-driven ingestion pattern
3. **Add JGraphT dependency** to `products/yappc/core/knowledge-graph/build.gradle.kts` (`implementation(libs.jgrapht.core)`)
4. **Add Caffeine dependency** to knowledge-graph module
5. **Create `ArtifactGraphController.java`** with the first HTTP route: `POST /api/v1/artifact/graph/ingest`
6. **Update TS synthesis orchestrator** to call the new route using `platform/typescript/api` `ApiClient`
7. **Write Flyway migration** for new artifact-specific node/edge types and GIN index

---

## 8. Files to Study Before Implementation

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

*This review document supersedes the architecture assumptions in `ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md`. The original plan's task breakdown and effort estimates should be recalibrated using the revised tasks above.*
