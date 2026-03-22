# Owner: YAPPC (Yet Another Platform Product Composer)

**Team:** YAPPC Team  
**Slack:** #platform-yappc  
**On-call:** YAPPC on-call rotation  
**Architecture lead:** YAPPC Tech Lead  
**Boundary audit score:** Previously 4/10; now 6/10 after BDY-5 documentation (2026-03-21)  

## Responsibility

YAPPC is the **AI-powered software composition platform** that enables developers to build, scaffold, refactor, and operate software products using agents. It:

- Provides an agent-based coding assistant (LLM-powered)
- Scaffolds new projects and features via declarative packs
- Orchestrates multi-agent refactoring workflows
- Hosts a knowledge graph of codebases

**Domain boundary:** YAPPC owns the developer tooling domain. It consumes AEP for pipeline orchestration and Data-Cloud for event streaming. See `products/yappc/docs/CORE_ARCHITECTURE.md` for module map.

## Architecture

See [docs/CORE_ARCHITECTURE.md](docs/CORE_ARCHITECTURE.md) for the full 18-module breakdown.

**Key principle:** The `core/` tree is well-decomposed into 5 domain clusters (foundation, ai/knowledge, agent execution, scaffolding, refactoring). Do not regress this by adding cross-cluster imports.

## Consumers

YAPPC is primarily end-user-facing. No other products should depend on YAPPC's internal modules.
