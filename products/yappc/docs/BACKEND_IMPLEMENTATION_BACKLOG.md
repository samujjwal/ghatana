# Backend Implementation Tracking: Privacy, Durability, and Cost (2026-04-27)

**Status**: OPEN — Backend tasks requiring server-side implementation  
**Owner**: YAPPC Backend / Platform Team  
**Source**: YAPPC_E2E_AUDIT_2026-04-27.md (C-Y2, C-Y14, C-Y15, F-Y021, F-Y023, F-Y028, F-Y034, F-Y046, F-Y058)

---

## 1. Workflow Cancel Durability (C-Y2 / F-Y006)

**Problem**: `WorkflowService.cancelWorkflow()` sets a flag in the DB but does not durably stop any in-flight
execution or propagate cancellation to AEP. If the process crashes after the flag is set but before AEP is
notified, the workflow remains running at AEP.

**Required implementation**:
1. On `CANCEL` command, write a `workflow_events` row with `type=CANCEL_REQUESTED` before returning.
2. A background worker polls for `CANCEL_REQUESTED` events and calls `AepOrchestrationClient.cancelRun(runId)`.
3. Only mark workflow `CANCELLED` in DB after AEP confirms cancellation.
4. Test: simulate crash after flag write; verify background worker resumes and AEP is called.

**Backend module**: `products/yappc/core/workflow/`  
**Test class**: `WorkflowCancellationDurabilityIT.java`  
**Acceptance**: R-MT-2 (cache invalidation follows cancel), round-trip cancel test passes

---

## 2. Cost Exposed Per Project + Per Tenant (C-Y14 / F-Y021)

**Problem**: `CostTrackingService` records per-call costs but there is no API route that aggregates them
per project or per tenant for the cockpit tile or admin billing surface.

**Required implementation**:
1. Add `GET /api/projects/{projectId}/ai-cost` → returns `{ totalTokens, estimatedUsd, breakdown }` (already added to OpenAPI spec).
2. Add `GET /api/admin/tenants/{tenantId}/ai-cost` → returns aggregate per-tenant cost (admin-gated route).
3. `CostTrackingService.getProjectCost(projectId)` must filter by tenant from `TenantContext`.
4. Test: `CostTrackingServiceTest` — assert that project cost query cannot return data from a different tenant.

**Backend module**: `products/yappc/core/ai/cost/`  
**Frontend**: Cockpit cost tile at `components/cockpit/CostTile.tsx` — already wired to `/api/projects/{projectId}/ai-cost`.  
**Acceptance**: R-MT-1 (cost tile renders real data), tenant isolation verified in test

---

## 3. SemanticCacheService Documentation + Hit Ratio Metric (F-Y023)

**Problem**: `SemanticCacheService` has no documented TTL policy, similarity threshold, or per-tenant scope.
No metric is exposed for cache hit ratio.

**Required implementation**:
1. Add Javadoc to `SemanticCacheService` specifying:
   - Default TTL: configured in `yappc.cache.semantic.ttl-minutes` (default: 60)
   - Similarity threshold: `yappc.cache.semantic.similarity-threshold` (default: 0.92)
   - Tenant scope: cache keys must include `tenantId` prefix
2. Expose `semantic_cache_hit_total` and `semantic_cache_miss_total` Prometheus counters.
3. Test: `SemanticCacheServiceTest` — verify TTL expiry and tenant key isolation.

**Backend module**: `products/yappc/core/ai/cache/`  
**Acceptance**: R-MT-2, metric scraped by Prometheus in integration test

---

## 4. Conversation Retention Policy (F-Y028)

**Problem**: No per-tenant conversation retention policy. Conversations persist indefinitely regardless of
tenant data residency requirements or user requests.

**Required implementation**:
1. Add `conversation_retention_days` field to tenant config (default: 365).
2. Add a scheduled job (`ConversationRetentionJob`) that purges conversations older than the configured period.
3. Audit log the purge with `actorId=SYSTEM` and `reason=RETENTION_POLICY`.
4. Expose `DELETE /api/tenants/{tenantId}/conversations` for admin-initiated purge.

**Backend module**: `products/yappc/core/conversation/`  
**Acceptance**: R-MT-4, retention job integration test passes

---

## 5. PII Classification on Logs (F-Y034)

**Problem**: Conversation content, prompt payloads, and cost data are logged without PII classification.
This violates Section 31 of copilot-instructions.md (schema-bound logging).

**Required implementation**:
1. Add `@PiiField` annotation to fields in `ConversationMessage`, `PromptPayload`, and `CostEntry`.
2. Register a Jackson filter that redacts `@PiiField` fields in log output (not API output).
3. Validate: no raw conversation content in `INFO`-level logs in production configuration.

**Backend module**: `platform/java/observability/` (annotation + filter), applied in `yappc/core/conversation/`  
**Acceptance**: R-MT-6, log output test confirms redaction

---

## 6. Export Classification (F-Y046)

**Problem**: The export endpoint returns all fields by default. PII and confidential fields should be
redacted unless the requesting user has explicit `export:full` permission.

**Required implementation**:
1. Add `ExportClassification` enum: `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `PII`.
2. Annotate entity fields with `@ExportClassification(CONFIDENTIAL)` or `@ExportClassification(PII)`.
3. The export serializer redacts fields above `PUBLIC` unless the caller has `export:classified` permission.
4. Integration test: export as a `VIEWER` role → confirm PII fields are absent.

**Backend module**: `products/yappc/core/export/`  
**Acceptance**: R-MT-15, integration test passes

---

## 7. Conversation Memory Delete-My-Data (C-Y15 / F-Y058)

**Problem**: Users have no way to delete their conversation memory from YAPPC. GDPR/CCPA requires a
delete-my-data path.

**Required implementation**:
1. Delegate to AEP GDPR/CCPA endpoint: `AepOrchestrationClient.deleteUserData(userId, tenantId)`.
2. YAPPC must also purge local `ConversationMessage` rows for the user.
3. Expose `DELETE /api/users/me/data` — authenticated route, no admin required.
4. Audit log: `actorId=userId`, `action=DELETE_MY_DATA`, `timestamp`.
5. Return `202 Accepted` with a `location` header pointing to a status endpoint.

**Backend module**: `products/yappc/core/conversation/` + AEP delegation  
**Acceptance**: R-MT-23, user delete round-trip test passes

---

## 8. YAPPC Core 18-Module Split ArchUnit Closure (F-Y038 / R-MT-10)

**Problem**: The YAPPC core 18-module split is described in the architecture but ArchUnit boundary tests
are only partially implemented.

**Required implementation**:
1. Verify each of the 18 modules has a corresponding `*ArchTest.java`.
2. For each module, test: no imports from `agents/*` into `scaffold`, no upward dependency violations.
3. CI must fail if any boundary test fails.

**Backend module**: `products/yappc/core/*/src/test/java/com/ghatana/yappc/*/`  
**Acceptance**: R-MT-10, all ArchUnit tests green

---

## Test Coverage Targets

| Task ID | Test Class | Module |
|---------|-----------|--------|
| C-Y2 | WorkflowCancellationDurabilityIT | yappc/core/workflow |
| C-Y14 | CostTrackingServiceTest (tenant isolation) | yappc/core/ai/cost |
| F-Y023 | SemanticCacheServiceTest | yappc/core/ai/cache |
| F-Y028 | ConversationRetentionJobIT | yappc/core/conversation |
| F-Y034 | PiiLogRedactionTest | platform/java/observability |
| F-Y046 | ExportClassificationIT | yappc/core/export |
| C-Y15/F-Y058 | DeleteMyDataIT | yappc/core/conversation |
| F-Y038 | YappcCoreArchTest (all 18 modules) | yappc/core/*/test |

---

## R-LT (Long-Term) Requirements

These are architectural aspirations tracked at the programme level, not sprint tasks:

| ID | Requirement | Owner |
|----|------------|-------|
| R-LT-1 | Eight-phase journey hardened end-to-end | YAPPC Platform Lead |
| R-LT-2 | AI-native by default with honest fallback | AI Platform Team |
| R-LT-3 | One cockpit, one services module, one persistence ownership map | Architecture Team |
| R-LT-4 | SOC2/GDPR by integration with AEP | Security + Compliance |
| R-LT-5 | Regional residency per tenant | Infra / Platform |

These feed into the YAPPC V2 roadmap and are tracked in the product backlog, not this audit TODO.

---

## SIMP-Y9 — REST ↔ GraphQL Overlap Removal

**Finding:** Two REST route groups overlap with GraphQL-canonical domains (per `docs/API_SURFACE_CANONICALIZATION.md`).

### 1. Approvals — remove `/api/v1/approvals/pending`

- **GraphQL canonical:** `approvalRequests(projectId, status)` in `requirements-approvals.graphql`
- **Action:** Before removing, audit all consumers of `GET /api/v1/approvals/pending`. If only the frontend uses it, migrate callers to the GraphQL query, then delete the REST route + controller handler.
- **Test class:** `ApprovalsRouteRemovalIT` — verify 404 after removal; verify GraphQL query returns equivalent data.

### 2. DevSecOps — remove `/api/devsecops/*`

- **GraphQL canonical:** `devsecops.graphql` schema (findings, dependencies, CVE links, phases)
- **Routes to remove:** `overview`, `ai-insights`, `anomaly-alerts`, `items`, `items/bulk-update`, `items/{id}`, `phases`, `phases/{phaseId}`
- **Action:** Migrate any non-GraphQL consumers (webhooks, server-side jobs, third-party integrations) to GraphQL. Delete the REST handler after migration.
- **Test class:** `DevSecOpsRouteRemovalIT` — verify 404 for all removed paths; verify GraphQL equivalents pass.
