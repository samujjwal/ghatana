import { atom } from 'jotai';

import type {
  TaskDefinition,
  TaskDomain,
  WorkflowDefinition,
} from 'yappc-core/types/tasks';

export interface TaskRegistryStats {
  totalTasks: number;
  totalDomains: number;
  totalWorkflows: number;
  totalStages: number;
}

export const allDomainsAtom = atom<TaskDomain[]>([]);
export const tasksByDomainAtom = atom<Record<string, TaskDefinition[]>>({});
export const filteredTasksAtom = atom<TaskDefinition[]>([]);
export const allWorkflowsAtom = atom<WorkflowDefinition[]>([]);
export const workflowByIdAtom = atom<Record<string, WorkflowDefinition>>({});
export const workflowsByCategoryAtom = atom<Record<string, WorkflowDefinition[]>>({
  discovery: [],
  delivery: [],
  maintenance: [],
  quality: [],
  operations: [],
  security: [],
  documentation: [],
});
export const taskByIdAtom = atom<Record<string, TaskDefinition>>({});
export const registryStatsAtom = atom<TaskRegistryStats>({
  totalTasks: 0,
  totalDomains: 0,
  totalWorkflows: 0,
  totalStages: 0,
});
