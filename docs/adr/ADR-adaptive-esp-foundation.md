# ADR: Adaptive ESP as AEP Foundation

**Status:** Accepted  
**Date:** 2026-05-23  
**Decision Makers:** AEP maintainers  
**Phase:** Adaptive event intelligence foundation

## Context

AEP needs a coherent foundation for event processing, pattern language, uncertainty, learning, adaptation, expert feedback, and agentic behavior. The dissertation's adaptive event stream processing model provides that foundation.

## Decision

AEP's core design is grounded in the adaptive ESP model:

```text
event model
operator model
time model
uncertainty model
predictive and recommended patterns
pattern exploration
pattern extraction
pattern learning
pattern adaptation
expert feedback
```

Modern AI, ML, and streaming advancements extend this foundation; they do not replace it.

Modern advancements are placed into coherent layers:

```text
stream runtime
probabilistic inference
online learning
neuro-symbolic reasoning
RAG-grounded agents
governed tool use
```

## Rationale

The adaptive ESP model keeps AEP formal and governable while allowing modern agents and learning systems to participate as typed operators.

## Consequences

- AEP architecture must map dissertation concepts to product modules.
- GenAI and agents must participate through typed operators.
- No AEP document should say GenAI replaces PatternSpec, EPL, pattern language, or rules.
- Learning outputs produce recommended or shadow patterns before promotion.

## Alternatives Considered

1. Make GenAI the primary pattern language. Rejected because it weakens validation, replay, and governance.
2. Keep static CEP rules and add learning out of band. Rejected because adaptation and expert feedback are core AEP responsibilities.
3. Treat uncertainty as a model-only concern. Rejected because uncertainty also applies to events, attributes, time, sources, retrieval, and pattern matches.
