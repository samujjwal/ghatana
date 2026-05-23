/**
 * Plugin Interaction Graph - visualizes plugin-to-plugin interaction relationships.
 *
 * @doc.type component
 * @doc.purpose Show plugin interaction graph with provider, consumer, contract, status, evidence, and failures
 * @doc.layer studio
 */

import React, { useState } from "react";

interface PluginNode {
  id: string;
  pluginId: string;
  pluginName: string;
  type: "provider" | "consumer";
  status: "active" | "inactive" | "error";
}

interface PluginEdge {
  id: string;
  contractId: string;
  source: string;
  target: string;
  status: "active" | "inactive" | "error";
  lastUsed: string;
  evidenceRefs: string[];
  failures: number;
}

interface PluginInteractionGraphProps {
  nodes: PluginNode[];
  edges: PluginEdge[];
  onSelectInteraction: (edgeId: string) => void;
}

const STATUS_COLORS = {
  active: "#10b981",
  inactive: "#9ca3af",
  error: "#ef4444",
} as const;

export function PluginInteractionGraph({ nodes, edges, onSelectInteraction }: PluginInteractionGraphProps) {
  const [selectedEdge, setSelectedEdge] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<"graph" | "list">("graph");

  const selectedEdgeData = edges.find((e) => e.id === selectedEdge);

  return (
    <div className="plugin-interaction-graph">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Plugin Interaction Graph</h2>
        <div className="flex gap-2">
          <button
            onClick={() => setViewMode("graph")}
            className={`px-3 py-1 rounded-md text-sm ${
              viewMode === "graph" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Graph View
          </button>
          <button
            onClick={() => setViewMode("list")}
            className={`px-3 py-1 rounded-md text-sm ${
              viewMode === "list" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            List View
          </button>
        </div>
      </div>

      <div className="flex gap-6">
        <div className="flex-1">
          {viewMode === "graph" ? (
            <div className="bg-white border border-gray-200 rounded-lg p-6 min-h-[500px]">
              <div className="flex items-center justify-center h-full text-gray-500">
                <div className="text-center">
                  <svg className="w-16 h-16 mx-auto mb-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
                  </svg>
                  <p>Interactive plugin graph visualization</p>
                  <p className="text-sm mt-2">Select an edge to view details</p>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-4">
                {nodes.map((node) => (
                  <div
                    key={node.id}
                    className="p-3 border rounded-lg"
                    style={{ borderColor: STATUS_COLORS[node.status] }}
                  >
                    <p className="font-semibold">{node.pluginName}</p>
                    <p className="text-sm text-gray-600">{node.type}</p>
                    <div className="mt-2 flex items-center">
                      <div
                        className="w-3 h-3 rounded-full mr-2"
                        style={{ backgroundColor: STATUS_COLORS[node.status] }}
                      />
                      <span className="text-xs">{node.status}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="bg-white border border-gray-200 rounded-lg p-6">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-2 px-4">Contract</th>
                    <th className="text-left py-2 px-4">Provider</th>
                    <th className="text-left py-2 px-4">Consumer</th>
                    <th className="text-left py-2 px-4">Status</th>
                    <th className="text-left py-2 px-4">Failures</th>
                  </tr>
                </thead>
                <tbody>
                  {edges.map((edge) => (
                    <tr
                      key={edge.id}
                      className="border-b hover:bg-gray-50 cursor-pointer"
                      onClick={() => {
                        setSelectedEdge(edge.id);
                        onSelectInteraction(edge.id);
                      }}
                    >
                      <td className="py-2 px-4">{edge.contractId}</td>
                      <td className="py-2 px-4">{edge.source}</td>
                      <td className="py-2 px-4">{edge.target}</td>
                      <td className="py-2 px-4">
                        <span
                          className="px-2 py-1 rounded-full text-xs"
                          style={{
                            backgroundColor: STATUS_COLORS[edge.status] + "20",
                            color: STATUS_COLORS[edge.status],
                          }}
                        >
                          {edge.status}
                        </span>
                      </td>
                      <td className="py-2 px-4">{edge.failures}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {selectedEdgeData && (
          <div className="w-80 bg-white border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold">Plugin Interaction Details</h3>
              <button
                onClick={() => setSelectedEdge(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <p className="text-sm text-gray-600">Contract ID</p>
                <p className="font-medium">{selectedEdgeData.contractId}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Provider</p>
                <p className="font-medium">{selectedEdgeData.source}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Consumer</p>
                <p className="font-medium">{selectedEdgeData.target}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Status</p>
                <span
                  className="px-2 py-1 rounded-full text-xs"
                  style={{
                    backgroundColor: STATUS_COLORS[selectedEdgeData.status] + "20",
                    color: STATUS_COLORS[selectedEdgeData.status],
                  }}
                >
                  {selectedEdgeData.status}
                </span>
              </div>
              <div>
                <p className="text-sm text-gray-600">Last Used</p>
                <p className="font-medium">{selectedEdgeData.lastUsed}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Failures</p>
                <p className="font-medium">{selectedEdgeData.failures}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Evidence References</p>
                <div className="mt-1 space-y-1">
                  {selectedEdgeData.evidenceRefs.map((ref, idx) => (
                    <p key={idx} className="text-xs font-mono bg-gray-100 p-1 rounded">
                      {ref}
                    </p>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
