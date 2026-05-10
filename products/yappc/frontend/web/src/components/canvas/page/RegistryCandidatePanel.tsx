/**
 * @doc.type component
 * @doc.purpose Panel for reviewing and promoting registry candidates from residual islands
 * @doc.layer product
 * @doc.pattern Widget
 */

import { CheckCircle, XCircle, Clock, Trash2 } from 'lucide-react';
import { Box, Stack, Typography, IconButton, Button, Surface as Paper } from '@ghatana/design-system';
import React, { useCallback } from 'react';
import { useI18n } from '../../../i18n/I18nProvider';

import type { RegistryCandidatePromotionResponse } from '../../../services/canvas/commands/RegistryCandidatePromotionService';

interface RegistryCandidateSummary {
  readonly candidateId: string;
  readonly artifactId: string;
  readonly residualIslandId: string;
  readonly proposedContractName: string;
  readonly status: RegistryCandidatePromotionResponse['status'];
  readonly auditRecordId: string;
  readonly createdAt: string;
}

interface RegistryCandidatePanelProps {
  readonly candidates: readonly RegistryCandidateSummary[];
  readonly canEdit: boolean;
  readonly onPromote: (candidateId: string) => Promise<void>;
  readonly onRemove?: (candidateId: string) => void;
  readonly onClose?: () => void;
}

export const RegistryCandidatePanel: React.FC<RegistryCandidatePanelProps> = ({
  candidates,
  canEdit,
  onPromote,
  onRemove,
  onClose,
}) => {
  const { t } = useI18n();

  const handlePromote = useCallback(
    async (candidateId: string) => {
      await onPromote(candidateId);
    },
    [onPromote]
  );

  const handleRemove = useCallback(
    (candidateId: string) => {
      onRemove?.(candidateId);
    },
    [onRemove]
  );

  if (candidates.length === 0) {
    return null;
  }

  return (
    <Paper elevation={2} sx={{ p: 3, mb: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Stack direction="row" alignItems="center" spacing={2}>
          <Typography variant="h6">Registry candidates: {candidates.length} awaiting review</Typography>
        </Stack>
        {onClose && (
          <IconButton onClick={onClose} aria-label="Close registry candidates panel">
            ✕
          </IconButton>
        )}
      </Stack>

      {candidates.map((candidate) => (
        <Paper key={candidate.candidateId} elevation={1} sx={{ p: 2, mb: 1 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Stack spacing={1}>
              <Stack direction="row" alignItems="center" spacing={1}>
                <Clock size={16} />
                <Typography variant="subtitle2">{candidate.proposedContractName}</Typography>
                <Typography variant="caption" color="text.secondary">
                  · {candidate.residualIslandId}
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Artifact: {candidate.artifactId} · Created: {new Date(candidate.createdAt).toLocaleString()}
              </Typography>
            </Stack>

            <Stack direction="row" spacing={1}>
              {canEdit && (
                <Button
                  size="small"
                  variant="contained"
                  onClick={() => handlePromote(candidate.candidateId)}
                >
                  Promote
                </Button>
              )}
              {onRemove && (
                <IconButton
                  size="small"
                  onClick={() => handleRemove(candidate.candidateId)}
                  aria-label={`Remove ${candidate.proposedContractName}`}
                >
                  <Trash2 size={16} />
                </IconButton>
              )}
            </Stack>
          </Stack>
        </Paper>
      ))}

      <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
        Review and promote residual islands to the component registry before handoff.
      </Typography>
    </Paper>
  );
};
