/**
 * @fileoverview Residual Island Review Panel for Page Designer
 *
 * Provides a panel for reviewing and managing residual islands that result from
 * artifact imports. Users can accept, reject, or promote residual islands to registry candidates.
 *
 * @doc.type component
 * @doc.purpose Residual island review and management
 * @doc.layer product
 * @doc.pattern Review Panel
 */

import React, { useState } from 'react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Button,
  Surface as Paper,
  TextArea,
  Chip,
  Drawer,
} from '@ghatana/design-system';
import { Check, X, AlertTriangle, ChevronRight } from 'lucide-react';
import { useI18n } from '../../../i18n/I18nProvider';

export interface ResidualReviewPanelProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly residualId: string;
  readonly sourceEvidence: string;
  readonly governedEvidence: string;
  readonly onAccept: (residualId: string, notes?: string) => Promise<void>;
  readonly onReject: (residualId: string, reason: string) => Promise<void>;
  readonly onPromote: (residualId: string, contractName: string) => Promise<void>;
  readonly isReviewing?: boolean;
  readonly isPromoting?: boolean;
}

export const ResidualReviewPanel: React.FC<ResidualReviewPanelProps> = ({
  open,
  onClose,
  residualId,
  sourceEvidence,
  governedEvidence,
  onAccept,
  onReject,
  onPromote,
  isReviewing = false,
  isPromoting = false,
}) => {
  const { t } = useI18n();
  const [notes, setNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [proposedContractName, setProposedContractName] = useState('');
  const [action, setAction] = useState<'accept' | 'reject' | 'promote' | null>(null);

  const handleAccept = async () => {
    if (!action) return;
    try {
      await onAccept(residualId, notes || undefined);
      setAction(null);
      setNotes('');
      onClose();
    } catch (err) {
      console.error('Failed to accept residual:', err);
    }
  };

  const handleReject = async () => {
    if (!action || !rejectionReason.trim()) {
      return;
    }
    try {
      await onReject(residualId, rejectionReason);
      setAction(null);
      setRejectionReason('');
      onClose();
    } catch (err) {
      console.error('Failed to reject residual:', err);
    }
  };

  const handlePromote = async () => {
    if (!action || !proposedContractName.trim()) {
      return;
    }
    try {
      await onPromote(residualId, proposedContractName);
      setAction(null);
      setProposedContractName('');
      onClose();
    } catch (err) {
      console.error('Failed to promote residual:', err);
    }
  };

  const handleCancel = () => {
    setAction(null);
    setNotes('');
    setRejectionReason('');
    setProposedContractName('');
  };

  if (!open) {
    return null;
  }

  return (
    <Drawer open={open} onClose={onClose} anchor="right">
      <Paper padding={4} elevation={2} style={{ minWidth: 600, maxWidth: 600 }}>
        <Stack spacing={4}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">Residual Island Review</Typography>
            <IconButton onClick={onClose} aria-label="Close review panel">
              <X />
            </IconButton>
          </Stack>

          <Box padding={3} style={{ backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '4px' }}>
            <Stack direction="row" spacing={2} alignItems="flex-start">
              <AlertTriangle size={20} style={{ flexShrink: 0, marginTop: 2 }} />
              <Typography variant="body2" color="text.secondary">
                This residual island was not matched to any reviewed registry contract. Review and decide how to handle it.
              </Typography>
            </Stack>
          </Box>

          <Stack spacing={2}>
            <Typography variant="subtitle2">Residual ID</Typography>
            <Typography variant="body1" style={{ fontFamily: 'monospace' }}>
              {residualId}
            </Typography>
          </Stack>

          <Stack spacing={2}>
            <Typography variant="subtitle2">Source Evidence</Typography>
            <Typography variant="body2" color="text.secondary">
              {sourceEvidence}
            </Typography>
          </Stack>

          <Stack spacing={2}>
            <Typography variant="subtitle2">Governed Evidence</Typography>
            <Typography variant="body2" color="text.secondary">
              {governedEvidence}
            </Typography>
          </Stack>

          {!action && (
            <Stack spacing={2}>
              <Typography variant="subtitle2">Review Action</Typography>
              <Stack direction="row" spacing={2}>
                <Button
                  variant="outlined"
                  startIcon={<Check />}
                  onClick={() => setAction('accept')}
                  disabled={isReviewing}
                >
                  Accept
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<X />}
                  onClick={() => setAction('reject')}
                  disabled={isReviewing}
                >
                  Reject
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<ChevronRight />}
                  onClick={() => setAction('promote')}
                  disabled={isReviewing}
                >
                  Promote to Registry
                </Button>
              </Stack>
            </Stack>
          )}

          {action === 'accept' && (
            <Stack spacing={3}>
              <Typography variant="subtitle2">Accept Residual</Typography>
              <Typography variant="body2" color="text.secondary">
                Accept this residual island into the builder document. It will be preserved as-is.
              </Typography>
              <Stack spacing={2}>
                <Typography variant="subtitle2">Review Notes (Optional)</Typography>
                <TextArea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Add any notes about this acceptance..."
                  rows={3}
                />
              </Stack>
              <Stack direction="row" spacing={2}>
                <Button variant="contained" onClick={handleAccept} disabled={isReviewing}>
                  {isReviewing ? 'Accepting...' : 'Confirm Accept'}
                </Button>
                <Button variant="outlined" onClick={handleCancel} disabled={isReviewing}>
                  Cancel
                </Button>
              </Stack>
            </Stack>
          )}

          {action === 'reject' && (
            <Stack spacing={3}>
              <Typography variant="subtitle2">Reject Residual</Typography>
              <Typography variant="body2" color="text.secondary">
                Reject this residual island. It will not be included in the builder document.
              </Typography>
              <Stack spacing={2}>
                <Typography variant="subtitle2">Rejection Reason (Required)</Typography>
                <TextArea
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  placeholder="Explain why this residual is being rejected..."
                  rows={3}
                />
              </Stack>
              <Stack direction="row" spacing={2}>
                <Button
                  variant="contained"
                  onClick={handleReject}
                  disabled={isReviewing || !rejectionReason.trim()}
                >
                  {isReviewing ? 'Rejecting...' : 'Confirm Reject'}
                </Button>
                <Button variant="outlined" onClick={handleCancel} disabled={isReviewing}>
                  Cancel
                </Button>
              </Stack>
            </Stack>
          )}

          {action === 'promote' && (
            <Stack spacing={3}>
              <Typography variant="subtitle2">Promote to Registry Candidate</Typography>
              <Typography variant="body2" color="text.secondary">
                Promote this residual island as a registry candidate for future review and potential inclusion in the component registry.
              </Typography>
              <Stack spacing={2}>
                <Typography variant="subtitle2">Proposed Contract Name</Typography>
                <Typography variant="body2" color="text.secondary">
                  This will be the name of the registry candidate contract.
                </Typography>
                <Chip label={proposedContractName || 'Auto-generated from residual ID'} />
                <TextArea
                  value={proposedContractName}
                  onChange={(e) => setProposedContractName(e.target.value)}
                  placeholder="Enter a custom contract name or leave blank for auto-generation..."
                  rows={2}
                />
              </Stack>
              <Stack direction="row" spacing={2}>
                <Button
                  variant="contained"
                  onClick={handlePromote}
                  disabled={isPromoting}
                >
                  {isPromoting ? 'Promoting...' : 'Confirm Promote'}
                </Button>
                <Button variant="outlined" onClick={handleCancel} disabled={isPromoting}>
                  Cancel
                </Button>
              </Stack>
            </Stack>
          )}
        </Stack>
      </Paper>
    </Drawer>
  );
};
