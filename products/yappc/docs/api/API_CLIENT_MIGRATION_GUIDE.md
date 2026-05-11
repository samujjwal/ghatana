# API Client Migration Guide

## Overview

This guide documents the migration from hand-coded REST client to OpenAPI-generated TypeScript client.

## Source of Truth

**OpenAPI Specification (`products/yappc/docs/api/openapi.yaml`)** is the canonical source for the generated TypeScript client.

- **Generation**: The TypeScript client is automatically generated from the OpenAPI specification
- **Adapter Layer**: The `client.ts` file provides an ergonomic adapter layer that delegates to the generated client
- **Route Manifest**: The route manifest (`products/yappc/docs/api/route-manifest.yaml`) is the source of truth for all routes, which must match the OpenAPI specification
- **Validation**: The `checkYappcOpenApiParity` Gradle task validates parity between the route manifest and OpenAPI specification

## Why This Migration?

The hand-coded REST client had several issues:
- Drift from OpenAPI specification
- Token-oriented auth responses instead of cookie-session responses
- Duplicate type definitions between client and domain files
- Manual maintenance burden for API changes

The OpenAPI-generated client:
- Ensures type safety and parity with OpenAPI specification
- Automatically reflects auth/session changes
- Eliminates manual duplication of request/response types
- Provides a single source of truth for API contracts

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              Application Code (React Components)            │
│  Uses ergonomic API methods: yappcApi.projects.list()     │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Adapter Layer (client.ts)                      │
│  Provides ergonomic wrapper over generated client           │
│  Handles scope propagation, error handling, retries       │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│         Generated OpenAPI Client (from openapi.yaml)       │
│  Type-safe, auto-generated from OpenAPI specification     │
│  Located: frontend/web/src/clients/generated/             │
└─────────────────────────────────────────────────────────────┘
```

## Migration Map

| Old Method (hand-coded) | Generated Operation | Notes |
|------------------------|---------------------|-------|
| `projects.list()` | `ProjectsService.listProjects()` | Workspace-scoped |
| `projects.get(id)` | `ProjectsService.getProject(id)` | Project-scoped |
| `projects.create(data)` | `ProjectsService.createProject(data)` | |
| `projects.update(id, data)` | `ProjectsService.updateProject(id, workspaceId, data)` | Requires workspaceId |
| `projects.delete(id)` | `ProjectsService.deleteProject(id, workspaceId)` | Requires workspaceId |
| `workspaces.list()` | `WorkspacesService.listWorkspaces()` | |
| `workspaces.get(id)` | `WorkspacesService.getWorkspace(id)` | |
| `workspaces.create(data)` | `WorkspacesService.createWorkspace(data)` | |
| `workspaces.update(id, data)` | `WorkspacesService.updateWorkspace(id, data)` | |
| `workspaces.delete(id)` | `WorkspacesService.deleteWorkspace(id)` | |

## Auth Migration

### Before (Token-Oriented)

```typescript
// Login returned token pair
interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
}

// Client stored tokens locally
const login = async (email: string, password: string) => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const tokens = await response.json() as AuthTokenResponse;
  localStorage.setItem('accessToken', tokens.accessToken);
  localStorage.setItem('refreshToken', tokens.refreshToken);
};
```

### After (Cookie-Session)

```typescript
// Login returns session info (server sets httpOnly cookie)
interface LoginSessionResponse {
  user: AuthUser;
  session: {
    expiresAt: string;
    authMode: 'COOKIE';
  };
}

// Client uses credentials: 'same-origin' for cookie handling
const login = async (email: string, password: string) => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
    credentials: 'same-origin', // Cookie handling
  });
  const session = await response.json() as LoginSessionResponse;
  // No local storage needed - server manages session cookie
};
```

## Type Migration

### Before (Duplicate Types)

```typescript
// client.ts - duplicate type definitions
interface Project {
  id: string;
  name: string;
  description?: string;
}

// domain/project.ts - duplicate type definitions
interface Project {
  id: string;
  name: string;
  description?: string;
  status: string;
}
```

### After (Generated Types)

```typescript
// Use generated types from OpenAPI client
import { Project } from '@/clients/generated/api/core/OpenAPI';

// Or use adapter layer which wraps generated types
import { yappcApi } from '@/lib/api/client';

// No duplicate type definitions needed
```

## Migration Steps

### Step 1: Review Generated Client

Explore the generated client to understand the new API structure:

```bash
# View generated client
ls frontend/web/src/clients/generated/
```

### Step 2: Identify Hand-Coded Methods

Search for hand-coded API methods in `client.ts`:

```bash
grep -n "async" frontend/web/src/lib/api/client.ts
```

### Step 3: Map to Generated Operations

Use the migration map above to find the corresponding generated operation.

### Step 4: Update Adapter Layer

Update the adapter methods in `client.ts` to delegate to the generated client:

**Before:**
```typescript
async listProjects(workspaceId: string): Promise<Project[]> {
  const response = await fetch(`/api/v1/projects?workspaceId=${workspaceId}`);
  return response.json();
}
```

**After:**
```typescript
async listProjects(workspaceId: string): Promise<ProjectsResponse> {
  return ProjectsService.listProjects(workspaceId);
}
```

### Step 5: Remove Duplicate Types

Remove duplicate type definitions from `client.ts` and domain files. Use generated types instead.

### Step 6: Update Auth Handling

Update auth methods to use cookie-session instead of token storage:

**Before:**
```typescript
async refreshAuthToken(refreshToken: string): Promise<RefreshTokenResponse> {
  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
  return response.json();
}
```

**After:**
```typescript
async refreshSession(): Promise<RefreshTokenResponse> {
  // Cookie is automatically sent by browser
  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    credentials: 'same-origin',
  });
  return response.json();
}
```

### Step 7: Test

Run tests to verify all API calls still work correctly:

```bash
pnpm test
```

### Step 8: Run Contract Tests

Verify API parity between client, route manifest, and OpenAPI:

```bash
./gradlew :products:yappc:checkYappcOpenApiParity
pnpm test apiContract
```

## Common Migration Patterns

### Pattern 1: Simple GET Request

**Before:**
```typescript
async getProject(projectId: string): Promise<Project> {
  const response = await fetch(`/api/v1/projects/${projectId}`);
  return response.json();
}
```

**After:**
```typescript
async getProject(projectId: string): Promise<ProjectResponse> {
  return ProjectsService.getProject(projectId);
}
```

### Pattern 2: POST Request with Body

**Before:**
```typescript
async createProject(data: CreateProjectRequest): Promise<Project> {
  const response = await fetch('/api/v1/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  return response.json();
}
```

**After:**
```typescript
async createProject(data: CreateProjectRequest): Promise<ProjectResponse> {
  return ProjectsService.createProject(data);
}
```

### Pattern 3: Request with Path Parameters

**Before:**
```typescript
async updateProject(projectId: string, data: UpdateProjectRequest): Promise<Project> {
  const response = await fetch(`/api/v1/projects/${projectId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  return response.json();
}
```

**After:**
```typescript
async updateProject(projectId: string, workspaceId: string, data: UpdateProjectRequest): Promise<ProjectResponse> {
  return ProjectsService.updateProject(projectId, workspaceId, data);
}
```

## Rollback Plan

If issues arise during migration:

1. Revert `client.ts` to the hand-coded implementation
2. Report issues to the team for investigation
3. Check OpenAPI specification for breaking changes
4. Regenerate client if OpenAPI was updated incorrectly

## Lint Rules

The following ESLint rules enforce proper API client usage:

- **no-raw-fetch**: Disallows direct `fetch()` calls, requires typed API clients
- **no-direct-platform-imports**: Prevents direct platform module imports, requires facades

## Contract Tests

Contract tests verify API parity between:
- Route manifest and OpenAPI specification
- Generated client and OpenAPI specification
- Adapter layer and generated client

Run contract tests with:

```bash
# Backend: validate route manifest parity with OpenAPI
./gradlew :products:yappc:checkYappcOpenApiParity

# Frontend: validate client contract
pnpm test apiContract
```

## Timeline

- **Phase 1**: OpenAPI-generated client created (✅ Complete)
- **Phase 2**: Adapter layer updated to delegate to generated client (✅ Complete)
- **Phase 3**: Duplicate types removed (✅ Complete)
- **Phase 4**: Auth migrated to cookie-session (✅ Complete)
- **Phase 5**: Documentation and migration guide (✅ Complete)
- **Ongoing**: Regenerate client when OpenAPI specification changes

## Regenerating the Client

When the OpenAPI specification is updated, regenerate the client:

```bash
cd frontend/web
pnpm generate:client
```

## Support

If you encounter issues during migration:

1. Check this guide for common patterns
2. Review the migration map for method name changes
3. Verify OpenAPI specification matches route manifest
4. Consult the team for complex migration scenarios
5. Use the rollback plan if needed

## Checklist

- [ ] Review generated client structure
- [ ] Identify hand-coded API methods in client.ts
- [ ] Map each method to generated operation
- [ ] Update adapter layer to delegate to generated client
- [ ] Remove duplicate type definitions
- [ ] Update auth handling to use cookie-session
- [ ] Run tests to verify functionality
- [ ] Run contract tests to verify parity
- [ ] Update migration map with any new methods
