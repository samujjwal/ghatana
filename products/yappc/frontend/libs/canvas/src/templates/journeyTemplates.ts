/**
 * Persona Journey Templates
 *
 * Pre-built canvas templates for different persona workflows.
 * These templates provide starting points for common use cases.
 *
 * @doc.type module
 * @doc.purpose Persona-specific canvas templates
 * @doc.layer product
 * @doc.pattern Template
 *
 * Templates by Persona:
 * - Product Manager: Requirements Canvas, User Flow Canvas
 * - Architect: System Design Canvas, Infrastructure Canvas
 * - Developer: API Design Canvas, Microservice Canvas
 * - QA: Test Plan Canvas
 * - UX Designer: Wireframe Canvas
 */

import type { Node, Edge } from '@xyflow/react';
import type {
    AIPromptNodeData,
    DatabaseNodeData,
    ServiceNodeData,
    APIEndpointNodeData,
    UIScreenNodeData,
    TestSuiteNodeData,
} from '../components/PersonaNodes';

// ============================================================================
// TEMPLATE INTERFACES
// ============================================================================

/** Node type for journey templates (compatible with ReactFlow) */
export type JourneyNode = Node<
    | AIPromptNodeData
    | DatabaseNodeData
    | ServiceNodeData
    | APIEndpointNodeData
    | UIScreenNodeData
    | TestSuiteNodeData
    | Record<string, unknown>
>;

/** Edge type for journey templates */
export type JourneyEdge = Edge;

export interface JourneyTemplate {
    id: string;
    name: string;
    description: string;
    persona: 'pm' | 'architect' | 'developer' | 'qa' | 'ux' | 'all';
    category: 'requirements' | 'design' | 'implementation' | 'testing' | 'deployment';
    thumbnail?: string;
    tags: string[];
    nodes: JourneyNode[];
    edges: JourneyEdge[];
    metadata?: {
        author?: string;
        version?: string;
        lastUpdated?: string;
        usageCount?: number;
    };
}

// ============================================================================
// PRODUCT MANAGER TEMPLATES
// ============================================================================

/**
 * Requirements Canvas Template
 *
 * Starting point for PM brainstorming and requirements gathering.
 * Includes AI prompt nodes and flow connectors.
 */
export const requirementsCanvasTemplate: JourneyTemplate = {
    id: 'tpl-pm-requirements',
    name: 'Requirements Canvas',
    description: 'Brainstorm and define product requirements with AI assistance',
    persona: 'pm',
    category: 'requirements',
    tags: ['requirements', 'brainstorming', 'ai', 'product-management'],
    nodes: [
        {
            id: 'ai-prompt-1',
            type: 'aiPrompt',
            position: { x: 100, y: 200 },
            data: {
                label: 'Feature Brainstorm',
                type: 'aiPrompt',
                persona: 'pm',
                prompt: 'Describe the core feature you want to build...',
                status: 'draft',
            } as AIPromptNodeData,
        },
        {
            id: 'ui-screen-1',
            type: 'uiScreen',
            position: { x: 450, y: 100 },
            data: {
                label: 'Main View',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'view',
                status: 'draft',
            } as UIScreenNodeData,
        },
        {
            id: 'ui-screen-2',
            type: 'uiScreen',
            position: { x: 450, y: 300 },
            data: {
                label: 'Edit Mode',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'edit',
                status: 'draft',
            } as UIScreenNodeData,
        },
    ],
    edges: [],
    metadata: {
        author: 'YAPPC Team',
        version: '1.0.0',
        lastUpdated: new Date().toISOString(),
    },
};

/**
 * User Flow Canvas Template
 *
 * Map out user journeys and navigation flows.
 */
export const userFlowCanvasTemplate: JourneyTemplate = {
    id: 'tpl-pm-user-flow',
    name: 'User Flow Canvas',
    description: 'Map user journeys from entry to goal completion',
    persona: 'pm',
    category: 'requirements',
    tags: ['user-flow', 'journey', 'ux', 'navigation'],
    nodes: [
        {
            id: 'screen-entry',
            type: 'uiScreen',
            position: { x: 100, y: 200 },
            data: {
                label: 'Entry Point',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'view',
                status: 'draft',
            } as UIScreenNodeData,
        },
        {
            id: 'screen-action',
            type: 'uiScreen',
            position: { x: 350, y: 200 },
            data: {
                label: 'Action Screen',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'edit',
                status: 'draft',
            } as UIScreenNodeData,
        },
        {
            id: 'screen-result',
            type: 'uiScreen',
            position: { x: 600, y: 200 },
            data: {
                label: 'Result/Confirmation',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'detail',
                status: 'draft',
            } as UIScreenNodeData,
        },
    ],
    edges: [],
    metadata: {
        author: 'YAPPC Team',
        version: '1.0.0',
    },
};

// ============================================================================
// ARCHITECT TEMPLATES
// ============================================================================

/**
 * System Design Canvas Template
 *
 * Design backend architecture with services, databases, and APIs.
 */
export const systemDesignCanvasTemplate: JourneyTemplate = {
    id: 'tpl-arch-system-design',
    name: 'System Design Canvas',
    description: 'Design microservices architecture with databases and APIs',
    persona: 'architect',
    category: 'design',
    tags: ['architecture', 'microservices', 'system-design', 'backend'],
    nodes: [
        {
            id: 'service-1',
            type: 'service',
            position: { x: 300, y: 100 },
            data: {
                label: 'API Gateway',
                type: 'service',
                persona: 'architect',
                technology: 'nodejs',
                config: { replicas: 2, cpu: '500m', memory: '512Mi' },
                status: 'draft',
            } as ServiceNodeData,
        },
        {
            id: 'service-2',
            type: 'service',
            position: { x: 100, y: 300 },
            data: {
                label: 'User Service',
                type: 'service',
                persona: 'architect',
                technology: 'java',
                config: { replicas: 3, cpu: '1000m', memory: '1Gi' },
                endpoints: ['/users', '/users/:id', '/users/search'],
                status: 'draft',
            } as ServiceNodeData,
        },
        {
            id: 'service-3',
            type: 'service',
            position: { x: 500, y: 300 },
            data: {
                label: 'Order Service',
                type: 'service',
                persona: 'architect',
                technology: 'java',
                config: { replicas: 3, cpu: '1000m', memory: '1Gi' },
                endpoints: ['/orders', '/orders/:id'],
                status: 'draft',
            } as ServiceNodeData,
        },
        {
            id: 'db-1',
            type: 'database',
            position: { x: 100, y: 500 },
            data: {
                label: 'Users DB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                schema: {
                    tables: [
                        { name: 'users', columns: [{ name: 'id', type: 'uuid' }, { name: 'email', type: 'varchar' }] },
                        { name: 'profiles', columns: [{ name: 'user_id', type: 'uuid' }, { name: 'bio', type: 'text' }] },
                    ],
                },
                status: 'draft',
            } as DatabaseNodeData,
        },
        {
            id: 'db-2',
            type: 'database',
            position: { x: 500, y: 500 },
            data: {
                label: 'Orders DB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                schema: {
                    tables: [
                        { name: 'orders', columns: [{ name: 'id', type: 'uuid' }, { name: 'status', type: 'varchar' }] },
                    ],
                },
                status: 'draft',
            } as DatabaseNodeData,
        },
    ],
    edges: [],
    metadata: {
        author: 'YAPPC Team',
        version: '1.0.0',
    },
};

/**
 * Infrastructure Canvas Template
 *
 * Cloud infrastructure design with AWS/GCP/Azure components.
 */
export const infrastructureCanvasTemplate: JourneyTemplate = {
    id: 'tpl-arch-infrastructure',
    name: 'Cloud Infrastructure Canvas',
    description: 'Design cloud infrastructure with VPCs, subnets, and services',
    persona: 'architect',
    category: 'design',
    tags: ['infrastructure', 'cloud', 'aws', 'terraform', 'devops'],
    nodes: [
        {
            id: 'service-lb',
            type: 'service',
            position: { x: 300, y: 50 },
            data: {
                label: 'Load Balancer',
                type: 'service',
                persona: 'architect',
                technology: 'nodejs',
                status: 'draft',
                description: 'Application Load Balancer',
            } as ServiceNodeData,
        },
        {
            id: 'service-app-1',
            type: 'service',
            position: { x: 150, y: 200 },
            data: {
                label: 'App Server 1',
                type: 'service',
                persona: 'architect',
                technology: 'java',
                config: { replicas: 1, cpu: '2000m', memory: '4Gi' },
                status: 'draft',
            } as ServiceNodeData,
        },
        {
            id: 'service-app-2',
            type: 'service',
            position: { x: 450, y: 200 },
            data: {
                label: 'App Server 2',
                type: 'service',
                persona: 'architect',
                technology: 'java',
                config: { replicas: 1, cpu: '2000m', memory: '4Gi' },
                status: 'draft',
            } as ServiceNodeData,
        },
        {
            id: 'db-primary',
            type: 'database',
            position: { x: 200, y: 400 },
            data: {
                label: 'Primary DB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                status: 'draft',
            } as DatabaseNodeData,
        },
        {
            id: 'db-replica',
            type: 'database',
            position: { x: 450, y: 400 },
            data: {
                label: 'Read Replica',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                status: 'draft',
            } as DatabaseNodeData,
        },
    ],
    edges: [],
    metadata: {
        author: 'YAPPC Team',
        version: '1.0.0',
    },
};

// ============================================================================
// DEVELOPER TEMPLATES
// ============================================================================

/**
 * API Design Canvas Template
 *
 * Design REST API endpoints with request/response schemas.
 */
export const apiDesignCanvasTemplate: JourneyTemplate = {
    id: 'tpl-dev-api-design',
    name: 'REST API Design Canvas',
    description: 'Design API endpoints with schemas and generate OpenAPI specs',
    persona: 'developer',
    category: 'implementation',
    tags: ['api', 'rest', 'openapi', 'backend', 'endpoints'],
    nodes: [
        {
            id: 'api-list',
            type: 'apiEndpoint',
            position: { x: 100, y: 100 },
            data: {
                label: 'List Resources',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'GET',
                path: '/api/v1/resources',
                status: 'draft',
            } as APIEndpointNodeData,
        },
        {
            id: 'api-get',
            type: 'apiEndpoint',
            position: { x: 100, y: 250 },
            data: {
                label: 'Get Resource',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'GET',
                path: '/api/v1/resources/:id',
                status: 'draft',
            } as APIEndpointNodeData,
        },
        {
            id: 'api-create',
            type: 'apiEndpoint',
            position: { x: 350, y: 100 },
            data: {
                label: 'Create Resource',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'POST',
                path: '/api/v1/resources',
                status: 'draft',
            } as APIEndpointNodeData,
        },
        {
            id: 'api-update',
            type: 'apiEndpoint',
            position: { x: 350, y: 250 },
            data: {
                label: 'Update Resource',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'PUT',
                path: '/api/v1/resources/:id',
                status: 'draft',
            } as APIEndpointNodeData,
        },
        {
            id: 'api-delete',
            type: 'apiEndpoint',
            position: { x: 350, y: 400 },
            data: {
                label: 'Delete Resource',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'DELETE',
                path: '/api/v1/resources/:id',
                status: 'draft',
            } as APIEndpointNodeData,
        },
        {
            id: 'service-backend',
            type: 'service',
            position: { x: 600, y: 200 },
            data: {
                label: 'Resource Service',
                type: 'service',
                persona: 'developer',
                technology: 'nodejs',
                status: 'draft',
            } as ServiceNodeData,
        },
        {
            id: 'db-resources',
            type: 'database',
            position: { x: 600, y: 400 },
            data: {
                label: 'Resources DB',
                type: 'database',
                persona: 'developer',
                engine: 'postgres',
                status: 'draft',
            } as DatabaseNodeData,
        },
    ],
    edges: [],
    metadata: {
        author: 'YAPPC Team',
        version: '1.0.0',
    },
};

// ============================================================================
// QA TEMPLATES
// ============================================================================

/**
 * Test Plan Canvas Template
 *
 * Plan test coverage with unit, integration, and E2E tests.
 */
export const testPlanCanvasTemplate: JourneyTemplate = {
    id: 'tpl-qa-test-plan',
    name: 'Test Plan Canvas',
    description: 'Plan comprehensive test coverage across unit, integration, and E2E',
    persona: 'qa',
    category: 'testing',
    tags: ['testing', 'qa', 'coverage', 'test-plan'],
    nodes: [
        {
            id: 'test-unit',
            type: 'testSuite',
            position: { x: 100, y: 100 },
            data: {
                label: 'Unit Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'unit',
                testCount: 0,
                passRate: 0,
                status: 'draft',
            } as TestSuiteNodeData,
        },
        {
            id: 'test-integration',
            type: 'testSuite',
            position: { x: 350, y: 100 },
            data: {
                label: 'Integration Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'integration',
                testCount: 0,
                passRate: 0,
                status: 'draft',
            } as TestSuiteNodeData,
        },
        {
            id: 'test-e2e',
            type: 'testSuite',
            position: { x: 600, y: 100 },
            data: {
                label: 'E2E Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'e2e',
                testCount: 0,
                passRate: 0,
                status: 'draft',
            } as TestSuiteNodeData,
        },
        {
            id: 'test-perf',
            type: 'testSuite',
            position: { x: 350, y: 280 },
            data: {
                label: 'Performance Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'performance',
                testCount: 0,
                passRate: 0,
                status: 'draft',
            } as TestSuiteNodeData,
        },
    ],
    edges: [],
    metadata: {
        author: 'YAPPC Team',
        version: '1.0.0',
    },
};

// ============================================================================
// FULL-STACK TEMPLATES
// ============================================================================

/**
 * Full-Stack Feature Canvas Template
 *
 * End-to-end feature design from UI to database.
 */
export const fullStackFeatureTemplate: JourneyTemplate = {
    id: 'tpl-fullstack-feature',
    name: 'Full-Stack Feature Canvas',
    description: 'Design a complete feature from UI screens through API to database',
    persona: 'all',
    category: 'implementation',
    tags: ['full-stack', 'feature', 'ui', 'api', 'database'],
    nodes: [
        // UI Layer
        {
            id: 'ui-list',
            type: 'uiScreen',
            position: { x: 100, y: 100 },
            data: {
                label: 'List View',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'list',
                status: 'draft',
            } as UIScreenNodeData,
        },
        {
            id: 'ui-detail',
            type: 'uiScreen',
            position: { x: 100, y: 280 },
            data: {
                label: 'Detail View',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'detail',
                status: 'draft',
            } as UIScreenNodeData,
        },
        {
            id: 'ui-form',
            type: 'uiScreen',
            position: { x: 100, y: 460 },
            data: {
                label: 'Form View',
                type: 'uiScreen',
                persona: 'ux',
                screenType: 'edit',
                status: 'draft',
            } as UIScreenNodeData,
        },
        // API Layer
        {
            id: 'api-crud',
            type: 'apiEndpoint',
            position: { x: 350, y: 200 },
            data: {
                label: 'CRUD Endpoints',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'GET',
                path: '/api/v1/features',
                status: 'draft',
            } as APIEndpointNodeData,
        },
        // Service Layer
        {
            id: 'service-feature',
            type: 'service',
            position: { x: 550, y: 200 },
            data: {
                label: 'Feature Service',
                type: 'service',
                persona: 'developer',
                technology: 'nodejs',
                status: 'draft',
            } as ServiceNodeData,
        },
        // Database Layer
        {
            id: 'db-feature',
            type: 'database',
            position: { x: 550, y: 400 },
            data: {
                label: 'Feature DB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                status: 'draft',
            } as DatabaseNodeData,
        },
        // Test Layer
        {
            id: 'test-feature',
            type: 'testSuite',
            position: { x: 800, y: 200 },
            data: {
                label: 'Feature Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'integration',
                testCount: 0,
                passRate: 0,
                status: 'draft',
            } as TestSuiteNodeData,
        },
    ],
    edges: [],
    metadata: {
        author: 'YAPPC Team',
        version: '1.0.0',
    },
};

// ============================================================================
// TEMPLATE REGISTRY
// ============================================================================

export const journeyTemplates: JourneyTemplate[] = [
    // PM Templates
    requirementsCanvasTemplate,
    userFlowCanvasTemplate,
    // Architect Templates
    systemDesignCanvasTemplate,
    infrastructureCanvasTemplate,
    // Developer Templates
    apiDesignCanvasTemplate,
    // QA Templates
    testPlanCanvasTemplate,
    // Full-Stack Templates
    fullStackFeatureTemplate,
];

/**
 * Get templates by persona
 */
export function getTemplatesByPersona(persona: JourneyTemplate['persona']): JourneyTemplate[] {
    if (persona === 'all') return journeyTemplates;
    return journeyTemplates.filter((t) => t.persona === persona || t.persona === 'all');
}

/**
 * Get templates by category
 */
export function getTemplatesByCategory(category: JourneyTemplate['category']): JourneyTemplate[] {
    return journeyTemplates.filter((t) => t.category === category);
}

/**
 * Get template by ID
 */
export function getTemplateById(id: string): JourneyTemplate | undefined {
    return journeyTemplates.find((t) => t.id === id);
}
