# TutorPutor Audit TODOs (Simple List)

- [x] P0 | Complexity: L | Term: Immediate (0-7 days) | Implement real LTI JWT signature verification in `libs/tutorputor-core/src/lti-auth-middleware.ts`.
- [x] P0 | Complexity: M | Term: Immediate (0-7 days) | Implement nonce replay prevention with Redis TTL in `libs/tutorputor-core/src/lti-auth-middleware.ts`.
- [x] P0 | Complexity: L | Term: Immediate (0-7 days) | Replace deterministic encryption key fallback with mandatory secure key source (KMS/Vault) in `services/tutorputor-platform/src/core/encryption/field-encryption.ts`.
- [x] P0 | Complexity: L | Term: Short (1-2 weeks) | Implement and validate encryption key rotation workflow in `services/tutorputor-platform/src/core/encryption/field-encryption.ts`.

- [x] P1 | Complexity: M | Term: Short (1-2 weeks) | Replace placeholder billing portal URL with real Stripe customer portal session flow in `services/tutorputor-platform/src/modules/integration/billing/service.ts`.
- [x] P1 | Complexity: L | Term: Short (1-2 weeks) | Replace placeholder tax compliance report structure with DB-backed transaction reporting in `services/tutorputor-platform/src/modules/payments/stripe-tax-service.ts`.
- [x] P1 | Complexity: M | Term: Short (1-2 weeks) | Persist payout records and payout-failure notifications in `services/tutorputor-platform/src/modules/payments/stripe-connect-service.ts`.
- [x] P1 | Complexity: M | Term: Short (1-2 weeks) | Implement Stripe portal session route behavior in `services/tutorputor-platform/src/modules/payments/routes.ts`.
- [x] P1 | Complexity: M | Term: Short (1-2 weeks) | Replace static `/metrics` response with real Prometheus metrics registry and tests in `services/tutorputor-platform/src/plugins/core.ts`.
- [x] P1 | Complexity: M | Term: Short (1-2 weeks) | Implement remediation queue schema + persistence in `services/tutorputor-platform/src/modules/content/evaluation/unified-content-evaluator.ts`.

- [x] P1 | Complexity: M | Term: Medium (2-4 weeks) | Add integration test infrastructure for deferred gRPC contract tests in `services/tutorputor-platform/src/workers/content/grpc/__tests__/contract.test.ts`.
- [x] P1 | Complexity: M | Term: Medium (2-4 weeks) | Add negative-path security tests (forged signature, replay nonce, expired token) for LTI auth.
- [x] P1 | Complexity: M | Term: Medium (2-4 weeks) | Add payment lifecycle integration tests (checkout, portal, tax report, payout failure) with realistic fixtures.
- [x] P1 | Complexity: M | Term: Medium (2-4 weeks) | Add observability contract tests (`/health`, `/ready`, `/metrics`) with failure mode assertions.

- [x] P2 | Complexity: S | Term: Medium (2-6 weeks) | Resolve schema-drift TODOs in `libs/tutorputor-core/src/db/optimization.ts` and remove dead TODOs.
- [x] P2 | Complexity: S | Term: Medium (2-6 weeks) | Fix seed-path TODO in `libs/tutorputor-core/src/db/seed.ts`.
- [x] P2 | Complexity: M | Term: Medium (2-6 weeks) | Expand gateway-specific contract/error-mapping tests in `apps/api-gateway`.
- [x] P2 | Complexity: M | Term: Medium (2-6 weeks) | Improve `apps/content-explorer` baseline tests (currently near-zero).
- [x] P2 | Complexity: M | Term: Long (1-2 quarters) | Increase simulation concurrency/soak test coverage in `libs/tutorputor-simulation`.
- [x] P2 | Complexity: M | Term: Long (1-2 quarters) | Increase AI module evaluation guardrail tests in `libs/tutorputor-ai` and content quality agent modules.
- [x] P2 | Complexity: M | Term: Long (1-2 quarters) | Raise contract backward/forward compatibility coverage in `contracts` package and API docs checks.
