/**
 * Infrastructure View
 *
 * Infrastructure monitoring view for the Unified Content Studio.
 *
 * @doc.type component
 * @doc.purpose Infrastructure monitoring for content studio
 * @doc.layer product
 * @doc.pattern View Component
 */

import { Cpu, Database, Layers } from "lucide-react";

export interface InfrastructureStatus {
  aiModels: {
    openai: { status: string; latency: number; successRate: number };
    ollama: { status: string; latency: number; successRate: number };
    claude: { status: string; latency: number; successRate: number };
  };
  databases: {
    postgres: { status: string; connections: number; queryTime: number };
    redis: { status: string; memory: number; hitRate: number };
  };
  storage: {
    s3: { status: string; size: number; objects: number };
    local: { status: string; size: number; objects: number };
  };
}

export interface InfrastructureViewProps {
  infrastructureStatus: InfrastructureStatus | null;
}

export function InfrastructureView({
  infrastructureStatus,
}: InfrastructureViewProps) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* AI Models */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <Cpu className="h-5 w-5" />
            AI Models
          </h3>
          <div className="space-y-4">
            {infrastructureStatus?.aiModels &&
              Object.entries(infrastructureStatus.aiModels).map(
                ([model, status]) => (
                  <div key={model} className="border-l-4 border-green-500 pl-4">
                    <div className="flex items-center justify-between">
                      <span className="font-medium capitalize">{model}</span>
                      <span
                        className={`px-2 py-1 text-xs rounded-full ${
                          status.status === "healthy"
                            ? "bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400"
                            : "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400"
                        }`}
                      >
                        {status.status}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      Latency: {status.latency}ms | Success:{" "}
                      {status.successRate.toFixed(1)}%
                    </div>
                  </div>
                ),
              )}
          </div>
        </div>

        {/* Databases */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <Database className="h-5 w-5" />
            Databases
          </h3>
          <div className="space-y-4">
            {infrastructureStatus?.databases &&
              Object.entries(infrastructureStatus.databases).map(
                ([db, status]) => (
                  <div key={db} className="border-l-4 border-blue-500 pl-4">
                    <div className="flex items-center justify-between">
                      <span className="font-medium capitalize">{db}</span>
                      <span className="px-2 py-1 text-xs rounded-full bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400">
                        {status.status}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      {db === "postgres"
                        ? `Connections: ${("connections" in status ? status.connections : 0)} | Query: ${("queryTime" in status ? status.queryTime : 0)}ms`
                        : `Memory: ${("memory" in status ? status.memory : 0)}% | Hit Rate: ${("hitRate" in status ? status.hitRate.toFixed(1) : 0)}%`}
                    </div>
                  </div>
                ),
              )}
          </div>
        </div>

        {/* Storage */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <Layers className="h-5 w-5" />
            Storage
          </h3>
          <div className="space-y-4">
            {infrastructureStatus?.storage &&
              Object.entries(infrastructureStatus.storage).map(
                ([storage, status]) => (
                  <div
                    key={storage}
                    className="border-l-4 border-purple-500 pl-4"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium capitalize">{storage}</span>
                      <span className="px-2 py-1 text-xs rounded-full bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400">
                        {status.status}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      Size: {status.size}GB | Objects:{" "}
                      {status.objects.toLocaleString()}
                    </div>
                  </div>
                ),
              )}
          </div>
        </div>
      </div>
    </div>
  );
}
