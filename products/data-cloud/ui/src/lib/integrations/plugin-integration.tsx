/**
 * Plugin Framework Integration for Data-Cloud
 *
 * Bridges @ghatana/plugin-framework with Data-Cloud's plugin system.
 * Provides hooks and providers for plugin marketplace and management.
 *
 * @doc.type module
 * @doc.purpose Plugin framework integration for Data-Cloud
 * @doc.layer frontend
 * @doc.pattern Integration
 */

import * as React from 'react';
import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

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
// API SERVICE
// ============================================

const PLUGIN_API_BASE = '/api/plugins';

async function fetchInstalledPlugins(): Promise<DataCloudPlugin[]> {
    const response = await fetch(`${PLUGIN_API_BASE}/installed`);
    if (!response.ok) throw new Error('Failed to fetch installed plugins');
    return response.json();
}

async function fetchMarketplacePlugins(options?: {
    category?: PluginCategory;
    search?: string;
}): Promise<DataCloudPlugin[]> {
    const params = new URLSearchParams();
    if (options?.category) params.set('category', options.category);
    if (options?.search) params.set('search', options.search);

    const response = await fetch(`${PLUGIN_API_BASE}/marketplace?${params}`);
    if (!response.ok) throw new Error('Failed to fetch marketplace plugins');
    return response.json();
}

async function fetchPluginHealth(pluginId: string): Promise<PluginHealth> {
    const response = await fetch(`${PLUGIN_API_BASE}/${pluginId}/health`);
    if (!response.ok) throw new Error('Failed to fetch plugin health');
    return response.json();
}

async function fetchAllPluginHealth(): Promise<Record<string, PluginHealth>> {
    const response = await fetch(`${PLUGIN_API_BASE}/health`);
    if (!response.ok) throw new Error('Failed to fetch plugin health');
    return response.json();
}

async function installPlugin(
    pluginId: string,
    options?: PluginInstallOptions
): Promise<PluginInstance> {
    const response = await fetch(`${PLUGIN_API_BASE}/${pluginId}/install`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(options ?? {}),
    });
    if (!response.ok) throw new Error('Failed to install plugin');
    return response.json();
}

async function uninstallPlugin(pluginId: string): Promise<void> {
    const response = await fetch(`${PLUGIN_API_BASE}/${pluginId}/uninstall`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to uninstall plugin');
}

async function enablePlugin(pluginId: string): Promise<PluginInstance> {
    const response = await fetch(`${PLUGIN_API_BASE}/${pluginId}/enable`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to enable plugin');
    return response.json();
}

async function disablePlugin(pluginId: string): Promise<PluginInstance> {
    const response = await fetch(`${PLUGIN_API_BASE}/${pluginId}/disable`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to disable plugin');
    return response.json();
}

async function updatePluginConfig(
    pluginId: string,
    config: Record<string, unknown>
): Promise<PluginInstance> {
    const response = await fetch(`${PLUGIN_API_BASE}/${pluginId}/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
    });
    if (!response.ok) throw new Error('Failed to update plugin config');
    return response.json();
}

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
 * <PluginMarketplace
 *   plugins={marketplace}
 *   installedPlugins={installed}
 *   onInstall={(id) => installPlugin(id)}
 * />
 * ```
 */
export function useDataCloudPlugins(options?: {
    category?: PluginCategory;
    search?: string;
    includeMarketplace?: boolean;
}) {
    const queryClient = useQueryClient();

    // Fetch installed plugins
    const {
        data: installed = [],
        isLoading: loadingInstalled,
        error: installedError,
        refetch: refetchInstalled,
    } = useQuery({
        queryKey: ['plugins', 'installed'],
        queryFn: fetchInstalledPlugins,
        staleTime: 30000,
    });

    // Fetch marketplace plugins
    const {
        data: marketplace = [],
        isLoading: loadingMarketplace,
        error: marketplaceError,
        refetch: refetchMarketplace,
    } = useQuery({
        queryKey: ['plugins', 'marketplace', options?.category, options?.search],
        queryFn: () => fetchMarketplacePlugins({
            category: options?.category,
            search: options?.search,
        }),
        staleTime: 60000,
        enabled: options?.includeMarketplace !== false,
    });

    // Fetch plugin health
    const {
        data: healthMap = {},
        isLoading: loadingHealth,
    } = useQuery({
        queryKey: ['plugins', 'health'],
        queryFn: fetchAllPluginHealth,
        refetchInterval: 30000,
    });

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
        isLoading: loadingInstalled || loadingMarketplace,
        loadingInstalled,
        loadingMarketplace,
        loadingHealth,
        error: installedError ?? marketplaceError,
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
    const queryClient = useQueryClient();

    const installMutation = useMutation({
        mutationFn: ({ pluginId, options }: { pluginId: string; options?: PluginInstallOptions }) =>
            installPlugin(pluginId, options),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
            queryClient.invalidateQueries({ queryKey: ['plugins', 'health'] });
        },
    });

    const uninstallMutation = useMutation({
        mutationFn: (pluginId: string) => uninstallPlugin(pluginId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
            queryClient.invalidateQueries({ queryKey: ['plugins', 'health'] });
        },
    });

    const enableMutation = useMutation({
        mutationFn: (pluginId: string) => enablePlugin(pluginId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
        },
    });

    const disableMutation = useMutation({
        mutationFn: (pluginId: string) => disablePlugin(pluginId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
        },
    });

    return {
        install: (pluginId: string, options?: PluginInstallOptions) =>
            installMutation.mutateAsync({ pluginId, options }),
        uninstall: uninstallMutation.mutateAsync,
        enable: enableMutation.mutateAsync,
        disable: disableMutation.mutateAsync,
        isInstalling: installMutation.isPending,
        isUninstalling: uninstallMutation.isPending,
        isEnabling: enableMutation.isPending,
        isDisabling: disableMutation.isPending,
        installError: installMutation.error,
        uninstallError: uninstallMutation.error,
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
    const queryClient = useQueryClient();

    const {
        data: plugin,
        isLoading: loadingPlugin,
    } = useQuery({
        queryKey: ['plugins', pluginId],
        queryFn: async () => {
            const plugins = await fetchInstalledPlugins();
            return plugins.find((p) => p.id === pluginId);
        },
        enabled: !!pluginId,
    });

    const {
        data: health,
        isLoading: loadingHealth,
    } = useQuery({
        queryKey: ['plugins', pluginId, 'health'],
        queryFn: () => fetchPluginHealth(pluginId!),
        enabled: !!pluginId,
        refetchInterval: 30000,
    });

    const updateMutation = useMutation({
        mutationFn: (config: Record<string, unknown>) =>
            updatePluginConfig(pluginId!, config),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['plugins', pluginId] });
            queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
        },
    });

    return {
        plugin,
        config: (plugin?.configSchema ? {} : undefined) as Record<string, unknown> | undefined, // Current config would come from instance
        schema: plugin?.configSchema,
        health,
        isLoading: loadingPlugin || loadingHealth,
        updateConfig: updateMutation.mutateAsync,
        isUpdating: updateMutation.isPending,
        updateError: updateMutation.error,
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
