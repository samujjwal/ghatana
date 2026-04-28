/**
 * A/B Testing Dashboard Page (Admin-Only)
 *
 * Operator UI for ABTestingEvaluationService to register variants,
 * view results, promote winners, and pause experiments.
 * Wired to the real A/B Testing API via TanStack Query.
 *
 * @doc.type component
 * @doc.purpose Admin-only A/B testing management UI
 * @doc.layer product
 * @doc.pattern Admin Component
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  TrendingUp as TrendingUpIcon,
  Award as PromoteIcon,
  Eye as ViewIcon,
  Plus as AddIcon,
  CheckCircle as WinnerIcon,
  X as CloseIcon,
  Loader2 as SpinnerIcon,
  AlertCircle as ErrorIcon,
  PauseCircle as PauseIcon,
  RefreshCw as RefreshIcon,
} from 'lucide-react';
import {
  listExperiments,
  createExperiment,
  promoteWinner,
  pauseExperiment,
  type Experiment,
  type CreateVariantRequest,
} from '../../services/admin/abTestingApi';

interface ABTestingDashboardPageProps {
  className?: string;
}

export function ABTestingDashboardPage({ className }: ABTestingDashboardPageProps) {
  const queryClient = useQueryClient();

  const [selectedExperiment, setSelectedExperiment] = useState<Experiment | null>(null);
  const [viewDialogOpen, setViewDialogOpen] = useState(false);
  const [promoteDialogOpen, setPromoteDialogOpen] = useState(false);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [selectedVariantId, setSelectedVariantId] = useState<string>('');
  const [promoteReason, setPromoteReason] = useState('');
  const [newExperimentName, setNewExperimentName] = useState('');
  const [newExperimentDescription, setNewExperimentDescription] = useState('');
  const [newExperimentPromptName, setNewExperimentPromptName] = useState('');
  const [newExperimentVariantA, setNewExperimentVariantA] = useState('');
  const [newExperimentVariantB, setNewExperimentVariantB] = useState('');

  const {
    data: listResponse,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['admin', 'ab-experiments'],
    queryFn: () => listExperiments(),
    staleTime: 30_000,
  });

  const experiments = listResponse?.items;

  const createMutation = useMutation({
    mutationFn: (req: CreateVariantRequest) => createExperiment(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'ab-experiments'] });
      setCreateDialogOpen(false);
      setNewExperimentName('');
      setNewExperimentDescription('');
      setNewExperimentPromptName('');
      setNewExperimentVariantA('');
      setNewExperimentVariantB('');
    },
  });

  const promoteMutation = useMutation({
    mutationFn: ({
      experimentId,
      variantId,
      reason,
    }: {
      experimentId: string;
      variantId: string;
      reason: string;
    }) => promoteWinner(experimentId, variantId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'ab-experiments'] });
      setPromoteDialogOpen(false);
      setSelectedExperiment(null);
      setSelectedVariantId('');
      setPromoteReason('');
    },
  });

  const pauseMutation = useMutation({
    mutationFn: (experimentId: string) => pauseExperiment(experimentId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'ab-experiments'] });
    },
  });

  const handleView = (experiment: Experiment) => {
    setSelectedExperiment(experiment);
    setViewDialogOpen(true);
  };

  const handlePromote = (experiment: Experiment) => {
    setSelectedExperiment(experiment);
    setSelectedVariantId(experiment.variants[0]?.variantId ?? '');
    setPromoteDialogOpen(true);
  };

  const handleConfirmPromote = () => {
    if (!selectedExperiment) return;
    promoteMutation.mutate({
      experimentId: selectedExperiment.id,
      variantId: selectedVariantId,
      reason: promoteReason,
    });
  };

  const handleCreateExperiment = () => {
    if (!newExperimentName.trim() || !newExperimentPromptName.trim()) return;
    createMutation.mutate({
      experimentName: newExperimentName.trim(),
      description: newExperimentDescription.trim(),
      promptName: newExperimentPromptName.trim(),
      variantA: newExperimentVariantA.trim(),
      variantB: newExperimentVariantB.trim(),
    });
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
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-zinc-100 mb-2 flex items-center gap-2">
              <TrendingUpIcon size={24} className="text-blue-400" />
              A/B Testing Dashboard
            </h1>
            <p className="text-sm text-zinc-400">
              Manage A/B testing experiments, view results, and promote winning variants.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void refetch()}
              disabled={isLoading}
              className="inline-flex items-center gap-1.5 px-3 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
              title="Refresh experiments"
            >
              <RefreshIcon size={14} className={isLoading ? 'animate-spin' : ''} />
            </button>
            <button
              type="button"
              onClick={() => setCreateDialogOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors"
            >
              <AddIcon size={16} />
              Create Experiment
            </button>
          </div>
        </div>

        <div className="bg-blue-900/20 border border-blue-800 rounded-lg p-4 text-blue-400 text-sm">
          This page is for administrators only. Changes here affect AI model selection across all tenants.
        </div>

        {isLoading && (
          <div className="flex items-center justify-center py-16">
            <SpinnerIcon size={32} className="animate-spin text-blue-400" />
          </div>
        )}

        {isError && (
          <div className="flex items-center gap-3 p-4 bg-red-900/20 border border-red-800 rounded-lg text-red-400">
            <ErrorIcon size={18} className="shrink-0" />
            <p className="text-sm">
              {error instanceof Error ? error.message : 'Failed to load experiments. Please retry.'}
            </p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="ml-auto text-sm underline underline-offset-2 hover:no-underline"
            >
              Retry
                  on>
          </div>
        )}

          <div className="flex flex-col items-center justify-center py-16 text-zinc-500 space-y-3">
            <TrendingUpIcon size={40} className="opacity-30" />
            <p className="text-sm">No experiments found.</p>
            <button
              type="button"
              onClick={() => setCreateDialogOpen(true)}
              className="text-sm text-blue-400 hover:text-blue-300 underline underline-offset-2"
            >
              Create your first experiment
            </button>
          </div>
        )}

                                 & experiments && experiments.length > 0 && (
          <div className="space-y-4">
            {experiments.map((experiment) => (
              <div
                key={experiment.id}
                className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 hover:border-zinc-700 transition-colors"
              >
                <div className="flex ite                <div className="flex ite                <div className="   <h2 className="text-lg font-semibold text-zinc-100 mb-1">{experiment.name}</h2>
                    <p className="text-sm text-zinc-400">{experiment.description}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-flex items-center px-2 py-1 rounded text-xs font-medium capitalize ${getStatusColor(experiment.status)}`}
                    >
                      {experiment.status}
                    </span>
                    {experiment.winnerId && (
                      <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-purple-900/30 text-purple-400 text-xs font-medium">
                        <WinnerIcon size={12} />
                        Winner: {experiment.variants.find((v) => v.variantId === experiment.winnerId)?.variantName}
                      </span>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                  {experiment.variants.map((variant) => (
                    <div
                      key={variant.variantId}
                      className={`p-3 rounded-lg border ${
                        experiment.winnerId === variant.variantId
                          ? 'border-purple-500 bg-purple-900/10'
                          : 'border-zinc-800 bg-zinc-800/50'
                      }`}
                    >
                      <div className="text-sm font-medium text-zinc-300 mb-2">{variant.variantName}</div>
                      <div className="space-y-1 text-xs text-zinc-400">
                        <div className="flex justify-between">
                          <span>Conversions:</span>
                          <span className="text-zinc-300">{(variant.conversionRate * 100).toFixed(1)}%</span>
                        </div>
                        <div className="flex justify-between">
                          <span>Quality:</span>
                          <span className="text-zinc-300">{variant.avgQuality.toFixed(1)}</span>
                        </div>
                        {variant.statisticalSignificance && (
                          <div className="text-emerald-400">Statistically significant</div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                <div className="flex items-center justify-between pt-3 border-t border-zinc-800">
                  <div className="text-xs text-zinc-500">
                    Created: {new Date(experiment.createdAt).toLocaleString()}
                    {experiment.endedAt && ` – Ended: ${new Date(experiment.endedAt).toLocaleString()}`}
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
                    {experiment.status === 'running' && (
                      <button
                        type="button"
                        onClick={() => pauseMutation.mutate(experiment.id)}
                        disabled={pauseMutation.isPending}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-amber-700/40 hover:bg-amber-700/60 text-amber-300 text-xs font-medium rounded-lg transition-colors disabled:opacity-50"
                      >
                        {pauseMutation.isPending ? (
                          <SpinnerIcon size={14} className="animate-spin" />
                        ) : (
                          <PauseIcon size={14} />
                        )}
                        Pause
                      </button>
                    )}                    {experiment.status === 'completed' && !experiment.winnerId && (                      <button
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
        )}
      </div>

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
                  <span className="ml-2 text         ">{selectedExperiment.name}</span>
                </div>
                <div>
                  <span className="text-zinc-400">Status:</span>
                  <span className={`ml-2 capitalize ${getStatusColor(selectedExperiment.status)}`}>
                    {selectedExperiment.status}
                  </span>
                </div>
              </div>
              <div>
                <h3 className="text-base font-semibold text-zinc-100 mb-3">Variant Performance</h3>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b border-zinc-800">
                        <th className="text-left py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">Variant</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">Impressions</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">Conv. Rate</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">Quality</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">Cost</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-zinc-400 uppercase tracking-wider">Significance</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedExperiment.variants.map((variant) => (
                        <tr key={variant.variantId} className="border-b border-zinc-800">
                          <td className="py-2 px-3 text-sm text-zinc-300">
                            {variant.variantName}
                            {selectedExperiment.winnerId === variant.variantId && (
                              <span className="ml-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-purple-900/30 text-purple-400 text-xs">
                                <WinnerIcon size={10} />
                                Winner
                              </span>
                            )}
                          </td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">{variant.impressions.toLocaleString()}</td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">{(variant.conversionRate * 100).toFixed(1)}%</td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">{variant.avgQuality.toFixed(1)}</td>
                          <td className="py-2 px-3 text-sm text-zinc-300 text-right">${variant.avgCost.toFixed(3)}</td>
                          <td className="py-2 px-3 text-sm text-right">
                            {variant.statisticalSignificance ? (
                              <span className="text-emerald-400">yes (p={variant.pValue?.toFixed(3)})</span>
                            ) : (
                              <span className="text-zinc-500">no</span>
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
                Select the winning variant. It will be promoted as the production default.
              </p>
              <div className="space-y-2">
                {selectedExperiment.variants.map((variant) => (
                  <button
                    key={variant.variantId}
                    type="button"
                    onClick={() => setSelectedVariantId(variant.variantId)}
                    className={`w-full p-3 rounded-lg border text-left transition-colors ${
                      selectedVariantId === variant.variantId
                        ? 'border-purple-500 bg-purple-900/20'
                        : 'border-zinc-800 bg-zinc-800/50 hover:border-zinc-700'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="text-sm font-medium text-zinc-300">{variant.variantName}</div>
                      <div className="text-xs text-zinc-400">{(variant.conversionRate * 100).toFixed(1)}% conversion</div>
                    </div>
                  </button>
                ))}
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-400 mb-1" htmlFor="promote-reason">
                  Reason (optional)
                </label>
                <input
                  id="promote-reason"
                  type="text"
                  value={promoteReason}
                  onChange={(e) => setPromoteReason(e.target.value)}
                  placeholder="Why are you promoting this variant?"
                  className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 text-zinc-300 text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-purple-500"
                />
              </div>
              {promoteMutation.isError && (
                <p className="text-sm text-red-400">
                  {promoteMutation.error instanceof Error ? promoteMutation.error.message : 'Failed to promote winner.'}
                </p>
              )}
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setPromoteDialogOpen(false)}
                disabled={promoteMutation.isPending}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmPromote}
                className="inline-flex items-center gap-2 px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
              >
                                           && <SpinnerIcon size={14} className="animate-spin" />}
                Promote Winner
              </button>
            </div>
          </div>
        </div>
      )}

      {createDialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Create Experiment</h2>
              <button
                type="button"
                onClick={() => setCreateDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-4">
              <div>
                <label className="block text-xs font-medium text-zinc-400 mb-1" htmlFor="exp-name">
                  Experiment Name <span className="text-red-400">*</span>
                </label>
                <input
                  id="exp-name"
                  type="text"
                  value={newExperimentName}
                  onChange={(e) => setNewExperimentName(e.target.value)}
                  placeholder="e.g. Prompt Template A/B Test"
                  className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 text-zinc-300 text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-400 mb-1" htmlFor="exp-desc">
                  Description
                </label>
                <textarea
                  id="exp-desc"
                  value={newExperimentDescription}
                  onChange={(e) => setNewExperimentDescription(e.target.value)}
                  placeholder="What are you testing?"
                  rows={3}
                  className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 text-zinc-300 text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-400 mb-1" htmlFor="exp-prompt">
                  Prompt Name <span className="text-red-400">*</span>
                </label>
                <input
                  id="exp-prompt"
                  type="text"
                  value={newExperimentPromptName}
                  onChange={(e) => setNewExperimentPromptName(e.target.value)}
                  placeholder="e.g. requirement-gen-v1"
                  className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 text-zinc-300 text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-400 mb-1" htmlFor="exp-va">
                  Variant A (Baseline)
                </label>
                <input
                  id="exp-va"
                  type="text"
                  value={newExperimentVariantA}
                  onChange={(e) => setNewExperimentVariantA(e.target.value)}
                  placeholder="e.g. template-a"
                  className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 text-zinc-300 text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-400 mb-1" htmlFor="exp-vb">
                  Variant B (Challenger)
                </label>
                <input
                  id="exp-vb"
                  type="text"
                  value={newExperimentVariantB}
                  onChange={(e) => setNewExperimentVariantB(e.target.value)}
                  placeholder="e.g. template-b"
                  className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 text-zinc-300 text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              {createMutation.isError && (
                <p className="text-sm text-red-400">
                  {createMutation.error instanceof Error ? createMutation.error.message : 'Failed to create experiment.'}
                </p>
              )}
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setCreateDialogOpen(false)}
                disabled={createMutation.isPending}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleCreateExperiment}
                disabled={!newExperimentName.trim() || !newExperimentPromptName.trim() || createMutation.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
              >
                {createMutation.isPending && <SpinnerIcon size={14} className="animate-spin" />}
                Create
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default ABTestingDashboardPage;
