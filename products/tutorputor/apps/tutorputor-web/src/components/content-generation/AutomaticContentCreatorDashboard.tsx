/**
 * Automatic Content Creator Dashboard
 *
 * Extends the existing content generation system with full automation capabilities.
 * Reuses existing components and adds automation orchestration, scheduling, and management.
 *
 * @doc.type module
 * @doc.purpose Automation dashboard for content creation
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
import { ContentGenerationVisualizationDashboard } from "./ContentGenerationVisualizationDashboard";
import { SimulationPreview } from "./SimulationPreview";
import type {
  AutomationRule,
  AutomationMetrics,
  ViewType,
  DomainData,
  PerformanceData,
  QualityData,
} from "../../types/content-generation";

/**
 * Automatic Content Creator Dashboard Component
 */
export const AutomaticContentCreatorDashboard: React.FC = () => {
  const [activeView, setActiveView] = useState<
    "overview" | "rules" | "analytics" | "monitoring" | "existing"
  >("overview");

  const [metrics, setMetrics] = useState<AutomationMetrics>({
    totalRules: 0,
    activeRules: 0,
    contentGenerated: 0,
    automationSuccessRate: 0,
    averageProcessingTime: 0,
    errorRate: 0,
    dailyVolume: 0,
    weeklyTrend: [] as PerformanceData[],
    triggerDistribution: [] as DomainData[],
    qualityMetrics: [] as QualityData[],
  });

  const [automationRules, setAutomationRules] = useState<AutomationRule[]>([
    {
      id: "1",
      name: "Daily Math Practice",
      trigger: "scheduled",
      schedule: "0 9 * * *",
      condition: "User has active math course",
      action: "Generate 5 practice problems",
      quality: "auto",
      delivery: ["learning-path", "email"],
      status: "active",
      lastRun: "2024-01-20 09:00:00",
      nextRun: "2024-01-21 09:00:00",
      successRate: 98.5,
    },
    {
      id: "2",
      name: "New User Onboarding",
      trigger: "event",
      condition: "User registration event",
      action: "Create welcome content + first lesson",
      quality: "full",
      delivery: ["instant", "email", "dashboard"],
      status: "active",
      lastRun: "2024-01-20 14:23:00",
      successRate: 99.2,
    },
    {
      id: "3",
      name: "Performance Gap Detection",
      trigger: "ai-suggested",
      condition: "Score < 60% on assessment",
      action: "Generate remedial content",
      quality: "review",
      delivery: ["learning-path", "notification"],
      status: "active",
      lastRun: "2024-01-20 16:45:00",
      successRate: 95.8,
    },
  ]);

  // Simulate real-time metrics updates
  useEffect(() => {
    const interval = setInterval(() => {
      setMetrics((prev) => ({
        ...prev,
        totalRules: automationRules.length,
        activeRules: automationRules.filter((r) => r.status === "active")
          .length,
        contentGenerated: prev.contentGenerated + Math.floor(Math.random() * 5),
        automationSuccessRate: 95 + Math.random() * 4,
        averageProcessingTime: 2.5 + Math.random() * 1.5,
        errorRate: Math.random() * 2,
        dailyVolume: prev.dailyVolume + Math.floor(Math.random() * 3),
        weeklyTrend: [
          ...prev.weeklyTrend.slice(-6),
          {
            name: new Date().toLocaleDateString("en", { weekday: "short" }),
            content: prev.dailyVolume,
            success: prev.automationSuccessRate,
          } as PerformanceData,
        ],
        triggerDistribution: [
          {
            name: "Scheduled",
            value: automationRules.filter((r) => r.trigger === "scheduled")
              .length,
            color: "#3b82f6",
          } as DomainData,
          {
            name: "Event",
            value: automationRules.filter((r) => r.trigger === "event").length,
            color: "#10b981",
          } as DomainData,
          {
            name: "API",
            value: automationRules.filter((r) => r.trigger === "api").length,
            color: "#f59e0b",
          } as DomainData,
          {
            name: "AI-Suggested",
            value: automationRules.filter((r) => r.trigger === "ai-suggested")
              .length,
            color: "#8b5cf6",
          } as DomainData,
        ],
        qualityMetrics: [
          {
            name: "Auto",
            value: automationRules.filter((r) => r.quality === "auto").length,
            color: "#10b981",
          } as QualityData,
          {
            name: "Review",
            value: automationRules.filter((r) => r.quality === "review").length,
            color: "#f59e0b",
          } as QualityData,
          {
            name: "Full",
            value: automationRules.filter((r) => r.quality === "full").length,
            color: "#ef4444",
          } as QualityData,
        ],
      }));
    }, 3000);

    return () => clearInterval(interval);
  }, [automationRules]);

  const toggleRuleStatus = (ruleId: string) => {
    setAutomationRules((prev) =>
      prev.map((rule) =>
        rule.id === ruleId
          ? { ...rule, status: rule.status === "active" ? "paused" : "active" }
          : rule,
      ),
    );
  };

  const renderOverview = () => (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">Active Rules</p>
            <p className="text-2xl font-bold text-gray-900">
              {metrics.activeRules}
            </p>
            <p className="text-xs text-gray-500">
              of {metrics.totalRules} total
            </p>
          </div>
          <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
            <div className="w-6 h-6 bg-blue-500 rounded-full animate-pulse"></div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">
              Content Generated
            </p>
            <p className="text-2xl font-bold text-gray-900">
              {metrics.contentGenerated}
            </p>
            <p className="text-xs text-green-600">
              +{metrics.dailyVolume} today
            </p>
          </div>
          <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
            <div className="text-green-600 font-bold">📝</div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">Success Rate</p>
            <p className="text-2xl font-bold text-gray-900">
              {metrics.automationSuccessRate.toFixed(1)}%
            </p>
            <p className="text-xs text-gray-500">Automation accuracy</p>
          </div>
          <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
            <div className="text-purple-600 font-bold">✓</div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">Avg Processing</p>
            <p className="text-2xl font-bold text-gray-900">
              {metrics.averageProcessingTime.toFixed(1)}s
            </p>
            <p className="text-xs text-gray-500">Per content item</p>
          </div>
          <div className="w-12 h-12 bg-orange-100 rounded-lg flex items-center justify-center">
            <div className="text-orange-600 font-bold">⚡</div>
          </div>
        </div>
      </div>
    </div>
  );

  const renderRules = () => (
    <div className="bg-white rounded-lg shadow">
      <div className="px-6 py-4 border-b border-gray-200">
        <h3 className="text-lg font-medium text-gray-900">Automation Rules</h3>
        <p className="text-sm text-gray-500">
          Manage your automated content creation rules
        </p>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Rule
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Trigger
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Action
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Quality
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Success Rate
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Status
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {automationRules.map((rule) => (
              <tr key={rule.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 whitespace-nowrap">
                  <div>
                    <div className="text-sm font-medium text-gray-900">
                      {rule.name}
                    </div>
                    <div className="text-sm text-gray-500">
                      {rule.condition}
                    </div>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span
                    className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                      rule.trigger === "scheduled"
                        ? "bg-blue-100 text-blue-800"
                        : rule.trigger === "event"
                          ? "bg-green-100 text-green-800"
                          : rule.trigger === "api"
                            ? "bg-yellow-100 text-yellow-800"
                            : "bg-purple-100 text-purple-800"
                    }`}
                  >
                    {rule.trigger}
                  </span>
                  {rule.schedule && (
                    <div className="text-xs text-gray-500 mt-1">
                      {rule.schedule}
                    </div>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                  {rule.action}
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span
                    className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                      rule.quality === "auto"
                        ? "bg-green-100 text-green-800"
                        : rule.quality === "review"
                          ? "bg-yellow-100 text-yellow-800"
                          : "bg-red-100 text-red-800"
                    }`}
                  >
                    {rule.quality}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="text-sm text-gray-900">
                    {rule.successRate.toFixed(1)}%
                  </div>
                  {rule.lastRun && (
                    <div className="text-xs text-gray-500">
                      Last: {rule.lastRun}
                    </div>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span
                    className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                      rule.status === "active"
                        ? "bg-green-100 text-green-800"
                        : rule.status === "paused"
                          ? "bg-red-100 text-red-800"
                          : "bg-gray-100 text-gray-800"
                    }`}
                  >
                    {rule.status}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                  <button
                    onClick={() => toggleRuleStatus(rule.id)}
                    className={`px-3 py-1 rounded text-xs font-medium ${
                      rule.status === "active"
                        ? "bg-red-100 text-red-800 hover:bg-red-200"
                        : "bg-green-100 text-green-800 hover:bg-green-200"
                    }`}
                  >
                    {rule.status === "active" ? "Pause" : "Start"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );

  const renderAnalytics = () => (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">
          Weekly Content Generation Trend
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={metrics.weeklyTrend}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="day" />
            <YAxis />
            <Tooltip />
            <Legend />
            <Line
              type="monotone"
              dataKey="content"
              stroke="#3b82f6"
              name="Content Items"
            />
            <Line
              type="monotone"
              dataKey="success"
              stroke="#10b981"
              name="Success Rate %"
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">
          Trigger Distribution
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <PieChart>
            <Pie
              data={metrics.triggerDistribution}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={({ name, value }) => `${name}: ${value}`}
              outerRadius={80}
              fill="#8884d8"
              dataKey="value"
            >
              {metrics.triggerDistribution.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip />
          </PieChart>
        </ResponsiveContainer>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">
          Quality Control Distribution
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={metrics.qualityMetrics}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="value" fill="#8b5cf6" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">
          Performance Metrics
        </h3>
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <span className="text-sm font-medium text-gray-600">
              Average Processing Time
            </span>
            <span className="text-sm font-bold text-gray-900">
              {metrics.averageProcessingTime.toFixed(2)}s
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm font-medium text-gray-600">
              Error Rate
            </span>
            <span className="text-sm font-bold text-red-600">
              {metrics.errorRate.toFixed(2)}%
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm font-medium text-gray-600">
              Daily Volume
            </span>
            <span className="text-sm font-bold text-green-600">
              {metrics.dailyVolume} items
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm font-medium text-gray-600">
              Total Generated
            </span>
            <span className="text-sm font-bold text-blue-600">
              {metrics.contentGenerated} items
            </span>
          </div>
        </div>
      </div>
    </div>
  );

  const renderMonitoring = () => (
    <div className="space-y-6">
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">
          Real-time Monitoring
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-green-50 border border-green-200 rounded-lg p-4">
            <div className="flex items-center">
              <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse mr-3"></div>
              <div>
                <p className="text-sm font-medium text-green-900">
                  System Healthy
                </p>
                <p className="text-xs text-green-700">
                  All automation rules running
                </p>
              </div>
            </div>
          </div>
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div className="flex items-center">
              <div className="w-3 h-3 bg-blue-500 rounded-full animate-pulse mr-3"></div>
              <div>
                <p className="text-sm font-medium text-blue-900">
                  Processing Queue
                </p>
                <p className="text-xs text-blue-700">3 items in queue</p>
              </div>
            </div>
          </div>
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
            <div className="flex items-center">
              <div className="w-3 h-3 bg-yellow-500 rounded-full animate-pulse mr-3"></div>
              <div>
                <p className="text-sm font-medium text-yellow-900">
                  Quality Review
                </p>
                <p className="text-xs text-yellow-700">
                  2 items pending review
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">
          Recent Activity
        </h3>
        <div className="space-y-3">
          <div className="flex items-center justify-between py-2 border-b">
            <div className="flex items-center">
              <div className="w-2 h-2 bg-green-500 rounded-full mr-3"></div>
              <div>
                <p className="text-sm font-medium text-gray-900">
                  Daily Math Practice completed
                </p>
                <p className="text-xs text-gray-500">
                  Generated 5 practice problems for 12 users
                </p>
              </div>
            </div>
            <span className="text-xs text-gray-500">2 min ago</span>
          </div>
          <div className="flex items-center justify-between py-2 border-b">
            <div className="flex items-center">
              <div className="w-2 h-2 bg-blue-500 rounded-full mr-3"></div>
              <div>
                <p className="text-sm font-medium text-gray-900">
                  New user onboarding triggered
                </p>
                <p className="text-xs text-gray-500">
                  Welcome content created for user #1234
                </p>
              </div>
            </div>
            <span className="text-xs text-gray-500">15 min ago</span>
          </div>
          <div className="flex items-center justify-between py-2 border-b">
            <div className="flex items-center">
              <div className="w-2 h-2 bg-purple-500 rounded-full mr-3"></div>
              <div>
                <p className="text-sm font-medium text-gray-900">
                  AI suggested remedial content
                </p>
                <p className="text-xs text-gray-500">
                  Performance gap detected in algebra
                </p>
              </div>
            </div>
            <span className="text-xs text-gray-500">1 hour ago</span>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-6">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">
                🤖 Automatic Content Creator
              </h1>
              <p className="mt-1 text-sm text-gray-500">
                Full automation system for educational content generation
              </p>
            </div>
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
                <span className="text-sm text-gray-600">Automation Active</span>
              </div>
              <div className="text-sm text-gray-600">
                <span className="font-semibold">
                  {metrics.automationSuccessRate.toFixed(1)}%
                </span>{" "}
                Success Rate
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <nav className="flex space-x-8" aria-label="Tabs">
            {[
              { id: "overview", name: "Overview", icon: "📊" },
              { id: "rules", name: "Automation Rules", icon: "⚙️" },
              { id: "analytics", name: "Analytics", icon: "📈" },
              { id: "monitoring", name: "Monitoring", icon: "🔍" },
              { id: "existing", name: "Existing System", icon: "🔄" },
            ].map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveView(tab.id as any)}
                className={`${
                  activeView === tab.id
                    ? "border-blue-500 text-blue-600"
                    : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm flex items-center space-x-2`}
              >
                <span>{tab.icon}</span>
                <span>{tab.name}</span>
              </button>
            ))}
          </nav>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeView === "overview" && renderOverview()}
        {activeView === "rules" && renderRules()}
        {activeView === "analytics" && renderAnalytics()}
        {activeView === "monitoring" && renderMonitoring()}
        {activeView === "existing" && (
          <div className="space-y-8">
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
              <h3 className="text-lg font-medium text-blue-900 mb-2">
                🔄 Reusing Existing Components
              </h3>
              <p className="text-blue-700">
                The automation system extends the existing content generation
                infrastructure without duplicating components.
              </p>
            </div>

            {/* Reuse existing dashboard */}
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4">
                📊 Content Generation Analytics
              </h3>
              <ContentGenerationVisualizationDashboard />
            </div>

            {/* Reuse existing simulation preview */}
            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4">
                🎮 Interactive Content Preview
              </h3>
              <SimulationPreview />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AutomaticContentCreatorDashboard;
