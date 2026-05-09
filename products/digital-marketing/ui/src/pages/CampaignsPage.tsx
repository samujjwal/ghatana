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
import { canPerformAction } from '@/lib/action-permissions';
import type { CampaignType, CampaignObjective } from '@/types/campaign';
import {
  Button,
  TextField,
  Select,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Badge,
} from '@ghatana/design-system';
import { ToastContainer } from '@/components/Toast';
import { PageStateNotice } from '@/components/PageStateNotice';

const CAMPAIGN_TYPES: CampaignType[] = ['EMAIL', 'SOCIAL', 'PAID_SEARCH', 'PUSH', 'SMS', 'OMNICHANNEL'];
const CAMPAIGN_OBJECTIVES: CampaignObjective[] = ['AWARENESS', 'LEADS', 'CONVERSIONS', 'RETENTION', 'ENGAGEMENT', 'TRAFFIC'];

const PAGE_SIZE = 20;

export function CampaignsPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated, roles } = useAuth();
  const { toasts, showSuccess, showError, dismissToast } = useToast();
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
  const [archiveTarget, setArchiveTarget] = useState<{ id: string; name: string } | null>(null);
  const [rollbackTarget, setRollbackTarget] = useState<{ id: string; name: string } | null>(null);
  const [duplicateTarget, setDuplicateTarget] = useState<{ id: string; name: string } | null>(null);
  const [duplicateName, setDuplicateName] = useState('');
  const [duplicateNameError, setDuplicateNameError] = useState<string | null>(null);
  const canCreateCampaign = canPerformAction(roles, 'create-campaign');
  const canLaunchCampaign = canPerformAction(roles, 'launch-campaign');
  const canPauseCampaign = canPerformAction(roles, 'pause-campaign');
  const canCompleteCampaign = canPerformAction(roles, 'complete-campaign');
  const canArchiveCampaign = canPerformAction(roles, 'archive-campaign');
  const canRollbackCampaign = canPerformAction(roles, 'rollback-campaign');
  const canDuplicateCampaign = canPerformAction(roles, 'duplicate-campaign');

  const { campaigns, count, isLoading, isError, error } = useCampaigns(workspaceId ?? null, {
    limit: PAGE_SIZE,
    offset: page * PAGE_SIZE,
  });

  const totalPages = Math.max(1, Math.ceil(count / PAGE_SIZE));

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

  const handleUnexpectedMutationError = useCallback((error: unknown, context: string) => {
    if (error instanceof ApiError) {
      return;
    }

    showError(`${context}: An unexpected error occurred.`);
  }, [showError]);

  const handleComplete = useCallback(async (campaignId: string, campaignName: string) => {
    await complete(campaignId)
      .then(() => {
        showSuccess(`Campaign "${campaignName}" marked as completed`);
      })
      .catch((error: unknown) => handleUnexpectedMutationError(error, 'Failed to complete campaign'));
  }, [complete, showSuccess, handleUnexpectedMutationError]);

  const openArchiveDialog = useCallback((campaignId: string, campaignName: string) => {
    if (!canArchiveCampaign) return;
    setArchiveTarget({ id: campaignId, name: campaignName });
  }, [canArchiveCampaign]);

  const confirmArchive = useCallback(async () => {
    if (!archiveTarget) return;
    await archive(archiveTarget.id)
      .then(() => {
        showSuccess(`Campaign "${archiveTarget.name}" archived`);
        setArchiveTarget(null);
      })
      .catch((error: unknown) => handleUnexpectedMutationError(error, 'Failed to archive campaign'));
  }, [archive, archiveTarget, showSuccess, handleUnexpectedMutationError]);

  const openRollbackDialog = useCallback((campaignId: string, campaignName: string) => {
    if (!canRollbackCampaign) return;
    setRollbackTarget({ id: campaignId, name: campaignName });
  }, [canRollbackCampaign]);

  const confirmRollback = useCallback(async () => {
    if (!rollbackTarget) return;
    await rollback(rollbackTarget.id)
      .then(() => {
        showSuccess(`Campaign "${rollbackTarget.name}" rolled back to draft`);
        setRollbackTarget(null);
      })
      .catch((error: unknown) => handleUnexpectedMutationError(error, 'Failed to rollback campaign'));
  }, [rollback, rollbackTarget, showSuccess, handleUnexpectedMutationError]);

  const openDuplicateDialog = useCallback((campaignId: string, campaignName: string) => {
    if (!canDuplicateCampaign) return;
    setDuplicateTarget({ id: campaignId, name: campaignName });
    setDuplicateName(`${campaignName} (copy)`);
    setDuplicateNameError(null);
  }, [canDuplicateCampaign]);

  const confirmDuplicate = useCallback(async () => {
    if (!duplicateTarget) return;
    if (!duplicateName.trim()) {
      setDuplicateNameError('Duplicate campaign name is required.');
      return;
    }

    const resolvedName = duplicateName.trim();
    await duplicate(duplicateTarget.id, resolvedName)
      .then(() => {
        showSuccess(`Campaign duplicated as "${resolvedName}"`);
        setDuplicateTarget(null);
        setDuplicateName('');
        setDuplicateNameError(null);
      })
      .catch((error: unknown) => handleUnexpectedMutationError(error, 'Failed to duplicate campaign'));
  }, [duplicate, duplicateName, duplicateTarget, showSuccess, handleUnexpectedMutationError]);

  const handleCreate = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!name.trim() || !workspaceId) return;
      const budget = parseFloat(budgetDollars);
      if (isNaN(budget) || budget <= 0) return;
      await create({
        name: name.trim(),
        type,
        objective,
        budgetCents: Math.round(budget * 100),
        startDate,
        endDate,
        audience: audience.trim(),
        landingPageUrl: landingPageUrl.trim() || undefined,
      })
        .then(() => {
          showSuccess(`Campaign "${name.trim()}" created successfully`);
          setName('');
          setBudgetDollars('');
          setStartDate('');
          setEndDate('');
          setAudience('');
          setLandingPageUrl('');
          setPage(0);
        })
        .catch((error: unknown) => handleUnexpectedMutationError(error, 'Failed to create campaign'));
    },
    [
      create,
      name,
      type,
      objective,
      budgetDollars,
      startDate,
      endDate,
      audience,
      landingPageUrl,
      workspaceId,
      showSuccess,
      handleUnexpectedMutationError,
    ],
  );

  const handleLaunch = useCallback(async (campaignId: string, campaignName: string) => {
    await launch(campaignId)
      .then(() => {
        showSuccess(`Campaign "${campaignName}" launched successfully`);
      })
      .catch((error: unknown) => handleUnexpectedMutationError(error, 'Failed to launch campaign'));
  }, [launch, showSuccess, handleUnexpectedMutationError]);

  const handlePause = useCallback(async (campaignId: string, campaignName: string) => {
    await pause(campaignId)
      .then(() => {
        showSuccess(`Campaign "${campaignName}" paused successfully`);
      })
      .catch((error: unknown) => handleUnexpectedMutationError(error, 'Failed to pause campaign'));
  }, [pause, showSuccess, handleUnexpectedMutationError]);

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
              disabled={isCreating || !canCreateCampaign || !name.trim() || !audience.trim() || !budgetDollars || !startDate || !endDate}
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
        <PageStateNotice
          testId="campaigns-loading"
          tone="loading"
          message="Loading campaigns…"
        />
      )}

      {isError && (
        <PageStateNotice
          testId="campaigns-error"
          tone="error"
          message={error instanceof ApiError ? error.getUserMessage() : 'Failed to load campaigns.'}
        />
      )}

      {!isLoading && !isError && campaigns.length === 0 && (
        <PageStateNotice
          testId="campaigns-empty"
          tone="empty"
          message="No campaigns yet. Create your first campaign above."
        />
      )}

      {!isLoading && !isError && campaigns.length > 0 && (
        <>
          {!canCreateCampaign && (
            <p
              data-testid="campaign-action-permission-banner"
              className="mb-4 text-sm text-yellow-700 bg-yellow-50 px-3 py-2 rounded"
            >
              You have view-only campaign access. Mutation actions are restricted by role.
            </p>
          )}
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
                            disabled={isLaunchingFor(c.id) || !canLaunchCampaign}
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
                            disabled={isPausingFor(c.id) || !canPauseCampaign}
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
                            disabled={isCompletingFor(c.id) || !canCompleteCampaign}
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
                            onClick={() => openArchiveDialog(c.id, c.name)}
                            disabled={isArchivingFor(c.id) || !canArchiveCampaign}
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
                            onClick={() => openRollbackDialog(c.id, c.name)}
                            disabled={isRollingBackFor(c.id) || !canRollbackCampaign}
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
                          onClick={() => openDuplicateDialog(c.id, c.name)}
                          disabled={isDuplicatingFor(c.id) || !canDuplicateCampaign}
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

      <Dialog
        open={archiveTarget !== null}
        onClose={() => setArchiveTarget(null)}
        size="sm"
        aria-labelledby="archive-dialog-title"
        data-testid="archive-dialog"
      >
        <DialogTitle id="archive-dialog-title">Archive Campaign</DialogTitle>
        <DialogContent>
          <p className="text-sm text-gray-700">
            Archive campaign "{archiveTarget?.name}"? This action is destructive and cannot be undone.
          </p>
        </DialogContent>
        <DialogActions>
          <Button
            data-testid="archive-cancel-btn"
            variant="outline"
            tone="neutral"
            onClick={() => setArchiveTarget(null)}
            disabled={archiveTarget ? isArchivingFor(archiveTarget.id) : false}
          >
            Cancel
          </Button>
          <Button
            data-testid="archive-confirm-btn"
            tone="danger"
            onClick={confirmArchive}
            disabled={archiveTarget ? isArchivingFor(archiveTarget.id) || !canArchiveCampaign : true}
            loading={archiveTarget ? isArchivingFor(archiveTarget.id) : false}
            loadingText="Archiving…"
          >
            Archive Campaign
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={rollbackTarget !== null}
        onClose={() => setRollbackTarget(null)}
        size="sm"
        aria-labelledby="rollback-dialog-title"
        data-testid="rollback-dialog"
      >
        <DialogTitle id="rollback-dialog-title">Rollback Campaign</DialogTitle>
        <DialogContent>
          <p className="text-sm text-gray-700">
            Roll back campaign "{rollbackTarget?.name}" to DRAFT?
          </p>
        </DialogContent>
        <DialogActions>
          <Button
            data-testid="rollback-cancel-btn"
            variant="outline"
            tone="neutral"
            onClick={() => setRollbackTarget(null)}
            disabled={rollbackTarget ? isRollingBackFor(rollbackTarget.id) : false}
          >
            Cancel
          </Button>
          <Button
            data-testid="rollback-confirm-btn"
            tone="warning"
            onClick={confirmRollback}
            disabled={rollbackTarget ? isRollingBackFor(rollbackTarget.id) || !canRollbackCampaign : true}
            loading={rollbackTarget ? isRollingBackFor(rollbackTarget.id) : false}
            loadingText="Rolling back…"
          >
            Confirm Rollback
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={duplicateTarget !== null}
        onClose={() => {
          setDuplicateTarget(null);
          setDuplicateNameError(null);
        }}
        size="sm"
        aria-labelledby="duplicate-dialog-title"
        data-testid="duplicate-dialog"
      >
        <DialogTitle id="duplicate-dialog-title">Duplicate Campaign</DialogTitle>
        <DialogContent>
          <p className="text-sm text-gray-700 mb-3">
            Enter a name for the duplicate of "{duplicateTarget?.name}".
          </p>
          <TextField
            data-testid="duplicate-name-input"
            label="Duplicate Name"
            type="text"
            value={duplicateName}
            onChange={(e) => {
              setDuplicateName(e.target.value);
              if (duplicateNameError) setDuplicateNameError(null);
            }}
            required
            fullWidth
          />
          {duplicateNameError && (
            <p className="mt-2 text-sm text-red-600" data-testid="duplicate-name-error" role="alert">
              {duplicateNameError}
            </p>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            data-testid="duplicate-cancel-btn"
            variant="outline"
            tone="neutral"
            onClick={() => {
              setDuplicateTarget(null);
              setDuplicateNameError(null);
            }}
            disabled={duplicateTarget ? isDuplicatingFor(duplicateTarget.id) : false}
          >
            Cancel
          </Button>
          <Button
            data-testid="duplicate-confirm-btn"
            tone="primary"
            onClick={confirmDuplicate}
            disabled={duplicateTarget ? isDuplicatingFor(duplicateTarget.id) || !canDuplicateCampaign : true}
            loading={duplicateTarget ? isDuplicatingFor(duplicateTarget.id) : false}
            loadingText="Duplicating…"
          >
            Duplicate Campaign
          </Button>
        </DialogActions>
      </Dialog>
      </section>
    </>
  );
}
