import { useState, useCallback } from 'react';

// Type definitions
export interface TaskDomain {
    id: string;
    name: string;
    personas?: string[];
    description?: string;
    icon?: string;
}

export interface Workflow {
    id: string;
    name: string;
    persona?: string;
    description?: string;
}

export interface Phase {
    id: string;
    name: string;
    primary_personas?: string[];
    secondary_personas?: string[];
    description?: string;
}

export interface LifecycleConfig {
    phases?: Phase[];
    domains?: TaskDomain[];
}

export interface AgentCapabilities {
    agents: unknown[];
    capabilities: unknown[];
}

// Mock data - default values until API is implemented
const MOCK_DOMAINS: TaskDomain[] = [
    {
        id: 'development',
        name: 'Development',
        personas: ['developer', 'architect'],
        description: 'Software development and design',
        icon: 'code'
    },
    {
        id: 'security',
        name: 'Security',
        personas: ['security-engineer'],
        description: 'Security practices and compliance',
        icon: 'shield'
    },
    {
        id: 'operations',
        name: 'Operations',
        personas: ['devops-engineer', 'site-reliability-engineer'],
        description: 'Infrastructure and operations',
        icon: 'settings'
    },
    {
        id: 'quality',
        name: 'Quality',
        personas: ['qa-engineer', 'developer'],
        description: 'Testing and quality assurance',
        icon: 'check_circle'
    },
    {
        id: 'product',
        name: 'Product',
        personas: ['product-manager'],
        description: 'Product management and strategy',
        icon: 'target'
    }
];

const MOCK_WORKFLOWS: Workflow[] = [
    {
        id: 'agile-dev-workflow',
        name: 'Agile Development',
        persona: 'developer',
        description: 'Standard agile development workflow'
    },
    {
        id: 'security-review',
        name: 'Security Review',
        persona: 'security-engineer',
        description: 'Security code review workflow'
    },
    {
        id: 'deployment-pipeline',
        name: 'Deployment Pipeline',
        persona: 'devops-engineer',
        description: 'CI/CD deployment workflow'
    },
    {
        id: 'test-automation',
        name: 'Test Automation',
        persona: 'qa-engineer',
        description: 'Automated testing workflow'
    },
    {
        id: 'release-management',
        name: 'Release Management',
        persona: 'product-manager',
        description: 'Product release workflow'
    }
];

const MOCK_LIFECYCLE_CONFIG: LifecycleConfig = {
    phases: [
        {
            id: 'intake',
            name: 'Intake',
            primary_personas: ['product-manager'],
            secondary_personas: ['developer']
        },
        {
            id: 'plan',
            name: 'Plan',
            primary_personas: ['product-manager'],
            secondary_personas: ['architect']
        },
        {
            id: 'develop',
            name: 'Develop',
            primary_personas: ['developer'],
            secondary_personas: ['architect']
        },
        {
            id: 'secure',
            name: 'Secure',
            primary_personas: ['security-engineer'],
            secondary_personas: ['developer']
        },
        {
            id: 'test',
            name: 'Test',
            primary_personas: ['qa-engineer'],
            secondary_personas: ['developer']
        },
        {
            id: 'deploy',
            name: 'Deploy',
            primary_personas: ['devops-engineer'],
            secondary_personas: ['site-reliability-engineer']
        },
        {
            id: 'monitor',
            name: 'Monitor',
            primary_personas: ['site-reliability-engineer'],
            secondary_personas: ['devops-engineer']
        },
        {
            id: 'operate',
            name: 'Operate',
            primary_personas: ['site-reliability-engineer'],
            secondary_personas: ['devops-engineer']
        },
        {
            id: 'govern',
            name: 'Govern',
            primary_personas: ['product-manager'],
            secondary_personas: ['security-engineer']
        }
    ]
};

// Hooks - returning mock data until API is implemented
export const useTaskDomains = (): TaskDomain[] => {
    return MOCK_DOMAINS;
};

export const useDomainById = (id: string): TaskDomain | undefined => {
    return MOCK_DOMAINS.find(d => d.id === id);
};

export const useWorkflows = (): Workflow[] => {
    return MOCK_WORKFLOWS;
};

export const useWorkflowById = (id: string): Workflow | undefined => {
    return MOCK_WORKFLOWS.find(w => w.id === id);
};

export const useLifecycleConfig = (): LifecycleConfig => {
    return MOCK_LIFECYCLE_CONFIG;
};

export const useAgentCapabilities = (): AgentCapabilities => {
    return {
        agents: [],
        capabilities: []
    };
};

export const useAllTasks = (): unknown[] => {
    return [
        {
            id: 'task-1',
            name: 'Setup development environment',
            persona: 'developer',
            description: 'Initial setup of dev environment'
        },
        {
            id: 'task-2',
            name: 'Code review',
            persona: 'developer',
            description: 'Review pull requests'
        },
        {
            id: 'task-3',
            name: 'Security audit',
            persona: 'security-engineer',
            description: 'Perform security audit'
        },
        {
            id: 'task-4',
            name: 'Deploy to production',
            persona: 'devops-engineer',
            description: 'Deploy to production environment'
        }
    ];
};

// Refresh hook
export const useConfigRefresh = () => {
    const [isRefreshing, setIsRefreshing] = useState(false);

    const refreshAll = useCallback(async () => {
        setIsRefreshing(true);
        try {
            // TODO: Implement actual API refresh calls
            console.log('Refreshing configuration...');
        } finally {
            setIsRefreshing(false);
        }
    }, []);

    return {
        refreshDomains: refreshAll,
        refreshWorkflows: refreshAll,
        refreshAll,
        isRefreshing
    };
};
