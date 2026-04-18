Audit the entire repository for **TypeScript/JavaScript code reuse opportunities** and produce an implementation-ready consolidation plan.

## Goal
Identify all places where TS/JS code should be:
- reused instead of duplicated
- abstracted into generic utilities/components/modules
- migrated into shared libraries
- moved from feature-local folders into local shared libs or global shared libs
- combined into a single source of truth with all usages updated

## Scope
Scan all TS/JS code, including:
- apps
- packages
- libs
- shared modules
- frontend components
- hooks
- utils
- API clients
- DTOs/types/interfaces
- schemas/validators
- state management code
- routing helpers
- form logic
- table/list logic
- UI patterns
- feature flags/config wrappers
- test helpers/fixtures/builders/mocks/seeds
- scripts/tooling
- build/config files where relevant

Do not limit review to obvious duplicates only. Also detect:
- near-duplicates
- copy-with-small-variation patterns
- same intent implemented differently
- hidden duplication in naming, contracts, transformations, validation, or state logic
- repeated UI composition patterns
- repeated API request/response handling
- repeated error/loading/empty states
- repeated test setup and fixtures
- repeated domain mapping logic
- repeated permission/auth checks
- repeated formatting, parsing, conversion, and adapter logic

## What to optimize for
- reuse first
- single source of truth
- simplicity
- correctness
- genericity without over-engineering
- minimal public API surface
- no library sprawl
- no duplicate local abstractions
- clear ownership and placement
- easy adoption across all current usages
- maintainability and testability

## Required review approach
Review TS/JS code only in this pass.

For every reuse candidate, determine:
1. what is duplicated
2. where it exists
3. whether it is exact duplicate, near duplicate, or conceptual duplicate
4. whether it belongs in:
   - feature-local code
   - local shared lib within one app/product
   - global shared lib across multiple apps/products
5. what the generic abstraction should be
6. what variability points should be parameterized
7. what should remain specialized and not be generalized
8. how all current usages should migrate
9. what risks or breakages may occur during migration
10. whether current tests are enough to safely refactor

## Strict rules
- Prefer existing shared libraries before creating new ones.
- Do not create a new abstraction if a good shared abstraction already exists.
- Do not create “utility dumping grounds”.
- Do not create abstractions that are generic in name only.
- Do not move code to shared just because it is used twice; validate semantic stability and ownership.
- Do not introduce backward-compatibility wrappers unless truly necessary.
- Do not preserve duplicate implementations after migration.
- Do not keep old and new paths alive in parallel.
- Do not increase dependency cycles or layering violations.
- Do not mix domain logic into low-level generic libraries.
- Do not keep multiple TS/JS libraries solving the same problem with overlapping intent.

## Specifically check these categories
1. **Types / Contracts**
   - duplicate interfaces/types/enums
   - repeated DTOs with small variations
   - repeated request/response contracts
   - duplicate zod/yup/validator schemas
   - duplicate event payload definitions
   - duplicate config schemas

2. **Domain Logic**
   - repeated computations
   - repeated mapping / transformation logic
   - repeated filtering/sorting/grouping
   - repeated business rules
   - repeated permission or feature access checks

3. **Frontend UI**
   - duplicate components
   - same component with slightly different props/variants
   - repeated page sections/cards/panels/tables/forms/dialogs
   - repeated stateful UI patterns
   - repeated loading/error/empty/skeleton behavior
   - repeated styling tokens/classes/theme wrappers

4. **Hooks / State / Data Access**
   - repeated hooks
   - repeated API query/mutation wrappers
   - repeated caching/query key patterns
   - repeated state atoms/stores/selectors
   - repeated form state or table state logic

5. **Infra / Utilities**
   - repeated fetch/client wrappers
   - repeated logging/error helpers
   - repeated date/time/number/locale helpers
   - repeated storage/session/auth helpers
   - repeated environment/config access

6. **Tests**
   - repeated fixtures/builders/factories
   - repeated render/setup helpers
   - repeated API mocks
   - repeated scenario setup
   - repeated assertions that should be standardized

## Deliverables
Produce a report with these sections:

### A. Executive summary
- biggest duplication clusters
- highest-value consolidation opportunities
- fastest safe wins
- risky areas needing care

### B. Reuse inventory
For each finding include:
- title
- duplicate locations
- current intent
- duplication type: exact / near / conceptual
- recommended target shared location
- recommended abstraction name
- generic API shape
- callers/usages to update
- expected benefits
- migration complexity: low / medium / high
- confidence: low / medium / high

### C. Shared library placement plan
Define what should become:
- local shared library
- global shared library
- remain feature-local

Include the reasoning for each.

### D. Migration sequence
Provide an ordered plan:
1. create or update shared abstraction
2. add/upgrade tests
3. migrate usages incrementally
4. delete legacy duplicates
5. validate all imports and boundaries
6. validate build/test/lint/typecheck

### E. Concrete refactor list
List exact files/modules to:
- extract
- merge
- rename
- move
- delete
- update imports for

### F. Risk list
Call out:
- over-abstraction risk
- unstable domain semantics
- type-level breakage risk
- tree-shaking/bundle-size risk
- circular dependency risk
- test fragility risk

## Final output format
Be concrete and imperative. Do not give generic advice.
Rank findings by impact.
Prefer fewer, stronger abstractions over many weak ones.
End with:
- “Top 10 immediate TS/JS consolidations”
- “Top 10 structural consolidations”
- “Do not abstract yet” list