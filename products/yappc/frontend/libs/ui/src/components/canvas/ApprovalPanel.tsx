/**
 * Approval Panel Component
 *
 * @description Manages approval workflows for canvas elements or project phases.
 * Shows approval status, pending approvers, and allows submitting approvals.
 *
 * @doc.type component
 * @doc.purpose Approval workflow UI
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  CheckCircle2,
  XCircle,
  Clock,
  AlertCircle,
  Shield,
  Users,
  ThumbsUp,
  ThumbsDown,
  ChevronDown,
  Send,
  RefreshCw,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Textarea } from '@ghatana/yappc-ui';
import { Avatar } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Progress } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

// =============================================================================
// Types
// =============================================================================

export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'changes_requested';

export interface Approver {
  id: string;
  name: string;
  avatar?: string;
  role: string;
  required: boolean;
  status: ApprovalStatus;
  comment?: string;
  respondedAt?: string;
}

export interface ApprovalRequirement {
  id: string;
  name: string;
  description?: string;
  minApprovers: number;
  requiredRoles?: string[];
}

export interface Approval {
  id: string;
  itemId: string;
  itemType: string;
  itemLabel?: string;
  status: ApprovalStatus;
  requirement: ApprovalRequirement;
  approvers: Approver[];
  requestedBy: {
    id: string;
    name: string;
    avatar?: string;
  };
  requestedAt: string;
  deadline?: string;
  currentUserCanApprove: boolean;
}

export interface ApprovalPanelProps {
  /** Approval data */
  approval: Approval;
  /** Current user ID */
  currentUserId: string;
  /** Called when user submits approval */
  onApprove: (comment?: string) => Promise<void>;
  /** Called when user rejects */
  onReject: (comment: string) => Promise<void>;
  /** Called when user requests changes */
  onRequestChanges: (comment: string) => Promise<void>;
  /** Called when approval is cancelled */
  onCancel?: () => Promise<void>;
  /** Called when reminder is sent */
  onSendReminder?: (approverId: string) => Promise<void>;
  /** Compact mode */
  compact?: boolean;
  /** Collapsed by default */
  defaultCollapsed?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Animation Variants
// =============================================================================

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.05 },
  },
} as const;

const itemVariants = {
  hidden: { opacity: 0, x: -10 },
  visible: { opacity: 1, x: 0 },
} as const;

// =============================================================================
// Status Badge
// =============================================================================

interface StatusBadgeProps {
  status: ApprovalStatus;
  size?: 'sm' | 'md';
}

const STATUS_CONFIG: Record<ApprovalStatus, {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  color: string;
  bgColor: string;
}> = {
  pending: {
    icon: Clock,
    label: 'Pending',
    color: 'text-amber-600 dark:text-amber-400',
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
  },
  approved: {
    icon: CheckCircle2,
    label: 'Approved',
    color: 'text-success-600 dark:text-success-400',
    bgColor: 'bg-success-100 dark:bg-success-900/30',
  },
  rejected: {
    icon: XCircle,
    label: 'Rejected',
    color: 'text-error-600 dark:text-error-400',
    bgColor: 'bg-error-100 dark:bg-error-900/30',
  },
  changes_requested: {
    icon: AlertCircle,
    label: 'Changes Requested',
    color: 'text-warning-600 dark:text-warning-400',
    bgColor: 'bg-warning-100 dark:bg-warning-900/30',
  },
};

const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'md' }) => {
  const config = STATUS_CONFIG[status];
  const Icon = config.icon;

  return (
    <Badge
      variant="outline"
      className={cn(
        config.color,
        config.bgColor,
        size === 'sm' && 'text-xs py-0'
      )}
    >
      <Icon className={cn('mr-1', size === 'sm' ? 'h-3 w-3' : 'h-4 w-4')} />
      {config.label}
    </Badge>
  );
};

// =============================================================================
// Approver Card
// =============================================================================

interface ApproverCardProps {
  approver: Approver;
  onSendReminder?: () => void;
  canSendReminder: boolean;
}

const ApproverCard: React.FC<ApproverCardProps> = ({
  approver,
  onSendReminder,
  canSendReminder,
}) => {
  const config = STATUS_CONFIG[approver.status];
  const Icon = config.icon;

  return (
    <motion.div
      variants={itemVariants}
      className={cn(
        'flex items-start gap-3 rounded-lg p-3',
        approver.status === 'approved' && 'bg-success-50/50 dark:bg-success-950/20',
        approver.status === 'rejected' && 'bg-error-50/50 dark:bg-error-950/20',
        approver.status === 'changes_requested' && 'bg-warning-50/50 dark:bg-warning-950/20',
        approver.status === 'pending' && 'bg-neutral-50 dark:bg-neutral-800/50'
      )}
    >
      <div className="relative">
        <Avatar size="medium" alt={approver.name} src={approver.avatar} />
        <div
          className={cn(
            'absolute -bottom-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full',
            config.bgColor
          )}
        >
          <Icon className={cn('h-3 w-3', config.color)} />
        </div>
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-medium text-neutral-900 dark:text-neutral-100 truncate">
            {approver.name}
          </span>
          {approver.required && (
            <Badge variant="outline" className="text-xs shrink-0">
              <Shield className="mr-1 h-3 w-3" />
              Required
            </Badge>
          )}
        </div>
        <p className="text-sm text-neutral-500">{approver.role}</p>

        {approver.comment && (
          <div className="mt-2 rounded bg-neutral-100 p-2 dark:bg-neutral-700/50">
            <p className="text-sm text-neutral-700 dark:text-neutral-300">
              "{approver.comment}"
            </p>
          </div>
        )}

        {approver.respondedAt && (
          <p className="mt-1 text-xs text-neutral-400">
            Responded {new Date(approver.respondedAt).toLocaleDateString()}
          </p>
        )}
      </div>

      {approver.status === 'pending' && canSendReminder && onSendReminder && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="sm" onClick={onSendReminder}>
              <RefreshCw className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Send reminder</TooltipContent>
        </Tooltip>
      )}
    </motion.div>
  );
};

// =============================================================================
// Approval Form
// =============================================================================

interface ApprovalFormProps {
  onApprove: (comment?: string) => void;
  onReject: (comment: string) => void;
  onRequestChanges: (comment: string) => void;
  loading: boolean;
}

const ApprovalForm: React.FC<ApprovalFormProps> = ({
  onApprove,
  onReject,
  onRequestChanges,
  loading,
}) => {
  const [action, setAction] = useState<'approve' | 'reject' | 'changes' | null>(null);
  const [comment, setComment] = useState('');

  const handleSubmit = () => {
    switch (action) {
      case 'approve':
        onApprove(comment || undefined);
        break;
      case 'reject':
        if (comment.trim()) onReject(comment);
        break;
      case 'changes':
        if (comment.trim()) onRequestChanges(comment);
        break;
    }
    setComment('');
    setAction(null);
  };

  const requiresComment = action === 'reject' || action === 'changes';
  const isValid = !requiresComment || comment.trim().length > 0;

  return (
    <div className="space-y-4 border-t pt-4 dark:border-neutral-700">
      <p className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
        Your Response
      </p>

      {/* Action buttons */}
      <div className="flex flex-wrap gap-2">
        <Button
          variant={action === 'approve' ? 'solid' : 'outline'}
          colorScheme={action === 'approve' ? 'success' : 'neutral'}
          onClick={() => setAction(action === 'approve' ? null : 'approve')}
          disabled={loading}
        >
          <ThumbsUp className="mr-2 h-4 w-4" />
          Approve
        </Button>
        <Button
          variant={action === 'changes' ? 'solid' : 'outline'}
          colorScheme={action === 'changes' ? 'warning' : 'neutral'}
          onClick={() => setAction(action === 'changes' ? null : 'changes')}
          disabled={loading}
        >
          <AlertCircle className="mr-2 h-4 w-4" />
          Request Changes
        </Button>
        <Button
          variant={action === 'reject' ? 'solid' : 'outline'}
          colorScheme={action === 'reject' ? 'error' : 'neutral'}
          onClick={() => setAction(action === 'reject' ? null : 'reject')}
          disabled={loading}
        >
          <ThumbsDown className="mr-2 h-4 w-4" />
          Reject
        </Button>
      </div>

      {/* Comment input */}
      <AnimatePresence>
        {action && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="space-y-3 overflow-hidden"
          >
            <Textarea
              value={comment}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setComment(e.target.value)}
              placeholder={
                action === 'approve'
                  ? 'Add an optional comment...'
                  : 'Please explain your feedback...'
              }
              className="min-h-[80px]"
              disabled={loading}
            />
            {requiresComment && !comment.trim() && (
              <p className="text-xs text-error-500">
                A comment is required for this action
              </p>
            )}
            <div className="flex justify-end gap-2">
              <Button
                variant="ghost"
                onClick={() => {
                  setAction(null);
                  setComment('');
                }}
                disabled={loading}
              >
                Cancel
              </Button>
              <Button
                variant="solid"
                colorScheme={
                  action === 'approve'
                    ? 'success'
                    : action === 'reject'
                      ? 'error'
                      : 'warning'
                }
                onClick={handleSubmit}
                disabled={!isValid || loading}
              >
                {loading ? (
                  <motion.div
                    animate={{ rotate: 360 }}
                    transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                  >
                    <Clock className="mr-2 h-4 w-4" />
                  </motion.div>
                ) : (
                  <Send className="mr-2 h-4 w-4" />
                )}
                Submit
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const ApprovalPanel: React.FC<ApprovalPanelProps> = ({
  approval,
  currentUserId,
  onApprove,
  onReject,
  onRequestChanges,
  onCancel,
  onSendReminder,
  compact = false,
  defaultCollapsed = false,
  className,
}) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const [loading, setLoading] = useState(false);

  // Calculate progress
  const approvedCount = approval.approvers.filter(
    (a) => a.status === 'approved'
  ).length;
  const requiredCount = approval.requirement.minApprovers;
  const progress = (approvedCount / requiredCount) * 100;

  // Check if current user has responded
  const currentUserApprover = approval.approvers.find(
    (a) => a.id === currentUserId
  );
  const hasResponded = currentUserApprover && currentUserApprover.status !== 'pending';

  // Handlers
  const handleApprove = useCallback(async (comment?: string) => {
    setLoading(true);
    try {
      await onApprove(comment);
    } finally {
      setLoading(false);
    }
  }, [onApprove]);

  const handleReject = useCallback(async (comment: string) => {
    setLoading(true);
    try {
      await onReject(comment);
    } finally {
      setLoading(false);
    }
  }, [onReject]);

  const handleRequestChanges = useCallback(async (comment: string) => {
    setLoading(true);
    try {
      await onRequestChanges(comment);
    } finally {
      setLoading(false);
    }
  }, [onRequestChanges]);

  // Compact mode
  if (compact) {
    return (
      <div
        className={cn(
          'flex items-center gap-3 rounded-lg border p-3',
          'dark:border-neutral-700',
          className
        )}
      >
        <StatusBadge status={approval.status} size="sm" />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium truncate">
            {approval.itemLabel || approval.itemType}
          </p>
          <p className="text-xs text-neutral-500">
            {approvedCount}/{requiredCount} approvals
          </p>
        </div>
        <div className="flex -space-x-2">
          {approval.approvers.slice(0, 3).map((approver) => (
            <Avatar
              key={approver.id}
              size="small"
              alt={approver.name}
              src={approver.avatar}
              className="ring-2 ring-white dark:ring-neutral-900"
            />
          ))}
          {approval.approvers.length > 3 && (
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-neutral-200 text-xs font-medium dark:bg-neutral-700">
              +{approval.approvers.length - 3}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'rounded-lg border bg-white dark:border-neutral-700 dark:bg-neutral-900',
        className
      )}
    >
      {/* Header */}
      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        className="flex w-full items-center justify-between p-4 text-left hover:bg-neutral-50 dark:hover:bg-neutral-800/50"
      >
        <div className="flex items-center gap-3">
          <Shield className="h-5 w-5 text-primary-500" />
          <div>
            <div className="flex items-center gap-2">
              <span className="font-medium text-neutral-900 dark:text-neutral-100">
                Approval Required
              </span>
              <StatusBadge status={approval.status} />
            </div>
            <p className="text-sm text-neutral-500">
              {approval.requirement.name}
            </p>
          </div>
        </div>
        <motion.div
          animate={{ rotate: collapsed ? 0 : 180 }}
          transition={{ duration: 0.2 }}
        >
          <ChevronDown className="h-5 w-5 text-neutral-500" />
        </motion.div>
      </button>

      {/* Content */}
      <AnimatePresence>
        {!collapsed && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="border-t p-4 dark:border-neutral-700">
              {/* Progress */}
              <div className="mb-4">
                <div className="mb-2 flex items-center justify-between text-sm">
                  <span className="text-neutral-600 dark:text-neutral-400">
                    Progress
                  </span>
                  <span className="font-medium">
                    {approvedCount}/{requiredCount} approvals
                  </span>
                </div>
                <Progress value={progress} className="h-2" />
              </div>

              {/* Requirement description */}
              {approval.requirement.description && (
                <p className="mb-4 text-sm text-neutral-600 dark:text-neutral-400">
                  {approval.requirement.description}
                </p>
              )}

              {/* Deadline */}
              {approval.deadline && (
                <div className="mb-4 flex items-center gap-2 text-sm">
                  <Clock className="h-4 w-4 text-neutral-500" />
                  <span className="text-neutral-600 dark:text-neutral-400">
                    Due by {new Date(approval.deadline).toLocaleDateString()}
                  </span>
                </div>
              )}

              {/* Approvers list */}
              <div className="mb-4">
                <div className="mb-2 flex items-center gap-2">
                  <Users className="h-4 w-4 text-neutral-500" />
                  <span className="text-sm font-medium">Approvers</span>
                </div>
                <motion.div
                  variants={containerVariants}
                  initial="hidden"
                  animate="visible"
                  className="space-y-2"
                >
                  {approval.approvers.map((approver) => (
                    <ApproverCard
                      key={approver.id}
                      approver={approver}
                      onSendReminder={
                        onSendReminder
                          ? () => onSendReminder(approver.id)
                          : undefined
                      }
                      canSendReminder={
                        approval.requestedBy.id === currentUserId
                      }
                    />
                  ))}
                </motion.div>
              </div>

              {/* Approval form */}
              {approval.currentUserCanApprove && !hasResponded && (
                <ApprovalForm
                  onApprove={handleApprove}
                  onReject={handleReject}
                  onRequestChanges={handleRequestChanges}
                  loading={loading}
                />
              )}

              {/* Cancel button for requester */}
              {approval.requestedBy.id === currentUserId &&
                approval.status === 'pending' &&
                onCancel && (
                  <div className="mt-4 flex justify-end border-t pt-4 dark:border-neutral-700">
                    <Button
                      variant="ghost"
                      colorScheme="error"
                      onClick={onCancel}
                    >
                      Cancel Request
                    </Button>
                  </div>
                )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default ApprovalPanel;
