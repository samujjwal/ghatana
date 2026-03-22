# Monorepo Boundary, Right-Sizing, and Code Placement Audit

Date: 2026-03-22  
Repo: `ghatana`  
Audit lens: boundary-first, simplicity-first, ownership-first  
Evidence base: root Gradle and pnpm wiring, product/package manifests, selected OWNER/README/ADR docs, dependency declarations, selected hotspot files

## Audit Method

This audit is evidence-based, not folder-name based. It was derived from:

- `settings.gradle.kts`, `build.gradle.kts`, `pnpm-workspace.yaml`, `package.json`, `turbo.json`
- product OWNER docs and product READMEs
- shared-services strategy and ADR-013
- package manifests across `platform/typescript`, `products/yappc/frontend`, `products/dcmaar`, `products/tutorputor`, `products/data-cloud/ui`, `products/software-org/client/web`
- Gradle module dependency declarations across `platform/*`, `products/*`, and `shared-services/*`
- file-level hotspot sampling in YAPPC, DCMAAR, Software-Org, and shared-services

The audit focuses on code-bearing and build-bearing areas. It does not attempt to line-by-line review every documentation archive.

---

## Part 1 - Executive Summary

### 1. Executive Verdict

The current structure is not go-forward clean. It contains three architectural epochs at once:

1. the active root monorepo model (`platform`, `products`, `shared-services`)
2. product-local standalone/workspace islands, especially YAPPC and DCMAAR
3. residual parallel architectures, especially `products/app-platform`

The result is boundary drift, accidental cross-product coupling, duplicated abstractions, compatibility shims that never get removed, and a module graph that is larger than the real ownership graph.

### 2. Main Boundary Problems

- Cross-product Java dependencies remain unresolved instead of being extracted to a durable shared contract.
- Cross-product TypeScript dependencies exist at product-package level, not product-owned-shared or global-shared level.
- `products/app-platform` persists as a parallel source of truth even though Finance has already become its own product.
- `shared-services` governance says "keep it small and runtime-only", but the tree still contains services that ADR-013 says should be removed or decided.
- YAPPC uses standalone aliasing and nested workspaces to preserve multiple build modes, which increases structural ambiguity.

### 3. Main Over-Engineering Problems

- Thin facades and compatibility shims remain after consolidation.
- Product-local code is split into too many modules/packages in YAPPC and historical app-platform layers.
- AEP uses `platform-*` naming and a large bundle facade that hides product ownership.
- Multiple parallel error-boundary implementations exist across platform and products.
- Empty or near-empty platform folder shells (`platform/typescript/design-system`, `platform/typescript/realtime`, `platform/typescript/canvas`) coexist with the real implementations under `platform/typescript/capabilities/*`.

### 4. Main Under-Engineering Problems

- YAPPC frontend has extremely large libraries, especially `libs/ui` and `libs/canvas`.
- YAPPC and DCMAAR contain oversized UI files that mix rendering, orchestration, and recovery behavior.
- `kernel-capabilities` is a broad umbrella module with too many unrelated capabilities.
- Finance duplicates aggregation layers (`products/finance` and `products/finance/product`) with overlapping responsibilities.
- Documentation and actual code placement are out of sync in multiple products.

### 5. Top Structural Risks

- Future teams will keep importing from the wrong place because the repo has multiple plausible homes for similar concepts.
- "Shared" libraries will continue to form at product boundaries instead of explicit product-owned shared boundaries.
- Build guardrails will miss important problems because some product-to-product coupling happens in package-workspace land, not only in Gradle.
- Residual obsolete modules will continue to distort package decisions and migration work.

### 6. High-Level Simplification Opportunities

- Delete or archive `products/app-platform` as live code; preserve only authoritative docs that still matter.
- Close AEP operator/pipeline ownership by extracting the real shared contract and shrinking `platform-bundle`.
- Collapse duplicate aggregation layers in Finance.
- Prune deprecated shims and compatibility packages in platform TypeScript and YAPPC frontend.
- Reduce cross-product frontend imports by moving code closer to product ownership or extracting a minimal global package only when reuse is real.

---

## Part 2 - Boundary Model

### 7. Current Boundary Model

Current reality is a mixed model:

- `platform/*` contains some true shared infrastructure and some "consolidated because convenient" umbrellas.
- `products/*` contains both clean product-local code and product-owned shared code consumed by other products.
- `shared-services/*` contains both real service candidates and unresolved leftovers.
- some products maintain independent package/workspace/build regimes that effectively bypass the main monorepo shape.

### 8. Recommended Boundary Model

- Default home: product-local
- Second home: product-owned shared
- Rare home: truly global shared
- No parallel "generic product platform" unless it is actively built, owned, and consumed

### 9. Definition of Product-Local vs Product-Owned Shared vs Truly Global Shared

| Class | Definition | Good examples here | Bad examples here |
|---|---|---|---|
| Product-Local | One product owns roadmap, behavior, and change cadence | `products/finance/domains/*`, `products/dcmaar/libs/typescript/*`, `products/tutorputor/contracts` | `products/data-cloud/ui` importing `@yappc/code-editor` |
| Product-Owned Shared | One product owns it, others consume it | `products/data-cloud:spi`, parts of `products:aep` once contracts are narrowed | `products:aep:platform-bundle` in its current oversized form |
| Truly Global Shared | Stable, domain-neutral, multiple real consumers | `platform/java/core`, `platform/java/http`, `platform/typescript/design-system`, `tokens`, `theme` | `platform/typescript/ui` shim, `kernel-capabilities` umbrella |

### 10. Ownership Principles

- Shareability does not imply global ownership.
- Product ownership should be explicit and visible in the path.
- Runtime services require an owner, not just a directory.
- Deprecated shims require a removal date and an actual removal action.

### 11. Code Placement Principles

- Put code at the narrowest honest ownership boundary.
- Extract only the contract, not the whole implementation surface.
- Use platform for primitives, not for domain gravity wells.
- Prefer one cohesive product package over several facade packages with 5-20 files each.

### 12. Shared Library Admission Criteria

A package should enter global shared only if:

- at least two products use it today
- its API is product-neutral
- its naming is product-neutral
- ownership and API stability are clear
- moving it to global reduces duplication without distorting the model

---

## Part 3 - Monorepo Structure Audit

### 13. Product-Level Placement Audit

| Product | Actual responsibility | Current state | Classification | Verdict |
|---|---|---|---|---|
| AEP | event/operator runtime | active, but exports too much product surface | Product-Owned Shared, partly misplaced | keep product-owned, extract narrower shared contract |
| Data-Cloud | event backbone and data platform | active and legitimately consumed by others | Product-Owned Shared | keep, but reduce UI and doc drift |
| YAPPC | developer tooling and AI composition | active but highly fragmented and oversized | Product-Local with product-owned shared leakage | consolidate further and stop accidental sharing |
| Finance | financial domain product | relatively clean domain ownership | Product-Local | keep, simplify duplicate aggregation layers |
| Audio-Video | media processing product | comparatively clean | Product-Local | keep |
| DCMAAR | guardian/parental control product | mostly product-local TS libs, some large app surfaces | Product-Local | keep, simplify UI/app layering |
| Tutorputor | tutoring platform | coherent product namespace, one large simulation lib | Product-Local | keep, split simulation internally if needed |
| Software-Org | org simulation product | depends on AEP and Virtual-Org directly; frontend has stale deps | Misplaced / Needs Relocation | tighten product contract and clean frontend imports |
| Virtual-Org | org framework/runtime | currently acts like product-owned shared for Software-Org | Product-Owned Shared or extractable contract | clarify whether it is a product or shared framework |
| Flashit | context capture product | standalone full-stack island | Product-Local | keep local, avoid premature extraction |
| Security-Gateway | shared auth/security gateway | product path suggests product, responsibility is shared infra | Misplaced / Needs Relocation | either fold into shared-services/auth-gateway strategy or rename as product-owned infra product |
| PHR | mostly planning docs, but code exists | docs and code disagree | Misplaced / Needs Clarification | decide if pre-dev docs product or active code product |
| App-Platform | generic multi-domain kernel vision | residual parallel architecture | Redundant / Candidate for Removal | archive as code, retain only active docs if needed |

### 14. Shared Library Audit

| Library / module | Current location | Recommended location | True owner | Consumers | Classification | Action |
|---|---|---|---|---|---|---|
| `platform/java/core` | `platform/java/core` | keep | Platform | many | Truly Global Shared | Keep |
| `platform/java/http` | `platform/java/http` | keep | Platform | many | Truly Global Shared | Keep |
| `platform/java/observability` | `platform/java/observability` | keep | Platform | many | Truly Global Shared | Keep |
| `platform/java/security` | `platform/java/security` | keep | Platform/Security | many | Truly Global Shared | Keep |
| `platform/java/kernel-capabilities` | `platform/java/kernel-capabilities` | split by concern | Platform | many | Misplaced / Needs Relocation | Split |
| `platform/java/governance` | `platform/java/governance` | keep, but remove test coupling to service impl | Platform | several | Truly Global Shared | Keep after cleanup |
| `products:data-cloud:spi` | `products/data-cloud/spi` | keep | Data-Cloud | AEP, YAPPC | Product-Owned Shared | Keep |
| `products:data-cloud:platform` | `products/data-cloud/platform` | keep or rename `sdk/runtime` | Data-Cloud | YAPPC, Data-Cloud | Product-Owned Shared | Keep, tighten API |
| `products:aep:platform-bundle` | `products/aep/platform-bundle` | shrink or replace with extracted contract package | AEP | YAPPC, Virtual-Org, Software-Org | Misplaced / Needs Relocation | Split |
| `platform/typescript/design-system` actual impl | `platform/typescript/capabilities/design-system` | move to `platform/typescript/design-system` or rename folder to match reality | Platform FE | many | Truly Global Shared | Rename or move |
| `@ghatana/ui` | `platform/typescript/ui` | remove after migration | Platform FE | legacy | Redundant / Candidate for Removal | Delete |
| `@ghatana/canvas` | `platform/typescript/capabilities/canvas-core` | keep only if multiple products truly need same core | Data-Cloud + Platform FE | YAPPC, Data-Cloud | Product-Owned Shared trending global | Clarify ownership |
| `@yappc/code-editor` | `products/yappc/frontend/libs/code-editor` | keep YAPPC-local unless another real product adopts it cleanly | YAPPC | YAPPC, Data-Cloud UI | Misplaced / Needs Relocation | Remove cross-product use or extract minimal neutral editor package |

### 15. Package and Module Boundary Audit

| Area | Finding | Boundary verdict |
|---|---|---|
| AEP `platform-core` | thin facade kept for compatibility only | over-engineered, delete after migration |
| AEP `platform-bundle` | huge umbrella consumed by other products | over-broad and hides true shared contract |
| Finance `products/finance` + `products/finance/product` | two aggregation layers with similar purpose | redundant layering |
| YAPPC `services:lifecycle` | merged service still accumulates AI, scaffold, AEP orchestration, config validation | oversized, not right-sized |
| YAPPC standalone settings | alias graph recreates product and peer modules | over-engineered build indirection |
| App-Platform kernel modules | residual generic platform still present as code | duplicate architecture, misleading |
| Software-Org frontend | stale YAPPC package references | incorrect placement and ownership leakage |

### 16. Folder Placement Audit

| Folder | Actual responsibility | Ownership | Verdict | Action |
|---|---|---|---|---|
| `products/app-platform` | legacy generic platform/code templates/docs | none clear | wrong as active code | archive or delete live code |
| `platform/typescript/capabilities/*` | real FE shared packages | Platform FE | right idea, naming layer inconsistent | flatten or rename |
| `platform/typescript/ui` | deprecated shim | Platform FE | wrong to keep long-term | remove |
| `products/yappc/frontend/libs` | product-local FE libraries | YAPPC | too many large libs and deprecated remnants | consolidate and prune |
| `products/dcmaar/libs/typescript` | product-local FE libs | DCMAAR | mostly good, but shared-ui shells are thin | merge thin shells |
| `shared-services` | runtime services | platform/shared teams | partially correct, partially stale | enforce ADR-013 |
| `products/phr` | docs-first product with some code | unclear | inconsistent | decide active vs planning |

### 17. File Placement Audit

| File | Actual responsibility | Placement verdict | Evidence |
|---|---|---|---|
| `products/data-cloud/ui/package.json` | product UI manifest | wrong dependency boundary | imports `@yappc/code-editor` |
| `products/software-org/client/web/package.json` | product UI manifest | stale/misaligned | references absent `@ghatana/yappc-state`, `@yappc/core/types` |
| `products/aep/platform-core/build.gradle.kts` | compatibility facade | wrong long-term placement | explicit backward-compat facade |
| `products/yappc/settings.gradle.kts` | product standalone build graph | over-engineered placement | recreates alias graph for peer modules |
| `platform/java/governance/build.gradle.kts` | platform governance module | test boundary leak | test depends on `:shared-services:auth-gateway` |
| `products/phr/README.md` | product status doc | incorrect | says no production code exists, but code/build file exist |
| `shared-services/ai-inference-service/README.md` | service docs | stale | still documents old `:products:shared-services:*` path |

### 18. Cross-Product Coupling Audit

| Consumer | Provider | Current mechanism | Why it is a problem | Recommendation |
|---|---|---|---|---|
| YAPPC Java | AEP | `:products:aep:platform-bundle`, `:products:aep:orchestrator` | imports too much product runtime surface | extract narrower runtime/contract API |
| YAPPC Java | Data-Cloud | `:products:data-cloud:platform` | likely larger than needed | narrow to SPI/client contracts |
| Virtual-Org | AEP | `:products:aep:platform-bundle` | TODO admitted in build file | extract operator/pipeline API |
| Software-Org | AEP | `:products:aep:platform-bundle` | product depends on another product bundle | replace with shared contract |
| Software-Org | Virtual-Org | `:products:virtual-org:modules:framework` | software-org effectively extends another product | clarify as shared framework or collapse ownership |
| Data-Cloud UI | YAPPC FE | `@yappc/code-editor` | product-to-product FE import | localize or extract minimal neutral editor package |
| Software-Org Web | YAPPC FE | `@yappc/ui`, stale YAPPC packages | product-to-product FE import with stale names | extract only truly shared UI primitives or localize |

### 19. Product vs Shared Responsibility Matrix

| Concern | Product-Local | Product-Owned Shared | Truly Global Shared |
|---|---|---|---|
| financial domain logic | Finance | - | - |
| event store SPI | - | Data-Cloud | - |
| operator/pipeline contracts | - | AEP today, should become narrow shared contract | maybe `platform/java/workflow-api` if fully neutral |
| design system primitives | - | - | Platform TypeScript |
| code editor | YAPPC | maybe later | not yet |
| guardian connectors | DCMAAR | maybe later | not yet |
| tutor contracts | Tutorputor | yes, inside product | no |
| auth gateway runtime | - | shared-services/auth-gateway | runtime shared service |

---

## Part 4 - Over-Engineering and Under-Engineering Audit

### 20. Over-Engineering Findings

| Finding | Area | Why over-engineered | Simpler replacement |
|---|---|---|---|
| compatibility facade module | `products/aep/platform-core` | build module exists mainly to re-export engine | migrate consumers, delete facade |
| deprecated FE shim | `platform/typescript/ui` | package only forwards to design-system | update imports, remove package |
| YAPPC standalone alias graph | `products/yappc/settings.gradle.kts` | recreates monorepo modules and peer aliases | prefer one monorepo source of truth; standalone mode should be thinner |
| parallel generic product architecture | `products/app-platform` | duplicates Finance/Data-Cloud/AEP decisions | archive code, retain docs selectively |
| duplicated error boundaries | multiple FE packages and apps | many similar implementations with small deltas | choose one platform canonical + one product override pattern |
| nested capability folder shells | `platform/typescript/*` vs `capabilities/*` | two naming systems for same FE shared layer | one path model |

### 21. Under-Engineering Findings

| Finding | Area | Why under-engineered | Missing structure |
|---|---|---|---|
| oversized YAPPC frontend libs | `products/yappc/frontend/libs/ui`, `libs/canvas` | too much unrelated UI behavior per package | split by domain surface or feature family |
| oversized YAPPC service modules | `products/yappc/services/lifecycle` | merged layers still mix orchestration, AI, scaffold, AEP runtime | clearer service/application boundaries |
| oversized kernel umbrella | `platform/java/kernel-capabilities` | mixed auth/config/db/event/audit/resilience | narrower capability modules |
| duplicated finance aggregation layers | `products/finance`, `products/finance/product` | unclear entry-point ownership | one product root aggregation |
| stale product docs and manifests | PHR, AEP, Data-Cloud, Software-Org | documented boundaries do not match code | ownership and migration cleanup |

### 22. Unnecessary Abstractions

- `products/aep/platform-core` facade
- `platform/typescript/ui` shim
- deprecated `@yappc/component-traceability` compatibility package
- deprecated `@yappc/ide` package kept beside its intended replacement
- multiple local ErrorBoundary wrappers where platform already has canonical options

### 23. Missing Boundaries

- narrow operator/pipeline contract between AEP and its consumers
- clear shared framework boundary between Virtual-Org and Software-Org
- clean editor boundary between YAPPC and Data-Cloud UI
- explicit runtime service completion/removal for `shared-services/ai-inference-service`

### 24. Over-Splitting / Micro-Packaging Findings

- YAPPC frontend package garden
- historical app-platform kernel slices
- empty/placeholder FE top-level folders under `platform/typescript`

### 25. Under-Splitting / God-Module Findings

- `platform/java/kernel-capabilities`
- `products/yappc/services/lifecycle`
- `products/yappc/frontend/libs/canvas`
- `products/yappc/frontend/libs/ui`
- `products/dcmaar/apps/device-health/src/ui/pages/DashboardPage.tsx`

### 26. Premature Generalization Findings

- App-Platform generic multi-domain kernel still present as code after Finance became concrete product code
- AEP `platform-*` naming suggests platform neutrality that the implementation does not yet have
- some frontend packages are named/shared before they have proven multi-product reuse

### 27. Hidden Coupling Findings

- YAPPC frontend architecture script maps `@yappc/ui` to `libs/ui-new` and `@yappc/canvas` to `libs/canvas-new`, paths that do not match the visible workspace layout
- Software-Org frontend references absent YAPPC packages
- platform governance test scope depends on auth-gateway service implementation

---

## Part 5 - Merge / Split / Move Analysis

### 28. Merge Opportunities

| Opportunity | Type | Current | Recommended |
|---|---|---|---|
| Finance aggregation collapse | Merge | `products/finance` + `products/finance/product` | one root product aggregation |
| Software-Org error boundaries | Merge | two local ErrorBoundary components | one product-local implementation |
| DCMAAR thin shared-ui wrappers | Merge | `shared-ui-core`, `shared-ui-tailwind`, `shared-ui-charts` thin packages | merge where wrappers have no real API value |
| Auth services | Merge | `shared-services/auth-service` + `shared-services/auth-gateway` | only auth-gateway |

### 29. Combine / Consolidate Opportunities

| Opportunity | Why |
|---|---|
| FE platform folder model | remove empty top-level shells and use one canonical package path model |
| YAPPC deprecated packages | complete consolidation into canonical packages |
| shared-services docs | align READMEs and build includes with ADR-013 |

### 30. Split Opportunities

| Opportunity | Why split |
|---|---|
| `platform/java/kernel-capabilities` | unrelated concerns bundled together |
| `products:aep:platform-bundle` | oversized cross-product dependency surface |
| `products/yappc/frontend/libs/ui` | too broad; UI, state, auth, traceability, canvas coupling |
| `products/yappc/frontend/libs/canvas` | product feature engine mixed with generic canvas concerns |
| `products/yappc/services/lifecycle` | orchestration and integrations combined |

### 31. Move Closer to Product Opportunities

| Opportunity | Current | Recommended |
|---|---|---|
| code editor usage in Data-Cloud UI | depends on `@yappc/code-editor` | localize inside Data-Cloud UI until neutral editor package exists |
| Virtual-Org framework use in Software-Org | direct product-to-product import | either move specialized extension into Software-Org or extract shared org-framework contract |
| Tutorputor contracts | already product-local | keep where they are; do not move global |

### 32. Move to Product-Owned Shared Opportunities

| Opportunity | Current | Recommended |
|---|---|---|
| AEP operator/pipeline contracts | hidden inside AEP bundle | dedicated AEP-owned shared contract module |
| Data-Cloud platform client surface | broad runtime module | narrower Data-Cloud-owned shared client/sdk |
| Virtual-Org framework | product module used by Software-Org | explicit product-owned shared framework if reuse is intended |

### 33. Move to Truly Global Shared Opportunities

Move only after proof:

- minimal workflow/operator abstractions if they become product-neutral
- minimal editor abstraction if at least two non-YAPPC products use the same API
- no current evidence supports moving YAPPC UI or code-editor wholesale to global

### 34. Rename Opportunities

| Current | Recommended | Why |
|---|---|---|
| `products/aep/platform-*` | names reflecting event runtime purpose | current names obscure product ownership |
| `platform/typescript/capabilities/design-system` | `platform/typescript/design-system` or similar | path should match package identity |
| `platform/typescript/capabilities/realtime-engine` | `platform/typescript/realtime` | package name and folder should align |
| `platform/typescript/capabilities/canvas-core` | `platform/typescript/canvas` if global; otherwise product-owned path | align ownership with identity |

### 35. Delete / Deprecate Opportunities

| Item | Reason |
|---|---|
| `products/app-platform` live code | residual parallel architecture |
| `platform/typescript/ui` | deprecated shim |
| `shared-services/auth-service` | ADR says consolidate |
| `shared-services/ai-registry` | ADR says consolidate |
| `products/yappc/frontend/libs/component-traceability` | compatibility shim only |
| `@yappc/ide` after migration window | explicitly deprecated |

---

## Part 6 - Deep File and Folder Review

### 36. Folder-Level Review

| Folder | Responsibility | Owner | Scope | Cohesion | Recommendation |
|---|---|---|---|---|---|
| `products/yappc/frontend/libs/ui` | product UI foundation plus app behavior | YAPPC | too broad | low-medium | split and prune |
| `products/yappc/frontend/libs/canvas` | product canvas app behavior | YAPPC | too broad | low-medium | split generic canvas from YAPPC-specific tooling |
| `products/finance/domains` | financial domain packs | Finance | mostly right-sized | high | keep |
| `products/app-platform/kernel` | generic kernel slices | unclear | artificial | low | archive/delete as code |
| `shared-services` | runtime services | platform/shared teams | partially cohesive | medium-low | enforce keep/delete decisions |
| `platform/typescript/capabilities` | real FE shared packages | Platform FE | workable but naming-confused | medium | flatten or rename |

### 37. Module-Level Review

| Module | Responsibility | Cohesion | Boundary fit | Recommendation |
|---|---|---|---|---|
| `platform:java:kernel-capabilities` | consolidated kernel concerns | low | weak | split |
| `products:aep:platform-bundle` | AEP umbrella dependency | low-medium | weak | split |
| `products:yappc:services:lifecycle` | orchestration service | medium-low | weak | split or narrow |
| `products:finance:domains:*` | domain modules | medium-high | strong | keep |
| `products:data-cloud:spi` | event/store SPI | high | strong | keep |

### 38. Package-Level Review

| Package | Current role | Verdict |
|---|---|---|
| `@ghatana/design-system` | true FE primitive package | keep global |
| `@ghatana/ui` | deprecated alias | delete |
| `@yappc/code-editor` | YAPPC-local editor | keep local, stop accidental reuse |
| `@dcmaar/connectors` | product-specific secure connectors | keep product-local |
| `@tutorputor/contracts` | product-owned contracts | keep product-owned shared |
| `@yappc/component-traceability` | shim | delete |

### 39. File-Level Hotspot Review

| File | Lines | Main issue | Recommendation |
|---|---:|---|---|
| `products/yappc/frontend/libs/canvas/src/hooks/useMicroservicesExtractor.ts` | 886 | god-hook mixing types, state, analysis, export | split into state, analysis, export, model files |
| `products/yappc/frontend/libs/ui/src/error/AuthErrorBoundary.tsx` | 658 | too much auth, navigation, reset, messaging logic | reduce to boundary + delegate auth handling |
| `products/dcmaar/apps/device-health/src/ui/pages/DashboardPage.tsx` | 574 | page orchestrates data fetching, cards, fallback, navigation | split containers, cards, and fallback widgets |
| `products/yappc/frontend/web/src/components/shared/ErrorBoundary.tsx` | 460 | local duplicate of already duplicated concept | converge to one product-local boundary |
| `shared-services/auth-service/src/main/java/com/ghatana/services/auth/AuthService.java` | 408 | large service that ADR says should be merged away | merge behavior into auth-gateway or delete |

### 40. Naming Review

- AEP `platform-*` naming is misleading because the code is still AEP-owned.
- `platform/typescript/capabilities/*` conflicts with package names and top-level empty directories.
- `products/security-gateway` is path-named like a product but functionally like shared infra.
- `products/app-platform` reads like an active foundational product but behaves like a superseded architecture.

### 41. Duplication Review

| Duplicated concept | Evidence | Action |
|---|---|---|
| ErrorBoundary implementations | platform, YAPPC, Software-Org, Flashit, Tutorputor, DCMAAR | standardize canonical + product override strategy |
| auth service logic | `shared-services/auth-service` and `shared-services/auth-gateway` | consolidate |
| FE shared package shells and actual packages | platform/typescript top-level shells vs capabilities packages | flatten naming model |
| finance/app-platform domain overlap | App-Platform docs and Finance code occupy same conceptual space | retire app-platform code |

### 42. Ownership Review

| Area | Ownership clarity |
|---|---|
| Finance | clear |
| Audio-Video | clear |
| Data-Cloud | mostly clear |
| AEP | partially clear, but exports too much |
| YAPPC | product owner clear, internal boundaries weak |
| App-Platform | unclear and obsolete |
| Shared-services | improving, but not consistently executed |

---

## Part 7 - Scoring

### 43. Product Boundary Scorecard

| Product | Score / 10 | Note |
|---|---:|---|
| Finance | 7.5 | best active Java domain structure, still has duplicate aggregation |
| Audio-Video | 7.0 | clean product-locality, modest size |
| Data-Cloud | 5.5 | strong SPI boundary, but broad platform module and FE leakage |
| DCMAAR | 5.5 | mostly local packages, app surfaces are heavy |
| Tutorputor | 5.5 | coherent namespace, one oversized simulation lib |
| Flashit | 5.0 | mostly local island, not well integrated with monorepo standards |
| AEP | 4.5 | core shared contract not closed; bundle too broad |
| Virtual-Org | 4.0 | ambiguous product vs shared framework role |
| YAPPC | 3.5 | highest structural complexity and package sprawl |
| Software-Org | 3.0 | stale FE deps and direct dependence on other product frameworks |
| Security-Gateway | 4.5 | responsibility is clear, placement is not |
| PHR | 2.5 | docs/code mismatch, unclear state |
| App-Platform | 1.0 | residual duplicate architecture |

### 44. Package Boundary Scores

| Package | Score / 10 |
|---|---:|
| `@ghatana/design-system` | 8.0 |
| `@ghatana/theme` | 8.0 |
| `@ghatana/tokens` | 8.0 |
| `@ghatana/ui` | 2.0 |
| `@yappc/code-editor` | 5.0 |
| `@yappc/component-traceability` | 1.0 |
| `@dcmaar/connectors` | 7.0 |
| `@tutorputor/contracts` | 7.5 |

### 45. Module Boundary Scores

| Module | Score / 10 |
|---|---:|
| `platform:java:core` | 8.5 |
| `platform:java:http` | 8.0 |
| `platform:java:kernel-capabilities` | 3.0 |
| `products:aep:platform-bundle` | 3.5 |
| `products:data-cloud:spi` | 8.0 |
| `products:yappc:services:lifecycle` | 3.0 |
| `products:finance:domains:oms` and peers | 7.0 |

### 46. File Hotspot Scores

| File | Responsibility clarity | Placement correctness | Naming quality | Cohesion | Complexity control | Maintainability | Testability | Boundary fit |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `products/yappc/frontend/libs/canvas/src/hooks/useMicroservicesExtractor.ts` | 4 | 5 | 6 | 2 | 2 | 3 | 4 | 4 |
| `products/yappc/frontend/libs/ui/src/error/AuthErrorBoundary.tsx` | 5 | 4 | 7 | 3 | 3 | 4 | 5 | 4 |
| `products/yappc/frontend/web/src/components/shared/ErrorBoundary.tsx` | 6 | 3 | 7 | 4 | 5 | 4 | 5 | 3 |
| `products/dcmaar/apps/device-health/src/ui/pages/DashboardPage.tsx` | 5 | 7 | 6 | 3 | 3 | 4 | 5 | 6 |
| `shared-services/auth-service/src/main/java/com/ghatana/services/auth/AuthService.java` | 6 | 1 | 7 | 3 | 4 | 3 | 4 | 1 |
| `products/software-org/client/web/package.json` | 3 | 2 | 6 | 4 | 5 | 3 | 3 | 2 |

### 47. Over-Engineering Score

4/10. Too many compatibility layers, residual modules, and naming shells.

### 48. Under-Engineering Score

5/10. Several hotspots are too large and mixed, but the more common repo-wide issue is misplaced abstraction rather than complete absence of structure.

### 49. Maintainability Score

4.5/10. The code is workable in local slices, but monorepo-level ownership is not obvious enough for safe change.

### 50. Simplicity Score

3.5/10. The repo is more complex than its actual ownership model requires.

### 51. Ownership Clarity Score

4.5/10. Good in some products, poor in shared/platform edges and legacy areas.

### 52. Reuse Quality Score

4/10. Reuse exists, but too much of it is oversized, accidental, or undocumented.

---

## Part 8 - Target State

### 53. Target Monorepo Layout

```text
platform/
  java/
    core/
    http/
    database/
    security/
    observability/
    workflow-api/        # only if truly neutral contract is extracted
  typescript/
    design-system/
    tokens/
    theme/
    platform-utils/
    realtime/

products/
  aep/
    runtime/
    analytics/
    registry/
    contracts/           # narrow shared contract if still AEP-owned
  data-cloud/
    spi/
    sdk/
    ui/
    feature-store-ingest/
  yappc/
    backend/
    core/
    frontend/
    platform/
  finance/
    domains/
    product/
  ...

shared-services/
  auth-gateway/
  user-profile-service/
  ai-inference-service/  # only if stabilized as real shared runtime
```

### 54. Target Product Boundary Layout

- products own their business logic, UI, adapters, and product-shaping libraries
- product-owned shared code stays under the owning product
- cross-product use happens through narrow contracts, not entire product bundles

### 55. Target Shared Library Layout

- platform libraries limited to primitives and neutral capabilities
- no deprecated aliases
- FE folder names match package names

### 56. Target Package / Module Design Principles

- no module that exists only as a facade for more than one release cycle
- no product package imported by another product unless its owner explicitly classifies it as product-owned shared
- no compatibility package without a dated delete task

### 57. Target Ownership Model

- one OWNER per shared service
- one product team per product-owned shared library
- one platform FE owner for global FE primitives
- one platform JVM owner for global Java primitives

### 58. Target Naming Model

- names reveal owner and purpose
- avoid `platform-*` inside products unless the module is actually platform-neutral
- avoid folder/package name mismatches

---

## Part 9 - Execution Plan

### 59. Immediate Moves

- remove product-to-product frontend imports that have no clear shared contract
- delete or disable `shared-services/auth-service` from active usage
- remove `shared-services/ai-registry` from active usage
- fix stale manifests and docs that reference removed or nonexistent packages

### 60. Short-Term Refactors

- split `kernel-capabilities`
- shrink or replace AEP `platform-bundle`
- collapse Finance duplicate aggregation layer
- remove deprecated FE shims in platform/YAPPC

### 61. Medium-Term Structural Changes

- archive `products/app-platform` as code
- settle Virtual-Org vs Software-Org framework ownership
- simplify YAPPC standalone build model

### 62. Long-Term Architecture Cleanup

- full FE package governance across products
- stronger build-time enforcement for package-workspace cross-product imports
- quarterly shared-services boundary review

### 63. Merge Plan

| Title | Type | Affected area | Current location | Recommended structure | Why | Benefit | Risk if skipped | Effort | Priority | Owner |
|---|---|---|---|---|---|---|---|---|---|---|
| Collapse Finance aggregation | Merge | Finance JVM | `products/finance`, `products/finance/product` | one aggregate product module | duplicated entry-point semantics | simpler product graph | ongoing confusion | S | P1 | Finance |
| Consolidate Software-Org error boundaries | Merge | Software-Org FE | two ErrorBoundary files | one product-local boundary | duplicate concept | lower FE drift | continued divergence | XS | P2 | Software-Org |
| Consolidate auth runtime | Merge | shared-services | `auth-service`, `auth-gateway` | `auth-gateway` only | ADR already decided | removes ambiguity | parallel auth logic | S | P0 | Security |

### 64. Split Plan

| Title | Type | Affected area | Current location | Recommended structure | Why | Benefit | Risk if skipped | Effort | Priority | Owner |
|---|---|---|---|---|---|---|---|---|---|---|
| Split kernel-capabilities | Split | Platform JVM | `platform/java/kernel-capabilities` | narrower capability modules | mixed concerns | clearer ownership/testing | continued god-module growth | M | P1 | Platform JVM |
| Split AEP bundle | Split | AEP | `products/aep/platform-bundle` | runtime + narrow contracts | cross-product imports too broad | safer reuse | more product coupling | M | P0 | AEP |
| Split YAPPC canvas and UI | Split | YAPPC FE | `libs/canvas`, `libs/ui` | feature families and generic subpackages | package size too high | faster change, clearer boundaries | more god-packages | L | P1 | YAPPC FE |

### 65. Move Plan

| Title | Type | Affected area | Current location | Recommended location | Why | Benefit | Risk if skipped | Effort | Priority | Owner |
|---|---|---|---|---|---|---|---|---|---|---|
| Remove Data-Cloud dependency on YAPPC editor | Move | Data-Cloud FE | `products/data-cloud/ui` -> `@yappc/code-editor` | local Data-Cloud adapter or neutral extracted package | accidental cross-product coupling | restores ownership clarity | more leakage | S | P0 | Data-Cloud FE |
| Clarify Security-Gateway placement | Move | shared auth infra | `products/security-gateway` | explicit infra/shared home or rename rationale | path implies wrong owner | boundary clarity | misleading future placement | M | P2 | Security |
| Resolve Virtual-Org framework ownership | Move | Virtual-Org / Software-Org | `products/virtual-org/modules/framework` | shared framework contract or software-org-local extension | current ambiguity | safer evolution | hard cross-product changes | M | P1 | Virtual-Org + Software-Org |

### 66. Rename Plan

| Title | Type | Affected area | Current location | Recommended name | Why | Benefit | Risk if skipped | Effort | Priority | Owner |
|---|---|---|---|---|---|---|---|---|---|---|
| Rename AEP modules away from `platform-*` | Rename | AEP | `products/aep/platform-*` | event-runtime oriented names | current names hide product ownership | honest boundaries | continued misuse | M | P2 | AEP |
| Align FE shared folder names | Rename | Platform FE | `platform/typescript/capabilities/*` | names matching package ids | current layout is misleading | easier discovery | repeated misplaced packages | S | P2 | Platform FE |

### 67. Delete / Deprecate Plan

| Title | Type | Affected area | Current location | Recommended action | Why | Benefit | Risk if skipped | Effort | Priority | Owner |
|---|---|---|---|---|---|---|---|---|---|---|
| Remove `@ghatana/ui` | Delete | Platform FE | `platform/typescript/ui` | delete after import migration | deprecated shim | less ambiguity | more accidental imports | XS | P1 | Platform FE |
| Remove `@yappc/component-traceability` | Delete | YAPPC FE | `products/yappc/frontend/libs/component-traceability` | delete | shim only | less package noise | compatibility debt | XS | P1 | YAPPC FE |
| Retire `@yappc/ide` | Delete | YAPPC FE | `products/yappc/frontend/libs/ide` | complete deprecation plan | deprecated package remains live | simpler FE landscape | ongoing split-brain UI architecture | M | P2 | YAPPC FE |
| Archive App-Platform code | Delete | legacy architecture | `products/app-platform` | archive docs, remove live code | duplicate architecture | major clarity improvement | continued false source of truth | M | P0 | Platform leadership |
| Remove `shared-services/ai-registry` | Delete | shared-services | `shared-services/ai-registry` | delete | ADR already decided | removes duplicate model-registry story | continued ambiguity | XS | P0 | Platform JVM |

### 68. Guardrails to Prevent Boundary Drift

- add package-workspace cross-product checks at root, not only inside YAPPC
- fail CI when deprecated packages remain in dependency graphs past sunset date
- require an OWNER and consumer list for every non-platform shared package
- forbid product package imports from another product unless package metadata explicitly marks it product-owned shared
- add stale-doc checks for README examples referencing removed module paths

### Execution Roadmap Table

| Phase | Window | Goal | Key actions |
|---|---|---|---|
| P0 | 0-2 weeks | stop active drift | remove stale shared-services, fix wrong package deps, archive app-platform decision |
| P1 | 2-6 weeks | clean major boundaries | split AEP bundle contract, split kernel-capabilities, collapse finance aggregation |
| P2 | 1-3 months | reduce package sprawl | YAPPC FE consolidation, Virtual-Org/Software-Org boundary decision, FE path renames |
| P3 | ongoing | governance | CI rules, quarterly shared-services review, deprecation enforcement |

---

## Part 10 - Final Decision

### 69. Go / No-Go on Current Structure

No-Go as a target state.  
Go only as a transitional state under active cleanup.

### 70. Top 10 Boundary Fixes

1. Archive `products/app-platform` as live code.
2. Replace `products:aep:platform-bundle` with a narrow shared contract surface.
3. Remove `products/data-cloud/ui` dependency on `@yappc/code-editor`.
4. Remove `shared-services/auth-service`.
5. Remove `shared-services/ai-registry`.
6. Split `platform/java/kernel-capabilities`.
7. Collapse Finance duplicate aggregation layers.
8. Resolve Virtual-Org vs Software-Org framework ownership.
9. Remove `platform/typescript/ui`.
10. Fix Software-Org frontend references to nonexistent YAPPC packages.

### 71. Top 10 Simplification Actions

1. Delete deprecated compatibility packages.
2. Rename misleading AEP modules.
3. Flatten/rename FE shared folder structure.
4. Reduce YAPPC frontend package count.
5. Reduce duplicated ErrorBoundary implementations.
6. Remove stale README and manifest paths.
7. Stop maintaining dual build truths for YAPPC where possible.
8. Keep editor/canvas packages local until reuse is real.
9. Keep product contracts inside their product unless truly generic.
10. Enforce shared-service admission criteria strictly.

### 72. Final Conclusion

The repo has strong raw ingredients: a useful platform layer, several clean product domains, and improving ownership docs. The main problem is not lack of architecture; it is too many overlapping architectures and too many package/module boundaries that no longer represent real ownership.

The highest-leverage move is to simplify the graph until the code layout says exactly one thing:

- platform is for primitives
- products own product logic
- product-owned shared stays with its product
- shared-services are rare, runtime-real, and explicitly owned

Until that happens, the monorepo will remain harder to evolve than its actual product surface requires.
