/**
 * A/B Testing Dashboard Page (Admin-Only)
 *
 * Operator UI for ABTestingEvaluationService to register variants,
 * view results, and promote winners.
 *
 * @doc.type component
 * @doc.purpose Admin-only A/B testing management UI
 * @doc.layer product
 * @doc.pattern Admin Component
 */

import React, { useState } from 'react';
import {
  TrendingUp as TrendingUpIcon,
  Award as PromoteIcon,
  Eye as ViewIcon,
  Plus as AddIcon,
  CheckCircle as WinnerIcon,
  XCircle as LoserIcon,
  X as CloseIcon,
} from 'lucide-react';

interface VariantMetrics {
  variantId: string;
  variantName: string;
  impressions: number;
  conversions: number;
  conversionRate: number;
  avgResponseTime: number;
  avgCost: number;
  avgQuality: number;
  statisticalSignificance: boolean;
  pValue?: number;
  confidenceInterval?: [number, number];
}

interface Experiment {
  id: string;
  name: string;
  description: string;
  status: 'running' | 'completed' | 'paused';
  createdAt: string;
  endedAt?: string;
  variants: VariantMetrics[];
  winner?: string;
}

interface ABTestingDashboardPageProps {
  className?: string;
}

export function ABTestingDashboardPage({ className }: ABTestingDashboardPageProps) {
  const [selectedExperiment, setSelectedExperiment] = useState<Experiment | null>(null);
  const [viewDialogOpen, setViewDialogOpen] = useState(false);
  const [promoteDialogOpen, setPromoteDialogOpen] = useState(false);

  // Mock data - in production, this would come from the ABTestingEvaluationService API
  const mockExperiments: Experiment[] = [
    {
      id: '1',
      name: 'Prompt Template A/B Test',
      description: 'Comparing two prompt templates for requirement generation',
      status: 'running',
      createdAt: '2026-04-27T10:00:00Z',
      variants: [
        {
          variantId: 'v1',
          variantName: 'Template A (Baseline)',
          impressions: 1000,
          conversions: 850,
          conversionRate: 0.85,
          avgResponseTime: 2.5,
          avgCost: 0.01,
          avgQuality: 8.5,
          statisticalSignificance: true,
          pValue: 0.02,
          confidenceInterval: [0.82, 0.88],
        },
        {
          variantId: 'v2',
          variantName: 'Template B (Improved)',
          impressions: 1000,
          conversions: 920,
          conversionRate: 0.92,
          avgResponseTime: 2.3,
          avgCost: 0.009,
          avgQuality: 9.0,
          statisticalSignificance: true,
          pValue: 0.01,
          confidenceInterval: [0.89, 0.95],
        },
      ],
    },
    {
      id: '2',
      name: 'Code Generation Model Test',
      description: 'Testing different code generation models',
      status: 'completed',
      createdAt: '2026-04-20T10:00:00Z',
      endedAt: '2026-04-25T10:00:00Z',
      variants: [
        {
          variantId: 'v1',
          variantName: 'GPT-4',
          impressions: 500,
          conversions: 450,
          conversionRate: 0.9,
          avgResponseTime: 3.0,
          avgCost: 0.05,
          avgQuality: 9.2,
          statisticalSignificance: true,
          pValue: 0.001,
          confidenceInterval: [0.87, 0.93],
        },
        {
          variantId: 'v2',
          variantName: 'Claude 3',
          impressions: 500,
          conversions: 470,
          conversionRate: 0.94,
          avgResponseTime: 2.8,
          avgCost: 0.048,
          avgQuality: 9.4,
          statisticalSignificance: true,
          pValue: 0.0005,
          confidenceInterval: [0.91, 0.97],
        },
      ],
      winner: 'v2',
    },
  ];

  const handleView = (experiment: Experiment) => {
    setSelectedExperiment(experiment);
    setViewDialogOpen(true);
  };

  const handlePromote = (experiment: Experiment) => {
    setSelectedExperiment(experiment);
    setPromoteDialogOpen(true);
  };

  const handleConfirmPromote = async () => {
    if (!selectedExperiment) return;
    // In production, this would call the ABTestingEvaluationService API to promote the winner
    console.log('Promoting winner for experiment:', selectedExperiment.id);
    setPromoteDialogOpen(false);
    setSelectedExperiment(null);
  };

  const getStatusColor = (status: Experiment['status']) => {
    switch (status) {
      case 'running':
        return 'bg-emerald-900/30 text-emerald-400';
      case 'completed':
        return 'bg-blue-900/30 text-blue-400';
      case 'paused':
        return 'bg-amber-900/30 text-amber-400';
      default:
        return 'bg-zinc-800 text-zinc-400';
    }
  };

  return (
    <div className={className}>
      <div className="p-6 space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-zinc-100 mb-2">
              A/B Testing Dashboard
            </h1>
            <p className="text-sm text-zinc-400">
              Manage A/B testing experiments, view results, and promote winning variants.
            </p>
          </div>
          <button
            type="button"
            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors"
          >
            <AddIcon size={16} />
            Create Experiment
          </button>
        </div>

        {/* Alert */}
        <div className="bg-blue-900/20 border border-blue-800 rounded-lg p-4 text-blue-400">
          This page is for administrators only. Changes here affect AI model selection across all tenants.
        </div>

        {/* Experiments List */}
        <div className="space-y-4">
          {mockExperiments.map((experiment) => (
            <div
              key={experiment.id}
              className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 hover:border-zinc-700 transition-colors"
            >
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h2 className="text-lg font-semibold text-zinc-100 mb-1">
                    {experiment.name}
                  </h2>
                  <p className="text-sm text-zinc-400">{experiment.description}</p>
                </div>
                <div className="flex items-center gap-2">
                  <span
                    className={`inline-flex items-center px-2 py-1 rounded text-xs font-medium capitalize ${getStatusColor(
                      experiment.status
                    )}`}
                  >
                    {experiment.status}
                  </span>
                  {experiment.winner && (
                    <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-purple-900/30 text-purple-400 text-xs font-medium">
                      <WinnerIcon size={12} />
                      Winner: {experiment.variants.find(v => v.variantId === experiment.winner)?.variantName}
                    </span>
                  )}
                </div>
              </div>

              {/* Variants Summary */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                {experiment.variants.map((variant) => (
                  <div
                    key={variant.variantId}
                    className={`p-3 rounded-lg border ${
                      experiment.winner === variant.variantId
                        ? 'border-purple-500 bg-purple-900/10'
                        : 'border-zinc-800 bg-zinc-800/50'
                    }`}
                  >
                    <div className="text-sm font-medium text-zinc-300 mb-2">
                      {variant.variantName}
                    </div>
                    <div className="space-y-1 text-xs text-zinc-400">
                      <div className="flex justify-between">
                        <span>Conversions:</span>
                        <span className="text-zinc-300">{variant.conversionRate.toFixed(1)}%</span>
                      </div>
                      <div className="flex justify-between">
                        <span>Quality:</span>
                        <span className="text-zinc-300">{variant.avgQuality.toFixed(1)}</span>
                      </div>
                      {variant.statisticalSignificance && (
                        <div className="text-emerald-400">✓ Statistically significant</div>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {/* Actions */}
              <div className="flex items-center justify-between pt-3 border-t border-zinc-800">
                <div className="text-xs text-zinc-500">
                  Created: {new Date(experiment.createdAt).toLocaleString()}
                  {experiment.endedAt && ` • Ended: ${new Date(experiment.endedAt).toLocaleString()}`}
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => handleView(experiment)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-xs font-medium rounded-lg transition-colors"
                  >
                    <ViewIcon size={14} />
                    View Details
                  </button>
                  {experiment.status === 'completed' && !experiment.winner && (
                    <button
                      type="button"
                      onClick={() => handlePromote(experiment)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-medium rounded-lg transition-colors"
                    >
                      <PromoteIcon size={14} />
                      Promote Winner
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* View Details Dialog */}
      {viewDialogOpen && selectedExperiment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-4xl w-full max-h-[80vh] overflow-auto">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Experiment Details</h2>
              <button
                type="button"
                onClick={() => setViewDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-4">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-zinc-400">Name:</span>
                  <span className="ml-2 text-zinc-300">{selectedExperiment.name}</span>
                </div>
                <div>
                  <span className="text-zinc-400">Status:</span>
                  <span className={`ml-2 capitalize ${getStatusColor(selectedExperiment.status)}`}>
                    {selectedExperiment.status}
                  </span>
                </div>
              </div>

              <div>
                <h3 className="text-md font-semibold text-zinc-100 mb-3">Variant Performance</h3>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b border-zinc-800">
                        <th className="text-left py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                          Variant
                        </th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                          Impressions
                        </th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                          Conversion Rate
                        </th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                          Quality
                        </th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                          Cost
                        </th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                          Significance
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedExperiment.variants.map((variant) => (
                        <tr key={variant.variantId} className="border-b border-zinc-800">
                          <td className="py-2 px-3 text-sm text-zinc-300">
                            {variant.variantName}
                            {selectedExperiment.winner === variant.variantId && (
                              <span className="ml-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-purple-900/30 text-purple-400 text-xs">
                                <WinnerIcon size={10} />
                                Winner
                              </span>
                            )}
                          </td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">
                            {variant.impressions.toLocaleString()}
                          </td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">
                            {(variant.conversionRate * 100).toFixed(1)}%
                          </td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">
                            {variant.avgQuality.toFixed(1)}
                          </td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">
                            ${variant.avgCost.toFixed(3)}
                          </td>
                          <td className="py-2 px-3 text-sm text-right">
                            {variant.statisticalSignificance ? (
                              <span className="text-emerald-400">✓ (p={variant.pValue?.toFixed(3)})</span>
                            ) : (
                              <span className="text-zinc-500">-</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
            <div className="flex justify-end p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setViewDialogOpen(false)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Promote Winner Dialog */}
      {promoteDialogOpen && selectedExperiment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Promote Winner</h2>
              <button
                type="button"
                onClick={() => setPromoteDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-4">
              <p className="text-sm text-zinc-300">
                Select the winning variant for this experiment. The selected variant will be promoted as the default for production use.
              </p>
              <div className="space-y-2">
                {selectedExperiment.variants.map((variant) => (
                  <button
                    key={variant.variantId}
                    type="button"
                    className="w-full p-3 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-800/50 text-left transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <div className="text-sm font-medium text-zinc-300">{variant.variantName}</div>
                      <div className="text-xs text-zinc-400">
                        {(variant.conversionRate * 100).toFixed(1)}% conversion
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setPromoteDialogOpen(false)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmPromote}
                className="px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white rounded-lg text-sm font-medium transition-colors"
              >
                Promote Winner
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default ABTestingDashboardPage;
