Below is a **detailed TODO backlog** for `samujjwal/ghatana` → `products/data-cloud` + shared libraries at commit `eaa2b5dac6d592ef9d738e309393632efdabc1c4`.

Use this as the implementation checklist. It is organized by **priority**, with **what to change**, **where**, and **validation**.

---

# P0 — Production-Blocking TODOs

## P0-01 — Replace deprecated tenant resolution with canonical request context everywhere

**Where**

* `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java`
* `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/security/RequestContextResolver.java`
* All handlers under:

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/
```

Especially:

```text
SurfaceRegistryHandler.java
EntityCrudHandler.java
DataSourceRegistryHandler.java
```

**Current problem**

`RequestContextResolver` has the correct production behavior: tenant must come from authenticated identity in production/staging/sovereign profiles, and `X-Tenant-Id` / `tenantId` query parameters are rejected. However, handlers still call deprecated `requireTenantIdOrFail()` and return `X-Tenant-Id header is required`, which conflicts with the production security model.  

**What to change**

1. Create a canonical helper method in `HttpHandlerSupport`, for example:

```java
public RequestContext requireRequestContext(HttpRequest request)
```

or use existing:

```java
resolveRequestContextWithError(request)
requireTenantIdWithError(request)
```

2. Replace every usage of:

```java
http.requireTenantIdOrFail(request)
```

with canonical request-context resolution.

3. Pass the full request context into service/domain methods, not just `tenantId`.

4. Stop returning:

```text
X-Tenant-Id header is required
```

from production-protected routes.

5. Return correct status codes:

```text
401 when authentication is missing
403 when tenant spoofing or access mismatch occurs
400 only when tenant format is invalid
```

**Validation**

Add/extend tests:

```text
DataCloudSecurityFilterProductionProfileTest
RequestContextResolverTest
TenantIsolationTest
SurfaceRegistryHandlerSecurityTest
EntityCrudHandlerSecurityTest
DataSourceRegistryHandlerSecurityTest
```

Required cases:

* production rejects `X-Tenant-Id`
* production rejects `tenantId` query param
* local/test may allow fallback only when explicitly configured
* missing JWT/API key returns 401
* JWT tenant mismatch returns 403
* handler receives tenant/workspace/project/principal from canonical context

---

## P0-02 — Remove deprecated tenant helper after migration

**Where**

```text
HttpHandlerSupport.java
```

**What to change**

After all handlers are migrated, delete or restrict:

```java
@Deprecated
public String requireTenantIdOrFail(HttpRequest request)
```

If some test/local code still needs it, rename it clearly:

```java
resolveTenantIdForLocalTestOnly(...)
```

and make it package-private or test-fixture-only.

**Validation**

Run grep:

```bash
grep -R "requireTenantIdOrFail" products/data-cloud \
  --exclude-dir=build \
  --exclude-dir=node_modules
```

Expected result:

```text
No production usage.
```

---

## P0-03 — Make audit writer mandatory for sensitive and critical production routes

**Where**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java
```

**Current problem**

`DataCloudSecurityFilter` logs and drops audit events when `auditService == null`. For sensitive/critical routes this is not production safe. 

**What to change**

1. In production/staging/sovereign profile, reject sensitive/critical route execution when `auditService == null`.

2. Add explicit startup validation in runtime/profile validation so the app fails before serving traffic.

3. Return a structured error:

```json
{
  "error": {
    "code": "AUDIT_SERVICE_REQUIRED",
    "message": "Audit service is required for sensitive and critical routes in this profile."
  }
}
```

4. Keep audit-optional behavior only for local/test profiles.

**Validation**

Add tests:

```text
SensitiveMutationAuditTrailTest
DataCloudSecurityFilterProductionProfileTest
RuntimeProfileValidatorTest
```

Required cases:

* critical route fails when audit service is missing
* sensitive route fails when audit service is missing in production
* local profile can run with audit disabled only if explicitly allowed
* audit failure is observable and includes request ID / tenant / route

---

## P0-04 — Remove broad API_CLIENT and PROCESSOR role bypasses

**Where**

```text
DataCloudSecurityFilter.java
```

**Current problem**

`hasRequiredAccess()` treats `ADMIN`, `API_CLIENT`, and `PROCESSOR` as broad bypass roles. That means machine roles can satisfy admin-level operations unless restricted elsewhere. 

**What to change**

1. Replace broad role bypass logic with explicit action grants.

2. Introduce a permission model such as:

```java
PermissionDecision evaluate(
    RequestContext context,
    RouteAction action,
    ResourceScope scope
)
```

3. Allow `API_CLIENT` only when token/scopes include the required action:

```text
connector:create
connector:rotate_credentials
governance:policy:update
entity:delete
pipeline:execute
```

4. Allow `PROCESSOR` only for execution/runtime actions, not admin/settings/governance actions.

5. Keep `ADMIN` powerful, but still tenant-scoped and policy-audited.

**Validation**

Add tests:

```text
RouteActionAccessRegistryTest
DataCloudSecurityFilterJwtTest
DataCloudSecurityFilterMachinePrincipalTest
```

Required cases:

* `API_CLIENT` cannot rotate connector credentials without explicit scope
* `PROCESSOR` cannot create governance policy
* `PROCESSOR` cannot delete entities
* scoped machine token can perform only granted route actions
* admin route access still emits audit

---

## P0-05 — Generate route/action access metadata from OpenAPI instead of manual maps

**Where**

```text
EndpointSensitivity.java
RouteActionAccessRegistry.java
DataCloudRouterBuilder.java
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/contracts/openapi/action-plane.yaml
```

**Current problem**

Security sensitivity and access levels are partly prefix-based and partly manually listed. That can drift from registered routes and OpenAPI contracts.  

**What to change**

1. Add OpenAPI extensions to every route:

```yaml
x-ghatana-surface: data.entities.write
x-ghatana-sensitivity: SENSITIVE
x-ghatana-required-access: OPERATOR
x-ghatana-policy-required: true
x-ghatana-audit-required: true
x-ghatana-idempotency-required: true
```

2. Generate `RouteActionAccessRegistry` from OpenAPI.

3. Generate `EndpointSensitivity` metadata from OpenAPI.

4. Add CI check:

```bash
products/data-cloud/scripts/check-route-security-drift.sh
```

5. Fail CI when:

* router has a route not present in OpenAPI
* OpenAPI route lacks sensitivity metadata
* runtime registry differs from generated registry

**Validation**

Required tests:

```text
RouteActionAccessRegistryTest
EndpointSensitivityContractTest
DataCloudHttpServerRouteInventoryTest
OpenApiSecurityMetadataTest
```

---

## P0-06 — Make entity write lifecycle atomic

**Where**

```text
EntityCrudHandler.java
DataCloudClient.java
planes/data/entity/
planes/event/core/
planes/event/store/
planes/governance/core/
delivery/runtime-composition/
```

**Current problem**

Entity save supports an optional transaction manager. Without it, entity save → event append → websocket broadcast → semantic indexing runs non-atomically. Event rollback is noted as best-effort. 

**What to change**

1. Introduce a canonical write pipeline:

```text
validate request
resolve request context
authorize
apply policy
persist entity mutation
persist event
persist audit
persist outbox item for indexing/notifications
commit
async process outbox
```

2. Require transaction/outbox for production.

3. Move websocket broadcast and semantic indexing outside the write transaction via outbox events.

4. Remove direct best-effort rollback hooks from production code.

5. Persist event/audit/indexing correlation IDs.

**Validation**

Add golden tests:

```text
EntityWriteOutboxTest
EntityEventConsistencyTest
EntityWriteCrashRecoveryTest
SemanticIndexOutboxTest
```

Required cases:

* crash after entity save does not lose event/audit
* crash after event append does not duplicate entity
* retry with same idempotency key returns same response
* semantic index eventually catches up
* failed indexing does not corrupt entity/event truth

---

## P0-07 — Add idempotency to all mutating routes, not just entity writes

**Where**

```text
EntityCrudHandler.java
EventHandler.java
DataSourceRegistryHandler.java
DataLifecycleHandler.java
PipelineCheckpointHandler.java
WorkflowExecutionHandler.java
LearningHandler.java
AlertingHandler.java
AiAssistHandler.java
```

**Current problem**

Entity idempotency exists, but the code explicitly notes that pipelines, events, governance, and analytics still need idempotency or explicit non-idempotent documentation. 

**What to change**

1. Define a route-level idempotency contract.

2. For every `POST`, `PUT`, `DELETE`, and state-changing route, decide:

```text
idempotent-required
naturally-idempotent
non-idempotent-explicit
```

3. Require `X-Idempotency-Key` for unsafe production mutations where retries can duplicate work.

4. Store idempotency keys using durable store in production.

5. Scope idempotency by:

```text
tenantId
workspaceId
route action
resource ID
idempotency key
principal/client ID
```

6. Return `409` for conflicting reuse of same key with different payload hash.

**Validation**

Required tests:

```text
IdempotencyContractTest
EventAppendIdempotencyTest
ConnectorSyncIdempotencyTest
PipelineExecutionIdempotencyTest
GovernanceMutationIdempotencyTest
```

---

## P0-08 — Replace connector registry-only behavior with durable connector job runtime

**Where**

```text
DataSourceRegistryHandler.java
DataFabricConnector.java
extensions/connectors/
planes/event/
planes/data/
planes/context/
planes/governance/
```

**Current problem**

Connector registration and sync exist, but sync/test/schema actions rely on optional `DataFabricConnector`; missing fabric returns pending/queued responses rather than creating durable jobs with evidence. 

**What to change**

Create a connector runtime with these services:

```text
ConnectorRegistryService
ConnectorCredentialService
ConnectorSchemaDiscoveryService
ConnectorSyncJobService
ConnectorEvidenceService
ConnectorLineageService
ConnectorHealthService
```

Implement durable job state:

```text
PENDING
VALIDATING
RUNNING
PARTIAL_SUCCESS
FAILED
COMPLETED
CANCELLED
RETRYING
DEAD_LETTERED
```

Each sync must persist:

```text
jobId
tenantId
connectorId
source system
schema snapshot
source cursor / offset
row evidence
row-level errors
target collection
created entities
created events
lineage links
audit events
```

**Validation**

Required tests:

```text
ConnectorRegistrationE2ETest
ConnectorCredentialRedactionTest
ConnectorSyncJobLifecycleTest
ConnectorRetryIdempotencyTest
ConnectorSourceRowEvidenceTest
ConnectorTenantIsolationTest
```

---

## P0-09 — Fail closed when connector runtime is unavailable in production

**Where**

```text
DataSourceRegistryHandler.java
RuntimeProfileValidator.java
SurfaceRegistryHandler.java
```

**What to change**

1. In production/staging/sovereign profiles, connector test/sync/schema routes must return `503` if connector runtime is unavailable.

2. Do not return:

```text
pending
queued
fabricAvailable=false
```

as if it were a real accepted job unless a durable job was actually created.

3. Runtime Truth should mark connector surfaces:

```text
MISCONFIGURED
UNAVAILABLE
DEGRADED
```

depending on missing dependency.

**Validation**

Required tests:

```text
ConnectorRuntimeUnavailableProductionTest
RuntimeTruthConnectorSurfaceTest
```

---

## P0-10 — Make Runtime Truth a single typed canonical contract

**Where**

```text
SurfaceRegistryHandler.java
SurfaceRecord.java
SurfaceSchemaGenerator.java
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/delivery/ui/src/lib/capabilities.ts
products/data-cloud/delivery/ui/src/hooks/useCapabilityGate.ts
```

**Current problem**

`/api/v1/surfaces` returns raw capability map semantics, while `/api/v1/surfaces/typed` returns typed `SurfaceRecord` data. The README says `/api/v1/surfaces` is canonical.  

**What to change**

1. Make `GET /api/v1/surfaces` return typed surface records.

2. Include:

```text
surface id
state
owner
dependencies
dependency probe results
tenant scope
runtime profile
evidence
limitations
action gates
runtime posture metadata
generatedAt
```

3. Deprecate/remove `/api/v1/surfaces/typed`.

4. Remove “capability” naming from UI hooks and backend comments unless compatibility-only.

5. Remove the legacy constructor that defaults typed surface list to empty.

**Validation**

Required tests:

```text
SurfaceRegistryHandlerContractTest
RuntimeTruthSchemaTest
RuntimeTruthUiGateTest
SdkSurfaceContractTest
```

---

# P1 — Major Architecture / Correctness TODOs

## P1-01 — Remove duplicate legacy Action Plane route aliases

**Where**

```text
DataCloudRouterBuilder.java
RouteActionAccessRegistry.java
products/data-cloud/contracts/openapi/action-plane.yaml
products/data-cloud/contracts/openapi/data-cloud.yaml
```

**Current problem**

Router registers both legacy root routes and canonical `/api/v1/action/*` routes for pipelines, memory, learning, and executions. 

**What to change**

1. Keep canonical routes:

```text
/api/v1/action/pipelines
/api/v1/action/executions
/api/v1/action/learning
/api/v1/action/memory
```

2. Mark legacy routes as deprecated:

```text
/api/v1/pipelines
/api/v1/executions
/api/v1/learning
/api/v1/memory
```

3. Add config:

```text
DATACLOUD_ENABLE_LEGACY_ACTION_ROUTES=false
```

4. Default to disabled in production.

5. Emit deprecation warnings if enabled.

6. Remove `RouteActionAccessRegistry` normalization that rewrites `/api/v1/action/` back to `/api/v1/`.

**Validation**

Required tests:

```text
ActionPlaneRouteNamespaceTest
LegacyActionRouteDisabledProductionTest
OpenApiRouteInventoryTest
```

---

## P1-02 — Consolidate Action Plane UI client into Data Cloud product SDK

**Where**

```text
products/data-cloud/planes/action/ui/src/generated/aep-client.ts
products/data-cloud/planes/action/ui/src/hooks/useCapabilities.ts
products/data-cloud/delivery/ui/
products/data-cloud/delivery/sdk/
products/data-cloud/contracts/openapi/action-plane.yaml
```

**Current problem**

Search shows separate Action Plane UI generated client and capability hook under `planes/action/ui`, which conflicts with the one-product UI and SDK model.  

**What to change**

1. Generate Action Plane client from product-level contracts into:

```text
products/data-cloud/delivery/sdk
```

2. Update Data Cloud UI to consume SDK/adapters from `delivery/sdk`.

3. Move reusable Action UI components into `delivery/ui` or a shared UI package only if genuinely reusable.

4. Delete generated AEP client after migration.

**Validation**

Required tests:

```text
UnifiedSdkGenerationTest
ActionPlaneUiUsesProductSdkTest
ProductionBundleNoAepClientTest
```

---

## P1-03 — Remove production-visible UI mocks and deprecated route fixtures

**Where**

```text
products/data-cloud/delivery/ui/src/mocks/deprecatedRoutes.ts
products/data-cloud/delivery/ui/e2e/helpers/api-mocks.ts
```

**Current problem**

Mock/deprecated route files are discoverable in the product UI tree. E2E mocks may be valid, but production import boundaries must prove they are test-only.  

**What to change**

1. Delete `src/mocks/deprecatedRoutes.ts` if unused.

2. If still needed for tests, move to:

```text
products/data-cloud/delivery/ui/test-fixtures/
```

or:

```text
products/data-cloud/delivery/ui/e2e/fixtures/
```

3. Add eslint/import rule preventing production files from importing test fixtures.

4. Add build check:

```bash
grep -R "src/mocks\|api-mocks\|deprecatedRoutes" \
  products/data-cloud/delivery/ui/src
```

Expected: no production imports.

**Validation**

Required tests:

```text
ProductionBundleNoMocksTest
UiDeprecatedRouteImportGuardTest
```

---

## P1-04 — Move connector credential handling behind a dedicated secret service

**Where**

```text
DataSourceRegistryHandler.java
extensions/connectors/
planes/governance/core/
delivery/runtime-composition/
```

**What to change**

1. Keep rejecting raw credentials in API payloads.

2. Replace direct `secretRef` pass-through with a `ConnectorSecretService`.

3. Support:

```text
create secret reference
rotate secret reference
revoke secret reference
validate secret accessibility
audit secret access
redact secret metadata in responses
```

4. Ensure `secretRef` cannot leak cross-tenant.

**Validation**

Required tests:

```text
ConnectorRawCredentialRejectionTest
ConnectorSecretRefTenantIsolationTest
ConnectorSecretRotationAuditTest
```

---

## P1-05 — Add source-row evidence and lineage for connector ingestion

**Where**

```text
extensions/connectors/
planes/context/
planes/data/
planes/event/
DataSourceRegistryHandler.java
```

**What to change**

For every ingested record, persist:

```text
source connector ID
source object/table/topic
source row/document key
source offset/cursor/version
source extracted timestamp
schema version
mapping version
canonical entity ID
generated event ID
lineage edge ID
quality/trust state
```

**Validation**

Required tests:

```text
SourceRowToEntityLineageTest
ConnectorSchemaVersionLineageTest
IngestionEvidenceQueryTest
```

---

## P1-06 — Add schema discovery and mapping lifecycle

**Where**

```text
DataSourceRegistryHandler.java
extensions/connectors/
planes/data/entity/
products/data-cloud/contracts/
delivery/ui/
```

**What to change**

Implement lifecycle:

```text
DISCOVERED
DRAFT_MAPPING
VALIDATED
APPROVED
ACTIVE
DEPRECATED
RETIRED
```

Required APIs:

```text
GET /api/v1/connectors/{id}/schema
POST /api/v1/connectors/{id}/mappings
POST /api/v1/connectors/{id}/mappings/{mappingId}/validate
POST /api/v1/connectors/{id}/mappings/{mappingId}/approve
POST /api/v1/connectors/{id}/mappings/{mappingId}/activate
```

**Validation**

Required tests:

```text
SchemaDiscoveryMappingLifecycleTest
MappingValidationTest
MappingApprovalAuthorizationTest
```

---

## P1-07 — Make AI/automation outputs evidence-first and policy-gated

**Where**

```text
AiAssistHandler.java
LearningHandler.java
WorkflowExecutionHandler.java
planes/intelligence/
planes/action/
planes/governance/
delivery/ui/
```

**What to change**

For every AI/automation response, return:

```text
input scope
source evidence
confidence
risk level
policy decision
freshness
lineage/provenance
human review requirement
trace ID
audit ID
```

For low confidence or high risk:

```text
create review item
disable auto-apply
require approve/reject/escalate
```

**Validation**

Required tests:

```text
AiSuggestionEvidenceContractTest
LowConfidenceHitlGateTest
AutomationPolicyDenyTest
AiActionAuditTrailTest
```

---

## P1-08 — Add human override / delegation controls for automation

**Where**

```text
planes/action/
LearningHandler.java
WorkflowExecutionHandler.java
delivery/ui/
contracts/openapi/action-plane.yaml
```

**What to change**

Support action states:

```text
PROPOSED
AUTO_APPLIED
REVIEW_REQUIRED
INTERRUPTED_BY_HUMAN
TAKEN_OVER_BY_HUMAN
RESUMED_AUTOMATION
REJECTED
ROLLED_BACK
```

Add APIs:

```text
POST /api/v1/action/runs/{runId}/interrupt
POST /api/v1/action/runs/{runId}/take-over
POST /api/v1/action/runs/{runId}/resume
POST /api/v1/action/runs/{runId}/rollback
```

**Validation**

Required tests:

```text
HumanInterruptAutomationTest
TakeoverAuditTrailTest
ResumeAutomationPolicyTest
RollbackEvidenceTest
```

---

## P1-09 — Enforce product/shared-library dependency boundaries

**Where**

```text
products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md
products/data-cloud/
platform/
libs/
shared/
scripts/check-circular-deps.mjs
scripts/check-cross-workspace-deps.mjs
```

**What to change**

Implement architecture checks for the documented rules:

```text
Data/Event/Context/Governance must not import Action internals.
Contracts must not depend on runtime implementation.
UI must use generated clients/adapters, not backend internals.
Extensions must depend on contracts/SPI, not launcher internals.
Shared libraries must not contain Data Cloud product behavior.
```

The plane architecture explicitly defines allowed and forbidden dependency directions. 

**Validation**

Required tests/checks:

```text
DataCloudArchitectureTest
check-data-cloud-boundaries.mjs
check-shared-library-product-leakage.mjs
```

---

## P1-10 — Move Data Cloud-specific behavior out of shared/platform libraries

**Where**

Inspect and classify:

```text
platform/java/agent-core
platform/java/workflow
platform/java/messaging
platform/java/ai-integration
platform/java/data-governance
platform/contracts
```

The canonical plane architecture already identifies shared-platform review rules and migration candidates. 

**What to change**

For each module:

1. Keep generic interfaces in platform only if used by multiple unrelated products.
2. Move Data Cloud plane semantics into `products/data-cloud/planes/*`.
3. Move Action Plane runtime behavior into `products/data-cloud/planes/action/*`.
4. Move Data Cloud contracts into `products/data-cloud/contracts`.

**Validation**

Required checks:

```text
SharedLibraryReuseScorecardTest
ProductSpecificTypeLeakageTest
CrossProductDependencyTest
```

---

## P1-11 — Standardize canonical error envelope across all handlers

**Where**

```text
HttpHandlerSupport.java
ApiResponse.java
DataCloudSecurityFilter.java
all handlers
contracts/openapi/data-cloud.yaml
contracts/openapi/action-plane.yaml
```

**What to change**

Use one error envelope everywhere:

```json
{
  "error": {
    "code": "SURFACE_UNAVAILABLE",
    "message": "...",
    "correlationId": "...",
    "tenantId": "...",
    "surface": "...",
    "retryable": false,
    "details": {}
  }
}
```

Avoid ad hoc JSON strings in security filter responses.

**Validation**

Required tests:

```text
ErrorEnvelopeContractTest
SecurityFilterErrorEnvelopeTest
HandlerErrorEnvelopeTest
```

---

## P1-12 — Add route-level surface IDs

**Where**

```text
DataCloudRouterBuilder.java
SurfaceRegistryHandler.java
contracts/openapi/*.yaml
delivery/ui/
```

**What to change**

Every route should map to a surface ID:

```text
data.entities.read
data.entities.write
event.append
connectors.sync
action.pipelines.execute
governance.policy.update
ai.suggestions.apply
```

Surface IDs should drive:

```text
runtime truth
UI gating
authorization metadata
audit event type
OpenAPI metadata
SDK feature flags
```

**Validation**

Required tests:

```text
RouteSurfaceMappingTest
SurfaceIdOpenApiMetadataTest
UiSurfaceGateCoverageTest
```

---

# P2 — Cleanup, Documentation, and Consistency TODOs

## P2-01 — Fix contradictory canonical documentation around Action Plane paths

**Where**

```text
products/data-cloud/docs/product/02_data_cloud_unified_detailed_architecture.md
products/data-cloud/docs/product/03_data_cloud_unified_high_level_design.md
products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md
```

**Current problem**

Canonical docs correctly state AEP lives under `products/data-cloud/planes/action`, but some validation/migration language appears to forbid active `products/data-cloud/planes/action` paths, which contradicts the target architecture.  

**What to change**

1. Replace stale text like:

```text
No active products/data-cloud/planes/action after merge
```

with:

```text
No active legacy products/aep or old AEP product-boundary paths after merge.
```

2. Update stale-path grep scripts to search for old product paths, not the canonical Action Plane path.

3. Make `PLANE_ARCHITECTURE.md` the canonical reference.

**Validation**

Add:

```text
DocumentationTruthTest
StalePathDocumentationCheck
```

---

## P2-02 — Archive or delete top-level audit artifacts

**Where**

```text
dc-aep-analysis.md
code-audits/todo.md
docs/archive/data-cloud-audit-legacy/
```

**Current problem**

Top-level and archived audit/TODO artifacts are still discoverable and can confuse future audits.  

**What to change**

1. Delete if obsolete.
2. Otherwise move to:

```text
docs/archive/data-cloud-audit-legacy/
```

3. Add clear archive banner:

```text
Archived. Not canonical. Do not use for current audits.
```

4. Exclude archived audit docs from documentation truth checks.

**Validation**

```bash
grep -R "data_cloud_audit_report\|todo.md\|dc-aep-analysis" .
```

Expected:

* either absent
* or under `docs/archive/` only with archive banner

---

## P2-03 — Create canonical docs index and enforce it

**Where**

```text
products/data-cloud/docs/README.md
products/data-cloud/README.md
scripts/check-documentation-truth.mjs
```

**What to change**

Create a canonical docs matrix:

```text
Vision / market positioning
Plane architecture
Detailed architecture
High-level design
API/contracts
Connector strategy
Ingestion strategy
Retrieval/search/indexing strategy
AI/automation strategy
Governance/provenance/security
Operations/runbook
Testing strategy
Shared-library boundary guide
UI/design-system guide
```

Each doc should have:

```text
Status: Canonical / Draft / Archived
Owner:
Last reviewed:
Supersedes:
Superseded by:
```

**Validation**

Add CI check:

```bash
node scripts/check-documentation-truth.mjs
```

Fail if:

* canonical docs are missing
* archived docs are referenced as canonical
* duplicate canonical docs exist for same topic

---

## P2-04 — Rename remaining “capability” terminology where it means Runtime Truth

**Where**

```text
SurfaceRegistryHandler.java
delivery/ui/src/hooks/useCapabilityGate.ts
delivery/ui/src/lib/capabilities.ts
planes/action/ui/src/hooks/useCapabilities.ts
docs
tests
```

**What to change**

Use:

```text
surface
runtime truth
surface state
dependency probe
action gate
```

Avoid:

```text
capability
capability gate
capability registry
```

unless referring to explicitly deprecated compatibility code.

**Validation**

```bash
grep -R "capability\|Capability" products/data-cloud \
  --exclude-dir=build \
  --exclude-dir=node_modules
```

Every remaining usage must be justified as compatibility/deprecation.

---

## P2-05 — Remove dead or duplicate route definitions

**Where**

```text
DataCloudRouterBuilder.java
contracts/openapi/*.yaml
REST_API_DOCUMENTATION.md
```

**What to change**

1. Generate route inventory from router.
2. Generate route inventory from OpenAPI.
3. Compare.
4. Remove routes not in OpenAPI or add them properly to OpenAPI.
5. Remove duplicate legacy routes after compatibility window.

**Validation**

```text
RuntimeRouteOpenApiParityTest
DuplicateRouteRegistrationTest
```

---

## P2-06 — Add production bundle guard for frontend

**Where**

```text
products/data-cloud/delivery/ui/
```

**What to change**

Add check that production build does not contain:

```text
mock
fixture
deprecatedRoutes
fake
demo
placeholder
api-mocks
```

except allowed test-only folders.

**Validation**

```bash
pnpm build
node scripts/check-production-ui-bundle.mjs
```

---

## P2-07 — Consolidate Data Cloud UI docs

**Where**

```text
products/data-cloud/delivery/ui/ARCHITECTURE.md
products/data-cloud/delivery/ui/docs/DESIGN_ARCHITECTURE.md
products/data-cloud/delivery/ui/docs/guidelines/CODING.md
products/data-cloud/delivery/ui/docs/usage/USER_MANUAL.md
```

**What to change**

Create one UI canonical set:

```text
UI_ARCHITECTURE.md
DESIGN_SYSTEM_USAGE.md
USER_MANUAL.md
UI_TESTING_STRATEGY.md
```

Remove overlapping repeated content.

**Validation**

Documentation truth check should show one canonical doc per topic.

---

## P2-08 — Add shared-library reuse scorecard as CI gate

**Where**

```text
products/data-cloud/scripts/check-reuse-scorecard.sh
platform/
libs/
shared/
products/data-cloud/
```

**What to change**

For every shared library Data Cloud depends on, classify:

```text
Keep shared
Move to Data Cloud
Split generic/product-specific
Delete
Replace
```

Criteria:

```text
used by 3+ unrelated products
generic infrastructure
no Data Cloud plane semantics
no Action Plane runtime semantics
clear dependency direction
```

**Validation**

CI fails when product-specific code appears in shared library without explicit waiver.

---

# Final Consolidated Implementation Order

Use this exact order to avoid rework:

1. **Canonical request context migration**
2. **Audit/policy/durability fail-closed production gates**
3. **Role/action permission model**
4. **Contract-generated route/security registry**
5. **Typed Runtime Truth consolidation**
6. **Entity/event/audit outbox**
7. **Route-wide idempotency**
8. **Connector durable job runtime**
9. **Connector evidence/lineage/schema mapping**
10. **Action Plane route namespace cleanup**
11. **Data Cloud UI + SDK consolidation**
12. **Mock/deprecated UI cleanup**
13. **Shared-library boundary enforcement**
14. **Canonical docs cleanup**
15. **CI gates for route drift, docs drift, mock imports, stale paths, forbidden dependencies**

This sequence fixes root causes first, then removes duplicated/legacy paths so future audits do not keep rediscovering the same issues.
