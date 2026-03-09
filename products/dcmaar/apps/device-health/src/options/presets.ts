/**
 * @fileoverview Configuration Presets - common configurations for quick setup
 *
 * Provides pre-built configuration templates for:
 * - Development environments
 * - Production deployments
 * - Testing scenarios
 * - Common use cases
 */

import type { ExtensionBootstrapConfig, DynamicRuntimeConfig } from '../connectors/schemas/config';
import { DEFAULT_FEATURE_FLAGS } from '../core/config/ExtensionConfig';

/**
 * Bootstrap preset for local development
 */
export const BOOTSTRAP_PRESET_LOCAL_DEV: ExtensionBootstrapConfig = {
  version: '1.0',
  configSources: [
    {
      id: 'local-file',
      enabled: true,
      type: 'filesystem',
      config: {
        watchPath: './config/runtime-config.json',
        pollInterval: 5000,
      },
      refreshInterval: 5,
      responseFormat: 'json',
    },
  ],
  caching: {
    enabled: true,
    ttlSeconds: 300, // 5 minutes for dev
    persistToStorage: false,
  },
  resilience: {
    maxRetries: 3,
    initialBackoffMs: 500,
    maxBackoffMs: 5000,
  },
};

/**
 * Bootstrap preset for production with remote config server
 */
export const BOOTSTRAP_PRESET_PRODUCTION: ExtensionBootstrapConfig = {
  version: '1.0',
  configSources: [
    {
      id: 'primary-config-server',
      enabled: true,
      type: 'http',
      config: {
        url: 'https://config.example.com/api/extension/config',
        method: 'GET',
      },
      refreshInterval: 300, // 5 minutes
      responseFormat: 'json',
    },
    {
      id: 'fallback-config-server',
      enabled: true,
      type: 'http',
      config: {
        url: 'https://config-backup.example.com/api/extension/config',
        method: 'GET',
      },
      refreshInterval: 300,
      responseFormat: 'json',
    },
  ],
  caching: {
    enabled: true,
    ttlSeconds: 3600, // 1 hour
    persistToStorage: true,
  },
  resilience: {
    maxRetries: 5,
    initialBackoffMs: 1000,
    maxBackoffMs: 30000,
  },
};

/**
 * Bootstrap preset for WebSocket-based real-time config
 */
export const BOOTSTRAP_PRESET_WEBSOCKET: ExtensionBootstrapConfig = {
  version: '1.0',
  configSources: [
    {
      id: 'websocket-config',
      enabled: true,
      type: 'websocket',
      config: {
        url: 'wss://config.example.com/ws',
      },
      refreshInterval: 0, // Real-time updates
      responseFormat: 'json',
    },
  ],
  caching: {
    enabled: true,
    ttlSeconds: 600,
    persistToStorage: true,
  },
  resilience: {
    maxRetries: 10,
    initialBackoffMs: 1000,
    maxBackoffMs: 60000,
  },
};

/**
 * Runtime config preset for minimal telemetry
 */
export const RUNTIME_PRESET_MINIMAL: DynamicRuntimeConfig = {
  version: '1.0',
  validFrom: new Date().toISOString(),
  sinks: [
    {
      id: 'console-sink',
      enabled: true,
      type: 'http',
      config: {
        url: 'https://telemetry.example.com/events',
        method: 'POST',
      },
      accepts: ['event', 'error'],
      batch: {
        enabled: true,
        maxSize: 50,
        maxWaitMs: 10000,
      },
    },
  ],
  addOnProcesses: [],
  dataCollection: {
    extension: {
      enabled: true,
      metrics: ['loadTime', 'errorRate'],
      samplingIntervalMs: 60000, // 1 minute
    },
    pii: {
      enabled: true,
      fieldsToRedact: ['email', 'creditCard', 'ssn', 'phoneNumber'],
      redactionCharacter: '*',
    },
  },
  features: {
    ...DEFAULT_FEATURE_FLAGS,
    syntheticRuns: false,
    performanceBudgets: false,
    connectorExports: false,
    alerting: false,
    reporting: false,
    pageUsageDashboard: false,
    productivityAnalytics: false,
    environmentTelemetry: false,
  },
};

/**
 * Runtime config preset for comprehensive monitoring
 */
export const RUNTIME_PRESET_FULL_MONITORING: DynamicRuntimeConfig = {
  version: '1.0',
  validFrom: new Date().toISOString(),
  sinks: [
    {
      id: 'telemetry-sink',
      enabled: true,
      type: 'http',
      config: {
        url: 'https://telemetry.example.com/metrics',
        method: 'POST',
      },
      accepts: ['telemetry', 'event'],
      batch: {
        enabled: true,
        maxSize: 100,
        maxWaitMs: 5000,
        compressionEnabled: true,
      },
    },
    {
      id: 'error-sink',
      enabled: true,
      type: 'http',
      config: {
        url: 'https://errors.example.com/log',
        method: 'POST',
      },
      accepts: ['error'],
    },
  ],
  addOnProcesses: [],
  dataCollection: {
    system: {
      enabled: true,
      metrics: ['cpu', 'memory', 'network'],
      samplingIntervalMs: 5000,
    },
    extension: {
      enabled: true,
      metrics: ['loadTime', 'memoryUsage', 'messageCount', 'errorRate'],
      samplingIntervalMs: 10000,
    },
    browser: {
      enabled: true,
      metrics: ['pageLoadTime', 'activeTabCount', 'extensionErrors'],
      samplingIntervalMs: 10000,
    },
    userActivity: {
      enabled: true,
      captureClicks: false,
      capturePageNavigation: true,
      captureConsoleErrors: true,
      sampleRate: 0.1, // 10% sampling
    },
    pii: {
      enabled: true,
      fieldsToRedact: ['email', 'creditCard', 'ssn', 'phoneNumber'],
      redactionCharacter: '*',
    },
  },
  analysis: {
    aggregations: [
      {
        id: 'avg-load-time',
        type: 'avg',
        sourceMetric: 'extension_load_time_ms',
        windowSizeMs: 60000,
        outputTo: 'telemetry-sink',
      },
      {
        id: 'error-count',
        type: 'count',
        sourceMetric: 'extension_error_rate',
        windowSizeMs: 60000,
        outputTo: 'telemetry-sink',
      },
    ],
    alerts: [
      {
        id: 'high-error-rate',
        name: 'High Error Rate',
        condition: 'value > 0.05',
        severity: 'warning',
        outputTo: 'error-sink',
        cooldownSeconds: 300,
      },
      {
        id: 'memory-leak',
        name: 'Memory Leak Detected',
        condition: 'value > 100000000',
        severity: 'critical',
        outputTo: 'error-sink',
        cooldownSeconds: 600,
      },
    ],
    anomalyDetection: {
      enabled: true,
      modelType: 'zscore',
      sensitivity: 'medium',
      outputTo: 'telemetry-sink',
    },
  },
  features: {
    ...DEFAULT_FEATURE_FLAGS,
  },
};

/**
 * All available presets grouped by category
 */
export const PRESETS = {
  bootstrap: {
    'local-dev': BOOTSTRAP_PRESET_LOCAL_DEV,
    production: BOOTSTRAP_PRESET_PRODUCTION,
    websocket: BOOTSTRAP_PRESET_WEBSOCKET,
  },
  runtime: {
    minimal: RUNTIME_PRESET_MINIMAL,
    'full-monitoring': RUNTIME_PRESET_FULL_MONITORING,
  },
};

/**
 * Preset metadata for UI display
 */
export const PRESET_METADATA = {
  bootstrap: {
    'local-dev': {
      name: 'Local Development',
      description: 'File-based config for local development with fast refresh',
      icon: '🔧',
      recommended: true,
    },
    production: {
      name: 'Production (HTTP)',
      description: 'Remote config server with fallback and persistence',
      icon: '🚀',
      recommended: false,
    },
    websocket: {
      name: 'WebSocket (Real-time)',
      description: 'Real-time config updates via WebSocket connection',
      icon: '⚡',
      recommended: false,
    },
  },
  runtime: {
    minimal: {
      name: 'Minimal Telemetry',
      description: 'Basic error tracking and load time metrics',
      icon: '📊',
      recommended: true,
    },
    'full-monitoring': {
      name: 'Full Monitoring',
      description: 'Comprehensive metrics, alerts, and anomaly detection',
      icon: '🔍',
      recommended: false,
    },
  },
};

/**
 * Get a bootstrap preset by name
 */
export function getBootstrapPreset(name: string): ExtensionBootstrapConfig | null {
  return PRESETS.bootstrap[name as keyof typeof PRESETS.bootstrap] || null;
}

/**
 * Get a runtime preset by name
 */
export function getRuntimePreset(name: string): DynamicRuntimeConfig | null {
  return PRESETS.runtime[name as keyof typeof PRESETS.runtime] || null;
}

/**
 * Get all available bootstrap preset names
 */
export function getBootstrapPresetNames(): string[] {
  return Object.keys(PRESETS.bootstrap);
}

/**
 * Get all available runtime preset names
 */
export function getRuntimePresetNames(): string[] {
  return Object.keys(PRESETS.runtime);
}
