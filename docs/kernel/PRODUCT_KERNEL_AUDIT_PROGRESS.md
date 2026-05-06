# Product/Kernel Audit Progress

Last updated: 2026-05-06

## Snapshot

- Completed: `60 / 60`
- Partial: `0 / 60`
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
| 7 | Completed | All audited product runtime artifacts found in the repo now declare a Kernel-owned template source, with PHR/Finance launcher Dockerfiles, FlashIt compose, FlashIt Java agent, FlashIt gateway, and FlashIt web Dockerfiles covered by runtime conformance checks for base images, healthchecks, secret rules, observability mounts, pnpm version, and no skipped tests. |
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
| 22 | Completed | FlashIt now uses Kernel-owned Prometheus scrape config and Grafana provisioning from `monitoring/`, keeps only product-owned dashboard/alert overlays, and conformance checks fail if product-local Prometheus/Grafana stack config returns. |
| 23 | Completed | Shared Gradle convention plugin exists for product pack validation. |
| 24 | Completed | Finance uses `kernel-testing` as `testImplementation`, not production API surface. |
| 25 | Completed | Product root build files are now checked against manifest-declared platform plugins, and audited mismatches were pruned. |
| 26 | Completed | Kernel capability docs no longer rely on product-specific examples as platform defaults. |
| 27 | Completed | DMOS boundary rules now carry required metadata for auditability. |
| 28 | Completed | DMOS default deny requires audit and is validated consistently. |
| 29 | Completed | Finance default-deny sentinel naming is aligned with the shared validator contract. |
| 30 | Completed | PHR default-deny sentinel naming is aligned with the shared validator contract. |
| 31 | Completed | Shared `PageHeader` and `PageLayout` primitives now back the migrated product wrappers, and the remaining allowlisted layout files are AppShells tracked under the separate shared-shell task. |
| 32 | Completed | Design-system conformance now has zero hardcoded-style exception files: the last DMOS/FlashIt dynamic progress bars were moved to semantic elements or non-inline classes, FlashIt web/mobile color literals now flow through design-token aliases, stale allowlist entries were removed, and the checker fails on raw color literals while allowing non-token animation bindings. |
| 33 | Completed | PHR, DMOS, and FlashIt now expose backend route/content entitlement payloads with the shared `ProductRouteEntitlement` fields (`product`, principal, tenant, role/persona/tier, routes, actions, and cards), and conformance checks enforce both frontend manifest consumption and backend API evidence. |
| 34 | Completed | Bridge compliance automation now enforces audited bridge inventory against the active DMOS bridge-module tests for lifecycle, authorization, audit enrichment, feature-flag fail-closed behavior, notification retry, and redaction rules without relying on quarantined integration buckets. |
| 35 | Completed | Shared data-access contract checks now cover PHR and Finance service-base write metadata, DMOS canonical operation metadata (`tenantId`, `principalId`, `correlationId`, write idempotency, audit classification, and data-owner scope), and FlashIt moment persistence/read paths through a typed data-access context that requires idempotency on writes and carries audit/data-owner metadata into persistence, audit events, and business logs. |
| 36 | Completed | Observability conformance is now manifest-backed through `config/observability/product-observability-flows.json`; every audited product has explicit API/bridge flow evidence for trace propagation, tenant/principal context, metrics, audit emission, safe logging, and redaction, and FlashIt 5xx responses now redact internal exception messages by default. |
| 37 | Completed | PHR now composes boundary resolution and consent enforcement in a tested access gate, with verified allowed, revoked, expired, emergency, missing-feature, and direct-bypass fail-closed scenarios plus audit emission. |
| 38 | Completed | Finance transaction mutation workflow proof now verifies four-eyes review returns `PENDING_REVIEW`, emits audit/idempotency/tenant metadata before side-effecting agent execution, avoids autonomous decision recording while approval is pending, and is guarded by both focused Gradle tests and a workflow-proof conformance check. |
| 39 | Completed | DMOS boundary workflow coverage now exercises every `DM-BP-*` rule through the Kernel resolver with product workflow scenarios for workspace, contact, audience, campaign, budget, content, connector, and default-deny paths, including blocked variants and a conformance guard. |
| 40 | Completed | Secret/default-credential scanning exists across audited product surfaces and CI. |
| 41 | Completed | Root-level Digital Marketing architecture docs were consolidated into canonical product docs. |
| 42 | Completed | Product docs taxonomy is normalized and validated for PHR and Finance. |
| 43 | Completed | FlashIt now has canonical product docs and boundary declarations. |
| 44 | Completed | Kernel now validates product policy actions and resource namespaces against manifest-declared vocabularies. |
| 45 | Completed | Audited launchers fail closed if `InMemoryBoundaryPolicyStore` is wired in production-like environments, and CI checks preserve the guard. |
| 46 | Completed | Product contract tests enforce stable-kernel API usage and forbid implementation-class imports. |
| 47 | Completed | Architecture/dependency direction checks now enforce platform/product dependency boundaries. |
| 48 | Completed | The product scaffolder now generates a Gradle-ready product module, pack/docs/UI/runtime skeletons, a minimal runnable web shell (`vite`/`vitest`/Playwright configs plus route manifest and shared shell), auto-registers product-shape, pnpm workspace, Gradle settings, and the audited coverage/API/a11y/visual/E2E/performance workflow entries with an executable checker, and fresh generated sample products have proven compile, test, build, conformance, API-contract, and local-run behavior in an isolated smoke repo without manual boilerplate edits. |
| 49 | Completed | Finance product shape is explicitly declared as backend-only. |
| 50 | Completed | FlashIt’s declared web and mobile client packages are both registered, validated, and aligned to shared shell/tokens/API-client/route-entitlement conventions. |
| 51 | Completed | Audited launcher Docker builds now invoke product conformance, API-contract checks, and launcher verification explicitly before packaging, and CI enforces the shared-template/no-skip-tests contract. |
| 52 | Completed | Shared product launcher Docker template exists and is referenced by audited launcher flows. |
| 53 | Completed | FlashIt local ports are env-backed and validated for cross-product conflicts. |
| 54 | Completed | Audited UI products now have visual regression coverage for shared shell/dashboard, permission-denied, loading, error, and empty states, and CI checks keep those baselines present. |
| 55 | Completed | PHR, DMOS, and FlashIt a11y suites now exercise keyboard navigation, landmarks, alerts/live regions, contrast-sensitive states, and denied or sensitive workflows through the shared shell/route contracts, with current executions green. |
| 56 | Completed | Product release/conformance task naming is standardized through shared conventions. |
| 57 | Completed | Finance root now composes domain modules through runtime-only `ServiceLoader` discovery instead of compile-linking every finance domain module, and CI checks enforce that dependency scope. |
| 58 | Completed | Product manifest schema includes the required kernel/platform/product capability fields. |
| 59 | Completed | Duplicate section numbering in the product guide is fixed. |
| 60 | Completed | FlashIt package metadata is aligned to the monorepo ownership conventions and linted. |

## Remaining Themes

All original audit tasks are now marked complete in this tracker. Remaining engineering work should be managed as follow-up hardening rather than part of the original 60-task audit backlog.

## Follow-Up Hardening Log

- 2026-05-06: Architecture compliance now supports exact-match temporary exceptions through `config/architecture-compliance-allowlist.json`, requires owner/reason/removal-date metadata, fails on stale allowlist entries, and keeps new package-naming or banned-library violations as hard failures. FlashIt stale `uuid` dependencies were removed from the gateway and mobile client manifests.
