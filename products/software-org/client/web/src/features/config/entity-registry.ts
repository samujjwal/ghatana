/**
 * Entity Registry - Central registry of all configurable entity types
 *
 * Defines templates, schemas, and metadata for all entity types that can be
 * created and managed within the organization configuration system.
 *
 * @doc.type registry
 * @doc.purpose Entity type definitions and templates
 * @doc.layer product
 * @doc.pattern Registry
 */

import {
    Bot,
    Building2,
    Users,
    Layers,
    GitBranch,
    Server,
    Plug,
    Workflow,
    BarChart3,
    MessageSquare,
    Cog,
    Scroll,
    DollarSign,
    Network,
    type LucideIcon,
} from 'lucide-react';

// ============================================================================
// Entity Field Definitions
// ============================================================================

export type FieldType =
    | 'text'
    | 'textarea'
    | 'number'
    | 'select'
    | 'multiselect'
    | 'tags'
    | 'boolean'
    | 'json'
    | 'color'
    | 'icon';

export interface FieldOption {
    value: string;
    label: string;
    description?: string;
}

export interface EntityField {
    /** Unique field key */
    key: string;
    /** Display label */
    label: string;
    /** Field type */
    type: FieldType;
    /** Is field required */
    required?: boolean;
    /** Placeholder text */
    placeholder?: string;
    /** Help text */
    helpText?: string;
    /** Default value */
    defaultValue?: unknown;
    /** Options for select/multiselect */
    options?: FieldOption[];
    /** Dynamic options loader key */
    optionsFrom?: 'departments' | 'agents' | 'personas' | 'phases' | 'workflows';
    /** Validation rules */
    validation?: {
        min?: number;
        max?: number;
        pattern?: string;
        patternMessage?: string;
    };
    /** Field group/section */
    group?: string;
    /** Display order within group */
    order?: number;
}

// ============================================================================
// Entity Type Definition
// ============================================================================

export interface EntityTypeDefinition {
    /** Unique entity type identifier */
    id: string;
    /** Display name */
    name: string;
    /** Plural display name */
    namePlural: string;
    /** Description */
    description: string;
    /** Icon component */
    icon: LucideIcon;
    /** Color class for styling */
    colorClass: string;
    /** Background color class */
    bgClass: string;
    /** Route path segment */
    routePath: string;
    /** API endpoint */
    apiEndpoint: string;
    /** Fields definition */
    fields: EntityField[];
    /** Field groups for form organization */
    fieldGroups?: {
        id: string;
        label: string;
        description?: string;
    }[];
    /** Whether instances can be created */
    canCreate: boolean;
    /** Whether instances can be edited */
    canEdit: boolean;
    /** Whether instances can be deleted */
    canDelete: boolean;
    /** Primary display field */
    primaryField: string;
    /** Secondary display field */
    secondaryField?: string;
    /** Fields to show in list view */
    listFields: string[];
    /** Search fields */
    searchFields: string[];
    /** Filter fields */
    filterFields?: string[];
}

// ============================================================================
// Entity Type Definitions
// ============================================================================

export const ENTITY_TYPES: Record<string, EntityTypeDefinition> = {
    agent: {
        id: 'agent',
        name: 'Agent',
        namePlural: 'Agents',
        description: 'AI agents that perform automated tasks',
        icon: Bot,
        colorClass: 'text-emerald-600',
        bgClass: 'bg-emerald-100 dark:bg-emerald-900/30',
        routePath: 'agents',
        apiEndpoint: '/api/v1/config/agents',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'role',
        listFields: ['name', 'role', 'department', 'status', 'capabilities'],
        searchFields: ['name', 'role', 'description'],
        filterFields: ['department', 'status'],
        fieldGroups: [
            { id: 'basic', label: 'Basic Information', description: 'Core agent identity' },
            { id: 'behavior', label: 'Behavior', description: 'How the agent operates' },
            { id: 'model', label: 'AI Model', description: 'Model configuration' },
            { id: 'capabilities', label: 'Capabilities', description: 'What the agent can do' },
        ],
        fields: [
            {
                key: 'id',
                label: 'Agent ID',
                type: 'text',
                required: true,
                placeholder: 'agent-my-agent',
                helpText: 'Unique identifier (lowercase, hyphens allowed)',
                group: 'basic',
                order: 1,
                validation: { pattern: '^[a-z][a-z0-9-]*$', patternMessage: 'Must start with letter, only lowercase and hyphens' },
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'My Agent',
                helpText: 'Human-readable name',
                group: 'basic',
                order: 2,
            },
            {
                key: 'role',
                label: 'Role',
                type: 'text',
                required: true,
                placeholder: 'e.g., Code Review Specialist',
                helpText: 'The primary function of this agent',
                group: 'basic',
                order: 3,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                placeholder: 'Describe what this agent does...',
                group: 'basic',
                order: 4,
            },
            {
                key: 'department',
                label: 'Department',
                type: 'select',
                required: true,
                optionsFrom: 'departments',
                helpText: 'Which department this agent belongs to',
                group: 'basic',
                order: 5,
            },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                defaultValue: 'active',
                options: [
                    { value: 'active', label: 'Active', description: 'Agent is operational' },
                    { value: 'idle', label: 'Idle', description: 'Agent is paused' },
                    { value: 'disabled', label: 'Disabled', description: 'Agent is turned off' },
                ],
                group: 'basic',
                order: 6,
            },
            {
                key: 'personality.temperature',
                label: 'Temperature',
                type: 'number',
                defaultValue: 0.7,
                helpText: 'Creativity level (0.0 = deterministic, 1.0 = creative)',
                group: 'behavior',
                order: 1,
                validation: { min: 0, max: 1 },
            },
            {
                key: 'model.id',
                label: 'Model',
                type: 'select',
                required: true,
                options: [
                    { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
                    { value: 'gpt-4o', label: 'GPT-4o' },
                    { value: 'claude-3-opus', label: 'Claude 3 Opus' },
                    { value: 'claude-3-sonnet', label: 'Claude 3 Sonnet' },
                ],
                group: 'model',
                order: 1,
            },
            {
                key: 'model.maxTokens',
                label: 'Max Tokens',
                type: 'number',
                defaultValue: 4096,
                helpText: 'Maximum response length',
                group: 'model',
                order: 2,
                validation: { min: 100, max: 128000 },
            },
            {
                key: 'systemPrompt',
                label: 'System Prompt',
                type: 'textarea',
                placeholder: 'You are an AI assistant that...',
                helpText: 'Instructions for the AI model',
                group: 'behavior',
                order: 2,
            },
            {
                key: 'capabilities',
                label: 'Capabilities',
                type: 'tags',
                placeholder: 'Add capability...',
                helpText: 'Skills and abilities this agent has',
                group: 'capabilities',
                order: 1,
            },
        ],
    },

    department: {
        id: 'department',
        name: 'Department',
        namePlural: 'Departments',
        description: 'Organizational units that group agents',
        icon: Building2,
        colorClass: 'text-blue-600',
        bgClass: 'bg-blue-100 dark:bg-blue-900/30',
        routePath: 'departments',
        apiEndpoint: '/api/v1/config/departments',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'type',
        listFields: ['name', 'type', 'description', 'agentCount'],
        searchFields: ['name', 'description'],
        filterFields: ['type'],
        fieldGroups: [
            { id: 'basic', label: 'Basic Information' },
            { id: 'config', label: 'Configuration' },
        ],
        fields: [
            {
                key: 'id',
                label: 'Department ID',
                type: 'text',
                required: true,
                placeholder: 'engineering',
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'Engineering',
                group: 'basic',
                order: 2,
            },
            {
                key: 'type',
                label: 'Department Type',
                type: 'select',
                required: true,
                options: [
                    { value: 'ENGINEERING', label: 'Engineering' },
                    { value: 'QA', label: 'Quality Assurance' },
                    { value: 'DEVOPS', label: 'DevOps' },
                    { value: 'SECURITY', label: 'Security' },
                    { value: 'PRODUCT', label: 'Product' },
                    { value: 'DATA', label: 'Data' },
                    { value: 'PLATFORM', label: 'Platform' },
                ],
                group: 'basic',
                order: 3,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                placeholder: 'What this department does...',
                group: 'basic',
                order: 4,
            },
        ],
    },

    operator: {
        id: 'operator',
        name: 'Operator',
        namePlural: 'Operators',
        description: 'Domain-specific processing units',
        icon: Cog,
        colorClass: 'text-amber-600',
        bgClass: 'bg-amber-100 dark:bg-amber-900/30',
        routePath: 'operators',
        apiEndpoint: '/api/v1/config/operators',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'domain',
        listFields: ['name', 'domain', 'description', 'modes'],
        searchFields: ['name', 'description', 'domain'],
        filterFields: ['domain'],
        fieldGroups: [
            { id: 'basic', label: 'Basic Information' },
            { id: 'io', label: 'Inputs & Outputs' },
            { id: 'modes', label: 'Operating Modes' },
        ],
        fields: [
            {
                key: 'id',
                label: 'Operator ID',
                type: 'text',
                required: true,
                placeholder: 'build-operator',
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'Build Operator',
                group: 'basic',
                order: 2,
            },
            {
                key: 'domain',
                label: 'Domain',
                type: 'select',
                required: true,
                options: [
                    { value: 'build', label: 'Build' },
                    { value: 'test', label: 'Test' },
                    { value: 'deploy', label: 'Deploy' },
                    { value: 'security', label: 'Security' },
                    { value: 'observe', label: 'Observe' },
                ],
                group: 'basic',
                order: 3,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                placeholder: 'What this operator does...',
                group: 'basic',
                order: 4,
            },
            {
                key: 'version',
                label: 'Version',
                type: 'text',
                defaultValue: '1.0.0',
                group: 'basic',
                order: 5,
            },
        ],
    },

    workflow: {
        id: 'workflow',
        name: 'Workflow',
        namePlural: 'Workflows',
        description: 'Automated process definitions',
        icon: Workflow,
        colorClass: 'text-rose-600',
        bgClass: 'bg-rose-100 dark:bg-rose-900/30',
        routePath: 'workflows',
        apiEndpoint: '/api/v1/config/workflows',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'trigger',
        listFields: ['name', 'trigger', 'steps', 'status'],
        searchFields: ['name', 'description'],
        filterFields: ['status'],
        fieldGroups: [
            { id: 'basic', label: 'Basic Information' },
            { id: 'trigger', label: 'Trigger Configuration' },
            { id: 'steps', label: 'Workflow Steps' },
        ],
        fields: [
            {
                key: 'id',
                label: 'Workflow ID',
                type: 'text',
                required: true,
                placeholder: 'wf-ci-cd',
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'CI/CD Pipeline',
                group: 'basic',
                order: 2,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                placeholder: 'What this workflow does...',
                group: 'basic',
                order: 3,
            },
            {
                key: 'trigger.event',
                label: 'Trigger Event',
                type: 'select',
                options: [
                    { value: 'git.push', label: 'Git Push' },
                    { value: 'git.pull_request', label: 'Pull Request' },
                    { value: 'schedule', label: 'Schedule' },
                    { value: 'manual', label: 'Manual' },
                    { value: 'webhook', label: 'Webhook' },
                ],
                group: 'trigger',
                order: 1,
            },
        ],
    },

    persona: {
        id: 'persona',
        name: 'Persona',
        namePlural: 'Personas',
        description: 'Role-based access profiles',
        icon: Users,
        colorClass: 'text-purple-600',
        bgClass: 'bg-purple-100 dark:bg-purple-900/30',
        routePath: 'personas',
        apiEndpoint: '/api/v1/config/personas',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'display_name',
        secondaryField: 'id',
        listFields: ['display_name', 'tags', 'permissions'],
        searchFields: ['display_name', 'id'],
        filterFields: ['tags'],
        fields: [
            {
                key: 'id',
                label: 'Persona ID',
                type: 'text',
                required: true,
                placeholder: 'backend_engineer',
                group: 'basic',
                order: 1,
            },
            {
                key: 'display_name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'Backend Engineer',
                group: 'basic',
                order: 2,
            },
            {
                key: 'tags',
                label: 'Tags',
                type: 'tags',
                placeholder: 'Add tag...',
                group: 'basic',
                order: 3,
            },
            {
                key: 'permissions',
                label: 'Permissions',
                type: 'tags',
                placeholder: 'Add permission...',
                group: 'basic',
                order: 4,
            },
        ],
    },

    service: {
        id: 'service',
        name: 'Service',
        namePlural: 'Services',
        description: 'Microservices in the system',
        icon: Server,
        colorClass: 'text-cyan-600',
        bgClass: 'bg-cyan-100 dark:bg-cyan-900/30',
        routePath: 'services',
        apiEndpoint: '/api/v1/config/services',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'type',
        listFields: ['name', 'type', 'status', 'url'],
        searchFields: ['name', 'description'],
        filterFields: ['type', 'status'],
        fields: [
            {
                key: 'id',
                label: 'Service ID',
                type: 'text',
                required: true,
                placeholder: 'api-gateway',
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'API Gateway',
                group: 'basic',
                order: 2,
            },
            {
                key: 'type',
                label: 'Service Type',
                type: 'select',
                options: [
                    { value: 'api', label: 'API' },
                    { value: 'worker', label: 'Worker' },
                    { value: 'scheduler', label: 'Scheduler' },
                    { value: 'gateway', label: 'Gateway' },
                ],
                group: 'basic',
                order: 3,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                group: 'basic',
                order: 4,
            },
            {
                key: 'url',
                label: 'URL',
                type: 'text',
                placeholder: 'https://...',
                group: 'basic',
                order: 5,
            },
        ],
    },

    integration: {
        id: 'integration',
        name: 'Integration',
        namePlural: 'Integrations',
        description: 'External tool connections',
        icon: Plug,
        colorClass: 'text-pink-600',
        bgClass: 'bg-pink-100 dark:bg-pink-900/30',
        routePath: 'integrations',
        apiEndpoint: '/api/v1/config/integrations',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'type',
        listFields: ['name', 'type', 'enabled', 'capabilities'],
        searchFields: ['name', 'description'],
        filterFields: ['type', 'enabled'],
        fields: [
            {
                key: 'id',
                label: 'Integration ID',
                type: 'text',
                required: true,
                placeholder: 'github',
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'GitHub',
                group: 'basic',
                order: 2,
            },
            {
                key: 'type',
                label: 'Integration Type',
                type: 'select',
                options: [
                    { value: 'scm', label: 'Source Control' },
                    { value: 'ci', label: 'CI/CD' },
                    { value: 'monitoring', label: 'Monitoring' },
                    { value: 'communication', label: 'Communication' },
                ],
                group: 'basic',
                order: 3,
            },
            {
                key: 'enabled',
                label: 'Enabled',
                type: 'boolean',
                defaultValue: true,
                group: 'basic',
                order: 4,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                group: 'basic',
                order: 5,
            },
        ],
    },

    kpi: {
        id: 'kpi',
        name: 'KPI',
        namePlural: 'KPIs',
        description: 'Key Performance Indicators',
        icon: BarChart3,
        colorClass: 'text-violet-600',
        bgClass: 'bg-violet-100 dark:bg-violet-900/30',
        routePath: 'kpis',
        apiEndpoint: '/api/v1/config/kpis',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'category',
        listFields: ['name', 'category', 'target', 'unit'],
        searchFields: ['name', 'description'],
        filterFields: ['category'],
        fields: [
            {
                key: 'id',
                label: 'KPI ID',
                type: 'text',
                required: true,
                placeholder: 'deployment-frequency',
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'Deployment Frequency',
                group: 'basic',
                order: 2,
            },
            {
                key: 'category',
                label: 'Category',
                type: 'select',
                options: [
                    { value: 'performance', label: 'Performance' },
                    { value: 'quality', label: 'Quality' },
                    { value: 'reliability', label: 'Reliability' },
                    { value: 'velocity', label: 'Velocity' },
                ],
                group: 'basic',
                order: 3,
            },
            {
                key: 'target',
                label: 'Target Value',
                type: 'text',
                placeholder: '10',
                group: 'basic',
                order: 4,
            },
            {
                key: 'unit',
                label: 'Unit',
                type: 'text',
                placeholder: 'per day',
                group: 'basic',
                order: 5,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                group: 'basic',
                order: 6,
            },
        ],
    },

    interaction: {
        id: 'interaction',
        name: 'Interaction',
        namePlural: 'Interactions',
        description: 'Agent interaction patterns',
        icon: MessageSquare,
        colorClass: 'text-teal-600',
        bgClass: 'bg-teal-100 dark:bg-teal-900/30',
        routePath: 'interactions',
        apiEndpoint: '/api/v1/config/interactions',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'type',
        listFields: ['name', 'type', 'participants'],
        searchFields: ['name', 'description'],
        filterFields: ['type'],
        fields: [
            {
                key: 'id',
                label: 'Interaction ID',
                type: 'text',
                required: true,
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                group: 'basic',
                order: 2,
            },
            {
                key: 'type',
                label: 'Interaction Type',
                type: 'select',
                options: [
                    { value: 'request-response', label: 'Request/Response' },
                    { value: 'broadcast', label: 'Broadcast' },
                    { value: 'delegation', label: 'Delegation' },
                    { value: 'collaboration', label: 'Collaboration' },
                ],
                group: 'basic',
                order: 3,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                group: 'basic',
                order: 4,
            },
        ],
    },

    phase: {
        id: 'phase',
        name: 'Phase',
        namePlural: 'Phases',
        description: 'Lifecycle phases',
        icon: Layers,
        colorClass: 'text-green-600',
        bgClass: 'bg-green-100 dark:bg-green-900/30',
        routePath: 'phases',
        apiEndpoint: '/api/v1/config/phases',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'display_name',
        secondaryField: 'id',
        listFields: ['display_name', 'personas', 'description'],
        searchFields: ['display_name', 'id', 'description'],
        fields: [
            {
                key: 'id',
                label: 'Phase ID',
                type: 'text',
                required: true,
                placeholder: 'PRODUCT_LIFECYCLE_PHASE_BUILD',
                group: 'basic',
                order: 1,
            },
            {
                key: 'display_name',
                label: 'Display Name',
                type: 'text',
                required: true,
                placeholder: 'Build & Integrate',
                group: 'basic',
                order: 2,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                group: 'basic',
                order: 3,
            },
            {
                key: 'personas',
                label: 'Assigned Personas',
                type: 'multiselect',
                optionsFrom: 'personas',
                group: 'basic',
                order: 4,
            },
        ],
    },

    stage: {
        id: 'stage',
        name: 'Stage',
        namePlural: 'Stages',
        description: 'Stage-to-phase mappings',
        icon: GitBranch,
        colorClass: 'text-orange-600',
        bgClass: 'bg-orange-100 dark:bg-orange-900/30',
        routePath: 'stages',
        apiEndpoint: '/api/v1/config/stages',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'stage',
        listFields: ['stage', 'phases'],
        searchFields: ['stage'],
        fields: [
            {
                key: 'stage',
                label: 'Stage Name',
                type: 'text',
                required: true,
                placeholder: 'stage-develop',
                group: 'basic',
                order: 1,
            },
            {
                key: 'phases',
                label: 'Mapped Phases',
                type: 'multiselect',
                optionsFrom: 'phases',
                group: 'basic',
                order: 2,
            },
        ],
    },

    flow: {
        id: 'flow',
        name: 'Flow',
        namePlural: 'Flows',
        description: 'DevSecOps process flows',
        icon: Workflow,
        colorClass: 'text-indigo-600',
        bgClass: 'bg-indigo-100 dark:bg-indigo-900/30',
        routePath: 'flows',
        apiEndpoint: '/api/v1/config/flows',
        canCreate: true,
        canEdit: true,
        canDelete: true,
        primaryField: 'name',
        secondaryField: 'persona_id',
        listFields: ['name', 'persona_id', 'steps'],
        searchFields: ['name', 'description'],
        fields: [
            {
                key: 'id',
                label: 'Flow ID',
                type: 'text',
                required: true,
                group: 'basic',
                order: 1,
            },
            {
                key: 'name',
                label: 'Display Name',
                type: 'text',
                required: true,
                group: 'basic',
                order: 2,
            },
            {
                key: 'persona_id',
                label: 'Target Persona',
                type: 'select',
                optionsFrom: 'personas',
                group: 'basic',
                order: 3,
            },
            {
                key: 'description',
                label: 'Description',
                type: 'textarea',
                group: 'basic',
                order: 4,
            },
        ],
    },

    norm: {
        id: 'norm',
        name: 'Norm',
        namePlural: 'Norms',
        description: 'Organization rules and policies',
        icon: Scroll,
        colorClass: 'text-amber-600',
        bgClass: 'bg-amber-100 dark:bg-amber-900/30',
        routePath: '../manage/norms',
        apiEndpoint: '/api/v1/norms',
        canCreate: false,
        canEdit: true,
        canDelete: true,
        primaryField: 'title',
        listFields: ['title', 'description'],
        searchFields: ['title', 'description'],
        fields: [],
    },

    budget: {
        id: 'budget',
        name: 'Budget',
        namePlural: 'Budgets',
        description: 'Resource allocation and planning',
        icon: DollarSign,
        colorClass: 'text-emerald-600',
        bgClass: 'bg-emerald-100 dark:bg-emerald-900/30',
        routePath: '../manage/budget',
        apiEndpoint: '/api/v1/budgets',
        canCreate: false,
        canEdit: true,
        canDelete: false,
        primaryField: 'year',
        listFields: ['year', 'allocated', 'spent'],
        searchFields: ['year'],
        fields: [],
    },

    orgChart: {
        id: 'org-chart',
        name: 'Org Chart',
        namePlural: 'Org Charts',
        description: 'Visual organization hierarchy',
        icon: Network,
        colorClass: 'text-purple-600',
        bgClass: 'bg-purple-100 dark:bg-purple-900/30',
        routePath: '../manage/org-chart',
        apiEndpoint: '/api/v1/org-chart',
        canCreate: false,
        canEdit: true,
        canDelete: false,
        primaryField: 'name',
        listFields: [],
        searchFields: [],
        fields: [],
    },
};

// ============================================================================
// Helper Functions
// ============================================================================

export function getEntityType(typeId: string): EntityTypeDefinition | undefined {
    return ENTITY_TYPES[typeId];
}

export function getAllEntityTypes(): EntityTypeDefinition[] {
    return Object.values(ENTITY_TYPES);
}

export function getCreatableEntityTypes(): EntityTypeDefinition[] {
    return Object.values(ENTITY_TYPES).filter(t => t.canCreate);
}

export function getEntityTypeByRoutePath(routePath: string): EntityTypeDefinition | undefined {
    return Object.values(ENTITY_TYPES).find(t => t.routePath === routePath);
}
