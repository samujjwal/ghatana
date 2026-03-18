# PHR Platform — QA Delivery Plan

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** QA Lead  
**Approval status:** Draft for release governance review  
**Classification:** Internal

| Field | Value |
| --- | --- |
| Primary consumers | QA, backend, frontend, security, DevOps |
| Source-of-truth inputs | [API testcases](../05_testing/phr_api_testcases.md), [Service integration testcases](../05_testing/phr_service_integration_testcases.md), [UI E2E testcases](../05_testing/phr_ui_e2e_testcases.md), [Non-functional and compliance testcases](../05_testing/phr_nonfunctional_and_compliance_testcases.md), [Traceability matrix](phr_requirements_traceability_matrix.md), [Retention and deletion policy](phr_retention_and_deletion_policy.md) |
| Execution artifacts | [Test automation mapping](../05_testing/phr_test_automation_mapping.md), [Seed data and fixture plan](../05_testing/phr_seed_data_and_test_fixture_plan.md) |

This document turns the current testcase packs into execution-ready QA workstreams, ownership, environments, and release gates.

---

## 1. QA delivery goals

- give every testcase ID an owning suite location
- define the seed data and stub dependencies needed to run those suites
- separate smoke, regression, compliance, and performance execution paths
- establish gating rules for pull request, staging, pre-production, and release approval

---

## 2. Planned quality workspace shape

```text
products/phr/
  apps/
    api/test/
      contract/
      integration/
      security/
    web/test/
      e2e/
      accessibility/
    mobile/test/
      e2e/
  qa/
    fixtures/
    performance/
    compliance/
    reports/
```

Target execution tooling:

- API and integration: Jest or Vitest + Supertest + Prisma test harness
- UI E2E: Playwright for web, Detox or Maestro for mobile
- performance: k6
- DAST: OWASP ZAP baseline and authenticated scan profiles
- SAST and dependency scanning: Semgrep, OWASP Dependency-Check, Trivy

---

## 3. Suite tiers

| Tier | Trigger | Scope | Failure policy |
| --- | --- | --- | --- |
| Smoke | every pull request | critical auth, patient read/write, consent, health checks | block merge |
| Regression | every merge to protected branch | all MVP API, service, and key UI flows | block staging deploy |
| Compliance | nightly and before release | OWASP, DPIA evidence, audit, retention, tenant isolation | block production approval |
| Performance | scheduled and before release candidate | latency, sustained load, upload, timeout, failover | require explicit release sign-off |

---

## 4. QA workstreams

| Workstream | Deliverables | Depends on |
| --- | --- | --- |
| Contract automation | request and response assertions for all MVP routes, including export polling semantics | OpenAPI DTO drafts, shared schemas |
| Service integration | DB, storage, cache, and openIMIS integration coverage | Prisma draft, tenant strategy, stubs |
| UI E2E | role-specific patient, caregiver, provider, FCHV, and admin scenarios, including generalized offline sync, payments, referrals, imaging, and export UX | route implementation, seeded environments |
| Security validation | auth hardening, IDOR, OWASP, DAST, dependency scans | secrets, CI/CD, staging environment |
| Non-functional | latency, upload, ASR accuracy, failover, restore drills, artifact expiry validation | monitoring, performance environments |

---

## 5. Environment matrix

| Environment | Purpose | Required suites |
| --- | --- | --- |
| local | developer feedback | targeted unit, smoke, component E2E |
| CI ephemeral | merge protection | smoke, API contract, selected integration |
| staging | integrated validation | full regression, DAST baseline, accessibility, tenant isolation |
| pre-prod | release rehearsal | compliance pack, performance, restore drill, pen-test support |

---

## 6. Ownership model

| Area | Primary owner | Supporting owners |
| --- | --- | --- |
| API contract suites | QA automation lead | backend module owners |
| service and integration suites | QA automation lead | backend module owners, DevOps |
| web UI E2E | frontend QA owner | frontend lead |
| mobile E2E | mobile QA owner | frontend lead |
| security validation | security lead | QA lead, DevOps |
| performance and capacity tests | performance QA owner | DevOps, backend lead |
| compliance evidence pack | compliance lead | QA lead, security lead |

---

## 7. Release gates

### 7.1 Pull request gate

- smoke suite green
- API contract changes reflected in shared schema and test mapping docs
- no new critical or high SAST findings

### 7.2 Staging gate

- full regression green
- cross-tenant isolation suites green
- DAST baseline green or waived with tracked remediation
- accessibility automation green for changed screens

### 7.3 Release candidate gate

- compliance suite green
- load and stress criteria met
- backup restore drill within acceptable window
- secrets, monitoring, and incident runbooks reviewed

---

## 8. Current QA blockers

| Blocker | Impact |
| --- | --- |
| test suite file paths not yet created | testcase IDs cannot be assigned to automation owners |
| shared validation schemas not generated from frozen DTO drafts | contract tests cannot enforce final runtime validation rules |
| tenant enforcement not implemented | cross-tenant isolation tests cannot become authoritative |
| secrets and staging config unresolved | DAST and integration suites cannot run reliably |
| fixture strategy not codified | UI and integration tests cannot share deterministic data |
| retention cleanup, export expiry, and sync-replay jobs not implemented | compliance, portability expiry, and offline conflict suites cannot become authoritative |

This plan is complete when every testcase ID has a suite path, owner, environment, and release gate.