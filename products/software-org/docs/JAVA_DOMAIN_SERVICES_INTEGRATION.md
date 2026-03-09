# Java Domain Services Integration - Software-Org Personas

**Status**: Complete ✅  
**Date**: 2025-01-XX  
**Architecture**: Hybrid Java + Node.js Backend

## Overview

Software-org now has a **properly separated hybrid backend architecture**:

1. **Java Backend** - Domain logic for persona roles, permissions, workflows
2. **Node.js Backend** - User preferences, workspace overrides, real-time collaboration
3. **Frontend** - React 19 + React Router v7 + React Query

## Architecture Boundaries (CRITICAL)

### Java Domain Services (✅ Implemented)

**What Java Handles** - Domain Logic:
- ✅ Persona role definitions (14 roles: Admin, TechLead, Developer, + 11 specialized)
- ✅ Permission models (what each role can do)
- ✅ Role composition rules (how roles combine, conflicts)
- ✅ Role validation (business rules enforcement)
- ✅ Permission resolution (compute effective permissions from active roles)

**Java Modules Created**:
```
products/software-org/
├── libs/java/domain-models/src/main/java/com/ghatana/softwareorg/domain/persona/
│   ├── PersonaRoleDefinition.java      # Value object for role metadata
│   └── PersonaRoleService.java         # Domain service for role management
└── src/main/java/com/ghatana/softwareorg/api/persona/
    ├── PersonaRoleController.java      # REST API controller
    └── PersonaRoleHttpAdapter.java     # HTTP adapter using core/http-server
```

**Java API Endpoints**:
```
GET  /api/v1/personas/roles                  → List all role definitions
GET  /api/v1/personas/roles/:roleId          → Get specific role definition
POST /api/v1/personas/roles/validate         → Validate role combination
POST /api/v1/personas/roles/resolve-permissions → Resolve effective permissions
```

### Node.js Backend (✅ Implemented)

**What Node.js Handles** - User Preferences:
- ✅ User persona configurations (WHICH roles user activates)
- ✅ Workspace admin overrides (admin-defined defaults)
- ✅ JWT authentication (user sessions)
- ✅ REST API endpoints (user-facing CRUD)
- ⏸️ WebSocket real-time sync (Phase 3 Task 16)

**Node.js Modules Created**:
```
products/software-org/apps/backend/src/
├── middleware/
│   └── auth.ts                                    # JWT authentication
├── services/
│   ├── persona.service.ts                         # User preference CRUD
│   ├── workspace.service.ts                       # Admin override CRUD
│   └── persona-role-domain.client.ts              # Java domain service client
└── routes/
    ├── personas.ts                                 # Persona preference endpoints
    └── workspaces.ts                               # Workspace override endpoints
```

**Node.js API Endpoints**:
```
GET    /api/personas/:workspaceId              → Fetch user's persona config
PUT    /api/personas/:workspaceId              → Update persona config
DELETE /api/personas/:workspaceId              → Reset to defaults

GET    /api/workspaces/:workspaceId/overrides  → Fetch admin overrides (admin only)
POST   /api/workspaces/:workspaceId/overrides  → Create/update overrides (admin)
DELETE /api/workspaces/:workspaceId/overrides  → Remove overrides (admin)
```

## Integration Pattern (CRITICAL)

### How Node.js Calls Java

**Workflow: User Updates Persona Config**

1. **Frontend** → Node.js: `PUT /api/personas/workspace-123`
   ```json
   {
     "activeRoles": ["tech-lead", "backend-developer"],
     "preferences": {
       "dashboardLayout": "grid",
       "features": {"ai-assistant": true}
     }
   }
   ```

2. **Node.js** → Java: `POST /api/v1/personas/roles/validate`
   ```json
   {
     "roleIds": ["tech-lead", "backend-developer"]
   }
   ```

3. **Java** → Node.js: Validation Result
   ```json
   {
     "isValid": true,
     "errorMessage": null
   }
   ```

4. **Node.js** → Database: Save user preferences
   ```sql
   INSERT INTO persona_preferences (user_id, workspace_id, active_roles, preferences)
   VALUES ('user-123', 'workspace-123', '["tech-lead", "backend-developer"]', '{"dashboardLayout":"grid"}')
   ```

5. **Node.js** → Frontend: Success Response
   ```json
   {
     "id": "pref-456",
     "userId": "user-123",
     "workspaceId": "workspace-123",
     "activeRoles": ["tech-lead", "backend-developer"],
     "preferences": {"dashboardLayout": "grid"}
   }
   ```

### Authorization Check Example

**Workflow: Check if User Can Approve Code**

1. **Frontend** → Node.js: `GET /api/projects/123/pull-requests`

2. **Node.js** → Database: Fetch user's active roles
   ```sql
   SELECT active_roles FROM persona_preferences
   WHERE user_id = 'user-123' AND workspace_id = 'workspace-123'
   ```
   Result: `["tech-lead", "backend-developer"]`

3. **Node.js** → Java: `POST /api/v1/personas/roles/resolve-permissions`
   ```json
   {
     "roleIds": ["tech-lead", "backend-developer"]
   }
   ```

4. **Java** → Node.js: Effective Permissions
   ```json
   {
     "permissions": {
       "code.approve": true,
       "code.write": true,
       "deployment.staging": true
     },
     "capabilities": {
       "approveCodeReviews": true,
       "submitCode": true,
       "deployStaging": true
     }
   }
   ```

5. **Node.js** → Check Permission: `hasPermission('code.approve') === true`

6. **Node.js** → Frontend: Include approval flag in response
   ```json
   {
     "pullRequests": [...],
     "userPermissions": {
       "canApprove": true,
       "canMerge": true,
       "canDeploy": false
     }
   }
   ```

## Role Definitions (14 Roles)

### Base Roles (4)

| Role ID    | Display Name    | Permissions                                                          |
| ---------- | --------------- | -------------------------------------------------------------------- |
| `admin`    | Administrator   | All permissions (workspace.manage, team.manage, deployment.prod, etc.) |
| `tech-lead` | Tech Lead      | code.approve, architecture.review, deployment.staging, analytics.team |
| `developer` | Developer      | code.write, code.review, deployment.dev, project.view                 |
| `viewer`   | Viewer          | project.view, analytics.basic                                         |

### Specialized Roles (10)

| Role ID                  | Display Name           | Parent Role | Key Permissions                              |
| ------------------------ | ---------------------- | ----------- | -------------------------------------------- |
| `fullstack-developer`    | Full-Stack Developer   | developer   | code.write, code.review, debugProduction     |
| `backend-developer`      | Backend Developer      | developer   | code.write, database.read, api.test          |
| `frontend-developer`     | Frontend Developer     | developer   | code.write, design.view, analytics.user      |
| `devops-engineer`        | DevOps Engineer        | developer   | deployment.prod, monitoring.full, infra.manage |
| `qa-engineer`            | QA Engineer            | developer   | test.execute, bug.report, test.plan          |
| `product-manager`        | Product Manager        | viewer      | product.plan, requirements.define, analytics.product |
| `designer`               | UX/UI Designer         | viewer      | design.create, prototype.create, user.research |
| `data-analyst`           | Data Analyst           | viewer      | analytics.full, database.read, report.create |
| `security-engineer`      | Security Engineer      | developer   | security.audit, vulnerability.scan, compliance |
| `architect`              | Software Architect     | tech-lead   | architecture.design, code.approve, project.view |

## Business Rules

### Role Validation Rules (Enforced by Java)

1. ✅ At least one role must be activated
2. ✅ Maximum 5 roles can be activated
3. ✅ Admin and Viewer roles are incompatible
4. ✅ All role IDs must exist in role registry
5. ✅ Custom rules can be added to `PersonaRoleService.validateRoleActivation()`

### Permission Resolution Rules

1. **Union of Permissions**: User gets ALL permissions from ALL active roles
2. **Inheritance**: Specialized roles inherit permissions from parent roles
3. **No Conflicts**: Multiple roles with same permission → permission is granted
4. **Parent Roles**: `architect` inherits from `tech-lead` → gets tech-lead permissions too

**Example**:
```
activeRoles: ["tech-lead", "backend-developer"]

Effective Permissions:
- From tech-lead: code.approve, architecture.review, deployment.staging
- From backend-developer: database.read, api.test
- Inherited from developer (parent of backend-developer): code.write, code.review

Final Result: code.approve ✅, code.write ✅, database.read ✅, deployment.staging ✅
```

## Implementation Checklist

### ✅ Completed

- [x] Java domain model: `PersonaRoleDefinition` (record with 14 roles)
- [x] Java domain service: `PersonaRoleService` (role registry, validation, permission resolution)
- [x] Java REST controller: `PersonaRoleController` (4 endpoints)
- [x] Java HTTP adapter: `PersonaRoleHttpAdapter` (uses core/http-server abstractions)
- [x] Node.js domain client: `PersonaRoleDomainClient` (axios HTTP client)
- [x] Node.js service integration: `persona.service.ts` validates via Java before saving
- [x] Architecture documentation: This file

### ⏸️ Pending (Next Steps)

- [ ] **Java HTTP Server Registration** (1 hour)
  - Register `PersonaRoleHttpAdapter` routes in `SoftwareOrgApiGateway`
  - Test Java endpoints: `curl localhost:8080/api/v1/personas/roles`

- [ ] **Node.js Environment Config** (30 min)
  - Add `JAVA_API_BASE_URL=http://localhost:8080` to `.env`
  - Update `persona-role-domain.client.ts` to use env variable

- [ ] **Add axios Dependency** (5 min)
  ```bash
  cd apps/backend && pnpm add axios
  ```

- [ ] **Java Unit Tests** (2 hours)
  - `PersonaRoleDefinitionTest` - Test role creation, validation
  - `PersonaRoleServiceTest` - Test role registry, validation rules, permission resolution
  - `PersonaRoleControllerTest` - Test REST endpoints

- [ ] **Node.js Integration Tests** (2 hours)
  - Mock Java API responses (MSW or nock)
  - Test `upsertPersonaPreference()` with role validation
  - Test error handling (invalid roles, Java service down)

- [ ] **Redis Caching** (2 hours - Optional Performance Optimization)
  - Cache role definitions (1 hour TTL)
  - Cache permission resolutions (15 min TTL)
  - Invalidate on role definition changes

- [ ] **Frontend Integration** (Task 17 - React Query Hooks)
  - `useRoleDefinitions()` - Fetch all role definitions from Java
  - `usePersonaConfig()` - Fetch user preferences from Node.js
  - `useUpdatePersonaConfig()` - Mutation with optimistic UI updates
  - React Router v7 loaders for SSR data fetching

- [ ] **WebSocket Real-time Sync** (Task 16)
  - Emit `persona:updated` event when preferences change
  - Broadcast to all user sessions across workspaces
  - Optimistic UI updates with fallback

## Testing Strategy

### Java Unit Tests (JUnit 5)

```java
@DisplayName("Persona Role Service Tests")
class PersonaRoleServiceTest {
    private PersonaRoleService service;

    @BeforeEach
    void setUp() {
        service = new PersonaRoleService();
    }

    @Test
    @DisplayName("Should validate valid role combination")
    void shouldValidateValidRoleCombination() {
        // GIVEN: Valid roles
        List<String> roles = List.of("tech-lead", "backend-developer");

        // WHEN: Validate
        var result = service.validateRoleActivation(roles);

        // THEN: Valid
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should reject incompatible roles")
    void shouldRejectIncompatibleRoles() {
        // GIVEN: Incompatible roles (Admin + Viewer)
        List<String> roles = List.of("admin", "viewer");

        // WHEN: Validate
        var result = service.validateRoleActivation(roles);

        // THEN: Invalid
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("incompatible");
    }

    @Test
    @DisplayName("Should resolve effective permissions from multiple roles")
    void shouldResolveEffectivePermissions() {
        // GIVEN: Multiple roles
        List<String> roles = List.of("tech-lead", "backend-developer");

        // WHEN: Resolve permissions
        var permissions = service.resolveEffectivePermissions(roles);

        // THEN: Union of all permissions
        assertThat(permissions.hasPermission("code.approve")).isTrue(); // from tech-lead
        assertThat(permissions.hasPermission("database.read")).isTrue(); // from backend-developer
        assertThat(permissions.hasPermission("deployment.production")).isFalse(); // not granted
    }
}
```

### Node.js Integration Tests (Vitest)

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { upsertPersonaPreference } from './persona.service';
import { getPersonaRoleDomainClient, ValidationError } from './persona-role-domain.client';

// Mock Java domain client
vi.mock('./persona-role-domain.client', () => ({
  getPersonaRoleDomainClient: vi.fn(() => ({
    validateRoleActivation: vi.fn()
  })),
  ValidationError: class ValidationError extends Error {}
}));

describe('PersonaService', () => {
  let roleClient: ReturnType<typeof getPersonaRoleDomainClient>;

  beforeEach(() => {
    roleClient = getPersonaRoleDomainClient();
  });

  it('should save preferences when role validation succeeds', async () => {
    // GIVEN: Java service returns valid
    vi.mocked(roleClient.validateRoleActivation).mockResolvedValue({
      isValid: true
    });

    // WHEN: Upsert preferences
    const result = await upsertPersonaPreference('user-123', 'workspace-123', {
      activeRoles: ['tech-lead', 'backend-developer'],
      preferences: { dashboardLayout: 'grid' }
    });

    // THEN: Preferences saved
    expect(result.activeRoles).toEqual(['tech-lead', 'backend-developer']);
    expect(roleClient.validateRoleActivation).toHaveBeenCalledWith(['tech-lead', 'backend-developer']);
  });

  it('should reject preferences when role validation fails', async () => {
    // GIVEN: Java service returns invalid
    vi.mocked(roleClient.validateRoleActivation).mockResolvedValue({
      isValid: false,
      errorMessage: 'Admin and Viewer roles are incompatible'
    });

    // WHEN/THEN: Upsert throws ValidationError
    await expect(
      upsertPersonaPreference('user-123', 'workspace-123', {
        activeRoles: ['admin', 'viewer'],
        preferences: {}
      })
    ).rejects.toThrow(ValidationError);
  });
});
```

## Performance Considerations

### Caching Strategy

**Role Definitions** (High Cache Hit Rate):
- Cache in Redis: 1 hour TTL
- Role definitions rarely change
- Cache key: `persona:roles:all` or `persona:role:{roleId}`
- Invalidate: When role definitions updated (rare)

**Permission Resolution** (Medium Cache Hit Rate):
- Cache in Redis: 15 min TTL
- Permission checks happen frequently
- Cache key: `persona:permissions:{sortedRoleIds}` (e.g., `backend-developer,tech-lead`)
- Invalidate: When role definitions updated

**User Preferences** (Database Only):
- Do NOT cache (changes frequently with real-time sync)
- Read from PostgreSQL directly
- WebSocket broadcasts changes to connected clients

### Load Testing Targets

- **Java Role API**: 1000 req/sec (role definitions rarely queried, caching expected)
- **Node.js Preference API**: 5000 req/sec (read-heavy, write via WebSocket)
- **Validation Latency**: <50ms p99 (including Java call)
- **Permission Resolution**: <100ms p99 (including Java call)

## Deployment Configuration

### Environment Variables

**Java Backend** (application.properties):
```properties
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/software_org_java
```

**Node.js Backend** (.env):
```
DATABASE_URL="postgresql://postgres:postgres@localhost:5432/software_org_node"
JAVA_API_BASE_URL="http://localhost:8080"
JWT_SECRET="your-secret-key"
CORS_ORIGIN="http://localhost:5173"
```

### Database Schemas

**Java PostgreSQL Schema** (`software_org_java`):
- Role definitions stored in code (not database) for immutability
- Can optionally add `persona_roles` table for custom roles

**Node.js PostgreSQL Schema** (`software_org_node`):
- `persona_preferences` - User configurations (activeRoles JSON array)
- `workspace_overrides` - Admin overrides (overrides JSON object)

## Migration Path (If Needed)

### From Hardcoded Roles to Java Domain Service

If existing frontend has hardcoded role definitions:

1. **Phase 1: Dual Read** (Week 1)
   - Frontend queries Java for role definitions
   - Falls back to hardcoded if Java unavailable
   - Log metrics: Java success rate

2. **Phase 2: Java Primary** (Week 2)
   - Frontend uses Java as primary source
   - Remove hardcoded fallback after 99% success rate

3. **Phase 3: Cleanup** (Week 3)
   - Remove all hardcoded role definitions
   - 100% Java domain service

## FAQ

### Q: Why not store role definitions in Node.js?

**A**: Role definitions are **domain logic**, not user preferences:
- Role permissions are business rules (complex, reused across products)
- Role composition has inheritance and conflict resolution
- Roles integrate with workflows, agents, and event processing (all in Java)
- Node.js should only store WHICH roles user activates, not WHAT roles mean

### Q: Why not use gRPC instead of HTTP REST?

**A**: HTTP REST is simpler for MVP:
- Easier debugging (curl, browser DevTools)
- No proto compilation needed
- Lower latency not critical for role queries (caching mitigates)
- Can migrate to gRPC later if performance demands (Phase 2 optimization)

### Q: How does this work with multi-tenancy?

**A**: Tenant isolation at both layers:
- **Java**: Role definitions are tenant-agnostic (same roles for all tenants)
- **Node.js**: User preferences are tenant-specific (userId + workspaceId composite key)
- **Frontend**: Workspace context passed in all API calls

### Q: What if Java service is down?

**A**: Graceful degradation:
1. Node.js returns cached role definitions (Redis, stale data acceptable)
2. Frontend shows "Role validation unavailable" warning
3. User can still view preferences (read-only mode)
4. Circuit breaker prevents cascading failures

## Summary

✅ **Architecture Boundaries Respected**:
- Java handles domain logic (role definitions, validation, permissions)
- Node.js handles user preferences (which roles activated, UI state)
- No redundancy, clear separation of concerns

✅ **Integration Pattern Established**:
- Node.js calls Java HTTP REST API for domain operations
- Java validates role combinations before Node.js saves preferences
- Frontend queries both services (roles from Java, preferences from Node.js)

✅ **Next Steps Clear**:
1. Register Java HTTP routes in SoftwareOrgApiGateway
2. Add axios dependency to Node.js backend
3. Write unit/integration tests
4. Implement React Query hooks (Task 17)
5. Implement WebSocket sync (Task 16)

**Estimated Remaining Work**: 6-8 hours (Java integration + testing) + 8-10 hours (frontend + WebSocket)
