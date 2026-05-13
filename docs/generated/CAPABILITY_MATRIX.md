# Ghatana Platform Capability Matrix

> Generated from `config/canonical-product-registry.json`, product manifests, and `config/kernel-product-capability-registry.json`.

## Product Capability Consumption

| Product | Capability | Adapter / Runtime Evidence | Tests / Conformance Evidence | Conformance Status |
| --- | --- | --- | --- | --- |
| `digital-marketing` | `approval-workflow` | dm-api<br>dm-application<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | config/observability/product-observability-flows.json<br>products/digital-marketing/dm-domain-packs/build.gradle.kts<br>products/digital-marketing/dm-domain-packs/domain-pack.json<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | agentDefinitions, bridge, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `digital-marketing` | `audit-trail` | dm-api<br>dm-application<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | config/observability/product-observability-flows.json<br>products/digital-marketing/dm-domain-packs/build.gradle.kts<br>products/digital-marketing/dm-domain-packs/domain-pack.json<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | agentDefinitions, bridge, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `digital-marketing` | `boundary-policy-evaluation` | dm-api<br>dm-application<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | config/observability/product-observability-flows.json<br>products/digital-marketing/dm-domain-packs/build.gradle.kts<br>products/digital-marketing/dm-domain-packs/domain-pack.json<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | agentDefinitions, bridge, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `digital-marketing` | `tenant-context` | dm-api<br>dm-application<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java -> products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | config/observability/product-observability-flows.json<br>products/digital-marketing/dm-domain-packs/build.gradle.kts<br>products/digital-marketing/dm-domain-packs/domain-pack.json<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java<br>products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java | agentDefinitions, bridge, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `finance` | `approval-workflow` | data-cloud-kernel-bridge<br>integration-testing<br>launcher | config/observability/product-observability-flows.json<br>products/finance/build.gradle.kts<br>products/finance/domain-pack-manifest.yaml | agentDefinitions, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `finance` | `audit-trail` | data-cloud-kernel-bridge<br>integration-testing<br>launcher | config/observability/product-observability-flows.json<br>products/finance/build.gradle.kts<br>products/finance/domain-pack-manifest.yaml | agentDefinitions, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `finance` | `boundary-policy-evaluation` | data-cloud-kernel-bridge<br>integration-testing<br>launcher | config/observability/product-observability-flows.json<br>products/finance/build.gradle.kts<br>products/finance/domain-pack-manifest.yaml | agentDefinitions, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `finance` | `tenant-context` | data-cloud-kernel-bridge<br>integration-testing<br>launcher | config/observability/product-observability-flows.json<br>products/finance/build.gradle.kts<br>products/finance/domain-pack-manifest.yaml | agentDefinitions, dataAccess, evaluationPacks, manifest, masteryBindings, observability, security |
| `flashit` | `audit-trail` | backend-agent<br>web-api | config/observability/product-observability-flows.json<br>products/flashit/build.gradle.kts<br>products/flashit/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |
| `flashit` | `boundary-policy-evaluation` | backend-agent<br>web-api | config/observability/product-observability-flows.json<br>products/flashit/build.gradle.kts<br>products/flashit/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |
| `flashit` | `human-approval` | backend-agent<br>web-api | config/observability/product-observability-flows.json<br>products/flashit/build.gradle.kts<br>products/flashit/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |
| `flashit` | `observability` | backend-agent<br>web-api | config/observability/product-observability-flows.json<br>products/flashit/build.gradle.kts<br>products/flashit/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |
| `phr` | `audit-trail` | data-cloud-kernel-bridge<br>fhir-server<br>launcher | config/observability/product-observability-flows.json<br>products/phr/build.gradle.kts<br>products/phr/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |
| `phr` | `boundary-policy-evaluation` | data-cloud-kernel-bridge<br>fhir-server<br>launcher | config/observability/product-observability-flows.json<br>products/phr/build.gradle.kts<br>products/phr/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |
| `phr` | `consent-enforcement` | data-cloud-kernel-bridge<br>fhir-server<br>launcher | config/observability/product-observability-flows.json<br>products/phr/build.gradle.kts<br>products/phr/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |
| `phr` | `tenant-context` | data-cloud-kernel-bridge<br>fhir-server<br>launcher | config/observability/product-observability-flows.json<br>products/phr/build.gradle.kts<br>products/phr/domain-pack-manifest.yaml | dataAccess, manifest, observability, security |

## Kernel Capabilities

| Capability | Description |
| --- | --- |
| `approval-workflow` | Kernel-managed approval workflow orchestration. |
| `audit-trail` | Kernel audit trail and immutable evidence emission. |
| `boundary-policy-evaluation` | Kernel policy boundary evaluation for product actions. |
| `consent-enforcement` | Kernel consent enforcement and delegation checks. |
| `human-approval` | Kernel human-in-the-loop approval capability. |
| `observability` | Kernel observability, trace, metric, and log propagation. |
| `tenant-context` | Kernel tenant context propagation and validation. |

## Platform Plugins

| Plugin | Description |
| --- | --- |
| `plugin-audit-trail` | Tamper-evident audit event ledger. |
| `plugin-compliance` | Product compliance rule evaluation. |
| `plugin-consent` | Consent grant, revocation, and purpose-bound access lifecycle. |
| `plugin-fraud-detection` | Fraud signal evaluation and gating. |
| `plugin-human-approval` | Human approval gates for sensitive workflows. |
| `plugin-ledger` | Double-entry ledger integrity. |
| `plugin-risk-management` | Risk scoring and product action gating. |

## Product Domain Packs

| Domain Pack | Owner |
| --- | --- |
| `digital-marketing-boundary-policy` | digital-marketing |
| `digital-marketing-compliance-rule-pack` | digital-marketing |
| `finance-compliance-rule-pack` | finance |
| `finance-trading-boundary-policy` | finance |
| `flashit-boundary-policy` | flashit |
| `flashit-compliance-rule-pack` | flashit |
| `kernel-canonical` | Canonical Kernel domain packs - products declare their own domain packs in manifests |
| `phr-compliance-rule-pack` | phr |
| `phr-healthcare-boundary-policy` | phr |

## Policy Vocabulary

Canonical actions: `read`, `write`, `delete`, `export`, `download`.

Product-specific actions must use a registered product namespace, for example `finance:settle` or `digital-marketing:launch`.
