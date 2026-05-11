# Frontend API Client Migration Map

**Task 3.2.5**: Migration map documenting old method → generated operation

## Overview

This document tracks the migration from hand-coded fetch-based API calls to the generated OpenAPI client in `client.ts`. The adapter layer in `client.ts` delegates to generated services while preserving backward compatibility.

## Migration Status

- **Status**: Partial migration complete
- **Generated Client Location**: `frontend/web/src/clients/generated/api/`
- **Adapter Layer**: `frontend/web/src/lib/api/client.ts`

## Service Mappings

### AuthService (Auth)

| Old Method | Generated Service | Generated Method | Status | Notes |
|-----------|------------------|------------------|---------|-------|
| `auth.login` | `GeneratedAuthService` | `login` | ✅ Migrated | Adapter converts LoginRequest, adapts LoginResponse to LoginSessionResponse |
| `auth.loginSession` | `GeneratedAuthService` | `login` | ✅ Migrated | Alias to login for backward compatibility |
| `auth.refresh` | — | — | ⏳ Pending | Kept old implementation (cookie refresh not in generated client yet) |
| `auth.logout` | — | — | ⏳ Pending | Kept old implementation (cookie logout not in generated client yet) |
| `auth.updateProfile` | — | — | ⏳ Pending | Not in generated client yet |
| `auth.ssoCallback` | — | — | ⏳ Pending | Not in generated client yet |
| `auth.validate` | `GeneratedAuthService` | `validateToken` | ✅ Migrated | Adapter converts response |
| `auth.me` | `GeneratedAuthService` | `currentUser` | ✅ Migrated | Adapter converts UserInfo to UserProfile |

### WorkspacesService (Workspaces)

| Old Method | Generated Service | Generated Method | Status | Notes |
|-----------|------------------|------------------|---------|-------|
| `workspaces.list` | `WorkspacesService` | `listWorkspaces` | ✅ Migrated | Takes no parameters (filtered server-side) |
| `workspaces.get` | `WorkspacesService` | `getWorkspace` | ✅ Migrated | Direct parameter mapping |
| `workspaces.create` | `WorkspacesService` | `createWorkspace` | ✅ Migrated | Direct parameter mapping |
| `workspaces.update` | `WorkspacesService` | `updateWorkspace` | ✅ Migrated | Direct parameter mapping |
| `workspaces.delete` | `WorkspacesService` | `deleteWorkspace` | ✅ Migrated | Direct parameter mapping |
| `workspaces.suggestName` | — | — | ⏳ Pending | Kept old implementation |
| `workspaces.refreshAi` | — | — | ⏳ Pending | Kept old implementation |
| `workspaces.refreshAiDetails` | — | — | ⏳ Pending | Kept old implementation |

### ProjectsService (Projects)

| Old Method | Generated Service | Generated Method | Status | Notes |
|-----------|------------------|------------------|---------|-------|
| `projects.list` | `ProjectsService` | `listProjects` | ✅ Migrated | Requires workspaceId parameter |
| `projects.get` | `ProjectsService` | `getProject` | ✅ Migrated | Takes projectId and optional workspaceId |
| `projects.create` | `ProjectsService` | `createProject` | ✅ Migrated | Direct parameter mapping |
| `projects.update` | `ProjectsService` | `updateProject` | ✅ Migrated | Requires projectId, workspaceId, and body |
| `projects.delete` | `ProjectsService` | `deleteProject` | ✅ Migrated | Requires projectId and workspaceId |
| `projects.getScoped` | — | — | ⏳ Pending | Kept old implementation (enrichment logic) |
| `projects.updateScoped` | — | — | ⏳ Pending | Kept old implementation (enrichment logic) |
| `projects.suggestName` | `ProjectsService` | `suggestProjectName` | ⏳ Pending | Kept old implementation |
| `projects.setupSuggestion` | `ProjectsService` | `suggestProjectSetup` | ⏳ Pending | Kept old implementation |
| `projects.current` | — | — | ⏳ Pending | Not in generated client yet |
| `projects.activity` | `ProjectsService` | `getProjectActivity` | ⏳ Pending | Kept old implementation |
| `projects.artifacts` | — | — | ⏳ Pending | Not in generated client yet |
| `projects.sprintCurrent` | — | — | ⏳ Pending | Not in generated client yet |
| `projects.backlog` | — | — | ⏳ Pending | Not in generated client yet |
| `projects.recentRuns` | — | — | ⏳ Pending | Not in generated client yet |
| `projects.availableForInclusion` | `ProjectsService` | `listAvailableProjectsForInclusion` | ⏳ Pending | Kept old implementation |
| `projects.include` | `ProjectsService` | `includeProject` | ⏳ Pending | Kept old implementation |
| `projects.dashboardActions` | `ProjectsService` | `listProjectDashboardActions` | ⏳ Pending | Kept old implementation |
| `projects.executeDashboardAction` | `ProjectsService` | `executeProjectDashboardAction` | ⏳ Pending | Kept old implementation |
| `projects.aiCost` | `ProjectsService` | `getProjectAiCost` | ⏳ Pending | Kept old implementation |
| `projects.refreshAi` | `ProjectsService` | `refreshProjectAiSuggestions` | ⏳ Pending | Kept old implementation |
| `projects.refreshAiDetails` | `ProjectsService` | `refreshProjectAiSuggestions` | ⏳ Pending | Kept old implementation |
| `projects.export` | — | — | ⏳ Pending | Not in generated client yet |

### Other Services

| Old Method | Generated Service | Status | Notes |
|-----------|------------------|---------|-------|
| `userProfile.get` | — | ⏳ Pending | Not in generated client yet |
| `userProfile.update` | — | ⏳ Pending | Not in generated client yet |
| `billing.getSummary` | — | ⏳ Pending | Not in generated client yet |
| `operations.getOnCallSchedule` | — | ⏳ Pending | Not in generated client yet |
| `operations.getServiceTopology` | — | ⏳ Pending | Not in generated client yet |
| `collaboration.*` | — | ⏳ Pending | Not in generated client yet |
| `settings.getWorkspaceSettings` | — | ⏳ Pending | Not in generated client yet |
| `anomalies.*` | — | ⏳ Pending | Not in generated client yet |
| `canvas.save` | — | ⏳ Pending | Not in generated client yet |
| `errorReporting.report` | — | ⏳ Pending | Not in generated client yet |

## Type Mappings

### Workspace Types

| Old Type | Generated Type | Status | Notes |
|----------|----------------|---------|-------|
| `Workspace` | `Workspace` | ✅ Migrated | Exported as type alias |
| `CreateWorkspaceRequest` | `CreateWorkspaceRequest` | ✅ Migrated | Exported as type alias |
| `UpdateWorkspaceRequest` | `UpdateWorkspaceRequest` | ✅ Migrated | Exported as type alias |

### Project Types

| Old Type | Generated Type | Status | Notes |
|----------|----------------|---------|-------|
| `Project` (enriched) | `Project` (base) | ✅ Partial | Extended with frontend-specific fields (role, isOwned, capabilities, etc.) |
| `ProjectBase` | `Project` | ✅ Migrated | Exported as type alias for base type |
| `CreateProjectRequest` | `CreateProjectRequest` | ✅ Migrated | Exported as type alias |
| `UpdateProjectRequest` | `UpdateProjectRequest` | ✅ Migrated | Exported as type alias |

### Auth Types

| Old Type | Generated Type | Status | Notes |
|----------|----------------|---------|-------|
| `LoginRequest` | `LoginRequest` | ✅ Migrated | Compatible structure |
| `LoginSessionResponse` | `LoginResponse` | ✅ Partial | Adapter converts token-based response to session-based |
| `AuthTokenResponse` | — | ⏳ Pending | Kept for backward compatibility |

## Configuration

### OpenAPI Client Configuration

```typescript
OpenAPI.BASE = '/api';
OpenAPI.WITH_CREDENTIALS = true;  // Cookie-session mode
OpenAPI.CREDENTIALS = 'same-origin';
OpenAPI.TOKEN = undefined;  // No token for cookie-session mode
```

### Cookie-Session Mode

- **Auth Mode**: Cookie-based session (COOKIE authMode)
- **Token Storage**: Not used in browser (server manages session via cookies)
- **Service Auth**: X-API-Key and Bearer tokens still supported for internal/service use

## Migration Path

### Phase 1: Core CRUD (Complete)
- ✅ Auth: login, validate, me
- ✅ Workspaces: list, get, create, update, delete
- ✅ Projects: list, get, create, update, delete

### Phase 2: Remaining Operations (Pending)
- ⏳ Auth: refresh, logout (need cookie-session support in generated client)
- ⏳ Projects: dashboard actions, activity, artifacts, etc.
- ⏳ Workspaces: AI refresh operations
- ⏳ Other services: billing, operations, collaboration, etc.

### Phase 3: Cleanup (Future)
- Remove adapter layer once all operations are migrated
- Remove duplicate type definitions
- Update all call sites to use generated client directly

## Notes

- The adapter layer preserves backward compatibility while delegating to generated services
- Frontend-specific enrichment (e.g., `role`, `isOwned`, `capabilities`) is kept in the local `Project` interface
- Cookie-session mode requires `WITH_CREDENTIALS: true` and `CREDENTIALS: 'same-origin'`
- Some operations remain hand-coded because they're not yet in the OpenAPI spec or require client-side enrichment logic

## References

- **Generated Client**: `frontend/web/src/clients/generated/api/`
- **Adapter Layer**: `frontend/web/src/lib/api/client.ts`
- **Contract Test**: `frontend/web/src/lib/api/__tests__/client.contract.test.ts`
- **OpenAPI Spec**: `products/yappc/frontend/web/openapi.yaml`
