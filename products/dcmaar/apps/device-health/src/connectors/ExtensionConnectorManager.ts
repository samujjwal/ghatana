import type { ConnectorConfig } from '../core/config/ExtensionConfig';

/**
 * Extension Connector Manager
 *
 * Lightweight facade over connector bindings until full integration lands.
 */
export class ExtensionConnectorManager {
  constructor(private readonly config: ConnectorConfig) {}

  async initialize(): Promise<void> {
    // Placeholder for future connector wiring.
    console.log('[ExtensionConnectorManager] Initializing with config:', this.config);
  }

  async dispose(): Promise<void> {
    console.log('[ExtensionConnectorManager] Disposing connectors');
  }

  hasEnabledConnectors(): boolean {
    return Boolean(
      this.config.desktop?.enabled || this.config.http?.enabled || this.config.websocket?.enabled
    );
  }
}
