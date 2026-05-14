# Kernel Visibility and Control Plane

**Status:** Active  
**Last Updated:** 2026-05-13  
**Owner:** YAPPC Architecture Team

---

## 1. Purpose

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

### 4.1 Local Development (Initial Implementation)

YAPPC reads Kernel outputs from local filesystem:

```
.kernel/out/products/<productId>/
├── lifecycle-plan.json          # Planned lifecycle run
├── lifecycle-result.json        # Execution result
├── artifacts.json               # Produced artifacts with fingerprints
├── deployment.json              # Deployment state
├── health-snapshot.json         # Current health status
└── gates.json                   # Gate evaluation results
```

### 4.2 Future: Data Cloud / Event Stream

Production implementation will consume:
- Data Cloud events for lifecycle phase transitions
- Event Cloud streams for real-time updates
- Kernel health APIs for current state
- ProductUnit registry for metadata

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

## 9. Future Data Cloud/Event-Backed Ingestion

Current implementation uses local filesystem ingestion for development. Future production implementation:

### 9.1 Event Stream Ingestion

- Subscribe to Kernel lifecycle events via Event Cloud
- Real-time updates to YAPPC read model
- Event types: `PhaseTransitioned`, `GateEvaluated`, `ArtifactProduced`, `DeploymentUpdated`, `AgentGovernanceChanged`

### 9.2 Data Cloud Queries

- Query Data Cloud for historical lifecycle runs
- Retrieve artifact metadata and deployment history
- Access agent governance and learning state

### 9.3 Kernel Health APIs

- Call Kernel health endpoints for current status
- Query ProductUnit registry for metadata
- Access gate evaluation results via API

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

| Component | Status | Notes |
|-----------|--------|-------|
| Documentation (this file) | ✅ Complete | |
| Creator Lifecycle to Kernel Mapping | ⏳ Pending | Task 5 |
| ProductUnitIntentExporter | ⏳ Pending | Task 6 |
| ProductUnitIntentValidationService | ⏳ Pending | Task 7 |
| CreateCommand kernel-product-unit support | ⏳ Pending | Task 8 |
| CICommand kernel-product-unit support | ⏳ Pending | Task 9 |
| KernelLifecycleEventIngestService | ⏳ Pending | Task 11 |
| KernelHealthSnapshotService | ⏳ Pending | Task 12 |
| KernelActionRecommendationService | ⏳ Pending | Task 13 |
| Kernel Health Dashboard UI | ⏳ Pending | Tasks 14-20 |
| Boundary Check Script | ⏳ Pending | Task 21 |

---

## 12. References

- [YAPPC Architecture](../ARCHITECTURE.md)
- [Creator Lifecycle to Kernel Mapping](CREATOR_LIFECYCLE_TO_KERNEL_MAPPING.md)
- [Kernel Product Contracts](../../../../platform/typescript/kernel-product-contracts)
- [Implementation Plan 02](../../../../../../Downloads/implementation_plan_02_yappc_visibility_shared_libraries.md)

---

**Status:** Living Document  
**Owner:** YAPPC Architecture Team  
**Review Cycle:** As needed during implementation
