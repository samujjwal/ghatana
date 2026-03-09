/**
 * Analytics Dashboard Components for Flashit Web App
 * Interactive charts and insights visualization
 *
 * @doc.type component
 * @doc.purpose Analytics dashboard with charts and insights
 * @doc.layer product
 * @doc.pattern ReactComponent
 */

import React, { useState, useEffect } from 'react';
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell
} from 'recharts';

// Types
interface DashboardData {
  dailyData: Array<{
    date: string;
    moments: number;
    productivity: number;
    emotionDiversity: number;
    searches: number;
    sessionTime: number;
  }>;
  insights: Array<{
    id: string;
    type: string;
    category: string;
    title: string;
    description: string;
    confidence: number;
    priority: number;
    actionable: boolean;
    createdAt: string;
  }>;
  sphereActivity: Array<{
    id: string;
    name: string;
    momentCount: number;
    avgImportance: number;
  }>;
  trends: {
    productivity: 'up' | 'down' | 'stable';
    emotion: 'up' | 'down' | 'stable';
  };
  summary: {
    totalMoments: number;
    avgProductivity: number;
    totalSearches: number;
    activeDays: number;
  };
}

// Color schemes
const COLORS = {
  primary: '#3B82F6',
  success: '#10B981',
  warning: '#F59E0B',
  danger: '#EF4444',
  purple: '#8B5CF6',
  pink: '#EC4899',
  teal: '#14B8A6',
  orange: '#F97316',
};

const CHART_COLORS = [COLORS.primary, COLORS.success, COLORS.warning, COLORS.purple, COLORS.pink, COLORS.teal];

/**
 * Main Analytics Dashboard Component
 */
export function AnalyticsDashboard() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedPeriod, setSelectedPeriod] = useState<'week' | 'month'>('month');

  useEffect(() => {
    fetchDashboardData();
  }, [selectedPeriod]);

  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      const response = await fetch(`/api/analytics/dashboard?period=${selectedPeriod}`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('flashit_token')}`,
        },
      });

      if (response.ok) {
        const dashboardData = await response.json();
        setData(dashboardData);
      } else {
        setError('Failed to load analytics data');
      }
    } catch (err) {
      setError('Failed to connect to analytics service');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">Loading your analytics...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="bg-red-50 border border-red-200 rounded-lg p-6 text-center">
          <div className="text-red-500 text-xl mb-2">⚠️</div>
          <p className="text-red-700">{error}</p>
          <button
            onClick={fetchDashboardData}
            className="mt-4 bg-red-500 text-white px-4 py-2 rounded-md hover:bg-red-600 transition-colors"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <p className="text-gray-500">No analytics data available</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">Analytics Dashboard</h1>
              <p className="text-gray-600 mt-1">Insights into your moment capture journey</p>
            </div>
            <div className="flex space-x-2">
              <button
                onClick={() => setSelectedPeriod('week')}
                className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                  selectedPeriod === 'week'
                    ? 'bg-blue-500 text-white'
                    : 'bg-white text-gray-700 hover:bg-gray-50'
                }`}
              >
                Week
              </button>
              <button
                onClick={() => setSelectedPeriod('month')}
                className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                  selectedPeriod === 'month'
                    ? 'bg-blue-500 text-white'
                    : 'bg-white text-gray-700 hover:bg-gray-50'
                }`}
              >
                Month
              </button>
            </div>
          </div>
        </div>

        {/* Summary Cards */}
        <SummaryCards summary={data.summary} trends={data.trends} />

        {/* Main Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
          <ProductivityChart data={data.dailyData} />
          <EmotionChart data={data.dailyData} />
          <ActivityChart data={data.dailyData} />
          <SphereActivityChart sphereData={data.sphereActivity} />
        </div>

        {/* Insights Section */}
        <InsightsSection insights={data.insights} />
      </div>
    </div>
  );
}

/**
 * Summary Cards Component
 */
function SummaryCards({
  summary,
  trends
}: {
  summary: DashboardData['summary'];
  trends: DashboardData['trends'];
}) {
  const cards = [
    {
      title: 'Total Moments',
      value: summary.totalMoments,
      icon: '📝',
      color: 'blue',
    },
    {
      title: 'Avg Productivity',
      value: `${summary.avgProductivity.toFixed(1)}%`,
      icon: '📈',
      color: 'green',
      trend: trends.productivity,
    },
    {
      title: 'Total Searches',
      value: summary.totalSearches,
      icon: '🔍',
      color: 'purple',
    },
    {
      title: 'Active Days',
      value: summary.activeDays,
      icon: '📅',
      color: 'orange',
    },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      {cards.map((card, index) => (
        <div key={index} className="bg-white rounded-lg shadow-sm p-6 border border-gray-100">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">{card.title}</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">{card.value}</p>
            </div>
            <div className="text-2xl">{card.icon}</div>
          </div>
          {card.trend && (
            <div className="mt-2 flex items-center">
              <TrendIndicator trend={card.trend} />
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

/**
 * Trend Indicator Component
 */
function TrendIndicator({ trend }: { trend: 'up' | 'down' | 'stable' }) {
  const icons = {
    up: '↗️',
    down: '↘️',
    stable: '➡️',
  };

  const colors = {
    up: 'text-green-600',
    down: 'text-red-600',
    stable: 'text-gray-600',
  };

  return (
    <span className={`text-sm ${colors[trend]} flex items-center`}>
      <span className="mr-1">{icons[trend]}</span>
      {trend === 'up' ? 'Increasing' : trend === 'down' ? 'Decreasing' : 'Stable'}
    </span>
  );
}

/**
 * Productivity Chart Component
 */
function ProductivityChart({ data }: { data: DashboardData['dailyData'] }) {
  return (
    <div className="bg-white rounded-lg shadow-sm p-6 border border-gray-100">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">Productivity Trend</h3>
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="date"
            tickFormatter={(date) => new Date(date).toLocaleDateString()}
          />
          <YAxis domain={[0, 100]} />
          <Tooltip
            labelFormatter={(date) => new Date(date).toLocaleDateString()}
            formatter={(value: number) => [`${value.toFixed(1)}%`, 'Productivity']}
          />
          <Line
            type="monotone"
            dataKey="productivity"
            stroke={COLORS.primary}
            strokeWidth={2}
            dot={{ fill: COLORS.primary, strokeWidth: 2, r: 4 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

/**
 * Emotion Diversity Chart Component
 */
function EmotionChart({ data }: { data: DashboardData['dailyData'] }) {
  return (
    <div className="bg-white rounded-lg shadow-sm p-6 border border-gray-100">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">Emotional Diversity</h3>
      <ResponsiveContainer width="100%" height={300}>
        <AreaChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="date"
            tickFormatter={(date) => new Date(date).toLocaleDateString()}
          />
          <YAxis domain={[0, 100]} />
          <Tooltip
            labelFormatter={(date) => new Date(date).toLocaleDateString()}
            formatter={(value: number) => [`${value.toFixed(1)}%`, 'Emotion Diversity']}
          />
          <Area
            type="monotone"
            dataKey="emotionDiversity"
            stroke={COLORS.purple}
            fill={COLORS.purple}
            fillOpacity={0.3}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

/**
 * Activity Chart Component
 */
function ActivityChart({ data }: { data: DashboardData['dailyData'] }) {
  return (
    <div className="bg-white rounded-lg shadow-sm p-6 border border-gray-100">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">Daily Activity</h3>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="date"
            tickFormatter={(date) => new Date(date).toLocaleDateString()}
          />
          <YAxis />
          <Tooltip
            labelFormatter={(date) => new Date(date).toLocaleDateString()}
            formatter={(value: number, name: string) => [
              value,
              name === 'moments' ? 'Moments' : 'Searches'
            ]}
          />
          <Bar dataKey="moments" fill={COLORS.success} />
          <Bar dataKey="searches" fill={COLORS.warning} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/**
 * Sphere Activity Chart Component
 */
function SphereActivityChart({ sphereData }: { sphereData: DashboardData['sphereActivity'] }) {
  return (
    <div className="bg-white rounded-lg shadow-sm p-6 border border-gray-100">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">Sphere Activity</h3>
      <ResponsiveContainer width="100%" height={300}>
        <PieChart>
          <Pie
            data={sphereData}
            cx="50%"
            cy="50%"
            labelLine={false}
            label={({ name, momentCount }) => `${name}: ${momentCount}`}
            outerRadius={100}
            fill="#8884d8"
            dataKey="momentCount"
          >
            {sphereData.map((entry, index) => (
              <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
            ))}
          </Pie>
          <Tooltip formatter={(value: number) => [value, 'Moments']} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

/**
 * Insights Section Component
 */
function InsightsSection({ insights }: { insights: DashboardData['insights'] }) {
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  const categories = ['all', ...Array.from(new Set(insights.map(insight => insight.category)))];
  const filteredInsights = selectedCategory === 'all'
    ? insights
    : insights.filter(insight => insight.category === selectedCategory);

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-100">
      <div className="p-6 border-b border-gray-100">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-gray-900">Personal Insights</h3>
          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            className="px-3 py-1 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {categories.map(category => (
              <option key={category} value={category}>
                {category === 'all' ? 'All Categories' : category.charAt(0).toUpperCase() + category.slice(1)}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="p-6">
        {filteredInsights.length === 0 ? (
          <div className="text-center py-8">
            <div className="text-4xl mb-4">🔍</div>
            <h4 className="text-lg font-medium text-gray-900 mb-2">No insights yet</h4>
            <p className="text-gray-600">Keep capturing moments to generate personalized insights!</p>
          </div>
        ) : (
          <div className="space-y-4">
            {filteredInsights.map((insight) => (
              <InsightCard key={insight.id} insight={insight} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Individual Insight Card Component
 */
function InsightCard({ insight }: { insight: DashboardData['insights'][0] }) {
  const [actionTaken, setActionTaken] = useState(false);

  const categoryIcons = {
    productivity: '📈',
    emotional: '💭',
    content: '📝',
    growth: '🌱',
    usage: '📊',
    social: '👥',
  };

  const typeColors = {
    trend: 'bg-blue-50 border-blue-200 text-blue-800',
    pattern: 'bg-purple-50 border-purple-200 text-purple-800',
    anomaly: 'bg-yellow-50 border-yellow-200 text-yellow-800',
    recommendation: 'bg-green-50 border-green-200 text-green-800',
    achievement: 'bg-pink-50 border-pink-200 text-pink-800',
  };

  const handleActionTaken = async () => {
    try {
      const response = await fetch(`/api/analytics/insights/${insight.id}/action`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('flashit_token')}`,
        },
      });

      if (response.ok) {
        setActionTaken(true);
      }
    } catch (error) {
      console.error('Failed to mark action as taken:', error);
    }
  };

  return (
    <div className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow">
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center space-x-3">
          <span className="text-2xl">
            {categoryIcons[insight.category as keyof typeof categoryIcons] || '💡'}
          </span>
          <div>
            <h4 className="font-semibold text-gray-900">{insight.title}</h4>
            <div className="flex items-center space-x-2 mt-1">
              <span className={`px-2 py-1 text-xs font-medium rounded-full border ${
                typeColors[insight.type as keyof typeof typeColors] || 'bg-gray-50 border-gray-200 text-gray-800'
              }`}>
                {insight.type}
              </span>
              <span className="text-sm text-gray-500">
                {insight.confidence}% confidence
              </span>
            </div>
          </div>
        </div>
        <div className="flex items-center space-x-1">
          {Array.from({ length: Math.ceil(insight.priority / 20) }, (_, i) => (
            <span key={i} className="text-yellow-400">⭐</span>
          ))}
        </div>
      </div>

      <p className="text-gray-700 mb-3">{insight.description}</p>

      <div className="flex items-center justify-between">
        <span className="text-sm text-gray-500">
          {new Date(insight.createdAt).toLocaleDateString()}
        </span>
        {insight.actionable && !actionTaken && (
          <button
            onClick={handleActionTaken}
            className="px-3 py-1 bg-blue-500 text-white text-sm rounded-md hover:bg-blue-600 transition-colors"
          >
            Mark as Done
          </button>
        )}
        {actionTaken && (
          <span className="text-sm text-green-600 font-medium">✓ Action taken</span>
        )}
      </div>
    </div>
  );
}

export default AnalyticsDashboard;
