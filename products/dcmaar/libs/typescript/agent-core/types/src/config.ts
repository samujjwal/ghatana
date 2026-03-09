// Shared configuration types

export interface AppConfig {
  /** Application name */
  name: string;
  
  /** Application version */
  version: string;
  
  /** Environment (development, production, test, etc.) */
  env: 'development' | 'production' | 'test' | string;
  
  /** Whether the app is running in development mode */
  isDev: boolean;
  
  /** Whether the app is running in production mode */
  isProd: boolean;
  
  /** API configuration */
  api: {
    /** Base URL for API requests */
    baseUrl: string;
    
    /** Timeout for API requests in milliseconds */
    timeout: number;
  };
  
  /** Feature flags */
  features: Record<string, boolean>;
}

/**
 * Runtime environment variables that can be used to configure the application
 */
export interface EnvVariables {
  NODE_ENV?: 'development' | 'production' | 'test';
  API_URL?: string;
  API_TIMEOUT?: string;
  [key: string]: string | undefined;
}
