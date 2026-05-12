# Artifact Decompilation Report: Combined Architecture

Date: 2026-04-21 (Combined from 2026-04-20 and 2026-04-21 versions)

## Executive Summary

Ghatana already has the forward half of a bidirectional product compiler:
- Data/config → builder document
- Builder document → React/web code artifacts
- Design-system schema → contracts, manifests, tokens, registry entries

What it does not yet have is a true reverse pipeline that can ingest **arbitrary existing product codebases** (not just Ghatana-generated code) and reconstruct a durable, reviewable, configurable product model from it.

The core recommendation is to build a new artifact-to-model pipeline around two new intermediate layers:

1. **A loss-aware `ArtifactGraph`**
   This is a repository-wide parse/index of source files, symbols, styles, schemas, routes, stories, and relationships across **any codebase**.

2. **A canonical `SemanticProductModel`**
   This is the normalized configuration/model layer that builder, generators, previews, and future platform emitters can all consume.

The `BuilderDocument` should remain important, but it is too UI-centric to be the only reverse target. Existing products contain more than component trees: pages, data contracts, styles, themes, database schemas, stories, ownership markers, privacy annotations, and platform-specific logic. A decompiler has to preserve all of that or explicitly record what could not be modeled.

The right long-term shape is:
- existing artifacts → extractors → `ArtifactGraph`
- `ArtifactGraph` → semantic synthesis → `SemanticProductModel`
- `SemanticProductModel` → builder/design-system/runtime projections
- projections → generated code/artifacts
- regeneration and re-import both attach provenance, confidence, and residual loss markers

**Critical Design Principle**: This system must decompile **arbitrary codebases**, not just Ghatana-generated artifacts. It must work with:
- Any React/Next.js application
- Any Java/Spring service
- Any database schema (PostgreSQL, MySQL, etc.)
- Any CI/CD pipeline configuration
- Any state management system (Redux, Zustand, Context, XState)
- Any messaging system (Kafka, RabbitMQ, AWS SQS/SNS)
- Any caching layer (Redis, Memcached, in-memory)

All implementation must be self-contained using only open source permissive libraries (MIT, Apache-2.0, BSD-2/3-Clause). No external service dependencies or proprietary tools.

## Why This Matters

If Ghatana can only generate from greenfield config, adoption will be limited to new projects and partial rewrites. If it can decompile **any existing product** into governed models and then continue development from there, it becomes a migration and modernization platform instead of only an authoring platform.

That unlocks:
- bootstrap from **any** brownfield repository
- continuous round-trip between hand-authored code and model-driven editing
- governance over real-world product assets
- incremental migration instead of all-or-nothing replacement
- better AI assistance because the model can be derived from source of truth rather than hand-entered manually

## Repo Baseline

The current repo already contains strong building blocks:

- `@ghatana/ui-builder` has a document model, import path, validation, persistence, and codegen.
- `@ghatana/ds-schema` already defines component contracts, manifests, and DTCG-aligned token schemas.
- `@ghatana/ds-registry` already stores components, token sets, themes, and patterns.
- `@ghatana/ds-governance` already applies contribution gates.
- `@ghatana/design-system` now has recipe-driven composition and platform render plans.
- The wider repo contains real artifacts to reverse-engineer:
  - TSX components and pages
  - Storybook stories
  - Prisma schemas
  - SQL migrations
  - Java services and domain models

Representative code locations:

- `platform/typescript/ui-builder/src/core/import.ts`
- `platform/typescript/ui-builder/src/core/codegen.ts`
- `platform/typescript/ui-builder/src/core/types.ts`
- `platform/typescript/ds-schema/src/tokens/dtcg.ts`
- `platform/typescript/design-system/src/contracts/index.ts`
- `platform/typescript/ds-registry/src/registry/store.ts`
- `products/yappc/frontend/web/src/pages/*`
- `products/yappc/frontend/apps/api/prisma/schema.prisma`

## Current Strengths

### 1. The model already values round-trip fidelity

`@ghatana/ui-builder` explicitly models `RoundTripFidelity`, `LossPoint`, ownership, and import conflicts. That is the right direction for a decompiler because reverse compilation is never binary success/failure; it is confidence-graded.

### 2. The design-system schema is already structured enough to anchor extraction

Component contracts include:
- props
- slots
- events
- builder metadata
- examples
- accessibility metadata

Tokens are already aligned with DTCG-style structures and registry storage exists for components, themes, patterns, and token sets.

### 3. The composition model is now serializable

The design-system composition work already moved toward recipes, manifests, and platform-neutral render plans. That gives reverse tooling a stable target to synthesize into.

## Current Gaps

### 1. Reverse import is still heuristic

Current TSX import uses regex scanning, not AST or type-aware parsing. HTML import is also best-effort and flat. That is useful as a placeholder, but not sufficient for trustworthy decompilation of arbitrary codebases.

### 2. `BuilderDocument` is too narrow as the single reverse target

A product repo contains more than a component tree:
- component contracts
- pages and routes
- stories/examples
- token systems
- CSS and CSS-in-JS
- API schemas
- database schemas
- domain and service models

These need a richer canonical model than `BuilderDocument` alone.

### 3. No repository-wide symbol/provenance graph exists

The current stack can project and generate, but it does not yet maintain a repository graph of:
- exports/imports
- component usage
- route ownership
- style origin
- schema origin
- DB/entity relationships
- generated vs hand-authored boundaries

### 4. No explicit residual representation exists

A serious decompiler needs a safe place to put information it cannot normalize yet:
- unsupported expressions
- imperative side effects
- custom hooks
- embedded business logic
- SQL features not representable in the target schema
- CSS constructs not reducible to tokens

Without this, round-trip becomes lossy in hidden ways.

## Research Findings

### Typed AST extraction should be the primary reverse path for TS/TSX

The TypeScript Compiler API exposes `Program`, `CompilerHost`, and `SourceFile` as the core building blocks for whole-application analysis. That is the right base when you need symbol resolution, project-level context, and stable type-driven extraction.

`ts-morph` is a strong ergonomics layer on top of that when you want easier navigation and manipulation, but the architectural anchor should still be the compiler program.

**Recommendation:**
- use TypeScript compiler programs for repository indexing and semantic analysis
- use `ts-morph` for authoring/maintaining extraction passes faster

### Babel is useful, but it should be secondary in this repo

Babel's parser and traverse stack is excellent for JS/JSX/TS syntax handling and codemod-like transforms. It is ideal when syntactic traversal is enough.

For Ghatana, Babel is most useful for:
- fast syntax extraction
- codemod-style recovery
- JS-first repos without full TS config
- fallback parsing for isolated files

It is not enough by itself for reliable type-rich product reconstruction across a TypeScript-heavy monorepo.

### Tree-sitter should be the cross-language parsing substrate

Tree-sitter is especially valuable because this repository is not single-language. It gives fast, incremental parsing across many grammars and can serve as the universal structural index.

**2026 Update:** Tree-sitter 0.19.0 emphasizes incremental parsing, re-parsing only modified regions. A 2026 article reports that Tree-sitter re-parses only changed regions, reducing parse times by up to 70% on large files and improving error recovery. Tree-sitter's modular architecture supports dozens of grammars, making it an ideal structural index for TypeScript, Java, SQL and other languages in the repo. It also underpins knowledge graphs such as Codebase-Memory, which builds a cross-language graph over 66 languages and reduces token consumption by an order of magnitude while retaining ~83% quality.

**Recommendation:**
- Tree-sitter for repository-wide language coverage and fast indexing
- language-native semantic extractors for high-value domains

This means:
- TypeScript Compiler API for TS/TSX semantics
- JavaParser or OpenRewrite for Java semantics
- Prisma introspection/parsing for Prisma models
- SQL parsers and migration analyzers for DDL
- PostCSS and parse5 for styles and HTML
- Tree-sitter for structural indexing across all languages

### Lossless Semantic Trees and Moderne

OpenRewrite's Lossless Semantic Tree (LST) representation preserves formatting, comments and type information beyond a standard AST, enabling safe, deterministic refactoring. Moderne extends LST to support Java, JavaScript and TypeScript with full type attribution and cross-language control- and data-flow analysis. LSTs could serve as the backbone for multi-language extraction and faithful regeneration in Ghatana.

**Recommendation:** Use type-attributed LSTs to preserve whitespace, comments and type information for Java, JavaScript and TypeScript extraction. LSTs support deterministic regeneration and safe transformations.

### Styles need their own extraction pipeline

PostCSS explicitly supports parsing CSS and also input documents that include HTML or JS with CSS-in-JS blocks. That makes it a good fit for recovering:
- raw CSS rules
- custom properties
- media queries
- value references
- candidate design tokens

Style Dictionary and the DTCG spec matter here because token recovery should not stop at extracting CSS values. It should normalize them into token candidates, aliases, and theme overrides.

### HTML should be parsed spec-compliantly

parse5 is WHATWG HTML-compliant. That matters when decompiling real templates because heuristic HTML parsing is fragile around nesting, void elements, and serialization edge cases.

**2026 Update:** The WHATWG HTML specification is a living standard updated frequently; as of 19 April 2026, it remains current. parse5 adheres to this standard and should remain the default HTML parser for reliable extraction.

### Database reverse engineering should use native introspection first

Prisma introspection already knows how to read relational schemas and turn them into a data model, while also surfacing unsupported database features as warnings. That is a good pattern for the whole decompiler:
- extract what can be normalized
- preserve warnings for unsupported semantics
- never silently drop important structure

The repo also contains direct SQL and Java/JDBC persistence code, so Prisma is not enough on its own. But it is the strongest path wherever a Prisma schema or Prisma-managed DB already exists.

### SQL canonicalization and diff

SQLGlot is a multi-dialect SQL parser that can produce canonical abstract syntax trees, introspect ASTs and compute semantic diffs between queries. Using SQLGlot for SQL extraction allows normalization across dialects and enables tracking of schema evolution.

**Recommendation:** Use SQLGlot to parse and canonicalize SQL, compute semantic diffs and support multi-dialect normalization.

### Stories are a rich source of semantic examples

Storybook CSF defines a default export with component metadata and named story exports with args and configuration. This is valuable for reverse modeling because stories often encode:
- canonical prop sets
- visual states
- slot examples
- interaction expectations
- variants that should become schema examples or presets

**2026 Update:** Storybook 10.3 introduces the Model Context Protocol (MCP) that allows AI agents to query real components, build UIs and run tests within Storybook. It also adds CSF Factory patterns across frameworks and improved accessibility with ARIA semantics and keyboard navigation. These features make Storybook an even richer source of semantic examples and testable states for decompilation.

**Recommendation:** Extend story extraction to recognize CSF Factories and metadata in Storybook 10.3. Use MCP to feed extracted component models to AI agents for testing and preview.

### API specs and service boundaries should be normalized into reusable schemas

OpenAPI 3.2.0 provides a standard reusable component model for schemas and request/response shapes. Even if Ghatana does not use OpenAPI everywhere yet, its structure is useful as a target shape for API/service reverse modeling.

**2026 Update:** OpenAPI 3.2.0 remains backward-compatible but adds important features. Tag objects now support summaries and hierarchical relationships, allowing logical grouping of endpoints. The spec introduces first-class streaming media types (e.g., text/event-stream, application/jsonl) with item schemas. It also extends the list of supported HTTP methods and introduces an additionalOperations field to define non-standard verbs. Extractors should capture these details when constructing API models.

**Recommendation:** Update the ApiModel to support hierarchical tags, streaming media types and non-standard HTTP methods with additionalOperations. Map streaming sequences into typed event models.

### Design tokens specification

**2026 Update:** The Design Tokens Community Group published its first stable design tokens format (2025.10). It provides a vendor-neutral, production-ready representation of colours, typography and component decisions, supports modern colour spaces like Display P3 and Oklch, and enables cross-platform theming across iOS, Android, web and Flutter. Aligning token extraction and generation with this standard will future-proof Ghatana's design system.

**Recommendation:** Integrate the stable design tokens format 2025.10 into token extraction and generation. Normalize CSS values into token candidates using modern colour spaces and cross-platform categories.

## Proposed Architecture

### Layer 1: Artifact Inventory

Scan the repository and classify every file into artifact families:
- component implementation
- page/route
- story/example
- token/theme/style
- API schema
- DB schema or migration
- domain/service code
- configuration/build
- unknown/manual

Output:
- `ArtifactRecord`
- artifact kind
- language
- framework guess
- extractor eligibility
- source path
- import/export summary
- checksum
- incremental parse tokens (for Tree-sitter-based re-parsing)

The inventory should integrate with a file watcher to trigger selective re-extraction when files change.

### Layer 2: ArtifactGraph

Create a new repository-wide graph model:
- files
- symbols
- components
- routes
- styles
- tokens
- entities
- database objects
- stories
- API endpoints
- cross-references

Each node should carry:
- source location
- extractor id/version
- confidence
- provenance
- privacy/security flags
- residual fragments

Use Tree-sitter 0.19.0 for incremental parsing, storing parse trees and cross-language edges in a persistent knowledge graph. This graph enables low-latency queries, call-graph analysis and impact assessment across languages.

This is the missing "reverse compiler IR".

### Layer 3: SemanticProductModel

Normalize the graph into product-facing models:
- `ComponentModel`
- `PageModel`
- `LayoutModel`
- `TokenModel` (aligned with the stable design tokens spec 2025.10)
- `ThemeModel`
- `StyleModel`
- `DataModel` (from Prisma and SQLGlot extraction)
- `ApiModel` (supporting OpenAPI 3.2.0 features)
- `BehaviorModel`
- `PolicyModel`
- `ObservabilityModel`
- `StateModel` (stores, reducers, selectors, state flows)
- `InteractionModel` (events, chains, state transitions)
- `CacheModel` (strategies, TTL, invalidation rules)
- `WorkflowModel` (CI/CD, jobs, pipelines)
- `ScriptModel` (build, migration, utility scripts)

Each element carries provenance and confidence. Residual islands (CodeIsland, StyleIsland, QueryIsland, LogicIsland) store unmapped code with reasons and regeneration strategies.

Then project that model into:
- `BuilderDocument`
- `ComponentContract`
- `BuilderComponentManifest`
- DTCG token files
- patterns/presets
- codegen inputs

### Layer 4: Residual Model

Introduce explicit escape hatches:
- `CodeIsland`
- `StyleIsland`
- `QueryIsland`
- `LogicIsland`

Each island stores:
- original source
- normalized summary
- reason it could not be fully modeled
- review requirement
- regeneration strategy

This is critical. Full fidelity will fail if the system pretends every artifact is perfectly modelable.

### Layer 5: Bidirectional Merge Engine

Add a merge engine that compares:
- existing repo artifacts
- extracted semantic model
- generated output from that model

It should classify differences as:
- safe normalization
- semantic equivalence
- manual review required
- unsupported divergence

Round-trip generation should preserve semantics rather than textual equivalence, with residual islands capturing non-normalized regions. Use confidence and provenance to prioritise review.

## Additional Architectural Enhancements (2026)

Recent research suggests several enhancements:

### Incremental parsing and knowledge graph

Adopt Tree-sitter 0.19.0 for incremental parsing and build a persistent cross-language knowledge graph akin to Codebase-Memory. Each extraction updates the graph, enabling incremental re-analysis and efficient querying.

### Lossless Semantic Tree representation

Use type-attributed LSTs to preserve whitespace, comments and type information for Java, JavaScript and TypeScript extraction. LSTs support deterministic regeneration and safe transformations.

### Design tokens spec alignment

Integrate the stable design tokens format 2025.10 into token extraction and generation. Normalize CSS values into token candidates using modern colour spaces and cross-platform categories.

### Storybook CSF Factories and MCP

Extend story extraction to recognize CSF Factories and metadata in Storybook 10.3. Use MCP to feed extracted component models to AI agents for testing and preview.

### Extended API features

Update the ApiModel to support hierarchical tags, streaming media types and non-standard HTTP methods with additionalOperations. Map streaming sequences into typed event models.

### SQL canonicalization and diff

Incorporate SQLGlot to parse and canonicalize SQL, compute semantic diffs and support multi-dialect normalization.

### Persistent data model introspection

Schedule regular Prisma introspection to keep the DataModel aligned with the database and record unsupported features.

### Accessibility and inclusive design

Capture ARIA roles, accessibility semantics and contrast requirements from components and stories. Extend the SemanticProductModel with accessibility conformance metrics and remediation guidance.

## Reverse Extraction Strategy by Artifact Type

### 1. React components and hooks

**Target output:**
- component contracts
- slot topology
- prop schema
- event schema
- variant/state model
- accessibility metadata
- usage examples
- builder canvas hints

**Technique:**
- TS Compiler API for symbol/type extraction
- ts-morph for ergonomic traversal
- JSX AST for element/slot structure
- hook pattern analysis for state and effects
- story correlation to improve confidence
- Represent residual logic in CodeIsland when imperative code cannot be normalized

**Key challenge:**
- separating render semantics from arbitrary imperative logic

### 2. Pages and routes

**Target output:**
- page model
- layout hierarchy
- route metadata
- data dependencies
- auth/visibility guards
- SEO and title metadata

**Technique:**
- route file scanning
- component composition graph
- layout/header/sidebar extraction
- loader/action/fetch usage mapping
- Use incremental parsing and call-graph analysis to map component composition

### 3. Styles and themes

**Target output:**
- token candidates
- token alias graph
- theme overrides
- component-level style contracts
- residual CSS rules that cannot become tokens

**Technique:**
- PostCSS parsing
- CSS variable extraction
- repeated-value clustering
- mapping values into DTCG token candidates aligned with the 2025.10 spec
- Detect theme overrides and residual CSS constructs

### 4. Storybook stories and examples

**Target output:**
- contract examples
- variant matrices
- visual states
- canonical arg presets
- interaction fixtures

**Technique:**
- TS/JS AST on CSF files
- story args/decorators/parameters extraction
- Recognize CSF Factory patterns and link stories to components and variants
- Use MCP to run tests and generate preview states

### 5. Prisma schemas and SQL

**Target output:**
- entity models
- relations
- indexes
- constraints
- unsupported DB feature warnings
- migration lineage

**Technique:**
- Prisma parser/introspection where available
- SQLGlot to parse SQL migrations, canonicalize queries and compute diffs
- map both into a common DataModel
- record unsupported features as residuals

### 6. Java services and domain code

**Target output:**
- domain entities
- service boundaries
- endpoints
- persistence patterns
- event/message shapes
- policy annotations

**Technique:**
- JavaParser or OpenRewrite-based semantic extraction to build LSTs for Java
- Tree-sitter as a structural fallback
- Use the knowledge graph to link Java services with TS front-end models

### 7. State management (Redux, Zustand, Context, XState)

**Target output:**
- state tree structure
- action types and payloads
- reducers and state transitions
- selectors and derived state
- state machine definitions
- state flow diagrams between components

**Technique:**
- AST analysis of store configuration
- action creator/reducer pattern recognition
- hook-based state extraction (useState, useReducer, custom hooks)
- context provider/consumer mapping

### 8. Client-side caching (React Query, SWR, Apollo)

**Target output:**
- cache key structures
- cache strategies (stale-while-revalidate, cache-first, etc.)
- invalidation rules and dependencies
- refetch triggers and intervals
- optimistic update patterns

**Technique:**
- hook call pattern analysis
- query key extraction and normalization
- cache configuration parsing
- mutation/invalidation relationship mapping

### 9. Message queues and event streams

**Target output:**
- topic/queue definitions
- subscription patterns
- event schemas and payloads
- consumer groups and offsets
- dead-letter queue handling

**Technique:**
- configuration file parsing (Kafka, RabbitMQ, AWS SQS/SNS)
- producer/consumer code pattern recognition
- event schema extraction from type definitions
- streaming topology inference

### 10. Build and CI/CD configurations

**Target output:**
- workflow definitions (GitHub Actions, GitLab CI, Jenkins)
- job dependencies and parallelization
- build steps and environment configuration
- deployment pipelines and stages
- artifact generation and publishing

**Technique:**
- YAML configuration parsing
- build script analysis (Gradle, npm scripts, Make)
- dependency graph extraction
- environment variable and secret usage mapping

## Innovation Opportunities

### 1. Confidence-scored decompilation

Every extracted field should carry confidence, not just every file. Example:
- route path: 0.98
- component prop default: 0.93
- page auth requirement: 0.71
- accessibility semantic role: 0.54

This lets the UI prioritize review where it matters.

### 2. Provenance-first modeling

Every model element should remember:
- where it came from
- which extractor inferred it
- whether it was exact, inferred, or synthesized
- whether a human confirmed it

This will matter enormously for trust.

### 3. Residual islands instead of silent loss

Do not force full normalization. Preserve hard cases verbatim and let the model reference them.

### 4. Multi-source synthesis

The best model element often comes from combining artifacts:
- component implementation + stories + tests
- page file + route config + auth wrapper
- CSS + token definitions + Storybook theme examples
- Prisma schema + SQL migrations + service queries

### 5. Regeneration with semantic equivalence, not textual equivalence

The goal should not be to reproduce the same file byte-for-byte. The goal should be:
- same semantics
- same review guarantees
- bounded formatting drift
- explicit residual preservation for non-normalized regions

## High-Level Codebase Impact

### `@ghatana/ui-builder`

**What exists:**
- document model
- import/export/codegen
- fidelity/loss abstractions

**What should be added:**
- new reverse extractor interfaces
- `ArtifactGraph` types
- `SemanticProductModel` projection
- merge/confidence APIs
- residual island support
- repository-scale import, not only single-file import

### `@ghatana/ds-schema`

**What exists:**
- component contract schema
- manifest schema
- DTCG token schema

**What should be added:**
- schema for extracted components/pages/styles/entities/apis
- provenance and confidence schemas
- residual island schemas
- extractor diagnostics schema

### `@ghatana/ds-registry`

**What exists:**
- storage for components, tokens, themes, patterns

**What should be added:**
- storage for extracted semantic models
- versioned extractor results
- graph indexes
- repository snapshots

### `@ghatana/ds-governance`

**What exists:**
- contribution gates for contracts

**What should be added:**
- reverse-model quality gates
- required review thresholds
- forbidden loss classes
- security/privacy validation on extracted artifacts

### `@ghatana/design-system`

**What exists:**
- recipe-based composition and platform render plans

**What should be added:**
- reverse mapping from implementation patterns back to recipes/manifests
- standardized behavior signatures for extractor recognition
- extractor-friendly annotations for slots, states, roles, and tokens

### `@ghatana/ds-generator`

**What exists:**
- preset/token materialization

**What should be added:**
- reverse token normalization
- preset synthesis from existing brand/style sources

### New package recommendation

Introduce a dedicated package, likely:
- `@ghatana/artifact-compiler`
or
- `@ghatana/model-extraction`

**Suggested modules:**
- `inventory`
- `artifact-graph`
- `extractors/typescript`
- `extractors/storybook`
- `extractors/styles`
- `extractors/prisma`
- `extractors/sql`
- `extractors/java`
- `extractors/state` (Redux, Zustand, Context, XState)
- `extractors/cache` (React Query, SWR, Apollo)
- `extractors/messaging` (Kafka, RabbitMQ, event streams)
- `extractors/workflow` (CI/CD, build scripts)
- `synthesis`
- `merge`
- `diagnostics`
- `persistence`

## Recommended Phased Plan

### Phase 0: Canonical Model Design

Define:
- `ArtifactRecord`
- `ArtifactGraph`
- `SemanticProductModel`
- provenance/confidence/residual schemas

This is the most important step. Do not start with extractors before the intermediate representation is stable.

### Phase 1: TypeScript/Storybook/Prisma/State MVP

Build the first high-confidence reverse path for the most leverage:
- TS/TSX component extraction using the compiler API
- Storybook CSF and MCP extraction
- Prisma introspection
- DTCG/token normalization aligned with the 2025.10 spec
- Basic state store extraction (Redux, Zustand, Context)
- API route extraction (Next.js, Express patterns)
- Introduce Tree-sitter incremental parsing and a persistent knowledge graph

**Reason:**
- strongest overlap with current builder/design-system packages
- highest immediate chance of useful round-trip
- state management is critical for data-driven recreation of interactive UIs

### Phase 2: Page and Layout Extraction

Add:
- route/page extraction
- layout and shell modeling
- auth/visibility detection
- API dependency mapping
- incremental merge

### Phase 3: Style Normalization

Add:
- PostCSS pipeline
- token candidate mining
- theme synthesis
- residual style islands

### Phase 4: Java and SQL Deep Extraction

Add:
- JavaParser or OpenRewrite integration for Java LSTs
- SQLGlot for SQL migrations
- service/domain/API synthesis
- Cache layer extraction (Redis, Caffeine, etc.)
- Message queue integration analysis (Kafka, RabbitMQ patterns)
- Scheduled job/cron extraction

### Phase 5: Full Round-Trip Merge Workflow

Add:
- repository scan
- extract to semantic model
- review low-confidence findings
- regenerate selected slices
- semantic diff and merge back
- build and CI/CD workflow extraction
- script and automation decompilation

## Concrete Near-Term Deliverables

The original report enumerated several deliverables for the MVP. Additional deliverables reflecting 2026-era tooling include:

1. Define the new IR and schemas.
2. Replace regex TSX import with AST-backed import.
3. Add Storybook CSF extractor.
4. Add Prisma extractor that maps into a shared `DataModel`.
5. Add confidence/provenance to all extraction results.
6. Add residual islands before attempting "full fidelity".
7. Add a repository scanner command that emits an initial inventory and graph summary.
8. Define SemanticProductModel persistence strategy (storage, versioning, diff).
9. Add basic state management extractor (Redux/Zustand/Context patterns).
10. Add interaction/event flow extraction for user journey mapping.
11. Implement Tree-sitter incremental parsing and build a persistent knowledge graph for the repository, enabling incremental re-extraction and efficient queries.
12. Develop cross-language extractors that produce Lossless Semantic Trees for Java, JavaScript and TypeScript, leveraging OpenRewrite/Moderne.
13. Update the token extraction pipeline to align with the stable design tokens specification (2025.10) and support modern colour spaces.
14. Enhance API extractors to handle OpenAPI 3.2.0 features, including hierarchical tags, streaming responses and extended HTTP methods.
15. Integrate Storybook 10.3 CSF Factory parsing and the Model Context Protocol into story and component extraction.
16. Use SQLGlot to canonicalize SQL queries and migrations and compute semantic diffs across dialects.
17. Regularly run Prisma introspection to update the DataModel and track unsupported database features.

## SemanticProductModel Persistence

A robust persistence strategy is critical for enabling incremental updates, versioning and cross-repo sharing of the SemanticProductModel:

### Storage Architecture

- **Primary storage**: PostgreSQL with JSONB for model nodes and relationships
- **Graph relationships**: Dedicated edge table with provenance metadata and confidence scores
- **Blob storage**: Large artifacts (original source files, residual islands) in object storage
- **Query layer**: GraphQL API for model traversal and filtering
- **Knowledge graph**: Persist the knowledge graph built from Tree-sitter parse trees

### Versioning and Evolution

- **Append-only snapshots**: Every extraction produces a new model version
- **Diff compression**: Store only deltas between versions using semantic diff
- **Branching support**: Allow parallel extraction branches for experimental decompilation
- **Rollback capability**: Restore any previous model version for regeneration

### Incremental Updates

- **Change detection**: File system watching or git hook integration for trigger
- **Selective re-extraction**: Only re-extract changed artifacts and their dependents
- **Confidence propagation**: Update confidence scores based on extraction history
- **Merge workflow**: Automated merge of incremental updates with conflict resolution

### Query and Retrieval

- **Graph traversal**: Query component hierarchies, state flows, and interaction chains
- **Provenance filtering**: Find all elements from a specific extractor or source location
- **Confidence thresholding**: Filter model elements by minimum confidence score
- **Residual inspection**: Query unmodeled fragments requiring manual review

### Export and Import

- **Model serialization**: JSON/YAML export for version control and external tools
- **Model validation**: Schema validation before import to prevent corruption
- **Cross-repo sharing**: Import/export semantic models between product repositories
- **Template library**: Store reusable model patterns as templates

## Risks

### Biggest technical risk

Trying to use one universal model too early.

If `BuilderDocument` is forced to represent everything, the model will either become distorted or silently lossy.

### Biggest product risk

Promising "full round-trip" before confidence, provenance, and residuals exist.

### Biggest governance risk

Reverse-extracted configs may look authoritative even when inferred from incomplete evidence.

The UI and schema need to make uncertainty explicit.

## Open Source Permissive Library Requirements

All implementation must use only open source permissive libraries (MIT, Apache-2.0, BSD-2/3-Clause). No external service dependencies or proprietary tools.

### Recommended Libraries

**TypeScript/JavaScript:**
- TypeScript Compiler API (Apache-2.0)
- ts-morph (MIT)
- Babel (@babel/parser, @babel/traverse) (MIT)
- Tree-sitter (MIT)
- PostCSS (MIT)
- parse5 (MIT)

**Java:**
- JavaParser (Apache-2.0)
- OpenRewrite (Apache-2.0)

**SQL:**
- SQLGlot (MIT)

**Design Tokens:**
- Style Dictionary (Apache-2.0)

**Storybook:**
- @storybook/addon-essentials (MIT)
- @storybook/react (MIT)

**Knowledge Graph:**
- Consider building custom graph storage on PostgreSQL or using open source graph libraries

**HTML Parsing:**
- parse5 (MIT)

All libraries must be verified for permissive licensing before integration.

## Bottom Line

The idea is strong and strategically important.

Ghatana is already close to the right direction because it has:
- structured design-system schemas
- a builder document model
- round-trip fidelity concepts
- code generation
- token and registry abstractions

To make **arbitrary codebase decompilation** real, the platform now needs:
- a repository-scale artifact graph
- a richer semantic model than `BuilderDocument`
- typed extractors per artifact family (including state, cache, messaging, workflows)
- provenance/confidence/residual support
- a semantic merge workflow
- a robust persistence strategy for the SemanticProductModel
- incremental parsing with Tree-sitter for performance
- Lossless Semantic Trees for faithful regeneration
- alignment with modern standards (design tokens 2025.10, OpenAPI 3.2.0)

If done well, this becomes a differentiator: Ghatana would not only generate products from models, it could absorb **any existing product** into models and keep both views live. The addition of state management, interaction flows, caching, messaging, and workflow extraction ensures that the full product stack—not just the UI layer—can be represented, stored, and recreated in a data-driven manner.

## References

- TypeScript Compiler API: [Using the Compiler API](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)
- ts-morph: [Documentation](https://ts-morph.com/)
- Tree-sitter: [Introduction](https://tree-sitter.github.io/tree-sitter/index.html)
- Babel parser: [@babel/parser](https://babeljs.io/docs/babel-parser)
- Babel traverse: [@babel/traverse](https://babeljs.io/docs/babel-traverse)
- PostCSS: [API](https://postcss.org/api/)
- parse5: [parse5](https://parse5.js.org/)
- Prisma introspection: [What is introspection?](https://docs.prisma.io/docs/orm/prisma-schema/introspection)
- Design Tokens Community Group: [Design Tokens Format Module 2025.10](https://www.designtokens.org/TR/2025.10/format/)
- Style Dictionary: [Overview](https://styledictionary.com/getting-started/installation/)
- Storybook CSF: [Component Story Format](https://storybook.js.org/docs/api/csf)
- OpenAPI: [OpenAPI Specification v3.2.0](https://spec.openapis.org/oas/latest)
- JavaParser: [Getting Started](https://javaparser.org/getting-started.html)
- OpenRewrite: [Introduction to OpenRewrite](https://docs.openrewrite.org/)
- SQLGlot: [GitHub Repository](https://github.com/tobymao/sqlglot)
- Moderne: [Lossless Semantic Trees](https://www.moderne.io/)
- Codebase-Memory: [Knowledge Graph for Code](https://github.com/paul-gauthier/aider) (reference for Tree-sitter-based knowledge graphs)
