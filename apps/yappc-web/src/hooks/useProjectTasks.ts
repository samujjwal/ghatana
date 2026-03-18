/**
 * Project Tasks Hook
 * 
 * Manages tasks for a project, organized by lifecycle phase.
 * Provides CRUD operations and task filtering.
 * 
 * @doc.type hook
 * @doc.purpose Project task management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useMemo } from 'react';
import { useAtom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import { LifecyclePhase } from '../types/lifecycle';

// ============================================================================
// Types
// ============================================================================

/**
 * Automation level for tasks
 */
export type AutomationLevel = 'manual' | 'assisted' | 'automated';

/**
 * Task status
 */
export type TaskStatus = 'pending' | 'in-progress' | 'blocked' | 'completed' | 'skipped';

/**
 * Task priority
 */
export type TaskPriority = 'low' | 'medium' | 'high' | 'critical';

/**
 * A task within a project phase
 */
export interface ProjectTask {
    id: string;
    name: string;
    description?: string;
    phase: LifecyclePhase;
    status: TaskStatus;
    priority: TaskPriority;
    automationLevel: AutomationLevel;
    progress: number; // 0-100
    assignee?: string;
    dueDate?: string;
    tags: string[];
    dependencies: string[]; // Task IDs this task depends on
    subtasks: SubTask[];
    createdAt: string;
    updatedAt: string;
    completedAt?: string;
    aiSuggested?: boolean;
    aiReason?: string;
}

/**
 * Sub-task within a task
 */
export interface SubTask {
    id: string;
    name: string;
    status: TaskStatus;
    completedAt?: string;
}

/**
 * Task creation input
 */
export interface CreateTaskInput {
    name: string;
    description?: string;
    phase: LifecyclePhase;
    priority?: TaskPriority;
    automationLevel?: AutomationLevel;
    tags?: string[];
    dependencies?: string[];
}

/**
 * Task update input
 */
export interface UpdateTaskInput {
    name?: string;
    description?: string;
    status?: TaskStatus;
    priority?: TaskPriority;
    progress?: number;
    assignee?: string;
    dueDate?: string;
    tags?: string[];
}

// ============================================================================
// Atoms
// ============================================================================

/**
 * Persisted tasks by project
 */
const projectTasksAtom = atomWithStorage<Record<string, ProjectTask[]>>(
    'project-tasks',
    {}
);

// ============================================================================
// Default Tasks by Phase
// ============================================================================

const DEFAULT_PHASE_TASKS: Record<LifecyclePhase, Array<Omit<ProjectTask, 'id' | 'createdAt' | 'updatedAt'>>> = {
    [LifecyclePhase.INTENT]: [
        {
            name: 'Define project goals',
            description: 'Articulate what you want to build and why',
            phase: LifecyclePhase.INTENT,
            status: 'pending',
            priority: 'high',
            automationLevel: 'assisted',
            progress: 0,
            tags: ['planning'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Identify target users',
            description: 'Define who will use this application',
            phase: LifecyclePhase.INTENT,
            status: 'pending',
            priority: 'medium',
            automationLevel: 'manual',
            progress: 0,
            tags: ['planning', 'user-research'],
            dependencies: [],
            subtasks: [],
        },
    ],
    [LifecyclePhase.SHAPE]: [
        {
            name: 'Design system architecture',
            description: 'Create high-level component diagram',
            phase: LifecyclePhase.SHAPE,
            status: 'pending',
            priority: 'high',
            automationLevel: 'assisted',
            progress: 0,
            tags: ['architecture'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Define data models',
            description: 'Design database schema and entities',
            phase: LifecyclePhase.SHAPE,
            status: 'pending',
            priority: 'high',
            automationLevel: 'assisted',
            progress: 0,
            tags: ['data', 'database'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Create UI wireframes',
            description: 'Design key screens and user flows',
            phase: LifecyclePhase.SHAPE,
            status: 'pending',
            priority: 'medium',
            automationLevel: 'manual',
            progress: 0,
            tags: ['design', 'ui'],
            dependencies: [],
            subtasks: [],
        },
    ],
    [LifecyclePhase.VALIDATE]: [
        {
            name: 'Review architecture',
            description: 'AI validates design patterns and dependencies',
            phase: LifecyclePhase.VALIDATE,
            status: 'pending',
            priority: 'high',
            automationLevel: 'automated',
            progress: 0,
            tags: ['validation', 'ai'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Security review',
            description: 'Check for security vulnerabilities',
            phase: LifecyclePhase.VALIDATE,
            status: 'pending',
            priority: 'high',
            automationLevel: 'automated',
            progress: 0,
            tags: ['security', 'validation'],
            dependencies: [],
            subtasks: [],
        },
    ],
    [LifecyclePhase.GENERATE]: [
        {
            name: 'Generate code scaffolding',
            description: 'AI generates initial code structure',
            phase: LifecyclePhase.GENERATE,
            status: 'pending',
            priority: 'high',
            automationLevel: 'automated',
            progress: 0,
            tags: ['code-gen', 'ai'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Generate API endpoints',
            description: 'Create REST/GraphQL endpoints',
            phase: LifecyclePhase.GENERATE,
            status: 'pending',
            priority: 'high',
            automationLevel: 'automated',
            progress: 0,
            tags: ['api', 'code-gen'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Generate UI components',
            description: 'Create React components from designs',
            phase: LifecyclePhase.GENERATE,
            status: 'pending',
            priority: 'medium',
            automationLevel: 'automated',
            progress: 0,
            tags: ['ui', 'code-gen'],
            dependencies: [],
            subtasks: [],
        },
    ],
    [LifecyclePhase.RUN]: [
        {
            name: 'Configure deployment',
            description: 'Set up deployment pipeline',
            phase: LifecyclePhase.RUN,
            status: 'pending',
            priority: 'high',
            automationLevel: 'assisted',
            progress: 0,
            tags: ['deploy', 'ci-cd'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Deploy to staging',
            description: 'Deploy application to staging environment',
            phase: LifecyclePhase.RUN,
            status: 'pending',
            priority: 'high',
            automationLevel: 'automated',
            progress: 0,
            tags: ['deploy'],
            dependencies: [],
            subtasks: [],
        },
    ],
    [LifecyclePhase.OBSERVE]: [
        {
            name: 'Set up monitoring',
            description: 'Configure metrics and alerting',
            phase: LifecyclePhase.OBSERVE,
            status: 'pending',
            priority: 'high',
            automationLevel: 'assisted',
            progress: 0,
            tags: ['monitoring', 'observability'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Review performance',
            description: 'Analyze application performance metrics',
            phase: LifecyclePhase.OBSERVE,
            status: 'pending',
            priority: 'medium',
            automationLevel: 'assisted',
            progress: 0,
            tags: ['performance', 'analysis'],
            dependencies: [],
            subtasks: [],
        },
    ],
    [LifecyclePhase.IMPROVE]: [
        {
            name: 'Collect feedback',
            description: 'Gather user feedback and analytics',
            phase: LifecyclePhase.IMPROVE,
            status: 'pending',
            priority: 'medium',
            automationLevel: 'manual',
            progress: 0,
            tags: ['feedback', 'analysis'],
            dependencies: [],
            subtasks: [],
        },
        {
            name: 'Plan improvements',
            description: 'Prioritize and plan next iteration',
            phase: LifecyclePhase.IMPROVE,
            status: 'pending',
            priority: 'medium',
            automationLevel: 'assisted',
            progress: 0,
            tags: ['planning', 'iteration'],
            dependencies: [],
            subtasks: [],
        },
    ],
};

// ============================================================================
// Hook
// ============================================================================

export interface UseProjectTasksResult {
    /** All tasks for the project */
    tasks: ProjectTask[];

    /** Tasks filtered by phase */
    getTasksByPhase: (phase: LifecyclePhase) => ProjectTask[];

    /** Tasks filtered by status */
    getTasksByStatus: (status: TaskStatus) => ProjectTask[];

    /** Get a single task by ID */
    getTask: (taskId: string) => ProjectTask | undefined;

    /** Create a new task */
    createTask: (input: CreateTaskInput) => ProjectTask;

    /** Update an existing task */
    updateTask: (taskId: string, input: UpdateTaskInput) => void;

    /** Update task status */
    setTaskStatus: (taskId: string, status: TaskStatus) => void;

    /** Update task progress */
    setTaskProgress: (taskId: string, progress: number) => void;

    /** Delete a task */
    deleteTask: (taskId: string) => void;

    /** Add a subtask to a task */
    addSubtask: (taskId: string, name: string) => void;

    /** Toggle subtask completion */
    toggleSubtask: (taskId: string, subtaskId: string) => void;

    /** Initialize default tasks for a project */
    initializeDefaultTasks: () => void;

    /** Progress summary by phase */
    getPhaseProgress: (phase: LifecyclePhase) => { completed: number; total: number; percentage: number };

    /** Overall project progress */
    projectProgress: { completed: number; total: number; percentage: number };

    /** Loading state */
    isLoading: boolean;
}

/**
 * Hook for managing project tasks
 */
export function useProjectTasks(projectId: string): UseProjectTasksResult {
    const [allProjectTasks, setAllProjectTasks] = useAtom(projectTasksAtom);

    // Get tasks for this project
    const tasks = useMemo(() => {
        return allProjectTasks[projectId] || [];
    }, [allProjectTasks, projectId]);

    // Set tasks for this project
    const setTasks = useCallback((updater: (prev: ProjectTask[]) => ProjectTask[]) => {
        setAllProjectTasks((prev) => ({
            ...prev,
            [projectId]: updater(prev[projectId] || []),
        }));
    }, [projectId, setAllProjectTasks]);

    // Get tasks by phase
    const getTasksByPhase = useCallback((phase: LifecyclePhase) => {
        return tasks.filter((t) => t.phase === phase);
    }, [tasks]);

    // Get tasks by status
    const getTasksByStatus = useCallback((status: TaskStatus) => {
        return tasks.filter((t) => t.status === status);
    }, [tasks]);

    // Get single task
    const getTask = useCallback((taskId: string) => {
        return tasks.find((t) => t.id === taskId);
    }, [tasks]);

    // Create task
    const createTask = useCallback((input: CreateTaskInput): ProjectTask => {
        const now = new Date().toISOString();
        const newTask: ProjectTask = {
            id: `task_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            name: input.name,
            description: input.description,
            phase: input.phase,
            status: 'pending',
            priority: input.priority || 'medium',
            automationLevel: input.automationLevel || 'manual',
            progress: 0,
            tags: input.tags || [],
            dependencies: input.dependencies || [],
            subtasks: [],
            createdAt: now,
            updatedAt: now,
        };

        setTasks((prev) => [...prev, newTask]);
        return newTask;
    }, [setTasks]);

    // Update task
    const updateTask = useCallback((taskId: string, input: UpdateTaskInput) => {
        setTasks((prev) => prev.map((t) => {
            if (t.id !== taskId) return t;
            return {
                ...t,
                ...input,
                updatedAt: new Date().toISOString(),
            };
        }));
    }, [setTasks]);

    // Set task status
    const setTaskStatus = useCallback((taskId: string, status: TaskStatus) => {
        setTasks((prev) => prev.map((t) => {
            if (t.id !== taskId) return t;
            return {
                ...t,
                status,
                progress: status === 'completed' ? 100 : t.progress,
                completedAt: status === 'completed' ? new Date().toISOString() : undefined,
                updatedAt: new Date().toISOString(),
            };
        }));
    }, [setTasks]);

    // Set task progress
    const setTaskProgress = useCallback((taskId: string, progress: number) => {
        setTasks((prev) => prev.map((t) => {
            if (t.id !== taskId) return t;
            const clampedProgress = Math.max(0, Math.min(100, progress));
            return {
                ...t,
                progress: clampedProgress,
                status: clampedProgress === 100 ? 'completed' : t.status === 'completed' ? 'in-progress' : t.status,
                completedAt: clampedProgress === 100 ? new Date().toISOString() : undefined,
                updatedAt: new Date().toISOString(),
            };
        }));
    }, [setTasks]);

    // Delete task
    const deleteTask = useCallback((taskId: string) => {
        setTasks((prev) => prev.filter((t) => t.id !== taskId));
    }, [setTasks]);

    // Add subtask
    const addSubtask = useCallback((taskId: string, name: string) => {
        setTasks((prev) => prev.map((t) => {
            if (t.id !== taskId) return t;
            return {
                ...t,
                subtasks: [
                    ...t.subtasks,
                    {
                        id: `subtask_${Date.now()}`,
                        name,
                        status: 'pending' as TaskStatus,
                    },
                ],
                updatedAt: new Date().toISOString(),
            };
        }));
    }, [setTasks]);

    // Toggle subtask
    const toggleSubtask = useCallback((taskId: string, subtaskId: string) => {
        setTasks((prev) => prev.map((t) => {
            if (t.id !== taskId) return t;
            return {
                ...t,
                subtasks: t.subtasks.map((st) => {
                    if (st.id !== subtaskId) return st;
                    const isCompleting = st.status !== 'completed';
                    return {
                        ...st,
                        status: isCompleting ? 'completed' as TaskStatus : 'pending' as TaskStatus,
                        completedAt: isCompleting ? new Date().toISOString() : undefined,
                    };
                }),
                updatedAt: new Date().toISOString(),
            };
        }));
    }, [setTasks]);

    // Initialize default tasks
    const initializeDefaultTasks = useCallback(() => {
        const now = new Date().toISOString();
        const defaultTasks: ProjectTask[] = [];

        for (const phase of Object.values(LifecyclePhase)) {
            const phaseTasks = DEFAULT_PHASE_TASKS[phase] || [];
            for (const template of phaseTasks) {
                defaultTasks.push({
                    ...template,
                    id: `task_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
                    createdAt: now,
                    updatedAt: now,
                });
            }
        }

        setTasks(() => defaultTasks);
    }, [setTasks]);

    // Get phase progress
    const getPhaseProgress = useCallback((phase: LifecyclePhase) => {
        const phaseTasks = getTasksByPhase(phase);
        const completed = phaseTasks.filter((t) => t.status === 'completed').length;
        const total = phaseTasks.length;
        return {
            completed,
            total,
            percentage: total > 0 ? Math.round((completed / total) * 100) : 0,
        };
    }, [getTasksByPhase]);

    // Overall project progress
    const projectProgress = useMemo(() => {
        const completed = tasks.filter((t) => t.status === 'completed').length;
        const total = tasks.length;
        return {
            completed,
            total,
            percentage: total > 0 ? Math.round((completed / total) * 100) : 0,
        };
    }, [tasks]);

    return {
        tasks,
        getTasksByPhase,
        getTasksByStatus,
        getTask,
        createTask,
        updateTask,
        setTaskStatus,
        setTaskProgress,
        deleteTask,
        addSubtask,
        toggleSubtask,
        initializeDefaultTasks,
        getPhaseProgress,
        projectProgress,
        isLoading: false,
    };
}
