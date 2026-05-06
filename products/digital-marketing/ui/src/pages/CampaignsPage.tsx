/**
 * Campaigns Page - Campaign management UI.
 *
 * <p>P1-030: Surfaces mutation errors with toast notifications and correlation ID.</p>
 * <p>P1-031: Per-row action pending states for concurrent operations.</p>
 *
 * @doc.type page
 * @doc.purpose Campaign listing, creation, launch, and pause with error handling
 * @doc.layer frontend
 */
import React, { useState, useCallback } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useCampaigns, useCreateCampaign, useLaunchCampaign, usePauseCampaign } from '@/hooks/useCampaigns';
import { useToast } from '@/hooks/useToast';
import { ToastContainer } from '@/components/Toast';
import { ApiError } from '@/lib/http-client';
import type { CampaignType } from '@/types/campaign';

const CAMPAIGN_TYPES: CampaignType[] = ['EMAIL', 'SOCIAL', 'PAID_SEARCH', 'PUSH', 'SMS', 'OMNICHANNEL'];

export function CampaignsPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const { toasts, showSuccess, showError, dismissToast } = useToast();
  const [name, setName] = useState('');
  const [type, setType] = useState<CampaignType>('EMAIL');

  const { campaigns, isLoading, isError, error } = useCampaigns(workspaceId ?? null);

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

  const handleCreate = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!name.trim() || !workspaceId) return;
      try {
        await create({ name: name.trim(), type });
        showSuccess(`Campaign "${name.trim()}" created successfully`);
        setName('');
      } catch {
        // Error is handled by handleCreateError callback
      }
    },
    [create, name, type, workspaceId, showSuccess],
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
        <form onSubmit={handleCreate} className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm flex-1 min-w-[200px]">
            Name
            <input
              data-testid="campaign-name-input"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="Q4 Acquisition"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Type
            <select
              data-testid="campaign-type-select"
              value={type}
              onChange={(e) => setType(e.target.value as CampaignType)}
              className="border rounded px-2 py-1"
            >
              {CAMPAIGN_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
          <button
            data-testid="create-campaign-btn"
            type="submit"
            disabled={isCreating || !name.trim()}
            className="px-4 py-1 bg-blue-600 text-white rounded text-sm disabled:opacity-50"
          >
            {isCreating ? 'Creating…' : 'Create'}
          </button>
        </form>
      </section>

      {isLoading && (
        <p data-testid="campaigns-loading" className="text-sm text-gray-400">
          Loading campaigns…
        </p>
      )}

      {isError && (
        <p data-testid="campaigns-error" role="alert" className="text-sm text-red-600">
          {error instanceof Error ? error.message : 'Failed to load campaigns.'}
        </p>
      )}

      {!isLoading && !isError && campaigns.length === 0 && (
        <p data-testid="campaigns-empty" className="text-sm text-gray-500">
          No campaigns yet. Create your first campaign above.
        </p>
      )}

      {!isLoading && !isError && campaigns.length > 0 && (
        <div data-testid="campaigns-list" className="border rounded overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Name</th>
                <th className="text-left px-4 py-2 font-medium">Type</th>
                <th className="text-left px-4 py-2 font-medium">Status</th>
                <th className="text-left px-4 py-2 font-medium">Created</th>
                <th className="text-left px-4 py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {campaigns.map((c) => (
                <tr key={c.id} className="border-t" data-testid={`campaign-row-${c.id}`}>
                  <td className="px-4 py-2">{c.name}</td>
                  <td className="px-4 py-2">{c.type}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                        c.status === 'LAUNCHED'
                          ? 'bg-green-100 text-green-800'
                          : c.status === 'PAUSED'
                            ? 'bg-yellow-100 text-yellow-800'
                            : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {c.status}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-gray-500">{new Date(c.createdAt).toLocaleDateString()}</td>
                  <td className="px-4 py-2">
                    <div className="flex gap-2">
                      {c.status === 'DRAFT' && (
                        <button
                          data-testid={`launch-campaign-${c.id}`}
                          onClick={() => handleLaunch(c.id, c.name)}
                          disabled={isLaunchingFor(c.id)}
                          className="px-3 py-1 bg-green-600 text-white rounded text-xs disabled:opacity-50"
                        >
                          {isLaunchingFor(c.id) ? 'Launching…' : 'Launch'}
                        </button>
                      )}
                      {c.status === 'LAUNCHED' && (
                        <button
                          data-testid={`pause-campaign-${c.id}`}
                          onClick={() => handlePause(c.id, c.name)}
                          disabled={isPausingFor(c.id)}
                          className="px-3 py-1 bg-yellow-500 text-white rounded text-xs disabled:opacity-50"
                        >
                          {isPausingFor(c.id) ? 'Pausing…' : 'Pause'}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      </section>
    </>
  );
}
