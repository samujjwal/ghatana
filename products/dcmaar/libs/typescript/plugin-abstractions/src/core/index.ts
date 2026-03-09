/**
 * Core plugin framework exports
 * 
 * This module provides the complete plugin infrastructure:
 * - Lifecycle management (PluginLifecycleManager)
 * - Plugin registry (PluginRegistry)
 * - Plugin loader (PluginLoader)
 * - Unified management (PluginManager)
 */

// Lifecycle
export {
  PluginLifecycleState,
} from './PluginLifecycle';
export type {
  PluginLifecycleEvent,
  IPluginLifecycleManager,
} from './PluginLifecycle';
export {
  PluginLifecycleManager,
} from './PluginLifecycle';

// Registry
export type {
  IPluginRegistry,
} from './PluginRegistry';
export {
  PluginRegistry,
} from './PluginRegistry';

// Loader
export type {
  PluginLoaderConfig,
  IPluginLoader,
  PluginLoadResult,
} from './PluginLoader';
export {
  PluginLoader,
} from './PluginLoader';

// Manager
export type {
  IPluginManager,
  PluginManagerStats,
} from './PluginManager';
export {
  PluginManager,
} from './PluginManager';
