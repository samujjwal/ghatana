/**
 * Jotai store for data connector state management.
 *
 * Manages data connectors with app-scoped state, supporting CRUD operations,
 * selection, filtering, and sync statistics.
 *
 * @doc.type store
 * @doc.purpose Data connector state management
 * @doc.layer product
 * @doc.pattern State Store
 */

import { atom, Getter, Setter } from "jotai";
import type { DataConnector, SyncStatistics } from "../types";

/**
 * Data connector state container.
 */
type ConnectorState = {
  connectors: DataConnector[];
  selectedConnectorId: string | null;
  isLoading: boolean;
  error: string | null;
  statistics: Record<string, SyncStatistics>;
  testingConnectorId: string | null;
};

const initialState: ConnectorState = {
  connectors: [],
  selectedConnectorId: null,
  isLoading: false,
  error: null,
  statistics: {},
  testingConnectorId: null,
};

/**
 * Core connector atom.
 *
 * Holds all connectors and selection state.
 */
export const dataConnectorAtom = atom<ConnectorState>(initialState);

/**
 * Derived atom: all connectors.
 */
export const allDataConnectorsAtom = atom((get: Getter) =>
  get(dataConnectorAtom).connectors
);

/**
 * Derived atom: selected connector.
 */
export const selectedDataConnectorAtom = atom((get: Getter) => {
  const state = get(dataConnectorAtom);
  return state.connectors.find((c: DataConnector) => c.id === state.selectedConnectorId) ?? null;
});

/**
 * Derived atom: active connectors only.
 */
export const activeConnectorsAtom = atom((get: Getter) =>
  get(allDataConnectorsAtom).filter((c: DataConnector) => c.status === "active")
);

/**
 * Derived atom: connectors grouped by storage profile ID.
 */
export const connectorsByProfileAtom = atom((get: Getter) => {
  const connectors = get(allDataConnectorsAtom);
  return connectors.reduce(
    (acc: Record<string, DataConnector[]>, connector: DataConnector) => {
      if (!acc[connector.storageProfileId]) {
        acc[connector.storageProfileId] = [];
      }
      acc[connector.storageProfileId].push(connector);
      return acc;
    },
    {} as Record<string, DataConnector[]>
  );
});

/**
 * Derived atom: loading state.
 */
export const connectorLoadingAtom = atom(
  (get: Getter) => get(dataConnectorAtom).isLoading
);

/**
 * Derived atom: error state.
 */
export const connectorErrorAtom = atom(
  (get: Getter) => get(dataConnectorAtom).error
);

/**
 * Derived atom: connector being tested.
 */
export const testingConnectorIdAtom = atom(
  (get: Getter) => get(dataConnectorAtom).testingConnectorId
);

/**
 * Derived atom: statistics for selected connector.
 */
export const selectedConnectorStatisticsAtom = atom((get: Getter) => {
  const state = get(dataConnectorAtom);
  if (!state.selectedConnectorId) return null;
  return state.statistics[state.selectedConnectorId] ?? null;
});

/**
 * Action atom: load connectors.
 */
export const loadDataConnectorsAtom = atom(
  null,
  async (get: Getter, set: Setter, connectors: DataConnector[]) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      connectors,
      isLoading: false,
      error: null,
    }));
  }
);

/**
 * Action atom: set loading state.
 */
export const setConnectorLoadingAtom = atom(
  null,
  (get: Getter, set: Setter, isLoading: boolean) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      isLoading,
    }));
  }
);

/**
 * Action atom: set error state.
 */
export const setConnectorErrorAtom = atom(
  null,
  (get: Getter, set: Setter, error: string | null) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      error,
    }));
  }
);

/**
 * Action atom: select a connector.
 */
export const selectDataConnectorAtom = atom(
  null,
  (get: Getter, set: Setter, connectorId: string | null) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      selectedConnectorId: connectorId,
    }));
  }
);

/**
 * Action atom: add a new connector.
 */
export const addDataConnectorAtom = atom(
  null,
  (get: Getter, set: Setter, connector: DataConnector) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      connectors: [...prev.connectors, connector],
    }));
  }
);

/**
 * Action atom: update an existing connector.
 */
export const updateDataConnectorAtom = atom(
  null,
  (get: Getter, set: Setter, connector: DataConnector) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      connectors: prev.connectors.map((c: DataConnector) =>
        c.id === connector.id ? connector : c
      ),
    }));
  }
);

/**
 * Action atom: delete a connector.
 */
export const deleteDataConnectorAtom = atom(
  null,
  (get: Getter, set: Setter, connectorId: string) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      connectors: prev.connectors.filter((c: DataConnector) => c.id !== connectorId),
      selectedConnectorId:
        prev.selectedConnectorId === connectorId
          ? null
          : prev.selectedConnectorId,
    }));
  }
);

/**
 * Action atom: update sync statistics.
 */
export const updateSyncStatisticsAtom = atom(
  null,
  (get: Getter, set: Setter, statistics: SyncStatistics) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      statistics: {
        ...prev.statistics,
        [statistics.connectorId]: statistics,
      },
    }));
  }
);

/**
 * Action atom: set testing connector ID.
 */
export const setTestingConnectorAtom = atom(
  null,
  (get: Getter, set: Setter, connectorId: string | null) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      testingConnectorId: connectorId,
    }));
  }
);

/**
 * Action atom: toggle connector enabled state.
 */
export const toggleConnectorStateAtom = atom(
  null,
  (get: Getter, set: Setter, connectorId: string) => {
    set(dataConnectorAtom, (prev: ConnectorState) => ({
      ...prev,
      connectors: prev.connectors.map((c: DataConnector) =>
        c.id === connectorId ? { ...c, isEnabled: !c.isEnabled } : c
      ),
    }));
  }
);

/**
 * Action atom: reset to initial state.
 */
export const resetConnectorAtom = atom(null, (get: Getter, set: Setter) => {
  set(dataConnectorAtom, initialState);
});
