# Ghatana Platform — Capability Matrix

> **Generated from:** `scripts/generate-architecture-docs.py`  
> **Last Updated:** 2026-04-29  
> **Purpose:** Authoritative cross-reference of products, plugins, kernel contracts, and compliance frameworks.

---

## 1. Module Inventory

### Kernel Core

| Module | Path | Role |
|--------|------|------|
| `kernel-core` | `platform-kernel/kernel-core` | Platform lifecycle, adapters, tenant context |
| `kernel-contracts` | `platform/contracts` | Shared event + domain contracts |

### Platform Plugins (8)

| Plugin ID | Path | Type | Capability Scope |
|-----------|------|------|-----------------|
| `plugin-audit-trail` | `platform-plugins/plugin-audit-trail` | GOVERNANCE | Tamper-evident event ledger |
| `plugin-compliance` | `platform-plugins/plugin-compliance` | GOVERNANCE | Rule-based regulatory evaluation |
| `plugin-consent` | `platform-plugins/plugin-consent` | SECURITY | Consent, authorization grant, delegation, revocation, and purpose-bound access lifecycle |
| `plugin-ledger` | `platform-plugins/plugin-ledger` | FINANCIAL | Double-entry ledger integrity |
| `plugin-fraud-detection` | `platform-plugins/plugin-fraud-detection` | SECURITY | ML-backed fraud signal |
| `plugin-risk-management` | `platform-plugins/plugin-risk-management` | ANALYTICS | Risk scoring and gating |
| `plugin-human-approval` | `platform-plugins/plugin-human-approval` | WORKFLOW | Human-in-the-loop approval gates |
| `core-observability` | `platform-plugins/core-observability` | OBSERVABILITY | OTel spans, metrics, MDC |

### Products (9)

| Product | Path | Domain |
|---------|------|--------|
| `phr` | `products/phr` | Regulated Healthcare |
| `finance` | `products/finance` | Regulated Finance |
| `digital-marketing` | `products/digital-marketing` | Customer Growth / Marketing Automation |
| `flashit` | `products/flashit` | Personal Reflection / Moments |
| `aep` | `products/aep` | AI/Agent Runtime |
| `yappc` | `products/yappc` | Code Generation / DevOps |
| `data-cloud` | `products/data-cloud` | Data Infrastructure |
| `dcmaar` | `products/dcmaar` | Data-Cloud MAR |
| `tutorputor` | `products/tutorputor` | Learning Platform |

---

## 2. Product × Plugin Capability Matrix

✅ = Required | ⚠️ = Recommended | — = Not applicable

| Plugin | PHR | Finance | AEP | YAPPC | Data-Cloud |
|--------|:---:|:-------:|:---:|:-----:|:----------:|
| `plugin-audit-trail` | ✅ | ✅ | ✅ | ⚠️ | ⚠️ |
| `plugin-compliance` | ✅ | ✅ | — | — | ⚠️ |
| `plugin-consent` | ✅ | — | ⚠️ | — | — |
| `plugin-ledger` | ⚠️ | ✅ | — | — | — |
| `plugin-fraud-detection` | — | ✅ | ⚠️ | — | — |
| `plugin-risk-management` | — | ✅ | — | — | — |
| `plugin-human-approval` | ✅ | ✅ | ⚠️ | ⚠️ | — |
| `core-observability` | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## 3. Product × Kernel Contract Matrix

| Kernel Contract | PHR | Finance | AEP | YAPPC | Data-Cloud |
|----------------|:---:|:-------:|:---:|:-----:|:----------:|
| `DataCloudKernelAdapter` | ✅ | ✅ | ✅ | ✅ | — (owner) |
| `AepKernelAdapter` | ⚠️ | ⚠️ | ✅ | ✅ | — |
| `KernelTenantContext` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `CrossScopeAuditService` | ✅ | ✅ | ⚠️ | ⚠️ | ⚠️ |
| `SecretProvider` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `KernelLifecycleAware` | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## 4. Compliance Framework Coverage Matrix

| Framework | Regulation | PHR | Finance | AEP | YAPPC |
|-----------|-----------|:---:|:-------:|:---:|:-----:|
| **HIPAA** | US Health Privacy | ✅ | — | — | — |
| **Nepal Directive 2081** | Nepal Health Data | ✅ | — | — | — |
| **Privacy Act 2075** | Nepal Privacy | ✅ | — | — | — |
| **SOX** | Finance Reporting | — | ✅ | — | — |
| **PCI-DSS** | Payment Security | — | ✅ | — | — |
| **GDPR** | Data Protection | ✅ | ✅ | ⚠️ | ⚠️ |

---

## 5. Architecture Boundary Rules (Enforced in CI)

| Rule ID | Description | Enforced By |
|---------|-------------|-------------|
| `ARCH-001` | Kernel modules cannot depend on products | ArchUnit + CI |
| `ARCH-002` | Plugins cannot depend on product implementations | ArchUnit + CI |
| `ARCH-003` | All async operations use ActiveJ Promise (no CompletableFuture) | ArchUnit + CI |
| `ARCH-010` | Plugins cannot depend on other plugin implementations | ArchUnit + CI |
| `ARCH-011` | Audit plugin isolated from billing/fraud/risk logic | ArchUnit + CI |
| `ARCH-012` | Billing plugin isolated from fraud/risk logic | ArchUnit + CI |

---

## 6. Kernel Bridge Extensions

| Bridge | Owner Product | Consumers | Purpose |
|--------|--------------|-----------|---------|
| `data-cloud-kernel-bridge` | data-cloud | phr, finance, aep, yappc | DataCloud capability into kernel |
| `aep-kernel-bridge` | aep | aep, yappc | AEP agent runtime into kernel |
| `yappc-kernel-bridge` | yappc | yappc | YAPPC code-gen into kernel |
| `phr-fhir-interop` | phr | phr | PHR-owned FHIR R4 server lifecycle + providers (product extension, not a kernel bridge) |

---

## 7. Test Coverage by Plugin

| Plugin | Unit Tests | Tenant Isolation | Contract Tests | Min Coverage |
|--------|:----------:|:----------------:|:--------------:|:------------:|
| `plugin-audit-trail` | ✅ | ✅ | ✅ | 80% |
| `plugin-compliance` | ✅ | ✅ (`ComplianceTenantIsolationTest`) | ✅ | 80% |
| `plugin-consent` | ✅ | ✅ | ✅ | 80% |
| `plugin-ledger` | ✅ | ✅ | ✅ | 80% |
| `plugin-fraud-detection` | ✅ | ✅ | ✅ | 80% |
| `plugin-risk-management` | ✅ | ✅ | ✅ | 80% |
| `plugin-human-approval` | ✅ | ✅ | ✅ | 80% |

---

## 8. Product Safety Proof Status

| Product | Consent-Before-Access | Tenant Isolation | Audit Trail | Ledger Integrity | AI Governance |
|---------|:---------------------:|:----------------:|:-----------:|:----------------:|:-------------:|
| PHR | ✅ | ✅ | ✅ | — | ✅ |
| Finance | — | ✅ | ✅ | ✅ | ✅ |
| AEP | — | ✅ | ⚠️ | — | ✅ |
| YAPPC | — | ✅ | ⚠️ | — | — |
| Data-Cloud | — | ✅ | ✅ | — | — |

---

## 9. Observability Coverage

| Component | Spans | Metrics | Structured Logs | Correlation ID | PHI Redaction |
|-----------|:-----:|:-------:|:---------------:|:--------------:|:-------------:|
| kernel-core | ✅ | ✅ | ✅ | ✅ | — |
| platform-plugins | ✅ | ✅ | ✅ | ✅ | — |
| products/phr | ✅ | ✅ | ✅ | ✅ | ✅ |
| products/finance | ✅ | ✅ | ✅ | ✅ | — |
| products/aep | ✅ | ✅ | ✅ | ✅ | — |

---

## 10. CI/CD Gate Coverage

| Gate | Scope | Workflow |
|------|-------|----------|
| Architecture boundary check | kernel + all plugins | `plugin-contract-tests.yml` |
| JaCoCo coverage (≥70–80%) | all Java modules | Gradle `check` task |
| SpotBugs SAST | kernel-core, all plugins | `security-scan.yml` |
| PMD code analysis | kernel-core, plugins, phr | `security-scan.yml` |
| License compliance | all Gradle modules | `license-compliance.yml` |
| Product manifest validation | all products | `manifest-validation.yml` |
| Runbook structure validation | `docs/runbooks/` | `validate-runbooks.yml` |
| Secret scanning (TruffleHog) | full repository | `security-scan.yml` |
