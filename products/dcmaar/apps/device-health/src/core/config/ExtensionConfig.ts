/**
 * @fileoverview Extension Configuration
 *
 * Simplified configuration focusing on browser-specific concerns.
 * Connection/transport configuration is handled by @ghatana/dcmaar-connectors.
 *
 * @module core/config/ExtensionConfig
 */

/**
 * Metrics collection configuration
 */
export interface MetricsConfig {
  /** Collect page load metrics */
  pageLoad: boolean;
  /** Collect user interaction metrics */
  userInteraction: boolean;
  /** Collect network request metrics */
  networkRequests: boolean;
  /** Collect resource timing metrics */
  resourceTiming: boolean;
  /** Collection interval in milliseconds */
  collectionInterval?: number;
  /** Batch size for metric collection */
  batchSize?: number;
}

/**
 * Event capture configuration
 */
export interface EventCaptureConfig {
  /** Capture tab events */
  tabs: boolean;
  /** Capture navigation events */
  navigation: boolean;
  /** Capture network events */
  network: boolean;
  /** Capture web request events */
  webRequests: boolean;
  /** Capture history events */
  history: boolean;
  /** URL patterns to include */
  includePatterns?: string[];
  /** URL patterns to exclude */
  excludePatterns?: string[];
}

/**
 * Connector configuration
 *
 * Defines connections to external systems using @ghatana/dcmaar-connectors.
 */
export interface ConnectorConfig {
  /** Desktop/agent connector via IPC */
  desktop?: {
    enabled: boolean;
    channel: string; // IPC channel name
    autoConnect?: boolean;
  };
  /** Remote HTTP endpoint */
  http?: {
    enabled: boolean;
    endpoint: string;
    apiKey?: string;
    batchSize?: number;
    flushInterval?: number;
  };
  /** Remote WebSocket endpoint */
  websocket?: {
    enabled: boolean;
    endpoint: string;
    apiKey?: string;
    reconnect?: boolean;
    heartbeatInterval?: number;
  };
}

/**
 * Browser UI configuration
 */
export interface BrowserUIConfig {
  /** Update browser badge with metrics count */
  badgeUpdates: boolean;
  /** Show browser notifications */
  notifications: boolean;
  /** Add context menu items */
  contextMenus: boolean;
  /** Enable keyboard shortcuts */
  shortcuts: boolean;
}

/**
 * Storage configuration
 */
export interface StorageConfig {
  /** Storage area to use (local or sync) */
  area: 'local' | 'sync';
  /** Key prefix for namespacing */
  prefix?: string;
  /** Enable quota monitoring */
  quotaMonitoring?: boolean;
}

/**
 * Privacy configuration
 */
export interface PrivacyConfig {
  /** Enable PII scrubbing */
  scrubPII: boolean;
  /** Keys to redact from collected data */
  redactKeys?: string[];
  /** Mask sensitive values */
  maskSensitiveData: boolean;
  /** Enable user consent tracking */
  requireConsent?: boolean;
}

/**
 * Main extension configuration
 */
export interface FeatureFlags {
  webVitals: boolean;
  resourceTiming: boolean;
  syntheticRuns: boolean;
  performanceBudgets: boolean;
  networkDiagnostics: boolean;
  activityTracking: boolean;
  engagement: boolean;
  productivityAnalytics: boolean;
  reporting: boolean;
  pageUsageDashboard: boolean;
  connectorExports: boolean;
  alerting: boolean;
  environmentTelemetry: boolean;
}

export interface ExtensionConfig {
  /** Configuration version */
  version: string;

  /** Metrics collection settings */
  metrics: MetricsConfig;

  /** Event capture settings */
  events: EventCaptureConfig;

  /** Connector settings */
  connectors: ConnectorConfig;

  /** Browser UI settings */
  ui: BrowserUIConfig;

  /** Storage settings */
  storage: StorageConfig;

  /** Privacy settings */
  privacy: PrivacyConfig;

  /** Enable debug logging */
  debug?: boolean;

  /** Feature flags */
  features: FeatureFlags;
}

export const DEFAULT_FEATURE_FLAGS: FeatureFlags = {
  webVitals: true,
  resourceTiming: true,
  syntheticRuns: true,
  performanceBudgets: true,
  networkDiagnostics: true,
  activityTracking: true,
  engagement: true,
  productivityAnalytics: true,
  reporting: true,
  pageUsageDashboard: true,
  connectorExports: true,
  alerting: true,
  environmentTelemetry: true,
};

/**
 * Default extension configuration
 */
export const DEFAULT_EXTENSION_CONFIG: ExtensionConfig = {
  version: '1.0.0',

  metrics: {
    pageLoad: true,
    userInteraction: true,
    networkRequests: false,
    resourceTiming: false,
    collectionInterval: 30000, // 30 seconds
    batchSize: 100,
  },

  events: {
    tabs: true,
    navigation: true,
    network: false,
    webRequests: false,
    history: false,
    includePatterns: ['<all_urls>'],
    excludePatterns: [],
  },

  connectors: {
    desktop: {
      enabled: true,
      channel: 'dcmaar-bridge',
      autoConnect: true,
    },
  },

  ui: {
    badgeUpdates: true,
    notifications: false,
    contextMenus: false,
    shortcuts: false,
  },

  storage: {
    area: 'local',
    prefix: 'dcmaar:',
    quotaMonitoring: true,
  },

  privacy: {
    scrubPII: true,
    redactKeys: ['password', 'token', 'secret', 'apiKey', 'email'],
    maskSensitiveData: true,
    requireConsent: false,
  },

  debug: false,

  features: { ...DEFAULT_FEATURE_FLAGS },
};

/**
 * Configuration storage key
 */
export const CONFIG_STORAGE_KEY = 'dcmaar:extension:config';

/**
 * Load configuration from storage
 *
 * @param storage - Storage adapter
 * @returns Promise resolving to configuration (or default if not found)
 */
export async function loadConfig(storage: {
  get<T>(key: string): Promise<T | undefined>;
}): Promise<ExtensionConfig> {
  const stored = await storage.get<ExtensionConfig>(CONFIG_STORAGE_KEY);

  if (!stored) {
    return DEFAULT_EXTENSION_CONFIG;
  }

  return {
    ...DEFAULT_EXTENSION_CONFIG,
    ...stored,
    metrics: { ...DEFAULT_EXTENSION_CONFIG.metrics, ...stored.metrics },
    events: { ...DEFAULT_EXTENSION_CONFIG.events, ...stored.events },
    connectors: { ...DEFAULT_EXTENSION_CONFIG.connectors, ...stored.connectors },
    ui: { ...DEFAULT_EXTENSION_CONFIG.ui, ...stored.ui },
    storage: { ...DEFAULT_EXTENSION_CONFIG.storage, ...stored.storage },
    privacy: { ...DEFAULT_EXTENSION_CONFIG.privacy, ...stored.privacy },
    features: { ...DEFAULT_FEATURE_FLAGS, ...(stored.features ?? {}) },
  };
}

/**
 * Save configuration to storage
 *
 * @param config - Configuration to save
 * @param storage - Storage adapter
 */
export async function saveConfig(
  config: ExtensionConfig,
  storage: { set<T>(key: string, value: T): Promise<void> }
): Promise<void> {
  await storage.set(CONFIG_STORAGE_KEY, config);
}

/**
 * Validate configuration
 *
 * @param config - Configuration to validate
 * @returns Validation result
 */
export function validateConfig(config: unknown): {
  valid: boolean;
  error?: string;
  config?: ExtensionConfig;
} {
  if (!config || typeof config !== 'object') {
    return { valid: false, error: 'Config must be an object' };
  }

  const cfg = config as Partial<ExtensionConfig>;

  if (!cfg.version) {
    return { valid: false, error: 'Config must have version' };
  }

  if (!cfg.metrics || typeof cfg.metrics !== 'object') {
    return { valid: false, error: 'Config must have metrics object' };
  }

  if (!cfg.events || typeof cfg.events !== 'object') {
    return { valid: false, error: 'Config must have events object' };
  }

  return { valid: true, config: cfg as ExtensionConfig };
}
