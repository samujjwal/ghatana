# PHR Nepal — Implementation Plan

**Status:** Alpha Pilot  
**Lifecycle stage:** Local pilot — `validate`, `test`, `build`, `package`, `deploy:local`, `verify:local`  
**Target:** Production-ready regulated healthcare pilot under Ghatana Kernel lifecycle governance
**Classification:** Target architecture. **Evidence:** `products/phr/kernel-product.yaml`, `products/phr/lifecycle/readiness-evidence.yaml`

---

## Pilot Identity

| Attribute                   | Value                                                                             |
| --------------------------- | --------------------------------------------------------------------------------- |
| Product ID                  | `phr`                                                                             |
| Kind                        | `business-product`                                                                |
| Lifecycle status            | `enabled` — Existing and executable. Evidence: `products/phr/kernel-product.yaml` |
| Lifecycle execution allowed | `true`                                                                            |
| Deployment target           | `compose-local`                                                                   |
| Surfaces                    | `backend-api` (Gradle Java), `web` (pnpm Vite React)                              |

---

## Capability Status

### Implemented (Alpha)

| Capability                                | Module                             | Notes                                                                                                  |
| ----------------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Patient identity and record ownership     | `products/phr/src`                 | Field-level consent enforcement                                                                        |
| Consent management                        | `products/phr/src`                 | Distributed cache, rate-limited, audit-logged                                                          |
| FHIR R4 transformation engine             | `products/phr/domains/healthcare`  | Patient, Observation, Medication, Appointment, Consent, Document                                       |
| Security (HIPAA-compliant AuthN/AuthZ)    | `products/phr/src`                 | JWT + RBAC + ABAC, `platform:java:security`                                                            |
| Privacy (Nepal Directive 2081 model)      | `products/phr/src`                 | Consent model compliance                                                                               |
| Observability (audit trail + telemetry)   | `products/phr/src`                 | Immutable audit, Micrometer metrics                                                                    |
| Lab / Medication / Imaging / Immunization | `products/phr/src`                 | FHIR-backed clinical services                                                                          |
| Appointments / Telemedicine / Referrals   | `products/phr/src`                 | Scheduling, reminders, caregiver delegation                                                            |
| Emergency access (break-glass)            | `products/phr/src`                 | Mandatory post-access audit                                                                            |
| Retention / deletion workflow             | `products/phr/src`                 | Nepal Privacy Act 2075 right-to-erasure                                                                |
| Billing baseline                          | `products/phr/src`                 | Encounter + insurance claim; EDI out of scope v1                                                       |
| AI/ML clinical decision support           | `products/phr/src`                 | Lab anomaly, medication interaction, readmission agents                                                |
| Web UI (patient + clinician flows)        | `products/phr/apps/web`            | Existing but partial. Core flows complete; E2E pending. Evidence: `products/phr/apps/web/package.json` |
| Backend API (ActiveJ HTTP)                | `products/phr/src`                 | Existing and executable. MVP complete. Evidence: `products/phr/build.gradle`                           |
| Kernel lifecycle integration              | `products/phr/kernel-product.yaml` | Existing and executable. All phases enabled. Evidence: `products/phr/kernel-product.yaml`              |

### Partial / Planned

| Capability                | Status  | Blocking Factor                             |
| ------------------------- | ------- | ------------------------------------------- |
| FHIR Server Endpoint      | Planned | Only transformation engine exists today     |
| Mobile App (React Native) | Planned | Scaffold not started                        |
| Nepal HIE Integration     | Planned | Interface design pending                    |
| E2E Playwright tests      | Partial | Local config exists; CI integration pending |

---

## Healthcare Gate Packs

PHR requires the following regulated gates to pass before any lifecycle promotion:

| Gate ID                    | Description                                      | Evidence                                                                            |
| -------------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------- |
| `consent`                  | Patient consent enforced before data access      | `PhrConsentBoundaryAccessGate`, `ConsentService`, `healthcare-boundary-policy.yaml` |
| `pii-classification`       | PII fields classified and protected              | PII classification policies in domain models                                        |
| `audit-evidence`           | Sensitive access logged in immutable audit trail | `PHRAuditTrailServiceImpl`, HIPAA validation evidence                               |
| `fhir-contract-validation` | FHIR R4 resource conformance                     | `FhirInteropKernelPlugin`, `schema-registry.yaml`, `openapi.yaml`                   |
| `tenant-data-sovereignty`  | Tenant data scoped and isolated                  | Tenant context propagation in all record queries                                    |

Gate packs are declared in `products/phr/lifecycle/gate-packs/*.yaml` with `executionMode: evidence-backed`.

---

## Lifecycle Commands

All lifecycle execution uses the Kernel product runner. PHR must not implement its own lifecycle runner.

```bash
# Full lifecycle proof sequence
pnpm plan:validate:phr  &&  pnpm validate:phr
pnpm plan:test:phr      &&  pnpm test:phr
pnpm plan:build:phr     &&  pnpm build:phr
pnpm plan:package:phr   &&  pnpm package:phr
pnpm plan:deploy:local:phr   &&  pnpm deploy:local:phr
pnpm plan:verify:local:phr   &&  pnpm verify:local:phr
```

### Phase descriptions

| Phase          | What it does                                                           |
| -------------- | ---------------------------------------------------------------------- |
| `validate`     | Runs type-check and compilation for backend and web surfaces           |
| `test`         | Runs all unit and integration tests for backend and web                |
| `build`        | Produces deployable artifacts (JAR for backend, static bundle for web) |
| `package`      | Builds Docker images via `docker-buildx` adapter                       |
| `deploy:local` | Deploys via Docker Compose to local environment                        |
| `verify:local` | Runs health checks against `/ready` (backend) and `/` (web)            |

---

## Evidence Output Paths

Lifecycle evidence is generated under:

```
.kernel/out/products/phr/<phase>/latest/
.kernel/evidence/phr/<runId>/
```

Gate pack evidence references are under:

```
products/phr/lifecycle/readiness-evidence.yaml
products/phr/lifecycle/gate-packs/*.yaml
```

---

## Rollout Criteria (Local Pilot)

PHR is rollout-ready for local pilot only when:

1. `validate`, `test`, `build` phases all succeed
2. All 5 healthcare gates execute and fail closed when evidence is missing
3. `deploy:local` + `verify:local` succeed or report `NOT_READY` with actionable reason
4. Lifecycle evidence pack is generated under `.kernel/evidence/phr/<runId>/`
5. No PHR-specific lifecycle runner exists (Kernel runner only)
6. No healthcare domain logic leaks into platform packages

---

## Promotion Criteria (Beyond Local Pilot)

- FHIR Server Endpoint implemented and validated
- Nepal HIE Integration interface approved by healthcare authority
- Full E2E Playwright suite passing in CI
- HIPAA validation evidence updated for production environment
- Nepal Directive 2081 compliance audit completed
- Staging deployment verified with smoke-test evidence
