/**
 * @fileoverview Extension Connectors Integration - Main entry point.
 * Provides unified connector management for the browser extension with dynamic
 * configuration, secure communication, and resilience patterns.
 *
 * **Architecture**:
 * - Two-tier configuration: Bootstrap (user-provided) → Dynamic (source-fetched)
 * - Dynamic connector instantiation from user config (zero hard-coded connectors)
 * - Hot-reload support without extension restart
 * - Graceful degradation with fallback caching
 * - Secure defaults: TLS, input validation, PII redaction
 *
 * **Usage**:
 * ```typescript
 * import { ExtensionConnectorManager } from '@ghatana/dcmaar-extension-connectors';
 *
 * const manager = new ExtensionConnectorManager({
 *   bootstrapConfig: {
 *     version: '1.0',
 *     configSources: [{
 *       id: 'primary',
 *       type: 'http',
 *       config: { url: 'https://config.example.com/extension' }
 *     }]
 *   }
 * });
 *
 * await manager.initialize();
 * manager.on('extension-ready', () => console.log('Ready!'));
 * ```
 *
 * @see CONNECTORS_INTEGRATION_PLAN.md for detailed architecture
 */

// Configuration schemas and types
export * from './schemas/config';
export * from './schemas/transport';

// Core manager
export * from './ExtensionConnectorManager';

// Utilities
export * from './utils/security';
