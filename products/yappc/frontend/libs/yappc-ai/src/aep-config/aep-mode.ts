/**
 * AEP (Agentic Event Processor) Mode Configuration
 *
 * YAPPC supports two modes of using AEP:
 * 1. LIBRARY MODE (Default for Dev): AEP runs as an in-process library
 *    - No external service needed
 *    - Fast development iteration
 *    - Perfect for local testing and rapid prototyping
 *
 * 2. SERVICE MODE (For Staging/Production): AEP runs as an external service
 *    - Separate microservice with its own lifecycle
 *    - Distributed architecture
 *    - Suited for production environments
 *
 * @see {@link https://ghatana.dev/aep}
 */

/**
 * AEP Modes
 */
export enum AepMode {
  /** AEP as in-process library (dev default) */
  LIBRARY = 'library',
  /** AEP as external microservice (production) */
  SERVICE = 'service',
}

/**
 * AEP Configuration
 */
export interface AepConfig {
  /** Current operating mode */
  mode: AepMode;

  /** Environment name */
  environment: 'development' | 'staging' | 'production';

  // === LIBRARY MODE SETTINGS ===
  library?: {
    /** Enable debug logging in library mode */
    debug: boolean;
    /** In-memory cache size (entries) */
    cacheSize: number;
    /** Pattern detection interval (ms) */
    patternDetectionInterval: number;
    /** Max concurrent operations */
    maxConcurrentOps: number;
  };

  // === SERVICE MODE SETTINGS ===
  service?: {
    /** AEP service hostname/IP */
    host: string;
    /** AEP service port */
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
  };
}

/**
 * Default AEP configurations by environment
 */
export const DEFAULT_AEP_CONFIGS: Record<string, AepConfig> = {
  development: {
    mode: AepMode.LIBRARY, // ✅ Library mode for dev
    environment: 'development',
    library: {
      debug: true,
      cacheSize: 1000,
      patternDetectionInterval: 5000,
      maxConcurrentOps: 10,
    },
    global: {
      metricsEnabled: false,
      logLevel: 'debug',
      tracingEnabled: false,
    },
  },

  staging: {
    mode: AepMode.SERVICE, // Service mode for staging
    environment: 'staging',
    service: {
      host: 'aep-service',
      port: 7106,
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
    },
  },

  production: {
    mode: AepMode.SERVICE, // Service mode for production
    environment: 'production',
    service: {
      host: process.env.AEP_SERVICE_HOST || 'aep-service',
      port: parseInt(process.env.AEP_SERVICE_PORT || '7106', 10),
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
    },
  },
};

/**
 * Get AEP configuration for current environment
 *
 * Priority (highest to lowest):
 * 1. AEP_MODE environment variable (explicit override)
 * 2. NODE_ENV environment variable (picks default)
 * 3. Hard-coded development (fallback)
 *
 * @example
 * ```ts
 * const config = getAepConfig();
 * if (config.mode === AepMode.LIBRARY) {
 *   initializeLibraryMode(config.library);
 * } else {
 *   initializeServiceMode(config.service);
 * }
 * ```
 */
export function getAepConfig(): AepConfig {
  const env = process.env.NODE_ENV || 'development';
  const explicitMode = process.env.AEP_MODE as AepMode | undefined;

  let config =
    { ...DEFAULT_AEP_CONFIGS[env] } || DEFAULT_AEP_CONFIGS.development;

  // Override mode if explicitly set
  if (explicitMode) {
    config.mode = explicitMode;
    console.info(`🔧 AEP mode explicitly set to: ${explicitMode}`);
  }

  // Override service host/port if provided
  if (config.mode === AepMode.SERVICE && config.service) {
    if (process.env.AEP_SERVICE_HOST) {
      config.service.host = process.env.AEP_SERVICE_HOST;
    }
    if (process.env.AEP_SERVICE_PORT) {
      config.service.port = parseInt(process.env.AEP_SERVICE_PORT, 10);
    }
  }

  return config;
}

/**
 * Validate AEP configuration
 *
 * @throws Error if configuration is invalid
 */
export function validateAepConfig(config: AepConfig): void {
  if (!Object.values(AepMode).includes(config.mode)) {
    throw new Error(`Invalid AEP mode: ${config.mode}`);
  }

  if (config.mode === AepMode.SERVICE && config.service) {
    if (!config.service.host) {
      throw new Error('AEP service host is required in SERVICE mode');
    }
    if (config.service.port < 1 || config.service.port > 65535) {
      throw new Error(`Invalid AEP service port: ${config.service.port}`);
    }
  }

  if (config.mode === AepMode.LIBRARY && config.library) {
    if (config.library.cacheSize < 1) {
      throw new Error('Cache size must be at least 1');
    }
    if (config.library.maxConcurrentOps < 1) {
      throw new Error('Max concurrent ops must be at least 1');
    }
  }
}

/**
 * Format AEP config for display/logging
 */
export function formatAepConfig(config: AepConfig): string {
  const lines = [
    `🔧 AEP Configuration`,
    `  Mode: ${config.mode}`,
    `  Environment: ${config.environment}`,
  ];

  if (config.mode === AepMode.LIBRARY && config.library) {
    lines.push(
      `  Library Settings:`,
      `    - Cache Size: ${config.library.cacheSize}`,
      `    - Pattern Detection: ${config.library.patternDetectionInterval}ms`,
      `    - Max Concurrent Ops: ${config.library.maxConcurrentOps}`,
      `    - Debug Mode: ${config.library.debug ? 'ON' : 'OFF'}`
    );
  }

  if (config.mode === AepMode.SERVICE && config.service) {
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
      `    - Tracing: ${config.global.tracingEnabled ? 'ON' : 'OFF'}`
    );
  }

  return lines.join('\n');
}

/**
 * Determine if running in dev mode with library AEP
 */
export function isLibraryMode(config?: AepConfig): boolean {
  const cfg = config || getAepConfig();
  return cfg.mode === AepMode.LIBRARY;
}

/**
 * Determine if running in service mode with external AEP
 */
export function isServiceMode(config?: AepConfig): boolean {
  const cfg = config || getAepConfig();
  return cfg.mode === AepMode.SERVICE;
}
