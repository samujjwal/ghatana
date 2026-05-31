# PHR Nepal - Personal Health Records

**Product Owner:** @ghatana/phr-team
**Status:** Alpha - Kernel-native implementation in progress
**Stack:** Java 21 + ActiveJ, React 19 + Tailwind CSS, React Native (Expo)

## Purpose

**PHR Nepal** is a Kernel-native personal health records application for the Nepal market. It provides patients and healthcare operators with a secure, interoperable platform for managing medical records, prescriptions, lab results, appointment history, immunizations, documents, notifications, profile data, and emergency break-glass access. Provider, caregiver, FCHV, insurance, referrals, and broader telemedicine workflows remain hidden or deferred unless the Kernel route contract promotes them.

## Implementation Status

| Area | Status | Notes |
| --- | --- | --- |
| Kernel product registration | Implemented | PHR declares product/plugin metadata and Kernel dependencies. |
| Route contract and web shell | Implemented for current stable routes | `products/phr/config/phr-route-contract.json` is the canonical route source. Hidden provider/caregiver/FCHV routes are not mounted. |
| Policy dispatch | Implemented through Kernel plugin | Kernel dispatches to PHR healthcare decision providers; unknown policy IDs fail closed. |
| Audit and telemetry | Implemented with remaining evidence work | Audit envelopes use Kernel defaults with a PHR persistence adapter; telemetry enforces safe tags. Staging/performance evidence is not complete. |
| Mobile privacy | Implemented for current mobile surfaces | Encrypted cache clearing, consent invalidation, biometric emergency gate, stale metadata, and context headers are covered by mobile tests. |
| FHIR/HL7 and HIE | Partial | Kernel supplies reusable FHIR validation; PHR owns providers, transformations, HL7 parsing, and HIE adapters. |
| Documents/OCR | Implemented for current workflow | Kernel validates upload policy, provenance, malware attestation, and OCR review transitions; PHR owns persistence and authorization. |
| Appointments | Partial | Request workflow exists; full scheduling lifecycle is not complete. |
| Provider/caregiver/FCHV workflows | Deferred/hidden | Present in the route contract as hidden until full UI/API/policy/test coverage exists. |
| Compliance readiness | Not complete | HIPAA/staging/security-performance sign-off must be produced before production-readiness claims. |

## Architecture

- **Backend:** Java 21 + ActiveJ (ActiveJ Promise async, ServiceLauncher lifecycle)
- **Security:** Kernel policy plugin + PHR healthcare decision providers; `platform:java:security` remains a supporting dependency
- **Observability:** Kernel audit envelopes, safe telemetry tags, and product-specific persistence/metrics adapters
- **Database:** `platform:java:database` - DataCloud kernel adapter, 25-year retention policy
- **Standards:** HL7 FHIR R4 for health data interoperability
- **Regulatory:** Nepal Directive 2081 and Nepal Privacy Act 2075 alignment work; HIPAA compatibility evidence is not complete
- **Frontend:** React 19 + Tailwind CSS + `@ghatana/design-system`
- **Mobile:** React Native (Expo) with API-backed dashboard loading and offline cache support

## Module Layout

```text
src/main/java/com/ghatana/phr/
  kernel/              - PhrKernelModule, service catalog, consent, events, retention
    service/           - 15 registered services (patient records, billing, lab, medication, clinical decision support, ...)
    consent/           - ConsentService interface (single source of truth)
    module/            - Sub-modules: Patient, Clinical, Administrative, Emergency
  security/            - PHRSecurityManagerImpl, PHRPrivacyManagerImpl, HIPAAPrivacyPolicy
  observability/       - PHRAuditTrailServiceImpl, PHRTelemetryManagerImpl, explainability
  fhir/                - FhirR4TransformationEngine, FhirValidator, FhirTransformer
  model/               - Domain models: PatientRecord, PatientConsent, ProviderInfo, ...
  repository/          - Data access: PatientRecordRepository, ConsentRepository, ...
  plugin/              - PhrKernelPlugin, FhirInteropKernelPlugin
  extension/           - HealthcareConsentKernelExtension
```

## Documents

| Document                                                               | Description                                                    |
| ---------------------------------------------------------------------- | -------------------------------------------------------------- |
| [`PHR_KERNEL_INTEGRATION_README.md`](PHR_KERNEL_INTEGRATION_README.md) | Code-grounded Kernel integration status and remaining evidence work |
| [`docs/phr-research.md`](docs/phr-research.md)                         | Market analysis, regulatory considerations (MoHP Nepal)        |
| [`docs/phr-feature-list.md`](docs/phr-feature-list.md)                 | Prioritized feature backlog (MoSCoW)                           |
| [`docs/phr-e2e-requirements.md`](docs/phr-e2e-requirements.md)         | End-to-end system requirements                                 |
| [`domain-pack-manifest.yaml`](domain-pack-manifest.yaml)               | Kernel capability declarations for this domain pack            |
