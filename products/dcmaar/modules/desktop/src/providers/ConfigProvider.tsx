import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';

// Mock Tauri invoke function  
const invoke = async (cmd: string, args?: unknown) => {
  console.log('Mock Tauri invoke:', cmd, args);
  return Promise.resolve({});
};
import { CollectionConfig, CommandConfig, AppConfig, AppConfigSchema } from '../services/configService';

interface ConfigContextType {
  config: AppConfig;
  isLoading: boolean;
  error: Error | null;
  refresh: () => Promise<void>;
  updateConfig: (updates: Partial<AppConfig>) => Promise<void>;
  
  // Collections
  collections: CollectionConfig[];
  getCollection: (id: string) => CollectionConfig | undefined;
  addCollection: (collection: Omit<CollectionConfig, 'id'>) => Promise<void>;
  updateCollection: (id: string, updates: Partial<Omit<CollectionConfig, 'id'>>) => Promise<void>;
  deleteCollection: (id: string) => Promise<void>;
  
  // Commands
  commands: CommandConfig[];
  getCommand: (id: string) => CommandConfig | undefined;
  addCommand: (command: Omit<CommandConfig, 'id'>) => Promise<void>;
  updateCommand: (id: string, updates: Partial<Omit<CommandConfig, 'id'>>) => Promise<void>;
  deleteCommand: (id: string) => Promise<void>;
}

const ConfigContext = createContext<ConfigContextType | undefined>(undefined);

export const ConfigProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [config, setConfig] = useState<AppConfig>({
    version: '1.0.0',
    collections: {},
    commands: {},
    settings: {
      autoStart: true,
      logLevel: 'info',
      maxLogSize: 10,
    },
  });
  
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);
  // Mock snackbar notification
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const enqueueSnackbar = (message: string, _options?: unknown) => {
    console.log('Notification:', message, _options);
  };

  // Helper to generate a unique ID
  const generateId = () => Math.random().toString(36).substr(2, 9);

  // Load configuration from storage and server
  const loadConfig = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    
    try {
      // First try to load from server
      try {
        const serverConfig = await invoke('fetch_config') as AppConfig;
        const validated = AppConfigSchema.parse(serverConfig);
        setConfig(validated);
      } catch (serverError) {
        console.warn('Failed to load config from server, using local storage:', serverError);
        
        // Fall back to local storage if server is not available
        const stored = localStorage.getItem('dcmaar_config');
        if (stored) {
          const parsed = JSON.parse(stored);
          const validated = AppConfigSchema.parse(parsed);
          setConfig(validated);
        }
      }
    } catch (err) {
      console.error('Failed to load configuration:', err);
      setError(err instanceof Error ? err : new Error('Failed to load configuration'));
      enqueueSnackbar('Failed to load configuration', { variant: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [enqueueSnackbar]);

  // Save configuration to storage and server
  const saveConfig = useCallback(async (newConfig: AppConfig) => {
    try {
      // Validate the config
      const validated = AppConfigSchema.parse(newConfig);
      
      // Save to local storage
      localStorage.setItem('dcmaar_config', JSON.stringify(validated));
      
      // Try to save to server
      try {
        await invoke('save_config', { config: validated });
      } catch (serverError) {
        console.warn('Failed to save config to server:', serverError);
        enqueueSnackbar('Configuration saved locally (server unavailable)', { variant: 'warning' });
      }
      
      // Update the state
      setConfig(validated);
      return validated;
    } catch (err) {
      console.error('Failed to save configuration:', err);
      enqueueSnackbar('Failed to save configuration', { variant: 'error' });
      throw err;
    }
  }, [enqueueSnackbar]);

  // Load config on mount
  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  // Update configuration
  const updateConfig = useCallback(async (updates: Partial<AppConfig>) => {
    const newConfig = { ...config, ...updates };
    await saveConfig(newConfig);
  }, [config, saveConfig]);

  // Collections
  const collections = Object.values(config.collections);
  
  const getCollection = useCallback((id: string) => {
    return config.collections[id];
  }, [config.collections]);
  
  const addCollection = useCallback(async (collection: Omit<CollectionConfig, 'id'>) => {
    const id = generateId();
    const newCollection = { ...collection, id };
    const collections = { ...config.collections, [id]: newCollection };
    await updateConfig({ collections });
    enqueueSnackbar('Collection added', { variant: 'success' });
  }, [config.collections, updateConfig, enqueueSnackbar]);
  
  const updateCollection = useCallback(async (id: string, updates: Partial<Omit<CollectionConfig, 'id'>>) => {
    const collection = config.collections[id];
    if (!collection) {
      throw new Error(`Collection with ID ${id} not found`);
    }
    const updatedCollection = { ...collection, ...updates };
    const collections = { ...config.collections, [id]: updatedCollection };
    await updateConfig({ collections });
    enqueueSnackbar('Collection updated', { variant: 'success' });
  }, [config.collections, updateConfig, enqueueSnackbar]);
  
  const deleteCollection = useCallback(async (id: string) => {
  const { [id]: _removed, ...collections } = config.collections;
  void _removed; // explicitly mark as used to satisfy linter
  await updateConfig({ collections });
    enqueueSnackbar('Collection deleted', { variant: 'info' });
  }, [config.collections, updateConfig, enqueueSnackbar]);

  // Commands
  const commands = Object.values(config.commands);
  
  const getCommand = useCallback((id: string) => {
    return config.commands[id];
  }, [config.commands]);
  
  const addCommand = useCallback(async (command: Omit<CommandConfig, 'id'>) => {
    const id = generateId();
    const newCommand = { ...command, id };
    const commands = { ...config.commands, [id]: newCommand };
    await updateConfig({ commands });
    enqueueSnackbar('Command added', { variant: 'success' });
  }, [config.commands, updateConfig, enqueueSnackbar]);
  
  const updateCommand = useCallback(async (id: string, updates: Partial<Omit<CommandConfig, 'id'>>) => {
    const command = config.commands[id];
    if (!command) {
      throw new Error(`Command with ID ${id} not found`);
    }
    const updatedCommand = { ...command, ...updates };
    const commands = { ...config.commands, [id]: updatedCommand };
    await updateConfig({ commands });
    enqueueSnackbar('Command updated', { variant: 'success' });
  }, [config.commands, updateConfig, enqueueSnackbar]);
  
  const deleteCommand = useCallback(async (id: string) => {
  const { [id]: _removed, ...commands } = config.commands;
  void _removed; // explicitly mark as used to satisfy linter
  await updateConfig({ commands });
    enqueueSnackbar('Command deleted', { variant: 'info' });
  }, [config.commands, updateConfig, enqueueSnackbar]);

  // Context value
  const contextValue = {
    config,
    isLoading,
    error,
    refresh: loadConfig,
    updateConfig,
    
    // Collections
    collections,
    getCollection,
    addCollection,
    updateCollection,
    deleteCollection,
    
    // Commands
    commands,
    getCommand,
    addCommand,
    updateCommand,
    deleteCommand,
  };

  return (
    <ConfigContext.Provider value={contextValue}>
      {children}
    </ConfigContext.Provider>
  );
};

export const useConfigContext = (): ConfigContextType => {
  const context = useContext(ConfigContext);
  if (context === undefined) {
    throw new Error('useConfigContext must be used within a ConfigProvider');
  }
  return context;
};
