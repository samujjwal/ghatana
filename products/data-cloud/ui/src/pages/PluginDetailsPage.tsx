/**
 * Plugin Details Page
 *
 * Detailed view of a plugin with documentation, changelog, dependencies, and usage examples.
 *
 * @doc.type page
 * @doc.purpose Plugin documentation and details
 * @doc.layer frontend
 */

import React from 'react';
import { useParams, useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft,
  Package,
  AlertCircle,
  CheckCircle,
  Clock,
  Users,
  ExternalLink,
  BookOpen,
  FileText,
  Layers,
} from 'lucide-react';
import { cn, buttonStyles, textStyles, bgStyles, cardStyles } from '../lib/theme';
import { pluginService } from '../api/plugin.service';
import { PluginHealthMonitor } from '../components/plugins/PluginHealthMonitor';
import { PluginVersionCompare } from '../components/plugins/PluginVersionCompare';
import { PluginDependencyGraph } from '../components/plugins/PluginDependencyGraph';
import { PluginPerformanceMetrics } from '../components/plugins/PluginPerformanceMetrics';
import { PluginLogsViewer } from '../components/plugins/PluginLogsViewer';
import type { PluginNode, PluginDependency } from '../components/plugins/PluginDependencyGraph';

// Mock data for dependency graph
function getMockGraphNodes(): PluginNode[] {
  return [
    { id: 'postgres-connector', name: 'PostgreSQL', version: '2.1.0', status: 'active', category: 'connector' },
    { id: 'snowflake-connector', name: 'Snowflake', version: '1.8.2', status: 'active', category: 'connector' },
    { id: 'data-quality-checker', name: 'Data Quality', version: '3.0.1', status: 'active', category: 'quality' },
    { id: 'pii-detector', name: 'PII Detector', version: '2.5.0', status: 'inactive', category: 'governance' },
    { id: 'data-transformer', name: 'Transformer', version: '1.2.0', status: 'active', category: 'transformer' },
  ];
}

function getMockDependencies(currentPluginId: string): PluginDependency[] {
  const deps: PluginDependency[] = [
    { from: 'postgres-connector', to: 'data-quality-checker', type: 'optional', resolved: true },
    { from: 'snowflake-connector', to: 'data-quality-checker', type: 'requires', resolved: true },
    { from: 'data-quality-checker', to: 'pii-detector', type: 'requires', resolved: false },
    { from: 'data-transformer', to: 'data-quality-checker', type: 'optional', resolved: true },
    { from: 'postgres-connector', to: 'data-transformer', type: 'provides', resolved: true },
  ];

  // Add a conflict example if viewing the PII detector
  if (currentPluginId === 'pii-detector') {
    deps.push({ from: 'pii-detector', to: 'data-transformer', type: 'conflicts', resolved: false });
  }

  return deps;
}

/**
 * Plugin Details Page Component
 */
export function PluginDetailsPage(): React.ReactElement {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { data: plugin, isLoading, error } = useQuery({
    queryKey: ['plugins', 'details', id],
    queryFn: async () => {
      // Try to get from installed plugins first
      const installed = await pluginService.getInstalledPlugins();
      const found = installed.find((p) => p.id === id);
      if (found) return found;
      
      // If not found, could fetch from marketplace
      // For now, return null
      return null;
    },
    enabled: Boolean(id),
  });

  if (isLoading) {
    return (
      <div className={cn('min-h-screen flex items-center justify-center', bgStyles.page)}>
        <div className="text-center">
          <div className="inline-block w-8 h-8 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin mb-4" />
          <p className="text-gray-600 dark:text-gray-400">Loading plugin details...</p>
        </div>
      </div>
    );
  }

  if (error || !plugin) {
    return (
      <div className={cn('min-h-screen flex items-center justify-center', bgStyles.page)}>
        <div className="text-center max-w-md">
          <AlertCircle className="h-12 w-12 text-red-500 mx-auto mb-4" />
          <h2 className={cn(textStyles.h2, 'mb-2')}>Plugin Not Found</h2>
          <p className="text-gray-600 dark:text-gray-400 mb-6">
            The plugin you're looking for doesn't exist or has been removed.
          </p>
          <button onClick={() => navigate('/plugins')} className={cn(buttonStyles.primary, 'px-6 py-3')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Plugins
          </button>
        </div>
      </div>
    );
  }

  const statusConfig = {
    active: { color: 'text-green-600', bg: 'bg-green-100 dark:bg-green-900/20', label: 'Active' },
    inactive: { color: 'text-gray-600', bg: 'bg-gray-100 dark:bg-gray-900/20', label: 'Inactive' },
    installing: { color: 'text-blue-600', bg: 'bg-blue-100 dark:bg-blue-900/20', label: 'Installing' },
    error: { color: 'text-red-600', bg: 'bg-red-100 dark:bg-red-900/20', label: 'Error' },
    uninstalling: { color: 'text-orange-600', bg: 'bg-orange-100 dark:bg-orange-900/20', label: 'Uninstalling' },
  };

  const status = statusConfig[plugin.status];

  return (
    <div className={cn('min-h-screen', bgStyles.page)}>
      {/* Header */}
      <div className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <div className="px-6 py-4">
          <button
            onClick={() => navigate('/plugins')}
            className={cn(buttonStyles.ghost, 'px-3 py-2 mb-4')}
          >
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Plugins
          </button>

          <div className="flex items-start gap-6">
            {/* Plugin Icon */}
            <div className="w-20 h-20 bg-gradient-to-br from-primary-500 to-primary-700 rounded-xl flex items-center justify-center flex-shrink-0">
              <Package className="h-10 w-10 text-white" />
            </div>

            {/* Plugin Info */}
            <div className="flex-1">
              <div className="flex items-start justify-between gap-4 mb-3">
                <div>
                  <div className="flex items-center gap-3 mb-2">
                    <h1 className={textStyles.h1}>{plugin.metadata.name}</h1>
                    <span className={cn('px-2 py-1 rounded text-xs font-medium', status.bg, status.color)}>
                      {status.label}
                    </span>
                  </div>
                  <p className="text-gray-600 dark:text-gray-400">{plugin.metadata.description}</p>
                </div>
              </div>

              {/* Meta Info */}
              <div className="flex items-center gap-6 text-sm">
                <div className="flex items-center gap-2">
                  <Users className="h-4 w-4 text-gray-400" />
                  <span className="text-gray-600 dark:text-gray-400">{plugin.metadata.author}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Package className="h-4 w-4 text-gray-400" />
                  <span className="text-gray-600 dark:text-gray-400">v{plugin.metadata.version}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-gray-400" />
                  <span className="text-gray-600 dark:text-gray-400">
                    Installed {new Date(plugin.installedAt).toLocaleDateString()}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="p-6">
        <div className="grid grid-cols-3 gap-6">
          {/* Main Content */}
          <div className="col-span-2 space-y-6">
            {/* Overview */}
            <div className={cardStyles.base}>
              <div className="flex items-center gap-2 mb-4">
                <BookOpen className="h-5 w-5 text-primary-600" />
                <h2 className={textStyles.h3}>Overview</h2>
              </div>
              <div className="prose dark:prose-invert max-w-none">
                <p className="text-gray-700 dark:text-gray-300">
                  {plugin.metadata.description}
                </p>
                <p className="text-gray-700 dark:text-gray-300 mt-4">
                  This plugin provides functionality for {plugin.metadata.category.toLowerCase()} operations.
                  It integrates seamlessly with the Data Cloud platform and follows all security best practices.
                </p>
              </div>
            </div>

            {/* Version Comparison */}
            <PluginVersionCompare
              currentVersion={plugin.metadata.version}
              availableVersion="2.0.0"
              changelog={[
                'New AI-powered data transformation',
                'Improved performance by 40%',
                'Added support for JSON and XML formats',
                'Fixed memory leak in batch processing',
                'Enhanced error reporting',
              ]}
              onUpgrade={() => {
                console.log('Upgrade plugin');
                // TODO: Implement upgrade logic
              }}
            />

            {/* Capabilities */}
            <div className={cardStyles.base}>
              <div className="flex items-center gap-2 mb-4">
                <Layers className="h-5 w-5 text-primary-600" />
                <h2 className={textStyles.h3}>Capabilities</h2>
              </div>
              <div className="space-y-3">
                {plugin.capabilities.map((capability, idx) => (
                  <div key={idx} className="flex items-start gap-3 p-3 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                    <CheckCircle className="h-5 w-5 text-green-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-medium text-sm mb-1">{capability.name}</h4>
                      {capability.description && (
                        <p className="text-xs text-gray-600 dark:text-gray-400">{capability.description}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Documentation */}
            <div className={cardStyles.base}>
              <div className="flex items-center gap-2 mb-4">
                <FileText className="h-5 w-5 text-primary-600" />
                <h2 className={textStyles.h3}>Documentation</h2>
              </div>
              <div className="space-y-4">
                <div>
                  <h4 className="font-medium text-sm mb-2">Getting Started</h4>
                  <div className="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                    <code className="text-xs">
                      {`// Install and enable the plugin
await pluginService.installPlugin({ pluginId: '${plugin.id}' });
await pluginService.enablePlugin('${plugin.id}');

// Configure the plugin
await pluginService.updatePluginConfiguration('${plugin.id}', {
  // your configuration here
});`}
                    </code>
                  </div>
                </div>

                <div>
                  <h4 className="font-medium text-sm mb-2">Usage Example</h4>
                  <div className="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                    <code className="text-xs">
                      {`// Example usage
const result = await plugin.execute({
  input: "your-data",
  options: {}
});`}
                    </code>
                  </div>
                </div>
              </div>
            </div>

            {/* Changelog */}
            <div className={cardStyles.base}>
              <div className="flex items-center gap-2 mb-4">
                <Clock className="h-5 w-5 text-primary-600" />
                <h2 className={textStyles.h3}>Changelog</h2>
              </div>
              <div className="space-y-4">
                <div className="border-l-2 border-primary-600 pl-4">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="font-mono text-sm font-medium">v{plugin.metadata.version}</span>
                    <span className="text-xs text-gray-500">Current Version</span>
                  </div>
                  <ul className="text-sm text-gray-700 dark:text-gray-300 space-y-1">
                    <li>• Latest stable release</li>
                    <li>• Performance improvements</li>
                    <li>• Bug fixes and stability updates</li>
                  </ul>
                </div>
              </div>
            </div>

            {/* Dependency Graph */}
            <PluginDependencyGraph
              plugins={getMockGraphNodes()}
              dependencies={getMockDependencies(plugin.id)}
              onPluginSelect={(pluginId) => {
                if (pluginId !== plugin.id) {
                  navigate(`/plugins/${pluginId}`);
                }
              }}
            />

            {/* Performance Metrics */}
            <PluginPerformanceMetrics
              pluginId={plugin.id}
              timeRange="24h"
              refreshInterval={60000}
            />

            {/* Logs Viewer */}
            <PluginLogsViewer
              pluginId={plugin.id}
              maxLogs={1000}
              streaming={plugin.status === 'active'}
              refreshInterval={2000}
            />
          </div>

          {/* Sidebar */}
          <div className="space-y-6">
            {/* Stats */}
            <div className={cardStyles.base}>
              <h3 className={cn(textStyles.h4, 'mb-4')}>Statistics</h3>
              <div className="space-y-3">
                <div>
                  <div className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                    {plugin.stats?.usageCount || 0}
                  </div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">Usage Count</div>
                </div>
                <div>
                  <div className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                    {plugin.stats?.errorCount || 0}
                  </div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">Errors</div>
                </div>
                <div>
                  <div className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                    {plugin.stats?.lastUsed ? new Date(plugin.stats.lastUsed).toLocaleDateString() : 'Never'}
                  </div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">Last Used</div>
                </div>
              </div>
            </div>

            {/* Health Monitor */}
            <PluginHealthMonitor pluginId={plugin.id} autoRefresh={true} refreshInterval={30000} />

            {/* Dependencies */}
            <div className={cardStyles.base}>
              <h3 className={cn(textStyles.h4, 'mb-4')}>Dependencies</h3>
              {plugin.capabilities.length > 0 ? (
                <div className="space-y-2">
                  {plugin.capabilities.slice(0, 3).map((cap, idx) => (
                    <div key={idx} className="text-xs font-mono p-2 bg-gray-50 dark:bg-gray-900/50 rounded">
                      {cap.name}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-600 dark:text-gray-400">No dependencies</p>
              )}
            </div>

            {/* Links */}
            <div className={cardStyles.base}>
              <h3 className={cn(textStyles.h4, 'mb-4')}>Resources</h3>
              <div className="space-y-2">
                <a
                  href="#"
                  className="flex items-center gap-2 text-sm text-primary-600 hover:text-primary-700"
                >
                  <ExternalLink className="h-4 w-4" />
                  Documentation
                </a>
                <a
                  href="#"
                  className="flex items-center gap-2 text-sm text-primary-600 hover:text-primary-700"
                >
                  <ExternalLink className="h-4 w-4" />
                  Source Code
                </a>
                <a
                  href="#"
                  className="flex items-center gap-2 text-sm text-primary-600 hover:text-primary-700"
                >
                  <ExternalLink className="h-4 w-4" />
                  Report Issue
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
