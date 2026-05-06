# Product/Kernel Audit Progress

Last updated: 2026-05-05

## Snapshot

- Completed: `42 / 60`
- Partial: `18 / 60`
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
| 13 | Partial | Shared product shell contract is now in use for PHR and FlashIt web, but other audited UI surfaces are not fully migrated to one shell yet. |
| 14 | Completed | PHR navigation is route-manifest driven and protected by role-aware route guards. |
| 15 | Completed | Audited product UIs are aligned on the approved router strategy and checked by CI. |
| 16 | Completed | PHR no longer carries direct MUI ownership outside the approved shell/design-system path. |
| 17 | Completed | Frontend script contract is enforced for audited product UI packages. |
| 18 | Completed | DMOS workflow uses workspace-consistent pnpm installs and filters. |
| 19 | Completed | DMOS test gate now fails explicitly when required jobs fail. |
| 20 | Completed | Finance is explicitly documented and validated as backend-only in workspace governance. |
| 21 | Completed | FlashIt package manager, engines, scripts, and metadata are aligned to pnpm workspace conventions. |
| 22 | Partial | FlashIt observability ownership is partly centralized, but full stack template adoption is not finished across all product overrides. |
| 23 | Completed | Shared Gradle convention plugin exists for product pack validation. |
| 24 | Completed | Finance uses `kernel-testing` as `testImplementation`, not production API surface. |
| 25 | Completed | Product root build files are now checked against manifest-declared platform plugins, and audited mismatches were pruned. |
| 26 | Completed | Kernel capability docs no longer rely on product-specific examples as platform defaults. |
| 27 | Completed | DMOS boundary rules now carry required metadata for auditability. |
| 28 | Completed | DMOS default deny requires audit and is validated consistently. |
| 29 | Completed | Finance default-deny sentinel naming is aligned with the shared validator contract. |
| 30 | Completed | PHR default-deny sentinel naming is aligned with the shared validator contract. |
| 31 | Partial | Shared `PageHeader` and `PageLayout` primitives now exist, and Data Cloud/YAPPC/DCMAAR wrappers compose them, but additional product-local layout primitives still remain. |
| 32 | Partial | Design-system conformance checks exist, but there is still allowlisted UI debt to migrate. |
| 33 | Partial | Shared route entitlement contract exists and audited UIs consume route metadata, but backend entitlement APIs are not universally implemented yet. |
| 34 | Partial | Bridge compliance automation exists and DMOS bridge is compliant, but the broader bridge surface still relies on static/source-level enforcement. |
| 35 | Partial | Shared data-access contract checks exist, but full runtime enforcement across every repository/service method is not complete. |
| 36 | Partial | Observability conformance checks exist, but not every API/bridge flow has end-to-end integration coverage yet. |
| 37 | Partial | PHR consent/boundary guidance is tighter, but full end-to-end consent enforcement scenarios are not all proven by integration tests yet. |
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
| 48 | Partial | A new Kernel-aligned product scaffolder foundation exists, but root workspace/module/CI auto-registration is not implemented yet. |
| 49 | Completed | Finance product shape is explicitly declared as backend-only. |
| 50 | Partial | FlashIt web now composes the shared product shell and route contracts, while additional client surfaces still need full migration depth. |
| 51 | Partial | Shared runtime-template checks prevent test-skipping patterns, but the full packaging pipeline dependency contract still needs broader rollout. |
| 52 | Completed | Shared product launcher Docker template exists and is referenced by audited launcher flows. |
| 53 | Completed | FlashIt local ports are env-backed and validated for cross-product conflicts. |
| 54 | Partial | Visual regression coverage/workflows exist for audited UIs, but not every required state across every product is covered yet. |
| 55 | Partial | Accessibility workflows/specs exist, but critical-route breadth and sensitive-workflow coverage are not complete across all audited products. |
| 56 | Completed | Product release/conformance task naming is standardized through shared conventions. |
| 57 | Partial | Finance root no longer carries the unused compliance domain dependency, but deeper decomposition is still needed so the product root is not a broad aggregator. |
| 58 | Completed | Product manifest schema includes the required kernel/platform/product capability fields. |
| 59 | Completed | Duplicate section numbering in the product guide is fixed. |
| 60 | Completed | FlashIt package metadata is aligned to the monorepo ownership conventions and linted. |

## Remaining Themes

The work still left is concentrated in a few bigger themes rather than scattered one-off fixes:

- Shared runtime/observability rollout: `7`, `22`, `45`, `51`
- Shared shell/layout/design-system migration depth: `13`, `31`, `32`, `50`, `54`, `55`
- Backend entitlement/data/bridge/observability depth: `33`, `34`, `35`, `36`
- Product workflow proof: `37`, `38`, `39`, `57`
- Kernel platform expansion: `48`
