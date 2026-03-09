/**
 * Plugin Abstractions
 * Central export point for all plugin interfaces and implementations
 */

// Re-export from @ghatana/dcmaar-types (only interfaces, not PluginManager which is implemented here)
export type { IPlugin, Plugin } from '@ghatana/dcmaar-types';

// Export interfaces
export type { IDataCollector } from './interfaces/DataCollector';
export type { IAnalytics } from './interfaces/Analytics';
export type { INotification } from './interfaces/Notification';
export type { IStorage } from './interfaces/Storage';

// Export implementations - Notifications
export { EmailNotificationPlugin } from './implementations/EmailNotificationPlugin';
export { SlackNotificationPlugin } from './implementations/notifications/SlackNotificationPlugin';
export { WebhookNotificationPlugin } from './implementations/notifications/WebhookNotificationPlugin';

// Export implementations - Storage
export { InMemoryStoragePlugin } from './implementations/storage/InMemoryStoragePlugin';
export { LocalStoragePlugin } from './implementations/storage/LocalStoragePlugin';
export { RemoteStoragePlugin } from './implementations/storage/RemoteStoragePlugin';

// Export core framework components
export {
  PluginLifecycleState,
  PluginLifecycleManager,
  PluginRegistry,
  PluginLoader,
  PluginManager as CorePluginManager,
} from './core';
export type {
  PluginLifecycleEvent,
  IPluginLifecycleManager,
  IPluginRegistry,
  PluginLoaderConfig,
  IPluginLoader,
  PluginLoadResult,
  IPluginManager,
  PluginManagerStats,
} from './core';
