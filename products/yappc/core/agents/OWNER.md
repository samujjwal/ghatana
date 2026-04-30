# Owner: YAPPC Core Agents

**Team:** YAPPC Agent Team
**Slack:** #yappc-agents
**Parent ownership:** `products/yappc/core/OWNER.md`
**Last Updated:** 2026-04-29

## Module Purpose

Domain-specific agent implementations for the YAPPC platform. Contains:

- `runtime/` — agent registry, execution engine, prompt templates, and catalog ownership tests
- `code-specialists/` — code generation, review, and refactoring agents
- `testing-specialists/` — test generation, coverage analysis, and quality gate agents
- `architecture-specialists/` — architectural analysis and ADR generation agents
- `delivery-specialists/` — CI/CD, deployment, and release management agents
- `workflow/` — multi-agent workflow coordination and DAG execution

## Ownership Map

| Area | Path | Owned By |
|------|------|---------|
| Agent runtime & registry | `runtime/` | YAPPC Agent Team |
| Code specialist agents | `code-specialists/` | YAPPC Agent Team |
| Testing specialist agents | `testing-specialists/` | YAPPC Agent Team |
| Architecture agents | `architecture-specialists/` | YAPPC Agent Team |
| Delivery agents | `delivery-specialists/` | YAPPC Agent Team |
| Workflow orchestration | `workflow/` | YAPPC Agent Team |
| Shared agent utilities | `common/` | YAPPC Agent Team |

## Dependency Rules

- Agent modules depend on `yappc-domain` (domain contracts) only — not on each other.
- No agent module may import from `frontend/` or `infrastructure/` directly.
- New agents must be registered in the agent catalog (`platform/agent-catalog/`) before use.
- Agents must not perform blocking I/O on the ActiveJ event loop; use `Promise.ofBlocking()`.

## Production Stability Notes

- `YappcAgentSystemCatalogOwnershipTest` asserts catalog ownership coverage — must remain green.
- Agent prompt templates in `runtime/src/main/.../prompts/` are production-sensitive;
  changes require review from the YAPPC AI Team.
- `delivery-specialists/` CI/CD agents coordinate with `GitHubActionsCiCdAdapter` which is
  currently fail-closed (NOT_READY) until real GitHub Actions integration is complete.
