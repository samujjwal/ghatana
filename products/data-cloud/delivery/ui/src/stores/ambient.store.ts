/**
 * Ambient Intelligence Store
 *
 * Jotai-based state management for ambient metrics displayed across the UI.
 * Aggregates quality, cost, governance, learning, execution, and alert metrics for the Ambient Bar.
 *
 * @doc.type store
 * @doc.purpose State management for ambient intelligence metrics
 * @doc.layer frontend
 */

import { atom } from 'jotai';

/**
 * Types for ambient intelligence metrics
 */
export type AmbientMetricType =
  | 'quality'
  | 'cost'
  | 'governance'
  | 'pattern'
  | 'learning'
  | 'execution'  // Pipeline execution status
  | 'alert'      // System alerts
  | 'health';    // System health

export type AmbientSeverity = 'info' | 'warning' | 'critical';

export interface AmbientMetric {
  id: string;
  type: AmbientMetricType;
  severity: AmbientSeverity;
  summary: string;
  count?: number;
  timestamp: string;
  detailPath?: string;
  metadata?: Record<string, unknown>;
}

export interface AmbientState {
  metrics: AmbientMetric[];
  isLoading: boolean;
  lastUpdated: string | null;
  connectionStatus: 'connected' | 'disconnected' | 'reconnecting';
}

/**
 * Initial state for ambient intelligence
 */
const initialAmbientState: AmbientState = {
  metrics: [],
  isLoading: false,
  lastUpdated: null,
  connectionStatus: 'disconnected',
};

/**
 * Base atom for ambient state
 */
export const ambientStateAtom = atom<AmbientState>(initialAmbientState);

/**
 * Derived atom for quality metrics only
 */
export const qualityMetricsAtom = atom((get) =>
  get(ambientStateAtom).metrics.filter((m) => m.type === 'quality')
);

/**
 * Derived atom for cost metrics only
 */
export const costMetricsAtom = atom((get) =>
  get(ambientStateAtom).metrics.filter((m) => m.type === 'cost')
);

/**
 * Derived atom for governance metrics only
 */
export const governanceMetricsAtom = atom((get) =>
  get(ambientStateAtom).metrics.filter((m) => m.type === 'governance')
);

/**
 * Derived atom for pattern/learning metrics
 */
export const learningMetricsAtom = atom((get) =>
  get(ambientStateAtom).metrics.filter((m) => m.type === 'learning' || m.type === 'pattern')
);

/**
 * Derived atom for execution metrics
 */
export const executionMetricsAtom = atom((get) =>
  get(ambientStateAtom).metrics.filter((m) => m.type === 'execution')
);

/**
 * Derived atom for alert metrics
 */
export const alertMetricsAtom = atom((get) =>
  get(ambientStateAtom).metrics.filter((m) => m.type === 'alert')
);

/**
 * Derived atom for health metrics
 */
export const healthMetricsAtom = atom((get) =>
  get(ambientStateAtom).metrics.filter((m) => m.type === 'health')
);

/**
 * Derived atom for critical alerts count
 */
export const criticalCountAtom = atom(
  (get) => get(ambientStateAtom).metrics.filter((m) => m.severity === 'critical').length
);

/**
 * Derived atom for warning count
 */
export const warningCountAtom = atom(
  (get) => get(ambientStateAtom).metrics.filter((m) => m.severity === 'warning').length
);

/**
 * Atom to update ambient metrics
 */
export const updateAmbientMetricsAtom = atom(
  null,
  (get, set, metrics: AmbientMetric[]) => {
    set(ambientStateAtom, {
      ...get(ambientStateAtom),
      metrics,
      lastUpdated: new Date().toISOString(),
    });
  }
);

/**
 * Atom to add a single metric
 */
export const addAmbientMetricAtom = atom(
  null,
  (get, set, metric: AmbientMetric) => {
    const current = get(ambientStateAtom);
    // Avoid duplicates by id
    const filtered = current.metrics.filter((m) => m.id !== metric.id);
    set(ambientStateAtom, {
      ...current,
      metrics: [...filtered, metric],
      lastUpdated: new Date().toISOString(),
    });
  }
);

/**
 * Atom to remove a metric
 */
export const removeAmbientMetricAtom = atom(
  null,
  (get, set, metricId: string) => {
    const current = get(ambientStateAtom);
    set(ambientStateAtom, {
      ...current,
      metrics: current.metrics.filter((m) => m.id !== metricId),
      lastUpdated: new Date().toISOString(),
    });
  }
);

/**
 * Atom to update connection status
 */
export const updateConnectionStatusAtom = atom(
  null,
  (get, set, status: 'connected' | 'disconnected' | 'reconnecting') => {
    set(ambientStateAtom, {
      ...get(ambientStateAtom),
      connectionStatus: status,
    });
  }
);

/**
 * Atom to set loading state
 */
export const setAmbientLoadingAtom = atom(
  null,
  (get, set, isLoading: boolean) => {
    set(ambientStateAtom, {
      ...get(ambientStateAtom),
      isLoading,
    });
  }
);

