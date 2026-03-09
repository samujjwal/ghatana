import type { DataFlowConfig } from '../app/background/integration/ExtensionDataFlow';
import type { BrowserEventSourceConfig } from '../connectors/sources/BrowserEventSource';
import type { IndexedDBSinkConfig } from '../connectors/sinks/IndexedDBSink';

/**
 * Extension bootstrap defaults.
 *
 * Provides sensible defaults for extension data flow without external dependencies.
 * These can be overridden by user configuration or environment-specific settings.
 */
export const DEFAULT_DATA_FLOW_CONFIG: DataFlowConfig = {
  source: {
    // Source identification
    id: 'browser-events-default',

    // Event types to capture
    events: ['tabs', 'navigation', 'performance'],

    // Sampling configuration
    sampling: {
      rate: 1.0, // Capture all events in development
      strategy: 'uniform',
    },

    // URL filtering
    filters: {
      includePatterns: ['https://*', 'http://*'],
      excludePatterns: ['chrome://*', 'chrome-extension://*'],
    },

    // Batching configuration
    batching: {
      size: 50,
      flushIntervalMs: 5000, // Flush every 5 seconds
    },

    // Performance monitoring
    performance: {
      captureWebVitals: true,
      captureResourceTiming: false,
      captureNavigationTiming: true,
    },

    // Throttling configuration
    throttling: {
      tabUpdates: 1000, // 1 second
      navigation: 500,   // 500ms
      interactions: 100, // 100ms for high-frequency interactions
    },
  } as BrowserEventSourceConfig,

  sink: {
    // Sink identification
    id: 'indexeddb-default',
    dbName: 'dcmaar-events',
    storeName: 'events',

    // Batching configuration
    batchSize: 50,
    flushIntervalMs: 5000,

    // Storage management
    maxEvents: 10000,
    retentionDays: 7,

    // Features
    compression: false,
    autoCleanup: true,
    cleanupIntervalMs: 3600000, // 1 hour
  } as IndexedDBSinkConfig,

  // Pipeline configuration
  pipeline: {
    processors: [], // No processors by default
  },

  // Monitoring enabled
  monitoring: true,
};
