/**
 * @doc.type component
 * @doc.purpose Panel for reviewing artifact graph merge conflicts before handoff
 * @doc.layer product
 * @doc.pattern Widget
 */

import { AlertTriangle, RefreshCw, CheckCircle, XCircle } from 'lucide-react';
import { Box, Stack, Typography, IconButton, Button, Surface as Paper } from '@ghatana/design-system';
import React, { useCallback } from 'react';
import { useTranslation } from '@ghatana/i18n';

import type { PageArtifactGraphSnapshot } from './pageArtifactDocument';
import type { ArtifactGraphMergeReviewResult } from '../../../services/canvas/commands/ArtifactGraphMergeReviewService';

type ArtifactGraphMergeReviewStatus = 'required' | 'running' | 'passed' | 'conflicts' | 'failed';

interface ArtifactGraphMergeReviewState {
  readonly artifactId: string;
  readonly graph: PageArtifactGraphSnapshot;
  readonly status: ArtifactGraphMergeReviewStatus;
  readonly attemptedAt?: string;
  readonly result?: ArtifactGraphMergeReviewResult;
  readonly error?: string;
}

interface GraphMergeReviewPanelProps {
  readonly reviewState: ArtifactGraphMergeReviewState | null;
  readonly canEdit: boolean;
  readonly onRunReview: () => Promise<void>;
  readonly onClose?: () => void;
}

export const GraphMergeReviewPanel: React.FC<GraphMergeReviewPanelProps> = ({
  reviewState,
  canEdit,
  onRunReview,
  onClose,
}) => {
  const { t } = useTranslation('common');

  const handleRunReview = useCallback(async () => {
    await onRunReview();
  }, [onRunReview]);

  if (!reviewState) {
    return null;
  }

  const getStatusIcon = () => {
    switch (reviewState.status) {
      case 'running':
        return <RefreshCw size={16} className="animate-spin" />;
      case 'passed':
        return <CheckCircle size={16} />;
      case 'conflicts':
      case 'failed':
        return <XCircle size={16} />;
      default:
        return <AlertTriangle size={16} />;
    }
  };

  const getStatusColor = () => {
    switch (reviewState.status) {
      case 'running':
        return 'info';
      case 'passed':
        return 'success';
      case 'conflicts':
      case 'failed':
        return 'error';
      default:
        return 'warning';
    }
  };

  return (
    <Paper elevation={2} sx={{ p: 3, mb: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Stack direction="row" alignItems="center" spacing={2}>
          {getStatusIcon()}
          <Typography variant="h6">
            Artifact graph merge review: {reviewState.status}
          </Typography>
        </Stack>
        {onClose && (
          <IconButton onClick={onClose} aria-label="Close graph merge review panel">
            ✕
          </IconButton>
        )}
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Graph {reviewState.graph.graphId} includes {reviewState.graph.nodes.length} nodes,{' '}
        {reviewState.graph.edges.length} edges, and{' '}
        {reviewState.graph.provenance.residualIslandIds.length} residual island references. Run merge
        review before trusting graph-wide handoff.
      </Typography>

      {reviewState.result ? (
        <Box sx={{ mb: 2 }}>
          <Typography
            variant="body2"
            color={reviewState.result.conflictCount > 0 ? 'warning' : 'success'}
            sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
          >
            Merge result: {reviewState.result.conflictCount} conflict
            {reviewState.result.conflictCount === 1 ? '' : 's'} · {reviewState.result.message}
          </Typography>
        </Box>
      ) : null}

      {reviewState.error ? (
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" color="error">
            Review failed: {reviewState.error}
          </Typography>
        </Box>
      ) : null}

      <Button
        variant="contained"
        disabled={!canEdit || reviewState.status === 'running'}
        onClick={handleRunReview}
        startIcon={<RefreshCw size={16} className={reviewState.status === 'running' ? 'animate-spin' : ''} />}
      >
        {reviewState.status === 'running' ? 'Running review...' : 'Run merge review'}
      </Button>

      {(reviewState.status === 'failed' || reviewState.status === 'conflicts') && (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
          Review must pass before graph handoff can proceed.
        </Typography>
      )}
    </Paper>
  );
};
