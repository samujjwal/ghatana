/**
 * @fileoverview Connector State Management (Jotai Atoms)
 *
 * Manages state for the desktop connector system:
 * - Connector configuration
 * - Active connector state
 * - Telemetry data from sources
 * - Error tracking
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type { DesktopConnectorConfig, ConnectorState, TelemetrySnapshot } from '../libs/connectors';

/**
 * Connector configuration atom (persisted to localStorage)
 * This stores the user's connector configuration
 */
export const connectorConfigAtom = atomWithStorage<DesktopConnectorConfig | null>(
  'desktop-connector-config',
  null
);

/**
 * Connector state atom (runtime state)
 * This reflects the current state of the connector manager
 */
export const connectorStateAtom = atom<ConnectorState>({
  initialized: false,
  activeSourcesCount: 0,
  activeSinksCount: 0,
  totalSourcesCount: 0,
  totalSinksCount: 0,
  healthy: false,
  errors: [],
});

/**
 * Latest telemetry snapshot atom
 * Updated whenever new telemetry arrives from sources
 */
export const telemetrySnapshotAtom = atom<TelemetrySnapshot | null>(null);

/**
 * Connector manager initialization status
 */
export const connectorManagerInitializedAtom = atom<boolean>(false);

/**
 * Connector errors atom (for displaying in UI)
 */
export const connectorErrorsAtom = atom<
  Array<{ timestamp: number; message: string; connectorId?: string }>
>([]);

/**
 * Derived atom: Is connector system active?
 */
export const isConnectorActiveAtom = atom(get => {
  const state = get(connectorStateAtom);
  return state.initialized && state.healthy;
});

/**
 * Derived atom: Total active connectors count
 */
export const activeConnectorsCountAtom = atom(get => {
  const state = get(connectorStateAtom);
  return state.activeSourcesCount + state.activeSinksCount;
});

/**
 * Action atom: Update connector config
 */
export const updateConnectorConfigAtom = atom(
  null,
  (get, set, newConfig: DesktopConnectorConfig) => {
    set(connectorConfigAtom, newConfig);
  }
);

/**
 * Action atom: Update connector state
 */
export const updateConnectorStateAtom = atom(
  null,
  (get, set, newState: Partial<ConnectorState>) => {
    const currentState = get(connectorStateAtom);
    set(connectorStateAtom, { ...currentState, ...newState });
  }
);

/**
 * Action atom: Add connector error
 */
export const addConnectorErrorAtom = atom(
  null,
  (get, set, error: { message: string; connectorId?: string }) => {
    const currentErrors = get(connectorErrorsAtom);
    set(connectorErrorsAtom, [
      ...currentErrors,
      {
        timestamp: Date.now(),
        ...error,
      },
    ]);
  }
);

/**
 * Action atom: Clear connector errors
 */
export const clearConnectorErrorsAtom = atom(null, (get, set) => {
  set(connectorErrorsAtom, []);
});
