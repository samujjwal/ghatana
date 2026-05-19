# PHR Healthcare Domain (`products/phr/domains/healthcare`)

**Gradle module:** `:products:phr:domains:healthcare`  
**Status:** Alpha — Core Implementation Complete  
**Language:** Java 21

---

## Purpose

The PHR healthcare domain module contains the regulated healthcare domain logic for PHR Nepal. It implements FHIR R4 resource transformations, patient identity and record ownership rules, clinical workflow domain models, and compliance enforcement for Nepal's healthcare regulatory requirements.

This module is a dependency of the main `products/phr` backend and must not import from other product namespaces.

---

## Domain Areas

| Area                     | Description                                                                       |
| ------------------------ | --------------------------------------------------------------------------------- |
| FHIR R4 Transformation   | Patient, Observation, Medication, Appointment, Consent, Document resource mapping |
| Patient Record Ownership | Field-level access control, 25-year retention enforcement                         |
| Consent Domain Model     | Distributed consent state, grant/revoke/audit workflows                           |
| Clinical Workflow        | Lab results, medications, imaging, immunizations, referrals                       |
| Appointment Domain       | Scheduling, reminders, caregiver delegation                                       |
| Emergency Access         | Break-glass authorization and mandatory audit                                     |
| Regulatory Compliance    | Nepal Directive 2081, Nepal Privacy Act 2075, HIPAA compatibility                 |

---

## Build

```bash
# From repo root
./gradlew :products:phr:domains:healthcare:build
./gradlew :products:phr:domains:healthcare:check
./gradlew :products:phr:domains:healthcare:test
```

---

## Key Constraints

- **Async-only**: All domain operations use ActiveJ `Promise<T>`. Blocking I/O is wrapped via `Promise.ofBlocking(...)`.
- **No platform leakage**: Domain logic must not import platform infrastructure packages directly. Use platform contracts via constructor injection.
- **Fail-closed**: Healthcare gate violations throw typed domain exceptions with actionable reason codes, never silently pass.
- **FHIR conformance**: All FHIR resource transformations must pass HL7 R4 conformance validation before being emitted.

---

## Healthcare Gate Evidence

This module provides evidence for the following PHR lifecycle healthcare gate packs:

| Gate                       | Evidence Reference                                       |
| -------------------------- | -------------------------------------------------------- |
| `fhir-contract-validation` | FHIR R4 transformation engine and conformance tests      |
| `consent`                  | Consent domain model, `ConsentService`, enforcement gate |
| `pii-classification`       | PII field classification in domain models                |
| `audit-evidence`           | Domain-level audit event emission                        |
| `tenant-data-sovereignty`  | Tenant scoping in all record queries and mutations       |

See `products/phr/lifecycle/gate-packs/` for the evidence references.
