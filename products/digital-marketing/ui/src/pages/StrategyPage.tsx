/**
 * Strategy Page - Strategy generation UI.
 *
 * @doc.type page
 * @doc.purpose Strategy generation, review, and approval
 * @doc.layer frontend
 */
import React, { useState, useCallback } from 'react';
import { useParams, Navigate } from 'react-router';
import { useAuth } from '@/context/AuthContext';
import {
  useStrategy,
  useGenerateStrategy,
  useSubmitStrategyApproval,
  useApproveStrategy,
} from '@/hooks/useStrategy';

export function StrategyPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  const [serviceArea, setServiceArea] = useState('');
  const [primaryOffer, setPrimaryOffer] = useState('');
  const [monthlyBudget, setMonthlyBudget] = useState('');
  const [intakeCompletionPct, setIntakeCompletionPct] = useState('100');
  const [auditFindingCount, setAuditFindingCount] = useState('0');
  const [trackingGapsDetected, setTrackingGapsDetected] = useState(false);
  const [keywordOpportunityCount, setKeywordOpportunityCount] = useState('0');
  const [topCompetitorCount, setTopCompetitorCount] = useState('0');

  const { strategy, isLoading, isError, error } = useStrategy(workspaceId ?? null);
  const { generate, isPending: isGenerating } = useGenerateStrategy(workspaceId ?? null);
  const { submit, isPending: isSubmitting } = useSubmitStrategyApproval(workspaceId ?? null);
  const { approve, isPending: isApproving } = useApproveStrategy(workspaceId ?? null);

  const handleGenerate = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const budget = parseInt(monthlyBudget, 10);
      const intake = parseInt(intakeCompletionPct, 10);
      const audit = parseInt(auditFindingCount, 10);
      const keywords = parseInt(keywordOpportunityCount, 10);
      const competitors = parseInt(topCompetitorCount, 10);

      if (
        !serviceArea.trim() ||
        !primaryOffer.trim() ||
        Number.isNaN(budget) ||
        budget < 0 ||
        Number.isNaN(intake) ||
        intake < 0 ||
        intake > 100 ||
        Number.isNaN(audit) ||
        audit < 0 ||
        Number.isNaN(keywords) ||
        keywords < 0 ||
        Number.isNaN(competitors) ||
        competitors < 0 ||
        !workspaceId
      )
        return;

      await generate({
        serviceArea: serviceArea.trim(),
        primaryOffer: primaryOffer.trim(),
        monthlyBudget: budget,
        intakeCompletionPct: intake,
        auditFindingCount: audit,
        trackingGapsDetected,
        keywordOpportunityCount: keywords,
        topCompetitorCount: competitors,
      });

      setServiceArea('');
      setPrimaryOffer('');
      setMonthlyBudget('');
      setIntakeCompletionPct('100');
      setAuditFindingCount('0');
      setTrackingGapsDetected(false);
      setKeywordOpportunityCount('0');
      setTopCompetitorCount('0');
    },
    [
      generate,
      serviceArea,
      primaryOffer,
      monthlyBudget,
      intakeCompletionPct,
      auditFindingCount,
      trackingGapsDetected,
      keywordOpportunityCount,
      topCompetitorCount,
      workspaceId,
    ],
  );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main data-testid="strategy-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Strategy</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      <section className="mb-8 p-4 border rounded bg-white">
        <h2 className="text-lg font-semibold mb-3">Generate 30-Day Marketing Strategy</h2>
        <form onSubmit={handleGenerate} className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <label className="flex flex-col gap-1 text-sm">
            Service Area
            <input
              data-testid="strategy-service-area-input"
              type="text"
              value={serviceArea}
              onChange={(e) => setServiceArea(e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="e.g. Austin, TX"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Primary Offer
            <input
              data-testid="strategy-offer-input"
              type="text"
              value={primaryOffer}
              onChange={(e) => setPrimaryOffer(e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="e.g. Dental implants"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Monthly Budget
            <input
              data-testid="strategy-budget-input"
              type="number"
              min="0"
              value={monthlyBudget}
              onChange={(e) => setMonthlyBudget(e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="5000"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Intake Completion (%)
            <input
              data-testid="strategy-intake-input"
              type="number"
              min="0"
              max="100"
              value={intakeCompletionPct}
              onChange={(e) => setIntakeCompletionPct(e.target.value)}
              className="border rounded px-2 py-1"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Audit Findings
            <input
              data-testid="strategy-audit-input"
              type="number"
              min="0"
              value={auditFindingCount}
              onChange={(e) => setAuditFindingCount(e.target.value)}
              className="border rounded px-2 py-1"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Keyword Opportunities
            <input
              data-testid="strategy-keywords-input"
              type="number"
              min="0"
              value={keywordOpportunityCount}
              onChange={(e) => setKeywordOpportunityCount(e.target.value)}
              className="border rounded px-2 py-1"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Top Competitors
            <input
              data-testid="strategy-competitors-input"
              type="number"
              min="0"
              value={topCompetitorCount}
              onChange={(e) => setTopCompetitorCount(e.target.value)}
              className="border rounded px-2 py-1"
            />
          </label>
          <label className="flex items-center gap-2 text-sm sm:pt-6">
            <input
              data-testid="strategy-tracking-gaps-input"
              type="checkbox"
              checked={trackingGapsDetected}
              onChange={(e) => setTrackingGapsDetected(e.target.checked)}
              className="border rounded"
            />
            Tracking gaps detected
          </label>
          <div className="sm:col-span-2">
            <button
              data-testid="generate-strategy-btn"
              type="submit"
              disabled={isGenerating}
              className="px-4 py-1 bg-blue-600 text-white rounded text-sm disabled:opacity-50"
            >
              {isGenerating ? 'Generating…' : 'Generate Strategy'}
            </button>
          </div>
        </form>
      </section>

      {isLoading && (
        <p data-testid="strategy-loading" className="text-sm text-gray-400">
          Loading strategy…
        </p>
      )}

      {isError && (
        <p data-testid="strategy-error" role="alert" className="text-sm text-red-600">
          {error instanceof Error ? error.message : 'Failed to load strategy.'}
        </p>
      )}

      {!isLoading && !isError && strategy && (
        <div data-testid="strategy-detail" className="border rounded p-4 bg-white">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-semibold">Latest Strategy</h2>
              <p className="text-sm text-gray-500">
                Status: <span className="font-medium">{strategy.status}</span>
              </p>
            </div>
            <div className="flex gap-2">
              {strategy.status === 'DRAFT' && (
                <button
                  data-testid="submit-strategy-btn"
                  onClick={() => submit(strategy.strategyId)}
                  disabled={isSubmitting}
                  className="px-3 py-1 bg-blue-600 text-white rounded text-sm disabled:opacity-50"
                >
                  {isSubmitting ? 'Submitting…' : 'Submit for Approval'}
                </button>
              )}
              {strategy.status === 'PENDING_APPROVAL' && (
                <button
                  data-testid="approve-strategy-btn"
                  onClick={() => approve(strategy.strategyId)}
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
              <p className="text-sm text-gray-500">Budget Cap</p>
              <p className="text-lg font-medium">${strategy.budgetCap.toLocaleString()}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Model Version</p>
              <p className="text-sm font-medium">{strategy.modelVersion}</p>
            </div>
          </div>

          {strategy.goals.length > 0 && (
            <div className="mb-4">
              <p className="text-sm font-medium mb-2">Goals</p>
              <ul className="list-disc list-inside text-sm text-gray-700">
                {strategy.goals.map((g, idx) => (
                  <li key={idx}>
                    <span className="font-medium">{g.goalType}</span>: {g.description} ({g.targetMetric})
                  </li>
                ))}
              </ul>
            </div>
          )}

          {strategy.channelPlans.length > 0 && (
            <div className="mb-4">
              <p className="text-sm font-medium mb-2">Channel Plans</p>
              <div className="border rounded overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="text-left px-4 py-2 font-medium">Channel</th>
                      <th className="text-left px-4 py-2 font-medium">Objective</th>
                      <th className="text-left px-4 py-2 font-medium">Estimated Budget</th>
                    </tr>
                  </thead>
                  <tbody>
                    {strategy.channelPlans.map((p, idx) => (
                      <tr key={idx} className="border-t">
                        <td className="px-4 py-2">{p.channelType}</td>
                        <td className="px-4 py-2">{p.objective}</td>
                        <td className="px-4 py-2">${p.estimatedBudget.toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {strategy.rationale && (
            <div className="mb-4">
              <p className="text-sm font-medium">Rationale</p>
              <p className="text-sm text-gray-600">{strategy.rationale}</p>
            </div>
          )}

          {strategy.assumptions && (
            <div className="mb-4">
              <p className="text-sm font-medium">Assumptions</p>
              <p className="text-sm text-gray-600">{strategy.assumptions}</p>
            </div>
          )}

          {strategy.measurementPlan && (
            <div className="mb-4">
              <p className="text-sm font-medium">Measurement Plan</p>
              <p className="text-sm text-gray-600">{strategy.measurementPlan}</p>
            </div>
          )}

          {strategy.contentPlan && (
            <div>
              <p className="text-sm font-medium">Content Plan</p>
              <p className="text-sm text-gray-600">{strategy.contentPlan}</p>
            </div>
          )}
        </div>
      )}

      {!isLoading && !isError && !strategy && (
        <p data-testid="strategy-empty" className="text-sm text-gray-500">
          No strategy generated yet. Fill out the form above to create one.
        </p>
      )}
    </main>
  );
}
