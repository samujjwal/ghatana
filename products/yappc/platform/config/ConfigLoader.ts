/**
 * Centralized Configuration Management
 * 
 * Provides a unified configuration system for all YAPPC services.
 * Consolidates scattered configuration from various files into a single source of truth.
 * 
 * @doc.type class
 * @doc.purpose Centralized configuration management
 * @doc.layer platform
 * @doc.pattern Configuration Management
 */

import { readFileSync } from 'fs';
import { join } from 'path';
import { parse as parseYaml } from 'yaml';

/**
 * Configuration schema definition
 */
export interface YappcConfig {
  /** Application metadata */
  app: {
    name: string;
    version: string;
    environment: 'development' | 'staging' | 'production';
    logLevel: 'debug' | 'info' | 'warn' | 'error';
  };
  
  /** Server configuration */
  server: {
    port: number;
    host: string;
    cors: {
      origins: string[];
      methods: string[];
    };
    rateLimit: {
      windowMs: number;
      maxRequests: number;
    };
  };
  
  /** Database configuration */
  database: {
    host: string;
    port: number;
    name: string;
    pool: {
      min: number;
      max: number;
    };
    ssl: boolean;
  };
  
  /** AI/LLM configuration */
  ai: {
    providers: {
      openai?: { apiKey: string; model: string };
      anthropic?: { apiKey: string; model: string };
      ollama?: { host: string; model: string };
    };
    defaultProvider: string;
    fallbackEnabled: boolean;
    timeoutMs: number;
  };
  
  /** Agent framework configuration */
  agents: {
    registryPath: string;
    capabilitiesPath: string;
    mappingsPath: string;
    maxConcurrent: number;
    defaultTimeoutMs: number;
  };
  
  /** Observability configuration */
  observability: {
    metrics: {
      enabled: boolean;
      port: number;
    };
    tracing: {
      enabled: boolean;
      jaegerEndpoint?: string;
    };
    logging: {
      format: 'json' | 'pretty';
      output: 'stdout' | 'file' | 'both';
    };
  };
  
  /** Feature flags */
  features: Record<string, boolean>;
}

/**
 * Default configuration values
 */
const DEFAULT_CONFIG: Partial<YappcConfig> = {
  app: {
    name: 'yappc',
    version: '1.0.0',
    environment: 'development',
    logLevel: 'info',
  },
  server: {
    port: 8080,
    host: '0.0.0.0',
    cors: {
      origins: ['http://localhost:3000', 'http://localhost:5173'],
      methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    },
    rateLimit: {
      windowMs: 15 * 60 * 1000, // 15 minutes
      maxRequests: 100,
    },
  },
  database: {
    host: 'localhost',
    port: 5432,
    name: 'yappc',
    pool: {
      min: 2,
      max: 10,
    },
    ssl: false,
  },
  ai: {
    providers: {},
    defaultProvider: 'ollama',
    fallbackEnabled: true,
    timeoutMs: 30000,
  },
  agents: {
    registryPath: 'config/agents/registry.yaml',
    capabilitiesPath: 'config/agents/capabilities.yaml',
    mappingsPath: 'config/agents/mappings.yaml',
    maxConcurrent: 10,
    defaultTimeoutMs: 60000,
  },
  observability: {
    metrics: {
      enabled: true,
      port: 9090,
    },
    tracing: {
      enabled: false,
    },
    logging: {
      format: 'json',
      output: 'stdout',
    },
  },
  features: {},
};

/**
 * Configuration loader
 * Loads configuration from multiple sources in order of priority:
 * 1. Environment variables (highest priority)
 * 2. Configuration files (config/application.yaml)
 * 3. Default values (lowest priority)
 */
export class ConfigLoader {
  private config: YappcConfig;
  
  constructor(configPath?: string) {
    this.config = this.loadConfig(configPath);
  }
  
  /**
   * Get configuration value
   */
  get<T>(path: string): T | undefined {
    const parts = path.split('.');
    let current: any = this.config;
    
    for (const part of parts) {
      if (current === null || current === undefined) {
        return undefined;
      }
      current = current[part];
    }
    
    return current as T;
  }
  
  /**
   * Get entire configuration
   */
  getAll(): YappcConfig {
    return { ...this.config };
  }
  
  /**
   * Load configuration from all sources
   */
  private loadConfig(configPath?: string): YappcConfig {
    // Start with defaults
    let config = this.mergeDeep({}, DEFAULT_CONFIG) as YappcConfig;
    
    // Load from file if exists
    const filePath = configPath || process.env.YAPPC_CONFIG || 'config/application.yaml';
    try {
      const fileConfig = this.loadFromFile(filePath);
      config = this.mergeDeep(config, fileConfig);
    } catch (error) {
      console.warn(`[Config] Could not load config file ${filePath}:`, error);
    }
    
    // Override with environment variables
    const envConfig = this.loadFromEnvironment();
    config = this.mergeDeep(config, envConfig);
    
    return config;
  }
  
  /**
   * Load configuration from YAML file
   */
  private loadFromFile(path: string): Partial<YappcConfig> {
    try {
      const content = readFileSync(path, 'utf8');
      return parseYaml(content) as Partial<YappcConfig>;
    } catch (error) {
      return {};
    }
  }
  
  /**
   * Load configuration from environment variables
   * Converts YAPPC_SERVER_PORT to { server: { port } }
   */
  private loadFromEnvironment(): Partial<YappcConfig> {
    const envConfig: any = {};
    
    for (const [key, value] of Object.entries(process.env)) {
      if (key.startsWith('YAPPC_')) {
        const path = key
          .replace('YAPPC_', '')
          .toLowerCase()
          .split('_');
        
        this.setNestedValue(envConfig, path, this.parseValue(value));
      }
    }
    
    return envConfig;
  }
  
  /**
   * Set nested object value
   */
  private setNestedValue(obj: any, path: string[], value: any): void {
    let current = obj;
    
    for (let i = 0; i < path.length - 1; i++) {
      if (!(path[i] in current)) {
        current[path[i]] = {};
      }
      current = current[path[i]];
    }
    
    current[path[path.length - 1]] = value;
  }
  
  /**
   * Parse environment variable value
   */
  private parseValue(value: string | undefined): any {
    if (value === undefined) return undefined;
    
    // Try boolean
    if (value.toLowerCase() === 'true') return true;
    if (value.toLowerCase() === 'false') return false;
    
    // Try number
    const num = Number(value);
    if (!isNaN(num) && value !== '') return num;
    
    // Try JSON array/object
    try {
      return JSON.parse(value);
    } catch {
      return value;
    }
  }
  
  /**
   * Deep merge objects
   */
  private mergeDeep(target: any, source: any): any {
    if (source === null || source === undefined) return target;
    if (typeof source !== 'object') return source;
    
    const result = { ...target };
    
    for (const key of Object.keys(source)) {
      if (source[key] instanceof Object && key in result) {
        result[key] = this.mergeDeep(result[key], source[key]);
      } else {
        result[key] = source[key];
      }
    }
    
    return result;
  }
}

/**
 * Singleton configuration instance
 */
let configInstance: ConfigLoader | null = null;

export function getConfig(configPath?: string): ConfigLoader {
  if (!configInstance) {
    configInstance = new ConfigLoader(configPath);
  }
  return configInstance;
}

export function resetConfig(): void {
  configInstance = null;
}
