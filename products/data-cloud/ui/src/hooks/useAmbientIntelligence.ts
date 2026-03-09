/**
 * useAmbientIntelligence Hook
 *
 * Custom hook for managing ambient intelligence metrics.
 * Aggregates quality, cost, governance, learning, execution, alert, and health metrics.
 *
 * @doc.type hook
 * @doc.purpose Ambient intelligence state and real-time updates
 * @doc.layer frontend
 */

import { useCallback, useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ambientStateAtom,
  qualityMetricsAtom,
  costMetricsAtom,
  governanceMetricsAtom,
  learningMetricsAtom,
  executionMetricsAtom,
  alertMetricsAtom,
  healthMetricsAtom,
  criticalCountAtom,
  warningCountAtom,
  updateAmbientMetricsAtom,
  addAmbientMetricAtom,
  removeAmbientMetricAtom,
  updateConnectionStatusAtom,
  AmbientMetric,
  AmbientMetricType,
} from '../stores/ambient.store';
import { qualityService } from '../api/quality.service';
import { costService } from '../api/cost.service';
import { brainService } from '../api/brain.service';
import { useWebSocketAutoConnect, useWebSocketState } from '../lib/websocket';

/**
 * Transform quality issues to ambient metrics
 */
function transformQualityToAmbient(data: { issues?: { severity: string }[] }): AmbientMetric[] {
  const issues = data?.issues || [];
  const criticalCount = issues.filter((i) => i.severity === 'CRITICAL').length;
  const highCount = issues.filter((i) => i.severity === 'HIGH').length;

  const metrics: AmbientMetric[] = [];

  if (criticalCount > 0) {
    metrics.push({
      id: 'quality-critical',
      type: 'quality',
      severity: 'critical',
      summary: `${criticalCount} critical quality issue${criticalCount > 1 ? 's' : ''}`,
      count: criticalCount,
      timestamp: new Date().toISOString(),
      detailPath: '/data?view=quality',
    });
  }

  if (highCount > 0) {
    metrics.push({
      id: 'quality-high',
      type: 'quality',
      severity: 'warning',
      summary: `${highCount} quality warning${highCount > 1 ? 's' : ''}`,
      count: highCount,
      timestamp: new Date().toISOString(),
      detailPath: '/data?view=quality',
    });
  }

  return metrics;
}

/**
 * Transform cost data to ambient metrics
 */
function transformCostToAmbient(data: { optimizations?: { estimatedSavings: number }[] }): AmbientMetric[] {
  const optimizations = data?.optimizations || [];
  if (optimizations.length === 0) return [];

  const totalSavings = optimizations.reduce(
    (sum, opt) => sum + (opt.estimatedSavings || 0),
    0
  );

  return [
    {
      id: 'cost-optimizations',
      type: 'cost',
      severity: 'info',
      summary: `${optimizations.length} cost optimization${optimizations.length > 1 ? 's' : ''} available${totalSavings > 0 ? ` (~$${totalSavings.toFixed(0)} savings)` : ''}`,
      count: optimizations.length,
      timestamp: new Date().toISOString(),
      detailPath: '/data?view=cost',
    },
  ];
}

/**
 * Transform learning signals to ambient metrics
 */
function transformLearningToAmbient(data: { patterns?: number; signals?: number }): AmbientMetric[] {
  const metrics: AmbientMetric[] = [];

  if (data?.patterns && data.patterns > 0) {
    metrics.push({
      id: 'patterns-learned',
      type: 'pattern',
      severity: 'info',
      summary: `${data.patterns} pattern${data.patterns > 1 ? 's' : ''} learned from your feedback`,
      count: data.patterns,
      timestamp: new Date().toISOString(),
    });
  }

  if (data?.signals && data.signals > 0) {
    metrics.push({
      id: 'learning-pending',
      type: 'learning',
      severity: 'info',
      summary: `${data.signals} learning signal${data.signals > 1 ? 's' : ''} pending`,
      count: data.signals,
      timestamp: new Date().toISOString(),
    });
  }

  return metrics;
}

/**
 * Transform execution status to ambient metrics
 */
function transformExecutionToAmbient(data: { running?: number; failed?: number }): AmbientMetric[] {
  const metrics: AmbientMetric[] = [];

  if (data?.running && data.running > 0) {
    metrics.push({
      id: 'executions-running',
      type: 'execution',
      severity: 'info',
      summary: `${data.running} pipeline${data.running > 1 ? 's' : ''} running`,
      count: data.running,
      timestamp: new Date().toISOString(),
      detailPath: '/pipelines?view=executions',
    });
  }

  if (data?.failed && data.failed > 0) {
    metrics.push({
      id: 'executions-failed',
      type: 'execution',
      severity: 'critical',
      summary: `${data.failed} pipeline${data.failed > 1 ? 's' : ''} failed`,
      count: data.failed,
      timestamp: new Date().toISOString(),
      detailPath: '/pipelines?view=executions&status=failed',
    });
  }

  return metrics;
}

/**
 * Transform system health to ambient metrics
 */
function transformHealthToAmbient(data: { cpu?: number; memory?: number; latency?: number }): AmbientMetric[] {
  const metrics: AmbientMetric[] = [];

  if (data?.cpu && data.cpu >= 90) {
    metrics.push({
      id: 'health-cpu-critical',
      type: 'health',
      severity: 'critical',
      summary: `CPU usage at ${data.cpu}%`,
      count: data.cpu,
      timestamp: new Date().toISOString(),
    });
  } else if (data?.cpu && data.cpu >= 75) {
    metrics.push({
      id: 'health-cpu-warning',
      type: 'health',
      severity: 'warning',
      summary: `CPU usage at ${data.cpu}%`,
      count: data.cpu,
      timestamp: new Date().toISOString(),
    });
  }

  if (data?.memory && data.memory >= 90) {
    metrics.push({
      id: 'health-memory-critical',
      type: 'health',
      severity: 'critical',
      summary: `Memory usage at ${data.memory}%`,
      count: data.memory,
      timestamp: new Date().toISOString(),
    });
  } else if (data?.memory && data.memory >= 80) {
    metrics.push({
      id: 'health-memory-warning',
      type: 'health',
      severity: 'warning',
      summary: `Memory usage at ${data.memory}%`,
      count: data.memory,
      timestamp: new Date().toISOString(),
    });
  }

  if (data?.latency && data.latency >= 100) {
    metrics.push({
      id: 'health-latency-warning',
      type: 'health',
      severity: 'warning',
      summary: `High latency: ${data.latency}ms`,
      count: data.latency,
      timestamp: new Date().toISOString(),
    });
  }

  return metrics;
}

export interface UseAmbientIntelligenceReturn {
  metrics: AmbientMetric[];
  qualityMetrics: AmbientMetric[];
  costMetrics: AmbientMetric[];
  governanceMetrics: AmbientMetric[];
  learningMetrics: AmbientMetric[];
  executionMetrics: AmbientMetric[];
  alertMetrics: AmbientMetric[];
  healthMetrics: AmbientMetric[];
  criticalCount: number;
  warningCount: number;
  isLoading: boolean;
  lastUpdated: string | null;
  connectionStatus: 'connected' | 'disconnected' | 'reconnecting';
  refresh: () => void;
  dismissMetric: (id: string) => void;
}

/**
 * Custom hook for Ambient Intelligence metrics
 */
export function useAmbientIntelligence(): UseAmbientIntelligenceReturn {
  const queryClient = useQueryClient();
  const [state] = useAtom(ambientStateAtom);
  const qualityMetrics = useAtomValue(qualityMetricsAtom);
  const costMetrics = useAtomValue(costMetricsAtom);
  const governanceMetrics = useAtomValue(governanceMetricsAtom);
  const learningMetrics = useAtomValue(learningMetricsAtom);
  const executionMetrics = useAtomValue(executionMetricsAtom);
  const alertMetrics = useAtomValue(alertMetricsAtom);
  const healthMetrics = useAtomValue(healthMetricsAtom);
  const criticalCount = useAtomValue(criticalCountAtom);
  const warningCount = useAtomValue(warningCountAtom);

  const updateMetrics = useSetAtom(updateAmbientMetricsAtom);
  const addMetric = useSetAtom(addAmbientMetricAtom);
  const removeMetric = useSetAtom(removeAmbientMetricAtom);
  const updateConnectionStatus = useSetAtom(updateConnectionStatusAtom);

  // WebSocket connection for real-time updates
  useWebSocketAutoConnect();
  const wsState = useWebSocketState();

  // Update connection status based on WebSocket state
  useEffect(() => {
    if (wsState === 'connected') {
      updateConnectionStatus('connected');
    } else if (wsState === 'connecting' || wsState === 'reconnecting') {
      updateConnectionStatus('reconnecting');
    } else {
      updateConnectionStatus('disconnected');
    }
  }, [wsState, updateConnectionStatus]);

  // Fetch quality metrics
  const { data: qualityData, isLoading: qualityLoading } = useQuery({
    queryKey: ['ambient-quality'],
    queryFn: async () => {
      try {
        const metrics = await qualityService.getQualityMetrics();
        const issues = metrics.flatMap((m) => m.issues || []);
        return { issues };
      } catch {
        return { issues: [] };
      }
    },
    refetchInterval: 60000, // 1 minute
    staleTime: 30000,
  });

  // Fetch cost metrics
  const { data: costData, isLoading: costLoading } = useQuery({
    queryKey: ['ambient-cost'],
    queryFn: async () => {
      try {
        await costService.getCostAnalysis('7d');
        // In real implementation, fetch optimizations
        return { optimizations: [] };
      } catch {
        return { optimizations: [] };
      }
    },
    refetchInterval: 300000, // 5 minutes
    staleTime: 120000,
  });

  // Fetch learning signals
  const { data: learningData, isLoading: learningLoading } = useQuery({
    queryKey: ['ambient-learning'],
    queryFn: async () => {
      try {
        const signals = await brainService.getLearningSignals(10);
        const pendingCount = signals.filter((s) => s.status === 'PENDING').length;
        return { signals: pendingCount, patterns: 0 };
      } catch {
        return { signals: 0, patterns: 0 };
      }
    },
    refetchInterval: 120000, // 2 minutes
    staleTime: 60000,
  });

  // Fetch execution status
  const { data: executionData, isLoading: executionLoading } = useQuery({
    queryKey: ['ambient-executions'],
    queryFn: async () => {
      try {
        const response = await fetch('/api/executions/summary');
        if (!response.ok) {
          return { running: 0, failed: 0 };
        }
        return response.json();
      } catch {
        return { running: 0, failed: 0 };
      }
    },
    refetchInterval: 10000, // 10 seconds for executions
    staleTime: 5000,
  });

  // Fetch system health
  const { data: healthData, isLoading: healthLoading } = useQuery({
    queryKey: ['ambient-health'],
    queryFn: async () => {
      try {
        const response = await fetch('/api/metrics/system');
        if (!response.ok) {
          return { cpu: 0, memory: 0, latency: 0 };
        }
        return response.json();
      } catch {
        return { cpu: 0, memory: 0, latency: 0 };
      }
    },
    refetchInterval: 30000, // 30 seconds for health
    staleTime: 15000,
  });

  // Aggregate all metrics when data changes
  useEffect(() => {
    const allMetrics: AmbientMetric[] = [
      ...transformQualityToAmbient(qualityData || {}),
      ...transformCostToAmbient(costData || {}),
      ...transformLearningToAmbient(learningData || {}),
      ...transformExecutionToAmbient(executionData || {}),
      ...transformHealthToAmbient(healthData || {}),
    ];
    updateMetrics(allMetrics);
  }, [qualityData, costData, learningData, executionData, healthData, updateMetrics]);

  const refresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['ambient-quality'] });
    queryClient.invalidateQueries({ queryKey: ['ambient-cost'] });
    queryClient.invalidateQueries({ queryKey: ['ambient-learning'] });
    queryClient.invalidateQueries({ queryKey: ['ambient-executions'] });
    queryClient.invalidateQueries({ queryKey: ['ambient-health'] });
  }, [queryClient]);

  const dismissMetric = useCallback(
    (id: string) => {
      removeMetric(id);
    },
    [removeMetric]
  );

  return {
    metrics: state.metrics,
    qualityMetrics,
    costMetrics,
    governanceMetrics,
    learningMetrics,
    executionMetrics,
    alertMetrics,
    healthMetrics,
    criticalCount,
    warningCount,
    isLoading: qualityLoading || costLoading || learningLoading || executionLoading || healthLoading,
    lastUpdated: state.lastUpdated,
    connectionStatus: state.connectionStatus,
    refresh,
    dismissMetric,
  };
}

export default useAmbientIntelligence;
