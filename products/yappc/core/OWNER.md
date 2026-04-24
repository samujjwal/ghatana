# Owner: YAPPC Core — Java Backend

**Team:** YAPPC Backend Team  
**Slack:** #yappc-core  
**Parent ownership:** `products/yappc/OWNER.md`  
**Last Updated:** 2026-04-23  

## Module Map

| Module | Purpose | Owned By |
|--------|---------|---------|
| `yappc-services` | 8-phase lifecycle HTTP API (LifecycleApiController), per-phase service implementations | YAPPC Backend |
| `yappc-domain` | Pure domain types: IntentSpec, ShapeSpec, GeneratedArtifacts, RunResult, etc. | YAPPC Backend |
| `yappc-common` | JsonMapper, shared utilities | YAPPC Backend |
| `services-lifecycle` | Lifecycle orchestration pipeline, event processing, auth filter chain, DLQ retry | YAPPC Backend |
| `agents/*` | Domain-specific agent implementations (code-gen, testing, security, refactor, scaffold) | YAPPC Agent Team |
| `scaffold/*` | Scaffold CLI, monorepo tooling | YAPPC Scaffold Team |
| `refactorer/*` | Refactoring API, import analysis, CLI | YAPPC Refactor Team |
| `knowledge/*` | Knowledge graph, vector retrieval, semantic search | YAPPC AI Team |

## Dependency Rules

- `yappc-domain` must have no dependencies on other YAPPC modules.
- `yappc-services` may depend on `yappc-domain`, `yappc-common`, and `platform:java:*`.
- Agent modules may depend on `yappc-domain` but must not depend on each other.
- `core/` sub-modules must not import from `products/yappc/frontend/`.

## Production Stability Notes

- `RunServiceImpl`: Returns synthetic CI/CD outputs. No real CI/CD integration exists yet.
- `ObserveServiceImpl`: Generates metrics from `RunResult` heuristics, not real systems.
- `EvolutionServiceImpl`: Evolution plans are LLM-derived; gate enforcement is not wired.
- `ValidationServiceImpl`: Policy gate is wired but stage-transition enforcement is
  audit-log-only — no hard blocking on gate failure except within the lifecycle pipeline.
