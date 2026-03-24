/**
 * Extension Marketplace Manager
 * 
 * Handles plugin discovery, installation, updates, and marketplace integration.
 */

import {
  type MarketplacePlugin,
  type SearchFilters,
  type SearchResults,
  type InstallationProgress,
  type InstallationStatus,
  type InstallOptions,
  type UninstallOptions,
  type PluginUpdate,
  type SecurityVerification,
  type MarketplaceConfig,
} from './marketplaceTypes';
import { PluginManager } from './pluginManager';
import { PluginError } from './types';

/**
 * Event handler type
 */
type EventHandler = (...args: unknown[]) => void;

/**
 * Marketplace events
 */
export type MarketplaceEventMap = {
  'search-started': [filters: SearchFilters];
  'search-completed': [results: SearchResults];
  'search-failed': [error: Error];
  'install-started': [pluginId: string];
  'install-progress': [pluginId: string, progress: InstallationProgress];
  'install-completed': [pluginId: string];
  'install-failed': [pluginId: string, error: Error];
  'uninstall-started': [pluginId: string];
  'uninstall-completed': [pluginId: string];
  'uninstall-failed': [pluginId: string, error: Error];
  'update-available': [pluginId: string, update: PluginUpdate];
  'update-started': [pluginId: string];
  'update-completed': [pluginId: string];
  'update-failed': [pluginId: string, error: Error];
  'security-warning': [pluginId: string, issues: string[]];
};

/**
 * Marketplace Manager - Singleton
 * 
 * Manages plugin discovery, installation, and marketplace integration.
 */
export class MarketplaceManager {
  private static instance: MarketplaceManager;
  private config: MarketplaceConfig;
  private installations = new Map<string, InstallationProgress>();
  private updateCheckTimer?: NodeJS.Timeout;
  private eventHandlers = new Map<keyof MarketplaceEventMap, Set<EventHandler>>();
  
  /**
   *
   */
  private constructor(config: Partial<MarketplaceConfig> = {}) {
    this.config = {
      apiUrl: config.apiUrl || 'https://marketplace.yappc.dev/api',
      token: config.token,
      autoUpdate: config.autoUpdate ?? false,
      updateCheckInterval: config.updateCheckInterval || 24 * 60 * 60 * 1000, // 24 hours
      allowPrerelease: config.allowPrerelease ?? false,
      verifiedOnly: config.verifiedOnly ?? true,
      cacheDir: config.cacheDir || './.plugin-cache',
      maxConcurrentDownloads: config.maxConcurrentDownloads || 3,
    };
    
    if (this.config.autoUpdate) {
      this.startUpdateChecker();
    }
  }
  
  /**
   * Get singleton instance
   */
  static getInstance(config?: Partial<MarketplaceConfig>): MarketplaceManager {
    if (!MarketplaceManager.instance) {
      MarketplaceManager.instance = new MarketplaceManager(config);
    }
    return MarketplaceManager.instance;
  }
  
  /**
   * Search marketplace for plugins
   */
  async search(filters: SearchFilters = {}): Promise<SearchResults> {
    this.emit('search-started', filters);
    
    try {
      const params = new URLSearchParams();
      
      if (filters.query) params.append('q', filters.query);
      if (filters.category) params.append('category', filters.category);
      if (filters.tags) params.append('tags', filters.tags.join(','));
      if (filters.capabilities) params.append('capabilities', filters.capabilities.join(','));
      if (filters.verifiedOnly !== undefined) params.append('verified', filters.verifiedOnly.toString());
      if (filters.featuredOnly !== undefined) params.append('featured', filters.featuredOnly.toString());
      if (filters.minRating !== undefined) params.append('minRating', filters.minRating.toString());
      if (filters.sortBy) params.append('sort', filters.sortBy);
      params.append('limit', (filters.limit || 20).toString());
      params.append('offset', (filters.offset || 0).toString());
      
      const response = await fetch(`${this.config.apiUrl}/plugins/search?${params}`, {
        headers: this.getHeaders(),
      });
      
      if (!response.ok) {
        throw new Error(`Search failed: ${response.statusText}`);
      }
      
      const data = await response.json();
      
      const results: SearchResults = {
        plugins: data.plugins.map((p: unknown) => this.parseMarketplacePlugin(p)),
        total: data.total,
        offset: data.offset,
        limit: data.limit,
        hasMore: data.offset + data.limit < data.total,
      };
      
      this.emit('search-completed', results);
      return results;
    } catch (error) {
      const pluginError = new PluginError(
        `Failed to search marketplace: ${(error as Error).message}`,
        'marketplace',
        'RUNTIME_ERROR',
      );
      this.emit('search-failed', pluginError);
      throw pluginError;
    }
  }
  
  /**
   * Get plugin details from marketplace
   */
  async getPlugin(pluginId: string): Promise<MarketplacePlugin> {
    try {
      const response = await fetch(`${this.config.apiUrl}/plugins/${encodeURIComponent(pluginId)}`, {
        headers: this.getHeaders(),
      });
      
      if (!response.ok) {
        throw new Error(`Failed to get plugin: ${response.statusText}`);
      }
      
      const data = await response.json();
      return this.parseMarketplacePlugin(data);
    } catch (error) {
      throw new PluginError(
        `Failed to get plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        'RUNTIME_ERROR',
      );
    }
  }
  
  /**
   * Install a plugin from marketplace
   */
  async install(pluginId: string, options: InstallOptions = {}): Promise<void> {
    this.emit('install-started', pluginId);
    
    // Check if already installing
    if (this.installations.has(pluginId)) {
      const error = new PluginError(
        `Plugin "${pluginId}" is already being installed`,
        pluginId,
        'RUNTIME_ERROR',
      );
      this.emit('install-failed', pluginId, error);
      throw error;
    }
    
    // Check if already installed
    const pluginManager = PluginManager.getInstance();
    const existingPlugin = pluginManager.getPlugins().find(p => p.id === pluginId);
    
    if (existingPlugin && !options.force) {
      throw new PluginError(
        `Plugin "${pluginId}" is already installed. Use force option to reinstall.`,
        pluginId,
        'RUNTIME_ERROR',
      );
    }
    
    // Initialize progress tracking
    this.updateProgress(pluginId, {
      pluginId,
      status: 'pending',
      progress: 0,
      step: 'Fetching plugin information...',
    });
    
    try {
      // Get plugin details
      const marketplacePlugin = await this.getPlugin(pluginId);
      
      // Find version to install
      const version = options.version || marketplacePlugin.manifest.version;
      const versionInfo = marketplacePlugin.marketplace.versions.find(v => v.version === version);
      
      if (!versionInfo) {
        throw new Error(`Version ${version} not found`);
      }
      
      // Verify permissions
      if (!options.skipVerification) {
        this.updateProgress(pluginId, {
          pluginId,
          status: 'verifying',
          progress: 10,
          step: 'Verifying plugin security...',
        });
        
        const verification = await this.verifyPlugin(marketplacePlugin);
        if (!verification.verified) {
          throw new Error(`Security verification failed: ${verification.errors?.join(', ')}`);
        }
      }
      
      // Install dependencies
      if (!options.skipDependencies && marketplacePlugin.manifest.dependencies) {
        this.updateProgress(pluginId, {
          pluginId,
          status: 'installing',
          progress: 20,
          step: 'Installing dependencies...',
        });
        
        await this.installDependencies(marketplacePlugin.manifest.dependencies);
      }
      
      // Download plugin
      this.updateProgress(pluginId, {
        pluginId,
        status: 'downloading',
        progress: 40,
        step: 'Downloading plugin...',
      });
      
      const pluginBundle = await this.downloadPlugin(versionInfo.downloadUrl, (progress) => {
        this.updateProgress(pluginId, {
          pluginId,
          status: 'downloading',
          progress: 40 + progress * 0.4, // 40-80%
          step: `Downloading plugin (${Math.round(progress * 100)}%)...`,
        });
      });
      
      // Install plugin
      this.updateProgress(pluginId, {
        pluginId,
        status: 'installing',
        progress: 80,
        step: 'Installing plugin...',
      });
      
      await this.installPluginBundle(pluginBundle, marketplacePlugin.manifest);
      
      // Activate if requested
      if (options.autoActivate !== false) {
        this.updateProgress(pluginId, {
          pluginId,
          status: 'installing',
          progress: 90,
          step: 'Activating plugin...',
        });
        
        await pluginManager.activate(pluginId);
      }
      
      // Complete
      this.updateProgress(pluginId, {
        pluginId,
        status: 'installed',
        progress: 100,
        step: 'Installation complete!',
      });
      
      this.emit('install-completed', pluginId);
      
      // Clean up progress after delay
      setTimeout(() => {
        this.installations.delete(pluginId);
      }, 5000);
      
    } catch (error) {
      this.updateProgress(pluginId, {
        pluginId,
        status: 'failed',
        progress: 0,
        step: 'Installation failed',
        error: (error as Error).message,
      });
      
      const pluginError = new PluginError(
        `Failed to install plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        'RUNTIME_ERROR',
      );
      
      this.emit('install-failed', pluginId, pluginError);
      
      setTimeout(() => {
        this.installations.delete(pluginId);
      }, 10000);
      
      throw pluginError;
    }
  }
  
  /**
   * Uninstall a plugin
   */
  async uninstall(pluginId: string, options: UninstallOptions = {}): Promise<void> {
    const pluginManager = PluginManager.getInstance();
    this.emit('uninstall-started', pluginId);
    
    // Check dependencies
    if (!options.force) {
      const dependents = this.findDependentPlugins(pluginId);
      if (dependents.length > 0) {
        const error = new PluginError(
          `Cannot uninstall "${pluginId}": Required by ${dependents.join(', ')}. Use force option to override.`,
          pluginId,
          'DEPENDENCY_MISSING',
        );
        this.emit('uninstall-failed', pluginId, error);
        throw error;
      }
    }
    
    try {
      // Unregister from plugin manager
      await pluginManager.unregister(pluginId);
      
      // Remove cached data
      if (options.removeData) {
        await this.removePluginData(pluginId);
      }
      
      this.emit('uninstall-completed', pluginId);
      
    } catch (error) {
      const pluginError = new PluginError(
        `Failed to uninstall plugin "${pluginId}": ${(error as Error).message}`,
        pluginId,
        'RUNTIME_ERROR',
      );
      this.emit('uninstall-failed', pluginId, pluginError);
      throw pluginError;
    }
  }
  
  /**
   * Check for plugin updates
   */
  async checkUpdates(): Promise<PluginUpdate[]> {
    const pluginManager = PluginManager.getInstance();
    const installedPlugins = pluginManager.getPlugins();
    const updates: PluginUpdate[] = [];
    
    for (const pluginManifest of installedPlugins) {
      try {
        const marketplacePlugin = await this.getPlugin(pluginManifest.id);
        const currentVersion = pluginManifest.version;
        const latestVersion = marketplacePlugin.manifest.version;
        
        if (this.isNewerVersion(latestVersion, currentVersion)) {
          const latestVersionInfo = marketplacePlugin.marketplace.versions.find(
            v => v.version === latestVersion
          );
          
          updates.push({
            pluginId: pluginManifest.id,
            currentVersion,
            latestVersion,
            changelog: latestVersionInfo?.changelog,
            breaking: this.isBreakingChange(currentVersion, latestVersion),
            priority: this.determineUpdatePriority(currentVersion, latestVersion),
          });
        }
      } catch (error) {
        // Skip plugins not in marketplace
        continue;
      }
    }
    
    return updates;
  }
  
  /**
   * Update a plugin to latest version
   */
  async update(pluginId: string, options: InstallOptions = {}): Promise<void> {
    await this.uninstall(pluginId, { removeData: false });
    await this.install(pluginId, { ...options, autoActivate: true });
  }
  
  /**
   * Get installation progress
   */
  getInstallationProgress(pluginId: string): InstallationProgress | undefined {
    return this.installations.get(pluginId);
  }
  
  /**
   * Get all active installations
   */
  getActiveInstallations(): InstallationProgress[] {
    return Array.from(this.installations.values());
  }
  
  /**
   * Cancel an installation
   */
  cancelInstallation(pluginId: string): void {
    const progress = this.installations.get(pluginId);
    if (progress && progress.status !== 'installed' && progress.status !== 'failed') {
      this.updateProgress(pluginId, {
        ...progress,
        status: 'cancelled',
        step: 'Installation cancelled',
      });
      
      setTimeout(() => {
        this.installations.delete(pluginId);
      }, 5000);
    }
  }
  
  // Private helper methods
  
  /**
   *
   */
  private getHeaders(): HeadersInit {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };
    
    if (this.config.token) {
      headers['Authorization'] = `Bearer ${this.config.token}`;
    }
    
    return headers;
  }
  
  /**
   *
   */
  private parseMarketplacePlugin(data: unknown): MarketplacePlugin {
    return {
      manifest: data.manifest,
      downloadUrl: data.downloadUrl,
      marketplace: {
        publisher: data.publisher,
        stats: data.stats,
        publishedAt: new Date(data.publishedAt),
        updatedAt: new Date(data.updatedAt),
        versions: data.versions.map((v: unknown) => ({
          ...v,
          releaseDate: new Date(v.releaseDate),
        })),
        media: data.media,
        tags: data.tags,
        category: data.category,
        featured: data.featured,
        verified: data.verified,
        deprecated: data.deprecated,
      },
    };
  }
  
  /**
   *
   */
  private async verifyPlugin(plugin: MarketplacePlugin): Promise<SecurityVerification> {
    // In a real implementation, this would:
    // 1. Verify code signature
    // 2. Check checksums
    // 3. Scan for known vulnerabilities
    // 4. Validate permissions
    
    const checks = {
      signature: plugin.marketplace.verified || false,
      checksum: true, // Would verify actual checksum
      vulnerabilities: true, // Would scan for vulnerabilities
      permissions: true, // Would validate permissions
    };
    
    const warnings: string[] = [];
    if (!plugin.marketplace.verified) {
      warnings.push('Plugin publisher is not verified');
    }
    
    return {
      verified: Object.values(checks).every(Boolean),
      checks,
      warnings,
      errors: [],
      verifiedAt: new Date(),
    };
  }
  
  /**
   *
   */
  private async installDependencies(dependencies: string[]): Promise<void> {
    const pluginManager = PluginManager.getInstance();
    
    for (const depId of dependencies) {
      const installed = pluginManager.getPlugins().find(p => p.id === depId);
      if (!installed) {
        // Recursively install dependency
        await this.install(depId, { autoActivate: true });
      }
    }
  }
  
  /**
   *
   */
  private async downloadPlugin(
    url: string,
    onProgress?: (progress: number) => void
  ): Promise<Blob> {
    const response = await fetch(url);
    
    if (!response.ok) {
      throw new Error(`Download failed: ${response.statusText}`);
    }
    
    const total = parseInt(response.headers.get('content-length') || '0', 10);
    let loaded = 0;
    
    const reader = response.body?.getReader();
    if (!reader) {
      throw new Error('Response body is not readable');
    }
    
    const chunks: Uint8Array[] = [];
    
    while (true) {
      const { done, value } = await reader.read();
      
      if (done) break;
      
      chunks.push(value);
      loaded += value.length;
      
      if (onProgress && total > 0) {
        onProgress(loaded / total);
      }
    }
    
    const blob = new Blob(chunks as BlobPart[]);
    return blob;
  }
  
  /**
   *
   */
  private async installPluginBundle(bundle: Blob, manifest: unknown): Promise<void> {
    // In a real implementation, this would:
    // 1. Extract the bundle
    // 2. Validate the contents
    // 3. Load the plugin code
    // 4. Register with PluginManager
    
    // For now, we'll simulate this
    const pluginManager = PluginManager.getInstance();
    
    // Create a mock plugin from the bundle
    const plugin = {
      manifest,
      onLoad: async () => {
        console.log(`Plugin ${manifest.id} loaded from marketplace`);
      },
    };
    
    await pluginManager.register(plugin, { autoActivate: false });
  }
  
  /**
   *
   */
  private async removePluginData(pluginId: string): Promise<void> {
    // Remove plugin storage
    const keys = Object.keys(localStorage).filter(key =>
      key.startsWith(`plugin:${pluginId}:`)
    );
    
    keys.forEach(key => localStorage.removeItem(key));
  }
  
  /**
   *
   */
  private findDependentPlugins(pluginId: string): string[] {
    const pluginManager = PluginManager.getInstance();
    const allPlugins = pluginManager.getPlugins();
    
    return allPlugins
      .filter(p =>
        p.dependencies?.includes(pluginId) ||
        p.optionalDependencies?.includes(pluginId)
      )
      .map(p => p.id);
  }
  
  /**
   *
   */
  private isNewerVersion(version1: string, version2: string): boolean {
    const v1Parts = version1.split('.').map(Number);
    const v2Parts = version2.split('.').map(Number);
    
    for (let i = 0; i < 3; i++) {
      if (v1Parts[i] > v2Parts[i]) return true;
      if (v1Parts[i] < v2Parts[i]) return false;
    }
    
    return false;
  }
  
  /**
   *
   */
  private isBreakingChange(currentVersion: string, newVersion: string): boolean {
    const current = currentVersion.split('.').map(Number);
    const newVer = newVersion.split('.').map(Number);
    
    return newVer[0] > current[0]; // Major version change
  }
  
  /**
   *
   */
  private determineUpdatePriority(
    currentVersion: string,
    latestVersion: string
  ): 'critical' | 'high' | 'medium' | 'low' {
    if (this.isBreakingChange(currentVersion, latestVersion)) {
      return 'high';
    }
    
    const current = currentVersion.split('.').map(Number);
    const latest = latestVersion.split('.').map(Number);
    
    // Minor version update
    if (latest[1] > current[1]) {
      return 'medium';
    }
    
    // Patch update
    return 'low';
  }
  
  /**
   *
   */
  private updateProgress(pluginId: string, progress: InstallationProgress): void {
    this.installations.set(pluginId, progress);
    this.emit('install-progress', pluginId, progress);
    
    // Emit event for UI updates
    if (typeof window !== 'undefined') {
      window.dispatchEvent(
        new CustomEvent('plugin-installation-progress', {
          detail: progress,
        })
      );
    }
  }
  
  /**
   *
   */
  private startUpdateChecker(): void {
    this.updateCheckTimer = setInterval(async () => {
      try {
        const updates = await this.checkUpdates();
        
        if (updates.length > 0 && typeof window !== 'undefined') {
          window.dispatchEvent(
            new CustomEvent('plugins-updates-available', {
              detail: updates,
            })
          );
        }
      } catch (error) {
        console.error('Failed to check for updates:', error);
      }
    }, this.config.updateCheckInterval);
  }
  
  /**
   * Stop automatic update checking
   */
  stopUpdateChecker(): void {
    if (this.updateCheckTimer) {
      clearInterval(this.updateCheckTimer);
      this.updateCheckTimer = undefined;
    }
  }
  
  /**
   * Update marketplace configuration
   */
  updateConfig(config: Partial<MarketplaceConfig>): void {
    this.config = { ...this.config, ...config };
    
    if (config.autoUpdate !== undefined) {
      if (config.autoUpdate) {
        this.startUpdateChecker();
      } else {
        this.stopUpdateChecker();
      }
    }
  }

  /**
   * Register event handler
   */
  on<K extends keyof MarketplaceEventMap>(
    event: K,
    handler: (...args: MarketplaceEventMap[K]) => void
  ): void {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, new Set());
    }
    this.eventHandlers.get(event)!.add(handler as EventHandler);
  }

  /**
   * Unregister event handler
   */
  off<K extends keyof MarketplaceEventMap>(
    event: K,
    handler: (...args: MarketplaceEventMap[K]) => void
  ): void {
    const handlers = this.eventHandlers.get(event);
    if (handlers) {
      handlers.delete(handler as EventHandler);
    }
  }

  /**
   * Emit event
   */
  private emit<K extends keyof MarketplaceEventMap>(
    event: K,
    ...args: MarketplaceEventMap[K]
  ): void {
    const handlers = this.eventHandlers.get(event);
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(...args);
        } catch (error) {
          console.error(`Error in ${event} handler:`, error);
        }
      });
    }
  }

  /**
   * Cleanup and destroy the manager
   */
  destroy(): void {
    this.stopUpdateChecker();
    this.eventHandlers.clear();
    this.installations.clear();
  }
}

/**
 * Get the singleton marketplace manager instance
 */
export function getMarketplaceManager(config?: Partial<MarketplaceConfig>): MarketplaceManager {
  return MarketplaceManager.getInstance(config);
}
