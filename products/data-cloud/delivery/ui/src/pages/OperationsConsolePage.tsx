/**
 * Operations Console Page
 *
 * Consolidated operator-facing console for preview tools, diagnostics,
 * and operational controls. This page provides a single coherent interface
 * for operators to manage Data Cloud operations.
 *
 * Features:
 * - System health monitoring
 * - Alert triage and management
 * - Event stream monitoring
 * - Fabric metrics preview
 * - Plugin health status
 * - Operational diagnostics
 *
 * @doc.type page
 * @doc.purpose Coherent operations console for operator-facing tools
 * @doc.layer frontend
 */

import React from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  Settings,
  Shield,
  Terminal,
  ChevronRight,
  RefreshCw,
  CheckCircle,
  XCircle,
  Clock,
} from 'lucide-react';
import {
  cn,
  cardStyles,
  textStyles,
  buttonStyles,
} from '../lib/theme';
import { RBACGuard } from '../components/security/RBACGuard';
import { useAlertsSummary } from '../api/alerts.service';
import { useSurfaceRegistry, type SurfaceSignal } from '../api/surfaces.service';

interface HealthStatus {
  component: string;
  status: 'healthy' | 'degraded' | 'unhealthy';
  lastCheck: string;
  uptime: string;
}

function mapCapabilitiesToHealth(surfaces: SurfaceSignal[]): HealthStatus[] {
  return surfaces.map((cap) => {
    const status: HealthStatus['status'] =
      cap.status === 'LIVE' ? 'healthy' : cap.status === 'DEGRADED' || cap.status === 'PREVIEW' ? 'degraded' : 'unhealthy';
    return {
      component: cap.label || cap.key,
      status,
      lastCheck: '—',
      uptime: cap.summary || '-',
    };
  });
}

export const OperationsConsolePage: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const {
    data: surfaceRegistry,
    isLoading: capabilitiesLoading,
  } = useSurfaceRegistry();

  const {
    data: alertSummary,
    isLoading: alertsLoading,
  } = useAlertsSummary();

  const healthStatuses = React.useMemo(
    () => (surfaceRegistry ? mapCapabilitiesToHealth(surfaceRegistry.surfaces) : []),
    [surfaceRegistry]
  );

  const isLoading = capabilitiesLoading || alertsLoading;

  const handleRefresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['capability-registry'] });
    void queryClient.invalidateQueries({ queryKey: ['alerts-summary'] });
  };

  const getStatusIcon = (status: HealthStatus['status']) => {
    switch (status) {
      case 'healthy':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'degraded':
        return <Clock className="h-4 w-4 text-yellow-500" />;
      case 'unhealthy':
        return <XCircle className="h-4 w-4 text-red-500" />;
    }
  };

  const getStatusColor = (status: HealthStatus['status']) => {
    switch (status) {
      case 'healthy':
        return 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800';
      case 'degraded':
        return 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800';
      case 'unhealthy':
        return 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800';
    }
  };

  return (
    <RBACGuard permission="ADMIN" fallback={<div>Access denied. Admin role required.</div>}>
      <div className="min-h-screen bg-gray-50 dark:bg-gray-950 p-6">
        <div className="mx-auto max-w-7xl">
          {/* Header */}
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-semibold text-gray-900 dark:text-white">
                Operations Console
              </h1>
              <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
                System health, alerts, and operational diagnostics
              </p>
            </div>
            <button
              onClick={handleRefresh}
              disabled={isLoading}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg',
                buttonStyles.primary,
                isLoading && 'opacity-50 cursor-not-allowed'
              )}
            >
              <RefreshCw className={cn('h-4 w-4', isLoading && 'animate-spin')} />
              Refresh
            </button>
          </div>

          {/* Diagnostics Grid — scoped to real, backed surfaces only */}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {/* System Health */}
            <div className={cn(cardStyles.base, 'p-6')}>
              <div className="mb-4 flex items-center gap-2">
                <Shield className="h-5 w-5 text-blue-500" />
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                  System Health
                </h2>
              </div>
              {isLoading ? (
                <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
                  <RefreshCw className="h-4 w-4 animate-spin" />
                  {t('loading.healthData')}
                </div>
              ) : healthStatuses.length === 0 ? (
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {t('operations.noCapabilitySignals')}
                </p>
              ) : (
                <div className="space-y-3">
                  {healthStatuses.map((status) => (
                    <div
                      key={status.component}
                      className={cn(
                        'flex items-center justify-between p-3 rounded-lg border',
                        getStatusColor(status.status)
                      )}
                    >
                      <div className="flex items-center gap-3">
                        {getStatusIcon(status.status)}
                        <div>
                          <div className="text-sm font-medium text-gray-900 dark:text-white">
                            {status.component}
                          </div>
                          <div className="text-xs text-gray-600 dark:text-gray-400">
                            Last check: {status.lastCheck}
                          </div>
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="text-xs text-gray-600 dark:text-gray-400">Uptime</div>
                        <div className="text-sm font-medium text-gray-900 dark:text-white">
                          {status.uptime}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Alert Summary */}
            <div className={cn(cardStyles.base, 'p-6')}>
              <div className="mb-4 flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-orange-500" />
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                  Alert Summary
                </h2>
              </div>
              <div className="space-y-4">
                <div className="flex items-center justify-between p-3 bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-200 dark:border-red-800">
                  <span className="text-sm text-gray-700 dark:text-gray-300">Critical</span>
                  <span className="text-2xl font-semibold text-red-600 dark:text-red-400">
                    {alertSummary ? (alertSummary.critical ?? 0) : '—'}
                  </span>
                </div>
                <div className="flex items-center justify-between p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
                  <span className="text-sm text-gray-700 dark:text-gray-300">Warning</span>
                  <span className="text-2xl font-semibold text-yellow-600 dark:text-yellow-400">
                    {alertSummary ? (alertSummary.warning ?? 0) : '—'}
                  </span>
                </div>
                <div className="flex items-center justify-between p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
                  <span className="text-sm text-gray-700 dark:text-gray-300">Info</span>
                  <span className="text-2xl font-semibold text-blue-600 dark:text-blue-400">
                    {alertSummary ? (alertSummary.info ?? 0) : '—'}
                  </span>
                </div>
                <div className="pt-3 border-t border-gray-200 dark:border-gray-700">
                  <Link
                    to="/alerts"
                    className="flex items-center justify-center gap-2 w-full py-2 text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg transition-colors"
                  >
                    View All Alerts
                    <ChevronRight className="h-4 w-4" />
                  </Link>
                </div>
              </div>
            </div>

            {/* Operator Navigation — links to canonical first-class routes */}
            <div className={cn(cardStyles.base, 'p-6')}>
              <div className="mb-4 flex items-center gap-2">
                <Terminal className="h-5 w-5 text-purple-500" />
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                  Operator Surfaces
                </h2>
              </div>
              <div className="space-y-3">
                <Link
                  to="/events"
                  className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <Activity className="h-4 w-4 text-gray-600 dark:text-gray-400" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    Event Stream
                  </span>
                  <ChevronRight className="h-4 w-4 ml-auto text-gray-400" />
                </Link>
                <Link
                  to="/trust"
                  className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <Shield className="h-4 w-4 text-gray-600 dark:text-gray-400" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    Trust & Compliance
                  </span>
                  <ChevronRight className="h-4 w-4 ml-auto text-gray-400" />
                </Link>
                <Link
                  to="/insights"
                  className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <BarChart3 className="h-4 w-4 text-gray-600 dark:text-gray-400" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    Insights Dashboard
                  </span>
                  <ChevronRight className="h-4 w-4 ml-auto text-gray-400" />
                </Link>
                <Link
                  to="/pipelines"
                  className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <Settings className="h-4 w-4 text-gray-600 dark:text-gray-400" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    Workflow Designer
                  </span>
                  <ChevronRight className="h-4 w-4 ml-auto text-gray-400" />
                </Link>
                <Link
                  to="/operations/jobs"
                  className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <Clock className="h-4 w-4 text-gray-600 dark:text-gray-400" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    Job Center
                  </span>
                  <ChevronRight className="h-4 w-4 ml-auto text-gray-400" />
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </RBACGuard>
  );
};

export default OperationsConsolePage;
