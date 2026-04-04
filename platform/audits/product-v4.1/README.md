# Platform Product V4.1 Audit Index

Generated reports: 47
Output directory: /Users/samujjwal/Development/ghatana/platform/audits/product-v4.1

## Cross-Cutting Risks
- agent-catalog: no automated tests found for maintained source.
- contracts: meaningful duplicate symbol overlap detected (JsonSchemaBundleToPojoGenerator.java: contracts/src/main/java/com/ghatana/contracts/schema/JsonSchemaBundleToPojoGenerator.java, contracts/src/schemaGen/java/com/ghatana/contracts/schema/JsonSchemaBundleToPojoGenerator.java).
- typescript/accessibility-audit: stale/legacy files present (typescript/accessibility-audit/src/AccessibilityAuditor.old.ts, typescript/accessibility-audit/src/AccessibilityReportViewer.old.tsx).
- typescript/api: meaningful duplicate symbol overlap detected (client.ts: typescript/api/src/client.ts, typescript/realtime/src/client.ts).
- typescript/canvas: meaningful duplicate symbol overlap detected (accessibility.ts: typescript/canvas/src/accessibility/accessibility.ts, typescript/canvas/src/core/accessibility.ts, typescript/design-system/src/utils/accessibility.ts, typescript/foundation/platform-utils/src/accessibility.ts).
- typescript/canvas/flow-canvas: no automated tests found for maintained source.
- typescript/design-system: meaningful duplicate symbol overlap detected (accessibility.ts: typescript/canvas/src/accessibility/accessibility.ts, typescript/canvas/src/core/accessibility.ts, typescript/design-system/src/utils/accessibility.ts, typescript/foundation/platform-utils/src/accessibility.ts).
- typescript/foundation/platform-utils: meaningful duplicate symbol overlap detected (accessibility.ts: typescript/canvas/src/accessibility/accessibility.ts, typescript/canvas/src/core/accessibility.ts, typescript/design-system/src/utils/accessibility.ts, typescript/foundation/platform-utils/src/accessibility.ts).
- typescript/platform-shell: no automated tests found for maintained source.
- typescript/realtime: meaningful duplicate symbol overlap detected (client.ts: typescript/api/src/client.ts, typescript/realtime/src/client.ts).
- typescript/theme: meaningful duplicate symbol overlap detected (theme.ts: typescript/canvas/src/theme/theme.ts, typescript/theme/src/theme.ts).
- typescript/tokens: meaningful duplicate symbol overlap detected (validation.ts: typescript/canvas/src/topology/builder/validation.ts, typescript/tokens/src/validation.ts).
- java/agent-core: meaningful duplicate symbol overlap detected (HealthStatus.java: java/agent-core/src/main/java/com/ghatana/agent/HealthStatus.java, java/core/src/main/java/com/ghatana/platform/health/HealthStatus.java, java/database/src/main/java/com/ghatana/core/database/health/HealthStatus.java, java/domain/src/main/java/com/ghatana/platform/domain/agent/registry/HealthStatus.java).
- java/agent-memory: no automated tests found for maintained source.
- java/ai-integration: meaningful duplicate symbol overlap detected (Feature.java: java/ai-integration/src/main/java/com/ghatana/aiplatform/featurestore/Feature.java, java/core/src/main/java/com/ghatana/platform/core/feature/Feature.java).
- java/audio-video: meaningful duplicate symbol overlap detected (ValidationError.java: java/audio-video/src/main/java/com/ghatana/media/common/ValidationError.java, java/core/src/main/java/com/ghatana/platform/validation/ValidationError.java).
- java/audit: meaningful duplicate symbol overlap detected (AuditEvent.java: java/audit/src/main/java/com/ghatana/platform/audit/AuditEvent.java, java/domain/src/main/java/com/ghatana/platform/domain/audit/AuditEvent.java).
- java/core: meaningful duplicate symbol overlap detected (HealthStatus.java: java/agent-core/src/main/java/com/ghatana/agent/HealthStatus.java, java/core/src/main/java/com/ghatana/platform/health/HealthStatus.java, java/database/src/main/java/com/ghatana/core/database/health/HealthStatus.java, java/domain/src/main/java/com/ghatana/platform/domain/agent/registry/HealthStatus.java).
- java/database: meaningful duplicate symbol overlap detected (HealthStatus.java: java/agent-core/src/main/java/com/ghatana/agent/HealthStatus.java, java/core/src/main/java/com/ghatana/platform/health/HealthStatus.java, java/database/src/main/java/com/ghatana/core/database/health/HealthStatus.java, java/domain/src/main/java/com/ghatana/platform/domain/agent/registry/HealthStatus.java).
- java/domain: meaningful duplicate symbol overlap detected (HealthStatus.java: java/agent-core/src/main/java/com/ghatana/agent/HealthStatus.java, java/core/src/main/java/com/ghatana/platform/health/HealthStatus.java, java/database/src/main/java/com/ghatana/core/database/health/HealthStatus.java, java/domain/src/main/java/com/ghatana/platform/domain/agent/registry/HealthStatus.java).
- java/governance: meaningful duplicate symbol overlap detected (Role.java: java/domain/src/main/java/com/ghatana/platform/domain/auth/Role.java, java/governance/src/main/java/com/ghatana/platform/governance/rbac/Role.java, java/security/src/main/java/com/ghatana/platform/security/rbac/Role.java).
- java/kernel: meaningful duplicate symbol overlap detected (Policy.java: java/agent-core/src/main/java/com/ghatana/agent/framework/memory/Policy.java, java/kernel/src/main/java/com/ghatana/kernel/security/Policy.java, java/security/src/main/java/com/ghatana/platform/security/rbac/Policy.java).
- java/plugin: meaningful duplicate symbol overlap detected (PluginLoader.java: java/kernel/src/main/java/com/ghatana/kernel/loader/PluginLoader.java, java/plugin/src/main/java/com/ghatana/platform/plugin/loader/PluginLoader.java).
- java/security: meaningful duplicate symbol overlap detected (Policy.java: java/agent-core/src/main/java/com/ghatana/agent/framework/memory/Policy.java, java/kernel/src/main/java/com/ghatana/kernel/security/Policy.java, java/security/src/main/java/com/ghatana/platform/security/rbac/Policy.java).
- java/tool-runtime: meaningful duplicate symbol overlap detected (ApprovalRequest.java: java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/ApprovalRequest.java, java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/approval/ApprovalRequest.java).

## Orphan / Non-Module Directories Worth Reviewing
- java/cache exists without being treated as a first-class audited module in this pass.
- typescript/testing exists without being treated as a first-class audited module in this pass.

## Reports
- agent-catalog: agent-catalog.md (NO-GO)
- contracts: contracts.md (CONDITIONAL GO AFTER P0/P1)
- shared-services: shared-services.md (CONDITIONAL GO)
- testing: testing.md (CONDITIONAL GO)
- typescript/accessibility-audit: typescript__accessibility-audit.md (CONDITIONAL GO AFTER P0/P1)
- typescript/api: typescript__api.md (CONDITIONAL GO AFTER P0/P1)
- typescript/canvas: typescript__canvas.md (CONDITIONAL GO AFTER P0/P1)
- typescript/canvas/flow-canvas: typescript__canvas__flow-canvas.md (CONDITIONAL GO AFTER P0/P1)
- typescript/charts: typescript__charts.md (CONDITIONAL GO)
- typescript/code-editor: typescript__code-editor.md (CONDITIONAL GO)
- typescript/design-system: typescript__design-system.md (CONDITIONAL GO AFTER P0/P1)
- typescript/foundation/platform-utils: typescript__foundation__platform-utils.md (CONDITIONAL GO AFTER P0/P1)
- typescript/i18n: typescript__i18n.md (CONDITIONAL GO)
- typescript/platform-shell: typescript__platform-shell.md (CONDITIONAL GO AFTER P0/P1)
- typescript/realtime: typescript__realtime.md (CONDITIONAL GO AFTER P0/P1)
- typescript/sso-client: typescript__sso-client.md (CONDITIONAL GO AFTER P0/P1)
- typescript/theme: typescript__theme.md (CONDITIONAL GO AFTER P0/P1)
- typescript/tokens: typescript__tokens.md (CONDITIONAL GO AFTER P0/P1)
- typescript/ui-integration: typescript__ui-integration.md (CONDITIONAL GO)
- java/agent-core: java__agent-core.md (CONDITIONAL GO AFTER P0/P1)
- java/agent-memory: java__agent-memory.md (NO-GO)
- java/ai-integration: java__ai-integration.md (CONDITIONAL GO AFTER P0/P1)
- java/audio-video: java__audio-video.md (CONDITIONAL GO AFTER P0/P1)
- java/audit: java__audit.md (CONDITIONAL GO AFTER P0/P1)
- java/billing: java__billing.md (CONDITIONAL GO)
- java/config: java__config.md (CONDITIONAL GO)
- java/connectors: java__connectors.md (CONDITIONAL GO)
- java/core: java__core.md (CONDITIONAL GO AFTER P0/P1)
- java/data-governance: java__data-governance.md (CONDITIONAL GO)
- java/database: java__database.md (CONDITIONAL GO AFTER P0/P1)
- java/distributed-cache: java__distributed-cache.md (CONDITIONAL GO)
- java/domain: java__domain.md (CONDITIONAL GO AFTER P0/P1)
- java/governance: java__governance.md (CONDITIONAL GO AFTER P0/P1)
- java/http: java__http.md (CONDITIONAL GO)
- java/identity: java__identity.md (CONDITIONAL GO AFTER P0/P1)
- java/incident-response: java__incident-response.md (CONDITIONAL GO)
- java/kernel: java__kernel.md (CONDITIONAL GO AFTER P0/P1)
- java/kernel-persistence: java__kernel-persistence.md (CONDITIONAL GO)
- java/observability: java__observability.md (CONDITIONAL GO)
- java/plugin: java__plugin.md (CONDITIONAL GO AFTER P0/P1)
- java/policy-as-code: java__policy-as-code.md (CONDITIONAL GO AFTER P0/P1)
- java/runtime: java__runtime.md (CONDITIONAL GO)
- java/security: java__security.md (CONDITIONAL GO AFTER P0/P1)
- java/security-analytics: java__security-analytics.md (CONDITIONAL GO AFTER P0/P1)
- java/testing: java__testing.md (CONDITIONAL GO)
- java/tool-runtime: java__tool-runtime.md (CONDITIONAL GO AFTER P0/P1)
- java/workflow: java__workflow.md (CONDITIONAL GO)
