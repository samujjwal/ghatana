/**
 * Strategy Page - Strategy generation UI.
 *
 * @doc.type page
 * @doc.purpose Strategy generation, review, and approval
 * @doc.layer frontend
 */
import React, { useState, useCallback } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/hooks/useToast';
import { ToastContainer } from '@/components/Toast';
import { ApprovalDialog } from '@/components/ApprovalDialog';
import { AIProvenancePanel } from '@/components/AIProvenancePanel';
import { ApiError } from '@/lib/http-client';
import {
  useStrategy,
  useGenerateStrategy,
  useSubmitStrategyApproval,
  useApproveStrategy,
} from '@/hooks/useStrategy';
import {
  Button,
  TextField,
  Checkbox,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from '@ghatana/design-system';

export function StrategyPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const { toasts, showSuccess, showError, dismissToast } = useToast();
  const [showApprovalDialog, setShowApprovalDialog] = useState(false);

  const handleMutationError = useCallback((err: unknown, context: string) => {
    const apiErr = err instanceof ApiError ? err : null;
    showError(
      apiErr ? `${context}: ${apiErr.getUserMessage()}` : `${context}: An unexpected error occurred.`,
      apiErr?.correlationId ?? undefined,
    );
  }, [showError]);

  const [serviceArea, setServiceArea] = useState('');
  const [primaryOffer, setPrimaryOffer] = useState('');
  const [monthlyBudget, setMonthlyBudget] = useState('');
  const [intakeCompletionPct, setIntakeCompletionPct] = useState('100');
  const [auditFindingCount, setAuditFindingCount] = useState('0');
  const [trackingGapsDetected, setTrackingGapsDetected] = useState(false);
  const [keywordOpportunityCount, setKeywordOpportunityCount] = useState('0');
  const [topCompetitorCount, setTopCompetitorCount] = useState('0');

  const { strategy, isLoading, isError, error } = useStrategy(workspaceId ?? null);
  const { generate, isPending: isGenerating } = useGenerateStrategy(workspaceId ?? null, {
    onError: (err) => handleMutationError(err, 'Strategy generation failed'),
  });
  const { submit, isPending: isSubmitting } = useSubmitStrategyApproval(workspaceId ?? null, {
    onError: (err) => handleMutationError(err, 'Failed to submit for approval'),
  });
  const { approve, isPending: isApproving } = useApproveStrategy(workspaceId ?? null, {
    onError: (err) => handleMutationError(err, 'Approval failed'),
  });

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

      try {
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
        showSuccess('Strategy generated successfully');
        setServiceArea('');
        setPrimaryOffer('');
        setMonthlyBudget('');
        setIntakeCompletionPct('100');
        setAuditFindingCount('0');
        setTrackingGapsDetected(false);
        setKeywordOpportunityCount('0');
        setTopCompetitorCount('0');
      } catch {
        // Error is surfaced through the onError callback registered in useGenerateStrategy
      }
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
    <section data-testid="strategy-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Strategy</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      <section className="mb-8 p-4 border rounded bg-white">
        <h2 className="text-lg font-semibold mb-3">Generate 30-Day Marketing Strategy</h2>
        <form onSubmit={handleGenerate} className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <TextField
            id="strategy-service-area"
            name="serviceArea"
            data-testid="strategy-service-area-input"
            label="Service Area"
            type="text"
            value={serviceArea}
            onChange={(e) => setServiceArea(e.target.value)}
            placeholder="e.g. Austin, TX"
            required
            fullWidth
          />
          <TextField
            id="strategy-primary-offer"
            name="primaryOffer"
            data-testid="strategy-offer-input"
            label="Primary Offer"
            type="text"
            value={primaryOffer}
            onChange={(e) => setPrimaryOffer(e.target.value)}
            placeholder="e.g. Dental implants"
            required
            fullWidth
          />
          <TextField
            id="strategy-monthly-budget"
            name="monthlyBudget"
            data-testid="strategy-budget-input"
            label="Monthly Budget"
            type="number"
            inputProps={{ min: '0' }}
            value={monthlyBudget}
            onChange={(e) => setMonthlyBudget(e.target.value)}
            placeholder="5000"
            required
            fullWidth
          />
          <TextField
            data-testid="strategy-intake-input"
            label="Intake Completion (%)"
            type="number"
            inputProps={{ min: '0', max: '100' }}
            value={intakeCompletionPct}
            onChange={(e) => setIntakeCompletionPct(e.target.value)}
            required
            fullWidth
          />
          <TextField
            data-testid="strategy-audit-input"
            label="Audit Findings"
            type="number"
            inputProps={{ min: '0' }}
            value={auditFindingCount}
            onChange={(e) => setAuditFindingCount(e.target.value)}
            fullWidth
          />
          <TextField
            data-testid="strategy-keywords-input"
            label="Keyword Opportunities"
            type="number"
            inputProps={{ min: '0' }}
            value={keywordOpportunityCount}
            onChange={(e) => setKeywordOpportunityCount(e.target.value)}
            fullWidth
          />
          <TextField
            data-testid="strategy-competitors-input"
            label="Top Competitors"
            type="number"
            inputProps={{ min: '0' }}
            value={topCompetitorCount}
            onChange={(e) => setTopCompetitorCount(e.target.value)}
            fullWidth
          />
          <div className="flex items-center gap-2 sm:pt-6">
            <Checkbox
              data-testid="strategy-tracking-gaps-input"
              id="strategy-tracking-gaps"
              checked={trackingGapsDetected}
              onChange={(e) => setTrackingGapsDetected((e.target as HTMLInputElement).checked)}
            />
            <label htmlFor="strategy-tracking-gaps" className="text-sm cursor-pointer">
              Tracking gaps detected
            </label>
          </div>
          <div className="sm:col-span-2">
            <Button
              data-testid="generate-strategy-btn"
              type="submit"
              disabled={isGenerating}
              loading={isGenerating}
              loadingText="Generating…"
              tone="primary"
            >
              Generate Strategy
            </Button>
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
                <Button
                  data-testid="submit-strategy-btn"
                  size="sm"
                  tone="primary"
                  onClick={() => {
                    void submit(strategy.strategyId).then(() =>
                      showSuccess('Strategy submitted for approval'),
                    );
                  }}
                  disabled={isSubmitting}
                  loading={isSubmitting}
                  loadingText="Submitting…"
                >
                  Submit for Approval
                </Button>
              )}
              {strategy.status === 'PENDING_APPROVAL' && (
                <Button
                  data-testid="approve-strategy-btn"
                  size="sm"
                  tone="success"
                  onClick={() => setShowApprovalDialog(true)}
                  disabled={isApproving}
                  loading={isApproving}
                  loadingText="Approving…"
                >
                  Approve
                </Button>
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
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell component="th" className="text-left px-4 py-2 font-medium">Channel</TableCell>
                      <TableCell component="th" className="text-left px-4 py-2 font-medium">Objective</TableCell>
                      <TableCell component="th" className="text-left px-4 py-2 font-medium">Estimated Budget</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {strategy.channelPlans.map((p, idx) => (
                      <TableRow key={idx} className="border-t">
                        <TableCell className="px-4 py-2">{p.channelType}</TableCell>
                        <TableCell className="px-4 py-2">{p.objective}</TableCell>
                        <TableCell className="px-4 py-2">${p.estimatedBudget.toLocaleString()}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
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

          {/* P2-004: AI Reasoning & Provenance panel */}
          <AIProvenancePanel
            modelVersion={strategy.modelVersion}
            generatedAt={strategy.generatedAt}
            generatedBy={strategy.generatedBy}
            rationale={strategy.rationale}
            assumptions={strategy.assumptions}
          />
        </div>
      )}

      {!isLoading && !isError && !strategy && (
        <p data-testid="strategy-empty" className="text-sm text-gray-500">
          No strategy generated yet. Fill out the form above to create one.
        </p>
      )}

      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      {showApprovalDialog && strategy && (
        <ApprovalDialog
          entityLabel="Marketing Strategy"
          entityId={strategy.strategyId}
          snapshotLines={[
            `Service area: ${strategy.serviceArea ?? '—'}`,
            `Primary offer: ${strategy.primaryOffer ?? '—'}`,
            `Status: ${strategy.status}`,
            `Generated: ${strategy.generatedAt ? new Date(strategy.generatedAt).toLocaleString() : '—'}`,
          ]}
          isPending={isApproving}
          onConfirm={(comment) => {
            void approve(strategy.strategyId, comment).then(() => {
              showSuccess('Strategy approved');
              setShowApprovalDialog(false);
            });
          }}
          onCancel={() => setShowApprovalDialog(false)}
        />
      )}
    </section>
  );
}
