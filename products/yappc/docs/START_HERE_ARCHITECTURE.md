# YAPPC Start Here Architecture

This is the contributor entrypoint for the current YAPPC topology. Treat this document, `MODULE_CATALOG.md`, and `products/yappc/settings.gradle.kts` as the shared source of truth for active module names.

## What Is Current

- `services/` is the deployable YAPPC application entrypoint.
- `core/services-platform` and `core/services-lifecycle` are the canonical reusable service libraries behind the deployable app.
- `core/yappc-domain-impl`, `core/yappc-services`, `core/yappc-infrastructure`, `core/yappc-api`, `core/yappc-shared`, and `core/yappc-agents` are the primary current product modules.
- Capability families such as `core/agents/*`, `core/scaffold/*`, `core/refactorer/*`, `core/ai`, and `core/knowledge-graph` remain active where they still own real code.
- `libs/java/yappc-domain` is the public product domain contract surface.

## What Is Historical

Do not describe these as primary architecture in docs, reviews, or CI:

- `backend:api`
- `core:domain`
- `core:framework`
- `core:lifecycle`
- old `services:lifecycle` naming where the canonical module is now `core/services-lifecycle`

Historical migration narratives belong in dated audit history and ADR records outside the canonical active-doc surface.

## Contributor Routing

- New HTTP/runtime work: start in `core/services-lifecycle`, then confirm how `services/` composes it.
- Business logic changes: start in `core/yappc-services`.
- Adapter/persistence/integration work: start in `core/yappc-infrastructure` or `infrastructure/datacloud`.
- Public product contract changes: start in `libs/java/yappc-domain`.
- Agent behavior changes: confirm whether the caller is using `core/yappc-agents` or a remaining `core/agents/*` capability module.

## CI Vocabulary

The active YAPPC workflows should describe the product with the same module names above.

- Main product CI: `.github/workflows/yappc-ci.yml`
- Product-isolated CI: `.github/workflows/product-isolated-ci.yml`
- Contract tests: `.github/workflows/yappc-contract-tests.yml`

Coverage and quality checks should run on the canonical modules that own the code, not on retired compatibility names.

## Recommended Reading Order

1. `MODULE_CATALOG.md`
2. `onboarding/developer.md` or `onboarding/architect.md`
3. `CORE_ARCHITECTURE.md` for deeper rationale
4. `RELEASE_READINESS_CHECKLIST.md` before release-candidate sign-off
