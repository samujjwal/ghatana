/**
 * useAnomalyDetection Hook
 *
 * Custom React hook for managing anomaly detection UI state and operations.
 * Provides access to anomalies, baselines, threat intelligence, and utilities.
 *
 * @doc.type function
 * @doc.purpose React hook for anomaly detection features
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useEffect } from 'react';
import { yappcApi } from '@/lib/api/client';

/**
 * Anomaly data model
 */
export interface Anomaly {
  id: string;
  tenantId: string;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  confidence: number;
  sourceSystem: string;
  affectedComponent: string;
  user?: string;
  ipAddress?: string;
  status: string;
  detectedAt: Date;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Anomaly baseline model
 */
export interface AnomalyBaseline {
  id: string;
  tenantId: string;
  system: string;
  metric: string;
  normalValue: number;
  standardDeviation: number;
  sensitivity: number;
  isActive: boolean;
}

/**
 * Hook state
 */
export interface AnomalyDetectionState {
  anomalies: Anomaly[];
  loading: boolean;
  error: Error | null;
  totalCount: number;
  criticalCount: number;
  highCount: number;
}

/**
 * useAnomalyDetection Hook
 *
 * Manages anomaly detection state and provides query/mutation utilities.
 *
 * @param tenantId - Tenant identifier
 * @returns Hook state and functions
 *
 * @example
 * ```tsx
 * function AnomalyComponent() {
 *   const { anomalies, loading, error, refreshAnomalies } = useAnomalyDetection('tenant-123');
 *
 *   if (loading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *
 *   return (
 *     <div>
 *       <button onClick={() => refreshAnomalies()}>Refresh</button>
 *       {anomalies.map(a => (
 *         <div key={a.id}>{a.type} - {a.severity}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useAnomalyDetection(tenantId: string) {
  const [state, setState] = useState<AnomalyDetectionState>({
    anomalies: [],
    loading: false,
    error: null,
    totalCount: 0,
    criticalCount: 0,
    highCount: 0,
  });

  /**
   * Fetch anomalies from API
   */
  const fetchAnomalies = useCallback(async (
    startDate: Date,
    endDate: Date,
    severity?: string,
    status?: string,
  ) => {
    setState((prev) => ({ ...prev, loading: true, error: null }));
    try {
      // Call GraphQL query or REST API
      const data = await yappcApi.anomalies.query<{ anomalies?: Anomaly[] }>({
          tenantId,
          startDate,
          endDate,
          severity,
          status,
      });

      const anomalies = data.anomalies || [];
      const criticalCount = anomalies.filter(
        (a: Anomaly) => a.severity === 'CRITICAL',
      ).length;
      const highCount = anomalies.filter(
        (a: Anomaly) => a.severity === 'HIGH',
      ).length;

      setState({
        anomalies,
        loading: false,
        error: null,
        totalCount: anomalies.length,
        criticalCount,
        highCount,
      });
    } catch (err) {
      setState((prev) => ({
        ...prev,
        loading: false,
        error: err instanceof Error ? err : new Error('Unknown error'),
      }));
    }
  }, [tenantId]);

  /**
   * Refresh anomalies (last 24 hours)
   */
  const refreshAnomalies = useCallback(async () => {
    const endDate = new Date();
    const startDate = new Date(endDate.getTime() - 24 * 60 * 60 * 1000);
    return fetchAnomalies(startDate, endDate);
  }, [fetchAnomalies]);

  /**
   * Get critical anomalies only
   */
  const getCriticalAnomalies = useCallback(async () => {
    const endDate = new Date();
    const startDate = new Date(endDate.getTime() - 24 * 60 * 60 * 1000);
    return fetchAnomalies(startDate, endDate, 'CRITICAL');
  }, [fetchAnomalies]);

  /**
   * Get anomalies by user
   */
  const getAnomaliesByUser = useCallback(async (userId: string) => {
    setState((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const data = await yappcApi.anomalies.byUser<{ anomalies?: Anomaly[] }>(userId, tenantId);
      setState({
        anomalies: data.anomalies || [],
        loading: false,
        error: null,
        totalCount: data.anomalies?.length || 0,
        criticalCount: data.anomalies?.filter((a: Anomaly) => a.severity === 'CRITICAL').length || 0,
        highCount: data.anomalies?.filter((a: Anomaly) => a.severity === 'HIGH').length || 0,
      });
    } catch (err) {
      setState((prev) => ({
        ...prev,
        loading: false,
        error: err instanceof Error ? err : new Error('Unknown error'),
      }));
    }
  }, [tenantId]);

  /**
   * Update anomaly status
   */
  const updateAnomalyStatus = useCallback(
    async (anomalyId: string, newStatus: string, notes?: string) => {
      try {
        const response = await yappcApi.anomalies.updateStatus<unknown>(anomalyId, tenantId, {
          status: newStatus,
          notes,
        });

        // Update local state
        setState((prev) => ({
          ...prev,
          anomalies: prev.anomalies.map((a) =>
            a.id === anomalyId ? { ...a, status: newStatus } : a,
          ),
        }));

        return response;
      } catch (err) {
        setState((prev) => ({
          ...prev,
          error: err instanceof Error ? err : new Error('Unknown error'),
        }));
        throw err;
      }
    },
    [tenantId],
  );

  /**
   * Create investigation for anomaly
   */
  const createInvestigation = useCallback(
    async (anomalyId: string, assignedTo: string) => {
      try {
        return await yappcApi.anomalies.createInvestigation<unknown>(
          anomalyId,
          tenantId,
          { assignedTo },
        );
      } catch (err) {
        setState((prev) => ({
          ...prev,
          error: err instanceof Error ? err : new Error('Unknown error'),
        }));
        throw err;
      }
    },
    [tenantId],
  );

  /**
   * Get anomaly baselines
   */
  const getBaselines = useCallback(async () => {
    try {
      return await yappcApi.anomalies.baselines<unknown>(tenantId);
    } catch (err) {
      setState((prev) => ({
        ...prev,
        error: err instanceof Error ? err : new Error('Unknown error'),
      }));
      throw err;
    }
  }, [tenantId]);

  /**
   * Get threat intelligence
   */
  const getThreatIntelligence = useCallback(async () => {
    try {
      return await yappcApi.anomalies.threatIntelligence<unknown>(tenantId);
    } catch (err) {
      setState((prev) => ({
        ...prev,
        error: err instanceof Error ? err : new Error('Unknown error'),
      }));
      throw err;
    }
  }, [tenantId]);

  /**
   * Auto-refresh on mount
   */
  useEffect(() => {
    refreshAnomalies();
  }, [refreshAnomalies]);

  return {
    // State
    ...state,

    // Functions
    fetchAnomalies,
    refreshAnomalies,
    getCriticalAnomalies,
    getAnomaliesByUser,
    updateAnomalyStatus,
    createInvestigation,
    getBaselines,
    getThreatIntelligence,

    // Filters
    filterBySeverity: (severity: string) => {
      return state.anomalies.filter((a) => a.severity === severity);
    },
    filterByStatus: (status: string) => {
      return state.anomalies.filter((a) => a.status === status);
    },
    filterByType: (type: string) => {
      return state.anomalies.filter((a) => a.type === type);
    },
  };
}

/**
 * Hook for managing risk scores
 */
export function useRiskScores(tenantId: string) {
  const [riskScores, setRiskScores] = useState<unknown[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchRiskScores = useCallback(async () => {
    setLoading(true);
    try {
      const data = await yappcApi.anomalies.riskScores<unknown[]>(tenantId);
      setRiskScores(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    fetchRiskScores();
  }, [fetchRiskScores]);

  return { riskScores, loading, error, refresh: fetchRiskScores };
}

/**
 * Hook for anomaly detail view
 */
export function useAnomalyDetail(anomalyId: string, tenantId: string) {
  const [anomaly, setAnomaly] = useState<Anomaly | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await yappcApi.anomalies.detail<Anomaly>(anomalyId, tenantId);
      setAnomaly(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [anomalyId, tenantId]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  return { anomaly, loading, error, refresh: fetchDetail };
}
