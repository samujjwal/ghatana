# Artifact Decompilation Report

Date: 2026-04-20

## Executive Summary

Ghatana already has the early half of a bidirectional product compiler:

- Data/config -> builder document
- Builder document -> React/web code artifacts
- Design-system schema -> contracts, manifests, tokens, registry entries

What it does not yet have is a true reverse pipeline that can ingest an existing product codebase and reconstruct a durable, reviewable, configurable product model from it.

The core recommendation is to build a new artifact-to-model pipeline around two new intermediate layers:

1. A loss-aware `ArtifactGraph`
   This is a repository-wide parse/index of source files, symbols, styles, schemas, routes, stories, and relationships.

2. A canonical `SemanticProductModel`
   This is the normalized configuration/model layer that builder, generators, previews, and future platform emitters can all consume.

The `BuilderDocument` should remain important, but it is too UI-centric to be the only reverse target. Existing products contain more than component trees: pages, data contracts, styles, themes, database schemas, stories, ownership markers, privacy annotations, and platform-specific logic. A decompiler has to preserve all of that or explicitly record what could not be modeled.

The right long-term shape is:

- existing artifacts -> extractors -> `ArtifactGraph`
- `ArtifactGraph` -> semantic synthesis -> `SemanticProductModel`
- `SemanticProductModel` -> builder/design-system/runtime projections
- projections -> generated code/artifacts
- regeneration and re-import both attach provenance, confidence, and residual loss markers

## Why This Matters

If Ghatana can only generate from greenfield config, adoption will be limited to new projects and partial rewrites. If it can decompile existing products into governed models and then continue development from there, it becomes a migration and modernization platform instead of only an authoring platform.

That unlocks:

- bootstrap from brownfield repositories
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

Current TSX import uses regex scanning, not AST or type-aware parsing. HTML import is also best-effort and flat. That is useful as a placeholder, but not sufficient for trustworthy decompilation.

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

Recommendation:

- use TypeScript compiler programs for repository indexing and semantic analysis
- use `ts-morph` for authoring/maintaining extraction passes faster

### Babel is useful, but it should be secondary in this repo

Babel’s parser and traverse stack is excellent for JS/JSX/TS syntax handling and codemod-like transforms. It is ideal when syntactic traversal is enough.

For Ghatana, Babel is most useful for:

- fast syntax extraction
- codemod-style recovery
- JS-first repos without full TS config
- fallback parsing for isolated files

It is not enough by itself for reliable type-rich product reconstruction across a TypeScript-heavy monorepo.

### Tree-sitter should be the cross-language parsing substrate

Tree-sitter is especially valuable because this repository is not single-language. It gives fast, incremental parsing across many grammars and can serve as the universal structural index.

Recommendation:

- Tree-sitter for repository-wide language coverage and fast indexing
- language-native semantic extractors for high-value domains

This means:

- TypeScript Compiler API for TS/TSX semantics
- JavaParser or OpenRewrite for Java semantics
- Prisma introspection/parsing for Prisma models
- SQL parsers and migration analyzers for DDL
- PostCSS and parse5 for styles and HTML

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

### Database reverse engineering should use native introspection first

Prisma introspection already knows how to read relational schemas and turn them into a data model, while also surfacing unsupported database features as warnings. That is a good pattern for the whole decompiler:

- extract what can be normalized
- preserve warnings for unsupported semantics
- never silently drop important structure

The repo also contains direct SQL and Java/JDBC persistence code, so Prisma is not enough on its own. But it is the strongest path wherever a Prisma schema or Prisma-managed DB already exists.

### Stories are a rich source of semantic examples

Storybook CSF defines a default export with component metadata and named story exports with args and configuration. This is valuable for reverse modeling because stories often encode:

- canonical prop sets
- visual states
- slot examples
- interaction expectations
- variants that should become schema examples or presets

### API specs and service boundaries should be normalized into reusable schemas

OpenAPI 3.2.0 provides a standard reusable component model for schemas and request/response shapes. Even if Ghatana does not use OpenAPI everywhere yet, its structure is useful as a target shape for API/service reverse modeling.

## Proposed Architecture

## Layer 1: Artifact Inventory

Create a repository scanner that classifies every file into artifact families:

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

## Layer 2: ArtifactGraph

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

This is the missing “reverse compiler IR”.

## Layer 3: SemanticProductModel

Normalize the graph into product-facing models:

- `ComponentModel`
- `PageModel`
- `LayoutModel`
- `TokenModel`
- `ThemeModel`
- `StyleModel`
- `DataModel`
- `ApiModel`
- `BehaviorModel`
- `PolicyModel`
- `ObservabilityModel`
- `StateModel` (stores, reducers, selectors, state flows)
- `InteractionModel` (events, chains, state transitions)
- `CacheModel` (strategies, TTL, invalidation rules)
- `WorkflowModel` (CI/CD, jobs, pipelines)
- `ScriptModel` (build, migration, utility scripts)

Then project that model into:

- `BuilderDocument`
- `ComponentContract`
- `BuilderComponentManifest`
- DTCG token files
- patterns/presets
- codegen inputs

## Layer 4: Residual Model

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

## Layer 5: Bidirectional Merge Engine

Add a merge engine that compares:

- existing repo artifacts
- extracted semantic model
- generated output from that model

It should classify differences as:

- safe normalization
- semantic equivalence
- manual review required
- unsupported divergence

This is where provenance, ownership, and review status from the existing builder model become powerful.

## Reverse Extraction Strategy By Artifact Type

### 1. React components and hooks

Target output:

- component contracts
- slot topology
- prop schema
- event schema
- variant/state model
- accessibility metadata
- usage examples
- builder canvas hints

Technique:

- TS Compiler API for symbol/type extraction
- JSX AST for element/slot structure
- hook pattern analysis for state and effects
- story correlation to improve confidence

Key challenge:

- separating render semantics from arbitrary imperative logic

### 2. Pages and routes

Target output:

- page model
- layout hierarchy
- route metadata
- data dependencies
- auth/visibility guards
- SEO and title metadata

Technique:

- route file scanning
- component composition graph
- layout/header/sidebar extraction
- loader/action/fetch usage mapping

### 3. Styles and themes

Target output:

- token candidates
- token alias graph
- theme overrides
- component-level style contracts
- residual CSS rules that cannot become tokens

Technique:

- PostCSS parsing
- CSS variable extraction
- repeated-value clustering
- mapping values into DTCG token candidates

### 4. Storybook stories and examples

Target output:

- contract examples
- variant matrices
- visual states
- canonical arg presets
- interaction fixtures

Technique:

- TS/JS AST on CSF files
- story args/decorators/parameters extraction

### 5. Prisma schemas and SQL

Target output:

- entity models
- relations
- indexes
- constraints
- unsupported DB feature warnings
- migration lineage

Technique:

- Prisma parser/introspection where available
- SQL AST or migration parser elsewhere
- map both into a common `DataModel`

### 6. Java services and domain code

Target output:

- domain entities
- service boundaries
- endpoints
- persistence patterns
- event/message shapes
- policy annotations

Technique:

- JavaParser or OpenRewrite-based semantic extraction
- Tree-sitter as a structural fallback

This is especially relevant here because much of the repo is Java, not just TypeScript.

### 7. State management (Redux, Zustand, Context, XState)

Target output:

- state tree structure
- action types and payloads
- reducers and state transitions
- selectors and derived state
- state machine definitions
- state flow diagrams between components

Technique:

- AST analysis of store configuration
- action creator/reducer pattern recognition
- hook-based state extraction (useState, useReducer, custom hooks)
- context provider/consumer mapping

### 8. Client-side caching (React Query, SWR, Apollo)

Target output:

- cache key structures
- cache strategies (stale-while-revalidate, cache-first, etc.)
- invalidation rules and dependencies
- refetch triggers and intervals
- optimistic update patterns

Technique:

- hook call pattern analysis
- query key extraction and normalization
- cache configuration parsing
- mutation/invalidation relationship mapping

### 9. Message queues and event streams

Target output:

- topic/queue definitions
- subscription patterns
- event schemas and payloads
- consumer groups and offsets
- dead-letter queue handling

Technique:

- configuration file parsing (Kafka, RabbitMQ, AWS SQS/SNS)
- producer/consumer code pattern recognition
- event schema extraction from type definitions
- streaming topology inference

### 10. Build and CI/CD configurations

Target output:

- workflow definitions (GitHub Actions, GitLab CI, Jenkins)
- job dependencies and parallelization
- build steps and environment configuration
- deployment pipelines and stages
- artifact generation and publishing

Technique:

- YAML configuration parsing
- build script analysis (Gradle, npm scripts, Make)
- dependency graph extraction
- environment variable and secret usage mapping

## Innovation Opportunities

## 1. Confidence-scored decompilation

Every extracted field should carry confidence, not just every file. Example:

- route path: 0.98
- component prop default: 0.93
- page auth requirement: 0.71
- accessibility semantic role: 0.54

This lets the UI prioritize review where it matters.

## 2. Provenance-first modeling

Every model element should remember:

- where it came from
- which extractor inferred it
- whether it was exact, inferred, or synthesized
- whether a human confirmed it

This will matter enormously for trust.

## 3. Residual islands instead of silent loss

Do not force full normalization. Preserve hard cases verbatim and let the model reference them.

## 4. Multi-source synthesis

The best model element often comes from combining artifacts:

- component implementation + stories + tests
- page file + route config + auth wrapper
- CSS + token definitions + Storybook theme examples
- Prisma schema + SQL migrations + service queries

## 5. Regeneration with semantic equivalence, not textual equivalence

The goal should not be to reproduce the same file byte-for-byte. The goal should be:

- same semantics
- same review guarantees
- bounded formatting drift
- explicit residual preservation for non-normalized regions

## High-Level Codebase Impact

### `@ghatana/ui-builder`

What exists:

- document model
- import/export/codegen
- fidelity/loss abstractions

What should be added:

- new reverse extractor interfaces
- `ArtifactGraph` types
- `SemanticProductModel` projection
- merge/confidence APIs
- residual island support
- repository-scale import, not only single-file import

### `@ghatana/ds-schema`

What exists:

- component contract schema
- manifest schema
- DTCG token schema

What should be added:

- schema for extracted components/pages/styles/entities/apis
- provenance and confidence schemas
- residual island schemas
- extractor diagnostics schema

### `@ghatana/ds-registry`

What exists:

- storage for components, tokens, themes, patterns

What should be added:

- storage for extracted semantic models
- versioned extractor results
- graph indexes
- repository snapshots

### `@ghatana/ds-governance`

What exists:

- contribution gates for contracts

What should be added:

- reverse-model quality gates
- required review thresholds
- forbidden loss classes
- security/privacy validation on extracted artifacts

### `@ghatana/design-system`

What exists:

- recipe-based composition and platform render plans

What should be added:

- reverse mapping from implementation patterns back to recipes/manifests
- standardized behavior signatures for extractor recognition
- extractor-friendly annotations for slots, states, roles, and tokens

### `@ghatana/ds-generator`

What exists:

- preset/token materialization

What should be added:

- reverse token normalization
- preset synthesis from existing brand/style sources

### New package recommendation

Introduce a dedicated package, likely:

- `@ghatana/artifact-compiler`
or
- `@ghatana/model-extraction`

Suggested modules:

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

- TS/TSX component extraction
- Storybook extraction
- Prisma extraction
- DTCG/token normalization
- Basic state store extraction (Redux, Zustand, Context)
- API route extraction (Next.js, Express patterns)

Reason:

- strongest overlap with current builder/design-system packages
- highest immediate chance of useful round-trip
- state management is critical for data-driven recreation of interactive UIs

### Phase 2: Page and Layout Extraction

Add:

- route/page extraction
- layout and shell modeling
- auth/visibility detection
- API dependency mapping

### Phase 3: Style Normalization

Add:

- PostCSS pipeline
- token candidate mining
- theme synthesis
- residual style islands

### Phase 4: Java and SQL Deep Extraction

Add:

- JavaParser or OpenRewrite integration
- SQL migration/entity extraction
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

1. Define the new IR and schemas.
2. Replace regex TSX import with AST-backed import.
3. Add Storybook CSF extractor.
4. Add Prisma extractor that maps into a shared `DataModel`.
5. Add confidence/provenance to all extraction results.
6. Add residual islands before attempting “full fidelity”.
7. Add a repository scanner command that emits an initial inventory and graph summary.
8. Define SemanticProductModel persistence strategy (storage, versioning, diff).
9. Add basic state management extractor (Redux/Zustand/Context patterns).
10. Add interaction/event flow extraction for user journey mapping.

## Risks

### Biggest technical risk

Trying to use one universal model too early.

If `BuilderDocument` is forced to represent everything, the model will either become distorted or silently lossy.

### Biggest product risk

Promising “full round-trip” before confidence, provenance, and residuals exist.

### Biggest governance risk

Reverse-extracted configs may look authoritative even when inferred from incomplete evidence.

The UI and schema need to make uncertainty explicit.

## SemanticProductModel Persistence

To support data-driven product development with full round-trip capability, the `SemanticProductModel` requires a robust persistence strategy:

### Storage Architecture

- **Primary storage**: PostgreSQL with JSONB for model nodes and relationships
- **Graph relationships**: Dedicated edge table with provenance metadata and confidence scores
- **Blob storage**: Large artifacts (original source files, residual islands) in object storage
- **Query layer**: GraphQL API for model traversal and filtering

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

## Bottom Line

The idea is strong and strategically important.

Ghatana is already close to the right direction because it has:

- structured design-system schemas
- a builder document model
- round-trip fidelity concepts
- code generation
- token and registry abstractions

To make existing-product decompilation real, the platform now needs:

- a repository-scale artifact graph
- a richer semantic model than `BuilderDocument`
- typed extractors per artifact family (including state, cache, messaging, workflows)
- provenance/confidence/residual support
- a semantic merge workflow
- a robust persistence strategy for the SemanticProductModel

If done well, this becomes a differentiator: Ghatana would not only generate products from models, it could absorb existing products into models and keep both views live. The addition of state management, interaction flows, caching, messaging, and workflow extraction ensures that the full product stack—not just the UI layer—can be represented, stored, and recreated in a data-driven manner.

## References

- TypeScript Compiler API:
  [Using the Compiler API](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)
- ts-morph:
  [Documentation](https://ts-morph.com/)
- Tree-sitter:
  [Introduction](https://tree-sitter.github.io/tree-sitter/index.html)
- Babel parser:
  [@babel/parser](https://babeljs.io/docs/babel-parser)
- Babel traverse:
  [@babel/traverse](https://babeljs.io/docs/babel-traverse)
- PostCSS:
  [API](https://postcss.org/api/)
- parse5:
  [parse5](https://parse5.js.org/)
- Prisma introspection:
  [What is introspection?](https://docs.prisma.io/docs/orm/prisma-schema/introspection)
- Design Tokens Community Group:
  [Design Tokens Format Module 2025.10](https://www.designtokens.org/TR/2025.10/format/)
- Style Dictionary:
  [Overview](https://styledictionary.com/getting-started/installation/)
- Storybook CSF:
  [Component Story Format](https://storybook.js.org/docs/api/csf)
- OpenAPI:
  [OpenAPI Specification v3.2.0](https://spec.openapis.org/oas/latest)
- JavaParser:
  [Getting Started](https://javaparser.org/getting-started.html)
- OpenRewrite:
  [Introduction to OpenRewrite](https://docs.openrewrite.org/)
