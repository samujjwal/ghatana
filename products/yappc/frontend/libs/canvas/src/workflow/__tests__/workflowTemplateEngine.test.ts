import { describe, it, expect, beforeEach } from 'vitest';

import {
    WorkflowTemplateEngine,
    type TaskRegistry,
} from '../workflowTemplateEngine';
import {
    type LifecycleStageConfig,
    type StageTransitionConfig,
    generateDefaultTransitions,
} from '../lifecycleStateMachine';
import type {
    WorkflowDefinition,
    TaskDefinition,
    LifecycleStage,
} from '@ghatana/types/tasks';

// ============================================================================
// Test Fixtures
// ============================================================================

const createTestStageConfigs = (): LifecycleStageConfig[] => [
    {
        id: 'intent',
        name: 'Intent',
        description: 'Define intent',
        order: 0,
        icon: 'lightbulb',
        color: '#8B5CF6',
        required: true,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
    {
        id: 'context',
        name: 'Context',
        description: 'Gather context',
        order: 1,
        icon: 'explore',
        color: '#6366F1',
        required: true,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
    {
        id: 'plan',
        name: 'Plan',
        description: 'Create plan',
        order: 2,
        icon: 'map',
        color: '#3B82F6',
        required: true,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
    {
        id: 'execute',
        name: 'Execute',
        description: 'Execute plan',
        order: 3,
        icon: 'rocket',
        color: '#10B981',
        required: true,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
    {
        id: 'verify',
        name: 'Verify',
        description: 'Verify results',
        order: 4,
        icon: 'check',
        color: '#F59E0B',
        required: true,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
    {
        id: 'observe',
        name: 'Observe',
        description: 'Observe production',
        order: 5,
        icon: 'visibility',
        color: '#EF4444',
        required: false,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
    {
        id: 'learn',
        name: 'Learn',
        description: 'Learn from experience',
        order: 6,
        icon: 'school',
        color: '#EC4899',
        required: false,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
    {
        id: 'institutionalize',
        name: 'Institutionalize',
        description: 'Institutionalize learnings',
        order: 7,
        icon: 'account_balance',
        color: '#14B8A6',
        required: false,
        entryCriteria: [],
        exitCriteria: [],
        typicalActivities: [],
        artifacts: [],
    },
];

const createTestTasks = (): Map<string, TaskDefinition> => {
    const tasks = new Map<string, TaskDefinition>();

    const taskDefs: TaskDefinition[] = [
        {
            id: 'problem-understanding/define-problem',
            name: 'Define Problem',
            description: 'Define the problem statement',
            domain: 'problem-understanding',
            personas: ['Developer', 'Tech Lead'],
            lifecycleStages: ['intent'],
            automationLevel: 'assisted',
            requiredCapabilities: ['research'],
            inputSchema: { type: 'object', properties: {} },
            outputSchema: { type: 'object', properties: {} },
            auditArtifacts: ['DecisionRecord'],
            ui: { icon: 'help', color: '#8B5CF6', tags: ['analysis'] },
        },
        {
            id: 'research/explore-codebase',
            name: 'Explore Codebase',
            description: 'Explore the existing codebase',
            domain: 'research',
            personas: ['Developer'],
            lifecycleStages: ['context'],
            automationLevel: 'assisted',
            requiredCapabilities: ['code-analysis'],
            inputSchema: { type: 'object', properties: {} },
            outputSchema: { type: 'object', properties: {} },
            auditArtifacts: ['InputSnapshot'],
            ui: { icon: 'search', color: '#6366F1', tags: ['research'] },
        },
        {
            id: 'planning/create-task-breakdown',
            name: 'Create Task Breakdown',
            description: 'Break down the work into tasks',
            domain: 'planning',
            personas: ['Developer', 'Tech Lead'],
            lifecycleStages: ['plan'],
            automationLevel: 'assisted',
            requiredCapabilities: ['research'],
            inputSchema: { type: 'object', properties: {} },
            outputSchema: { type: 'object', properties: {} },
            auditArtifacts: ['DecisionRecord'],
            ui: { icon: 'list', color: '#3B82F6', tags: ['planning'] },
        },
        {
            id: 'implementation/write-code',
            name: 'Write Code',
            description: 'Implement the solution',
            domain: 'implementation',
            personas: ['Developer'],
            lifecycleStages: ['execute'],
            automationLevel: 'assisted',
            requiredCapabilities: ['code-generation'],
            inputSchema: { type: 'object', properties: {} },
            outputSchema: { type: 'object', properties: {} },
            auditArtifacts: ['ChangeSet'],
            ui: { icon: 'code', color: '#10B981', tags: ['coding'] },
        },
        {
            id: 'testing/write-tests',
            name: 'Write Tests',
            description: 'Write unit tests',
            domain: 'testing',
            personas: ['Developer', 'QA'],
            lifecycleStages: ['verify'],
            automationLevel: 'automated',
            requiredCapabilities: ['test-generation'],
            inputSchema: { type: 'object', properties: {} },
            outputSchema: { type: 'object', properties: {} },
            auditArtifacts: ['VerificationResult'],
            ui: { icon: 'bug_report', color: '#F59E0B', tags: ['testing'] },
        },
    ];

    for (const task of taskDefs) {
        tasks.set(task.id, task);
    }

    return tasks;
};

const createTestWorkflowTemplate = (): WorkflowDefinition => ({
    id: 'new-feature',
    name: 'New Feature Development',
    description: 'Workflow for implementing new features',
    category: 'development',
    applicablePersonas: ['Developer', 'Tech Lead'],
    estimatedDuration: '2 weeks',
    phases: [
        {
            name: 'Discovery',
            lifecycleStage: 'intent',
            tasks: ['problem-understanding/define-problem'],
        },
        {
            name: 'Research',
            lifecycleStage: 'context',
            tasks: ['research/explore-codebase'],
        },
        {
            name: 'Planning',
            lifecycleStage: 'plan',
            tasks: ['planning/create-task-breakdown'],
        },
        {
            name: 'Implementation',
            lifecycleStage: 'execute',
            tasks: ['implementation/write-code'],
        },
        {
            name: 'Testing',
            lifecycleStage: 'verify',
            tasks: ['testing/write-tests'],
        },
    ],
});

const createMockTaskRegistry = (tasks: Map<string, TaskDefinition>): TaskRegistry => ({
    getTask: (taskId: string) => tasks.get(taskId),
    getTasksByDomain: (domainId: string) =>
        Array.from(tasks.values()).filter((t) => t.domain === domainId),
    searchTasks: (query: string) =>
        Array.from(tasks.values()).filter(
            (t) => t.name.toLowerCase().includes(query.toLowerCase()),
        ),
});

// ============================================================================
// Tests
// ============================================================================

describe('WorkflowTemplateEngine', () => {
    let engine: WorkflowTemplateEngine;
    let stageConfigs: LifecycleStageConfig[];
    let transitionConfigs: StageTransitionConfig[];
    let tasks: Map<string, TaskDefinition>;
    let taskRegistry: TaskRegistry;

    beforeEach(() => {
        stageConfigs = createTestStageConfigs();
        transitionConfigs = generateDefaultTransitions();
        tasks = createTestTasks();
        taskRegistry = createMockTaskRegistry(tasks);
        engine = new WorkflowTemplateEngine(taskRegistry, stageConfigs, transitionConfigs);
    });

    describe('Template Management', () => {
        it('should register a template', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            expect(engine.getTemplate('new-feature')).toBeDefined();
            expect(engine.getTemplate('new-feature')?.name).toBe('New Feature Development');
        });

        it('should register multiple templates', () => {
            const template1 = createTestWorkflowTemplate();
            const template2: WorkflowDefinition = {
                ...template1,
                id: 'bug-fix',
                name: 'Bug Fix',
            };

            engine.registerTemplates([template1, template2]);

            expect(engine.getAllTemplates()).toHaveLength(2);
        });

        it('should get templates by category', () => {
            const template1 = createTestWorkflowTemplate();
            const template2: WorkflowDefinition = {
                ...template1,
                id: 'bug-fix',
                name: 'Bug Fix',
                category: 'maintenance',
            };

            engine.registerTemplates([template1, template2]);

            const devTemplates = engine.getTemplatesByCategory('development');
            expect(devTemplates).toHaveLength(1);
            expect(devTemplates[0].id).toBe('new-feature');
        });

        it('should return undefined for non-existent template', () => {
            expect(engine.getTemplate('non-existent')).toBeUndefined();
        });
    });

    describe('Workflow Instantiation', () => {
        it('should instantiate a workflow from template', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            const instance = engine.instantiate('new-feature', {
                workflowId: 'wf-123',
                createdBy: 'user1',
            });

            expect(instance.id).toBe('wf-123');
            expect(instance.templateId).toBe('new-feature');
            expect(instance.status).toBe('pending');
            expect(instance.phases).toHaveLength(5);
        });

        it('should throw for non-existent template', () => {
            expect(() =>
                engine.instantiate('non-existent', {
                    workflowId: 'wf-123',
                    createdBy: 'user1',
                }),
            ).toThrow('Workflow template not found');
        });

        it('should create phases with steps', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            const instance = engine.instantiate('new-feature', {
                workflowId: 'wf-123',
                createdBy: 'user1',
            });

            expect(instance.phases[0].steps).toHaveLength(1);
            expect(instance.phases[0].steps[0].taskId).toBe('problem-understanding/define-problem');
        });

        it('should resolve task definitions in steps', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            const instance = engine.instantiate('new-feature', {
                workflowId: 'wf-123',
                createdBy: 'user1',
            });

            expect(instance.phases[0].steps[0].task).toBeDefined();
            expect(instance.phases[0].steps[0].task?.name).toBe('Define Problem');
        });

        it('should create lifecycle state machine', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            const instance = engine.instantiate('new-feature', {
                workflowId: 'wf-123',
                createdBy: 'user1',
            });

            expect(instance.lifecycleState).toBeDefined();
            expect(instance.lifecycleState.getCurrentStage()).toBe('intent');
        });

        it('should set custom name if provided', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            const instance = engine.instantiate('new-feature', {
                workflowId: 'wf-123',
                name: 'Custom Feature Name',
                createdBy: 'user1',
            });

            expect(instance.name).toBe('Custom Feature Name');
        });

        it('should store parameters and metadata', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            const instance = engine.instantiate('new-feature', {
                workflowId: 'wf-123',
                createdBy: 'user1',
                parameters: { featureName: 'Auth' },
                metadata: { priority: 'high' },
            });

            expect(instance.parameters).toEqual({ featureName: 'Auth' });
            expect(instance.metadata).toEqual({ priority: 'high' });
        });
    });

    describe('Instance Management', () => {
        it('should retrieve instance by ID', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            engine.instantiate('new-feature', {
                workflowId: 'wf-123',
                createdBy: 'user1',
            });

            const instance = engine.getInstance('wf-123');
            expect(instance).toBeDefined();
            expect(instance?.id).toBe('wf-123');
        });

        it('should return undefined for non-existent instance', () => {
            expect(engine.getInstance('non-existent')).toBeUndefined();
        });

        it('should get all instances', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            engine.instantiate('new-feature', { workflowId: 'wf-1', createdBy: 'user1' });
            engine.instantiate('new-feature', { workflowId: 'wf-2', createdBy: 'user1' });

            expect(engine.getAllInstances()).toHaveLength(2);
        });

        it('should get instances by status', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);

            engine.instantiate('new-feature', { workflowId: 'wf-1', createdBy: 'user1' });
            engine.instantiate('new-feature', { workflowId: 'wf-2', createdBy: 'user1' });

            const pending = engine.getInstancesByStatus('pending');
            expect(pending).toHaveLength(2);

            const running = engine.getInstancesByStatus('running');
            expect(running).toHaveLength(0);
        });
    });

    describe('Workflow Execution', () => {
        it('should start a workflow', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });

            engine.startWorkflow('wf-123');

            const instance = engine.getInstance('wf-123');
            expect(instance?.status).toBe('running');
            expect(instance?.startedAt).toBeDefined();
        });

        it('should start first phase when workflow starts', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });

            engine.startWorkflow('wf-123');

            const instance = engine.getInstance('wf-123');
            expect(instance?.phases[0].status).toBe('in-progress');
            expect(instance?.currentPhaseIndex).toBe(0);
        });

        it('should throw when starting non-existent workflow', () => {
            expect(() => engine.startWorkflow('non-existent')).toThrow('Workflow instance not found');
        });

        it('should throw when starting already started workflow', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });
            engine.startWorkflow('wf-123');

            expect(() => engine.startWorkflow('wf-123')).toThrow('Cannot start workflow in status');
        });

        it('should pause a running workflow', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });
            engine.startWorkflow('wf-123');

            engine.pauseWorkflow('wf-123');

            expect(engine.getInstance('wf-123')?.status).toBe('paused');
        });

        it('should resume a paused workflow', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });
            engine.startWorkflow('wf-123');
            engine.pauseWorkflow('wf-123');

            engine.resumeWorkflow('wf-123');

            expect(engine.getInstance('wf-123')?.status).toBe('running');
        });

        it('should cancel a workflow', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });
            engine.startWorkflow('wf-123');

            engine.cancelWorkflow('wf-123');

            expect(engine.getInstance('wf-123')?.status).toBe('cancelled');
        });
    });

    describe('Progress Tracking', () => {
        it('should calculate progress as 0 for new workflow', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });

            expect(engine.getProgress('wf-123')).toBe(0);
        });

        it('should get remaining tasks in current phase', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });
            engine.startWorkflow('wf-123');

            const remaining = engine.getRemainingTasks('wf-123');
            expect(remaining).toHaveLength(1);
            expect(remaining[0].taskId).toBe('problem-understanding/define-problem');
        });

        it('should get next available task', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });
            engine.startWorkflow('wf-123');

            const nextTask = engine.getNextTask('wf-123');
            expect(nextTask).toBeDefined();
            expect(nextTask?.taskId).toBe('problem-understanding/define-problem');
        });
    });

    describe('Serialization', () => {
        it('should serialize instance', () => {
            const template = createTestWorkflowTemplate();
            engine.registerTemplate(template);
            engine.instantiate('new-feature', { workflowId: 'wf-123', createdBy: 'user1' });
            engine.startWorkflow('wf-123');

            const serialized = engine.serializeInstance('wf-123');

            expect(serialized).toBeDefined();
            expect(serialized?.id).toBe('wf-123');
            expect(serialized?.status).toBe('running');
            expect(typeof serialized?.createdAt).toBe('string');
        });

        it('should return undefined for non-existent instance serialization', () => {
            expect(engine.serializeInstance('non-existent')).toBeUndefined();
        });
    });
});
