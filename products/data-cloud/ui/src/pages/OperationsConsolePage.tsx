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

import React, { useState } from 'react';
import { Link } from 'react-router';
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
  bgStyles,
  buttonStyles,
} from '../lib/theme';
import { RBACGuard } from '../components/security/RBACGuard';

interface HealthStatus {
  component: string;
  status: 'healthy' | 'degraded' | 'unhealthy';
  lastCheck: string;
  uptime: string;
}

interface AlertSummary {
  total: number;
  critical: number;
  warning: number;
  info: number;
}

interface MetricCard {
  title: string;
  value: string;
  change: string;
  trend: 'up' | 'down' | 'stable';
}

export const OperationsConsolePage: React.FC = () => {
  const [refreshing, setRefreshing] = useState(false);

  // Mock data - in production, this would come from real APIs
  const healthStatuses: HealthStatus[] = [
    { component: 'Launcher', status: 'healthy', lastCheck: '2s ago', uptime: '45d 12h' },
    { component: 'API Gateway', status: 'healthy', lastCheck: '5s ago', uptime: '45d 12h' },
    { component: 'Workflow Engine', status: 'healthy', lastCheck: '1s ago', uptime: '45d 11h' },
    { component: 'Agent Registry', status: 'degraded', lastCheck: '10s ago', uptime: '45d 10h' },
    { component: 'Event Stream', status: 'healthy', lastCheck: '3s ago', uptime: '45d 12h' },
  ];

  const alertSummary: AlertSummary = {
    total: 23,
    critical: 3,
    warning: 12,
    info: 8,
  };

  const metrics: MetricCard[] = [
    { title: 'Request Rate', value: '1,234 req/s', change: '+5.2%', trend: 'up' },
    { title: 'Error Rate', value: '0.12%', change: '-0.03%', trend: 'down' },
    { title: 'P95 Latency', value: '245ms', change: '+12ms', trend: 'up' },
    { title: 'Active Workflows', value: '1,892', change: '+45', trend: 'up' },
  ];

  const handleRefresh = async () => {
    setRefreshing(true);
    // In production, fetch real data
    await new Promise((resolve) => setTimeout(resolve, 1000));
    setRefreshing(false);
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
    <RBACGuard requiredRole="ADMIN" fallback={<div>Access denied. Admin role required.</div>}>
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
              disabled={refreshing}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg',
                buttonStyles.primary,
                refreshing && 'opacity-50 cursor-not-allowed'
              )}
            >
              <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} />
              Refresh
            </button>
          </div>

          {/* Quick Metrics */}
          <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {metrics.map((metric) => (
              <div key={metric.title} className={cn(cardStyles.base, 'p-4')}>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600 dark:text-gray-400">
                    {metric.title}
                  </span>
                  <span
                    className={cn(
                      'text-xs font-medium',
                      metric.trend === 'up' && metric.title !== 'Error Rate'
                        ? 'text-green-600 dark:text-green-400'
                        : metric.trend === 'down'
                        ? 'text-green-600 dark:text-green-400'
                        : 'text-gray-600 dark:text-gray-400'
                    )}
                  >
                    {metric.change}
                  </span>
                </div>
                <div className="mt-2 text-2xl font-semibold text-gray-900 dark:text-white">
                  {metric.value}
                </div>
              </div>
            ))}
          </div>

          {/* Main Grid */}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {/* System Health */}
            <div className={cn(cardStyles.base, 'p-6')}>
              <div className="mb-4 flex items-center gap-2">
                <Shield className="h-5 w-5 text-blue-500" />
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                  System Health
                </h2>
              </div>
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
                      <div className="text-xs text-gray-600 dark:text-gray-400">
                        Uptime
                      </div>
                      <div className="text-sm font-medium text-gray-900 dark:text-white">
                        {status.uptime}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
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
                    {alertSummary.critical}
                  </span>
                </div>
                <div className="flex items-center justify-between p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
                  <span className="text-sm text-gray-700 dark:text-gray-300">Warning</span>
                  <span className="text-2xl font-semibold text-yellow-600 dark:text-yellow-400">
                    {alertSummary.warning}
                  </span>
                </div>
                <div className="flex items-center justify-between p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
                  <span className="text-sm text-gray-700 dark:text-gray-300">Info</span>
                  <span className="text-2xl font-semibold text-blue-600 dark:text-blue-400">
                    {alertSummary.info}
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

            {/* Quick Actions */}
            <div className={cn(cardStyles.base, 'p-6')}>
              <div className="mb-4 flex items-center gap-2">
                <Terminal className="h-5 w-5 text-purple-500" />
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                  Quick Actions
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
              </div>
            </div>
          </div>

          {/* Preview Tools Section */}
          <div className="mt-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900 dark:text-white">
              Preview Tools
            </h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <div className={cn(cardStyles.base, 'p-4 border-l-4 border-l-yellow-400')}>
                <div className="flex items-center gap-2 mb-2">
                  <BarChart3 className="h-4 w-4 text-yellow-500" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    Fabric Metrics
                  </span>
                  <span className="ml-auto text-xs px-2 py-1 bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300 rounded">
                    Preview
                  </span>
                </div>
                <p className="text-xs text-gray-600 dark:text-gray-400">
                  Live data-fabric metrics are not yet exposed. Topology layout is available for design review.
                </p>
              </div>
              <div className={cn(cardStyles.base, 'p-4 border-l-4 border-l-orange-400')}>
                <div className="flex items-center gap-2 mb-2">
                  <Terminal className="h-4 w-4 text-orange-500" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    AI Pipeline Builder
                  </span>
                  <span className="ml-auto text-xs px-2 py-1 bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300 rounded">
                    Unavailable
                  </span>
                </div>
                <p className="text-xs text-gray-600 dark:text-gray-400">
                  Natural-language pipeline generation requires backend contract. Use manual editor instead.
                </p>
              </div>
              <div className={cn(cardStyles.base, 'p-4 border-l-4 border-l-gray-400')}>
                <div className="flex items-center gap-2 mb-2">
                  <Settings className="h-4 w-4 text-gray-500" />
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    Dependency Graph
                  </span>
                  <span className="ml-auto text-xs px-2 py-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded">
                    Not in Deployment
                  </span>
                </div>
                <p className="text-xs text-gray-600 dark:text-gray-400">
                  Plugin-to-plugin dependency graph is not published by the launcher API.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </RBACGuard>
  );
};

export default OperationsConsolePage;
