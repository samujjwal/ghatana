// Mock Tauri invoke function
const invoke = async (cmd: string, args?: unknown) => {
  console.log('Mock Tauri invoke:', cmd, args);
  return Promise.resolve({});
};
import { z } from 'zod';

// Define the schema for collection configuration
export const CollectionConfigSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  enabled: z.boolean().default(true),
  interval: z.number().min(1000).default(60000), // in milliseconds
  maxRetries: z.number().min(0).default(3),
  retryDelay: z.number().min(0).default(5000), // in milliseconds
});

export type CollectionConfig = z.infer<typeof CollectionConfigSchema>;

// Define the schema for command configuration
export const CommandConfigSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  command: z.string(),
  args: z.array(z.string()).default([]),
  timeout: z.number().min(0).default(30000), // in milliseconds
  workingDir: z.string().optional(),
  env: z.record(z.string(), z.string()).default({}),
});

export type CommandConfig = z.infer<typeof CommandConfigSchema>;

// Main configuration schema
export const AppConfigSchema = z.object({
  version: z.string(),
  collections: z.record(z.string(), CollectionConfigSchema).default({}),
  commands: z.record(z.string(), CommandConfigSchema).default({}),
  settings: z.object({
    autoStart: z.boolean().default(true),
    logLevel: z.enum(['error', 'warn', 'info', 'debug', 'trace']).default('info'),
    maxLogSize: z.number().min(1).default(10), // in MB
  }).default({
    autoStart: true,
    logLevel: 'info' as const,
    maxLogSize: 10,
  }),
});

export type AppConfig = z.infer<typeof AppConfigSchema>;

const CONFIG_STORAGE_KEY = 'dcmaar_desktop_config';

class ConfigService {
  private config: AppConfig = {
    version: '1.0.0',
    collections: {},
    commands: {},
    settings: {
      autoStart: true,
      logLevel: 'info',
      maxLogSize: 10,
    },
  };

  constructor() {
    this.loadFromStorage();
  }

  private async loadFromStorage() {
    try {
      const stored = localStorage.getItem(CONFIG_STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        this.config = AppConfigSchema.parse(parsed);
      }
    } catch (error) {
      console.error('Failed to load config from storage:', error);
      // Reset to defaults on error
      this.config = AppConfigSchema.parse({});
    }
  }

  private saveToStorage() {
    try {
      localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify(this.config));
    } catch (error) {
      console.error('Failed to save config to storage:', error);
    }
  }

  async fetchFromServer(): Promise<AppConfig> {
    try {
      // This will call the Tauri command to fetch config from the daemon
      const config = await invoke('fetch_config') as AppConfig;
      this.config = AppConfigSchema.parse(config);
      this.saveToStorage();
      return this.config;
    } catch (error) {
      console.error('Failed to fetch config from server:', error);
      throw error;
    }
  }

  async saveToServer(): Promise<void> {
    try {
      // This will call the Tauri command to save config to the daemon
      await invoke('save_config', { config: this.config });
      this.saveToStorage();
    } catch (error) {
      console.error('Failed to save config to server:', error);
      throw error;
    }
  }

  getConfig(): AppConfig {
    return { ...this.config };
  }

  updateConfig(updates: Partial<AppConfig>): AppConfig {
    this.config = {
      ...this.config,
      ...updates,
      settings: {
        ...this.config.settings,
        ...updates.settings,
      },
    };
    this.saveToStorage();
    return this.getConfig();
  }

  // Collection CRUD operations
  getCollection(id: string): CollectionConfig | undefined {
    return this.config.collections[id];
  }

  addCollection(collection: Omit<CollectionConfig, 'id'>, id?: string): CollectionConfig {
    const collectionId = id || `col_${Date.now()}`;
    const newCollection = {
      ...collection,
      id: collectionId,
    };
    
    this.config.collections[collectionId] = newCollection;
    this.saveToStorage();
    return newCollection;
  }

  updateCollection(id: string, updates: Partial<Omit<CollectionConfig, 'id'>>): CollectionConfig | undefined {
    const collection = this.config.collections[id];
    if (!collection) return undefined;

    const updated = {
      ...collection,
      ...updates,
      id, // Ensure ID doesn't change
    };

    this.config.collections[id] = updated;
    this.saveToStorage();
    return updated;
  }

  deleteCollection(id: string): boolean {
    if (!this.config.collections[id]) return false;
    delete this.config.collections[id];
    this.saveToStorage();
    return true;
  }

  // Command CRUD operations
  getCommand(id: string): CommandConfig | undefined {
    return this.config.commands[id];
  }

  addCommand(command: Omit<CommandConfig, 'id'>, id?: string): CommandConfig {
    const commandId = id || `cmd_${Date.now()}`;
    const newCommand = {
      ...command,
      id: commandId,
    };
    
    this.config.commands[commandId] = newCommand;
    this.saveToStorage();
    return newCommand;
  }

  updateCommand(id: string, updates: Partial<Omit<CommandConfig, 'id'>>): CommandConfig | undefined {
    const command = this.config.commands[id];
    if (!command) return undefined;

    const updated = {
      ...command,
      ...updates,
      id, // Ensure ID doesn't change
    };

    this.config.commands[id] = updated;
    this.saveToStorage();
    return updated;
  }

  deleteCommand(id: string): boolean {
    if (!this.config.commands[id]) return false;
    delete this.config.commands[id];
    this.saveToStorage();
    return true;
  }
}

export const configService = new ConfigService();
