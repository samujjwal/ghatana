# ADR-DC-011: OpenAPI-Generated Clients Only — No Placeholder Success Clients

**Status:** Accepted
**Date:** 2026-04-26
**Authors:** Data Cloud Architecture Team

## Context

The SDK module contained hand-written client methods that returned hardcoded fake success responses for endpoints that were not yet implemented. External developers integrating against these clients would receive `200 OK` with synthetic data, leading to production incidents when the real backend returned different shapes or errors.

## Decision

1. All SDK clients (Java, TypeScript, Python) MUST be generated from the canonical OpenAPI spec ONLY.
2. Any placeholder or stub method that returns fake success MUST be removed or clearly marked as `@Deprecated` with a body that throws `NotImplementedException`.
3. CI MUST run contract tests against generated clients on every build.
4. An OpenAPI drift check MUST run in CI — the build MUST fail if the implementation routes drift from the spec.
5. The UI client layer (`ui/src/api/*.service.ts`) MUST validate responses against Zod schemas derived from the OpenAPI contract.

## Consequences

- **Positive:** SDK truth matches backend truth. Integration failures are caught at build time, not in production.
- **Negative:** Requires maintaining OpenAPI spec discipline. Every route change must update the spec first.

## Related

- P0.6 in `data-cloud-implementation-tasks.md`
- `sdk/` module
- `OpenApiDriftDetectionTest.java`
