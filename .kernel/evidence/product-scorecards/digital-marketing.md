# Digital Marketing Product Scorecard

- Current classification: enabled business-product lifecycle pilot.
- Target classification for phase: executable web + backend API pilot with product-owned bridge conformance.
- Surfaces: `backend-api`, `web`.
- Adapters: `gradle-java-service`, `pnpm-vite-react`, `docker-buildx`, `compose-local`.
- Gates: lifecycle contract, boundary workflow, marketing consent, data minimization, integration and contract coverage, deployment readiness.
- Manifests: lifecycle plan/result, gate result manifest, artifact manifest, deployment manifest, verify health report, lifecycle health snapshot.
- Lifecycle execution allowed: true.
- Validation commands: `pnpm check:digital-marketing-lifecycle-pilot`, `pnpm plan:validate:digital-marketing`, `pnpm validate:digital-marketing`.
- Failing gaps: smoke lifecycle pilot check passes; full `package` is currently environment-blocked by local Docker Buildx health (`docker-buildx-builder-unavailable` / Docker EOF).
- Boundary impact: bridge remains product-owned and platform-contract-backed.
- Duplication impact: no product-owned generic lifecycle runtime.
- Security/privacy/o11y readiness: marketing consent and customer-data minimization gates declared in lifecycle.
- Next required work: restart/repair Docker Desktop Buildx builders, then rerun full package/deploy/verify evidence.
