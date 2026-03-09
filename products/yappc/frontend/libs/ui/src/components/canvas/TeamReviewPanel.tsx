/**
 * Team Review Panel Component
 *
 * @description Panel for managing team review workflows including inviting
 * reviewers, managing permissions, and tracking review progress.
 *
 * @doc.type component
 * @doc.purpose Team review management
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Users,
  UserPlus,
  Mail,
  Copy,
  Check,
  X,
  Eye,
  Edit3,
  Settings,
  ChevronDown,
  Clock,
  Link2,
  Send,
  AlertCircle,
  CheckCircle2,
  MessageSquare,
  Star,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Avatar } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';
import { Dialog } from '@ghatana/ui';

// =============================================================================
// Types
// =============================================================================

export type ReviewerRole = 'owner' | 'reviewer' | 'viewer';
export type ReviewStatus = 'pending' | 'in_review' | 'approved' | 'changes_requested';

export interface Reviewer {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  role: ReviewerRole;
  status: ReviewStatus;
  invitedAt: string;
  respondedAt?: string;
  comments?: number;
  lastActive?: string;
}

export interface PendingInvite {
  id: string;
  email: string;
  role: ReviewerRole;
  invitedAt: string;
  expiresAt: string;
}

export interface TeamReviewPanelProps {
  /** Item being reviewed */
  itemId: string;
  itemLabel?: string;
  /** Current reviewers */
  reviewers: Reviewer[];
  /** Pending invites */
  pendingInvites: PendingInvite[];
  /** Current user ID */
  currentUserId: string;
  /** Current user's role */
  currentUserRole: ReviewerRole;
  /** Called when reviewer is invited */
  onInvite: (email: string, role: ReviewerRole) => Promise<void>;
  /** Called when invite is cancelled */
  onCancelInvite: (inviteId: string) => Promise<void>;
  /** Called when reviewer is removed */
  onRemoveReviewer: (reviewerId: string) => Promise<void>;
  /** Called when reviewer's role is changed */
  onChangeRole: (reviewerId: string, role: ReviewerRole) => Promise<void>;
  /** Called when share link is generated */
  onGenerateShareLink?: () => Promise<string>;
  /** Called when invite is resent */
  onResendInvite: (inviteId: string) => Promise<void>;
  /** Allow role management */
  allowRoleManagement?: boolean;
  /** Collapsed by default */
  defaultCollapsed?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const ROLE_CONFIG: Record<ReviewerRole, {
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
}> = {
  owner: {
    label: 'Owner',
    description: 'Full access, can manage reviewers',
    icon: Star,
    color: 'text-amber-500',
  },
  reviewer: {
    label: 'Reviewer',
    description: 'Can comment and approve',
    icon: Edit3,
    color: 'text-blue-500',
  },
  viewer: {
    label: 'Viewer',
    description: 'View only access',
    icon: Eye,
    color: 'text-neutral-500',
  },
};

const STATUS_CONFIG: Record<ReviewStatus, {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
}> = {
  pending: {
    label: 'Pending',
    icon: Clock,
    color: 'text-neutral-500',
  },
  in_review: {
    label: 'In Review',
    icon: Eye,
    color: 'text-blue-500',
  },
  approved: {
    label: 'Approved',
    icon: CheckCircle2,
    color: 'text-success-500',
  },
  changes_requested: {
    label: 'Changes Requested',
    icon: AlertCircle,
    color: 'text-warning-500',
  },
};

// =============================================================================
// Animation Variants
// =============================================================================

const listVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.05 },
  },
} as const;

const itemVariants = {
  hidden: { opacity: 0, x: -10 },
  visible: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: 10 },
} as const;

// =============================================================================
// Reviewer Card
// =============================================================================

interface ReviewerCardProps {
  reviewer: Reviewer;
  currentUserId: string;
  currentUserRole: ReviewerRole;
  onRemove: () => void;
  onChangeRole: (role: ReviewerRole) => void;
  allowRoleManagement: boolean;
}

const ReviewerCard: React.FC<ReviewerCardProps> = ({
  reviewer,
  currentUserId,
  currentUserRole,
  onRemove,
  onChangeRole,
  allowRoleManagement,
}) => {
  const roleConfig = ROLE_CONFIG[reviewer.role];
  const statusConfig = STATUS_CONFIG[reviewer.status];
  const RoleIcon = roleConfig.icon;
  const StatusIcon = statusConfig.icon;
  const isCurrentUser = reviewer.id === currentUserId;
  const canManage = allowRoleManagement && currentUserRole === 'owner' && !isCurrentUser;

  return (
    <motion.div
      variants={itemVariants}
      className="flex items-center gap-3 rounded-lg p-3 hover:bg-neutral-50 dark:hover:bg-neutral-800/50"
    >
      <div className="relative">
        <Avatar size="medium" alt={reviewer.name} src={reviewer.avatar} />
        <div
          className={cn(
            'absolute -bottom-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-white dark:bg-neutral-900',
            statusConfig.color
          )}
        >
          <StatusIcon className="h-3 w-3" />
        </div>
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-medium text-neutral-900 dark:text-neutral-100 truncate">
            {reviewer.name}
          </span>
          {isCurrentUser && (
            <Badge variant="outline" className="text-xs">You</Badge>
          )}
        </div>
        <div className="flex items-center gap-2 text-xs text-neutral-500">
          <span className={cn('flex items-center gap-1', roleConfig.color)}>
            <RoleIcon className="h-3 w-3" />
            {roleConfig.label}
          </span>
          {reviewer.comments && reviewer.comments > 0 && (
            <>
              <span>•</span>
              <span className="flex items-center gap-1">
                <MessageSquare className="h-3 w-3" />
                {reviewer.comments}
              </span>
            </>
          )}
        </div>
      </div>

      {canManage && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm">
              <Settings className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <div className="px-2 py-1.5 text-xs font-medium text-neutral-500">
              Change Role
            </div>
            {Object.entries(ROLE_CONFIG)
              .filter(([role]) => role !== 'owner')
              .map(([role, config]) => (
                <DropdownMenuItem
                  key={role}
                  onClick={() => onChangeRole(role as ReviewerRole)}
                >
                  <config.icon className={cn('mr-2 h-4 w-4', config.color)} />
                  {config.label}
                  {reviewer.role === role && (
                    <Check className="ml-auto h-4 w-4 text-primary-500" />
                  )}
                </DropdownMenuItem>
              ))}
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={onRemove}
              className="text-error-600 dark:text-error-400"
            >
              <X className="mr-2 h-4 w-4" />
              Remove
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </motion.div>
  );
};

// =============================================================================
// Pending Invite Card
// =============================================================================

interface PendingInviteCardProps {
  invite: PendingInvite;
  onCancel: () => void;
  onResend: () => void;
}

const PendingInviteCard: React.FC<PendingInviteCardProps> = ({
  invite,
  onCancel,
  onResend,
}) => {
  const roleConfig = ROLE_CONFIG[invite.role];
  const isExpired = new Date(invite.expiresAt) < new Date();

  return (
    <motion.div
      variants={itemVariants}
      className={cn(
        'flex items-center gap-3 rounded-lg border border-dashed p-3',
        isExpired
          ? 'border-error-300 bg-error-50/50 dark:border-error-800 dark:bg-error-950/20'
          : 'border-neutral-300 dark:border-neutral-600'
      )}
    >
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
        <Mail className="h-5 w-5 text-neutral-500" />
      </div>

      <div className="flex-1 min-w-0">
        <span className="font-medium text-neutral-900 dark:text-neutral-100 truncate block">
          {invite.email}
        </span>
        <div className="flex items-center gap-2 text-xs text-neutral-500">
          <span className={roleConfig.color}>{roleConfig.label}</span>
          <span>•</span>
          {isExpired ? (
            <span className="text-error-500">Expired</span>
          ) : (
            <span>Pending</span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="sm" onClick={onResend}>
              <Send className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Resend invite</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="sm" onClick={onCancel}>
              <X className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Cancel invite</TooltipContent>
        </Tooltip>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Invite Dialog
// =============================================================================

interface InviteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onInvite: (email: string, role: ReviewerRole) => Promise<void>;
}

const InviteDialog: React.FC<InviteDialogProps> = ({
  open,
  onOpenChange,
  onInvite,
}) => {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<ReviewerRole>('reviewer');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    if (!email.trim()) {
      setError('Email is required');
      return;
    }
    if (!email.includes('@')) {
      setError('Please enter a valid email');
      return;
    }

    setLoading(true);
    setError('');
    try {
      await onInvite(email, role);
      setEmail('');
      setRole('reviewer');
      onOpenChange(false);
    } catch (err) {
      setError('Failed to send invite. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      header="Invite Reviewer"
      actions={
        <div className="flex gap-2">
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant="solid" onClick={handleSubmit} disabled={loading}>
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
            Send Invite
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-neutral-700 dark:text-neutral-300 mb-1">
            Email Address
          </label>
          <Input
            type="email"
            placeholder="Enter email address"
            value={email}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
              setEmail(e.target.value);
              setError('');
            }}
          />
          {error && (
            <p className="mt-1 text-xs text-error-500">{error}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-neutral-700 dark:text-neutral-300 mb-2">
            Role
          </label>
          <div className="space-y-2">
            {Object.entries(ROLE_CONFIG)
              .filter(([r]) => r !== 'owner')
              .map(([r, config]) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setRole(r as ReviewerRole)}
                  className={cn(
                    'flex w-full items-start gap-3 rounded-lg border p-3 text-left transition-colors',
                    role === r
                      ? 'border-primary-500 bg-primary-50 dark:bg-primary-950/30'
                      : 'border-neutral-200 hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600'
                  )}
                >
                  <config.icon className={cn('mt-0.5 h-5 w-5', config.color)} />
                  <div>
                    <span className="font-medium">{config.label}</span>
                    <p className="text-sm text-neutral-500">{config.description}</p>
                  </div>
                  {role === r && (
                    <Check className="ml-auto h-5 w-5 text-primary-500" />
                  )}
                </button>
              ))}
          </div>
        </div>
      </div>
    </Dialog>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const TeamReviewPanel: React.FC<TeamReviewPanelProps> = ({
  // itemId - used for item identification (passed to callbacks implicitly)
  itemLabel,
  reviewers,
  pendingInvites,
  currentUserId,
  currentUserRole,
  onInvite,
  onCancelInvite,
  onRemoveReviewer,
  onChangeRole,
  onGenerateShareLink,
  onResendInvite,
  allowRoleManagement = true,
  defaultCollapsed = false,
  className,
}) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const [showInviteDialog, setShowInviteDialog] = useState(false);
  const [shareLink, setShareLink] = useState<string | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);
  const [generatingLink, setGeneratingLink] = useState(false);

  const canInvite = currentUserRole === 'owner' || currentUserRole === 'reviewer';

  // Review stats
  const stats = useMemo(() => {
    const approved = reviewers.filter((r) => r.status === 'approved').length;
    const pending = reviewers.filter((r) => r.status === 'pending').length;
    return { approved, pending, total: reviewers.length };
  }, [reviewers]);

  const handleGenerateLink = useCallback(async () => {
    if (!onGenerateShareLink) return;
    setGeneratingLink(true);
    try {
      const link = await onGenerateShareLink();
      setShareLink(link);
    } finally {
      setGeneratingLink(false);
    }
  }, [onGenerateShareLink]);

  const handleCopyLink = useCallback(() => {
    if (!shareLink) return;
    navigator.clipboard.writeText(shareLink);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  }, [shareLink]);

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
          <Users className="h-5 w-5 text-primary-500" />
          <div>
            <div className="flex items-center gap-2">
              <span className="font-medium text-neutral-900 dark:text-neutral-100">
                Team Review
              </span>
              <Badge variant="outline">
                {stats.approved}/{stats.total} approved
              </Badge>
            </div>
            {itemLabel && (
              <p className="text-sm text-neutral-500">{itemLabel}</p>
            )}
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
              {/* Actions */}
              <div className="mb-4 flex items-center gap-2">
                {canInvite && (
                  <Button
                    variant="solid"
                    size="sm"
                    onClick={() => setShowInviteDialog(true)}
                  >
                    <UserPlus className="mr-2 h-4 w-4" />
                    Invite Reviewer
                  </Button>
                )}
                {onGenerateShareLink && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleGenerateLink}
                    disabled={generatingLink}
                  >
                    <Link2 className="mr-2 h-4 w-4" />
                    {generatingLink ? 'Generating...' : 'Share Link'}
                  </Button>
                )}
              </div>

              {/* Share link */}
              <AnimatePresence>
                {shareLink && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    className="mb-4 flex items-center gap-2 rounded-lg bg-neutral-100 p-3 dark:bg-neutral-800"
                  >
                    <Input
                      value={shareLink}
                      readOnly
                      className="flex-1 bg-white dark:bg-neutral-900"
                    />
                    <Button variant="outline" size="sm" onClick={handleCopyLink}>
                      {copiedLink ? (
                        <Check className="h-4 w-4 text-success-500" />
                      ) : (
                        <Copy className="h-4 w-4" />
                      )}
                    </Button>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Reviewers list */}
              <div className="space-y-4">
                {reviewers.length > 0 && (
                  <div>
                    <h4 className="mb-2 text-sm font-medium text-neutral-700 dark:text-neutral-300">
                      Reviewers ({reviewers.length})
                    </h4>
                    <motion.div
                      variants={listVariants}
                      initial="hidden"
                      animate="visible"
                      className="space-y-1"
                    >
                      {reviewers.map((reviewer) => (
                        <ReviewerCard
                          key={reviewer.id}
                          reviewer={reviewer}
                          currentUserId={currentUserId}
                          currentUserRole={currentUserRole}
                          onRemove={() => onRemoveReviewer(reviewer.id)}
                          onChangeRole={(role) => onChangeRole(reviewer.id, role)}
                          allowRoleManagement={allowRoleManagement}
                        />
                      ))}
                    </motion.div>
                  </div>
                )}

                {pendingInvites.length > 0 && (
                  <div>
                    <h4 className="mb-2 text-sm font-medium text-neutral-700 dark:text-neutral-300">
                      Pending Invites ({pendingInvites.length})
                    </h4>
                    <motion.div
                      variants={listVariants}
                      initial="hidden"
                      animate="visible"
                      className="space-y-2"
                    >
                      {pendingInvites.map((invite) => (
                        <PendingInviteCard
                          key={invite.id}
                          invite={invite}
                          onCancel={() => onCancelInvite(invite.id)}
                          onResend={() => onResendInvite(invite.id)}
                        />
                      ))}
                    </motion.div>
                  </div>
                )}

                {reviewers.length === 0 && pendingInvites.length === 0 && (
                  <div className="py-8 text-center text-neutral-500">
                    <Users className="mx-auto h-8 w-8 opacity-50" />
                    <p className="mt-2 text-sm">No reviewers yet</p>
                    <p className="text-xs">Invite team members to review</p>
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Invite Dialog */}
      <InviteDialog
        open={showInviteDialog}
        onOpenChange={setShowInviteDialog}
        onInvite={onInvite}
      />
    </div>
  );
};

export default TeamReviewPanel;
