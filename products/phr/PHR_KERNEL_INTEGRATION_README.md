# PHR Kernel Integration Status

This document is a code-grounded status snapshot for the PHR product’s Kernel integration. It is not release evidence, a compliance attestation, or proof that staging/performance/security audit gates have passed.

## Ownership Boundary

PHR is a Kernel-native product. Route lifecycle, policy dispatch, audit envelopes, safe telemetry, mobile privacy, document/OCR lifecycle rules, FHIR validation, localization checks, accessibility checks, and plugin declarations must come from Kernel contracts or Kernel plugins where those primitives exist.

PHR remains responsible for healthcare-specific domain providers, DTOs, route adapters, persistence adapters, FHIR resource providers, HIE integrations, consent semantics, emergency workflows, and product UI behavior. YAPPC must not contain PHR-specific generation logic or PHR domain knowledge; it may consume only generic Kernel product contracts.

## Current Truth Matrix

| Area | Code-grounded status | Validation anchor |
| --- | --- | --- |
| Kernel route contract and route plugin | Implemented for current PHR web route registration, visibility, entitlements, and navigation metadata | `pnpm --dir products/phr/apps/web type-check`, route parity tests, `node scripts/check-phr-route-contract-parity.mjs` |
| Backend route exposure | Canonical `/api/v1` route exposure is Kernel-contract aligned for current stable routes; hidden provider/caregiver/FCHV routes remain unmounted | `PhrHttpServerTest`, hidden route/backend mount guards |
| Policy dispatch | Kernel policy plugin dispatches PHR-supplied healthcare decision providers; unknown policy IDs fail closed | `KernelPolicyPluginTest`, `PhrPolicyEvaluatorTest`, `node scripts/validate-phr-policy-registry.mjs` |
| Audit envelope | PHR audit service uses Kernel `DefaultAuditTrailService` with PHR Data Cloud persistence adapter | `PHRAuditTrailServiceTest`, `PhrPolicyEvaluatorTest` |
| Safe telemetry | PHR telemetry accepts only safe tag names and patient identifiers are not metric dimensions | `PHRTelemetryManagerImplTest`, `PatientServiceTest`, `node scripts/check-phr-safe-telemetry.mjs` |
| Mobile PHI/privacy | Mobile restricted-field stripping, encrypted cache/session clearing, consent invalidation, biometric gate, stale metadata, and context headers are Kernel-backed or Kernel-contract aligned | mobile Node Jest suite, mobile type-check, Kernel mobile privacy tests |
| FHIR/HL7 interop | Kernel owns reusable FHIR R4 structural validation; PHR owns FHIR providers, storage, transformations, HL7 parsing, and HIE adapters | `KernelFhirHl7PluginTest`, `FhirInteropKernelPluginTest` |
| Document/OCR lifecycle | Kernel owns upload policy, provenance, malware attestation, and OCR review transition validation; PHR owns persistence, authorization, audit event names, and OCR engine behavior | `KernelDocumentOcrPluginTest`, `DocumentServiceTest` |
| i18n/a11y checks | Kernel generic product scanners back the PHR wrappers | `kernel-product-localization-accessibility-plugin.test.mjs`, `node scripts/check-phr-i18n-conformance.mjs`, `node scripts/check-phr-accessibility.mjs` |
| HIPAA/staging/performance evidence | Not complete in this snapshot | Formal staging, compliance, and performance artifacts are still required before production-readiness claims |

## Kernel Plugin Dependencies

PHR declares and/or consumes these Kernel/plugin capabilities:

- `policy`: policy registry and fail-closed dispatch.
- `audit`: immutable audit event envelope and persistence adapter contract.
- `observability`: safe telemetry tags and sensitive-flow diagnostics.
- `privacy`: PHI/PII classification and safe mobile cache stripping.
- `mobile-privacy`: encrypted cache/session/consent invalidation clearing.
- `fhir-hl7`: reusable FHIR R4 validation primitives.
- `document-ocr`: document upload/OCR lifecycle validation.
- `localization-accessibility`: generic i18n and accessibility conformance checks.
- Product-specific healthcare providers remain inside `products/phr/**`.

## Security And Runtime Notes

- PHR API handlers must require tenant, principal, role, persona, tier, facility, and correlation context where PHI or policy-protected resources are involved.
- Emergency PHI access must pass server authorization and device/biometric gates before rendering sensitive data.
- Consent revocation must invalidate encrypted PHI and offline caches through the Kernel mobile privacy plugin.
- Audit and telemetry failures in sensitive flows must be observable and must not silently allow PHI access.
- Raw user-facing text and unlabeled interactive controls are guarded by Kernel-backed product checks.

## Remaining Evidence Work

- Produce staging/load evidence for policy latency, audit write latency, consent checks, and API performance.
- Complete formal compliance review and sign-off before any HIPAA production-readiness claim.
- Keep route, use-case, security, mobile, and docs truth matrices aligned with current code and tests.
- Continue removing product-local platform primitives when a Kernel plugin provides the reusable behavior.
