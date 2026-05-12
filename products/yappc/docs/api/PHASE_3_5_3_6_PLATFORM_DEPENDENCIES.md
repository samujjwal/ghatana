# Phase 3.5 and 3.6 Platform Dependencies

**Status:** Pending Platform-Level Changes  
**Last Updated:** 2026-05-11

---

## Phase 3.5: Replace sample activity - add audit query API

### Current State
The `queryActivityFeed` method in `PhasePacketServiceImpl` returns a sample activity entry because the platform's `AuditService` interface does not provide a query method.

### Required Platform Changes
The platform's `AuditService` interface needs to be extended with query capabilities:

```java
// platform/java/audit/src/main/java/com/ghatana/platform/audit/AuditService.java
public interface AuditService {
    Promise<Void> record(AuditEvent event);
    
    // NEW: Query methods needed for activity feed
    Promise<List<AuditEvent>> query(AuditQuery query);
    Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate);
    Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate);
}
```

### Acceptance Criteria
- [ ] AuditService interface extended with query methods
- [ ] AuditService implementations (production, test) updated with query logic
- [ ] PhasePacketServiceImpl.queryActivityFeed uses AuditService.queryByPhase
- [ ] Activity feed returns real audit events for the phase and project

---

## Phase 3.6: Replace default healthy signals - preview health from preview runtime

### Current State
The `buildHealthSignals` method returns default healthy signals. Preview health should come from the preview runtime service.

### Required Platform Changes
The preview runtime needs to expose health status:

```java
// platform/java/runtime/src/main/java/com/ghatana/platform/runtime/PreviewRuntimeService.java
public interface PreviewRuntimeService {
    PreviewHealthStatus getHealth(String previewId);
    GenerationHealthStatus getGenerationHealth(String generationId);
    RuntimeHealthStatus getRuntimeHealth(String runtimeId);
}
```

### Acceptance Criteria
- [ ] PreviewRuntimeService interface defined with health query methods
- [ ] PreviewRuntimeService implementation queries actual preview runtime health
- [ ] PhasePacketServiceImpl.buildHealthSignals uses PreviewRuntimeService
- [ ] Health signals reflect actual preview/generation/runtime status

---

## Migration Notes

Both Phase 3.5 and 3.6 require platform-level changes before YAPPC can fully implement them. These are cross-cutting concerns that affect multiple products.

**Recommendation:** Coordinate with platform team to prioritize these platform extensions.
