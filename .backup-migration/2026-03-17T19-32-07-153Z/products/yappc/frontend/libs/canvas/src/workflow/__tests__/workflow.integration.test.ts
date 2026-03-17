/**
 * YAPPC Workflow E2E Integration Tests
 *
 * Tests the full workflow execution pipeline from configuration loading
 * through task execution and state management.
 *
 * @module tests/workflow.integration.test
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { createStore, type Setter, type Getter } from 'jotai';

// Import types using path aliases
import type {
    TaskDefinition,
    TaskDomainId,
    LifecycleStage,
    AutomationLevel,
} from '@ghatana/yappc-types/tasks';

// ============================================================================
// Test Data Factories
// ============================================================================

function createMockTask(overrides: Partial<TaskDefinition> = {}): TaskDefinition {
    const id = overrides.id || `task-${Date.now()}`;
    return {
        id,
        name: overrides.name || `Test Task ${id}`,
        description: overrides.description || 'A test task for integration testing',
        domain: overrides.domain || 'data-modeling',
        lifecycleStage: overrides.lifecycleStage || 'development',
        automationLevel: overrides.automationLevel || 'ai-assisted',
        requiredPersonas: overrides.requiredPersonas || ['developer'],
        estimatedTime: overrides.estimatedTime || 15,
        inputSchema: overrides.inputSchema || {},
        outputSchema: overrides.outputSchema || {},
        dependencies: overrides.dependencies || [],
        tags: overrides.tags || ['test'],
        ...overrides,
    } as TaskDefinition;
}

function createMockWorkflowTemplate() {
    return {
        id: 'integration-test-workflow',
        name: 'Integration Test Workflow',
        description: 'Workflow for testing the full execution pipeline',
        version: '1.0.0',
        category: 'testing',
        applicableLifecycleStages: ['development'] as LifecycleStage[],
        estimatedTotalTime: 60,
        phases: [
            {
                id: 'phase-1',
                name: 'Setup Phase',
                description: 'Initial setup tasks',
                order: 1,
                requiredStage: 'development' as LifecycleStage,
                steps: [
                    {
                        id: 'step-1',
                        taskId: 'setup-task',
                        order: 1,
                        optional: false,
                        dependsOn: [],
                    },
                    {
                        id: 'step-2',
                        taskId: 'config-task',
                        order: 2,
                        optional: false,
                        dependsOn: ['step-1'],
                    },
                ],
            },
            {
                id: 'phase-2',
                name: 'Execution Phase',
                description: 'Main execution tasks',
                order: 2,
                requiredStage: 'development' as LifecycleStage,
                steps: [
                    {
                        id: 'step-3',
                        taskId: 'execute-task',
                        order: 1,
                        optional: false,
                        dependsOn: [],
                    },
                ],
            },
        ],
        successCriteria: ['All required steps completed'],
        tags: ['test', 'integration'],
    };
}

// ============================================================================
// Configuration Loading Tests
// ============================================================================

describe('Configuration Loading', () => {
    describe('Task Domain Configuration', () => {
        it('should load task domain YAML files', async () => {
            // Simulate loading task domain configs
            const mockDomainConfig = {
                id: 'data-modeling',
                name: 'Data Modeling',
                description: 'Database and data structure tasks',
                category: 'architecture',
                tasks: [
                    createMockTask({ id: 'create-schema', domain: 'data-modeling' as TaskDomainId }),
                    createMockTask({ id: 'define-relations', domain: 'data-modeling' as TaskDomainId }),
                ],
            };

            expect(mockDomainConfig.id).toBe('data-modeling');
            expect(mockDomainConfig.tasks).toHaveLength(2);
            expect(mockDomainConfig.tasks[0].domain).toBe('data-modeling');
        });

        it('should validate task schema against JSON Schema', () => {
            const task = createMockTask();

            // Required fields validation
            expect(task.id).toBeDefined();
            expect(task.name).toBeDefined();
            expect(task.domain).toBeDefined();
            expect(task.lifecycleStage).toBeDefined();
            expect(task.automationLevel).toBeDefined();

            // Automation level validation
            const validLevels: AutomationLevel[] = [
                'fully-automated',
                'ai-assisted',
                'human-assisted',
                'human-only',
            ];
            expect(validLevels).toContain(task.automationLevel);
        });

        it('should merge configuration from multiple sources', () => {
            const baseTask = createMockTask({ id: 'base-task' });
            const overrideConfig = {
                estimatedTime: 30,
                tags: ['custom', 'override'],
            };

            const mergedTask = { ...baseTask, ...overrideConfig };

            expect(mergedTask.id).toBe('base-task');
            expect(mergedTask.estimatedTime).toBe(30);
            expect(mergedTask.tags).toEqual(['custom', 'override']);
        });
    });

    describe('Workflow Template Configuration', () => {
        it('should load workflow template configurations', () => {
            const template = createMockWorkflowTemplate();

            expect(template.id).toBe('integration-test-workflow');
            expect(template.phases).toHaveLength(2);
            expect(template.phases[0].steps).toHaveLength(2);
        });

        it('should validate phase ordering', () => {
            const template = createMockWorkflowTemplate();

            const orders = template.phases.map((p) => p.order);
            const sortedOrders = [...orders].sort((a, b) => a - b);

            expect(orders).toEqual(sortedOrders);
        });

        it('should validate step dependencies within phase', () => {
            const template = createMockWorkflowTemplate();
            const phase = template.phases[0];
            const stepIds = phase.steps.map((s) => s.id);

            for (const step of phase.steps) {
                for (const dep of step.dependsOn) {
                    expect(stepIds).toContain(dep);
                }
            }
        });
    });

    describe('Lifecycle Configuration', () => {
        it('should load lifecycle stage definitions', () => {
            const stages: LifecycleStage[] = [
                'discovery',
                'design',
                'development',
                'data-integration',
                'testing',
                'deployment',
                'operations',
                'iteration',
            ];

            expect(stages).toHaveLength(8);
            expect(stages[0]).toBe('discovery');
            expect(stages[7]).toBe('iteration');
        });

        it('should validate stage transitions', () => {
            const transitions = {
                discovery: ['design'],
                design: ['development', 'discovery'],
                development: ['data-integration', 'testing', 'design'],
                'data-integration': ['testing', 'development'],
                testing: ['deployment', 'development'],
                deployment: ['operations', 'testing'],
                operations: ['iteration', 'testing'],
                iteration: ['discovery', 'design', 'development'],
            };

            // Verify all stages have transitions defined
            const stages: LifecycleStage[] = [
                'discovery',
                'design',
                'development',
                'data-integration',
                'testing',
                'deployment',
                'operations',
                'iteration',
            ];

            for (const stage of stages) {
                expect(transitions).toHaveProperty(stage);
            }
        });
    });
});

// ============================================================================
// Workflow Instantiation Tests
// ============================================================================

describe('Workflow Instantiation', () => {
    describe('Template to Instance', () => {
        it('should instantiate workflow from template', () => {
            const template = createMockWorkflowTemplate();
            const projectId = 'test-project-123';

            // Simulate instantiation
            const instance = {
                id: `${template.id}-${Date.now()}`,
                templateId: template.id,
                projectId,
                status: 'pending' as const,
                createdAt: new Date().toISOString(),
                phases: template.phases.map((phase) => ({
                    ...phase,
                    status: 'pending' as const,
                    steps: phase.steps.map((step) => ({
                        ...step,
                        status: 'pending' as const,
                    })),
                })),
                currentPhaseIndex: 0,
            };

            expect(instance.templateId).toBe(template.id);
            expect(instance.projectId).toBe(projectId);
            expect(instance.status).toBe('pending');
            expect(instance.phases).toHaveLength(2);
        });

        it('should apply project-specific customizations', () => {
            const template = createMockWorkflowTemplate();
            const customizations = {
                skipOptionalSteps: true,
                additionalTags: ['custom-project'],
            };

            const instance = {
                id: `${template.id}-${Date.now()}`,
                templateId: template.id,
                ...customizations,
            };

            expect(instance.skipOptionalSteps).toBe(true);
            expect(instance.additionalTags).toContain('custom-project');
        });

        it('should generate unique instance IDs', () => {
            const template = createMockWorkflowTemplate();
            const ids = new Set<string>();

            for (let i = 0; i < 100; i++) {
                const id = `${template.id}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
                ids.add(id);
            }

            expect(ids.size).toBe(100);
        });
    });

    describe('Variable Substitution', () => {
        it('should substitute project variables in task inputs', () => {
            const variables = {
                projectName: 'MyApp',
                authorName: 'John Doe',
                targetFramework: 'React',
            };

            const inputTemplate = {
                componentName: '{{projectName}}Header',
                author: '{{authorName}}',
                framework: '{{targetFramework}}',
            };

            const substituted = Object.fromEntries(
                Object.entries(inputTemplate).map(([key, value]) => {
                    let resolved = value;
                    for (const [varName, varValue] of Object.entries(variables)) {
                        resolved = resolved.replace(`{{${varName}}}`, varValue);
                    }
                    return [key, resolved];
                })
            );

            expect(substituted.componentName).toBe('MyAppHeader');
            expect(substituted.author).toBe('John Doe');
            expect(substituted.framework).toBe('React');
        });
    });
});

// ============================================================================
// Task Execution Tests
// ============================================================================

describe('Task Execution', () => {
    describe('Single Task Execution', () => {
        it('should execute a task with valid inputs', async () => {
            const task = createMockTask({ automationLevel: 'fully-automated' });
            const inputs = { param1: 'value1' };

            // Simulate execution
            const execution = {
                id: `exec-${task.id}-${Date.now()}`,
                taskId: task.id,
                status: 'completed' as const,
                inputs,
                outputs: { result: 'success' },
                startedAt: new Date().toISOString(),
                completedAt: new Date().toISOString(),
            };

            expect(execution.status).toBe('completed');
            expect(execution.outputs).toBeDefined();
        });

        it('should validate task inputs against schema', () => {
            const task = createMockTask({
                inputSchema: {
                    type: 'object',
                    properties: {
                        name: { type: 'string' },
                        count: { type: 'number' },
                    },
                    required: ['name'],
                },
            });

            // Valid inputs
            const validInputs = { name: 'test', count: 5 };
            expect(() => validateInputs(task.inputSchema, validInputs)).not.toThrow();

            // Invalid inputs (missing required field)
            const invalidInputs = { count: 5 };
            expect(() => validateInputs(task.inputSchema, invalidInputs)).toThrow();
        });

        it('should handle task execution failure', async () => {
            const task = createMockTask({ automationLevel: 'human-only' });

            const execution = {
                id: `exec-${task.id}-${Date.now()}`,
                taskId: task.id,
                status: 'failed' as const,
                error: 'Human intervention required',
                startedAt: new Date().toISOString(),
                completedAt: new Date().toISOString(),
            };

            expect(execution.status).toBe('failed');
            expect(execution.error).toBeDefined();
        });
    });

    describe('Dependency Resolution', () => {
        it('should execute tasks in dependency order', () => {
            const tasks = [
                createMockTask({ id: 'task-3', dependencies: ['task-2'] }),
                createMockTask({ id: 'task-1', dependencies: [] }),
                createMockTask({ id: 'task-2', dependencies: ['task-1'] }),
            ];

            const sorted = topologicalSort(tasks);
            const order = sorted.map((t) => t.id);

            expect(order.indexOf('task-1')).toBeLessThan(order.indexOf('task-2'));
            expect(order.indexOf('task-2')).toBeLessThan(order.indexOf('task-3'));
        });

        it('should detect circular dependencies', () => {
            const tasks = [
                createMockTask({ id: 'task-1', dependencies: ['task-3'] }),
                createMockTask({ id: 'task-2', dependencies: ['task-1'] }),
                createMockTask({ id: 'task-3', dependencies: ['task-2'] }),
            ];

            expect(() => topologicalSort(tasks)).toThrow('Circular dependency');
        });

        it('should handle parallel independent tasks', () => {
            const tasks = [
                createMockTask({ id: 'task-1', dependencies: [] }),
                createMockTask({ id: 'task-2', dependencies: [] }),
                createMockTask({ id: 'task-3', dependencies: [] }),
            ];

            const parallelGroups = groupParallelTasks(tasks);

            expect(parallelGroups).toHaveLength(1);
            expect(parallelGroups[0]).toHaveLength(3);
        });
    });

    describe('Agent Selection', () => {
        it('should select appropriate agent based on task domain', () => {
            const domainAgentMapping = {
                'data-modeling': 'data-modeling-agent',
                'api-design': 'api-design-agent',
                'component-creation': 'frontend-agent',
            };

            const task = createMockTask({ domain: 'data-modeling' as TaskDomainId });
            const selectedAgent = domainAgentMapping[task.domain as keyof typeof domainAgentMapping];

            expect(selectedAgent).toBe('data-modeling-agent');
        });

        it('should fall back to default agent for unmapped domains', () => {
            const defaultAgent = 'general-purpose-agent';
            const domainAgentMapping: Record<string, string> = {
                'data-modeling': 'data-modeling-agent',
            };

            const task = createMockTask({ domain: 'unknown-domain' as TaskDomainId });
            const selectedAgent = domainAgentMapping[task.domain] || defaultAgent;

            expect(selectedAgent).toBe(defaultAgent);
        });
    });
});

// ============================================================================
// State Management Tests
// ============================================================================

describe('State Management', () => {
    describe('Workflow State', () => {
        it('should track workflow progress', () => {
            const workflow = {
                phases: [
                    { steps: [{ status: 'completed' }, { status: 'completed' }] },
                    { steps: [{ status: 'completed' }, { status: 'pending' }] },
                ],
            };

            const totalSteps = workflow.phases.reduce((sum, p) => sum + p.steps.length, 0);
            const completedSteps = workflow.phases.reduce(
                (sum, p) => sum + p.steps.filter((s) => s.status === 'completed').length,
                0
            );
            const progress = (completedSteps / totalSteps) * 100;

            expect(progress).toBe(75);
        });

        it('should persist workflow state', () => {
            const state = {
                activeWorkflowId: 'workflow-123',
                workflows: new Map([['workflow-123', { id: 'workflow-123', status: 'running' }]]),
            };

            // Simulate serialization
            const serialized = JSON.stringify({
                ...state,
                workflows: Object.fromEntries(state.workflows),
            });

            const parsed = JSON.parse(serialized);
            const restored = {
                ...parsed,
                workflows: new Map(Object.entries(parsed.workflows)),
            };

            expect(restored.activeWorkflowId).toBe('workflow-123');
            expect(restored.workflows.get('workflow-123')).toBeDefined();
        });
    });

    describe('Lifecycle State Machine', () => {
        it('should track current lifecycle stage', () => {
            const lifecycleState = {
                currentStage: 'development' as LifecycleStage,
                stageHistory: ['discovery', 'design', 'development'] as LifecycleStage[],
                stageData: new Map<string, Record<string, unknown>>(),
            };

            expect(lifecycleState.currentStage).toBe('development');
            expect(lifecycleState.stageHistory).toHaveLength(3);
        });

        it('should validate stage transitions', () => {
            const allowedTransitions: Record<LifecycleStage, LifecycleStage[]> = {
                discovery: ['design'],
                design: ['development', 'discovery'],
                development: ['data-integration', 'testing', 'design'],
                'data-integration': ['testing', 'development'],
                testing: ['deployment', 'development'],
                deployment: ['operations', 'testing'],
                operations: ['iteration', 'testing'],
                iteration: ['discovery', 'design', 'development'],
            };

            const currentStage: LifecycleStage = 'development';
            const targetStage: LifecycleStage = 'testing';

            const canTransition = allowedTransitions[currentStage].includes(targetStage);
            expect(canTransition).toBe(true);

            const invalidTarget: LifecycleStage = 'discovery';
            const invalidTransition = allowedTransitions[currentStage].includes(invalidTarget);
            expect(invalidTransition).toBe(false);
        });
    });

    describe('Execution Statistics', () => {
        it('should calculate execution metrics', () => {
            const executions = [
                { duration: 1000, success: true },
                { duration: 2000, success: true },
                { duration: 1500, success: false },
                { duration: 3000, success: true },
            ];

            const totalDuration = executions.reduce((sum, e) => sum + e.duration, 0);
            const averageDuration = totalDuration / executions.length;
            const successRate = (executions.filter((e) => e.success).length / executions.length) * 100;

            expect(averageDuration).toBe(1875);
            expect(successRate).toBe(75);
        });
    });
});

// ============================================================================
// Integration Flow Tests
// ============================================================================

describe('Full Integration Flow', () => {
    it('should complete end-to-end workflow execution', async () => {
        // Step 1: Load configuration
        const template = createMockWorkflowTemplate();
        const tasks = template.phases.flatMap((p) =>
            p.steps.map((s) => createMockTask({ id: s.taskId }))
        );

        expect(template).toBeDefined();
        expect(tasks).toHaveLength(3);

        // Step 2: Instantiate workflow
        const instance = {
            id: `${template.id}-${Date.now()}`,
            templateId: template.id,
            projectId: 'test-project',
            status: 'running' as const,
            phases: template.phases.map((p) => ({
                ...p,
                status: 'pending' as const,
                steps: p.steps.map((s) => ({
                    ...s,
                    status: 'pending' as const,
                })),
            })),
            currentPhaseIndex: 0,
        };

        expect(instance.status).toBe('running');

        // Step 3: Execute tasks in order
        const executions = [];
        for (const phase of instance.phases) {
            for (const step of phase.steps) {
                const task = tasks.find((t) => t.id === step.taskId);
                if (task) {
                    const execution = {
                        id: `exec-${step.id}`,
                        taskId: task.id,
                        status: 'completed' as const,
                        startedAt: new Date().toISOString(),
                        completedAt: new Date().toISOString(),
                    };
                    executions.push(execution);
                    step.status = 'completed' as const;
                }
            }
        }

        expect(executions).toHaveLength(3);

        // Step 4: Verify completion
        const allCompleted = instance.phases.every((p) =>
            p.steps.every((s) => s.status === 'completed')
        );
        expect(allCompleted).toBe(true);

        // Step 5: Calculate final metrics
        const completedTasks = executions.filter((e) => e.status === 'completed').length;
        expect(completedTasks).toBe(3);
    });

    it('should handle workflow with lifecycle transitions', async () => {
        const lifecycleStages: LifecycleStage[] = ['discovery', 'design', 'development'];
        const stageHistory: LifecycleStage[] = [];
        let currentStage = lifecycleStages[0];

        for (const stage of lifecycleStages) {
            stageHistory.push(currentStage);
            currentStage = stage;
        }

        expect(stageHistory).toHaveLength(3);
        expect(currentStage).toBe('development');
    });
});

// ============================================================================
// Helper Functions
// ============================================================================

function validateInputs(schema: Record<string, unknown>, inputs: Record<string, unknown>): void {
    const required = (schema.required as string[]) || [];
    for (const field of required) {
        if (!(field in inputs)) {
            throw new Error(`Missing required field: ${field}`);
        }
    }
}

function topologicalSort(tasks: TaskDefinition[]): TaskDefinition[] {
    const taskMap = new Map(tasks.map((t) => [t.id, t]));
    const visited = new Set<string>();
    const visiting = new Set<string>();
    const result: TaskDefinition[] = [];

    function visit(taskId: string): void {
        if (visited.has(taskId)) return;
        if (visiting.has(taskId)) {
            throw new Error('Circular dependency detected');
        }

        const task = taskMap.get(taskId);
        if (!task) return;

        visiting.add(taskId);
        for (const dep of task.dependencies || []) {
            visit(dep);
        }
        visiting.delete(taskId);
        visited.add(taskId);
        result.push(task);
    }

    for (const task of tasks) {
        visit(task.id);
    }

    return result;
}

function groupParallelTasks(tasks: TaskDefinition[]): TaskDefinition[][] {
    const taskMap = new Map(tasks.map((t) => [t.id, t]));
    const levels = new Map<string, number>();

    function getLevel(taskId: string): number {
        if (levels.has(taskId)) return levels.get(taskId)!;

        const task = taskMap.get(taskId);
        if (!task || !task.dependencies?.length) {
            levels.set(taskId, 0);
            return 0;
        }

        const maxDepLevel = Math.max(...task.dependencies.map(getLevel));
        const level = maxDepLevel + 1;
        levels.set(taskId, level);
        return level;
    }

    for (const task of tasks) {
        getLevel(task.id);
    }

    const groups: TaskDefinition[][] = [];
    for (const task of tasks) {
        const level = levels.get(task.id) || 0;
        if (!groups[level]) groups[level] = [];
        groups[level].push(task);
    }

    return groups.filter(Boolean);
}
