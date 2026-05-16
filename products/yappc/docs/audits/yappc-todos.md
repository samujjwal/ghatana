Reviewed `samujjwal/ghatana` current `main` through the GitHub connector. I treated this as a review/research/implementation-planning pass, not a repo mutation.

## Bottom line

YAPPC already has a **strong skeleton** for artifact compiler/decompiler, but it is not yet the full production-grade loop you described:

> source repo/folder/GitHub/GitLab → scan/read → logical YAPPC model → YAPPC modifies model → compile back to safe code patches.

Current state is closer to:

> single-file/ZIP import + repo inventory scanner + partial AST extractors + semantic model schemas + Java graph APIs + persistence/versioning scaffolding.

The most important next move is **not adding more extractors first**. It is stabilizing the **round-trip IR**: deterministic IDs, resolved graph edges, source provenance, residual preservation, model synthesis, and patch-based code regeneration.

---

## What exists today

### 1. Artifact compiler package exists

There is an existing frontend library at:

`products/yappc/frontend/libs/yappc-artifact-compiler`

Its package describes itself as a decompilation/reverse-engineering library and exports inventory, graph, model, extractors, provenance, residual, merge, and synthesis subpaths. 

One inconsistency: the architecture document calls the package `@yappc/artifact-compiler`, while the actual `package.json` name is `yappc-artifact-compiler`. That should be normalized before more packages start depending on it.  

### 2. Repository inventory scanner exists, but it is heuristic

`scanner.ts` can walk folders, classify files by language/framework/kind, compute checksums, extract import/export summaries, and return an `ArtifactInventory`. It supports TS/TSX, JS/JSX, Java, SQL, Prisma, CSS/SCSS, YAML, JSON, Markdown, shell, Python, Rust, and Go. 

However, the scanner is not yet a production-grade repository ingestion engine. It uses glob exclusions, not a true `.gitignore` parser; it assigns random IDs; it has regex-based import extraction; and it does not produce a stable, resolved, repo-wide dependency graph by itself. 

### 3. Logical YAPPC semantic model is already broad

The semantic model layer is much broader than UI-only. It includes components, pages, layouts, tokens, themes, styles, data entities, APIs, state stores, interactions, cache config, and workflows. This is the right direction for a YAPPC-consumable logical representation.  

The graph layer is also already shaped as a reverse-compiler IR with nodes for files, symbols, components, routes, pages, layouts, styles, tokens, entities, APIs, state stores, workflows, scripts, and edges such as imports, uses, renders, queries, mutates, styles, tokens, and depends-on. 

### 4. Partial extractors exist

The library currently exports TypeScript component/page/state extractors, Storybook CSF extraction, and Prisma schema extraction. 

The TypeScript component extractor uses the TypeScript Compiler API, extracts exported React components, props, slots, event-like props, hooks, JSX usage, and accessibility metadata. That is a good foundation.  

But there is a serious IR correctness issue: JSX usage edges are created with `targetId: usage`, where `usage` is a component name string, while the graph schema expects UUID node IDs. This means unresolved edges are being stored as if they were resolved edges. That must be fixed before trusting graph analysis or round-trip edits. 

### 5. Frontend import workflow exists, but it is not repo-scale

`ImportSourceWorkflow.ts` supports `tsx`, `route`, `storybook`, `artifact`, and `zip`, and calls the compiler library for extraction. It also includes governed options for tenant/workspace scope and server import. 

But this workflow is still source-file/import oriented. It does not yet represent the full repository acquisition and round-trip pipeline needed for GitHub/GitLab/folder scanning.

### 6. Backend artifact graph APIs exist

There is an ActiveJ-backed artifact graph service interface for ingest, analyze, merge, query, and residual analysis. 

The implementation uses Caffeine caches, JGraphT algorithms, graph ingestion, graph analysis, query support, merge handling, and model versioning. 

The HTTP server wires artifact graph endpoints under:

`/api/v1/yappc/artifact/graph/ingest`
`/api/v1/yappc/artifact/graph/analyze`
`/api/v1/yappc/artifact/graph/merge`
`/api/v1/yappc/artifact/graph/query`
`/api/v1/yappc/artifact/residual/analyze` 

Persistence exists through `artifact_nodes`, `artifact_edges`, and artifact model version repositories/tables.   

---

## Critical gaps to close

### 1. No complete source acquisition layer

The current governed source import route fetches a trusted HTTPS/artifact source and returns a review-required single-file payload. It has job tracking, auditing, size limits, and scope checks, but it does not scan GitHub/GitLab repos, branch refs, commits, folders, or full ZIP archives into the artifact graph. Jobs are also stored in memory, not durably.  

Needed abstraction:

```ts
interface SourceProvider {
  readonly kind: 'local-folder' | 'github' | 'gitlab' | 'zip' | 'artifact-registry';

  resolve(input: SourceLocator): Promise<RepositorySnapshot>;
  readFile(snapshot: RepositorySnapshot, path: string): Promise<string | Uint8Array>;
  listFiles(snapshot: RepositorySnapshot): AsyncIterable<SourceFileRef>;
}
```

This must produce a stable `RepositorySnapshot` with provider, repo ID, branch/ref, commit SHA, ignore rules, manifests, package boundaries, and content hashes.

### 2. IR IDs must become deterministic

Random UUIDs make repeat scans impossible to compare reliably. The compiler/decompiler needs deterministic IDs such as:

```text
artifact://github/samujjwal/ghatana@<commit>/products/yappc/frontend/web/src/App.tsx#component:App
```

Use random IDs only for user-created logical artifacts that have no source anchor. Source-derived graph nodes must be stable across scans.

### 3. Graph edges need a two-phase resolution model

Today some edges use target names where UUIDs are expected. The fix is to separate unresolved references from resolved graph edges:

```ts
interface UnresolvedGraphEdge {
  sourceId: ArtifactNodeId;
  targetRef: string;
  targetKindHint?: GraphNodeKind;
  relationship: GraphEdgeKind;
  sourceLocation: SourceLocation;
}

interface ResolvedGraphEdge {
  sourceId: ArtifactNodeId;
  targetId: ArtifactNodeId;
  relationship: GraphEdgeKind;
  confidence: number;
}
```

Pipeline should be:

1. Extract local symbols and references.
2. Build repo-wide symbol index.
3. Resolve imports/aliases/path mappings/barrels.
4. Emit valid graph edges only after resolution.
5. Keep unresolved edges as residual/reference debt with confidence and review status.

### 4. Synthesis pipeline appears planned, not complete

The architecture document describes `src/synthesis/engine.ts` and `SynthesisPipeline`, but I did not find an implementation during the repo search. This is the missing bridge between extraction results and a coherent YAPPC-consumable semantic product model. 

This should become the canonical compile step:

```ts
compileRepository(snapshot)
  -> ArtifactInventory
  -> ExtractionResult[]
  -> ArtifactGraph
  -> SemanticProductModel
  -> ResidualIsland[]
  -> ProvenanceIndex
```

### 5. No true compile-back/decompile-back layer yet

The current system extracts and imports, but it does not yet safely convert YAPPC model changes back into code patches. That requires:

```ts
decompileModelChanges(baseSnapshot, currentModel, desiredModel)
  -> ChangePlan
  -> PatchSet
  -> ReviewBundle
```

The output should be a patch/PR, not direct overwrites.

### 6. Backend tenant enforcement is incomplete

`query` and `analyzeResidual` resolve tenant from the authenticated principal and reject request-body tenant mismatch, but `ingest`, `analyze`, and `merge` still parse tenant/project from request bodies in the shown controller code. That creates cross-tenant manipulation risk if authorization is not enforced elsewhere with equal strictness. 

All artifact graph operations should resolve tenant/workspace/project from principal + resource registry, not request payload.

### 7. Tree-sitter strategy conflicts with architecture

The architecture document says Tree-sitter JNI should be deferred or replaced due JVM stability risk. 

But `ArtifactGraphServiceImpl` currently falls back to `TreeSitterArtifactExtractor` from the JVM path and catches JNI availability failures. 

Tree-sitter itself is a good fit for broad language parsing because it is incremental, robust to syntax errors, and has official parsers/bindings across many languages. But for this repo, it should run as a **sidecar/subprocess/WASM/TypeScript layer**, not a fragile JVM JNI fallback. ([Tree-sitter][1])

---

## Recommended target architecture

### A. Source acquisition

Add source providers for:

1. Local folders.
2. GitHub repo/ref/PR/commit.
3. GitLab repo/ref/MR/commit.
4. ZIP/tar archives.
5. Existing YAPPC artifact registry.

Output:

```ts
RepositorySnapshot {
  snapshotId;
  provider;
  repoUrl?;
  branch?;
  commitSha?;
  rootPath;
  files;
  manifests;
  ignoreRules;
  createdAt;
}
```

### B. Inventory and classification

Enhance current scanner into a stable inventory compiler:

```ts
ArtifactInventory {
  snapshotId;
  files: ArtifactRecord[];
  packageBoundaries;
  workspaceBoundaries;
  dependencyManifests;
  buildSystems;
}
```

Fixes needed:

* Deterministic IDs.
* Real `.gitignore` support.
* Binary/generated file detection.
* Package boundary detection for pnpm/Nx/Gradle/Maven.
* Source maps for file ownership and module ownership.
* Parallel scanning with bounded concurrency.
* Incremental scan by checksum.

### C. Extraction passes

Use multi-pass extraction:

1. **File pass**: file metadata, language, framework, package/module.
2. **Symbol pass**: exported/imported symbols, classes, functions, components, routes, models.
3. **Relationship pass**: import resolution, JSX render edges, route edges, API calls, DB queries, state usage.
4. **Domain pass**: semantic models for UI, API, data, workflow, infra, tests.
5. **Residual pass**: unmodeled code fragments, confidence, review requirements.
6. **Security/privacy pass**: secrets, PII, dangerous APIs, license/import risk.

For Java round-trip refactoring, OpenRewrite is a strong fit because it uses Lossless Semantic Trees and can print modified trees back while preserving original formatting. ([OpenRewrite Docs][2])

For rule-based security/style/dangerous-pattern detection, a Semgrep-style rule layer is appropriate because Semgrep rules support pattern matching and data-flow-style analysis across code. ([Semgrep][3])

### D. Artifact graph and semantic model

Keep both:

* **ArtifactGraph** = source-faithful, queryable, provenance-rich graph.
* **SemanticProductModel** = YAPPC-facing logical representation.

Do not collapse them into one object. The graph should answer “where did this come from?” The semantic model should answer “what does the product mean?”

### E. Modification planner

YAPPC should not directly rewrite source files. It should produce model-level changes:

```ts
ModelChange {
  operation: 'add' | 'update' | 'remove' | 'move' | 'rename';
  targetElementId;
  desiredValue;
  confidence;
  requiresReview;
}
```

Then a compiler/decompiler planner converts those into source-level changes:

```ts
ChangePlan {
  modelChanges;
  affectedFiles;
  affectedSymbols;
  conflicts;
  residualRisks;
  reviewRequired;
}
```

### F. Patch emitter

Compile back as:

```ts
PatchSet {
  baseSnapshotId;
  files: FilePatch[];
  generatedArtifacts;
  deletedArtifacts;
  warnings;
  validationResults;
}
```

Patch emitter must preserve:

* Formatting.
* Comments.
* Unsupported/residual code.
* Imports.
* Local conventions.
* Existing design-system usage.
* Tests where possible.

---

## Prescriptive implementation plan

### Phase 1 — Stabilize compiler IR

**Files to update**

* `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`

  * Replace random UUIDs with deterministic artifact IDs.
  * Import/use `randomUUID` only where random IDs are intentional.
  * Add `.gitignore` parsing.
  * Add generated/binary/large-file classification.
  * Add workspace/package boundary metadata.

* `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/types.ts`

  * Add unresolved edge schema.
  * Add stable `sourceRef` / `symbolRef`.
  * Keep `targetId` UUID-only for resolved edges.

* `products/yappc/frontend/libs/yappc-artifact-compiler/src/extractors/typescript/component-extractor.ts`

  * Emit unresolved JSX usage edges first.
  * Do not place component names into `targetId`.
  * Add exact source spans for props, JSX usage, imports, and events.

**Validation**

* Same repo scanned twice at same commit produces identical IDs.
* Renaming unrelated files does not change IDs for unchanged artifacts.
* Unresolved edges never enter the resolved graph table as fake IDs.

### Phase 2 — Add source providers

**New files**

* `src/source/SourceProvider.ts`
* `src/source/LocalFolderSourceProvider.ts`
* `src/source/GitHubSourceProvider.ts`
* `src/source/GitLabSourceProvider.ts`
* `src/source/ArchiveSourceProvider.ts`
* `src/source/RepositorySnapshot.ts`

**Backend**

* Replace in-memory source import jobs with durable job tables.
* Support async scan jobs with progress, audit, cancellation, retry, and resume.
* Add provider credentials through governed secret references, not raw tokens.

### Phase 3 — Implement synthesis engine

**New files**

* `src/synthesis/engine.ts`
* `src/synthesis/modelSynthesizer.ts`
* `src/synthesis/confidence.ts`
* `src/synthesis/provenanceIndex.ts`
* `src/synthesis/residualPolicy.ts`

**Contract**

```ts
class SynthesisPipeline {
  compile(snapshot: RepositorySnapshot): Promise<CompiledArtifactModel>;
}
```

**Output**

```ts
CompiledArtifactModel {
  inventory;
  graph;
  semanticModel;
  residualIslands;
  provenanceIndex;
  diagnostics;
}
```

### Phase 4 — Connect to backend graph safely

**Files to update**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`

  * Resolve tenant/workspace/project from authenticated principal for ingest/analyze/merge too.
  * Reject request-body scope mismatch everywhere.

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`

  * Move heavy JGraphT work to a dedicated blocking executor.
  * Fix topological sort cycle detection.
  * Replace all-pairs reachability with bounded, paginated graph queries.
  * Remove JVM Tree-sitter fallback or isolate it behind a sidecar/subprocess adapter.

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`

  * Replace delete-then-reinsert with checksum-based incremental upsert.
  * Add graph snapshot/version ID to nodes/edges.
  * Add tombstone support for removed files/symbols.

### Phase 5 — Compile back to code patches

**New files**

* `src/decompiler/change-plan.ts`
* `src/decompiler/patch-set.ts`
* `src/decompiler/react/reactPatchEmitter.ts`
* `src/decompiler/prisma/prismaPatchEmitter.ts`
* `src/decompiler/workflow/workflowPatchEmitter.ts`
* `src/decompiler/residualPreserver.ts`

**Rules**

* Never overwrite full files unless generated-only.
* Prefer AST/CST patching.
* Preserve comments and residual islands.
* Mark low-confidence changes as review-required.
* Emit unified diff + review metadata.

### Phase 6 — YAPPC UX integration

**Files to update**

* `products/yappc/frontend/web/src/services/compiler/ImportSourceWorkflow.ts`

  * Change from single-source import to repository import job orchestration.
  * Display scan progress, confidence, extracted modules, residuals, and review-required items.

* `products/yappc/frontend/web/src/components/canvas/page/ImportWizard.tsx`

  * Add provider choices: GitHub, GitLab, local folder, archive, artifact registry.
  * Show “what will be imported” before applying.
  * Show confidence and unsupported/residual areas.

* `products/yappc/frontend/web/src/components/canvas/page/artifactCompilerBridge.ts`

  * Consume `SemanticProductModel`, not raw extracted component arrays only.
  * Preserve provenance from page artifact back to source file/symbol.

---

## First milestone I recommend

Name it:

**Artifact Compiler Milestone 1: Stable Repository IR and Source Snapshot Compiler**

Deliverables:

1. `SourceProvider` abstraction.
2. Local folder provider.
3. GitHub provider for repo/ref/commit.
4. Deterministic artifact IDs.
5. Real `.gitignore` support.
6. Resolved/unresolved edge split.
7. `compileRepository()` pipeline returning inventory + graph + semantic model + residuals.
8. Golden tests proving idempotent compile.
9. One small TSX fixture proving decompile → modify model → compile patch.

This milestone creates the foundation for everything else. Without it, graph analysis, AI edits, import UX, and code regeneration will keep producing brittle or non-repeatable results.

[1]: https://tree-sitter.github.io/tree-sitter/ "Introduction - Tree-sitter"
[2]: https://docs.openrewrite.org/ "OpenRewrite by Moderne | Large Scale Automated Refactoring | OpenRewrite Docs"
[3]: https://semgrep.dev/docs/writing-rules/overview/ "Overview | Semgrep"
