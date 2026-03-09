/**
 * Work Items API Service
 *
 * <p><b>Purpose</b><br>
 * API service for managing work items (stories, epics, tasks) in the
 * Engineer persona flow. Provides CRUD operations and status transitions
 * for the full story lifecycle.
 *
 * <p><b>Mock Data</b><br>
 * Currently uses mock data. Replace with actual API calls to
 * `/api/v1/work-items` when backend is available.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { workItemsApi } from '@/services/api/workItemsApi';
 *
 * const items = await workItemsApi.getMyWorkItems('engineer-123');
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Work items API service for engineer flow
 * @doc.layer product
 * @doc.pattern API Service
 */

import type {
    WorkItem,
    WorkItemSummary,
    WorkItemFilters,
    WorkItemPlanPayload,
    WorkItemStatus,
} from '@/types/workItem';

// ============================================================================
// MOCK DATA
// ============================================================================

const MOCK_WORK_ITEMS: WorkItem[] = [
    {
        id: 'WI-1234',
        title: 'Implement user authentication flow',
        description: `## Overview
Implement a secure authentication flow with OAuth 2.0 support.

## Requirements
- Support Google and GitHub OAuth providers
- Implement JWT token refresh
- Add session management
- Create login/logout UI components

## Technical Notes
- Use existing auth library from libs/auth
- Follow security guidelines from compliance team`,
        type: 'story',
        status: 'ready',
        priority: 'p1',
        assignee: { id: 'eng-1', name: 'Alex Chen', avatar: '' },
        reporter: { id: 'pm-1', name: 'Sarah Johnson' },
        service: 'auth-service',
        team: 'Platform',
        labels: ['security', 'authentication', 'oauth'],
        acceptanceCriteria: [
            { id: 'ac-1', description: 'Users can log in with Google OAuth', completed: false },
            { id: 'ac-2', description: 'Users can log in with GitHub OAuth', completed: false },
            { id: 'ac-3', description: 'JWT tokens are refreshed automatically', completed: false },
            { id: 'ac-4', description: 'Session expires after 24 hours of inactivity', completed: false },
        ],
        linkedPullRequests: [],
        linkedPipelines: [],
        contextLinks: [
            { id: 'ctx-1', type: 'document', title: 'Auth Design Doc', href: '/docs/auth-design' },
            { id: 'ctx-2', type: 'service', title: 'auth-service', href: '/services/auth-service' },
        ],
        createdAt: '2025-11-20T10:00:00Z',
        updatedAt: '2025-11-25T14:30:00Z',
    },
    {
        id: 'WI-1235',
        title: 'Fix payment gateway timeout issues',
        description: `## Problem
Payment gateway is timing out under high load, causing failed transactions.

## Root Cause
Connection pool exhaustion when processing > 100 concurrent requests.

## Solution
- Increase connection pool size
- Implement circuit breaker pattern
- Add retry logic with exponential backoff`,
        type: 'bug',
        status: 'in-progress',
        priority: 'p0',
        assignee: { id: 'eng-1', name: 'Alex Chen', avatar: '' },
        reporter: { id: 'sre-1', name: 'Mike Wilson' },
        service: 'payment-service',
        team: 'Payments',
        labels: ['bug', 'critical', 'performance'],
        acceptanceCriteria: [
            { id: 'ac-1', description: 'No timeouts under 500 concurrent requests', completed: true },
            { id: 'ac-2', description: 'Circuit breaker activates after 5 failures', completed: false },
            { id: 'ac-3', description: 'Retry logic handles transient failures', completed: false },
        ],
        plan: {
            affectedServices: ['payment-service', 'checkout-service'],
            designNotes: 'Implement circuit breaker using resilience4j library. Update connection pool config.',
            featureFlag: {
                enabled: true,
                name: 'payment-circuit-breaker',
                rolloutStrategy: 'canary',
                rolloutPercentage: 10,
            },
            branchName: 'fix/WI-1235-payment-timeout',
            estimatedHours: 8,
            risks: ['May affect checkout latency during rollout'],
        },
        linkedPullRequests: [
            {
                id: 'pr-456',
                title: 'fix: Add circuit breaker to payment gateway',
                url: 'https://github.com/org/repo/pull/456',
                status: 'open',
                approvals: 1,
                requiredApprovals: 2,
                ciStatus: 'passed',
                author: 'Alex Chen',
                createdAt: '2025-11-25T09:00:00Z',
                updatedAt: '2025-11-25T14:00:00Z',
            },
        ],
        linkedPipelines: [
            {
                id: 'pipe-789',
                name: 'payment-service-ci',
                status: 'passed',
                branch: 'fix/WI-1235-payment-timeout',
                commit: 'abc123',
                startedAt: '2025-11-25T14:00:00Z',
                completedAt: '2025-11-25T14:15:00Z',
                duration: 900,
            },
        ],
        contextLinks: [
            { id: 'ctx-1', type: 'incident', title: 'INC-789: Payment failures', href: '/incidents/INC-789' },
            { id: 'ctx-2', type: 'dashboard', title: 'Payment Metrics', href: '/dashboard?service=payment' },
        ],
        createdAt: '2025-11-24T08:00:00Z',
        updatedAt: '2025-11-25T14:30:00Z',
        startedAt: '2025-11-24T10:00:00Z',
    },
    {
        id: 'WI-1236',
        title: 'Add real-time notifications for order updates',
        description: `## Feature
Users should receive real-time notifications when their order status changes.

## Requirements
- WebSocket connection for real-time updates
- Push notifications for mobile
- Email notifications for key events
- User preferences for notification types`,
        type: 'story',
        status: 'in-review',
        priority: 'p2',
        assignee: { id: 'eng-1', name: 'Alex Chen', avatar: '' },
        reporter: { id: 'pm-2', name: 'Emily Davis' },
        service: 'notification-service',
        team: 'Platform',
        labels: ['feature', 'notifications', 'websocket'],
        acceptanceCriteria: [
            { id: 'ac-1', description: 'WebSocket delivers updates within 500ms', completed: true },
            { id: 'ac-2', description: 'Push notifications work on iOS and Android', completed: true },
            { id: 'ac-3', description: 'Users can configure notification preferences', completed: true },
        ],
        plan: {
            affectedServices: ['notification-service', 'order-service', 'user-service'],
            designNotes: 'Use existing WebSocket infrastructure. Add FCM for push notifications.',
            featureFlag: {
                enabled: true,
                name: 'realtime-order-notifications',
                rolloutStrategy: 'percentage',
                rolloutPercentage: 50,
            },
            branchName: 'feature/WI-1236-realtime-notifications',
            estimatedHours: 16,
        },
        linkedPullRequests: [
            {
                id: 'pr-457',
                title: 'feat: Add real-time order notifications',
                url: 'https://github.com/org/repo/pull/457',
                status: 'open',
                approvals: 2,
                requiredApprovals: 2,
                ciStatus: 'passed',
                author: 'Alex Chen',
                createdAt: '2025-11-23T11:00:00Z',
                updatedAt: '2025-11-25T10:00:00Z',
            },
        ],
        linkedPipelines: [
            {
                id: 'pipe-790',
                name: 'notification-service-ci',
                status: 'passed',
                branch: 'feature/WI-1236-realtime-notifications',
                commit: 'def456',
                startedAt: '2025-11-25T10:00:00Z',
                completedAt: '2025-11-25T10:12:00Z',
                duration: 720,
            },
        ],
        contextLinks: [],
        createdAt: '2025-11-18T09:00:00Z',
        updatedAt: '2025-11-25T10:00:00Z',
        startedAt: '2025-11-20T10:00:00Z',
    },
    {
        id: 'WI-1237',
        title: 'Optimize database queries for product search',
        description: `## Problem
Product search is slow (> 2s) for large catalogs.

## Solution
- Add database indexes
- Implement query caching
- Consider Elasticsearch for full-text search`,
        type: 'task',
        status: 'staging',
        priority: 'p2',
        assignee: { id: 'eng-1', name: 'Alex Chen', avatar: '' },
        reporter: { id: 'eng-2', name: 'Jordan Lee' },
        service: 'product-service',
        team: 'Catalog',
        labels: ['performance', 'database', 'search'],
        acceptanceCriteria: [
            { id: 'ac-1', description: 'Search queries complete in < 500ms', completed: true },
            { id: 'ac-2', description: 'Cache hit rate > 80%', completed: true },
        ],
        plan: {
            affectedServices: ['product-service'],
            designNotes: 'Add composite index on (category_id, name). Use Redis for query caching.',
            branchName: 'perf/WI-1237-search-optimization',
            estimatedHours: 6,
        },
        linkedPullRequests: [
            {
                id: 'pr-458',
                title: 'perf: Optimize product search queries',
                url: 'https://github.com/org/repo/pull/458',
                status: 'merged',
                approvals: 2,
                requiredApprovals: 2,
                ciStatus: 'passed',
                author: 'Alex Chen',
                createdAt: '2025-11-22T14:00:00Z',
                updatedAt: '2025-11-24T16:00:00Z',
            },
        ],
        linkedPipelines: [
            {
                id: 'pipe-791',
                name: 'product-service-ci',
                status: 'passed',
                branch: 'main',
                commit: 'ghi789',
                startedAt: '2025-11-24T16:00:00Z',
                completedAt: '2025-11-24T16:10:00Z',
                duration: 600,
            },
        ],
        contextLinks: [
            { id: 'ctx-1', type: 'dashboard', title: 'Product Search Latency', href: '/reports?view=staging&service=product' },
        ],
        createdAt: '2025-11-15T10:00:00Z',
        updatedAt: '2025-11-24T16:00:00Z',
        startedAt: '2025-11-20T09:00:00Z',
    },
    {
        id: 'WI-1238',
        title: 'Implement rate limiting for API endpoints',
        description: `## Security Requirement
Add rate limiting to prevent API abuse and DDoS attacks.

## Scope
- All public API endpoints
- Per-user and per-IP limits
- Configurable limits per endpoint`,
        type: 'story',
        status: 'deployed',
        priority: 'p1',
        assignee: { id: 'eng-1', name: 'Alex Chen', avatar: '' },
        reporter: { id: 'sec-1', name: 'Security Team' },
        service: 'api-gateway',
        team: 'Platform',
        labels: ['security', 'rate-limiting', 'api'],
        acceptanceCriteria: [
            { id: 'ac-1', description: 'Rate limits enforced on all public endpoints', completed: true },
            { id: 'ac-2', description: '429 response returned when limit exceeded', completed: true },
            { id: 'ac-3', description: 'Limits configurable via admin panel', completed: true },
        ],
        plan: {
            affectedServices: ['api-gateway'],
            designNotes: 'Use Redis for rate limit counters. Implement sliding window algorithm.',
            branchName: 'feature/WI-1238-rate-limiting',
            estimatedHours: 12,
        },
        linkedPullRequests: [
            {
                id: 'pr-459',
                title: 'feat: Add rate limiting to API gateway',
                url: 'https://github.com/org/repo/pull/459',
                status: 'merged',
                approvals: 2,
                requiredApprovals: 2,
                ciStatus: 'passed',
                author: 'Alex Chen',
                createdAt: '2025-11-18T10:00:00Z',
                updatedAt: '2025-11-20T14:00:00Z',
            },
        ],
        linkedPipelines: [],
        contextLinks: [
            { id: 'ctx-1', type: 'dashboard', title: 'API Gateway Metrics', href: '/dashboard?storyId=WI-1238' },
        ],
        createdAt: '2025-11-10T09:00:00Z',
        updatedAt: '2025-11-22T10:00:00Z',
        startedAt: '2025-11-15T10:00:00Z',
        completedAt: '2025-11-22T10:00:00Z',
    },
    // Scenario: Work item with FAILING CI pipeline
    {
        id: 'WI-1239',
        title: 'Add GraphQL subscriptions for live updates',
        description: `## Feature
Add GraphQL subscriptions to enable real-time data updates for clients.

## Requirements
- WebSocket-based subscriptions
- Authentication for subscription connections
- Rate limiting for subscription connections`,
        type: 'story',
        status: 'in-progress',
        priority: 'p2',
        assignee: { id: 'eng-1', name: 'Alex Chen', avatar: '' },
        reporter: { id: 'pm-1', name: 'Sarah Johnson' },
        service: 'graphql-gateway',
        team: 'Platform',
        labels: ['feature', 'graphql', 'realtime'],
        acceptanceCriteria: [
            { id: 'ac-1', description: 'Subscription connections are authenticated', completed: true },
            { id: 'ac-2', description: 'Live updates are delivered within 100ms', completed: false },
            { id: 'ac-3', description: 'Connection rate limits are enforced', completed: false },
        ],
        plan: {
            affectedServices: ['graphql-gateway', 'api-gateway'],
            designNotes: 'Use Apollo Server subscriptions with Redis PubSub.',
            featureFlag: {
                enabled: true,
                name: 'graphql-subscriptions',
                rolloutStrategy: 'canary',
                rolloutPercentage: 5,
            },
            branchName: 'feature/WI-1239-graphql-subscriptions',
            estimatedHours: 20,
        },
        linkedPullRequests: [
            {
                id: 'pr-460',
                title: 'feat: Add GraphQL subscriptions support',
                url: 'https://github.com/org/repo/pull/460',
                status: 'open',
                approvals: 0,
                requiredApprovals: 2,
                ciStatus: 'failed',
                author: 'Alex Chen',
                createdAt: '2025-11-25T14:00:00Z',
                updatedAt: '2025-11-26T09:00:00Z',
            },
        ],
        linkedPipelines: [
            {
                id: 'pipe-800',
                name: 'graphql-gateway-ci',
                status: 'failed',
                branch: 'feature/WI-1239-graphql-subscriptions',
                commit: 'pqr1234',
                startedAt: '2025-11-26T09:00:00Z',
                completedAt: '2025-11-26T09:12:00Z',
                duration: 720,
            },
        ],
        contextLinks: [],
        createdAt: '2025-11-24T10:00:00Z',
        updatedAt: '2025-11-26T09:00:00Z',
        startedAt: '2025-11-25T09:00:00Z',
    },
    // Scenario: Work item with WARNING status in staging
    {
        id: 'WI-1240',
        title: 'Implement user activity logging',
        description: `## Compliance Requirement
Log user activity for audit and compliance purposes.

## Scope
- Login/logout events
- Data access events
- Configuration changes`,
        type: 'story',
        status: 'staging',
        priority: 'p1',
        assignee: { id: 'eng-1', name: 'Alex Chen', avatar: '' },
        reporter: { id: 'compliance-1', name: 'Compliance Team' },
        service: 'audit-service',
        team: 'Platform',
        labels: ['compliance', 'audit', 'logging'],
        acceptanceCriteria: [
            { id: 'ac-1', description: 'All user activities are logged', completed: true },
            { id: 'ac-2', description: 'Logs are encrypted at rest', completed: true },
            { id: 'ac-3', description: 'Logs retention follows compliance policy', completed: true },
        ],
        plan: {
            affectedServices: ['audit-service', 'auth-service', 'api-gateway'],
            designNotes: 'Use structured logging with ELK stack. Encrypt sensitive fields.',
            branchName: 'feature/WI-1240-activity-logging',
            estimatedHours: 16,
        },
        linkedPullRequests: [
            {
                id: 'pr-461',
                title: 'feat: Add user activity logging',
                url: 'https://github.com/org/repo/pull/461',
                status: 'merged',
                approvals: 2,
                requiredApprovals: 2,
                ciStatus: 'passed',
                author: 'Alex Chen',
                createdAt: '2025-11-22T10:00:00Z',
                updatedAt: '2025-11-24T14:00:00Z',
            },
        ],
        linkedPipelines: [
            {
                id: 'pipe-801',
                name: 'audit-service-ci',
                status: 'passed',
                branch: 'main',
                commit: 'stu5678',
                startedAt: '2025-11-24T14:00:00Z',
                completedAt: '2025-11-24T14:10:00Z',
                duration: 600,
            },
        ],
        contextLinks: [
            { id: 'ctx-1', type: 'document', title: 'Compliance Requirements', href: '/docs/compliance' },
        ],
        createdAt: '2025-11-15T09:00:00Z',
        updatedAt: '2025-11-25T10:00:00Z',
        startedAt: '2025-11-20T10:00:00Z',
    },
];

// ============================================================================
// API FUNCTIONS
// ============================================================================

/**
 * Simulate API delay for realistic behavior
 */
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Apply filters to work items and return summaries
 */
function applyFiltersAndMapToSummary(
    items: WorkItem[],
    filters?: WorkItemFilters
): WorkItemSummary[] {
    let filtered = [...items];

    if (filters?.status?.length) {
        filtered = filtered.filter(item => filters.status!.includes(item.status));
    }
    if (filters?.priority?.length) {
        filtered = filtered.filter(item => filters.priority!.includes(item.priority));
    }
    if (filters?.type?.length) {
        filtered = filtered.filter(item => filters.type!.includes(item.type));
    }
    if (filters?.service) {
        filtered = filtered.filter(item => item.service === filters.service);
    }
    if (filters?.assigneeId) {
        filtered = filtered.filter(item => item.assignee.id === filters.assigneeId);
    }
    if (filters?.search) {
        const search = filters.search.toLowerCase();
        filtered = filtered.filter(
            item =>
                item.title.toLowerCase().includes(search) ||
                item.id.toLowerCase().includes(search)
        );
    }

    return filtered.map(item => ({
        id: item.id,
        title: item.title,
        type: item.type,
        status: item.status,
        priority: item.priority,
        service: item.service,
        assignee: item.assignee,
        updatedAt: item.updatedAt,
    }));
}

/**
 * Get all work items (for list page)
 */
export async function getAllWorkItems(
    filters?: WorkItemFilters
): Promise<WorkItemSummary[]> {
    await delay(300);
    return applyFiltersAndMapToSummary(MOCK_WORK_ITEMS, filters);
}

/**
 * Get work items assigned to a user
 */
export async function getMyWorkItems(
    _userId: string,
    filters?: WorkItemFilters
): Promise<WorkItemSummary[]> {
    await delay(300);

    // Filter to items assigned to the user
    const userItems = MOCK_WORK_ITEMS.filter(item => item.assignee.id === 'eng-1'); // Mock: return all for demo
    return applyFiltersAndMapToSummary(userItems, filters);
}

/**
 * Get a single work item by ID
 */
export async function getWorkItem(id: string): Promise<WorkItem | null> {
    await delay(200);
    return MOCK_WORK_ITEMS.find(item => item.id === id) ?? null;
}

/**
 * Update work item status
 */
export async function updateWorkItemStatus(
    id: string,
    status: WorkItemStatus
): Promise<WorkItem | null> {
    await delay(300);
    const item = MOCK_WORK_ITEMS.find(item => item.id === id);
    if (item) {
        item.status = status;
        item.updatedAt = new Date().toISOString();
        if (status === 'in-progress' && !item.startedAt) {
            item.startedAt = new Date().toISOString();
        }
        if (status === 'done') {
            item.completedAt = new Date().toISOString();
        }
    }
    return item ?? null;
}

/**
 * Update work item plan
 */
export async function updateWorkItemPlan(
    id: string,
    plan: WorkItemPlanPayload
): Promise<WorkItem | null> {
    await delay(300);
    const item = MOCK_WORK_ITEMS.find(item => item.id === id);
    if (item) {
        item.plan = plan;
        item.updatedAt = new Date().toISOString();
    }
    return item ?? null;
}

/**
 * Update acceptance criteria completion
 */
export async function updateAcceptanceCriteria(
    workItemId: string,
    criteriaId: string,
    completed: boolean
): Promise<WorkItem | null> {
    await delay(200);
    const item = MOCK_WORK_ITEMS.find(item => item.id === workItemId);
    if (item) {
        const criteria = item.acceptanceCriteria.find(ac => ac.id === criteriaId);
        if (criteria) {
            criteria.completed = completed;
            item.updatedAt = new Date().toISOString();
        }
    }
    return item ?? null;
}

/**
 * Complete/close a work item
 */
export async function completeWorkItem(id: string): Promise<WorkItem | null> {
    return updateWorkItemStatus(id, 'done');
}

/**
 * Work items API object for convenient access
 */
export const workItemsApi = {
    getAllWorkItems,
    getMyWorkItems,
    getWorkItem,
    updateWorkItemStatus,
    updateWorkItemPlan,
    updateAcceptanceCriteria,
    completeWorkItem,
};

export default workItemsApi;
