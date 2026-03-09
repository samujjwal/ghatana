/**
 * Content Generation Demo Page
 *
 * A comprehensive demo page that showcases all the visualization components
 * for the TutorPutor content generation system.
 *
 * @doc.type module
 * @doc.purpose Demo page for content generation visualizations
 * @doc.layer product
 * @doc.pattern Demo
 */

import React, { useState } from "react";
import { Link } from "react-router-dom";
import ContentGenerationVisualizationDashboard from "./ContentGenerationVisualizationDashboard";
import SimulationPreview from "./SimulationPreview";

/**
 * Content Generation Demo Page
 */
export const ContentGenerationDemoPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<
    "dashboard" | "simulation" | "pipeline" | "analytics"
  >("dashboard");

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-6">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">
                TutorPutor Content Generation
              </h1>
              <p className="mt-1 text-sm text-gray-500">
                Visualize AI-powered educational content generation
              </p>
            </div>
            <div className="flex items-center space-x-4">
              <Link
                to="/automatic-content-creator"
                className="bg-blue-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-blue-700 transition-colors"
              >
                🤖 Try Automation
              </Link>
              <div className="flex items-center space-x-2">
                <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
                <span className="text-sm text-gray-600">System Active</span>
              </div>
              <div className="text-sm text-gray-600">
                <span className="font-semibold">98%</span> Success Rate
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex space-x-8">
            {[
              { id: "dashboard", label: "Analytics Dashboard", icon: "📊" },
              { id: "simulation", label: "Simulation Preview", icon: "🎮" },
              { id: "pipeline", label: "Pipeline Monitor", icon: "⚙️" },
              { id: "analytics", label: "Advanced Analytics", icon: "📈" },
            ].map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as any)}
                className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
                  activeTab === tab.id
                    ? "border-blue-500 text-blue-600"
                    : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                }`}
              >
                <span className="mr-2">{tab.icon}</span>
                {tab.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === "dashboard" && (
          <div>
            <div className="mb-6">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">
                Real-time Analytics Dashboard
              </h2>
              <p className="text-gray-600">
                Monitor content generation performance, quality metrics, and
                system health in real-time.
              </p>
            </div>
            <ContentGenerationVisualizationDashboard />
          </div>
        )}

        {activeTab === "simulation" && (
          <div>
            <div className="mb-6">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">
                Interactive Simulation Preview
              </h2>
              <p className="text-gray-600">
                See generated simulations in action with interactive controls
                and real-time state monitoring.
              </p>
            </div>
            <SimulationPreview />
          </div>
        )}

        {activeTab === "pipeline" && (
          <div>
            <div className="mb-6">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">
                Generation Pipeline Monitor
              </h2>
              <p className="text-gray-600">
                Track the complete content generation pipeline from concept to
                final output.
              </p>
            </div>
            <PipelineMonitor />
          </div>
        )}

        {activeTab === "analytics" && (
          <div>
            <div className="mb-6">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">
                Advanced Analytics
              </h2>
              <p className="text-gray-600">
                Deep dive into performance trends, quality analysis, and
                predictive insights.
              </p>
            </div>
            <AdvancedAnalytics />
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="bg-white border-t mt-12">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            <div>
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                System Status
              </h3>
              <div className="space-y-2">
                <div className="flex justify-between">
                  <span className="text-gray-600">Generation Engine</span>
                  <span className="text-green-600 font-medium">● Online</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">AI Services</span>
                  <span className="text-green-600 font-medium">● Active</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Template Library</span>
                  <span className="text-green-600 font-medium">● Ready</span>
                </div>
              </div>
            </div>
            <div>
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                Quick Stats
              </h3>
              <div className="space-y-2">
                <div className="flex justify-between">
                  <span className="text-gray-600">Total Generated</span>
                  <span className="font-medium">1,247</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Avg. Generation Time</span>
                  <span className="font-medium">24.3s</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Quality Score</span>
                  <span className="font-medium">89.2%</span>
                </div>
              </div>
            </div>
            <div>
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                Recent Activity
              </h3>
              <div className="space-y-2 text-sm">
                <div className="text-gray-600">
                  • Physics simulation generated (2 min ago)
                </div>
                <div className="text-gray-600">
                  • Chemistry template updated (5 min ago)
                </div>
                <div className="text-gray-600">
                  • Quality check completed (8 min ago)
                </div>
                <div className="text-gray-600">
                  • System maintenance scheduled (1 hour ago)
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * Pipeline Monitor Component
 */
const PipelineMonitor: React.FC = () => {
  const pipelineSteps = [
    { name: "Concept Input", status: "completed", duration: "0.5s" },
    { name: "AI Generation", status: "completed", duration: "18.2s" },
    { name: "Template Matching", status: "completed", duration: "0.3s" },
    { name: "Quality Validation", status: "in-progress", duration: "2.1s" },
    { name: "Final Output", status: "pending", duration: "-" },
  ];

  return (
    <div className="space-y-6">
      {/* Pipeline Flow */}
      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Current Generation Pipeline
        </h3>
        <div className="flex items-center justify-between">
          {pipelineSteps.map((step, index) => (
            <React.Fragment key={step.name}>
              <div className="flex flex-col items-center">
                <div
                  className={`w-12 h-12 rounded-full flex items-center justify-center ${
                    step.status === "completed"
                      ? "bg-green-500"
                      : step.status === "in-progress"
                        ? "bg-blue-500 animate-pulse"
                        : "bg-gray-300"
                  }`}
                >
                  {step.status === "completed"
                    ? "✓"
                    : step.status === "in-progress"
                      ? "⚡"
                      : "⏳"}
                </div>
                <div className="text-sm font-medium text-gray-900 mt-2">
                  {step.name}
                </div>
                <div className="text-xs text-gray-500">{step.duration}</div>
              </div>
              {index < pipelineSteps.length - 1 && (
                <div className="flex-1 h-1 bg-gray-300 mx-4">
                  <div
                    className={`h-full ${
                      step.status === "completed"
                        ? "bg-green-500"
                        : "bg-gray-300"
                    }`}
                    style={{
                      width: step.status === "completed" ? "100%" : "0%",
                    }}
                  ></div>
                </div>
              )}
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* Active Generations */}
      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Active Generations
        </h3>
        <div className="space-y-4">
          {[
            {
              id: "gen-001",
              concept: "Newton's Laws of Motion",
              domain: "Physics",
              progress: 85,
              time: "18s",
            },
            {
              id: "gen-002",
              concept: "Chemical Bonding",
              domain: "Chemistry",
              progress: 62,
              time: "12s",
            },
            {
              id: "gen-003",
              concept: "Binary Search Algorithm",
              domain: "CS Discrete",
              progress: 34,
              time: "6s",
            },
          ].map((gen) => (
            <div key={gen.id} className="border rounded-lg p-4">
              <div className="flex justify-between items-center mb-2">
                <div>
                  <div className="font-medium text-gray-900">{gen.concept}</div>
                  <div className="text-sm text-gray-600">
                    {gen.domain} • {gen.time}
                  </div>
                </div>
                <div className="text-sm font-medium text-blue-600">
                  {gen.progress}%
                </div>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-blue-500 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${gen.progress}%` }}
                ></div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Queue Status */}
      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Generation Queue
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="text-center p-4 bg-blue-50 rounded-lg">
            <div className="text-2xl font-bold text-blue-600">12</div>
            <div className="text-sm text-gray-600">Queued</div>
          </div>
          <div className="text-center p-4 bg-yellow-50 rounded-lg">
            <div className="text-2xl font-bold text-yellow-600">3</div>
            <div className="text-sm text-gray-600">Processing</div>
          </div>
          <div className="text-center p-4 bg-green-50 rounded-lg">
            <div className="text-2xl font-bold text-green-600">247</div>
            <div className="text-sm text-gray-600">Completed Today</div>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * Advanced Analytics Component
 */
const AdvancedAnalytics: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Performance Trends */}
      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Performance Trends
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <h4 className="font-medium text-gray-800 mb-2">
              Generation Time Trends
            </h4>
            <div className="h-48 bg-gray-100 rounded-lg flex items-center justify-center">
              <span className="text-gray-500">
                📈 Chart: Generation time decreasing over time
              </span>
            </div>
          </div>
          <div>
            <h4 className="font-medium text-gray-800 mb-2">
              Quality Score Evolution
            </h4>
            <div className="h-48 bg-gray-100 rounded-lg flex items-center justify-center">
              <span className="text-gray-500">
                📊 Chart: Quality scores improving
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Predictive Analytics */}
      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Predictive Analytics
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="border rounded-lg p-4">
            <h4 className="font-medium text-gray-800 mb-2">Expected Load</h4>
            <div className="text-2xl font-bold text-blue-600">+23%</div>
            <div className="text-sm text-gray-600">Next 24 hours</div>
            <div className="text-xs text-gray-500 mt-2">
              Based on historical patterns
            </div>
          </div>
          <div className="border rounded-lg p-4">
            <h4 className="font-medium text-gray-800 mb-2">
              Quality Prediction
            </h4>
            <div className="text-2xl font-bold text-green-600">91.2%</div>
            <div className="text-sm text-gray-600">Avg. confidence score</div>
            <div className="text-xs text-gray-500 mt-2">
              AI model improvement trend
            </div>
          </div>
          <div className="border rounded-lg p-4">
            <h4 className="font-medium text-gray-800 mb-2">
              Resource Optimization
            </h4>
            <div className="text-2xl font-bold text-purple-600">-15%</div>
            <div className="text-sm text-gray-600">Expected resource usage</div>
            <div className="text-xs text-gray-500 mt-2">After optimization</div>
          </div>
        </div>
      </div>

      {/* Domain Insights */}
      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Domain-Specific Insights
        </h3>
        <div className="space-y-4">
          {[
            {
              domain: "Physics",
              insight: "Pendulum simulations most popular, 97% success rate",
              trend: "up",
            },
            {
              domain: "Chemistry",
              insight: "Titration templates showing 15% improvement",
              trend: "up",
            },
            {
              domain: "CS Discrete",
              insight: "Algorithm visualizations in high demand",
              trend: "stable",
            },
            {
              domain: "Mathematics",
              insight: "Geometry templates need optimization",
              trend: "down",
            },
          ].map((item) => (
            <div
              key={item.domain}
              className="flex items-center justify-between p-4 border rounded-lg"
            >
              <div>
                <div className="font-medium text-gray-900">{item.domain}</div>
                <div className="text-sm text-gray-600">{item.insight}</div>
              </div>
              <div
                className={`px-3 py-1 rounded-full text-xs font-medium ${
                  item.trend === "up"
                    ? "bg-green-100 text-green-800"
                    : item.trend === "down"
                      ? "bg-red-100 text-red-800"
                      : "bg-gray-100 text-gray-800"
                }`}
              >
                {item.trend === "up"
                  ? "↗️ Improving"
                  : item.trend === "down"
                    ? "↘️ Declining"
                    : "➡️ Stable"}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default ContentGenerationDemoPage;
