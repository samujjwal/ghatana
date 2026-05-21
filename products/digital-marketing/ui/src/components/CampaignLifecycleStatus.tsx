/**
 * Campaign Lifecycle Status Component
 *
 * <p>Displays the current campaign status and available lifecycle transitions
 * based on the campaign state machine.</p>
 *
 * @doc.type component
 * @doc.purpose Campaign lifecycle status display with transition actions
 * @doc.layer frontend
 */
import React from 'react';
import type { CampaignStatus } from '@/types/campaign';
import {
  StatusBadge,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
} from '@ghatana/design-system';

interface CampaignLifecycleStatusProps {
  status: CampaignStatus;
  campaignId: string;
  campaignName: string;
  onTransition: (campaignId: string, toStatus: string, actor: string, reason: string) => void;
  canTransition: boolean;
}

const CAMPAIGN_STATUS_MAPPINGS: Record<CampaignStatus, { variant: 'default' | 'secondary' | 'destructive' | 'success'; label: string }> = {
  DRAFT: { variant: 'default', label: 'Draft' },
  PENDING_APPROVAL: { variant: 'default', label: 'Pending Approval' },
  APPROVED: { variant: 'secondary', label: 'Approved' },
  PENDING_LAUNCH: { variant: 'secondary', label: 'Pending Launch' },
  LAUNCH_RUNNING: { variant: 'secondary', label: 'Launch Running' },
  LAUNCH_FAILED: { variant: 'destructive', label: 'Launch Failed' },
  EXTERNAL_EXECUTION_BLOCKED: { variant: 'destructive', label: 'Execution Blocked' },
  LAUNCHED: { variant: 'success', label: 'Launched' },
  PAUSED: { variant: 'default', label: 'Paused' },
  COMPLETED: { variant: 'success', label: 'Completed' },
  ARCHIVED: { variant: 'default', label: 'Archived' },
  ROLLED_BACK: { variant: 'destructive', label: 'Rolled Back' },
};

const AVAILABLE_TRANSITIONS: Record<CampaignStatus, Array<{ status: string; label: string; requiresReason?: boolean }>> = {
  DRAFT: [{ status: 'PENDING_APPROVAL', label: 'Request Approval' }],
  PENDING_APPROVAL: [{ status: 'APPROVED', label: 'Approve', requiresReason: true }],
  APPROVED: [{ status: 'PENDING_LAUNCH', label: 'Launch' }],
  PENDING_LAUNCH: [
    { status: 'LAUNCH_RUNNING', label: 'Start Launch' },
    { status: 'EXTERNAL_EXECUTION_BLOCKED', label: 'Block Execution' },
    { status: 'LAUNCH_FAILED', label: 'Fail Launch' },
  ],
  LAUNCH_RUNNING: [{ status: 'LAUNCHED', label: 'Complete Launch' }],
  LAUNCH_FAILED: [{ status: 'ROLLED_BACK', label: 'Rollback', requiresReason: true }],
  EXTERNAL_EXECUTION_BLOCKED: [],
  LAUNCHED: [
    { status: 'PAUSED', label: 'Pause' },
    { status: 'COMPLETED', label: 'Complete' },
  ],
  PAUSED: [{ status: 'COMPLETED', label: 'Complete' }],
  COMPLETED: [{ status: 'ARCHIVED', label: 'Archive' }],
  ARCHIVED: [],
  ROLLED_BACK: [],
};

export function CampaignLifecycleStatus({
  status,
  campaignId,
  campaignName,
  onTransition,
  canTransition,
}: CampaignLifecycleStatusProps): React.ReactElement {
  const [transitionDialog, setTransitionDialog] = React.useState<{
    targetStatus: string;
    label: string;
    requiresReason: boolean;
  } | null>(null);
  const [reason, setReason] = React.useState('');

  const availableTransitions = AVAILABLE_TRANSITIONS[status] || [];

  const handleTransitionClick = (targetStatus: string, label: string, requiresReason = false) => {
    if (requiresReason) {
      setTransitionDialog({ targetStatus, label, requiresReason });
    } else {
      onTransition(campaignId, targetStatus, 'system', `Transitioning to ${label}`);
    }
  };

  const handleConfirmTransition = () => {
    if (transitionDialog) {
      onTransition(campaignId, transitionDialog.targetStatus, 'system', reason || transitionDialog.label);
      setTransitionDialog(null);
      setReason('');
    }
  };

  const handleCancelTransition = () => {
    setTransitionDialog(null);
    setReason('');
  };

  return (
    <div className="flex items-center gap-2">
      <StatusBadge status={status} statusMappings={CAMPAIGN_STATUS_MAPPINGS} />
      
      {canTransition && availableTransitions.length > 0 && (
        <div className="flex items-center gap-1">
          {availableTransitions.map((transition) => (
            <Button
              key={transition.status}
              variant="outline"
              size="sm"
              onClick={() => handleTransitionClick(transition.status, transition.label, transition.requiresReason)}
            >
              {transition.label}
            </Button>
          ))}
        </div>
      )}

      {transitionDialog && transitionDialog.requiresReason && (
        <Dialog open={!!transitionDialog} onClose={handleCancelTransition}>
          <DialogTitle>
            {transitionDialog.label} Campaign
          </DialogTitle>
          <DialogContent>
            <p className="mb-4">
              Please provide a reason for {transitionDialog.label.toLowerCase()} the campaign "{campaignName}".
            </p>
            <TextField
              label="Reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              multiline
              rows={3}
              fullWidth
            />
          </DialogContent>
          <DialogActions>
            <Button variant="outline" onClick={handleCancelTransition}>
              Cancel
            </Button>
            <Button onClick={handleConfirmTransition} disabled={!reason.trim()}>
              Confirm
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </div>
  );
}
