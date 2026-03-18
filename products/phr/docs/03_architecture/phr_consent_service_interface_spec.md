# PHR Platform — ConsentService Interface Specification

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** Architecture Lead  
**Approval status:** Proposed P0 architecture control point  
**Classification:** Internal — Restricted

| Field | Value |
| --- | --- |
| Source-of-truth inputs | [Consent workflow](../04_design_and_workflows/phr_workflow_consent.md), [Runtime architecture](phr_runtime_architecture.md), [NestJS modules architecture](phr_nestjs_modules_detailed_architecture.md), [Requirements traceability matrix](../01_governance/phr_requirements_traceability_matrix.md) |
| Primary consumers | Backend, security, QA |

This document defines the mandatory runtime interface for consent-aware access decisions across all PHR patient-data paths.

---

## 1. Decision statement

All reads and writes that touch patient data must go through a single application-level control point named `ConsentService`. Module-local permission helpers may exist, but they cannot bypass or replace this contract.

The service answers one question consistently:

`Can this actor perform this action on this patient-scoped resource in this tenant context right now?`

---

## 2. Interface contract

```typescript
export type ConsentAction =
  | "patient.read"
  | "patient.write"
  | "document.read"
  | "document.write"
  | "medication.read"
  | "medication.write"
  | "timeline.read"
  | "insurance.read"
  | "insurance.check"
  | "audit.read"
  | "emergency.read";

export type ConsentAccessDecision = {
  allowed: boolean;
  reasonCode:
    | "SELF_ACCESS"
    | "EXPLICIT_GRANT"
    | "ROLE_ALLOWED"
    | "EMERGENCY_GRANT"
    | "GRANT_EXPIRED"
    | "GRANT_REVOKED"
    | "TENANT_MISMATCH"
    | "OUT_OF_SCOPE"
    | "RESTRICTED_RESOURCE"
    | "SYSTEM_DENY";
  grantId?: string;
  cacheStatus: "HIT" | "MISS" | "BYPASS";
  auditRequired: boolean;
  expiresAt?: string;
  obligations: string[];
};

export type ConsentCheckRequest = {
  requestId: string;
  tenantId: string;
  actor: {
    actorId: string;
    actorType: "PATIENT" | "PROVIDER" | "CAREGIVER" | "ADMIN" | "FCHV";
    patientId?: string;
    practitionerId?: string;
    organizationId?: string;
    scopes: string[];
  };
  target: {
    patientId: string;
    resourceType: string;
    resourceId?: string;
    classification: "C1" | "C2" | "C3" | "C4";
  };
  action: ConsentAction;
  purposeOfUse:
    | "SELF_SERVICE"
    | "CARE_DELIVERY"
    | "ELIGIBILITY_CHECK"
    | "EMERGENCY"
    | "AUDIT_REVIEW"
    | "ASSISTED_REGISTRATION";
  emergency?: {
    enabled: boolean;
    justification?: string;
    category?: "TRAUMA" | "UNCONSCIOUS" | "MINOR_WITHOUT_GUARDIAN";
  };
};

export interface ConsentService {
  checkAccess(request: ConsentCheckRequest): Promise<ConsentAccessDecision>;
  assertAccess(request: ConsentCheckRequest): Promise<ConsentAccessDecision>;
  invalidatePatientAccessCache(input: {
    tenantId: string;
    patientId: string;
    reason: "GRANT_CREATED" | "GRANT_REVOKED" | "GRANT_EXPIRED" | "DOCUMENT_VISIBILITY_CHANGED";
  }): Promise<void>;
}
```

---

## 3. Behavioral rules

### 3.1 `checkAccess`

- returns a structured allow or deny decision
- never throws for an expected deny path
- may throw only for system failure, corrupted policy state, or unavailable dependencies

### 3.2 `assertAccess`

- wraps `checkAccess`
- throws a typed application error on deny
- always includes `reasonCode`, tenant id, actor id, patient id, and request id in the error context

### 3.3 Mandatory callers

The following entry points must call `assertAccess` or `checkAccess` before data access:

- route guards for direct patient resource reads
- timeline and dashboard aggregators before fan-out
- document download handlers before storage lookup
- provider clinical write flows before mutation
- insurance eligibility checks before request submission
- audit review endpoints before result projection

---

## 4. Middleware and NestJS integration pattern

### 4.1 Request context flow

1. auth guard resolves actor and tenant
2. tenant guard validates request tenant context
3. route or service builds `ConsentCheckRequest`
4. `ConsentService.assertAccess()` executes
5. decision metadata is attached to request context for audit logging
6. handler proceeds to repository or integration call

### 4.2 Injection pattern

Use a request-scoped policy helper or route-level guard that depends on `ConsentService`, not a module-local ad hoc implementation.

```typescript
@Injectable()
export class PatientAccessGuard implements CanActivate {
  constructor(private readonly consentService: ConsentService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    await this.consentService.assertAccess(buildConsentRequest(request));
    return true;
  }
}
```

### 4.3 Aggregator rule

Composite handlers such as timeline, dashboard, and patient summary must call `ConsentService` once at the patient boundary and again for any resource class that has stricter visibility rules such as document content or emergency-only data.

---

## 5. Error behavior

| Deny reason | HTTP behavior | Audit behavior |
| --- | --- | --- |
| `TENANT_MISMATCH` | `403` at guard boundary or `404` when resource existence must be concealed | always audited |
| `GRANT_EXPIRED` | `403` | audited |
| `GRANT_REVOKED` | `403` | audited |
| `OUT_OF_SCOPE` | `403` or `404` depending on route privacy policy | audited |
| `RESTRICTED_RESOURCE` | `403` | audited |
| `SYSTEM_DENY` | `503` or `500` based on failure mode | audited as policy failure |

Security rule: deny responses must never leak the existence of tenant-external resources.

---

## 6. Emergency access rules

- emergency access is implemented as a specific `EMERGENCY` purpose of use
- break-the-glass flows always bypass positive cache hits and query authoritative state
- emergency grants are read-only, time-limited, and scope-restricted
- emergency access to restricted categories such as mental health, reproductive health, or HIV data requires explicit secondary policy approval and is denied by default

---

## 7. Caching rules

- deny decisions may be cached for the shorter of 5 minutes or grant expiry
- allow decisions may be cached for the shorter of 5 minutes or grant expiry
- any access decision within 5 minutes of expiry must bypass cache
- document-level visibility changes invalidate all cached allow decisions for that patient
- emergency decisions always use `cacheStatus = "BYPASS"`

---

## 8. Test obligations

Minimum required coverage:

- `API-022`, `API-023`, `API-048` to `API-050`
- `SVC-010`, `SVC-020` to `SVC-023`
- `NFR-002`, `NFR-021`, `NFR-027`

The service is not considered agreed until the deny semantics, cache invalidation, and emergency path all have explicit automated tests.