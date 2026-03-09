/**
 * Environment variable utilities with type safety
 */

/**
 * Get a required environment variable
 * @throws {Error} If the variable is not set
 */
export function getRequiredEnvVar(key: string): string {
  const value = import.meta.env[key];
  if (value === undefined || value === '') {
    throw new Error(`Required environment variable ${key} is not set`);
  }
  return value;
}

/**
 * Get an optional environment variable with a default value
 */
export function getOptionalEnvVar<T extends string | number | boolean>(
  key: string,
  defaultValue: T,
  type: 'string' | 'number' | 'boolean' = 'string'
): T {
  const value = import.meta.env[key];
  
  if (value === undefined || value === '') {
    return defaultValue;
  }

  try {
    switch (type) {
      case 'number':
        return Number(value) as T;
      case 'boolean':
        return (value === 'true') as T;
      default:
        return value as T;
    }
  } catch (_error) {
    console.warn(`Failed to parse environment variable ${key}, using default value`);
    void _error;
    return defaultValue;
  }
}

/**
 * Check if the app is running in development mode
 */
export const isDev = import.meta.env.DEV;

/**
 * Check if the app is running in production mode
 */
export const isProd = import.meta.env.PROD;

/**
 * Check if the app is running in test mode
 */
export const isTest = import.meta.env.MODE === 'test';

/**
 * Get the current environment name
 */
export const getEnvName = (): string => {
  if (isTest) return 'test';
  if (isDev) return 'development';
  return 'production';
};

/**
 * Environment variable validators
 */
export const envValidators = {
  required: (key: string): string => getRequiredEnvVar(key),
  string: (key: string, defaultValue: string = ''): string => 
    getOptionalEnvVar<string>(key, defaultValue, 'string'),
  number: (key: string, defaultValue: number): number =>
    getOptionalEnvVar<number>(key, defaultValue, 'number'),
  boolean: (key: string, defaultValue: boolean): boolean =>
    getOptionalEnvVar<boolean>(key, defaultValue, 'boolean'),
};

/**
 * Common environment variables with type safety
 */
export const env = {
  // App info
  appName: envValidators.string('VITE_APP_NAME', 'DCMAR Desktop'),
  appVersion: envValidators.string('VITE_APP_VERSION', '0.1.0'),
  appBasePath: envValidators.string('VITE_APP_BASE_PATH', ''),
  
  // API
  apiUrl: envValidators.string('VITE_API_URL', 'http://localhost:8080/api'),
  apiBaseUrl: envValidators.string('VITE_API_BASE_URL', 'http://localhost:8080'),
  
  // Features
  enableAnalytics: envValidators.boolean('VITE_ENABLE_ANALYTICS', false),
  enableLogging: envValidators.boolean('VITE_ENABLE_LOGGING', !isProd),
  
  // Optional integrations
  sentryDsn: envValidators.string('VITE_SENTRY_DSN', ''),
  googleAnalyticsId: envValidators.string('VITE_GOOGLE_ANALYTICS_ID', ''),
};

// Export the full Vite env object for advanced use cases
export const viteEnv = import.meta.env;
