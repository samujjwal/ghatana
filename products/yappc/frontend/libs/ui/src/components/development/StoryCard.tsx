/**
 * StoryCard Component
 *
 * @description Kanban board story card displaying story information including
 * title, points, assignee, priority, and status. Supports drag-and-drop.
 *
 * @doc.phase 3
 * @doc.component StoryCard
 */

import React, { useMemo, useCallback } from 'react';

// ============================================================================
// Types
// ============================================================================

export type StoryType = 'feature' | 'bug' | 'chore' | 'spike';
export type StoryPriority = 'P0' | 'P1' | 'P2' | 'P3';
export type StoryStatus =
  | 'backlog'
  | 'todo'
  | 'in-progress'
  | 'review'
  | 'done';

export interface StoryAssignee {
  id: string;
  name: string;
  avatar?: string;
}

export interface StoryTask {
  id: string;
  title: string;
  completed: boolean;
}

export interface StoryPullRequest {
  id: string;
  number: number;
  status: 'open' | 'approved' | 'merged' | 'closed';
  url: string;
}

export interface Story {
  id: string;
  code: string;
  title: string;
  description?: string;
  type: StoryType;
  priority: StoryPriority;
  status: StoryStatus;
  storyPoints: number;
  assignee?: StoryAssignee;
  tasks?: StoryTask[];
  pullRequest?: StoryPullRequest;
  blockedBy?: string[];
  createdAt: Date;
  updatedAt: Date;
}

export interface StoryCardProps {
  story: Story;
  isDragging?: boolean;
  isSelected?: boolean;
  compact?: boolean;
  onClick?: (story: Story) => void;
  onDragStart?: (story: Story) => void;
  onDragEnd?: () => void;
}

// ============================================================================
// Constants
// ============================================================================

const TYPE_ICONS: Record<StoryType, string> = {
  feature: '✨',
  bug: '🐛',
  chore: '🔧',
  spike: '🔬',
};

const TYPE_COLORS: Record<StoryType, { bg: string; text: string }> = {
  feature: { bg: '#DBEAFE', text: '#1E40AF' },
  bug: { bg: '#FEE2E2', text: '#991B1B' },
  chore: { bg: '#E5E7EB', text: '#374151' },
  spike: { bg: '#F3E8FF', text: '#6B21A8' },
};

const PRIORITY_COLORS: Record<StoryPriority, { bg: string; text: string }> = {
  P0: { bg: '#FEE2E2', text: '#DC2626' },
  P1: { bg: '#FEF3C7', text: '#D97706' },
  P2: { bg: '#DBEAFE', text: '#2563EB' },
  P3: { bg: '#E5E7EB', text: '#6B7280' },
};

const PR_STATUS_COLORS: Record<string, string> = {
  open: '#F59E0B',
  approved: '#10B981',
  merged: '#6366F1',
  closed: '#6B7280',
};

// ============================================================================
// Sub Components
// ============================================================================

interface AssigneeAvatarProps {
  assignee?: StoryAssignee;
}

const AssigneeAvatar: React.FC<AssigneeAvatarProps> = ({ assignee }) => {
  if (!assignee) {
    return (
      <div className="assignee-avatar unassigned" title="Unassigned">
        <span>—</span>
      </div>
    );
  }

  return (
    <div className="assignee-avatar" title={assignee.name}>
      {assignee.avatar ? (
        <img src={assignee.avatar} alt={assignee.name} />
      ) : (
        <span>{assignee.name.charAt(0).toUpperCase()}</span>
      )}
    </div>
  );
};

interface TaskProgressProps {
  tasks: StoryTask[];
}

const TaskProgress: React.FC<TaskProgressProps> = ({ tasks }) => {
  const completed = tasks.filter((t) => t.completed).length;
  const total = tasks.length;
  const percentage = total > 0 ? (completed / total) * 100 : 0;

  return (
    <div className="task-progress">
      <div className="task-progress-bar">
        <div
          className="task-progress-fill"
          style={{ width: `${percentage}%` }}
        />
      </div>
      <span className="task-progress-text">
        {completed}/{total}
      </span>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const StoryCard: React.FC<StoryCardProps> = ({
  story,
  isDragging = false,
  isSelected = false,
  compact = false,
  onClick,
  onDragStart,
  onDragEnd,
}) => {
  const typeStyle = useMemo(() => TYPE_COLORS[story.type], [story.type]);
  const priorityStyle = useMemo(
    () => PRIORITY_COLORS[story.priority],
    [story.priority]
  );

  const isBlocked = story.blockedBy && story.blockedBy.length > 0;
  const hasPR = !!story.pullRequest;
  const hasTasks = story.tasks && story.tasks.length > 0;

  const handleClick = useCallback(() => {
    onClick?.(story);
  }, [onClick, story]);

  const handleDragStart = useCallback(
    (e: React.DragEvent) => {
      e.dataTransfer.setData('text/plain', story.id);
      e.dataTransfer.effectAllowed = 'move';
      onDragStart?.(story);
    },
    [onDragStart, story]
  );

  const handleDragEnd = useCallback(() => {
    onDragEnd?.();
  }, [onDragEnd]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        onClick?.(story);
      }
    },
    [onClick, story]
  );

  return (
    <div
      className={`story-card ${isDragging ? 'story-card--dragging' : ''} ${
        isSelected ? 'story-card--selected' : ''
      } ${compact ? 'story-card--compact' : ''} ${
        isBlocked ? 'story-card--blocked' : ''
      }`}
      draggable
      onClick={handleClick}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onKeyDown={handleKeyDown}
      tabIndex={0}
      role="article"
      aria-label={`Story ${story.code}: ${story.title}`}
      aria-selected={isSelected}
    >
      {/* Header Row */}
      <div className="story-header">
        <span
          className="story-type"
          style={{ background: typeStyle.bg, color: typeStyle.text }}
          title={story.type}
        >
          {TYPE_ICONS[story.type]}
        </span>
        <span className="story-code">{story.code}</span>
        {isBlocked && (
          <span className="story-blocked" title="Blocked">
            🔥
          </span>
        )}
      </div>

      {/* Title */}
      <h4 className="story-title">{story.title}</h4>

      {/* Task Progress */}
      {!compact && hasTasks && <TaskProgress tasks={story.tasks!} />}

      {/* PR Status */}
      {!compact && hasPR && (
        <div className="story-pr">
          <span
            className="pr-indicator"
            style={{
              background:
                PR_STATUS_COLORS[story.pullRequest!.status] || PR_STATUS_COLORS.open,
            }}
          />
          <span className="pr-text">PR #{story.pullRequest!.number}</span>
        </div>
      )}

      {/* Footer */}
      <div className="story-footer">
        <span
          className="story-points"
          title={`${story.storyPoints} story points`}
        >
          🏷 {story.storyPoints}pts
        </span>
        <span
          className="story-priority"
          style={{ background: priorityStyle.bg, color: priorityStyle.text }}
        >
          ⚡ {story.priority}
        </span>
        <AssigneeAvatar assignee={story.assignee} />
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .story-card {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 8px;
          padding: 0.75rem;
          cursor: grab;
          transition: all 0.15s ease;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
        }

        .story-card:hover {
          border-color: #3B82F6;
          box-shadow: 0 2px 8px rgba(59, 130, 246, 0.15);
        }

        .story-card:focus {
          outline: 2px solid #3B82F6;
          outline-offset: 2px;
        }

        .story-card--dragging {
          opacity: 0.5;
          cursor: grabbing;
          transform: rotate(2deg);
        }

        .story-card--selected {
          border-color: #3B82F6;
          background: #EFF6FF;
        }

        .story-card--compact {
          padding: 0.5rem;
        }

        .story-card--blocked {
          border-left: 3px solid #DC2626;
        }

        .story-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-bottom: 0.5rem;
        }

        .story-type {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 20px;
          height: 20px;
          border-radius: 4px;
          font-size: 0.75rem;
        }

        .story-code {
          font-size: 0.75rem;
          font-weight: 500;
          color: #6B7280;
          font-family: 'Monaco', 'Menlo', monospace;
        }

        .story-blocked {
          margin-left: auto;
          animation: pulse 2s infinite;
        }

        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.5; }
        }

        .story-title {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
          line-height: 1.3;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .story-card--compact .story-title {
          -webkit-line-clamp: 1;
        }

        .task-progress {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-top: 0.5rem;
        }

        .task-progress-bar {
          flex: 1;
          height: 4px;
          background: #E5E7EB;
          border-radius: 2px;
          overflow: hidden;
        }

        .task-progress-fill {
          height: 100%;
          background: #10B981;
          transition: width 0.3s ease;
        }

        .task-progress-text {
          font-size: 0.625rem;
          color: #6B7280;
          white-space: nowrap;
        }

        .story-pr {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          margin-top: 0.5rem;
          padding: 0.25rem 0.5rem;
          background: #F3F4F6;
          border-radius: 4px;
        }

        .pr-indicator {
          width: 6px;
          height: 6px;
          border-radius: 50%;
        }

        .pr-text {
          font-size: 0.625rem;
          color: #6B7280;
          font-family: 'Monaco', 'Menlo', monospace;
        }

        .story-footer {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-top: 0.75rem;
          padding-top: 0.5rem;
          border-top: 1px solid #F3F4F6;
        }

        .story-points {
          font-size: 0.625rem;
          color: #6B7280;
        }

        .story-priority {
          font-size: 0.625rem;
          padding: 0.125rem 0.375rem;
          border-radius: 4px;
          font-weight: 500;
        }

        .assignee-avatar {
          width: 24px;
          height: 24px;
          border-radius: 50%;
          overflow: hidden;
          background: #E5E7EB;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 0.75rem;
          font-weight: 500;
          color: #374151;
          margin-left: auto;
        }

        .assignee-avatar.unassigned {
          color: #9CA3AF;
        }

        .assignee-avatar img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
      `}</style>
    </div>
  );
};

StoryCard.displayName = 'StoryCard';

export default StoryCard;
