/**
 * Product-local DevSecOps board types for Software Org.
 *
 * These types intentionally model only the surface used by the
 * Software Org operate dashboard so the product no longer depends on
 * YAPPC frontend packages for board rendering.
 *
 * @doc.type types
 * @doc.purpose Product-local DevSecOps board types
 * @doc.layer product
 * @doc.pattern TypeScript Types
 */

export type PhaseKey =
    | 'ideation'
    | 'planning'
    | 'development'
    | 'security'
    | 'testing'
    | 'deployment'
    | 'operations';

export type Priority = 'low' | 'medium' | 'high' | 'critical';

export type ItemStatus =
    | 'not-started'
    | 'in-progress'
    | 'blocked'
    | 'in-review'
    | 'completed'
    | 'archived';

export type DevSecOpsItemType = 'feature' | 'story' | 'task' | 'bug' | 'epic';

export interface DevSecOpsUser {
    id: string;
    name: string;
    email: string;
    role: 'Executive' | 'PM' | 'Developer' | 'Security' | 'DevOps' | 'QA';
}

export interface DevSecOpsPhase {
    id: string;
    key: PhaseKey;
    title: string;
    description: string;
    order: number;
    color: string;
}

export interface Item {
    id: string;
    title: string;
    description?: string;
    type: DevSecOpsItemType;
    priority: Priority;
    status: ItemStatus;
    phaseId: string;
    owners: DevSecOpsUser[];
    tags: string[];
    createdAt: string;
    updatedAt: string;
    progress: number;
    artifacts: unknown[];
}

export interface ItemFilter {
    phaseIds?: string[];
    status?: ItemStatus[];
    priority?: Priority[];
    ownerIds?: string[];
    tags?: string[];
    search?: string;
}

export const defaultDevSecOpsPhases: DevSecOpsPhase[] = [
    {
        id: 'ideation',
        key: 'ideation',
        title: 'Ideation',
        description: 'Problem intake and initial discovery.',
        order: 1,
        color: '#64748b',
    },
    {
        id: 'planning',
        key: 'planning',
        title: 'Planning',
        description: 'Planning and scoping the change.',
        order: 2,
        color: '#2563eb',
    },
    {
        id: 'development',
        key: 'development',
        title: 'Development',
        description: 'Implementation and active coding.',
        order: 3,
        color: '#7c3aed',
    },
    {
        id: 'security',
        key: 'security',
        title: 'Security',
        description: 'Security validation and remediation.',
        order: 4,
        color: '#dc2626',
    },
    {
        id: 'testing',
        key: 'testing',
        title: 'Testing',
        description: 'Functional and non-functional validation.',
        order: 5,
        color: '#ea580c',
    },
    {
        id: 'deployment',
        key: 'deployment',
        title: 'Deployment',
        description: 'Release orchestration and rollout.',
        order: 6,
        color: '#16a34a',
    },
    {
        id: 'operations',
        key: 'operations',
        title: 'Operations',
        description: 'Operate, observe, and learn.',
        order: 7,
        color: '#0891b2',
    },
];
