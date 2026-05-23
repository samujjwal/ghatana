/**
 * Product Unit Detail - displays detailed information about a specific product unit.
 *
 * @doc.type component
 * @doc.purpose Show product unit details including surfaces, adapters, interactions, and gates
 * @doc.layer studio
 */

import React, { useState } from "react";

interface ProductUnit {
  id: string;
  name: string;
  kind: string;
  owner: string;
  status: "active" | "disabled" | "pilot";
  releaseReadinessScore: number;
  surfaces: Surface[];
  adapters: Adapter[];
  interactions: Interaction[];
  gates: Gate[];
}

interface Surface {
  id: string;
  type: string;
  language: string;
  runtime: string;
  buildSystem: string;
  path: string;
  health: "healthy" | "degraded" | "unhealthy";
}

interface Adapter {
  id: string;
  type: string;
  language: string;
  status: "registered" | "unregistered" | "blocked";
}

interface Interaction {
  id: string;
  contractId: string;
  provider: string;
  consumer: string;
  status: "active" | "inactive" | "error";
  lastUsed: string;
}

interface Gate {
  id: string;
  name: string;
  type: string;
  status: "passed" | "failed" | "skipped";
  lastRun: string;
}

interface ProductUnitDetailProps {
  product: ProductUnit;
  onBack: () => void;
}

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "surfaces", label: "Surfaces" },
  { id: "adapters", label: "Adapters" },
  { id: "interactions", label: "Interactions" },
  { id: "gates", label: "Gates" },
] as const;

type TabId = (typeof TABS)[number]["id"];

export function ProductUnitDetail({ product, onBack }: ProductUnitDetailProps) {
  const [activeTab, setActiveTab] = useState<TabId>("overview");

  const HEALTH_COLORS = {
    healthy: "bg-green-100 text-green-800",
    degraded: "bg-yellow-100 text-yellow-800",
    unhealthy: "bg-red-100 text-red-800",
  } as const;

  const GATE_STATUS_COLORS = {
    passed: "bg-green-100 text-green-800",
    failed: "bg-red-100 text-red-800",
    skipped: "bg-gray-100 text-gray-800",
  } as const;

  const INTERACTION_STATUS_COLORS = {
    active: "bg-green-100 text-green-800",
    inactive: "bg-gray-100 text-gray-800",
    error: "bg-red-100 text-red-800",
  } as const;

  return (
    <div className="product-unit-detail">
      <button
        onClick={onBack}
        className="mb-4 flex items-center text-blue-600 hover:text-blue-800"
      >
        <svg className="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
        Back to Registry
      </button>

      <div className="bg-white border border-gray-200 rounded-lg p-6 mb-6">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">{product.name}</h1>
            <p className="text-gray-500">{product.id}</p>
          </div>
          <span className="px-3 py-1 rounded-full text-sm font-medium bg-blue-100 text-blue-800">
            {product.status}
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
            <p className="text-sm text-gray-600">Kind</p>
            <p className="font-semibold">{product.kind}</p>
          </div>
          <div>
            <p className="text-sm text-gray-600">Owner</p>
            <p className="font-semibold">{product.owner}</p>
          </div>
          <div>
            <p className="text-sm text-gray-600">Release Readiness</p>
            <p className="font-semibold">{(product.releaseReadinessScore * 100).toFixed(0)}%</p>
          </div>
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg">
        <div className="border-b border-gray-200">
          <nav className="flex space-x-8 px-6" aria-label="Tabs">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`py-4 px-1 border-b-2 font-medium text-sm ${
                  activeTab === tab.id
                    ? "border-blue-500 text-blue-600"
                    : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </div>

        <div className="p-6">
          {activeTab === "overview" && (
            <div className="space-y-6">
              <div>
                <h3 className="text-lg font-semibold mb-3">Summary</h3>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="bg-gray-50 p-4 rounded-lg">
                    <p className="text-2xl font-bold text-gray-900">{product.surfaces.length}</p>
                    <p className="text-sm text-gray-600">Surfaces</p>
                  </div>
                  <div className="bg-gray-50 p-4 rounded-lg">
                    <p className="text-2xl font-bold text-gray-900">{product.adapters.length}</p>
                    <p className="text-sm text-gray-600">Adapters</p>
                  </div>
                  <div className="bg-gray-50 p-4 rounded-lg">
                    <p className="text-2xl font-bold text-gray-900">{product.interactions.length}</p>
                    <p className="text-sm text-gray-600">Interactions</p>
                  </div>
                  <div className="bg-gray-50 p-4 rounded-lg">
                    <p className="text-2xl font-bold text-gray-900">{product.gates.length}</p>
                    <p className="text-sm text-gray-600">Gates</p>
                  </div>
                </div>
              </div>

              <div>
                <h3 className="text-lg font-semibold mb-3">Surface Health</h3>
                <div className="space-y-2">
                  {product.surfaces.map((surface) => (
                    <div key={surface.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                      <div>
                        <p className="font-medium">{surface.type}</p>
                        <p className="text-sm text-gray-600">
                          {surface.language} / {surface.runtime}
                        </p>
                      </div>
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${HEALTH_COLORS[surface.health]}`}>
                        {surface.health}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {activeTab === "surfaces" && (
            <div className="space-y-3">
              {product.surfaces.map((surface) => (
                <div key={surface.id} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-start justify-between mb-2">
                    <div>
                      <h4 className="font-semibold">{surface.type}</h4>
                      <p className="text-sm text-gray-500">{surface.path}</p>
                    </div>
                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${HEALTH_COLORS[surface.health]}`}>
                      {surface.health}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm">
                    <div>
                      <p className="text-gray-600">Language</p>
                      <p className="font-medium">{surface.language}</p>
                    </div>
                    <div>
                      <p className="text-gray-600">Runtime</p>
                      <p className="font-medium">{surface.runtime}</p>
                    </div>
                    <div>
                      <p className="text-gray-600">Build System</p>
                      <p className="font-medium">{surface.buildSystem}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {activeTab === "adapters" && (
            <div className="space-y-3">
              {product.adapters.map((adapter) => (
                <div key={adapter.id} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-start justify-between">
                    <div>
                      <h4 className="font-semibold">{adapter.type}</h4>
                      <p className="text-sm text-gray-500">{adapter.language}</p>
                    </div>
                    <span className="px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                      {adapter.status}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}

          {activeTab === "interactions" && (
            <div className="space-y-3">
              {product.interactions.map((interaction) => (
                <div key={interaction.id} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-start justify-between mb-2">
                    <div>
                      <h4 className="font-semibold">{interaction.contractId}</h4>
                      <p className="text-sm text-gray-500">
                        {interaction.provider} → {interaction.consumer}
                      </p>
                    </div>
                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${INTERACTION_STATUS_COLORS[interaction.status]}`}>
                      {interaction.status}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">Last used: {interaction.lastUsed}</p>
                </div>
              ))}
            </div>
          )}

          {activeTab === "gates" && (
            <div className="space-y-3">
              {product.gates.map((gate) => (
                <div key={gate.id} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-start justify-between mb-2">
                    <div>
                      <h4 className="font-semibold">{gate.name}</h4>
                      <p className="text-sm text-gray-500">{gate.type}</p>
                    </div>
                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${GATE_STATUS_COLORS[gate.status]}`}>
                      {gate.status}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">Last run: {gate.lastRun}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
