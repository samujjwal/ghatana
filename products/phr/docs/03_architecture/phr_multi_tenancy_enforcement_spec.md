# PHR Platform — Multi-Tenancy Enforcement Specification

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** Architecture Lead  
**Approval status:** Proposed P0 architecture decision  
**Classification:** Internal — Restricted

| Field | Value |
| --- | --- |
| Source-of-truth inputs | [Runtime architecture](phr_runtime_architecture.md), [Schema delta spec](phr_core_schema_delta_spec.md), [Traceability matrix](../01_governance/phr_requirements_traceability_matrix.md), [Service integration testcases](../05_testing/phr_service_integration_testcases.md) |
| Decision summary | Use defense-in-depth: PostgreSQL RLS plus application-layer tenant assertions |

This document operationalizes tenant isolation for all PHR data paths.

---

## 1. Decision

PHR will use a two-layer strategy:

1. application-layer tenant assertion on every request and storage interaction
2. PostgreSQL row-level security on every tenant-owned table

The system will not rely on application filters alone.

---

## 2. Rationale

| Option | Decision | Rationale |
| --- | --- | --- |
| Application filtering only | Rejected | too easy to bypass in ad hoc repository code or future export jobs |
| RLS only | Rejected | insufficient for early request rejection, storage isolation, and error shaping |
| App filters + RLS | Accepted | catches errors early and still prevents DB-level escape paths |

This decision aligns with the backlog requirement that multi-tenancy be operationalized rather than mentioned abstractly.

---

## 3. Scope of tenant ownership

All tenant-owned tables must carry `tenantId` and be RLS-protected. This includes:

- patient, practitioner, organization, encounter, observation, condition, medication request, appointment, document, coverage, claim, consent, audit, and all app-owned operational tables
- outbox and integration attempt tables that contain tenant data
- review queue and notification tables

Global reference data that is truly non-tenant-specific may be kept outside RLS, but it cannot contain patient or organization identifiers.

---

## 4. Request-level enforcement

### 4.1 Required request context

Every authenticated request must have:

- tenant id from trusted token claims
- actor id and actor type
- request id
- facility or organization id when relevant

`X-Tenant-Id` may be accepted for routing and diagnostics, but the JWT claim is authoritative. If the header and token do not match, reject the request.

### 4.2 Application-layer rules

- controllers reject tenant mismatch before hitting repositories
- repositories require explicit tenant context input
- storage keys include tenant-prefixed object paths
- cache keys include tenant id as the first segment
- audit entries always persist tenant id

---

## 5. PostgreSQL RLS model

### 5.1 Session bootstrap

Each DB transaction must set the effective tenant in the session before any query executes.

Suggested pattern:

```sql
SELECT set_config('app.current_tenant_id', $1, true);
```

### 5.2 Base policy pattern

```sql
ALTER TABLE patient ENABLE ROW LEVEL SECURITY;

CREATE POLICY patient_tenant_isolation ON patient
USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
```

### 5.3 Additional policy rule

RLS enforces only tenant boundaries. It does not replace consent, role, or relationship rules. Those remain in the application layer.

---

## 6. Storage and cache isolation

### 6.1 Object storage

Ceph object keys must use this shape:

```text
tenant/{tenantId}/{module}/{resourceId}/{objectId}
```

No object lookup may be performed without tenant-prefixed path resolution.

### 6.2 Cache keys

Key format:

```text
phr:{tenantId}:{domain}:{key}
```

Examples:

- `phr:tenant-1:consent:patient-123:provider-456`
- `phr:tenant-1:timeline:patient-123:2026-03`

---

## 7. Background jobs and exports

- worker jobs carry tenant id in payload and structured logs
- export jobs open a tenant-scoped transaction before reading data
- batch jobs may iterate across tenants only through an explicit orchestrator that opens separate isolated execution units per tenant

---

## 8. Failure behavior

| Failure | Required behavior |
| --- | --- |
| missing tenant context | fail closed before repository access |
| header-token mismatch | `403` and audit security event |
| RLS misconfiguration detected | fail deployment or halt migration rollout |
| cross-tenant lookup attempt | return `404` when existence concealment is required |

---

## 9. Verification and test obligations

Required validation set:

- `API-048`, `API-049`, `API-050`
- `SVC-016`, `SVC-017`, `SVC-018`, `SVC-019`
- `NFR-041`, `NFR-042`, `NFR-043`

No module is schema-ready until its tables are included in the RLS rollout list and mapped to these tests.