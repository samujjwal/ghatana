## A. Kernel Boundary and Architecture

## TODO `1`: Replace hardcoded product registries with a canonical product registry

* **Priority**: Critical
* **Boundary**: Kernel
* **Area**: Architecture
* **Product(s)**: All
* **Where**: `config/product-shape.json`, `config/kernel-product-capability-registry.json`, `settings.gradle.kts`, `pnpm-workspace.yaml`, `package.json`, `scripts/check-product-manifest-contracts.mjs`
* **Problem**: Product registration is split across multiple manually maintained files with different scopes and conventions.
* **Correctness Impact**: Products can be present in Gradle/pnpm but missing from Kernel product-shape or manifest validation.
* **Abstraction/SRP Impact**: Product discovery, build registration, manifest validation, UI shape, and CI gating are spread across unrelated files.
* **Reuse/DRY Impact**: Product names, package paths, runtime surfaces, and validation rules are duplicated.
* **Performance Impact**: Repeated manual workspace scans and broad root commands increase CI/build work.
* **What to do**: Create one canonical Kernel product registry schema and generate `product-shape.json`, Gradle includes, pnpm workspace entries, CI matrices, product manifest checks, and root scripts from it.
* **Kernel vs Product Decision**: Kernel owns product registration and platform consumption metadata; products own their manifest content.
* **Why**: Ensures correctness, DRY registration, flexible product onboarding, and consistent platform consumption.
* **Validation**: Add schema tests, generated-file drift checks, `pnpm check:product-workspace-registration`, Gradle configuration cache check, and manifest conformance tests.
* **Cleanup Required**: Remove manually duplicated product declarations from scripts once generated registry is authoritative.

## TODO `2`: Remove product-specific special cases from Kernel manifest validation

* **Priority**: Critical
* **Boundary**: Kernel
* **Area**: Architecture
* **Product(s)**: Multiple
* **Where**: `scripts/check-product-manifest-contracts.mjs`
* **Problem**: The manifest checker hardcodes `phr`, `finance`, `digital-marketing`, and `flashit`, and includes finance-specific dependency logic.
* **Correctness Impact**: New products can bypass validation unless added manually; Kernel validation may encode product-specific rules.
* **Abstraction/SRP Impact**: Generic manifest validation and finance dependency enforcement are mixed in one script.
* **Reuse/DRY Impact**: Product-specific rules cannot be reused consistently through declarative product manifests.
* **Performance Impact**: Script must repeatedly parse custom hardcoded maps instead of using one registry.
* **What to do**: Replace hardcoded `MANIFESTS` and `validateFinanceDomainDependencyScopes` with declarative rules loaded from the canonical product registry and product manifest.
* **Kernel vs Product Decision**: Kernel owns generic manifest validation; finance owns finance-specific composition rules through product-owned config.
* **Why**: Prevents Kernel from becoming product-aware while preserving strict conformance.
* **Validation**: Add tests proving a new product is discovered automatically and product-specific rules are loaded only from that product’s manifest/config.
* **Cleanup Required**: Delete hardcoded product arrays and finance-specific branches from the Kernel script.

## TODO `3`: Enforce Kernel promotion and de-promotion through executable gates

* **Priority**: High
* **Boundary**: Kernel
* **Area**: Architecture
* **Product(s)**: All
* **Where**: `docs/kernel/KERNEL_PROMOTION_CRITERIA.md`, `docs/kernel/KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md`, `platform-kernel/**`, `platform-plugins/**`, `scripts/**`
* **Problem**: Promotion/de-promotion rules are documented but not fully enforced against new Kernel code.
* **Correctness Impact**: Product-specific concepts can enter Kernel without automated prevention.
* **Abstraction/SRP Impact**: Kernel scope can drift into god-product behavior.
* **Reuse/DRY Impact**: Reusable abstractions may remain product-local while product-specific abstractions may be promoted too early.
* **Performance Impact**: None.
* **What to do**: Add a `check:kernel-scope-governance` gate that verifies multi-product reuse proof, product-neutral naming, public API docs, consumer tests, and no banned product/domain terms in Kernel source.
* **Kernel vs Product Decision**: Kernel owns promotion governance; products provide reuse evidence.
* **Why**: Keeps Kernel powerful but bounded.
* **Validation**: Add positive/negative fixtures for promoted abstractions, single-product abstractions, and product-term contamination.
* **Cleanup Required**: Remove stale Kernel TODO docs that are not tied to executable gates.

---

## B. Correctness and Contract Integrity

## TODO `4`: Fix invalid policy vocabulary in product manifests

* **Priority**: Critical
* **Boundary**: Cross-Product
* **Area**: API Contracts
* **Product(s)**: digital-marketing / finance
* **Where**: `products/finance/domain-pack-manifest.yaml`, `products/digital-marketing/dm-domain-packs/domain-pack.json`, `config/kernel-product-capability-registry.json`, `platform/typescript/product-manifest-contracts/index.mjs`
* **Problem**: Finance declares unprefixed `settle`, `cancel`, wildcard `*`, wildcard resource `**`, and slash-glob resources; DMOS declares unprefixed product-specific actions/resources such as `sync`, `launch`, `pause`, `contacts`, `audiences`, and `budgets`.
* **Correctness Impact**: Policy evaluation becomes ambiguous and manifest validation can fail or be weakened.
* **Abstraction/SRP Impact**: Kernel registry is forced to understand product semantics.
* **Reuse/DRY Impact**: Products invent local action/resource vocabulary instead of using a shared namespacing contract.
* **Performance Impact**: Ambiguous wildcard policy matching can increase authorization evaluation complexity.
* **What to do**: Require product-specific actions/resources to use `<product>:<action>` and `<product>:<resource>`; remove `*` and `**`; represent hierarchical resources through explicit structured manifest fields instead of string globs.
* **Kernel vs Product Decision**: Kernel owns vocabulary rules; products own product-specific policy terms.
* **Why**: Ensures least privilege, deterministic authorization, and product-neutral Kernel policy enforcement.
* **Validation**: Add negative tests for wildcards, unprefixed product verbs, and slash-glob resources; add positive tests for namespaced resources.
* **Cleanup Required**: Remove wildcard entries and unprefixed product-specific policy strings from manifests.

## TODO `5`: Replace loose passthrough product manifest schema with strict versioned contracts

* **Priority**: High
* **Boundary**: Kernel
* **Area**: API Contracts
* **Product(s)**: All
* **Where**: `platform/typescript/product-manifest-contracts/index.mjs`, `products/phr/domain-pack-manifest.yaml`, `products/finance/domain-pack-manifest.yaml`, `products/digital-marketing/dm-domain-packs/domain-pack.json`, `products/flashit/domain-pack-manifest.yaml`
* **Problem**: `productManifestSchema` uses `.passthrough()` and loose records, while manifests differ in required fields, shape, naming, and metadata.
* **Correctness Impact**: Unknown, misspelled, deprecated, or product-specific fields can silently pass validation.
* **Abstraction/SRP Impact**: Schema is neither a strict Kernel contract nor a product-specific extension model.
* **Reuse/DRY Impact**: Product manifests cannot be reliably consumed by generators, CI, docs, or runtime loaders.
* **Performance Impact**: Loose schemas require repeated defensive parsing downstream.
* **What to do**: Introduce a versioned manifest schema with strict base fields, explicit extension sections, typed policy vocabulary, typed surfaces, typed runtime services, and product-owned extension schemas.
* **Kernel vs Product Decision**: Kernel owns base schema; products own namespaced extension schemas.
* **Why**: Makes manifests safe, reusable, composable, and toolable.
* **Validation**: Add schema fixture tests for all four products and negative tests for unknown root fields.
* **Cleanup Required**: Normalize all product manifests to the same base contract format.

## TODO `6`: Fail closed instead of silently returning empty results or completing audits

* **Priority**: Critical
* **Boundary**: Cross-Product
* **Area**: Backend
* **Product(s)**: phr / finance
* **Where**: `products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceBase.java`, `products/finance/src/main/java/com/ghatana/finance/kernel/service/FinanceServiceBase.java`
* **Problem**: `readRecord`, `queryRecords`, `createSchema`, and `audit` swallow exceptions or return empty results when not running or when audit/schema operations fail.
* **Correctness Impact**: Data-access failures can look like valid empty reads; audit failures can disappear in regulated flows.
* **Abstraction/SRP Impact**: Lifecycle handling, persistence, audit policy, and error strategy are embedded in product service bases.
* **Reuse/DRY Impact**: Same unsafe failure pattern is duplicated between PHR and Finance.
* **Performance Impact**: Silent retries/debug loops can waste runtime and operational investigation time.
* **What to do**: Add a Kernel/shared data service base with explicit failure modes: `ServiceNotRunning`, `DataAccessFailed`, `AuditWriteFailed`, `SchemaAlreadyExists`, and product-configurable audit durability policy.
* **Kernel vs Product Decision**: Kernel/shared library owns failure semantics; products map failures to domain responses.
* **Why**: Correctness and audit integrity must be deterministic.
* **Validation**: Add unit and integration tests proving reads fail closed, audit failures are recorded or surfaced according to policy, and schema-exists is the only tolerated non-fatal schema path.
* **Cleanup Required**: Remove duplicated failure-swallowing code from PHR and Finance service bases.

---

## C. Cross-Product Duplication and Reuse

## TODO `7`: Extract duplicated product data-service base into reusable Kernel/shared abstraction

* **Priority**: Critical
* **Boundary**: Shared Library
* **Area**: Backend
* **Product(s)**: phr / finance
* **Where**: `products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceBase.java`, `products/finance/src/main/java/com/ghatana/finance/kernel/service/FinanceServiceBase.java`
* **Problem**: PHR and Finance contain near-identical lifecycle, DataCloud, serialization, query, mutation, audit, ID generation, and validation logic.
* **Correctness Impact**: Fixes to metadata, idempotency, lifecycle, audit, or serialization must be applied in multiple places.
* **Abstraction/SRP Impact**: Product service bases contain generic platform data-access behavior.
* **Reuse/DRY Impact**: Major duplicated backend platform logic exists across products.
* **Performance Impact**: Each product repeats blocking serialization and query mapping patterns independently.
* **What to do**: Create `platform-kernel` or `platform/java/database` abstraction such as `ProductDataServiceBase<TContext>` with injectable metadata strategy, tenant resolver, audit strategy, serializer, and executor.
* **Kernel vs Product Decision**: Shared platform owns the generic data-access template; products own metadata naming, entity classification, and domain-specific owner inference.
* **Why**: Reduces duplication while keeping domain semantics product-owned.
* **Validation**: Add shared tests plus PHR/Finance conformance tests proving identical lifecycle, mutation, query, and audit behavior.
* **Cleanup Required**: Delete duplicated template-method code from PHR and Finance after migration.

## TODO `8`: Extract shared data-access context contract across Java and TypeScript products

* **Priority**: High
* **Boundary**: Kernel
* **Area**: Data
* **Product(s)**: phr / finance / flashit / digital-marketing
* **Where**: `products/flashit/backend/gateway/src/lib/data-access-context.ts`, `products/digital-marketing/dm-core-contracts/src/main/java/com/ghatana/digitalmarketing/contracts/DmOperationContext.java`, `products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceBase.java`, `products/finance/src/main/java/com/ghatana/finance/kernel/service/FinanceServiceBase.java`, `scripts/check-data-access-contract.mjs`
* **Problem**: Tenant, principal, correlation, idempotency, audit classification, and data owner metadata are implemented separately per product and checked through token scans.
* **Correctness Impact**: Metadata naming and required fields can drift across languages and products.
* **Abstraction/SRP Impact**: Product code owns platform data-access metadata construction.
* **Reuse/DRY Impact**: Same context concept exists in Java and TypeScript without a canonical contract.
* **Performance Impact**: Repeated context construction and validation paths increase code size and runtime inconsistency.
* **What to do**: Define canonical Kernel `DataAccessContext` contract with Java and TypeScript bindings, product extension metadata, tenant resolver SPI, idempotency requirements, and generated test fixtures.
* **Kernel vs Product Decision**: Kernel owns core context contract; products own domain metadata extensions.
* **Why**: Creates cross-product correctness and reuse without making Kernel domain-specific.
* **Validation**: Replace token scans with schema/contract tests and product conformance tests.
* **Cleanup Required**: Remove ad-hoc context builders after product migration.

## TODO `9`: Replace one-off product conformance scripts with a generic Kernel conformance runner

* **Priority**: High
* **Boundary**: Kernel
* **Area**: Testing
* **Product(s)**: All
* **Where**: `scripts/check-data-access-contract.mjs`, `scripts/check-observability-conformance.mjs`, `scripts/check-route-entitlement-contracts.mjs`, `scripts/check-bridge-compliance.mjs`, `scripts/check-product-manifest-contracts.mjs`
* **Problem**: Several conformance checks use hardcoded paths, tokens, product names, and product-specific expectations.
* **Correctness Impact**: Token presence can pass while real behavior is wrong; new products can be missed.
* **Abstraction/SRP Impact**: Kernel conformance logic and product evidence declarations are mixed.
* **Reuse/DRY Impact**: Each script reimplements file existence, token scanning, and product coverage logic.
* **Performance Impact**: Multiple scripts repeatedly read and scan overlapping files.
* **What to do**: Build a generic `kernel-conformance` runner that loads product conformance manifests and executes typed validators for manifests, routes, bridges, observability, data access, security, UI, and tests.
* **Kernel vs Product Decision**: Kernel owns validator framework; products own evidence manifests.
* **Why**: Improves correctness, composability, and scalable product onboarding.
* **Validation**: Add fixture products with passing/failing conformance manifests.
* **Cleanup Required**: Delete or shrink product-hardcoded scripts after migration.

---

## D. SRP and Composability

## TODO `10`: Move route entitlement metadata from frontend-local files to generated shared contracts

* **Priority**: Critical
* **Boundary**: Cross-Product
* **Area**: Frontend
* **Product(s)**: phr / digital-marketing / flashit
* **Where**: `products/phr/apps/web/src/routeManifest.tsx`, `products/digital-marketing/ui/src/routeManifest.tsx`, `products/flashit/client/web/src/routeManifest.tsx`, `products/flashit/backend/gateway/src/routes/route-manifest.contract.ts`, `platform/typescript/product-shell/src/types.ts`
* **Problem**: Route metadata, entitlement metadata, route elements, personas, tiers, actions, and cards are assembled differently per product.
* **Correctness Impact**: Frontend route visibility can drift from backend entitlement decisions.
* **Abstraction/SRP Impact**: UI route rendering, entitlement contracts, route metadata, and product page mapping are mixed.
* **Reuse/DRY Impact**: Products duplicate route capability patterns instead of consuming a generated Kernel contract.
* **Performance Impact**: Local transformation and deduplication run at app startup.
* **What to do**: Create a shared route contract generator that emits product route contract packages consumed by both backend entitlement APIs and frontend route manifests.
* **Kernel vs Product Decision**: Kernel owns route contract shape/generator; products own route definitions and page bindings.
* **Why**: Ensures composable, contract-safe route/entitlement flows.
* **Validation**: Add frontend/backend route parity tests for all UI products.
* **Cleanup Required**: Remove product-local route transformation helpers that duplicate contract logic.

## TODO `11`: Stop frontend packages from importing backend source directly

* **Priority**: Critical
* **Boundary**: Product
* **Area**: Architecture
* **Product(s)**: flashit
* **Where**: `products/flashit/client/web/src/routeManifest.tsx`, `products/flashit/backend/gateway/src/routes/route-manifest.contract.ts`
* **Problem**: FlashIt web imports `flashItRouteContracts` directly from backend gateway source.
* **Correctness Impact**: Frontend build becomes coupled to backend source layout and runtime module constraints.
* **Abstraction/SRP Impact**: Backend route implementation and shared route contract are not separated.
* **Reuse/DRY Impact**: Contract reuse is achieved through illegal source coupling instead of a shared package.
* **Performance Impact**: Can increase frontend bundle/build coupling and invalidate caches when backend internals change.
* **What to do**: Move route contracts to `products/flashit/libs/ts/contracts` or a Kernel route-contract package, then make backend and frontend consume that package.
* **Kernel vs Product Decision**: Product owns FlashIt route definitions; Kernel owns route contract interface.
* **Why**: Preserves clean layering and composability.
* **Validation**: Add dependency-boundary rule preventing `client/**` from importing `backend/**`.
* **Cleanup Required**: Remove direct relative import from frontend to backend source.

## TODO `12`: Extract repeated ProductShell wrapper configuration into composable shell adapters

* **Priority**: High
* **Boundary**: Kernel
* **Area**: Frontend
* **Product(s)**: phr / digital-marketing / flashit
* **Where**: `products/phr/apps/web/src/layout/PhrProductShell.tsx`, `products/digital-marketing/ui/src/layout/DmosProductShell.tsx`, `products/flashit/client/web/src/components/FlashitProductShell.tsx`, `platform/typescript/product-shell/src/components/ProductShell.tsx`, `platform/typescript/product-shell/src/types.ts`
* **Problem**: Product wrappers duplicate role labels, role descriptions, sidebar footer text, header action composition, search wiring, and main content props.
* **Correctness Impact**: Accessibility, logout behavior, role disclosure, and shell copy can drift across products.
* **Abstraction/SRP Impact**: Product wrappers contain both product-specific branding and platform shell boilerplate.
* **Reuse/DRY Impact**: Shared shell consumption is still repetitive.
* **Performance Impact**: Repeated config-building patterns increase rerender risk.
* **What to do**: Add `createProductShellAdapter` and `useProductShellEntitlements` APIs that hydrate common shell config from product manifest/route entitlement payloads while preserving product slots for logo, header actions, and domain-specific controls.
* **Kernel vs Product Decision**: Kernel owns shell adapter; products own branding and domain actions.
* **Why**: Makes Kernel easier to consume without centralizing product UX logic.
* **Validation**: Add shared shell adapter tests and product wrapper snapshots/behavioral tests.
* **Cleanup Required**: Remove repeated sidebar footer and role boilerplate from product wrappers.

---

## E. Kernel Consumption Experience

## TODO `13`: Make product scaffolding generate complete product conformance artifacts

* **Priority**: High
* **Boundary**: Kernel
* **Area**: DevEx
* **Product(s)**: All
* **Where**: `scripts/scaffold-product.mjs`, `scripts/check-product-scaffolder.mjs`, `config/product-shape.json`, `docs/kernel/PRODUCT_DEVELOPMENT_GUIDE.md`
* **Problem**: Product scaffolding is not the single source for product registry, manifest, CI, shell, conformance, test, and docs setup.
* **Correctness Impact**: Newly added products may miss required Kernel contracts.
* **Abstraction/SRP Impact**: Product onboarding knowledge is spread across docs, scripts, package files, and Gradle settings.
* **Reuse/DRY Impact**: Product setup remains manually duplicated.
* **Performance Impact**: Manual setup can create unnecessary build/test targets.
* **What to do**: Update scaffolder to generate manifest, product registry entry, Gradle/pnpm registration, shell adapter, route entitlement contract, observability flow manifest, data-access context fixture, CI matrix entry, and starter tests.
* **Kernel vs Product Decision**: Kernel owns scaffolding; products fill domain-specific fields.
* **Why**: Products should focus on core business logic only.
* **Validation**: Add golden tests that scaffold a product and run all Kernel conformance checks.
* **Cleanup Required**: Remove outdated product-on-Kernel examples that do not match generated output.

## TODO `14`: Replace root package product-specific scripts with generated product task groups

* **Priority**: High
* **Boundary**: Infra
* **Area**: DevEx
* **Product(s)**: All
* **Where**: `package.json`, `config/product-shape.json`, `pnpm-workspace.yaml`
* **Problem**: Root scripts contain many product-specific commands and inconsistent build/test coverage.
* **Correctness Impact**: Some products use explicit scripts while generic root scripts miss packages outside `products/*/ui`.
* **Abstraction/SRP Impact**: Root package mixes platform tasks, product tasks, conformance tasks, and one-off checks.
* **Reuse/DRY Impact**: Product commands are manually duplicated.
* **Performance Impact**: Root recursive commands may overbuild or underbuild due to incomplete filters.
* **What to do**: Generate `build:<product>`, `test:<product>`, `typecheck:<product>`, and `check:<product>` from canonical product registry and CI matrix.
* **Kernel vs Product Decision**: Kernel/infra owns task generation; products own package-level scripts.
* **Why**: Ensures consistent, scalable product consumption.
* **Validation**: Add script drift check and dry-run all generated product task groups.
* **Cleanup Required**: Remove hand-maintained product-specific root script sprawl.

---

## F. Backend Platform Capabilities

## TODO `15`: Replace inferred default tenant/principal/idempotency values with required context validation

* **Priority**: Critical
* **Boundary**: Kernel
* **Area**: Data
* **Product(s)**: phr / finance
* **Where**: `products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceBase.java`, `products/finance/src/main/java/com/ghatana/finance/kernel/service/FinanceServiceBase.java`
* **Problem**: Mutation metadata inserts defaults such as `tenantId/default`, `tenant_id/default`, `principalId/system`, `principal_id/system`, and generated idempotency keys.
* **Correctness Impact**: Missing tenant/principal/idempotency context can silently become valid-looking production metadata.
* **Abstraction/SRP Impact**: Product service base guesses security context instead of requiring Kernel context.
* **Reuse/DRY Impact**: Unsafe inference exists in multiple products.
* **Performance Impact**: Incorrect idempotency can increase duplicate writes and reconciliation cost.
* **What to do**: Require explicit `DataAccessContext` for all mutations; fail closed when tenant, principal, correlation ID, or required idempotency key is missing.
* **Kernel vs Product Decision**: Kernel owns required context fields; products own domain owner-scope mapping.
* **Why**: Security and data integrity depend on explicit context.
* **Validation**: Add tests for missing tenant, missing principal, missing idempotency, and unauthorized owner scope.
* **Cleanup Required**: Remove default/system fallback logic.

## TODO `16`: Introduce durable audit outbox for product mutations

* **Priority**: Critical
* **Boundary**: Kernel
* **Area**: Observability
* **Product(s)**: phr / finance / flashit / digital-marketing
* **Where**: `products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceBase.java`, `products/finance/src/main/java/com/ghatana/finance/kernel/service/FinanceServiceBase.java`, `products/flashit/backend/gateway/src/routes/moments.ts`, `products/digital-marketing/dm-kernel-bridge/**`
* **Problem**: Audit behavior is implemented inconsistently and can be non-blocking or swallowed.
* **Correctness Impact**: Regulated workflows can mutate data without guaranteed audit evidence.
* **Abstraction/SRP Impact**: Products individually decide audit durability.
* **Reuse/DRY Impact**: Audit recording and retry semantics are duplicated.
* **Performance Impact**: Synchronous audit calls can block critical paths; swallowed failures hide retry needs.
* **What to do**: Add Kernel audit outbox with durable persistence, retry, DLQ, correlation, tenant/principal propagation, and product-configurable blocking policy.
* **Kernel vs Product Decision**: Kernel owns audit durability mechanics; products declare which actions require blocking audit.
* **Why**: Enables production-grade audit integrity without product duplication.
* **Validation**: Add mutation + audit atomicity tests, outbox retry tests, and DLQ observability tests.
* **Cleanup Required**: Remove product-local fire-and-forget audit patterns.

---

## G. Frontend Platform Capabilities

## TODO `17`: Add Kernel route-entitlement hydration hook and remove frontend-only visibility decisions

* **Priority**: Critical
* **Boundary**: Kernel
* **Area**: AuthZ
* **Product(s)**: phr / digital-marketing / flashit
* **Where**: `platform/typescript/product-shell/src/types.ts`, `products/phr/apps/web/src/routeManifest.tsx`, `products/digital-marketing/ui/src/routeManifest.tsx`, `products/flashit/client/web/src/routeAccess.ts`, `products/flashit/client/web/src/routeManifest.tsx`
* **Problem**: Products still compute route access locally using role order and static metadata.
* **Correctness Impact**: UI visibility can diverge from backend authorization and entitlement APIs.
* **Abstraction/SRP Impact**: Product UI mixes display logic with authorization assumptions.
* **Reuse/DRY Impact**: Role-order access helpers are repeated per product.
* **Performance Impact**: Local filtering and duplicated entitlement requests can cause inconsistent rendering.
* **What to do**: Add `useProductRouteEntitlements(productId)` and shell-level fail-closed loading/error/empty states backed by product entitlement APIs.
* **Kernel vs Product Decision**: Kernel owns entitlement hydration and shell handling; products own entitlement API implementation.
* **Why**: Keeps UI simple while backend remains source of truth.
* **Validation**: Add e2e tests proving unauthorized routes are hidden and direct navigation is denied.
* **Cleanup Required**: Remove product-local role filtering as the source of truth.

## TODO `18`: Enforce design-system token usage without permanent product allowlists

* **Priority**: High
* **Boundary**: Kernel
* **Area**: UI/UX
* **Product(s)**: phr / digital-marketing / flashit
* **Where**: `config/frontend-conformance-allowlist.json`, `products/phr/apps/web/src/styles.css`, `products/*/ui/**`, `products/*/apps/web/**`, `products/flashit/client/web/**`, `platform/typescript/design-system/**`
* **Problem**: Frontend conformance allows product-local hardcoded styles and local layout primitives.
* **Correctness Impact**: Visual consistency, accessibility, and token usage can drift.
* **Abstraction/SRP Impact**: Products can own platform styling concerns.
* **Reuse/DRY Impact**: Shared design-system primitives are bypassed.
* **Performance Impact**: Duplicated CSS increases bundle size and maintenance cost.
* **What to do**: Add expiring allowlist entries with owners/removal dates and enforce design-token usage for colors, spacing, typography, radius, shadows, and states.
* **Kernel vs Product Decision**: Design system owns primitives/tokens; products own composition and content.
* **Why**: Ensures simple, consistent, production-grade UI across products.
* **Validation**: Add stylelint/token conformance and visual regression checks.
* **Cleanup Required**: Remove permanent hardcoded-style exceptions.

---

## H. Product-Specific Corrections

## TODO `19`: Fix FlashIt build aggregation target

* **Priority**: High
* **Boundary**: Product
* **Area**: Infrastructure
* **Product(s)**: flashit
* **Where**: `products/flashit/build.gradle.kts`, `settings.gradle.kts`
* **Problem**: `buildAll` depends on `:agent:build`, but the settings file includes `:products:flashit` and not a root `:agent` module.
* **Correctness Impact**: Aggregate FlashIt build can fail or reference the wrong project.
* **Abstraction/SRP Impact**: Product build orchestration is hardcoded instead of registry-driven.
* **Reuse/DRY Impact**: Build composition is not generated from product component declarations.
* **Performance Impact**: Broken aggregation prevents reliable incremental build execution.
* **What to do**: Replace `:agent:build` with the correct registered FlashIt backend/agent module path or generate FlashIt aggregate tasks from the product registry.
* **Kernel vs Product Decision**: Product owns component paths; Kernel/infra owns aggregate task generation.
* **Why**: Product builds must be deterministic.
* **Validation**: Run `./gradlew :products:flashit:buildAll` and root product CI matrix.
* **Cleanup Required**: Remove stale target references.

## TODO `20`: Normalize PHR source-set structure into explicit submodules

* **Priority**: Medium
* **Boundary**: Product
* **Area**: Backend
* **Product(s)**: phr
* **Where**: `products/phr/build.gradle.kts`, `products/phr/domains/healthcare/**`
* **Problem**: PHR root build remaps `main` and `test` source sets directly to `domains/healthcare`.
* **Correctness Impact**: Domain code can be hidden inside the root product module instead of explicit module boundaries.
* **Abstraction/SRP Impact**: Product root composition and healthcare domain implementation are mixed.
* **Reuse/DRY Impact**: Domain module pattern differs from Finance domain modules.
* **Performance Impact**: Reduced module granularity limits incremental builds and targeted tests.
* **What to do**: Create explicit `:products:phr:domains:healthcare` module and make root PHR compose it through runtime/implementation dependency.
* **Kernel vs Product Decision**: Product owns PHR domain module; Kernel/infra owns module convention.
* **Why**: Aligns product structure with composable product architecture.
* **Validation**: Run Gradle build, PHR release gate, and dependency boundary checks.
* **Cleanup Required**: Remove custom `sourceSets` remapping from root PHR build.

## TODO `21`: Make Finance backend-only declaration consistent with product submodules

* **Priority**: Medium
* **Boundary**: Product
* **Area**: Architecture
* **Product(s)**: finance
* **Where**: `config/product-shape.json`, `products/finance/README.md`, `settings.gradle.kts`, `products/finance/**`
* **Problem**: Finance is declared backend-only in product shape while settings include modules such as `regulator-portal`, `operator-workflows`, and platform SDK surfaces that may expose product surfaces beyond a simple backend-only classification.
* **Correctness Impact**: UI/client/API surface validation may skip Finance surfaces that should be governed.
* **Abstraction/SRP Impact**: Product surface classification is too coarse.
* **Reuse/DRY Impact**: Surface declarations are not derived from actual modules.
* **Performance Impact**: CI may skip relevant checks or run irrelevant checks.
* **What to do**: Replace boolean `ui`/`backend-only` with typed product surfaces: `backend-api`, `worker`, `portal`, `sdk`, `web`, `mobile`, `cli`, `operator`.
* **Kernel vs Product Decision**: Kernel owns surface taxonomy; Finance declares actual surfaces.
* **Why**: Prevents skipped conformance on non-web product surfaces.
* **Validation**: Add product-shape schema tests and Finance surface conformance tests.
* **Cleanup Required**: Remove coarse backend-only logic after typed surfaces are introduced.

---

## I. Security, Privacy, Governance, and Compliance

## TODO `22`: Move product-specific canonical policy resources out of Kernel registry

* **Priority**: High
* **Boundary**: Kernel
* **Area**: Security
* **Product(s)**: All
* **Where**: `config/kernel-product-capability-registry.json`
* **Problem**: Kernel canonical policy resources include product/domain-like terms such as `records`, `moments`, `campaigns`, `accounts`, and `transactions`.
* **Correctness Impact**: Product domains leak into Kernel authorization vocabulary.
* **Abstraction/SRP Impact**: Kernel policy registry mixes platform resources and product resources.
* **Reuse/DRY Impact**: Future products may incorrectly reuse another product’s resource terms.
* **Performance Impact**: None.
* **What to do**: Keep only truly platform-level canonical resources in Kernel and require product resources to be namespaced in product manifests.
* **Kernel vs Product Decision**: Kernel owns policy grammar; products own product resource vocabulary.
* **Why**: Prevents god-Kernel drift and preserves product boundaries.
* **Validation**: Add Kernel purity tests for resource vocabulary and manifest validation tests.
* **Cleanup Required**: Remove product-domain resources from Kernel registry.

## TODO `23`: Replace source-token compliance checks with behavioral authorization tests

* **Priority**: High
* **Boundary**: Tests
* **Area**: AuthZ
* **Product(s)**: All
* **Where**: `scripts/check-bridge-compliance.mjs`, `scripts/check-route-entitlement-contracts.mjs`, product bridge tests, product entitlement tests
* **Problem**: Compliance scripts check for method names and token strings instead of enforcing behavior through shared conformance tests.
* **Correctness Impact**: Code can contain required strings while still failing authorization behavior.
* **Abstraction/SRP Impact**: Compliance behavior is encoded in scripts rather than reusable test contracts.
* **Reuse/DRY Impact**: Each product needs custom evidence tokens.
* **Performance Impact**: Repeated file scanning adds CI overhead with weak guarantees.
* **What to do**: Create shared authorization/bridge conformance test suites that products implement through fixtures and adapters.
* **Kernel vs Product Decision**: Kernel owns conformance tests; products supply adapters and fixtures.
* **Why**: Authorization must be proven behaviorally.
* **Validation**: Add negative tests proving unauthorized bridge, route, and feature-flag paths fail closed.
* **Cleanup Required**: Remove token-only compliance checks after behavioral coverage exists.

---

## J. Observability and Operations

## TODO `24`: Generate observability flow checks from product manifests

* **Priority**: High
* **Boundary**: Kernel
* **Area**: Observability
* **Product(s)**: All
* **Where**: `scripts/check-observability-conformance.mjs`, `config/observability/product-observability-flows.json`, `monitoring/**`, `products/*/monitoring/**`
* **Problem**: Observability conformance has hardcoded product files and flow expectations.
* **Correctness Impact**: New product flows can be missed or stale evidence can pass.
* **Abstraction/SRP Impact**: Product flow declarations and Kernel validation are mixed.
* **Reuse/DRY Impact**: Observability facets are duplicated across script and config.
* **Performance Impact**: Repeated token scanning is inefficient and weak.
* **What to do**: Make product manifests declare required operational flows and generate observability conformance checks from those declarations.
* **Kernel vs Product Decision**: Kernel owns observability facets and validator; products own flow evidence.
* **Why**: Ensures consistent observability without hardcoding products.
* **Validation**: Add tests for missing logs, metrics, traces, audit events, dashboards, alerts, and health checks.
* **Cleanup Required**: Remove hardcoded product file list from observability script.

---

## K. Testing and Quality Infrastructure

## TODO `25`: Replace production-stub scan exclusions with risk-based scoped scanning

* **Priority**: High
* **Boundary**: Infra
* **Area**: Testing
* **Product(s)**: All
* **Where**: `scripts/check-production-stubs.mjs`, `config/production-critical-scopes.config.json`
* **Problem**: Production stub scanning excludes broad path segments such as `scripts/`, `monitoring/`, `config/`, `docs/`, `integration-tests/`, and `frontend/`.
* **Correctness Impact**: Critical platform scripts and generated configs can contain placeholders without being scanned.
* **Abstraction/SRP Impact**: Production-critical ownership is path-excluded instead of risk-classified.
* **Reuse/DRY Impact**: Each scanner may need separate exception logic.
* **Performance Impact**: Broad scans can be expensive, but broad exclusions reduce correctness.
* **What to do**: Replace path exclusions with explicit scope classifications: production runtime, production tooling, generated artifacts, tests, docs, examples, and archived content.
* **Kernel vs Product Decision**: Infra owns scan policy; products can declare scoped exclusions with expiry.
* **Why**: Ensures no production-critical path bypasses quality gates.
* **Validation**: Add fixtures proving scripts/config/monitoring production files are scanned while test/docs/examples are excluded.
* **Cleanup Required**: Remove broad `frontend/` and `scripts/` exclusions where production-critical.

## TODO `26`: Standardize product UI test scripts and coverage gates

* **Priority**: High
* **Boundary**: Infra
* **Area**: Testing
* **Product(s)**: phr / digital-marketing / flashit
* **Where**: `products/phr/apps/web/package.json`, `products/digital-marketing/ui/package.json`, `products/flashit/client/web/package.json`, `package.json`
* **Problem**: Product UI packages use inconsistent test script names and coverage semantics.
* **Correctness Impact**: Root test commands cannot reliably run the same test tiers for all products.
* **Abstraction/SRP Impact**: Product packages independently define platform test taxonomy.
* **Reuse/DRY Impact**: Test tier conventions are repeated and inconsistent.
* **Performance Impact**: CI may overrun or miss tests due to inconsistent script names.
* **What to do**: Define Kernel UI test script contract: `test:unit`, `test:integration`, `test:e2e`, `test:e2e:a11y`, `test:coverage`, `type-check`, `lint`, and enforce through package conformance.
* **Kernel vs Product Decision**: Kernel/infra owns script taxonomy; products own test content.
* **Why**: Consistent test tiers are required for production-grade products.
* **Validation**: Add `check:product-package-metadata` rules for required scripts and coverage thresholds.
* **Cleanup Required**: Rename inconsistent package scripts and update root task generation.

---

## L. Performance, Scalability, and Efficiency

## TODO `27`: Enforce dependency version alignment across product UIs

* **Priority**: High
* **Boundary**: Infra
* **Area**: Performance
* **Product(s)**: phr / digital-marketing / flashit
* **Where**: `package.json`, `products/phr/apps/web/package.json`, `products/digital-marketing/ui/package.json`, `products/flashit/client/web/package.json`, `pnpm-workspace.yaml`
* **Problem**: Product packages declare versions that differ from root overrides for React, Vite, TanStack Query, Tailwind, and related tooling.
* **Correctness Impact**: Different dependency ranges can produce subtle runtime/test differences.
* **Abstraction/SRP Impact**: Dependency governance is split between root overrides and product package files.
* **Reuse/DRY Impact**: Versions are repeated in multiple package manifests.
* **Performance Impact**: Version drift can increase bundle size, duplicate dependency graphs, and install/build time.
* **What to do**: Move product UI dependency versions into one workspace catalog and require product packages to use `workspace:*` or catalog references for platform-approved dependencies.
* **Kernel vs Product Decision**: Infra owns dependency governance; products own product-only dependencies.
* **Why**: Prevents library/version sprawl.
* **Validation**: Add dependency drift gate and bundle duplicate analysis.
* **Cleanup Required**: Remove duplicated dependency versions from product package files.

## TODO `28`: Replace local route transformation with precomputed generated manifests

* **Priority**: Medium
* **Boundary**: Product
* **Area**: Performance
* **Product(s)**: digital-marketing
* **Where**: `products/digital-marketing/ui/src/routeManifest.tsx`, `products/digital-marketing/ui/src/generated/routeManifest.generated`
* **Problem**: DMOS route manifest performs runtime path mapping, metadata merging, deduplication, and action replacement.
* **Correctness Impact**: Runtime transformation can hide contract drift and merge collisions.
* **Abstraction/SRP Impact**: Route contract generation and UI consumption are mixed.
* **Reuse/DRY Impact**: Metadata lives partly in generated route manifest and partly in handwritten map.
* **Performance Impact**: Work is repeated at app startup and can grow with route count.
* **What to do**: Move all route transformation to build-time generation and make UI import a final typed manifest.
* **Kernel vs Product Decision**: Product owns route definitions; Kernel route generator owns transformation.
* **Why**: Improves correctness and startup efficiency.
* **Validation**: Add generated-manifest drift test and UI startup route snapshot.
* **Cleanup Required**: Delete runtime `toUiPath`, `mergeRouteActions`, and handwritten metadata map after generation.

---

## M. Infrastructure, Build, and DevEx

## TODO `29`: Replace direct product dependency on `react-router-dom` with Kernel router facade

* **Priority**: Medium
* **Boundary**: Kernel
* **Area**: Frontend
* **Product(s)**: phr / digital-marketing / flashit
* **Where**: `products/phr/apps/web/package.json`, `products/digital-marketing/ui/package.json`, `products/flashit/client/web/package.json`, product route files
* **Problem**: Product packages depend directly on `react-router-dom` even though router/version strategy is a Kernel frontend platform concern.
* **Correctness Impact**: Products can drift router versions or patterns.
* **Abstraction/SRP Impact**: Product routing implementation is tied directly to third-party APIs.
* **Reuse/DRY Impact**: Guarded routes, unsupported routes, and navigation helpers are duplicated.
* **Performance Impact**: Version drift can increase bundle duplication.
* **What to do**: Provide `@ghatana/product-router` facade for route declarations, guarded routes, navigation helpers, and entitlement-aware routing.
* **Kernel vs Product Decision**: Kernel owns router abstraction; products own route definitions and pages.
* **Why**: Keeps product code focused on domain navigation, not router plumbing.
* **Validation**: Add dependency rule preventing product UI direct router dependency except inside approved adapter.
* **Cleanup Required**: Remove direct product router dependency after migration.

## TODO `30`: Split root build settings into generated product/platform include files

* **Priority**: Medium
* **Boundary**: Infra
* **Area**: Infrastructure
* **Product(s)**: All
* **Where**: `settings.gradle.kts`
* **Problem**: `settings.gradle.kts` manually includes many platform and product modules in one large file.
* **Correctness Impact**: Module registration can drift from product manifests and registry.
* **Abstraction/SRP Impact**: Root settings owns product topology manually instead of delegating to generated registries.
* **Reuse/DRY Impact**: Module lists are duplicated with product docs/manifests/build files.
* **Performance Impact**: Broad module inclusion can slow configuration and reduce targeted build efficiency.
* **What to do**: Generate product include blocks from product registry and split platform/product/shared-service includes into separate generated files.
* **Kernel vs Product Decision**: Infra owns include generation; products declare modules.
* **Why**: Improves maintainability and build performance.
* **Validation**: Run Gradle configuration cache, included-build validation, and generated-file drift check.
* **Cleanup Required**: Remove hand-maintained product include sprawl.

---

## N. Documentation and Repo Cleanup

## TODO `31`: Consolidate Kernel/product docs around executable contracts

* **Priority**: Medium
* **Boundary**: Docs
* **Area**: Docs
* **Product(s)**: All
* **Where**: `docs/kernel/**`, `docs/generated/**`, `KERNEL_BOUNDARY_HARDENING_PLAN.md`, product docs under `products/*/docs/**`
* **Problem**: Kernel responsibility, promotion criteria, audit progress, generated matrices, and hardening plans exist across multiple docs.
* **Correctness Impact**: Developers may follow stale docs instead of executable gates.
* **Abstraction/SRP Impact**: Governance rules are split between narrative docs and scripts.
* **Reuse/DRY Impact**: Ownership and capability rules are repeated.
* **Performance Impact**: None.
* **What to do**: Keep a minimal canonical doc set: responsibility matrix, promotion criteria, product development guide, conformance guide, and generated capability matrix.
* **Kernel vs Product Decision**: Kernel owns platform docs; products own domain docs.
* **Why**: Prevents audit churn and duplicated guidance.
* **Validation**: Add doc-truth checks that every mandatory claim links to executable gate or canonical source.
* **Cleanup Required**: Archive or delete stale hardening plans, old audit progress docs, and duplicate generated matrices.

## TODO `32`: Add cleanup gate for dead product examples, stale comments, and temporary migration notes

* **Priority**: Medium
* **Boundary**: Docs
* **Area**: Docs
* **Product(s)**: All
* **Where**: `docs/kernel/examples/**`, `products/*/README.md`, `products/*/docs/**`, `settings.gradle.kts`, `build.gradle.kts` files
* **Problem**: Comments and examples reference migrations, legacy modules, and future setup steps without executable tracking.
* **Correctness Impact**: Developers may preserve obsolete compatibility paths.
* **Abstraction/SRP Impact**: Documentation and build files carry historical decisions that should be encoded as current contracts.
* **Reuse/DRY Impact**: Stale examples cause repeated manual product setup.
* **Performance Impact**: None.
* **What to do**: Add a cleanup check that flags migration comments, deprecated module comments, future TODOs, stale examples, and non-canonical setup instructions unless linked to an active issue or removal plan.
* **Kernel vs Product Decision**: Kernel/infra owns cleanup gate; products own current docs.
* **Why**: Keeps repo clean and avoids repeated audits finding the same clutter.
* **Validation**: Add positive/negative cleanup fixtures and run in production-readiness gate.
* **Cleanup Required**: Remove obsolete comments and non-canonical examples after gate adoption.
