# ADR: AEP, Data-Cloud, and EventCloud Product Boundaries

**Status:** Accepted  
**Date:** 2026-05-23  
**Decision Makers:** AEP and Data-Cloud maintainers  
**Phase:** Documentation coherence

## Context

Data-Cloud and AEP documentation has used conflicting product boundaries. Some documents describe AEP as an implementation detail of Data-Cloud, while AEP runtime and planning material describe EventCloud, pattern processing, learning, and agentic processing as AEP semantics.

The canonical boundary must keep storage concerns separate from adaptive event intelligence so dependency direction, runtime ownership, and governance stay clear.

## Decision

Data-Cloud owns governed entity storage, metadata, schemas, audit, retention, and pluggable persistence.

AEP owns adaptive event processing semantics: EventCloud, PatternSpec/EPL, operator catalog, pattern detection, uncertainty, learning/adaptation, agent-as-operator runtime, and pattern governance.

EventCloud is AEP-owned. Data-Cloud may implement EventCloud persistence plugins, but Data-Cloud must not expose EventCloud, CEP, PatternSpec, operator-runtime, or learning semantics.

Dependency rule:

```text
Data-Cloud must not depend on AEP.
AEP may use Data-Cloud storage plugins through stable SPI.
```

## Rationale

Data-Cloud is the governed data/storage substrate. AEP is the adaptive event intelligence platform. Keeping EventCloud in AEP avoids leaking CEP semantics into Data-Cloud and keeps Data-Cloud usable by products that need storage without adaptive event processing.

## Consequences

- Current AEP implementation modules may remain under `products/data-cloud/planes/action/*` during migration.
- The temporary code location does not change semantic ownership: AEP owns EventCloud, PatternSpec/EPL, operator runtime, adaptive learning, and agent-as-operator behavior.
- Data-Cloud docs must not describe Data-Cloud as CEP or EventCloud.
- AEP docs must not describe EventCloud as a generic standalone product.
- Data-Cloud EventLog language remains valid for simple append/replay and storage-plane event records.
- Data-Cloud EventCloud integration is limited to plugin-backed persistence behind AEP SPI.
- Cross-product architecture tests should enforce the forbidden dependency direction.

## Alternatives Considered

1. Treat AEP as an internal Data-Cloud Action Plane implementation. Rejected because it mixes storage substrate and adaptive event intelligence semantics.
2. Treat EventCloud as a standalone product. Rejected because EventCloud semantics belong to AEP and need PatternSpec, operator, replay, and lifecycle governance.
3. Let Data-Cloud expose pattern APIs directly. Rejected because it would make Data-Cloud a CEP platform and create circular ownership.
