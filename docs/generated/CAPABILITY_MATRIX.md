# Ghatana Platform Capability Matrix

> Generated from `config/kernel-product-capability-registry.json`.

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
| `phr-compliance-rule-pack` | phr |
| `phr-healthcare-boundary-policy` | phr |

## Policy Vocabulary

Canonical actions: `read`, `write`, `delete`, `export`, `download`.

Product-specific actions must use a registered product namespace, for example `finance:settle` or `digital-marketing:launch`.
