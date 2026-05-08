// Configuration hooks
export * from './hooks/useConfig';

export {
  configQueryKeys,
  usePersonas,
  usePersona,
  useDomains,
  useDomain,
  useTemplates,
  useTemplate,
  useTasks,
  useTask,
} from 'yappc-state/config-hooks';
export type {
  PersonaConfig,
  TaskTemplate,
  TaskData,
  WorkflowConfig,
} from 'yappc-state/config-hooks';

// Configuration components
export { DomainSelector } from './components/config/DomainSelector';
export { WorkflowRenderer } from './components/config/WorkflowRenderer';
export { TaskListView } from './components/config/TaskListView';
