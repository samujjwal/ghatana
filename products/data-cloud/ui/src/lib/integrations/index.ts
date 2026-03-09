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
    useBrainAgents,
    useBrainInterventions,
    useBrainMemory,
    useBrainInteractions,
    DataCloudAgentProvider,
    type BrainAgent,
    type BrainIntervention,
} from './agent-integration';

// ============================================
// PLUGIN FRAMEWORK INTEGRATION
// ============================================
export {
    useDataCloudPlugins,
    usePluginInstallation,
    usePluginConfiguration,
    DataCloudPluginProvider,
    type DataCloudPlugin,
    type PluginCategory,
    DATA_CLOUD_PLUGIN_CATEGORIES,
} from './plugin-integration';

// ============================================
// REALTIME STREAMING INTEGRATION
// ============================================
export {
    useEventCloudStream,
    useBrainStateStream,
    useEntityChangeStream,
    DataCloudRealtimeProvider,
    useDataCloudRealtimeContext,
    type EventCloudEvent,
    type BrainStateUpdate,
    type EntityChangeEvent,
} from './realtime-integration';

// ============================================
// VISUALIZATION INTEGRATION
// ============================================
export {
    useDataCloudMetrics,
    useAvailableMetrics,
    useDashboards,
    useDashboard,
    useSystemHealth,
    useRecentEvents,
    DataCloudVisualizationProvider,
    useDataCloudVisualizationContext,
    type DataCloudMetric,
    type TimeRange,
    type DashboardConfig,
    type DashboardPanel,
    type SystemHealth,
} from './visualization-integration';
