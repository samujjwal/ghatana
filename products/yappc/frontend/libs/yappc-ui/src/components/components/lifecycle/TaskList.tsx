/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Task List Component
 */

import React, { useState, useMemo } from 'react';
import { LifecycleStageId } from './LifecycleStage';

export interface TaskItem {
  id: string;
  title: string;
  description: string;
  status: 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  stage: LifecycleStageId;
  assignedAgentId?: string;
  requiredCapabilities: string[];
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  deadlineAt?: string;
  retryCount: number;
  maxRetries: number;
  labels: Record<string, string>;
}

export interface TaskListProps {
  tasks: TaskItem[];
  onTaskClick: (taskId: string) => void;
  onTaskAction?: (taskId: string, action: 'start' | 'retry' | 'cancel') => void;
  className?: string;
}

type FilterStatus = TaskItem['status'] | 'ALL';
type FilterPriority = TaskItem['priority'] | 'ALL';
type SortField = 'createdAt' | 'priority' | 'status' | 'stage';
type SortOrder = 'asc' | 'desc';

/**
 * TaskList Component
 * 
 * Comprehensive task list with filtering, sorting, and action capabilities.
 * Shows task status, priority, assigned agent, and lifecycle stage.
 * 
 * @example
 * ```tsx
 * <TaskList
 *   tasks={tasks}
 *   onTaskClick={(id) => openTaskDetail(id)}
 *   onTaskAction={(id, action) => handleTaskAction(id, action)}
 * />
 * ```
 */
export function TaskList({
  tasks,
  onTaskClick,
  onTaskAction,
  className = '',
}: TaskListProps) {
  // Filter states
  const [statusFilter, setStatusFilter] = useState<FilterStatus>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<FilterPriority>('ALL');
  const [stageFilter, setStageFilter] = useState<LifecycleStageId | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  
  // Sort states
  const [sortField, setSortField] = useState<SortField>('createdAt');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');

  // Filter and sort tasks
  const filteredTasks = useMemo(() => {
    let result = tasks;

    // Apply filters
    if (statusFilter !== 'ALL') {
      result = result.filter(t => t.status === statusFilter);
    }
    if (priorityFilter !== 'ALL') {
      result = result.filter(t => t.priority === priorityFilter);
    }
    if (stageFilter !== 'ALL') {
      result = result.filter(t => t.stage === stageFilter);
    }
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(t => 
        t.title.toLowerCase().includes(query) ||
        t.description.toLowerCase().includes(query) ||
        t.assignedAgentId?.toLowerCase().includes(query)
      );
    }

    // Apply sorting
    result = [...result].sort((a, b) => {
      let comparison = 0;
      
      switch (sortField) {
        case 'createdAt':
          comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
          break;
        case 'priority':
          const priorityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
          comparison = priorityOrder[a.priority] - priorityOrder[b.priority];
          break;
        case 'status':
          const statusOrder = { IN_PROGRESS: 0, ASSIGNED: 1, PENDING: 2, COMPLETED: 3, FAILED: 4, CANCELLED: 5 };
          comparison = statusOrder[a.status] - statusOrder[b.status];
          break;
        case 'stage':
          const stageOrder = ['intent', 'context', 'plan', 'execute', 'verify', 'observe', 'learn', 'institutionalize'];
          comparison = stageOrder.indexOf(a.stage) - stageOrder.indexOf(b.stage);
          break;
      }
      
      return sortOrder === 'asc' ? comparison : -comparison;
    });

    return result;
  }, [tasks, statusFilter, priorityFilter, stageFilter, searchQuery, sortField, sortOrder]);

  // Stats
  const stats = useMemo(() => ({
    total: tasks.length,
    pending: tasks.filter(t => t.status === 'PENDING').length,
    inProgress: tasks.filter(t => t.status === 'IN_PROGRESS').length,
    completed: tasks.filter(t => t.status === 'COMPLETED').length,
    failed: tasks.filter(t => t.status === 'FAILED').length,
  }), [tasks]);

  const toggleSort = (field: SortField) => {
    if (sortField === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortOrder('asc');
    }
  };

  return (
    <div className={`task-list ${className}`}>
      {/* Stats bar */}
      <div className="task-list__stats">
        <StatBadge count={stats.total} label="Total" />
        <StatBadge count={stats.pending} label="Pending" color="#f59e0b" />
        <StatBadge count={stats.inProgress} label="In Progress" color="#3b82f6" />
        <StatBadge count={stats.completed} label="Completed" color="#22c55e" />
        <StatBadge count={stats.failed} label="Failed" color={stats.failed > 0 ? '#ef4444' : '#6b7280'} />
      </div>

      {/* Filters */}
      <div className="task-list__filters">
        <input
          type="search"
          placeholder="Search tasks..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="task-list__search"
        />
        
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as FilterStatus)}
          className="task-list__filter"
        >
          <option value="ALL">All Status</option>
          <option value="PENDING">Pending</option>
          <option value="ASSIGNED">Assigned</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>

        <select
          value={priorityFilter}
          onChange={(e) => setPriorityFilter(e.target.value as FilterPriority)}
          className="task-list__filter"
        >
          <option value="ALL">All Priorities</option>
          <option value="CRITICAL">Critical</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
      </div>

      {/* Results count */}
      <div className="task-list__results">
        Showing {filteredTasks.length} of {tasks.length} tasks
      </div>

      {/* Task table */}
      <div className="task-list__table-container">
        <table className="task-list__table">
          <thead>
            <tr>
              <th onClick={() => toggleSort('status')} className="task-list__th--sortable">
                Status {sortField === 'status' && (sortOrder === 'asc' ? '↑' : '↓')}
              </th>
              <th onClick={() => toggleSort('priority')} className="task-list__th--sortable">
                Priority {sortField === 'priority' && (sortOrder === 'asc' ? '↑' : '↓')}
              </th>
              <th>Task</th>
              <th onClick={() => toggleSort('stage')} className="task-list__th--sortable">
                Stage {sortField === 'stage' && (sortOrder === 'asc' ? '↑' : '↓')}
              </th>
              <th>Assigned To</th>
              <th>Capabilities</th>
              <th onClick={() => toggleSort('createdAt')} className="task-list__th--sortable">
                Created {sortField === 'createdAt' && (sortOrder === 'asc' ? '↑' : '↓')}
              </th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredTasks.map(task => (
              <TaskRow
                key={task.id}
                task={task}
                onClick={() => onTaskClick(task.id)}
                onAction={onTaskAction}
              />
            ))}
          </tbody>
        </table>

        {filteredTasks.length === 0 && (
          <div className="task-list__empty">
            No tasks match the current filters.
          </div>
        )}
      </div>
    </div>
  );
}

// Stat Badge Component
function StatBadge({ count, label, color = '#6b7280' }: { count: number; label: string; color?: string }) {
  return (
    <div className="stat-badge">
      <span className="stat-badge__count" style={{ color }}>{count}</span>
      <span className="stat-badge__label">{label}</span>
    </div>
  );
}

// Task Row Component
interface TaskRowProps {
  task: TaskItem;
  onClick: () => void;
  onAction?: (taskId: string, action: 'start' | 'retry' | 'cancel') => void;
}

function TaskRow({ task, onClick, onAction }: TaskRowProps) {
  const statusConfig = {
    PENDING: { color: '#f59e0b', bg: '#fef3c7', label: 'Pending' },
    ASSIGNED: { color: '#8b5cf6', bg: '#ede9fe', label: 'Assigned' },
    IN_PROGRESS: { color: '#3b82f6', bg: '#dbeafe', label: 'In Progress' },
    COMPLETED: { color: '#22c55e', bg: '#dcfce7', label: 'Completed' },
    FAILED: { color: '#ef4444', bg: '#fee2e2', label: 'Failed' },
    CANCELLED: { color: '#6b7280', bg: '#f3f4f6', label: 'Cancelled' },
  };

  const priorityConfig = {
    CRITICAL: { color: '#ef4444', icon: '🔴' },
    HIGH: { color: '#f59e0b', icon: '🟠' },
    MEDIUM: { color: '#3b82f6', icon: '🔵' },
    LOW: { color: '#6b7280', icon: '⚪' },
  };

  const status = statusConfig[task.status];
  const priority = priorityConfig[task.priority];
  const canRetry = task.status === 'FAILED' && task.retryCount < task.maxRetries;

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
    });
  };

  return (
    <tr className="task-row" onClick={onClick}>
      <td>
        <span 
          className="task-row__status"
          style={{ color: status.color, background: status.bg }}
        >
          {status.label}
        </span>
      </td>
      <td>
        <span className="task-row__priority" style={{ color: priority.color }}>
          {priority.icon} {task.priority}
        </span>
      </td>
      <td className="task-row__title-cell">
        <div className="task-row__title">{task.title}</div>
        <div className="task-row__description">{task.description}</div>
        {task.retryCount > 0 && (
          <div className="task-row__retry">
            Retry {task.retryCount}/{task.maxRetries}
          </div>
        )}
      </td>
      <td>
        <span className="task-row__stage">{task.stage}</span>
      </td>
      <td className="task-row__agent">
        {task.assignedAgentId 
          ? task.assignedAgentId.split('.').pop() 
          : '—'}
      </td>
      <td>
        <div className="task-row__capabilities">
          {task.requiredCapabilities.slice(0, 2).map(cap => (
            <span key={cap} className="task-row__capability">{cap}</span>
          ))}
          {task.requiredCapabilities.length > 2 && (
            <span className="task-row__capability--more">
              +{task.requiredCapabilities.length - 2}
            </span>
          )}
        </div>
      </td>
      <td className="task-row__date">{formatDate(task.createdAt)}</td>
      <td className="task-row__actions" onClick={(e) => e.stopPropagation()}>
        {task.status === 'PENDING' && onAction && (
          <button 
            className="task-row__action-btn task-row__action-btn--start"
            onClick={() => onAction(task.id, 'start')}
          >
            Start
          </button>
        )}
        {canRetry && onAction && (
          <button 
            className="task-row__action-btn task-row__action-btn--retry"
            onClick={() => onAction(task.id, 'retry')}
          >
            Retry
          </button>
        )}
        {(task.status === 'PENDING' || task.status === 'ASSIGNED') && onAction && (
          <button 
            className="task-row__action-btn task-row__action-btn--cancel"
            onClick={() => onAction(task.id, 'cancel')}
          >
            Cancel
          </button>
        )}
      </td>
    </tr>
  );
}

// CSS styles
export const taskListStyles = `
.task-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.task-list__stats {
  display: flex;
  gap: 1.5rem;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 0.5rem;
}

.stat-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
}

.stat-badge__count {
  font-size: 1.5rem;
  font-weight: 700;
}

.stat-badge__label {
  font-size: 0.75rem;
  color: #6b7280;
  text-transform: uppercase;
}

.task-list__filters {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.task-list__search {
  flex: 1;
  min-width: 200px;
  padding: 0.5rem 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  font-size: 0.875rem;
}

.task-list__filter {
  padding: 0.5rem 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  background: white;
}

.task-list__results {
  font-size: 0.875rem;
  color: #6b7280;
}

.task-list__table-container {
  overflow-x: auto;
}

.task-list__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.task-list__table th {
  text-align: left;
  padding: 0.75rem;
  color: #374151;
  font-weight: 600;
  border-bottom: 2px solid #e5e7eb;
  white-space: nowrap;
}

.task-list__th--sortable {
  cursor: pointer;
  user-select: none;
}

.task-list__th--sortable:hover {
  background: #f9fafb;
}

.task-list__table td {
  padding: 0.75rem;
  border-bottom: 1px solid #f3f4f6;
}

.task-row {
  cursor: pointer;
  transition: background 0.15s;
}

.task-row:hover {
  background: #f9fafb;
}

.task-row__status {
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.task-row__priority {
  font-weight: 500;
  white-space: nowrap;
}

.task-row__title-cell {
  min-width: 200px;
}

.task-row__title {
  font-weight: 500;
  color: #374151;
}

.task-row__description {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

.task-row__retry {
  font-size: 0.75rem;
  color: #f59e0b;
  margin-top: 0.25rem;
}

.task-row__stage {
  padding: 0.125rem 0.5rem;
  background: #e5e7eb;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  text-transform: capitalize;
}

.task-row__agent {
  color: #6b7280;
  font-size: 0.75rem;
}

.task-row__capabilities {
  display: flex;
  gap: 0.25rem;
  flex-wrap: wrap;
}

.task-row__capability {
  padding: 0.125rem 0.375rem;
  background: #dbeafe;
  color: #1e40af;
  border-radius: 0.25rem;
  font-size: 0.625rem;
}

.task-row__capability--more {
  padding: 0.125rem 0.375rem;
  background: #f3f4f6;
  color: #6b7280;
  border-radius: 0.25rem;
  font-size: 0.625rem;
}

.task-row__date {
  color: #6b7280;
  font-size: 0.75rem;
  white-space: nowrap;
}

.task-row__actions {
  display: flex;
  gap: 0.5rem;
}

.task-row__action-btn {
  padding: 0.25rem 0.75rem;
  border: none;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.task-row__action-btn:hover {
  opacity: 0.8;
}

.task-row__action-btn--start {
  background: #22c55e;
  color: white;
}

.task-row__action-btn--retry {
  background: #f59e0b;
  color: white;
}

.task-row__action-btn--cancel {
  background: #ef4444;
  color: white;
}

.task-list__empty {
  text-align: center;
  padding: 3rem;
  color: #9ca3af;
}
`;
