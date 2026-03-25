# YAPPC Structure Simplification Plan

**Status:** Proposed  
**Date:** 2026-03-24  
**Scope:** Product structure, module taxonomy, ownership boundaries, documentation truth, migration sequencing

## Executive Summary

YAPPC is not primarily suffering from a "too many files" problem. It is suffering from a "too many competing mental models" problem.

Today, at least three different structures claim to be the source of truth:

1. The active Gradle graph in `settings.gradle.kts`
2. The physical product layout under `core/`, `services/`, `frontend/`, `infrastructure/`, `config/`, and `docs/`
3. The architecture and README documents that still describe removed, renamed, merged, or hypothetical modules

That mismatch makes the product hard to navigate, hard to extend safely, and expensive to govern. Engineers cannot reliably answer:

- What is the canonical public contract?
- Which modules are runtime entry points versus reusable capabilities?
- Which folders are active versus compatibility or migration residue?
- Where deployment, operations, and product runtime boundaries actually sit?
- Which dependencies are allowed to cross from product logic into platform, AEP, and Data Cloud?

The path to a world class structure is not a big-bang rewrite. It is a controlled simplification program that makes one architecture explicit, makes naming tell the truth, and adds enforcement so the codebase cannot drift back into ambiguity.

## Review Findings

### 1. The codebase exposes conflicting source-of-truth models

Evidence:

- `settings.gradle.kts` includes `:core:domain`, `:core:domain:service`, `:core:domain:task`, `:core:lifecycle`, and `:core:framework`
- the active source-bearing directories under `core/` are instead `yappc-domain/`, `yappc-services/`, `yappc-infrastructure/`, `yappc-api/`, `yappc-agents/`, plus capability folders such as `ai/`, `agents/`, `scaffold/`, `knowledge-graph/`, and `refactorer/`
- `docs/ARCHITECTURE.md` and `README.md` still describe `api/`, `domain/`, `ai/`, and `backend/` as if they were the dominant physical layout

Impact:

- onboarding cost is high
- architecture discussions become semantic instead of concrete
- refactors are risky because names no longer imply reality

### 2. The backend has too many aggregator modules

Current behavior shows multiple modules acting as catch-all integration surfaces:

- `services/` is an application module that depends on platform libraries plus many YAPPC modules
- `services:platform` mixes application-facing service composition with infrastructure access
- `services:lifecycle` mixes orchestration, AEP integration, AI integration, agents, scaffold concerns, and plugin usage
- `core:yappc-services` is also a large orchestration and transformation hub

Impact:

- business logic, composition logic, and runtime bootstrapping are blurred
- dependency boundaries become harder to reason about
- changes in one capability cascade into broad rebuild and retest scopes

### 3. YAPPC has dual domain contract sources

Current state:

- `core/yappc-domain` exists as an internal domain implementation and exports substantial APIs
- `libs/java/yappc-domain` also exists as a shared domain contract library
- `core/yappc-services`, `core/ai`, `infrastructure/datacloud`, `services:platform`, and `services:lifecycle` depend on one or both

Impact:

- developers must guess whether to depend on the internal domain or the shared contract
- domain drift and duplicate model evolution become more likely
- public versus internal model ownership is unclear

### 4. Legacy and active structures still coexist in the same conceptual namespace

Observed residue:

- `.legacy-backup/` is present under major backend and frontend areas
- settings, docs, and comments still refer to removed or merged modules
- migration scripts and migration-oriented documentation remain close to active code

Impact:

- engineers cannot quickly distinguish live architecture from archaeological material
- accidental reintroduction risk is high
- architecture reviews spend time on dead structure

### 5. Deployment and infrastructure assets are mixed with runtime adapter code

Current state under `infrastructure/`:

- `datacloud/` is an active Java adapter module
- `docker/`, `helm/`, `kubernetes/`, `kustomize/`, `monitoring/`, and `persistence/` are deployment and operational assets

Impact:

- "infrastructure" means two different things: runtime adapters and operational deployment
- code ownership becomes ambiguous
- navigation by intent is poor

### 6. Frontend consolidation is only partially complete

Current state under `frontend/libs/`:

- canonical consolidated packages exist such as `yappc-ui`, `yappc-ai`, `yappc-core`, and `yappc-state`
- numerous compatibility or pre-consolidation packages also remain in the active workspace: `base-ui`, `development-ui`, `initialization-ui`, `navigation-ui`, `theme`, `messaging`, `realtime`, `notifications`, `config-hooks`, `crdt`, `types`, `utils`, and others
- lint rules prohibit several old imports, but the packages still live in the main workspace and continue to occupy mental space
- `frontend/scripts/architectural-fitness.ts` still maps imports to `libs/ui-new` and `libs/canvas-new`, which do not match the visible workspace layout

Impact:

- the workspace still looks larger than the real product API surface
- build and tooling conventions are out of sync with actual library names
- deprecation and compatibility are not clearly separated from first-class libraries

### 7. Documentation currently amplifies confusion instead of reducing it

Observed patterns:

- README describes a simplified architecture that does not reflect the physical or build structure
- `docs/ARCHITECTURE.md` describes removed or non-canonical directories
- some architecture files describe a target-state that was never fully completed or was later superseded

Impact:

- docs cannot be trusted as a navigation aid
- people fall back to grep and local intuition
- architecture debt compounds because the written model is stale

### 8. External product dependencies leak directly into capability modules

Examples:

- agent modules depend on AEP runtime and registry directly
- lifecycle services depend directly on AEP contracts, orchestrator, and runtime
- knowledge graph and service modules depend directly on Data Cloud modules

Impact:

- YAPPC capability modules are not cleanly separated from integration adapters
- testability and future portability are weaker than necessary
- dependency graphs become denser than the business model requires

## Root Cause

The core problem is that YAPPC has grown through several partially completed restructures:

- layer-oriented naming
- capability-oriented naming
- consolidated `yappc-*` naming
- frontend library consolidation
- legacy backup migration
- service thinning and merge programs

Each of those moves improved one part of the system, but the product never fully re-established a single explicit topology afterward.

## Simplification Goals

The target structure should optimize for five qualities:

1. **Findability** - a new engineer should identify the correct home for a change in under five minutes.
2. **Truthful naming** - module names should describe what they are, not what they used to be.
3. **Stable contracts** - public contracts should have one canonical location.
4. **Low-coupling extensibility** - new capabilities should plug into clear seams, not broad aggregators.
5. **Governed evolution** - the build, docs, and lint rules should prevent drift.

## Target Architecture Model

The product should standardize on a capability-plus-boundary model.

### A. Top-level product layout

Recommended target layout:

```text
products/yappc/
├── apps/                 # Runtime entry points only
├── backend/              # Reusable backend capabilities and adapters
├── frontend/             # UI apps and frontend packages
├── contracts/            # Stable product-wide contracts and schemas
├── config/               # Runtime and design-time configuration
├── deployment/           # Docker, Helm, Kubernetes, monitoring, ops assets
├── docs/                 # Human documentation
├── examples/             # Plugin and integration examples
└── tools/                # Validation and developer tooling
```

Interpretation:

- `apps/` contains executable products or deployable entry points
- `backend/` contains reusable implementation modules, not deployables
- `contracts/` contains the stable models/schemas other modules consume
- `deployment/` contains operational assets only

### B. Backend taxonomy

Recommended backend grouping:

```text
backend/
├── contracts/            # If kept inside backend instead of top-level contracts
├── capabilities/
│   ├── ai/
│   ├── agents/
│   ├── scaffold/
│   ├── knowledge-graph/
│   └── refactorer/
├── domain/
│   ├── model/
│   └── services/
├── adapters/
│   ├── datacloud/
│   ├── aep/
│   ├── http/
│   └── plugin/
└── support/
    └── shared/
```

This can be reached incrementally without immediately renaming every Gradle module. The critical idea is semantic grouping:

- **domain** = YAPPC business concepts and orchestration rules
- **capabilities** = product capabilities that implement specialized behavior
- **adapters** = dependencies on external products/frameworks such as Data Cloud, AEP, HTTP, plugin SPI
- **apps** = actual deployable runtimes

### C. Frontend taxonomy

Recommended frontend model:

```text
frontend/
├── apps/
│   ├── web/
│   └── api/
├── packages/
│   ├── ui/
│   ├── canvas/
│   ├── ai/
│   ├── state/
│   └── core/
├── compat/               # Temporary import-compat packages only
└── tooling/
```

Rules:

- only the five canonical libraries remain as first-class workspace packages
- all compatibility packages move to `compat/` or leave the workspace entirely
- deprecated packages stop appearing next to canonical ones in `libs/`

### D. Contracts model

There should be one canonical product contract layer:

- `libs/java/yappc-domain` becomes the only stable shared contract module, or
- it is moved to `contracts/java/yappc-domain` if the product wants contracts to be visibly first-class

`core/yappc-domain` should then be re-scoped to one of two roles:

1. internal domain implementation only, renamed to reflect that role
2. merged into the application/domain layer if it exists only to duplicate contract concepts

The important outcome is that engineers never have to ask which domain module is the public one.

## Recommended Target Module Set

This is the recommended steady-state module portfolio. The point is not to collapse everything. The point is to group by intent and reduce ambiguous entry surfaces.

### Deployable apps

- `apps:api` - current `services/` responsibilities, reduced to bootstrapping and wiring
- `apps:lifecycle` - only if lifecycle truly runs as a separate deployable surface
- `frontend:apps:web`
- `frontend:apps:api` if retained as a user-facing API backend

### Backend contracts and domain

- `contracts:java:yappc-domain` - canonical shared domain contracts
- `backend:domain:services` - business orchestration and internal domain logic

### Backend capabilities

- `backend:capabilities:ai`
- `backend:capabilities:agents`
- `backend:capabilities:scaffold`
- `backend:capabilities:knowledge-graph`
- `backend:capabilities:refactorer`

### Backend adapters

- `backend:adapters:datacloud`
- `backend:adapters:aep`
- `backend:adapters:http`
- `backend:adapters:plugin`

### Backend support

- `backend:support:shared` only if a real shared kernel remains after cleanup

## Concrete Mapping From Current To Target

| Current | Target role | Recommendation |
|---|---|---|
| `services/` | app bootstrapping | Rename conceptually to `apps:api`; remove catch-all business logic over time |
| `services:platform` | HTTP/application adapter | Fold into `backend:adapters:http` or into `apps:api` wiring |
| `services:lifecycle` | lifecycle app or lifecycle capability | Keep only if separately deployable; otherwise move orchestration logic into domain/capabilities |
| `core/yappc-services` | domain services | Keep as main business orchestration module, but narrow scope |
| `core/yappc-domain` | internal domain implementation | Either rename or merge to avoid confusion with contract module |
| `libs/java/yappc-domain` | shared contracts | Make this the sole canonical contract module |
| `core/yappc-infrastructure` | adapter/support | Split external integration concerns from product support concerns |
| `infrastructure/datacloud` | Data Cloud adapter | Keep, but move conceptually under adapters |
| `core/yappc-api` | API adapter | Merge with HTTP/app wiring or clearly define why it remains distinct |
| `core/yappc-agents` | agent capability integration | Merge with `core/agents` or redefine as app wiring, not both |
| `core/ai` | capability | Keep |
| `core/agents/*` | capability | Keep, but simplify specialist hierarchy only if usage data supports it |
| `core/scaffold/*` | capability | Keep, but unify public entry surface |
| `core/refactorer/*` | capability | Keep, but reduce adapter leakage |
| `core/knowledge-graph` | capability | Keep, but isolate external dependencies behind adapters |
| `core/spi` | plugin adapter/contract | Move mentally under adapters/plugin |
| `infrastructure/docker|helm|kubernetes|kustomize|monitoring` | deployment assets | Move to `deployment/` |

## Boundary Rules For The Future State

These rules are the most important part of the plan. Without them, the structure will drift again.

### Rule 1: Apps can depend on domain, capabilities, and adapters

Apps are composition roots. They wire dependencies and expose runtime entry points. They should not become a second business logic layer.

### Rule 2: Domain can depend on contracts and platform libraries, but not directly on product-external adapters

If domain logic needs AEP or Data Cloud behavior, it should consume an interface owned by YAPPC and implemented by adapters.

### Rule 3: Capability modules can depend on contracts and domain, but external product dependencies must be pushed to adapter seams

For example, direct AEP usage inside generic agent capability code should be reduced where possible.

### Rule 4: Adapter modules own Data Cloud, AEP, HTTP, plugin, and infrastructure-framework coupling

This localizes impact when external products evolve.

### Rule 5: Contracts never depend on implementation modules

Contracts are the most stable layer.

### Rule 6: Compatibility packages are not first-class packages

Deprecated frontend packages must move out of the main workspace surface.

### Rule 7: Deployment assets never share a namespace with runtime adapter code

`deployment/` and `backend/adapters/` must remain separate.

## Simplification Program

### Phase 0 - Freeze and establish truth

Objective:

- stop structural drift while the cleanup is underway

Actions:

- declare a single architecture owner for YAPPC simplification
- freeze net-new module creation until the taxonomy is approved
- classify all current modules as `app`, `domain`, `capability`, `adapter`, `support`, `compat`, or `legacy`
- mark which modules are canonical, transitional, or scheduled for removal

Success criteria:

- every active module has one explicit architectural role
- every non-canonical module has a retirement or compatibility plan

### Phase 1 - Repair truth sources

Objective:

- make settings, build files, and docs tell the same story

Actions:

- remove or correct stale includes and comments in `settings.gradle.kts`
- update `README.md`, `docs/ARCHITECTURE.md`, and architecture overview docs to match reality
- fix tooling references such as frontend architectural fitness mappings that point at non-existent paths
- introduce one authoritative YAPPC module catalog doc

Success criteria:

- product docs match the active build graph
- no architecture doc describes removed modules as current

### Phase 2 - Separate deployables from reusable modules

Objective:

- reduce confusion between runtime entry points and reusable code

Actions:

- treat current `services/` as an app module only
- move bootstrapping, distribution, and runtime packaging concerns into `apps/`
- shrink `services:platform` and `services:lifecycle` so they become either true deployables or true reusable modules, not both

Success criteria:

- engineers can identify all deployables from one directory and naming pattern
- app modules primarily wire dependencies and expose runtime entry points

### Phase 3 - Canonicalize contracts and domain

Objective:

- remove domain ambiguity

Actions:

- designate `libs/java/yappc-domain` or a moved equivalent as the single contract home
- rename or narrow `core/yappc-domain` to an internal-only role
- remove duplicate exports and documentation that suggest both are peer public APIs

Success criteria:

- there is one canonical domain contract module
- dependency guidance becomes obvious and enforceable

### Phase 4 - Re-scope adapters

Objective:

- isolate external coupling

Actions:

- move Data Cloud integration under an adapter taxonomy
- introduce an AEP adapter seam for lifecycle and agent flows that currently depend directly on AEP modules
- move plugin SPI into an explicit plugin adapter/contract area
- keep HTTP-specific integration at the adapter/app boundary

Success criteria:

- core domain and capability modules no longer import external product concerns casually
- integration surfaces become explicit and testable

### Phase 5 - Frontend workspace rationalization

Objective:

- make the frontend package surface match the real conceptual surface

Actions:

- keep only canonical packages as first-class workspace packages
- move deprecation shims into `frontend/compat/` or publish-only packages
- update workspace scripts, lint rules, docs, and path mappings to match actual canonical packages
- define one clear package map for UI, canvas, AI, state, and core

Success criteria:

- the visible frontend workspace packages are the packages people should actually build against
- compatibility packages no longer look like equal peers to canonical ones

### Phase 6 - Move operational assets to deployment

Objective:

- make runtime code and operational assets navigable by intent

Actions:

- move `infrastructure/docker`, `helm`, `kubernetes`, `kustomize`, `monitoring`, and related operational assets to `deployment/`
- keep runtime adapter code separate under backend adapters
- align ownership files with the new structure

Success criteria:

- `deployment/` contains operational assets only
- runtime adapter code no longer shares namespace with deployment manifests

### Phase 7 - Add governance and guardrails

Objective:

- prevent regression

Actions:

- add module-role metadata or a simple module catalog checked in CI
- add dependency rules for allowed layer directions
- add checks for deprecated frontend imports and disallowed workspace package usage
- add checks to ensure docs reference only active modules

Success criteria:

- structural regressions fail in CI
- naming and dependency discipline become automated instead of tribal

## Recommended First 30 Days

### Week 1

- publish module catalog and role classification
- document canonical backend and frontend package/module surfaces
- identify all stale architecture documents and label them `historical`, `transitional`, or `authoritative`

### Week 2

- repair settings/build/doc mismatches
- stop treating compatibility frontend packages as first-class modules
- define the canonical contract module decision and communicate it broadly

### Week 3

- split deployable bootstrapping from reusable modules
- design AEP and Data Cloud adapter seams
- move deployment assets under a dedicated top-level deployment area

### Week 4

- add dependency governance checks
- remove or quarantine the highest-confusion residues
- publish migration guides for contributors

## Non-Goals

This plan does **not** recommend:

- collapsing all backend modules into a monolith
- removing capability specialization that still has genuine product value
- renaming everything at once for cosmetic purity
- forcing frontend and backend into the same folder taxonomy if their tooling needs differ

The goal is clarity and evolvability, not aesthetic symmetry.

## Decision Recommendations

### Recommendation 1

Treat `services/` as an app, not as a domain surface.

### Recommendation 2

Designate one and only one YAPPC domain contract module.

### Recommendation 3

Move operational assets out of `infrastructure/` into `deployment/`.

### Recommendation 4

Reduce direct AEP and Data Cloud coupling from capability modules by adding adapter seams.

### Recommendation 5

Make frontend compatibility packages second-class citizens immediately.

### Recommendation 6

Replace stale architecture narratives with a single authoritative module catalog plus a small set of maintained architecture docs.

## Success Metrics

Track the cleanup with objective measures:

- time for a new engineer to locate the right module for a change
- number of architecture docs that describe active structure accurately
- number of first-class frontend packages visible in the workspace
- number of modules classified as `transitional` or `compat`
- number of direct YAPPC capability dependencies on AEP/Data Cloud modules
- percentage of active modules with explicit ownership and role classification
- CI checks covering dependency rules and deprecated structure usage

## Final Assessment

YAPPC already contains strong implementation assets. The main issue is not lack of capability. It is structural ambiguity.

The product can become significantly easier to understand and safer to extend by doing four things well:

1. pick one architecture model
2. rename or regroup modules so the names tell the truth
3. isolate external integrations behind explicit adapters
4. enforce the result with documentation and build rules

If that work is done in the phased order above, YAPPC can keep all current capabilities while becoming substantially easier to navigate, maintain, and evolve.