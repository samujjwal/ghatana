# Module Admission Checklist

> Every new Gradle or pnpm module MUST pass this checklist before it can be merged.
> This ensures we do not regress on module count, boundary clarity, or ownership.

## Pre-Creation Checklist

Before writing any code, answer these questions:

### 1. Justification

- [ ] **Why is a new module needed?** (Can this code live in an existing module?)
- [ ] **What problem does this module solve?** (One sentence)
- [ ] **Have you checked `libs/java/*` and `libs/*` for existing solutions?**

### 2. Ownership

- [ ] **Owner:** _________ (Team or individual)
- [ ] **Consumers:** _________ (List known consumers, or "internal-only")
- [ ] **Product:** _________ (Which product does this belong to?)

### 3. Classification

| Property | Value |
|----------|-------|
| Module type | `product` / `platform-lib` / `shared-lib` |
| Boundary type | `local` / `owned-shared` / `global-shared` |
| Language | Java / TypeScript / Both |

### 4. Build Requirements

- [ ] Module builds in isolation (`./gradlew :path:to:module:build`)
- [ ] Tests run without sibling modules
- [ ] No circular dependencies introduced
- [ ] Added to `settings.gradle.kts` or `pnpm-workspace.yaml`

### 5. Documentation

- [ ] README.md with purpose, usage, ownership
- [ ] All public classes have JavaDoc with `@doc.*` tags
- [ ] Added to shared-library-registry (if shared)

### 6. Dependencies

- [ ] Dependencies follow the downward flow: `products → libs → contracts`
- [ ] No cross-product dependencies without Architecture Board approval
- [ ] No new global shared libraries without Platform Team review

## Approval Process

1. Create a PR with the new module
2. Fill out this checklist in the PR description
3. **Run `scripts/check-new-platform-module.sh <module-path>`** and attach the output
4. Require approval from:
   - Module owner (always)
   - Platform Team (if `platform-lib` or `global-shared`)
   - Architecture Board (if new global shared library or module count would exceed ceiling)

## Module Count Ceiling

> **Current ceiling: 145 modules** (baseline 142 after Phase 1 consolidation, January 2026).
> Adding a module above the ceiling requires Architecture Board approval AND removal of at least one existing module.
> The CI gate (`scripts/architecture-score-gate.sh`) deducts 15 points when the ceiling is exceeded.

## Anti-Patterns to Avoid

- **"Just one more module"** — Check if existing modules cover the need
- **God modules** — Keep modules focused (<200 classes)
- **Orphan modules** — Every module must have at least one consumer
- **Circular dependencies** — Never depend on a peer or parent
