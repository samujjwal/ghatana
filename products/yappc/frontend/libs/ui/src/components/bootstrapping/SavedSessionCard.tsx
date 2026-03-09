/**
 * Saved Session Card Component
 *
 * @description Displays a saved bootstrapping session with options to resume,
 * duplicate, or delete. Shows session status, progress, and last activity.
 *
 * @doc.type component
 * @doc.purpose Saved session display
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useMemo } from 'react';
import { motion } from 'framer-motion';
import {
  Play,
  Copy,
  Trash2,
  MoreHorizontal,
  Clock,
  CheckCircle2,
  AlertCircle,
  Pause,
  FileText,
  Users,
  Calendar,
  ChevronRight,
  Star,
  Archive,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Avatar } from '@ghatana/ui';
import { Progress } from '@ghatana/ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

// =============================================================================
// Types
// =============================================================================

export type SessionStatus = 'in_progress' | 'paused' | 'completed' | 'error' | 'archived';

export interface SessionCollaborator {
  id: string;
  name: string;
  avatar?: string;
}

export interface SavedSession {
  id: string;
  name: string;
  description?: string;
  status: SessionStatus;
  progress: number;
  currentPhase?: string;
  createdAt: string;
  updatedAt: string;
  owner: SessionCollaborator;
  collaborators?: SessionCollaborator[];
  tags?: string[];
  isStarred?: boolean;
  artifactCount?: number;
  commentCount?: number;
}

export interface SavedSessionCardProps {
  /** Session data */
  session: SavedSession;
  /** Called when resume is clicked */
  onResume: () => void;
  /** Called when duplicate is clicked */
  onDuplicate: () => void;
  /** Called when delete is clicked */
  onDelete: () => void;
  /** Called when star is toggled */
  onToggleStar?: () => void;
  /** Called when archive is clicked */
  onArchive?: () => void;
  /** Compact display mode */
  compact?: boolean;
  /** Show collaborators */
  showCollaborators?: boolean;
  /** Show progress bar */
  showProgress?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const STATUS_CONFIG: Record<SessionStatus, {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  color: string;
  bgColor: string;
}> = {
  in_progress: {
    icon: Play,
    label: 'In Progress',
    color: 'text-blue-600 dark:text-blue-400',
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
  },
  paused: {
    icon: Pause,
    label: 'Paused',
    color: 'text-amber-600 dark:text-amber-400',
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
  },
  completed: {
    icon: CheckCircle2,
    label: 'Completed',
    color: 'text-success-600 dark:text-success-400',
    bgColor: 'bg-success-100 dark:bg-success-900/30',
  },
  error: {
    icon: AlertCircle,
    label: 'Error',
    color: 'text-error-600 dark:text-error-400',
    bgColor: 'bg-error-100 dark:bg-error-900/30',
  },
  archived: {
    icon: Archive,
    label: 'Archived',
    color: 'text-neutral-600 dark:text-neutral-400',
    bgColor: 'bg-neutral-100 dark:bg-neutral-800',
  },
};

// =============================================================================
// Animation Variants
// =============================================================================

const cardVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
  hover: { scale: 1.02 },
} as const;

// =============================================================================
// Helper Functions
// =============================================================================

const formatDate = (date: string): string => {
  const d = new Date(date);
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const minutes = Math.floor(diff / (1000 * 60));

  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  if (hours < 24) return `${hours}h ago`;
  if (days < 7) return `${days}d ago`;
  return d.toLocaleDateString();
};

// =============================================================================
// Main Component
// =============================================================================

export const SavedSessionCard: React.FC<SavedSessionCardProps> = ({
  session,
  onResume,
  onDuplicate,
  onDelete,
  onToggleStar,
  onArchive,
  compact = false,
  showCollaborators = true,
  showProgress = true,
  className,
}) => {
  const [isHovered, setIsHovered] = useState(false);
  const statusConfig = STATUS_CONFIG[session.status];
  const StatusIcon = statusConfig.icon;

  const canResume = session.status === 'in_progress' || session.status === 'paused';
  const isComplete = session.status === 'completed';
  const lastUpdated = formatDate(session.updatedAt);

  // Compact card
  if (compact) {
    return (
      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="visible"
        whileHover="hover"
        className={cn(
          'group flex items-center gap-3 rounded-lg border p-3 cursor-pointer transition-colors',
          'hover:border-primary-300 hover:bg-primary-50/50',
          'dark:border-neutral-700 dark:hover:border-primary-700 dark:hover:bg-primary-950/20',
          className
        )}
        onClick={onResume}
      >
        <div className={cn('flex h-10 w-10 items-center justify-center rounded-lg', statusConfig.bgColor)}>
          <StatusIcon className={cn('h-5 w-5', statusConfig.color)} />
        </div>
        
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-neutral-900 dark:text-neutral-100 truncate">
              {session.name}
            </span>
            {session.isStarred && (
              <Star className="h-4 w-4 fill-amber-400 text-amber-400" />
            )}
          </div>
          <div className="flex items-center gap-2 text-xs text-neutral-500">
            <span>{lastUpdated}</span>
            <span>•</span>
            <span>{session.progress}% complete</span>
          </div>
        </div>

        <ChevronRight className="h-5 w-5 text-neutral-400 group-hover:text-primary-500" />
      </motion.div>
    );
  }

  // Full card
  return (
    <motion.div
      variants={cardVariants}
      initial="hidden"
      animate="visible"
      whileHover="hover"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className={cn(
        'group relative rounded-lg border bg-white p-4 transition-shadow',
        'hover:shadow-md',
        'dark:border-neutral-700 dark:bg-neutral-900',
        className
      )}
    >
      {/* Star indicator */}
      {session.isStarred && (
        <div className="absolute -top-2 -right-2">
          <Star className="h-5 w-5 fill-amber-400 text-amber-400" />
        </div>
      )}

      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-medium text-neutral-900 dark:text-neutral-100 truncate">
              {session.name}
            </h3>
            <Badge variant="outline" className={cn('shrink-0', statusConfig.color, statusConfig.bgColor)}>
              <StatusIcon className="mr-1 h-3 w-3" />
              {statusConfig.label}
            </Badge>
          </div>
          {session.description && (
            <p className="mt-1 text-sm text-neutral-500 line-clamp-2">
              {session.description}
            </p>
          )}
        </div>

        {/* Actions menu */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm" className="shrink-0">
              <MoreHorizontal className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {canResume && (
              <DropdownMenuItem onClick={onResume}>
                <Play className="mr-2 h-4 w-4" />
                Resume
              </DropdownMenuItem>
            )}
            {isComplete && (
              <DropdownMenuItem onClick={onResume}>
                <FileText className="mr-2 h-4 w-4" />
                View
              </DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={onDuplicate}>
              <Copy className="mr-2 h-4 w-4" />
              Duplicate
            </DropdownMenuItem>
            {onToggleStar && (
              <DropdownMenuItem onClick={onToggleStar}>
                <Star className={cn('mr-2 h-4 w-4', session.isStarred && 'fill-current')} />
                {session.isStarred ? 'Unstar' : 'Star'}
              </DropdownMenuItem>
            )}
            {onArchive && session.status !== 'archived' && (
              <DropdownMenuItem onClick={onArchive}>
                <Archive className="mr-2 h-4 w-4" />
                Archive
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onDelete} className="text-error-600 dark:text-error-400">
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Progress */}
      {showProgress && (
        <div className="mt-3">
          <div className="flex items-center justify-between text-xs text-neutral-500 mb-1">
            <span>{session.currentPhase || 'Phase 1'}</span>
            <span>{session.progress}%</span>
          </div>
          <Progress value={session.progress} className="h-1.5" />
        </div>
      )}

      {/* Tags */}
      {session.tags && session.tags.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1">
          {session.tags.slice(0, 3).map((tag) => (
            <Badge key={tag} variant="outline" className="text-xs">
              {tag}
            </Badge>
          ))}
          {session.tags.length > 3 && (
            <Badge variant="outline" className="text-xs">
              +{session.tags.length - 3}
            </Badge>
          )}
        </div>
      )}

      {/* Footer */}
      <div className="mt-4 flex items-center justify-between">
        {/* Collaborators */}
        {showCollaborators && (
          <div className="flex items-center gap-2">
            <div className="flex -space-x-2">
              <Avatar
                size="small"
                alt={session.owner.name}
                src={session.owner.avatar}
                className="ring-2 ring-white dark:ring-neutral-900"
              />
              {session.collaborators?.slice(0, 2).map((collab) => (
                <Avatar
                  key={collab.id}
                  size="small"
                  alt={collab.name}
                  src={collab.avatar}
                  className="ring-2 ring-white dark:ring-neutral-900"
                />
              ))}
              {session.collaborators && session.collaborators.length > 2 && (
                <div className="flex h-6 w-6 items-center justify-center rounded-full bg-neutral-200 text-xs font-medium dark:bg-neutral-700 ring-2 ring-white dark:ring-neutral-900">
                  +{session.collaborators.length - 2}
                </div>
              )}
            </div>
            {session.collaborators && session.collaborators.length > 0 && (
              <Tooltip>
                <TooltipTrigger>
                  <Users className="h-4 w-4 text-neutral-400" />
                </TooltipTrigger>
                <TooltipContent>
                  {session.collaborators.length + 1} collaborators
                </TooltipContent>
              </Tooltip>
            )}
          </div>
        )}

        {/* Meta info */}
        <div className="flex items-center gap-3 text-xs text-neutral-500">
          {session.artifactCount !== undefined && session.artifactCount > 0 && (
            <Tooltip>
              <TooltipTrigger className="flex items-center gap-1">
                <FileText className="h-3 w-3" />
                {session.artifactCount}
              </TooltipTrigger>
              <TooltipContent>{session.artifactCount} artifacts</TooltipContent>
            </Tooltip>
          )}
          <Tooltip>
            <TooltipTrigger className="flex items-center gap-1">
              <Calendar className="h-3 w-3" />
              {lastUpdated}
            </TooltipTrigger>
            <TooltipContent>Last updated {lastUpdated}</TooltipContent>
          </Tooltip>
        </div>
      </div>

      {/* Quick action on hover */}
      {canResume && isHovered && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="absolute inset-0 flex items-center justify-center bg-white/80 dark:bg-neutral-900/80 rounded-lg"
        >
          <Button variant="solid" colorScheme="primary" onClick={onResume}>
            <Play className="mr-2 h-4 w-4" />
            Resume Session
          </Button>
        </motion.div>
      )}
    </motion.div>
  );
};

export default SavedSessionCard;
