/**
 * Anomaly Alerts Hook
 *
 * React hook for real-time anomaly detection and alerting.
 * Monitors metrics and triggers alerts when anomalies are detected.
 *
 * @module ai/hooks/useAnomalyAlerts
 * @doc.type hook
 * @doc.purpose Real-time anomaly detection and alerts
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import {
  AIAgentClientFactory,
  type AnomalyInput,
  type AnomalyOutput,
} from '../agents';

/**
 * Anomaly severity levels
 */
export type AnomalySeverity = 'critical' | 'high' | 'medium' | 'low';

/**
 * Anomaly types
 */
export type AnomalyType =
  | 'spike'
  | 'drop'
  | 'trend_change'
  | 'outlier'
  | 'pattern_break'
  | 'threshold_breach';

/**
 * Single anomaly alert
 */
export interface AnomalyAlert {
  id: string;
  metricName: string;
  type: AnomalyType;
  severity: AnomalySeverity;
  title: string;
  description: string;
  score: number;
  currentValue: number;
  expectedValue: number;
  deviation: number;
  detectedAt: Date;
  acknowledgedAt?: Date;
  resolvedAt?: Date;
  metadata: Record<string, unknown>;
}

/**
 * Alert statistics
 */
export interface AlertStats {
  total: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
  acknowledged: number;
  unacknowledged: number;
}

/**
 * Hook options
 */
export interface UseAnomalyAlertsOptions {
  /**
   * Workspace ID
   */
  workspaceId: string;

  /**
   * Metric names to monitor
   */
  metrics?: string[];

  /**
   * Detection sensitivity (0-1)
   * @default 0.8
   */
  sensitivity?: number;

  /**
   * Polling interval in milliseconds
   * @default 30000 (30 seconds)
   */
  pollInterval?: number;

  /**
   * API base URL
   * @default 'http://localhost:8080'
   */
  baseUrl?: string;

  /**
   * Enable notifications
   * @default true
   */
  enableNotifications?: boolean;

  /**
   * Callback when new alert is detected
   */
  onAlert?: (alert: AnomalyAlert) => void;
}

/**
 * Hook return type
 */
export interface UseAnomalyAlertsReturn {
  /**
   * All active alerts
   */
  alerts: AnomalyAlert[];

  /**
   * Critical alerts that need immediate attention
   */
  criticalAlerts: AnomalyAlert[];

  /**
   * Alert statistics
   */
  stats: AlertStats;

  /**
   * Whether monitoring is active
   */
  isMonitoring: boolean;

  /**
   * Whether alerts are loading
   */
  isLoading: boolean;

  /**
   * Error message if any
   */
  error: string | null;

  /**
   * Start monitoring
   */
  startMonitoring: () => void;

  /**
   * Stop monitoring
   */
  stopMonitoring: () => void;

  /**
   * Acknowledge an alert
   */
  acknowledgeAlert: (alertId: string) => void;

  /**
   * Resolve an alert
   */
  resolveAlert: (alertId: string) => void;

  /**
   * Dismiss an alert
   */
  dismissAlert: (alertId: string) => void;

  /**
   * Check specific metric for anomalies
   */
  checkMetric: (
    metricName: string,
    currentValue: number,
    historicalValues: number[]
  ) => Promise<AnomalyAlert | null>;
}

/**
 * Hook for anomaly detection and alerting
 *
 * @example
 * ```tsx
 * function MonitoringDashboard({ workspaceId }: { workspaceId: string }) {
 *   const {
 *     alerts,
 *     criticalAlerts,
 *     stats,
 *     acknowledgeAlert,
 *   } = useAnomalyAlerts({
 *     workspaceId,
 *     metrics: ['velocity', 'throughput', 'cycle_time'],
 *     onAlert: (alert) => toast.error(alert.title),
 *   });
 *
 *   return (
 *     <div>
 *       <StatsBar stats={stats} />
 *       {criticalAlerts.map(alert => (
 *         <CriticalAlertBanner
 *           key={alert.id}
 *           alert={alert}
 *           onAcknowledge={() => acknowledgeAlert(alert.id)}
 *         />
 *       ))}
 *       <AlertList alerts={alerts} />
 *     </div>
 *   );
 * }
 * ```
 */
export function useAnomalyAlerts(
  options: UseAnomalyAlertsOptions
): UseAnomalyAlertsReturn {
  const {
    workspaceId,
    metrics = ['velocity', 'throughput', 'cycle_time', 'bug_rate'],
    sensitivity = 0.8,
    pollInterval = 30000,
    baseUrl = import.meta.env.DEV
      ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
      : '',
    enableNotifications = true,
    onAlert,
  } = options;

  const [alerts, setAlerts] = useState<AnomalyAlert[]>([]);
  const [isMonitoring, setIsMonitoring] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const factoryRef = useRef<AIAgentClientFactory | null>(null);
  const pollingRef = useRef<NodeJS.Timeout | null>(null);
  const alertIdsRef = useRef<Set<string>>(new Set());

  // Initialize factory
  useEffect(() => {
    factoryRef.current = new AIAgentClientFactory({ baseUrl });
  }, [baseUrl]);

  // Check for anomalies
  const checkAnomalies = useCallback(async () => {
    if (!factoryRef.current || !isMonitoring) return;

    setIsLoading(true);
    setError(null);

    try {
      const client = factoryRef.current.createAnomalyDetectorClient();
      const newAlerts: AnomalyAlert[] = [];

      for (const metricName of metrics) {
        // In real implementation, get actual values from metrics service
        const currentValue = Math.random() * 100;
        const historicalValues = Array.from(
          { length: 30 },
          () => Math.random() * 100
        );

        const input: AnomalyInput = {
          workspaceId,
          metricName,
          currentValue,
          historicalValues,
          sensitivity,
        };

        const result = await client.execute(input, {
          userId: 'system',
          workspaceId,
          traceId: generateId(),
          spanId: generateId().substring(0, 16),
        });

        if (result.success && result.data?.anomalies) {
          for (const anomaly of result.data.anomalies) {
            const alertId = `${metricName}-${anomaly.type}-${anomaly.timestamp}`;

            // Skip if already processed
            if (alertIdsRef.current.has(alertId)) continue;
            alertIdsRef.current.add(alertId);

            const alert: AnomalyAlert = {
              id: alertId,
              metricName,
              type: anomaly.type as AnomalyType,
              severity: anomaly.severity as AnomalySeverity,
              title: `${metricName}: ${anomaly.type} detected`,
              description: anomaly.description,
              score: anomaly.score,
              currentValue: anomaly.value,
              expectedValue: anomaly.expectedValue,
              deviation: anomaly.deviation,
              detectedAt: new Date(anomaly.timestamp),
              metadata: anomaly.context || {},
            };

            newAlerts.push(alert);

            // Trigger notification
            if (enableNotifications && onAlert) {
              onAlert(alert);
            }

            // Browser notification for critical alerts
            if (
              enableNotifications &&
              alert.severity === 'critical' &&
              'Notification' in window &&
              Notification.permission === 'granted'
            ) {
              new Notification('Critical Anomaly Detected', {
                body: alert.title,
                icon: '/icons/warning.png',
              });
            }
          }
        }
      }

      if (newAlerts.length > 0) {
        setAlerts((prev) => [...newAlerts, ...prev]);
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to check anomalies'
      );
    } finally {
      setIsLoading(false);
    }
  }, [
    workspaceId,
    metrics,
    sensitivity,
    isMonitoring,
    enableNotifications,
    onAlert,
  ]);

  // Start/stop monitoring
  useEffect(() => {
    if (isMonitoring) {
      checkAnomalies();
      pollingRef.current = setInterval(checkAnomalies, pollInterval);
    }

    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
      }
    };
  }, [checkAnomalies, isMonitoring, pollInterval]);

  // Calculate stats
  const stats: AlertStats = {
    total: alerts.length,
    critical: alerts.filter((a) => a.severity === 'critical').length,
    high: alerts.filter((a) => a.severity === 'high').length,
    medium: alerts.filter((a) => a.severity === 'medium').length,
    low: alerts.filter((a) => a.severity === 'low').length,
    acknowledged: alerts.filter((a) => a.acknowledgedAt).length,
    unacknowledged: alerts.filter((a) => !a.acknowledgedAt).length,
  };

  const criticalAlerts = alerts.filter(
    (a) => (a.severity === 'critical' || a.severity === 'high') && !a.resolvedAt
  );

  const startMonitoring = useCallback(() => {
    setIsMonitoring(true);
  }, []);

  const stopMonitoring = useCallback(() => {
    setIsMonitoring(false);
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
    }
  }, []);

  const acknowledgeAlert = useCallback((alertId: string) => {
    setAlerts((prev) =>
      prev.map((a) =>
        a.id === alertId ? { ...a, acknowledgedAt: new Date() } : a
      )
    );
  }, []);

  const resolveAlert = useCallback((alertId: string) => {
    setAlerts((prev) =>
      prev.map((a) => (a.id === alertId ? { ...a, resolvedAt: new Date() } : a))
    );
  }, []);

  const dismissAlert = useCallback((alertId: string) => {
    setAlerts((prev) => prev.filter((a) => a.id !== alertId));
  }, []);

  const checkMetric = useCallback(
    async (
      metricName: string,
      currentValue: number,
      historicalValues: number[]
    ): Promise<AnomalyAlert | null> => {
      if (!factoryRef.current) return null;

      try {
        const client = factoryRef.current.createAnomalyDetectorClient();
        const result = await client.execute(
          {
            workspaceId,
            metricName,
            currentValue,
            historicalValues,
            sensitivity,
          },
          {
            userId: 'current-user',
            workspaceId,
            traceId: generateId(),
            spanId: generateId().substring(0, 16),
          }
        );

        if (result.success && result.data?.anomalies?.length > 0) {
          const anomaly = result.data.anomalies[0];
          return {
            id: generateId(),
            metricName,
            type: anomaly.type as AnomalyType,
            severity: anomaly.severity as AnomalySeverity,
            title: `${metricName}: ${anomaly.type}`,
            description: anomaly.description,
            score: anomaly.score,
            currentValue: anomaly.value,
            expectedValue: anomaly.expectedValue,
            deviation: anomaly.deviation,
            detectedAt: new Date(),
            metadata: {},
          };
        }

        return null;
      } catch {
        return null;
      }
    },
    [workspaceId, sensitivity]
  );

  return {
    alerts,
    criticalAlerts,
    stats,
    isMonitoring,
    isLoading,
    error,
    startMonitoring,
    stopMonitoring,
    acknowledgeAlert,
    resolveAlert,
    dismissAlert,
    checkMetric,
  };
}

// Helper functions
function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}
