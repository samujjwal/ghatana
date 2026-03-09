/**
 * Environment configuration validation and type-safe access to environment variables.
 *
 * <p><b>Purpose</b><br>
 * Validates required environment variables on application startup to prevent runtime
 * errors due to missing configuration. Provides type-safe access to configuration
 * values with proper parsing (numbers, booleans) and default values where appropriate.
 *
 * <p><b>Configuration Categories</b><br>
 * - Server: NODE_ENV, PORT, CORS origins
 * - Database: PostgreSQL connection parameters (host, port, database, credentials)
 * - Authentication: JWT secrets, token expiration times
 * - Email: SMTP server settings (host, port, credentials)
 * - External APIs: Google OAuth, SendGrid
 * - Monitoring: Sentry DSN, Prometheus port
 * - WebSocket: Socket.io configuration
 *
 * <p><b>Validation</b><br>
 * On startup, checks for presence of all required variables. Throws descriptive
 * error if any are missing, preventing silent failures in production. Optional
 * variables have sensible defaults (e.g., PORT=3000, NODE_ENV=development).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { env } from './config/environment';
 * 
 * const server = fastify({ port: env.PORT });
 * const db = new Pool({
 *   host: env.DB_HOST,
 *   port: env.DB_PORT,
 *   database: env.DB_NAME
 * });
 * }</pre>
 *
 * <p><b>Security</b><br>
 * Never log or expose sensitive values (JWT secrets, database passwords, API keys).
 * Use .env files for local development, environment variables in production.
 *
 * @doc.type class
 * @doc.purpose Environment configuration validation and type-safe access
 * @doc.layer backend
 * @doc.pattern Configuration
 */
import { logger } from '../utils/logger';

interface EnvironmentConfig {
  // Server
  NODE_ENV: string;
  PORT: string;
  
  // Database
  DB_HOST: string;
  DB_PORT: string;
  DB_NAME: string;
  DB_USER: string;
  DB_PASSWORD: string;
  
  // Authentication
  JWT_SECRET: string;
  JWT_REFRESH_SECRET: string;
  
  // CORS
  CORS_ORIGIN?: string;
  
  // Optional (with defaults)
  LOG_LEVEL?: string;
  RATE_LIMIT_WINDOW_MS?: string;
  RATE_LIMIT_MAX_REQUESTS?: string;
}

/**
 * Required environment variables (must be set)
 */
const REQUIRED_VARS = [
  'DB_HOST',
  'DB_PASSWORD',
  'JWT_SECRET',
  'JWT_REFRESH_SECRET',
] as const;

/**
 * Validate environment configuration
 * Throws error if any required variable is missing or invalid
 */
export function validateEnvironment(): EnvironmentConfig {
  const errors: string[] = [];
  
  // Check for missing required variables
  for (const varName of REQUIRED_VARS) {
    if (!process.env[varName]) {
      errors.push(`Missing required environment variable: ${varName}`);
    }
  }
  
  // Validate JWT secret strength (must be at least 32 characters)
  if (process.env.JWT_SECRET && process.env.JWT_SECRET.length < 32) {
    errors.push('JWT_SECRET must be at least 32 characters long for security');
  }
  
  if (process.env.JWT_REFRESH_SECRET && process.env.JWT_REFRESH_SECRET.length < 32) {
    errors.push('JWT_REFRESH_SECRET must be at least 32 characters long for security');
  }
  
  // Validate database port is a number
  if (process.env.DB_PORT && isNaN(parseInt(process.env.DB_PORT))) {
    errors.push('DB_PORT must be a valid number');
  }
  
  // Validate server port is a number
  if (process.env.PORT && isNaN(parseInt(process.env.PORT))) {
    errors.push('PORT must be a valid number');
  }
  
  // Production-specific validation
  if (process.env.NODE_ENV === 'production') {
    // Ensure CORS is configured for production
    if (!process.env.CORS_ORIGIN) {
      errors.push('CORS_ORIGIN must be set in production');
    }
    
    // Ensure strong database password
    if (process.env.DB_PASSWORD && process.env.DB_PASSWORD.length < 16) {
      errors.push('DB_PASSWORD must be at least 16 characters in production');
    }
    
    // Warn about default database user
    if (process.env.DB_USER === 'guardian') {
      logger.warn('Using default database user in production - consider using a custom username');
    }
    
    // Recommend Sentry for error monitoring
    if (!process.env.SENTRY_DSN) {
      logger.warn('SENTRY_DSN not configured - error monitoring disabled');
    }
  }
  
  // Throw error if any validation failed
  if (errors.length > 0) {
    logger.error('Environment validation failed', { errors });
    throw new Error(`Environment validation failed:\n${errors.join('\n')}`);
  }
  
  // Log successful validation
  logger.info('Environment configuration validated', {
    nodeEnv: process.env.NODE_ENV || 'development',
    dbHost: process.env.DB_HOST,
    dbName: process.env.DB_NAME,
    corsOrigin: process.env.CORS_ORIGIN,
  });
  
  return process.env as unknown as EnvironmentConfig;
}

/**
 * Get environment variable with type safety
 */
export function getEnv(key: keyof EnvironmentConfig, defaultValue?: string): string {
  return process.env[key] || defaultValue || '';
}

/**
 * Get environment variable as number
 */
export function getEnvNumber(key: keyof EnvironmentConfig, defaultValue: number): number {
  const value = process.env[key];
  if (!value) return defaultValue;
  const parsed = parseInt(value, 10);
  return isNaN(parsed) ? defaultValue : parsed;
}

/**
 * Get environment variable as boolean
 */
export function getEnvBoolean(key: string, defaultValue: boolean): boolean {
  const value = process.env[key];
  if (!value) return defaultValue;
  return value.toLowerCase() === 'true' || value === '1';
}

export default {
  validateEnvironment,
  getEnv,
  getEnvNumber,
  getEnvBoolean,
};
