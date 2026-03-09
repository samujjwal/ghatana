import { describe, it, expect, beforeEach } from 'vitest';

import {
    LifecycleStateMachine,
    createLifecycleStateMachine,
    generateDefaultTransitions,
    STAGE_ORDER,
    STAGE_GROUPS,
    type LifecycleStageConfig,
    type StageTransitionConfig,
} from '../lifecycleStateMachine';

// ============================================================================
// Test Fixtures
// ============================================================================

const createTestStageConfigs = (): LifecycleStageConfig[] => [
    {
        id: 'intent',
        name: 'Intent',
        description: 'Define what you want to achieve',
        order: 0,
        icon: 'lightbulb',
        color: '#8B5CF6',
        required: true,
        entryCriteria: [],
        exitCriteria: ['problem_defined'],
        typicalActivities: ['Problem definition'],
        artifacts: ['Problem statement'],
    },
    {
        id: 'context',
        name: 'Context',
        description: 'Understand the current state',
        order: 1,
        icon: 'explore',
        color: '#6366F1',
        required: true,
        entryCriteria: ['problem_defined'],
        exitCriteria: ['context_understood'],
        typicalActivities: ['Code exploration'],
        artifacts: ['Context document'],
    },
    {
        id: 'plan',
        name: 'Plan',
        description: 'Create a plan of action',
        order: 2,
        icon: 'map',
        color: '#3B82F6',
        required: true,
        entryCriteria: ['context_understood'],
        exitCriteria: ['plan_created'],
        typicalActivities: ['Task breakdown'],
        artifacts: ['Implementation plan'],
    },
    {
        id: 'execute',
        name: 'Execute',
        description: 'Implement the solution',
        order: 3,
        icon: 'rocket_launch',
        color: '#10B981',
        required: true,
        entryCriteria: ['plan_created'],
        exitCriteria: ['implementation_complete'],
        typicalActivities: ['Coding'],
        artifacts: ['Source code'],
    },
    {
        id: 'verify',
        name: 'Verify',
        description: 'Test and validate',
        order: 4,
        icon: 'verified',
        color: '#F59E0B',
        required: true,
        entryCriteria: ['implementation_complete'],
        exitCriteria: ['tests_passed'],
        typicalActivities: ['Testing'],
        artifacts: ['Test results'],
    },
    {
        id: 'observe',
        name: 'Observe',
        description: 'Monitor in production',
        order: 5,
        icon: 'visibility',
        color: '#EF4444',
        required: false,
        entryCriteria: ['tests_passed'],
        exitCriteria: ['metrics_collected'],
        typicalActivities: ['Monitoring'],
        artifacts: ['Metrics'],
    },
    {
        id: 'learn',
        name: 'Learn',
        description: 'Extract learnings',
        order: 6,
        icon: 'school',
        color: '#EC4899',
        required: false,
        entryCriteria: ['metrics_collected'],
        exitCriteria: ['learnings_documented'],
        typicalActivities: ['Retrospective'],
        artifacts: ['Learnings document'],
    },
    {
        id: 'institutionalize',
        name: 'Institutionalize',
        description: 'Codify best practices',
        order: 7,
        icon: 'account_balance',
        color: '#14B8A6',
        required: false,
        entryCriteria: ['learnings_documented'],
        exitCriteria: [],
        typicalActivities: ['Documentation'],
        artifacts: ['Guidelines'],
    },
];

// ============================================================================
// Tests
// ============================================================================

describe('LifecycleStateMachine', () => {
    let stages: LifecycleStageConfig[];
    let transitions: StageTransitionConfig[];

    beforeEach(() => {
        stages = createTestStageConfigs();
        transitions = generateDefaultTransitions();
    });

    describe('Creation', () => {
        it('should create state machine with default initial stage', () => {
            const machine = createLifecycleStateMachine(stages, transitions);

            expect(machine.getCurrentStage()).toBe('intent');
        });

        it('should create state machine with specified initial stage', () => {
            const config = {
                stages,
                transitions,
                hooks: new Map(),
                enforceEntryCriteria: true,
                enforceExitCriteria: true,
                allowSkipTransitions: false,
            };
            const machine = new LifecycleStateMachine(config, 'plan');

            expect(machine.getCurrentStage()).toBe('plan');
        });

        it('should initialize history with entry', () => {
            const machine = createLifecycleStateMachine(stages, transitions);
            const history = machine.getHistory();

            expect(history).toHaveLength(1);
            expect(history[0].stage).toBe('intent');
            expect(history[0].transitionType).toBe('forward');
        });
    });

    describe('Stage Information', () => {
        it('should return current stage config', () => {
            const machine = createLifecycleStateMachine(stages, transitions);
            const config = machine.getCurrentStageConfig();

            expect(config?.id).toBe('intent');
            expect(config?.name).toBe('Intent');
        });

        it('should return correct stage index', () => {
            const machine = createLifecycleStateMachine(stages, transitions);

            expect(machine.getStageIndex('intent')).toBe(0);
            expect(machine.getStageIndex('execute')).toBe(3);
            expect(machine.getStageIndex('institutionalize')).toBe(7);
        });

        it('should identify stage groups correctly', () => {
            const config = {
                stages,
                transitions,
                hooks: new Map(),
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
                allowSkipTransitions: true,
            };

            const machine = new LifecycleStateMachine(config, 'intent');
            expect(machine.isInDiscovery()).toBe(true);
            expect(machine.isInDelivery()).toBe(false);
            expect(machine.isInOperations()).toBe(false);

            // Move to execute
            machine.transitionTo('context', 'user', 'test');
            machine.transitionTo('plan', 'user', 'test');
            machine.transitionTo('execute', 'user', 'test');

            expect(machine.isInDiscovery()).toBe(false);
            expect(machine.isInDelivery()).toBe(true);
        });

        it('should calculate progress correctly', () => {
            const config = {
                stages,
                transitions,
                hooks: new Map(),
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
                allowSkipTransitions: false,
            };

            const machine = new LifecycleStateMachine(config, 'intent');
            expect(machine.getProgress()).toBe(0);

            machine.transitionTo('context', 'user', 'test');
            expect(machine.getProgress()).toBeGreaterThan(0);
            expect(machine.getProgress()).toBeLessThan(100);
        });
    });

    describe('Transition Validation', () => {
        it('should allow forward transitions', () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
            });

            const validation = machine.canTransitionTo('context');
            expect(validation.valid).toBe(true);
            expect(validation.errors).toHaveLength(0);
        });

        it('should not allow undefined transitions', () => {
            const machine = createLifecycleStateMachine(stages, transitions);

            const validation = machine.canTransitionTo('execute'); // Skip from intent
            expect(validation.valid).toBe(false);
            expect(validation.errors.some((e) => e.code === 'TRANSITION_NOT_DEFINED')).toBe(true);
        });

        it('should validate exit criteria when enforced', () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: true,
            });

            const validation = machine.canTransitionTo('context');
            expect(validation.valid).toBe(false);
            expect(validation.errors.some((e) => e.code === 'EXIT_CRITERION_NOT_MET')).toBe(true);
        });

        it('should pass when exit criteria are completed', () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: true,
            });

            machine.completeCriterion('problem_defined');

            const validation = machine.canTransitionTo('context');
            expect(validation.valid).toBe(true);
        });
    });

    describe('Transitions', () => {
        it('should execute forward transition', async () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
            });

            const result = await machine.transitionTo('context', 'user1', 'Moving forward');

            expect(result.success).toBe(true);
            expect(result.previousStage).toBe('intent');
            expect(result.currentStage).toBe('context');
            expect(machine.getCurrentStage()).toBe('context');
        });

        it('should update history on transition', async () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
            });

            await machine.transitionTo('context', 'user1', 'Test reason');

            const history = machine.getHistory();
            expect(history).toHaveLength(2);
            expect(history[0].exitedAt).toBeDefined();
            expect(history[1].stage).toBe('context');
            expect(history[1].triggeredBy).toBe('user1');
            expect(history[1].reason).toBe('Test reason');
        });

        it('should allow backward transitions', async () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
            });

            await machine.transitionTo('context', 'user1');

            const result = await machine.transitionTo('intent', 'user1', 'Going back');

            expect(result.success).toBe(true);
            expect(result.transitionType).toBe('backward');
            expect(machine.getCurrentStage()).toBe('intent');
        });

        it('should add warning for backward transitions', async () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
            });

            await machine.transitionTo('context', 'user1');

            const result = await machine.transitionTo('intent', 'user1');

            expect(result.warnings?.some((w) => w.code === 'BACKWARD_TRANSITION')).toBe(true);
        });

        it('should return errors for invalid transitions', async () => {
            const machine = createLifecycleStateMachine(stages, transitions);

            const result = await machine.transitionTo('execute', 'user1');

            expect(result.success).toBe(false);
            expect(result.errors).toBeDefined();
            expect(result.errors!.length).toBeGreaterThan(0);
        });
    });

    describe('Criteria Management', () => {
        it('should track completed criteria', () => {
            const machine = createLifecycleStateMachine(stages, transitions);

            machine.completeCriterion('problem_defined');
            machine.completeCriterion('context_understood');

            expect(machine.isCriterionCompleted('problem_defined')).toBe(true);
            expect(machine.isCriterionCompleted('context_understood')).toBe(true);
            expect(machine.isCriterionCompleted('plan_created')).toBe(false);
        });

        it('should return all completed criteria', () => {
            const machine = createLifecycleStateMachine(stages, transitions);

            machine.completeCriterion('problem_defined');
            machine.completeCriterion('context_understood');

            const criteria = machine.getCompletedCriteria();
            expect(criteria).toContain('problem_defined');
            expect(criteria).toContain('context_understood');
            expect(criteria).toHaveLength(2);
        });
    });

    describe('Allowed Transitions', () => {
        it('should return allowed transitions from current stage', () => {
            const machine = createLifecycleStateMachine(stages, transitions);

            const allowed = machine.getAllowedTransitions();

            expect(allowed.length).toBeGreaterThan(0);
            expect(allowed.some((t) => t.to === 'context')).toBe(true);
        });
    });

    describe('Serialization', () => {
        it('should serialize state', async () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
            });

            machine.completeCriterion('problem_defined');
            await machine.transitionTo('context', 'user1');

            const serialized = machine.serialize();

            expect(serialized.currentStage).toBe('context');
            expect(serialized.stageHistory).toHaveLength(2);
            expect(serialized.completedCriteria).toContain('problem_defined');
            expect(typeof serialized.stageStartedAt).toBe('string');
        });

        it('should deserialize state', async () => {
            const machine = createLifecycleStateMachine(stages, transitions, {
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
            });

            machine.completeCriterion('problem_defined');
            await machine.transitionTo('context', 'user1');

            const serialized = machine.serialize();

            const config = {
                stages,
                transitions,
                hooks: new Map(),
                enforceEntryCriteria: false,
                enforceExitCriteria: false,
                allowSkipTransitions: false,
            };

            const restored = LifecycleStateMachine.deserialize(config, serialized);

            expect(restored.getCurrentStage()).toBe('context');
            expect(restored.getHistory()).toHaveLength(2);
            expect(restored.isCriterionCompleted('problem_defined')).toBe(true);
        });
    });

    describe('Default Transitions', () => {
        it('should generate forward transitions for all stages', () => {
            const defaultTransitions = generateDefaultTransitions();

            const forwardTransitions = defaultTransitions.filter((t) => t.type === 'forward');

            expect(forwardTransitions.length).toBe(STAGE_ORDER.length - 1);
        });

        it('should generate backward transitions for all stages', () => {
            const defaultTransitions = generateDefaultTransitions();

            const backwardTransitions = defaultTransitions.filter((t) => t.type === 'backward');

            expect(backwardTransitions.length).toBe(STAGE_ORDER.length - 1);
        });

        it('should include skip transitions', () => {
            const defaultTransitions = generateDefaultTransitions();

            const skipTransitions = defaultTransitions.filter((t) => t.type === 'skip');

            expect(skipTransitions.length).toBeGreaterThan(0);
            expect(skipTransitions.every((t) => t.requiresApproval === true)).toBe(true);
        });
    });

    describe('Stage Constants', () => {
        it('should have correct stage order', () => {
            expect(STAGE_ORDER).toEqual([
                'intent',
                'context',
                'plan',
                'execute',
                'verify',
                'observe',
                'learn',
                'institutionalize',
            ]);
        });

        it('should have correct stage groups', () => {
            expect(STAGE_GROUPS.discovery).toContain('intent');
            expect(STAGE_GROUPS.discovery).toContain('context');
            expect(STAGE_GROUPS.discovery).toContain('plan');
            expect(STAGE_GROUPS.delivery).toContain('execute');
            expect(STAGE_GROUPS.delivery).toContain('verify');
            expect(STAGE_GROUPS.operations).toContain('observe');
            expect(STAGE_GROUPS.operations).toContain('learn');
            expect(STAGE_GROUPS.operations).toContain('institutionalize');
        });
    });
});
