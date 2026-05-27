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
import { useTranslation } from '@ghatana/i18n';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';

interface ABTestingDashboardPageProps {
  className?: string;
}

export function ABTestingDashboardPage({ className }: ABTestingDashboardPageProps) {
  const { t } = useTranslation('common');
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

  const experiments = listResponse?.items ?? [];

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
        return 'bg-info-bg text-info-color';
      case 'paused':
        return 'bg-warning-bg text-warning-color';
      default:
        return 'bg-surface text-fg-muted';
    }
  };

  return (
    <div className={className} data-testid="ab-testing-dashboard">
      <div className="p-6 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-fg-muted mb-2 flex items-center gap-2">
              <TrendingUpIcon size={24} className="text-info-color" />
              A/B Testing Dashboard
            </h1>
            <p className="text-sm text-fg-muted">
              Manage A/B testing experiments, view results, and promote winning variants.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              onClick={() => void refetch()}
              disabled={isLoading}
              className="inline-flex items-center gap-1.5 px-3 py-2 bg-surface hover:bg-surface-muted text-fg-muted text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
              title={t('admin.ab.refreshExperiments')}
              variant="ghost"
              size="sm"
            >
              <RefreshIcon size={14} className={isLoading ? 'animate-spin' : ''} />
            </Button>
            <Button
              type="button"
              onClick={() => setCreateDialogOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-info-bg text-white text-sm font-medium rounded-lg transition-colors"
              data-testid="btn-create-experiment"
            >
              <AddIcon size={16} />
              Create Experiment
            </Button>
          </div>
        </div>

        <div className="bg-info-bg border border-info-border rounded-lg p-4 text-info-color text-sm">
          This page is for administrators only. Changes here affect AI model selection across all tenants.
        </div>

        {isLoading && (
          <div className="flex items-center justify-center py-16" data-testid="loading-spinner">
            <SpinnerIcon size={32} className="animate-spin text-info-color" />
          </div>
        )}

        {isError && (
          <div
            className="flex items-center gap-3 p-4 bg-destructive-bg border border-destructive-border rounded-lg text-destructive"
            data-testid="error-message"
          >
            <ErrorIcon size={18} className="shrink-0" />
            <p className="text-sm">
              {error instanceof Error ? error.message : 'Failed to load experiments. Please retry.'}
            </p>
          </div>
        )}

        {!isLoading && !isError && experiments.length === 0 && (
          <div
            className="flex flex-col items-center justify-center py-16 text-fg-muted space-y-3"
            data-testid="empty-state"
          >
            <TrendingUpIcon size={40} className="opacity-30" />
            <p className="text-sm">No experiments found.</p>
            <Button
              type="button"
              onClick={() => setCreateDialogOpen(true)}
              className="text-sm text-info-color hover:text-info-color underline underline-offset-2"
              variant="link"
            >
              Create your first experiment
            </Button>
          </div>
        )}

        {!isLoading && !isError && experiments.length > 0 && (
          <div className="space-y-4">
            {experiments.map((experiment) => (
              <div
                key={experiment.id}
                className="bg-surface border border-border rounded-xl p-5 hover:border-border transition-colors"
                data-testid={`experiment-row-${experiment.id}`}
              >
                <div className="flex items-start justify-between mb-4">
                  <div>
                    <h2 className="text-lg font-semibold text-fg-muted mb-1">{experiment.name}</h2>
                    <p className="text-sm text-fg-muted">{experiment.description}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-flex items-center px-2 py-1 rounded text-xs font-medium capitalize ${getStatusColor(experiment.status)}`}
                    >
                      {experiment.status}
                    </span>
                    {experiment.winnerId && (
                      <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-info-bg text-info-color text-xs font-medium">
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
                          ? 'border-info-border bg-info-bg'
                          : 'border-border bg-surface/50'
                      }`}
                    >
                      <div className="text-sm font-medium text-fg-muted mb-2">{variant.variantName}</div>
                      <div className="space-y-1 text-xs text-fg-muted">
                        <div className="flex justify-between">
                          <span>Conversions:</span>
                          <span className="text-fg-muted">{(variant.conversionRate * 100).toFixed(1)}%</span>
                        </div>
                        <div className="flex justify-between">
                          <span>Quality:</span>
                          <span className="text-fg-muted">{variant.avgQualityScore.toFixed(1)}</span>
                        </div>
                        {variant.statisticalSignificance && (
                          <div className="text-emerald-400">Statistically significant</div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                <div className="flex items-center justify-between pt-3 border-t border-border">
                  <div className="text-xs text-fg-muted">
                    Created: {new Date(experiment.createdAt).toLocaleString()}
                    {experiment.endedAt && ` – Ended: ${new Date(experiment.endedAt).toLocaleString()}`}
                    {experiment.reversible && experiment.rollbackTargetWinnerId && (
                      <span className="ml-3" data-testid={`rollback-target-${experiment.id}`}>
                        Rollback: {experiment.variants.find((variant) => variant.variantId === experiment.rollbackTargetWinnerId)?.variantName ?? experiment.rollbackTargetWinnerId}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      onClick={() => handleView(experiment)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-surface hover:bg-surface-muted text-fg-muted text-xs font-medium rounded-lg transition-colors"
                      data-testid={`btn-view-${experiment.id}`}
                      variant="ghost"
                      size="sm"
                    >
                      <ViewIcon size={14} />
                      View Details
                    </Button>
                    {experiment.status === 'running' && (
                      <Button
                        type="button"
                        onClick={() => pauseMutation.mutate(experiment.id)}
                        disabled={pauseMutation.isPending}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-warning-bg hover:bg-warning-bg text-warning-color text-xs font-medium rounded-lg transition-colors disabled:opacity-50"
                        data-testid={`btn-pause-${experiment.id}`}
                        variant="soft"
                        size="sm"
                      >
                        {pauseMutation.isPending ? (
                          <SpinnerIcon size={14} className="animate-spin" />
                        ) : (
                          <PauseIcon size={14} />
                        )}
                        Pause
                      </Button>
                    )}
                    {experiment.status === 'completed' && !experiment.winnerId && (
                      <Button
                        type="button"
                        onClick={() => handlePromote(experiment)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-info-bg hover:bg-info-bg text-white text-xs font-medium rounded-lg transition-colors"
                        data-testid={`btn-promote-${experiment.id}`}
                        size="sm"
                      >
                        <PromoteIcon size={14} />
                        Promote Winner
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {viewDialogOpen && selectedExperiment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" data-testid="view-dialog">
          <div className="bg-surface border border-border rounded-xl max-w-4xl w-full max-h-[80vh] overflow-auto">
            <div className="flex items-center justify-between p-4 border-b border-border">
              <h2 className="text-lg font-semibold text-fg-muted">Experiment Details</h2>
              <Button
                type="button"
                onClick={() => setViewDialogOpen(false)}
                className="p-1 rounded hover:bg-surface text-fg-muted hover:text-fg-muted"
                variant="ghost"
                size="sm"
                aria-label={t('admin.ab.closeExperimentDetails')}
              >
                <CloseIcon size={20} />
              </Button>
            </div>
            <div className="p-4 space-y-4">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-fg-muted">Name:</span>
                  <span className="ml-2 text         ">{selectedExperiment.name}</span>
                </div>
                <div>
                  <span className="text-fg-muted">Status:</span>
                  <span className={`ml-2 capitalize ${getStatusColor(selectedExperiment.status)}`}>
                    {selectedExperiment.status}
                  </span>
                </div>
                {selectedExperiment.reversible && selectedExperiment.rollbackTargetWinnerId && (
                  <div>
                    <span className="text-fg-muted">Rollback:</span>
                    <span className="ml-2 text-fg-muted">
                      {selectedExperiment.variants.find((variant) => variant.variantId === selectedExperiment.rollbackTargetWinnerId)?.variantName
                        ?? selectedExperiment.rollbackTargetWinnerId}
                    </span>
                  </div>
                )}
              </div>
              <div>
                <h3 className="text-base font-semibold text-fg-muted mb-3">Variant Performance</h3>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b border-border">
                        <th className="text-left py-2 px-3 text-xs font-medium text-fg-muted uppercase tracking-wider">Variant</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-fg-muted uppercase tracking-wider">Impressions</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-fg-muted uppercase tracking-wider">Conv. Rate</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-fg-muted uppercase tracking-wider">Quality</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-fg-muted uppercase tracking-wider">Cost</th>
                        <th className="text-right py-2 px-3 text-xs font-medium text-fg-muted uppercase tracking-wider">Significance</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedExperiment.variants.map((variant) => (
                        <tr key={variant.variantId} className="border-b border-border">
                          <td className="py-2 px-3 text-sm text-fg-muted">
                            {variant.variantName}
                            {selectedExperiment.winnerId === variant.variantId && (
                              <span className="ml-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-info-bg text-info-color text-xs">
                                <WinnerIcon size={10} />
                                Winner
                              </span>
                            )}
                          </td>
                          <td className="py-2 px-3 text-sm text-fg-muted text-right">{variant.impressions.toLocaleString()}</td>
                          <td className="py-2 px-3 text-sm text-fg-muted text-right">{(variant.conversionRate * 100).toFixed(1)}%</td>
                          <td className="py-2 px-3 text-sm text-fg-muted text-right">{variant.avgQualityScore.toFixed(1)}</td>
                          <td className="py-2 px-3 text-sm text-fg-muted text-right">${variant.avgCostUsd.toFixed(3)}</td>
                          <td className="py-2 px-3 text-sm text-right">
                            {variant.statisticalSignificance ? (
                              <span className="text-emerald-400">yes (p={variant.pValue?.toFixed(3)})</span>
                            ) : (
                              <span className="text-fg-muted">no</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
            <div className="flex justify-end p-4 border-t border-border">
              <Button
                type="button"
                onClick={() => setViewDialogOpen(false)}
                className="px-4 py-2 bg-surface hover:bg-surface-muted text-fg-muted rounded-lg text-sm font-medium transition-colors"
                variant="ghost"
              >
                Close
              </Button>
            </div>
          </div>
        </div>
      )}

      {promoteDialogOpen && selectedExperiment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" data-testid="promote-dialog">
          <div className="bg-surface border border-border rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-border">
              <h2 className="text-lg font-semibold text-fg-muted">Promote Winner</h2>
              <Button
                type="button"
                onClick={() => setPromoteDialogOpen(false)}
                className="p-1 rounded hover:bg-surface text-fg-muted hover:text-fg-muted"
                variant="ghost"
                size="sm"
                aria-label={t('admin.ab.closePromoteDialog')}
              >
                <CloseIcon size={20} />
              </Button>
            </div>
            <div className="p-4 space-y-4">
              <p className="text-sm text-fg-muted">
                Select the winning variant. It will be promoted as the production default.
              </p>
              <div className="space-y-2">
                {selectedExperiment.variants.map((variant) => (
                  <Button
                    key={variant.variantId}
                    type="button"
                    onClick={() => setSelectedVariantId(variant.variantId)}
                    data-testid={`variant-option-${variant.variantId}`}
                    className={`w-full p-3 rounded-lg border text-left transition-colors ${
                      selectedVariantId === variant.variantId
                        ? 'border-info-border bg-info-bg'
                        : 'border-border bg-surface/50 hover:border-border'
                    }`}
                    variant="ghost"
                  >
                    <div className="flex items-center justify-between">
                      <div className="text-sm font-medium text-fg-muted">{variant.variantName}</div>
                      <div className="text-xs text-fg-muted">{(variant.conversionRate * 100).toFixed(1)}% conversion</div>
                    </div>
                  </Button>
                ))}
              </div>
              <div>
                <label className="block text-xs font-medium text-fg-muted mb-1" htmlFor="promote-reason">
                  Reason (optional)
                </label>
                <Input
                  id="promote-reason"
                  type="text"
                  value={promoteReason}
                  onChange={(e) => setPromoteReason(e.target.value)}
                  placeholder={t('admin.ab.promoteReasonPlaceholder')}
                  className="w-full px-3 py-2 bg-surface border border-border text-fg-muted text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-purple-500"
                  fullWidth
                />
              </div>
              {promoteMutation.isError && (
                <p className="text-sm text-destructive">
                  {promoteMutation.error instanceof Error ? promoteMutation.error.message : 'Failed to promote winner.'}
                </p>
              )}
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-border">
              <Button
                type="button"
                onClick={() => setPromoteDialogOpen(false)}
                disabled={promoteMutation.isPending}
                className="px-4 py-2 bg-surface hover:bg-surface-muted text-fg-muted rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
                variant="ghost"
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleConfirmPromote}
                disabled={!selectedVariantId || promoteMutation.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 bg-info-bg hover:bg-info-bg text-white rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
                data-testid="btn-confirm-promote"
              >
                {promoteMutation.isPending && <SpinnerIcon size={14} className="animate-spin" />}
                Promote Winner
              </Button>
            </div>
          </div>
        </div>
      )}

      {createDialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" data-testid="create-dialog">
          <div className="bg-surface border border-border rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-border">
              <h2 className="text-lg font-semibold text-fg-muted">Create Experiment</h2>
              <Button
                type="button"
                onClick={() => setCreateDialogOpen(false)}
                className="p-1 rounded hover:bg-surface text-fg-muted hover:text-fg-muted"
                variant="ghost"
                size="sm"
                aria-label={t('admin.ab.closeCreateExperimentDialog')}
              >
                <CloseIcon size={20} />
              </Button>
            </div>
            <div className="p-4 space-y-4">
              <div>
                <label className="block text-xs font-medium text-fg-muted mb-1" htmlFor="exp-name">
                  Experiment Name <span className="text-destructive">*</span>
                </label>
                <Input
                  id="exp-name"
                  type="text"
                  value={newExperimentName}
                  onChange={(e) => setNewExperimentName(e.target.value)}
                  placeholder={t('admin.ab.experimentNamePlaceholder')}
                  className="w-full px-3 py-2 bg-surface border border-border text-fg-muted text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  fullWidth
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-fg-muted mb-1" htmlFor="exp-desc">
                  Description
                </label>
                <Textarea
                  id="exp-desc"
                  value={newExperimentDescription}
                  onChange={(e) => setNewExperimentDescription(e.target.value)}
                  placeholder={t('admin.ab.experimentDescriptionPlaceholder')}
                  rows={3}
                  className="w-full px-3 py-2 bg-surface border border-border text-fg-muted text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none"
                  resize="none"
                  fullWidth
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-fg-muted mb-1" htmlFor="exp-prompt">
                  Prompt Name <span className="text-destructive">*</span>
                </label>
                <Input
                  id="exp-prompt"
                  type="text"
                  value={newExperimentPromptName}
                  onChange={(e) => setNewExperimentPromptName(e.target.value)}
                  placeholder={t('admin.ab.promptNamePlaceholder')}
                  className="w-full px-3 py-2 bg-surface border border-border text-fg-muted text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  fullWidth
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-fg-muted mb-1" htmlFor="exp-va">
                  Variant A (Baseline)
                </label>
                <Input
                  id="exp-va"
                  type="text"
                  value={newExperimentVariantA}
                  onChange={(e) => setNewExperimentVariantA(e.target.value)}
                  placeholder={t('admin.ab.variantAPlaceholder')}
                  className="w-full px-3 py-2 bg-surface border border-border text-fg-muted text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  fullWidth
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-fg-muted mb-1" htmlFor="exp-vb">
                  Variant B (Challenger)
                </label>
                <Input
                  id="exp-vb"
                  type="text"
                  value={newExperimentVariantB}
                  onChange={(e) => setNewExperimentVariantB(e.target.value)}
                  placeholder={t('admin.ab.variantBPlaceholder')}
                  className="w-full px-3 py-2 bg-surface border border-border text-fg-muted text-sm rounded-lg placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  fullWidth
                />
              </div>
              {createMutation.isError && (
                <p className="text-sm text-destructive">
                  {createMutation.error instanceof Error ? createMutation.error.message : 'Failed to create experiment.'}
                </p>
              )}
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-border">
              <Button
                type="button"
                onClick={() => setCreateDialogOpen(false)}
                disabled={createMutation.isPending}
                className="px-4 py-2 bg-surface hover:bg-surface-muted text-fg-muted rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
                variant="ghost"
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleCreateExperiment}
                disabled={!newExperimentName.trim() || !newExperimentPromptName.trim() || createMutation.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-info-bg text-white rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
                data-testid="btn-confirm-create"
              >
                {createMutation.isPending && <SpinnerIcon size={14} className="animate-spin" />}
                Create
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default ABTestingDashboardPage;
