/**
 * Artifact Version Conflict Resolver
 * 
 * Handles remote artifact version conflicts with user-visible UX for:
 * - Reload: Fetch the latest remote version
 * - Compare: Show diff between local and remote versions
 * - Reapply: Re-apply local changes on top of remote version
 * - Discard: Discard local changes and use remote version
 * - Retry: Retry the sync operation
 * 
 * @doc.type component
 * @doc.purpose Artifact version conflict resolution UI
 * @doc.layer product
 * @doc.pattern Conflict Resolution Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, Paper, Button, IconButton, Divider } from '@ghatana/design-system';
import { AlertTriangle, RefreshCw as Refresh, GitCompare as Compare, Trash2 as Discard, X, ChevronDown as ExpandMore, ChevronUp as ExpandLess } from 'lucide-react';
import { useTranslation } from '@ghatana/i18n';

export interface ArtifactVersionConflict {
  artifactId: string;
  artifactName: string;
  localVersion: string;
  remoteVersion: string;
  localContent: string;
  remoteContent: string;
  correlationId: string;
  timestamp: number;
}

export interface ArtifactVersionConflictResolverProps {
  conflict: ArtifactVersionConflict;
  isResolving?: boolean;
  onReload: (artifactId: string) => Promise<void>;
  onCompare: (artifactId: string) => Promise<void>;
  onReapply: (artifactId: string, localContent: string) => Promise<void>;
  onDiscard: (artifactId: string) => Promise<void>;
  onRetry: (artifactId: string) => Promise<void>;
  onDismiss?: () => void;
  className?: string;
}

export function ArtifactVersionConflictResolver({
  conflict,
  isResolving = false,
  onReload,
  onCompare,
  onReapply,
  onDiscard,
  onRetry,
  onDismiss,
  className = '',
}: ArtifactVersionConflictResolverProps) {
  const { t } = useTranslation('common');
  const [showDetails, setShowDetails] = useState(false);
  const [activeAction, setActiveAction] = useState<string | null>(null);

  const handleReload = useCallback(async () => {
    setActiveAction('reload');
    try {
      await onReload(conflict.artifactId);
    } finally {
      setActiveAction(null);
    }
  }, [conflict.artifactId, onReload]);

  const handleCompare = useCallback(async () => {
    setActiveAction('compare');
    try {
      await onCompare(conflict.artifactId);
    } finally {
      setActiveAction(null);
    }
  }, [conflict.artifactId, onCompare]);

  const handleReapply = useCallback(async () => {
    setActiveAction('reapply');
    try {
      await onReapply(conflict.artifactId, conflict.localContent);
    } finally {
      setActiveAction(null);
    }
  }, [conflict.artifactId, conflict.localContent, onReapply]);

  const handleDiscard = useCallback(async () => {
    setActiveAction('discard');
    try {
      await onDiscard(conflict.artifactId);
    } finally {
      setActiveAction(null);
    }
  }, [conflict.artifactId, onDiscard]);

  const handleRetry = useCallback(async () => {
    setActiveAction('retry');
    try {
      await onRetry(conflict.artifactId);
    } finally {
      setActiveAction(null);
    }
  }, [conflict.artifactId, onRetry]);

  const formatTimestamp = (timestamp: number): string => {
    return new Date(timestamp).toLocaleString();
  };

  const isActionLoading = (action: string): boolean => {
    return activeAction === action && isResolving;
  };

  return (
    <Paper
      elevation={2}
      className={`flex flex-col gap-3 p-4 border border-warning-border bg-warning-bg ${className}`}
    >
      {/* Header */}
      <Box className="flex items-start justify-between gap-3">
        <Box className="flex items-start gap-2 flex-1">
          <AlertTriangle size={20} className="text-warning mt-0.5 flex-shrink-0" />
          <Box className="flex-1 min-w-0">
            <Typography variant="body2" style={{ fontWeight: 600 }}>
              Artifact version conflict
            </Typography>
            <Typography variant="caption" className="block mt-1 text-warning">
              {conflict.artifactName}
            </Typography>
            <Typography variant="caption" className="block mt-0.5 text-muted">
              Local: {conflict.localVersion} → Remote: {conflict.remoteVersion}
            </Typography>
            <Typography variant="caption" className="block text-muted">
              Correlation ID: {conflict.correlationId}
            </Typography>
            <Typography variant="caption" className="block text-muted">
              Last updated: {formatTimestamp(conflict.timestamp)}
            </Typography>
          </Box>
        </Box>
        {onDismiss && (
          <IconButton
            size="small"
            onClick={onDismiss}
            aria-label="Dismiss conflict"
            disabled={isResolving}
          >
            <X size={16} />
          </IconButton>
        )}
      </Box>

      {/* Expandable Details */}
      <Box>
        <Button
          variant="ghost"
          size="small"
          onClick={() => setShowDetails(!showDetails)}
          className="flex items-center gap-1 px-2 py-1"
          disabled={isResolving}
        >
          {showDetails ? <ExpandLess size={14} /> : <ExpandMore size={14} />}
          <Typography variant="caption">
            {showDetails ? 'Hide details' : 'Show details'}
          </Typography>
        </Button>

        {showDetails && (
          <Box className="mt-3 grid gap-3 sm:grid-cols-2">
            {/* Local Version */}
            <Box className="rounded border border-border bg-surface p-3">
              <Typography variant="caption" style={{ fontWeight: 600 }}>
                Local version ({conflict.localVersion})
              </Typography>
              <Typography variant="caption" className="block mt-2 text-muted break-all">
                {conflict.localContent.substring(0, 200)}
                {conflict.localContent.length > 200 ? '...' : ''}
              </Typography>
            </Box>

            {/* Remote Version */}
            <Box className="rounded border border-border bg-surface p-3">
              <Typography variant="caption" style={{ fontWeight: 600 }}>
                Remote version ({conflict.remoteVersion})
              </Typography>
              <Typography variant="caption" className="block mt-2 text-muted break-all">
                {conflict.remoteContent.substring(0, 200)}
                {conflict.remoteContent.length > 200 ? '...' : ''}
              </Typography>
            </Box>
          </Box>
        )}
      </Box>

      <Divider />

      {/* Action Buttons */}
      <Box className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          size="small"
          onClick={handleReload}
          disabled={isResolving}
          startIcon={<Refresh size={14} />}
          loading={isActionLoading('reload')}
        >
          Reload
        </Button>

        <Button
          variant="outline"
          size="small"
          onClick={handleCompare}
          disabled={isResolving}
          startIcon={<Compare size={14} />}
          loading={isActionLoading('compare')}
        >
          Compare
        </Button>

        <Button
          variant="outline"
          size="small"
          onClick={handleReapply}
          disabled={isResolving}
          loading={isActionLoading('reapply')}
        >
          Reapply local
        </Button>

        <Button
          variant="outline"
          size="small"
          onClick={handleDiscard}
          disabled={isResolving}
          startIcon={<Discard size={14} />}
          loading={isActionLoading('discard')}
          className="border-danger-border text-danger hover:bg-danger-bg"
        >
          Discard local
        </Button>

        <Button
          variant="outline"
          size="small"
          onClick={handleRetry}
          disabled={isResolving}
          loading={isActionLoading('retry')}
        >
          Retry sync
        </Button>
      </Box>

      {/* Action Descriptions */}
      <Box className="rounded border border-border bg-surface p-3">
        <Typography variant="caption" style={{ fontWeight: 600 }}>
          Resolution options:
        </Typography>
        <Typography variant="caption" className="block mt-2 text-muted">
          <strong>Reload:</strong> Fetch the latest remote version and replace local changes
        </Typography>
        <Typography variant="caption" className="block mt-1 text-muted">
          <strong>Compare:</strong> View detailed diff between local and remote versions
        </Typography>
        <Typography variant="caption" className="block mt-1 text-muted">
          <strong>Reapply local:</strong> Re-apply your local changes on top of the remote version
        </Typography>
        <Typography variant="caption" className="block mt-1 text-muted">
          <strong>Discard local:</strong> Discard your local changes and use the remote version
        </Typography>
        <Typography variant="caption" className="block mt-1 text-muted">
          <strong>Retry sync:</strong> Retry the sync operation with current local version
        </Typography>
      </Box>
    </Paper>
  );
}

export default ArtifactVersionConflictResolver;
