# PHR Nepal — Personal Health Records

**Product Owner:** @ghatana/phr-team  
**Status:** Alpha — Core Implementation Complete  
**Stack:** Java 21 + ActiveJ · React 19 + Tailwind CSS · React Native (Expo)

## Purpose

**PHR Nepal** is a personal health records application for the Nepal market. It provides patients and healthcare providers with a secure, interoperable platform for managing medical records, prescriptions, lab results, appointment history, clinical notes, imaging, immunizations, referrals, caregiver access, telemedicine, and emergency break-glass access.

## Implementation Status

| Module                                                | Status            | Notes                                                                       |
| ----------------------------------------------------- | ----------------- | --------------------------------------------------------------------------- |
| `PhrKernelModule` (14 services)                       | ✅ Complete       | ActiveJ + Kernel Platform integration                                       |
| Security (`PHRSecurityManagerImpl`)                   | ✅ Complete       | HIPAA-compliant authN/authZ                                                 |
| Privacy (`PHRPrivacyManagerImpl`)                     | ✅ Complete       | Nepal Directive 2081 consent model                                          |
| Consent Management                                    | ✅ Complete       | Distributed cache, rate-limited, audit-logged                               |
| FHIR R4 Transformation Engine                         | ✅ Complete       | Patient, Observation, Medication, Appointment, Consent, Document            |
| Observability (`PHRAuditTrailServiceImpl`, telemetry) | ✅ Complete       | Immutable audit trail, Micrometer metrics                                   |
| Patient Record Service                                | ✅ Complete       | 25-year retention, field-level consent enforcement                          |
| Lab / Medication / Imaging / Immunization             | ✅ Complete       | FHIR-backed clinical services                                               |
| Appointments / Telemedicine / Referrals               | ✅ Complete       | Scheduling, reminder hooks, caregiver delegation                            |
| Emergency Access                                      | ✅ Complete       | Break-glass with mandatory post-access audit                                |
| Retention / Deletion Workflow                         | ✅ Complete       | Nepal Privacy Act 2075 right-to-erasure compliance                          |
| Billing (PHR-side)                                    | ✅ Complete       | Encounter + insurance claim baseline; EDI clearinghouse out of scope for v1 |
| FHIR Server Endpoint                                  | ❌ Planned        | Only transformation engine exists today                                     |
| Mobile App                                            | ❌ Planned        | React Native scaffold not started                                           |
| AI/ML Clinical Decision Support                       | ✅ Complete       | Lab anomaly, medication interaction, and readmission agents exposed via kernel service |
| Nepal HIE Integration                                 | ❌ Planned        | Interface design pending                                                    |

## Architecture

- **Backend:** Java 21 + ActiveJ (ActiveJ Promise async, ServiceLauncher lifecycle)
- **Security:** `platform:java:security` — JWT, RBAC, ABAC, API key management
- **Observability:** `platform:java:observability` — OpenTelemetry, Micrometer, immutable audit trail
- **Database:** `platform:java:database` — DataCloud kernel adapter, 25-year retention policy
- **Standards:** HL7 FHIR R4 for health data interoperability
- **Regulatory:** Nepal Directive 2081, Nepal Privacy Act 2075, HIPAA (for international compatibility)
- **Frontend:** React 19 + Tailwind CSS + `@ghatana/design-system`
- **Mobile:** React Native (Expo) — planned

## Module Layout

```
src/main/java/com/ghatana/phr/
  kernel/              — PhrKernelModule, service catalog, consent, events, retention
    service/           — 15 registered services (patient records, billing, lab, medication, clinical decision support, …)
    consent/           — ConsentService interface (single source of truth)
    module/            — Sub-modules: Patient, Clinical, Administrative, Emergency
  security/            — PHRSecurityManagerImpl, PHRPrivacyManagerImpl, HIPAAPrivacyPolicy
  observability/       — PHRAuditTrailServiceImpl, PHRTelemetryManagerImpl, explainability
  fhir/                — FhirR4TransformationEngine, FhirValidator, FhirTransformer
  model/               — Domain models: PatientRecord, PatientConsent, ProviderInfo, …
  repository/          — Data access: PatientRecordRepository, ConsentRepository, …
  plugin/              — PhrKernelPlugin, FhirInteropKernelPlugin
  extension/           — HealthcareConsentKernelExtension
```

## Documents

| Document                                                               | Description                                                    |
| ---------------------------------------------------------------------- | -------------------------------------------------------------- |
| [`PHR_KERNEL_INTEGRATION_README.md`](PHR_KERNEL_INTEGRATION_README.md) | Full Kernel Platform integration guide and component inventory |
| [`docs/phr-research.md`](docs/phr-research.md)                         | Market analysis, regulatory considerations (MoHP Nepal)        |
| [`docs/phr-feature-list.md`](docs/phr-feature-list.md)                 | Prioritized feature backlog (MoSCoW)                           |
| [`docs/phr-e2e-requirements.md`](docs/phr-e2e-requirements.md)         | End-to-end system requirements                                 |
| [`domain-pack-manifest.yaml`](domain-pack-manifest.yaml)               | Kernel capability declarations for this domain pack            |
