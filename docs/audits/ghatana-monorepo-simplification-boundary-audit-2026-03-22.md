# Ghatana Monorepo Simplification, Boundary, Coupling, and Cohesion Audit

Date: 2026-03-22  
Repository: `ghatana`  
Primary scope: `products/data-cloud`, `products/aep`, `products/yappc`  
Secondary scope: `platform/java`, `platform/typescript`, `shared-services`, root build/workspace governance

## Audit Basis

This audit is evidence-based and grounded in:

- Root workspace/build config: `package.json`, `pnpm-workspace.yaml`, `turbo.json`, `settings.gradle.kts`, `build.gradle.kts`
- Product docs and product-level build files
- Package manifests for TypeScript workspaces
- Gradle dependency declarations across `data-cloud`, `aep`, `yappc`, `platform/java`, and `shared-services`
- Source tree shape, directory counts, file counts, and manual hotspot inspection
- Duplicate-module checks, alias/compatibility layers, and retired-but-still-present module analysis

### High-Signal Evidence Anchors

- `products/data-cloud/platform` contains `630` Java/Kotlin files and `174,630` lines
- `products/yappc/core` contains `1,649` Java/Kotlin files and `254,442` lines
- `products/yappc/frontend/libs/ui` contains `839` TS/TSX files and `189,470` lines
- `products/yappc/frontend/libs/canvas` contains `583` TS/TSX files and `289,602` lines
- `products/aep/platform-*` directories exist but contain only `build.gradle.kts` files
- `products/yappc/build.gradle.kts` bans reintroduction of `services:ai`, `services:scaffold`, `core:scaffold:packs`, `backend:websocket`, `infrastructure:security`, and `launcher`, but root `settings.gradle.kts` still includes all of them
- `products/yappc/services/ai/.../AiServiceModule.java` and `products/yappc/services/lifecycle/.../AiServiceModule.java` are byte-identical
- `products/yappc/services/scaffold/.../ScaffoldServiceModule.java` and the copy under `services/lifecycle` are byte-identical
- `products/aep/ui/vite.config.ts` and `vitest.config.ts` still point to nonexistent `platform/typescript/capabilities/design-system` and `canvas-core` paths

---

## Part 1 - Executive Assessment

### 1. Executive Verdict on `ghatana` Structure

`ghatana` is not currently structured for simplification, loose coupling, or safe refactoring. The repo has the right high-level intent, but the actual codebase shows boundary drift, duplicate “merged” modules, oversized product kernels, and weak library governance.

### 2. Top Simplification Findings

- `products/yappc` contains two active structural stories at once: the “merged” story in `products/yappc/settings.gradle.kts` and the still-live pre-merge modules in root `settings.gradle.kts`.
- `products/data-cloud/platform` is a kitchen-sink module, not a platform. It mixes API, client, plugins, analytics, config, deployment, infrastructure, observability, workflow, and product logic.
- `products/aep` carries both canonical modules and abandoned `platform-*` wrapper shells, preserving historical scaffolding without current value.
- `platform/typescript` includes wrappers and dead zones: `@ghatana/utils` is a compatibility veneer over `@ghatana/platform-utils`, and `platform/typescript/capabilities` is empty.

### 3. Top Coupling Problems

- YAPPC directly depends on AEP and Data Cloud implementation packages, not just stable product-owned contracts.
- AEP server depends on `products:data-cloud:platform`, not just `products:data-cloud:spi`.
- YAPPC backend and services embed AEP-specific sources and Data Cloud domain classes directly into local modules.
- Frontend alias layers keep old and new package names active simultaneously, making import boundaries advisory rather than real.

### 4. Top Cohesion Problems

- `products/data-cloud/platform`
- `products/yappc/services/lifecycle`
- `products/yappc/backend/api`
- `products/yappc/frontend/libs/ui`
- `products/yappc/frontend/libs/canvas`
- `platform/java/ai-integration`

Each of these units has multiple unrelated reasons to change.

### 5. Main Boundary Failures

- “Shared” is frequently used as a migration landing zone rather than an ownership signal.
- Product-owned shared boundaries are underused; code is promoted to platform/global too early or consumed across products directly.
- Retired modules were not actually retired.
- Naming (`platform`, `core`, `services`, `ui`) no longer reveals ownership or scope reliably.

### 6. High-Level Structural Recommendation

Adopt a strict three-tier model:

1. Product-local by default
2. Product-owned shared for real cross-product contracts owned by one product
3. Truly global shared only for small, neutral, governed primitives

Then:

- Split `data-cloud/platform` into focused product modules
- Collapse duplicate YAPPC “merged but still present” modules
- Delete AEP `platform-*` shells
- Shrink global shared libraries to a minimal, intentional core

### Table 1. `ghatana` Product Inventory Table

| Area | Current footprint | Primary role | Current health | Audit classification |
| --- | ---: | --- | --- | --- |
| `products/data-cloud` | `1,093` files / `389` dirs | Event/data platform, registry, UI | Monolithic backend core | `data-cloud local`, `product-owned shared: data-cloud`, `candidate for split` |
| `products/aep` | `724` files / `478` dirs | Event processing engine, orchestration, server, UI | Moderately bounded but migration debris remains | `aep local`, `product-owned shared: aep`, `candidate for deletion` |
| `products/yappc` | `8,216` files / `2,560` dirs | Product creator platform, backend, frontend monorepo, tooling | Boundary drift is severe | `yappc local`, `misplaced`, `redundant`, `candidate for split` |
| `platform/java` | `2,030` files / `1,098` dirs | Shared Java infrastructure | Overgrown and partially domain-colored | `truly global shared`, `misplaced`, `candidate for split` |
| `platform/typescript` | `563` files / `148` dirs | Shared TS primitives | Mixed quality; some excellent, some migration residue | `truly global shared`, `redundant` |
| `shared-services` | `107` files / `80` dirs | Cross-product services | Small but half-consolidated | `truly global shared`, `misplaced` |

---

## Part 2 - Ghatana Boundary Model

### 7. Current `ghatana` Boundary Model

The current de facto model is:

- Big global platform buckets for Java and TS
- Product folders that still import each other directly
- Shared-services partly active, partly merged into platform, partly disabled
- Product-local migrations that were documented but not completed

### 8. Recommended `ghatana` Boundary Model

- Keep domain logic, workflows, product-specific UI, and product-specific adapters inside each product
- Use product-owned shared packages for:
  - `products/data-cloud/spi`
  - `products/aep/aep-operator-contracts`
  - `products/yappc/libs/java/yappc-domain`
- Keep global shared tiny:
  - `platform/java/{core,http,config,observability,security,testing}`
  - `platform/typescript/{tokens,theme,design-system,platform-utils}`

### 9. Product-Local vs Product-Owned Shared vs Truly Global Shared Definitions

- `data-cloud local`, `aep local`, `yappc local`: change cadence and semantics are driven by one product
- `product-owned shared: <product>`: other products consume it, but one product is still the source of truth
- `truly global shared`: domain-neutral, stable, reusable, and governed

### 10. Ownership Principles for `data-cloud`, `aep`, and `yappc`

- `data-cloud` owns event storage, registry persistence, data-centric plugins, and its SPI
- `aep` owns operator contracts, orchestration, pipeline execution, and AEP runtime APIs
- `yappc` owns product lifecycle orchestration, product-creation AI, scaffolding, and its frontend domain model

### 11. Shared Library Admission Rules

- No product-specific nouns in truly global shared package names or APIs
- No global promotion until there are at least two real consumers and a stable API seam
- No wrappers-only packages
- No “compatibility” package left active after migration windows expire

### 12. Boundary Drift Risks

- Retired modules linger and continue being imported
- Wrapper packages keep legacy names alive
- Root build includes modules that product-local settings say are deleted
- Test exclusions hide structural problems instead of resolving them

### Table 2. Product vs Shared Responsibility Matrix

| Capability | Actual owner | Current location | Correct classification | Why current placement is right or wrong | Recommended action |
| --- | --- | --- | --- | --- | --- |
| Event storage SPI | Data Cloud | `products/data-cloud/spi` | `product-owned shared: data-cloud` | Correctly product-owned and consumed by others | Keep |
| Operator contracts | AEP | `products/aep/aep-operator-contracts` | `product-owned shared: aep` | Correctly shared but still clearly AEP-shaped | Keep |
| Product lifecycle orchestration | YAPPC | `products/yappc/services/lifecycle`, `core/lifecycle` | `yappc local` | Correct owner, but split is incoherent and duplicated | Merge and split by concern |
| Design tokens/theme | Platform | `platform/typescript/tokens`, `theme` | `truly global shared` | Neutral and reusable | Keep |
| Design system | Platform | `platform/typescript/design-system` | `truly global shared` | Legitimate cross-product primitive library | Keep |
| Data Cloud implementation classes | Data Cloud | `products/data-cloud/platform` | `data-cloud local` | Wrongly used as cross-product dependency target | Split and fence |
| AI integration runtime | Mixed | `platform/java/ai-integration` | `misplaced` | Combines neutral AI API with operational, training, serving, and feature-store concerns | Split |
| Auth gateway | Shared platform | `shared-services/auth-gateway` | `truly global shared` | Shared service boundary is clear | Keep |
| YAPPC frontend compatibility aliases | YAPPC | `products/yappc/frontend/tsconfig.base.json` | `misplaced` | Keeps legacy package graph alive | Delete legacy aliases after migration |

### Table 3. Library Classification Table

| Library | Current location | Recommended location | Actual owner | Current consumers | Classification | Why current placement is right or wrong | Recommended action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `@ghatana/design-system` | `platform/typescript/design-system` | Keep | Platform UI | Data Cloud UI, YAPPC web, AEP UI | `truly global shared` | Neutral and broadly useful | Keep |
| `@ghatana/theme` / `@ghatana/tokens` | `platform/typescript/theme`, `tokens` | Keep | Platform UI | Multiple products | `truly global shared` | Correct abstraction level | Keep |
| `@ghatana/utils` | `platform/typescript/utils` | Fold into `platform-utils` | Platform UI | Design system and legacy callers | `redundant` | Only a compatibility wrapper | Deprecate and delete |
| `platform/java/core` | `platform/java/core` | Keep | Platform | Many Java modules | `truly global shared` | Core primitive layer is valid | Keep |
| `platform/java/ai-integration` | `platform/java/ai-integration` | Split into neutral API + service/runtime packages | Shared platform / mixed | Platform, YAPPC, shared services | `misplaced`, `candidate for split` | Too product-colored and operationally broad for global shared | Split |
| `products/data-cloud/spi` | `products/data-cloud/spi` | Keep | Data Cloud | AEP, YAPPC | `product-owned shared: data-cloud` | Good example of product-owned shared | Keep |
| `products/aep/aep-operator-contracts` | `products/aep/aep-operator-contracts` | Keep | AEP | YAPPC, AEP | `product-owned shared: aep` | Good example of product-owned shared | Keep |
| `products/yappc/libs/java/yappc-domain` | `products/yappc/libs/java/yappc-domain` | Keep closer to YAPPC app/service boundaries | YAPPC | YAPPC backend/services | `yappc local` | Mostly YAPPC-internal, not real shared platform | Keep local and simplify |
| `shared-services/auth-gateway` | `shared-services/auth-gateway` | Keep | Shared platform | Cross-product | `truly global shared` | Real shared service boundary | Keep |

---

## Part 3 - Product-Specific Audit

### 13. `data-cloud` Boundary and Structure Audit

What it is actually for:

- Product-local event/data storage and registry platform
- Cross-product SPI provider for event-log and registry access
- UI for Data Cloud operations

What is right:

- `products/data-cloud/spi` is correctly positioned as product-owned shared
- `feature-store-ingest` being moved out of `shared-services` into Data Cloud is directionally right

What is wrong:

- `products/data-cloud/platform` is too large to be a safe product boundary
- Cross-product consumers are encouraged to depend on implementation-heavy modules instead of `spi`
- Launcher tests are excluded instead of the HTTP surface being right-sized

### 14. `aep` Boundary and Structure Audit

What it is actually for:

- Operator contracts
- Execution engine
- Registry/orchestration/server
- Pipeline UI

What is right:

- `aep-operator-contracts` is a valid product-owned shared seam
- Core active modules are more bounded than Data Cloud

What is wrong:

- `platform-*` directories are dead scaffolding
- `aep-runtime-core` is a thin facade that duplicates the historical `platform-core` story
- `server` directly depends on `products:data-cloud:platform`
- Namespace sprawl inside `aep-engine` (`com.ghatana.aep`, `com.ghatana.core`, `com.ghatana.pipeline`, `com.ghatana.eventprocessing`, `com.ghatana.platform`, etc.) indicates unfinished extraction

### 15. `yappc` Boundary and Structure Audit

What it is actually for:

- Product-creation lifecycle platform
- Backend API and persistence
- AI and agents
- Frontend application and internal UI/canvas libraries
- Tooling, scaffolding, plugin runtime, and refactorer

What is right:

- YAPPC owns a broad but coherent product mission
- Some local libraries are correctly kept inside the product

What is wrong:

- The product boundary is overloaded with too many sub-platforms
- Merged modules still exist in parallel
- Backend, services, core, and frontend all use inconsistent naming and package namespaces
- Java modules import AEP and Data Cloud implementation classes directly
- Frontend has both old `@ghatana/yappc-*` and new `@yappc/*` alias families active at the same time

### 16. Cross-Product Coupling Audit

Key observed couplings:

- YAPPC -> Data Cloud implementation: `products/yappc/infrastructure/datacloud/build.gradle.kts`, `services/platform`, `services`, `backend/api`, `core/lifecycle`
- YAPPC -> AEP implementation: `services/lifecycle`, `backend/api`, `core/agents/runtime`
- AEP -> Data Cloud implementation: `products/aep/server/build.gradle.kts`

Judgment:

- `AEP -> Data Cloud SPI` is acceptable
- `AEP -> Data Cloud platform` is too strong
- `YAPPC -> AEP/Data Cloud contracts` can be acceptable
- `YAPPC -> AEP/Data Cloud implementations` is too tight

### 17. Product vs Shared Responsibility Matrix

See Table 2 above. The core conclusion is that `data-cloud/spi` and `aep-operator-contracts` are the healthiest cross-product seams in the repo, while most other cross-product usage bypasses those seams.

### Table 4. Package Placement Table

| Package | Current location | Recommended location | Actual owner | Current consumers | Classification | Why current placement is right or wrong | Recommended action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Data Cloud backend core | `products/data-cloud/platform` | Split within `products/data-cloud/*` | Data Cloud | Data Cloud, AEP, YAPPC | `misplaced`, `candidate for split` | Too big and consumed as implementation library | Split |
| Data Cloud SPI | `products/data-cloud/spi` | Keep | Data Cloud | AEP, YAPPC | `product-owned shared: data-cloud` | Good product-owned contract seam | Keep |
| AEP operator contracts | `products/aep/aep-operator-contracts` | Keep | AEP | AEP, YAPPC | `product-owned shared: aep` | Correctly shaped as AEP-owned shared | Keep |
| AEP dead wrappers | `products/aep/platform-*` | Remove | AEP | None in root build | `redundant`, `candidate for deletion` | Only build files remain | Delete |
| AEP server | `products/aep/server` | Keep, but consume SPI/contracts only | AEP | AEP | `aep local`, `candidate for split` | Correct product, wrong dependency surface | Split dependencies and slim |
| YAPPC backend API | `products/yappc/backend/api` | Split API surface from embedded adapters | YAPPC | YAPPC | `yappc local`, `candidate for split` | Too many concerns and source-set merges | Split |
| YAPPC services lifecycle | `products/yappc/services/lifecycle` | Keep one lifecycle module, delete duplicate siblings | YAPPC | YAPPC | `yappc local`, `candidate for merge` | Right domain, wrong coexistence pattern | Merge/delete duplicates |
| YAPPC services AI | `products/yappc/services/ai` | Fold fully into lifecycle or restore as real bounded module | YAPPC | YAPPC | `redundant`, `candidate for deletion` | “Removed” in docs but still live | Delete or re-scope |
| YAPPC services scaffold | `products/yappc/services/scaffold` | Fold fully into lifecycle or restore as real bounded module | YAPPC | YAPPC | `redundant`, `candidate for deletion` | “Removed” in docs but still live | Delete or re-scope |
| YAPPC websocket backend | `products/yappc/backend/websocket` | Merge into `backend/api` or restore as actual transport module | YAPPC | YAPPC | `misplaced`, `candidate for merge`, `candidate for deletion` | Parallel websocket trees exist | Choose one home |
| YAPPC frontend UI lib | `products/yappc/frontend/libs/ui` | Split by domain/UI primitive layers | YAPPC | YAPPC frontend | `yappc local`, `candidate for split` | 839 files / 189k lines is too broad | Split |
| YAPPC frontend canvas lib | `products/yappc/frontend/libs/canvas` | Split by canvas kernel, workflow views, devsecops, security | YAPPC | YAPPC frontend | `yappc local`, `candidate for split` | 583 files / 289k lines is too broad | Split |
| YAPPC frontend core | `products/yappc/frontend/libs/core` | Remove or rename to `compat-core` | YAPPC | YAPPC frontend | `redundant` | Only a thin re-export facade | Delete or rename |

---

## Part 4 - Shared Libraries and Internal Packages Audit

### 18. Truly Global Shared Libraries Audit

Healthy or mostly healthy:

- `platform/java/core`
- `platform/java/http`
- `platform/java/config`
- `platform/java/observability`
- `platform/java/security`
- `platform/java/testing`
- `platform/typescript/tokens`
- `platform/typescript/theme`
- `platform/typescript/design-system`
- `platform/typescript/foundation/platform-utils`

Needs cleanup but should remain global:

- `platform/typescript/design-system` should stop depending on the `@ghatana/utils` compatibility layer

### 19. Product-Owned Shared Libraries Audit

Healthy candidates:

- `products/data-cloud/spi`
- `products/aep/aep-operator-contracts`

Borderline:

- `products/data-cloud/sdk` is product-owned shared and fine, but should stay downstream of a stable API contract, not a kitchen-sink runtime module

### 20. Misplaced Shared Libraries Audit

- `platform/java/ai-integration` is too broad for global shared
- `platform/typescript/utils` is only a compatibility package
- `platform/typescript/canvas` is global in name, but the product with the largest canvas need has its own giant local canvas library

### 21. Package Boundary Audit

Main issues:

- Package names over-promise neutrality (`platform`, `core`) while carrying product-colored logic
- Compatibility wrappers are published like first-class packages
- Package splits often preserve historical shapes instead of current responsibility

### 22. Module Boundary Audit

Main issues:

- YAPPC merged modules coexist with predecessors
- AEP runtime/core facade layers preserve indirection with little boundary value
- Data Cloud has one “platform” module where there should be several product-local modules

### 23. Folder Placement Audit

Main issues:

- YAPPC duplicates `services/ai` and `services/scaffold` content under `services/lifecycle/src/main/java/com/ghatana/yappc/services/{ai,scaffold}`
- YAPPC duplicates websocket packages in both `backend/api` and `backend/websocket`
- AEP UI points to old folder structures that no longer exist

### 24. Naming and Ownership Audit

Misleading names:

- `products/data-cloud/platform` is not just a platform; it is the product runtime, client surface, plugin host, and much more
- `products/yappc/services/platform` is not platform code; it is application/service glue
- `@yappc/core` sounds foundational but is just a compatibility facade
- `platform-*` under AEP reads active but is dead

### Table 5. Module Placement Table

| Module | Current location | Recommended location | Actual owner | Current consumers | Classification | Why current placement is right or wrong | Recommended action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `ai-integration` | `platform/java/ai-integration` | Split into global API + shared runtime services | Shared platform / mixed | Platform, YAPPC, services | `misplaced`, `candidate for split` | Contains operational AI lifecycle, not just API | Split |
| `kernel-capabilities` | `platform/java/kernel-capabilities` | Split or delete merged subdomains | Shared platform / mixed | Platform | `candidate for split` | Merge target is too broad to govern | Split |
| `aep-runtime-core` | `products/aep/aep-runtime-core` | Merge into `aep-engine` or keep only if real API seam exists | AEP | AEP modules | `redundant`, `candidate for merge` | Thin facade pattern with exclusion-based tests | Merge |
| `orchestrator` | `products/aep/orchestrator` | Keep, but reduce API surface and Data Cloud coupling | AEP | AEP, YAPPC transitively | `aep local`, `candidate for split` | Too many `api(...)` deps and one excluded DI test | Split API/runtime concerns |
| `services:lifecycle` | `products/yappc/services/lifecycle` | Keep as sole lifecycle host | YAPPC | YAPPC | `yappc local` | Right owner, wrong coexistence with old siblings | Keep and absorb duplicates |
| `services:ai` | `products/yappc/services/ai` | Delete after migration | YAPPC | YAPPC | `candidate for deletion` | Product-local duplicate of merged module | Delete |
| `services:scaffold` | `products/yappc/services/scaffold` | Delete after migration | YAPPC | YAPPC | `candidate for deletion` | Product-local duplicate of merged module | Delete |
| `backend:websocket` | `products/yappc/backend/websocket` | Pick one websocket home | YAPPC | YAPPC | `candidate for merge` | Parallel websocket trees exist | Merge/delete |
| `@ghatana/utils` | `platform/typescript/utils` | Inline into `platform-utils` consumers | Platform UI | Platform TS libs | `redundant` | Pure wrapper | Delete |
| `@yappc/core` | `products/yappc/frontend/libs/core` | Delete or rename to compat-only | YAPPC | YAPPC frontend | `redundant` | Re-exports deprecated packages and empty types | Delete or rename |

### Table 6. Folder Placement Table

| Folder | Current location | Recommended location | Actual owner | Current consumers | Classification | Why current placement is right or wrong | Recommended action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Data Cloud plugin folders | `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/*` | Split by product runtime concern | Data Cloud | Data Cloud | `data-cloud local`, `candidate for split` | Plugin family lives inside a giant omnibus module | Split |
| AEP dead wrappers | `products/aep/platform-*` | Remove | AEP | None | `redundant`, `candidate for deletion` | Folders only contain build files | Delete |
| YAPPC duplicate AI folder | `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/ai` | Delete after merge | YAPPC | YAPPC | `redundant` | Exact duplicate of `services/ai` code | Delete |
| YAPPC duplicate scaffold folder | `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/scaffold` | Delete after merge | YAPPC | YAPPC | `redundant` | Exact duplicate of `services/scaffold` code | Delete |
| YAPPC websocket packages | `products/yappc/backend/api/.../websocket` and `products/yappc/backend/websocket/...` | Single home only | YAPPC | YAPPC | `misplaced`, `candidate for merge` | Two folders own same responsibility | Merge |
| Empty platform TS area | `platform/typescript/capabilities` | Remove or repurpose intentionally | Platform UI | None | `candidate for deletion` | Empty directory but still referenced by AEP UI configs | Delete |
| YAPPC frontend alias compatibility | `products/yappc/frontend/tsconfig.base.json` paths to old packages | Remove after migration | YAPPC | YAPPC frontend | `misplaced` | Keeps dead names active | Clean up |

---

## Part 5 - Simplification and Right-Sizing Audit

### 25. Over-Engineering Findings

- AEP `platform-*` wrappers
- YAPPC compatibility wrappers (`@yappc/core`, `@ghatana/utils`)
- Root/platform consolidation narratives that preserve both old and new shapes
- Global platform modules that merged multiple subdomains under one name

### 26. Under-Engineering Findings

- `products/data-cloud/platform` not split by responsibility
- `products/yappc/services/lifecycle` and `backend/api` mix too many reasons to change
- YAPPC frontend libraries are oversized
- AEP server and Data Cloud launcher keep excluding tests instead of fixing module seams

### 27. Merge / Combine Opportunities

- AEP `aep-runtime-core` into `aep-engine`
- YAPPC websocket code into a single backend module
- YAPPC lifecycle duplicate service folders into one module
- Platform TS utils wrapper into platform-utils

### 28. Split Opportunities

- Data Cloud platform
- YAPPC frontend `ui`
- YAPPC frontend `canvas`
- YAPPC backend API
- Platform Java `ai-integration`

### 29. Move Closer to Product Opportunities

- Data Cloud implementation APIs currently depended on by other products should be hidden behind `spi`
- YAPPC product-specific frontend primitives should remain inside YAPPC, not be presented as neutral global libraries

### 30. Move to Product-Owned Shared Opportunities

- Keep cross-product event/data contracts in `products/data-cloud/spi`
- Keep operator contracts in `products/aep/aep-operator-contracts`
- If YAPPC exposes reusable creation lifecycle contracts later, keep them under YAPPC ownership first

### 31. Move to Truly Global Shared Opportunities

- None of the current large product-colored modules should be promoted
- The only safe promotions are small neutral primitives extracted from product internals after real reuse proves out

### 32. Delete / Deprecate Opportunities

- `products/aep/platform-*`
- `products/yappc/services/ai`
- `products/yappc/services/scaffold`
- `products/yappc/backend/websocket` or the websocket subtree under `backend/api` after choosing one
- `products/yappc/infrastructure/security` after consolidating
- `products/yappc/launcher`
- `platform/typescript/capabilities`
- `platform/typescript/utils` after migration

### Table 7. File Hotspot Table

| File | LOC | Current role | Main issue | Classification |
| --- | ---: | --- | --- | --- |
| `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/client/EmbeddedDataCloudClient.java` | `1655` | Embedded client facade | Mixed storage, lifecycle, fallback, validation, client mode logic | `candidate for split` |
| `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/config/ConfigRegistry.java` | `1091` | Config registry | Oversized mixed configuration orchestration | `candidate for split` |
| `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java` | `910` | HTTP server surface | Many controllers and transport concerns in one file | `candidate for split` |
| `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/LifecycleServiceModule.java` | `1197` | DI module | Product lifecycle, workflow, memory, plugin, metrics, AI wiring all mixed | `candidate for split` |
| `products/yappc/core/domain/src/main/java/com/ghatana/products/yappc/domain/agent/http/AgentController.java` | `1173` | Domain HTTP controller | Domain and transport mixed in “core/domain” | `misplaced`, `candidate for split` |
| `products/yappc/backend/persistence/src/main/java/com/ghatana/yappc/api/domain/BootstrappingSession.java` | `1174` | Persistence/domain model | Oversized entity/model hotspot | `candidate for split` |
| `products/yappc/frontend/web/src/pages/development/CodeReviewDetailPage.tsx` | `1954` | Web page | Page is doing page, state, data, and presentation work | `candidate for split` |
| `products/yappc/frontend/libs/canvas/src/index.ts` | `1639` | Canvas export surface | Kitchen-sink export barrel | `candidate for split` |

### Table 8. Over-Engineering Findings Table

| Finding | What is over-engineered | Why unnecessary | Complexity cost | Simpler replacement |
| --- | --- | --- | --- | --- |
| AEP dead wrappers | `products/aep/platform-*` | No sources; not in root build | False module count, naming noise, migration confusion | Delete |
| YAPPC compat core | `products/yappc/frontend/libs/core` | Only re-exports deprecated packages and empty types | Adds aliases, indirection, unclear ownership | Delete or rename as explicit compat package |
| TS utils wrapper | `platform/typescript/utils` | Pure re-export of `@ghatana/platform-utils` | Duplicate package identity and migration residue | Fold into `platform-utils` |
| YAPPC merged-but-alive modules | `services/ai`, `services/scaffold`, `backend/websocket`, `infrastructure/security`, `launcher` | Product build says they are retired | Duplicate code paths and policy contradictions | Pick one structure and remove the rest |
| Mega merged global modules | `platform/java/ai-integration`, `kernel-capabilities` | Merges exceeded neutral shared scope | Ambiguous ownership and hard refactoring | Split into smaller neutral/global vs product-owned units |

### Table 9. Under-Engineering Findings Table

| Finding | What is under-engineered | Why risky | Missing boundary | Justified split/extraction |
| --- | --- | --- | --- | --- |
| Data Cloud backend core | `products/data-cloud/platform` | Changes are high-blast-radius | Runtime/client/api/plugin boundaries | Split into runtime, client, api, plugin families |
| AEP server surface | `AepHttpServer` | Hard to test and evolve independently | Transport/router/controller assembly seam | Extract route composition and feature modules |
| YAPPC lifecycle | `LifecycleServiceModule` | Wiring and domain logic evolve together | AI/workflow/plugin/memory boundaries | Split provider modules |
| YAPPC backend API | `products/yappc/backend/api` | Controllers, config, websocket, AEP sources are co-located | API/transport/service/integration boundary | Split |
| YAPPC frontend UI and canvas | `libs/ui`, `libs/canvas` | Slow change, unclear ownership inside product | Primitive/domain/editor/workflow boundaries | Split by responsibility |

### Table 10. Coupling Hotspots Table

| Hotspot | Evidence | Coupling problem | Severity | Recommended fix |
| --- | --- | --- | --- | --- |
| YAPPC -> Data Cloud implementation | `products/yappc/services/build.gradle.kts`, `services/platform`, `backend/api`, `core/lifecycle` | Depends on implementation-heavy Data Cloud module | High | Depend on `products/data-cloud/spi` or smaller product-owned contracts |
| YAPPC -> AEP implementation | `services/lifecycle`, `backend/api`, `core/agents/runtime` | Depends on orchestrator/engine/runtime internals | High | Introduce AEP-owned shared contracts or adapter layer |
| AEP -> Data Cloud implementation | `products/aep/server/build.gradle.kts` | Server depends on `products:data-cloud:platform` | High | Depend on `spi` only |
| AEP UI -> stale platform aliases | `products/aep/ui/vite.config.ts` | UI build depends on dead folder paths | Medium | Point to actual packages or workspace deps |
| YAPPC frontend alias mesh | `products/yappc/frontend/tsconfig.base.json` | Old and new package names both active | High | Remove legacy aliases after migration |

### Table 11. Cohesion Problems Table

| Unit | Why cohesion is low | Evidence |
| --- | --- | --- |
| `products/data-cloud/platform` | API, client, plugins, infra, analytics, workflow, deployment all together | `630` Java files / `174,630` lines; package tree spans `api`, `application`, `client`, `config`, `plugins`, `infrastructure`, `workflow`, `edge`, `analytics` |
| `products/yappc/services/lifecycle` | Lifecycle, AI, scaffold, plugin, memory, workflow bootstrapping mixed | `LifecycleServiceModule.java` at `1197` LOC plus duplicate `services/ai` and `services/scaffold` content |
| `products/yappc/backend/api` | API, persistence-adjacent config, websocket, AEP source set, platform integration mixed | `sourceSets` merges `src/main/java` and `aep/src/main/java` |
| `products/yappc/frontend/libs/ui` | Kitchen-sink component package with self-imports | `839` files / `189,470` lines; many files import from `@yappc/ui` inside the same package |
| `products/yappc/frontend/libs/canvas` | Kernel, workflow, devsecops, security, onboarding, AI, export surface mixed | `583` files / `289,602` lines |
| `platform/java/ai-integration` | Neutral AI API and operational AI runtime are combined | Build file plus folder history indicate serving, training, gateway, evaluation, testing |

### Table 12. Duplication Table

| Duplicate area | Evidence | Duplication type | Recommendation |
| --- | --- | --- | --- |
| YAPPC AI service module | SHA-1 of `services/ai/.../AiServiceModule.java` matches copy under `services/lifecycle/.../ai/AiServiceModule.java` | Exact duplicate code | Delete old module copy |
| YAPPC scaffold service module | SHA-1 of `services/scaffold/.../ScaffoldServiceModule.java` matches copy under `services/lifecycle/.../scaffold/ScaffoldServiceModule.java` | Exact duplicate code | Delete old module copy |
| YAPPC scaffold service class | SHA-1 of `services/scaffold/.../YappcScaffoldService.java` matches copy under lifecycle | Exact duplicate code | Delete old module copy |
| YAPPC websocket transport | Same class names exist under `backend/websocket` and `backend/api/.../websocket` | Parallel responsibility duplication | Pick one canonical module |
| YAPPC package naming | `@ghatana/yappc-*` and `@yappc/*` aliases both live | Naming and import duplication | Finish migration and drop old names |

---

## Part 6 - Deep File and Folder Review

### 33. Folder-Level Review

- `products/data-cloud/platform/src/main/java/com/ghatana/datacloud`: too broad; this should not be one folder tree
- `products/aep/platform-*`: delete
- `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/{ai,scaffold}`: delete after consolidation
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/websocket`: merge with or replace `backend/websocket`
- `products/yappc/frontend/libs/ui/src/components`: split into primitives, domain widgets, workflow widgets, and legacy compat
- `products/yappc/frontend/libs/canvas/src`: split into kernel, editor shell, domain overlays, devsecops overlays, security overlays

### 34. Package-Level Review

- `products/data-cloud/platform`: wrong size
- `products/data-cloud/spi`: right boundary
- `products/aep/aep-operator-contracts`: right boundary
- `products/aep/server`: correct owner, wrong dependency discipline
- `products/yappc/backend/api`: wrong size and mixed seams
- `products/yappc/frontend/libs/ui`: wrong size
- `platform/typescript/design-system`: mostly good
- `platform/typescript/utils`: should disappear

### 35. Module-Level Review

- `products/aep/aep-runtime-core`: merge candidate
- `products/yappc/services/lifecycle`: keep as canonical but split internal provider modules
- `products/yappc/core/scaffold/packs`: either restore as real bounded module or delete if truly merged
- `products/yappc/backend/websocket`: same choice as above

### 36. File-Level Hotspot Review

Representative file judgments:

- `EmbeddedDataCloudClient.java`: placement partly right, size badly wrong
- `ConfigRegistry.java`: likely a “policy/config/orchestration god object”
- `AepHttpServer.java`: server assembly and endpoint responsibility are too concentrated
- `LifecycleServiceModule.java`: DI module has become a functional kernel
- `CodeReviewDetailPage.tsx`: UI page is oversized and likely doing too much orchestration
- `libs/canvas/src/index.ts`: export barrel is hiding a monolith, not representing a clean API

### 37. Duplication Review

Most harmful duplication is not copy-paste utility code. It is structural duplication:

- duplicated modules
- duplicated aliases
- duplicated websocket ownership
- duplicated “merged” and “pre-merge” layouts

### 38. Tight Coupling Hotspots

- YAPPC backend/service stack to AEP/Data Cloud implementations
- AEP server to Data Cloud implementation
- YAPPC frontend `ui` to itself and to giant sibling packages
- Platform Java modules that still depend on operational or domain-heavy merged modules

### 39. Low Cohesion Hotspots

- Data Cloud platform runtime
- YAPPC lifecycle service
- YAPPC backend API
- YAPPC frontend canvas and UI

### Table 13. Merge Opportunities Table

| Title | Type | Affected product/package/module/file | Current location | Recommended location or structure | Why this change is needed | Simplification benefit | Coupling reduction benefit | Cohesion improvement benefit | Risk if unchanged | Effort | Priority | Recommended owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Merge AEP facade into engine | Merge | `products/aep/aep-runtime-core` | `products/aep/aep-runtime-core` | absorb into `products/aep/aep-engine` | Thin facade adds indirection and excluded tests | Fewer modules | Removes fake API layer | Engine owns engine | Continued confusion | S | P1 | AEP team |
| Merge YAPPC websocket ownership | Merge | `backend/api` + `backend/websocket` | two YAPPC backend modules | one websocket home | Same responsibility exists twice | Fewer modules | Removes transport ambiguity | Single websocket boundary | Drift between copies | M | P0 | YAPPC backend |
| Merge TS utils wrapper away | Merge | `@ghatana/utils` | `platform/typescript/utils` | `platform-utils` only | Pure compatibility wrapper | One fewer package | Removes alias indirection | Utilities become explicit | Long-lived wrapper debt | XS | P1 | Platform UI |
| Merge duplicate lifecycle service folders | Merge | YAPPC AI/scaffold service copies | `services/ai`, `services/scaffold`, `services/lifecycle/...` | `services/lifecycle` only | Exact duplicate files exist | Removes duplicate codepaths | Clear canonical module | Lifecycle module becomes authoritative | Ongoing drift | XS | P0 | YAPPC backend |

### Table 14. Split Opportunities Table

| Title | Type | Affected product/package/module/file | Current location | Recommended location or structure | Why this change is needed | Simplification benefit | Coupling reduction benefit | Cohesion improvement benefit | Risk if unchanged | Effort | Priority | Recommended owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Split Data Cloud platform omnibus | Split | `products/data-cloud/platform` | one backend omnibus | `runtime-core`, `api`, `client`, `plugins/*`, `analytics`, `registry` | One module owns too many concerns | Safer changes | Allows SPI-only consumers | Focused modules | Blast radius remains high | XL | P0 | Data Cloud team |
| Split YAPPC backend API | Split | `products/yappc/backend/api` | one application module | `api-http`, `api-aep-adapter`, `api-websocket`, `api-config` | Mixed transport, config, integration, source-set merge | Clearer ownership | Isolates external integrations | Smaller app modules | Harder testing and delivery | L | P0 | YAPPC backend |
| Split YAPPC frontend canvas | Split | `products/yappc/frontend/libs/canvas` | one giant TS package | `canvas-kernel`, `canvas-editor`, `canvas-domain-overlays` | 289k-line package is not governable | Smaller libraries | Clearer import graph | Better internal focus | Feature work stays risky | XL | P1 | YAPPC frontend |
| Split YAPPC frontend UI | Split | `products/yappc/frontend/libs/ui` | one giant TS package | `ui-primitives`, `ui-domain`, `ui-workflow`, `ui-legacy` | 189k-line UI lib is too broad | Faster navigation | Fewer cross-subpackage imports | Better component locality | Continued self-import cycles | XL | P1 | YAPPC frontend |
| Split global AI integration | Split | `platform/java/ai-integration` | one merged global module | `platform/java/ai-api` + service/runtime modules | Operational AI concerns are not neutral shared | Smaller global core | Reduces shared blast radius | Better ownership | Global module becomes dumping ground | L | P1 | Platform + product owners |

### Table 15. Move Opportunities Table

| Title | Type | Affected product/package/module/file | Current location | Recommended location or structure | Why this change is needed | Simplification benefit | Coupling reduction benefit | Cohesion improvement benefit | Risk if unchanged | Effort | Priority | Recommended owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Move consumers to Data Cloud SPI | Move | AEP and YAPPC consumers of Data Cloud impl | direct deps on `products:data-cloud:platform` | `products:data-cloud:spi` or smaller contracts | Consumers should not bind to Data Cloud internals | Simpler dependency graph | Lower cross-product coupling | Cleaner product boundaries | Refactors stay unsafe | M | P0 | Data Cloud + consumers |
| Keep AEP contracts AEP-owned | Keep | `aep-operator-contracts` | `products/aep/aep-operator-contracts` | same | Shareability does not require global promotion | Avoids premature platform growth | Maintains explicit owner | Clear AEP semantics | Ownership could be hidden later | XS | P1 | AEP team |
| Move YAPPC compat core out of “core” name | Move/Rename | `@yappc/core` | `products/yappc/frontend/libs/core` | `compat-core` or delete | Current name overstates importance | Reduces mental load | Avoids false center of gravity | Aligns name to purpose | More alias debt | XS | P1 | YAPPC frontend |

### Table 16. Rename Opportunities Table

| Title | Type | Affected product/package/module/file | Current location | Recommended location or structure | Why this change is needed | Simplification benefit | Coupling reduction benefit | Cohesion improvement benefit | Risk if unchanged | Effort | Priority | Recommended owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Rename Data Cloud platform | Rename | `products/data-cloud/platform` | current name implies neutral platform | `products/data-cloud/runtime` or split names | Current name hides product/runtime mix | More honest layout | Makes consumer misuse obvious | Clarifies module purpose | Continued misuse as shared impl | M | P1 | Data Cloud team |
| Rename YAPPC services platform | Rename | `products/yappc/services/platform` | current name implies platform | `products/yappc/application-core` | It is application glue, not platform | Less ambiguity | Better dependency semantics | Clearer owner | Further platform confusion | S | P1 | YAPPC backend |
| Rename YAPPC compat package | Rename | `products/yappc/frontend/libs/core` | `@yappc/core` | `@yappc/compat-core` | Prevent false foundational status | Clearer package intent | Reduces accidental imports | Better cohesion expectations | Misuse continues | XS | P1 | YAPPC frontend |

### Table 17. Delete / Deprecate Table

| Title | Type | Affected product/package/module/file | Current location | Recommended location or structure | Why this change is needed | Simplification benefit | Coupling reduction benefit | Cohesion improvement benefit | Risk if unchanged | Effort | Priority | Recommended owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Delete AEP `platform-*` shells | Delete | `products/aep/platform-*` | dead wrapper dirs | none | Only build files remain | Removes noise | Eliminates fake module graph | N/A | Ongoing architectural confusion | XS | P0 | AEP team |
| Delete YAPPC services AI | Delete | `products/yappc/services/ai` | duplicate old module | absorbed by lifecycle | Product build says it is retired | Fewer modules | Removes duplicate imports | Single owner | Duplicate changes continue | XS | P0 | YAPPC backend |
| Delete YAPPC services scaffold | Delete | `products/yappc/services/scaffold` | duplicate old module | absorbed by lifecycle | Exact duplicate files exist | Fewer modules | Removes duplicate imports | Single owner | Duplicate changes continue | XS | P0 | YAPPC backend |
| Delete YAPPC launcher | Delete | `products/yappc/launcher` | retired shell | absorb in backend/api app if still needed | Product build says launcher removed | One less shell | Less confusion | N/A | Root/settings contradiction remains | XS | P1 | YAPPC backend |
| Delete platform TS capabilities folder | Delete | `platform/typescript/capabilities` | empty | none | Dead migration residue | Removes ghost path | Prevents stale aliases | N/A | More broken path references | XS | P1 | Platform UI |

---

## Part 7 - Scoring

### 40. Product Boundary Scorecard

### Table 18. Ownership Table

| Area | True owner | Boundary score | Notes |
| --- | --- | ---: | --- |
| `data-cloud` | Data Cloud team | `5.5/10` | Good SPI seam, bad omnibus runtime boundary |
| `aep` | AEP team | `6.0/10` | Better than Data Cloud, but dead wrappers and Data Cloud impl coupling hurt |
| `yappc` | YAPPC team | `3.0/10` | Strong product ownership, weak internal boundaries |
| `platform/java` | Shared platform | `4.5/10` | Some true shared modules, some overgrown merged modules |
| `platform/typescript` | Platform UI | `6.5/10` | Good primitives, weak migration cleanup |

### 41. Package Scores

| Package | Placement correctness | Cohesion | Coupling control | Simplicity | Maintainability | Testability | Ownership clarity |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `products/data-cloud/platform` | 4 | 2 | 3 | 2 | 2 | 4 | 6 |
| `products/data-cloud/spi` | 9 | 8 | 8 | 8 | 8 | 8 | 9 |
| `products/aep/server` | 6 | 4 | 4 | 4 | 4 | 5 | 7 |
| `products/aep/orchestrator` | 6 | 5 | 5 | 5 | 5 | 5 | 7 |
| `products/yappc/backend/api` | 4 | 3 | 2 | 2 | 3 | 4 | 5 |
| `products/yappc/services/lifecycle` | 4 | 2 | 2 | 2 | 2 | 3 | 4 |
| `products/yappc/frontend/libs/ui` | 5 | 2 | 3 | 2 | 2 | 3 | 6 |
| `products/yappc/frontend/libs/canvas` | 5 | 2 | 2 | 1 | 2 | 3 | 6 |
| `platform/typescript/design-system` | 8 | 7 | 7 | 7 | 7 | 7 | 8 |
| `platform/typescript/utils` | 3 | 7 | 4 | 3 | 4 | 7 | 3 |

### 42. Module Scores

| Module | Placement correctness | Cohesion | Coupling control | Simplicity | Maintainability | Testability | Ownership clarity |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `platform/java/ai-integration` | 4 | 3 | 4 | 3 | 4 | 5 | 4 |
| `products/aep/aep-runtime-core` | 4 | 5 | 4 | 4 | 4 | 4 | 6 |
| `products/aep/aep-operator-contracts` | 8 | 7 | 8 | 7 | 7 | 7 | 8 |
| `products/yappc/services/ai` | 2 | 4 | 3 | 2 | 2 | 4 | 2 |
| `products/yappc/services/scaffold` | 2 | 4 | 3 | 2 | 2 | 4 | 2 |
| `products/yappc/backend/websocket` | 3 | 5 | 3 | 3 | 3 | 5 | 3 |
| `@yappc/core` | 2 | 3 | 3 | 2 | 3 | 6 | 2 |

### 43. File Hotspot Scores

| File | Placement correctness | Cohesion | Coupling control | Simplicity | Maintainability | Testability | Ownership clarity |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `EmbeddedDataCloudClient.java` | 4 | 3 | 4 | 2 | 2 | 4 | 6 |
| `ConfigRegistry.java` | 3 | 2 | 3 | 2 | 2 | 3 | 5 |
| `AepHttpServer.java` | 6 | 4 | 4 | 3 | 3 | 4 | 7 |
| `LifecycleServiceModule.java` | 4 | 2 | 2 | 1 | 2 | 3 | 4 |
| `BootstrappingSession.java` | 5 | 3 | 3 | 2 | 3 | 3 | 5 |
| `AgentController.java` in YAPPC core/domain | 3 | 2 | 2 | 2 | 2 | 3 | 4 |
| `CodeReviewDetailPage.tsx` | 6 | 3 | 4 | 1 | 2 | 3 | 7 |
| `libs/canvas/src/index.ts` | 5 | 2 | 2 | 1 | 2 | 2 | 5 |

### 44. Simplification Score

`4/10`

### 45. Coupling Score

`4/10`

### 46. Cohesion Score

`3.5/10`

### 47. Ownership Clarity Score

`4.5/10`

### 48. Maintainability Score

`4/10`

### Table 19. Structural Risk Table

| Risk | Evidence | Impact | Likelihood | Mitigation |
| --- | --- | --- | --- | --- |
| Parallel module truths | YAPPC retired modules still included at root | High | High | Remove duplicate modules from root and tree |
| Shared-module overreach | `platform/java/ai-integration` and `kernel-capabilities` | High | Medium | Split by real ownership |
| Product monoliths | Data Cloud platform, YAPPC frontend UI/canvas | High | High | Split into focused product-local packages |
| Unsafe cross-product dependency | AEP/YAPPC depend on Data Cloud implementation | High | High | Introduce or enforce product-owned contract seams |
| Stale build aliases | AEP UI points to nonexistent TS package paths | Medium | High | Clean configs immediately |

---

## Part 8 - Target State

### 49. Target `ghatana` Monorepo Layout

```text
ghatana/
  platform/
    java/
      core/
      http/
      config/
      observability/
      security/
      testing/
      ai-api/
    typescript/
      tokens/
      theme/
      design-system/
      foundation/platform-utils/
  shared-services/
    auth-gateway/
    ai-inference-service/
  products/
    data-cloud/
      spi/
      runtime/
      api/
      plugins/
      registry/
      sdk/
      ui/
    aep/
      aep-operator-contracts/
      engine/
      registry/
      orchestrator/
      server/
      ui/
    yappc/
      backend/
        api/
        auth/
        persistence/
      application/
        lifecycle/
        scaffold/
        ai/
      frontend/
        apps/web/
        libs/ui-primitives/
        libs/ui-domain/
        libs/canvas-kernel/
        libs/canvas-domain/
```

### 50. Target Product Boundary Layout

- Data Cloud exposes `spi` and optional client/sdk only
- AEP exposes operator contracts only
- YAPPC consumes those contracts through adapters, not implementations

### 51. Target Shared Library Layout

- Global shared contains primitives only
- Shared services contain real shared runtimes only
- Product-owned shared remains under product ownership

### 52. Target Package and Module Design

- No module with a “merged from five things” description should remain unsplit unless it is still cohesive
- No empty or wrapper-only package should remain first-class
- No module should exist solely to preserve an old import path unless clearly marked `compat`

### 53. Target Ownership Model

- Data Cloud team governs event/data contracts
- AEP team governs operator and orchestration contracts
- YAPPC team governs product-creation lifecycle and frontend domain
- Platform teams govern only neutral primitives and shared services

### 54. Target Simplification Principles

- Fewer packages, stronger packages
- No duplicate “old” and “new” active simultaneously
- No build-time contradictions between root and product-local settings

### 55. Target Coupling and Cohesion Rules

- Cross-product dependencies allowed only through product-owned shared contracts
- Frontend packages may not self-import through their published package name
- Giant export barrels must be split by domain or layer

---

## Part 9 - Execution Plan

### 56. Immediate Structural Fixes

- Remove or archive AEP `platform-*` shells
- Reconcile YAPPC root settings with YAPPC product settings
- Fix AEP UI aliases to actual TS package paths
- Delete exact duplicate AI/scaffold service files under YAPPC lifecycle or sibling modules

### 57. Short-Term Refactors

- Make AEP server depend on Data Cloud `spi`, not `platform`
- Collapse YAPPC websocket ownership to one module
- Delete `@ghatana/utils` wrapper after consumer migration

### 58. Medium-Term Boundary Cleanup

- Split Data Cloud platform omnibus
- Split YAPPC backend API
- Split `platform/java/ai-integration`

### 59. Long-Term Monorepo Simplification

- Standardize naming (`runtime`, `api`, `application`, `contracts`, `compat`)
- Add automated checks for wrapper-only packages, empty packages, and root/product settings drift
- Introduce product-owned shared admission governance

### 60. Merge Plan

- AEP runtime facade -> engine
- YAPPC websocket -> one backend home
- YAPPC lifecycle duplicates -> one canonical lifecycle module

### 61. Split Plan

- Data Cloud platform -> runtime/api/client/plugins/analytics
- YAPPC frontend UI -> primitives/domain/workflow/legacy
- YAPPC frontend canvas -> kernel/editor/domain overlays
- YAPPC backend API -> http/config/integration/websocket

### 62. Move Plan

- Move all cross-product consumers to `data-cloud/spi` and `aep-operator-contracts`
- Move product-specific AI/runtime behavior out of global shared where appropriate

### 63. Rename Plan

- Rename misleading “platform” and “core” compat packages

### 64. Delete / Deprecate Plan

- Delete dead shells and exact duplicates first
- Deprecate wrappers and legacy alias families second

### 65. Guardrails to Prevent Future Drift

- Root/product settings parity check
- Package graph rule: no self-import via published package name
- Package graph rule: no product->product dependency except allowlisted product-owned shared contracts
- Wrapper-only package detector
- Empty directory/package detector

### Table 20. Execution Roadmap Table

| Phase | Timeframe | Actions | Outcome |
| --- | --- | --- | --- |
| Phase 0 | 1-3 days | Delete AEP `platform-*`, fix AEP UI aliases, reconcile YAPPC root/settings contradictions | Repo truth becomes consistent |
| Phase 1 | 1-2 weeks | Remove YAPPC duplicate AI/scaffold/websocket/security/launcher modules or restore them as real modules | YAPPC graph shrinks sharply |
| Phase 2 | 2-4 weeks | Re-point AEP and YAPPC to Data Cloud/AEP product-owned contracts only | Cross-product coupling drops |
| Phase 3 | 4-8 weeks | Split Data Cloud platform, split YAPPC backend API, split platform AI integration | Major cohesion improvement |
| Phase 4 | 8-12 weeks | Split YAPPC frontend UI/canvas and remove legacy alias families | Frontend maintainability improves |

---

## Part 10 - Final Recommendation

### 66. Go / No-Go on Current `ghatana` Structure

`No-Go` for continued large-scale feature growth without structural cleanup.

Reason:

- The repo can still ship code, but current boundaries are not trustworthy enough for fast, safe refactoring across `data-cloud`, `aep`, and especially `yappc`.

### 67. Top 10 Boundary Fixes

1. Force AEP and YAPPC to depend on `products/data-cloud/spi`, not `products:data-cloud:platform`
2. Keep `products/aep/aep-operator-contracts` as the only AEP cross-product contract seam
3. Delete AEP `platform-*` shells
4. Reconcile YAPPC root settings with product-local settings
5. Remove YAPPC duplicate AI/scaffold/websocket/security/launcher module story
6. Rename misleading YAPPC “platform” and “core” compat packages
7. Split `platform/java/ai-integration`
8. Stop using compatibility wrappers as primary packages
9. Remove legacy frontend alias families after migration
10. Enforce product-owned shared governance explicitly

### 68. Top 10 Simplification Actions

1. Delete dead wrapper modules
2. Delete exact duplicate source copies
3. Delete empty TS capability folder
4. Delete `@ghatana/utils`
5. Delete or rename `@yappc/core`
6. Collapse YAPPC websocket ownership
7. Collapse YAPPC lifecycle duplicate service ownership
8. Split Data Cloud platform
9. Split YAPPC frontend `ui`
10. Split YAPPC frontend `canvas`

### 69. Top 10 Coupling Reductions

1. YAPPC -> Data Cloud via SPI only
2. YAPPC -> AEP via operator contracts/adapters only
3. AEP -> Data Cloud via SPI only
4. Remove root/product settings contradictions
5. Remove self-imports inside `@yappc/ui`
6. Remove legacy alias families
7. Reduce `api(...)` usage in AEP orchestrator
8. Shrink global AI integration scope
9. Keep product-specific code out of global shared
10. Separate transport modules from domain modules in YAPPC backend

### 70. Top 10 Cohesion Improvements

1. Split Data Cloud runtime/client/api/plugin families
2. Split YAPPC backend API
3. Split YAPPC lifecycle DI/wiring from runtime services
4. Split YAPPC frontend canvas by responsibility
5. Split YAPPC frontend UI by layer
6. Split global AI integration
7. Split giant server/router files
8. Replace export-monolith barrels with focused entry points
9. Remove wrapper-only packages
10. Normalize package namespaces in AEP and YAPPC

### 71. Final Conclusion

The healthiest boundaries in `ghatana` today are the ones that already behave like product-owned shared libraries: `products/data-cloud/spi` and `products/aep/aep-operator-contracts`. The least healthy areas are the ones pretending to be simplified while still carrying both old and new structures, especially inside `products/yappc`.

The simplest sustainable path is not more abstraction. It is:

- fewer modules,
- clearer product ownership,
- smaller truly global shared layers,
- and aggressive deletion of migration residue.

That will lower coupling, raise cohesion, and make `ghatana` materially easier to change.
