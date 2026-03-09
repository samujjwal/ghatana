interface AppConfig {
  appName: string;
  appVersion: string;
  api: {
    baseUrl: string;
    timeout: number;
  };
  features: {
    analytics: boolean;
    logging: boolean;
  };
  sentryDsn?: string;
  googleAnalyticsId?: string;
}

// Get configuration from environment variables with fallbacks
const config: AppConfig = {
  appName: import.meta.env.VITE_APP_NAME || 'DCMAR Desktop',
  appVersion: import.meta.env.VITE_APP_VERSION || '0.1.0',
  api: {
    baseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    timeout: 30000, // 30 seconds
  },
  features: {
    analytics: import.meta.env.VITE_ENABLE_ANALYTICS === 'true',
    logging: import.meta.env.VITE_ENABLE_LOGGING !== 'false', // enabled by default
  },
  // Optional integrations
  ...(import.meta.env.VITE_SENTRY_DSN && { sentryDsn: import.meta.env.VITE_SENTRY_DSN }),
  ...(import.meta.env.VITE_GOOGLE_ANALYTICS_ID && { 
    googleAnalyticsId: import.meta.env.VITE_GOOGLE_ANALYTICS_ID 
  }),
};

// Validate required configuration in production
if (import.meta.env.PROD) {
  const requiredVars = ['VITE_APP_NAME', 'VITE_APP_VERSION', 'VITE_API_BASE_URL'];
  const missingVars = requiredVars.filter(varName => !import.meta.env[varName]);
  
  if (missingVars.length > 0) {
    console.error('Missing required environment variables:', missingVars.join(', '));
    // In production, you might want to throw an error or handle this differently
    if (import.meta.env.VITE_ENFORCE_ENV_VARS === 'true') {
      throw new Error(`Missing required environment variables: ${missingVars.join(', ')}`);
    }
  }
}

export default config;
