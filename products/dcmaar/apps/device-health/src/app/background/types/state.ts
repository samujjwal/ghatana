export interface ExtensionConfig {
  version: string;
  environment: 'development' | 'staging' | 'production';
  apiEndpoints: {
    metrics: string;
    config: string;
    telemetry: string;
  };
  storage: {
    prefix: string;
    version: number;
  };
  features: Record<string, boolean>;
  logging: {
    level: 'error' | 'warn' | 'info' | 'debug';
    persist: boolean;
  };
}

export interface ExtensionState {
  isInitialized: boolean;
  lastActive: number;
  sessionStart: number;
  metrics: {
    enabled: boolean;
    eventsProcessed: number;
    lastFlush: number;
  };
  config: ExtensionConfig;
  features: Record<string, { enabled: boolean; lastUsed?: number }>;
}

export const DEFAULT_STATE: ExtensionState = {
  isInitialized: false,
  lastActive: Date.now(),
  sessionStart: Date.now(),
  metrics: {
    enabled: true,
    eventsProcessed: 0,
    lastFlush: 0,
  },
  config: {
    version: '1.0.0',
    environment: 'development',
    apiEndpoints: {
      metrics: 'https://api.example.com/v1/metrics',
      config: 'https://api.example.com/v1/config',
      telemetry: 'https://api.example.com/v1/telemetry',
    },
    storage: {
      prefix: 'dcmaar_',
      version: 1,
    },
    features: {
      metrics: true,
      logging: true,
      autoUpdate: true,
    },
    logging: {
      level: 'info',
      persist: false,
    },
  },
  features: {
    metrics: { enabled: true },
    logging: { enabled: true },
  },
};

export function isExtensionState(value: unknown): value is ExtensionState {
  return typeof value === 'object' && value !== null && 'isInitialized' in value;
}

export function isExtensionConfig(value: unknown): value is ExtensionConfig {
  return typeof value === 'object' && value !== null && 'version' in value;
}

