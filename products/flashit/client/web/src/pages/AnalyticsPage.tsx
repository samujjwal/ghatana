/**
 * Analytics Page
 * Displays meaning metrics, language evolution, and cross-time referencing
 */

import { useState } from 'react';
import {
  useMeaningMetrics,
  useLanguageEvolution,
  useReturnToMeaningRate,
  useCrossTimeReferencing,
} from '../hooks/use-api';
import Layout from '../components/Layout';
import { BarChart3, TrendingUp, GitBranch, Activity, Calendar } from 'lucide-react';

type Tab = 'overview' | 'language' | 'patterns' | 'connections';

export default function AnalyticsPage() {
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [timeWindow, setTimeWindow] = useState('30');

  const { data: metrics, isLoading: metricsLoading } = useMeaningMetrics({
    timeWindowDays: parseInt(timeWindow),
  });
  const { data: evolution, isLoading: evolutionLoading } = useLanguageEvolution({
    timeWindowDays: parseInt(timeWindow),
  });
  const { data: returnRate } = useReturnToMeaningRate({
    timeWindowDays: parseInt(timeWindow),
  });
  const { data: crossTime } = useCrossTimeReferencing({
    timeWindowDays: parseInt(timeWindow),
  });

  const tabs = [
    { id: 'overview' as Tab, label: 'Overview', icon: BarChart3 },
    { id: 'language' as Tab, label: 'Language', icon: TrendingUp },
    { id: 'patterns' as Tab, label: 'Patterns', icon: Activity },
    { id: 'connections' as Tab, label: 'Connections', icon: GitBranch },
  ];

  return (
    <Layout>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Analytics</h1>
            <p className="mt-1 text-sm text-gray-500">
              Insights into your meaning-making patterns and language evolution
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Calendar className="h-4 w-4 text-gray-400" />
            <select
              value={timeWindow}
              onChange={(e) => setTimeWindow(e.target.value)}
              className="border border-gray-300 rounded-md px-3 py-1.5 text-sm"
              aria-label="Time window"
            >
              <option value="7">Last 7 days</option>
              <option value="30">Last 30 days</option>
              <option value="90">Last 90 days</option>
              <option value="365">Last year</option>
            </select>
          </div>
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200">
          <nav className="flex -mb-px space-x-8" aria-label="Analytics tabs">
            {tabs.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => setActiveTab(id)}
                className={`flex items-center gap-2 px-1 py-3 border-b-2 text-sm font-medium ${
                  activeTab === id
                    ? 'border-primary-500 text-primary-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                aria-current={activeTab === id ? 'page' : undefined}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </nav>
        </div>

        {/* Overview Tab */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {metricsLoading ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {[1, 2, 3, 4].map((i) => (
                  <div key={i} className="bg-white rounded-lg border border-gray-200 p-6 animate-pulse">
                    <div className="h-4 bg-gray-200 rounded w-1/2 mb-3"></div>
                    <div className="h-8 bg-gray-200 rounded w-1/3"></div>
                  </div>
                ))}
              </div>
            ) : metrics ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <MetricCard
                  title="Total Moments"
                  value={metrics.totalMoments ?? 0}
                  icon={<BarChart3 className="h-5 w-5 text-blue-500" />}
                />
                <MetricCard
                  title="Meaning Score"
                  value={metrics.meaningScore != null ? `${(metrics.meaningScore * 100).toFixed(0)}%` : 'N/A'}
                  icon={<TrendingUp className="h-5 w-5 text-green-500" />}
                />
                <MetricCard
                  title="Active Spheres"
                  value={metrics.activeSpheres ?? 0}
                  icon={<Activity className="h-5 w-5 text-purple-500" />}
                />
                <MetricCard
                  title="Connections"
                  value={metrics.connectionCount ?? 0}
                  icon={<GitBranch className="h-5 w-5 text-orange-500" />}
                />
              </div>
            ) : (
              <EmptyState message="No metrics available for the selected time window" />
            )}

            {returnRate && (
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-sm font-medium text-gray-900 mb-4">Return-to-Meaning Rate</h3>
                <p className="text-3xl font-bold text-primary-600">
                  {typeof returnRate.rate === 'number' ? `${(returnRate.rate * 100).toFixed(1)}%` : 'N/A'}
                </p>
                <p className="text-sm text-gray-500 mt-1">
                  How often you revisit and build on previous themes
                </p>
              </div>
            )}
          </div>
        )}

        {/* Language Tab */}
        {activeTab === 'language' && (
          <div className="space-y-6">
            {evolutionLoading ? (
              <div className="bg-white rounded-lg border border-gray-200 p-6 animate-pulse">
                <div className="h-4 bg-gray-200 rounded w-1/4 mb-4"></div>
                <div className="h-48 bg-gray-200 rounded"></div>
              </div>
            ) : evolution ? (
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-sm font-medium text-gray-900 mb-4">Language Evolution</h3>
                {evolution.periods && Array.isArray(evolution.periods) ? (
                  <div className="space-y-3">
                    {evolution.periods.map((period: { period: string; vocabularySize?: number; avgSentimentScore?: number; dominantThemes?: string[] }, idx: number) => (
                      <div key={idx} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
                        <span className="text-sm text-gray-600">{period.period}</span>
                        <div className="flex items-center gap-4">
                          {period.vocabularySize != null && (
                            <span className="text-sm text-gray-900">
                              {period.vocabularySize} words
                            </span>
                          )}
                          {period.avgSentimentScore != null && (
                            <span className={`text-sm font-medium ${
                              period.avgSentimentScore > 0 ? 'text-green-600' : period.avgSentimentScore < 0 ? 'text-red-600' : 'text-gray-600'
                            }`}>
                              {period.avgSentimentScore > 0 ? '+' : ''}{period.avgSentimentScore.toFixed(2)}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState message="No language evolution data available" />
                )}
              </div>
            ) : (
              <EmptyState message="No language data available" />
            )}
          </div>
        )}

        {/* Patterns Tab */}
        {activeTab === 'patterns' && (
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <h3 className="text-sm font-medium text-gray-900 mb-4">Recurring Patterns</h3>
            <p className="text-sm text-gray-500">
              Pattern detection analyzes your moments to identify recurring themes,
              emotional cycles, and habitual thought patterns. Capture more moments to
              see richer patterns.
            </p>
            {metrics?.patterns && Array.isArray(metrics.patterns) && metrics.patterns.length > 0 ? (
              <div className="mt-4 space-y-2">
                {metrics.patterns.map((pattern: { name: string; frequency?: number; description?: string }, idx: number) => (
                  <div key={idx} className="p-3 bg-gray-50 rounded-lg">
                    <p className="font-medium text-sm text-gray-900">{pattern.name}</p>
                    {pattern.description && (
                      <p className="text-xs text-gray-500 mt-1">{pattern.description}</p>
                    )}
                    {pattern.frequency != null && (
                      <span className="text-xs text-primary-600 font-medium">
                        Frequency: {pattern.frequency}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState message="No patterns detected yet. Keep capturing moments!" />
            )}
          </div>
        )}

        {/* Connections Tab */}
        {activeTab === 'connections' && (
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <h3 className="text-sm font-medium text-gray-900 mb-4">Cross-Time Connections</h3>
            {crossTime && Array.isArray(crossTime.connections) && crossTime.connections.length > 0 ? (
              <div className="space-y-3">
                {crossTime.connections.map((conn: { sourceId: string; targetId: string; strength?: number; reason?: string }, idx: number) => (
                  <div key={idx} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                    <div>
                      <p className="text-sm font-medium text-gray-900">
                        {conn.sourceId.slice(0, 8)}... → {conn.targetId.slice(0, 8)}...
                      </p>
                      {conn.reason && (
                        <p className="text-xs text-gray-500 mt-1">{conn.reason}</p>
                      )}
                    </div>
                    {conn.strength != null && (
                      <div className="flex items-center gap-2">
                        <div className="w-20 h-2 bg-gray-200 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary-500 rounded-full"
                            style={{ width: `${conn.strength * 100}%` }}
                          ></div>
                        </div>
                        <span className="text-xs text-gray-500">{(conn.strength * 100).toFixed(0)}%</span>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState message="No cross-time connections found yet" />
            )}
          </div>
        )}
      </div>
    </Layout>
  );
}

function MetricCard({ title, value, icon }: { title: string; value: string | number; icon: React.ReactNode }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm text-gray-500">{title}</span>
        {icon}
      </div>
      <p className="text-2xl font-bold text-gray-900">{value}</p>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="text-center py-8">
      <BarChart3 className="h-10 w-10 text-gray-300 mx-auto" />
      <p className="mt-3 text-sm text-gray-500">{message}</p>
    </div>
  );
}
