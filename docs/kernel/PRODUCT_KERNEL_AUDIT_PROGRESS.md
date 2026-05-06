# Product/Kernel Audit Progress

Last updated: 2026-05-06

## Snapshot

- Completed: `50 / 60`
- Partial: `10 / 60`
- Open: `0 / 60`

This tracker reflects the original cross-product Kernel/Product audit backlog.
`Completed` means the repo now has both implementation and an enforceable check
or test for the requirement. `Partial` means a meaningful slice is in place, but
the original task is broader than the current enforcement. `Open` means the task
still needs first-class implementation.

## Status By Task

| ID | Status | Summary |
|---|---|---|
| 1 | Completed | Canonical Kernel/Product responsibility matrix exists and is wired into docs/contracts. |
| 2 | Completed | `checkKernelProductBoundary` exists and fails on product-domain leakage into kernel/plugins. |
| 3 | Completed | Coverage-gate workflow includes Digital Marketing and FlashIt. |
| 4 | Completed | API contract conformance is product-driven across audited products. |
| 5 | Completed | FlashIt has domain-pack manifest, policy store, compliance pack, bindings, and validation tasks. |
| 6 | Completed | FlashIt local compose requires real secrets unless AI is explicitly disabled. |
| 7 | Partial | Shared runtime templates exist, but not every audited product runtime is fully generated from them yet. |
| 8 | Completed | Kernel policy-pack validator API is shared across products. |
| 9 | Completed | PHR, Finance, and DMOS fail closed on unsupported tenant/region overrides. |
| 10 | Completed | Finance policy validation executes real rules and semantics, not class-existence only. |
| 11 | Completed | PHR policy validation executes real rules and semantics, not class-existence only. |
| 12 | Completed | DMOS policy validation uses object-level rule inspection with the shared validator. |
| 13 | Completed | Audited product web shells now compose `@ghatana/product-shell`, and CI checks enforce the PHR/DMOS/FlashIt shared-shell contract. |
| 14 | Completed | PHR navigation is route-manifest driven and protected by role-aware route guards. |
| 15 | Completed | Audited product UIs are aligned on the approved router strategy and checked by CI. |
| 16 | Completed | PHR no longer carries direct MUI ownership outside the approved shell/design-system path. |
| 17 | Completed | Frontend script contract is enforced for audited product UI packages. |
| 18 | Completed | DMOS workflow uses workspace-consistent pnpm installs and filters. |
| 19 | Completed | DMOS test gate now fails explicitly when required jobs fail. |
| 20 | Completed | Finance is explicitly documented and validated as backend-only in workspace governance. |
| 21 | Completed | FlashIt package manager, engines, scripts, and metadata are aligned to pnpm workspace conventions. |
| 22 | Partial | FlashIt observability mounts now flow through the shared `PRODUCT_OBSERVABILITY_ROOT` contract and product dashboards/alerts remain overlays, but full stack template adoption is not finished across all product overrides. |
| 23 | Completed | Shared Gradle convention plugin exists for product pack validation. |
| 24 | Completed | Finance uses `kernel-testing` as `testImplementation`, not production API surface. |
| 25 | Completed | Product root build files are now checked against manifest-declared platform plugins, and audited mismatches were pruned. |
| 26 | Completed | Kernel capability docs no longer rely on product-specific examples as platform defaults. |
| 27 | Completed | DMOS boundary rules now carry required metadata for auditability. |
| 28 | Completed | DMOS default deny requires audit and is validated consistently. |
| 29 | Completed | Finance default-deny sentinel naming is aligned with the shared validator contract. |
| 30 | Completed | PHR default-deny sentinel naming is aligned with the shared validator contract. |
| 31 | Completed | Shared `PageHeader` and `PageLayout` primitives now back the migrated product wrappers, and the remaining allowlisted layout files are AppShells tracked under the separate shared-shell task. |
| 32 | Partial | Design-system conformance checks now retire additional shared FlashIt mobile primitive debt (`Skeleton`, `Toast`, `AudioPlaybackControls`, `QuickCaptureButton`, `UploadProgressIndicator`, and `UploadItemCard`) and fail on stale allowlist entries, but there is still concentrated allowlisted UI debt to migrate. |
| 33 | Partial | Shared route entitlement contract exists, and PHR, DMOS, and FlashIt now block direct URL access from the same route metadata, but backend entitlement APIs are not universally implemented yet. |
| 34 | Completed | Bridge compliance automation now enforces audited bridge inventory against the active DMOS bridge-module tests for lifecycle, authorization, audit enrichment, feature-flag fail-closed behavior, notification retry, and redaction rules without relying on quarantined integration buckets. |
| 35 | Partial | Shared data-access contract checks now pair with runtime mutation-metadata enrichment in the PHR and Finance service-base write paths (tenant/principal/correlation/idempotency/audit/data-owner fields), but full enforcement across every repository/service method and DMOS/FlashIt persistence path is not complete yet. |
| 36 | Partial | Observability conformance checks now also enforce Finance transaction outcome metadata, Finance transaction test evidence, the PHR consent-boundary audit contract, and DMOS bridge audit/trace evidence, but not every API/bridge flow has end-to-end integration coverage yet. |
| 37 | Completed | PHR now composes boundary resolution and consent enforcement in a tested access gate, with verified allowed, revoked, expired, emergency, missing-feature, and direct-bypass fail-closed scenarios plus audit emission. |
| 38 | Partial | Finance approval/audit policy semantics are validated, but domain workflow proof for every transaction mutation path is still incomplete. |
| 39 | Partial | DMOS rule-pack validation is strong, but full workflow-by-workflow integration coverage for every boundary rule is not finished. |
| 40 | Completed | Secret/default-credential scanning exists across audited product surfaces and CI. |
| 41 | Completed | Root-level Digital Marketing architecture docs were consolidated into canonical product docs. |
| 42 | Completed | Product docs taxonomy is normalized and validated for PHR and Finance. |
| 43 | Completed | FlashIt now has canonical product docs and boundary declarations. |
| 44 | Completed | Kernel now validates product policy actions and resource namespaces against manifest-declared vocabularies. |
| 45 | Completed | Audited launchers fail closed if `InMemoryBoundaryPolicyStore` is wired in production-like environments, and CI checks preserve the guard. |
| 46 | Completed | Product contract tests enforce stable-kernel API usage and forbid implementation-class imports. |
| 47 | Completed | Architecture/dependency direction checks now enforce platform/product dependency boundaries. |
| 48 | Partial | The product scaffolder now generates a Gradle-ready product module, pack/docs/UI/runtime skeletons, a minimal runnable web shell (`vite`/`vitest`/Playwright configs plus route manifest and shared shell), auto-registers product-shape, pnpm workspace, Gradle settings, and the audited coverage/API/a11y/visual/E2E/performance workflow entries with an executable checker, and fresh generated sample products have now proven the Gradle compile/test/conformance/API-contract path in an isolated smoke repo; the remaining gap is finishing the last clean web-package smoke proof without hand edits. |
| 49 | Completed | Finance product shape is explicitly declared as backend-only. |
| 50 | Completed | FlashIt’s declared web and mobile client packages are both registered, validated, and aligned to shared shell/tokens/API-client/route-entitlement conventions. |
| 51 | Completed | Audited launcher Docker builds now invoke product conformance, API-contract checks, and launcher verification explicitly before packaging, and CI enforces the shared-template/no-skip-tests contract. |
| 52 | Completed | Shared product launcher Docker template exists and is referenced by audited launcher flows. |
| 53 | Completed | FlashIt local ports are env-backed and validated for cross-product conflicts. |
| 54 | Completed | Audited UI products now have visual regression coverage for shared shell/dashboard, permission-denied, loading, error, and empty states, and CI checks keep those baselines present. |
| 55 | Partial | Audited UI specs now cover keyboard navigation, landmarks, alerts/live regions, and denied or sensitive workflows for PHR, DMOS, and FlashIt, but full end-to-end execution breadth is not yet proven across every critical route. |
| 56 | Completed | Product release/conformance task naming is standardized through shared conventions. |
| 57 | Completed | Finance root now composes domain modules through runtime-only `ServiceLoader` discovery instead of compile-linking every finance domain module, and CI checks enforce that dependency scope. |
| 58 | Completed | Product manifest schema includes the required kernel/platform/product capability fields. |
| 59 | Completed | Duplicate section numbering in the product guide is fixed. |
| 60 | Completed | FlashIt package metadata is aligned to the monorepo ownership conventions and linted. |

## Remaining Themes

The work still left is concentrated in a few bigger themes rather than scattered one-off fixes:

- Shared runtime/observability rollout: `7`, `22`
- Shared shell/layout/design-system migration depth: `32`, `55`
- Backend entitlement/data/bridge/observability depth: `33`, `35`, `36`
- Product workflow proof: `38`, `39`
- Kernel platform expansion: `48`
