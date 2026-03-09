# Library Spec – @ghatana/api

Fetch-based API client with middleware and retries for the Ghatana platform.

---

## 1. Purpose & Scope

- Provide a **shared HTTP client layer** (fetch-based) with:
  - Middleware (auth, logging, tracing, etc.).
  - Retries and error handling.
- Avoid per-app reimplementation of API wrappers.

From `package.json`:

- Name: `@ghatana/api`.
- Description: "Fetch-based API client with middleware and retries for the Ghatana platform".

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Expose a **typed API client** for common patterns (REST/GraphQL where applicable).
- Centralize:
  - Base URLs.
  - Auth header injection.
  - Retry logic and backoff.
  - Error normalization.

**Non-responsibilities:**

- No direct React components or hooks (hooks belong in app or higher-level libs).
- No domain-specific business logic (e.g., pipeline orchestration).

---

## 3. Consumers & Typical Usage

- App frontends (AEP UI, App Creator, TutorPutor) for calling backend APIs.
- Other libraries (e.g., domain-specific SDKs) that sit on top of the HTTP layer.

Conceptual example:

```ts
import { apiClient } from "@ghatana/api";

const res = await apiClient.get("/v1/pipelines");
```

---

## 4. Dependencies & Relationships

- Lives at the **boundary** between frontend and backend.
- Should integrate conceptually with:
  - `@ghatana/state` for cached/observed data.
  - `@ghatana/realtime` for realtime complements.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Ad-hoc fetch wrappers in apps:**

  - Some apps still define local API clients.  
    → Candidates to consolidate into `@ghatana/api`.

- **Lack of shared error contract:**
  - Ensure error shapes (status, code, message, details) are consistent and exported as types.

---

## 6. Enhancement Opportunities

1. **Middleware registry:**

   - Clearly documented middleware chain (auth, tracing, logging, retry, caching).

2. **Typed endpoints / SDKs:**

   - Generate or manually define higher-level endpoints for key domains (pipelines, patterns, modules).

3. **Observability hooks:**
   - Export structured events for metrics/tracing (e.g., start/end of API call) that can be wired into observability systems.

---

## 7. Usage Guidelines

- New HTTP calls should go through `@ghatana/api` instead of raw `fetch` where possible.
- Keep the public surface small and intentional; avoid exposing too many ad-hoc helpers.
