/**
 * Plugins Page
 *
 * Plugin management interface for bundled inventory, toggles, and upgrade guidance.
 *
 * Features:
 * - Browse bundled plugin inventory and installed state
 * - Enable/disable plugins
 * - Configure plugin settings
 * - Review bundled-plugin delivery guidance
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
  RefreshCw,
  CheckCircle,
  AlertCircle,
} from 'lucide-react';
import { SearchFilterBar } from '../components/common/SearchFilterBar';
import { EmptyState, NotFoundState } from '../components/common/AsyncStates';
import { QueryStateBoundary } from '../components/common/QueryStateBoundary';
import { cn, buttonStyles, textStyles, bgStyles } from '../lib/theme';
import {
  PLUGINS_EMPTY_STATE_DETAIL,
  PLUGINS_INVENTORY_HEADER_DETAIL,
} from '../lib/runtime-boundaries';
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
    <section className={cn('min-h-screen', bgStyles.page)} data-testid="plugins-page" aria-label="Plugins">
      {/* Header */}
      <div className="border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="px-6 py-4">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h1 className={textStyles.h1}>Plugins</h1>
              <p className={cn(textStyles.body, 'mt-1')} data-testid="plugins-header-detail">
                {PLUGINS_INVENTORY_HEADER_DETAIL}
              </p>
            </div>
            <button
              onClick={() => {
                void queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] });
              }}
              className={cn(buttonStyles.ghost, 'px-3 py-2')}
              title="Refresh bundled plugin status"
              aria-label="Refresh bundled plugin status"
            >
              <RefreshCw className="h-5 w-5" />
            </button>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-4 gap-4" data-testid="plugins-stats-grid">
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
                data-testid={`plugins-tab-${tab.id}`}
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
          <SearchFilterBar
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            searchPlaceholder="Search plugins..."
            filters={[
              {
                id: 'plugins-category-filter',
                label: 'Category',
                value: categoryFilter,
                options: categories.map((cat) => ({ value: cat.value, label: cat.label })),
                onChange: (v) => setCategoryFilter(v as PluginCategory | 'all'),
              },
              ...(activeTab === 'installed'
                ? [
                  {
                    id: 'plugins-status-filter',
                    label: 'Status',
                    value: statusFilter,
                    options: [
                      { value: 'all', label: 'All Status' },
                      { value: 'active', label: 'Active' },
                      { value: 'inactive', label: 'Inactive' },
                      { value: 'error', label: 'Error' },
                    ],
                    onChange: (v: string) => setStatusFilter(v as typeof statusFilter),
                  },
                ]
                : []),
            ]}
            hasActiveFilters={
              searchQuery.length > 0 || categoryFilter !== 'all' || statusFilter !== 'all'
            }
            onClear={() => {
              setSearchQuery('');
              setCategoryFilter('all');
              setStatusFilter('all');
            }}
          />
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

            <QueryStateBoundary
              isLoading={loadingInstalled}
              isError={!!installedError}
              error={installedError instanceof Error ? installedError : null}
              errorTitle="Failed to load plugins"
              errorFallback="There was an error loading the installed plugins. Please try again."
              onRetry={() => queryClient.invalidateQueries({ queryKey: ['plugins', 'installed'] })}
              loadingMessage="Loading plugins..."
            >
            {installedPlugins.length === 0 ? (
              <EmptyState
                title="No plugins installed"
                description={PLUGINS_EMPTY_STATE_DETAIL}
                icon={<Package className="h-12 w-12 text-gray-400 mb-4" />}
              />
            ) : filteredInstalledPlugins.length === 0 ? (
              <NotFoundState
                query={searchQuery}
                onClear={() => {
                  setSearchQuery('');
                  setCategoryFilter('all');
                  setStatusFilter('all');
                }}
              />
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6" data-testid="plugins-installed-grid">
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
            </QueryStateBoundary>
          </div>
        )}
      </div>
    </section>
  );
}
