# @tutorputor/validation

Shared Zod validation schemas for the TutorPutor platform. Provides a single authoritative source for request/response schema definitions used by the backend platform service, API gateway, and frontend apps.

## Purpose

- Eliminates duplicate Zod schema definitions across `tutorputor-platform`, `tutorputor-web`, and `tutorputor-admin`
- All schemas use `.strict()` to reject extra properties at API boundaries
- Branded types for IDs prevent cross-domain ID confusion at compile time
- Each schema module exports a `parse*` helper that throws `ZodError` (map to 422 in Fastify)

## Exports

| Subpath | Schemas |
|---|---|
| `@tutorputor/validation` | All schemas (barrel) |
| `@tutorputor/validation/common` | Branded IDs, pagination, enums, slug, URL primitives |
| `@tutorputor/validation/auth` | `LoginRequestSchema`, `RefreshTokenRequestSchema`, `CurrentUserResponseSchema` |
| `@tutorputor/validation/learning` | `ListModulesQuerySchema`, `EnrollRequestSchema`, `SubmitAttemptRequestSchema`, `LearningEventInputSchema` |
| `@tutorputor/validation/content-studio` | `CreateExperienceRequestSchema`, `ClaimSchema`, `PublishGateResultSchema`, `GenerateArtifactRequestSchema` |

## Usage in platform service (Fastify route)

```ts
import { parseLoginRequest } from "@tutorputor/validation/auth";

fastify.post("/api/v1/auth/login", async (request, reply) => {
  const body = parseLoginRequest(request.body);  // throws ZodError → mapped to 422
  const tokens = await authService.login(body);
  return reply.send(tokens);
});
```

## Usage in apps (boundary validation)

```ts
import { parseSubmitAttemptRequest } from "@tutorputor/validation/learning";

const attempt = parseSubmitAttemptRequest(formData);
await api.learning.submitAttempt(assessmentId, attempt);
```
