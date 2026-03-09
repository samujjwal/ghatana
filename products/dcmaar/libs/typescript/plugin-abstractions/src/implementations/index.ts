/**
 * Plugin Implementations
 * Exports all concrete plugin implementations
 */

export { EmailNotificationPlugin } from './EmailNotificationPlugin';

// Re-export from organized subdirectories
export { SlackNotificationPlugin } from './notifications/SlackNotificationPlugin';
export { WebhookNotificationPlugin } from './notifications/WebhookNotificationPlugin';
export { InMemoryStoragePlugin } from './storage/InMemoryStoragePlugin';
export { LocalStoragePlugin } from './storage/LocalStoragePlugin';
export { RemoteStoragePlugin } from './storage/RemoteStoragePlugin';
