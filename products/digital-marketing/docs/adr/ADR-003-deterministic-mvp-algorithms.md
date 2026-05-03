# ADR-003: Deterministic Algorithms for MVP AI Features

**Date**: 2026-05-01  
**Status**: Accepted  
**Deciders**: Platform Engineering, DMOS Product Team  
**Ticket**: DMOS-F1-013 (30-Day Strategy Generator)

---

## Context

DMOS MVP features (strategy generation, budget recommendation, website audit, lead scoring) require AI-powered outputs but must be testable, reproducible, and safe to deploy without external LLM dependencies at MVP scope.

Alternatives considered:
1. **Live LLM API calls** — non-deterministic, expensive, requires API key management at test time, cannot reach 90% branch coverage
2. **LLM with evaluation fixtures** — deterministic for known inputs but adds evaluation infrastructure overhead
3. **Deterministic rule-based algorithms** — fully deterministic, 100% testable, zero external dependencies, upgradeable to probabilistic later

---

## Decision

DMOS MVP services implement **deterministic rule-based algorithms** that can be replaced by probabilistic LLM-backed implementations in a later product phase. Each `*ServiceImpl` class is clearly annotated with `@doc.pattern Service` and the algorithm logic is document in code comments.

Examples:
- `BudgetRecommendationServiceImpl` — 70/20/10 channel split rule
- `StrategyGeneratorServiceImpl` — scoring and threshold-based strategy assembly
- `WebsiteAuditServiceImpl` — rule-based SEO and tracking tag checks

---

## Consequences

**Positive:**
- 100% testable without mocking or stubbing
- Build and CI work with no external dependencies
- Behavior is reviewable, auditable, and explainable to customers
- Future upgrade to LLM is a drop-in replacement (interface is stable)

**Negative / Trade-offs:**
- Rule-based outputs may be less nuanced than LLM-generated outputs
- Business logic is explicit and requires updates as rules evolve

**Mitigations:**
- Algorithm logic is documented clearly in the implementation file
- Interface stability means upgrading to probabilistic agents requires no API changes
- ADR-004 will document the LLM migration path when applicable
