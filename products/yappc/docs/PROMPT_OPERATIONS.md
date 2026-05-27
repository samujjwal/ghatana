# YAPPC Prompt Operations

This guide describes how prompt evaluation, promotion, rollback, weight rebalancing, and quarantine are operated in YAPPC today. The admin UI and backend API are intentionally narrow: operators can list prompt versions, inspect content and hashes, roll back active versions, and update variant weights. Experiment promotion flows can feed scores into the prompt lifecycle service.

## Runtime Model

| Concern | Current source | Operator surface | Evidence |
| --- | --- | --- | --- |
| Prompt version records | `yappc_prompt_versions` Data Cloud collection through `AdminPromptVersionController.VERSION_COLLECTION` | Admin prompt versions page lists versions, active state, author, hash, metrics, and weight. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/AdminPromptVersionController.java`; `products/yappc/frontend/web/src/components/admin/PromptVersionsPage.tsx` |
| Prompt lifecycle audit | `yappc_prompt_version_audit` through `AdminPromptVersionController.AUDIT_COLLECTION` and `PromptLifecycleService` audit events | Rollback and weight changes require an operator reason and write audit records. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/AdminPromptVersionController.java`; `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/ai/PromptLifecycleService.java` |
| Active prompt selection | `PromptTemplateRegistry.activeVersion` with latest-version fallback | Runtime services resolve active prompts by key and version. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/ai/PromptTemplateRegistry.java`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/ai/PromptTemplateRegistryTest.java` |
| A/B variant scoring | `PromptLifecycleService.recordScore` and `PromptTemplateRegistry.recordVariantScore` | A/B testing promotion can record winner scores before rebalance. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/ai/PromptLifecycleService.java`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/ai/PromptLifecycleServiceTest.java` |
| Weight rebalancing | `PATCH /api/admin/prompt-versions/weights` | Admin prompt versions page opens a weight dialog and persists bounded weights. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/AdminPromptVersionController.java`; `products/yappc/frontend/web/src/services/admin/promptVersioningApi.ts` |
| Rollback | `POST /api/admin/prompt-versions/{versionId}/rollback` | Admin prompt versions page requires a rollback reason and makes the target version active. | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/AdminPromptVersionControllerTest.java`; `products/yappc/frontend/web/src/components/admin/__tests__/PromptVersionsPage.test.tsx` |

## Evaluation

Evaluation starts with versioned prompt records that include prompt name, content, content hash, active state, weight, author, and optional metrics. Operators review those fields in `/admin/prompt-versions`. Automated experiment flows can record a bounded score for a prompt variant through `PromptLifecycleService.recordScore`, and the registry clamps scores to `[0, 1]` before accumulating samples.

Required evidence for an evaluation decision:

| Evidence | Required field or source | Validation |
| --- | --- | --- |
| Prompt identity | `promptName`, `id`, `promptVersion`, `contentHash` | `AdminPromptVersionControllerTest`, `PromptVersionsPage.test.tsx` |
| Prompt quality signal | `metrics`, experiment winner score, or manual weight decision | `PromptLifecycleServiceTest`, `ABTestingDashboardPage.test.tsx` |
| Operator or system actor | `actorId` or `updatedBy` | `AdminPromptVersionControllerTest` |
| Reason | Rollback and weight-change audit reason | `AdminPromptVersionControllerTest` |
| Correlation | `X-Correlation-ID` propagated to audit records when supplied | `AdminPromptVersionControllerTest` |

## Promotion

Direct prompt promotion is owned by `PromptLifecycleService.promote`, which updates the active version in `PromptTemplateRegistry` and emits a `prompt.lifecycle.promoted` audit event. The current admin UI does not expose a standalone Promote button; promotion normally enters through A/B testing winner promotion and prompt lifecycle service calls.

Promotion checklist:

1. Confirm the candidate prompt version exists in `yappc_prompt_versions`.
2. Confirm evaluation evidence shows the candidate is better than the active version for the same prompt key.
3. Call the service promotion path or A/B testing winner promotion with actor and reason.
4. Verify active version selection through `/api/admin/prompt-versions`.
5. Verify audit evidence contains previous version, target version, actor, reason, applied state, and timestamp.

## Rollback

Rollback is the primary visible safety control in `/admin/prompt-versions`. The operator selects an inactive version, provides a non-empty reason, and the backend:

| Step | Backend behavior | Evidence |
| --- | --- | --- |
| Validate request | Rejects missing tenant, missing version, missing reason, or invalid body. | `AdminPromptVersionControllerTest` |
| Register known versions | Registers Data Cloud prompt records into `PromptLifecycleService` before changing state. | `AdminPromptVersionController.rollbackExistingVersion` |
| Apply rollback | Calls `PromptLifecycleService.rollback(promptName, versionId, actorId, reason)`. | `PromptLifecycleServiceTest` |
| Persist state | Marks only the target version active and stores `previousActiveVersionId`. | `AdminPromptVersionControllerTest` |
| Audit | Writes `ROLLED_BACK` with actor, reason, correlation ID, previous active version, and applied state. | `AdminPromptVersionControllerTest` |

Post-rollback verification:

```powershell
./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.AdminPromptVersionControllerTest" --tests "com.ghatana.yappc.ai.PromptLifecycleServiceTest" --no-daemon
pnpm -C products/yappc/frontend/web exec vitest run src/components/admin/__tests__/PromptVersionsPage.test.tsx
```

## Weight Rebalancing

Weight rebalancing changes traffic allocation among variants. Manual admin weight updates are bounded to `[0, 1]` in the controller before persistence. Automated rebalance requires enough scored samples per variant, then updates registry weights in proportion to average score.

Operator rules:

| Rule | Rationale | Validation |
| --- | --- | --- |
| Keep the total effective allocation understandable to reviewers. | Manual weights are stored per version; reviewers need to reason about traffic share. | `PromptVersionsPage.test.tsx` |
| Record why the weight changed. | The controller writes `WEIGHTS_REBALANCED` audit records. | `AdminPromptVersionControllerTest` |
| Do not rebalance from empty or low-sample evidence. | Registry refuses automated rebalance until every variant has the minimum sample count. | `PromptLifecycleServiceTest` |
| Recheck active prompt selection after weights change. | Runtime selection uses weighted deterministic buckets. | `PromptTemplateRegistryTest` |

## Quarantine

There is no separate prompt quarantine endpoint in the current admin UI. Quarantine is operated as a controlled rollback and traffic removal procedure:

1. Roll back away from the suspect prompt version with a reason that starts with `quarantine:` and includes the incident or evidence ID.
2. Set the suspect version weight to `0` or the lowest allowed operational allocation through `/api/admin/prompt-versions/weights`.
3. Confirm the suspect version is inactive in `/admin/prompt-versions`.
4. Confirm the audit stream contains the rollback and weight-change reason.
5. Keep the prompt record in Data Cloud for investigation; do not delete the record as part of quarantine.

When a future dedicated quarantine status is added, it must be represented in `yappc_prompt_versions`, exposed in the admin UI, included in the route/OpenAPI contract, and covered by backend and frontend tests before this runbook changes.

## Admin UI Contract

The admin prompt page must continue to expose:

| UI behavior | Backend contract | Validation |
| --- | --- | --- |
| Loading, empty, and error states with correlation ID support | `GET /api/admin/prompt-versions` | `PromptVersionsPage.test.tsx` |
| Grouping by prompt name | List response `{ items, total }` | `PromptVersionsPage.test.tsx` |
| Content inspection with content hash | `PromptVersion.content` and `contentHash` | `PromptVersionsPage.test.tsx` |
| Rollback dialog requiring a reason | `POST /api/admin/prompt-versions/{versionId}/rollback` | `PromptVersionsPage.test.tsx`, `AdminPromptVersionControllerTest` |
| Weight dialog with bounded numeric value | `PATCH /api/admin/prompt-versions/weights` | `PromptVersionsPage.test.tsx`, `AdminPromptVersionControllerTest` |

## Change Rules

1. Add new prompt lifecycle operations to `PromptLifecycleService` first, then expose them through `AdminPromptVersionController` and OpenAPI/route manifest.
2. Keep prompt operation responses free of sensitive raw prompt content except on explicitly admin-only inspection routes.
3. Require actor, tenant, reason, timestamp, and correlation ID for every mutating prompt operation.
4. Update this guide and the focused backend/frontend tests whenever the admin UI adds a prompt operation.
