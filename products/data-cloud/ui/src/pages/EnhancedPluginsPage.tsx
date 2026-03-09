/**
 * Enhanced Plugins Page
 *
 * This version integrates plugin management with Data-Cloud specific hooks
 * via the integration layer.
 *
 * Features:
 * - Plugin marketplace for discovery and installation
 * - Plugin configuration management
 * - Health monitoring dashboard
 * - Real-time health updates
 *
 * @doc.type page
 * @doc.purpose Plugin management with shared components
 * @doc.layer frontend
 */

import React, { useState, useMemo } from 'react';
import { Package, Settings, Activity, Search, Grid, List, Shield, Check, X, AlertTriangle } from 'lucide-react';
import {
    useDataCloudPlugins,
    usePluginInstallation,
    usePluginConfiguration,
    DATA_CLOUD_PLUGIN_CATEGORIES,
    type DataCloudPlugin,
    type PluginCategory,
} from '../lib/integrations/plugin-integration';
import { useSystemHealth } from '../lib/integrations/visualization-integration';

type TabType = 'installed' | 'marketplace' | 'health';
type ViewMode = 'grid' | 'list';

// Config field type for form rendering
interface ConfigField {
    key: string;
    label: string;
    type: 'string' | 'number' | 'boolean' | 'select';
    required?: boolean;
    default?: unknown;
    description?: string;
    options?: Array<{ value: unknown; label: string }>;
}

/** @deprecated Use PluginsPage instead. EnhancedPluginsPage is superseded by the primary PluginsPage route. */
export function EnhancedPluginsPage() {
    const [activeTab, setActiveTab] = useState<TabType>('installed');
    const [viewMode, setViewMode] = useState<ViewMode>('grid');
    const [searchQuery, setSearchQuery] = useState('');
    const [categoryFilter, setCategoryFilter] = useState<PluginCategory | 'all'>('all');
    const [configuringPluginId, setConfiguringPluginId] = useState<string | null>(null);

    // Integration hooks
    const {
        installed,
        marketplace,
        healthMap,
        stats,
        isLoading,
    } = useDataCloudPlugins({
        category: categoryFilter === 'all' ? undefined : categoryFilter,
        search: searchQuery || undefined,
        includeMarketplace: activeTab === 'marketplace',
    });

    const {
        install,
        isInstalling,
        uninstall,
        isUninstalling,
        enable,
        isEnabling,
        disable,
        isDisabling,
    } = usePluginInstallation();

    const {
        config: rawConfig,
        schema,
        updateConfig,
        isUpdating,
        isLoading: configLoading,
    } = usePluginConfiguration(configuringPluginId ?? undefined);

    // Type cast config for indexing
    const config = rawConfig as Record<string, unknown> | undefined;

    // Convert plugin schema fields to form fields
    const schemaFields: ConfigField[] = useMemo(() => {
        if (!schema?.fields) return [];
        return schema.fields.map((field) => ({
            key: field.key,
            label: field.label,
            type: field.type === 'select' ? 'select' : field.type === 'boolean' ? 'boolean' : field.type === 'number' ? 'number' : 'string',
            required: field.required,
            default: field.default,
            description: field.description,
            options: field.options?.map((opt) => ({ value: opt.value, label: opt.label })),
        }));
    }, [schema]);

    const { isHealthy } = useSystemHealth();

    // Handlers
    const handleInstall = async (pluginId: string) => {
        await install(pluginId);
    };

    const handleUninstall = async (pluginId: string) => {
        if (confirm('Are you sure you want to uninstall this plugin?')) {
            await uninstall(pluginId);
        }
    };

    const handleToggleEnable = async (pluginId: string, isEnabled: boolean) => {
        if (isEnabled) {
            await disable(pluginId);
        } else {
            await enable(pluginId);
        }
    };

    const handleConfigure = (pluginId: string) => {
        setConfiguringPluginId(pluginId);
    };

    const handleSaveConfig = async (newConfig: Record<string, unknown>) => {
        await updateConfig(newConfig);
        setConfiguringPluginId(null);
    };

    // Filter plugins by search
    const filteredInstalled = installed.filter((p) =>
        p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        p.description?.toLowerCase().includes(searchQuery.toLowerCase())
    );

    const filteredMarketplace = marketplace.filter((p) =>
        !installed.some((i) => i.id === p.id) &&
        (p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            p.description?.toLowerCase().includes(searchQuery.toLowerCase()))
    );

    const tabs: { id: TabType; label: string; icon: React.ReactNode; badge?: number }[] = [
        { id: 'installed', label: 'Installed', icon: <Package className="h-4 w-4" />, badge: stats.total },
        { id: 'marketplace', label: 'Marketplace', icon: <Grid className="h-4 w-4" />, badge: stats.marketplace },
        { id: 'health', label: 'Health', icon: <Activity className="h-4 w-4" />, badge: stats.unhealthy > 0 ? stats.unhealthy : undefined },
    ];

    return (
        <div className="min-h-screen bg-gray-50">
            {/* Header */}
            <div className="bg-white border-b border-gray-200">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-gradient-to-br from-green-500 to-emerald-500 rounded-lg">
                                <Package className="h-8 w-8 text-white" />
                            </div>
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900">Plugin Manager</h1>
                                <p className="text-sm text-gray-600 mt-1">
                                    Extend Data-Cloud with shared plugin framework
                                </p>
                            </div>
                        </div>
                        <div className="flex items-center gap-4">
                            {/* Stats */}
                            <div className="flex items-center gap-4 text-sm">
                                <span className="text-gray-600">
                                    <span className="font-semibold text-gray-900">{stats.total}</span> installed
                                </span>
                                <span className="text-gray-600">
                                    <span className="font-semibold text-green-600">{stats.healthy}</span> healthy
                                </span>
                                {stats.unhealthy > 0 && (
                                    <span className="text-gray-600">
                                        <span className="font-semibold text-red-600">{stats.unhealthy}</span> unhealthy
                                    </span>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Tab Navigation */}
                    <div className="mt-6 flex items-center justify-between">
                        <div className="flex gap-1 border-b border-gray-200">
                            {tabs.map((tab) => (
                                <button
                                    key={tab.id}
                                    onClick={() => setActiveTab(tab.id)}
                                    className={`flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === tab.id
                                        ? 'border-green-500 text-green-600'
                                        : 'border-transparent text-gray-500 hover:text-gray-700'
                                        }`}
                                >
                                    {tab.icon}
                                    {tab.label}
                                    {tab.badge !== undefined && tab.badge > 0 && (
                                        <span className={`ml-1 px-2 py-0.5 text-xs rounded-full ${tab.id === 'health' && stats.unhealthy > 0
                                            ? 'bg-red-100 text-red-600'
                                            : 'bg-green-100 text-green-600'
                                            }`}>
                                            {tab.badge}
                                        </span>
                                    )}
                                </button>
                            ))}
                        </div>

                        {/* View Mode Toggle */}
                        {activeTab !== 'health' && (
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={() => setViewMode('grid')}
                                    className={`p-2 rounded ${viewMode === 'grid' ? 'bg-gray-200' : 'hover:bg-gray-100'}`}
                                >
                                    <Grid className="h-4 w-4" />
                                </button>
                                <button
                                    onClick={() => setViewMode('list')}
                                    className={`p-2 rounded ${viewMode === 'list' ? 'bg-gray-200' : 'hover:bg-gray-100'}`}
                                >
                                    <List className="h-4 w-4" />
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Search and Filters */}
            {activeTab !== 'health' && (
                <div className="bg-white border-b border-gray-200">
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
                        <div className="flex items-center gap-4">
                            {/* Search */}
                            <div className="relative flex-1 max-w-md">
                                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
                                <input
                                    type="text"
                                    placeholder="Search plugins..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500"
                                />
                            </div>

                            {/* Category Filter */}
                            <select
                                value={categoryFilter}
                                onChange={(e) => setCategoryFilter(e.target.value as PluginCategory | 'all')}
                                className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500"
                            >
                                <option value="all">All Categories</option>
                                {DATA_CLOUD_PLUGIN_CATEGORIES.map(({ id, label }) => (
                                    <option key={id} value={id}>
                                        {label}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>
                </div>
            )}

            {/* Main Content */}
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {/* Installed Tab */}
                {activeTab === 'installed' && (
                    <div>
                        {isLoading ? (
                            <div className="text-center py-12">
                                <div className="animate-spin h-8 w-8 border-4 border-green-500 border-t-transparent rounded-full mx-auto" />
                                <p className="mt-4 text-gray-600">Loading plugins...</p>
                            </div>
                        ) : filteredInstalled.length === 0 ? (
                            <div className="text-center py-12 bg-white rounded-lg shadow">
                                <Package className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                                <h3 className="text-lg font-medium text-gray-900 mb-2">No plugins installed</h3>
                                <p className="text-gray-600 mb-4">Get started by browsing the marketplace</p>
                                <button
                                    onClick={() => setActiveTab('marketplace')}
                                    className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700"
                                >
                                    Browse Marketplace
                                </button>
                            </div>
                        ) : (
                            <div className={viewMode === 'grid' ? 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6' : 'space-y-4'}>
                                {filteredInstalled.map((plugin) => {
                                    const health = healthMap[plugin.id];
                                    return (
                                        <div key={plugin.id} className="bg-white rounded-lg shadow p-4 hover:shadow-md transition-shadow">
                                            <div className="flex items-start justify-between">
                                                <div className="flex items-center gap-3">
                                                    <div className="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center text-xl">
                                                        {plugin.icon ?? '📦'}
                                                    </div>
                                                    <div>
                                                        <h3 className="font-medium text-gray-900">{plugin.name}</h3>
                                                        <p className="text-sm text-gray-500">v{plugin.version}</p>
                                                    </div>
                                                </div>
                                                <div className={`px-2 py-1 rounded text-xs ${health?.status === 'healthy' ? 'bg-green-100 text-green-700' :
                                                    health?.status === 'degraded' ? 'bg-yellow-100 text-yellow-700' :
                                                        health?.status === 'unhealthy' ? 'bg-red-100 text-red-700' :
                                                            'bg-gray-100 text-gray-600'
                                                    }`}>
                                                    {health?.status ?? 'unknown'}
                                                </div>
                                            </div>
                                            <p className="text-sm text-gray-600 mt-2 line-clamp-2">{plugin.description}</p>
                                            <div className="flex items-center gap-2 mt-3">
                                                <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">
                                                    {plugin.category}
                                                </span>
                                                {plugin.license && (
                                                    <span className="px-2 py-0.5 text-xs bg-blue-50 text-blue-600 rounded">
                                                        {plugin.license}
                                                    </span>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-2 mt-4 pt-3 border-t border-gray-100">
                                                <button
                                                    onClick={() => handleConfigure(plugin.id)}
                                                    className="flex-1 px-3 py-1.5 text-sm bg-gray-100 text-gray-700 rounded hover:bg-gray-200"
                                                >
                                                    <Settings className="h-3 w-3 inline mr-1" />
                                                    Configure
                                                </button>
                                                <button
                                                    onClick={() => handleUninstall(plugin.id)}
                                                    disabled={isUninstalling}
                                                    className="px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 rounded"
                                                >
                                                    Uninstall
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                )}

                {/* Marketplace Tab */}
                {activeTab === 'marketplace' && (
                    <div>
                        {isLoading ? (
                            <div className="text-center py-12">
                                <div className="animate-spin h-8 w-8 border-4 border-green-500 border-t-transparent rounded-full mx-auto" />
                                <p className="mt-4 text-gray-600">Loading marketplace...</p>
                            </div>
                        ) : filteredMarketplace.length === 0 ? (
                            <div className="text-center py-12 bg-white rounded-lg shadow">
                                <Grid className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                                <h3 className="text-lg font-medium text-gray-900 mb-2">No plugins found</h3>
                                <p className="text-gray-600">Try adjusting your search or filters</p>
                            </div>
                        ) : (
                            <div className={viewMode === 'grid' ? 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6' : 'space-y-4'}>
                                {filteredMarketplace.map((plugin) => (
                                    <div key={plugin.id} className="bg-white rounded-lg shadow p-4 hover:shadow-md transition-shadow">
                                        <div className="flex items-start justify-between">
                                            <div className="flex items-center gap-3">
                                                <div className="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center text-xl">
                                                    {plugin.icon ?? '📦'}
                                                </div>
                                                <div>
                                                    <h3 className="font-medium text-gray-900">{plugin.name}</h3>
                                                    <p className="text-sm text-gray-500">v{plugin.version} · {plugin.author}</p>
                                                </div>
                                            </div>
                                        </div>
                                        <p className="text-sm text-gray-600 mt-2 line-clamp-2">{plugin.description}</p>
                                        <div className="flex items-center gap-2 mt-3">
                                            <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">
                                                {plugin.category}
                                            </span>
                                        </div>
                                        <button
                                            onClick={() => handleInstall(plugin.id)}
                                            disabled={isInstalling}
                                            className="w-full mt-4 px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50"
                                        >
                                            {isInstalling ? 'Installing...' : 'Install'}
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Health Tab */}
                {activeTab === 'health' && (
                    <div className="bg-white rounded-lg shadow">
                        <div className="p-4 border-b border-gray-200">
                            <h2 className="text-lg font-semibold text-gray-900">Plugin Health Overview</h2>
                        </div>
                        {isLoading ? (
                            <div className="p-8 flex items-center justify-center">
                                <div className="animate-spin h-8 w-8 border-4 border-green-500 border-t-transparent rounded-full" />
                            </div>
                        ) : (
                            <div className="divide-y divide-gray-200">
                                {installed.map((plugin) => {
                                    const health = healthMap[plugin.id];
                                    return (
                                        <div key={plugin.id} className="p-4 hover:bg-gray-50">
                                            <div className="flex items-center justify-between">
                                                <div className="flex items-center gap-3">
                                                    <div className={`w-3 h-3 rounded-full ${health?.status === 'healthy' ? 'bg-green-500' :
                                                        health?.status === 'degraded' ? 'bg-yellow-500' :
                                                            health?.status === 'unhealthy' ? 'bg-red-500' :
                                                                'bg-gray-400'
                                                        }`} />
                                                    <div>
                                                        <h3 className="font-medium text-gray-900">{plugin.name}</h3>
                                                        <p className="text-sm text-gray-500">
                                                            {health?.lastCheck
                                                                ? `Last checked: ${new Date(health.lastCheck).toLocaleTimeString()}`
                                                                : 'Never checked'}
                                                        </p>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-4">
                                                    {health?.metrics && (
                                                        <div className="flex items-center gap-4 text-sm text-gray-600">
                                                            <span>
                                                                <span className="font-medium">{health.metrics.requestsPerMinute}</span> req/min
                                                            </span>
                                                            <span>
                                                                <span className="font-medium">{health.metrics.avgLatency}</span> ms
                                                            </span>
                                                            <span className={health.metrics.errorRate && health.metrics.errorRate > 0.05 ? 'text-red-600' : ''}>
                                                                <span className="font-medium">{((health.metrics.errorRate ?? 0) * 100).toFixed(1)}%</span> errors
                                                            </span>
                                                        </div>
                                                    )}
                                                    <div className={`px-2 py-1 rounded text-xs font-medium ${health?.status === 'healthy' ? 'bg-green-100 text-green-700' :
                                                        health?.status === 'degraded' ? 'bg-yellow-100 text-yellow-700' :
                                                            health?.status === 'unhealthy' ? 'bg-red-100 text-red-700' :
                                                                'bg-gray-100 text-gray-600'
                                                        }`}>
                                                        {health?.status ?? 'unknown'}
                                                    </div>
                                                </div>
                                            </div>
                                            {health?.issues && health.issues.length > 0 && (
                                                <div className="mt-2 ml-6 space-y-1">
                                                    {health.issues.map((issue, idx) => (
                                                        <div key={idx} className={`flex items-center gap-2 text-sm ${issue.severity === 'critical' ? 'text-red-600' :
                                                            issue.severity === 'high' ? 'text-orange-600' :
                                                                'text-yellow-600'
                                                            }`}>
                                                            <AlertTriangle className="h-3 w-3" />
                                                            {issue.message}
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Configuration Modal */}
            {configuringPluginId && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
                        <div className="p-6 border-b border-gray-200">
                            <div className="flex items-center justify-between">
                                <h2 className="text-xl font-semibold text-gray-900">
                                    Configure Plugin
                                </h2>
                                <button
                                    onClick={() => setConfiguringPluginId(null)}
                                    className="p-2 hover:bg-gray-100 rounded text-xl"
                                >
                                    ×
                                </button>
                            </div>
                        </div>
                        <div className="p-6">
                            {configLoading ? (
                                <div className="text-center py-8">
                                    <div className="animate-spin h-8 w-8 border-4 border-green-500 border-t-transparent rounded-full mx-auto" />
                                </div>
                            ) : schemaFields.length > 0 ? (
                                <form onSubmit={(e) => {
                                    e.preventDefault();
                                    const formData = new FormData(e.currentTarget);
                                    const newConfig: Record<string, unknown> = {};
                                    schemaFields.forEach((field) => {
                                        newConfig[field.key] = formData.get(field.key);
                                    });
                                    handleSaveConfig(newConfig);
                                }}>
                                    <div className="space-y-4">
                                        {schemaFields.map((field) => (
                                            <div key={field.key}>
                                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                                    {field.label}
                                                    {field.required && <span className="text-red-500 ml-1">*</span>}
                                                </label>
                                                {field.type === 'select' && field.options ? (
                                                    <select
                                                        name={field.key}
                                                        defaultValue={(config?.[field.key] as string) ?? (field.default as string) ?? ''}
                                                        required={field.required}
                                                        className="w-full px-3 py-2 border border-gray-300 rounded-md"
                                                    >
                                                        {field.options.map((opt) => (
                                                            <option key={String(opt.value)} value={String(opt.value)}>
                                                                {opt.label}
                                                            </option>
                                                        ))}
                                                    </select>
                                                ) : field.type === 'boolean' ? (
                                                    <input
                                                        type="checkbox"
                                                        name={field.key}
                                                        defaultChecked={Boolean(config?.[field.key] ?? field.default)}
                                                        className="h-4 w-4"
                                                    />
                                                ) : (
                                                    <input
                                                        type={field.type === 'number' ? 'number' : 'text'}
                                                        name={field.key}
                                                        defaultValue={String(config?.[field.key] ?? field.default ?? '')}
                                                        required={field.required}
                                                        className="w-full px-3 py-2 border border-gray-300 rounded-md"
                                                    />
                                                )}
                                                {field.description && (
                                                    <p className="text-xs text-gray-500 mt-1">{field.description}</p>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                    <div className="flex justify-end gap-3 mt-6">
                                        <button
                                            type="button"
                                            onClick={() => setConfiguringPluginId(null)}
                                            className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded"
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            type="submit"
                                            disabled={isUpdating}
                                            className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50"
                                        >
                                            {isUpdating ? 'Saving...' : 'Save Configuration'}
                                        </button>
                                    </div>
                                </form>
                            ) : (
                                <div className="text-center py-8 text-gray-500">
                                    <Settings className="h-12 w-12 mx-auto mb-4 opacity-50" />
                                    <p>No configuration options available</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Compliance Badge */}
            <div className="fixed bottom-4 right-4">
                <div className="flex items-center gap-2 px-3 py-2 bg-white rounded-lg shadow-lg border border-gray-200">
                    <Shield className="h-4 w-4 text-green-600" />
                    <span className="text-xs text-gray-600">
                        All plugins verified
                    </span>
                </div>
            </div>
        </div>
    );
}

export default EnhancedPluginsPage;
