# Owner: Aura — Personal AI Intelligence Platform

**Team:** Aura Team  
**Slack:** #product-aura  
**On-call:** Aura on-call rotation  
**Architecture lead:** Aura Tech Lead  
**Boundary audit score:** 3/10 (2026-03-22) — pre-production, minimal implementation

## Responsibility

Aura is a **consumer AI product** providing a personal intelligence engine. It delivers:
- Personalised recommendations and style intelligence
- Knowledge graph management for personal context
- Long-horizon task execution
- Adaptive consumer experience across devices

**Domain boundary:** Aura owns the personal consumer AI domain. It consumes `platform:java:agent-runtime` for agent execution and `platform:java:ai-integration` for model inference. No other products should depend on Aura's internal modules.

## Architecture

See [docs/](docs/) for full architecture and sprint planning documentation.

**Current status:** Design & Architecture phase. Engineering implementation not yet begun. Reference [`docs/Aura_Engineering_Sprint_Plan_6_Months.md`](docs/Aura_Engineering_Sprint_Plan_6_Months.md) for the roadmap.

## Known Issues

- `OWNER.md` was missing as of the 2026-03-22 boundary audit (score 3/10, accountability gap)
- Product needs to reach implementation phase for a meaningful boundary score
- When implementation begins, verify no duplication with `products/yappc` consumer AI features
