/**
 * Plugin Framework Integration for Data-Cloud
 *
 * Bridges @ghatana/plugin-framework with Data-Cloud's plugin system.
 * Provides hooks and providers for bundled plugin inventory and
 * boundary-aware management.
 *
 * @doc.type module
 * @doc.purpose Plugin framework integration for Data-Cloud
 * @doc.layer frontend
 * @doc.pattern Integration
 */

import * as React from 'react';
import { createContext, useContext, useMemo, type ReactNode } from 'react';
import {
    PLUGIN_INTEGRATION_BOUNDARY_MESSAGE,
    createRuntimeBoundaryError,
} from '@/lib/runtime-boundaries';

export { PLUGIN_INTEGRATION_BOUNDARY_MESSAGE } from '@/lib/runtime-boundaries';

function createPluginBoundaryError(): Error {
    return createRuntimeBoundaryError(PLUGIN_INTEGRATION_BOUNDARY_MESSAGE);
}

// ============================================
// BASE TYPES (Local definitions to avoid import issues)
// ============================================

/** Plugin status */
export type PluginStatus = 'installed' | 'enabled' | 'disabled' | 'error' | 'pending' | 'updating';

/** Plugin category */
export type PluginCategory =
    | 'storage'
    | 'connector'
    | 'processor'
    | 'transformer'
    | 'validator'
    | 'exporter'
    | 'visualization'
    | 'governance';

/** Plugin descriptor */
export interface PluginDescriptor {
    id: string;
    name: string;
    description: string;
    version: string;
    author: string;
    category: PluginCategory;
    icon?: string;
    homepage?: string;
    repository?: string;
    tags?: string[];
    dependencies?: string[];
}

/** Plugin config schema field */
export interface PluginConfigField {
    key: string;
    type: 'string' | 'number' | 'boolean' | 'select' | 'multiselect' | 'json';
    label: string;
    description?: string;
    required?: boolean;
    default?: unknown;
    options?: Array<{ value: string | number; label: string }>;
    validation?: {
        min?: number;
        max?: number;
        pattern?: string;
        message?: string;
    };
}

/** Plugin config schema */
export interface PluginConfigSchema {
    fields: PluginConfigField[];
    sections?: Array<{
        id: string;
        title: string;
        description?: string;
        fields: string[];
    }>;
}

/** Plugin instance */
export interface PluginInstance {
    id: string;
    pluginId: string;
    descriptor: PluginDescriptor;
    status: PluginStatus;
    config: Record<string, unknown>;
    configSchema?: PluginConfigSchema;
    installedAt: string;
    updatedAt: string;
    enabledAt?: string;
    error?: string;
}

/** Plugin health */
export interface PluginHealth {
    pluginId: string;
    status: 'healthy' | 'degraded' | 'unhealthy' | 'unknown';
    lastCheck: string;
    uptime?: number;
    metrics?: {
        requestsPerMinute?: number;
        errorRate?: number;
        avgLatency?: number;
    };
    issues?: Array<{
        severity: 'low' | 'medium' | 'high' | 'critical';
        message: string;
        timestamp: string;
    }>;
}

// ============================================
// DATA-CLOUD SPECIFIC TYPES
// ============================================

/**
 * Data-Cloud Plugin - extends generic plugin with DC-specific properties
 */
export interface DataCloudPlugin extends PluginDescriptor {
    /** Plugin license type */
    license: 'open-source' | 'commercial' | 'enterprise';
    /** Optional configuration schema */
    configSchema?: PluginConfigSchema;
    /** Supported entity types */
    supportedEntityTypes?: string[];
    /** Required permissions */
    requiredPermissions?: string[];
    /** Governance compliance */
    compliance?: {
        gdpr: boolean;
        hipaa: boolean;
        sox: boolean;
    };
}

/**
 * Plugin installation options
 */
export interface PluginInstallOptions {
    /** Enable immediately after install */
    enableOnInstall?: boolean;
    /** Initial configuration */
    initialConfig?: Record<string, unknown>;
    /** Target namespace/tenant */
    tenantId?: string;
}

// ============================================
// DATA-CLOUD PLUGIN CATEGORIES
// ============================================

export const DATA_CLOUD_PLUGIN_CATEGORIES: Array<{
    id: PluginCategory;
    label: string;
    description: string;
    icon: string;
}> = [
        {
            id: 'storage',
            label: 'Storage Connectors',
            description: 'Connect to databases, data lakes, and storage systems',
            icon: '💾',
        },
        {
            id: 'connector',
            label: 'Data Sources',
            description: 'Connect to external data sources and APIs',
            icon: '🔌',
        },
        {
            id: 'processor',
            label: 'Data Processors',
            description: 'Transform and process data',
            icon: '⚙️',
        },
        {
            id: 'transformer',
            label: 'Transformers',
            description: 'Data transformation and mapping',
            icon: '🔄',
        },
        {
            id: 'validator',
            label: 'Validators',
            description: 'Data quality and validation',
            icon: '✅',
        },
        {
            id: 'exporter',
            label: 'Exporters',
            description: 'Export data to external systems',
            icon: '📤',
        },
        {
            id: 'visualization',
            label: 'Visualizations',
            description: 'Custom visualization components',
            icon: '📊',
        },
        {
            id: 'governance',
            label: 'Governance',
            description: 'Data governance and compliance tools',
            icon: '🛡️',
        },
    ];

// ============================================
// HOOKS
// ============================================

/**
 * Hook for Data-Cloud plugin management
 *
 * @example
 * ```tsx
 * const { installed, marketplace, isLoading } = useDataCloudPlugins();
 *
 * // marketplace remains empty until launcher-backed catalog routes exist
 * ```
 */
export function useDataCloudPlugins(options?: {
    category?: PluginCategory;
    search?: string;
    includeMarketplace?: boolean;
}) {
    void options;
    const installed: DataCloudPlugin[] = [];
    const marketplace: DataCloudPlugin[] = [];
    const healthMap: Record<string, PluginHealth> = {};
    const error = createPluginBoundaryError();
    const refetchInstalled = async () => {
        throw createPluginBoundaryError();
    };
    const refetchMarketplace = async () => {
        throw createPluginBoundaryError();
    };

    // Stats
    const stats = useMemo(() => {
        const byCategory: Record<string, number> = {};
        const byStatus: Record<string, number> = {};
        let healthy = 0;
        let unhealthy = 0;

        installed.forEach((plugin) => {
            byCategory[plugin.category] = (byCategory[plugin.category] ?? 0) + 1;
            const health = healthMap[plugin.id];
            if (health?.status === 'healthy') healthy++;
            else unhealthy++;
        });

        return {
            total: installed.length,
            marketplace: marketplace.length,
            healthy,
            unhealthy,
            byCategory,
        };
    }, [installed, marketplace, healthMap]);

    return {
        installed,
        marketplace,
        dataCloudPlugins: installed,
        healthMap,
        stats,
        isLoading: false,
        loadingInstalled: false,
        loadingMarketplace: false,
        loadingHealth: false,
        error,
        refetchInstalled,
        refetchMarketplace,
    };
}

/**
 * Hook for plugin installation operations
 *
 * @example
 * ```tsx
 * const { install, uninstall, isInstalling } = usePluginInstallation();
 *
 * <button onClick={() => install(pluginId, { enableOnInstall: true })}>
 *   Install
 * </button>
 * ```
 */
export function usePluginInstallation() {
    const rejectUnsupported = async () => {
        throw createPluginBoundaryError();
    };

    return {
        install: (pluginId: string, options?: PluginInstallOptions) => {
            void pluginId;
            void options;
            return rejectUnsupported();
        },
        uninstall: (pluginId: string) => {
            void pluginId;
            return rejectUnsupported();
        },
        enable: (pluginId: string) => {
            void pluginId;
            return rejectUnsupported();
        },
        disable: (pluginId: string) => {
            void pluginId;
            return rejectUnsupported();
        },
        isInstalling: false,
        isUninstalling: false,
        isEnabling: false,
        isDisabling: false,
        installError: createPluginBoundaryError(),
        uninstallError: createPluginBoundaryError(),
    };
}

/**
 * Hook for plugin configuration
 *
 * @example
 * ```tsx
 * const { config, updateConfig, schema } = usePluginConfiguration(pluginId);
 *
 * <PluginConfigForm
 *   pluginId={pluginId}
 *   config={config}
 *   schema={schema}
 *   onSave={(newConfig) => updateConfig(newConfig)}
 * />
 * ```
 */
export function usePluginConfiguration(pluginId: string | undefined) {
    void pluginId;
    const plugin = undefined as DataCloudPlugin | undefined;
    const health = undefined as PluginHealth | undefined;
    const updateConfig = async (config: Record<string, unknown>) => {
        void config;
        throw createPluginBoundaryError();
    };

    return {
        plugin,
        config: (plugin?.configSchema ? {} : undefined) as Record<string, unknown> | undefined, // Current config would come from instance
        schema: plugin?.configSchema,
        health,
        isLoading: false,
        updateConfig,
        isUpdating: false,
        updateError: createPluginBoundaryError(),
    };
}

// ============================================
// CONTEXT & PROVIDER
// ============================================

interface DataCloudPluginContextValue {
    installed: PluginDescriptor[];
    marketplace: PluginDescriptor[];
    healthMap: Record<string, PluginHealth>;
    isLoading: boolean;
    install: (pluginId: string, options?: PluginInstallOptions) => Promise<PluginInstance>;
    uninstall: (pluginId: string) => Promise<void>;
    enable: (pluginId: string) => Promise<PluginInstance>;
    disable: (pluginId: string) => Promise<PluginInstance>;
}

const DataCloudPluginContext = createContext<DataCloudPluginContextValue | null>(null);

/**
 * Provider for Data-Cloud plugin integration
 *
 * @example
 * ```tsx
 * function App() {
 *   return (
 *     <DataCloudPluginProvider>
 *       <PluginsPage />
 *     </DataCloudPluginProvider>
 *   );
 * }
 * ```
 */
export function DataCloudPluginProvider({ children }: { children: ReactNode }) {
    const pluginsHook = useDataCloudPlugins({ includeMarketplace: true });
    const installationHook = usePluginInstallation();

    const value: DataCloudPluginContextValue = useMemo(
        () => ({
            installed: pluginsHook.installed,
            marketplace: pluginsHook.marketplace,
            healthMap: pluginsHook.healthMap,
            isLoading: pluginsHook.isLoading,
            install: installationHook.install,
            uninstall: installationHook.uninstall,
            enable: installationHook.enable,
            disable: installationHook.disable,
        }),
        [pluginsHook, installationHook]
    );

    return (
        <DataCloudPluginContext.Provider value={value}>
            {children}
        </DataCloudPluginContext.Provider>
    );
}

/**
 * Hook to access Data-Cloud plugin context
 */
export function useDataCloudPluginContext() {
    const context = useContext(DataCloudPluginContext);
    if (!context) {
        throw new Error('useDataCloudPluginContext must be used within DataCloudPluginProvider');
    }
    return context;
}
