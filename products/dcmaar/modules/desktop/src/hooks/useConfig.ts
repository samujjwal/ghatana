import { useState, useEffect, useCallback } from 'react';
import { configService, type AppConfig, type CollectionConfig, type CommandConfig } from '../services/configService';

export function useConfig() {
  const [config, setConfig] = useState<AppConfig>(configService.getConfig());
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);

  // Load config from server on mount
  useEffect(() => {
    let mounted = true;
    
    const loadConfig = async () => {
      try {
        setIsLoading(true);
        const serverConfig = await configService.fetchFromServer();
        if (mounted) {
          setConfig(serverConfig);
          setError(null);
        }
      } catch (err) {
        console.error('Failed to load config:', err);
        if (mounted) {
          setError(err instanceof Error ? err : new Error('Failed to load configuration'));
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    };

    loadConfig();

    return () => {
      mounted = false;
    };
  }, []);

  // Update configuration
  const updateConfig = useCallback(async (updates: Partial<AppConfig>) => {
    try {
      setIsLoading(true);
      const updatedConfig = configService.updateConfig(updates);
      await configService.saveToServer();
      setConfig(updatedConfig);
      setError(null);
      return updatedConfig;
    } catch (err) {
      console.error('Failed to update config:', err);
      const error = err instanceof Error ? err : new Error('Failed to update configuration');
      setError(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Collection operations
  const addCollection = useCallback(async (collection: Omit<CollectionConfig, 'id'>, id?: string) => {
    try {
      setIsLoading(true);
      const newCollection = configService.addCollection(collection, id);
      await configService.saveToServer();
      setConfig(configService.getConfig());
      setError(null);
      return newCollection;
    } catch (err) {
      console.error('Failed to add collection:', err);
      const error = err instanceof Error ? err : new Error('Failed to add collection');
      setError(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const updateCollection = useCallback(async (id: string, updates: Partial<Omit<CollectionConfig, 'id'>>) => {
    try {
      setIsLoading(true);
      const updated = configService.updateCollection(id, updates);
      if (!updated) {
        throw new Error(`Collection with id ${id} not found`);
      }
      await configService.saveToServer();
      setConfig(configService.getConfig());
      setError(null);
      return updated;
    } catch (err) {
      console.error('Failed to update collection:', err);
      const error = err instanceof Error ? err : new Error('Failed to update collection');
      setError(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const deleteCollection = useCallback(async (id: string) => {
    try {
      setIsLoading(true);
      const success = configService.deleteCollection(id);
      if (!success) {
        throw new Error(`Collection with id ${id} not found`);
      }
      await configService.saveToServer();
      setConfig(configService.getConfig());
      setError(null);
      return success;
    } catch (err) {
      console.error('Failed to delete collection:', err);
      const error = err instanceof Error ? err : new Error('Failed to delete collection');
      setError(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Command operations
  const addCommand = useCallback(async (command: Omit<CommandConfig, 'id'>, id?: string) => {
    try {
      setIsLoading(true);
      const newCommand = configService.addCommand(command, id);
      await configService.saveToServer();
      setConfig(configService.getConfig());
      setError(null);
      return newCommand;
    } catch (err) {
      console.error('Failed to add command:', err);
      const error = err instanceof Error ? err : new Error('Failed to add command');
      setError(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const updateCommand = useCallback(async (id: string, updates: Partial<Omit<CommandConfig, 'id'>>) => {
    try {
      setIsLoading(true);
      const updated = configService.updateCommand(id, updates);
      if (!updated) {
        throw new Error(`Command with id ${id} not found`);
      }
      await configService.saveToServer();
      setConfig(configService.getConfig());
      setError(null);
      return updated;
    } catch (err) {
      console.error('Failed to update command:', err);
      const error = err instanceof Error ? err : new Error('Failed to update command');
      setError(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const deleteCommand = useCallback(async (id: string) => {
    try {
      setIsLoading(true);
      const success = configService.deleteCommand(id);
      if (!success) {
        throw new Error(`Command with id ${id} not found`);
      }
      await configService.saveToServer();
      setConfig(configService.getConfig());
      setError(null);
      return success;
    } catch (err) {
      console.error('Failed to delete command:', err);
      const error = err instanceof Error ? err : new Error('Failed to delete command');
      setError(error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Get collections and commands as arrays for easier rendering
  const collections = Object.values(config.collections);
  const commands = Object.values(config.commands);

  return {
    // State
    config,
    isLoading,
    error,
    
    // Collections
    collections,
    getCollection: configService.getCollection.bind(configService),
    addCollection,
    updateCollection,
    deleteCollection,
    
    // Commands
    commands,
    getCommand: configService.getCommand.bind(configService),
    addCommand,
    updateCommand,
    deleteCommand,
    
    // General config
    updateConfig,
    refresh: () => configService.fetchFromServer().then(setConfig),
  };
}

export default useConfig;
