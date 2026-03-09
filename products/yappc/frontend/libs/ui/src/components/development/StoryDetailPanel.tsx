/**
 * StoryDetailPanel Component
 *
 * @description Slide-out panel for viewing and editing story details,
 * including tasks, acceptance criteria, linked resources, and activity feed.
 *
 * @doc.phase 3
 * @doc.component StoryDetailPanel
 */

import React, { useCallback, useMemo, useState } from 'react';

// ============================================================================
// Types
// ============================================================================

export type StoryType = 'feature' | 'bug' | 'chore' | 'spike';
export type StoryStatus = 'todo' | 'in_progress' | 'in_review' | 'done';
export type StoryPriority = 'low' | 'medium' | 'high' | 'critical';

export interface StoryTask {
  id: string;
  title: string;
  completed: boolean;
  assigneeId?: string;
}

export interface StoryAssignee {
  id: string;
  name: string;
  avatar?: string;
}

export interface AcceptanceCriteria {
  id: string;
  description: string;
  completed: boolean;
}

export interface LinkedResource {
  id: string;
  type: 'pr' | 'figma' | 'doc' | 'link' | 'ticket';
  title: string;
  url: string;
  status?: string;
}

export interface ActivityItem {
  id: string;
  type: 'comment' | 'status_change' | 'assignment' | 'created' | 'edited';
  userId: string;
  userName: string;
  userAvatar?: string;
  content: string;
  timestamp: string;
}

export interface StoryLabel {
  id: string;
  name: string;
  color: string;
}

export interface Story {
  id: string;
  key: string;
  title: string;
  description?: string;
  type: StoryType;
  status: StoryStatus;
  priority: StoryPriority;
  points?: number;
  assignees: StoryAssignee[];
  reporter: StoryAssignee;
  tasks: StoryTask[];
  acceptanceCriteria: AcceptanceCriteria[];
  linkedResources: LinkedResource[];
  activity: ActivityItem[];
  labels: StoryLabel[];
  sprintId?: string;
  sprintName?: string;
  createdAt: string;
  updatedAt: string;
  dueDate?: string;
}

export interface StoryDetailPanelProps {
  story: Story;
  isOpen: boolean;
  onClose: () => void;
  onUpdate?: (story: Story, updates: Partial<Story>) => void;
  onTaskToggle?: (story: Story, taskId: string, completed: boolean) => void;
  onCriteriaToggle?: (story: Story, criteriaId: string, completed: boolean) => void;
  onAddComment?: (story: Story, comment: string) => void;
  onAddTask?: (story: Story, title: string) => void;
  onDelete?: (story: Story) => void;
}

// ============================================================================
// Constants
// ============================================================================

const TYPE_CONFIG: Record<StoryType, { icon: string; color: string; bg: string }> = {
  feature: { icon: '✨', color: '#10B981', bg: '#D1FAE5' },
  bug: { icon: '🐛', color: '#EF4444', bg: '#FEE2E2' },
  chore: { icon: '🔧', color: '#6B7280', bg: '#F3F4F6' },
  spike: { icon: '🔬', color: '#8B5CF6', bg: '#EDE9FE' },
};

const STATUS_CONFIG: Record<StoryStatus, { label: string; color: string }> = {
  todo: { label: 'To Do', color: '#6B7280' },
  in_progress: { label: 'In Progress', color: '#3B82F6' },
  in_review: { label: 'In Review', color: '#F59E0B' },
  done: { label: 'Done', color: '#10B981' },
};

const PRIORITY_CONFIG: Record<StoryPriority, { label: string; color: string; bg: string }> = {
  low: { label: 'Low', color: '#6B7280', bg: '#F3F4F6' },
  medium: { label: 'Medium', color: '#3B82F6', bg: '#EFF6FF' },
  high: { label: 'High', color: '#F59E0B', bg: '#FEF3C7' },
  critical: { label: 'Critical', color: '#EF4444', bg: '#FEE2E2' },
};

const RESOURCE_ICONS: Record<LinkedResource['type'], string> = {
  pr: '🔀',
  figma: '🎨',
  doc: '📄',
  link: '🔗',
  ticket: '🎫',
};

// ============================================================================
// Utility Functions
// ============================================================================

const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const formatTimeAgo = (dateString: string): string => {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return formatDate(dateString);
};

// ============================================================================
// Main Component
// ============================================================================

export const StoryDetailPanel: React.FC<StoryDetailPanelProps> = ({
  story,
  isOpen,
  onClose,
  onUpdate,
  onTaskToggle,
  onCriteriaToggle,
  onAddComment,
  onAddTask,
  onDelete,
}) => {
  const [activeTab, setActiveTab] = useState<'details' | 'activity'>('details');
  const [commentText, setCommentText] = useState('');
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [isAddingTask, setIsAddingTask] = useState(false);

  const typeConfig = useMemo(() => TYPE_CONFIG[story.type], [story.type]);
  const statusConfig = useMemo(() => STATUS_CONFIG[story.status], [story.status]);
  const priorityConfig = useMemo(() => PRIORITY_CONFIG[story.priority], [story.priority]);

  const taskProgress = useMemo(() => {
    if (story.tasks.length === 0) return 0;
    const completed = story.tasks.filter((t) => t.completed).length;
    return Math.round((completed / story.tasks.length) * 100);
  }, [story.tasks]);

  const criteriaProgress = useMemo(() => {
    if (story.acceptanceCriteria.length === 0) return 0;
    const completed = story.acceptanceCriteria.filter((c) => c.completed).length;
    return Math.round((completed / story.acceptanceCriteria.length) * 100);
  }, [story.acceptanceCriteria]);

  const handleStatusChange = useCallback(
    (newStatus: StoryStatus) => {
      if (onUpdate) {
        onUpdate(story, { status: newStatus });
      }
    },
    [onUpdate, story]
  );

  const handleTaskToggle = useCallback(
    (taskId: string, completed: boolean) => {
      if (onTaskToggle) {
        onTaskToggle(story, taskId, completed);
      }
    },
    [onTaskToggle, story]
  );

  const handleCriteriaToggle = useCallback(
    (criteriaId: string, completed: boolean) => {
      if (onCriteriaToggle) {
        onCriteriaToggle(story, criteriaId, completed);
      }
    },
    [onCriteriaToggle, story]
  );

  const handleAddComment = useCallback(() => {
    if (onAddComment && commentText.trim()) {
      onAddComment(story, commentText.trim());
      setCommentText('');
    }
  }, [onAddComment, story, commentText]);

  const handleAddTask = useCallback(() => {
    if (onAddTask && newTaskTitle.trim()) {
      onAddTask(story, newTaskTitle.trim());
      setNewTaskTitle('');
      setIsAddingTask(false);
    }
  }, [onAddTask, story, newTaskTitle]);

  const handleDelete = useCallback(() => {
    if (onDelete) {
      onDelete(story);
    }
  }, [onDelete, story]);

  if (!isOpen) return null;

  return (
    <div className="story-panel-overlay" onClick={onClose}>
      <aside
        className="story-detail-panel"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-label={`Story details: ${story.title}`}
      >
        {/* Header */}
        <header className="panel-header">
          <div className="header-top">
            <div className="story-key-badge">
              <span className="type-icon" style={{ color: typeConfig.color }}>
                {typeConfig.icon}
              </span>
              <span className="story-key">{story.key}</span>
            </div>
            <div className="header-actions">
              <button
                type="button"
                className="action-btn"
                onClick={handleDelete}
                title="Delete Story"
              >
                🗑️
              </button>
              <button
                type="button"
                className="close-btn"
                onClick={onClose}
                aria-label="Close panel"
              >
                ✕
              </button>
            </div>
          </div>
          <h2 className="story-title">{story.title}</h2>
          <div className="meta-row">
            <span
              className="priority-badge"
              style={{ color: priorityConfig.color, background: priorityConfig.bg }}
            >
              {priorityConfig.label}
            </span>
            {story.points !== undefined && (
              <span className="points-badge">{story.points} pts</span>
            )}
            {story.sprintName && (
              <span className="sprint-badge">🏃 {story.sprintName}</span>
            )}
          </div>
        </header>

        {/* Status Bar */}
        <div className="status-bar">
          {(Object.keys(STATUS_CONFIG) as StoryStatus[]).map((statusKey) => {
            const config = STATUS_CONFIG[statusKey];
            const isActive = story.status === statusKey;
            return (
              <button
                key={statusKey}
                type="button"
                className={`status-option ${isActive ? 'status-option--active' : ''}`}
                style={{
                  '--status-color': config.color,
                } as React.CSSProperties}
                onClick={() => handleStatusChange(statusKey)}
              >
                {config.label}
              </button>
            );
          })}
        </div>

        {/* Tab Navigation */}
        <nav className="tab-nav">
          <button
            type="button"
            className={`tab-btn ${activeTab === 'details' ? 'tab-btn--active' : ''}`}
            onClick={() => setActiveTab('details')}
          >
            Details
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'activity' ? 'tab-btn--active' : ''}`}
            onClick={() => setActiveTab('activity')}
          >
            Activity ({story.activity.length})
          </button>
        </nav>

        {/* Content */}
        <div className="panel-content">
          {activeTab === 'details' ? (
            <>
              {/* Description */}
              {story.description && (
                <section className="section">
                  <h3 className="section-title">Description</h3>
                  <p className="description">{story.description}</p>
                </section>
              )}

              {/* Assignees */}
              <section className="section">
                <h3 className="section-title">Assignees</h3>
                <div className="assignees-list">
                  {story.assignees.length > 0 ? (
                    story.assignees.map((assignee) => (
                      <div key={assignee.id} className="assignee">
                        {assignee.avatar ? (
                          <img
                            src={assignee.avatar}
                            alt={assignee.name}
                            className="assignee-avatar"
                          />
                        ) : (
                          <span className="assignee-avatar assignee-avatar--fallback">
                            {assignee.name.charAt(0).toUpperCase()}
                          </span>
                        )}
                        <span className="assignee-name">{assignee.name}</span>
                      </div>
                    ))
                  ) : (
                    <span className="empty-text">Unassigned</span>
                  )}
                </div>
              </section>

              {/* Labels */}
              {story.labels.length > 0 && (
                <section className="section">
                  <h3 className="section-title">Labels</h3>
                  <div className="labels">
                    {story.labels.map((label) => (
                      <span
                        key={label.id}
                        className="label"
                        style={{ background: label.color + '20', color: label.color }}
                      >
                        {label.name}
                      </span>
                    ))}
                  </div>
                </section>
              )}

              {/* Tasks */}
              <section className="section">
                <div className="section-header">
                  <h3 className="section-title">
                    Tasks ({story.tasks.filter((t) => t.completed).length}/{story.tasks.length})
                  </h3>
                  <span className="progress-text">{taskProgress}%</span>
                </div>
                <div className="progress-bar">
                  <div className="progress-fill" style={{ width: `${taskProgress}%` }} />
                </div>
                <ul className="tasks-list">
                  {story.tasks.map((task) => (
                    <li key={task.id} className="task-item">
                      <label className="task-checkbox">
                        <input
                          type="checkbox"
                          checked={task.completed}
                          onChange={(e) => handleTaskToggle(task.id, e.target.checked)}
                        />
                        <span className={`task-title ${task.completed ? 'task-title--completed' : ''}`}>
                          {task.title}
                        </span>
                      </label>
                    </li>
                  ))}
                </ul>
                {isAddingTask ? (
                  <div className="add-task-form">
                    <input
                      type="text"
                      value={newTaskTitle}
                      onChange={(e) => setNewTaskTitle(e.target.value)}
                      placeholder="Task title..."
                      className="add-task-input"
                      autoFocus
                      onKeyDown={(e) => e.key === 'Enter' && handleAddTask()}
                    />
                    <button type="button" className="add-task-btn" onClick={handleAddTask}>
                      Add
                    </button>
                    <button
                      type="button"
                      className="cancel-task-btn"
                      onClick={() => setIsAddingTask(false)}
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="add-item-btn"
                    onClick={() => setIsAddingTask(true)}
                  >
                    + Add Task
                  </button>
                )}
              </section>

              {/* Acceptance Criteria */}
              <section className="section">
                <div className="section-header">
                  <h3 className="section-title">
                    Acceptance Criteria ({story.acceptanceCriteria.filter((c) => c.completed).length}/
                    {story.acceptanceCriteria.length})
                  </h3>
                  <span className="progress-text">{criteriaProgress}%</span>
                </div>
                <div className="progress-bar">
                  <div className="progress-fill" style={{ width: `${criteriaProgress}%` }} />
                </div>
                <ul className="criteria-list">
                  {story.acceptanceCriteria.map((criteria) => (
                    <li key={criteria.id} className="criteria-item">
                      <label className="criteria-checkbox">
                        <input
                          type="checkbox"
                          checked={criteria.completed}
                          onChange={(e) => handleCriteriaToggle(criteria.id, e.target.checked)}
                        />
                        <span className={`criteria-text ${criteria.completed ? 'criteria-text--completed' : ''}`}>
                          {criteria.description}
                        </span>
                      </label>
                    </li>
                  ))}
                </ul>
              </section>

              {/* Linked Resources */}
              {story.linkedResources.length > 0 && (
                <section className="section">
                  <h3 className="section-title">Linked Resources</h3>
                  <ul className="resources-list">
                    {story.linkedResources.map((resource) => (
                      <li key={resource.id} className="resource-item">
                        <a
                          href={resource.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="resource-link"
                        >
                          <span className="resource-icon">
                            {RESOURCE_ICONS[resource.type]}
                          </span>
                          <span className="resource-title">{resource.title}</span>
                          {resource.status && (
                            <span className="resource-status">{resource.status}</span>
                          )}
                        </a>
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {/* Dates */}
              <section className="section section--meta">
                <div className="meta-item">
                  <span className="meta-label">Created</span>
                  <span className="meta-value">{formatDate(story.createdAt)}</span>
                </div>
                <div className="meta-item">
                  <span className="meta-label">Updated</span>
                  <span className="meta-value">{formatDate(story.updatedAt)}</span>
                </div>
                {story.dueDate && (
                  <div className="meta-item">
                    <span className="meta-label">Due Date</span>
                    <span className="meta-value">{formatDate(story.dueDate)}</span>
                  </div>
                )}
                <div className="meta-item">
                  <span className="meta-label">Reporter</span>
                  <span className="meta-value">{story.reporter.name}</span>
                </div>
              </section>
            </>
          ) : (
            <>
              {/* Activity Feed */}
              <div className="activity-feed">
                {story.activity.map((item) => (
                  <div key={item.id} className="activity-item">
                    {item.userAvatar ? (
                      <img
                        src={item.userAvatar}
                        alt={item.userName}
                        className="activity-avatar"
                      />
                    ) : (
                      <span className="activity-avatar activity-avatar--fallback">
                        {item.userName.charAt(0).toUpperCase()}
                      </span>
                    )}
                    <div className="activity-content">
                      <div className="activity-header">
                        <span className="activity-user">{item.userName}</span>
                        <span className="activity-time">{formatTimeAgo(item.timestamp)}</span>
                      </div>
                      <p className="activity-text">{item.content}</p>
                    </div>
                  </div>
                ))}
              </div>

              {/* Add Comment */}
              <div className="add-comment">
                <textarea
                  value={commentText}
                  onChange={(e) => setCommentText(e.target.value)}
                  placeholder="Add a comment..."
                  className="comment-input"
                  rows={3}
                />
                <button
                  type="button"
                  className="comment-submit"
                  onClick={handleAddComment}
                  disabled={!commentText.trim()}
                >
                  Comment
                </button>
              </div>
            </>
          )}
        </div>

        {/* CSS-in-JS Styles */}
        <style>{`
          .story-panel-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.4);
            z-index: 100;
            display: flex;
            justify-content: flex-end;
          }

          .story-detail-panel {
            width: 480px;
            max-width: 100%;
            height: 100%;
            background: #fff;
            box-shadow: -4px 0 24px rgba(0, 0, 0, 0.1);
            display: flex;
            flex-direction: column;
            overflow: hidden;
          }

          .panel-header {
            padding: 1.5rem;
            border-bottom: 1px solid #E5E7EB;
          }

          .header-top {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.75rem;
          }

          .story-key-badge {
            display: flex;
            align-items: center;
            gap: 0.5rem;
          }

          .type-icon {
            font-size: 1rem;
          }

          .story-key {
            font-size: 0.875rem;
            font-weight: 600;
            color: #6B7280;
          }

          .header-actions {
            display: flex;
            gap: 0.5rem;
          }

          .action-btn, .close-btn {
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #F9FAFB;
            border: 1px solid #E5E7EB;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .action-btn:hover, .close-btn:hover {
            background: #F3F4F6;
          }

          .story-title {
            margin: 0 0 0.75rem;
            font-size: 1.25rem;
            font-weight: 600;
            color: #111827;
            line-height: 1.3;
          }

          .meta-row {
            display: flex;
            gap: 0.5rem;
            flex-wrap: wrap;
          }

          .priority-badge, .points-badge, .sprint-badge {
            padding: 0.25rem 0.5rem;
            border-radius: 4px;
            font-size: 0.75rem;
            font-weight: 500;
          }

          .points-badge {
            background: #EFF6FF;
            color: #3B82F6;
          }

          .sprint-badge {
            background: #F3F4F6;
            color: #374151;
          }

          .status-bar {
            display: flex;
            padding: 0.75rem 1.5rem;
            border-bottom: 1px solid #E5E7EB;
            gap: 0.5rem;
            background: #F9FAFB;
          }

          .status-option {
            flex: 1;
            padding: 0.5rem;
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 6px;
            font-size: 0.75rem;
            font-weight: 500;
            color: #6B7280;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .status-option:hover {
            border-color: var(--status-color);
            color: var(--status-color);
          }

          .status-option--active {
            background: var(--status-color);
            border-color: var(--status-color);
            color: #fff;
          }

          .tab-nav {
            display: flex;
            padding: 0 1.5rem;
            border-bottom: 1px solid #E5E7EB;
          }

          .tab-btn {
            padding: 0.75rem 1rem;
            background: none;
            border: none;
            border-bottom: 2px solid transparent;
            font-size: 0.875rem;
            font-weight: 500;
            color: #6B7280;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .tab-btn:hover {
            color: #374151;
          }

          .tab-btn--active {
            color: #3B82F6;
            border-bottom-color: #3B82F6;
          }

          .panel-content {
            flex: 1;
            overflow-y: auto;
            padding: 1.5rem;
          }

          .section {
            margin-bottom: 1.5rem;
          }

          .section--meta {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 1rem;
          }

          .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.5rem;
          }

          .section-title {
            margin: 0 0 0.5rem;
            font-size: 0.75rem;
            font-weight: 600;
            color: #6B7280;
            text-transform: uppercase;
            letter-spacing: 0.05em;
          }

          .section-header .section-title {
            margin: 0;
          }

          .progress-text {
            font-size: 0.75rem;
            font-weight: 600;
            color: #10B981;
          }

          .progress-bar {
            height: 4px;
            background: #E5E7EB;
            border-radius: 2px;
            margin-bottom: 0.75rem;
            overflow: hidden;
          }

          .progress-fill {
            height: 100%;
            background: #10B981;
            transition: width 0.3s ease;
          }

          .description {
            margin: 0;
            font-size: 0.875rem;
            color: #374151;
            line-height: 1.6;
          }

          .assignees-list {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
          }

          .assignee {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.375rem 0.625rem;
            background: #F9FAFB;
            border-radius: 20px;
          }

          .assignee-avatar {
            width: 24px;
            height: 24px;
            border-radius: 50%;
            object-fit: cover;
          }

          .assignee-avatar--fallback {
            display: flex;
            align-items: center;
            justify-content: center;
            background: #E5E7EB;
            color: #374151;
            font-size: 0.75rem;
            font-weight: 600;
          }

          .assignee-name {
            font-size: 0.8125rem;
            color: #374151;
          }

          .empty-text {
            font-size: 0.875rem;
            color: #9CA3AF;
            font-style: italic;
          }

          .labels {
            display: flex;
            flex-wrap: wrap;
            gap: 0.375rem;
          }

          .label {
            padding: 0.25rem 0.625rem;
            border-radius: 4px;
            font-size: 0.75rem;
            font-weight: 500;
          }

          .tasks-list, .criteria-list, .resources-list {
            list-style: none;
            margin: 0;
            padding: 0;
          }

          .task-item, .criteria-item {
            padding: 0.5rem 0;
            border-bottom: 1px solid #F3F4F6;
          }

          .task-checkbox, .criteria-checkbox {
            display: flex;
            align-items: flex-start;
            gap: 0.5rem;
            cursor: pointer;
          }

          .task-checkbox input, .criteria-checkbox input {
            margin-top: 0.25rem;
          }

          .task-title, .criteria-text {
            font-size: 0.875rem;
            color: #374151;
            line-height: 1.4;
          }

          .task-title--completed, .criteria-text--completed {
            text-decoration: line-through;
            color: #9CA3AF;
          }

          .add-task-form {
            display: flex;
            gap: 0.5rem;
            margin-top: 0.75rem;
          }

          .add-task-input {
            flex: 1;
            padding: 0.5rem;
            border: 1px solid #E5E7EB;
            border-radius: 6px;
            font-size: 0.875rem;
          }

          .add-task-btn, .cancel-task-btn {
            padding: 0.5rem 0.75rem;
            font-size: 0.75rem;
            font-weight: 500;
            border: none;
            border-radius: 6px;
            cursor: pointer;
          }

          .add-task-btn {
            background: #3B82F6;
            color: #fff;
          }

          .cancel-task-btn {
            background: #F3F4F6;
            color: #374151;
          }

          .add-item-btn {
            display: block;
            width: 100%;
            padding: 0.5rem;
            margin-top: 0.75rem;
            background: #F9FAFB;
            border: 1px dashed #D1D5DB;
            border-radius: 6px;
            font-size: 0.8125rem;
            color: #6B7280;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .add-item-btn:hover {
            background: #F3F4F6;
            border-color: #9CA3AF;
          }

          .resource-item {
            margin-bottom: 0.5rem;
          }

          .resource-link {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.5rem;
            background: #F9FAFB;
            border: 1px solid #E5E7EB;
            border-radius: 6px;
            text-decoration: none;
            transition: all 0.15s ease;
          }

          .resource-link:hover {
            border-color: #3B82F6;
            background: #EFF6FF;
          }

          .resource-icon {
            font-size: 0.875rem;
          }

          .resource-title {
            flex: 1;
            font-size: 0.8125rem;
            color: #374151;
          }

          .resource-status {
            padding: 0.125rem 0.375rem;
            background: #E5E7EB;
            border-radius: 4px;
            font-size: 0.6875rem;
            color: #6B7280;
          }

          .meta-item {
            display: flex;
            flex-direction: column;
            gap: 0.25rem;
          }

          .meta-label {
            font-size: 0.6875rem;
            font-weight: 600;
            color: #6B7280;
            text-transform: uppercase;
          }

          .meta-value {
            font-size: 0.8125rem;
            color: #374151;
          }

          .activity-feed {
            margin-bottom: 1rem;
          }

          .activity-item {
            display: flex;
            gap: 0.75rem;
            padding: 0.75rem 0;
            border-bottom: 1px solid #F3F4F6;
          }

          .activity-avatar {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            object-fit: cover;
            flex-shrink: 0;
          }

          .activity-avatar--fallback {
            display: flex;
            align-items: center;
            justify-content: center;
            background: #E5E7EB;
            color: #374151;
            font-size: 0.8125rem;
            font-weight: 600;
          }

          .activity-content {
            flex: 1;
          }

          .activity-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.25rem;
          }

          .activity-user {
            font-size: 0.8125rem;
            font-weight: 600;
            color: #111827;
          }

          .activity-time {
            font-size: 0.6875rem;
            color: #9CA3AF;
          }

          .activity-text {
            margin: 0;
            font-size: 0.8125rem;
            color: #374151;
            line-height: 1.5;
          }

          .add-comment {
            padding-top: 1rem;
            border-top: 1px solid #E5E7EB;
          }

          .comment-input {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid #E5E7EB;
            border-radius: 8px;
            font-size: 0.875rem;
            resize: none;
            margin-bottom: 0.5rem;
          }

          .comment-input:focus {
            outline: none;
            border-color: #3B82F6;
          }

          .comment-submit {
            padding: 0.5rem 1rem;
            background: #3B82F6;
            color: #fff;
            border: none;
            border-radius: 6px;
            font-size: 0.8125rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .comment-submit:hover:not(:disabled) {
            background: #2563EB;
          }

          .comment-submit:disabled {
            background: #D1D5DB;
            cursor: not-allowed;
          }
        `}</style>
      </aside>
    </div>
  );
};

StoryDetailPanel.displayName = 'StoryDetailPanel';

export default StoryDetailPanel;
