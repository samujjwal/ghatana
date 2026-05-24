# CI Check Tiers

Ghatana uses four check tiers so routine pull requests stay fast while release
promotion still has strict evidence gates.

## Tier 1: `check:staged`

`pnpm check:staged` runs `lint-staged` against files staged for commit. Use it
for pre-commit feedback. It is intentionally file-local and should not be used
as release evidence.

## Tier 2: `check:required`

`pnpm check:required -- --base <ref> --head <ref>` is the default PR and local
confidence gate. It resolves changed files, calls
`scripts/resolve-affected-products.mjs` as the source of truth, and runs only
the checks needed for the affected products and global surfaces.

`check:required` is an orchestrator over product-specific stages. It does not
run every product build/test for every affected-product result. Directly
touched products run the product stage commands, and shared/global/platform
changes run global guards plus changed-file scans unless a product is selected
explicitly.

Stage commands:

- `pnpm check:dev -- --paths file1,file2`: fastest local feedback. Runs
  docs formatting for docs-only changes or affected TypeScript workspace
  typecheck for code changes. It does not start dev servers.
- `pnpm check:validate -- --products phr --paths file1,file2`: changed-only
  production/test-authenticity scans, plus product validation only for
  contract, security, lifecycle, or release-sensitive files.
- `pnpm check:test -- --products data-cloud --paths file1,file2`: product tests
  for touched products and touched surfaces when surface scripts exist.
- `pnpm check:build -- --products digital-marketing --paths file1,file2`:
  product builds for build-impacting changes, scoped to touched surfaces when
  possible.
- `pnpm check:package -- --products phr`: packaging checks for products that
  expose package lifecycle scripts.
- `pnpm check:release:product -- --products phr`: strict product release
  readiness.

Dependency behavior:

- Product dependencies come from product foundation usage profiles when present.
  Required `dataCloud` usage adds Data Cloud provider contract checks.
- Required `agentCore` or `aep` usage adds YAPPC provider contract checks.
- Dependency checks are skipped in `dev`, lightweight in `validate`/`test`/
  `build`, and strict in release/readiness paths.

`check:required` behavior:

- Docs-only changes run formatter checks for changed documentation/config text
  files and then exit.
- Root, config, workflow, script, Gradle, and platform-kernel changes run global
  integrity checks plus stage-scoped workspace typecheck and changed-only
  production scans.
- Product code changes run `dev`, `validate`, and `test` stages for directly
  touched products.
- Product build-impacting changes run the `build` stage for directly touched
  products.
- Contract-like, security-sensitive, and release-risk product changes run
  validation or scoped product release readiness checks.
- Expensive E2E/performance/security gates run only when matching paths changed
  or `--expensive`/`RUN_EXPENSIVE_CHECKS=true` is set.

Inputs:

- `--base <ref>` and `--head <ref>`: preferred in CI.
- `--paths file1,file2`: deterministic local or workflow-driven scopes.
- `--products id,id`: explicit product scope.
- `--stdin`: read changed files from standard input.
- `--release-risk`, `--full`, `--expensive`: opt into stricter profiles.

## Tier 3: `check:full`

`pnpm check:full` runs the platform/product confidence gate for broad changes:
architecture boundaries, TypeScript workspace typecheck, and the root test
surface. Use it for sweeping refactors, shared platform changes, or when an
affected-only result is not enough evidence.

## Tier 4: `check:release`

`pnpm check:release` is the strict release gate and maps to the historical
`check:release-gate` surface. It is intentionally broad and expensive.

For a single product release, use:

```sh
pnpm check:release:product -- --products data-cloud
```

Product release readiness now scopes execution:

- `--products data-cloud` runs global prerequisites, Data Cloud checks, product
  checks, and required platform dependencies.
- `--products digital-marketing` runs global prerequisites, DMOS checks,
  product checks, and required platform dependencies.
- `--full` restores the previous full execution order.
- Missing products in release context fail loudly instead of silently no-oping.

## Workflow Policy

- `Required Checks` is the always-running PR/push workflow and invokes
  `check:required` with an explicit base/head diff.
- Product-specific CI remains advisory or scoped to matching product paths.
- Strict release gates should run only for release branches, tags, manual
  promotion, or explicit product selection.
- Workflows pin the repository runtime policy: Node 22 and pnpm 10.33.0.
