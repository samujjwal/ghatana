/**
 * Phase-Specific Actions
 * 
 * Comprehensive action definitions for each lifecycle phase.
 * Actions support the YAPPC workflow: INTENT → SHAPE → VALIDATE → GENERATE → RUN → OBSERVE → IMPROVE
 * 
 * @doc.type actions
 * @doc.purpose Phase-specific action definitions
 * @doc.layer core
 */

import { ActionDefinition, ActionContext } from '../core/action-registry';
import { LifecyclePhase } from '../chrome';

/**
 * INTENT Phase Actions
 * Focus: Ideation, requirements, vision
 */
export const INTENT_ACTIONS: ActionDefinition[] = [
    {
        id: 'intent-create-vision',
        label: 'Create Vision Statement',
        icon: '👁️',
        shortcut: 'V',
        category: 'phase',
        description: 'Create a vision statement for the project',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating vision statement', context);
        },
        isEnabled: (context) => context.phase === 'INTENT',
        isVisible: (context) => context.phase === 'INTENT',
    },
    {
        id: 'intent-add-user-story',
        label: 'Add User Story',
        icon: '📖',
        shortcut: 'U',
        category: 'phase',
        description: 'Add a user story',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding user story', context);
        },
        isEnabled: (context) => context.phase === 'INTENT',
        isVisible: (context) => context.phase === 'INTENT',
    },
    {
        id: 'intent-define-requirement',
        label: 'Define Requirement',
        icon: '📋',
        shortcut: 'R',
        category: 'phase',
        description: 'Define a functional or non-functional requirement',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Defining requirement', context);
        },
        isEnabled: (context) => context.phase === 'INTENT',
        isVisible: (context) => context.phase === 'INTENT',
    },
    {
        id: 'intent-add-stakeholder',
        label: 'Add Stakeholder',
        icon: '👤',
        shortcut: 'S',
        category: 'phase',
        description: 'Add a project stakeholder',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding stakeholder', context);
        },
        isEnabled: (context) => context.phase === 'INTENT',
        isVisible: (context) => context.phase === 'INTENT',
    },
    {
        id: 'intent-create-goal',
        label: 'Create Goal',
        icon: '🎯',
        shortcut: 'G',
        category: 'phase',
        description: 'Create a project goal',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Creating goal', context);
        },
        isEnabled: (context) => context.phase === 'INTENT',
        isVisible: (context) => context.phase === 'INTENT',
    },
];

/**
 * SHAPE Phase Actions
 * Focus: Architecture, design, structure
 */
export const SHAPE_ACTIONS: ActionDefinition[] = [
    {
        id: 'shape-create-diagram',
        label: 'Create Architecture Diagram',
        icon: '📐',
        shortcut: 'D',
        category: 'phase',
        description: 'Create an architecture diagram',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating architecture diagram', context);
        },
        isEnabled: (context) => context.phase === 'SHAPE',
        isVisible: (context) => context.phase === 'SHAPE',
    },
    {
        id: 'shape-add-service',
        label: 'Add Service',
        icon: '🔷',
        shortcut: 'S',
        category: 'phase',
        description: 'Add a service to the architecture',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding service', context);
        },
        isEnabled: (context) => context.phase === 'SHAPE',
        isVisible: (context) => context.phase === 'SHAPE',
    },
    {
        id: 'shape-define-api-contract',
        label: 'Define API Contract',
        icon: '🔌',
        shortcut: 'A',
        category: 'phase',
        description: 'Define an API contract',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Defining API contract', context);
        },
        isEnabled: (context) => context.phase === 'SHAPE',
        isVisible: (context) => context.phase === 'SHAPE',
    },
    {
        id: 'shape-add-data-model',
        label: 'Add Data Model',
        icon: '🗄️',
        shortcut: 'M',
        category: 'phase',
        description: 'Add a data model',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding data model', context);
        },
        isEnabled: (context) => context.phase === 'SHAPE',
        isVisible: (context) => context.phase === 'SHAPE',
    },
    {
        id: 'shape-create-component',
        label: 'Create Component',
        icon: '🧩',
        shortcut: 'C',
        category: 'phase',
        description: 'Create a component design',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Creating component', context);
        },
        isEnabled: (context) => context.phase === 'SHAPE',
        isVisible: (context) => context.phase === 'SHAPE',
    },
];

/**
 * VALIDATE Phase Actions
 * Focus: Testing, verification, validation
 */
export const VALIDATE_ACTIONS: ActionDefinition[] = [
    {
        id: 'validate-add-rule',
        label: 'Add Validation Rule',
        icon: '✓',
        shortcut: 'R',
        category: 'phase',
        description: 'Add a validation rule',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding validation rule', context);
        },
        isEnabled: (context) => context.phase === 'VALIDATE',
        isVisible: (context) => context.phase === 'VALIDATE',
    },
    {
        id: 'validate-create-test-case',
        label: 'Create Test Case',
        icon: '🧪',
        shortcut: 'T',
        category: 'phase',
        description: 'Create a test case',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Creating test case', context);
        },
        isEnabled: (context) => context.phase === 'VALIDATE',
        isVisible: (context) => context.phase === 'VALIDATE',
    },
    {
        id: 'validate-add-acceptance-criteria',
        label: 'Add Acceptance Criteria',
        icon: '📝',
        shortcut: 'A',
        category: 'phase',
        description: 'Add acceptance criteria',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding acceptance criteria', context);
        },
        isEnabled: (context) => context.phase === 'VALIDATE',
        isVisible: (context) => context.phase === 'VALIDATE',
    },
    {
        id: 'validate-run-simulation',
        label: 'Run Simulation',
        icon: '▶️',
        shortcut: 'S',
        category: 'phase',
        description: 'Run a simulation or test',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Running simulation', context);
        },
        isEnabled: (context) => context.phase === 'VALIDATE',
        isVisible: (context) => context.phase === 'VALIDATE',
    },
    {
        id: 'validate-add-review-comment',
        label: 'Add Review Comment',
        icon: '💬',
        shortcut: 'C',
        category: 'phase',
        description: 'Add a review comment',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Adding review comment', context);
        },
        isEnabled: (context) => context.phase === 'VALIDATE',
        isVisible: (context) => context.phase === 'VALIDATE',
    },
];

/**
 * GENERATE Phase Actions
 * Focus: Code generation, scaffolding
 */
export const GENERATE_ACTIONS: ActionDefinition[] = [
    {
        id: 'generate-code',
        label: 'Generate Code',
        icon: '⚙️',
        shortcut: 'G',
        category: 'phase',
        description: 'Generate code from design',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Generating code', context);
        },
        isEnabled: (context) => context.phase === 'GENERATE',
        isVisible: (context) => context.phase === 'GENERATE',
    },
    {
        id: 'generate-create-scaffold',
        label: 'Create Scaffold',
        icon: '🏗️',
        shortcut: 'S',
        category: 'phase',
        description: 'Create project scaffold',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Creating scaffold', context);
        },
        isEnabled: (context) => context.phase === 'GENERATE',
        isVisible: (context) => context.phase === 'GENERATE',
    },
    {
        id: 'generate-add-configuration',
        label: 'Add Configuration',
        icon: '⚙️',
        shortcut: 'C',
        category: 'phase',
        description: 'Add configuration files',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding configuration', context);
        },
        isEnabled: (context) => context.phase === 'GENERATE',
        isVisible: (context) => context.phase === 'GENERATE',
    },
    {
        id: 'generate-tests',
        label: 'Generate Tests',
        icon: '🧪',
        shortcut: 'T',
        category: 'phase',
        description: 'Generate test files',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Generating tests', context);
        },
        isEnabled: (context) => context.phase === 'GENERATE',
        isVisible: (context) => context.phase === 'GENERATE',
    },
    {
        id: 'generate-deployment-config',
        label: 'Create Deployment Config',
        icon: '🚀',
        shortcut: 'D',
        category: 'phase',
        description: 'Create deployment configuration',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Creating deployment config', context);
        },
        isEnabled: (context) => context.phase === 'GENERATE',
        isVisible: (context) => context.phase === 'GENERATE',
    },
];

/**
 * RUN Phase Actions
 * Focus: Execution, deployment, operations
 */
export const RUN_ACTIONS: ActionDefinition[] = [
    {
        id: 'run-deploy-service',
        label: 'Deploy Service',
        icon: '🚀',
        shortcut: 'D',
        category: 'phase',
        description: 'Deploy service to environment',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Deploying service', context);
        },
        isEnabled: (context) => context.phase === 'RUN',
        isVisible: (context) => context.phase === 'RUN',
    },
    {
        id: 'run-start-container',
        label: 'Start Container',
        icon: '📦',
        shortcut: 'C',
        category: 'phase',
        description: 'Start container instance',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Starting container', context);
        },
        isEnabled: (context) => context.phase === 'RUN',
        isVisible: (context) => context.phase === 'RUN',
    },
    {
        id: 'run-execute-tests',
        label: 'Run Tests',
        icon: '🧪',
        shortcut: 'T',
        category: 'phase',
        description: 'Execute test suite',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Running tests', context);
        },
        isEnabled: (context) => context.phase === 'RUN',
        isVisible: (context) => context.phase === 'RUN',
    },
    {
        id: 'run-execute-pipeline',
        label: 'Execute Pipeline',
        icon: '⚡',
        shortcut: 'P',
        category: 'phase',
        description: 'Execute CI/CD pipeline',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Executing pipeline', context);
        },
        isEnabled: (context) => context.phase === 'RUN',
        isVisible: (context) => context.phase === 'RUN',
    },
    {
        id: 'run-monitor-logs',
        label: 'Monitor Logs',
        icon: '📝',
        shortcut: 'L',
        category: 'phase',
        description: 'Monitor application logs',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Monitoring logs', context);
        },
        isEnabled: (context) => context.phase === 'RUN',
        isVisible: (context) => context.phase === 'RUN',
    },
];

/**
 * OBSERVE Phase Actions
 * Focus: Monitoring, metrics, observability
 */
export const OBSERVE_ACTIONS: ActionDefinition[] = [
    {
        id: 'observe-add-metric',
        label: 'Add Metric',
        icon: '📊',
        shortcut: 'M',
        category: 'phase',
        description: 'Add monitoring metric',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding metric', context);
        },
        isEnabled: (context) => context.phase === 'OBSERVE',
        isVisible: (context) => context.phase === 'OBSERVE',
    },
    {
        id: 'observe-create-dashboard',
        label: 'Create Dashboard',
        icon: '📈',
        shortcut: 'D',
        category: 'phase',
        description: 'Create monitoring dashboard',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Creating dashboard', context);
        },
        isEnabled: (context) => context.phase === 'OBSERVE',
        isVisible: (context) => context.phase === 'OBSERVE',
    },
    {
        id: 'observe-set-alert',
        label: 'Set Alert',
        icon: '🔔',
        shortcut: 'A',
        category: 'phase',
        description: 'Set up alert rule',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Setting alert', context);
        },
        isEnabled: (context) => context.phase === 'OBSERVE',
        isVisible: (context) => context.phase === 'OBSERVE',
    },
    {
        id: 'observe-view-traces',
        label: 'View Traces',
        icon: '🔍',
        shortcut: 'T',
        category: 'phase',
        description: 'View distributed traces',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Viewing traces', context);
        },
        isEnabled: (context) => context.phase === 'OBSERVE',
        isVisible: (context) => context.phase === 'OBSERVE',
    },
    {
        id: 'observe-analyze-performance',
        label: 'Analyze Performance',
        icon: '⚡',
        shortcut: 'P',
        category: 'phase',
        description: 'Analyze performance metrics',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Analyzing performance', context);
        },
        isEnabled: (context) => context.phase === 'OBSERVE',
        isVisible: (context) => context.phase === 'OBSERVE',
    },
];

/**
 * IMPROVE Phase Actions
 * Focus: Optimization, refactoring, enhancement
 */
export const IMPROVE_ACTIONS: ActionDefinition[] = [
    {
        id: 'improve-create-enhancement',
        label: 'Create Enhancement',
        icon: '✨',
        shortcut: 'E',
        category: 'phase',
        description: 'Create enhancement proposal',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating enhancement', context);
        },
        isEnabled: (context) => context.phase === 'IMPROVE',
        isVisible: (context) => context.phase === 'IMPROVE',
    },
    {
        id: 'improve-add-optimization',
        label: 'Add Optimization',
        icon: '⚡',
        shortcut: 'O',
        category: 'phase',
        description: 'Add performance optimization',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding optimization', context);
        },
        isEnabled: (context) => context.phase === 'IMPROVE',
        isVisible: (context) => context.phase === 'IMPROVE',
    },
    {
        id: 'improve-refactor-code',
        label: 'Refactor Code',
        icon: '🔧',
        shortcut: 'R',
        category: 'phase',
        description: 'Refactor existing code',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Refactoring code', context);
        },
        isEnabled: (context) => context.phase === 'IMPROVE',
        isVisible: (context) => context.phase === 'IMPROVE',
    },
    {
        id: 'improve-update-architecture',
        label: 'Update Architecture',
        icon: '🏗️',
        shortcut: 'A',
        category: 'phase',
        description: 'Update system architecture',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Updating architecture', context);
        },
        isEnabled: (context) => context.phase === 'IMPROVE',
        isVisible: (context) => context.phase === 'IMPROVE',
    },
    {
        id: 'improve-add-feature',
        label: 'Add Feature',
        icon: '🎁',
        shortcut: 'F',
        category: 'phase',
        description: 'Add new feature',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Adding feature', context);
        },
        isEnabled: (context) => context.phase === 'IMPROVE',
        isVisible: (context) => context.phase === 'IMPROVE',
    },
];

/**
 * Get all phase actions
 */
export function getAllPhaseActions(): Record<LifecyclePhase, ActionDefinition[]> {
    return {
        INTENT: INTENT_ACTIONS,
        SHAPE: SHAPE_ACTIONS,
        VALIDATE: VALIDATE_ACTIONS,
        GENERATE: GENERATE_ACTIONS,
        RUN: RUN_ACTIONS,
        OBSERVE: OBSERVE_ACTIONS,
        IMPROVE: IMPROVE_ACTIONS,
    };
}

/**
 * Get actions for specific phase
 */
export function getPhaseActions(phase: LifecyclePhase): ActionDefinition[] {
    const allActions = getAllPhaseActions();
    return allActions[phase] || [];
}
