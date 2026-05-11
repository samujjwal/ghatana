/**
 * Strategy Page - Strategy generation UI.
 *
 * @doc.type page
 * @doc.purpose Strategy generation, review, and approval
 * @doc.layer frontend
 */
import React, { useState, useCallback, useEffect, useMemo } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/hooks/useToast';
import { ToastContainer } from '@/components/Toast';
import { ApprovalDialog } from '@/components/ApprovalDialog';
import { AIProvenancePanel } from '@/components/AIProvenancePanel';
import { PageStateNotice } from '@/components/PageStateNotice';
import { ApiError } from '@/lib/http-client';
import { canPerformAction } from '@/lib/action-permissions';
import { formatCurrency, formatDateTime } from '@/lib/i18n/format';
import {
  useStrategy,
  useGenerateStrategy,
  useSubmitStrategyApproval,
  useApproveStrategy,
} from '@/hooks/useStrategy';
import { useIntakeProfile } from '@/hooks/useIntakeProfile';
import { useLatestWebsiteAudit } from '@/hooks/useWebsiteAudit';
import { useLatestCompetitorResearch } from '@/hooks/useCompetitorResearch';
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
  const { isAuthenticated, roles } = useAuth();
  const { toasts, showSuccess, showError, dismissToast } = useToast();
  const [showApprovalDialog, setShowApprovalDialog] = useState(false);
  const canGenerateStrategy = canPerformAction(roles, 'generate-strategy');
  const canSubmitStrategy = canPerformAction(roles, 'submit-strategy');
  const canApproveStrategy = canPerformAction(roles, 'approve-strategy');

  const handleMutationError = useCallback((err: unknown, context: string) => {
    const apiErr = err instanceof ApiError ? err : null;
    showError(
      apiErr ? `${context}: ${apiErr.getUserMessage()}` : `${context}: An unexpected error occurred.`,
      apiErr?.correlationId ?? undefined,
    );
  }, [showError]);

  const [businessObjective, setBusinessObjective] = useState('');
  const [primaryOffer, setPrimaryOffer] = useState('');
  const [targetAudience, setTargetAudience] = useState('');
  const [primaryGeography, setPrimaryGeography] = useState('');
  const [constraints, setConstraints] = useState('');
  const [websiteUrl, setWebsiteUrl] = useState('');
  const [monthlyBudget, setMonthlyBudget] = useState('');

  const { strategy, isLoading, isError, error } = useStrategy(workspaceId ?? null);
  const { intake } = useIntakeProfile(workspaceId ?? null);
  const { report: latestAudit } = useLatestWebsiteAudit(workspaceId ?? null);
  const { snapshot: latestResearch } = useLatestCompetitorResearch(workspaceId ?? null);

  const derivedInputs = useMemo(() => {
    const auditFindingCount = latestAudit?.findings.length ?? 0;
    const trackingGapsDetected =
      latestAudit?.findings.some((finding) =>
        /tracking|analytics|tag|pixel/i.test(
          `${finding.category} ${finding.evidence} ${finding.recommendedAction}`,
        ),
      ) ?? false;
    const keywordOpportunityCount = latestResearch?.keywordFindings.length ?? 0;
    const topCompetitorCount = latestResearch?.competitorFindings.length ?? 0;

    return {
      auditFindingCount,
      trackingGapsDetected,
      keywordOpportunityCount,
      topCompetitorCount,
    };
  }, [latestAudit, latestResearch]);

  const intakeCompletionPct = useMemo(() => {
    const guidedFields = [
      businessObjective,
      primaryOffer,
      targetAudience,
      primaryGeography,
      monthlyBudget,
      constraints,
    ];
    const completed = guidedFields.filter((value) => value.trim().length > 0).length;
    return Math.round((completed / guidedFields.length) * 100);
  }, [businessObjective, primaryOffer, targetAudience, primaryGeography, monthlyBudget, constraints]);

  useEffect(() => {
    if (!intake) {
      return;
    }
    if (!primaryOffer.trim() && intake.offerSummary) {
      setPrimaryOffer(intake.offerSummary);
    }
    if (!targetAudience.trim() && intake.targetAudience) {
      setTargetAudience(intake.targetAudience);
    }
    if (!primaryGeography.trim() && intake.primaryGeography) {
      setPrimaryGeography(intake.primaryGeography);
    }
    if (!businessObjective.trim() && intake.growthGoal) {
      setBusinessObjective(intake.growthGoal);
    }
    if (!constraints.trim() && intake.constraints.length > 0) {
      setConstraints(intake.constraints.join(', '));
    }
    if (!websiteUrl.trim() && intake.websiteUrl) {
      setWebsiteUrl(intake.websiteUrl);
    }
    if (!monthlyBudget.trim() && intake.monthlyBudgetAmount != null) {
      setMonthlyBudget(String(intake.monthlyBudgetAmount));
    }
  }, [intake, primaryOffer, targetAudience, primaryGeography, businessObjective, constraints, websiteUrl, monthlyBudget]);
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

      if (
        !businessObjective.trim() ||
        !primaryOffer.trim() ||
        !targetAudience.trim() ||
        !primaryGeography.trim() ||
        !constraints.trim() ||
        Number.isNaN(budget) ||
        budget < 0 ||
        !workspaceId
      )
        return;

      try {
        await generate({
          serviceArea: primaryGeography.trim(),
          primaryOffer: primaryOffer.trim(),
          monthlyBudget: budget,
          intakeCompletionPct,
          auditFindingCount: derivedInputs.auditFindingCount,
          trackingGapsDetected: derivedInputs.trackingGapsDetected,
          keywordOpportunityCount: derivedInputs.keywordOpportunityCount,
          topCompetitorCount: derivedInputs.topCompetitorCount,
        });
        showSuccess('Strategy generated successfully');
      } catch {
        // Error is surfaced through the onError callback registered in useGenerateStrategy
      }
    },
    [
      generate,
      businessObjective,
      primaryOffer,
      targetAudience,
      primaryGeography,
      constraints,
      monthlyBudget,
      intakeCompletionPct,
      derivedInputs,
      workspaceId,
      showSuccess,
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
        <h2 className="text-lg font-semibold mb-1">Guided Strategy Builder</h2>
        <p className="text-sm text-gray-600 mb-4">
          Describe your outcome. DMOS derives audit and market signals from existing workspace services.
        </p>
        <form onSubmit={handleGenerate} className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <TextField
            id="strategy-objective"
            name="businessObjective"
            data-testid="strategy-objective-input"
            label="Business Objective"
            type="text"
            value={businessObjective}
            onChange={(e) => setBusinessObjective(e.target.value)}
            placeholder="e.g. increase qualified leads in 30 days"
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
            id="strategy-audience"
            name="targetAudience"
            data-testid="strategy-audience-input"
            label="Target Audience"
            type="text"
            value={targetAudience}
            onChange={(e) => setTargetAudience(e.target.value)}
            placeholder="e.g. working professionals, ages 30-55"
            required
            fullWidth
          />
          <TextField
            id="strategy-geography"
            name="primaryGeography"
            data-testid="strategy-geography-input"
            label="Geography"
            type="text"
            value={primaryGeography}
            onChange={(e) => setPrimaryGeography(e.target.value)}
            placeholder="e.g. Austin metro area"
            required
            fullWidth
          />
          <TextField
            id="strategy-monthly-budget"
            name="monthlyBudget"
            data-testid="strategy-budget-input"
            label="Monthly Budget (USD)"
            type="number"
            inputProps={{ min: '0' }}
            value={monthlyBudget}
            onChange={(e) => setMonthlyBudget(e.target.value)}
            placeholder="5000"
            required
            fullWidth
          />
          <TextField
            id="strategy-constraints"
            name="constraints"
            data-testid="strategy-constraints-input"
            label="Constraints"
            type="text"
            value={constraints}
            onChange={(e) => setConstraints(e.target.value)}
            placeholder="e.g. no claims without evidence, no weekend ad spend"
            required
            fullWidth
          />
          <TextField
            id="strategy-website-url"
            name="websiteUrl"
            data-testid="strategy-website-url-input"
            label="Website URL (for audit provenance)"
            type="url"
            value={websiteUrl}
            onChange={(e) => setWebsiteUrl(e.target.value)}
            placeholder="https://example.com"
            fullWidth
          />
          <div className="sm:col-span-2 border rounded bg-gray-50 p-3" data-testid="strategy-derived-inputs">
            <h3 className="text-sm font-semibold text-gray-700 mb-2">Derived Market Inputs</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm text-gray-700">
              <p data-testid="strategy-derived-intake">Intake completion: {intakeCompletionPct}%</p>
              <p data-testid="strategy-derived-audit">Audit findings: {derivedInputs.auditFindingCount}</p>
              <p data-testid="strategy-derived-keywords">Keyword opportunities: {derivedInputs.keywordOpportunityCount}</p>
              <p data-testid="strategy-derived-competitors">Competitor count: {derivedInputs.topCompetitorCount}</p>
            </div>
            <div className="mt-2 flex items-center gap-2 text-sm">
              <Checkbox
                data-testid="strategy-derived-tracking-gaps"
                id="strategy-derived-tracking-gaps"
                checked={derivedInputs.trackingGapsDetected}
                disabled
              />
              <label htmlFor="strategy-derived-tracking-gaps" className="cursor-default">
                Tracking gaps detected (derived)
              </label>
            </div>
            {!latestAudit && (
              <PageStateNotice
                testId="strategy-missing-audit"
                tone="warning"
                message="Latest website audit is unavailable. Strategy generation will use conservative defaults."
              />
            )}
            {!latestResearch && (
              <PageStateNotice
                testId="strategy-missing-research"
                tone="warning"
                message="Latest competitor research is unavailable. Strategy generation will use conservative defaults."
              />
            )}
          </div>
          <div className="sm:col-span-2">
            <Button
              data-testid="generate-strategy-btn"
              type="submit"
              disabled={isGenerating || !canGenerateStrategy}
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
        <PageStateNotice
          testId="strategy-loading"
          tone="loading"
          message="Loading strategy…"
        />
      )}

      {isError && (
        <PageStateNotice
          testId="strategy-error"
          tone="error"
          message={error instanceof ApiError ? error.getUserMessage() : 'Failed to load strategy.'}
        />
      )}

      {!isLoading && !isError && strategy && (
        <div data-testid="strategy-detail" className="border rounded p-4 bg-white">
          {!canGenerateStrategy && (
            <p
              data-testid="strategy-action-permission-banner"
              className="mb-4 text-sm text-yellow-700 bg-yellow-50 px-3 py-2 rounded"
            >
              You have view-only strategy access. Mutation actions are restricted by role.
            </p>
          )}
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
                  disabled={isSubmitting || !canSubmitStrategy}
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
                  onClick={() => canApproveStrategy && setShowApprovalDialog(true)}
                  disabled={isApproving || !canApproveStrategy}
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
              <p className="text-lg font-medium">{formatCurrency(strategy.budgetCap)}</p>
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
                        <TableCell className="px-4 py-2">{formatCurrency(p.estimatedBudget)}</TableCell>
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
        <PageStateNotice
          testId="strategy-empty"
          tone="empty"
          message="No strategy generated yet. Complete the guided form above to create one."
        />
      )}

      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      {showApprovalDialog && strategy && (
        <ApprovalDialog
          entityLabel="Marketing Strategy"
          entityId={strategy.strategyId}
          snapshotLines={[
            `Goals: ${strategy.goals.length}`,
            `Budget cap: ${formatCurrency(strategy.budgetCap)}`,
            `Status: ${strategy.status}`,
            `Generated: ${formatDateTime(strategy.generatedAt, { fallback: '—' })}`,
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
