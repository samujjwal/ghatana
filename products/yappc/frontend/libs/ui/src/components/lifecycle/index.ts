/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Lifecycle Components Export
 */

// Components
export { LifecycleStage, LIFECYCLE_STAGES, lifecycleStageStyles } from './LifecycleStage';
export { ProjectDashboard, projectDashboardStyles } from './ProjectDashboard';
export { TaskList, taskListStyles } from './TaskList';
export { StageNavigation, stageNavigationStyles } from './StageNavigation';

// Hooks (from parent directory)
export {
  useProjects,
  useProject,
  useTasks,
  useTask,
  usePhaseStates,
  useProjectMetrics,
} from '../../hooks/useLifecycleApi';

// Types
export type { LifecycleStageId } from './LifecycleStage';
export type { Task, ProjectMetrics } from './ProjectDashboard';
export type { TaskItem } from './TaskList';
export type { Project, Task as TaskEntity, PhaseState } from '../../hooks/useLifecycleApi';

// Combined styles for easy import
export const lifecycleStyles = `
${lifecycleStageStyles}
${projectDashboardStyles}
${taskListStyles}
${stageNavigationStyles}
`;
