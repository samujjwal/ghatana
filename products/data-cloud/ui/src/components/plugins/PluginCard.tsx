/**
 * Plugin Card Component
 *
 * Displays plugin information with actions and status.
 *
 * @doc.type component
 * @doc.purpose Plugin display card
 * @doc.layer frontend
 */

import React from 'react';
import {
  Package,
  Power,
  Settings,
  Trash2,
  Download,
  ExternalLink,
  AlertCircle,
  CheckCircle,
  Clock,
  Star,
  Shield,
  TrendingUp,
  Activity,
  FileText,
} from 'lucide-react';
import { cn, cardStyles, textStyles } from '../../lib/theme';
import type { Plugin, PluginMarketplaceItem, PluginStatus, PluginCategory } from '../../api/plugin.service';

interface PluginCardProps {
  plugin: Plugin | PluginMarketplaceItem;
  mode: 'installed' | 'marketplace';
  onEnable?: (id: string) => void;
  onDisable?: (id: string) => void;
  onConfigure?: (id: string) => void;
  onUninstall?: (id: string) => void;
  onViewDetails?: (id: string) => void;
  onInstall?: (id: string) => void;
  onUpdate?: (id: string) => void;
  className?: string;
}

/**
 * Get status badge color
 */
function getStatusColor(status: PluginStatus): string {
  switch (status) {
    case 'active':
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
    case 'inactive':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-300';
    case 'error':
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300';
    case 'installing':
    case 'uninstalling':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-300';
  }
}

/**
 * Get category icon
 */
function getCategoryIcon(category: PluginCategory): React.ReactNode {
  switch (category) {
    case 'connector':
      return <Package className="h-4 w-4" />;
    case 'transformer':
      return <TrendingUp className="h-4 w-4" />;
    case 'quality':
      return <CheckCircle className="h-4 w-4" />;
    case 'governance':
      return <Shield className="h-4 w-4" />;
    case 'ai':
      return <Star className="h-4 w-4" />;
    default:
      return <Package className="h-4 w-4" />;
  }
}

export function PluginCard({
  plugin,
  mode,
  onEnable,
  onDisable,
  onConfigure,
  onUninstall,
  onInstall,
  onUpdate,
  onViewDetails,
  className,
}: PluginCardProps): React.ReactElement {

  const { metadata } = plugin;

  const isInstalled = mode === 'installed';
  const installedPlugin = isInstalled ? (plugin as Plugin) : null;
  const marketplacePlugin = !isInstalled ? (plugin as PluginMarketplaceItem) : null;

  const status = installedPlugin?.status || 'inactive';
  const isActive = status === 'active';
  const isProcessing = status === 'installing' || status === 'uninstalling';

  const handleCardClick = () => {
    if (isInstalled && onViewDetails) {
      onViewDetails(plugin.id);
    }
  };

  return (
    <div
      className={cn(
        cardStyles.base,
        'relative overflow-hidden transition-all hover:shadow-lg group',
        isActive && 'ring-2 ring-green-500/20',
        isInstalled && onViewDetails && 'cursor-pointer',
        className
      )}
      onClick={handleCardClick}
    >
      {/* Quick Actions Overlay - Shows on Hover */}
      <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity flex gap-1 z-10">
        {mode === 'installed' && installedPlugin && (
          <>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onConfigure?.(plugin.id);
              }}
              className={cn(
                'p-2 bg-white dark:bg-gray-800 rounded-lg shadow-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors',
                'border border-gray-200 dark:border-gray-600'
              )}
              title="Configure"
            >
              <Settings className="h-4 w-4 text-gray-700 dark:text-gray-300" />
            </button>
            {isActive ? (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onDisable?.(plugin.id);
                }}
                className={cn(
                  'p-2 bg-white dark:bg-gray-800 rounded-lg shadow-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors',
                  'border border-gray-200 dark:border-gray-600'
                )}
                disabled={isProcessing}
                title="Disable"
              >
                <Power className="h-4 w-4 text-green-600" />
              </button>
            ) : (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onEnable?.(plugin.id);
                }}
                className={cn(
                  'p-2 bg-white dark:bg-gray-800 rounded-lg shadow-lg hover:bg-green-50 dark:hover:bg-green-900/20 transition-colors',
                  'border border-gray-200 dark:border-gray-600'
                )}
                disabled={isProcessing}
                title="Enable"
              >
                <Power className="h-4 w-4 text-gray-400" />
              </button>
            )}
          </>
        )}
      </div>

      {/* Header */}
      <div className="p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 flex-1 min-w-0">
            {/* Icon */}
            <div className={cn(
              "flex-shrink-0 w-12 h-12 rounded-lg flex items-center justify-center text-white transition-transform group-hover:scale-110",
              isActive 
                ? "bg-gradient-to-br from-green-500 to-green-600"
                : "bg-gradient-to-br from-gray-400 to-gray-500"
            )}>
              {metadata.icon ? (
                <span className="text-2xl">{metadata.icon}</span>
              ) : (
                getCategoryIcon(metadata.category)
              )}
            </div>

            {/* Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <h3 className={cn(textStyles.h4, 'truncate group-hover:text-primary-600 transition-colors')}>
                  {metadata.name}
                </h3>
                {marketplacePlugin?.isOfficial && (
                  <span title="Official Plugin">
                    <Shield className="h-4 w-4 text-blue-600 flex-shrink-0" />
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <p className={cn(textStyles.small, 'text-gray-600 dark:text-gray-400')}>
                  v{metadata.version}
                </p>
                <span className="text-gray-400">•</span>
                <p className={cn(textStyles.small, 'text-gray-600 dark:text-gray-400')}>
                  {metadata.author}
                </p>
              </div>
            </div>
          </div>

          {/* Badges */}
          <div className="flex flex-col items-end gap-2 flex-shrink-0">
            {/* Update Available Badge */}
            {isInstalled && metadata.version !== '2.0.0' && (
              <span className="px-2 py-1 text-xs font-medium rounded-full bg-blue-100 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300 animate-pulse">
                Update
              </span>
            )}
            
            {/* Status Badge */}
            {isInstalled && (
              <span
                className={cn(
                  'px-2 py-1 text-xs font-medium rounded-full flex items-center gap-1',
                  getStatusColor(status)
                )}
              >
                {isProcessing && <Clock className="h-3 w-3 animate-spin" />}
                {status === 'active' && <CheckCircle className="h-3 w-3" />}
                {status === 'error' && <AlertCircle className="h-3 w-3" />}
                {status.charAt(0).toUpperCase() + status.slice(1)}
              </span>
            )}
          </div>
        </div>

        {/* Description */}
        <p className={cn(textStyles.body, 'mt-3 text-sm text-gray-700 dark:text-gray-300 line-clamp-2')}>
          {metadata.description}
        </p>

        {/* Tags */}
        <div className="flex flex-wrap gap-2 mt-3">
          <span className="px-2 py-1 text-xs font-medium bg-primary-100 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300 rounded-md">
            {metadata.category}
          </span>
          {metadata.tags.slice(0, 2).map((tag) => (
            <span
              key={tag}
              className="px-2 py-1 text-xs bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 rounded-md"
            >
              #{tag}
            </span>
          ))}
          {metadata.tags.length > 2 && (
            <span className="px-2 py-1 text-xs bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 rounded-md">
              +{metadata.tags.length - 2}
            </span>
          )}
        </div>
      </div>

      {/* Live Stats Section */}
      <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        {mode === 'installed' && installedPlugin ? (
          <div className="grid grid-cols-3 gap-4">
            {/* Usage Count */}
            <div className="flex flex-col">
              <span className="text-xs text-gray-500 dark:text-gray-400 mb-1">Usage</span>
              <div className="flex items-baseline gap-1">
                <Activity className="h-4 w-4 text-blue-500" />
                <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {installedPlugin.stats?.usageCount?.toLocaleString() || 0}
                </span>
              </div>
            </div>

            {/* Error Count */}
            <div className="flex flex-col">
              <span className="text-xs text-gray-500 dark:text-gray-400 mb-1">Errors</span>
              <div className="flex items-baseline gap-1">
                {installedPlugin.stats?.errorCount && installedPlugin.stats.errorCount > 0 ? (
                  <>
                    <AlertCircle className="h-4 w-4 text-red-500" />
                    <span className="text-sm font-semibold text-red-600 dark:text-red-400">
                      {installedPlugin.stats.errorCount}
                    </span>
                  </>
                ) : (
                  <>
                    <CheckCircle className="h-4 w-4 text-green-500" />
                    <span className="text-sm font-semibold text-green-600 dark:text-green-400">
                      0
                    </span>
                  </>
                )}
              </div>
            </div>

            {/* Last Used */}
            <div className="flex flex-col">
              <span className="text-xs text-gray-500 dark:text-gray-400 mb-1">Last Used</span>
              <div className="flex items-baseline gap-1">
                <Clock className="h-4 w-4 text-purple-500" />
                <span className="text-xs font-semibold text-gray-900 dark:text-gray-100">
                  {installedPlugin.stats?.lastUsed 
                    ? new Date(installedPlugin.stats.lastUsed).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
                    : 'Never'}
                </span>
              </div>
            </div>
          </div>
        ) : mode === 'marketplace' && marketplacePlugin ? (
          <div className="grid grid-cols-2 gap-4">
            <div className="flex items-center gap-2">
              <Download className="h-4 w-4 text-blue-500" />
              <span className={cn(textStyles.small, 'font-medium')}>
                {marketplacePlugin.downloads.toLocaleString()}
              </span>
              <span className="text-xs text-gray-500">downloads</span>
            </div>
            <div className="flex items-center gap-2">
              <Star className="h-4 w-4 text-yellow-500 fill-current" />
              <span className={cn(textStyles.small, 'font-medium')}>
                {marketplacePlugin.rating.toFixed(1)}
              </span>
              <span className="text-xs text-gray-500">
                ({marketplacePlugin.reviewCount})
              </span>
            </div>
          </div>
        ) : null}
      </div>

      {/* Actions Footer */}
      <div className="p-4 bg-gray-50 dark:bg-gray-800/50">
        <div className="flex items-center justify-between gap-3">
          {/* Quick Info */}
          <div className="flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
            {metadata.homepage && (
              <a
                href={metadata.homepage}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1 hover:text-primary-600 dark:hover:text-primary-400 transition-colors"
                onClick={(e) => e.stopPropagation()}
              >
                <ExternalLink className="h-3 w-3" />
                <span>Website</span>
              </a>
            )}
            {metadata.documentation && (
              <a
                href={metadata.documentation}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1 hover:text-primary-600 dark:hover:text-primary-400 transition-colors"
                onClick={(e) => e.stopPropagation()}
              >
                <FileText className="h-3 w-3" />
                <span>Docs</span>
              </a>
            )}
            {mode === 'installed' && installedPlugin?.stats?.averageExecutionTime && (
              <div className="flex items-center gap-1">
                <TrendingUp className="h-3 w-3" />
                <span>
                  Avg: {installedPlugin.stats.averageExecutionTime.toFixed(0)}ms
                </span>
              </div>
            )}
          </div>

          {/* Primary Actions */}
          <div className="flex items-center gap-2">
            {mode === 'installed' && installedPlugin ? (
              <>
                {/* View Details */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onViewDetails?.(plugin.id);
                  }}
                  className={cn(
                    'px-3 py-1.5 text-xs font-medium rounded-lg',
                    'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-200',
                    'border border-gray-300 dark:border-gray-600',
                    'hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors',
                    'flex items-center gap-1'
                  )}
                >
                  <TrendingUp className="h-3 w-3" />
                  Details
                </button>

                {/* Update if available */}
                {metadata.version !== '2.0.0' && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onUpdate?.(plugin.id);
                    }}
                    className={cn(
                      'px-3 py-1.5 text-xs font-medium rounded-lg',
                      'bg-blue-600 hover:bg-blue-700 text-white transition-colors',
                      'flex items-center gap-1'
                    )}
                  >
                    <Download className="h-3 w-3" />
                    Update
                  </button>
                )}

                {/* Uninstall */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onUninstall?.(plugin.id);
                  }}
                  className={cn(
                    'p-1.5 rounded-lg',
                    'text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors'
                  )}
                  disabled={isProcessing}
                  title="Uninstall"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </>
            ) : mode === 'marketplace' && marketplacePlugin ? (
              <>
                {/* View Details - Marketplace */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    // For marketplace, could show a preview modal or navigate
                  }}
                  className={cn(
                    'px-3 py-1.5 text-xs font-medium rounded-lg',
                    'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-200',
                    'border border-gray-300 dark:border-gray-600',
                    'hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors'
                  )}
                >
                  Preview
                </button>

                {/* Install or Update */}
                {marketplacePlugin.isInstalled ? (
                  marketplacePlugin.updateAvailable ? (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onUpdate?.(plugin.id);
                      }}
                      className={cn(
                        'px-4 py-1.5 text-xs font-medium rounded-lg',
                        'bg-blue-600 hover:bg-blue-700 text-white transition-colors',
                        'flex items-center gap-1.5'
                      )}
                    >
                      <Download className="h-3 w-3" />
                      Update
                    </button>
                  ) : (
                    <span className="px-4 py-1.5 text-xs text-gray-500 dark:text-gray-400">
                      Installed ✓
                    </span>
                  )
                ) : (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onInstall?.(plugin.id);
                    }}
                    className={cn(
                      'px-4 py-1.5 text-xs font-medium rounded-lg',
                      'bg-primary-600 hover:bg-primary-700 text-white transition-colors',
                      'flex items-center gap-1.5'
                    )}
                  >
                    <Download className="h-3 w-3" />
                    Install
                  </button>
                )}
              </>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
