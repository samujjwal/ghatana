# PHR — Kernel-Native Product Lifecycle

> **Purpose**: Documents how the PHR product integrates with the Ghatana Platform Kernel lifecycle, what is currently wired, and what remains to complete the Kernel-native posture.
> **Last updated**: 2026-05-02

---

## 1. What Is Kernel-Native?

A "Kernel-native" product is one that:

1. Registers as a `KernelModule` and exposes all capabilities through the Kernel capability/dependency protocol.
2. Emits lifecycle events (`PhrLifecycleEvent`, `PhrAuditEvent`, `PhrConsentEvent`) through the Kernel event bus.
3. Uses Kernel policy evaluation for consent, role, and entitlement decisions — not ad hoc in-route checks.
4. Generates a machine-readable IA baseline and exposes it through the Kernel lifecycle/readiness API.
5. Consumes the Kernel route entitlement API at the frontend shell level, not a hand-maintained frontend manifest.

---

## 2. Current Kernel Integration Status

### 2.1 Module Registration

**File**: `products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java`

PHR registers as a `KernelModule` implementing `KernelLifecycleAware`. The module:

- Declares capabilities via `@KernelCapability` (patient records, consent management, FHIR R4, documents, emergency access, clinical data, observability)
- Declares dependencies via `@KernelDependency` (security, observability, distributed cache)
- Registers event schemas via `ContractRegistry` (`PhrAuditEvent`, `PhrConsentEvent`, `PhrLifecycleEvent`)
- Implements `onStart`, `onStop`, and health reporting via `HealthStatus`

**Status**: ✅ Implemented

### 2.2 Kernel Plugins

| Plugin | File | Purpose | Status |
|--------|------|---------|--------|
| `PhrKernelPlugin` | `plugin/PhrKernelPlugin.java` | Main PHR Kernel plugin | ✅ Implemented |
| `FhirInteropKernelPlugin` | `plugin/FhirInteropKernelPlugin.java` | FHIR interoperability plugin | ✅ Implemented |
| `HealthcareConsentKernelExtension` | `extension/HealthcareConsentKernelExtension.java` | Consent extension for Kernel | ✅ Implemented |

### 2.3 Kernel Event Emission

PHR emits structured lifecycle events through the Kernel event bus for:

- Emergency access requests/approvals/denials → `KernelEventEmergencyAccessReviewAuditLogger`
- Emergency access notifications → `KernelEventEmergencyAccessNotificationSender`
- Consent grant/revocation → `PhrConsentEvent` schema
- Audit events → `PhrAuditEvent` schema (principal, tenantId, eventType, success, details)
- Lifecycle state changes → `PhrLifecycleEvent`

**Evidence outbox**: `FileBackedPhrEvidenceOutbox` + `PhrEvidenceOutboxDispatcher` persist evidence to `.kernel/evidence/phr/`

**Status**: ✅ Implemented (event schemas, outbox)

### 2.4 Release Readiness API

**File**: `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrReleaseReadinessRoutes.java`

PHR exposes a release readiness endpoint that reads Kernel evidence files.

**Gap**: The frontend `ReleaseCockpitPage.tsx` reads evidence directly from the Kernel API, but the backend currently reads from `.kernel/evidence/` files rather than delegating fully to the Kernel lifecycle/readiness service.

**Status**: 🔶 Partial — file-backed evidence reader; Kernel API delegation pending

### 2.5 Route Entitlement Manifest

**Backend**: `PhrEntitlementRoutes.java` serves `GET /phr/entitlements` with the authorized route list per session role.

**Frontend**: `phrRouteContracts.ts` is a hand-maintained TypeScript manifest that mirrors the backend entitlement.

**Gap**: The frontend manifest is not generated from the backend; drift is possible. A Kernel-native route contract generator should generate `phrRouteContracts.ts` from the canonical route schema.

**Status**: 🔶 Partial — drift between frontend manifest and backend entitlement

---

## 3. Security and Consent Policy Integration

### 3.1 Current Posture

PHR uses ad hoc role/consent checks via `PhrRouteSupport`:

```java
// Current: ad hoc helper method
PhrRouteSupport.canPerformAdminOperation(session, tenantId)
PhrRouteSupport.assertConsentOrAdmin(session, patientId, tenantId)
```

These helpers call `ConsentManagementService` directly from within the route handler.

### 3.2 Target Posture (Kernel Policy Evaluator)

The Kernel-native posture replaces these ad hoc checks with a centralized policy evaluator:

```java
// Target: Kernel policy evaluator
KernelPolicyEvaluator evaluator = kernelContext.getPolicyEvaluator();
PolicyDecision decision = evaluator.evaluate(PolicyRequest.builder()
    .subject(session.principalId())
    .resource("patient-record:" + patientId)
    .action("read")
    .attributes(Map.of("tenantId", tenantId, "consentScope", scope))
    .build());

if (!decision.isAllowed()) {
    throw new PhrAccessDeniedException(decision.getReason());
}
```

**Status**: ❌ Not yet implemented — Kernel policy evaluator integration pending

---

## 4. IA Baseline and Coverage

### 4.1 Machine-Readable Baseline

**File**: `products/phr/config/phr-usecase-baseline.json`

The canonical use-case baseline maps persona → IA route → web/mobile screen → backend APIs → implementation status.

**Status**: ✅ Implemented

### 4.2 Coverage Gate

**Script**: `scripts/check-phr-ia-coverage.mjs`

Validates that every route declared in the IA documentation is present in `phrRouteContracts`.

**Status**: ✅ Implemented

### 4.3 Kernel Lifecycle Readiness Score

The Kernel should compute a product readiness score from:
- IA coverage percentage (from `phr-usecase-baseline.json`)
- Test pass rate (from CI evidence)
- Evidence freshness (from `.kernel/evidence/phr/`)

**Status**: ❌ Not yet implemented — readiness score generator pending

---

## 5. Remaining Kernel-Native Tasks

| Task | Priority | Status |
|------|----------|--------|
| Replace ad hoc consent checks with Kernel policy evaluator | P0 | ❌ Not started |
| Generate `phrRouteContracts.ts` from Kernel canonical route contract | P0 | ❌ Not started |
| Delegate release readiness reads to Kernel lifecycle API (not file reader) | P0 | 🔶 Partial |
| Implement Kernel product readiness score from IA + tests + evidence | P2 | ❌ Not started |
| Add Kernel lifecycle "explain/recover" for missing IA tasks | P2 | ❌ Not started |
| Register PHR IA baseline as Kernel product unit in YAPPC | P1 | ❌ Not started |
| Visualize PHR IA coverage gaps on YAPPC canvas | P1 | ❌ Not started |

---

## 6. Kernel Integration Architecture Diagram

```
                    ┌─────────────────────────────────┐
                    │       Platform Kernel            │
                    │  ┌──────────────────────────┐   │
                    │  │  KernelLifecycleService   │   │
                    │  │  KernelPolicyEvaluator    │   │
                    │  │  ContractRegistry         │   │
                    │  │  EvidenceOutbox           │   │
                    │  └──────────────────────────┘   │
                    └────────────┬────────────────────┘
                                 │  KernelModule interface
                    ┌────────────▼────────────────────┐
                    │       PhrKernelModule            │
                    │  ┌──────────────────────────┐   │
                    │  │  PhrKernelPlugin          │   │
                    │  │  HealthcareConsentKernel  │   │
                    │  │  FhirInteropKernelPlugin  │   │
                    │  │  PhrEvidenceOutbox        │   │
                    │  └──────────────────────────┘   │
                    │          │                       │
                    │  ┌───────▼──────────────────┐   │
                    │  │  PHR Domain Services      │   │
                    │  │  PatientRecordService     │   │
                    │  │  ConsentManagementService │   │
                    │  │  EmergencyAccessLog       │   │
                    │  └──────────────────────────┘   │
                    └─────────────────────────────────┘
                                 │
                    ┌────────────▼────────────────────┐
                    │    PHR ActiveJ HTTP Server       │
                    │  PhrRoutes (auth/records/        │
                    │  consent/clinical/emergency/     │
                    │  entitlements/release)           │
                    └─────────────────────────────────┘
```

---

## 7. Related Documents

- [PHR Kernel Integration README](../../PHR_KERNEL_INTEGRATION_README.md)
- [PHR Runtime Architecture](phr_runtime_architecture.md)
- [PHR Access Policy Matrix](../security/phr_access_policy_matrix.md)
- [PHR Current Implemented Surface](../current-state/generated-current-surface.md)
- [Kernel Implementation Plan](../../../../platform-kernel/docs/01-KERNEL_IMPLEMENTATION_PLAN.md)
