/**
 * @fileoverview Extension platform integrations for @ghatana/dcmaar-connectors.
 *
 * Provides browser extension-specific implementations of storage, IPC, and logging
 * abstractions defined in the shared @ghatana/dcmaar-connectors package.
 *
 * **Exports**:
 * - ExtensionStorageProvider: chrome.storage.local wrapper
 * - ExtensionIpcChannel: browser.runtime messaging wrapper
 * - ExtensionLogger: Structured console logger
 *
 * **Usage**:
 * ```typescript
 * import { ConnectorManager } from '@ghatana/dcmaar-connectors';
 * import {
 *   ExtensionStorageProvider,
 *   ExtensionIpcChannel,
 *   ExtensionLogger,
 *   LogLevel
 * } from './platform';
 *
 * const storage = new ExtensionStorageProvider();
 * const ipcChannel = new ExtensionIpcChannel({ contextType: 'background' });
 * const logger = new ExtensionLogger({
 *   context: 'ConnectorManager',
 *   minLevel: LogLevel.INFO
 * });
 *
 * const manager = new ConnectorManager({
 *   storage,
 *   ipcChannel,
 *   // Note: ConnectorManager doesn't accept logger yet,
 *   // but individual connectors can use it
 * });
 * ```
 *
 * @module connectors/platform
 * @since Phase 2.1
 */

export { ExtensionStorageProvider } from './ExtensionStorageProvider';
export { ExtensionIpcChannel } from './ExtensionIpcChannel';
export { ExtensionLogger, LogLevel, type LogEntry } from './ExtensionLogger';
