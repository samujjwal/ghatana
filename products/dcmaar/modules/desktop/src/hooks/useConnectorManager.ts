/**
 * @fileoverview useConnectorManager Hook
 *
 * React hook for managing the desktop connector system.
 * Initializes the connector manager, handles state synchronization,
 * and provides methods for connector operations.
 */

import { useEffect, useCallback, useRef } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
  connectorConfigAtom,
  connectorStateAtom,
  telemetrySnapshotAtom,
  connectorManagerInitializedAtom,
  addConnectorErrorAtom,
  updateConnectorStateAtom,
} from '../state/connectors';
import { DesktopConnectorManager, type ControlCommand } from '../libs/connectors';
import type { DesktopConnectorConfig } from '../libs/connectors';

/**
 * Hook for managing the connector system
 */
export function useConnectorManager() {
  const [config, setConfig] = useAtom(connectorConfigAtom);
  const [state] = useAtom(connectorStateAtom);
  const setTelemetry = useSetAtom(telemetrySnapshotAtom);
  const [isInitialized, setIsInitialized] = useAtom(connectorManagerInitializedAtom);
  const addError = useSetAtom(addConnectorErrorAtom);
  const updateState = useSetAtom(updateConnectorStateAtom);

  const managerRef = useRef<DesktopConnectorManager | null>(null);

  /**
   * Initialize connector manager with configuration
   */
  const initialize = useCallback(
    async (newConfig: DesktopConnectorConfig) => {
      try {
        // Shutdown existing manager if any
        if (managerRef.current) {
          await managerRef.current.shutdown();
        }

        // Create new manager
        const manager = new DesktopConnectorManager(newConfig);

        // Set up event listeners
        manager.on('telemetryUpdate', (snapshot: any) => {
          setTelemetry(snapshot);
        });

        manager.on('stateChange', (newState: any) => {
          updateState(newState);
        });

        manager.on('error', (error: any, connectorId?: string) => {
          addError({
            message: error?.message || String(error),
            connectorId,
          });
        });

        // Initialize manager
        await manager.initialize();

        managerRef.current = manager;
        setConfig(newConfig);
        setIsInitialized(true);

        return manager;
      } catch (error) {
        addError({
          message: `Failed to initialize connector manager: ${(error as Error).message}`,
        });
        throw error;
      }
    },
    [setConfig, setTelemetry, updateState, addError, setIsInitialized]
  );

  /**
   * Send command to sinks
   */
  const sendCommand = useCallback(async (command: ControlCommand) => {
    if (!managerRef.current) {
      throw new Error('Connector manager not initialized');
    }
    return managerRef.current.sendCommand(command);
  }, []);

  /**
   * Get current snapshot from manager
   */
  const getSnapshot = useCallback(async () => {
    if (!managerRef.current) {
      throw new Error('Connector manager not initialized');
    }
    return managerRef.current.getSnapshot();
  }, []);

  /**
   * Perform health check
   */
  const healthCheck = useCallback(async () => {
    if (!managerRef.current) {
      throw new Error('Connector manager not initialized');
    }
    return managerRef.current.healthCheck();
  }, []);

  /**
   * Start all connectors
   */
  const startAll = useCallback(async () => {
    if (!managerRef.current) {
      throw new Error('Connector manager not initialized');
    }
    return managerRef.current.startAll();
  }, []);

  /**
   * Shutdown connector manager
   */
  const shutdown = useCallback(async () => {
    if (managerRef.current) {
      await managerRef.current.shutdown();
      managerRef.current = null;
      setIsInitialized(false);
    }
  }, [setIsInitialized]);

  // Initialize on mount if config exists
  useEffect(() => {
    if (config && !isInitialized) {
      initialize(config).catch(console.error);
    }

    // Cleanup on unmount
    return () => {
      if (managerRef.current) {
        managerRef.current.shutdown().catch(console.error);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return {
    config,
    state,
    isInitialized,
    initialize,
    sendCommand,
    getSnapshot,
    healthCheck,
    startAll,
    shutdown,
    manager: managerRef.current,
  };
}
