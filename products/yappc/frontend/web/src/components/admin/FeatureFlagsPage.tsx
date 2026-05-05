/**
 * Feature Flags Page (Admin-Only)
 *
 * Tenant-scoped feature flag management surface (F-Y047).
 * Allows OWNER/ADMIN to view, toggle, and audit all feature flags
 * scoped to the current tenant.
 *
 * @doc.type component
 * @doc.purpose Admin-only tenant feature flag management UI
 * @doc.layer product
 * @doc.pattern Admin Component
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  AlertCircle,
  Check,
  ChevronDown,
  ChevronUp,
  History,
  Loader2,
  RefreshCw,
  ToggleLeft,
  ToggleRight,
  X,
} from 'lucide-react';
import {
  Box,
  Card,
  CardContent,
  Chip,
  Typography,
} from '@ghatana/design-system';
import {
  listTenantFeatureFlags,
  setFeatureFlag,
  getFeatureFlagAuditLog,
  type FeatureFlag,
  type FeatureFlagAuditEntry,
} from '../../services/admin/featureFlagsApi';
import { useAtomValue } from 'jotai';
import { currentWorkspaceIdAtom } from '../../state/atoms/workspaceAtom';

// ─────────────────────────────────────────────────────────────────────────────
// Props
// ─────────────────────────────────────────────────────────────────────────────

export interface FeatureFlagsPageProps {
  className?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

interface ToggleConfirmDialogProps {
  flag: FeatureFlag;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
  isBusy: boolean;
}

const ToggleConfirmDialog: React.FC<ToggleConfirmDialogProps> = ({
  flag,
  onConfirm,
  onCancel,
  isBusy,
}) => {
  const [reason, setReason] = useState('');
  const action = flag.enabled ? 'disable' : 'enable';

  return (
    <Box
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="flag-dialog-title"
      data-testid="flag-toggle-dialog"
    >
      <Card className="w-full max-w-md shadow-xl">
        <CardContent className="p-5 space-y-4">
          <Box className="flex items-start justify-between">
            <Typography id="flag-dialog-title" className="text-base font-semibold capitalize">
              {action} <code className="text-sm">{flag.key}</code>?
            </Typography>
            <button
              type="button"
              onClick={onCancel}
              className="rounded p-1 text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800"
              aria-label="Cancel"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </Box>

          <Typography className="text-sm text-text-secondary">
            {flag.description}
          </Typography>

          <Box className="space-y-1">
            <label htmlFor="flag-reason" className="block text-xs font-medium text-text-secondary">
              Reason (required)
            </label>
            <textarea
              id="flag-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={2}
              placeholder="Explain why you're changing this flag…"
              className="w-full rounded border border-divider bg-white dark:bg-grey-800 px-2 py-1 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary resize-none"
            />
          </Box>

          <Box className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              disabled={isBusy}
              className="rounded px-4 py-2 text-sm font-medium text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800 disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => onConfirm(reason)}
              disabled={isBusy || reason.trim().length === 0}
              className="flex items-center gap-1 rounded bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary/90 disabled:opacity-50"
              aria-busy={isBusy}
            >
              {isBusy ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
              ) : (
                <Check className="h-3.5 w-3.5" aria-hidden="true" />
              )}
              Confirm
            </button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

interface AuditDrawerProps {
  tenantId: string;
  flagKey: string;
  onClose: () => void;
}

const AuditDrawer: React.FC<AuditDrawerProps> = ({ tenantId, flagKey, onClose }) => {
  const { data: entries = [], isLoading } = useQuery<FeatureFlagAuditEntry[]>({
    queryKey: ['flag-audit', tenantId, flagKey],
    queryFn: () => getFeatureFlagAuditLog(tenantId, flagKey),
    staleTime: 60_000,
  });

  return (
    <Box
      className="fixed inset-y-0 right-0 z-40 w-80 bg-background border-l border-divider shadow-xl overflow-y-auto p-4 space-y-3"
      data-testid="flag-audit-drawer"
    >
      <Box className="flex items-center justify-between">
        <Typography className="font-semibold text-sm">Audit log: {flagKey}</Typography>
        <button
          type="button"
          onClick={onClose}
          className="rounded p-1 text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800"
          aria-label="Close audit"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </Box>
      {isLoading && (
        <Box className="flex items-center gap-2 text-text-secondary">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          <Typography className="text-sm">Loading…</Typography>
        </Box>
      )}
      {!isLoading && entries.length === 0 && (
        <Typography className="text-sm text-text-secondary">No audit entries.</Typography>
      )}
      {entries.map((entry) => (
        <Box
          key={entry.id}
          className="rounded border border-divider p-3 space-y-1 text-xs"
        >
          <Box className="flex items-center gap-2">
            <Chip
              label={entry.newValue ? 'enabled' : 'disabled'}
              size="sm"
              className={
                entry.newValue
                  ? 'bg-emerald-100 text-emerald-800'
                  : 'bg-destructive-bg text-destructive'
              }
            />
            <span className="text-text-secondary">
              by {entry.changedBy}
            </span>
          </Box>
          <Typography className="text-text-secondary">
            {new Date(entry.timestamp).toLocaleString()}
          </Typography>
          {entry.reason && (
            <Typography className="italic text-text-secondary">"{entry.reason}"</Typography>
          )}
        </Box>
      ))}
    </Box>
  );
};

interface FlagRowProps {
  flag: FeatureFlag;
  tenantId: string;
  onToggle: (flag: FeatureFlag) => void;
  onShowAudit: (flag: FeatureFlag) => void;
}

const FlagRow: React.FC<FlagRowProps> = ({ flag, onToggle, onShowAudit }) => {
  return (
    <Box
      className="flex items-center gap-3 rounded-md border border-divider px-3 py-2.5"
      data-testid={`flag-row-${flag.key}`}
    >
      <Box className="flex-1 min-w-0">
        <Typography className="text-sm font-mono font-medium">{flag.key}</Typography>
        <Typography className="text-xs text-text-secondary truncate">{flag.description}</Typography>
        <Typography className="text-xs text-text-secondary">
          Rollout: {flag.rolloutPercentage}% · last updated by {flag.updatedBy}
        </Typography>
      </Box>
      <Box className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          onClick={() => onToggle(flag)}
          className="flex items-center gap-1 text-text-secondary hover:text-primary transition-colors"
          aria-label={`${flag.enabled ? 'Disable' : 'Enable'} ${flag.key}`}
          data-testid={`flag-toggle-${flag.key}`}
        >
          {flag.enabled ? (
            <ToggleRight className="h-5 w-5 text-emerald-500" aria-hidden="true" />
          ) : (
            <ToggleLeft className="h-5 w-5" aria-hidden="true" />
          )}
          <span className="text-xs font-medium">{flag.enabled ? 'On' : 'Off'}</span>
        </button>
        <button
          type="button"
          onClick={() => onShowAudit(flag)}
          className="rounded p-1 text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
          aria-label={`View audit for ${flag.key}`}
          data-testid={`flag-audit-${flag.key}`}
        >
          <History className="h-4 w-4" aria-hidden="true" />
        </button>
      </Box>
    </Box>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Main component
// ─────────────────────────────────────────────────────────────────────────────

/**
 * FeatureFlagsPage
 *
 * Admin-only tenant-scoped feature flag management (F-Y047).
 * Each flag toggle requires a reason which is recorded in an immutable audit log.
 *
 * @example
 * ```tsx
 * <FeatureFlagsPage />
 * ```
 */
export const FeatureFlagsPage: React.FC<FeatureFlagsPageProps> = ({ className }) => {
  const tenantId = useAtomValue(currentWorkspaceIdAtom) ?? 'default';
  const queryClient = useQueryClient();

  const [toggleTarget, setToggleTarget] = useState<FeatureFlag | null>(null);
  const [auditTarget, setAuditTarget] = useState<FeatureFlag | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [expanded, setExpanded] = useState(false);

  const {
    data: flags = [],
    isLoading,
    error,
    refetch,
  } = useQuery<FeatureFlag[]>({
    queryKey: ['tenant-feature-flags', tenantId],
    queryFn: () => listTenantFeatureFlags(tenantId),
    staleTime: 60_000,
  });

  const { mutate: toggle, isPending } = useMutation({
    mutationFn: ({ flag, reason }: { flag: FeatureFlag; reason: string }) =>
      setFeatureFlag(tenantId, {
        key: flag.key,
        enabled: !flag.enabled,
        reason,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tenant-feature-flags', tenantId] });
      setToggleTarget(null);
    },
  });

  const filteredFlags = flags.filter(
    (f) =>
      f.key.toLowerCase().includes(searchQuery.toLowerCase()) ||
      f.description.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const enabledCount = flags.filter((f) => f.enabled).length;

  return (
    <Box className={className} data-testid="feature-flags-page">
      {/* Confirm dialog */}
      {toggleTarget && (
        <ToggleConfirmDialog
          flag={toggleTarget}
          onConfirm={(reason) => toggle({ flag: toggleTarget, reason })}
          onCancel={() => setToggleTarget(null)}
          isBusy={isPending}
        />
      )}

      {/* Audit drawer */}
      {auditTarget && (
        <AuditDrawer
          tenantId={tenantId}
          flagKey={auditTarget.key}
          onClose={() => setAuditTarget(null)}
        />
      )}

      <Box className="mx-auto max-w-4xl px-4 py-6 space-y-4">
        {/* Header */}
        <Box className="flex items-start justify-between gap-4">
          <Box>
            <Typography className="text-xl font-bold">Feature Flags</Typography>
            <Typography className="text-sm text-text-secondary">
              Tenant-scoped flags — {enabledCount} of {flags.length} enabled
            </Typography>
          </Box>
          <button
            type="button"
            onClick={() => void refetch()}
            className="flex items-center gap-1 rounded border border-divider px-3 py-1.5 text-sm hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
            aria-label="Refresh"
          >
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            Refresh
          </button>
        </Box>

        {/* Search */}
        <input
          type="search"
          placeholder="Filter flags…"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full rounded border border-divider bg-white dark:bg-grey-800 px-3 py-2 text-sm text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary"
          aria-label="Filter feature flags"
        />

        {/* Loading state */}
        {isLoading && (
          <Box className="flex items-center gap-2 text-text-secondary py-8 justify-center">
            <Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />
            <Typography>Loading flags…</Typography>
          </Box>
        )}

        {/* Error state */}
        {error instanceof Error && (
          <Box className="flex items-center gap-2 text-destructive dark:text-destructive py-4" role="alert">
            <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
            <Typography className="text-sm">{error.message}</Typography>
          </Box>
        )}

        {/* Flags list */}
        {!isLoading && !error && (
          <Card>
            <CardContent className="p-4 space-y-2">
              {filteredFlags.length === 0 && (
                <Typography className="text-sm text-text-secondary text-center py-4">
                  {searchQuery ? 'No matching flags.' : 'No feature flags configured.'}
                </Typography>
              )}
              {(expanded ? filteredFlags : filteredFlags.slice(0, 8)).map((flag) => (
                <FlagRow
                  key={flag.id}
                  flag={flag}
                  tenantId={tenantId}
                  onToggle={setToggleTarget}
                  onShowAudit={setAuditTarget}
                />
              ))}
              {filteredFlags.length > 8 && (
                <button
                  type="button"
                  onClick={() => setExpanded((prev) => !prev)}
                  className="flex w-full items-center justify-center gap-1 rounded py-2 text-xs text-text-secondary hover:text-primary hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
                >
                  {expanded ? (
                    <>
                      <ChevronUp className="h-3.5 w-3.5" aria-hidden="true" />
                      Show fewer
                    </>
                  ) : (
                    <>
                      <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
                      Show all {filteredFlags.length} flags
                    </>
                  )}
                </button>
              )}
            </CardContent>
          </Card>
        )}
      </Box>
    </Box>
  );
};

export default FeatureFlagsPage;
