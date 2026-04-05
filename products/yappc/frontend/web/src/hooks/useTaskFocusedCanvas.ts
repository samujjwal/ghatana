/**
 * Task-Focused Canvas Hook
 *
 * Manages task-centric canvas views with automatic highlighting,
 * filtering, and context loading based on URL parameters.
 *
 * @doc.type hook
 * @doc.purpose Task-focused canvas state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useProjectTasks, type ProjectTask } from './useProjectTasks';
import { useTaskURLState } from './useCanvasURLState';
import { scrollToNode } from '../utils/canvasDeepLinking';

/**
 * Task focus state
 */
export interface TaskFocusState {
  /** Currently focused task */
  focusedTask: ProjectTask | null;
  /** Related tasks (dependencies, subtasks) */
  relatedTasks: ProjectTask[];
  /** Task-related node IDs on canvas */
  taskNodeIds: string[];
  /** Whether task is currently highlighted */
  isHighlighted: boolean;
}

/**
 * Hook return type
 */
export interface UseTaskFocusedCanvasReturn extends TaskFocusState {
  /** Focus on a specific task */
  focusTask: (taskId: string) => void;
  /** Clear task focus */
  clearFocus: () => void;
  /** Highlight task on canvas */
  highlightTask: (taskId: string) => void;
  /** Get tasks filtered by current context */
  getFilteredTasks: () => ProjectTask[];
  /** Check if task is in current focus */
  isTaskInFocus: (taskId: string) => boolean;
}

/**
 * Custom hook for task-focused canvas management
 *
 * @doc.param projectId - Project identifier
 * @doc.returns Task-focused canvas interface
 *
 * @example
 * ```tsx
 * const { focusedTask, focusTask, highlightTask } = useTaskFocusedCanvas('project-123');
 *
 * // Focus on task from URL
 * useEffect(() => {
 *   if (urlTaskId) {
 *     focusTask(urlTaskId);
 *   }
 * }, [urlTaskId]);
 * ```
 */
export function useTaskFocusedCanvas(
  projectId: string
): UseTaskFocusedCanvasReturn {
  const { taskId: urlTaskId } = useTaskURLState();
  const { tasks, getTask } = useProjectTasks(projectId);

  const [focusedTask, setFocusedTask] = useState<ProjectTask | null>(null);
  const [isHighlighted, setIsHighlighted] = useState(false);

  // Get related tasks (dependencies and subtasks)
  const relatedTasks = useMemo(() => {
    if (!focusedTask) return [];

    const related: ProjectTask[] = [];

    // Add dependency tasks
    focusedTask.dependencies.forEach((depId) => {
      const depTask = getTask(depId);
      if (depTask) {
        related.push(depTask);
      }
    });

    // Add tasks that depend on this task
    tasks.forEach((task) => {
      if (task.dependencies.includes(focusedTask.id)) {
        related.push(task);
      }
    });

    return related;
  }, [focusedTask, tasks, getTask]);

  // Get task-related node IDs
  const taskNodeIds = useMemo(() => {
    if (!focusedTask) return [];

    const nodeIds: string[] = [];

    // Add task node itself
    nodeIds.push(`task-${focusedTask.id}`);

    // Add related task nodes
    relatedTasks.forEach((task) => {
      nodeIds.push(`task-${task.id}`);
    });

    // Add artifact nodes related to task
    if (focusedTask.tags) {
      focusedTask.tags.forEach((tag) => {
        nodeIds.push(`artifact-${tag}`);
      });
    }

    return nodeIds;
  }, [focusedTask, relatedTasks]);

  /**
   * Focus on a specific task
   */
  const focusTask = useCallback(
    (taskId: string) => {
      const task = getTask(taskId);
      if (task) {
        setFocusedTask(task);
        setIsHighlighted(true);

        // Scroll to task node on canvas
        const taskNodeId = `task-${taskId}`;
        scrollToNode(taskNodeId);

        // Log telemetry
        console.log('[Canvas] Task focused:', {
          taskId,
          taskName: task.name,
          phase: task.phase,
        });
      } else {
        console.warn('[Canvas] Task not found:', taskId);
      }
    },
    [getTask]
  );

  /**
   * Clear task focus
   */
  const clearFocus = useCallback(() => {
    setFocusedTask(null);
    setIsHighlighted(false);
  }, []);

  /**
   * Highlight task on canvas
   */
  const highlightTask = useCallback((taskId: string) => {
    const taskNodeId = `task-${taskId}`;
    scrollToNode(taskNodeId);
    setIsHighlighted(true);

    // Auto-clear highlight after 3 seconds
    setTimeout(() => {
      setIsHighlighted(false);
    }, 3000);
  }, []);

  /**
   * Get tasks filtered by current context
   */
  const getFilteredTasks = useCallback(() => {
    if (!focusedTask) return tasks;

    // Filter tasks by same phase or related
    return tasks.filter((task) => {
      return (
        task.phase === focusedTask.phase ||
        task.dependencies.includes(focusedTask.id) ||
        focusedTask.dependencies.includes(task.id)
      );
    });
  }, [focusedTask, tasks]);

  /**
   * Check if task is in current focus
   */
  const isTaskInFocus = useCallback(
    (taskId: string) => {
      if (!focusedTask) return false;
      return (
        focusedTask.id === taskId ||
        relatedTasks.some((task) => task.id === taskId)
      );
    },
    [focusedTask, relatedTasks]
  );

  // Auto-focus task from URL
  useEffect(() => {
    if (urlTaskId) {
      focusTask(urlTaskId);
    }
  }, [urlTaskId, focusTask]);

  return {
    focusedTask,
    relatedTasks,
    taskNodeIds,
    isHighlighted,
    focusTask,
    clearFocus,
    highlightTask,
    getFilteredTasks,
    isTaskInFocus,
  };
}

/**
 * Hook for task-based canvas filtering
 *
 * @doc.param projectId - Project identifier
 * @doc.returns Task filtering interface
 */
export function useTaskCanvasFilter(projectId: string) {
  const { focusedTask, relatedTasks, isTaskInFocus } =
    useTaskFocusedCanvas(projectId);

  /**
   * Filter canvas nodes by task context
   */
  const filterNodesByTask = useCallback(
    (nodes: Array<{ id: string; data?: Record<string, unknown> }>) => {
      if (!focusedTask) return nodes;

      return nodes.filter((node) => {
        // Show task-related nodes
        if (node.id.startsWith('task-')) {
          const taskId = node.id.replace('task-', '');
          return isTaskInFocus(taskId);
        }

        // Show artifact nodes related to task
        if (node.id.startsWith('artifact-') && focusedTask.tags) {
          const artifactTag = node.id.replace('artifact-', '');
          return focusedTask.tags.includes(artifactTag);
        }

        // Show all other nodes by default
        return true;
      });
    },
    [focusedTask, isTaskInFocus]
  );

  /**
   * Get node opacity based on task focus
   */
  const getNodeOpacity = useCallback(
    (nodeId: string): number => {
      if (!focusedTask) return 1;

      // Full opacity for focused nodes
      if (nodeId.startsWith('task-')) {
        const taskId = nodeId.replace('task-', '');
        if (isTaskInFocus(taskId)) return 1;
      }

      // Reduced opacity for non-focused nodes
      return 0.3;
    },
    [focusedTask, isTaskInFocus]
  );

  return {
    focusedTask,
    relatedTasks,
    filterNodesByTask,
    getNodeOpacity,
    hasTaskFocus: !!focusedTask,
  };
}
