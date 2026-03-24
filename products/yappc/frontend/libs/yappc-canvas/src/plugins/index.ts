/**
 * Canvas Plugin System
 * 
 * Complete plugin architecture and extensibility system for Canvas.
 * 
 * @module @ghatana/yappc-canvas/plugins
 */

// Core plugin types and interfaces
export type {
  Plugin,
  PluginManifest,
  PluginContext,
  PluginState,
  PluginCapability,
  PluginLifecycle,
  PluginCanvasAPI,
  PluginStorage,
  PluginEventAPI,
  PluginUIAPI,
  PluginLogger,
  PluginRegistrationOptions,
  PluginElementType,
  PluginCommand,
  PluginTool,
  PluginExporter,
  PluginImporter,
  PluginPanel,
  PluginDialog,
  PluginContextMenuItem,
  CanvasEvent,
  EventHandler,
  RenderContext,
} from './types';

// Plugin error class
export { PluginError } from './types';

// Plugin manager
export { PluginManager, getPluginManager } from './pluginManager';

// Marketplace types
export type {
  MarketplacePlugin,
  InstallationStatus,
  InstallationProgress,
  PluginUpdate,
  SearchFilters,
  SearchResults,
  SecurityVerification,
  PermissionRequest,
  MarketplaceConfig,
  InstallOptions,
  UninstallOptions,
} from './marketplaceTypes';

// Marketplace manager
export { MarketplaceManager, getMarketplaceManager } from './marketplaceManager';

// Marketplace UI Components
export {
  PluginBrowser,
  type PluginBrowserProps,
  PluginCard,
  type PluginCardProps,
  InstallationProgress as InstallationProgressComponent,
  type InstallationProgressProps,
} from './ui';
