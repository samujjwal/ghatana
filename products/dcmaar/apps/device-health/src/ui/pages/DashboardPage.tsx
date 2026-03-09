import { Card, StatusBadge, Skeleton } from '@ghatana/dcmaar-shared-ui-tailwind';
import { ArrowPathIcon, InformationCircleIcon } from '@heroicons/react/20/solid';
import React, { Suspense, useState } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useNavigate } from 'react-router-dom';

import { PluginMetricsPanel } from '../components/plugins/PluginMetricsPanel';
import { useConnectionStatus } from '../hooks/useConnectionStatus';
import { useDomainAnalytics, type TimeFilter } from '../hooks/useDomainAnalytics';
import { usePluginMetrics } from '../hooks/usePluginMetrics';
import { useRecentActivity } from '../hooks/useRecentActivity';
import { useSystemResources } from '../hooks/useSystemResources';

// Error fallback component
const ErrorFallback = ({ error, resetErrorBoundary }: { error: Error; resetErrorBoundary: () => void }) => (
  <div role="alert" className="p-4 bg-red-100 rounded-md">
    <p className="text-red-700 font-medium">Something went wrong:</p>
    <pre className="text-red-600">{error.message}</pre>
    <button 
      onClick={resetErrorBoundary}
      className="mt-2 px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700"
    >
      Try again
    </button>
  </div>
);

// Loading component
const LoadingFallback = () => (
  <div className="space-y-4">
    <Skeleton className="h-32 w-full" />
    <Skeleton className="h-32 w-full" />
  </div>
);



// Card action button component
const CardActionButton: React.FC<{
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  variant?: 'default' | 'primary';
}> = ({ onClick, icon, label, variant = 'default' }) => (
  <button
    onClick={(e) => {
      e.preventDefault();
      e.stopPropagation();
      console.log('[CardActionButton] Button clicked:', label);
      onClick();
    }}
    className={`inline-flex items-center justify-center rounded-md p-1.5 transition-colors ${
      variant === 'primary'
        ? 'text-blue-600 hover:bg-blue-50'
        : 'text-gray-500 hover:bg-gray-100 hover:text-gray-700'
    }`}
    title={label}
    aria-label={label}
    type="button"
  >
    {icon}
  </button>
);

const DashboardContent = () => {
  const navigate = useNavigate();
  const [timeFilter, setTimeFilter] = useState<TimeFilter>('1day');
  
  const { 
    data: connectionStatus, 
    isLoading: isLoadingConnection,
    error: connectionError,
    refetch: refetchConnection 
  } = useConnectionStatus();

  const { 
    data: recentActivity, 
    isLoading: isLoadingActivity,
    error: activityError,
    refetch: refetchActivity 
  } = useRecentActivity();

  const {
    data: systemResources,
    isLoading: isLoadingResources,
    refetch: refetchResources
  } = useSystemResources();

  const {
    data: domainStats,
    isLoading: isLoadingDomains,
    refetch: refetchDomains
  } = useDomainAnalytics(timeFilter);

  const {
    metrics: pluginMetrics,
    isLoading: isLoadingPluginMetrics,
    refetch: refetchPluginMetrics
  } = usePluginMetrics();

  // Generate dynamic connection metrics from real data
  const connectionMetrics = [
    {
      label: 'Connection Latency',
      description: 'Time for messages to reach background service',
      value: `${connectionStatus?.latency || 0}ms`,
      trend: connectionStatus?.latency && connectionStatus.latency < 50 ? 'Excellent' : 'Good',
      trendTone: (connectionStatus?.latency || 0) < 50 ? 'positive' : 'neutral',
      icon: (
        <svg className="h-4 w-4 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
        </svg>
      )
    },
    {
      label: 'Success Rate',
      description: 'Percentage of successfully processed events',
      value: `${connectionStatus?.metrics?.successRate?.toFixed(1) || '100.0'}%`,
      trend: (connectionStatus?.metrics?.successRate || 100) > 95 ? 'Optimal' : 'Below target',
      trendTone: (connectionStatus?.metrics?.successRate || 100) > 95 ? 'positive' : 'warning',
      icon: (
        <svg className="h-4 w-4 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      )
    },
    {
      label: 'Events Captured',
      description: 'Total browser events captured and queued',
      value: (connectionStatus?.metrics?.totalEvents || 0).toLocaleString(),
      trend: `${connectionStatus?.metrics?.errorEvents || 0} errors`,
      trendTone: (connectionStatus?.metrics?.errorEvents || 0) === 0 ? 'positive' : 'warning',
      icon: (
        <svg className="h-4 w-4 text-purple-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      )
    },
    {
      label: 'Queue Size',
      description: 'Number of events waiting to be processed',
      value: String(connectionStatus?.metrics?.queueSize || 0),
      trend: (connectionStatus?.metrics?.queueSize || 0) === 0 ? 'Empty' : 'Processing',
      trendTone: (connectionStatus?.metrics?.queueSize || 0) < 5 ? 'positive' : 'warning',
      icon: (
        <svg className="h-4 w-4 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7h16M4 12h16M4 17h16" />
        </svg>
      )
    },
  ];  if (connectionError || activityError) {
    throw new Error(connectionError?.message || activityError?.message || 'Failed to load dashboard data');
  }

  return (
    <>
      <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-0.5">
          <h2 className="text-lg font-semibold text-gray-900">
            Live Overview
          </h2>
          <p className="text-xs text-gray-600">
            Connection health, throughput, and recent activity
          </p>
        </div>
      </div>

            <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                {/* Connection Status Card */}
                <Card
                    title="Connection Status"
                    description="Current event capture status"
                    className="border border-gray-200 bg-white shadow-sm"
                    headerActions={
                      <>
                        <CardActionButton
                          onClick={refetchConnection}
                          icon={<ArrowPathIcon className="h-4 w-4" />}
                          label="Refresh connection status"
                        />
                        <CardActionButton
                          onClick={() => {
                            console.log('[DASHBOARD] Details button clicked - navigating to /details?type=connection');
                            navigate('/details?type=connection');
                          }}
                          icon={<InformationCircleIcon className="h-4 w-4" />}
                          label="View details"
                          variant="primary"
                        />
                      </>
                    }
                >
                    <div className="space-y-4">
                        <div className="grid gap-4 md:grid-cols-2">
                            <div>
                                <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-500">
                                    Status
                                </p>
                                <div className="mt-2">
                                    {isLoadingConnection ? (
                                        <Skeleton width="w-24" height="h-5" />
                                    ) : connectionStatus ? (
                                        <StatusBadge
                                            status={connectionStatus.isConnected ? 'online' : 'offline'}
                                            pulse={connectionStatus.isConnected}
                                        />
                                    ) : null}
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-500">
                                        Last Active
                                    </p>
                                    <p className="mt-1.5 text-xs font-semibold text-gray-900">
                                        {connectionStatus?.lastConnectionTime || 'Never'}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-500">
                                        Uptime
                                    </p>
                                    <p className="mt-1.5 text-xs font-semibold text-gray-900">
                                        {connectionStatus?.uptime || '0s'}
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* Connection Metrics */}
                        <div className="space-y-2">
                            <h4 className="text-xs font-semibold text-gray-500">PERFORMANCE METRICS</h4>
                            <div className="grid grid-cols-2 gap-2">
                                {connectionMetrics.map((metric) => (
                                    <div 
                                        key={metric.label} 
                                        className="flex items-start space-x-2 rounded-lg border border-gray-200 p-2.5"
                                        title={metric.description}
                                    >
                                        <div className="mt-0.5 rounded-md bg-blue-50 p-1">
                                            {metric.icon}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <p className="text-xs font-medium text-gray-500">{metric.label}</p>
                                            <p className="text-sm font-semibold text-gray-900">{metric.value}</p>
                                            <p className={`text-[10px] ${
                                                metric.trendTone === 'positive' ? 'text-emerald-600' : 
                                                metric.trendTone === 'warning' ? 'text-amber-600' : 
                                                'text-gray-500'
                                            }`}>
                                                {metric.trend}
                                            </p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                </Card>

                {/* System Metrics Card */}
                <Card
                    title="System Resources"
                    description="Current system resource utilization"
                    className="border border-gray-200 bg-white shadow-sm"
                    headerActions={
                      <>
                        <CardActionButton
                          onClick={() => refetchResources()}
                          icon={<ArrowPathIcon className="h-4 w-4" />}
                          label="Refresh system resources"
                        />
                        <CardActionButton
                          onClick={() => {
                            console.log('[DASHBOARD] Details button clicked - navigating to /details?type=system');
                            navigate('/details?type=system');
                          }}
                          icon={<InformationCircleIcon className="h-4 w-4" />}
                          label="View details"
                          variant="primary"
                        />
                      </>
                    }
                >
                    <div className="space-y-3">
                        <div>
                            <h4 className="text-xs font-semibold text-gray-500">SYSTEM METRICS</h4>
                        </div>
                        
                        {isLoadingResources ? (
                            <div className="grid grid-cols-2 gap-3">
                                <Skeleton count={4} height="h-20" />
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 gap-3">
                                {(systemResources || []).map((metric) => (
                                    <div 
                                        key={metric.label}
                                        className="relative overflow-hidden rounded-lg border border-gray-200 p-3 hover:shadow-sm transition-all"
                                    >
                                        <div className="flex items-start space-x-3">
                                            <div className={`mt-0.5 rounded-md p-1.5 ${
                                                metric.status === 'normal' ? 'bg-green-50 text-green-600' :
                                                metric.status === 'warning' ? 'bg-amber-50 text-amber-600' :
                                                'bg-red-50 text-red-600'
                                            }`}>
                                                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    {metric.label === 'CPU Usage' && (
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6h2m7-10h2m2 0h2m-2 12h2m-10 0a7 7 0 110-14 7 7 0 010 14z" />
                                                    )}
                                                    {metric.label === 'Memory Usage' && (
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 5a1 1 0 011-1h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V5z" />
                                                    )}
                                                    {metric.label === 'Active Listeners' && (
                                                        <>
                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                                                        </>
                                                    )}
                                                    {metric.label === 'Storage Used' && (
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4" />
                                                    )}
                                                    {metric.label === 'I/O Operations' && (
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
                                                    )}
                                                    {metric.label === 'Network Usage' && (
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9" />
                                                    )}
                                                </svg>
                                            </div>
                                            <div className="flex-1">
                                                <p className="text-xs font-medium text-gray-500">{metric.label}</p>
                                                <p className="text-base font-semibold text-gray-900">{metric.value}</p>
                                                <p className="text-[10px] text-gray-500">{metric.trend}</p>
                                            </div>
                                        </div>
                                        
                                        {metric.status !== 'normal' && (
                                            <div className={`absolute -right-8 -top-2 px-8 py-1 text-xs font-medium transform rotate-45 translate-x-2 -translate-y-1 ${
                                                metric.status === 'warning' ? 'bg-amber-500 text-white' : 'bg-red-500 text-white'
                                            }`}>
                                                {metric.status === 'warning' ? '!' : '!!'}
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </Card>

                {/* Plugin Monitoring Card */}
                <Card
                    title="Plugin Real-time Monitoring"
                    description="CPU, Memory, and Battery metrics"
                    className="border border-gray-200 bg-white shadow-sm"
                    headerActions={
                      <>
                        <CardActionButton
                          onClick={refetchPluginMetrics}
                          icon={<ArrowPathIcon className="h-4 w-4" />}
                          label="Refresh plugin metrics"
                        />
                        <CardActionButton
                          onClick={() => {
                            console.log('[DASHBOARD] Settings button clicked - navigating to /plugin-settings');
                            navigate('/plugin-settings');
                          }}
                          icon={<InformationCircleIcon className="h-4 w-4" />}
                          label="Configure monitoring"
                          variant="primary"
                        />
                      </>
                    }
                >
                    <PluginMetricsPanel
                        metrics={pluginMetrics}
                        isLoading={isLoadingPluginMetrics}
                        onRefresh={refetchPluginMetrics}
                        onSettings={() => navigate('/plugin-settings')}
                        showAlerts={true}
                    />
                </Card>
            </div>

            {/* Domain Analytics Card */}
            <Card
                title="Domain Usage Analytics"
                description="Top domains visited and time spent"
                className="border border-gray-200 bg-white shadow-sm"
                headerActions={
                  <>
                    <CardActionButton
                      onClick={refetchDomains}
                      icon={<ArrowPathIcon className="h-4 w-4" />}
                      label="Refresh domain analytics"
                    />
                    <CardActionButton
                      onClick={() => {
                        console.log('[DASHBOARD] Details button clicked - navigating to /details?type=domains');
                        navigate('/details?type=domains');
                      }}
                      icon={<InformationCircleIcon className="h-4 w-4" />}
                      label="View details"
                      variant="primary"
                    />
                  </>
                }
            >
                <div className="space-y-3">
                    {/* Time Filter */}
                    <div className="flex items-center justify-between">
                        <h4 className="text-xs font-semibold text-gray-500">TIME RANGE</h4>
                        <select
                            value={timeFilter}
                            onChange={(e) => setTimeFilter(e.target.value as TimeFilter)}
                            className="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option value="1hr">Last Hour</option>
                            <option value="1day">Last 24 Hours</option>
                            <option value="1week">Last Week</option>
                            <option value="1month">Last Month</option>
                        </select>
                    </div>

                    {/* Domain Stats */}
                    {isLoadingDomains ? (
                        <div className="space-y-2">
                            <Skeleton count={5} height="h-12" />
                        </div>
                    ) : domainStats && domainStats.length > 0 ? (
                        <div className="space-y-2">
                            {/* Bar Chart Visualization */}
                            {domainStats.map((stat, idx) => {
                                const maxVisits = domainStats[0].visits;
                                const percentage = (stat.visits / maxVisits) * 100;
                                const barColor = idx === 0 ? 'bg-blue-500' : idx === 1 ? 'bg-blue-400' : 'bg-blue-300';
                                
                                return (
                                    <div key={stat.domain} className="relative">
                                        <div className="flex items-center justify-between mb-1">
                                            <div className="flex items-center gap-2 flex-1 min-w-0">
                                                <span className="text-xs font-medium text-gray-700 truncate" title={stat.domain}>
                                                    {stat.domain}
                                                </span>
                                                {idx < 3 && (
                                                    <span className={`inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium ${
                                                        idx === 0 ? 'bg-yellow-100 text-yellow-800' :
                                                        idx === 1 ? 'bg-gray-100 text-gray-700' :
                                                        'bg-orange-100 text-orange-700'
                                                    }`}>
                                                        #{idx + 1}
                                                    </span>
                                                )}
                                            </div>
                                            <span className="text-xs font-semibold text-gray-900 ml-2">
                                                {stat.visits} {stat.visits === 1 ? 'visit' : 'visits'}
                                            </span>
                                        </div>
                                        <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                                            <div
                                                className={`h-full ${barColor} transition-all duration-300 rounded-full`}
                                                style={{ width: `${percentage}%` }}
                                            />
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <p className="py-6 text-center text-xs text-gray-500">
                            No domain data available for the selected time range
                        </p>
                    )}

                    {/* Summary Stats */}
                    {domainStats && domainStats.length > 0 && (
                        <div className="pt-2 border-t border-gray-200">
                            <div className="grid grid-cols-2 gap-2 text-center">
                                <div className="rounded-lg bg-gray-50 px-3 py-2">
                                    <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Total Domains</p>
                                    <p className="mt-1 text-lg font-semibold text-gray-900">{domainStats.length}</p>
                                </div>
                                <div className="rounded-lg bg-gray-50 px-3 py-2">
                                    <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Total Visits</p>
                                    <p className="mt-1 text-lg font-semibold text-gray-900">
                                        {domainStats.reduce((sum, stat) => sum + stat.visits, 0).toLocaleString()}
                                    </p>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </Card>

            <Card
                title="Recent Activity"
                description="Latest events and actions"
                className="border border-gray-200 bg-white shadow-sm"
                headerActions={
                  <>
                    <CardActionButton
                      onClick={refetchActivity}
                      icon={<ArrowPathIcon className="h-4 w-4" />}
                      label="Refresh recent activity"
                    />
                    <CardActionButton
                      onClick={() => {
                        console.log('[DASHBOARD] Details button clicked - navigating to /details?type=activity');
                        navigate('/details?type=activity');
                      }}
                      icon={<InformationCircleIcon className="h-4 w-4" />}
                      label="View details"
                      variant="primary"
                    />
                  </>
                }
            >
                <div className="space-y-2">
                    {isLoadingActivity ? (
                        <Skeleton count={3} height="h-10" />
                    ) : recentActivity && recentActivity.length > 0 ? (
                        recentActivity.map((activity, idx) => (
                            <div
                                key={idx}
                                className="flex items-start justify-between rounded-lg border border-gray-200 bg-gray-50 px-3 py-2.5 transition hover:border-gray-300 hover:bg-gray-100"
                            >
                                <div className="flex-1 min-w-0">
                                    <p className="text-sm font-medium text-gray-900 truncate">
                                        {activity.type}
                                    </p>
                                    <p className="mt-0.5 text-[10px] text-gray-500">
                                        {activity.timeAgo}
                                    </p>
                                    {activity.details && (
                                        <p className="mt-1.5 text-xs text-gray-600 line-clamp-2">
                                            {activity.details}
                                        </p>
                                    )}
                                </div>
                                <span className="ml-3 flex-shrink-0 inline-flex items-center rounded-md border border-blue-200 bg-blue-50 px-2 py-1 text-[10px] font-medium text-blue-700">
                                    {activity.source}
                                </span>
                            </div>
                        ))
                    ) : (
                        <p className="py-6 text-center text-xs text-gray-500">
                            No recent activity
                        </p>
                    )}
                </div>
            </Card>
        </div>
    </>
  );
};

// Main DashboardPage component with ErrorBoundary and Suspense
export const DashboardPage = () => {
  return (
    <ErrorBoundary 
      FallbackComponent={ErrorFallback}
      onReset={() => window.location.reload()}
    >
      <Suspense fallback={<LoadingFallback />}>
        <DashboardContent />
      </Suspense>
    </ErrorBoundary>
  );
};

export default DashboardPage;
