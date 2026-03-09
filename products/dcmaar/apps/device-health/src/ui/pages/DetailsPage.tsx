import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeftIcon } from '@heroicons/react/20/solid';
import { useConnectionStatus } from '../hooks/useConnectionStatus';
import { useSystemResources } from '../hooks/useSystemResources';
import { useRecentActivity } from '../hooks/useRecentActivity';
import { useDomainAnalytics, TimeFilter } from '../hooks/useDomainAnalytics';

type DetailType = 'connection' | 'system' | 'activity' | 'domains';

export const DetailsPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const type = searchParams.get('type') as DetailType || 'connection';
  const [timeFilter, setTimeFilter] = React.useState<TimeFilter>('1day');

  console.log('[DETAILS PAGE] Loaded with type:', type);

  const { data: connectionStatus } = useConnectionStatus();
  const { data: systemResources } = useSystemResources();
  const { data: recentActivity } = useRecentActivity();
  const { data: domainStats } = useDomainAnalytics(timeFilter);

  console.log('[DETAILS PAGE] Data loaded:', {
    connectionStatus,
    systemResources,
    recentActivity,
    domainStats,
  });

  const renderConnectionDetails = () => {
    const connectionMetrics = [
      {
        label: 'Events Captured',
        value: connectionStatus?.metrics?.totalEvents || 0,
        description: 'Total number of events captured in this session',
      },
      {
        label: 'Queue Size',
        value: connectionStatus?.metrics?.queueSize || 0,
        description: 'Number of events waiting to be processed',
      },
      {
        label: 'Success Rate',
        value: `${connectionStatus?.metrics?.successRate || 0}%`,
        description: 'Percentage of successfully processed events',
      },
      {
        label: 'Errors',
        value: connectionStatus?.metrics?.errorEvents || 0,
        description: 'Number of errors encountered',
      },
      {
        label: 'Latency',
        value: `${connectionStatus?.latency || 0}ms`,
        description: 'Average network latency',
      },
      {
        label: 'Uptime',
        value: connectionStatus?.uptime || 'N/A',
        description: 'Time since last restart',
      },
    ];

    return (
      <div className="space-y-6">
        <div>
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Connection Metrics</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {connectionMetrics.map((metric) => (
              <div key={metric.label} className="rounded-lg border border-gray-200 bg-white p-4 hover:shadow-md transition-shadow">
                <p className="text-xs font-medium text-gray-500">{metric.label}</p>
                <p className="text-3xl font-bold text-gray-900 mt-2">{metric.value}</p>
                <p className="text-xs text-gray-600 mt-2">{metric.description}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Connection Status</h3>
          <div className="grid grid-cols-3 gap-6">
            <div>
              <dt className="text-xs font-medium text-gray-500">Status</dt>
              <dd className="mt-1">
                <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                  connectionStatus?.isConnected ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                }`}>
                  {connectionStatus?.isConnected ? 'Connected' : 'Disconnected'}
                </span>
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-gray-500">Latency</dt>
              <dd className="mt-1 text-sm font-semibold text-gray-900">{connectionStatus?.latency || 0}ms</dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-gray-500">Uptime</dt>
              <dd className="mt-1 text-sm font-semibold text-gray-900">{connectionStatus?.uptime || 'N/A'}</dd>
            </div>
          </div>
        </div>
      </div>
    );
  };

  const renderSystemDetails = () => {
    return (
      <div className="space-y-6">
        <div>
          <h3 className="text-sm font-semibold text-gray-700 mb-3">System Resource Usage</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {systemResources?.map((resource) => (
              <div key={resource.label} className="rounded-lg border border-gray-200 bg-white p-4 hover:shadow-md transition-shadow">
                <p className="text-xs font-medium text-gray-500">{resource.label}</p>
                <p className="text-3xl font-bold text-gray-900 mt-2">{resource.value}</p>
                <p className={`text-xs mt-2 ${
                  resource.status === 'critical' ? 'text-red-600' :
                  resource.status === 'warning' ? 'text-amber-600' :
                  'text-emerald-600'
                }`}>
                  {resource.trend}
                </p>
                {resource.percentage !== undefined && (
                  <div className="mt-3">
                    <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                      <div 
                        className={`h-full transition-all ${
                          resource.status === 'critical' ? 'bg-red-500' :
                          resource.status === 'warning' ? 'bg-amber-500' :
                          'bg-emerald-500'
                        }`}
                        style={{ width: `${resource.percentage}%` }}
                      />
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">System Health Summary</h3>
          <div className="space-y-3">
            {(systemResources?.filter(r => r.status !== 'normal').length || 0) === 0 ? (
              <p className="text-sm text-emerald-600">All systems operating normally</p>
            ) : (
              <>
                {(systemResources?.filter(r => r.status === 'critical').length || 0) > 0 && (
                  <div className="flex items-center gap-2 text-red-600">
                    <span className="text-sm font-medium">Critical:</span>
                    <span className="text-sm">
                      {systemResources?.filter(r => r.status === 'critical').map(r => r.label).join(', ')}
                    </span>
                  </div>
                )}
                {(systemResources?.filter(r => r.status === 'warning').length || 0) > 0 && (
                  <div className="flex items-center gap-2 text-amber-600">
                    <span className="text-sm font-medium">Warning:</span>
                    <span className="text-sm">
                      {systemResources?.filter(r => r.status === 'warning').map(r => r.label).join(', ')}
                    </span>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    );
  };

  const renderActivityDetails = () => {
    return (
      <div className="space-y-6">
        <div className="rounded-lg border border-gray-200 bg-white">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-sm font-semibold text-gray-700">Recent Activity Timeline</h3>
            <p className="text-xs text-gray-500 mt-1">All captured events and actions</p>
          </div>
          <div className="p-6">
            {recentActivity && recentActivity.length > 0 ? (
              <div className="space-y-3">
                {recentActivity.map((activity, idx) => (
                  <div
                    key={idx}
                    className="flex items-start gap-4 rounded-lg border border-gray-200 bg-gray-50 p-4 hover:border-gray-300 hover:bg-gray-100 transition-all"
                  >
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-medium text-gray-900">{activity.type}</p>
                        <span className="inline-flex items-center rounded-md border border-blue-200 bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                          {activity.source}
                        </span>
                      </div>
                      <p className="text-xs text-gray-500 mt-1">{activity.timestamp}</p>
                      {activity.details && (
                        <p className="text-xs text-gray-600 mt-2">{activity.details}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-center text-sm text-gray-500 py-8">No recent activity</p>
            )}
          </div>
        </div>
      </div>
    );
  };

  const renderDomainDetails = () => {
    return (
      <div className="space-y-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-sm font-semibold text-gray-700">Top Domains ({timeFilter})</h3>
              <p className="text-xs text-gray-500 mt-1">Websites you've visited most frequently</p>
            </div>
            <select
              value={timeFilter}
              onChange={(e) => setTimeFilter(e.target.value as TimeFilter)}
              className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="1hr">Last Hour</option>
              <option value="1day">Last 24 Hours</option>
              <option value="1week">Last Week</option>
              <option value="1month">Last Month</option>
            </select>
          </div>

          {domainStats && domainStats.length > 0 ? (
            <div className="space-y-3">
              {domainStats.map((stat, idx) => (
                <div
                  key={stat.domain}
                  className="flex items-center justify-between rounded-lg border border-gray-200 bg-gray-50 p-4 hover:border-gray-300 hover:bg-gray-100 transition-all"
                >
                  <div className="flex items-center gap-3">
                    <span className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold ${
                      idx === 0 ? 'bg-yellow-100 text-yellow-800' :
                      idx === 1 ? 'bg-gray-200 text-gray-700' :
                      idx === 2 ? 'bg-orange-100 text-orange-800' :
                      'bg-blue-50 text-blue-700'
                    }`}>
                      {idx + 1}
                    </span>
                    <div>
                      <p className="text-sm font-medium text-gray-900">{stat.domain}</p>
                      <p className="text-xs text-gray-500">
                        Last visited: {new Date(stat.lastVisit).toLocaleString()}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-semibold text-gray-900">{stat.visits} visits</p>
                    <p className="text-xs text-gray-500">{(stat.timeSpent / 60000).toFixed(1)} min</p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-center text-sm text-gray-500 py-8">
              No domain data available for this time period. Visit some websites to see statistics.
            </p>
          )}
        </div>

        {domainStats && domainStats.length > 0 && (
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Statistics</h3>
            <div className="grid grid-cols-3 gap-6">
              <div>
                <dt className="text-xs font-medium text-gray-500">Total Domains</dt>
                <dd className="mt-1 text-2xl font-bold text-gray-900">{domainStats.length}</dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-gray-500">Total Visits</dt>
                <dd className="mt-1 text-2xl font-bold text-gray-900">
                  {domainStats.reduce((sum, stat) => sum + stat.visits, 0)}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-gray-500">Avg Time/Domain</dt>
                <dd className="mt-1 text-2xl font-bold text-gray-900">
                  {(domainStats.reduce((sum, stat) => sum + stat.timeSpent, 0) / domainStats.length / 60000).toFixed(1)} min
                </dd>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  };

  const titles: Record<DetailType, string> = {
    connection: 'Connection Status Details',
    system: 'System Resources Details',
    activity: 'Activity Timeline',
    domains: 'Domain Analytics',
  };

  const descriptions: Record<DetailType, string> = {
    connection: 'Comprehensive view of your connection metrics and performance',
    system: 'Detailed breakdown of system resource utilization',
    activity: 'Complete history of all captured events and actions',
    domains: 'In-depth analysis of your browsing patterns',
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/dashboard')}
          className="flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <ArrowLeftIcon className="h-4 w-4" />
          Back to Dashboard
        </button>
      </div>

      <div>
        <h1 className="text-2xl font-bold text-gray-900">{titles[type]}</h1>
        <p className="text-sm text-gray-600 mt-1">{descriptions[type]}</p>
      </div>

      {/* Content */}
      {type === 'connection' && renderConnectionDetails()}
      {type === 'system' && renderSystemDetails()}
      {type === 'activity' && renderActivityDetails()}
      {type === 'domains' && renderDomainDetails()}
    </div>
  );
};
