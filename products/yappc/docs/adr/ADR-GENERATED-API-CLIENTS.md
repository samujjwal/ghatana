# ADR: Generated OpenAPI Clients Only (SIMP-Y19)

**Status**: Accepted  
**Date**: 2026-04-27  
**Context**: YAPPC frontend has both a generated OpenAPI client (`clients/generated/openapi.ts`) and 11 hand-coded API clients in `clients/dashboard/` and `clients/ai/`.

---

## Decision

New API client code MUST use the generated client from `clients/generated/openapi.ts`.  
Hand-coded clients in `clients/dashboard/` and `clients/ai/` are deprecated and must be fix-forwarded on touch.

---

## Rationale

- Hand-coded clients diverge from the API contract and cause type drift.
- The generated client (`openapi-typescript`) derives its types directly from the OpenAPI spec, providing compile-time safety.
- Any mismatch between the hand-coded client and the real API is only caught at runtime.
- Generated clients make breaking API changes visible as TypeScript errors.

---

## Migration Pattern

### Before (hand-coded, deprecated)

```ts
// clients/dashboard/WorkspaceApiClient.ts
export class WorkspaceApiClient extends BaseDashboardApiClient {
  async listWorkspaces(): Promise<Workspace[]> {
    const res = await fetch('/api/workspaces', { credentials: 'include' });
    if (!res.ok) throw new Error('Failed to fetch workspaces');
    return res.json() as Promise<Workspace[]>;
  }
}
```

### After (generated client)

```ts
import type { paths } from '../generated/openapi';
import createFetchClient from 'openapi-fetch';

type ListWorkspacesResponse =
  paths['/api/workspaces']['get']['responses'][200]['content']['application/json'];

const client = createFetchClient<paths>({ baseUrl: '' });

export async function listWorkspaces(): Promise<ListWorkspacesResponse> {
  const { data, error } = await client.GET('/api/workspaces');
  if (error) throw error;
  return data;
}
```

Or, if the generated client is used with `@tanstack/react-query`:

```ts
export function useWorkspaces() {
  return useQuery({
    queryKey: ['workspaces'],
    queryFn: () => client.GET('/api/workspaces').then(({ data, error }) => {
      if (error) throw error;
      return data;
    }),
  });
}
```

---

## Migration Backlog

The following hand-coded clients must be replaced when they are next touched:

| Client | Operations | Notes |
|---|---|---|
| `WorkspaceApiClient.ts` | list, get, create, update, delete | Covered by generated client |
| `AuthorizationApiClient.ts` | validate, currentUser | Covered by generated client |
| `WorkflowAgentApiClient.ts` | run, status | Extend OpenAPI spec if not yet covered |
| `RiskApiClient.ts` | processDecision, list | Extend OpenAPI spec if not yet covered |
| `VersionApiClient.ts` | list, diff | Extend OpenAPI spec if not yet covered |
| `RequirementsApiClient.ts` | list, create, update | Extend OpenAPI spec if not yet covered |
| `TaskApiClient.ts` | list, create, update | Extend OpenAPI spec if not yet covered |
| `AISuggestionsApiClient.ts` | suggest | Use GraphQL (AI domain) or extend OpenAPI |
| `ArchitectureApiClient.ts` | get, update | Extend OpenAPI spec if not yet covered |
| `AuditApiClient.ts` | list | Extend OpenAPI spec if not yet covered |
| `AIServiceClient.ts` | various | Use GraphQL (AI domain) per API_SURFACE_CANONICALIZATION.md |

---

## Enforcement

- ESLint rule (to be added): no `new *ApiClient()` or `extends BaseDashboardApiClient` in new files.
- All new feature code must use the generated client or the GraphQL client.
- When adding a new API endpoint, update `docs/api/openapi.yaml` first, then regenerate with `pnpm generate:api`.

---

## Regeneration Command

```bash
cd products/yappc/frontend/web
pnpm generate:api   # runs openapi-typescript against docs/api/openapi.yaml
```
