# Product Lifecycle Contract

This document defines the authoritative lifecycle contract between Kernel and products.

## Scope

- Lifecycle execution is enabled only for products with `lifecycleStatus: enabled` and `lifecycle.enabled: true` in `config/canonical-product-registry.json`.
- Lifecycle execution currently applies to `digital-marketing`.
- `yappc` and `data-cloud` are explicitly excluded by `config/kernel-lifecycle-exclusions.json` and must not declare executable lifecycle configuration.
- Planned products (for example `phr`, `finance`, `flashit`) may keep draft metadata but are not executable.

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
