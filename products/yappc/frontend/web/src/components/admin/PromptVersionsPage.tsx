/**
 * Prompt Versions Page (Admin-Only)
 *
 * Admin interface for managing prompt versions with rollback and weight rebalancing.
 * Wired to the real PromptVersioningService API via TanStack Query.
 *
 * @doc.type component
 * @doc.purpose Admin-only prompt version management UI
 * @doc.layer product
 * @doc.pattern Admin Component
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  History as HistoryIcon,
  RotateCcw as RollbackIcon,
  Eye as ViewIcon,
  Scale as WeightIcon,
  CheckCircle as ActiveIcon,
  Clock as InactiveIcon,
  X as CloseIcon,
  Loader2 as SpinnerIcon,
  AlertCircle as ErrorIcon,
  RefreshCw as RefreshIcon,
} from 'lucide-react';
import {
  listPromptVersions,
  rollbackPromptVersion,
  updatePromptWeights,
  type PromptVersion,
} from '../../services/admin/promptVersioningApi';

interface PromptVersionsPageProps {
  className?: string;
}

export function PromptVersionsPage({ className }: PromptVersionsPageProps) {
  const queryClient = useQueryClient();

  const [selectedPrompt, setSelectedPrompt] = useState<PromptVersion | null>(null);
  const [viewDialogOpen, setViewDialogOpen] = useState(false);
  const [rollbackDialogOpen, setRollbackDialogOpen] = useState(false);
  const [weightDialogOpen, setWeightDialogOpen] = useState(false);
  const [rollbackReason, setRollbackReason] = useState('');
  const [weightValue, setWeightValue] = useState('');

  // ── Queries ────────────────────────────────────────────────────────────────

  const {
    data,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['admin', 'prompt-versions'],
    queryFn: () => listPromptVersions(),
    staleTime: 30_000,
  });

  const promptVersions = data?.items ?? [];

  // ── Mutations ──────────────────────────────────────────────────────────────

  const rollbackMutation = useMutation({
    mutationFn: ({ versionId, reason }: { versionId: string; reason: string }) =>
      rollbackPromptVersion(versionId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'prompt-versions'] });
      setRollbackDialogOpen(false);
      setSelectedPrompt(null);
      setRollbackReason('');
    },
  });

  const weightMutation = useMutation({
    mutationFn: (weights: Record<string, number>) => updatePromptWeights(weights),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'prompt-versions'] });
      setWeightDialogOpen(false);
      setSelectedPrompt(null);
      setWeightValue('');
    },
  });

  // ── Handlers ───────────────────────────────────────────────────────────────

  const handleView = (prompt: PromptVersion) => {
    setSelectedPrompt(prompt);
    setViewDialogOpen(true);
  };

  const handleRollback = (prompt: PromptVersion) => {
    setSelectedPrompt(prompt);
    setRollbackReason('');
    setRollbackDialogOpen(true);
  };

  const handleConfirmRollback = () => {
    if (!selectedPrompt || !rollbackReason.trim()) return;
    rollbackMutation.mutate({ versionId: selectedPrompt.id, reason: rollbackReason.trim() });
  };

  const handleWeightRebalance = (prompt: PromptVersion) => {
    setSelectedPrompt(prompt);
    setWeightValue(String(prompt.weight ?? 1.0));
    setWeightDialogOpen(true);
  };

  const handleConfirmWeight = () => {
    if (!selectedPrompt) return;
    const parsed = parseFloat(weightValue);
    if (isNaN(parsed) || parsed < 0 || parsed > 1) return;
    weightMutation.mutate({ [selectedPrompt.id]: parsed });
  };

  // ── Derived ─────────────────────────────────────────────────────────────

  const groupedPrompts = promptVersions.reduce<Record<string, PromptVersion[]>>((acc, prompt) => {
    const list = acc[prompt.promptName] ?? [];
    list.push(prompt);
    acc[prompt.promptName] = list;
    return acc;
  }, {});

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className={className}>
      <div className="p-6 space-y-6">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-zinc-100 mb-2 flex items-center gap-2">
              <HistoryIcon size={24} />
              Prompt Version Management
            </h1>
            <p className="text-sm text-zinc-400">
              Manage AI prompt versions, roll back to previous versions, and configure weight rebalancing for A/B testing.
            </p>
          </div>
          <button
            type="button"
            onClick={() => void refetch()}
            className="p-2 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors"
            aria-label="Refresh"
            title="Refresh"
          >
            <RefreshIcon size={16} />
          </button>
        </div>

        {/* Admin alert */}
        <div className="bg-blue-900/20 border border-blue-800 rounded-lg p-4 text-blue-400 text-sm">
          This page is for administrators only. Changes here affect AI behavior across all tenants.
        </div>

        {/* Loading */}
        {isLoading && (
          <div className="flex items-center gap-2 text-zinc-400 py-8 justify-center">
            <SpinnerIcon size={20} className="animate-spin" />
            <span>Loading prompt versions…</span>
          </div>
        )}

        {/* Error */}
        {isError && (
          <div className="flex items-center gap-2 text-red-400 bg-red-900/20 border border-red-800 rounded-lg p-4">
            <ErrorIcon size={16} />
            <span className="text-sm">
              {error instanceof Error ? error.message : 'Failed to load prompt versions.'}
            </span>
          </div>
        )}

        {/* Prompt Groups */}
        {!isLoading && !isError && Object.entries(groupedPrompts).map(([promptName, versions]) => (
          <div key={promptName} className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
            <h2 className="text-lg font-semibold text-zinc-100 mb-4">{promptName}</h2>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-zinc-800">
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Status</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Version ID</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Description</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Author</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Weight</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Created</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Hash</th>
                    <th className="text-right py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {versions.map((version) => (
                    <tr key={version.id} className="border-b border-zinc-800 hover:bg-zinc-800/50">
                      <td className="py-3 px-4">
                        {version.active ? (
                          <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-emerald-900/30 text-emerald-400 text-xs font-medium">
                            <ActiveIcon size={12} />Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-zinc-800 text-zinc-400 text-xs font-medium">
                            <InactiveIcon size={12} />Inactive
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        <code className="text-xs text-zinc-400">{version.id.slice(0, 8)}</code>
                      </td>
                      <td className="py-3 px-4 text-sm text-zinc-300">{version.description}</td>
                      <td className="py-3 px-4 text-sm text-zinc-400">{version.author}</td>
                      <td className="py-3 px-4 text-sm text-zinc-400">
                        {typeof version.weight === 'number' ? `${(version.weight * 100).toFixed(0)}%` : '—'}
                      </td>
                      <td className="py-3 px-4 text-sm text-zinc-400">
                        {new Date(version.createdAt).toLocaleString()}
                      </td>
                      <td className="py-3 px-4">
                        <code className="text-xs text-zinc-400">{version.contentHash.slice(0, 8)}</code>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            type="button"
                            onClick={() => handleView(version)}
                            className="p-1.5 rounded hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 transition-colors"
                            aria-label="View prompt version"
                            title="View content"
                          >
                            <ViewIcon size={14} />
                          </button>
                          {!version.active && (
                            <button
                              type="button"
                              onClick={() => handleRollback(version)}
                              className="p-1.5 rounded hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 transition-colors"
                              aria-label="Rollback to this version"
                              title="Rollback to this version"
                            >
                              <RollbackIcon size={14} />
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => handleWeightRebalance(version)}
                            className="p-1.5 rounded hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 transition-colors"
                            aria-label="Configure weight"
                            title="Configure weight"
                          >
                            <WeightIcon size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}

        {/* Empty state */}
        {!isLoading && !isError && promptVersions.length === 0 && (
          <div className="text-center py-12 text-zinc-500">
            No prompt versions found.
          </div>
        )}
      </div>

      {/* View Dialog */}
      {viewDialogOpen && selectedPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-2xl w-full max-h-[80vh] overflow-auto">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Prompt Version Content</h2>
              <button
                type="button"
                onClick={() => setViewDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Prompt Name:</span> {selectedPrompt.promptName}
              </div>
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Description:</span> {selectedPrompt.description}
              </div>
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Author:</span> {selectedPrompt.author}
              </div>
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Content Hash:</span> {selectedPrompt.contentHash}
              </div>
              <div className="bg-zinc-800 rounded-lg p-4">
                <pre className="whitespace-pre-wrap text-sm text-zinc-300">
                  {selectedPrompt.content}
                </pre>
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

      {/* Rollback Confirmation Dialog */}
      {rollbackDialogOpen && selectedPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Confirm Rollback</h2>
              <button
                type="button"
                onClick={() => setRollbackDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <p className="text-sm text-zinc-300">
                Roll back to version{' '}
                <code className="text-zinc-400">{selectedPrompt.id.slice(0, 8)}</code>? This will
                make this version the active prompt and deactivate the current active version.
              </p>
              <div>
                <label htmlFor="rollback-reason" className="block text-xs font-medium text-zinc-400 mb-1">
                  Reason (required)
                </label>
                <input
                  id="rollback-reason"
                  type="text"
                  value={rollbackReason}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setRollbackReason(e.target.value)}
                  placeholder="Reason for rollback…"
                  className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-3 py-2 text-sm text-zinc-200 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              {rollbackMutation.isError && (
                <p className="text-xs text-red-400">
                  {rollbackMutation.error instanceof Error
                    ? rollbackMutation.error.message
                    : 'Rollback failed.'}
                </p>
              )}
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setRollbackDialogOpen(false)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmRollback}
                disabled={!rollbackReason.trim() || rollbackMutation.isPending}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
              >
                {rollbackMutation.isPending && <SpinnerIcon size={14} className="animate-spin" />}
                Confirm Rollback
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Weight Configuration Dialog */}
      {weightDialogOpen && selectedPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Configure Weight</h2>
              <button
                type="button"
                onClick={() => setWeightDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-4">
              <p className="text-sm text-zinc-300">
                Set the A/B testing weight for version{' '}
                <code className="text-zinc-400">{selectedPrompt.id.slice(0, 8)}</code>.
                Weight must be between 0 and 1.
              </p>
              <div>
                <label htmlFor="weight-value" className="block text-xs font-medium text-zinc-400 mb-1">
                  Weight (0.0–1.0)
                </label>
                <input
                  id="weight-value"
                  type="number"
                  min="0"
                  max="1"
                  step="0.05"
                  value={weightValue}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setWeightValue(e.target.value)}
                  className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-3 py-2 text-sm text-zinc-200 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              {weightMutation.isError && (
                <p className="text-xs text-red-400">
                  {weightMutation.error instanceof Error
                    ? weightMutation.error.message
                    : 'Weight update failed.'}
                </p>
              )}
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setWeightDialogOpen(false)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmWeight}
                disabled={weightMutation.isPending}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
              >
                {weightMutation.isPending && <SpinnerIcon size={14} className="animate-spin" />}
                Save Weight
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default PromptVersionsPage;
