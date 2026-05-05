/**
 * Pull Request Node Component
 *
 * Canvas node representing a pull request/code review in the development phase.
 * Shows PR details, review status, checks, and linked story.
 *
 * @doc.type component
 * @doc.purpose Pull request visualization in development canvas
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  GitPullRequest,
  GitMerge,
  GitBranch,
  CheckCircle2,
  XCircle,
  Clock,
  AlertCircle,
  MessageSquare,
  FileCode,
  Plus,
  Minus,
  MoreHorizontal,
  ExternalLink,
  User,
  Check,
  X,
  Loader2,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type ReviewStatus = 'pending' | 'changes_requested' | 'approved' | 'merged' | 'closed';
export type ChecksStatus = 'pending' | 'passing' | 'failing' | 'skipped';
export type GitProvider = 'github' | 'gitlab' | 'bitbucket' | 'azure_devops';

export interface PRReviewer {
  readonly id: string;
  readonly name: string;
  readonly avatarUrl?: string;
  readonly status: 'pending' | 'approved' | 'changes_requested' | 'commented';
}

export interface PRChecks {
  readonly total: number;
  readonly passed: number;
  readonly failed: number;
  readonly pending: number;
}

export interface PullRequestNodeData extends Record<string, unknown> {
  readonly label: string;
  readonly number: number;
  readonly provider: GitProvider;
  readonly status: ReviewStatus;
  readonly sourceBranch: string;
  readonly targetBranch: string;
  readonly author: {
    readonly name: string;
    readonly avatarUrl?: string;
  };
  readonly description?: string;
  readonly filesChanged: number;
  readonly additions: number;
  readonly deletions: number;
  readonly reviewers?: readonly PRReviewer[];
  readonly checks?: PRChecks;
  readonly linkedStoryKey?: string;
  readonly linkedStoryTitle?: string;
  readonly commentCount?: number;
  readonly mergeable: boolean;
  readonly url: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly nodeId?: string;
  // Callbacks
  readonly onOpenExternal?: (url: string) => void;
  readonly onOpenStory?: (storyKey: string) => void;
  readonly onApprove?: (nodeId: string) => void;
  readonly onRequestChanges?: (nodeId: string) => void;
  readonly onMerge?: (nodeId: string) => void;
}

type PullRequestCanvasNode = Node<PullRequestNodeData>;

export interface PullRequestNodeProps extends NodeProps<PullRequestCanvasNode> {}

// =============================================================================
// Constants
// =============================================================================

const STATUS_CONFIG: Record<ReviewStatus, { label: string; icon: typeof GitPullRequest; color: string; bgColor: string }> = {
  pending: {
    label: 'Open',
    icon: GitPullRequest,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
  changes_requested: {
    label: 'Changes Requested',
    icon: AlertCircle,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  approved: {
    label: 'Approved',
    icon: CheckCircle2,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
  merged: {
    label: 'Merged',
    icon: GitMerge,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  closed: {
    label: 'Closed',
    icon: XCircle,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg border-destructive-border',
  },
};

const PROVIDER_CONFIG: Record<GitProvider, { label: string; color: string }> = {
  github: { label: 'GitHub', color: 'text-fg' },
  gitlab: { label: 'GitLab', color: 'text-warning-color' },
  bitbucket: { label: 'Bitbucket', color: 'text-info-color' },
  azure_devops: { label: 'Azure DevOps', color: 'text-info-color' },
};

const REVIEWER_STATUS_ICON: Record<PRReviewer['status'], { icon: typeof Check; color: string }> = {
  pending: { icon: Clock, color: 'text-fg-muted' },
  approved: { icon: Check, color: 'text-success-color' },
  changes_requested: { icon: AlertCircle, color: 'text-warning-color' },
  commented: { icon: MessageSquare, color: 'text-info-color' },
};

// =============================================================================
// Sub-Components
// =============================================================================

interface ReviewerListProps {
  reviewers: readonly PRReviewer[];
}

const ReviewerList: React.FC<ReviewerListProps> = memo(({ reviewers }) => (
  <div className="flex items-center gap-1">
    {reviewers.slice(0, 4).map((reviewer) => {
      const statusConfig = REVIEWER_STATUS_ICON[reviewer.status];
      const StatusIcon = statusConfig.icon;
      
      return (
        <div
          key={reviewer.id}
          className="relative"
          title={`${reviewer.name}: ${reviewer.status}`}
        >
          {reviewer.avatarUrl ? (
            <img
              src={reviewer.avatarUrl}
              alt={reviewer.name}
              className="w-6 h-6 rounded-full border-2 border-surface"
            />
          ) : (
            <div className="w-6 h-6 rounded-full bg-muted flex items-center justify-center border-2 border-surface">
              <span className="text-[10px] font-medium text-fg-muted">
                {reviewer.name.charAt(0).toUpperCase()}
              </span>
            </div>
          )}
          <div className={cn(
            'absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-surface flex items-center justify-center'
          )}>
            <StatusIcon className={cn('w-2.5 h-2.5', statusConfig.color)} />
          </div>
        </div>
      );
    })}
    {reviewers.length > 4 && (
      <span className="text-xs text-fg-muted">+{reviewers.length - 4}</span>
    )}
  </div>
));

ReviewerList.displayName = 'ReviewerList';

interface ChecksStatusProps {
  checks: PRChecks;
}

const ChecksStatusDisplay: React.FC<ChecksStatusProps> = memo(({ checks }) => {
  const allPassed = checks.passed === checks.total;
  const hasFailed = checks.failed > 0;
  const hasPending = checks.pending > 0;

  let StatusIcon = CheckCircle2;
  let statusColor = 'text-success-color';
  let statusLabel = 'All checks passed';

  if (hasFailed) {
    StatusIcon = XCircle;
    statusColor = 'text-destructive';
    statusLabel = `${checks.failed} check${checks.failed > 1 ? 's' : ''} failed`;
  } else if (hasPending) {
    StatusIcon = Loader2;
    statusColor = 'text-warning-color';
    statusLabel = `${checks.pending} check${checks.pending > 1 ? 's' : ''} pending`;
  }

  return (
    <div className="flex items-center gap-2">
      <StatusIcon className={cn('w-4 h-4', statusColor, hasPending && 'animate-spin')} />
      <span className="text-xs text-fg-muted">{statusLabel}</span>
      <span className="text-xs text-fg-muted">
        ({checks.passed}/{checks.total})
      </span>
    </div>
  );
});

ChecksStatusDisplay.displayName = 'ChecksStatusDisplay';

// =============================================================================
// Main Component
// =============================================================================

const PullRequestNodeComponent: React.FC<PullRequestNodeProps> = ({
  data,
  selected,
  dragging,
}) => {
  const [showMenu, setShowMenu] = useState(false);

  const statusConfig = STATUS_CONFIG[data.status];
  const providerConfig = PROVIDER_CONFIG[data.provider];
  const StatusIcon = statusConfig.icon;

  const nodeId = data.nodeId || `pr-${data.number}`;

  const handleOpenExternal = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      data.onOpenExternal?.(data.url);
    },
    [data]
  );

  const handleOpenStory = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (data.linkedStoryKey) {
        data.onOpenStory?.(data.linkedStoryKey);
      }
    },
    [data]
  );

  const handleApprove = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onApprove?.(nodeId);
    },
    [data, nodeId]
  );

  const handleRequestChanges = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onRequestChanges?.(nodeId);
    },
    [data, nodeId]
  );

  const handleMerge = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onMerge?.(nodeId);
    },
    [data, nodeId]
  );

  const canMerge = data.status === 'approved' && data.mergeable;

  return (
    <div
      className={cn(
        'pr-node w-80 rounded-xl border shadow-sm transition-all duration-200',
        'bg-surface',
        statusConfig.bgColor,
        selected && 'ring-2 ring-primary ring-offset-2',
        dragging && 'opacity-75 shadow-lg scale-[1.02]'
      )}
      onDoubleClick={handleOpenExternal}
    >
      {/* Handles for connections */}
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />

      {/* Header */}
      <div className="p-4 border-b border-border">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <div className={cn('p-2 rounded-lg', statusConfig.bgColor)}>
              <StatusIcon className={cn('w-5 h-5', statusConfig.color)} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-fg-muted">#{data.number}</span>
                <span className={cn('text-xs', providerConfig.color)}>{providerConfig.label}</span>
              </div>
              <h4 className="font-semibold text-sm text-fg truncate">{data.label}</h4>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-1">
            <button
              onClick={handleOpenExternal}
              className="p-1.5 rounded hover:bg-surface-muted text-fg-muted hover:text-fg"
              title="Open in browser"
            >
              <ExternalLink className="w-4 h-4" />
            </button>

            <div className="relative">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setShowMenu(!showMenu);
                }}
                className="p-1.5 rounded hover:bg-surface-muted text-fg-muted hover:text-fg"
              >
                <MoreHorizontal className="w-4 h-4" />
              </button>

              {showMenu && (
                <>
                  <div
                    className="fixed inset-0 z-10"
                    onClick={(e) => {
                      e.stopPropagation();
                      setShowMenu(false);
                    }}
                  />
                  <div className="absolute right-0 top-full mt-1 w-44 bg-surface rounded-lg shadow-lg border border-border py-1 z-20">
                    {data.status === 'pending' && (
                      <>
                        <button
                          onClick={handleApprove}
                          className="flex items-center gap-2 w-full px-3 py-2 text-sm text-success-color hover:bg-success-bg"
                        >
                          <Check className="w-4 h-4" />
                          Approve
                        </button>
                        <button
                          onClick={handleRequestChanges}
                          className="flex items-center gap-2 w-full px-3 py-2 text-sm text-warning-color hover:bg-warning-bg"
                        >
                          <AlertCircle className="w-4 h-4" />
                          Request Changes
                        </button>
                      </>
                    )}
                    {canMerge && (
                      <button
                        onClick={handleMerge}
                        className="flex items-center gap-2 w-full px-3 py-2 text-sm text-info-color hover:bg-info-bg"
                      >
                        <GitMerge className="w-4 h-4" />
                        Merge PR
                      </button>
                    )}
                  </div>
                </>
              )}
            </div>
          </div>
        </div>

        {/* Author */}
        <div className="mt-2 flex items-center gap-2">
          {data.author.avatarUrl ? (
            <img
              src={data.author.avatarUrl}
              alt={data.author.name}
              className="w-5 h-5 rounded-full"
            />
          ) : (
            <div className="w-5 h-5 rounded-full bg-muted flex items-center justify-center">
              <User className="w-3 h-3 text-fg-muted" />
            </div>
          )}
          <span className="text-xs text-fg-muted">{data.author.name}</span>
        </div>

        {/* Branch info */}
        <div className="mt-3 flex items-center gap-2 text-xs">
          <div className="flex items-center gap-1 px-2 py-1 bg-surface-muted rounded-md">
            <GitBranch className="w-3 h-3 text-fg-muted" />
            <span className="font-mono text-fg truncate max-w-[100px]">
              {data.sourceBranch}
            </span>
          </div>
          <span className="text-fg-muted">→</span>
          <div className="flex items-center gap-1 px-2 py-1 bg-surface-muted rounded-md">
            <GitBranch className="w-3 h-3 text-fg-muted" />
            <span className="font-mono text-fg truncate max-w-[100px]">
              {data.targetBranch}
            </span>
          </div>
        </div>

        {/* Linked story */}
        {data.linkedStoryKey && (
          <button
            onClick={handleOpenStory}
            className="mt-2 flex items-center gap-1.5 text-xs text-info-color hover:opacity-80"
          >
            <span className="font-mono">{data.linkedStoryKey}</span>
            {data.linkedStoryTitle && (
              <span className="text-fg-muted truncate max-w-[180px]">
                {data.linkedStoryTitle}
              </span>
            )}
          </button>
        )}
      </div>

      {/* Stats */}
      <div className="p-4 space-y-3">
        {/* File changes */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs text-fg-muted">
            <FileCode className="w-3.5 h-3.5" />
            <span>{data.filesChanged} files changed</span>
          </div>
          <div className="flex items-center gap-2 text-xs font-mono">
            <span className="text-success-color flex items-center gap-0.5">
              <Plus className="w-3 h-3" />
              {data.additions}
            </span>
            <span className="text-destructive flex items-center gap-0.5">
              <Minus className="w-3 h-3" />
              {data.deletions}
            </span>
          </div>
        </div>

        {/* Checks status */}
        {data.checks && (
          <ChecksStatusDisplay checks={data.checks} />
        )}

        {/* Reviewers */}
        {data.reviewers && data.reviewers.length > 0 && (
          <div className="flex items-center justify-between">
            <span className="text-xs text-fg-muted">Reviewers</span>
            <ReviewerList reviewers={data.reviewers} />
          </div>
        )}

        {/* Merge status */}
        {data.status !== 'merged' && data.status !== 'closed' && (
          <div className="flex items-center gap-2">
            {data.mergeable ? (
              <>
                <CheckCircle2 className="w-4 h-4 text-success-color" />
                <span className="text-xs text-success-color">Ready to merge</span>
              </>
            ) : (
              <>
                <XCircle className="w-4 h-4 text-destructive" />
                <span className="text-xs text-destructive">Merge conflicts</span>
              </>
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="px-4 py-3 border-t border-border flex items-center justify-between text-xs text-fg-muted">
        <div className="flex items-center gap-3">
          {(data.commentCount ?? 0) > 0 && (
            <div className="flex items-center gap-1">
              <MessageSquare className="w-3.5 h-3.5" />
              <span>{data.commentCount}</span>
            </div>
          )}
        </div>
        <span>Updated {data.updatedAt}</span>
      </div>
    </div>
  );
};

/**
 * Memoized Pull Request Node
 *
 * Performance optimized with React.memo to prevent unnecessary re-renders
 * during canvas operations like panning and zooming.
 */
export const PullRequestNode = memo(PullRequestNodeComponent);
PullRequestNode.displayName = 'PullRequestNode';

export default PullRequestNode;
