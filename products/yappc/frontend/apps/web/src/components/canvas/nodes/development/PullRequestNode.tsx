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
import { Handle, Position, type NodeProps } from '@xyflow/react';
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

export interface PullRequestNodeData {
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

export interface PullRequestNodeProps extends NodeProps<PullRequestNodeData> {}

// =============================================================================
// Constants
// =============================================================================

const STATUS_CONFIG: Record<ReviewStatus, { label: string; icon: typeof GitPullRequest; color: string; bgColor: string }> = {
  pending: {
    label: 'Open',
    icon: GitPullRequest,
    color: 'text-emerald-600',
    bgColor: 'bg-emerald-50 border-emerald-200',
  },
  changes_requested: {
    label: 'Changes Requested',
    icon: AlertCircle,
    color: 'text-amber-600',
    bgColor: 'bg-amber-50 border-amber-200',
  },
  approved: {
    label: 'Approved',
    icon: CheckCircle2,
    color: 'text-green-600',
    bgColor: 'bg-green-50 border-green-200',
  },
  merged: {
    label: 'Merged',
    icon: GitMerge,
    color: 'text-purple-600',
    bgColor: 'bg-purple-50 border-purple-200',
  },
  closed: {
    label: 'Closed',
    icon: XCircle,
    color: 'text-red-600',
    bgColor: 'bg-red-50 border-red-200',
  },
};

const PROVIDER_CONFIG: Record<GitProvider, { label: string; color: string }> = {
  github: { label: 'GitHub', color: 'text-gray-800' },
  gitlab: { label: 'GitLab', color: 'text-orange-600' },
  bitbucket: { label: 'Bitbucket', color: 'text-blue-600' },
  azure_devops: { label: 'Azure DevOps', color: 'text-blue-500' },
};

const REVIEWER_STATUS_ICON: Record<PRReviewer['status'], { icon: typeof Check; color: string }> = {
  pending: { icon: Clock, color: 'text-gray-400' },
  approved: { icon: Check, color: 'text-green-500' },
  changes_requested: { icon: AlertCircle, color: 'text-amber-500' },
  commented: { icon: MessageSquare, color: 'text-blue-500' },
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
              className="w-6 h-6 rounded-full border-2 border-white"
            />
          ) : (
            <div className="w-6 h-6 rounded-full bg-gray-200 flex items-center justify-center border-2 border-white">
              <span className="text-[10px] font-medium text-gray-600">
                {reviewer.name.charAt(0).toUpperCase()}
              </span>
            </div>
          )}
          <div className={cn(
            'absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-white flex items-center justify-center'
          )}>
            <StatusIcon className={cn('w-2.5 h-2.5', statusConfig.color)} />
          </div>
        </div>
      );
    })}
    {reviewers.length > 4 && (
      <span className="text-xs text-gray-500">+{reviewers.length - 4}</span>
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
  let statusColor = 'text-emerald-500';
  let statusLabel = 'All checks passed';

  if (hasFailed) {
    StatusIcon = XCircle;
    statusColor = 'text-red-500';
    statusLabel = `${checks.failed} check${checks.failed > 1 ? 's' : ''} failed`;
  } else if (hasPending) {
    StatusIcon = Loader2;
    statusColor = 'text-amber-500';
    statusLabel = `${checks.pending} check${checks.pending > 1 ? 's' : ''} pending`;
  }

  return (
    <div className="flex items-center gap-2">
      <StatusIcon className={cn('w-4 h-4', statusColor, hasPending && 'animate-spin')} />
      <span className="text-xs text-gray-600">{statusLabel}</span>
      <span className="text-xs text-gray-400">
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
        'bg-white',
        statusConfig.bgColor,
        selected && 'ring-2 ring-violet-500 ring-offset-2',
        dragging && 'opacity-75 shadow-lg scale-[1.02]'
      )}
      onDoubleClick={handleOpenExternal}
    >
      {/* Handles for connections */}
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />

      {/* Header */}
      <div className="p-4 border-b border-gray-100">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <div className={cn('p-2 rounded-lg', statusConfig.bgColor)}>
              <StatusIcon className={cn('w-5 h-5', statusConfig.color)} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-gray-500">#{data.number}</span>
                <span className={cn('text-xs', providerConfig.color)}>{providerConfig.label}</span>
              </div>
              <h4 className="font-semibold text-sm text-gray-900 truncate">{data.label}</h4>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-1">
            <button
              onClick={handleOpenExternal}
              className="p-1.5 rounded hover:bg-gray-100 text-gray-400 hover:text-gray-600"
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
                className="p-1.5 rounded hover:bg-gray-100 text-gray-400 hover:text-gray-600"
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
                  <div className="absolute right-0 top-full mt-1 w-44 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-20">
                    {data.status === 'pending' && (
                      <>
                        <button
                          onClick={handleApprove}
                          className="flex items-center gap-2 w-full px-3 py-2 text-sm text-green-600 hover:bg-green-50"
                        >
                          <Check className="w-4 h-4" />
                          Approve
                        </button>
                        <button
                          onClick={handleRequestChanges}
                          className="flex items-center gap-2 w-full px-3 py-2 text-sm text-amber-600 hover:bg-amber-50"
                        >
                          <AlertCircle className="w-4 h-4" />
                          Request Changes
                        </button>
                      </>
                    )}
                    {canMerge && (
                      <button
                        onClick={handleMerge}
                        className="flex items-center gap-2 w-full px-3 py-2 text-sm text-purple-600 hover:bg-purple-50"
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
            <div className="w-5 h-5 rounded-full bg-gray-200 flex items-center justify-center">
              <User className="w-3 h-3 text-gray-500" />
            </div>
          )}
          <span className="text-xs text-gray-600">{data.author.name}</span>
        </div>

        {/* Branch info */}
        <div className="mt-3 flex items-center gap-2 text-xs">
          <div className="flex items-center gap-1 px-2 py-1 bg-gray-100 rounded-md">
            <GitBranch className="w-3 h-3 text-gray-500" />
            <span className="font-mono text-gray-700 truncate max-w-[100px]">
              {data.sourceBranch}
            </span>
          </div>
          <span className="text-gray-400">→</span>
          <div className="flex items-center gap-1 px-2 py-1 bg-gray-100 rounded-md">
            <GitBranch className="w-3 h-3 text-gray-500" />
            <span className="font-mono text-gray-700 truncate max-w-[100px]">
              {data.targetBranch}
            </span>
          </div>
        </div>

        {/* Linked story */}
        {data.linkedStoryKey && (
          <button
            onClick={handleOpenStory}
            className="mt-2 flex items-center gap-1.5 text-xs text-violet-600 hover:text-violet-700"
          >
            <span className="font-mono">{data.linkedStoryKey}</span>
            {data.linkedStoryTitle && (
              <span className="text-gray-500 truncate max-w-[180px]">
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
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <FileCode className="w-3.5 h-3.5" />
            <span>{data.filesChanged} files changed</span>
          </div>
          <div className="flex items-center gap-2 text-xs font-mono">
            <span className="text-emerald-600 flex items-center gap-0.5">
              <Plus className="w-3 h-3" />
              {data.additions}
            </span>
            <span className="text-red-600 flex items-center gap-0.5">
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
            <span className="text-xs text-gray-500">Reviewers</span>
            <ReviewerList reviewers={data.reviewers} />
          </div>
        )}

        {/* Merge status */}
        {data.status !== 'merged' && data.status !== 'closed' && (
          <div className="flex items-center gap-2">
            {data.mergeable ? (
              <>
                <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                <span className="text-xs text-emerald-600">Ready to merge</span>
              </>
            ) : (
              <>
                <XCircle className="w-4 h-4 text-red-500" />
                <span className="text-xs text-red-600">Merge conflicts</span>
              </>
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="px-4 py-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-400">
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
