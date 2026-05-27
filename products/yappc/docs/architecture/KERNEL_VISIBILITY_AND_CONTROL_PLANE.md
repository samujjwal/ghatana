# Kernel Visibility and Control Plane

> **Status**: Partially Production-Backed
> 
> This document defines the YAPPC → Kernel visibility and control plane boundaries as implemented and operational.

## Purpose

This document defines how YAPPC serves as the **visibility, intelligence, health, and control-plane layer** over Kernel and other platform components. YAPPC provides visibility into Kernel lifecycle execution without duplicating Kernel logic or mutating Kernel internals.

**Core Principle:** YAPPC creates product/app intent, generates ProductUnitIntent for Kernel when the target is lifecycle-managed, visualizes Kernel lifecycle health, explains gates/artifacts/deployment state, and recommends next actions. YAPPC does not directly execute Kernel lifecycle phases or mutate Kernel registry files.

---

## 2. YAPPC as Visibility/Control-Plane Layer

YAPPC provides:

- **Intent Creation**: Capture product/app goals and generate ProductUnitIntent for lifecycle-governed ProductUnits
- **Health Visualization**: Display Kernel lifecycle health, gate status, artifact state, deployment status
- **Intelligence**: Explain gate failures, recommend next actions, surface agent governance health
- **Control Plane**: Initiate Kernel lifecycle runs through public contracts (CLI/API), observe results
- **Learning**: Learn from Kernel lifecycle events to improve recommendations and intent generation

**What YAPPC Does:**
- Generate ProductUnitIntent from YAPPC project configuration
- Display Kernel lifecycle health snapshots and manifests
- Show gate failures with explanations and recommended actions
- Visualize artifact deployment states and verification results
- Display agent governance health and learning evidence
- Recommend next actions based on Kernel lifecycle state

**What YAPPC Does NOT Do:**
- Directly mutate Kernel registry files (e.g., `config/canonical-product-registry.json`)
- Execute Kernel Product Lifecycle phases internally
- Parse private Kernel logs as truth
- Duplicate Kernel phase enums or gate execution logic
- Add product-specific business logic into visibility layer

---

## 3. Kernel as Execution Truth Layer

Kernel is the single source of truth for:
- Product Lifecycle execution (dev → validate → test → build → package → deploy → verify)
- Gate evaluation and enforcement
- Artifact production and fingerprinting
- Deployment target management
- Agent governance and learning state
- Preview security evaluation

YAPPC consumes Kernel outputs through:
- Public events (lifecycle phase transitions, gate results, artifact production)
- Health snapshots and manifests (`.kernel/out/products/<productId>/**`)
- Public APIs (Kernel CLI, REST endpoints when available)
- ProductUnitIntent contract (for creation/update requests)

---

## 4. Data Inputs

### 4.1 Local Development Provider

YAPPC can read Kernel outputs from the local filesystem through the development provider:

```
.kernel/out/products/<productId>/
├── lifecycle-plan.json          # Planned lifecycle run
├── lifecycle-result.json        # Execution result
├── artifacts.json               # Produced artifacts with fingerprints
├── deployment.json              # Deployment state
├── health-snapshot.json         # Current health status
└── gates.json                   # Gate evaluation results
```

### 4.2 Production Provider

Production reads must flow through `KernelLifecycleTruthSource` implementations backed by durable platform truth:
- Data Cloud lifecycle/run/read-model records
- Event Cloud streams for real-time lifecycle changes
- Kernel health APIs for current state when available
- ProductUnit registry metadata

The filesystem provider is valid only for local development and deterministic test fixtures. Production profile wiring must inject a Data Cloud/Event Cloud provider and must not treat `.kernel/out/products/**` as multi-tenant runtime truth.

### 4.3 Implementation State

| Component | State | Notes |
| --- | --- | --- |
| ProductUnitIntent export | Production-backed | Export validates workspace, project, lifecycle profile, provider, and surfaces through a shared registry. |
| ProductUnitIntent validation | Production-backed | Provider/profile/surface validation uses `ProductUnitKernelContractRegistry`. |
| CLI Kernel handoff | Production-backed | Kernel ProductUnit creation requires explicit workspace ID, project ID, lifecycle profile, and surfaces. |
| Kernel filesystem ingest | Local-development provider | Managed executor lifecycle is supported; filesystem reads are not production truth. |
| Kernel Data Cloud ingest | Production provider implemented | `DataCloudKernelLifecycleTruthSource` reads typed `kernel_lifecycle_truth` records; production profile guards reject local filesystem truth. |

---

## 5. Health Views

### 5.1 ProductUnit Health Summary

- ProductUnit ID, name, kind, owner
- Current lifecycle phase and status
- Overall health indicator (healthy/degraded/failed)
- Last lifecycle run timestamp
- Active gate failures count
- Deployment health status

### 5.2 Lifecycle Run Status

- Run ID, timestamp, triggered by
- Phase-by-phase execution status
- Each phase: status (running/succeeded/failed/blocked), duration
- Gate results per phase
- Artifact production per phase

### 5.3 Gate Failures

- Gate name, phase, required/optional
- Failure reason and evidence
- Correct owner/team
- Recommended next action
- Link to evidence (logs, artifacts)

### 5.4 Artifact/Deployment Health

- Artifact type, surface, path/image
- Fingerprint/digest, produced by step
- Created time, deployment link
- Deployment target, environment
- Health checks status, last verification
- Rollback status if applicable

### 5.5 Agent Governance Health

- Agent ID, name, learning level
- Governance state (ready/requires approval/requires verification/obsolete/quarantined)
- Learning evidence status
- Policy blocks if any
- Promotion queue status

### 5.6 Preview Security Health

- Preview token scope and status
- Trust level (trusted/semi-trusted/untrusted)
- Acknowledgement requirements
- Token scope mismatches
- Expiration status

---

## 6. ProductUnitIntent Flow

```
YAPPC Creator (Generate/Run phases)
  ↓
ProductUnitIntentExporter (converts YAPPC project to ProductUnitIntent)
  ↓
ProductUnitIntentValidationService (validates intent structure and rules)
  ↓
Write to: targetPath/.yappc/product-unit-intent.yaml
  ↓
User executes: pnpm kernel product create --from-intent <file>
  ↓
Kernel executes Product Lifecycle (plan/validate/test/build/package/deploy/verify)
  ↓
Kernel emits events and writes manifests/snapshots
  ↓
YAPPC KernelLifecycleEventIngestService ingests into read model
  ↓
YAPPC KernelHealthSnapshotService provides health views
  ↓
YAPPC KernelActionRecommendationService suggests next actions
```

**ProductUnitIntent includes:**
- Schema version (1.0.0)
- Intent ID (unique)
- Producer (yappc, with project/workspace metadata)
- Target providers (registry, source)
- ProductUnit draft (id, name, kind, surfaces, lifecycle profile, metadata)
- Optional requested lifecycle configuration

---

## 7. Forbidden Direct Integrations

YAPPC must NOT:

1. **Mutate Kernel registry files directly**
   - Forbidden: Writing to `config/canonical-product-registry.json`
   - Allowed: Generate ProductUnitIntent, let Kernel CLI/API handle registry

2. **Define Kernel Product Lifecycle enum locally**
   - Forbidden: Repeating Kernel phase names in YAPPC code
   - Allowed: Reference Kernel contracts when needed for display

3. **Parse private Kernel logs**
   - Forbidden: Reading stdout/stderr logs for lifecycle status
   - Allowed: Consume public events, manifests, snapshots, APIs

4. **Import Kernel implementation packages**
   - Forbidden: Direct imports from Kernel implementation modules
   - Allowed: Import only public Kernel contracts (kernel-product-contracts)

5. **Execute Kernel gates**
   - Forbidden: YAPPC gate logic for Kernel delivery gates
   - Allowed: Display Kernel gate status, recommend actions

6. **Duplicate Kernel artifact logic**
   - Forbidden: Reimplement artifact fingerprinting, deployment logic
   - Allowed: Display Kernel artifact metadata from public sources

---

## 8. Digital Marketing Pilot View

Initial YAPPC kernel health dashboard will focus on Digital Marketing as the pilot product:

- Digital Marketing ProductUnit health summary card
- Lifecycle run timeline for Digital Marketing
- Gate failure details specific to Digital Marketing
- Artifact deployment status for Digital Marketing surfaces
- Agent governance health for Digital Marketing agents
- Preview security status for Digital Marketing previews

**Note:** Visibility layer is product-neutral. Digital Marketing is the first consumer to validate the architecture. Future products will use the same visibility infrastructure.

---

## 9. Data Cloud/Event-Backed Ingestion State

YAPPC now has both local-development and Data Cloud-backed Kernel lifecycle truth providers. The local filesystem provider remains useful for deterministic fixtures and developer inspection, but production wiring must select Data Cloud/Event Cloud truth and reject `.kernel/out/products/**` as runtime truth.

### 9.1 Implemented Provider Contract

- `KernelLifecycleTruthSource` defines the read boundary used by ingest, health, and recommendation services.
- `DataCloudKernelLifecycleTruthSource` reads typed `kernel_lifecycle_truth` records and returns explicit degraded records for malformed truth data.
- `KernelLifecycleEventIngestService` can be constructed from an injected truth source or directly from Data Cloud client/tenant context.
- `KernelHealthSnapshotService` and `KernelActionRecommendationService` can use the Data Cloud-backed truth source for product health and remediation views.

### 9.2 Production Wiring Rules

- Production deployments must set `YAPPC_KERNEL_LIFECYCLE_TRUTH_SOURCE=data-cloud`.
- Production configuration rejects missing, local, mock, fake, or demo Kernel lifecycle truth sources.
- Local filesystem constructors are guarded as dev/test-only and fail when `YAPPC_ENVIRONMENT=production`.
- CI runs `check-production-truth-sources.mjs` to keep deployment manifests aligned with the Data Cloud truth-source requirement.

### 9.3 Remaining Integration Surface

- Event Cloud streaming can still be layered onto the same `KernelLifecycleTruthSource` boundary for live updates.
- Kernel health APIs can supplement Data Cloud records for fresh point-in-time status when those public APIs are available.
- ProductUnit registry metadata remains consumed through public Kernel contracts; YAPPC must not mutate Kernel registry files.

---

## 10. Testing and Validation

### 10.1 Unit Tests

- `ProductUnitIntentExporterTest`: Validates intent generation from YAPPC projects
- `ProductUnitIntentValidationServiceTest`: Validates intent structure and rules
- `KernelHealthSnapshotServiceTest`: Validates health view construction
- `KernelActionRecommendationServiceTest`: Validates recommendation logic

### 10.2 Integration Tests

- Ingest service tests with sample Kernel manifests
- End-to-end flow from YAPPC project to ProductUnitIntent
- Health dashboard rendering with test data

### 10.3 Boundary Validation

- `check-kernel-boundary-usage.mjs`: Automated script to verify:
  - YAPPC does not mutate Kernel registry files
  - YAPPC does not define Kernel lifecycle enum locally
  - YAPPC kernel-health imports only public contracts
  - YAPPC does not parse private logs
  - CreateCommand with `target=kernel-product-unit` writes intent, not registry

### 10.4 Manual Validation

- Verify Digital Marketing pilot displays correctly
- Verify gate failures show actionable recommendations
- Verify ProductUnitIntent generation produces valid YAML
- Verify Kernel CLI can consume generated intent

---

## 11. Implementation Status

| Component | Status | Evidence |
|-----------|--------|----------|
| Documentation (this file) | Current | This document distinguishes local provider, production Data Cloud provider, and remaining integration surface. |
| Creator Lifecycle to Kernel Mapping | Implemented | [Creator Lifecycle to Kernel Mapping](CREATOR_LIFECYCLE_TO_KERNEL_MAPPING.md) |
| ProductUnitIntentExporter | Implemented | `ProductUnitIntentExporterTest` and golden ProductUnitIntent YAML contract coverage. |
| ProductUnitIntentValidationService | Implemented | `ProductUnitIntentValidationServiceTest` validates Kernel contract provider/profile/surface values. |
| CreateCommand kernel-product-unit support | Implemented | `CreateCommand` writes ProductUnitIntent files instead of mutating Kernel registry state. |
| Backend/API Kernel handoff | Implemented | `KernelProductUnitHandoffService` and `POST /api/v1/yappc/generate/product-unit-intent`. |
| KernelLifecycleEventIngestService | Implemented | `KernelLifecycleEventIngestServiceTest` covers injected truth source, provider delegation, and production local-provider rejection. |
| DataCloudKernelLifecycleTruthSource | Implemented | `DataCloudKernelLifecycleTruthSourceTest` covers typed truth records and degraded malformed-record handling. |
| KernelHealthSnapshotService | Implemented | `KernelHealthSnapshotServiceTest` covers list/detail health views from Kernel truth. |
| KernelActionRecommendationService | Implemented | `KernelActionRecommendationServiceTest` covers evidence-backed remediation recommendations. |
| Kernel Health Dashboard UI | Implemented | `KernelHealthDashboardPage.test.tsx` and kernel-health route E2E matrix entries cover list/detail sections. |
| Boundary Check Script | Implemented | `check-kernel-boundary-usage.mjs` enforces public-contract and no-private-Kernel-boundary rules. |

---

## 12. References

- [YAPPC Architecture](../ARCHITECTURE.md)
- [Creator Lifecycle to Kernel Mapping](CREATOR_LIFECYCLE_TO_KERNEL_MAPPING.md)
- [Kernel Product Contracts](../../../../platform/typescript/kernel-product-contracts)
- [YAPPC Backlog Progress](../YAPPC_BACKLOG_PROGRESS.md)

---

**Status:** Living Document  
**Owner:** YAPPC Architecture Team  
**Review Cycle:** As needed during implementation
