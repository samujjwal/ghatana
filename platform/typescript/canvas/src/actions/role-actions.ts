/**
 * Role-Specific Actions
 * 
 * Comprehensive action definitions for each persona role.
 * Supports 9 different roles with specialized actions for each.
 * 
 * @doc.type actions
 * @doc.purpose Role-specific action definitions
 * @doc.layer core
 */

import { ActionDefinition, ActionContext } from '../core/action-registry';

/**
 * Product Owner Actions
 */
export const PRODUCT_OWNER_ACTIONS: ActionDefinition[] = [
    {
        id: 'po-create-user-story',
        label: 'Create User Story',
        icon: '📖',
        shortcut: 'U',
        category: 'role',
        description: 'Create a new user story',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating user story', context);
        },
    },
    {
        id: 'po-define-requirement',
        label: 'Define Requirement',
        icon: '📋',
        shortcut: 'R',
        category: 'role',
        description: 'Define a product requirement',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Defining requirement', context);
        },
    },
    {
        id: 'po-prioritize-backlog',
        label: 'Prioritize Backlog',
        icon: '📊',
        shortcut: 'P',
        category: 'role',
        description: 'Prioritize backlog items',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Prioritizing backlog', context);
        },
    },
    {
        id: 'po-review-progress',
        label: 'Review Progress',
        icon: '👁️',
        shortcut: 'V',
        category: 'role',
        description: 'Review project progress',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Reviewing progress', context);
        },
    },
    {
        id: 'po-accept-deliverable',
        label: 'Accept Deliverable',
        icon: '✓',
        shortcut: 'A',
        category: 'role',
        description: 'Accept completed deliverable',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Accepting deliverable', context);
        },
    },
];

/**
 * Architect Actions
 */
export const ARCHITECT_ACTIONS: ActionDefinition[] = [
    {
        id: 'arch-create-diagram',
        label: 'Create Architecture Diagram',
        icon: '📐',
        shortcut: 'D',
        category: 'role',
        description: 'Create architecture diagram',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating architecture diagram', context);
        },
    },
    {
        id: 'arch-add-service',
        label: 'Add Service',
        icon: '🔷',
        shortcut: 'S',
        category: 'role',
        description: 'Add service to architecture',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding service', context);
        },
    },
    {
        id: 'arch-define-api-contract',
        label: 'Define API Contract',
        icon: '🔌',
        shortcut: 'C',
        category: 'role',
        description: 'Define API contract',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Defining API contract', context);
        },
    },
    {
        id: 'arch-create-adr',
        label: 'Create ADR',
        icon: '📝',
        shortcut: 'A',
        category: 'role',
        description: 'Create Architecture Decision Record',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Creating ADR', context);
        },
    },
    {
        id: 'arch-review-design',
        label: 'Review Design',
        icon: '🔍',
        shortcut: 'R',
        category: 'role',
        description: 'Review system design',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Reviewing design', context);
        },
    },
];

/**
 * Developer Actions
 */
export const DEVELOPER_ACTIONS: ActionDefinition[] = [
    {
        id: 'dev-add-code-block',
        label: 'Add Code Block',
        icon: '💻',
        shortcut: 'C',
        category: 'role',
        description: 'Add a code block',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding code block', context);
        },
    },
    {
        id: 'dev-create-function',
        label: 'Create Function',
        icon: 'ƒ',
        shortcut: 'F',
        category: 'role',
        description: 'Create a function',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Creating function', context);
        },
    },
    {
        id: 'dev-add-test',
        label: 'Add Test',
        icon: '🧪',
        shortcut: 'T',
        category: 'role',
        description: 'Add unit test',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding test', context);
        },
    },
    {
        id: 'dev-fix-bug',
        label: 'Fix Bug',
        icon: '🐛',
        shortcut: 'B',
        category: 'role',
        description: 'Fix a bug',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Fixing bug', context);
        },
    },
    {
        id: 'dev-refactor-code',
        label: 'Refactor Code',
        icon: '🔧',
        shortcut: 'R',
        category: 'role',
        description: 'Refactor existing code',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Refactoring code', context);
        },
    },
];

/**
 * QA Engineer Actions
 */
export const QA_ENGINEER_ACTIONS: ActionDefinition[] = [
    {
        id: 'qa-create-test-case',
        label: 'Create Test Case',
        icon: '🧪',
        shortcut: 'T',
        category: 'role',
        description: 'Create test case',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating test case', context);
        },
    },
    {
        id: 'qa-add-test-scenario',
        label: 'Add Test Scenario',
        icon: '📋',
        shortcut: 'S',
        category: 'role',
        description: 'Add test scenario',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding test scenario', context);
        },
    },
    {
        id: 'qa-report-bug',
        label: 'Report Bug',
        icon: '🐛',
        shortcut: 'B',
        category: 'role',
        description: 'Report a bug',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Reporting bug', context);
        },
    },
    {
        id: 'qa-run-test-suite',
        label: 'Run Test Suite',
        icon: '▶️',
        shortcut: 'R',
        category: 'role',
        description: 'Run test suite',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Running test suite', context);
        },
    },
    {
        id: 'qa-review-quality',
        label: 'Review Quality',
        icon: '✓',
        shortcut: 'Q',
        category: 'role',
        description: 'Review code quality',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Reviewing quality', context);
        },
    },
];

/**
 * DevOps Engineer Actions
 */
export const DEVOPS_ENGINEER_ACTIONS: ActionDefinition[] = [
    {
        id: 'devops-create-pipeline',
        label: 'Create Pipeline',
        icon: '⚡',
        shortcut: 'P',
        category: 'role',
        description: 'Create CI/CD pipeline',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating pipeline', context);
        },
    },
    {
        id: 'devops-add-deployment-config',
        label: 'Add Deployment Config',
        icon: '🚀',
        shortcut: 'D',
        category: 'role',
        description: 'Add deployment configuration',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding deployment config', context);
        },
    },
    {
        id: 'devops-configure-monitoring',
        label: 'Configure Monitoring',
        icon: '📊',
        shortcut: 'M',
        category: 'role',
        description: 'Configure monitoring',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Configuring monitoring', context);
        },
    },
    {
        id: 'devops-set-up-alert',
        label: 'Set Up Alert',
        icon: '🔔',
        shortcut: 'A',
        category: 'role',
        description: 'Set up alert',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Setting up alert', context);
        },
    },
    {
        id: 'devops-deploy-service',
        label: 'Deploy Service',
        icon: '🚀',
        shortcut: 'S',
        category: 'role',
        description: 'Deploy service',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Deploying service', context);
        },
    },
];

/**
 * Security Engineer Actions
 */
export const SECURITY_ENGINEER_ACTIONS: ActionDefinition[] = [
    {
        id: 'sec-add-security-control',
        label: 'Add Security Control',
        icon: '🔒',
        shortcut: 'C',
        category: 'role',
        description: 'Add security control',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Adding security control', context);
        },
    },
    {
        id: 'sec-create-threat-model',
        label: 'Create Threat Model',
        icon: '⚠️',
        shortcut: 'T',
        category: 'role',
        description: 'Create threat model',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Creating threat model', context);
        },
    },
    {
        id: 'sec-add-vulnerability',
        label: 'Add Vulnerability',
        icon: '🐛',
        shortcut: 'V',
        category: 'role',
        description: 'Document vulnerability',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Adding vulnerability', context);
        },
    },
    {
        id: 'sec-review-security',
        label: 'Review Security',
        icon: '🔍',
        shortcut: 'R',
        category: 'role',
        description: 'Review security',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Reviewing security', context);
        },
    },
    {
        id: 'sec-add-compliance-check',
        label: 'Add Compliance Check',
        icon: '✓',
        shortcut: 'P',
        category: 'role',
        description: 'Add compliance check',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Adding compliance check', context);
        },
    },
];

/**
 * UX Designer Actions
 */
export const UX_DESIGNER_ACTIONS: ActionDefinition[] = [
    {
        id: 'ux-create-wireframe',
        label: 'Create Wireframe',
        icon: '📐',
        shortcut: 'W',
        category: 'role',
        description: 'Create wireframe',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating wireframe', context);
        },
    },
    {
        id: 'ux-add-component',
        label: 'Add Component',
        icon: '🧩',
        shortcut: 'C',
        category: 'role',
        description: 'Add UI component',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding component', context);
        },
    },
    {
        id: 'ux-create-user-flow',
        label: 'Create User Flow',
        icon: '🔀',
        shortcut: 'F',
        category: 'role',
        description: 'Create user flow',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Creating user flow', context);
        },
    },
    {
        id: 'ux-add-interaction',
        label: 'Add Interaction',
        icon: '🖱️',
        shortcut: 'I',
        category: 'role',
        description: 'Add interaction',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding interaction', context);
        },
    },
    {
        id: 'ux-create-prototype',
        label: 'Create Prototype',
        icon: '🎨',
        shortcut: 'P',
        category: 'role',
        description: 'Create prototype',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Creating prototype', context);
        },
    },
];

/**
 * Data Engineer Actions
 */
export const DATA_ENGINEER_ACTIONS: ActionDefinition[] = [
    {
        id: 'data-create-pipeline',
        label: 'Create Data Pipeline',
        icon: '⚡',
        shortcut: 'P',
        category: 'role',
        description: 'Create data pipeline',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating data pipeline', context);
        },
    },
    {
        id: 'data-add-data-source',
        label: 'Add Data Source',
        icon: '🗄️',
        shortcut: 'S',
        category: 'role',
        description: 'Add data source',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding data source', context);
        },
    },
    {
        id: 'data-create-etl',
        label: 'Create ETL',
        icon: '🔄',
        shortcut: 'E',
        category: 'role',
        description: 'Create ETL process',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Creating ETL', context);
        },
    },
    {
        id: 'data-add-transformation',
        label: 'Add Transformation',
        icon: '🔧',
        shortcut: 'T',
        category: 'role',
        description: 'Add data transformation',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding transformation', context);
        },
    },
    {
        id: 'data-create-schema',
        label: 'Create Schema',
        icon: '📋',
        shortcut: 'C',
        category: 'role',
        description: 'Create data schema',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Creating schema', context);
        },
    },
];

/**
 * Business Analyst Actions
 */
export const BUSINESS_ANALYST_ACTIONS: ActionDefinition[] = [
    {
        id: 'ba-create-requirement',
        label: 'Create Requirement',
        icon: '📋',
        shortcut: 'R',
        category: 'role',
        description: 'Create business requirement',
        priority: 10,
        handler: async (context: ActionContext) => {
            console.log('Creating requirement', context);
        },
    },
    {
        id: 'ba-add-analysis',
        label: 'Add Analysis',
        icon: '📊',
        shortcut: 'A',
        category: 'role',
        description: 'Add business analysis',
        priority: 9,
        handler: async (context: ActionContext) => {
            console.log('Adding analysis', context);
        },
    },
    {
        id: 'ba-create-process-flow',
        label: 'Create Process Flow',
        icon: '🔀',
        shortcut: 'F',
        category: 'role',
        description: 'Create process flow',
        priority: 8,
        handler: async (context: ActionContext) => {
            console.log('Creating process flow', context);
        },
    },
    {
        id: 'ba-add-kpi',
        label: 'Add KPI',
        icon: '📈',
        shortcut: 'K',
        category: 'role',
        description: 'Add key performance indicator',
        priority: 7,
        handler: async (context: ActionContext) => {
            console.log('Adding KPI', context);
        },
    },
    {
        id: 'ba-create-report',
        label: 'Create Report',
        icon: '📄',
        shortcut: 'P',
        category: 'role',
        description: 'Create business report',
        priority: 6,
        handler: async (context: ActionContext) => {
            console.log('Creating report', context);
        },
    },
];

/**
 * Get all role actions
 */
export function getAllRoleActions(): Record<string, ActionDefinition[]> {
    return {
        product_owner: PRODUCT_OWNER_ACTIONS,
        architect: ARCHITECT_ACTIONS,
        developer: DEVELOPER_ACTIONS,
        qa_engineer: QA_ENGINEER_ACTIONS,
        devops_engineer: DEVOPS_ENGINEER_ACTIONS,
        security_engineer: SECURITY_ENGINEER_ACTIONS,
        ux_designer: UX_DESIGNER_ACTIONS,
        data_engineer: DATA_ENGINEER_ACTIONS,
        business_analyst: BUSINESS_ANALYST_ACTIONS,
    };
}

/**
 * Get actions for specific role
 */
export function getRoleActions(role: string): ActionDefinition[] {
    const allActions = getAllRoleActions();
    return allActions[role] || [];
}
