/**
 * Data-Cloud Shared Library Integrations
 *
 * Integration hooks and providers that bridge @ghatana shared libraries
 * with Data-Cloud product-specific functionality.
 *
 * @doc.type module
 * @doc.purpose Shared library integration layer
 * @doc.layer frontend
 * @doc.pattern Integration
 */

// ============================================
// AGENT FRAMEWORK INTEGRATION
// ============================================
export {
  DataCloudAgentProvider,
  useBrainAgents,
  useBrainInteractions,
  useBrainInterventions,
  useBrainMemory,
  type BrainAgent,
  type BrainIntervention,
} from "./agent-integration";

// ============================================
// PLUGIN FRAMEWORK INTEGRATION
// ============================================
export {
  DATA_CLOUD_PLUGIN_CATEGORIES,
  DataCloudPluginProvider,
  useDataCloudPlugins,
  usePluginConfiguration,
  usePluginInstallation,
  type DataCloudPlugin,
  type PluginCategory,
} from "./plugin-integration";

// ============================================
// REALTIME STREAMING INTEGRATION
// ============================================
export {
  DataCloudRealtimeProvider,
  useBrainStateStream,
  useDataCloudRealtimeContext,
  useEntityChangeStream,
  useEventLogStream as useEventCloudStream,
  useEventLogStream,
  type BrainStateUpdate,
  type EntityChangeEvent,
  type EventLogEvent as EventCloudEvent,
  type EventLogEvent,
} from "./realtime-integration";

// ============================================
// VISUALIZATION INTEGRATION
// ============================================
export {
  DataCloudVisualizationProvider,
  useAvailableMetrics,
  useDashboard,
  useDashboards,
  useDataCloudMetrics,
  useDataCloudVisualizationContext,
  useRecentEvents,
  useSystemHealth,
  type DashboardConfig,
  type DashboardPanel,
  type DataCloudMetric,
  type SystemHealth,
  type TimeRange,
} from "./visualization-integration";
