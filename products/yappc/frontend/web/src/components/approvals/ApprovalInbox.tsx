/**
 * Approval Inbox
 *
 * @doc.type component
 * @doc.purpose Review and decide pending AI-generated requirement approvals
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Typography,
} from '@ghatana/design-system';
import { CheckCircle, XCircle, MessageSquareWarning } from 'lucide-react';

export type ApprovalDecisionStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'EXPIRED';

export interface ApprovalRecord {
  id: string;
  projectId: string;
  requirementId?: string;
  requestedAction: string;
  status: ApprovalDecisionStatus;
  requesterId: string;
  reviewerId?: string;
  createdAt: string;
  reviewedAt?: string;
  decisionReason?: string;
}

export interface ApprovalInboxProps {
  approvals: ApprovalRecord[];
  selectedApprovalId?: string;
  onSelectApproval?: (approvalId: string) => void;
  onApprove?: (approvalId: string) => void;
  onReject?: (approvalId: string) => void;
  onRequestChanges?: (approvalId: string) => void;
  className?: string;
}

const STATUS_STYLES: Record<ApprovalDecisionStatus, string> = {
  PENDING: 'bg-warning-bg text-warning-color',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-destructive-bg text-destructive',
  CHANGES_REQUESTED: 'bg-info-bg text-info-color',
  EXPIRED: 'bg-surface-muted text-fg',
};

const formatTime = (isoTimestamp: string): string => {
  const value = new Date(isoTimestamp);
  if (Number.isNaN(value.getTime())) {
    return isoTimestamp;
  }
  return value.toLocaleString();
};

export const ApprovalInbox: React.FC<ApprovalInboxProps> = ({
  approvals,
  selectedApprovalId,
  onSelectApproval,
  onApprove,
  onReject,
  onRequestChanges,
  className = '',
}) => {
  return (
    <Box className={`space-y-3 ${className}`}>
      <Box className="flex items-center justify-between">
        <Typography className="text-lg font-semibold">Approval Inbox</Typography>
        <Chip
          label={`${approvals.filter((item) => item.status === 'PENDING').length} pending`}
          size="sm"
        />
      </Box>

      {approvals.length === 0 && (
        <Card>
          <CardContent className="p-4">
            <Typography className="text-sm text-fg-muted">
              No approval requests yet.
            </Typography>
          </CardContent>
        </Card>
      )}

      {approvals.map((approval) => {
        const isSelected = approval.id === selectedApprovalId;
        const isPending = approval.status === 'PENDING';

        return (
          <Card
            key={approval.id}
            className={isSelected ? 'border-2 border-info-border' : ''}
          >
            <CardContent className="space-y-3 p-4">
              <Box className="flex items-center justify-between gap-2">
                <Typography className="font-medium">{approval.requestedAction}</Typography>
                <Chip
                  label={approval.status}
                  size="sm"
                  className={STATUS_STYLES[approval.status]}
                />
              </Box>

              <Typography className="text-xs text-fg-muted">
                Requester: {approval.requesterId}
              </Typography>
              <Typography className="text-xs text-fg-muted">
                Created: {formatTime(approval.createdAt)}
              </Typography>
              {approval.requirementId && (
                <Typography className="text-xs text-fg-muted">
                  Requirement: {approval.requirementId}
                </Typography>
              )}
              {approval.decisionReason && (
                <Typography className="text-sm text-fg">
                  Reason: {approval.decisionReason}
                </Typography>
              )}

              <Box className="flex flex-wrap gap-2">
                {onSelectApproval && (
                  <Button
                    size="sm"
                    variant="outlined"
                    onClick={() => onSelectApproval(approval.id)}
                  >
                    Open Details
                  </Button>
                )}

                {isPending && onApprove && (
                  <Button
                    size="sm"
                    variant="contained"
                    onClick={() => onApprove(approval.id)}
                  >
                    <CheckCircle className="mr-1 h-4 w-4" />
                    Approve
                  </Button>
                )}

                {isPending && onReject && (
                  <Button
                    size="sm"
                    variant="outlined"
                    color="error"
                    onClick={() => onReject(approval.id)}
                  >
                    <XCircle className="mr-1 h-4 w-4" />
                    Reject
                  </Button>
                )}

                {isPending && onRequestChanges && (
                  <Button
                    size="sm"
                    variant="text"
                    onClick={() => onRequestChanges(approval.id)}
                  >
                    <MessageSquareWarning className="mr-1 h-4 w-4" />
                    Request changes
                  </Button>
                )}
              </Box>
            </CardContent>
          </Card>
        );
      })}
    </Box>
  );
};

export default ApprovalInbox;
