/**
 * Plugins Page
 *
 * Plugin management interface for browsing, installing, and configuring plugins.
 *
 * Features:
 * - Browse marketplace and installed plugins
 * - Install/uninstall plugins
 * - Enable/disable plugins
 * - Configure plugin settings
 * - Upload custom plugins
 *
 * @doc.type page
 * @doc.purpose Plugin management interface
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import {
  Package,
  Search,
  Filter,
  RefreshCw,
  Shield,
  AlertCircle,
  CheckCircle,
  Activity,
} from 'lucide-react';
import { cn, buttonStyles, inputStyles, textStyles, bgStyles } from '../lib/theme';
import { PluginCard } from '../components/plugins/PluginCard';
import {
  pluginService,
  type PluginCategory,
  type Plugin,
} from '../api/plugin.service';

type TabType = 'installed' | 'catalog' | 'delivery';

/**
 * Plugins Page Component
 */
export function PluginsPage(): React.ReactElement {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<TabType>('installed');
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<PluginCategory | 'all'>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive' | 'error'>('all');

  // Fetch installed plugins
  const {
    data: installedPlugins = [],
    isLoading: loadingInstalled,
    error: installedError,
  } = useQuery({
    queryKey: ['plugins', 'installed'],
    queryFn: () => pluginService.getInstalledPlugins(),
    staleTime: 30000,
  });

  // Enable plugin mutation
  const enableMutation = useMutation({
    mutationFn: (pluginId: string) => pluginService.enablePlugin(pluginId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
    },
  });

  // Disable plugin mutation
  const disableMutation = useMutation({
    mutationFn: (pluginId: string) => pluginService.disablePlugin(pluginId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
    },
  });

  const categories: Array<{ value: PluginCategory | 'all'; label: string }> = [
    { value: 'all', label: 'All' },
    { value: 'connector', label: 'Connectors' },
    { value: 'transformer', label: 'Transformers' },
    { value: 'quality', label: 'Quality' },
    { value: 'governance', label: 'Governance' },
    { value: 'visualization', label: 'Visualization' },
    { value: 'integration', label: 'Integration' },
    { value: 'ai', label: 'AI' },
  ];

  const tabs = [
    { id: 'installed' as const, label: 'Installed', icon: <Package className="h-4 w-4" /> },
    { id: 'catalog' as const, label: 'Catalog Boundary', icon: <Shield className="h-4 w-4" /> },
    { id: 'delivery' as const, label: 'Deployment', icon: <RefreshCw className="h-4 w-4" /> },
  ];

  // Filter installed plugins
  const filteredInstalledPlugins = installedPlugins.filter((plugin) => {
    // Search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      const matchesName = plugin.metadata.name.toLowerCase().includes(query);
      const matchesDescription = plugin.metadata.description.toLowerCase().includes(query);
      const matchesAuthor = plugin.metadata.author.toLowerCase().includes(query);
      if (!matchesName && !matchesDescription && !matchesAuthor) {
        return false;
      }
    }

    // Category filter
    if (categoryFilter !== 'all' && plugin.metadata.category !== categoryFilter) {
      return false;
    }

    // Status filter
    if (statusFilter !== 'all' && plugin.status !== statusFilter) {
      return false;
    }

    return true;
  });

  const stats = {
    total: installedPlugins.length,
    active: installedPlugins.filter((p) => p.status === 'active').length,
    inactive: installedPlugins.filter((p) => p.status === 'inactive').length,
    error: installedPlugins.filter((p) => p.status === 'error').length,
  };

  return (
    <div className={cn('min-h-screen', bgStyles.page)}>
      {/* Header */}
      <div className="border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="px-6 py-4">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h1 className={textStyles.h1}>Plugins</h1>
              <p className={cn(textStyles.body, 'mt-1')}>
                Monitor the bundled plugins shipped with the current launcher build
              </p>
            </div>
            <button
              onClick={() => {
                void queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
              }}
              className={cn(buttonStyles.ghost, 'px-3 py-2')}
              title="Refresh bundled plugin status"
            >
              <RefreshCw className="h-5 w-5" />
            </button>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-4 gap-4">
            <div className="px-4 py-3 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
              <div className="flex items-center gap-2">
                <Package className="h-5 w-5 text-gray-400" />
                <div>
                  <div className={textStyles.h3}>{stats.total}</div>
                  <div className={textStyles.small}>Total</div>
                </div>
              </div>
            </div>
            <div className="px-4 py-3 bg-green-50 dark:bg-green-900/20 rounded-lg">
              <div className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5 text-green-600" />
                <div>
                  <div className={textStyles.h3}>{stats.active}</div>
                  <div className={textStyles.small}>Active</div>
                </div>
              </div>
            </div>
            <div className="px-4 py-3 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
              <div className="flex items-center gap-2">
                <Package className="h-5 w-5 text-gray-400" />
                <div>
                  <div className={textStyles.h3}>{stats.inactive}</div>
                  <div className={textStyles.small}>Inactive</div>
                </div>
              </div>
            </div>
            <div className="px-4 py-3 bg-red-50 dark:bg-red-900/20 rounded-lg">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-5 w-5 text-red-600" />
                <div>
                  <div className={textStyles.h3}>{stats.error}</div>
                  <div className={textStyles.small}>Errors</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Tabs */}
        <div className="px-6">
          <div className="flex items-center gap-1 border-b border-gray-200 dark:border-gray-700">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={cn(
                  'px-4 py-3 flex items-center gap-2 text-sm font-medium border-b-2 -mb-px transition-colors',
                  activeTab === tab.id
                    ? 'border-primary-600 text-primary-600'
                    : 'border-transparent text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
                )}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Filters */}
      {activeTab === 'installed' && (
        <div className="px-6 py-4 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
          <div className="flex items-center gap-4">
            {/* Search */}
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search plugins..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className={cn(inputStyles.base, 'pl-10')}
              />
            </div>

            {/* Category Filter */}
            <div className="flex items-center gap-2">
              <Filter className="h-4 w-4 text-gray-400" />
              <select
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value as PluginCategory | 'all')}
                className={cn(inputStyles.base, 'w-48')}
              >
                {categories.map((cat) => (
                  <option key={cat.value} value={cat.value}>
                    {cat.label}
                  </option>
                ))}
              </select>
            </div>

            {/* Status Filter (Installed tab only) */}
            {activeTab === 'installed' && (
              <div className="flex items-center gap-2">
                <Activity className="h-4 w-4 text-gray-400" />
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
                  className={cn(inputStyles.base, 'w-40')}
                >
                  <option value="all">All Status</option>
                  <option value="active">Active</option>
                  <option value="inactive">Inactive</option>
                  <option value="error">Error</option>
                </select>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Content */}
      <div className="p-6">
        {activeTab === 'installed' && (
          <div>
            {/* Results count */}
            {!loadingInstalled && installedPlugins.length > 0 && (searchQuery || categoryFilter !== 'all' || statusFilter !== 'all') && (
              <div className="mb-4 flex items-center justify-between text-sm text-gray-600 dark:text-gray-400">
                <span>
                  Showing {filteredInstalledPlugins.length} of {installedPlugins.length} plugins
                </span>
                {(searchQuery || categoryFilter !== 'all' || statusFilter !== 'all') && (
                  <button
                    onClick={() => {
                      setSearchQuery('');
                      setCategoryFilter('all');
                      setStatusFilter('all');
                    }}
                    className="text-primary-600 hover:text-primary-700 font-medium"
                  >
                    Clear all filters
                  </button>
                )}
              </div>
            )}
            
            {loadingInstalled ? (
              <div className="flex items-center justify-center py-12">
                <RefreshCw className="h-8 w-8 animate-spin text-gray-400" />
              </div>
            ) : installedError ? (
              <div className="text-center py-12">
                <AlertCircle className="h-12 w-12 text-red-500 mx-auto mb-4" />
                <p className="text-gray-600 dark:text-gray-400">
                  Failed to load installed plugins
                </p>
              </div>
            ) : installedPlugins.length === 0 ? (
              <div className="text-center py-12">
                <Package className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                <h3 className={cn(textStyles.h3, 'mb-2')}>No plugins installed</h3>
                <p className="text-gray-600 dark:text-gray-400 mb-4">
                  No bundled plugins are currently registered in this launcher build
                </p>
              </div>
            ) : filteredInstalledPlugins.length === 0 ? (
              <div className="text-center py-12">
                <Search className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                <h3 className={cn(textStyles.h3, 'mb-2')}>No plugins found</h3>
                <p className="text-gray-600 dark:text-gray-400 mb-4">
                  Try adjusting your filters or search query
                </p>
                <button
                  onClick={() => {
                    setSearchQuery('');
                    setCategoryFilter('all');
                    setStatusFilter('all');
                  }}
                  className={cn(buttonStyles.secondary, 'px-4 py-2')}
                >
                  Clear Filters
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                {filteredInstalledPlugins.map((plugin) => (
                  <PluginCard
                    key={plugin.id}
                    plugin={plugin}
                    mode="installed"
                    onEnable={(id) => enableMutation.mutate(id)}
                    onDisable={(id) => disableMutation.mutate(id)}
                    onViewDetails={(id) => navigate(`/plugins/${id}`)}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'catalog' && (
          <div className="max-w-3xl mx-auto rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-900 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-100">
            <div className="flex items-start gap-3">
              <Shield className="mt-0.5 h-5 w-5 flex-shrink-0" />
              <div>
                <h3 className={cn(textStyles.h3, 'mb-2')}>Bundled Plugin Boundary</h3>
                <p className="text-sm leading-6">
                  The canonical backend only exposes bundled plugin inventory plus enable, disable, and upgrade-intent endpoints.
                  Marketplace browsing, runtime installation, and custom uploads are intentionally unavailable in this launcher.
                </p>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'delivery' && (
          <div className="max-w-3xl mx-auto rounded-xl border border-blue-200 bg-blue-50 p-6 text-blue-900 dark:border-blue-800 dark:bg-blue-900/20 dark:text-blue-100">
            <div className="flex items-start gap-3">
              <RefreshCw className="mt-0.5 h-5 w-5 flex-shrink-0" />
              <div>
                <h3 className={cn(textStyles.h3, 'mb-2')}>How Plugin Changes Ship</h3>
                <p className="text-sm leading-6">
                  To add or upgrade a plugin, publish a new Data Cloud server build that includes the updated bundled plugin artifact.
                  Runtime upload and hot-swap flows were removed here to match the actual launcher capability boundary.
                </p>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
