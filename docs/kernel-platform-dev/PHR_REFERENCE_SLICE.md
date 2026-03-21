# PHR Reference Slice: Data Sharing & Consent

> **Status**: Day 19 Planning Pass — COMPLETE  
> **Date**: 2026-01-19  
> **Reference Slice**: Data Sharing & Consent Workflow

## 1. Slice Selection Rationale

The Data Sharing & Consent workflow is the ideal PHR reference slice because:

1. **Cross-cutting policy control** — consent touches every patient-data access path
2. **Generic + domain-specific bridge** — kernel audit/config/events + healthcare consent logic
3. **Compliance leverage** — HIPAA-grade access control at scale is PHR's core differentiator
4. **Validation breadth** — exercises event sourcing, multi-tenancy, RBAC, audit immutability

## 2. Generic Kernel Services Required

| Service | Kernel Module | Application in PHR |
|:--------|:-------------|:-------------------|
| Authentication & Multi-Tenancy (K-01 IAM) | iam | Actor resolution (patient, provider, caregiver, FCHV); tenant isolation |
| Authorization & Roles (K-01 IAM) | iam | RBAC with healthcare roles: PATIENT, PROVIDER, CAREGIVER, ADMIN, FCHV |
| Hierarchical Configuration (K-02) | config-engine | Consent policy templates (purpose-of-use taxonomy, emergency override rules) |
| Event Bus & Event Store (K-05) | event-store | Consent lifecycle events: ConsentGrantCreated, ConsentRevoked, ConsentExpired |
| Immutable Audit Trail (K-07) | audit-trail | HIPAA-grade audit for every consent decision; regulatory evidence |
| Resilience Patterns (K-18) | resilience-patterns | Circuit breaker for consent service; deny-rather-than-timeout degradation |
| Observability (K-06+) | observability | Consent decision tracing, anomaly detection |

## 3. Domain-Specific Services (Pack-Owned)

| Service | Responsibility | Pattern |
|:--------|:--------------|:--------|
| ConsentManagementService | Patient grants time-limited access; emergency override; revocation | State machine + policy evaluation |
| PatientRecordService | Patient identity, demographics, caregiver delegation | Root aggregate for multi-tenancy |
| FhirInteropService | Map PHR domain models to FHIR R4 resources | Adapter + schema validation |
| HealthcareDataClassification | Classify health data sensitivity (C1: public → C4: PHI) | Policy engine |
| EligibilityInsuranceService | Patient coverage check via openIMIS; consent-aware | Integration adapter |
| ClinicalDocumentManagement | OCR-assisted uploads, access filtering by consent | Document lifecycle |

## 4. Contract Surface Declarations

### 4.1 API Contracts (ContractFamily.API)

| Method | Path | Purpose |
|:-------|:-----|:--------|
| POST | `/api/v1/consent/grants` | Patient creates time-limited access grant |
| GET | `/api/v1/consent/grants` | List patient's active/revoked grants |
| DELETE | `/api/v1/consent/grants/{grantId}` | Revoke an access grant |
| GET | `/api/v1/patients/{patientId}/records` | Provider fetches patient record (consent-gated) |

### 4.2 Schema Contracts (ContractFamily.SCHEMA)

| Event | Compatibility | Format |
|:------|:-------------|:-------|
| ConsentGrantCreatedEvent | BACKWARD | JSON Schema v7 |
| ConsentRevokedEvent | BACKWARD | JSON Schema v7 |
| ConsentExpiredEvent | BACKWARD | JSON Schema v7 |
| ConsentAccessDecisionEvent | BACKWARD | JSON Schema v7 |

### 4.3 Analytics Contracts (ContractFamily.ANALYTICS)

| Metric | Type | Tags |
|:-------|:-----|:-----|
| `phr.consent.grants_created_total` | COUNTER | `tenant_id`, `grantee_type` |
| `phr.consent.grants_revoked_total` | COUNTER | `tenant_id`, `revocation_reason` |
| `phr.consent.access_decisions_total` | COUNTER | `tenant_id`, `allowed`, `purpose_of_use` |
| `phr.consent.emergency_overrides_total` | COUNTER | `tenant_id` |

### 4.4 Packaging Contract (ContractFamily.PACKAGING)

- **Pack ID**: `phr.consent`
- **Tier**: T1_CORE
- **Dependencies**: `kernel.iam`, `kernel.audit-trail`, `kernel.config-engine`, `kernel.event-store`
- **Lifecycle hooks**: INSTALL (consent schema migration), UPGRADE (policy template migration), HEALTH_CHECK (consent cache status)

## 5. Identified Gaps

| Gap | Severity | Resolution |
|:----|:---------|:----------|
| Healthcare domain pack does not exist | P0 | Create `products/app-platform/domain-packs/healthcare/` |
| Workflow orchestration embeds finance workflows | P0 | Extract finance-specific workflows to finance domain pack |
| PHR Java scaffolding is minimal | P1 | Implement core modules per phr_runtime_architecture.md |
| No FHIR R4 mapping/validation service | P2 | Create FhirInteropService with R4 schema validation |
| Emergency access override lacks observability | P2 | Integrate AI Governance for anomaly detection |

## 6. Validation Path

```
1. Generic services exist → checked via CANONICAL_CAPABILITY_MAPPING.md Days 11-14
2. Consent contracts declared → ContractRegistry + ContractValidator
3. HIPAA audit compliance → AuditTrailStore + HashChainService immutability
4. Multi-tenancy isolation → K-01 IAM + product-isolation.gradle
5. Schema compatibility → SchemaGovernanceValidator on consent events
```

## 7. Key File Paths

### PHR Source
- `products/phr/src/main/java/com/ghatana/phr/kernel/service/ConsentManagementService.java`
- `products/phr/src/main/java/com/ghatana/phr/kernel/extension/HealthcareConsentKernelExtension.java`
- `products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java`

### PHR Specification
- `products/phr/docs/03_architecture/phr_consent_service_interface_spec.md`
- `products/phr/docs/04_design_and_workflows/phr_workflow_consent.md`
- `products/phr/docs/04_design_and_workflows/phr_mvp_route_contract_pack.md`

### Kernel Services
- `products/app-platform/kernel/iam/` — K-01 IAM
- `products/app-platform/kernel/audit-trail/` — K-07 Audit
- `products/app-platform/kernel/config-engine/` — K-02 Config
- `products/app-platform/kernel/event-store/` — K-05 Event Bus
