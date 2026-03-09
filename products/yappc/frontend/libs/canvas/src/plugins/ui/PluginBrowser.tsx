/**
 * Plugin Browser Component
 * 
 * Browse and search for plugins in the marketplace
 */

import { useState, useEffect, useCallback } from 'react';

import { getMarketplaceManager } from '../marketplaceManager';
import { InstallationProgress } from './InstallationProgress';
import { PluginCard } from './PluginCard';

import type { MarketplacePlugin, SearchFilters } from '../marketplaceTypes';

/**
 *
 */
export interface PluginBrowserProps {
  /** Initial search filters */
  initialFilters?: SearchFilters;
  
  /** Callback when a plugin is selected */
  onPluginSelect?: (plugin: MarketplacePlugin) => void;
  
  /** Show installation progress */
  showProgress?: boolean;
  
  /** Custom CSS class */
  className?: string;
}

/**
 * PluginBrowser - Browse marketplace plugins
 * 
 * @example
 * ```tsx
 * <PluginBrowser
 *   initialFilters={{ category: 'diagram', verifiedOnly: true }}
 *   onPluginSelect={(plugin) => console.log('Selected:', plugin.manifest.name)}
 *   showProgress={true}
 * />
 * ```
 */
export function PluginBrowser({
  initialFilters = {},
  onPluginSelect,
  showProgress = true,
  className = '',
}: PluginBrowserProps) {
  const [plugins, setPlugins] = useState<MarketplacePlugin[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<SearchFilters>(initialFilters);
  const [searchQuery, setSearchQuery] = useState('');
  const [installing, setInstalling] = useState<Set<string>>(new Set());
  
  const marketplace = getMarketplaceManager();
  
  // Load plugins
  const loadPlugins = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      const results = await marketplace.search({
        ...filters,
        query: searchQuery || undefined,
      });
      
      setPlugins(results.plugins);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [filters, searchQuery, marketplace]);
  
  // Load on mount and when filters change
  useEffect(() => {
    loadPlugins();
  }, [loadPlugins]);
  
  // Listen for installation events
  useEffect(() => {
    const handleInstallStart = (pluginId: string) => {
      setInstalling(prev => new Set(prev).add(pluginId));
    };
    
    const handleInstallComplete = (pluginId: string) => {
      setInstalling(prev => {
        const next = new Set(prev);
        next.delete(pluginId);
        return next;
      });
    };
    
    const handleInstallFailed = (pluginId: string) => {
      setInstalling(prev => {
        const next = new Set(prev);
        next.delete(pluginId);
        return next;
      });
    };
    
    marketplace.on('install-started', handleInstallStart);
    marketplace.on('install-completed', handleInstallComplete);
    marketplace.on('install-failed', handleInstallFailed);
    
    return () => {
      marketplace.off('install-started', handleInstallStart);
      marketplace.off('install-completed', handleInstallComplete);
      marketplace.off('install-failed', handleInstallFailed);
    };
  }, [marketplace]);
  
  const handleSearch = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    loadPlugins();
  }, [loadPlugins]);
  
  const handleInstall = useCallback(async (pluginId: string) => {
    try {
      await marketplace.install(pluginId, { autoActivate: true });
    } catch (err) {
      console.error('Installation failed:', err);
    }
  }, [marketplace]);
  
  return (
    <div className={`plugin-browser ${className}`}>
      {/* Search Bar */}
      <form onSubmit={handleSearch} className="search-bar">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search plugins..."
          className="search-input"
        />
        <button type="submit" className="search-button">
          Search
        </button>
      </form>
      
      {/* Filters */}
      <div className="filters">
        <label>
          <input
            type="checkbox"
            checked={filters.verifiedOnly ?? true}
            onChange={(e) => setFilters({ ...filters, verifiedOnly: e.target.checked })}
          />
          Verified only
        </label>
        <label>
          <input
            type="checkbox"
            checked={filters.featuredOnly ?? false}
            onChange={(e) => setFilters({ ...filters, featuredOnly: e.target.checked })}
          />
          Featured
        </label>
        <select
          value={filters.category || ''}
          onChange={(e) => setFilters({ ...filters, category: e.target.value || undefined })}
        >
          <option value="">All Categories</option>
          <option value="diagram">Diagram</option>
          <option value="import-export">Import/Export</option>
          <option value="collaboration">Collaboration</option>
          <option value="ai">AI</option>
          <option value="visualization">Visualization</option>
        </select>
      </div>
      
      {/* Installation Progress */}
      {showProgress && installing.size > 0 && (
        <div className="installation-progress-container">
          {Array.from(installing).map(pluginId => (
            <InstallationProgress key={pluginId} pluginId={pluginId} />
          ))}
        </div>
      )}
      
      {/* Loading State */}
      {loading && (
        <div className="loading">Loading plugins...</div>
      )}
      
      {/* Error State */}
      {error && (
        <div className="error">Error: {error}</div>
      )}
      
      {/* Plugin Grid */}
      {!loading && !error && (
        <div className="plugin-grid">
          {plugins.length === 0 ? (
            <div className="no-results">No plugins found</div>
          ) : (
            plugins.map(plugin => (
              <PluginCard
                key={plugin.manifest.id}
                plugin={plugin}
                onInstall={() => handleInstall(plugin.manifest.id)}
                onSelect={() => onPluginSelect?.(plugin)}
                installing={installing.has(plugin.manifest.id)}
              />
            ))
          )}
        </div>
      )}
    </div>
  );
}
