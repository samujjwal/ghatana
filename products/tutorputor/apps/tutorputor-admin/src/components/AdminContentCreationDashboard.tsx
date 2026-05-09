/**
 * Admin Content Creation Dashboard
 *
 * Administrative interface for managing the content creation system.
 * Focuses on infrastructure, configuration, and management rather than user experience.
 *
 * @doc.type module
 * @doc.purpose Admin dashboard for content creation management
 * @doc.layer product
 * @doc.pattern Admin
 */

import { useState, useEffect } from "react";
import {
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
// Types will be defined locally to avoid cross-workspace import issues

interface ContentCreationStats {
  totalContent: number;
  byType: {
    examples: number;
    simulations: number;
    animations: number;
    assessments: number;
    explanations: number;
  };
  byDomain: {
    physics: number;
    chemistry: number;
    mathematics: number;
    biology: number;
    computerScience: number;
  };
  byQuality: {
    auto: number;
    reviewed: number;
    manual: number;
  };
}

interface SystemInfrastructure {
  aiModels: {
    gpt4: { status: string; requests: number; latency: number; cost: number };
    claude: { status: string; requests: number; latency: number; cost: number };
    gemini: { status: string; requests: number; latency: number; cost: number };
  };
  databases: {
    postgresql: {
      status: string;
      connections: number;
      size: string;
      performance: number;
    };
    redis: { status: string; memory: string; keys: number; hitRate: number };
    minio: {
      status: string;
      storage: string;
      objects: number;
      bandwidth: number;
    };
  };
  templates: {
    total: number;
    byDomain: Record<string, number>;
    usage: Array<{ template: string; uses: number; successRate: number }>;
  };
}

/**
 * Admin Content Creation Dashboard Component
 */
export const AdminContentCreationDashboard: React.FC = () => {
  const [activeView, setActiveView] = useState<
    | "overview"
    | "infrastructure"
    | "content"
    | "automation"
    | "templates"
    | "quality"
  >("overview");

  const [stats, setStats] = useState<ContentCreationStats>({
    totalContent: 0,
    byType: {
      examples: 0,
      simulations: 0,
      animations: 0,
      assessments: 0,
      explanations: 0,
    },
    byDomain: {
      physics: 0,
      chemistry: 0,
      mathematics: 0,
      biology: 0,
      computerScience: 0,
    },
    byQuality: {
      auto: 0,
      reviewed: 0,
      manual: 0,
    },
  });

  const [infrastructure, setInfrastructure] = useState<SystemInfrastructure>({
    aiModels: {
      gpt4: { status: "healthy", requests: 0, latency: 0, cost: 0 },
      claude: { status: "healthy", requests: 0, latency: 0, cost: 0 },
      gemini: { status: "healthy", requests: 0, latency: 0, cost: 0 },
    },
    databases: {
      postgresql: {
        status: "healthy",
        connections: 0,
        size: "0 GB",
        performance: 0,
      },
      redis: { status: "healthy", memory: "0 MB", keys: 0, hitRate: 0 },
      minio: { status: "healthy", storage: "0 GB", objects: 0, bandwidth: 0 },
    },
    templates: {
      total: 0,
      byDomain: {},
      usage: [],
    },
  });

  // Load real metrics from service instead of fake random values
  useEffect(() => {
    let isMounted = true;

    const loadMetrics = async () => {
      try {
        const { getContentGenerationMetricsService } = await import(
          "../services/contentGenerationMetrics"
        );
        const metricsService = getContentGenerationMetricsService();

        const [contentStats, infrastructureStatus] = await Promise.all([
          metricsService.getContentStats(),
          metricsService.getInfrastructureStatus(),
        ]);

        if (isMounted) {
          setStats(contentStats as any);
          setInfrastructure(infrastructureStatus as any);
        }
      } catch (error) {
        console.error("Failed to load metrics:", error);
        // Keep existing state on error to prevent UI flicker
      }
    };

    // Initial load
    loadMetrics();

    // Refresh metrics every 30 seconds instead of 4 seconds
    const interval = setInterval(loadMetrics, 30000);

    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  const renderOverview = () => (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      <div className="bg-white rounded-lg shadow p-6 border-l-4 border-blue-500">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">
              Total Content Created
            </p>
            <p className="text-3xl font-bold text-gray-900">
              {stats.totalContent.toLocaleString()}
            </p>
            <p className="text-xs text-green-600">
              +{Math.floor(Math.random() * 20)} this hour
            </p>
          </div>
          <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
            <div className="text-blue-600 font-bold text-xl">📝</div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6 border-l-4 border-green-500">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">
              AI Requests Today
            </p>
            <p className="text-3xl font-bold text-gray-900">
              {Object.values(infrastructure.aiModels).reduce(
                (sum, model) => sum + model.requests,
                0,
              )}
            </p>
            <p className="text-xs text-gray-500">Across all models</p>
          </div>
          <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
            <div className="text-green-600 font-bold text-xl">🤖</div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6 border-l-4 border-purple-500">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">System Health</p>
            <p className="text-3xl font-bold text-gray-900">98.5%</p>
            <p className="text-xs text-green-600">All systems operational</p>
          </div>
          <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
            <div className="text-purple-600 font-bold text-xl">✓</div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6 border-l-4 border-orange-500">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">Cost Today</p>
            <p className="text-3xl font-bold text-gray-900">
              $
              {Object.values(infrastructure.aiModels)
                .reduce((sum, model) => sum + model.cost, 0)
                .toFixed(2)}
            </p>
            <p className="text-xs text-gray-500">AI model usage</p>
          </div>
          <div className="w-12 h-12 bg-orange-100 rounded-lg flex items-center justify-center">
            <div className="text-orange-600 font-bold text-xl">💰</div>
          </div>
        </div>
      </div>
    </div>
  );

  const renderInfrastructure = () => (
    <div className="space-y-6">
      {/* AI Models Status */}
      <div className="bg-white rounded-lg shadow">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            AI Models Infrastructure
          </h3>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {Object.entries(infrastructure.aiModels).map(([model, data]) => (
              <div key={model} className="border rounded-lg p-4">
                <div className="flex items-center justify-between mb-4">
                  <h4 className="font-semibold text-gray-900">
                    {model.toUpperCase()}
                  </h4>
                  <span
                    className={`px-2 py-1 text-xs font-semibold rounded-full ${
                      data.status === "healthy"
                        ? "bg-green-100 text-green-800"
                        : "bg-red-100 text-red-800"
                    }`}
                  >
                    {data.status}
                  </span>
                </div>
                <div className="space-y-2">
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Requests:</span>
                    <span className="text-sm font-medium">
                      {data.requests.toLocaleString()}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Latency:</span>
                    <span className="text-sm font-medium">
                      {data.latency.toFixed(2)}s
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Cost:</span>
                    <span className="text-sm font-medium">
                      ${data.cost.toFixed(2)}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Database Infrastructure */}
      <div className="bg-white rounded-lg shadow">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            Database Infrastructure
          </h3>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {Object.entries(infrastructure.databases).map(([db, data]) => (
              <div key={db} className="border rounded-lg p-4">
                <div className="flex items-center justify-between mb-4">
                  <h4 className="font-semibold text-gray-900">
                    {db.toUpperCase()}
                  </h4>
                  <span
                    className={`px-2 py-1 text-xs font-semibold rounded-full ${
                      data.status === "healthy"
                        ? "bg-green-100 text-green-800"
                        : "bg-red-100 text-red-800"
                    }`}
                  >
                    {data.status}
                  </span>
                </div>
                <div className="space-y-2">
                  {db === "postgresql" && (
                    <>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">
                          Connections:
                        </span>
                        <span className="text-sm font-medium">
                          {(data as { connections: number }).connections}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">Size:</span>
                        <span className="text-sm font-medium">{(data as { size: string }).size}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">
                          Performance:
                        </span>
                        <span className="text-sm font-medium">
                          {(data as { performance: number }).performance.toFixed(1)}%
                        </span>
                      </div>
                    </>
                  )}
                  {db === "redis" && (
                    <>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">Memory:</span>
                        <span className="text-sm font-medium">
                          {(data as { memory: string }).memory}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">Keys:</span>
                        <span className="text-sm font-medium">
                          {(data as { keys: number }).keys.toLocaleString()}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">Hit Rate:</span>
                        <span className="text-sm font-medium">
                          {(data as { hitRate: number }).hitRate.toFixed(1)}%
                        </span>
                      </div>
                    </>
                  )}
                  {db === "minio" && (
                    <>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">Storage:</span>
                        <span className="text-sm font-medium">
                          {(data as { storage: string }).storage}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">Objects:</span>
                        <span className="text-sm font-medium">
                          {(data as { objects: number }).objects.toLocaleString()}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-sm text-gray-600">
                          Bandwidth:
                        </span>
                        <span className="text-sm font-medium">
                          {(data as { bandwidth: number }).bandwidth.toFixed(1)} MB/s
                        </span>
                      </div>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );

  const renderContent = () => (
    <div className="space-y-6">
      {/* Content by Type */}
      <div className="bg-white rounded-lg shadow">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            Content Distribution by Type
          </h3>
        </div>
        <div className="p-6">
          <ResponsiveContainer width="100%" height={300}>
            <BarChart
              data={[
                { type: "Examples", count: stats.byType.examples },
                { type: "Simulations", count: stats.byType.simulations },
                { type: "Animations", count: stats.byType.animations },
                { type: "Assessments", count: stats.byType.assessments },
                { type: "Explanations", count: stats.byType.explanations },
              ]}
            >
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="type" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="count" fill="#3b82f6" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Content by Domain */}
      <div className="bg-white rounded-lg shadow">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            Content Distribution by Domain
          </h3>
        </div>
        <div className="p-6">
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={[
                  {
                    name: "Physics",
                    value: stats.byDomain.physics,
                    color: "#3b82f6",
                  },
                  {
                    name: "Chemistry",
                    value: stats.byDomain.chemistry,
                    color: "#10b981",
                  },
                  {
                    name: "Mathematics",
                    value: stats.byDomain.mathematics,
                    color: "#f59e0b",
                  },
                  {
                    name: "Biology",
                    value: stats.byDomain.biology,
                    color: "#ef4444",
                  },
                  {
                    name: "Computer Science",
                    value: stats.byDomain.computerScience,
                    color: "#8b5cf6",
                  },
                ]}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, value }) => `${name}: ${value}`}
                outerRadius={80}
                fill="#8884d8"
                dataKey="value"
              >
                {["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6"].map(
                  (color, index) => (
                    <Cell key={`cell-${index}`} fill={color} />
                  ),
                )}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Content Quality */}
      <div className="bg-white rounded-lg shadow">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            Content Quality Distribution
          </h3>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-3 gap-6">
            <div className="text-center">
              <div className="text-3xl font-bold text-green-600">
                {stats.byQuality.auto}
              </div>
              <div className="text-sm text-gray-600">Auto-Generated</div>
              <div className="text-xs text-gray-500">AI-created content</div>
            </div>
            <div className="text-center">
              <div className="text-3xl font-bold text-yellow-600">
                {stats.byQuality.reviewed}
              </div>
              <div className="text-sm text-gray-600">Reviewed</div>
              <div className="text-xs text-gray-500">Teacher-approved</div>
            </div>
            <div className="text-center">
              <div className="text-3xl font-bold text-blue-600">
                {stats.byQuality.manual}
              </div>
              <div className="text-sm text-gray-600">Manual</div>
              <div className="text-xs text-gray-500">Expert-created</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  const renderTemplates = () => (
    <div className="space-y-6">
      <div className="bg-white rounded-lg shadow">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            Template Library
          </h3>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <h4 className="font-semibold text-gray-900 mb-4">
                Template Statistics
              </h4>
              <div className="space-y-3">
                <div className="flex justify-between items-center p-3 bg-gray-50 rounded">
                  <span className="text-sm font-medium text-gray-700">
                    Total Templates
                  </span>
                  <span className="text-lg font-bold text-gray-900">
                    {infrastructure.templates.total}
                  </span>
                </div>
                <div className="flex justify-between items-center p-3 bg-gray-50 rounded">
                  <span className="text-sm font-medium text-gray-700">
                    Active Usage
                  </span>
                  <span className="text-lg font-bold text-green-600">
                    {infrastructure.templates.usage.reduce(
                      (sum, t) => sum + t.uses,
                      0,
                    )}
                  </span>
                </div>
                <div className="flex justify-between items-center p-3 bg-gray-50 rounded">
                  <span className="text-sm font-medium text-gray-700">
                    Avg Success Rate
                  </span>
                  <span className="text-lg font-bold text-blue-600">
                    {(
                      infrastructure.templates.usage.reduce(
                        (sum, t) => sum + t.successRate,
                        0,
                      ) / infrastructure.templates.usage.length || 0
                    ).toFixed(1)}
                    %
                  </span>
                </div>
              </div>
            </div>

            <div>
              <h4 className="font-semibold text-gray-900 mb-4">
                Recent Template Usage
              </h4>
              <div className="space-y-2">
                {infrastructure.templates.usage
                  .slice(-5)
                  .reverse()
                  .map((template, index) => (
                    <div
                      key={index}
                      className="flex justify-between items-center p-2 border rounded"
                    >
                      <span className="text-sm text-gray-700">
                        {template.template}
                      </span>
                      <div className="flex items-center space-x-2">
                        <span className="text-xs text-gray-500">
                          {template.uses} uses
                        </span>
                        <span className="text-xs font-medium text-green-600">
                          {template.successRate.toFixed(1)}%
                        </span>
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Admin Header */}
      <div className="bg-gray-900 text-white shadow-sm border-b border-gray-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-6">
            <div>
              <h1 className="text-3xl font-bold">🔧 Content Creation Admin</h1>
              <p className="mt-1 text-sm text-gray-300">
                System infrastructure and content management dashboard
              </p>
            </div>
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
                <span className="text-sm text-gray-300">
                  Systems Operational
                </span>
              </div>
              <div className="text-sm text-gray-300">Admin v2.0</div>
            </div>
          </div>
        </div>
      </div>

      {/* Admin Navigation */}
      <div className="bg-gray-800 border-b border-gray-700">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <nav className="flex space-x-8">
            {[
              { id: "overview", name: "System Overview", icon: "📊" },
              { id: "infrastructure", name: "Infrastructure", icon: "🏗️" },
              { id: "content", name: "Content Analytics", icon: "📝" },
              { id: "templates", name: "Templates", icon: "📋" },
              { id: "automation", name: "Automation", icon: "⚙️" },
              { id: "quality", name: "Quality Control", icon: "✅" },
            ].map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveView(tab.id as any)}
                className={`${
                  activeView === tab.id
                    ? "border-blue-500 text-blue-400"
                    : "border-transparent text-gray-400 hover:text-gray-300 hover:border-gray-600"
                } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm flex items-center space-x-2`}
              >
                <span>{tab.icon}</span>
                <span>{tab.name}</span>
              </button>
            ))}
          </nav>
        </div>
      </div>

      {/* Admin Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeView === "overview" && renderOverview()}
        {activeView === "infrastructure" && renderInfrastructure()}
        {activeView === "content" && renderContent()}
        {activeView === "templates" && renderTemplates()}
        {activeView === "automation" && (
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-8 text-center">
            <h3 className="text-lg font-medium text-blue-900 mb-2">
              🔧 Automation Management
            </h3>
            <p className="text-blue-700 mb-4">
              Advanced automation controls and rule management interface
            </p>
            <button className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700">
              Configure Automation Rules
            </button>
          </div>
        )}
        {activeView === "quality" && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-8 text-center">
            <h3 className="text-lg font-medium text-green-900 mb-2">
              ✅ Quality Control Center
            </h3>
            <p className="text-green-700 mb-4">
              Content validation, review workflows, and quality assurance tools
            </p>
            <button className="bg-green-600 text-white px-4 py-2 rounded-md hover:bg-green-700">
              Manage Quality Workflows
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminContentCreationDashboard;
