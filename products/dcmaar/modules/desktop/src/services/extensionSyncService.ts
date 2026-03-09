import { useEffect, useCallback } from 'react';
import { useExtension } from '../hooks/useExtension';
import { useConfigContext } from '../providers/ConfigProvider';
import { debounce } from 'lodash';
import { AppConfig } from './configService';

export interface ExtensionConfig {
  collections: AppConfig['collections'];
  commands: AppConfig['commands'];
  settings: AppConfig['settings']; // Use the full settings since all properties are public
  lastSynced?: number;
}

/**
 * Hook to handle bidirectional synchronization between the desktop app and browser extension
 */
export const useExtensionSync = () => {
  const { config, updateConfig } = useConfigContext();
  const { sendConfig, isConnected, on, off, requestConfig } = useExtension();

  // Validate and sanitize config before sending to extension
  const prepareExtensionConfig = useCallback((fullConfig: AppConfig): ExtensionConfig => {
    return {
      collections: fullConfig.collections,
      commands: fullConfig.commands,
      settings: {
        autoStart: fullConfig.settings.autoStart,
        logLevel: fullConfig.settings.logLevel,
        maxLogSize: fullConfig.settings.maxLogSize,
      },
      lastSynced: Date.now(),
    };
  }, []);

  // Debounced sync function to prevent rapid updates
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const syncConfigWithExtension = useCallback(debounce(async (newConfig: AppConfig) => {
    if (!isConnected) return;

    try {
      const extensionConfig = prepareExtensionConfig(newConfig);
      await sendConfig(extensionConfig);
    } catch (error) {
      console.error('Failed to sync config with extension:', error);
      // Optionally implement retry logic here
    }
  }, 500), [isConnected, sendConfig, prepareExtensionConfig]);

  // Request latest config from extension when connection is established
  const syncFromExtension = useCallback(async () => {
    if (!isConnected) return;

    try {
      const extensionConfig = await requestConfig();
      if (extensionConfig && typeof extensionConfig === 'object') {
        const ext: any = extensionConfig;
        updateConfig({
          ...config,
          collections: ext.collections || config.collections,
          commands: ext.commands || config.commands,
          settings: {
            ...config.settings,
            ...(ext.settings || {})
          }
        });
      }
    } catch (error) {
      console.error('Failed to sync config from extension:', error);
    }
  }, [isConnected, config, updateConfig, requestConfig]);

  // Handle initial sync when extension is connected
  useEffect(() => {
    if (isConnected) {
      syncFromExtension();
    }
  }, [isConnected, syncFromExtension]);

  // Sync config changes to the extension
  useEffect(() => {
    if (!isConnected) return;

    syncConfigWithExtension(config);

    return () => {
      syncConfigWithExtension.cancel();
    };
  }, [config, isConnected, syncConfigWithExtension]);

  // Handle config updates from the extension
  useEffect(() => {
    if (!isConnected) return;

    const handleExtensionConfigUpdate = (extensionConfig: any) => {
      if (!extensionConfig || typeof extensionConfig !== 'object') {
        console.warn('Received invalid config from extension');
        return;
      }

      try {
        const ext: any = extensionConfig;
        updateConfig({
          ...config,
          collections: ext.collections || config.collections,
          commands: ext.commands || config.commands,
          settings: {
            ...config.settings,
            ...(ext.settings || {})
          }
        });
      } catch (error) {
        console.error('Failed to update config from extension:', error);
      }
    };

    on('configUpdate', handleExtensionConfigUpdate);

    return () => {
      off('configUpdate', handleExtensionConfigUpdate);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    };
  }, [config, isConnected, updateConfig, on]);

  // Handle connection status changes
  useEffect(() => {
    if (!isConnected) return;

    // Re-sync when connection is restored
    const handleReconnect = () => {
      console.log('Extension reconnected, syncing config...');
      syncFromExtension();
    };

    on('connected', handleReconnect);

    return () => {
      // eslint-disable-next-line react-hooks/exhaustive-deps
      off('connected', handleReconnect);
    };
  }, [isConnected, syncFromExtension, on]);

  // Return sync status and manual sync function
  return {
    isSyncing: isConnected,
    lastSynced: Date.now(), // Mock timestamp for now
    forceSync: () => syncConfigWithExtension(config),
    syncFromExtension
  };
};
