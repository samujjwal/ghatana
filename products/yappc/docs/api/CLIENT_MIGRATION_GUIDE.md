# Client Migration Guide

**Purpose:** Migrate from legacy `yappcApi` to generated OpenAPI client  
**Approach:** Fix-forward (no deprecation period)  
**Last Updated:** 2026-05-11

---

## Migration Overview

The legacy `client.ts` exports a `yappcApi` object that wraps various API endpoint groups. This guide shows how to migrate to the generated OpenAPI client directly.

**Current State:**
- 30 files import from `@/lib/api/client`
- Consumers use `yappcApi` object with endpoint groups (auth, workspaces, projects, lifecycle, etc.)
- No direct usage of low-level fetch helpers (get, post, patch, put, del)

**Target State:**
- All consumers import directly from generated client: `@/clients/generated/api`
- Legacy `yappcApi` wrapper removed
- `client.ts` simplified to only export generated client configuration

---

## Migration Mapping

### Auth Endpoints

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

const result = await yappcApi.auth.login(credentials);
```

**After:**
```typescript
import { AuthService as GeneratedAuthService } from '@/clients/generated/api';

const result = await GeneratedAuthService.loginSession({ 
  requestBody: credentials 
});
```

### Audit Endpoints

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

const result = await yappcApi.audit.emit(eventData);
```

**After:**
```typescript
import { AuditService } from '@/clients/generated/api';

const result = await AuditService.recordAuditEvent(eventData);
```

### Workspaces Endpoints

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

const workspace = await yappcApi.workspaces.getWorkspace(workspaceId);
```

**After:**
```typescript
import { WorkspacesService } from '@/clients/generated/api';

const workspace = await WorkspacesService.getWorkspace({ workspaceId });
```

### Projects Endpoints

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

const project = await yappcApi.projects.getProject(projectId);
```

**After:**
```typescript
import { ProjectsService } from '@/clients/generated/api';

const project = await ProjectsService.getProject({ projectId });
```

### Lifecycle/Phase Endpoints

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

const packet = await yappcApi.phases.getPhasePacket(projectId, phase);
```

**After:**
```typescript
import { PhasesService } from '@/clients/generated/api';

const packet = await PhasesService.getPhasePacket({ projectId, phase });
```

### Preview Sessions

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

const session = await yappcApi.previewSessions.createPreviewSession(sessionData);
```

**After:**
```typescript
import { PreviewSessionsService } from '@/clients/generated/api';

const session = await PreviewSessionsService.createPreviewSession({ requestBody: sessionData });
```

---

## Common Migration Patterns

### Pattern 1: Simple GET Request

**Before:**
```typescript
const result = await yappcApi.workspaces.getWorkspace(workspaceId);
```

**After:**
```typescript
import { WorkspacesService } from '@/clients/generated/api';
const result = await WorkspacesService.getWorkspace({ workspaceId });
```

### Pattern 2: POST Request with Body

**Before:**
```typescript
const result = await yappcApi.projects.createProject(projectData);
```

**After:**
```typescript
import { ProjectsService } from '@/clients/generated/api';
const result = await ProjectsService.createProject({ requestBody: projectData });
```

### Pattern 3: Request with Path and Query Parameters

**Before:**
```typescript
const result = await yappcApi.phases.getPhasePacket(projectId, phase);
```

**After:**
```typescript
import { PhasesService } from '@/clients/generated/api';
const result = await PhasesService.getPhasePacket({ projectId, phase });
```

### Pattern 4: Type Imports

**Before:**
```typescript
import type { Project, Workspace } from '@/lib/api/client';
```

**After:**
```typescript
import type { components } from '@/clients/generated/api';
type Project = components['schemas']['Project'];
type Workspace = components['schemas']['Workspace'];
```

---

## Error Handling Migration

**Before:**
```typescript
import { ApiRequestError, yappcApi } from '@/lib/api/client';

try {
  const result = await yappcApi.workspaces.getWorkspace(workspaceId);
} catch (error) {
  if (error instanceof ApiRequestError) {
    console.error(`Request failed: ${error.status} - ${error.message}`);
  }
}
```

**After:**
```typescript
import { WorkspacesService } from '@/clients/generated/api';

try {
  const result = await WorkspacesService.getWorkspace({ workspaceId });
} catch (error) {
  // Generated client throws standard Error or ApiError
  console.error(`Request failed: ${error}`);
}
```

---

## Scope Header Configuration

The generated client is already configured with cookie-session mode in `client.ts`:

```typescript
import { OpenAPI } from '@/clients/generated/api';

OpenAPI.BASE = '/api';
OpenAPI.WITH_CREDENTIALS = true;
OpenAPI.CREDENTIALS = 'same-origin';
```

No additional scope header configuration is needed for the generated client.

---

## Complete File Migration Example

**Before (AuthService.ts):**
```typescript
import { ApiRequestError, yappcApi } from '@/lib/api/client';
import type { components } from '@/clients/generated/openapi';

type ApiAuthResponse = components['schemas']['LoginResponse'];

export class AuthService {
  async login(credentials: LoginCredentials): Promise<AuthResult> {
    try {
      const response = await yappcApi.auth.login(credentials);
      return { success: true, user: response.user };
    } catch (error) {
      if (error instanceof ApiRequestError) {
        return { success: false, error: error.message };
      }
      throw error;
    }
  }
}
```

**After (AuthService.ts):**
```typescript
import { AuthService as GeneratedAuthService } from '@/clients/generated/api';
import type { components } from '@/clients/generated/api';

type ApiAuthResponse = components['schemas']['LoginResponse'];

export class AuthService {
  async login(credentials: LoginCredentials): Promise<AuthResult> {
    try {
      const response = await GeneratedAuthService.login({ requestBody: credentials });
      return { success: true, user: response.user };
    } catch (error) {
      return { success: false, error: String(error) };
    }
  }
}
```

---

## Generated Client Service Reference

| Legacy Endpoint Group | Generated Service | Import Path |
|----------------------|-------------------|-------------|
| `yappcApi.auth` | `AuthService` | `@/clients/generated/api` |
| `yappcApi.workspaces` | `WorkspacesService` | `@/clients/generated/api` |
| `yappcApi.projects` | `ProjectsService` | `@/clients/generated/api` |
| `yappcApi.phases` | `PhasesService` | `@/clients/generated/api` |
| `yappcApi.lifecycle` | `LifecycleService` | `@/clients/generated/api` |
| `yappcApi.previewSessions` | `PreviewSessionsService` | `@/clients/generated/api` |
| `yappcApi.artifacts` | `ArtifactsService` | `@/clients/generated/api` |
| `yappcApi.pageArtifacts` | `PageArtifactsService` | `@/clients/generated/api` |
| `yappcApi.sourceImports` | `SourceImportsService` | `@/clients/generated/api` |
| `yappcApi.gates` | `GatesService` | `@/clients/generated/api` |
| `yappcApi.telemetry` | `TelemetryService` | `@/clients/generated/api` |
| `yappcApi.audit` | `AuditService` | `@/clients/generated/api` |

---

## Verification Steps

After migration, verify:

1. **Build succeeds:** `npm run build`
2. **Type checking passes:** `npm run typecheck`
3. **Linting passes:** `npm run lint`
4. **No imports from `@/lib/api/client` remain** (except for type exports)
5. **All tests pass:** `npm run test`

---

## Rollback Plan

If issues arise after migration:
1. Restore files from git: `git checkout HEAD -- frontend/web/src`
2. Investigate and fix root cause
3. Re-attempt migration after fixes
