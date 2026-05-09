/**
 * Campaigns Page - Campaign management UI.
 *
 * <p>P1-030: Surfaces mutation errors with toast notifications and correlation ID.</p>
 * <p>P1-031: Per-row action pending states for concurrent operations.</p>
 *
 * <p>P2-003: Complete, archive, rollback (feature-flagged), and duplicate lifecycle actions.</p>
 *
 * @doc.type page
 * @doc.purpose Campaign listing, creation, launch, and pause with error handling
 * @doc.layer frontend
 */
import React, { useState, useCallback } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useCampaigns, useCreateCampaign, useLaunchCampaign, usePauseCampaign, useCompleteCampaign, useArchiveCampaign, useRollbackCampaign, useDuplicateCampaign } from '@/hooks/useCampaigns';
import { useToast } from '@/hooks/useToast';
import { ApiError } from '@/lib/http-client';
import type { CampaignType, CampaignObjective } from '@/types/campaign';
import {
  Button,
  TextField,
  Select,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Badge,
} from '@ghatana/design-system';
import { ToastContainer } from '@/components/Toast';

const CAMPAIGN_TYPES: CampaignType[] = ['EMAIL', 'SOCIAL', 'PAID_SEARCH', 'PUSH', 'SMS', 'OMNICHANNEL'];
const CAMPAIGN_OBJECTIVES: CampaignObjective[] = ['AWARENESS', 'LEADS', 'CONVERSIONS', 'RETENTION', 'ENGAGEMENT', 'TRAFFIC'];

const PAGE_SIZE = 20;

export function CampaignsPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const { toasts, showSuccess, showError, dismissToast } = useToast();
  // TODO: Implement feature flags for rollback workflow
  const rollbackEnabled = false;
  const [name, setName] = useState('');
  const [type, setType] = useState<CampaignType>('EMAIL');
  const [objective, setObjective] = useState<CampaignObjective>('AWARENESS');
  const [budgetDollars, setBudgetDollars] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [audience, setAudience] = useState('');
  const [landingPageUrl, setLandingPageUrl] = useState('');
  const [page, setPage] = useState(0);

  const { campaigns, count, isLoading, isError, error } = useCampaigns(workspaceId ?? null, {
    limit: PAGE_SIZE,
    offset: page * PAGE_SIZE,
  });

  const totalPages = Math.max(1, Math.ceil(count / PAGE_SIZE));

  // P1-030: Error handlers with correlation ID for diagnostics
  const handleCreateError = useCallback((err: ApiError) => {
    showError(err.getUserMessage(), err.correlationId ?? undefined);
  }, [showError]);

  const handleLaunchError = useCallback((err: ApiError, campaignId: string) => {
    const campaign = campaigns.find(c => c.id === campaignId);
    showError(
      `Failed to launch "${campaign?.name ?? 'campaign'}": ${err.getUserMessage()}`,
      err.correlationId ?? undefined
    );
  }, [showError, campaigns]);

  const handlePauseError = useCallback((err: ApiError, campaignId: string) => {
    const campaign = campaigns.find(c => c.id === campaignId);
    showError(
      `Failed to pause "${campaign?.name ?? 'campaign'}": ${err.getUserMessage()}`,
      err.correlationId ?? undefined
    );
  }, [showError, campaigns]);

  const { create, isPending: isCreating } = useCreateCampaign(workspaceId ?? null, handleCreateError);

  // P1-031: Per-row pending states via isPendingFor
  const { launch, isPendingFor: isLaunchingFor } = useLaunchCampaign(workspaceId ?? null, handleLaunchError);
  const { pause, isPendingFor: isPausingFor } = usePauseCampaign(workspaceId ?? null, handlePauseError);

  const handleCompleteError = useCallback((err: ApiError, campaignId: string) => {
    const campaign = campaigns.find(c => c.id === campaignId);
    showError(`Failed to complete "${campaign?.name ?? 'campaign'}": ${err.getUserMessage()}`, err.correlationId ?? undefined);
  }, [showError, campaigns]);

  const handleArchiveError = useCallback((err: ApiError, campaignId: string) => {
    const campaign = campaigns.find(c => c.id === campaignId);
    showError(`Failed to archive "${campaign?.name ?? 'campaign'}": ${err.getUserMessage()}`, err.correlationId ?? undefined);
  }, [showError, campaigns]);

  const handleRollbackError = useCallback((err: ApiError, campaignId: string) => {
    const campaign = campaigns.find(c => c.id === campaignId);
    showError(`Failed to rollback "${campaign?.name ?? 'campaign'}": ${err.getUserMessage()}`, err.correlationId ?? undefined);
  }, [showError, campaigns]);

  const handleDuplicateError = useCallback((err: ApiError, campaignId: string) => {
    const campaign = campaigns.find(c => c.id === campaignId);
    showError(`Failed to duplicate "${campaign?.name ?? 'campaign'}": ${err.getUserMessage()}`, err.correlationId ?? undefined);
  }, [showError, campaigns]);

  const { execute: complete, isPendingFor: isCompletingFor } = useCompleteCampaign(workspaceId ?? null, handleCompleteError);
  const { execute: archive, isPendingFor: isArchivingFor } = useArchiveCampaign(workspaceId ?? null, handleArchiveError);
  const { execute: rollback, isPendingFor: isRollingBackFor } = useRollbackCampaign(workspaceId ?? null, handleRollbackError);
  const { execute: duplicate, isPendingFor: isDuplicatingFor } = useDuplicateCampaign(workspaceId ?? null, handleDuplicateError);

  const handleComplete = useCallback(async (campaignId: string, campaignName: string) => {
    try {
      await complete(campaignId);
      showSuccess(`Campaign "${campaignName}" marked as completed`);
    } catch { /* handled by handleCompleteError */ }
  }, [complete, showSuccess]);

  const handleArchive = useCallback(async (campaignId: string, campaignName: string) => {
    if (!window.confirm(`Archive campaign "${campaignName}"? This cannot be undone.`)) return;
    try {
      await archive(campaignId);
      showSuccess(`Campaign "${campaignName}" archived`);
    } catch { /* handled by handleArchiveError */ }
  }, [archive, showSuccess]);

  const handleRollback = useCallback(async (campaignId: string, campaignName: string) => {
    if (!window.confirm(`Roll back campaign "${campaignName}" to DRAFT?`)) return;
    try {
      await rollback(campaignId);
      showSuccess(`Campaign "${campaignName}" rolled back to draft`);
    } catch { /* handled by handleRollbackError */ }
  }, [rollback, showSuccess]);

  const handleDuplicate = useCallback(async (campaignId: string, campaignName: string) => {
    const newName = window.prompt('Name for the duplicate campaign:', `${campaignName} (copy)`);
    if (!newName?.trim()) return;
    try {
      await duplicate(campaignId, newName.trim());
      showSuccess(`Campaign duplicated as "${newName.trim()}"`);
    } catch { /* handled by handleDuplicateError */ }
  }, [duplicate, showSuccess]);

  const handleCreate = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!name.trim() || !workspaceId) return;
      const budget = parseFloat(budgetDollars);
      if (isNaN(budget) || budget <= 0) return;
      try {
        await create({
          name: name.trim(),
          type,
          objective,
          budgetCents: Math.round(budget * 100),
          startDate,
          endDate,
          audience: audience.trim(),
          landingPageUrl: landingPageUrl.trim() || undefined,
        });
        showSuccess(`Campaign "${name.trim()}" created successfully`);
        setName('');
        setBudgetDollars('');
        setStartDate('');
        setEndDate('');
        setAudience('');
        setLandingPageUrl('');
        setPage(0);
      } catch {
        // Error is handled by handleCreateError callback
      }
    },
    [create, name, type, objective, budgetDollars, startDate, endDate, audience, landingPageUrl, workspaceId, showSuccess],
  );

  const handleLaunch = useCallback(async (campaignId: string, campaignName: string) => {
    try {
      await launch(campaignId);
      showSuccess(`Campaign "${campaignName}" launched successfully`);
    } catch {
      // Error is handled by handleLaunchError callback
    }
  }, [launch, showSuccess]);

  const handlePause = useCallback(async (campaignId: string, campaignName: string) => {
    try {
      await pause(campaignId);
      showSuccess(`Campaign "${campaignName}" paused successfully`);
    } catch {
      // Error is handled by handlePauseError callback
    }
  }, [pause, showSuccess]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <>
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
      <section data-testid="campaigns-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Campaigns</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      <section className="mb-8 p-4 border rounded bg-white">
        <h2 className="text-lg font-semibold mb-3">Create Campaign</h2>
        <form onSubmit={handleCreate} className="grid grid-cols-2 gap-3 md:grid-cols-3">
          <div className="col-span-2 md:col-span-1">
            <TextField
              data-testid="campaign-name-input"
              label="Name *"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Q4 Acquisition"
              required
              fullWidth
            />
          </div>
          <div>
            <Select
              data-testid="campaign-type-select"
              label="Type *"
              value={type}
              onChange={(e) => setType(e.target.value as CampaignType)}
              options={CAMPAIGN_TYPES.map((t) => ({ value: t, label: t }))}
              fullWidth
            />
          </div>
          <div>
            <Select
              data-testid="campaign-objective-select"
              label="Objective *"
              value={objective}
              onChange={(e) => setObjective(e.target.value as CampaignObjective)}
              options={CAMPAIGN_OBJECTIVES.map((o) => ({ value: o, label: o }))}
              fullWidth
            />
          </div>
          <div>
            <TextField
              data-testid="campaign-budget-input"
              label="Budget (USD) *"
              type="number"
              min="1"
              step="0.01"
              value={budgetDollars}
              onChange={(e) => setBudgetDollars(e.target.value)}
              placeholder="500.00"
              required
              fullWidth
            />
          </div>
          <div>
            <TextField
              data-testid="campaign-start-date-input"
              label="Start Date *"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              required
              fullWidth
            />
          </div>
          <div>
            <TextField
              data-testid="campaign-end-date-input"
              label="End Date *"
              type="date"
              value={endDate}
              inputProps={{ min: startDate }}
              onChange={(e) => setEndDate(e.target.value)}
              required
              fullWidth
            />
          </div>
          <div className="col-span-2">
            <TextField
              data-testid="campaign-audience-input"
              label="Audience *"
              type="text"
              value={audience}
              onChange={(e) => setAudience(e.target.value)}
              placeholder="E.g. SMB owners 25–45 in US"
              required
              fullWidth
            />
          </div>
          <div className="col-span-2 md:col-span-1">
            <TextField
              data-testid="campaign-landing-page-input"
              label="Landing Page URL"
              type="url"
              value={landingPageUrl}
              onChange={(e) => setLandingPageUrl(e.target.value)}
              placeholder="https://example.com/landing"
              fullWidth
            />
          </div>
          <div className="col-span-2 md:col-span-3 flex justify-end">
            <Button
              data-testid="create-campaign-btn"
              type="submit"
              disabled={isCreating || !name.trim() || !audience.trim() || !budgetDollars || !startDate || !endDate}
              loading={isCreating}
              loadingText="Creating…"
              tone="primary"
            >
              Create Campaign
            </Button>
          </div>
        </form>
      </section>

      {isLoading && (
        <p data-testid="campaigns-loading" className="text-sm text-gray-400">
          Loading campaigns…
        </p>
      )}

      {isError && (
        <p data-testid="campaigns-error" role="alert" className="text-sm text-red-600">
          {error instanceof ApiError ? error.getUserMessage() : 'Failed to load campaigns.'}
        </p>
      )}

      {!isLoading && !isError && campaigns.length === 0 && (
        <p data-testid="campaigns-empty" className="text-sm text-gray-500">
          No campaigns yet. Create your first campaign above.
        </p>
      )}

      {!isLoading && !isError && campaigns.length > 0 && (
        <>
          <div data-testid="campaigns-list" className="border rounded overflow-hidden">
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell component="th" className="text-left px-4 py-2 font-medium">Name</TableCell>
                  <TableCell component="th" className="text-left px-4 py-2 font-medium">Type</TableCell>
                  <TableCell component="th" className="text-left px-4 py-2 font-medium">Status</TableCell>
                  <TableCell component="th" className="text-left px-4 py-2 font-medium">Created</TableCell>
                  <TableCell component="th" className="text-left px-4 py-2 font-medium">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {campaigns.map((c) => (
                  <TableRow key={c.id} className="border-t" data-testid={`campaign-row-${c.id}`}>
                    <TableCell className="px-4 py-2">{c.name}</TableCell>
                    <TableCell className="px-4 py-2">{c.type}</TableCell>
                    <TableCell className="px-4 py-2">
                      <Badge
                        tone={
                          c.status === 'LAUNCHED' ? 'success'
                          : c.status === 'PAUSED' ? 'warning'
                          : 'neutral'
                        }
                      >
                        {c.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="px-4 py-2 text-gray-500">{new Date(c.createdAt).toLocaleDateString()}</TableCell>
                    <TableCell className="px-4 py-2">
                      <div className="flex gap-2">
                        {c.status === 'DRAFT' && (
                          <Button
                            data-testid={`launch-campaign-${c.id}`}
                            size="sm"
                            tone="success"
                            onClick={() => handleLaunch(c.id, c.name)}
                            disabled={isLaunchingFor(c.id)}
                            loading={isLaunchingFor(c.id)}
                            loadingText="Launching…"
                          >
                            Launch
                          </Button>
                        )}
                        {c.status === 'LAUNCHED' && (
                          <Button
                            data-testid={`pause-campaign-${c.id}`}
                            size="sm"
                            tone="warning"
                            onClick={() => handlePause(c.id, c.name)}
                            disabled={isPausingFor(c.id)}
                            loading={isPausingFor(c.id)}
                            loadingText="Pausing…"
                          >
                            Pause
                          </Button>
                        )}
                        {c.status === 'LAUNCHED' && (
                          <Button
                            data-testid={`complete-campaign-${c.id}`}
                            size="sm"
                            tone="primary"
                            onClick={() => handleComplete(c.id, c.name)}
                            disabled={isCompletingFor(c.id)}
                            loading={isCompletingFor(c.id)}
                            loadingText="Completing…"
                          >
                            Complete
                          </Button>
                        )}
                        {c.status === 'COMPLETED' && (
                          <Button
                            data-testid={`archive-campaign-${c.id}`}
                            size="sm"
                            tone="neutral"
                            onClick={() => handleArchive(c.id, c.name)}
                            disabled={isArchivingFor(c.id)}
                            loading={isArchivingFor(c.id)}
                            loadingText="Archiving…"
                          >
                            Archive
                          </Button>
                        )}
                        {c.status === 'LAUNCHED' && rollbackEnabled && (
                          <Button
                            data-testid={`rollback-campaign-${c.id}`}
                            size="sm"
                            tone="warning"
                            variant="soft"
                            onClick={() => handleRollback(c.id, c.name)}
                            disabled={isRollingBackFor(c.id)}
                            loading={isRollingBackFor(c.id)}
                            loadingText="Rolling back…"
                          >
                            Rollback
                          </Button>
                        )}
                        <Button
                          data-testid={`duplicate-campaign-${c.id}`}
                          size="sm"
                          variant="outline"
                          tone="neutral"
                          onClick={() => handleDuplicate(c.id, c.name)}
                          disabled={isDuplicatingFor(c.id)}
                          loading={isDuplicatingFor(c.id)}
                          loadingText="Duplicating…"
                        >
                          Duplicate
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {/* P1-001: Pagination controls */}
          <div
            data-testid="campaigns-pagination"
            className="flex items-center justify-between mt-4 text-sm text-gray-600"
          >
            <span data-testid="campaigns-count">
              {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, count)} of {count} campaign{count !== 1 ? 's' : ''}
            </span>
            <div className="flex gap-2">
              <Button
                data-testid="campaigns-prev-page"
                variant="outline"
                tone="neutral"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                ← Previous
              </Button>
              <span className="px-3 py-1">
                Page {page + 1} of {totalPages}
              </span>
              <Button
                data-testid="campaigns-next-page"
                variant="outline"
                tone="neutral"
                size="sm"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
              >
                Next →
              </Button>
            </div>
          </div>
        </>
      )}
      </section>
    </>
  );
}
