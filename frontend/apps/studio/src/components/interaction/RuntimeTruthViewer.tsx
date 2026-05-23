/**
 * Runtime Truth Viewer - displays runtime truth and evidence for interactions.
 *
 * @doc.type component
 * @doc.purpose Show runtime truth and evidence viewers for product interactions
 * @doc.layer studio
 */

import React, { useState } from "react";

interface RuntimeTruth {
  interactionId: string;
  contractId: string;
  timestamp: string;
  status: "success" | "failure" | "pending";
  policyDecision: string;
  evidenceRefs: string[];
  metrics: {
    latency: number;
    retries: number;
    evidenceWriteTime: number;
  };
}

interface RuntimeTruthViewerProps {
  truths: RuntimeTruth[];
  onSelectTruth: (truthId: string) => void;
}

const STATUS_COLORS = {
  success: "bg-green-100 text-green-800",
  failure: "bg-red-100 text-red-800",
  pending: "bg-yellow-100 text-yellow-800",
} as const;

export function RuntimeTruthViewer({ truths, onSelectTruth }: RuntimeTruthViewerProps) {
  const [selectedTruth, setSelectedTruth] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "success" | "failure" | "pending">("all");

  const filteredTruths = truths.filter((truth) => filter === "all" || truth.status === filter);
  const selectedTruthData = truths.find((t) => t.interactionId === selectedTruth);

  return (
    <div className="runtime-truth-viewer">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Runtime Truth & Evidence</h2>
        <div className="flex gap-2">
          <button
            onClick={() => setFilter("all")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "all" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            All
          </button>
          <button
            onClick={() => setFilter("success")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "success" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Success
          </button>
          <button
            onClick={() => setFilter("failure")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "failure" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Failure
          </button>
          <button
            onClick={() => setFilter("pending")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "pending" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Pending
          </button>
        </div>
      </div>

      <div className="flex gap-6">
        <div className="flex-1 bg-white border border-gray-200 rounded-lg p-6">
          <table className="w-full">
            <thead>
              <tr className="border-b">
                <th className="text-left py-2 px-4">Interaction ID</th>
                <th className="text-left py-2 px-4">Contract</th>
                <th className="text-left py-2 px-4">Status</th>
                <th className="text-left py-2 px-4">Timestamp</th>
                <th className="text-left py-2 px-4">Latency</th>
              </tr>
            </thead>
            <tbody>
              {filteredTruths.map((truth) => (
                <tr
                  key={truth.interactionId}
                  className="border-b hover:bg-gray-50 cursor-pointer"
                  onClick={() => {
                    setSelectedTruth(truth.interactionId);
                    onSelectTruth(truth.interactionId);
                  }}
                >
                  <td className="py-2 px-4 font-mono text-sm">{truth.interactionId}</td>
                  <td className="py-2 px-4">{truth.contractId}</td>
                  <td className="py-2 px-4">
                    <span className={`px-2 py-1 rounded-full text-xs ${STATUS_COLORS[truth.status]}`}>
                      {truth.status}
                    </span>
                  </td>
                  <td className="py-2 px-4 text-sm">{truth.timestamp}</td>
                  <td className="py-2 px-4 text-sm">{truth.metrics.latency}ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {selectedTruthData && (
          <div className="w-96 bg-white border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold">Truth Details</h3>
              <button
                onClick={() => setSelectedTruth(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <p className="text-sm text-gray-600">Interaction ID</p>
                <p className="font-mono text-sm">{selectedTruthData.interactionId}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Contract ID</p>
                <p className="font-medium">{selectedTruthData.contractId}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Policy Decision</p>
                <p className="font-medium">{selectedTruthData.policyDecision}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Metrics</p>
                <div className="grid grid-cols-3 gap-2 mt-1">
                  <div className="bg-gray-50 p-2 rounded text-center">
                    <p className="text-xs text-gray-600">Latency</p>
                    <p className="font-semibold">{selectedTruthData.metrics.latency}ms</p>
                  </div>
                  <div className="bg-gray-50 p-2 rounded text-center">
                    <p className="text-xs text-gray-600">Retries</p>
                    <p className="font-semibold">{selectedTruthData.metrics.retries}</p>
                  </div>
                  <div className="bg-gray-50 p-2 rounded text-center">
                    <p className="text-xs text-gray-600">Write Time</p>
                    <p className="font-semibold">{selectedTruthData.metrics.evidenceWriteTime}ms</p>
                  </div>
                </div>
              </div>
              <div>
                <p className="text-sm text-gray-600">Evidence References</p>
                <div className="mt-1 space-y-1">
                  {selectedTruthData.evidenceRefs.map((ref, idx) => (
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
