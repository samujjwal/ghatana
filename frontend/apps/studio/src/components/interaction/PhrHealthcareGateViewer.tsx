/**
 * PHR Healthcare Gate Evidence Viewer - displays PHR-specific healthcare gate evidence.
 *
 * @doc.type component
 * @doc.purpose Show PHR healthcare gate evidence viewer for regulated healthcare workflows
 * @doc.layer studio
 */

import React, { useState } from "react";

interface HealthcareGate {
  id: string;
  name: string;
  type: "consent" | "pii" | "audit" | "data-sovereignty";
  status: "passed" | "failed" | "skipped" | "pending";
  lastRun: string;
  evidenceRefs: string[];
  details: {
    patientId?: string;
    consentType?: string;
    classification?: string;
    auditTrail?: string[];
  };
}

interface PhrHealthcareGateViewerProps {
  gates: HealthcareGate[];
  onSelectGate: (gateId: string) => void;
}

const GATE_TYPE_COLORS = {
  consent: "bg-purple-100 text-purple-800",
  pii: "bg-blue-100 text-blue-800",
  audit: "bg-green-100 text-green-800",
  "data-sovereignty": "bg-orange-100 text-orange-800",
} as const;

const STATUS_COLORS = {
  passed: "bg-green-100 text-green-800",
  failed: "bg-red-100 text-red-800",
  skipped: "bg-gray-100 text-gray-800",
  pending: "bg-yellow-100 text-yellow-800",
} as const;

export function PhrHealthcareGateViewer({ gates, onSelectGate }: PhrHealthcareGateViewerProps) {
  const [selectedGate, setSelectedGate] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "consent" | "pii" | "audit" | "data-sovereignty">("all");

  const filteredGates = gates.filter((gate) => filter === "all" || gate.type === filter);
  const selectedGateData = gates.find((g) => g.id === selectedGate);

  return (
    <div className="phr-healthcare-gate-viewer">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">PHR Healthcare Gate Evidence</h2>
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
            onClick={() => setFilter("consent")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "consent" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Consent
          </button>
          <button
            onClick={() => setFilter("pii")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "pii" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            PII
          </button>
          <button
            onClick={() => setFilter("audit")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "audit" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Audit
          </button>
          <button
            onClick={() => setFilter("data-sovereignty")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "data-sovereignty" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Data Sovereignty
          </button>
        </div>
      </div>

      <div className="flex gap-6">
        <div className="flex-1 bg-white border border-gray-200 rounded-lg p-6">
          <table className="w-full">
            <thead>
              <tr className="border-b">
                <th className="text-left py-2 px-4">Gate Name</th>
                <th className="text-left py-2 px-4">Type</th>
                <th className="text-left py-2 px-4">Status</th>
                <th className="text-left py-2 px-4">Last Run</th>
              </tr>
            </thead>
            <tbody>
              {filteredGates.map((gate) => (
                <tr
                  key={gate.id}
                  className="border-b hover:bg-gray-50 cursor-pointer"
                  onClick={() => {
                    setSelectedGate(gate.id);
                    onSelectGate(gate.id);
                  }}
                >
                  <td className="py-2 px-4 font-medium">{gate.name}</td>
                  <td className="py-2 px-4">
                    <span className={`px-2 py-1 rounded-full text-xs ${GATE_TYPE_COLORS[gate.type]}`}>
                      {gate.type}
                    </span>
                  </td>
                  <td className="py-2 px-4">
                    <span className={`px-2 py-1 rounded-full text-xs ${STATUS_COLORS[gate.status]}`}>
                      {gate.status}
                    </span>
                  </td>
                  <td className="py-2 px-4 text-sm">{gate.lastRun}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {selectedGateData && (
          <div className="w-96 bg-white border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold">Gate Details</h3>
              <button
                onClick={() => setSelectedGate(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <p className="text-sm text-gray-600">Gate Name</p>
                <p className="font-medium">{selectedGateData.name}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Type</p>
                <span className={`px-2 py-1 rounded-full text-xs ${GATE_TYPE_COLORS[selectedGateData.type]}`}>
                  {selectedGateData.type}
                </span>
              </div>
              <div>
                <p className="text-sm text-gray-600">Status</p>
                <span className={`px-2 py-1 rounded-full text-xs ${STATUS_COLORS[selectedGateData.status]}`}>
                  {selectedGateData.status}
                </span>
              </div>
              <div>
                <p className="text-sm text-gray-600">Last Run</p>
                <p className="font-medium">{selectedGateData.lastRun}</p>
              </div>

              {selectedGateData.details.patientId && (
                <div>
                  <p className="text-sm text-gray-600">Patient ID</p>
                  <p className="font-mono text-sm">{selectedGateData.details.patientId}</p>
                </div>
              )}

              {selectedGateData.details.consentType && (
                <div>
                  <p className="text-sm text-gray-600">Consent Type</p>
                  <p className="font-medium">{selectedGateData.details.consentType}</p>
                </div>
              )}

              {selectedGateData.details.classification && (
                <div>
                  <p className="text-sm text-gray-600">PII Classification</p>
                  <p className="font-medium">{selectedGateData.details.classification}</p>
                </div>
              )}

              {selectedGateData.details.auditTrail && (
                <div>
                  <p className="text-sm text-gray-600">Audit Trail</p>
                  <div className="mt-1 space-y-1">
                    {selectedGateData.details.auditTrail.map((entry, idx) => (
                      <p key={idx} className="text-xs font-mono bg-gray-100 p-1 rounded">
                        {entry}
                      </p>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <p className="text-sm text-gray-600">Evidence References</p>
                <div className="mt-1 space-y-1">
                  {selectedGateData.evidenceRefs.map((ref, idx) => (
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
