# Product Lifecycle Contract

This document defines the authoritative lifecycle contract between Kernel and products.

## Scope

- Lifecycle execution is enabled only for products with `lifecycleStatus: enabled` and `lifecycle.enabled: true` in `config/canonical-product-registry.json`.
- Lifecycle execution currently applies to the opening pilots `digital-marketing` and `phr`.
- `phr` is lifecycle-enabled through verify/package/deploy evidence, but rollback remains `target-partial` until stable deployment history, previous-artifact policy, post-rollback healthcare gates, and approval contracts are implemented.
- `yappc` and `data-cloud` are explicitly excluded by `config/kernel-lifecycle-exclusions.json` and must not declare executable lifecycle configuration.
- Planned products (for example `finance`, `flashit`) may keep draft metadata but are not executable.

## Source Of Truth

For enabled products, lifecycle behavior is driven by `products/<product>/kernel-product.yaml`:

- `surfaces`: adapter binding and adapter-required configuration
- `phases`: default surfaces and execution mode (`parallel` or `sequential`)
- `artifacts`: required artifact contracts by phase and surface
- `deployment`: deployment target and health checks
- `gates`: required lifecycle gates

Registry entries must point to this file via `lifecycleConfigPath`.

## Planning And Execution Ownership

- `scripts/kernel-product.mjs` is a thin CLI wrapper only.
- Planning is owned by `@ghatana/kernel-lifecycle` (`ProductLifecyclePlanner`).
- Execution is owned by `@ghatana/kernel-lifecycle` (`ProductLifecycleExecutor`).
- The planner emits explicit executable step metadata per surface/phase.

## Safety Rules

- Enabled products must use lifecycle profiles with `safeForDefault: true`.
- Stable lifecycle profiles must set `safeForDefault: true`.
- Experimental lifecycle profiles must set `safeForDefault: false`.
- Adapters selected by safe default profiles must come from adapter registry entries with `safeForDefault: true`.

## CLI

Supported CLI forms:

```bash
node scripts/kernel-product.mjs product plan <productId> <phase> --json
node scripts/kernel-product.mjs product <phase> <productId> --dry-run --json
node scripts/kernel-product.mjs plan <productId> <phase> --json
node scripts/kernel-product.mjs <phase> <productId> --json
```

Supported options include `--surface`, `--surfaces`, `--env`, `--output-dir`, `--source-ref`, `--dry-run`, `--json`, `--from`, `--to`, `--approval-id`, `--release-id`.

## Root Command Semantics

Root commands are intentionally tiered:

- `pnpm test` is the fast TypeScript/platform and product-UI test sweep. It uses workspace filters and `--if-present`; it must not be treated as complete Java/backend/product integration coverage.
- `pnpm lint` runs `pnpm typecheck` plus architecture boundary checks. Package-local ESLint/static checks remain enforced by the relevant product/platform gates.
- `pnpm typecheck` builds the platform TypeScript and product UI workspace slice, so it is a build-backed type gate rather than a pure `tsc --noEmit` sweep.
- `pnpm check:full-repo-test` is the explicit broader local regression command: it runs root test coverage, Gradle `check`, and the Digital Marketing/PHR lifecycle pilot smoke gates.
- `pnpm check:phase8` and `pnpm check:world-class-platform-readiness` are no-regression readiness gates and include Studio artifact workflow checks, artifact round-trip checks, Kernel lifecycle/provider checks, and product readiness checks.
