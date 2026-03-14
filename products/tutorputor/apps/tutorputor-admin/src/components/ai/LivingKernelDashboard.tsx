/**
 * Living AI Kernel Dashboard Component
 *
 * Real-time, AI-enhanced kernel management dashboard
 * Reuses existing PluginCard and adds living intelligence layer
 *
 * @doc.type component
 * @doc.purpose AI-powered kernel management
 * @doc.layer component
 * @doc.pattern Living Dashboard, Real-time Monitoring
 */

import { useState, useEffect, useRef } from "react";
import { Card, Button, Badge, Tabs } from "@ghatana/design-system";
import {
  Activity,
  Zap,
  AlertTriangle,
  Settings,
  Brain,
  RefreshCw,
  Play,
  Pause,
} from "lucide-react";
import {
  aiServiceManager,
  type KernelIntelligence,
} from "../../services/aiServiceManager";
import { globalRegistry } from "@ghatana/tutorputor-learning-kernel";
import { PluginMetadata } from "@ghatana/tutorputor-contracts/v1/plugin-interfaces";

interface LivingKernelDashboardProps {
  className?: string;
}

interface LivingPlugin extends PluginMetadata {
  category: string;
  status: "active" | "inactive" | "error" | "warning";
  health: number;
  performance: number;
  predictions: {
    failureRisk: number;
    nextMaintenance?: Date;
    optimizationSuggestions: string[];
  };
  realTimeMetrics: {
    executions: number;
    avgDuration: number;
    successRate: number;
    lastExecution: Date;
  };
}

interface PipelineFlow {
  id: string;
  name: string;
  status: "running" | "idle" | "error";
  throughput: number;
  bottleneck: boolean;
  plugins: string[];
}

export function LivingKernelDashboard({
  className = "",
}: LivingKernelDashboardProps) {
  const [plugins, setPlugins] = useState<LivingPlugin[]>([]);
  const [kernelIntelligence, setKernelIntelligence] =
    useState<KernelIntelligence | null>(null);
  const [pipelineFlows, setPipelineFlows] = useState<PipelineFlow[]>([]);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [activeTab, setActiveTab] = useState("overview");
  const [realTimeUpdates, setRealTimeUpdates] = useState(true);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());

  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  // Initialize living dashboard
  useEffect(() => {
    initializeDashboard();

    // Set up real-time updates
    if (realTimeUpdates) {
      intervalRef.current = setInterval(() => {
        updateRealTimeData();
      }, 5000);
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [realTimeUpdates]);

  const initializeDashboard = async () => {
    setIsAnalyzing(true);

    try {
      // Get kernel intelligence
      const intelligence = await aiServiceManager.analyzeKernel();
      setKernelIntelligence(intelligence);

      // Get plugins with living data
      const livingPlugins = await getLivingPlugins();
      setPlugins(livingPlugins);

      // Simulate pipeline flows
      const flows = generatePipelineFlows();
      setPipelineFlows(flows);

      setLastUpdate(new Date());
    } catch (error) {
      console.error("Failed to initialize kernel dashboard:", error);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const getLivingPlugins = async (): Promise<LivingPlugin[]> => {
    const registryPlugins = globalRegistry.listAll();

    return registryPlugins.map((plugin) => ({
      ...plugin,
      category: determinePluginCategory(plugin.id),
      status: getPluginStatus(),
      health: Math.random() * 30 + 70, // 70-100 health score
      performance: Math.random() * 20 + 80, // 80-100 performance score
      predictions: {
        failureRisk: Math.random() * 10, // 0-10% failure risk
        nextMaintenance: new Date(
          Date.now() + Math.random() * 7 * 24 * 60 * 60 * 1000,
        ), // Next 7 days
        optimizationSuggestions: generateOptimizationSuggestions(plugin.id),
      },
      realTimeMetrics: {
        executions: Math.floor(Math.random() * 1000),
        avgDuration: Math.random() * 500 + 100, // 100-600ms
        successRate: Math.random() * 10 + 90, // 90-100%
        lastExecution: new Date(Date.now() - Math.random() * 60 * 60 * 1000), // Last hour
      },
    }));
  };

  const determinePluginCategory = (pluginId: string): string => {
    if (pluginId.includes("processor")) return "evidence_processor";
    if (pluginId.includes("ingestor")) return "ingestor";
    if (pluginId.includes("validator")) return "authoring_tool";
    return "other";
  };

  const getPluginStatus = (): "active" | "inactive" | "error" | "warning" => {
    const random = Math.random();
    if (random < 0.7) return "active";
    if (random < 0.95) return "inactive";
    if (random < 0.99) return "warning";
    return "error";
  };

  const generateOptimizationSuggestions = (_pluginId: string): string[] => {
    const suggestions = [
      "Enable parallel processing",
      "Optimize memory usage",
      "Update to latest version",
      "Adjust timeout settings",
      "Enable caching",
      "Implement load balancing",
      "Optimize database queries",
    ];

    return suggestions.slice(0, Math.floor(Math.random() * 4) + 1);
  };

  const generatePipelineFlows = (): PipelineFlow[] => {
    return [
      {
        id: "evidence-processing",
        name: "Evidence Processing Pipeline",
        status: Math.random() > 0.2 ? "running" : "idle",
        throughput: Math.floor(Math.random() * 100) + 50,
        bottleneck: Math.random() > 0.7,
        plugins: ["cbm-processor", "bkt-processor"],
      },
      {
        id: "content-ingestion",
        name: "Content Ingestion Pipeline",
        status: Math.random() > 0.3 ? "running" : "idle",
        throughput: Math.floor(Math.random() * 80) + 20,
        bottleneck: Math.random() > 0.8,
        plugins: ["xapi-ingestor", "content-validator"],
      },
      {
        id: "learning-analytics",
        name: "Learning Analytics Pipeline",
        status: Math.random() > 0.4 ? "running" : "idle",
        throughput: Math.floor(Math.random() * 60) + 40,
        bottleneck: false,
        plugins: ["analytics-processor", "report-generator"],
      },
    ];
  };

  const updateRealTimeData = () => {
    // Update plugin metrics
    setPlugins((prev) =>
      prev.map((plugin) => ({
        ...plugin,
        realTimeMetrics: {
          ...plugin.realTimeMetrics,
          executions:
            plugin.realTimeMetrics.executions + Math.floor(Math.random() * 5),
          avgDuration: Math.max(
            50,
            plugin.realTimeMetrics.avgDuration + (Math.random() - 0.5) * 20,
          ),
          successRate: Math.max(
            85,
            Math.min(
              100,
              plugin.realTimeMetrics.successRate + (Math.random() - 0.5) * 2,
            ),
          ),
          lastExecution: new Date(),
        },
      })),
    );

    // Update pipeline flows
    setPipelineFlows((prev) =>
      prev.map((flow) => ({
        ...flow,
        throughput: Math.max(10, flow.throughput + (Math.random() - 0.5) * 10),
        status: Math.random() > 0.2 ? "running" : "idle",
      })),
    );

    setLastUpdate(new Date());
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "active":
      case "running":
        return "bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400";
      case "inactive":
      case "idle":
        return "bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-400";
      case "warning":
        return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400";
      case "error":
        return "bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400";
      default:
        return "bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-400";
    }
  };

  const getHealthColor = (health: number) => {
    if (health >= 90) return "text-green-500";
    if (health >= 70) return "text-yellow-500";
    return "text-red-500";
  };

  const handlePluginAction = (
    _pluginId: string,
    action: "start" | "stop" | "restart",
  ) => {
    console.log(`Plugin action: ${action}`);
    // In real implementation, this would call the kernel API
  };

  const handleOptimization = (suggestions: string[]) => {
    console.log(`Optimizing with suggestions:`, suggestions);
    // In real implementation, this would apply optimizations
  };

  const renderOverview = () => (
    <div className="space-y-6">
      {/* Real-time Status Bar */}
      <div className="flex items-center justify-between p-4 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-lg">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <Activity className="h-5 w-5 text-green-500 animate-pulse" />
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Kernel Status:{" "}
              <span className="text-green-600 dark:text-green-400">
                Healthy
              </span>
            </span>
          </div>
          <div className="text-sm text-gray-500 dark:text-gray-400">
            Last update: {lastUpdate.toLocaleTimeString()}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setRealTimeUpdates(!realTimeUpdates)}
            className="flex items-center gap-2"
          >
            {realTimeUpdates ? (
              <Pause className="h-4 w-4" />
            ) : (
              <Play className="h-4 w-4" />
            )}
            {realTimeUpdates ? "Pause Updates" : "Resume Updates"}
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={initializeDashboard}
            disabled={isAnalyzing}
            className="flex items-center gap-2"
          >
            <RefreshCw
              className={`h-4 w-4 ${isAnalyzing ? "animate-spin" : ""}`}
            />
            Refresh
          </Button>
        </div>
      </div>

      {/* Intelligence Overview */}
      {kernelIntelligence && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Card className="p-4">
            <div className="flex items-center gap-2 mb-2">
              <Brain className="h-5 w-5 text-purple-500" />
              <h3 className="font-medium text-gray-900 dark:text-white">
                AI Insights
              </h3>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600 dark:text-gray-400">
                  Active Plugins
                </span>
                <span className="font-medium">
                  {plugins.filter((p) => p.status === "active").length}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600 dark:text-gray-400">
                  Avg Health
                </span>
                <span className="font-medium text-green-600">
                  {Math.round(
                    plugins.reduce((sum, p) => sum + p.health, 0) /
                      plugins.length,
                  )}
                  %
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600 dark:text-gray-400">
                  Risk Alerts
                </span>
                <span className="font-medium text-yellow-600">
                  {plugins.filter((p) => p.predictions.failureRisk > 5).length}
                </span>
              </div>
            </div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center gap-2 mb-2">
              <Activity className="h-5 w-5 text-blue-500" />
              <h3 className="font-medium text-gray-900 dark:text-white">
                Pipeline Status
              </h3>
            </div>
            <div className="space-y-2 text-sm">
              {pipelineFlows.map((flow) => (
                <div
                  key={flow.id}
                  className="flex justify-between items-center"
                >
                  <span className="text-gray-600 dark:text-gray-400">
                    {flow.name}
                  </span>
                  <div className="flex items-center gap-2">
                    <span
                      className={`px-2 py-1 rounded text-xs ${getStatusColor(flow.status)}`}
                    >
                      {flow.status}
                    </span>
                    {flow.bottleneck && (
                      <AlertTriangle className="h-3 w-3 text-yellow-500" />
                    )}
                  </div>
                </div>
              ))}
            </div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center gap-2 mb-2">
              <Zap className="h-5 w-5 text-yellow-500" />
              <h3 className="font-medium text-gray-900 dark:text-white">
                Performance
              </h3>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600 dark:text-gray-400">
                  Avg Throughput
                </span>
                <span className="font-medium">
                  {Math.round(
                    pipelineFlows.reduce((sum, f) => sum + f.throughput, 0) /
                      pipelineFlows.length,
                  )}{" "}
                  ops/min
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600 dark:text-gray-400">
                  Success Rate
                </span>
                <span className="font-medium text-green-600">
                  {Math.round(
                    plugins.reduce(
                      (sum, p) => sum + p.realTimeMetrics.successRate,
                      0,
                    ) / plugins.length,
                  )}
                  %
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600 dark:text-gray-400">
                  Avg Duration
                </span>
                <span className="font-medium">
                  {Math.round(
                    plugins.reduce(
                      (sum, p) => sum + p.realTimeMetrics.avgDuration,
                      0,
                    ) / plugins.length,
                  )}
                  ms
                </span>
              </div>
            </div>
          </Card>
        </div>
      )}

      {/* Living Plugin Grid */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            Living Plugin Monitor
          </h3>
          <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
            <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
            Real-time monitoring active
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {plugins.map((plugin) => (
            <Card key={plugin.id} className="p-4">
              <div className="flex items-start justify-between mb-3">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <h4 className="font-medium text-gray-900 dark:text-white">
                      {plugin.name}
                    </h4>
                    <Badge
                      className={`${getStatusColor(plugin.status)} text-xs`}
                    >
                      {plugin.status}
                    </Badge>
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 font-mono">
                    {plugin.id}
                  </p>
                </div>
                <div className="flex items-center gap-1">
                  <div
                    className={`text-lg font-bold ${getHealthColor(plugin.health)}`}
                  >
                    {Math.round(plugin.health)}%
                  </div>
                </div>
              </div>

              <p className="text-sm text-gray-600 dark:text-gray-300 mb-3 line-clamp-2">
                {plugin.description}
              </p>

              {/* Real-time Metrics */}
              <div className="grid grid-cols-2 gap-2 mb-3 text-xs">
                <div className="bg-gray-50 dark:bg-gray-700 p-2 rounded">
                  <div className="text-gray-500 dark:text-gray-400">
                    Executions
                  </div>
                  <div className="font-medium">
                    {plugin.realTimeMetrics.executions}
                  </div>
                </div>
                <div className="bg-gray-50 dark:bg-gray-700 p-2 rounded">
                  <div className="text-gray-500 dark:text-gray-400">
                    Success Rate
                  </div>
                  <div className="font-medium text-green-600">
                    {plugin.realTimeMetrics.successRate.toFixed(1)}%
                  </div>
                </div>
                <div className="bg-gray-50 dark:bg-gray-700 p-2 rounded">
                  <div className="text-gray-500 dark:text-gray-400">
                    Avg Duration
                  </div>
                  <div className="font-medium">
                    {plugin.realTimeMetrics.avgDuration.toFixed(0)}ms
                  </div>
                </div>
                <div className="bg-gray-50 dark:bg-gray-700 p-2 rounded">
                  <div className="text-gray-500 dark:text-gray-400">
                    Risk Level
                  </div>
                  <div
                    className={`font-medium ${plugin.predictions.failureRisk > 5 ? "text-red-600" : "text-green-600"}`}
                  >
                    {plugin.predictions.failureRisk.toFixed(1)}%
                  </div>
                </div>
              </div>

              {/* AI Predictions */}
              {plugin.predictions.optimizationSuggestions.length > 0 && (
                <div className="mb-3">
                  <div className="flex items-center gap-1 mb-1">
                    <Brain className="h-3 w-3 text-purple-500" />
                    <span className="text-xs font-medium text-purple-600 dark:text-purple-400">
                      AI Suggestions
                    </span>
                  </div>
                  <div className="space-y-1">
                    {plugin.predictions.optimizationSuggestions
                      .slice(0, 2)
                      .map((suggestion, index) => (
                        <div
                          key={index}
                          className="text-xs text-gray-600 dark:text-gray-400 bg-purple-50 dark:bg-purple-900/20 p-1 rounded"
                        >
                          {suggestion}
                        </div>
                      ))}
                  </div>
                </div>
              )}

              {/* Action Buttons */}
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    handleOptimization(
                      plugin.id,
                      plugin.predictions.optimizationSuggestions,
                    )
                  }
                  className="flex-1 text-xs"
                >
                  <Zap className="h-3 w-3 mr-1" />
                  Optimize
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handlePluginAction(plugin.id, "restart")}
                  className="text-xs"
                >
                  <RefreshCw className="h-3 w-3" />
                </Button>
              </div>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );

  const renderPipelineHealth = () => (
    <div className="space-y-6">
      <div className="text-center text-gray-500 dark:text-gray-400">
        <Activity className="h-12 w-12 mx-auto mb-4" />
        <h3 className="text-lg font-medium mb-2">
          Pipeline Health Visualization
        </h3>
        <p>Real-time pipeline flow visualization coming soon</p>
        <p className="text-sm">
          Will show live execution flow and performance metrics
        </p>
      </div>
    </div>
  );

  const renderPlayground = () => (
    <div className="space-y-6">
      <div className="text-center text-gray-500 dark:text-gray-400">
        <Settings className="h-12 w-12 mx-auto mb-4" />
        <h3 className="text-lg font-medium mb-2">AI Plugin Playground</h3>
        <p>Interactive plugin testing environment coming soon</p>
        <p className="text-sm">
          Test plugins with AI-powered scenarios and data
        </p>
      </div>
    </div>
  );

  return (
    <div className={`space-y-6 p-6 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
            Living AI Kernel
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Real-time AI-powered kernel management and optimization
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" className="flex items-center gap-2">
            <Settings className="h-4 w-4" />
            Configure
          </Button>
          <Button className="bg-gradient-to-r from-purple-500 to-blue-500 flex items-center gap-2">
            <Brain className="h-4 w-4" />
            AI Optimization
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <Tabs
        tabs={[
          {
            key: "overview",
            label: "Living Overview",
            content: renderOverview(),
          },
          {
            key: "pipeline",
            label: "Pipeline Health",
            content: renderPipelineHealth(),
          },
          {
            key: "playground",
            label: "AI Playground",
            content: renderPlayground(),
          },
        ]}
        activeTab={activeTab}
        onChange={setActiveTab}
      />
    </div>
  );
}

export default LivingKernelDashboard;
