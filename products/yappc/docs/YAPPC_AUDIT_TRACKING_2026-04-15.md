# YAPPC Audit Implementation Tracking

**Report Date:** 2026-04-15  
**Target Completion:** 10-12 weeks  
**Status:** Ready for execution

---

## Quick Reference: Critical Files Requiring Changes

| Issue | Primary File | Line(s) | Severity |
|-------|-------------|---------|----------|
| dev-key default | `core/services-lifecycle/.../YappcApiSecurity.java` | 83 | P0 |
| dev password bootstrap | `core/services-lifecycle/.../LifecycleLoginController.java` | 253-265 | P0 |
| default-tenant fallback | `core/services-lifecycle/.../YappcApiSecurity.java` | 93 | P0 |
| default-tenant in users | `core/services-lifecycle/.../LifecycleLoginController.java` | 326 | P0 |
| runtime port mismatch | `YappcLifecycleService.java` | 70 (8082) | P0 |
| OpenAPI wrong port | `docs/api/openapi.yaml` | - (8080) | P0 |
| CI wrong port | `.github/workflows/yappc-*.yml` | - | P0 |
| sort ignored | `infrastructure/datacloud/.../YappcDataCloudRepository.java` | 165 | P0 |
| voice fake backend | `frontend/libs/yappc-ui/.../useVoiceCommands.ts` | - | P1 |
| in-memory collaboration | `frontend/apps/api/.../RealTimeService.ts` | - | P1 |
| duplicate web app | `frontend/apps/web/` | entire dir | P1 |
| 833-line canvas | `frontend/web/.../canvas.tsx` | 1-828 | P2 |
| ts-nocheck | `frontend/web/.../app-theme.tsx` | - | P2 |

---

## Phase 0: Critical Blockers (Weeks 1-2)

### Security: Remove Insecure Defaults

#### Item 0.1: Remove dev-key Default
```java
// File: core/services-lifecycle/src/main/java/.../YappcApiSecurity.java
// Line: 83

// CURRENT (INSECURE):
Set<String> allowedKeys = parseCsvSet(System.getenv().getOrDefault(API_KEYS_ENV, "dev-key"));

// REQUIRED:
String keysEnv = System.getenv(API_KEYS_ENV);
if (keysEnv == null || keysEnv.isBlank()) {
    if (isProduction()) {
        throw new IllegalStateException("YAPPC_API_KEYS required in production");
    }
    // Only in dev, with warning
    logger.warn("Using development-only API key");
    keysEnv = "dev-key";
}
Set<String> allowedKeys = parseCsvSet(keysEnv);
```

**Acceptance Criteria:**
- [ ] Service fails to start in production mode without YAPPC_API_KEYS
- [ ] Dev mode logs warning but starts
- [ ] Test: `YAPPC_ENV=production` without env var → startup failure

---

#### Item 0.2: Remove change-me-in-production Bootstrap
```java
// File: core/services-lifecycle/.../LifecycleLoginController.java
// Lines: 253-265 (bootstrapDevUser method)

// CURRENT (INSECURE):
private static List<UserRecord> bootstrapDevUser() {
    byte[] salt = generateSalt();
    String hash = hashPassword("change-me-in-production", salt);
    UserRecord dev = new UserRecord(
        "dev-user-1",
        "dev@yappc.io",
        hash,
        Base64.getEncoder().encodeToString(salt),
        "YAPPC Dev",
        List.of("admin", "user"),
        "default-tenant");
    return List.of(dev);
}

// REQUIRED:
// Remove method entirely. In fromEnvironment():
if (raw == null || raw.isBlank()) {
    if (isProduction()) {
        throw new IllegalStateException("YAPPC_AUTH_USERS required in production");
    }
    throw new IllegalStateException("YAPPC_AUTH_USERS not set");
}
```

**Acceptance Criteria:**
- [ ] Remove `bootstrapDevUser()` method
- [ ] Service fails to start without YAPPC_AUTH_USERS in any mode
- [ ] Update tests to set env var

---

#### Item 0.3: Remove default-tenant Fallbacks
```java
// File: core/services-lifecycle/.../YappcApiSecurity.java
// Line: 93

// CURRENT:
String tenantId = tenantMap.getOrDefault(apiKey, "default-tenant");

// REQUIRED:
String tenantId = tenantMap.get(apiKey);
if (tenantId == null) {
    throw new IllegalStateException("No tenant mapping for API key");
}
```

```java
// File: core/services-lifecycle/.../LifecycleLoginController.java
// Line: 326

// CURRENT:
(String) m.getOrDefault("tenantId", "default-tenant"));

// REQUIRED:
String tenantId = (String) m.get("tenantId");
if (tenantId == null || tenantId.isBlank()) {
    throw new IllegalArgumentException("User record missing required tenantId");
}
```

**Acceptance Criteria:**
- [ ] No "default-tenant" string in production code
- [ ] Missing tenant causes error, not fallback
- [ ] All test data includes explicit tenantId

---

### Runtime: Fix Contract Drift

#### Item 0.4: Align All References to Port 8082

| File | Current | Required |
|------|---------|----------|
| `YappcLifecycleService.java:70` | DEFAULT_PORT = 8082 | ✓ Correct |
| `docs/api/openapi.yaml` | servers: localhost:8080 | Change to 8082 |
| `deployment/helm/values.yaml` | service.port | Verify 8082 |
| CI workflows | curl localhost:8080 | Change to 8082 |
| README.md | Backend API: localhost:8080 | Change to 8082 |

**Acceptance Criteria:**
- [ ] All documentation references port 8082
- [ ] CI tests against port 8082
- [ ] Helm charts use port 8082
- [ ] OpenAPI spec uses port 8082

---

#### Item 0.5: Fix Health Path Mismatches

Service implements:
- GET `/health` → "OK"
- GET `/ready` → "READY"

Helm expects (wrong):
- `/health/live`
- `/health/ready`
- `/health/startup`

**Acceptance Criteria:**
- [ ] Update Helm values to use `/health` and `/ready`
- [ ] Update Kubernetes manifests
- [ ] Update CI health checks
- [ ] Document canonical paths

---

## Phase 1: Hardening (Weeks 3-6)

### Auth Consolidation

#### Item 1.1: Audit Node Auth Endpoints

File: `frontend/apps/api/src/routes/auth.ts`

Check for:
- [ ] `/api/auth/login` - should proxy to Java
- [ ] `/api/auth/logout` - should proxy to Java
- [ ] `/api/auth/me` - should proxy to Java
- [ ] `/api/auth/refresh` - should proxy to Java
- [ ] `/api/auth/validate` - should proxy to Java

**Acceptance Criteria:**
- [ ] List all Node auth endpoints
- [ ] Determine which can be removed vs proxied
- [ ] Create proxy middleware for Java backend

---

#### Item 1.2: Implement Auth Proxy

```typescript
// New file: frontend/apps/api/src/middleware/AuthProxy.ts

// All auth routes should proxy to Java lifecycle service
// Port 8082, paths match Java routes

export const authProxy = {
  login: proxyToJava('/api/auth/login'),
  logout: proxyToJava('/api/auth/logout'),
  me: proxyToJava('/api/auth/me'),
  validate: proxyToJava('/api/auth/validate'),
};
```

**Acceptance Criteria:**
- [ ] Node auth endpoints return 404 or proxy
- [ ] Java auth handles all auth logic
- [ ] End-to-end auth tests pass

---

### Collaboration Scalability

#### Item 1.3: Redis Room Storage

File: `frontend/apps/api/src/services/RealTimeService.ts`

Current: `const rooms = new Map();` (in-memory)

Required:
```typescript
// Use Redis for room state
import { Redis } from 'ioredis';

const redis = new Redis({
  host: process.env.REDIS_HOST,
  port: parseInt(process.env.REDIS_PORT || '6379'),
});

// Room state in Redis, not memory
// Presence in Redis pub/sub
```

**Acceptance Criteria:**
- [ ] Room state persists across restarts
- [ ] Multiple API instances share state
- [ ] Reconnect works after instance failure
- [ ] Load test with 2+ API instances

---

### Query Correctness

#### Item 1.4: Fix Data Cloud Queries

File: `infrastructure/datacloud/.../YappcDataCloudRepository.java`

Current issues:
1. `sort` parameter ignored (line 167)
2. `$gte` operators not supported
3. Only equality matching works

Options:
- **A:** Implement proper operator support
- **B:** Remove rich query API, expose only equality

**Recommended:** Option B until Data Cloud supports operators.

```java
// Remove findByFilter with Map<String, Object>
// Replace with explicit equality methods:

public Promise<List<T>> findByFieldEquals(String field, Object value);
public Promise<List<T>> findByIdIn(List<UUID> ids);
// No generic filter map
```

**Acceptance Criteria:**
- [ ] Remove misleading `findByFilter` method
- [ ] Add explicit equality-only methods
- [ ] Update all callers
- [ ] Document query limitations

---

### Fake Feature Removal

#### Item 1.5: Remove or Implement Voice

File: `frontend/libs/yappc-ui/src/components/voice/useVoiceCommands.ts`

Options:
- **A:** Remove voice UI entirely
- **B:** Integrate with `products/audio-video`
- **C:** Feature-flag with clear "beta" label

**Recommended:** Option A until audio-video integration is complete.

**Acceptance Criteria:**
- [ ] Remove `useVoiceCommands.ts` and references
- [ ] Remove VoiceInputService if not used
- [ ] Update documentation (remove voice claims)

---

## Phase 2: Cleanup (Weeks 7-10)

### Duplicate Consolidation

#### Item 2.1: Consolidate Web Apps

| App | Files | Action |
|-----|-------|--------|
| `frontend/web` | 1284 items | **Keep as canonical** |
| `frontend/apps/web` | ~10 items | **Delete or archive** |

**Acceptance Criteria:**
- [ ] Verify `frontend/apps/web` not deployed
- [ ] Move any unique features to `frontend/web`
- [ ] Delete `frontend/apps/web` directory
- [ ] Update CI to only build `frontend/web`

---

#### Item 2.2: Consolidate Agent Trees

| Tree | Modules | Action |
|------|---------|--------|
| `core/agents` | 602 items | Inventory, merge to yappc-agents |
| `core/yappc-agents` | 42 items | **Keep as canonical** |

**Acceptance Criteria:**
- [ ] List modules in both trees
- [ ] Identify overlapping functionality
- [ ] Migrate unique features from agents to yappc-agents
- [ ] Delete `core/agents` tree

---

#### Item 2.3: Decompose Canvas Route

File: `frontend/web/src/routes/app/project/canvas.tsx` (833 lines)

Target modules:
```
routes/app/project/_canvas/
  ├── CanvasOrchestrator.tsx     # Route-level only
  ├── CanvasWorkspace.tsx          # Workspace composition
  ├── hooks/
  │   ├── useCanvasState.ts        # State management
  │   ├── useCanvasDrawing.ts      # Drawing operations
  │   ├── useCanvasKeyboard.ts     # Keyboard shortcuts
  │   └── useCanvasExport.ts       # Export/import
  └── components/
      ├── CanvasToolbar.tsx
      ├── CanvasLeftPanel.tsx
      ├── CanvasRightPanel.tsx
      └── CanvasStatusBar.tsx
```

**Acceptance Criteria:**
- [ ] No file > 200 lines
- [ ] Clear separation of concerns
- [ ] All tests pass
- [ ] No functionality lost

---

### Type Safety

#### Item 2.4: Remove @ts-nocheck

Files to fix:
- [ ] `frontend/web/src/app-theme.tsx`
- [ ] Search for other `@ts-nocheck` instances

**Acceptance Criteria:**
- [ ] Zero `@ts-nocheck` in production code
- [ ] All types properly defined
- [ ] TypeScript strict mode passes

---

## Phase 3: Differentiation (Weeks 11-14)

### Knowledge Graph UX

#### Item 3.1: Surface KG Insights

Current: KG exists in backend only

Target: Panel in canvas showing:
- Related projects
- Semantic search across artifacts
- Code relationship visualization

**Acceptance Criteria:**
- [ ] Design KG insights panel
- [ ] API endpoints for KG queries
- [ ] UI integration
- [ ] User testing

---

### Performance Validation

#### Item 3.2: JMH Benchmarks

Create: `core/services-lifecycle/src/jmh/...`

Benchmarks:
- Agent dispatch latency
- Workflow execution time
- Data Cloud query performance
- Auth token validation

**Acceptance Criteria:**
- [ ] JMH harness configured
- [ ] Benchmarks for critical paths
- [ ] Performance baselines established
- [ ] CI performance regression detection

---

### Architecture Enforcement

#### Item 3.3: Adapter Pattern Tests

Create architecture tests:
```java
@Test
public void coreModulesShouldNotDependOnDataCloudDirectly() {
    // No imports of products:data-cloud in core/
}

@Test
public void allDataAccessShouldGoThroughAdapter() {
    // Only infrastructure/datacloud imports Data Cloud
}
```

**Acceptance Criteria:**
- [ ] Architecture tests in CI
- [ ] All violations fixed
- [ ] Documentation updated

---

## Daily Tracking Format

```markdown
## YYYY-MM-DD

### Completed
- [x] Item X.Y - Brief description

### In Progress
- [ ] Item X.Y - Brief description (% complete)

### Blocked
- [ ] Item X.Y - Blocker reason, help needed

### Notes
- Any observations, decisions, discoveries
```

---

## Weekly Review Questions

1. Are all P0 items from current phase complete?
2. Have any new critical issues been discovered?
3. Are we on track for target completion?
4. Do we need to adjust priorities?
5. Are there blockers requiring escalation?

---

## Definition of Done (Per Item)

- [ ] Code changes implemented
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Code review completed
- [ ] CI passes
- [ ] Deployed to staging
- [ ] Acceptance criteria verified
- [ ] Item marked complete in tracking

---

**Next Action:** Schedule Phase 0 kickoff, assign Item 0.1 owner
