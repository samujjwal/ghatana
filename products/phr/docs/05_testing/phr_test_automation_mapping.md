# PHR Platform — Test Automation Mapping

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** QA Lead  
**Approval status:** Execution-ready planning artifact  
**Classification:** Internal

This document maps testcase IDs to planned automation locations, owners, and execution tiers.

---

## 1. Planned suite locations

```text
products/phr/
  apps/api/test/contract/
  apps/api/test/integration/
  apps/api/test/security/
  apps/web/test/e2e/
  apps/web/test/accessibility/
  apps/mobile/test/e2e/
  qa/performance/
  qa/compliance/
```

---

## 2. Mapping by test pack

| Test IDs | Planned suite path | Execution tier | Owner |
| --- | --- | --- | --- |
| `API-001` to `API-024` | `apps/api/test/contract/core-mvp.contract.spec.ts` | smoke and regression | API QA owner |
| `API-025` to `API-026`, `API-028` | `apps/api/test/contract/data-input.contract.spec.ts` | regression | API QA owner |
| `API-027` to `API-029` | `apps/api/test/contract/export.contract.spec.ts` | regression | API QA owner |
| `API-034` to `API-042` | `apps/api/test/contract/emergency-and-fchv.contract.spec.ts` | regression | API QA owner |
| `API-043` to `API-048` | `apps/api/test/security/rate-limit-and-resilience.spec.ts` | regression | security QA owner |
| `API-049` to `API-058` | `apps/api/test/security/tenant-and-owasp.spec.ts` | regression and compliance | security QA owner |
| `API-059` to `API-062` | `apps/api/test/contract/caregiver-payments-referrals-imaging.contract.spec.ts` | regression | API QA owner |
| `SVC-001` to `SVC-012` | `apps/api/test/integration/core-modules.integration.spec.ts` | regression | integration QA owner |
| `SVC-016` to `SVC-026` | `apps/api/test/integration/tenant-consent-resilience.integration.spec.ts` | regression and compliance | integration QA owner |
| `SVC-027` to `SVC-037` | `apps/api/test/integration/document-input-and-fchv.integration.spec.ts` | regression | integration QA owner |
| `SVC-038` to `SVC-040` | `apps/api/test/integration/caregiver-payments-referrals-imaging.integration.spec.ts` | regression | integration QA owner |
| `UI-001` to `UI-021` | `apps/web/test/e2e/core-mvp-ui.spec.ts` | smoke and regression | web QA owner |
| `UI-022` to `UI-030` | `apps/web/test/e2e/emergency-fchv-ui.spec.ts` | regression | web QA owner |
| `UI-031` to `UI-038` | `apps/mobile/test/e2e/offline-and-locale.spec.ts` | regression | mobile QA owner |
| `UI-039` to `UI-044` | `apps/web/test/accessibility/wcag-audit.spec.ts` | regression and compliance | accessibility QA owner |
| `UI-045` to `UI-047` | `apps/web/test/e2e/caregiver-payments-referrals-imaging-ui.spec.ts` | regression | web QA owner |
| `NFR-001` to `NFR-015` | `qa/compliance/core-nfr-checklist.md` and `qa/compliance/core-nfr.spec.ts` | compliance | compliance lead |
| `NFR-016` to `NFR-020` | `qa/performance/core-latency.k6.js` | performance | performance QA owner |
| `NFR-021` to `NFR-035` | `apps/api/test/security/owasp-top10.spec.ts` | compliance | security lead |
| `NFR-036` to `NFR-040` | `qa/compliance/dpia-validation-checklist.md` | compliance | compliance lead |
| `NFR-041` to `NFR-047` | `apps/api/test/security/tenant-retention.spec.ts` | compliance | security QA owner |
| `NFR-048` to `NFR-051` | `qa/performance/asr-accuracy-benchmark.md` | scheduled non-functional | data-input QA owner |
| `NFR-052` to `NFR-054` | `qa/compliance/breach-notification-exercise.md` | release rehearsal | compliance lead |

---

## 3. Unimplemented suite placeholders

The following suite roots do not exist yet and must be created before implementation starts:

- `products/phr/apps/api/test/contract/`
- `products/phr/apps/api/test/integration/`
- `products/phr/apps/api/test/security/`
- `products/phr/apps/web/test/e2e/`
- `products/phr/apps/web/test/accessibility/`
- `products/phr/apps/mobile/test/e2e/`
- `products/phr/qa/performance/`
- `products/phr/qa/compliance/`

---

## 4. Mapping rule

No testcase ID may remain only in a design document. Every ID must point to a real suite file or a named placeholder path with an owner.