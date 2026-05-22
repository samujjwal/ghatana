# PHR Product Scorecard

- Current classification: enabled regulated business-product lifecycle pilot.
- Target classification for phase: executable healthcare web + backend API pilot with active gate evidence.
- Surfaces: `backend-api`, `web`.
- Adapters: `gradle-java-service`, `pnpm-vite-react`, `docker-buildx`, `compose-local`.
- Gates: `consent`, `pii-classification`, `audit-evidence`, `fhir-contract-validation`, `tenant-data-sovereignty`.
- Manifests: lifecycle plan/result, gate result manifest, artifact manifest, deployment manifest, verify health report, lifecycle health snapshot.
- Lifecycle execution allowed: true.
- Validation commands: `pnpm check:phr-lifecycle-readiness`, `pnpm plan:validate:phr`, `pnpm validate:phr`, `pnpm test:phr`, `pnpm build:phr`.
- Failing gaps: `package` is currently environment-blocked by local Docker Buildx health (`docker-buildx-builder-unavailable`); deploy/verify depend on packaged images.
- Boundary impact: product-local policy evidence only; Kernel consumes public gate-pack evidence through provider contracts.
- Duplication impact: no generic lifecycle runner added to product code.
- Security/privacy/o11y readiness: healthcare gates active, evidence-backed, and fail closed when evidence refs are missing.
- Next required work: restart/repair Docker Desktop Buildx builders, then rerun `pnpm package:phr`, `pnpm deploy:local:phr`, and `pnpm verify:local:phr`.
- Blocker severity: P0 for rollback enablement and package/deploy/verify refresh until healthcare post-rollback gates are promoted.
- Owner module: products/phr backend API, healthcare domain gates, web i18n/a11y, and lifecycle evidence.
- Ticket suggestion: PHR-PROD-001 complete post-rollback healthcare verification, then promote rollbackReadiness from target-partial with evidence.
