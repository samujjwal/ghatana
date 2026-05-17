# Artifact Compiler/Decompiler Architecture

> **Package**: `@yappc/artifact-compiler` (TypeScript) + `products/yappc/core/yappc-services` (Java)
> **Location**: `products/yappc/frontend/libs/yappc-artifact-compiler` + `products/yappc/core/yappc-services/`
> **Principle**: TypeScript handles lightweight extraction and orchestration; Java handles computation-heavy graph, merge, and indexing workloads using the ArtifactGraphService in yappc-services module.

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
│  Java Layer (products/yappc/core/yappc-services/) — DEDICATED ARTIFACT MODULE │
│  ─────────────────────────────────────────────────────────────────────────────  │
│  Core Services:                                                                  │
│    • ArtifactGraphService — ingest, analyze, merge, query, residual analysis   │
│    • ArtifactGraphRepository — JDBC persistence with checksum-based upsert      │
│    • ArtifactGraphController — HTTP routes for artifact-specific operations     │
│    • ArtifactRequestScope — tenant/project scope resolution                    │
│                                                                                 │
│  Persistence:                                                                   │
│    • V15 migration — repository_snapshots, artifact_inventory_items,            │
│      artifact_unresolved_edges, artifact_edge_resolution_records,              │
│      residual_islands, patch_sets, review_bundles tables                        │
│    • Checksum-based incremental upsert to skip unchanged nodes                   │
│    • Paginated query API with cursor support                                     │
│                                                                                 │
│  Graph Analysis:                                                                │
│    • JGraphT integration for centrality, SCC, community detection              │
│    • Three-way merge for semantic models                                        │
│    • Graph validation with structural integrity checks                           │
│                                                                                 │
│  Frontend Integration:                                                           │
│    • ArtifactCompilerClient — typed HTTP client for import, query, review      │
│    • SourceImportPanel — provider selector, progress tracking                   │
│    • ImportSummaryPanel — understood/skipped/residual metrics                  │
│    • PatchReviewPanel — diff display, validation, approve/reject                 │
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
- Barrel exports in `src/index.ts` with subpath exports for `inventory`, `graph`, `model`, `extractors`, `provenance`, `residual`, `merge`, `synthesis`, `source-providers`, `compile-back`, `capabilities`
- Capability Registry in `src/capabilities/capability-registry.ts`:
  - Runtime discovery of providers, extractors, emitters, and validators
  - Support levels (production, preview, unsupported)
  - Language and framework support metadata
  - Confidence ranges and limitations

### Synthesis Orchestrator

**Location**: `src/synthesis/pipeline.ts`

**Deliverables**:
- `SynthesisPipeline` class that coordinates extraction → graph building → semantic model generation
- Six-step compilation process:
  1. Source acquisition → RepositorySnapshot (via SourceProvider)
  2. Inventory scan → ArtifactInventory (via scanRepository)
  3. Extraction → ExtractionResult[] (via registered ArtifactExtractors)
  4. Symbol resolution → Resolved GraphEdge[] (via resolveSymbols)
  5. Graph assembly → ArtifactGraph (with validation via validateGraph)
  6. Provenance indexing → ProvenanceIndex
- Graph validation before proceeding to model synthesis (structural integrity checks)
- Confidence scoring per element based on extraction source and graph position
- Deterministic ordering for reproducible scans (entries sorted by name, artifacts by relativePath)

**Acceptance Criteria**:
- Pipeline processes 100 files end-to-end in <10s (with Java delegation)
- Each generated element has provenance traceable to source file + line
- Confidence scores follow defined thresholds (HIGH >0.8, MEDIUM 0.5-0.8, LOW <0.5)
- Graph validation catches structural errors before model synthesis
- Deterministic behavior across repeated scans of the same commit

---

## Java Layer (Compute-Heavy Operations)

### Framework: ActiveJ (NOT Spring Boot)

**Critical Decision**: The entire YAPPC backend uses **ActiveJ exclusively**. All new Java services must extend `UnifiedApplicationLauncher` and use ActiveJ patterns (`io.activej.promise.Promise`, `io.activej.inject.module.ModuleBuilder`, `io.activej.http.RoutingServlet`).

**Evidence**:
- `platform/java/runtime/src/main/java/com/ghatana/core/activej/launcher/UnifiedApplicationLauncher.java` — canonical service bootstrap
- `products/yappc/core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java` — extends `UnifiedApplicationLauncher`
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcHttpServer.java` — ActiveJ `HttpServerLauncher` + `RoutingServlet`

### HTTP API Extension (Phase 1 - COMPLETED)

**Location**: `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/`

**Deliverables**:
- `ArtifactGraphController` — ActiveJ HTTP routes with tenant/project scope enforcement:
  - `POST /api/v1/yappc/artifact/graph/ingest` — ingest `ArtifactGraphIngestRequest` with validation
  - `POST /api/v1/yappc/artifact/graph/analyze` — run graph analysis algorithms
  - `POST /api/v1/yappc/artifact/graph/merge` — three-way semantic merge
  - `POST /api/v1/yappc/artifact/graph/query` — paginated graph queries with cursor
  - `POST /api/v1/yappc/artifact/residual/analyze` — analyze residual islands
- Uses `YappcHttpServer` routing pattern from `core/yappc-services/`
- Principal-derived tenant/project scope enforcement (rejects payload scope manipulation)

**Dependencies**: `ArtifactGraphService`, `ArtifactGraphRepository`

### Artifact-to-Graph Mapper (COMPLETED)

**Location**: `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/`

**Deliverables**:
- `ArtifactNodeDto` — typed node with sourceLocation, extractorId, confidence, provenance
- `ArtifactEdgeDto` — typed edge with edgeId, confidence, bidirectional, metadata
- `ArtifactGraphIngestRequest` — includes snapshotRef, snapshotId, versionId, contentChecksum
- `ArtifactGraphResponse` — response with success status and metadata
- Graph validation before ingestion (structural integrity checks)

**Dependencies**: Jackson for JSON serialization

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

### Persistence: JDBC + Jackson (COMPLETED)

**Critical Decision**: ArtifactGraphRepository uses **plain JDBC** with `PreparedStatement`, raw SQL with text blocks, and **Jackson `ObjectMapper`** for serializing `properties`, `tags`, and `labels` to VARCHAR/JSON-like columns. There is no Neo4j dependency and no explicit JSONB type usage.

**Evidence**: `ArtifactGraphRepository.java` uses:
- `java.sql.Connection`, `PreparedStatement`, `ResultSet`
- `com.fasterxml.jackson.databind.ObjectMapper` for `writeJson()` / `readJson()`
- `ON CONFLICT (tenant_id, node_id) DO UPDATE SET` (PostgreSQL-specific upsert)
- Executor injection to avoid blocking event loops

**Deliverables**:
- `ArtifactGraphRepository` with JDBC persistence for nodes and edges
- Checksum-based incremental upsert (skip unchanged nodes)
- Persistence for unresolved edges, edge resolution records, residual islands
- Paginated query API with cursor support
- V15 Flyway migration for artifact-specific tables

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

### Source Providers (COMPLETED)

**Location**: `src/source-providers/{github-provider,gitlab-provider,zip-provider,archive-provider}.ts`

**Deliverables**:
- `GitHubProvider`: GitHub Contents API with commit pinning, retry/backoff, rate-limit diagnostics
- `GitLabProvider`: GitLab API with nested groups support, typed locator dispatch
- `ZipProvider`: ZIP extraction with diagnostics for skipped/unsafe/unsupported compression
- `ArchiveProvider`: Facade for zip/tar/tgz with shared safety rules
- `CredentialResolver`: Credential reference resolver for governed source acquisition
- Provider diagnostics for observability

**Security Features**:
- GitHub: Fails closed on tree API truncation (>500k entries)
- ZIP: Path containment guards prevent zip-slip attacks
- All providers: Structured diagnostics without exposing credentials
- Typed SourceLocator.provider dispatch with credentialRef support

### Compile-Back Layer (COMPLETED)

**Location**: `src/compile-back/{patch-coordinator,react-patch-emitter,prisma-patch-emitter,workflow-patch-emitter,residual-preserver,apply-patch}.ts`

**Deliverables**:
- `PatchCoordinator`: Orchestrates patch emitters, validates change plans, detects residual overlaps
- `ReactPatchEmitter`: AST/range-based minimal diffs with range metadata, TS Compiler API
- `PrismaPatchEmitter`: Prisma schema patch emitter
- `WorkflowPatchEmitter`: Workflow YAML patch emitter
- `ResidualPreserver`: Applies regeneration strategies to residual islands
- `apply-patch`: Dry-run/apply interface with checksum guard, residual guard, rollback metadata
- Full patch lifecycle types: ModelChange, ChangePlan, FilePatch, ReviewBundle, ValidationResult, RollbackMetadata
- Extended ChangeOpKind for page route, layout, token, API, data entity, workflow, manual-review

**Features**:
- AST/range-based minimal diffs (vs full-file replacements)
- Residual overlap detection and blocking
- Validation and review bundle generation
- Rollback metadata for audit trails
- Checksum guard for concurrent modification prevention

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

## Implementation Status (2026-05-16)

### Completed (P0 - High Priority)
- P0-1: Repository import extractor registry enforcement
- P0-2: Graph validation exports from graph barrel
- P0-3: Enhanced graph validation with resolved edge targets, raw label edges, resolution records, source ranges
- P0-4: Extended ArtifactGraphIngestRequest with snapshotRef, snapshotId, versionId, contentChecksum, unresolvedEdges, edgeResolutionRecords, residualIslandIds
- P0-5: Extended ArtifactNodeDto with sourceLocation, extractorId, extractorVersion, confidence, provenance, privacySecurityFlags, residualFragmentIds, sourceRef, symbolRef
- P0-6: Extended ArtifactEdgeDto with edgeId, confidence, bidirectional, metadata, snapshotId, versionId
- P0-7: Enforced workspace/project scope server-side in ArtifactGraphController
- P0-8: Removed/flagged production parser stubs with feature gate
- P0-9: Restricted React patch emitter canEmit to implemented ops
- P0-10: Added UnsupportedPatchOperation result to patch-coordinator
- P0-1 (Phase 1): Added workspace scope to ArtifactGraphRepository pagination/diff queries
- P0-2 (Phase 1): Fixed ArtifactGraphServiceImpl queryGraph to use proper scope and repository methods
- P0-3 (Phase 1): Fixed ArtifactGraphController to pass typed request and full scope
- P0-4 (Phase 1): Fixed SourceImportJobRepository scope issues and cancel semantics
- P0-5 (Phase 1): Fixed ImportController scope enforcement and job execution
- P0-6 (Phase 1): Fixed GitHubSourceProvider deterministic snapshotId, credentials, .gitignore
- P0-7 (Phase 1): Fixed LocalFolderSourceProvider content SHA-256 and deterministic snapshotId
- P0-8 (Phase 1): Fixed ArchiveSourceProvider streaming, content checksum, deterministic snapshotId
- P0-9 (Phase 1): Fixed ArtifactGraphServiceImpl to emit residual islands for unknown files
- P0-10 (Phase 1): ADD RepositoryInventoryScanner with .gitignore and classification
- P0-11 (Phase 1): ADD SourceFileRef for canonical durable file identity
- P0-12 (Phase 1): ADD migration V17 for repository snapshots and inventory

### Completed (P1 - Medium Priority)
- P1-1: Added CredentialResolver to source-providers/types.ts
- P1-2: Hardened GitHub provider with credentialRef resolver, retry/backoff, rate-limit diagnostics
- P1-3: Hardened GitLab provider with nested groups, typed locator dispatch, retry/backoff
- P1-4: Hardened ZIP provider with diagnostics for skipped/unsafe/unsupported compression
- P1-5: Added archive-provider.ts facade for zip/tar/tgz
- P1-6: Extended inventory types with skip sources, sourceFileRef, contentChecksum, classificationConfidence
- P1-7: Hardened scanner with battle-tested ignore parser, bounded concurrency, deterministic scan mode
- P1-8: Made synthesis pipeline deterministic with SHA-256 hashed IDs and extractor registry enforcement
- P1-9: Fixed symbol resolver with deterministic hashed edge IDs, source location, resolver version metadata
- P1-10: Fixed symbol index with alias derivation from tsconfig/package boundaries and scanner workspace metadata
- P1-11: Added V15 migration for repository_snapshots, artifact_inventory_items, artifact_unresolved_edges, artifact_edge_resolution_records, residual_islands, patch_sets, review_bundles
- P1-12: Fixed ArtifactGraphRepository (removed default blocking constructor, checksum-based skipping, persisted unresolved/residual tables, paginated query API)
- P1-13: Fixed ArtifactGraphServiceImpl with repository pagination routing and query cursor response
- P1-14: Extended compile-back types with ModelChange coverage for page route, layout, token, API, data entity, workflow, manual-review operation type
- P1-15: Added apply-patch.ts module with dry-run/apply interface, checksum guard, residual guard, rollback metadata, validation hook
- P1-13 (Phase 2): ADD GitLabSourceProvider.java with commit pinning, bounded concurrency, file-size limits
- P1-14 (Phase 2): Update SourceProviderRegistry.java to register GitLab provider
- P1-15 (Phase 2): Consolidate source-imports.ts to remove legacy TS route, keep only Java proxy
- P1-16 (Phase 2): Remove/deprecate job-repository.ts (no production use found)
- P1-17 (Phase 3): ADD SubprocessTsExtractorWorker.java with process timeout and schema validation
- P1-18 (Phase 3): ts-extractor-worker.ts already has zod contract validation
- P1-19 (Phase 3): artifact_compiler.proto already has SourceLocator, RepositorySnapshot, SourceFileRef messages
- P1-20 (Phase 4): ADD ResidualIslandService.java with database-backed full schema persistence
- P1-21 (Phase 5): ADD plan/generate/validate endpoints to ArtifactPatchController.java
- P1-22 (Phase 5): ArtifactPatchJobService.java already exists with job orchestration
- P1-23 (Phase 5): ADD migration V18 for patch_jobs, validation_results, review_decisions tables
- P1-24 (Phase 6): ImportSourceWorkflow.ts already consumes Java API with proper scope headers
- P1-25 (Phase 6): ImportSummaryPanel.tsx already exists with understood/skipped/residual metrics
- P1-26 (Phase 6): PatchReviewPanel.tsx already exists with diff, validation, approve/reject

### Completed (P2 - Medium Priority)
- P2-1: Added typed client artifactCompilerClient.ts for import job, graph summary, residual review, patch review
- P2-2: Added SourceImportPanel.tsx with provider selector, repo/ref/archive input, progress, job polling, unsupported-state display
- P2-3: Added ImportSummaryPanel.tsx showing understood vs skipped vs residual, confidence, review requirements
- P2-4: Added PatchReviewPanel.tsx with unified diff, validation results, residual overlaps, approve/reject
- P2-7: Update ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md (completed)

### Remaining (P2 - Medium Priority)
- P2-5: Write P2 tests (artifactCompilerClient.test.ts, Playwright import flow, component + E2E for ImportSummaryPanel, Playwright patch review)

### Remaining (P2 - Low Priority)
- P2-6: Document/consolidate V11/V14 duplicate snapshot columns (not applicable - V11/V14 migrations don't exist)
- P2-8: Archive/consolidate platform/comp-decomp-todo.md

### Remaining (P3 - Low Priority)
- P3-1: Improve graph query response ergonomics
- P3-2: Write P3 test for graph query pagination

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
| `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphService.java` | Artifact graph operations facade |
| `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java` | JDBC + Jackson persistence pattern with pagination |
| `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java` | HTTP routing pattern with scope enforcement |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/` | Source provider implementations |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/pipeline.ts` | Synthesis orchestrator with deterministic ID generation |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/apply-patch.ts` | Patch application with safety guards |
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
