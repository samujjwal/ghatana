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
  Upload,
  RefreshCw,
  Download,
  Shield,
  AlertCircle,
  CheckCircle,
  X,
  Activity,
} from 'lucide-react';
import { cn, buttonStyles, inputStyles, textStyles, bgStyles } from '../lib/theme';
import { PluginCard } from '../components/plugins/PluginCard';
import { PluginConfigModal } from '../components/plugins/PluginConfigModal';
import {
  pluginService,
  type PluginCategory,
  type Plugin,
} from '../api/plugin.service';

type TabType = 'installed' | 'marketplace' | 'upload';

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
  const [configuringPlugin, setConfiguringPlugin] = useState<Plugin | null>(null);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);


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

  // Fetch marketplace plugins
  const {
    data: marketplacePlugins = [],
    isLoading: loadingMarketplace,
    error: marketplaceError,
  } = useQuery({
    queryKey: ['plugins', 'marketplace', categoryFilter, searchQuery],
    queryFn: () =>
      pluginService.browseMarketplace({
        category: categoryFilter === 'all' ? undefined : categoryFilter,
        search: searchQuery || undefined,
      }),
    staleTime: 60000,
    enabled: activeTab === 'marketplace',
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

  // Install plugin mutation
  const installMutation = useMutation({
    mutationFn: (pluginId: string) =>
      pluginService.installPlugin({ pluginId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
    },
  });

  // Uninstall plugin mutation
  const uninstallMutation = useMutation({
    mutationFn: (pluginId: string) => pluginService.uninstallPlugin(pluginId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
    },
  });

  // Update plugin mutation
  const updateMutation = useMutation({
    mutationFn: (pluginId: string) =>
      pluginService.updatePlugin(pluginId, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
    },
  });

  // Refresh registry mutation
  const refreshMutation = useMutation({
    mutationFn: () => pluginService.refreshRegistry(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins', 'marketplace'] });
    },
  });

  // Upload plugin mutation
  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      
      // Simulate upload progress
      setUploadProgress(0);
      const interval = setInterval(() => {
        setUploadProgress((prev) => {
          if (prev >= 90) {
            clearInterval(interval);
            return 90;
          }
          return prev + 10;
        });
      }, 200);

      try {
        // TODO: Implement file upload to backend, get pluginId from response
        // For now, simulate with a dummy pluginId
        const result = await pluginService.installPlugin({ 
          pluginId: file.name.replace('.jar', ''),
          version: '1.0.0' 
        });
        clearInterval(interval);
        setUploadProgress(100);
        return result;
      } catch (error) {
        clearInterval(interval);
        throw error;
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
      setUploadFile(null);
      setUploadProgress(0);
      setActiveTab('installed');
    },
    onError: () => {
      setUploadProgress(0);
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
    { id: 'marketplace' as const, label: 'Marketplace', icon: <Download className="h-4 w-4" /> },
    { id: 'upload' as const, label: 'Upload', icon: <Upload className="h-4 w-4" /> },
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
                Extend Data Cloud with plugins for connectors, transformers, and integrations
              </p>
            </div>
            <button
              onClick={() => refreshMutation.mutate()}
              className={cn(buttonStyles.ghost, 'px-3 py-2')}
              disabled={refreshMutation.isPending}
              title="Refresh registry"
            >
              <RefreshCw
                className={cn('h-5 w-5', refreshMutation.isPending && 'animate-spin')}
              />
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
      {(activeTab === 'installed' || activeTab === 'marketplace') && (
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
                  Browse the marketplace to find and install plugins
                </p>
                <button
                  onClick={() => setActiveTab('marketplace')}
                  className={cn(buttonStyles.primary, 'px-4 py-2')}
                >
                  Browse Marketplace
                </button>
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
                    onConfigure={() => setConfiguringPlugin(plugin)}
                    onUninstall={(id) => {
                      if (confirm('Are you sure you want to uninstall this plugin?')) {
                        uninstallMutation.mutate(id);
                      }
                    }}
                    onViewDetails={(id) => navigate(`/plugins/${id}`)}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'marketplace' && (
          <div>
            {loadingMarketplace ? (
              <div className="flex items-center justify-center py-12">
                <RefreshCw className="h-8 w-8 animate-spin text-gray-400" />
              </div>
            ) : marketplaceError ? (
              <div className="text-center py-12">
                <AlertCircle className="h-12 w-12 text-red-500 mx-auto mb-4" />
                <p className="text-gray-600 dark:text-gray-400">
                  Failed to load marketplace plugins
                </p>
              </div>
            ) : marketplacePlugins.length === 0 ? (
              <div className="text-center py-12">
                <Download className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                <h3 className={cn(textStyles.h3, 'mb-2')}>No plugins found</h3>
                <p className="text-gray-600 dark:text-gray-400">
                  Try adjusting your filters or search query
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                {marketplacePlugins.map((plugin) => (
                  <PluginCard
                    key={plugin.id}
                    plugin={plugin}
                    mode="marketplace"
                    onInstall={(id) => installMutation.mutate(id)}
                    onUpdate={(id) => updateMutation.mutate(id)}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'upload' && (
          <div className="max-w-2xl mx-auto">
            {!uploadFile ? (
              <div className="bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-700 p-12 text-center">
                <Upload className="h-16 w-16 text-gray-400 mx-auto mb-4" />
                <h3 className={cn(textStyles.h3, 'mb-2')}>Upload Custom Plugin</h3>
                <p className="text-gray-600 dark:text-gray-400 mb-6">
                  Upload a custom plugin JAR file to install it
                </p>
                <input
                  type="file"
                  accept=".jar"
                  className="hidden"
                  id="plugin-upload"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) {
                      setUploadFile(file);
                    }
                  }}
                />
                <label
                  htmlFor="plugin-upload"
                  className={cn(buttonStyles.primary, 'px-6 py-3 cursor-pointer inline-block')}
                >
                  Choose File
                </label>
                <p className="text-xs text-gray-500 dark:text-gray-500 mt-4">
                  Only JAR files are supported (max 50MB)
                </p>
              </div>
            ) : (
              <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-start gap-3">
                    <div className="w-12 h-12 bg-primary-100 dark:bg-primary-900/20 rounded-lg flex items-center justify-center">
                      <Package className="h-6 w-6 text-primary-600" />
                    </div>
                    <div>
                      <h3 className={cn(textStyles.h4, 'mb-1')}>{uploadFile.name}</h3>
                      <p className="text-sm text-gray-600 dark:text-gray-400">
                        {(uploadFile.size / 1024 / 1024).toFixed(2)} MB
                      </p>
                    </div>
                  </div>
                  {!uploadMutation.isPending && (
                    <button
                      onClick={() => {
                        setUploadFile(null);
                        setUploadProgress(0);
                      }}
                      className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                    >
                      <X className="h-5 w-5" />
                    </button>
                  )}
                </div>

                {uploadMutation.isPending && (
                  <div className="mb-4">
                    <div className="flex items-center justify-between text-sm mb-2">
                      <span className="text-gray-600 dark:text-gray-400">Uploading...</span>
                      <span className="font-medium">{uploadProgress}%</span>
                    </div>
                    <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2 overflow-hidden">
                      <div
                        className="bg-primary-600 h-full transition-all duration-300"
                        style={{ width: `${uploadProgress}%` }}
                      />
                    </div>
                  </div>
                )}

                {uploadMutation.isError && (
                  <div className="mb-4 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                    <div className="flex items-start gap-2">
                      <AlertCircle className="h-4 w-4 text-red-600 flex-shrink-0 mt-0.5" />
                      <div>
                        <p className="text-sm font-medium text-red-900 dark:text-red-100">
                          Upload failed
                        </p>
                        <p className="text-xs text-red-800 dark:text-red-200 mt-1">
                          {uploadMutation.error instanceof Error
                            ? uploadMutation.error.message
                            : 'An error occurred during upload'}
                        </p>
                      </div>
                    </div>
                  </div>
                )}

                {!uploadMutation.isPending && !uploadMutation.isError && (
                  <button
                    onClick={() => uploadMutation.mutate(uploadFile)}
                    className={cn(buttonStyles.primary, 'w-full px-4 py-3')}
                  >
                    <Upload className="h-4 w-4 mr-2" />
                    Install Plugin
                  </button>
                )}
              </div>
            )}

            <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
              <div className="flex gap-3">
                <Shield className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                <div>
                  <h4 className="text-sm font-medium text-blue-900 dark:text-blue-100 mb-1">
                    Plugin Security
                  </h4>
                  <p className="text-sm text-blue-800 dark:text-blue-200">
                    Custom plugins run with full system access. Only install plugins from trusted
                    sources. All plugins are scanned for security vulnerabilities before
                    installation.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Configuration Modal */}
      {configuringPlugin && (
        <PluginConfigModal
          plugin={configuringPlugin}
          isOpen={true}
          onClose={() => setConfiguringPlugin(null)}
        />
      )}
    </div>
  );
}
