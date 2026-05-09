/**
 * Budget Page - Budget recommendation UI.
 *
 * @doc.type page
 * @doc.purpose Budget recommendation generation, review, and approval
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
  useBudget,
  useGenerateBudget,
  useSubmitBudgetApproval,
  useApproveBudget,
} from '@/hooks/useBudget';
import { useStrategy } from '@/hooks/useStrategy';
import {
  Button,
  TextField,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from '@ghatana/design-system';

export function BudgetPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const { toasts, showSuccess, showError, dismissToast } = useToast();
  const [showApprovalDialog, setShowApprovalDialog] = useState(false);
  const [strategyId, setStrategyId] = useState('');
  const [totalMonthlyCap, setTotalMonthlyCap] = useState('');
  const [changeThreshold, setChangeThreshold] = useState('10');

  const handleMutationError = useCallback((err: unknown, context: string) => {
    const apiErr = err instanceof ApiError ? err : null;
    showError(
      apiErr ? `${context}: ${apiErr.getUserMessage()}` : `${context}: An unexpected error occurred.`,
      apiErr?.correlationId ?? undefined,
    );
  }, [showError]);

  const { recommendation, isLoading, isError, error, refetch } = useBudgetRecommendation(workspaceId ?? null);
  const { strategy: approvedStrategy } = useStrategy(workspaceId ?? null);
  const approvedStrategyId = approvedStrategy?.status === 'APPROVED' ? approvedStrategy.strategyId : null;
  const { generate, isPending: isGenerating } = useGenerateBudget(workspaceId ?? null, {
    onError: (err) => handleMutationError(err, 'Budget generation failed'),
  });
  const { submit, isPending: isSubmitting } = useSubmitBudgetApproval(workspaceId ?? null, {
    onError: (err) => handleMutationError(err, 'Failed to submit for approval'),
  });
  const { approve, isPending: isApproving } = useApproveBudget(workspaceId ?? null, {
    onError: (err) => handleMutationError(err, 'Approval failed'),
  });

  const handleGenerate = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const cap = parseFloat(totalMonthlyCap);
      const threshold = parseFloat(changeThreshold);
      const effectiveStrategyId = approvedStrategyId ?? strategyId.trim();
      if (!effectiveStrategyId || Number.isNaN(cap) || cap < 0 || Number.isNaN(threshold) || !workspaceId) return;
      try {
        await generate({ strategyId: effectiveStrategyId, totalMonthlyCap: cap, changeThreshold: threshold });
        showSuccess('Budget recommendation generated successfully');
        setStrategyId('');
        setTotalMonthlyCap('');
        setChangeThreshold('10');
      } catch {
        // Error is surfaced through the onError callback registered in useGenerateBudget
      }
    },
    [generate, approvedStrategyId, strategyId, totalMonthlyCap, changeThreshold, workspaceId, showSuccess],
  );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <section data-testid="budget-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Budget</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      <section className="mb-8 p-4 border rounded bg-white">
        <h2 className="text-lg font-semibold mb-3">Generate Budget Recommendation</h2>
        <form onSubmit={handleGenerate} className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1 text-sm flex-1 min-w-[200px]">
            <span className="text-sm font-medium">Strategy</span>
            {approvedStrategyId ? (
              <div className="flex items-center gap-2">
                <TextField
                  data-testid="budget-strategy-id-input"
                  type="text"
                  value={approvedStrategyId}
                  readOnly
                  aria-label="Approved strategy ID (auto-selected)"
                  fullWidth
                />
                <span className="text-xs text-green-600 font-medium whitespace-nowrap">✓ Approved</span>
              </div>
            ) : (
              <TextField
                data-testid="budget-strategy-id-input"
                type="text"
                value={strategyId}
                onChange={(e) => setStrategyId(e.target.value)}
                placeholder="No approved strategy yet"
                required
                fullWidth
              />
            )}
          </div>
          <TextField
            data-testid="budget-cap-input"
            label="Monthly Cap"
            type="number"
            inputProps={{ min: '0', step: '0.01' }}
            value={totalMonthlyCap}
            onChange={(e) => setTotalMonthlyCap(e.target.value)}
            placeholder="5000"
            required
          />
          <TextField
            data-testid="budget-threshold-input"
            label="Change Threshold (%)"
            type="number"
            inputProps={{ min: '0', max: '100', step: '0.1' }}
            value={changeThreshold}
            onChange={(e) => setChangeThreshold(e.target.value)}
            placeholder="10"
            required
          />
          <Button
            data-testid="generate-budget-btn"
            type="submit"
            disabled={isGenerating}
            loading={isGenerating}
            loadingText="Generating…"
            tone="primary"
          >
            Generate
          </Button>
        </form>
      </section>

      {isLoading && (
        <p data-testid="budget-loading" className="text-sm text-gray-400">
          Loading budget recommendation…
        </p>
      )}

      {isError && (
        <p data-testid="budget-error" role="alert" className="text-sm text-red-600">
          {error instanceof ApiError ? error.getUserMessage() : 'Failed to load budget recommendation.'}
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
                <Button
                  data-testid="submit-budget-btn"
                  size="sm"
                  tone="primary"
                  onClick={() => {
                    void submit(recommendation.recommendationId).then(() =>
                      showSuccess('Budget submitted for approval'),
                    );
                  }}
                  disabled={isSubmitting}
                  loading={isSubmitting}
                  loadingText="Submitting…"
                >
                  Submit for Approval
                </Button>
              )}
              {recommendation.status === 'PENDING_APPROVAL' && (
                <Button
                  data-testid="approve-budget-btn"
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
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell component="th" className="text-left px-4 py-2 font-medium">Channel</TableCell>
                      <TableCell component="th" className="text-left px-4 py-2 font-medium">Amount</TableCell>
                      <TableCell component="th" className="text-left px-4 py-2 font-medium">Daily Cap</TableCell>
                      <TableCell component="th" className="text-left px-4 py-2 font-medium">Rationale</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {recommendation.channelAllocations.map((a, idx) => (
                      <TableRow key={idx} className="border-t">
                        <TableCell className="px-4 py-2">{a.channelType}</TableCell>
                        <TableCell className="px-4 py-2">${a.recommendedAmount.toLocaleString()}</TableCell>
                        <TableCell className="px-4 py-2">${a.dailyCap.toLocaleString()}</TableCell>
                        <TableCell className="px-4 py-2 text-gray-600">{a.rationale}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
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

          {/* P2-004: AI Reasoning & Provenance panel */}
          <AIProvenancePanel
            modelVersion={recommendation.modelVersion}
            generatedAt={recommendation.generatedAt}
            generatedBy={recommendation.generatedBy}
            rationale={recommendation.rationale}
            assumptions={recommendation.assumptions}
          />
        </div>
      )}

      {!isLoading && !isError && !recommendation && (
        <p data-testid="budget-empty" className="text-sm text-gray-500">
          No budget recommendation yet. Generate one above.
        </p>
      )}

      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      {showApprovalDialog && recommendation && (
        <ApprovalDialog
          entityLabel="Budget Recommendation"
          entityId={recommendation.recommendationId}
          snapshotLines={[
            `Total monthly cap: $${recommendation.totalMonthlyCap.toLocaleString()}`,
            `Change threshold: ${recommendation.changeThresholdPct}%`,
            `Status: ${recommendation.status}`,
          ]}
          isPending={isApproving}
          onConfirm={(comment) => {
            void approve(recommendation.recommendationId, comment).then(() => {
              showSuccess('Budget approved');
              setShowApprovalDialog(false);
            });
          }}
          onCancel={() => setShowApprovalDialog(false)}
        />
      )}
    </section>
  );
}
