/**
 * Secure Configuration Management
 * 
 * Provides centralized, secure configuration management with:
 * - Environment variable validation
 * - Secret management integration
 * - Type safety
 * - Default values for development
 * - Production security checks
 */

import { z } from 'zod';

// Configuration schema with validation
const ConfigSchema = z.object({
  // Server Configuration
  PORT: z.coerce.number().default(3000),
  NODE_ENV: z.enum(['development', 'staging', 'production']).default('development'),
  LOG_LEVEL: z.enum(['error', 'warn', 'info', 'debug']).default('info'),

  // Database Configuration
  DATABASE_URL: z.string().min(1, 'DATABASE_URL is required'),
  
  // Redis Configuration
  REDIS_URL: z.string().min(1, 'REDIS_URL is required'),

  // JWT Configuration
  JWT_SECRET: z.string()
    .min(32, 'JWT_SECRET must be at least 32 characters')
    .refine(
      (secret) => !['change-me-in-production', 'secret', 'test'].includes(secret.toLowerCase()),
      'JWT_SECRET must be a secure, unique value'
    ),

  // External Services
  AI_SERVICE_URL: z.string().url().default('localhost:50051'),
  SIM_RUNTIME_URL: z.string().url().default('localhost:50052'),

  // Object Storage Configuration
  S3_ENDPOINT: z.string().url().optional(),
  S3_ACCESS_KEY: z.string().min(1, 'S3_ACCESS_KEY is required').optional(),
  S3_SECRET_KEY: z.string().min(1, 'S3_SECRET_KEY is required').optional(),
  S3_BUCKET: z.string().min(1, 'S3_BUCKET is required').optional(),

  // Observability
  SENTRY_DSN: z.string().url().optional(),
  METRICS_PORT: z.coerce.number().default(9090),

  // Multi-tenancy
  DEFAULT_TENANT: z.string().default('default'),

  // Security Settings
  SESSION_SECRET: z.string()
    .min(32, 'SESSION_SECRET must be at least 32 characters')
    .optional(),
  CORS_ORIGIN: z.string().url().optional(),
  RATE_LIMIT_WINDOW: z.coerce.number().default(900000), // 15 minutes
  RATE_LIMIT_MAX: z.coerce.number().default(100),

  // Feature Flags
  CONTENT_WORKER_ENABLED: z.coerce.boolean().default(true),
  AI_PROXY_ENABLED: z.coerce.boolean().default(true),
  SIMULATION_ENABLED: z.coerce.boolean().default(true),
});

export type Config = z.infer<typeof ConfigSchema>;

/**
 * Load and validate configuration
 */
export function loadConfig(): Config {
  const rawConfig = {
    // Server
    PORT: process.env.PORT,
    NODE_ENV: process.env.NODE_ENV,
    LOG_LEVEL: process.env.LOG_LEVEL,

    // Database
    DATABASE_URL: process.env.DATABASE_URL,

    // Redis
    REDIS_URL: process.env.REDIS_URL,

    // JWT
    JWT_SECRET: process.env.JWT_SECRET,

    // External Services
    AI_SERVICE_URL: process.env.AI_SERVICE_URL,
    SIM_RUNTIME_URL: process.env.SIM_RUNTIME_URL,

    // Object Storage
    S3_ENDPOINT: process.env.S3_ENDPOINT,
    S3_ACCESS_KEY: process.env.S3_ACCESS_KEY,
    S3_SECRET_KEY: process.env.S3_SECRET_KEY,
    S3_BUCKET: process.env.S3_BUCKET,

    // Observability
    SENTRY_DSN: process.env.SENTRY_DSN,
    METRICS_PORT: process.env.METRICS_PORT,

    // Multi-tenancy
    DEFAULT_TENANT: process.env.DEFAULT_TENANT,

    // Security
    SESSION_SECRET: process.env.SESSION_SECRET,
    CORS_ORIGIN: process.env.CORS_ORIGIN,
    RATE_LIMIT_WINDOW: process.env.RATE_LIMIT_WINDOW,
    RATE_LIMIT_MAX: process.env.RATE_LIMIT_MAX,

    // Feature Flags
    CONTENT_WORKER_ENABLED: process.env.CONTENT_WORKER_ENABLED,
    AI_PROXY_ENABLED: process.env.AI_PROXY_ENABLED,
    SIMULATION_ENABLED: process.env.SIMULATION_ENABLED,
  };

  const config = ConfigSchema.parse(rawConfig);

  // Production security checks
  if (config.NODE_ENV === 'production') {
    validateProductionSecurity(config);
  }

  return config;
}

/**
 * Validate production security requirements
 */
function validateProductionSecurity(config: Config): void {
  const errors: string[] = [];

  // Check for secure secrets
  if (config.JWT_SECRET.length < 64) {
    errors.push('JWT_SECRET should be at least 64 characters in production');
  }

  if (config.SESSION_SECRET && config.SESSION_SECRET.length < 64) {
    errors.push('SESSION_SECRET should be at least 64 characters in production');
  }

  // Check for required production settings
  if (!config.SENTRY_DSN) {
    errors.push('SENTRY_DSN is required in production');
  }

  if (!config.S3_ACCESS_KEY || !config.S3_SECRET_KEY) {
    errors.push('S3 credentials are required in production');
  }

  if (!config.CORS_ORIGIN) {
    errors.push('CORS_ORIGIN should be explicitly set in production');
  }

  // Check for development defaults
  if (config.DATABASE_URL.includes('localhost') || config.DATABASE_URL.includes('password')) {
    errors.push('DATABASE_URL appears to use development defaults');
  }

  if (errors.length > 0) {
    throw new Error(`Production security validation failed:\n${errors.join('\n')}`);
  }
}

/**
 * Get configuration with runtime validation
 */
let configCache: Config | null = null;

export function getConfig(): Config {
  if (!configCache) {
    configCache = loadConfig();
  }
  return configCache;
}

/**
 * Check if a feature is enabled
 */
export function isFeatureEnabled(feature: keyof Pick<Config, 'CONTENT_WORKER_ENABLED' | 'AI_PROXY_ENABLED' | 'SIMULATION_ENABLED'>): boolean {
  return getConfig()[feature];
}

/**
 * Get database configuration (separated for security)
 */
export function getDatabaseConfig() {
  const config = getConfig();
  return {
    url: config.DATABASE_URL,
    // Add other database-specific configuration here
  };
}

/**
 * Get JWT configuration (separated for security)
 */
export function getJWTConfig() {
  const config = getConfig();
  return {
    secret: config.JWT_SECRET,
    // Add other JWT-specific configuration here
  };
}

/**
 * Get S3 configuration (separated for security)
 */
export function getS3Config() {
  const config = getConfig();
  if (!config.S3_ACCESS_KEY || !config.S3_SECRET_KEY) {
    throw new Error('S3 credentials not configured');
  }
  
  return {
    endpoint: config.S3_ENDPOINT,
    accessKey: config.S3_ACCESS_KEY,
    secretKey: config.S3_SECRET_KEY,
    bucket: config.S3_BUCKET,
  };
}

export default getConfig;
