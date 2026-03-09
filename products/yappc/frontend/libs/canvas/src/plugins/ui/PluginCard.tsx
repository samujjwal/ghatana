/**
 * Plugin Card Component
 * 
 * Display plugin information in a card format
 */

import type { MarketplacePlugin } from '../marketplaceTypes';

/**
 *
 */
export interface PluginCardProps {
  /** Plugin to display */
  plugin: MarketplacePlugin;
  
  /** Handler for install button */
  onInstall?: () => void;
  
  /** Handler for card selection */
  onSelect?: () => void;
  
  /** Whether plugin is currently installing */
  installing?: boolean;
  
  /** Custom CSS class */
  className?: string;
}

/**
 * PluginCard - Display plugin information
 * 
 * @example
 * ```tsx
 * <PluginCard
 *   plugin={marketplacePlugin}
 *   onInstall={() => handleInstall(plugin.manifest.id)}
 *   onSelect={() => setSelectedPlugin(plugin)}
 *   installing={false}
 * />
 * ```
 */
export function PluginCard({
  plugin,
  onInstall,
  onSelect,
  installing = false,
  className = '',
}: PluginCardProps) {
  const { manifest, marketplace } = plugin;
  
  return (
    <div 
      className={`plugin-card ${className}`} 
      onClick={onSelect}
      role="button"
      tabIndex={0}
    >
      {/* Header */}
      <div className="plugin-card-header">
        <h3 className="plugin-name">{manifest.name}</h3>
        {marketplace.verified && (
          <span className="verified-badge" title="Verified Publisher">
            ✓
          </span>
        )}
        {marketplace.featured && (
          <span className="featured-badge" title="Featured">
            ⭐
          </span>
        )}
      </div>
      
      {/* Metadata */}
      <div className="plugin-metadata">
        <span className="version">v{manifest.version}</span>
        <span className="author">{manifest.author.name}</span>
      </div>
      
      {/* Description */}
      <p className="plugin-description">{manifest.description}</p>
      
      {/* Stats */}
      <div className="plugin-stats">
        <span className="downloads">
          {marketplace.stats.downloads.toLocaleString()} downloads
        </span>
        <span className="rating">
          ⭐ {marketplace.stats.rating.toFixed(1)} ({marketplace.stats.reviews})
        </span>
      </div>
      
      {/* Tags */}
      {marketplace.tags && marketplace.tags.length > 0 && (
        <div className="plugin-tags">
          {marketplace.tags.slice(0, 3).map((tag: string) => (
            <span key={tag} className="tag">{tag}</span>
          ))}
        </div>
      )}
      
      {/* Install Button */}
      <button
        className="install-button"
        onClick={(e) => {
          e.stopPropagation();
          onInstall?.();
        }}
        disabled={installing}
      >
        {installing ? 'Installing...' : 'Install'}
      </button>
    </div>
  );
}
