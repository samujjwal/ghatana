/**
 * DMOS Consent/Notification Interaction Evidence Viewer - displays DMOS-specific consent and notification interaction evidence.
 *
 * @doc.type component
 * @doc.purpose Show DMOS consent/notification interaction evidence viewer for marketing workflows
 * @doc.layer studio
 */

import React, { useState } from "react";

interface DmosInteraction {
  id: string;
  type: "consent-check" | "notification-preference" | "audience-disable";
  contractId: string;
  status: "success" | "failure" | "pending";
  timestamp: string;
  customerId?: string;
  campaignId?: string;
  evidenceRefs: string[];
  details: {
    consentGranted?: boolean;
    notificationChannel?: string;
    audienceId?: string;
    reasonCode?: string;
  };
}

interface DmosConsentNotificationViewerProps {
  interactions: DmosInteraction[];
  onSelectInteraction: (interactionId: string) => void;
}

const TYPE_COLORS = {
  "consent-check": "bg-purple-100 text-purple-800",
  "notification-preference": "bg-blue-100 text-blue-800",
  "audience-disable": "bg-orange-100 text-orange-800",
} as const;

const STATUS_COLORS = {
  success: "bg-green-100 text-green-800",
  failure: "bg-red-100 text-red-800",
  pending: "bg-yellow-100 text-yellow-800",
} as const;

export function DmosConsentNotificationViewer({ interactions, onSelectInteraction }: DmosConsentNotificationViewerProps) {
  const [selectedInteraction, setSelectedInteraction] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "consent-check" | "notification-preference" | "audience-disable">("all");

  const filteredInteractions = interactions.filter((interaction) => filter === "all" || interaction.type === filter);
  const selectedInteractionData = interactions.find((i) => i.id === selectedInteraction);

  return (
    <div className="dmos-consent-notification-viewer">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">DMOS Consent/Notification Evidence</h2>
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
            onClick={() => setFilter("consent-check")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "consent-check" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Consent Check
          </button>
          <button
            onClick={() => setFilter("notification-preference")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "notification-preference" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Notification Preference
          </button>
          <button
            onClick={() => setFilter("audience-disable")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "audience-disable" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Audience Disable
          </button>
        </div>
      </div>

      <div className="flex gap-6">
        <div className="flex-1 bg-white border border-gray-200 rounded-lg p-6">
          <table className="w-full">
            <thead>
              <tr className="border-b">
                <th className="text-left py-2 px-4">Interaction ID</th>
                <th className="text-left py-2 px-4">Type</th>
                <th className="text-left py-2 px-4">Contract</th>
                <th className="text-left py-2 px-4">Status</th>
                <th className="text-left py-2 px-4">Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {filteredInteractions.map((interaction) => (
                <tr
                  key={interaction.id}
                  className="border-b hover:bg-gray-50 cursor-pointer"
                  onClick={() => {
                    setSelectedInteraction(interaction.id);
                    onSelectInteraction(interaction.id);
                  }}
                >
                  <td className="py-2 px-4 font-mono text-sm">{interaction.id}</td>
                  <td className="py-2 px-4">
                    <span className={`px-2 py-1 rounded-full text-xs ${TYPE_COLORS[interaction.type]}`}>
                      {interaction.type}
                    </span>
                  </td>
                  <td className="py-2 px-4 text-sm">{interaction.contractId}</td>
                  <td className="py-2 px-4">
                    <span className={`px-2 py-1 rounded-full text-xs ${STATUS_COLORS[interaction.status]}`}>
                      {interaction.status}
                    </span>
                  </td>
                  <td className="py-2 px-4 text-sm">{interaction.timestamp}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {selectedInteractionData && (
          <div className="w-96 bg-white border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold">Interaction Details</h3>
              <button
                onClick={() => setSelectedInteraction(null)}
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
                <p className="font-mono text-sm">{selectedInteractionData.id}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Type</p>
                <span className={`px-2 py-1 rounded-full text-xs ${TYPE_COLORS[selectedInteractionData.type]}`}>
                  {selectedInteractionData.type}
                </span>
              </div>
              <div>
                <p className="text-sm text-gray-600">Contract ID</p>
                <p className="font-medium">{selectedInteractionData.contractId}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Status</p>
                <span className={`px-2 py-1 rounded-full text-xs ${STATUS_COLORS[selectedInteractionData.status]}`}>
                  {selectedInteractionData.status}
                </span>
              </div>
              <div>
                <p className="text-sm text-gray-600">Timestamp</p>
                <p className="font-medium">{selectedInteractionData.timestamp}</p>
              </div>

              {selectedInteractionData.customerId && (
                <div>
                  <p className="text-sm text-gray-600">Customer ID</p>
                  <p className="font-mono text-sm">{selectedInteractionData.customerId}</p>
                </div>
              )}

              {selectedInteractionData.campaignId && (
                <div>
                  <p className="text-sm text-gray-600">Campaign ID</p>
                  <p className="font-mono text-sm">{selectedInteractionData.campaignId}</p>
                </div>
              )}

              {selectedInteractionData.details.consentGranted !== undefined && (
                <div>
                  <p className="text-sm text-gray-600">Consent Granted</p>
                  <span className={`px-2 py-1 rounded-full text-xs ${selectedInteractionData.details.consentGranted ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}>
                    {selectedInteractionData.details.consentGranted ? "Yes" : "No"}
                  </span>
                </div>
              )}

              {selectedInteractionData.details.notificationChannel && (
                <div>
                  <p className="text-sm text-gray-600">Notification Channel</p>
                  <p className="font-medium">{selectedInteractionData.details.notificationChannel}</p>
                </div>
              )}

              {selectedInteractionData.details.audienceId && (
                <div>
                  <p className="text-sm text-gray-600">Audience ID</p>
                  <p className="font-mono text-sm">{selectedInteractionData.details.audienceId}</p>
                </div>
              )}

              {selectedInteractionData.details.reasonCode && (
                <div>
                  <p className="text-sm text-gray-600">Reason Code</p>
                  <p className="font-medium">{selectedInteractionData.details.reasonCode}</p>
                </div>
              )}

              <div>
                <p className="text-sm text-gray-600">Evidence References</p>
                <div className="mt-1 space-y-1">
                  {selectedInteractionData.evidenceRefs.map((ref, idx) => (
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
