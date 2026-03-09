/**
 * Analytics Dashboard Component (Web)
 * Comprehensive analytics and insights
 *
 * @doc.type component
 * @doc.purpose Analytics visualization and dashboard
 * @doc.layer product
 * @doc.pattern AnalyticsDashboard
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';

// ============================================================================
// Types
// ============================================================================

interface AnalyticsData {
  timeRange: {
    start: Date;
    end: Date;
  };
  totalMoments: number;
  momentsCreated: number;
  momentsUpdated: number;
  avgMomentsPerDay: number;
  mostActiveDay: string;
  mostActiveHour: number;
  topTags: Array<{ tag: string; count: number }>;
  topCategories: Array<{ category: string; count: number }>;
  moodDistribution: Array<{ mood: string; count: number; percentage: number }>;
  wordCloud: Array<{ word: string; count: number }>;
  timelineData: Array<{ date: string; count: number }>;
  hourlyDistribution: Array<{ hour: number; count: number }>;
  weekdayDistribution: Array<{ day: string; count: number }>;
  engagementScore: number;
  streakDays: number;
  longestStreak: number;
}

type TimeRange = '7d' | '30d' | '90d' | '1y';

// ============================================================================
// API Functions
// ============================================================================

async function fetchAnalytics(
  userId: string,
  timeRange: TimeRange
): Promise<AnalyticsData> {
  const response = await fetch(`/api/analytics/${userId}?range=${timeRange}`);
  const data = await response.json();
  if (!data.success) throw new Error(data.error);
  return data.data;
}

// ============================================================================
// Component
// ============================================================================

interface AnalyticsDashboardProps {
  userId: string;
}

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];

export default function AnalyticsDashboard({ userId }: AnalyticsDashboardProps) {
  const [timeRange, setTimeRange] = useState<TimeRange>('30d');

  const { data: analytics, isLoading } = useQuery({
    queryKey: ['analytics', userId, timeRange],
    queryFn: () => fetchAnalytics(userId, timeRange),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (!analytics) {
    return <div>No analytics data available</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold mb-4">Analytics Dashboard</h1>
          
          {/* Time Range Selector */}
          <div className="flex gap-2">
            <button
              className={`px-4 py-2 rounded ${
                timeRange === '7d' ? 'bg-blue-500 text-white' : 'bg-white border'
              }`}
              onClick={() => setTimeRange('7d')}
            >
              Last 7 Days
            </button>
            <button
              className={`px-4 py-2 rounded ${
                timeRange === '30d' ? 'bg-blue-500 text-white' : 'bg-white border'
              }`}
              onClick={() => setTimeRange('30d')}
            >
              Last 30 Days
            </button>
            <button
              className={`px-4 py-2 rounded ${
                timeRange === '90d' ? 'bg-blue-500 text-white' : 'bg-white border'
              }`}
              onClick={() => setTimeRange('90d')}
            >
              Last 90 Days
            </button>
            <button
              className={`px-4 py-2 rounded ${
                timeRange === '1y' ? 'bg-blue-500 text-white' : 'bg-white border'
              }`}
              onClick={() => setTimeRange('1y')}
            >
              Last Year
            </button>
          </div>
        </div>

        {/* Key Metrics */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="text-sm text-gray-500 mb-1">Total Moments</div>
            <div className="text-3xl font-bold text-blue-600">{analytics.totalMoments}</div>
            <div className="text-sm text-gray-500 mt-2">
              {analytics.momentsCreated} created, {analytics.momentsUpdated} updated
            </div>
          </div>
          
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="text-sm text-gray-500 mb-1">Engagement Score</div>
            <div className="text-3xl font-bold text-green-600">{analytics.engagementScore}/100</div>
            <div className="w-full bg-gray-200 rounded-full h-2 mt-3">
              <div
                className="bg-green-600 h-2 rounded-full"
                style={{ width: `${analytics.engagementScore}%` }}
              />
            </div>
          </div>
          
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="text-sm text-gray-500 mb-1">Current Streak</div>
            <div className="text-3xl font-bold text-orange-600">{analytics.streakDays} days</div>
            <div className="text-sm text-gray-500 mt-2">
              Longest: {analytics.longestStreak} days
            </div>
          </div>
          
          <div className="bg-white p-6 rounded-lg shadow">
            <div className="text-sm text-gray-500 mb-1">Daily Average</div>
            <div className="text-3xl font-bold text-purple-600">
              {analytics.avgMomentsPerDay.toFixed(1)}
            </div>
            <div className="text-sm text-gray-500 mt-2">moments per day</div>
          </div>
        </div>

        {/* Timeline Chart */}
        <div className="bg-white p-6 rounded-lg shadow mb-8">
          <h2 className="text-xl font-bold mb-4">Activity Timeline</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={analytics.timelineData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="date"
                tickFormatter={(date) => new Date(date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
              />
              <YAxis />
              <Tooltip
                labelFormatter={(date) => new Date(date).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
              />
              <Legend />
              <Line type="monotone" dataKey="count" stroke="#3b82f6" strokeWidth={2} name="Moments" />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
          {/* Hourly Distribution */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-bold mb-4">Activity by Hour</h2>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={analytics.hourlyDistribution}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="hour" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="count" fill="#10b981" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Weekday Distribution */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-bold mb-4">Activity by Day</h2>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={analytics.weekdayDistribution}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="day" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="count" fill="#f59e0b" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Top Tags */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-bold mb-4">Top Tags</h2>
            <div className="space-y-3">
              {analytics.topTags.slice(0, 10).map((item, index) => (
                <div key={item.tag}>
                  <div className="flex justify-between mb-1">
                    <span className="text-sm font-medium">{item.tag}</span>
                    <span className="text-sm text-gray-500">{item.count}</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className="h-2 rounded-full"
                      style={{
                        width: `${(item.count / analytics.topTags[0].count) * 100}%`,
                        backgroundColor: COLORS[index % COLORS.length],
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Mood Distribution */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-bold mb-4">Mood Distribution</h2>
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={analytics.moodDistribution}
                  dataKey="count"
                  nameKey="mood"
                  cx="50%"
                  cy="50%"
                  outerRadius={100}
                  label={(entry) => `${entry.mood} (${entry.percentage.toFixed(1)}%)`}
                >
                  {analytics.moodDistribution.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Word Cloud */}
        <div className="bg-white p-6 rounded-lg shadow mb-8">
          <h2 className="text-xl font-bold mb-4">Word Cloud</h2>
          <div className="flex flex-wrap gap-3">
            {analytics.wordCloud.slice(0, 30).map((item, index) => {
              const maxCount = analytics.wordCloud[0].count;
              const size = 14 + (item.count / maxCount) * 32;
              return (
                <span
                  key={item.word}
                  className="font-semibold"
                  style={{
                    fontSize: `${size}px`,
                    color: COLORS[index % COLORS.length],
                  }}
                >
                  {item.word}
                </span>
              );
            })}
          </div>
        </div>

        {/* Insights */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-xl font-bold mb-4">Insights</h2>
          <div className="space-y-3">
            <div className="flex items-start gap-3">
              <span className="text-2xl">🔥</span>
              <div>
                <div className="font-medium">Most Active Day</div>
                <div className="text-gray-600">
                  {new Date(analytics.mostActiveDay).toLocaleDateString('en-US', {
                    weekday: 'long',
                    month: 'long',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </div>
              </div>
            </div>
            <div className="flex items-start gap-3">
              <span className="text-2xl">⏰</span>
              <div>
                <div className="font-medium">Most Active Hour</div>
                <div className="text-gray-600">
                  {analytics.mostActiveHour}:00 - {analytics.mostActiveHour + 1}:00
                </div>
              </div>
            </div>
            <div className="flex items-start gap-3">
              <span className="text-2xl">📊</span>
              <div>
                <div className="font-medium">Engagement Level</div>
                <div className="text-gray-600">
                  {analytics.engagementScore >= 80
                    ? 'Excellent! You\'re very engaged.'
                    : analytics.engagementScore >= 60
                    ? 'Good! Keep it up.'
                    : analytics.engagementScore >= 40
                    ? 'Fair. Try to be more consistent.'
                    : 'You can improve your engagement.'}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
