# PHR and Digital Marketing Lifecycle Classification

**Purpose**: Document current lifecycle execution status, evidence freshness, and blocker analysis for PHR and Digital Marketing pilot products.

**Last Updated**: 2026-05-20

---

## PHR Lifecycle Classification

### Product Identity
- **ProductId**: phr
- **Name**: Personal Health Records
- **Kind**: business-product
- **Lifecycle Status**: enabled
- **Lifecycle Execution Allowed**: true
- **Pilot Status**: Active healthcare pilot

### Current Lifecycle Facts

#### Surfaces
- **backend-api**: products/phr (Java service)
- **web**: products/phr/apps/web (React/Vite)

#### Toolchain Adapters
- **backend-api**: gradle-java-service
- **web**: pnpm-vite-react

#### Deployment Target
- **local**: compose-local

#### Rollback Readiness
- **Status**: target-partial
- **Classification**: target/partial
- **Reason Code**: phr-rollback-after-stable-deploy-verify
- **Required Before Enablement**:
  - stable-deployment-manifest-history
  - previous-artifact-selection-policy
  - healthcare-post-rollback-verification-gates
  - rollback-approval-contract

### Healthcare Gates (Required)

#### consent
- **Purpose**: Patient consent management and tracking
- **Status**: Active (declared in kernel-product.yaml)
- **Evidence Pack**: products/phr/lifecycle/gate-packs/consent.yaml
- **Implementation Required**: 
  - Prove access and sharing operations check active consent
  - Check consent revocation, expiry, purpose, actor, and patient scope
  - Emit consent validation evidence

#### pii-classification
- **Purpose**: PII data classification and handling
- **Status**: Active (declared in kernel-product.yaml)
- **Evidence Pack**: products/phr/lifecycle/gate-packs/pii-classification.yaml
- **Implementation Required**:
  - Classify patient-identifying fields
  - Enforce redaction/minimization
  - Emit classification evidence

#### audit-evidence
- **Purpose**: Audit trail and evidence logging
- **Status**: Active (declared in kernel-product.yaml)
- **Evidence Pack**: products/phr/lifecycle/gate-packs/audit-evidence.yaml
- **Implementation Required**:
  - Write immutable audit events for record access, sharing, consent change, break-glass, export
  - Make audit visible and queryable

#### fhir-contract-validation
- **Purpose**: FHIR R4 contract validation
- **Status**: Active (declared in kernel-product.yaml)
- **Evidence Pack**: products/phr/lifecycle/gate-packs/fhir-contract-validation.yaml
- **Implementation Required**:
  - Validate supported FHIR R4 resources against schema packs
  - Reject invalid resource shapes at API boundary

#### tenant-data-sovereignty
- **Purpose**: Tenant data sovereignty compliance
- **Status**: Active (declared in kernel-product.yaml)
- **Evidence Pack**: products/phr/lifecycle/gate-packs/tenant-data-sovereignty.yaml
- **Implementation Required**:
  - Prove tenant/workspace/patient data never crosses scope
  - Record storage and export location evidence

### Evidence Pack Status
- **Location**: .kernel/evidence/phr/phr-lifecycle-evidence-pack.json
- **Freshness**: Current (exists and populated)
- **Evidence References**:
  - products/phr/kernel-product.yaml
  - products/phr/schema-packs/schema-registry.yaml
  - products/phr/lifecycle/readiness-evidence.yaml
  - .kernel/evidence/phr/phr-lifecycle-evidence-pack.json

### Lifecycle Execution Status
- **Phases Supported**: dev, validate, test, build, package, deploy, verify
- **Rollback**: Blocked until healthcare post-rollback verification gates are implemented
- **Execution Mode**: Standard web API product profile
- **Provider Mode**: Bootstrap (file-backed providers)

### Blocker Analysis

#### Critical Blockers
1. **Healthcare Gates Not Evidence-Backed**
   - Gates are declared but not yet proven by real evidence
   - Need real FHIR validation, consent enforcement, PII classification, audit evidence, tenant sovereignty implementation
   - **Severity**: Critical
   - **Resolution**: Implement healthcare gate packs with real validation logic

2. **Rollback Disabled**
   - Rollback is intentionally target-partial until stable deployment manifest history exists
   - Missing previous-artifact selection policy
   - Missing healthcare post-rollback verification gates
   - Missing rollback approval contract
   - **Severity**: High
   - **Resolution**: Implement rollback prerequisites before enabling

#### Development Gaps
1. **Product Feature Completeness**
   - Lifecycle evidence proves platform smoke execution
   - Does not prove complete healthcare behavior
   - Required: patient profile, record summary, encounters, medications, allergies, conditions, labs, immunizations, documents, care team, consent, sharing authorization, audit access history, FHIR R4 validation, PII classification, tenant data sovereignty evidence, role-based privacy controls
   - **Severity**: High
   - **Resolution**: Complete PHR healthcare domain implementation

### Next Required Work
- Keep healthcare gate packs evidence-backed across validate, build, and deploy phases
- Keep generated lifecycle evidence packs current for validate, test, build, package, deploy, and verify
- Enable PHR rollback only after stable deployment manifest history, artifact selection policy, healthcare post-rollback gates, and approval contract are in place

---

## Digital Marketing Lifecycle Classification

### Product Identity
- **ProductId**: digital-marketing
- **Name**: Digital Marketing Operating System
- **Kind**: business-product
- **Lifecycle Status**: enabled
- **Lifecycle Execution Allowed**: true
- **Pilot Status**: Active lifecycle pilot (validated)

### Current Lifecycle Facts

#### Surfaces
- **backend-api**: products/digital-marketing/dm-api (Java service)
- **web**: products/digital-marketing/ui (React/Vite)

#### Toolchain Adapters
- **backend-api**: gradle-java-service
- **web**: pnpm-vite-react

#### Deployment Target
- **local**: compose-local

#### Rollback Readiness
- **Status**: Enabled
- **Strategy**: previous-artifact
- **Support**: Full deploy/promote/rollback flows supported

### Lifecycle Phases Supported
- **dev**: Development mode with parallel surface execution
- **validate**: Registry, manifest, lifecycle contract, bridge compliance, boundary workflow coverage, consent boundary, data minimization gates
- **test**: Unit, integration, contract test coverage gates
- **build**: Backend check, web route contract, typecheck, bundle budget, a11y readiness, i18n readiness, container image integrity gates
- **package**: Container scan, image vulnerability scan gates
- **deploy**: Deployment readiness, environment configuration validation gates
- **promote**: Promotion readiness, target environment validation, data migration validation gates
- **rollback**: Rollback readiness, rollback impact analysis gates

### Bridge Conformance
- **Status**: true
- **Bridge Adapters**: 
  - products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java
  - Tests: DigitalMarketingKernelAdapterImplTest.java, NotificationRetryAndDlqTest.java

### Evidence Pack Status
- **Location**: .kernel/evidence/digital-marketing/digital-marketing-lifecycle-evidence-pack.json
- **Freshness**: Current (exists and populated)
- **Evidence References**: Referenced in canonical-product-registry.json

### Lifecycle Execution Status
- **Phases Supported**: dev, validate, test, build, package, deploy, promote, rollback, verify
- **Rollback**: Enabled with previous-artifact strategy
- **Execution Mode**: Standard web API product profile
- **Provider Mode**: Bootstrap (file-backed providers)

### Plugin Bindings
- **audit**: kernel.audit (required)
- **observability**: kernel.observability (required)
- **data-access**: kernel.data-access (required)
- **identity-entitlement**: kernel.identity-entitlement (required)
- **security**: kernel.security (required)
- **preview-security**: kernel.preview-security (optional)

### Policy Packs
- **security**: 
  - kernel://policy-packs/web-api-security-baseline
  - kernel://policy-packs/container-image-integrity
- **privacy**:
  - kernel://policy-packs/non-regulated-customer-data-minimization
  - kernel://policy-packs/marketing-consent-boundary

### Blocker Analysis

#### Development Gaps
1. **Product Feature Completeness**
   - Lifecycle evidence proves platform smoke execution
   - Does not prove complete Digital Marketing product behavior
   - Required: customer/account management, campaign lifecycle, lead/conversion tracking, audience/segment management, channel configuration, Google Ads connector readiness, reporting dashboards, notification retry/DLQ, operator/admin workflows, API contracts, UI route completeness
   - **Severity**: High
   - **Resolution**: Complete Digital Marketing domain implementation

2. **Google Ads Connector**
   - Connector module exists
   - Must not fake success
   - Required: typed remote adapter + readiness states (NOT_READY, AUTH_FAILED, RATE_LIMITED, REMOTE_VALIDATION_FAILED, PUBLISH_FAILED, ENVIRONMENT_BLOCKED)
   - **Severity**: High
   - **Resolution**: Implement real connector with proper error states

3. **Reporting**
   - Route contracts exist
   - Required: real KPI/report queries backed by domain data
   - Required: analytics dashboards
   - **Severity**: High
   - **Resolution**: Implement reporting with real data queries

#### Integration Gaps
1. **Approval Workflows**
   - Approval configuration exists in kernel-product.yaml
   - Required: real approval workflow implementation
   - Required: notification retry/DLQ
   - **Severity**: Medium
   - **Resolution**: Implement approval and notification infrastructure

2. **Operator/Admin Workflows**
   - Required: operator workflows for campaign management
   - Required: admin configuration UI
   - **Severity**: Medium
   - **Resolution**: Implement operator and admin interfaces

### Next Required Work
- Complete DMOS pilot product, not just lifecycle smoke
- Implement campaign state machine with real domain logic
- Implement Google Ads connector with real remote adapter and proper error states
- Implement reporting with real KPI queries backed by domain data
- Implement approval workflows with notification retry/DLQ
- Implement operator/admin workflows and UI
- Ensure API contracts and UI routes match
- Ensure lifecycle phases produce versioned evidence
- Ensure Studio shows lifecycle/artifact/deployment/health truth

---

## Comparison Summary

| Aspect | PHR | Digital Marketing |
|--------|-----|-------------------|
| Lifecycle Status | Enabled (healthcare pilot) | Enabled (validated pilot) |
| Rollback | target-partial (blocked) | Enabled (previous-artifact) |
| Gates | 5 healthcare gates | Domain-specific gates per phase |
| Bridge Conformance | Not applicable | True (kernel bridge implemented) |
| Evidence Pack | Current | Current |
| Provider Mode | Bootstrap | Bootstrap |
| Platform Mode | Not yet ready | Not yet ready |
| Feature Completeness | Healthcare domain gaps | Marketing domain gaps |
| Critical Blockers | Healthcare gates not evidence-backed, rollback disabled | Feature completeness, connector, reporting |

---

## Platform Provider Mode Status

Both PHR and Digital Marketing currently operate in **bootstrap mode** (file-backed providers). Platform mode (Data Cloud-backed providers) is not yet ready for either product.

### Platform Mode Requirements
- Data Cloud provider health must be real
- Provider negotiation must be explicit
- Fail-closed behavior when Data Cloud unavailable
- Runtime truth provider integration
- Event provider integration
- Artifact provider integration
- Health provider integration

### Blockers to Platform Mode
1. Data Cloud provider boundary must be enforced
2. Kernel must not import Data Cloud plane internals
3. Data Cloud route/runtime-truth drift must be checked
4. Provider health matrix must be implemented
5. Platform mode negotiation logic must be added

---

## Evidence Freshness Convention

### Storage Layout
```
.kernel/evidence/<product>/<product>-lifecycle-evidence-pack.json
.kernel/evidence/<product>/<runId>/**
.kernel/evidence/platform/**
```

### Retention Rules
- Keep latest evidence pointer
- Retain release evidence permanently
- Clean ephemeral dev evidence by age/size
- Redact absolute local paths
- Redact secrets/tenant-sensitive data

### Freshness Status
- **PHR**: Evidence pack exists and is current
- **Digital Marketing**: Evidence pack exists and is current
- **Platform**: No platform-level evidence yet

---

## Lifecycle Truth Classification

### PHR Lifecycle Truth
- **Execution Allowed**: true
- **Mode**: Standard web API product profile
- **Phases**: dev, validate, test, build, package, deploy, verify
- **Rollback**: target-partial (blocked)
- **Provider Mode**: Bootstrap
- **Evidence**: Current
- **Blockers**: Healthcare gates not evidence-backed, rollback disabled

### Digital Marketing Lifecycle Truth
- **Execution Allowed**: true
- **Mode**: Standard web API product profile
- **Phases**: dev, validate, test, build, package, deploy, promote, rollback, verify
- **Rollback**: Enabled
- **Provider Mode**: Bootstrap
- **Evidence**: Current
- **Blockers**: Feature completeness, connector, reporting

---

## Conclusion

Both PHR and Digital Marketing are enabled lifecycle pilots with current evidence packs. However:

1. **PHR**: Healthcare gates are declared but not yet evidence-backed. Rollback is intentionally blocked until healthcare-specific prerequisites are met. Product feature completeness is a development gap.

2. **Digital Marketing**: Lifecycle is more mature with full phase support and rollback enabled. However, product feature completeness (campaign workflows, Google Ads connector, reporting) is a development gap.

Both products operate in bootstrap mode. Platform mode (Data Cloud-backed providers) requires additional infrastructure work including provider boundary enforcement, health matrix implementation, and fail-closed behavior.
