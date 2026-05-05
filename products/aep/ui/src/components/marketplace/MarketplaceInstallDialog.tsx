/**
 * MarketplaceInstallDialog — governed install flow for marketplace agents.
 *
 * @doc.type component
 * @doc.purpose Collects version-pinned install confirmation after a marketplace simulation preflight
 * @doc.layer frontend
 */
import React from 'react';
import { Button, Select, TextField, TextArea } from '@ghatana/design-system';
import type {
  MarketplaceAgentListing,
  MarketplaceInstallSimulation,
} from '@/api/aep.api';

export interface MarketplaceInstallDialogProps {
  listing: MarketplaceAgentListing;
  tenantId: string;
  environment: 'sandbox' | 'staging' | 'production';
  onEnvironmentChange: (environment: 'sandbox' | 'staging' | 'production') => void;
  simulation?: MarketplaceInstallSimulation;
  isSimulating: boolean;
  isInstalling: boolean;
  errorMessage?: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

function badgeTone(status?: MarketplaceInstallSimulation['compatibilityStatus']): string {
  switch (status) {
    case 'COMPATIBLE':
      return 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300';
    case 'REVIEW_REQUIRED':
      return 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300';
    case 'BLOCKED':
      return 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300';
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300';
  }
}

export function MarketplaceInstallDialog({
  listing,
  tenantId,
  environment,
  onEnvironmentChange,
  simulation,
  isSimulating,
  isInstalling,
  errorMessage,
  onConfirm,
  onCancel,
}: MarketplaceInstallDialogProps): React.ReactElement {
  const [reason, setReason] = React.useState('');
  const [confirmText, setConfirmText] = React.useState('');
  const canConfirm = confirmText.trim() === 'INSTALL' && reason.trim().length > 3 && simulation?.allowedToInstall;

  React.useEffect(() => {
    setReason('');
    setConfirmText('');
  }, [listing.id]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="marketplace-install-title"
        className="max-h-[85vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-xl dark:bg-gray-900"
      >
        <div className="border-b border-gray-200 px-6 py-4 dark:border-gray-800">
          <h2 id="marketplace-install-title" className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Install marketplace agent
          </h2>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Version pinning, compatibility review, and execution-path truth are required before this agent is registered for tenant use.
          </p>
        </div>

        <div className="space-y-5 px-6 py-5">
          <div className="grid gap-3 rounded-xl border border-gray-200 bg-gray-50 p-4 text-sm dark:border-gray-800 dark:bg-gray-950 md:grid-cols-2">
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-400">Agent</div>
              <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">{listing.name}</div>
            </div>
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-400">Pinned version</div>
              <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">v{listing.version}</div>
            </div>
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-400">Owner</div>
              <div className="mt-1 text-gray-700 dark:text-gray-300">{listing.owner}</div>
            </div>
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-400">Tenant</div>
              <div className="mt-1 text-gray-700 dark:text-gray-300">{tenantId}</div>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="target-environment">
              Target environment
            </label>
            <Select
              id="target-environment"
              value={environment}
              onChange={(event) => onEnvironmentChange(event.target.value as 'sandbox' | 'staging' | 'production')}
              className="mt-2"
            >
              <option value="sandbox">sandbox</option>
              <option value="staging">staging</option>
              <option value="production">production</option>
            </Select>
            <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
              Marketplace listings may be installed for production, but direct execution remains sandbox-only. Production execution must route through pipeline + HITL.
            </p>
          </div>

          <div className="rounded-xl border border-gray-200 p-4 dark:border-gray-800">
            <div className="flex items-center justify-between gap-3">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Simulation preflight</h3>
              <span className={`rounded-full px-3 py-1 text-xs font-medium ${badgeTone(simulation?.compatibilityStatus)}`}>
                {isSimulating ? 'Simulating…' : simulation?.compatibilityStatus ?? 'Pending'}
              </span>
            </div>

            {simulation ? (
              <div className="mt-4 space-y-4">
                <div className="grid gap-3 md:grid-cols-2">
                  <div className="rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-950">
                    <div className="text-xs uppercase tracking-wide text-gray-400">Direct execution</div>
                    <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">{simulation.directExecutionMode}</div>
                  </div>
                  <div className="rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-950">
                    <div className="text-xs uppercase tracking-wide text-gray-400">Production path</div>
                    <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">{simulation.productionExecutionMode}</div>
                  </div>
                  <div className="rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-950">
                    <div className="text-xs uppercase tracking-wide text-gray-400">Requested version</div>
                    <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">{simulation.requestedVersion}</div>
                  </div>
                  <div className="rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-950">
                    <div className="text-xs uppercase tracking-wide text-gray-400">Recommended path</div>
                    <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">{simulation.recommendedPath}</div>
                  </div>
                </div>

                <ul className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                  {simulation.compatibilityNotes.map((note) => (
                    <li key={note} className="rounded-lg border border-gray-200 px-3 py-2 dark:border-gray-800">
                      {note}
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">Waiting for marketplace simulation results…</p>
            )}
          </div>

          {errorMessage ? (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">
              {errorMessage}
            </div>
          ) : null}

          <div>
            <label htmlFor="install-reason" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Reason
            </label>
            <TextArea
              id="install-reason"
              rows={3}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Why should this listing be registered for tenant use?"
              className="mt-2 w-full"
            />
          </div>

          <div>
            <label htmlFor="install-confirmation" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Type <code className="rounded bg-gray-100 px-1 py-0.5 dark:bg-gray-800">INSTALL</code> to confirm
            </label>
            <TextField
              id="install-confirmation"
              type="text"
              value={confirmText}
              onChange={(event) => setConfirmText(event.target.value)}
              className="mt-2"
              fullWidth
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-4 dark:border-gray-800 dark:bg-gray-950">
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="primary"
            disabled={!canConfirm || isInstalling || isSimulating}
            onClick={() => onConfirm(reason.trim())}
          >
            {isInstalling ? 'Installing…' : 'Confirm install'}
          </Button>
        </div>
      </div>
    </div>
  );
}
