/**
 * Task Dependency Graph Component
 *
 * Visualizes task dependencies and their relationships.
 * Helps users understand task blocking and completion order.
 *
 * @doc.type component
 * @doc.purpose Task dependency visualization
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useMemo } from 'react';
import { ArrowRight as ArrowIcon, Lock as BlockedIcon, CheckCircle as CompletedIcon, Circle as PendingIcon } from 'lucide-react';
import { Typography, Box, Card, CardContent, Chip } from '@ghatana/design-system';
import type { Task } from '../../services/ai/TaskPrioritizationService';

// ============================================================================
// Types
// ============================================================================

export interface TaskDependencyGraphProps {
  tasks: Task[];
  selectedTaskId?: string;
  onTaskClick?: (taskId: string) => void;
  showStatus?: boolean;
  maxDepth?: number;
  className?: string;
}

// ============================================================================
// Graph Node Component
// ============================================================================

interface GraphNodeProps {
  task: Task;
  isCompleted: boolean;
  isBlocked: boolean;
  canStart: boolean;
  isSelected: boolean;
  onClick: () => void;
  showStatus: boolean;
}

function GraphNode({
  task,
  isCompleted,
  isBlocked,
  canStart,
  isSelected,
  onClick,
  showStatus,
}: GraphNodeProps): ReactNode {
  const getStatusIcon = () => {
    if (isCompleted) return <CompletedIcon className="w-4 h-4 text-green-600" />;
    if (isBlocked) return <BlockedIcon className="w-4 h-4 text-red-600" />;
    if (!canStart) return <BlockedIcon className="w-4 h-4 text-orange-600" />;
    return <PendingIcon className="w-4 h-4 text-blue-600" />;
  };

  const getStatusColor = () => {
    if (isCompleted) return 'border-green-300 dark:border-green-700 bg-green-50 dark:bg-green-900/20';
    if (isBlocked) return 'border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/20';
    if (!canStart) return 'border-orange-300 dark:border-orange-700 bg-orange-50 dark:bg-orange-900/20';
    return 'border-blue-300 dark:border-blue-700 bg-blue-50 dark:bg-blue-900/20';
  };

  return (
    <div
      onClick={onClick}
      className={`p-3 rounded-lg border-2 cursor-pointer transition-all ${
        isSelected ? 'ring-2 ring-blue-500' : 'hover:shadow-md'
      } ${getStatusColor()}`}
    >
      <div className="flex items-center gap-2 mb-1">
        {showStatus && getStatusIcon()}
        <Typography className="font-medium text-sm truncate">
          {task.title}
        </Typography>
      </div>
      <div className="flex items-center gap-2">
        <Chip size="sm" label={task.type} className="text-xs" />
        <Chip size="sm" label={task.priority} className="text-xs" />
      </div>
      {task.dueDate && (
        <Typography className="text-xs text-gray-500 mt-1">
          Due: {new Date(task.dueDate).toLocaleDateString()}
        </Typography>
      )}
    </div>
  );
}

// ============================================================================
// Task Dependency Graph Component
// ============================================================================

/**
 * Task Dependency Graph Component
 */
export function TaskDependencyGraph({
  tasks,
  selectedTaskId,
  onTaskClick,
  showStatus = true,
  maxDepth = 3,
  className = '',
}: TaskDependencyGraphProps): ReactNode {
  // Build dependency graph
  const { taskLevels, dependencies } = useMemo(() => {
    const taskMap = new Map(tasks.map(t => [t.id, t]));
    const taskLevels: Map<string, number> = new Map();
    const dependencies: Map<string, string[]> = new Map();

    // Initialize all tasks at level 0
    tasks.forEach(task => {
      taskLevels.set(task.id, 0);
      dependencies.set(task.id, task.dependencies || []);
    });

    // Calculate levels using topological sort
    const visited = new Set<string>();
    const calculateLevel = (taskId: string, depth: number = 0): number => {
      if (depth > maxDepth) return 0;
      if (visited.has(taskId)) return taskLevels.get(taskId) || 0;

      visited.add(taskId);
      const task = taskMap.get(taskId);
      if (!task) return 0;

      const deps = task.dependencies || [];
      let maxDepLevel = 0;

      deps.forEach(depId => {
        const depLevel = calculateLevel(depId, depth + 1);
        maxDepLevel = Math.max(maxDepLevel, depLevel);
      });

      const level = maxDepLevel + 1;
      taskLevels.set(taskId, level);
      return level;
    };

    tasks.forEach(task => calculateLevel(task.id));

    return { taskLevels, dependencies };
  }, [tasks, maxDepth]);

  // Group tasks by level
  const levels = useMemo(() => {
    const grouped: Map<number, Task[]> = new Map();

    tasks.forEach(task => {
      const level = taskLevels.get(task.id) || 0;
      if (!grouped.has(level)) {
        grouped.set(level, []);
      }
      grouped.get(level)!.push(task);
    });

    return grouped;
  }, [tasks, taskLevels]);

  // Check if task can be started
  const canStartTask = (task: Task): boolean => {
    const deps = dependencies.get(task.id) || [];
    return deps.every(depId => {
      const depTask = tasks.find(t => t.id === depId);
      return depTask?.status === 'completed';
    });
  };

  const isCompleted = (task: Task): boolean => task.status === 'completed';
  const isBlocked = (task: Task): boolean => task.status === 'blocked';

  return (
    <div className={`space-y-4 ${className}`}>
      {/* Legend */}
      <Box className="flex items-center gap-4 text-xs text-gray-500">
        <div className="flex items-center gap-1">
          <CompletedIcon className="w-4 h-4 text-green-600" />
          <span>Completed</span>
        </div>
        <div className="flex items-center gap-1">
          <BlockedIcon className="w-4 h-4 text-orange-600" />
          <span>Waiting for dependencies</span>
        </div>
        <div className="flex items-center gap-1">
          <BlockedIcon className="w-4 h-4 text-red-600" />
          <span>Blocked</span>
        </div>
        <div className="flex items-center gap-1">
          <PendingIcon className="w-4 h-4 text-blue-600" />
          <span>Ready to start</span>
        </div>
      </Box>

      {/* Graph Levels */}
      {Array.from(levels.entries())
        .sort(([a], [b]) => a - b)
        .map(([level, levelTasks]) => (
          <div key={level} className="space-y-2">
            <Typography className="text-xs font-medium text-gray-500">
              Level {level}
            </Typography>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
              {levelTasks.map(task => (
                <GraphNode
                  key={task.id}
                  task={task}
                  isCompleted={isCompleted(task)}
                  isBlocked={isBlocked(task)}
                  canStart={canStartTask(task)}
                  isSelected={selectedTaskId === task.id}
                  onClick={() => onTaskClick?.(task.id)}
                  showStatus={showStatus}
                />
              ))}
            </div>
            {level < levels.size - 1 && (
              <div className="flex justify-center py-2">
                <ArrowIcon className="w-5 h-5 text-gray-400" />
              </div>
            )}
          </div>
        ))}
    </div>
  );
}
