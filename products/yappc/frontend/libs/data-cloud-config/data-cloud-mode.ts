/**
 * Data Cloud Mode Configuration
 *
 * YAPPC supports two modes of using Data Cloud:
 * 1. LIBRARY MODE (Default for Dev): Data Cloud features run as in-process libraries
 *    - No external service needed
 *    - Fast development iteration
 *    - Perfect for local testing and rapid prototyping
 *
 * 2. SERVICE MODE (For Staging/Production): Data Cloud runs as an external service
 *    - Separate microservice with its own lifecycle
 *    - Distributed architecture
 *    - Suited for production environments
 *
 * @see {@link https://ghatana.dev/data-cloud}
 */

/**
 * Data Cloud Modes
 */
export enum DataCloudMode {
  /** Data Cloud as in-process library (dev default) */
  LIBRARY = 'library',
  /** Data Cloud as external microservice (production) */
  SERVICE = 'service',
}

/**
 * Data Cloud Configuration
 */
export interface DataCloudConfig {
  /** Current operating mode */
  mode: DataCloudMode;

  /** Environment name */
  environment: 'development' | 'staging' | 'production';

  // === LIBRARY MODE SETTINGS ===
  library?: {
    /** Enable debug logging in library mode */
    debug: boolean;
    /** In-memory cache size (entries) */
    cacheSize: number;
    /** Feature store sync interval (ms) */
    syncInterval: number;
    /** Max concurrent operations */
    maxConcurrentOps: number;
    /** Enable local feature computation */
    localFeatureCompute: boolean;
  };

  // === SERVICE MODE SETTINGS ===
  service?: {
    /** Data Cloud service hostname/IP */
    host: string;
    /** Data Cloud service port */
    port: number;
    /** Service connection timeout (ms) */
    timeout: number;
    /** Max retries on service failure */
    maxRetries: number;
    /** Retry backoff (ms) */
    retryBackoff: number;
    /** Enable health checks */
    healthCheckEnabled: boolean;
    /** Health check interval (ms) */
    healthCheckInterval: number;
  };

  /** Global settings */
  global?: {
    /** Enable metrics collection */
    metricsEnabled: boolean;
    /** Log level */
    logLevel: 'debug' | 'info' | 'warn' | 'error';
    /** Enable distributed tracing */
    tracingEnabled: boolean;
    /** Default feature TTL (ms) */
    featureTTL: number;
  };
}

/**
 * Default Data Cloud configurations by environment
 */
export const DEFAULT_DATA_CLOUD_CONFIGS: Record<string, DataCloudConfig> = {
  development: {
    mode: DataCloudMode.LIBRARY, // Library mode for dev
    environment: 'development',
    library: {
      debug: true,
      cacheSize: 1000,
      syncInterval: 30000,
      maxConcurrentOps: 10,
      localFeatureCompute: true,
    },
    global: {
      metricsEnabled: false,
      logLevel: 'debug',
      tracingEnabled: false,
      featureTTL: 300000, // 5 minutes
    },
  },

  staging: {
    mode: DataCloudMode.SERVICE, // Service mode for staging
    environment: 'staging',
    service: {
      host: 'data-cloud-service',
      port: 7004,
      timeout: 30000,
      maxRetries: 3,
      retryBackoff: 1000,
      healthCheckEnabled: true,
      healthCheckInterval: 30000,
    },
    global: {
      metricsEnabled: true,
      logLevel: 'info',
      tracingEnabled: true,
      featureTTL: 600000, // 10 minutes
    },
  },

  production: {
    mode: DataCloudMode.SERVICE, // Service mode for production
    environment: 'production',
    service: {
      host: process.env.DATA_CLOUD_SERVICE_HOST || 'data-cloud-service',
      port: parseInt(process.env.DATA_CLOUD_SERVICE_PORT || '7004', 10),
      timeout: 60000,
      maxRetries: 5,
      retryBackoff: 2000,
      healthCheckEnabled: true,
      healthCheckInterval: 60000,
    },
    global: {
      metricsEnabled: true,
      logLevel: 'info',
      tracingEnabled: true,
      featureTTL: 1800000, // 30 minutes
    },
  },
};

/**
 * Get Data Cloud configuration for current environment
 *
 * Priority (highest to lowest):
 * 1. DATA_CLOUD_MODE environment variable (explicit override)
 * 2. NODE_ENV environment variable (picks default)
 * 3. Hard-coded development (fallback)
 *
 * @example
 * ```ts
 * const config = getDataCloudConfig();
 * if (config.mode === DataCloudMode.LIBRARY) {
 *   initializeLibraryMode(config.library);
 * } else {
 *   initializeServiceMode(config.service);
 * }
 * ```
 */
export function getDataCloudConfig(): DataCloudConfig {
  const env = process.env.NODE_ENV || 'development';
  const explicitMode = process.env.DATA_CLOUD_MODE as DataCloudMode | undefined;

  const defaultConfig = DEFAULT_DATA_CLOUD_CONFIGS[env] || DEFAULT_DATA_CLOUD_CONFIGS.development;
  let config = { ...defaultConfig };

  // Override mode if explicitly set
  if (explicitMode) {
    config.mode = explicitMode;
    console.info(`🔧 Data Cloud mode explicitly set to: ${explicitMode}`);
  }

  // Override service host/port if provided
  if (config.mode === DataCloudMode.SERVICE && config.service) {
    if (process.env.DATA_CLOUD_SERVICE_HOST) {
      config.service.host = process.env.DATA_CLOUD_SERVICE_HOST;
    }
    if (process.env.DATA_CLOUD_SERVICE_PORT) {
      config.service.port = parseInt(process.env.DATA_CLOUD_SERVICE_PORT, 10);
    }
  }

  return config;
}

/**
 * Validate Data Cloud configuration
 *
 * @throws Error if configuration is invalid
 */
const VALID_MODES: readonly string[] = ['library', 'service'];

export function validateDataCloudConfig(config: DataCloudConfig): void {
  if (!VALID_MODES.includes(config.mode)) {
    throw new Error(`Invalid Data Cloud mode: ${config.mode}`);
  }

  if (config.mode === DataCloudMode.SERVICE && config.service) {
    if (!config.service.host) {
      throw new Error('Data Cloud service host is required in SERVICE mode');
    }
    if (config.service.port < 1 || config.service.port > 65535) {
      throw new Error(`Invalid Data Cloud service port: ${config.service.port}`);
    }
  }

  if (config.mode === DataCloudMode.LIBRARY && config.library) {
    if (config.library.cacheSize < 1) {
      throw new Error('Cache size must be at least 1');
    }
    if (config.library.maxConcurrentOps < 1) {
      throw new Error('Max concurrent ops must be at least 1');
    }
  }
}

/**
 * Format Data Cloud config for display/logging
 */
export function formatDataCloudConfig(config: DataCloudConfig): string {
  const lines = [
    `☁️  Data Cloud Configuration`,
    `  Mode: ${config.mode}`,
    `  Environment: ${config.environment}`,
  ];

  if (config.mode === DataCloudMode.LIBRARY && config.library) {
    lines.push(
      `  Library Settings:`,
      `    - Cache Size: ${config.library.cacheSize}`,
      `    - Sync Interval: ${config.library.syncInterval}ms`,
      `    - Max Concurrent Ops: ${config.library.maxConcurrentOps}`,
      `    - Local Feature Compute: ${config.library.localFeatureCompute ? 'ON' : 'OFF'}`,
      `    - Debug Mode: ${config.library.debug ? 'ON' : 'OFF'}`
    );
  }

  if (config.mode === DataCloudMode.SERVICE && config.service) {
    lines.push(
      `  Service Settings:`,
      `    - Host: ${config.service.host}:${config.service.port}`,
      `    - Timeout: ${config.service.timeout}ms`,
      `    - Max Retries: ${config.service.maxRetries}`,
      `    - Health Checks: ${config.service.healthCheckEnabled ? 'ON' : 'OFF'}`
    );
  }

  if (config.global) {
    lines.push(
      `  Global Settings:`,
      `    - Metrics: ${config.global.metricsEnabled ? 'ON' : 'OFF'}`,
      `    - Log Level: ${config.global.logLevel}`,
      `    - Tracing: ${config.global.tracingEnabled ? 'ON' : 'OFF'}`,
      `    - Feature TTL: ${config.global.featureTTL}ms`
    );
  }

  return lines.join('\n');
}

/**
 * Determine if running in dev mode with library Data Cloud
 */
export function isLibraryMode(config?: DataCloudConfig): boolean {
  const cfg = config || getDataCloudConfig();
  return cfg.mode === DataCloudMode.LIBRARY;
}

/**
 * Determine if running in service mode with external Data Cloud
 */
export function isServiceMode(config?: DataCloudConfig): boolean {
  const cfg = config || getDataCloudConfig();
  return cfg.mode === DataCloudMode.SERVICE;
}
