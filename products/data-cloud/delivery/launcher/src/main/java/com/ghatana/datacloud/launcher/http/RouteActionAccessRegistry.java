package com.ghatana.datacloud.launcher.http;

import java.util.Map;

/**
 * Contract-backed route/action access registry.
 *
 * <p>Provides explicit access-level requirements for high-impact routes,
 * reducing reliance on path-prefix inference in security decisions.
 *
 * <p>DC-P1-04: Route entries are generated from OpenAPI contracts via
 * {@code scripts/generate-route-security-metadata.mjs}. Do not edit manually.
 * Regenerate with: {@code node scripts/generate-route-security-metadata.mjs}
 *
 * @doc.type class
 * @doc.purpose Route/action access-level registry for Data Cloud HTTP security
 * @doc.layer product
 * @doc.pattern Registry
 */
final class RouteActionAccessRegistry {

    // DC-P1-04: Auto-generated from OpenAPI contracts
    // Regenerate with: node scripts/generate-route-security-metadata.mjs
    private static final Map<String, DataCloudSecurityFilter.AccessLevel> ACCESS_BY_ACTION = Map.ofEntries(
        Map.entry("DELETE /api/v1/alerts/rules/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/analytics/queries/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/bindings/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/checkpoints/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/context/keys/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/deployments/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/entities/{collection}/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /api/v1/event-types/{id}/schemas/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/governance/policies/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /api/v1/memory/{id}/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/patterns/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/sessions/current", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/settings/keys/{id}/revoke", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /data-fabric/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /admin/capabilities/connectors", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /admin/capabilities/encodings", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /admin/capabilities/schemas", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /admin/capabilities/transforms", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/agents", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/agents/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/agents/{id}/memory", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/agents/catalog", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/agents/catalog/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/actions", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/advisories/fabric/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/advisories/quality/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/advisories/workflows/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/correlations", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/feedback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/quality-summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/suggestions", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/ai/suggestions/metrics", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/alerts", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/alerts/{id}/remediations", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/alerts/groups", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/alerts/rules", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/alerts/stream", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/alerts/suggestions", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/analytics/kpis", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/analytics/query/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/analytics/query/{id}/plan", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/anomalies", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/auth/platform-session", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/auth/roles", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/autonomy/domains", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/autonomy/domains/:domain", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/autonomy/level", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/autonomy/logs", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/autonomy/plan/:actionType", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/bindings", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/bindings/:bindingId", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/attention/thresholds", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/config", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/health", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/patterns", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/salience/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/stats", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/workspace", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/brain/workspace/stream", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/catalog/marketplace/agents", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/catalog/marketplace/agents/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/catalog/marketplace/agents/{id}/reviews", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/checkpoints", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/checkpoints/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/collections", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/collections/{id}/cost-report", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/compliance/legal-holds", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/compliance/posture", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/compliance/soc2/report", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/conformance", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/conformance/entity-store", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/conformance/event-log-store", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/connectors", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/connectors/{id}/health", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/connectors/{id}/schema", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/connectors/{id}/sync/status", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/context", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/context/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/context/{id}/lineage/trust", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/context/snapshot", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/costs/summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/data-products", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/data-quality/trust-scores", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/entities/{collection}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/entities/{collection}/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/entities/{id}/{id}/history", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/events", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/events/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/events/notifications", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/executions/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/executions/{id}/checkpoints", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/executions/{id}/logs", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/features/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/audit/summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/compliance/summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/degradation", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/inventory", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/kill-switch", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/ops/summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/policies", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/governance/policies/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/governance/privacy/pii-fields", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/privacy/verify", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/retention/policy", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/governance/security/egress", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/hitl/pending", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/learning/episodes", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/learning/policies", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/learning/status", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/learning/stream", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/lineage/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/lineage/{id}/impact", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/mastery/preview/decision", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/mastery/preview/retrieval", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/memory", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/memory/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/memory/{id}/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/models", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/models/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/patterns", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/patterns/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/pipelines", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/pipelines/{id}/executions", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/pipelines/{id}/executions/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/pipelines/{id}/executions/{id}/logs", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/plugins", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/plugins/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/plugins/{id}/sandbox", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/plugins/marketplace", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/queries/estimate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/reports", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/reports/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/runs", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/runs/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sessions", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sessions/current", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/settings", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/settings/approvals", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/keys", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/keys/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/notifications", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/preferences", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/profile", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/security", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/sovereign/audit", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sovereign/backup", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sovereign/conformance", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sovereign/data-residency", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sovereign/data-subject-controls", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sovereign/models", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sovereign/profile", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/sovereign/region-policy", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/surfaces", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/surfaces/schema", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/user-activity/recent", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/voice/intents", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /data-fabric/connectors", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /data-fabric/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /data-fabric/connectors/{id}/statistics", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /data-fabric/metrics", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /events/notifications", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /events/stream", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /governance/audit/summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /governance/compliance/summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /governance/degradation", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /governance/kill-switch", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /governance/ops/summary", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /governance/security/egress", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /health", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /health/deep", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /health/detail", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /info", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /lifecycle/changes/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /lifecycle/recertification/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /live", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /mcp/v1/tools", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /metrics", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /metrics/slo", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /ready", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PATCH /api/v1/settings/notifications", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PATCH /api/v1/settings/preferences", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PATCH /api/v1/settings/profile", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/agents", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/agents/{id}/execute", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/context/rank", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/memory/retention", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/next-action", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/quality/drift-detect", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/rag-feedback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/suggestions", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/suggestions/{id}/apply", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/suggestions/{id}/feedback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/ai/suggestions/stages", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/acknowledge", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/resolve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/auto-remediate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/escalate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/remediate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/remediate/rollback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/groups/{id}/resolve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/rules", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/suggestions/{id}/apply", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/aggregation", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/anomalies", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/anomalies/{id}/false-positive", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/automate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/explain", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/forecast", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/kpis", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/query", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/analytics/suggest", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/autonomy/feedback-policy", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/bindings/{id}/simulate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/brain/attention/elevate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/brain/explain", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/brain/patterns/match", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/catalog/marketplace/agents", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/catalog/marketplace/agents/{id}/install", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/catalog/marketplace/agents/{id}/reviews", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/catalog/marketplace/agents/{id}/simulate-install", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/checkpoints", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/collections/{id}/metadata", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/collections/{id}/migrate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/ccpa/opt-out", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/evidence-package", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/gdpr/access", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/gdpr/erasure", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/gdpr/portability", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/legal-holds", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/legal-holds/{id}/extend", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/compliance/legal-holds/{id}/release", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/connectors", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/connectors/{id}/disable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/enable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/rotate-credentials", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/sync", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/connectors/{id}/test", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/sync-health", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/suggest-mapping", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/context/{id}/rag", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/context/{collection}/rag-policy-check", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/data-products", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/data-products/{id}/contract-check", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/data-products/{id}/sla-monitor", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/data-products/{id}/subscribe", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/deployments", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/entities/{collection}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/entities/{collection}/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/event-types/{id}/schemas", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/event-types/{id}/schemas/{id}/bindings", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/events", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/events/batch", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/checkpoint", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/restore", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/retry", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/rollback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/features", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/governance/degradation", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/governance/kill-switch/activate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/governance/kill-switch/deactivate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/governance/policies", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/policies/{id}/toggle", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/policies/simulate", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/policy/evaluate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/governance/privacy/redact", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/recommend", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/governance/retention/classify", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/governance/retention/purge", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/security/scan", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/hitl/{id}/approve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/hitl/{id}/escalate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/hitl/{id}/reject", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/learning/policies/{id}/approve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/learning/policies/{id}/reject", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/learning/reflect", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/learning/review/{id}/approve", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/learning/review/{id}/reject", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/learning/trigger", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/mastery/learning-deltas/{id}/dry-run-promotion", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/mastery/obsolescence-events/process", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/memory/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/memory/{id}/search", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/models", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/models/{id}/promote", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/nlp/parse", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/operations/anomaly-group", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/operations/forecast", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/patterns", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/patterns/{id}/activate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/patterns/{id}/deactivate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/pipelines", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/pipelines/{id}/execute", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/pipelines/{id}/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/pipelines/{id}/optimise-hint", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/pipelines/draft", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/plugins/{id}/conformance", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/plugins/{id}/disable", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/plugins/{id}/enable", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/plugins/{id}/upgrade", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/plugins/{id}/validate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/queries/explain", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/queries/federated", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/query/nlq", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/reports", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/runs/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/session", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/sessions/cleanup", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/settings", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/settings/approval-request", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/approvals/{id}/approve", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/approvals/{id}/reject", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/keys", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/keys/{id}/rotate", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/security", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/sovereign/backup", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/sovereign/restore", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/sovereign/validate-transfer", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/user-activity/log", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/voice/intent", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/voice/intent/classify", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/workflows/analyze-risk", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/workflows/validate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /data-fabric/connectors", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /data-fabric/connectors/{id}/disable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /data-fabric/connectors/{id}/enable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /data-fabric/connectors/{id}/sync", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /data-fabric/connectors/{id}/test", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /governance/degradation", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /governance/kill-switch/activate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /governance/kill-switch/deactivate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /governance/policy/evaluate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /governance/security/scan", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /lifecycle/changes", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /lifecycle/changes/{id}/approve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /lifecycle/changes/{id}/reject", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /lifecycle/recertification", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /mcp/v1/tools", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/alerts/rules/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/autonomy/level", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/bindings/:bindingId", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/brain/attention/thresholds", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/context", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/deployments/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/event-types/{id}/schemas/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/governance/policies/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/memory/{id}/{id}/retain", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/patterns/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/sovereign/data-subject-controls", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/sovereign/profile", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /data-fabric/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // DC-P0-01: Action Plane canonical routes — all under /api/v1/action/*
        // Keys use {id} form matching normalizePath output.

        // Action Plane — pipeline management
        Map.entry("GET /api/v1/action/pipelines", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/pipelines", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/action/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/action/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/pipelines/{id}/execute", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/pipelines/{id}/executions", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/pipelines/{id}/executions/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/pipelines/{id}/executions/{id}/logs", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/pipelines/{id}/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Action Plane — execution management
        Map.entry("GET /api/v1/action/executions/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/executions/{id}/logs", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/executions/{id}/checkpoints", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/retry", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/rollback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/checkpoint", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/restore", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Action Plane — agent memory
        Map.entry("GET /api/v1/action/memory", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/memory/{agentId}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/memory/{agentId}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/memory/{agentId}/{memoryId}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/memory/{agentId}/search", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/action/memory/{agentId}/{memoryId}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/action/memory/{agentId}/{memoryId}/retain", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Action Plane — learning
        Map.entry("POST /api/v1/action/learning/trigger", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/learning/status", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/learning/review", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/learning/review/{id}/approve", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/learning/review/{id}/reject", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Action Plane — autonomy
        Map.entry("GET /api/v1/action/autonomy/level", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/autonomy/domains", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/autonomy/domains/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/autonomy/logs", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/autonomy/plan/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/autonomy/feedback-policy", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Action Plane — plugins
        Map.entry("GET /api/v1/action/plugins", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/plugins/marketplace", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/plugins/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/plugins/{id}/sandbox", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/plugins/{id}/enable", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/plugins/{id}/disable", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/plugins/{id}/upgrade", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/plugins/{id}/validate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/plugins/{id}/conformance", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Action Plane — agent catalog
        Map.entry("GET /api/v1/action/agents/catalog", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("GET /api/v1/action/agents/catalog/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR)

    );

    private RouteActionAccessRegistry() {
    }

    static DataCloudSecurityFilter.AccessLevel requiredAccess(String method, String path) {
        String normalized = normalizePath(path);
        return ACCESS_BY_ACTION.get(method.toUpperCase() + " " + normalized);
    }

    private static String normalizePath(String path) {
        // DC-P0-01: Normalize path to a canonical form for registry lookup.
        // Rules are ordered most-specific first. The catch-all at the end handles
        // template-style :param paths (used in test coverage verification).
        String normalized = path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");

        // Learning review approve/reject (both namespaces)
        normalized = normalized.replaceAll("/learning/review/[^/]+/(approve|reject)$", "/learning/review/{id}/$1");
        normalized = normalized.replaceAll("/action/learning/review/[^/]+/(approve|reject)$", "/action/learning/review/{id}/$1");

        // Connector paths (any sub-path under a connector ID)
        normalized = normalized.replaceAll("/connectors/[^/]+", "/connectors/{id}");

        // Settings keys
        normalized = normalized.replaceAll("/settings/keys/[^/]+/(rotate|revoke)$", "/settings/keys/{id}/$1");
        normalized = normalized.replaceAll("/settings/keys/[^/]+$", "/settings/keys/{id}");

        // Settings approvals
        normalized = normalized.replaceAll("/settings/approvals/[^/]+/(approve|reject)$", "/settings/approvals/{id}/$1");

        // Plugin paths (both namespaces)
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/{id}");
        normalized = normalized.replaceAll("/action/plugins/[^/]+", "/action/plugins/{id}");

        // Governance policies
        normalized = normalized.replaceAll("/governance/policies/[^/]+/toggle$", "/governance/policies/{id}/toggle");
        normalized = normalized.replaceAll("/governance/policies/[^/]+$", "/governance/policies/{id}");

        // Autonomy plan (both namespaces)
        normalized = normalized.replaceAll("/autonomy/plan/[^/]+$", "/autonomy/plan/{id}");
        normalized = normalized.replaceAll("/action/autonomy/plan/[^/]+$", "/action/autonomy/plan/{id}");

        // Context paths
        normalized = normalized.replaceAll("/context/keys/[^/]+$", "/context/keys/{id}");
        normalized = normalized.replaceAll("/context/[^/]+/rag-policy-check$", "/context/{collection}/rag-policy-check");

        // Action pipeline execution paths (most-specific nested forms first)
        normalized = normalized.replaceAll("/action/pipelines/[^/]+/executions/[^/]+/(cancel|retry|rollback|restore|logs)$", "/action/pipelines/{id}/executions/{id}/$1");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+/executions/[^/]+$", "/action/pipelines/{id}/executions/{id}");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+/executions$", "/action/pipelines/{id}/executions");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+/execute$", "/action/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+$", "/action/pipelines/{id}");

        // Legacy pipeline paths (most-specific first)
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions/[^/]+/cancel$", "/pipelines/{id}/executions/{id}/cancel");
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions/[^/]+/logs$", "/pipelines/{id}/executions/{id}/logs");
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions/[^/]+$", "/pipelines/{id}/executions/{id}");
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions$", "/pipelines/{id}/executions");
        normalized = normalized.replaceAll("/pipelines/[^/]+/execute$", "/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/pipelines/[^/]+/optimise-hint$", "/pipelines/{id}/optimise-hint");
        normalized = normalized.replaceAll("/pipelines/[^/]+$", "/pipelines/{id}");

        // Action execution paths (checkpoint/checkpoints/logs/cancel/retry/rollback/restore)
        normalized = normalized.replaceAll("/action/executions/[^/]+/(cancel|retry|rollback|restore|checkpoint|checkpoints|logs)$", "/action/executions/{id}/$1");
        normalized = normalized.replaceAll("/action/executions/[^/]+$", "/action/executions/{id}");

        // Legacy execution paths
        normalized = normalized.replaceAll("/executions/[^/]+/(cancel|retry|rollback|restore|checkpoint|checkpoints|logs)$", "/executions/{id}/$1");
        normalized = normalized.replaceAll("/executions/[^/]+$", "/executions/{id}");

        // Alert paths
        normalized = normalized.replaceAll("/alerts/groups/[^/]+/resolve$", "/alerts/groups/{id}/resolve");
        normalized = normalized.replaceAll("/alerts/suggestions/[^/]+/apply$", "/alerts/suggestions/{id}/apply");
        normalized = normalized.replaceAll("/alerts/rules/[^/]+$", "/alerts/rules/{id}");
        normalized = normalized.replaceAll("/alerts/[^/]+/remediations$", "/alerts/{id}/remediations");
        normalized = normalized.replaceAll("/alerts/[^/]+/(remediate|auto-remediate|escalate|acknowledge|resolve)$", "/alerts/{id}/$1");
        normalized = normalized.replaceAll("/alerts/[^/]+/remediate/rollback$", "/alerts/{id}/remediate/rollback");

        // Model paths
        normalized = normalized.replaceAll("/models/[^/]+/(promote)$", "/models/{id}/$1");
        normalized = normalized.replaceAll("/models/[^/]+$", "/models/{id}");

        // Action memory paths (most-specific nested forms first)
        normalized = normalized.replaceAll("/action/memory/[^/]+/[^/]+/retain$", "/action/memory/{agentId}/{memoryId}/retain");
        normalized = normalized.replaceAll("/action/memory/[^/]+/search$", "/action/memory/{agentId}/search");
        normalized = normalized.replaceAll("/action/memory/[^/]+/[^/]+$", "/action/memory/{agentId}/{memoryId}");
        normalized = normalized.replaceAll("/action/memory/[^/]+$", "/action/memory/{agentId}");

        // Entity paths (named params preserved for disambiguation)
        normalized = normalized.replaceAll("/entities/[^/]+/[^/]+$", "/entities/{collection}/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+$", "/entities/{collection}");

        // Analytics query paths
        normalized = normalized.replaceAll("/analytics/query/[^/]+/plan$", "/analytics/query/{id}/plan");
        normalized = normalized.replaceAll("/analytics/query/[^/]+$", "/analytics/query/{id}");
        normalized = normalized.replaceAll("/analytics/queries/[^/]+$", "/analytics/queries/{id}");

        // Checkpoint paths
        normalized = normalized.replaceAll("/checkpoints/[^/]+$", "/checkpoints/{id}");

        // Catch-all: convert any remaining ActiveJ-style :param segments to {id}.
        // This handles template paths used in router-coverage tests and any routes
        // not covered by specific rules above.
        normalized = normalized.replaceAll("/:[a-zA-Z][a-zA-Z0-9]*", "/{id}");

        return normalized;
    }
}
