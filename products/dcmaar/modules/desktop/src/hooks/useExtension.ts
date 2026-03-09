import { useState, useCallback, useEffect } from 'react';

export interface ExtensionStatus {
  connected: boolean;
  version?: string;
  capabilities: string[];
  message?: string;
}

export interface ExtensionConnection {
  isConnected: boolean;
  status: ExtensionStatus | { connected: false; message?: string };
  error: string | null;
  isLoading: boolean;
  refresh: () => Promise<void>;
  sendConfig: (config: unknown) => Promise<void>;
  requestConfig: () => Promise<unknown>;
  sendCommand: (command: string, args?: unknown[]) => Promise<void>;
  hasCapability: (capability: string) => boolean;
  on: (event: string, handler: Function) => void;
  off: (event: string, handler: Function) => void;
}

/**
 * Hook for managing browser extension communication
 * This is a mock implementation - would need to be connected to actual extension communication
 */
export const useExtension = (): ExtensionConnection => {
  const [isConnected, setIsConnected] = useState(false);
  const [status, setStatus] = useState<ExtensionStatus | { connected: false; message?: string }>({ connected: false, message: 'Connecting...' });
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Mock connection status that changes after a delay
  useEffect(() => {
    const timer = setTimeout(() => {
      setIsConnected(true);
      setStatus({ connected: true, version: '1.0.0', capabilities: ['config-sync', 'commands', 'events'] });
      setIsLoading(false);
    }, 2000);

    return () => clearTimeout(timer);
  }, []);

  const refresh = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // Mock refresh logic
      await new Promise(resolve => setTimeout(resolve, 1000));
      setStatus({ connected: true, version: '1.0.0', capabilities: ['config-sync', 'commands', 'events'] });
      setIsConnected(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      setStatus({ connected: false, message: err instanceof Error ? err.message : 'Unknown error' });
    } finally {
      setIsLoading(false);
    }
  }, []);

  const sendConfig = useCallback(async (config: unknown) => {
    console.log('Sending config to extension:', config);
    // Mock implementation
  }, []);

  const requestConfig = useCallback(async () => {
    console.log('Requesting config from extension');
    // Mock implementation
    return {};
  }, []);

  const sendCommand = useCallback(async (command: string, args?: unknown[]) => {
    console.log('Sending command to extension:', command, args);
    // Mock implementation
  }, []);

  const hasCapability = useCallback((capability: string) => {
    console.log('Checking capability:', capability);
    // Mock implementation - always return true
    return true;
  }, []);

  const on = useCallback((event: string, _handler: Function) => {
    console.log('Registering event handler:', event);
    // Mock implementation (handler intentionally unused in mock)
  }, []);

  const off = useCallback((event: string, _handler: Function) => {
    console.log('Unregistering event handler:', event);
    // Mock implementation (handler intentionally unused in mock)
  }, []);

  return {
    isConnected,
    status,
    error,
    isLoading,
    refresh,
    sendConfig,
    requestConfig,
    sendCommand,
    hasCapability,
    on,
    off
  };
};