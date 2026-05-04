/**
 * Budget Page - Budget recommendation UI.
 *
 * @doc.type page
 * @doc.purpose Budget recommendation generation, review, and approval
 * @doc.layer frontend
 */
import React, { useState, useCallback } from 'react';
import { useParams, Navigate } from 'react-router';
import { useAuth } from '@/context/AuthContext';
import {
  useBudgetRecommendation,
  useGenerateBudget,
  useSubmitBudgetApproval,
  useApproveBudget,
} from '@/hooks/useBudget';

export function BudgetPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const [strategyId, setStrategyId] = useState('');
  const [totalMonthlyCap, setTotalMonthlyCap] = useState('');
  const [changeThreshold, setChangeThreshold] = useState('10');

  const { recommendation, isLoading, isError, error, refetch } = useBudgetRecommendation(workspaceId ?? null);
  const { generate, isPending: isGenerating } = useGenerateBudget(workspaceId ?? null);
  const { submit, isPending: isSubmitting } = useSubmitBudgetApproval(workspaceId ?? null);
  const { approve, isPending: isApproving } = useApproveBudget(workspaceId ?? null);

  const handleGenerate = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const cap = parseFloat(totalMonthlyCap);
      const threshold = parseFloat(changeThreshold);
      if (!strategyId.trim() || Number.isNaN(cap) || cap < 0 || Number.isNaN(threshold) || !workspaceId) return;
      await generate({ strategyId: strategyId.trim(), totalMonthlyCap: cap, changeThreshold: threshold });
      setStrategyId('');
      setTotalMonthlyCap('');
      setChangeThreshold('10');
    },
    [generate, strategyId, totalMonthlyCap, changeThreshold, workspaceId],
  );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main data-testid="budget-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Budget</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      <section className="mb-8 p-4 border rounded bg-white">
        <h2 className="text-lg font-semibold mb-3">Generate Budget Recommendation</h2>
        <form onSubmit={handleGenerate} className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm flex-1 min-w-[200px]">
            Strategy ID
            <input
              data-testid="budget-strategy-id-input"
              type="text"
              value={strategyId}
              onChange={(e) => setStrategyId(e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="strategy-123"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Monthly Cap
            <input
              data-testid="budget-cap-input"
              type="number"
              min="0"
              step="0.01"
              value={totalMonthlyCap}
              onChange={(e) => setTotalMonthlyCap(e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="5000"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Change Threshold (%)
            <input
              data-testid="budget-threshold-input"
              type="number"
              min="0"
              max="100"
              step="0.1"
              value={changeThreshold}
              onChange={(e) => setChangeThreshold(e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="10"
              required
            />
          </label>
          <button
            data-testid="generate-budget-btn"
            type="submit"
            disabled={isGenerating}
            className="px-4 py-1 bg-blue-600 text-white rounded text-sm disabled:opacity-50"
          >
            {isGenerating ? 'Generating…' : 'Generate'}
          </button>
        </form>
      </section>

      {isLoading && (
        <p data-testid="budget-loading" className="text-sm text-gray-400">
          Loading budget recommendation…
        </p>
      )}

      {isError && (
        <p data-testid="budget-error" role="alert" className="text-sm text-red-600">
          {error instanceof Error ? error.message : 'Failed to load budget recommendation.'}
        </p>
      )}

      {!isLoading && !isError && recommendation && (
        <div data-testid="budget-recommendation" className="border rounded p-4 bg-white">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-semibold">Latest Recommendation</h2>
              <p className="text-sm text-gray-500">
                Status: <span className="font-medium">{recommendation.status}</span>
              </p>
            </div>
            <div className="flex gap-2">
              {recommendation.status === 'DRAFT' && (
                <button
                  data-testid="submit-budget-btn"
                  onClick={() => submit(recommendation.recommendationId)}
                  disabled={isSubmitting}
                  className="px-3 py-1 bg-blue-600 text-white rounded text-sm disabled:opacity-50"
                >
                  {isSubmitting ? 'Submitting…' : 'Submit for Approval'}
                </button>
              )}
              {recommendation.status === 'PENDING_APPROVAL' && (
                <button
                  data-testid="approve-budget-btn"
                  onClick={() => approve(recommendation.recommendationId)}
                  disabled={isApproving}
                  className="px-3 py-1 bg-green-600 text-white rounded text-sm disabled:opacity-50"
                >
                  {isApproving ? 'Approving…' : 'Approve'}
                </button>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
            <div>
              <p className="text-sm text-gray-500">Total Monthly Cap</p>
              <p className="text-lg font-medium">${recommendation.totalMonthlyCap.toLocaleString()}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Change Threshold</p>
              <p className="text-lg font-medium">{recommendation.changeThresholdPct}%</p>
            </div>
          </div>

          {recommendation.channelAllocations.length > 0 && (
            <div>
              <p className="text-sm font-medium mb-2">Channel Allocations</p>
              <div className="border rounded overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="text-left px-4 py-2 font-medium">Channel</th>
                      <th className="text-left px-4 py-2 font-medium">Amount</th>
                      <th className="text-left px-4 py-2 font-medium">Daily Cap</th>
                      <th className="text-left px-4 py-2 font-medium">Rationale</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recommendation.channelAllocations.map((a, idx) => (
                      <tr key={idx} className="border-t">
                        <td className="px-4 py-2">{a.channelType}</td>
                        <td className="px-4 py-2">${a.recommendedAmount.toLocaleString()}</td>
                        <td className="px-4 py-2">${a.dailyCap.toLocaleString()}</td>
                        <td className="px-4 py-2 text-gray-600">{a.rationale}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {recommendation.rationale && (
            <div className="mt-4">
              <p className="text-sm font-medium">Rationale</p>
              <p className="text-sm text-gray-600">{recommendation.rationale}</p>
            </div>
          )}

          {recommendation.assumptions && (
            <div className="mt-4">
              <p className="text-sm font-medium">Assumptions</p>
              <p className="text-sm text-gray-600">{recommendation.assumptions}</p>
            </div>
          )}
        </div>
      )}

      {!isLoading && !isError && !recommendation && (
        <p data-testid="budget-empty" className="text-sm text-gray-500">
          No budget recommendation yet. Generate one above.
        </p>
      )}
    </main>
  );
}
