/**
 * Environment Variable Validation Utility
 * 
 * Validates all required environment variables at application startup.
 * Fails fast with clear error messages if configuration is invalid.
 * 
 * @doc.type utility
 * @doc.purpose Environment validation and configuration
 * @doc.layer platform
 */

export interface RequiredEnvVars {
  // Database
  DATABASE_URL: string;
  
  // Payment Processing
  STRIPE_SECRET_KEY: string;
  
  // Caching & Sessions
  REDIS_URL: string;
  
  // Authentication
  JWT_SECRET: string;
  
  // Application
  NODE_ENV: 'development' | 'test' | 'production';
}

export interface OptionalEnvVars {
  // External Services
  AI_SERVICE_URL?: string;
  FEATURE_STORE_URL?: string;
  AI_REGISTRY_URL?: string;
  
  // CORS
  CORS_ORIGIN?: string;
  
  // Server
  PORT?: string;
  HOST?: string;
  
  // gRPC
  GRPC_SERVER_ADDRESS?: string;
  GRPC_USE_TLS?: string;
}

interface ValidationError {
  variable: string;
  reason: string;
  example?: string;
}

/**
 * Validates that a URL is properly formatted
 */
function isValidUrl(url: string): boolean {
  try {
    new URL(url);
    return true;
  } catch {
    return false;
  }
}

/**
 * Validates Stripe API key format
 */
function isValidStripeKey(key: string): boolean {
  return /^sk_(test|live)_[a-zA-Z0-9]{24,}$/.test(key);
}

/**
 * Validates PostgreSQL connection string
 */
function isValidPostgresUrl(url: string): boolean {
  return url.startsWith('postgres://') || url.startsWith('postgresql://');
}

/**
 * Validates Redis connection string
 */
function isValidRedisUrl(url: string): boolean {
  return url.startsWith('redis://') || url.startsWith('rediss://');
}

/**
 * Validates JWT secret strength
 */
function isValidJwtSecret(secret: string): boolean {
  // Minimum 32 characters for production security
  return secret.length >= 32;
}

/**
 * Validates all required environment variables
 * Throws error with detailed message if validation fails
 */
export function validateEnvironment(): RequiredEnvVars {
  const errors: ValidationError[] = [];
  const env = process.env;

  // Validate NODE_ENV
  const nodeEnv = env.NODE_ENV;
  if (!nodeEnv || !['development', 'test', 'production'].includes(nodeEnv)) {
    errors.push({
      variable: 'NODE_ENV',
      reason: 'Must be one of: development, test, production',
      example: 'NODE_ENV=production',
    });
  }

  // Validate DATABASE_URL
  const databaseUrl = env.DATABASE_URL;
  if (!databaseUrl) {
    errors.push({
      variable: 'DATABASE_URL',
      reason: 'Required for database connection',
      example: 'DATABASE_URL=postgresql://user:password@localhost:5432/tutorputor',
    });
  } else if (!isValidPostgresUrl(databaseUrl)) {
    errors.push({
      variable: 'DATABASE_URL',
      reason: 'Must be a valid PostgreSQL connection string',
      example: 'DATABASE_URL=postgresql://user:password@localhost:5432/tutorputor',
    });
  }

  // Validate STRIPE_SECRET_KEY
  const stripeKey = env.STRIPE_SECRET_KEY;
  if (!stripeKey) {
    errors.push({
      variable: 'STRIPE_SECRET_KEY',
      reason: 'Required for payment processing',
      example: 'STRIPE_SECRET_KEY=stripe_test_placeholder_secret',
    });
  } else if (!isValidStripeKey(stripeKey)) {
    errors.push({
      variable: 'STRIPE_SECRET_KEY',
      reason: 'Must be a valid Stripe API key (sk_test_* or sk_live_*)',
      example: 'STRIPE_SECRET_KEY=stripe_test_placeholder_secret',
    });
  }

  // Validate REDIS_URL
  const redisUrl = env.REDIS_URL;
  if (!redisUrl) {
    errors.push({
      variable: 'REDIS_URL',
      reason: 'Required for caching and sessions',
      example: 'REDIS_URL=redis://localhost:6379',
    });
  } else if (!isValidRedisUrl(redisUrl)) {
    errors.push({
      variable: 'REDIS_URL',
      reason: 'Must be a valid Redis connection string',
      example: 'REDIS_URL=redis://localhost:6379',
    });
  }

  // Validate JWT_SECRET
  const jwtSecret = env.JWT_SECRET;
  if (!jwtSecret) {
    errors.push({
      variable: 'JWT_SECRET',
      reason: 'Required for authentication',
      example: 'JWT_SECRET=your-secret-key-min-32-chars',
    });
  } else if (!isValidJwtSecret(jwtSecret)) {
    errors.push({
      variable: 'JWT_SECRET',
      reason: 'Must be at least 32 characters for security',
      example: 'JWT_SECRET=your-very-long-secret-key-at-least-32-characters',
    });
  }

  // If there are validation errors, throw with detailed message
  if (errors.length > 0) {
    const errorMessage = [
      '❌ Environment Variable Validation Failed',
      '',
      'The following environment variables are missing or invalid:',
      '',
      ...errors.map(err => [
        `  • ${err.variable}`,
        `    Reason: ${err.reason}`,
        err.example ? `    Example: ${err.example}` : '',
        '',
      ].filter(Boolean).join('\n')),
      'Please set these environment variables and restart the application.',
      '',
    ].join('\n');

    throw new Error(errorMessage);
  }

  // Return validated environment
  return {
    DATABASE_URL: databaseUrl!,
    STRIPE_SECRET_KEY: stripeKey!,
    REDIS_URL: redisUrl!,
    JWT_SECRET: jwtSecret!,
    NODE_ENV: nodeEnv as 'development' | 'test' | 'production',
  };
}

/**
 * Validates optional environment variables and returns them with defaults
 */
export function getOptionalEnvVars(): OptionalEnvVars {
  const env = process.env;

  return {
    AI_SERVICE_URL: env.AI_SERVICE_URL,
    FEATURE_STORE_URL: env.FEATURE_STORE_URL,
    AI_REGISTRY_URL: env.AI_REGISTRY_URL,
    CORS_ORIGIN: env.CORS_ORIGIN || '*',
    PORT: env.PORT || '3000',
    HOST: env.HOST || '0.0.0.0',
    GRPC_SERVER_ADDRESS: env.GRPC_SERVER_ADDRESS,
    GRPC_USE_TLS: env.GRPC_USE_TLS,
  };
}

/**
 * Validates and returns all environment variables
 * Call this at application startup before any other initialization
 */
export function validateAndGetEnv(): { required: RequiredEnvVars; optional: OptionalEnvVars } {
  const required = validateEnvironment();
  const optional = getOptionalEnvVars();

  return { required, optional };
}
