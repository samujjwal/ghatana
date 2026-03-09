import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, Button, Tabs } from "@ghatana/ui";
import { globalRegistry } from "@ghatana/tutorputor-learning-kernel";
import { PluginCard } from "../../components/ai-kernel/PluginCard";
import { PluginMetadata } from "@ghatana/tutorputor-contracts/v1/plugin-interfaces";

// Import built-in plugins to ensure they are registered
import {
  CBMProcessor,
  XAPIIngestor,
  LearningUnitValidator,
} from "@ghatana/tutorputor-learning-kernel";

export function AIKernelDashboardPage() {
  const navigate = useNavigate();
  const [plugins, setPlugins] = useState<
    Array<
      PluginMetadata & {
        category: string;
        status: "active" | "inactive" | "error";
      }
    >
  >([]);
  const [activeTab, setActiveTab] = useState<string>("overview");

  useEffect(() => {
    // Register built-in plugins if not already registered
    // In a real app, this might happen in a bootstrap file
    try {
      if (!globalRegistry.has("cbm-processor")) {
        globalRegistry.registerEvidenceProcessor(new CBMProcessor());
      }
      if (!globalRegistry.has("xapi-ingestor")) {
        globalRegistry.registerIngestor(new XAPIIngestor());
      }
      if (!globalRegistry.has("lu-validator")) {
        globalRegistry.registerAuthoringTool(new LearningUnitValidator());
      }
    } catch (e) {
      console.warn("Plugins already registered", e);
    }

    // Fetch plugins from registry
    const allPlugins = globalRegistry.listAll().map((p) => ({
      ...p,
      status: "active" as const, // Mock status for now, registry doesn't expose status in listAll yet
    }));
    setPlugins(allPlugins);
  }, []);

  const stats = {
    total: plugins.length,
    processors: plugins.filter((p) => p.category === "evidence_processor")
      .length,
    ingestors: plugins.filter((p) => p.category === "ingestor").length,
    validators: plugins.filter((p) => p.category === "authoring_tool").length,
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
            AI Kernel Dashboard
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Manage the Learning Evidence Platform's plugin kernel
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => navigate("/ai-kernel/editor")}
          >
            New Learning Unit
          </Button>
          <Button variant="outline">Refresh Registry</Button>
          <Button>Add Plugin</Button>
        </div>
      </div>

      {/* Stats Overview */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card className="p-4 bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800">
          <div className="text-sm text-blue-600 dark:text-blue-400 font-medium">
            Total Plugins
          </div>
          <div className="text-3xl font-bold text-blue-900 dark:text-blue-100">
            {stats.total}
          </div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-gray-500 font-medium">
            Evidence Processors
          </div>
          <div className="text-2xl font-bold text-gray-900 dark:text-white">
            {stats.processors}
          </div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-gray-500 font-medium">Ingestors</div>
          <div className="text-2xl font-bold text-gray-900 dark:text-white">
            {stats.ingestors}
          </div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-gray-500 font-medium">
            Authoring Tools
          </div>
          <div className="text-2xl font-bold text-gray-900 dark:text-white">
            {stats.validators}
          </div>
        </Card>
      </div>

      <Tabs
        tabs={[
          {
            key: "overview",
            label: "Registry Overview",
            content: (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {plugins.map((plugin) => (
                  <PluginCard
                    key={plugin.id}
                    metadata={plugin}
                    status={plugin.status}
                    category={plugin.category}
                  />
                ))}
              </div>
            ),
          },
          {
            key: "pipeline",
            label: "Pipeline Health",
            content: (
              <Card className="p-8 text-center text-gray-500">
                Pipeline visualization coming soon.
                <br />
                Will show real-time execution flow of Evidence Processors.
              </Card>
            ),
          },
          {
            key: "playground",
            label: "Playground",
            content: (
              <Card className="p-8 text-center text-gray-500">
                Plugin playground coming soon.
                <br />
                Test ingestors and processors with raw JSON events.
              </Card>
            ),
          },
        ]}
        activeTab={activeTab}
        onChange={setActiveTab}
      />
    </div>
  );
}
