/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Project Dashboard Component
 */

import { LifecycleStage, LifecycleStageId } from './LifecycleStage';

export interface Task {
  id: string;
  title: string;
  description: string;
  status: 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  stage: LifecycleStageId;
  assignedAgentId?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface ProjectMetrics {
  totalTasks: number;
  completedTasks: number;
  inProgressTasks: number;
  failedTasks: number;
  averageTaskDuration: number; // seconds
  phaseTransitionCount: number;
}

export interface ProjectDashboardProps {
  projectId: string;
  projectName: string;
  description: string;
  currentStage: LifecycleStageId;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
  startedAt: string;
  lastActivityAt: string;
  tasks: Task[];
  metrics: ProjectMetrics;
  onStageChange: (stage: LifecycleStageId) => void;
  onTaskClick: (taskId: string) => void;
  onCreateTask: () => void;
  className?: string;
}

/**
 * ProjectDashboard Component
 * 
 * Main project view showing lifecycle stage, task list, and key metrics.
 * Provides navigation between stages and task management.
 * 
 * @example
 * ```tsx
 * <ProjectDashboard
 *   projectId="proj-123"
 *   projectName="New Feature"
 *   currentStage="execute"
 *   status="ACTIVE"
 *   tasks={[...]}
 *   metrics={{ totalTasks: 10, completedTasks: 5, ... }}
 *   onStageChange={(stage) => navigateToStage(stage)}
 *   onTaskClick={(id) => openTask(id)}
 * />
 * ```
 */
export function ProjectDashboard({
  projectId,
  projectName,
  description,
  currentStage,
  status,
  startedAt,
  lastActivityAt,
  tasks,
  metrics,
  onStageChange,
  onTaskClick,
  onCreateTask,
  className = '',
}: ProjectDashboardProps) {
  const statusColors = {
    ACTIVE: '#22c55e',
    PAUSED: '#f59e0b',
    COMPLETED: '#3b82f6',
    ARCHIVED: '#6b7280',
  };

  const formatDuration = (seconds: number) => {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    if (days > 0) return `${days}d ${hours}h`;
    return `${hours}h`;
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  return (
    <div className={`project-dashboard ${className}`}>
      {/* Header */}
      <header className="project-dashboard__header">
        <div className="project-dashboard__title-section">
          <h1 className="project-dashboard__title">{projectName}</h1>
          <span 
            className="project-dashboard__status"
            style={{ color: statusColors[status], borderColor: statusColors[status] }}
          >
            {status}
          </span>
        </div>
        <p className="project-dashboard__description">{description}</p>
        <div className="project-dashboard__meta">
          <span>Started: {formatDate(startedAt)}</span>
          <span>Last activity: {formatDate(lastActivityAt)}</span>
          <span>ID: {projectId}</span>
        </div>
      </header>

      {/* Lifecycle Stage */}
      <section className="project-dashboard__lifecycle">
        <h2 className="project-dashboard__section-title">Lifecycle Stage</h2>
        <LifecycleStage 
          currentStage={currentStage}
          onStageClick={onStageChange}
        />
      </section>

      {/* Metrics */}
      <section className="project-dashboard__metrics">
        <h2 className="project-dashboard__section-title">Metrics</h2>
        <div className="project-dashboard__metrics-grid">
          <MetricCard 
            label="Total Tasks"
            value={metrics.totalTasks}
            icon="📋"
          />
          <MetricCard 
            label="Completed"
            value={metrics.completedTasks}
            percentage={Math.round((metrics.completedTasks / metrics.totalTasks) * 100)}
            color="#22c55e"
            icon="✅"
          />
          <MetricCard 
            label="In Progress"
            value={metrics.inProgressTasks}
            color="#3b82f6"
            icon="🔨"
          />
          <MetricCard 
            label="Failed"
            value={metrics.failedTasks}
            color={metrics.failedTasks > 0 ? '#ef4444' : '#6b7280'}
            icon="❌"
          />
          <MetricCard 
            label="Avg Duration"
            value={formatDuration(metrics.averageTaskDuration)}
            icon="⏱️"
          />
          <MetricCard 
            label="Phase Transitions"
            value={metrics.phaseTransitionCount}
            icon="🔄"
          />
        </div>
      </section>

      {/* Task Summary */}
      <section className="project-dashboard__tasks">
        <div className="project-dashboard__tasks-header">
          <h2 className="project-dashboard__section-title">Tasks</h2>
          <button 
            className="project-dashboard__create-btn"
            onClick={onCreateTask}
          >
            + New Task
          </button>
        </div>
        
        <TaskSummaryTable 
          tasks={tasks.slice(0, 10)}
          onTaskClick={onTaskClick}
        />
        
        {tasks.length > 10 && (
          <p className="project-dashboard__tasks-more">
            +{tasks.length - 10} more tasks
          </p>
        )}
      </section>
    </div>
  );
}

// Metric Card Component
interface MetricCardProps {
  label: string;
  value: string | number;
  percentage?: number;
  color?: string;
  icon: string;
}

function MetricCard({ label, value, percentage, color, icon }: MetricCardProps) {
  return (
    <div className="metric-card">
      <div className="metric-card__icon">{icon}</div>
      <div className="metric-card__content">
        <span className="metric-card__value" style={{ color }}>{value}</span>
        <span className="metric-card__label">{label}</span>
        {percentage !== undefined && (
          <div className="metric-card__bar">
            <div 
              className="metric-card__bar-fill"
              style={{ width: `${percentage}%`, background: color }}
            />
          </div>
        )}
      </div>
    </div>
  );
}

// Task Summary Table
interface TaskSummaryTableProps {
  tasks: Task[];
  onTaskClick: (taskId: string) => void;
}

function TaskSummaryTable({ tasks, onTaskClick }: TaskSummaryTableProps) {
  const statusIcons = {
    PENDING: '⏳',
    ASSIGNED: '👤',
    IN_PROGRESS: '🔨',
    COMPLETED: '✅',
    FAILED: '❌',
    CANCELLED: '🚫',
  };

  const priorityColors = {
    LOW: '#6b7280',
    MEDIUM: '#3b82f6',
    HIGH: '#f59e0b',
    CRITICAL: '#ef4444',
  };

  if (tasks.length === 0) {
    return (
      <div className="task-summary-table--empty">
        No tasks yet. Create your first task to get started.
      </div>
    );
  }

  return (
    <table className="task-summary-table">
      <thead>
        <tr>
          <th>Status</th>
          <th>Task</th>
          <th>Stage</th>
          <th>Priority</th>
          <th>Assigned To</th>
        </tr>
      </thead>
      <tbody>
        {tasks.map(task => (
          <tr 
            key={task.id}
            onClick={() => onTaskClick(task.id)}
            className="task-summary-table__row"
          >
            <td>
              <span title={task.status}>
                {statusIcons[task.status]}
              </span>
            </td>
            <td className="task-summary-table__title">{task.title}</td>
            <td>
              <span className="task-summary-table__stage">{task.stage}</span>
            </td>
            <td>
              <span 
                className="task-summary-table__priority"
                style={{ color: priorityColors[task.priority] }}
              >
                {task.priority}
              </span>
            </td>
            <td className="task-summary-table__agent">
              {task.assignedAgentId 
                ? task.assignedAgentId.split('.').pop() 
                : '—'}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// CSS styles
export const projectDashboardStyles = `
.project-dashboard {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  padding: 1.5rem;
  max-width: 1200px;
  margin: 0 auto;
}

.project-dashboard__header {
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 1rem;
}

.project-dashboard__title-section {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.project-dashboard__title {
  font-size: 1.875rem;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.project-dashboard__status {
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  border: 2px solid;
}

.project-dashboard__description {
  color: #6b7280;
  margin: 0.5rem 0;
}

.project-dashboard__meta {
  display: flex;
  gap: 1.5rem;
  font-size: 0.875rem;
  color: #9ca3af;
}

.project-dashboard__section-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 1rem;
}

.project-dashboard__metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 0.5rem;
  border: 1px solid #e5e7eb;
}

.metric-card__icon {
  font-size: 1.5rem;
}

.metric-card__content {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.metric-card__value {
  font-size: 1.5rem;
  font-weight: 700;
}

.metric-card__label {
  font-size: 0.75rem;
  color: #6b7280;
  text-transform: uppercase;
}

.metric-card__bar {
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  margin-top: 0.5rem;
  overflow: hidden;
}

.metric-card__bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.project-dashboard__tasks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.project-dashboard__create-btn {
  padding: 0.5rem 1rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 0.375rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.project-dashboard__create-btn:hover {
  background: #2563eb;
}

.task-summary-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.task-summary-table th {
  text-align: left;
  padding: 0.75rem;
  color: #6b7280;
  font-weight: 500;
  text-transform: uppercase;
  font-size: 0.75rem;
  border-bottom: 1px solid #e5e7eb;
}

.task-summary-table td {
  padding: 0.75rem;
  border-bottom: 1px solid #f3f4f6;
}

.task-summary-table__row {
  cursor: pointer;
  transition: background 0.15s;
}

.task-summary-table__row:hover {
  background: #f9fafb;
}

.task-summary-table__title {
  font-weight: 500;
  color: #374151;
}

.task-summary-table__stage {
  padding: 0.125rem 0.5rem;
  background: #e5e7eb;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  text-transform: capitalize;
}

.task-summary-table__priority {
  font-weight: 500;
}

.task-summary-table__agent {
  color: #6b7280;
  font-size: 0.75rem;
}

.task-summary-table--empty {
  text-align: center;
  padding: 2rem;
  color: #9ca3af;
  background: #f9fafb;
  border-radius: 0.5rem;
}

.project-dashboard__tasks-more {
  text-align: center;
  color: #6b7280;
  font-size: 0.875rem;
  margin-top: 1rem;
}
`;
