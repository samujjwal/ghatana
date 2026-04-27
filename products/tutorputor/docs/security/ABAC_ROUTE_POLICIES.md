# TutorPutor ABAC Route Policy Matrix

Document purpose: explicit policy documentation for route families, mapped to the central ABAC engine.

Source implementation:
- `products/tutorputor/services/tutorputor-platform/src/modules/policy/abac-policy-engine.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/policy/__tests__/abac-policy-engine.test.ts`

Last updated: 2026-04-26

---

## Policy Principles

1. Fail closed by default.
2. Tenant isolation is mandatory.
3. Role checks and ownership checks are explicit.
4. Consent gates apply for learner AI/simulation actions.
5. All decisions (ALLOW or DENY) must emit audit entries.

---

## Built-in Policy IDs

- `builtin:admin-full-access`
- `builtin:creator-generation`
- `builtin:creator-experience-write`
- `builtin:teacher-read-review`
- `builtin:teacher-review-queue`
- `builtin:student-read-with-consent`
- `builtin:student-execute-simulation`
- `builtin:owner-read-own`
- `builtin:deny-cross-tenant`

---

## Route Family Matrix

| Route Family | Resource Type | Actions | Student | Teacher | Creator | Admin | Cross-Tenant |
|---|---|---|---|---|---|---|---|
| Learner Experience (`/api/v1/learning`, `/api/v1/modules`) | `experience` | `read` | ALLOW with consent | ALLOW | ALLOW | ALLOW | DENY |
| Content Generation (`/api/generation/requests`) | `generation_request` | `create/read/update` | DENY | DENY | ALLOW | ALLOW | DENY |
| Content Studio (`/api/content-studio`) | `experience` | `create/update/publish/read` | DENY | read only | ALLOW | ALLOW | DENY |
| Simulation Runtime (`/api/v1/simulation`, `/api/sim-author`) | `simulation` | `execute/read/update` | execute/read with consent | read/review | create/update/read | ALLOW | DENY |
| Review Queue (`/api/content-studio/review`) | `review_queue` | `review/read` | DENY | ALLOW | ALLOW (read) | ALLOW | DENY |
| Tenant Configuration (`/api/v1/tenant`) | `tenant_config` | `admin` | DENY | DENY | DENY | ALLOW | DENY |
| User Profile (`/api/v1/users/:id`) | `user_profile` | `read/update` | self-read only | role-based | role-based | ALLOW | DENY |

Notes:
- Student read and execute actions require `ai_tutor_consent`.
- Owner-read-own applies to resources with `ownerUserId` and `read` action.
- Admin full access does not override cross-tenant denial.

---

## Consent-Sensitive Actions

The following actions require consent (`ai_tutor_consent`) for student role:

- `read` on experience-like resources where AI adaptation is used
- `execute` on simulation resources

If consent is missing, policy evaluation yields DENY and an audit entry is emitted.

---

## Tenant-Scoped Lookup Requirement

Every resource mutation/read path must resolve by `(tenantId, resourceId)` or stricter ownership keys.

Required query pattern:

```ts
where: {
  id: resourceId,
  tenantId: actor.tenantId,
}
```

Forbidden query pattern:

```ts
where: {
  id: resourceId,
}
```

---

## Audit Requirements

Every ABAC decision must produce an audit entry with:

- `actorId`
- `tenantId`
- `resourceType`
- `resourceId`
- `action`
- `decision`
- `reason`
- `matchedPolicyId`
- `timestamp`

This is enforced by the `AbacPolicyEngine.evaluate()` return type.

---

## Verification Commands

```powershell
cd products/tutorputor/services/tutorputor-platform
pnpm exec vitest run src/modules/policy/__tests__/abac-policy-engine.test.ts
```

Expected result:
- Test file passes
- Role matrix and cross-tenant denies validated
- Audit entry shape validated
