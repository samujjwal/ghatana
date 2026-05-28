# Owner: YAPPC Infrastructure - AEP Integration

**Team:** YAPPC Backend / Infrastructure Team
**Slack:** #yappc-infra
**Parent ownership:** `products/yappc/OWNER.md`
**Last Updated:** 2026-05-28

## Module Purpose

Java adapter layer integrating YAPPC runtime ports with AEP registry/runtime services.

## Ownership Map

| Area | Path | Owned By |
|------|------|---------|
| AEP registry adapter | `src/main/java/com/ghatana/yappc/infrastructure/aep/AepAgentRegistryAdapter.java` | YAPPC Infrastructure |
| AEP runtime adapter | `src/main/java/com/ghatana/yappc/infrastructure/aep/AepAgentRuntimeAdapter.java` | YAPPC Infrastructure |

## Dependency Rules

- May depend on `products:data-cloud:planes:action:*` modules.
- Must expose integration through YAPPC ports (`AgentRegistryPort`, `AgentRuntimePort`).
- Capability/domain modules must not import AEP classes directly.
