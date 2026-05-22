Below is a **single consolidated TODO list** covering all 47 production-readiness dimensions. It is grounded in the current `c240177a558fe2407419fae4f6ad489db130d0d2` snapshot, where release-readiness evidence passes but several checks are still posture/conformance-heavy rather than deep runtime-behavior proof.

---

# Single TODO list

1. **Create a canonical product capability traceability matrix.**
   **Where:** `config/product-capability-matrix.json`, `scripts/check-product-shape-capability-matrix.mjs`, `.kernel/evidence/product-release-readiness.json`
   **What:** Map every promised product capability to product owner, UI route, API route, Runtime Truth surface, implementation module, tests, docs, and release gate. Mark each capability as `Complete`, `Partial`, `Missing`, `Broken`, `Overbuilt`, or `Unknown`.
   **Covers:** Vision alignment, product coherence, feature completeness.
   **Acceptance:** Release fails if a required customer-facing capability is `Missing`, `Broken`, or exposed without implementation evidence.

2. **Add product-specific release scorecards for every affected product.**
   **Where:** `scripts/check-product-release-readiness.mjs`, `.github/workflows/product-release.yml`, `.kernel/evidence/product-release-readiness.<product>.json`
   **What:** Generate one release-readiness artifact per affected product instead of only one aggregate artifact. Include 47-dimension score, blocking gaps, skipped gates, and evidence paths.
   **Covers:** Product coherence, release readiness, overall production readiness.
   **Acceptance:** Every affected product has an independent release verdict.

3. **Strengthen affected-product resolution for shared/platform changes.**
   **Where:** `scripts/resolve-affected-products.mjs`, `.github/workflows/product-release.yml`, tests for affected-product resolution
   **What:** Ensure Kernel, design-system, product-shell, contracts, shared libraries, and platform changes trigger all dependent products.
   **Covers:** Product coherence, dependency hygiene, architecture boundaries.
   **Acceptance:** A shared dependency change cannot skip release checks for dependent products.

4. **Replace atomic-workflow posture proof with executable failure-injection tests.**
   **Where:** `scripts/check-atomic-workflow-proof.mjs`, `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandlerAtomicFailureTest.java`
   **What:** Test real mutation failure cases: entity write succeeds but event append fails; event append succeeds but audit fails; audit succeeds but outbox fails; idempotency write fails; retry after partial failure; rollback after crash.
   **Covers:** Runtime correctness, audit durability, idempotency, reliability.
   **Acceptance:** Critical mutations cannot partially commit without consistent event/audit/outbox/idempotency state.

5. **Add atomic workflow tests for all critical route families.**
   **Where:** `products/data-cloud/delivery/launcher/src/test/java/...`, `RouteSecurityRegistry.java`, `scripts/check-atomic-workflow-proof.mjs`
   **What:** Extend atomic proof beyond entity writes to governance policies, connector credential rotation, model promotion, settings key rotation, sovereign backup/restore, Action Plane execution cancel/retry/rollback, and compliance legal holds.
   **Covers:** End-to-end workflows, Action Plane, governance, compliance.
   **Acceptance:** Every `CRITICAL` mutating route has a corresponding failure-injection test or explicit waiver.

6. **Replace runtime-failure posture check with executable dependency-failure scenarios.**
   **Where:** `scripts/check-runtime-failure-injection.mjs`, `products/data-cloud/scripts/run-runtime-failure-injection.sh`
   **What:** Execute scenarios for Postgres down, ClickHouse down, OpenSearch down, S3 down, audit sink unavailable, policy engine unavailable, AI completion unavailable, queue saturation, timeout, and retry exhaustion.
   **Covers:** Runtime correctness, reliability, degraded mode, observability.
   **Acceptance:** Release includes `.kernel/evidence/runtime-failure-injection-report.json` with all required scenarios executed.

7. **Validate release evidence content, not just file presence.**
   **Where:** `.github/workflows/data-cloud-release.yml`, `scripts/validate-release-evidence.mjs`, `scripts/generate-release-maturity-summary.mjs`
   **What:** Parse smoke, backup, runtime profile, atomic workflow, a11y, i18n, AI governance, and OpenAPI evidence. Fail on stale, empty, partial, posture-only, or low-coverage evidence.
   **Covers:** Release readiness, documentation truthfulness, testing quality.
   **Acceptance:** Release summary cannot claim readiness from incomplete or stale evidence.

8. **Expand strict release gates to every production-intended product.**
   **Where:** `.github/workflows/product-release.yml`, `scripts/check-affected-product-strict-release-profile.mjs`, `scripts/check-product-release-readiness.mjs`
   **What:** Add strict release profiles for Data Cloud, Digital Marketing, Finance, Flashit, PHR, YAPPC, and future active products.
   **Covers:** Product coherence, deployment readiness, release readiness.
   **Acceptance:** No product can release without strict product-specific readiness evidence.

9. **Add domain invariant suites per product.**
   **Where:** `products/data-cloud/**/src/test`, `products/finance/**`, `products/phr/**`, `products/digital-marketing/**`, `scripts/check-product-domain-invariants.mjs`
   **What:** Add golden tests for domain calculations, lifecycle transitions, ownership, compliance rules, retention, and invalid states.
   **Covers:** Domain correctness.
   **Acceptance:** Every production product has domain invariants that fail on incorrect business behavior.

10. **Add data model ownership and lifecycle metadata.**
    **Where:** product schemas/models, `config/product-data-model-registry.json`, `scripts/check-data-model-lifecycle.mjs`
    **What:** For every persisted model, define owner, tenant scope, source of truth, retention, migration policy, audit strategy, privacy classification, and lifecycle.
    **Covers:** Data model correctness, privacy, governance.
    **Acceptance:** Release fails if a persisted model lacks ownership/lifecycle metadata.

11. **Add privacy behavior proof.**
    **Where:** `products/data-cloud/delivery/launcher/src/test`, `products/data-cloud/contracts/openapi/data-cloud.yaml`, `scripts/check-privacy-conformance.mjs`
    **What:** Test PII classification, redaction, data export, data deletion, retention, legal hold, and AI redaction before model calls.
    **Covers:** Privacy, compliance, AI governance.
    **Acceptance:** Sensitive data cannot be exported, processed by AI, or retained without policy/audit evidence.

12. **Add data-subject control workflow tests.**
    **Where:** Data Cloud governance/privacy handlers, sovereign profile routes, `scripts/check-privacy-conformance.mjs`
    **What:** Test data-subject export, delete, redaction verification, legal hold conflict, and audit evidence.
    **Covers:** Privacy, governance, audit.
    **Acceptance:** Data-subject workflows are executable and release-gated.

13. **Generate full route-role-scope authorization matrix.**
    **Where:** `RouteSecurityRegistry.java`, `scripts/check-route-entitlement-contracts.mjs`, `.kernel/evidence/route-entitlement-matrix.json`
    **What:** Generate matrix for route, method, sensitivity, required access, tenant scope, workspace scope, policy requirement, blocking audit, and role eligibility.
    **Covers:** Authorization/RBAC/ABAC/scope.
    **Acceptance:** Every protected route has server-side authorization proof.

14. **Add cross-product tenant isolation runtime tests.**
    **Where:** `integration-tests/cross-service-workflow`, `scripts/check-cross-product-interaction-boundaries.mjs`
    **What:** Test wrong tenant, missing tenant, mismatched workspace, cross-product contract version mismatch, and unauthorized product interaction.
    **Covers:** Tenant isolation, cross-product workflows.
    **Acceptance:** Cross-product interactions fail closed on tenant/scope mismatch.

15. **Strengthen event-envelope replay and bridge proof.**
    **Where:** Event handlers, event replay service, Action Plane bridge tests
    **What:** Prove append → query → get by offset → tail → replay → Action Plane handoff preserves eventId, tenantId, workspaceId, actor, policy context, classification, provenance, trace, correlation, causation, payload, timestamp, headers.
    **Covers:** Event correctness, automation, audit.
    **Acceptance:** No canonical event field is lost across any event path.

16. **Add Action Plane lifecycle tests.**
    **Where:** `products/data-cloud/planes/action/**`, launcher tests, integration tests
    **What:** Test action dry-run, approval, execute, cancel, retry, rollback, restore, checkpoint, policy denial, audit failure, idempotency, and HITL takeover.
    **Covers:** Action Plane / automation correctness.
    **Acceptance:** Automation cannot execute without identity, policy, audit, and rollback/retry semantics where applicable.

17. **Add human-in-the-loop control tests.**
    **Where:** Action Plane, autonomy handlers, learning review handlers, `scripts/check-hitl-control.mjs`
    **What:** Test approval queue, manual takeover, pause/resume automation, delegation, override expiry, break-glass reason, break-glass audit, and denied override.
    **Covers:** HITL / override control.
    **Acceptance:** Humans can interrupt, approve, reject, override, resume, and audit automation safely.

18. **Deepen AI governance from token checks to behavior tests.**
    **Where:** `scripts/check-ai-governance-conformance.mjs`, AI handlers, learning review handlers, model promotion tests
    **What:** Test model availability, fallback prevention in production, privacy redaction before model call, prompt/input/output provenance, cost budget, evaluation quality, HITL escalation, and audit of AI-generated actions.
    **Covers:** Implicit AI/ML maturity.
    **Acceptance:** AI actions are governed, observable, cost-controlled, and interruptible.

19. **Add AI cost and usage budgets.**
    **Where:** `config/product-cost-budgets.json`, `scripts/check-product-cost-budgets.mjs`, AI telemetry
    **What:** Track AI calls, tokens/requests, model cost, workflow cost, and budget thresholds by product and tenant.
    **Covers:** AI governance, cost efficiency.
    **Acceptance:** Release fails or requires waiver on AI cost regression.

20. **Add degraded-mode behavior matrix.**
    **Where:** Runtime Truth, UI state components, `scripts/check-degraded-mode-matrix.mjs`
    **What:** Define expected behavior for unavailable dependencies: fail closed, retry, hide feature, show degraded state, or allow read-only.
    **Covers:** Error handling, degraded mode, reliability.
    **Acceptance:** Every critical dependency failure has documented, tested behavior.

21. **Add UI degraded-state tests.**
    **Where:** `products/data-cloud/delivery/ui`, product UI test suites
    **What:** Test loading, empty, error, unauthorized, forbidden, degraded, unavailable, stale data, and retry states for key pages.
    **Covers:** UI/API/runtime coherence, UX, degraded mode.
    **Acceptance:** UI never shows dead actions or misleading success when runtime is degraded.

22. **Add standardized error-envelope enforcement.**
    **Where:** OpenAPI contracts, HTTP handlers, `scripts/check-openapi-release-quality.mjs`
    **What:** Require every API error to use a typed envelope with code, message, requestId, correlationId, details, tenant-safe metadata.
    **Covers:** Contract correctness, error handling, observability.
    **Acceptance:** No public route returns ad hoc error strings.

23. **Make OpenAPI release quality method-level and multi-contract.**
    **Where:** `scripts/check-openapi-release-quality.mjs`, `data-cloud.yaml`, `action-plane.yaml`, `config/openapi-generic-schema-waivers.json`
    **What:** Enforce typed 2xx schemas, typed error envelopes, idempotency docs for mutations, `x-ghatana-*` metadata, examples, and schema waivers across Data Cloud and Action Plane contracts.
    **Covers:** Contract correctness, Route/API correctness.
    **Acceptance:** Public APIs cannot ship with untyped or weak schemas without explicit waiver.

24. **Add OpenAPI breaking-change detection.**
    **Where:** `scripts/check-openapi-breaking-changes.mjs`, release workflow
    **What:** Compare current contracts to previous release baseline and block incompatible path, method, request, response, enum, and schema changes.
    **Covers:** Migration/deprecation hygiene, contract correctness.
    **Acceptance:** Breaking API changes require versioned migration or explicit approval.

25. **Add compatibility-route lifecycle enforcement.**
    **Where:** `route-compatibility-registry.yaml`, `scripts/check-deprecation-lifecycle.mjs`
    **What:** Require every compatibility route to have owner, reason, canonical replacement, deprecation date, removal date, Runtime Truth `legacyStatus`, and tests.
    **Covers:** Migration/deprecation hygiene.
    **Acceptance:** Deprecated routes cannot remain indefinitely.

26. **Add product-wide i18n extraction and missing-key checks.**
    **Where:** `scripts/extract-i18n-keys.mjs`, `scripts/check-i18n-missing-keys.mjs`, product UI folders
    **What:** Extract user-facing strings, fail on hardcoded strings, missing translation keys, untranslated errors, and non-localized validation messages.
    **Covers:** i18n/l10n readiness.
    **Acceptance:** Active web products have complete translation key coverage.

27. **Add pseudo-locale UI behavior tests.**
    **Where:** Data Cloud UI, Digital Marketing UI, PHR UI, Flashit UI, shared testing utilities
    **What:** Run core workflows in `en-XA` or equivalent pseudo-locale and assert layout does not break.
    **Covers:** i18n, UI/UX consistency.
    **Acceptance:** Pseudo-locale rendering passes for every active web product.

28. **Add locale formatting tests.**
    **Where:** product UI formatter utilities, `scripts/check-i18n-conformance.mjs`
    **What:** Test date, time, timezone, number, currency, percentage, file size, and relative time formatting.
    **Covers:** i18n/l10n readiness.
    **Acceptance:** No product formats locale-sensitive values manually.

29. **Deepen product a11y route matrix into behavior assertions.**
    **Where:** `scripts/check-product-a11y-route-matrix.mjs`, `platform/typescript/testing-a11y`, product e2e specs
    **What:** Require actual assertions for axe, keyboard navigation, focus trap, screen-reader labels, tables/grids, charts, dialogs, forms, toasts, and error announcements.
    **Covers:** Accessibility.
    **Acceptance:** A11y specs prove behavior, not just file/workflow presence.

30. **Add chart and data-grid accessibility tests.**
    **Where:** Data Cloud UI reports/analytics pages, shared chart/table components
    **What:** Test accessible names, summaries, keyboard navigation, focus order, aria descriptions, and alternative data views.
    **Covers:** Accessibility, UI/UX.
    **Acceptance:** Charts and dense data components meet WCAG expectations.

31. **Add product UX quality gate.**
    **Where:** `scripts/check-product-ux-quality.mjs`, product UI routes
    **What:** Check dead links, missing empty/error/loading states, unclear primary action, inconsistent button placement, table overflow, missing breadcrumbs/back behavior, and permission visibility issues.
    **Covers:** UI/UX simplicity and consistency.
    **Acceptance:** Product pages are simple, consistent, and actionable.

32. **Add command/search integration checks for product actions.**
    **Where:** product UI, shared shell, `scripts/check-product-ux-quality.mjs`
    **What:** Ensure important routes, reports, data views, and actions are discoverable through command/search.
    **Covers:** UI/UX, feature completeness.
    **Acceptance:** Critical user actions are discoverable without hunting through pages.

33. **Add product SLO budgets.**
    **Where:** `config/product-slo-budgets.json`, `scripts/check-product-slo-budgets.mjs`, performance workflows
    **What:** Define p50/p95/p99 latency, throughput, memory, queue depth, and background job runtime per product workflow.
    **Covers:** Performance.
    **Acceptance:** Release fails on SLO regression.

34. **Add multi-tenant load and backpressure tests.**
    **Where:** `products/data-cloud/scripts/run-durable-load-suite.sh`, new load test fixtures
    **What:** Test concurrent tenants, queue saturation, large query/export workloads, event streaming, backpressure behavior, and retry limits.
    **Covers:** Scalability, reliability.
    **Acceptance:** Product remains stable under expected production load.

35. **Add product cost budget gates.**
    **Where:** `config/product-cost-budgets.json`, `scripts/check-product-cost-budgets.mjs`, release scorecards
    **What:** Budget AI calls, query cost, export cost, stream cost, storage growth, and background job compute.
    **Covers:** Cost / operational efficiency.
    **Acceptance:** Cost regressions block release or require explicit waiver.

36. **Add observability dashboard and alert conformance.**
    **Where:** `scripts/check-observability-conformance.mjs`, dashboards/alerts config
    **What:** Require dashboards and alerts for latency, errors, dependency failures, queue depth, audit failures, AI usage, cost, smoke/backup failures.
    **Covers:** Observability.
    **Acceptance:** Every critical workflow has logs, metrics, traces, dashboard, and alert coverage.

37. **Add trace propagation tests across product interactions.**
    **Where:** cross-service integration tests, product interaction broker tests
    **What:** Prove requestId, correlationId, traceId, tenantId, workspaceId propagate across Kernel/product interactions.
    **Covers:** Observability, tenant isolation, cross-product workflows.
    **Acceptance:** Operators can trace a cross-product action end to end.

38. **Add plugin lifecycle runtime tests.**
    **Where:** `platform-kernel:kernel-plugin`, Data Cloud Action Plane plugin routes
    **What:** Test plugin install, validate, sandbox, enable, disable, upgrade, rollback, dependency conflict, permission denial, and audit.
    **Covers:** Extensibility/plugin model.
    **Acceptance:** Plugins can be governed safely through full lifecycle.

39. **Add shared-library overuse/semantic leakage checks.**
    **Where:** `scripts/check-platform-product-boundaries.mjs`, `scripts/check-kernel-boundaries.mjs`
    **What:** Fail if product-specific concepts leak into generic platform modules or if shared libraries are used as dumping grounds.
    **Covers:** Shared-library reuse, architecture boundaries, maintainability.
    **Acceptance:** Shared modules remain generic and product-neutral.

40. **Add maintainability and complexity budgets.**
    **Where:** `scripts/check-maintainability-budgets.mjs`, code quality gates
    **What:** Track file size, class size, method length, dependency fan-in/fan-out, duplicate logic, and generated-vs-manual registry boundaries.
    **Covers:** Simplicity and maintainability.
    **Acceptance:** High-complexity changes fail or require refactor plan.

41. **Separate generated run evidence from source-controlled baseline evidence.**
    **Where:** `.kernel/evidence/`, `release-evidence/`, evidence-generation scripts
    **What:** Avoid timestamp-only commits unless explicitly tagged as release-evidence refresh. Store run-specific evidence as CI artifacts by default.
    **Covers:** Documentation truthfulness, maintainability.
    **Acceptance:** Source history distinguishes implementation changes from regenerated evidence.

42. **Add evidence freshness validation.**
    **Where:** `scripts/validate-release-evidence.mjs`, release workflows
    **What:** Fail if evidence was generated against a different commit, branch, product scope, or release environment.
    **Covers:** Release readiness, documentation truthfulness.
    **Acceptance:** Release cannot reuse stale evidence.

43. **Upgrade implementation-plan coverage from boolean to maturity depth.**
    **Where:** `scripts/check-kernel-implementation-plan-coverage.mjs`, `.kernel/evidence/kernel-implementation-plan-progress.json`
    **What:** Replace `covered: true` with maturity depth levels: artifact exists, static check, behavior test, release evidence, runtime production evidence.
    **Covers:** All 47 dimensions.
    **Acceptance:** A dimension cannot pass as production-grade with only token/static coverage.

44. **Add per-dimension owners.**
    **Where:** `config/production-readiness-dimension-owners.json`, implementation-plan evidence
    **What:** Assign owner/team/module for each of the 47 dimensions and each affected product.
    **Covers:** Overall production readiness.
    **Acceptance:** Every dimension has an owner and escalation path.

45. **Add release score threshold policy.**
    **Where:** `scripts/generate-release-maturity-summary.mjs`, `scripts/validate-release-evidence.mjs`
    **What:** Fail production release if critical dimensions score below 4.0 or any product has unresolved P0/P1 blockers.
    **Covers:** Overall production readiness, world-class maturity.
    **Acceptance:** Scorecard determines ship/no-ship, not narrative text.

46. **Add disaster recovery RPO/RTO assertions.**
    **Where:** `products/data-cloud/scripts/run-backup-drill.sh`, release workflow, DR evidence
    **What:** Validate restore point, restore duration, data integrity, object storage accessibility, and documented RPO/RTO.
    **Covers:** Backup/restore/DR, deployment/ops readiness.
    **Acceptance:** Backup/restore passes with measurable RPO/RTO.

47. **Add secret rotation readiness tests.**
    **Where:** `scripts/check-secret-default-credentials.mjs`, release config validation, product runtime config
    **What:** Test credential rotation for API keys, DB credentials, ClickHouse, OpenSearch, S3, connector secrets, and AI provider tokens.
    **Covers:** Config/secrets management, security.
    **Acceptance:** Secrets can rotate without downtime or data leakage.

48. **Add environment-specific production profile tests.**
    **Where:** `scripts/check-data-cloud-release-runtime-profile.mjs`, Data Cloud runtime startup tests
    **What:** Test local, staging, production, and sovereign profiles with expected dependency requirements and fail-closed behavior.
    **Covers:** Deployment/ops readiness, runtime correctness.
    **Acceptance:** Production/sovereign cannot start with local/test providers.

49. **Add product-specific documentation truth checks.**
    **Where:** `scripts/check-doc-claims-evidence.mjs`, product docs
    **What:** Link every production-readiness claim to code, tests, release evidence, and Runtime Truth.
    **Covers:** Documentation truthfulness.
    **Acceptance:** Docs cannot claim feature completeness or production readiness without evidence.

50. **Add world-class UX/product review checklist.**
    **Where:** `release-evidence/world-class-product-review.md`, `scripts/check-world-class-product-review.mjs`
    **What:** Review each product for simplicity, consistency, speed, clarity, low cognitive load, discoverability, accessibility, localization, and automation quality.
    **Covers:** Overall world-class maturity.
    **Acceptance:** Product cannot be called world-class without completing the checklist.

---

## DEFER THE TIME CONSUMING TEST LATER IN THE SESSION
