# PHR Kernel-Native Product Lifecycle

> **Purpose**: Documents the current PHR Kernel ownership boundary, plugin dependency map, and remaining lifecycle evidence work.
> **Last updated**: 2026-05-31

## Ownership Boundary

PHR is a Kernel-native product. Product lifecycle, route visibility, policy dispatch, audit envelopes, safe telemetry, mobile privacy, FHIR validation, document/OCR lifecycle checks, i18n checks, accessibility checks, and plugin declarations must use Kernel contracts or Kernel plugins when those primitives exist.

PHR owns healthcare-specific behavior: domain services, route adapters, DTOs, FHIR providers, HIE adapters, consent semantics, emergency workflows, persistence adapters, audit event names, and UI behavior. YAPPC must not contain PHR-specific generators, visualizers, agents, docs, or checks; it may consume generic Kernel product contracts only.

## Current Integration Map

| Kernel capability | PHR integration | Current status |
| --- | --- | --- |
| Product registration | `products/phr/domain-pack-manifest.yaml`, `products/phr/plugin-bindings/phr-plugin-bindings.yaml` | Implemented |
| Route contract and shell | `products/phr/config/phr-route-contract.json`, web route plugin, entitlement routes | Implemented for current stable routes |
| Hidden route lifecycle | Provider, caregiver, and FCHV routes stay hidden and unmounted until promoted | Implemented |
| Policy plugin | Kernel policy plugin dispatches PHR healthcare decision providers | Implemented |
| Audit plugin | PHR audit service extends Kernel `DefaultAuditTrailService` with PHR persistence | Implemented |
| Observability plugin | Safe metric tag allowlist and sensitive tag guard | Implemented |
| Mobile privacy plugin | Encrypted cache/session clearing, consent invalidation, biometric gate support | Implemented for current mobile surfaces |
| FHIR/HL7 plugin | Kernel structural FHIR validation with PHR provider/storage ownership | Implemented for validation; broader adapters remain PHR-owned |
| Document/OCR plugin | Kernel upload policy, provenance, malware attestation, OCR transition validation | Implemented |
| Localization/accessibility plugins | Generic Kernel scanners back PHR wrapper checks | Implemented |

## Runtime Flow

```text
Kernel product contracts
  -> PHR route/use-case/policy/plugin manifests
  -> Kernel route, policy, audit, privacy, FHIR, document, i18n, and a11y plugins
  -> PHR domain providers and ActiveJ route adapters
  -> Web/mobile product shells and API clients
```

Sensitive flows must carry tenant, principal, role, persona, tier, facility, and correlation context. Missing policy context must fail closed. Emergency PHI rendering requires both server authorization and device/biometric approval on mobile.

## Remaining Work

| Work item | Status |
| --- | --- |
| Formal staging/load evidence for policy latency, audit latency, consent checks, and API performance | Pending |
| Formal HIPAA/security sign-off | Pending |
| Release readiness evidence freshness and scorecard gates | Out of scope for this iteration |
| Continued removal of product-local primitives where Kernel plugins now provide reusable behavior | Ongoing |

## Validation Anchors

- `node scripts/check-phr-plugin-manifest.mjs`
- `node scripts/check-phr-ia-coverage.mjs`
- `node scripts/generate-phr-doc-code-mismatch-evidence.mjs`
- `node scripts/check-phr-safe-telemetry.mjs`
- `node scripts/check-phr-i18n-conformance.mjs`
- `node scripts/check-phr-accessibility.mjs`
- Focused Kernel and PHR tests referenced in `products/phr/PHR_KERNEL_INTEGRATION_README.md`

## Related Documents

- [PHR Kernel Integration Status](../../PHR_KERNEL_INTEGRATION_README.md)
- [PHR Runtime Architecture](phr_runtime_architecture.md)
- [PHR Access Policy Matrix](../security/phr_access_policy_matrix.md)
- [PHR Current Implemented Surface](../current-state/generated-current-surface.md)
