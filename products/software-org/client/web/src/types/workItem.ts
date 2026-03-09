/**
 * Work Item Type Definitions
 *
 * <p><b>Purpose</b><br>
 * Type definitions for work items (stories, epics, tasks) used in the
 * Engineer persona flow. Supports the full story lifecycle from intake
 * to deployment and monitoring.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import type { WorkItem, WorkItemStatus, WorkItemPlan } from '@/types/workItem';
 * }</pre>
 *
 * @doc.type types
 * @doc.purpose Work item type definitions for engineer flow
 * @doc.layer product
 * @doc.pattern TypeScript Types
 */

/**
 * Work item status representing lifecycle stages
 */
export type WorkItemStatus =
    | 'backlog'
    | 'ready'
    | 'in-progress'
    | 'in-review'
    | 'staging'
    | 'deployed'
    | 'done'
    | 'blocked';

/**
 * Work item priority levels
 */
export type WorkItemPriority = 'p0' | 'p1' | 'p2' | 'p3';

/**
 * Work item type classification
 */
export type WorkItemType = 'story' | 'epic' | 'bug' | 'task' | 'spike';

/**
 * Acceptance criteria for a work item
 */
export interface AcceptanceCriteria {
    id: string;
    description: string;
    completed: boolean;
}

/**
 * Implementation plan for a work item
 */
export interface WorkItemPlan {
    affectedServices: string[];
    designNotes: string;
    featureFlag?: {
        enabled: boolean;
        name: string;
        rolloutStrategy: 'full' | 'canary' | 'percentage';
        rolloutPercentage?: number;
    };
    branchName: string;
    estimatedHours?: number;
    risks?: string[];
}

/**
 * Pull request linked to a work item
 */
export interface LinkedPullRequest {
    id: string;
    title: string;
    url: string;
    status: 'open' | 'merged' | 'closed';
    approvals: number;
    requiredApprovals: number;
    ciStatus: 'pending' | 'running' | 'passed' | 'failed';
    author: string;
    createdAt: string;
    updatedAt: string;
}

/**
 * Pipeline run associated with a work item
 */
export interface LinkedPipeline {
    id: string;
    name: string;
    status: 'pending' | 'running' | 'passed' | 'failed' | 'cancelled';
    branch: string;
    commit: string;
    startedAt: string;
    completedAt?: string;
    duration?: number;
    url?: string;
}

/**
 * Context links for related resources
 */
export interface ContextLink {
    id: string;
    type: 'incident' | 'alert' | 'dashboard' | 'service' | 'document';
    title: string;
    href: string;
}

/**
 * Core work item entity
 */
export interface WorkItem {
    id: string;
    title: string;
    description: string;
    type: WorkItemType;
    status: WorkItemStatus;
    priority: WorkItemPriority;
    assignee: {
        id: string;
        name: string;
        avatar?: string;
    };
    reporter: {
        id: string;
        name: string;
    };
    service?: string;
    team?: string;
    labels: string[];
    acceptanceCriteria: AcceptanceCriteria[];
    plan?: WorkItemPlan;
    linkedPullRequests: LinkedPullRequest[];
    linkedPipelines: LinkedPipeline[];
    contextLinks: ContextLink[];
    createdAt: string;
    updatedAt: string;
    startedAt?: string;
    completedAt?: string;
}

/**
 * Summary view for work item lists
 */
export interface WorkItemSummary {
    id: string;
    title: string;
    type: WorkItemType;
    status: WorkItemStatus;
    priority: WorkItemPriority;
    service?: string;
    assignee: {
        id: string;
        name: string;
    };
    updatedAt: string;
}

/**
 * Filters for work item queries
 */
export interface WorkItemFilters {
    status?: WorkItemStatus[];
    priority?: WorkItemPriority[];
    type?: WorkItemType[];
    assigneeId?: string;
    service?: string;
    search?: string;
}

/**
 * Create/Update payload for work items
 */
export interface WorkItemPayload {
    title: string;
    description: string;
    type: WorkItemType;
    priority: WorkItemPriority;
    assigneeId?: string;
    service?: string;
    labels?: string[];
    acceptanceCriteria?: Omit<AcceptanceCriteria, 'id'>[];
}

/**
 * Update payload for work item plan
 */
export interface WorkItemPlanPayload {
    affectedServices: string[];
    designNotes: string;
    featureFlag?: {
        enabled: boolean;
        name: string;
        rolloutStrategy: 'full' | 'canary' | 'percentage';
        rolloutPercentage?: number;
    };
    branchName: string;
    estimatedHours?: number;
    risks?: string[];
}
