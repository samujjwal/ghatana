/**
 * Canonical runtime-boundary messages for unsupported Data Cloud launcher surfaces.
 *
 * @doc.type module
 * @doc.purpose Shared runtime-boundary messages and errors
 * @doc.layer frontend
 * @doc.pattern Registry
 */

export const ALERTS_UNSUPPORTED_MESSAGE =
  'Alert management APIs are not exposed by the current Data Cloud launcher API.';

export const AGENT_REGISTRY_BOUNDARY_MESSAGE =
  'Agent registration, deregistration, execution history, and live registry events are not exposed by the current Data Cloud launcher API.';

export const SUGGESTION_SERVICE_BOUNDARY_MESSAGE =
  'Workflow suggestion feedback APIs are not exposed by the current Data Cloud launcher API.';

export const WORKFLOW_CLIENT_BOUNDARY_MESSAGE =
  'Workflow execution detail, template browsing, workflow suggestions, and remote validation are not exposed by the current Data Cloud launcher API.';

export const PLUGIN_INTEGRATION_BOUNDARY_MESSAGE =
  'Plugin marketplace, health, installation, and configuration integration APIs are not exposed by the current Data Cloud launcher API.';

export const VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE =
  'Visualization metrics, analytics views, detailed health, and recent-event integration APIs are not exposed by the current Data Cloud launcher API.';

export const BRAIN_INTEGRATION_BOUNDARY_MESSAGE =
  'Brain agent management and intervention APIs are not exposed by the current Data Cloud launcher API.';

export const SCHEMA_SUGGESTION_BOUNDARY_MESSAGE =
  'Schema suggestion requires a collection-scoped canonical route and is not exposed by the current Data Cloud launcher through this hook.';

export const AI_COLLABORATOR_BOUNDARY_MESSAGE =
  'Workflow recommendation and feedback APIs are not exposed by the current Data Cloud launcher API.';

export const AUTONOMY_POLICY_UPDATE_BOUNDARY_MESSAGE =
  'Domain-level autonomy policy updates are not exposed by the current Data Cloud HTTP API; only /api/v1/autonomy/level is writable.';

export const BRAIN_MEMORY_STORE_BOUNDARY_MESSAGE =
  'Brain memory store is not exposed by the current Data Cloud HTTP API without an explicit agentId-backed memory route.';

export const DATASET_CATALOG_BOUNDARY_MESSAGE =
  'Collection-scoped dataset catalog routes are not exposed by the current Data Cloud launcher API.';

export const DATASET_DETAIL_BOUNDARY_MESSAGE =
  'Collection-scoped dataset detail routes are not exposed by the current Data Cloud launcher API.';

export const LINEAGE_GRAPH_BOUNDARY_MESSAGE =
  'Lineage graph APIs are not exposed by the current Data Cloud launcher. Use the Data Explorer lineage preview instead.';

export const IMPACT_ANALYSIS_BOUNDARY_MESSAGE =
  'Impact analysis APIs are not exposed by the current Data Cloud launcher.';

export const QUERY_VALIDATION_BOUNDARY_MESSAGE =
  'Standalone query validation is not exposed by the current Data Cloud launcher API.';

export const GLOBAL_SEARCH_BOUNDARY_MESSAGE =
  'Global cross-catalog search is not exposed by the current Data Cloud launcher API.';

export const EXECUTION_MONITOR_BOUNDARY_MESSAGE =
  'Execution detail and log streaming are not exposed by the current Data Cloud launcher API.';

export const EXECUTION_BY_ID_BOUNDARY_MESSAGE =
  'Execution-by-ID lookup is not exposed by the current Data Cloud launcher API.';

export const COLLECTION_AGNOSTIC_SCHEMA_SUGGESTION_BOUNDARY_MESSAGE =
  'Collection-agnostic schema suggestion is not exposed by the current Data Cloud launcher API.';

export const CROSS_COLLECTION_SEARCH_BOUNDARY_MESSAGE =
  'Cross-collection search is not exposed by the current Data Cloud launcher API. Provide a collectionId to use canonical entity search.';

export const AI_ENRICHMENT_SUGGESTION_BOUNDARY_MESSAGE =
  'Entity enrichment suggestions are not exposed by the current Data Cloud launcher API.';

export const AI_SEMANTIC_SEARCH_BOUNDARY_MESSAGE =
  'Semantic search is not exposed by the current Data Cloud launcher API through this helper.';

export const AI_QUERY_RECOMMENDATIONS_BOUNDARY_MESSAGE =
  'Query recommendations are not exposed by the current Data Cloud launcher API through this helper.';

export const AI_LINEAGE_EXPLANATION_BOUNDARY_MESSAGE =
  'Lineage explanation is not exposed by the current Data Cloud launcher API.';

export const AI_DATA_QUALITY_ASSESSMENT_BOUNDARY_MESSAGE =
  'AI-backed data quality assessment is not exposed by the current Data Cloud launcher API through this helper.';

export const AI_RELATED_ENTITY_DISCOVERY_BOUNDARY_MESSAGE =
  'Related-entity discovery is not exposed by the current Data Cloud launcher API through this helper.';

// DC-P1-009: Policy CRUD lifecycle is complete - boundary messages removed
// The following governance policy operations are fully implemented:
// - POST /api/v1/governance/policies (create)
// - PUT /api/v1/governance/policies/{id} (update)
// - DELETE /api/v1/governance/policies/{id} (delete)
// - POST /api/v1/governance/policies/{id}/toggle (toggle)
// Backend handlers: DataLifecycleHandler.java
// UI mutations: TrustCenter.tsx with capability gating
// Service methods: governance.service.ts

export const QUALITY_PII_MASK_BOUNDARY_MESSAGE =
  'Bulk field masking is not exposed by the current Data Cloud API.';

export const QUALITY_VALIDATION_RULE_CREATE_BOUNDARY_MESSAGE =
  'Validation rule creation is not exposed by the current Data Cloud API.';

export const QUALITY_VALIDATION_RULE_UPDATE_BOUNDARY_MESSAGE =
  'Validation rule updates are not exposed by the current Data Cloud API.';

export const QUALITY_VALIDATION_RULE_DELETE_BOUNDARY_MESSAGE =
  'Validation rule deletion is not exposed by the current Data Cloud API.';

export const PLUGIN_CONFIGURATION_BOUNDARY_MESSAGE =
  'Plugin configuration is not exposed by the bundled Data Cloud launcher API.';

export const PLUGIN_UNINSTALL_BOUNDARY_MESSAGE =
  'Bundled plugins cannot be uninstalled at runtime. Disable the plugin instead.';

export const PLUGIN_MARKETPLACE_BOUNDARY_MESSAGE =
  'Marketplace metadata is not exposed by the bundled Data Cloud launcher API.';

export const PLUGIN_INSTALL_BOUNDARY_MESSAGE =
  'Runtime plugin installation is not supported. Plugins are bundled at build time.';

export const PLUGIN_UPLOAD_BOUNDARY_MESSAGE =
  'Runtime plugin upload is not supported. Deploy a new launcher build with the bundled plugin.';

export const SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_TITLE =
  'AI assist unavailable';

export const SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_DETAIL =
  'Runtime capability truth reports AI assist as unavailable. You can still capture intent here and continue in the manual pipeline editor.';

export const SMART_WORKFLOW_AI_ASSIST_DEGRADED_TITLE =
  'AI assist degraded';

export const SMART_WORKFLOW_AI_ASSIST_DEGRADED_DETAIL =
  'Runtime capability truth reports AI assist as degraded. Suggestions should be treated as advisory only.';

export const WORKFLOW_HINTS_UNAVAILABLE_TITLE =
  'AI optimisation hints unavailable';

export const WORKFLOW_HINTS_UNAVAILABLE_DETAIL =
  'Runtime capability truth reports AI assist as unavailable in this deployment. Pipeline monitoring remains available without generated hints.';

export const WORKFLOW_HINTS_DEGRADED_TITLE =
  'AI optimisation hints degraded';

export const WORKFLOW_HINTS_DEGRADED_DETAIL =
  'AI assist is degraded. Any generated hints should be treated as advisory.';

export const SQL_FEDERATED_QUERY_UNAVAILABLE_DETAIL =
  'Federated query is unavailable until the Trino capability is active.';

export const SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_TITLE =
  'Optional Query Dependencies Are Not Fully Available';

export const SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_DETAIL =
  'The launcher reports degraded or unavailable query dependencies. Direct analytics and federated query behaviors may fail explicitly until operators configure the missing services.';

export const PLUGINS_CATALOG_BOUNDARY_DETAIL =
  'The canonical backend only exposes bundled plugin inventory plus enable, disable, and upgrade-intent endpoints. Marketplace browsing, runtime installation, and custom uploads are intentionally unavailable in this launcher.';

export const PLUGIN_COMPATIBILITY_BOUNDARY_WARNING =
  'Compatibility checks are limited to bundled plugins already present in the launcher build.';

export const PLUGIN_DELIVERY_BOUNDARY_DETAIL =
  'To add or upgrade a plugin, publish a new Data Cloud server build that includes the updated bundled plugin artifact.';

export const PLUGIN_DELIVERY_BOUNDARY_CONTINUATION =
  'Runtime upload and hot-swap flows were removed here to match the actual launcher capability boundary.';

export const PLUGINS_INVENTORY_HEADER_DETAIL =
  'Monitor the bundled plugins shipped with the current launcher build';

export const PLUGINS_EMPTY_STATE_DETAIL =
  'No bundled plugins are currently registered in this launcher build';

export const PLUGIN_UPGRADE_BOUNDARY_CHANGELOG =
  'Bundled plugin upgrades require deploying a new launcher build.';

export const PLUGIN_RELEASE_NOTES_CHANGELOG =
  'Use the server release notes to review included plugin changes.';

export const PLUGIN_HOT_SWAP_BOUNDARY_CHANGELOG =
  'Runtime hot-swap is intentionally unavailable in the standalone launcher.';

export const PLUGIN_RUNTIME_TOGGLE_DOC_COMMENT =
  '// Bundled plugins can only be toggled at runtime';

export const PLUGIN_BUNDLE_UPDATE_DOC_COMMENT =
  '// To change plugin contents or version, deploy a new launcher build';

export const PLUGIN_BUNDLE_UPDATE_DOC_CONTINUATION =
  '// that bundles the updated plugin artifact.';

export const AI_COLLABORATOR_BOUNDARY_TITLE =
  'AI collaborator unavailable in launcher mode';

export const AI_COLLABORATOR_CONTEXT_HINT =
  'Open or create a workflow to prepare recommendation requests once launcher support is added.';

export const AI_COLLABORATOR_FOOTER_NOTE =
  'Launcher boundary active for workflow recommendations';

export const EXECUTION_MONITOR_UNAVAILABLE_TITLE =
  'Execution Monitoring Unavailable';

export const EXECUTION_MONITOR_GUIDANCE_NOTE =
  'Use pipeline execution summaries and launcher-supported workflow pages instead of per-execution live monitoring.';

export const INSIGHTS_CAPABILITY_SNAPSHOT_NOTE =
  'Based on the current launcher capability registry snapshot.';

export const INSIGHTS_REGISTRY_REQUEST_NOTE =
  'Correlate this ID with launcher logs when capability truth looks inconsistent.';

export const COST_PREDICTIVE_ROUTING_BOUNDARY_WARNING =
  'Predictive query routing is not exposed by the current Data Cloud API.';

export const COST_QUERY_OPTIMIZATION_BOUNDARY_MESSAGE =
  'Query optimization suggestions are not exposed by the current Data Cloud launcher API. '
  + 'The cost-report endpoint provides per-collection cost data only.';

export const COST_APPLY_OPTIMIZATION_BOUNDARY_MESSAGE =
  'Applying query optimization suggestions is not exposed by the current Data Cloud launcher API. '
  + 'Use the collection migrate endpoint to change storage tiers instead.';

export const QUALITY_CORRELATION_BOUNDARY_PREFIX =
  'Quality correlation is not exposed by the current Data Cloud API';

export function createQualityCorrelationBoundaryMessage(datasetId: string, timestamp: string): string {
  return `${QUALITY_CORRELATION_BOUNDARY_PREFIX} for ${datasetId} at ${timestamp}.`;
}

// ── Settings surface boundaries ─────────────────────────────────────────────

export const SETTINGS_API_KEY_LIST_BOUNDARY_MESSAGE =
  'API key listing is not exposed by the current Data Cloud launcher API. Key management requires the identity backend.';

export const SETTINGS_API_KEY_CREATE_BOUNDARY_MESSAGE =
  'API key creation is not exposed by the current Data Cloud launcher API. Key issuance requires the identity backend.';

export const SETTINGS_API_KEY_REVOKE_BOUNDARY_MESSAGE =
  'API key revocation is not exposed by the current Data Cloud launcher API. Key lifecycle management requires the identity backend.';

export const SETTINGS_PROFILE_BOUNDARY_MESSAGE =
  'User profile mutations are not exposed by the current Data Cloud launcher API. Profile management requires the launcher-backed identity API.';

export const SETTINGS_PREFERENCES_BOUNDARY_MESSAGE =
  'Preferences storage is not exposed by the current Data Cloud launcher API. Preference persistence requires the launcher-backed user API.';

export const SETTINGS_NOTIFICATION_PREFS_BOUNDARY_MESSAGE =
  'Notification preference updates are not exposed by the current Data Cloud launcher API. Notification routing requires the notification backend.';

export const SETTINGS_SURFACE_DISABLED_MESSAGE =
  'Settings surface is disabled by runtime feature-gate policy for this deployment profile.';

export const MEMORY_SURFACE_BOUNDARY_MESSAGE =
  'Memory Plane APIs are disabled by runtime feature-gate policy for this deployment profile.';

export const CONTEXT_SURFACE_BOUNDARY_MESSAGE =
  'Context Explorer APIs are disabled by runtime feature-gate policy for this deployment profile.';

// ── AI operations boundaries ─────────────────────────────────────────────────

export const AI_OPERATIONS_SUGGESTION_BOUNDARY_MESSAGE =
  'AI-assisted operation suggestions are not exposed by the current Data Cloud launcher. The ML platform and feature scoring service are not yet available.';

export const AI_OPERATIONS_CROSS_SURFACE_BOUNDARY_MESSAGE =
  'Cross-surface AI correlation (alerts ↔ workflows ↔ quality ↔ fabric) is not exposed by the current Data Cloud launcher. The unified operation event model is not yet available.';

export const AI_OPERATIONS_APPLY_BOUNDARY_MESSAGE =
  'Applying AI-assisted operation suggestions is not exposed by the current Data Cloud launcher API.';

export const AI_WORKFLOW_ADVISORY_BOUNDARY_MESSAGE =
  'AI workflow advisories are not exposed by the current Data Cloud launcher. Workflow analysis requires the ML platform backend.';

export const AI_QUALITY_ADVISORY_BOUNDARY_MESSAGE =
  'AI data quality advisories are not exposed by the current Data Cloud launcher. Quality scoring requires the ML platform backend.';

export const AI_FABRIC_ADVISORY_BOUNDARY_MESSAGE =
  'AI fabric tier advisories are not exposed by the current Data Cloud launcher. Fabric placement scoring requires the ML platform backend.';

export const ANALYTICS_AI_DISABLED_BOUNDARY_MESSAGE =
  'Analytics AI features (query suggestions, policy evaluation) are disabled by runtime feature-gate policy for this deployment profile.';

export const BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE =
  'Brain autonomy operations are disabled by runtime feature-gate policy for this deployment profile.';

export const GOVERNANCE_POLICY_CREATE_BOUNDARY_MESSAGE =
  'Governance policy creation is disabled by runtime feature-gate policy for this deployment profile.';

export const GOVERNANCE_POLICY_DELETE_BOUNDARY_MESSAGE =
  'Governance policy deletion is disabled by runtime feature-gate policy for this deployment profile.';

export const GOVERNANCE_POLICY_TOGGLE_BOUNDARY_MESSAGE =
  'Governance policy toggle is disabled by runtime feature-gate policy for this deployment profile.';

export const GOVERNANCE_POLICY_UPDATE_BOUNDARY_MESSAGE =
  'Governance policy update is disabled by runtime feature-gate policy for this deployment profile.';

export const GOVERNANCE_VIOLATION_RESOLUTION_BOUNDARY_MESSAGE =
  'Governance violation resolution is disabled by runtime feature-gate policy for this deployment profile.';

export const AI_OBSERVABILITY_DISABLED_BOUNDARY_MESSAGE =
  'AI observability and quality-summary APIs are disabled by runtime feature-gate policy for this deployment profile.';

export class UnsupportedRuntimeBoundaryError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UnsupportedRuntimeBoundaryError';
  }
}

export function createRuntimeBoundaryError(message: string): UnsupportedRuntimeBoundaryError {
  return new UnsupportedRuntimeBoundaryError(message);
}