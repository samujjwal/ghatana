/**
 * Product Registry View - displays all registered products with release-readiness scores.
 *
 * @doc.type component
 * @doc.purpose Show product registry with release-readiness scores and quick actions
 * @doc.layer studio
 */

import React, { useState } from "react";

interface Product {
  id: string;
  name: string;
  kind: string;
  owner: string;
  releaseReadinessScore: number;
  status: "active" | "disabled" | "pilot";
  lastUpdated: string;
  surfaces: SurfaceSummary[];
}

interface SurfaceSummary {
  type: string;
  language: string;
  runtime: string;
}

interface ProductRegistryViewProps {
  products: Product[];
  onSelectProduct: (productId: string) => void;
}

const STATUS_COLORS = {
  active: "bg-green-100 text-green-800",
  disabled: "bg-gray-100 text-gray-800",
  pilot: "bg-blue-100 text-blue-800",
} as const;

const SCORE_COLORS = {
  high: "text-green-600",
  medium: "text-yellow-600",
  low: "text-red-600",
} as const;

export function ProductRegistryView({ products, onSelectProduct }: ProductRegistryViewProps) {
  const [filter, setFilter] = useState<"all" | "active" | "disabled" | "pilot">("all");
  const [searchQuery, setSearchQuery] = useState("");

  const filteredProducts = products.filter((product) => {
    const matchesFilter = filter === "all" || product.status === filter;
    const matchesSearch =
      product.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      product.id.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  const getScoreColor = (score: number) => {
    if (score >= 0.8) return SCORE_COLORS.high;
    if (score >= 0.5) return SCORE_COLORS.medium;
    return SCORE_COLORS.low;
  };

  return (
    <div className="product-registry-view">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Product Registry</h2>
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
            onClick={() => setFilter("active")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "active" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Active
          </button>
          <button
            onClick={() => setFilter("pilot")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "pilot" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Pilot
          </button>
          <button
            onClick={() => setFilter("disabled")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "disabled" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Disabled
          </button>
        </div>
      </div>

      <div className="mb-4">
        <input
          type="text"
          placeholder="Search products..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredProducts.map((product) => (
          <div
            key={product.id}
            className="bg-white border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer"
            onClick={() => onSelectProduct(product.id)}
          >
            <div className="flex items-start justify-between mb-2">
              <div>
                <h3 className="font-semibold text-gray-900">{product.name}</h3>
                <p className="text-sm text-gray-500">{product.id}</p>
              </div>
              <span
                className={`px-2 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[product.status]}`}
              >
                {product.status}
              </span>
            </div>

            <div className="mb-3">
              <div className="flex items-center justify-between text-sm mb-1">
                <span className="text-gray-600">Release Readiness</span>
                <span className={`font-semibold ${getScoreColor(product.releaseReadinessScore)}`}>
                  {(product.releaseReadinessScore * 100).toFixed(0)}%
                </span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className={`h-2 rounded-full ${
                    product.releaseReadinessScore >= 0.8
                      ? "bg-green-500"
                      : product.releaseReadinessScore >= 0.5
                      ? "bg-yellow-500"
                      : "bg-red-500"
                  }`}
                  style={{ width: `${product.releaseReadinessScore * 100}%` }}
                />
              </div>
            </div>

            <div className="text-sm text-gray-600 mb-2">
              <p>
                <span className="font-medium">Kind:</span> {product.kind}
              </p>
              <p>
                <span className="font-medium">Owner:</span> {product.owner}
              </p>
            </div>

            <div className="flex flex-wrap gap-1">
              {product.surfaces.slice(0, 3).map((surface, idx) => (
                <span
                  key={idx}
                  className="px-2 py-1 bg-gray-100 text-gray-700 rounded text-xs"
                >
                  {surface.language}
                </span>
              ))}
              {product.surfaces.length > 3 && (
                <span className="px-2 py-1 bg-gray-100 text-gray-700 rounded text-xs">
                  +{product.surfaces.length - 3}
                </span>
              )}
            </div>
          </div>
        ))}
      </div>

      {filteredProducts.length === 0 && (
        <div className="text-center py-12 text-gray-500">
          <p>No products found matching your criteria.</p>
        </div>
      )}
    </div>
  );
}
