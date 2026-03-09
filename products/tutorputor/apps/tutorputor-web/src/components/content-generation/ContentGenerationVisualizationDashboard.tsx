/**
 * Content Generation Visualization Dashboard
 *
 * Provides visual components to see the automated content generation
 * system in action with real-time metrics, quality indicators, and interactive demos.
 *
 * @doc.type module
 * @doc.purpose Visual dashboard for content generation system
 * @doc.layer product
 * @doc.pattern Dashboard
 */

import React, { useState, useEffect } from "react";
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
} from "recharts";

/**
 * Content Generation Dashboard Component
 */
export const ContentGenerationVisualizationDashboard: React.FC = () => {
  const [metrics, setMetrics] = useState({
    totalRequests: 0,
    successRate: 0,
    averageTime: 0,
    confidenceScore: 0,
    domainDistribution: [] as any[],
    performanceData: [] as any[],
    qualityMetrics: [] as any[],
  });

  const [activeView, setActiveView] = useState<
    "overview" | "performance" | "quality" | "domains"
  >("overview");

  useEffect(() => {
    // Simulate real-time data updates
    const interval = setInterval(() => {
      setMetrics({
        totalRequests: Math.floor(Math.random() * 1000) + 500,
        successRate: 95 + Math.random() * 4,
        averageTime: 20 + Math.random() * 10,
        confidenceScore: 0.85 + Math.random() * 0.1,
        domainDistribution: [
          {
            domain: "Physics",
            count: Math.floor(Math.random() * 200) + 100,
            successRate: 95 + Math.random() * 4,
          },
          {
            domain: "Chemistry",
            count: Math.floor(Math.random() * 150) + 80,
            successRate: 94 + Math.random() * 5,
          },
          {
            domain: "Biology",
            count: Math.floor(Math.random() * 120) + 60,
            successRate: 96 + Math.random() * 3,
          },
          {
            domain: "CS Discrete",
            count: Math.floor(Math.random() * 180) + 90,
            successRate: 97 + Math.random() * 2,
          },
          {
            domain: "Mathematics",
            count: Math.floor(Math.random() * 100) + 50,
            successRate: 95 + Math.random() * 4,
          },
        ],
        performanceData: Array.from({ length: 24 }, (_, i) => ({
          hour: i,
          requests: Math.floor(Math.random() * 50) + 20,
          successRate: 93 + Math.random() * 6,
          avgTime: 18 + Math.random() * 12,
        })),
        qualityMetrics: [
          { metric: "Schema Validation", value: 98, color: "#4CAF50" },
          { metric: "Grade Appropriateness", value: 94, color: "#2196F3" },
          { metric: "Content Completeness", value: 96, color: "#FF9800" },
          { metric: "Educational Value", value: 92, color: "#9C27B0" },
        ],
      });
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            Content Generation Visualization
          </h1>
          <p className="text-gray-600">
            Real-time monitoring of TutorPutor's automated content generation
            system
          </p>
        </div>

        {/* Key Metrics */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-6">
          <MetricCard
            title="Total Requests"
            value={metrics.totalRequests.toLocaleString()}
            change="+12%"
            changeType="positive"
            icon="📊"
          />
          <MetricCard
            title="Success Rate"
            value={`${metrics.successRate.toFixed(1)}%`}
            change="+2.3%"
            changeType="positive"
            icon="✅"
          />
          <MetricCard
            title="Avg Generation Time"
            value={`${metrics.averageTime.toFixed(1)}s`}
            change="-1.2s"
            changeType="positive"
            icon="⚡"
          />
          <MetricCard
            title="Confidence Score"
            value={(metrics.confidenceScore * 100).toFixed(1)}
            change="+0.8%"
            changeType="positive"
            icon="🎯"
          />
        </div>

        {/* View Selector */}
        <div className="bg-white rounded-lg shadow-md p-4 mb-6">
          <div className="flex space-x-4">
            {(["overview", "performance", "quality", "domains"] as const).map(
              (view) => (
                <button
                  key={view}
                  onClick={() => setActiveView(view)}
                  className={`px-4 py-2 rounded-lg font-medium transition-colors ${
                    activeView === view
                      ? "bg-blue-500 text-white"
                      : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                  }`}
                >
                  {view.charAt(0).toUpperCase() + view.slice(1)}
                </button>
              ),
            )}
          </div>
        </div>

        {/* Content Views */}
        <div className="bg-white rounded-lg shadow-md p-6">
          {activeView === "overview" && <OverviewView metrics={metrics} />}
          {activeView === "performance" && (
            <PerformanceView metrics={metrics} />
          )}
          {activeView === "quality" && <QualityView metrics={metrics} />}
          {activeView === "domains" && <DomainsView metrics={metrics} />}
        </div>
      </div>
    </div>
  );
};

/**
 * Metric Card Component
 */
interface MetricCardProps {
  title: string;
  value: string;
  change: string;
  changeType: "positive" | "negative";
  icon: string;
}

const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  change,
  changeType,
  icon,
}) => (
  <div className="bg-white rounded-lg shadow-md p-6">
    <div className="flex items-center justify-between mb-4">
      <div className="text-2xl">{icon}</div>
      <div
        className={`text-sm font-medium ${
          changeType === "positive" ? "text-green-600" : "text-red-600"
        }`}
      >
        {change}
      </div>
    </div>
    <div className="text-2xl font-bold text-gray-900 mb-1">{value}</div>
    <div className="text-sm text-gray-600">{title}</div>
  </div>
);

/**
 * Overview View Component
 */
interface OverviewViewProps {
  metrics: any;
}

const OverviewView: React.FC<OverviewViewProps> = ({ metrics }) => (
  <div className="space-y-6">
    <h2 className="text-xl font-bold text-gray-900 mb-4">System Overview</h2>

    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      {/* Performance Chart */}
      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Hourly Performance
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={metrics.performanceData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="hour" />
            <YAxis />
            <Tooltip />
            <Legend />
            <Line
              type="monotone"
              dataKey="requests"
              stroke="#8884d8"
              name="Requests"
            />
            <Line
              type="monotone"
              dataKey="successRate"
              stroke="#82ca9d"
              name="Success %"
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* Domain Distribution */}
      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Domain Distribution
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <PieChart>
            <Pie
              data={metrics.domainDistribution}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={({ domain, count }) => `${domain}: ${count}`}
              outerRadius={80}
              fill="#8884d8"
              dataKey="count"
            >
              {metrics.domainDistribution.map((entry: any, index: number) => (
                <Cell
                  key={`cell-${index}`}
                  fill={COLORS[index % COLORS.length]}
                />
              ))}
            </Pie>
            <Tooltip />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>

    {/* Quality Metrics */}
    <div>
      <h3 className="text-lg font-semibold text-gray-800 mb-4">
        Quality Metrics
      </h3>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={metrics.qualityMetrics}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="metric" />
          <YAxis />
          <Tooltip />
          <Bar dataKey="value" fill="#8884d8">
            {metrics.qualityMetrics.map((entry: any, index: number) => (
              <Cell key={`cell-${index}`} fill={entry.color} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  </div>
);

/**
 * Performance View Component
 */
const PerformanceView: React.FC<OverviewViewProps> = ({ metrics }) => (
  <div className="space-y-6">
    <h2 className="text-xl font-bold text-gray-900 mb-4">
      Performance Analysis
    </h2>

    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Response Time Distribution
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={metrics.performanceData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="hour" />
            <YAxis />
            <Tooltip />
            <Legend />
            <Line
              type="monotone"
              dataKey="avgTime"
              stroke="#ff7300"
              name="Avg Time (s)"
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Success Rate Trends
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={metrics.performanceData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="hour" />
            <YAxis />
            <Tooltip />
            <Legend />
            <Line
              type="monotone"
              dataKey="successRate"
              stroke="#00ff00"
              name="Success %"
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>

    {/* Performance Stats */}
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      <div className="bg-blue-50 rounded-lg p-4">
        <div className="text-2xl font-bold text-blue-600">
          {metrics.averageTime.toFixed(1)}s
        </div>
        <div className="text-sm text-gray-600">Average Generation Time</div>
      </div>
      <div className="bg-green-50 rounded-lg p-4">
        <div className="text-2xl font-bold text-green-600">
          {metrics.successRate.toFixed(1)}%
        </div>
        <div className="text-sm text-gray-600">Success Rate</div>
      </div>
      <div className="bg-purple-50 rounded-lg p-4">
        <div className="text-2xl font-bold text-purple-600">
          {(metrics.confidenceScore * 100).toFixed(1)}%
        </div>
        <div className="text-sm text-gray-600">Confidence Score</div>
      </div>
    </div>
  </div>
);

/**
 * Quality View Component
 */
const QualityView: React.FC<OverviewViewProps> = ({ metrics }) => (
  <div className="space-y-6">
    <h2 className="text-xl font-bold text-gray-900 mb-4">Quality Analysis</h2>

    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Quality Metrics Breakdown
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={metrics.qualityMetrics}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="metric" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="value">
              {metrics.qualityMetrics.map((entry: any, index: number) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Domain Quality Comparison
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={metrics.domainDistribution}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="domain" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="successRate" fill="#8884d8" name="Success %" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>

    {/* Quality Indicators */}
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-gray-800 mb-4">
        Quality Indicators
      </h3>
      {metrics.qualityMetrics.map((metric: any) => (
        <div
          key={metric.metric}
          className="flex items-center justify-between p-4 bg-gray-50 rounded-lg"
        >
          <div className="flex items-center space-x-3">
            <div
              className="w-4 h-4 rounded-full"
              style={{ backgroundColor: metric.color }}
            ></div>
            <span className="font-medium text-gray-900">{metric.metric}</span>
          </div>
          <div className="flex items-center space-x-4">
            <div className="text-2xl font-bold text-gray-900">
              {metric.value}%
            </div>
            <div className="text-sm text-gray-600">Score</div>
          </div>
        </div>
      ))}
    </div>
  </div>
);

/**
 * Domains View Component
 */
const DomainsView: React.FC<OverviewViewProps> = ({ metrics }) => (
  <div className="space-y-6">
    <h2 className="text-xl font-bold text-gray-900 mb-4">Domain Analysis</h2>

    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Request Volume by Domain
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={metrics.domainDistribution}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="domain" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="count" fill="#8884d8" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div>
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Success Rate by Domain
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={metrics.domainDistribution}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="domain" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="successRate" fill="#82ca9d" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>

    {/* Domain Details */}
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-gray-800 mb-4">
        Domain Performance Details
      </h3>
      {metrics.domainDistribution.map((domain: any) => (
        <div key={domain.domain} className="border rounded-lg p-4">
          <div className="flex items-center justify-between mb-2">
            <h4 className="font-semibold text-gray-900">{domain.domain}</h4>
            <div className="flex items-center space-x-4">
              <span className="text-sm text-gray-600">
                {domain.count} requests
              </span>
              <span className="text-sm font-medium text-green-600">
                {domain.successRate.toFixed(1)}% success
              </span>
            </div>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-green-500 h-2 rounded-full"
              style={{ width: `${domain.successRate}%` }}
            ></div>
          </div>
        </div>
      ))}
    </div>
  </div>
);

const COLORS = [
  "#0088FE",
  "#00C49F",
  "#FFBB28",
  "#FF8042",
  "#8DD1E1",
  "#D084D0",
];

export default ContentGenerationVisualizationDashboard;
