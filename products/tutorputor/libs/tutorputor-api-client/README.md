# @tutorputor/api-client

Typed TutorPutor API client derived from `@tutorputor/contracts`. Replaces hand-rolled per-app fetch wrappers across the web, admin, and mobile surfaces.

## Purpose

- Single source of truth for how TutorPutor API routes are called
- Fully typed request and response bodies based on `@tutorputor/contracts`
- Canonical auth header injection via `@tutorputor/auth-client`
- Typed error hierarchy (`UnauthorizedError`, `ForbiddenError`, `NotFoundError`, …)
- Transparent 401 retry with token refresh callback
- Configurable timeout via `timeoutMs`

## Usage

```ts
import { createTutorPutorApiClient } from "@tutorputor/api-client";
import { LocalStorageAuthTokenStorage } from "@tutorputor/auth-client/storage";

const storage = new LocalStorageAuthTokenStorage();

const api = createTutorPutorApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? "",
  getAccessToken: async () => {
    const pair = await storage.retrieve();
    return pair?.accessToken ?? null;
  },
  onUnauthorized: async () => {
    const pair = await storage.retrieve();
    if (!pair) return null;
    const newPair = await api.auth.refresh(pair.refreshToken);
    await storage.store(newPair);
    return newPair.accessToken;
  },
});

// Login
const tokens = await api.auth.login({ email, password, tenantId });
await storage.store(tokens);

// Load dashboard
const dashboard = await api.learning.getDashboard();

// Author content
const experience = await api.contentStudio.createExperience({ title, domain, difficulty: "INTRO" });
```

## Route sub-clients

| Sub-client | Namespace | Key methods |
|---|---|---|
| `api.auth` | `/api/v1/auth` | `login`, `me`, `refresh`, `logout` |
| `api.learning` | `/api/v1/learning`, `/api/v1/modules`, `/api/v1/pathways`, `/api/v1/assessments` | `getDashboard`, `listModules`, `getModule`, `enroll`, `trackEvent` |
| `api.contentStudio` | `/api/content-studio` | `listExperiences`, `createExperience`, `getPublishGate`, `publishExperience`, `listClaims` |
| `api.analytics` | `/api/v1/analytics` | `getSummary`, `getAdvanced` |

## Error handling

```ts
import { UnauthorizedError, ForbiddenError } from "@tutorputor/api-client/errors";

try {
  const dashboard = await api.learning.getDashboard();
} catch (err) {
  if (err instanceof UnauthorizedError) {
    // redirect to login
  } else if (err instanceof ForbiddenError) {
    // show access denied
  }
}
```
