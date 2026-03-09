/**
 * Integration Test Setup
 *
 * <p><b>Purpose</b><br>
 * Configuration and utilities for integration tests.
 * Sets up test environment, mocks, and helpers.
 *
 * <p><b>Features</b><br>
 * - API mock server setup
 * - Test data factories
 * - Helper utilities
 * - Cleanup functions
 *
 * @doc.type test
 * @doc.purpose Integration test setup
 * @doc.layer product
 * @doc.pattern Test Utilities
 */

import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll } from 'vitest';

const API_BASE_URL = 'http://localhost:8080';

// Mock data factories
export const mockOrgConfig = {
    name: 'test-org',
    namespace: 'test',
    displayName: 'Test Organization',
    description: 'Test organization for integration tests',
    structure: {
        type: 'hierarchical',
        maxDepth: 4,
    },
    settings: {
        defaultTimezone: 'UTC',
        events: {
            enabled: true,
            publishTo: 'kafka',
            eventPrefix: 'org.test',
        },
        hitl: {
            enabled: true,
            confidenceThreshold: 0.7,
        },
        ai: {
            enabled: true,
            primaryProvider: 'openai',
            fallbackProvider: 'anthropic',
        },
    },
    departments: [],
    workflows: [],
    organizationKpis: [],
};

export const mockDepartments = [
    {
        id: 'dept-1',
        name: 'Engineering',
        type: 'ENGINEERING',
        agentCount: 10,
        status: 'ACTIVE',
    },
    {
        id: 'dept-2',
        name: 'QA',
        type: 'QA',
        agentCount: 5,
        status: 'ACTIVE',
    },
];

export const mockAgents = [
    {
        id: 'agent-1',
        name: 'Backend Agent',
        role: 'developer',
        department: 'Engineering',
        status: 'ONLINE',
    },
    {
        id: 'agent-2',
        name: 'QA Agent',
        role: 'tester',
        department: 'QA',
        status: 'ONLINE',
    },
];

export const mockHitlActions = [
    {
        actionId: 'action-1',
        state: 'PENDING',
        submittedAt: new Date().toISOString(),
        submittedBy: 'agent-1',
    },
    {
        actionId: 'action-2',
        state: 'PENDING',
        submittedAt: new Date().toISOString(),
        submittedBy: 'agent-2',
    },
];

// MSW handlers
export const handlers = [
    // Organization API
    http.get(`${API_BASE_URL}/api/v1/org/config`, () => {
        return HttpResponse.json(mockOrgConfig);
    }),

    http.get(`${API_BASE_URL}/api/v1/org/graph`, () => {
        return HttpResponse.json({
            nodes: [
                { id: 'org-1', label: 'Test Org', type: 'organization', metadata: {} },
                { id: 'dept-1', label: 'Engineering', type: 'department', metadata: {} },
            ],
            edges: [
                { from: 'org-1', to: 'dept-1', type: 'contains' },
            ],
            metadata: {
                nodeCount: 2,
                edgeCount: 1,
                maxDepth: 2,
            },
        });
    }),

    http.get(`${API_BASE_URL}/api/v1/org/departments`, () => {
        return HttpResponse.json(mockDepartments);
    }),

    http.get(`${API_BASE_URL}/api/v1/org/agents`, () => {
        return HttpResponse.json(mockAgents);
    }),

    http.post(`${API_BASE_URL}/api/v1/org/hierarchy/move`, async ({ request }) => {
        const body = await request.json();
        return HttpResponse.json({
            success: true,
            nodeId: (body as any).nodeId,
            newParentId: (body as any).toParentId,
            message: 'Node moved successfully',
            metadata: {},
        });
    }),

    // Department API
    http.get(`${API_BASE_URL}/api/v1/departments`, () => {
        return HttpResponse.json(mockDepartments);
    }),

    http.get(`${API_BASE_URL}/api/v1/departments/:id`, ({ params }) => {
        const dept = mockDepartments.find(d => d.id === params.id);
        if (!dept) {
            return new HttpResponse(null, { status: 404 });
        }
        return HttpResponse.json({
            ...dept,
            description: 'Test department',
            agents: [],
            workflows: [],
        });
    }),

    http.get(`${API_BASE_URL}/api/v1/departments/:id/agents`, () => {
        return HttpResponse.json(mockAgents);
    }),

    http.get(`${API_BASE_URL}/api/v1/departments/:id/kpis`, ({ params }) => {
        return HttpResponse.json({
            departmentId: params.id,
            velocity: 85.5,
            throughput: 120.0,
            quality: 92.3,
            efficiency: 88.7,
            timestamp: new Date().toISOString(),
        });
    }),

    http.get(`${API_BASE_URL}/api/v1/departments/:id/workflows`, () => {
        return HttpResponse.json([
            {
                id: 'workflow-1',
                name: 'Feature Development',
                type: 'FEATURE_DEVELOPMENT',
                status: 'ACTIVE',
            },
        ]);
    }),

    // HITL API
    http.post(`${API_BASE_URL}/api/v1/hitl/actions`, async ({ request }) => {
        const body = await request.json() as any;
        const requiresApproval = body.confidence < 0.7;
        return HttpResponse.json({
            actionId: `action-${Date.now()}`,
            status: requiresApproval ? 'PENDING' : 'APPROVED',
            message: requiresApproval ? 'Action requires approval' : 'Action auto-approved',
            requiresApproval,
        });
    }),

    http.get(`${API_BASE_URL}/api/v1/hitl/actions/:id`, ({ params }) => {
        return HttpResponse.json({
            actionId: params.id,
            state: 'PENDING',
            submittedAt: new Date().toISOString(),
            submittedBy: 'agent-1',
        });
    }),

    http.post(`${API_BASE_URL}/api/v1/hitl/actions/:id/approve`, ({ params }) => {
        return HttpResponse.json({
            actionId: params.id,
            status: 'APPROVED',
            message: 'Action approved',
            requiresApproval: false,
        });
    }),

    http.post(`${API_BASE_URL}/api/v1/hitl/actions/:id/reject`, ({ params }) => {
        return HttpResponse.json({
            actionId: params.id,
            status: 'REJECTED',
            message: 'Action rejected',
            requiresApproval: false,
        });
    }),

    http.get(`${API_BASE_URL}/api/v1/hitl/pending`, () => {
        return HttpResponse.json(mockHitlActions);
    }),
];

// Setup MSW server
export const server = setupServer(...handlers);

// Start server before all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

// Reset handlers after each test
afterEach(() => server.resetHandlers());

// Clean up after all tests
afterAll(() => server.close());
