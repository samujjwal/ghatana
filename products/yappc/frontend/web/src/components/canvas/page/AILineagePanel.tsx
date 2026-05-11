/**
 * @doc.type component
 * @doc.purpose Panel for tracking and reviewing AI-generated action lineage
 * @doc.layer product
 * @doc.pattern Widget
 */

import { CheckCircle, XCircle, Clock, Undo2, Eye } from 'lucide-react';
import { Box, Stack, Typography, IconButton, Button, Surface as Paper } from '@ghatana/design-system';
import React, { useCallback } from 'react';
import { useTranslation } from '@ghatana/i18n';

import type { AIActionLineage } from './pageArtifactDocument';

interface AILineagePanelProps {
  readonly pendingActions: readonly AIActionLineage[];
  readonly canEdit: boolean;
  readonly onAccept?: (actionId: string) => void;
  readonly onReject?: (actionId: string) => void;
  readonly onReview?: (actionId: string) => void;
  readonly onClose?: () => void;
}

export const AILineagePanel: React.FC<AILineagePanelProps> = ({
  pendingActions,
  canEdit,
  onAccept,
  onReject,
  onReview,
  onClose,
}) => {
  const { t } = useTranslation('common');

  const handleAccept = useCallback(
    (actionId: string) => {
      onAccept?.(actionId);
    },
    [onAccept]
  );

  const handleReject = useCallback(
    (actionId: string) => {
      onReject?.(actionId);
    },
    [onReject]
  );

  const handleReview = useCallback(
    (actionId: string) => {
      onReview?.(actionId);
    },
    [onReview]
  );

  if (pendingActions.length === 0) {
    return null;
  }

  return (
    <Paper elevation={2} sx={{ p: 3, mb: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Stack direction="row" alignItems="center" spacing={2}>
          <Typography variant="h6">AI Actions: {pendingActions.length} pending review</Typography>
        </Stack>
        {onClose && (
          <IconButton onClick={onClose} aria-label="Close AI lineage panel">
            ✕
          </IconButton>
        )}
      </Stack>

      {pendingActions.map((action) => (
        <Paper key={action.actionId} elevation={1} sx={{ p: 2, mb: 1 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Stack spacing={1}>
              <Stack direction="row" alignItems="center" spacing={1}>
                <Clock size={16} />
                <Typography variant="subtitle2">{action.hookKind}</Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {action.reason}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Confidence: {Math.round(action.confidence * 100)}%
              </Typography>
            </Stack>

            <Stack direction="row" spacing={1}>
              {canEdit && (
                <>
                  <Button
                    size="small"
                    variant="contained"
                    onClick={() => handleAccept(action.actionId)}
                    startIcon={<CheckCircle size={14} />}
                  >
                    Accept
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => handleReject(action.actionId)}
                    startIcon={<XCircle size={14} />}
                  >
                    Reject
                  </Button>
                </>
              )}
            </Stack>
          </Stack>
        </Paper>
      ))}

      <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
        Review AI-generated changes before applying them to the document.
      </Typography>
    </Paper>
  );
};
