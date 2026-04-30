# Owner: YAPPC Core Services

**Team:** YAPPC Backend Team
**Slack:** #yappc-core
**Parent ownership:** `products/yappc/core/OWNER.md`
**Last Updated:** 2026-04-29

## Module Purpose

Core Java service implementations for the YAPPC 8-phase lifecycle. Contains:

- `GenerationServiceImpl` — AI-assisted artifact generation (code, config, docs, CI/CD pipeline)
- `RunServiceImpl` — execution orchestration with pluggable `CiCdPort` adapters
- `GitHubActionsCiCdAdapter` — fail-closed GitHub Actions CI/CD adapter (NOT_READY until wired)
- `NoOpCiCdAdapter` — safe default adapter for tests and unconfigured environments
- Lifecycle, scaffold, validation, observe, and evolution service implementations
- HTTP server wiring (`YappcHttpServer`) and module binding (`YappcApiModule`)

## Ownership Map

| Area | Path | Owned By |
|------|------|---------|
| Generation service | `src/main/.../services/generate/` | YAPPC AI Team |
| Run / CI/CD service | `src/main/.../services/run/` | YAPPC Backend |
| Lifecycle orchestration | `src/main/.../services/lifecycle/` | YAPPC Backend |
| Scaffold advisor | `src/main/.../services/scaffold/` | YAPPC Scaffold Team |
| HTTP module wiring | `src/main/.../api/` | YAPPC Backend |

## Dependency Rules

- Service impls depend on domain contracts from `yappc-domain` and platform modules only.
- Must not import from `frontend/` or `infrastructure/datacloud/` directly.
- `CiCdPort` is the sole abstraction point for CI/CD — never call GitHub API directly from service logic.

## Production Stability Notes

- `GitHubActionsCiCdAdapter`: All operations return `NOT_READY` status until real GitHub Actions
  workflow dispatch is implemented. This is intentional fail-closed behavior — no false success.
  Configured via `GITHUB_TOKEN` + `GITHUB_REPO` env vars; falls back to `NoOpCiCdAdapter` if missing.
- `RunServiceImpl`: The `shouldFail` config key in task config injects deterministic test failures.
- `GenerationServiceImpl`: Uses `CompletionService` (LLM abstraction); swap implementation for testing.
- `ObserveServiceImpl`, `EvolutionServiceImpl`: Some logic is heuristic — not backed by real telemetry pipelines.
