import React, { useState, useEffect } from 'react';
import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

/**
 * Runtime health status types.
 */
export type HealthStatus = 'healthy' | 'degraded' | 'blocked' | 'failed' | 'unknown';

/**
 * Health check data structure.
 */
export interface HealthCheck {
  id: string;
  name: string;
  status: HealthStatus;
  message: string;
  timestamp: string;
  duration?: number;
  deploymentId?: string;
  environment?: string;
}

/**
 * Runtime health visualization panel.
 *
 * @doc.type component
 * @doc.purpose Runtime health visualization for Studio mode
 * @doc.layer ui
 */
export interface RuntimeHealthPanelProps {
  healthChecks?: HealthCheck[];
  onRefresh?: () => void;
  isLoading?: boolean;
  className?: string;
}

export function RuntimeHealthPanel({
  healthChecks = [],
  onRefresh,
  isLoading = false,
  className,
}: RuntimeHealthPanelProps) {
  const { t } = useTranslation('studio');
  const [selectedCheck, setSelectedCheck] = useState<HealthCheck | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(true);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    if (!autoRefresh || !onRefresh) return;

    const interval = setInterval(() => {
      onRefresh();
    }, 30000);

    return () => clearInterval(interval);
  }, [autoRefresh, onRefresh]);

  const getStatusColor = (status: HealthStatus): string => {
    switch (status) {
      case 'healthy':
        return 'text-success-600 dark:text-success-400';
      case 'degraded':
        return 'text-warning-600 dark:text-warning-400';
      case 'blocked':
        return 'text-info-600 dark:text-info-400';
      case 'failed':
        return 'text-danger-600 dark:text-danger-400';
      default:
        return 'text-fg-muted dark:text-fg-muted';
    }
  };

  const getStatusBgColor = (status: HealthStatus): string => {
    switch (status) {
      case 'healthy':
        return 'bg-success-100 dark:bg-success-900/20';
      case 'degraded':
        return 'bg-warning-100 dark:bg-warning-900/20';
      case 'blocked':
        return 'bg-info-100 dark:bg-info-900/20';
      case 'failed':
        return 'bg-danger-100 dark:bg-danger-900/20';
      default:
        return 'bg-surface-100 dark:bg-surface-800';
    }
  };

  const getStatusIcon = (status: HealthStatus): string => {
    switch (status) {
      case 'healthy':
        return '✓';
      case 'degraded':
        return '⚠';
      case 'blocked':
        return '⏸';
      case 'failed':
        return '✗';
      default:
        return '?';
    }
  };

  const overallStatus = healthChecks.length > 0
    ? healthChecks.some(check => check.status === 'failed')
      ? 'failed'
      : healthChecks.some(check => check.status === 'blocked')
      ? 'blocked'
      : healthChecks.some(check => check.status === 'degraded')
      ? 'degraded'
      : 'healthy'
    : 'unknown';

  const formatTimestamp = (timestamp: string): string => {
    try {
      return new Date(timestamp).toLocaleString();
    } catch {
      return timestamp;
    }
  };

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b border-border dark:border-border">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-medium text-fg dark:text-fg">
            {t('runtimeHealth.title')}
          </h3>
          <div className={cn(
            'px-2 py-1 rounded-full text-xs font-medium',
            getStatusBgColor(overallStatus),
            getStatusColor(overallStatus)
          )}>
            {getStatusIcon(overallStatus as HealthStatus)} {t(`runtimeHealth.status.${overallStatus}`)}
          </div>
        </div>
        <div className="flex items-center gap-1">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={cn(
              'text-xs',
              autoRefresh ? 'text-info-600 dark:text-info-400' : 'text-fg-muted'
            )}
            aria-label={autoRefresh ? t('runtimeHealth.disableAutoRefresh') : t('runtimeHealth.enableAutoRefresh')}
          >
            {autoRefresh ? '🔄' : '⏸'}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onRefresh}
            disabled={isLoading}
            className="text-xs text-fg-muted hover:text-fg"
            aria-label={t('runtimeHealth.refresh')}
          >
            {isLoading ? '⏳' : '🔄'}
          </Button>
        </div>
      </div>

      {/* Health Checks List */}
      <div className="flex-1 overflow-auto p-3">
        {healthChecks.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-fg-muted dark:text-fg-muted">
            <div className="text-4xl mb-2">💚</div>
            <p className="text-sm text-center">
              {isLoading ? t('runtimeHealth.loading') : t('runtimeHealth.noChecks')}
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {healthChecks.map((check) => (
              <div
                key={check.id}
                className={cn(
                  'p-3 rounded-lg border cursor-pointer transition-all',
                  'border-border dark:border-border',
                  'hover:border-info-300 dark:hover:border-info-600',
                  selectedCheck?.id === check.id && 'border-info-500 dark:border-info-400',
                  getStatusBgColor(check.status)
                )}
                onClick={() => setSelectedCheck(check)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    setSelectedCheck(check);
                  }
                }}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className={cn('text-sm font-medium', getStatusColor(check.status))}>
                        {getStatusIcon(check.status)}
                      </span>
                      <h4 className="text-sm font-medium text-fg dark:text-fg truncate">
                        {check.name}
                      </h4>
                    </div>
                    <p className="text-xs text-fg-muted dark:text-fg-muted mt-1 truncate">
                      {check.message}
                    </p>
                    <div className="flex items-center gap-4 mt-2 text-xs text-fg-muted dark:text-fg-muted">
                      <span>{formatTimestamp(check.timestamp)}</span>
                      {check.duration && (
                        <span>{t('runtimeHealth.duration', { ms: check.duration })}</span>
                      )}
                      {check.environment && (
                        <span className="px-1 py-0.5 bg-surface-200 dark:bg-surface-700 rounded">
                          {check.environment}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Selected Check Details */}
      {selectedCheck && (
        <div className="border-t border-border dark:border-border p-3">
          <div className="flex items-center justify-between mb-2">
            <h4 className="text-sm font-medium text-fg dark:text-fg">
              {t('runtimeHealth.details')}
            </h4>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setSelectedCheck(null)}
              className="text-xs text-fg-muted hover:text-fg"
              aria-label={t('runtimeHealth.closeDetails')}
            >
              ✕
            </Button>
          </div>
          <div className="space-y-2 text-xs">
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('runtimeHealth.checkId')}:</span>
              <span className="font-mono text-fg dark:text-fg">{selectedCheck.id}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('runtimeHealth.status')}:</span>
              <span className={cn('font-medium', getStatusColor(selectedCheck.status))}>
                {t(`runtimeHealth.status.${selectedCheck.status}`)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('runtimeHealth.timestamp')}:</span>
              <span className="text-fg dark:text-fg">{formatTimestamp(selectedCheck.timestamp)}</span>
            </div>
            {selectedCheck.duration && (
              <div className="flex justify-between">
                <span className="text-fg-muted dark:text-fg-muted">{t('runtimeHealth.duration')}:</span>
                <span className="text-fg dark:text-fg">{selectedCheck.duration}ms</span>
              </div>
            )}
            {selectedCheck.deploymentId && (
              <div className="flex justify-between">
                <span className="text-fg-muted dark:text-fg-muted">{t('runtimeHealth.deploymentId')}:</span>
                <span className="font-mono text-fg dark:text-fg">{selectedCheck.deploymentId}</span>
              </div>
            )}
            {selectedCheck.environment && (
              <div className="flex justify-between">
                <span className="text-fg-muted dark:text-fg-muted">{t('runtimeHealth.environment')}:</span>
                <span className="text-fg dark:text-fg">{selectedCheck.environment}</span>
              </div>
            )}
            <div className="pt-2 border-t border-border dark:border-border">
              <span className="text-fg-muted dark:text-fg-muted">{t('runtimeHealth.message')}:</span>
              <p className="mt-1 text-fg dark:text-fg break-words">{selectedCheck.message}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Hook for fetching runtime health data.
 *
 * @doc.type hook
 * @doc.purpose Runtime health data fetching
 */
export function useRuntimeHealth(productUnitId?: string) {
  const [healthChecks, setHealthChecks] = useState<HealthCheck[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchHealthChecks = React.useCallback(async () => {
    if (!productUnitId) return;

    setIsLoading(true);
    setError(null);

    try {
      // This would integrate with the kernel health provider
      // For now, we'll simulate with mock data
      const mockHealthChecks: HealthCheck[] = [
        {
          id: 'health-1',
          name: 'API Gateway',
          status: 'healthy',
          message: 'All endpoints responding normally',
          timestamp: new Date().toISOString(),
          duration: 45,
          environment: 'production'
        },
        {
          id: 'health-2',
          name: 'Database Connection',
          status: 'healthy',
          message: 'Database connection pool healthy',
          timestamp: new Date(Date.now() - 30000).toISOString(),
          duration: 12,
          environment: 'production'
        },
        {
          id: 'health-3',
          name: 'Message Queue',
          status: 'degraded',
          message: 'High latency detected in message processing',
          timestamp: new Date(Date.now() - 60000).toISOString(),
          duration: 234,
          environment: 'production'
        }
      ];

      setHealthChecks(mockHealthChecks);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch health checks');
    } finally {
      setIsLoading(false);
    }
  }, [productUnitId]);

  useEffect(() => {
    fetchHealthChecks();
  }, [fetchHealthChecks]);

  return {
    healthChecks,
    isLoading,
    error,
    refetch: fetchHealthChecks,
  };
}
