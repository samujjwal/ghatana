/**
 * Example Journey Flows
 * 
 * Complete end-to-end workflow examples demonstrating persona collaboration.
 * These examples show how different personas work together from requirements to deployment.
 * 
 * @module exampleJourneys
 */

import type { Node, Edge } from '@xyflow/react';
import type {
    AIPromptNodeData,
    PersonaNodeData,
    DatabaseNodeData,
    ServiceNodeData,
    APIEndpointNodeData,
    TestSuiteNodeData,
    UIScreenNodeData,
} from '../components/PersonaNodes';

// ============================================================================
// EXAMPLE 1: E-COMMERCE PRODUCT CATALOG FEATURE
// Full journey: PM → Architect → Developer → QA
// ============================================================================

/**
 * Complete workflow for building an e-commerce product catalog feature
 * 
 * Flow:
 * 1. PM: Define requirements with AI brainstorming
 * 2. Architect: Design database schema and service architecture
 * 3. Developer: Implement API endpoints and services
 * 4. QA: Create comprehensive test suites
 */
export const ecommerceProductCatalogJourney = {
    id: 'example-ecommerce-catalog',
    name: 'E-Commerce Product Catalog',
    description: 'Complete PM → Architect → Developer → QA workflow for product catalog feature',

    nodes: [
        // ========== PM Phase ==========
        {
            id: 'pm-aiPrompt-1',
            type: 'aiPrompt',
            position: { x: 100, y: 100 },
            data: {
                label: 'Feature Discovery',
                type: 'aiPrompt',
                persona: 'pm',
                prompt: 'Design a modern product catalog system that supports:\n- Product search and filtering\n- Categories and subcategories\n- Inventory tracking\n- Dynamic pricing\n- Product recommendations',
                status: 'completed',
                aiResponse: 'Based on your requirements, I recommend a microservice architecture with...',
            } as AIPromptNodeData,
        },
        {
            id: 'pm-requirement-1',
            type: 'requirement',
            position: { x: 100, y: 300 },
            data: {
                label: 'Product Search API',
                type: 'requirement',
                persona: 'pm',
            } as PersonaNodeData,
        },
        {
            id: 'pm-requirement-2',
            type: 'requirement',
            position: { x: 100, y: 500 },
            data: {
                label: 'Product Management',
                type: 'requirement',
                persona: 'pm',
            } as PersonaNodeData,
        },

        // ========== Architect Phase ==========
        {
            id: 'arch-database-1',
            type: 'database',
            position: { x: 500, y: 200 },
            data: {
                label: 'Product Catalog DB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                schema: {
                    tables: [
                        {
                            name: 'products',
                            columns: [
                                { name: 'id', type: 'uuid', nullable: false },
                                { name: 'name', type: 'varchar(255)', nullable: false },
                                { name: 'description', type: 'text', nullable: true },
                                { name: 'price', type: 'decimal(10,2)', nullable: false },
                                { name: 'category_id', type: 'uuid', nullable: false },
                                { name: 'inventory_count', type: 'integer', nullable: false },
                                { name: 'is_active', type: 'boolean', nullable: false },
                                { name: 'created_at', type: 'timestamp', nullable: false },
                                { name: 'updated_at', type: 'timestamp', nullable: false },
                            ],
                        },
                        {
                            name: 'categories',
                            columns: [
                                { name: 'id', type: 'uuid', nullable: false },
                                { name: 'name', type: 'varchar(100)', nullable: false },
                                { name: 'parent_id', type: 'uuid', nullable: true },
                                { name: 'slug', type: 'varchar(100)', nullable: false },
                            ],
                        },
                        {
                            name: 'product_images',
                            columns: [
                                { name: 'id', type: 'uuid', nullable: false },
                                { name: 'product_id', type: 'uuid', nullable: false },
                                { name: 'url', type: 'varchar(500)', nullable: false },
                                { name: 'is_primary', type: 'boolean', nullable: false },
                                { name: 'sort_order', type: 'integer', nullable: false },
                            ],
                        },
                    ],
                },
            } as DatabaseNodeData,
        },
        {
            id: 'arch-service-1',
            type: 'service',
            position: { x: 500, y: 500 },
            data: {
                label: 'Product Catalog Service',
                type: 'service',
                persona: 'architect',
                framework: 'fastify',
                config: {
                    port: 3001,
                    replicas: 3,
                    cpu: '500m',
                    memory: '512Mi',
                },
            } as ServiceNodeData,
        },

        // ========== Developer Phase ==========
        {
            id: 'dev-api-1',
            type: 'apiEndpoint',
            position: { x: 900, y: 150 },
            data: {
                label: 'Search Products',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'GET',
                path: '/api/products/search',
                description: 'Search and filter products',
                requestSchema: {
                    query: {
                        q: 'string (search query)',
                        category: 'string (category slug)',
                        minPrice: 'number',
                        maxPrice: 'number',
                        page: 'number (default: 1)',
                        limit: 'number (default: 20, max: 50)',
                        sort: 'price_asc | price_desc | relevance | newest',
                    },
                },
                responseSchema: {
                    products: 'Array<Product>',
                    pagination: {
                        total: 'number',
                        page: 'number',
                        pages: 'number',
                    },
                },
            } as APIEndpointNodeData,
        },
        {
            id: 'dev-api-2',
            type: 'apiEndpoint',
            position: { x: 900, y: 350 },
            data: {
                label: 'Create Product',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'POST',
                path: '/api/products',
                description: 'Create a new product',
                requestSchema: {
                    body: {
                        name: 'string (required, max 255)',
                        description: 'string',
                        price: 'number (required, > 0)',
                        categoryId: 'uuid (required)',
                        inventoryCount: 'number (required, >= 0)',
                        images: 'Array<string> (URLs)',
                    },
                },
                responseSchema: {
                    product: 'Product',
                },
                authentication: 'JWT (admin role)',
            } as APIEndpointNodeData,
        },
        {
            id: 'dev-api-3',
            type: 'apiEndpoint',
            position: { x: 900, y: 550 },
            data: {
                label: 'Update Product',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'PUT',
                path: '/api/products/:id',
                description: 'Update product details',
                requestSchema: {
                    params: { id: 'uuid' },
                    body: {
                        name: 'string (optional, max 255)',
                        description: 'string',
                        price: 'number (optional, > 0)',
                        inventoryCount: 'number (optional, >= 0)',
                        isActive: 'boolean',
                    },
                },
                responseSchema: {
                    product: 'Product',
                },
                authentication: 'JWT (admin role)',
            } as APIEndpointNodeData,
        },

        // ========== QA Phase ==========
        {
            id: 'qa-test-1',
            type: 'testSuite',
            position: { x: 1300, y: 250 },
            data: {
                label: 'Product API Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'integration',
                tests: [
                    {
                        name: 'Search products with filters',
                        status: 'passing',
                        description: 'Test product search with various filter combinations',
                    },
                    {
                        name: 'Create product with valid data',
                        status: 'passing',
                        description: 'Verify product creation with all required fields',
                    },
                    {
                        name: 'Create product with invalid data',
                        status: 'passing',
                        description: 'Ensure validation errors for invalid input',
                    },
                    {
                        name: 'Update product inventory',
                        status: 'passing',
                        description: 'Test inventory updates and concurrent modifications',
                    },
                    {
                        name: 'Pagination and sorting',
                        status: 'passing',
                        description: 'Verify correct pagination and sort behavior',
                    },
                ],
                coverage: 92,
            } as TestSuiteNodeData,
        },
        {
            id: 'qa-test-2',
            type: 'testSuite',
            position: { x: 1300, y: 500 },
            data: {
                label: 'Performance Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'e2e',
                tests: [
                    {
                        name: 'Search response time < 200ms (p95)',
                        status: 'passing',
                        description: 'Load test: 1000 concurrent users searching products',
                    },
                    {
                        name: 'Database query optimization',
                        status: 'passing',
                        description: 'Verify indexes are used for search queries',
                    },
                    {
                        name: 'Image upload performance',
                        status: 'passing',
                        description: 'Test concurrent image uploads (10MB files)',
                    },
                ],
                coverage: 85,
            } as TestSuiteNodeData,
        },
    ] as Node[],

    edges: [
        // PM → Architect
        { id: 'e1', source: 'pm-aiPrompt-1', target: 'pm-requirement-1', type: 'smoothstep', animated: true },
        { id: 'e2', source: 'pm-aiPrompt-1', target: 'pm-requirement-2', type: 'smoothstep', animated: true },
        { id: 'e3', source: 'pm-requirement-1', target: 'arch-database-1', type: 'smoothstep', label: 'Data Model' },
        { id: 'e4', source: 'pm-requirement-2', target: 'arch-database-1', type: 'smoothstep', label: 'Schema' },
        { id: 'e5', source: 'arch-database-1', target: 'arch-service-1', type: 'smoothstep' },

        // Architect → Developer
        { id: 'e6', source: 'arch-service-1', target: 'dev-api-1', type: 'smoothstep', label: 'Implementation' },
        { id: 'e7', source: 'arch-service-1', target: 'dev-api-2', type: 'smoothstep' },
        { id: 'e8', source: 'arch-service-1', target: 'dev-api-3', type: 'smoothstep' },

        // Developer → QA
        { id: 'e9', source: 'dev-api-1', target: 'qa-test-1', type: 'smoothstep', label: 'Test' },
        { id: 'e10', source: 'dev-api-2', target: 'qa-test-1', type: 'smoothstep' },
        { id: 'e11', source: 'dev-api-3', target: 'qa-test-1', type: 'smoothstep' },
        { id: 'e12', source: 'dev-api-1', target: 'qa-test-2', type: 'smoothstep', label: 'Performance' },
    ] as Edge[],
};

// ============================================================================
// EXAMPLE 2: USER AUTHENTICATION SYSTEM
// Simplified journey: PM → Developer → QA
// ============================================================================

/**
 * User authentication system implementation
 * 
 * Flow:
 * 1. PM: Define auth requirements
 * 2. Developer: Implement JWT auth service and endpoints
 * 3. QA: Security and integration tests
 */
export const userAuthenticationJourney = {
    id: 'example-user-auth',
    name: 'User Authentication System',
    description: 'PM → Developer → QA workflow for user auth',

    nodes: [
        // PM Phase
        {
            id: 'pm-req-auth',
            type: 'requirement',
            position: { x: 100, y: 200 },
            data: {
                label: 'User Authentication',
                type: 'requirement',
                persona: 'pm',
            } as PersonaNodeData,
        },

        // Developer Phase
        {
            id: 'dev-service-auth',
            type: 'service',
            position: { x: 500, y: 200 },
            data: {
                label: 'Auth Service',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
                config: {
                    port: 3002,
                    replicas: 2,
                    cpu: '250m',
                    memory: '256Mi',
                },
            } as ServiceNodeData,
        },
        {
            id: 'dev-api-login',
            type: 'apiEndpoint',
            position: { x: 500, y: 400 },
            data: {
                label: 'Login',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'POST',
                path: '/api/auth/login',
                description: 'User login with email and password',
                requestSchema: {
                    body: {
                        email: 'string (email format)',
                        password: 'string (min 8 chars)',
                    },
                },
                responseSchema: {
                    accessToken: 'string (JWT)',
                    refreshToken: 'string',
                    expiresIn: 'number (seconds)',
                },
            } as APIEndpointNodeData,
        },

        // QA Phase
        {
            id: 'qa-test-auth',
            type: 'testSuite',
            position: { x: 900, y: 300 },
            data: {
                label: 'Auth Security Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'integration',
                tests: [
                    { name: 'Valid login returns JWT', status: 'passing', description: '' },
                    { name: 'Invalid password rejected', status: 'passing', description: '' },
                    { name: 'Rate limiting enforced', status: 'passing', description: '' },
                    { name: 'Expired token rejected', status: 'passing', description: '' },
                    { name: 'Refresh token rotation', status: 'passing', description: '' },
                ],
                coverage: 95,
            } as TestSuiteNodeData,
        },
    ] as Node[],

    edges: [
        { id: 'e1', source: 'pm-req-auth', target: 'dev-service-auth', type: 'smoothstep' },
        { id: 'e2', source: 'dev-service-auth', target: 'dev-api-login', type: 'smoothstep' },
        { id: 'e3', source: 'dev-api-login', target: 'qa-test-auth', type: 'smoothstep' },
    ] as Edge[],
};

// ============================================================================
// EXAMPLE 3: NOTIFICATION SYSTEM
// Architecture-focused journey: Architect → Developer → QA
// ============================================================================

/**
 * Multi-channel notification system
 * 
 * Flow:
 * 1. Architect: Design event-driven notification architecture
 * 2. Developer: Implement notification service with multiple channels
 * 3. QA: Test notification delivery and reliability
 */
export const notificationSystemJourney = {
    id: 'example-notifications',
    name: 'Notification System',
    description: 'Architect → Developer → QA workflow for notifications',

    nodes: [
        // Architect Phase
        {
            id: 'arch-db-notifications',
            type: 'database',
            position: { x: 100, y: 200 },
            data: {
                label: 'Notifications DB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                schema: {
                    tables: [
                        {
                            name: 'notifications',
                            columns: [
                                { name: 'id', type: 'uuid', nullable: false },
                                { name: 'user_id', type: 'uuid', nullable: false },
                                { name: 'type', type: 'varchar(50)', nullable: false },
                                { name: 'channel', type: 'varchar(20)', nullable: false },
                                { name: 'status', type: 'varchar(20)', nullable: false },
                                { name: 'content', type: 'jsonb', nullable: false },
                                { name: 'sent_at', type: 'timestamp', nullable: true },
                            ],
                        },
                    ],
                },
            } as DatabaseNodeData,
        },
        {
            id: 'arch-service-notify',
            type: 'service',
            position: { x: 500, y: 200 },
            data: {
                label: 'Notification Service',
                type: 'service',
                persona: 'architect',
                framework: 'fastify',
                config: {
                    port: 3003,
                    replicas: 2,
                    cpu: '250m',
                    memory: '256Mi',
                },
            } as ServiceNodeData,
        },

        // Developer Phase
        {
            id: 'dev-api-send',
            type: 'apiEndpoint',
            position: { x: 900, y: 150 },
            data: {
                label: 'Send Notification',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'POST',
                path: '/api/notifications/send',
                description: 'Send notification via email, SMS, or push',
                requestSchema: {
                    body: {
                        userId: 'uuid',
                        channel: 'email | sms | push',
                        type: 'order_confirmed | payment_received | etc',
                        data: 'object (channel-specific data)',
                    },
                },
                responseSchema: {
                    notificationId: 'uuid',
                    status: 'queued | sent | failed',
                },
            } as APIEndpointNodeData,
        },

        // QA Phase
        {
            id: 'qa-test-notify',
            type: 'testSuite',
            position: { x: 1200, y: 200 },
            data: {
                label: 'Notification Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'integration',
                tests: [
                    { name: 'Email delivery success', status: 'passing', description: '' },
                    { name: 'SMS retry on failure', status: 'passing', description: '' },
                    { name: 'Push notification batching', status: 'passing', description: '' },
                    { name: 'Rate limiting per user', status: 'passing', description: '' },
                ],
                coverage: 88,
            } as TestSuiteNodeData,
        },
    ] as Node[],

    edges: [
        { id: 'e1', source: 'arch-db-notifications', target: 'arch-service-notify', type: 'smoothstep' },
        { id: 'e2', source: 'arch-service-notify', target: 'dev-api-send', type: 'smoothstep' },
        { id: 'e3', source: 'dev-api-send', target: 'qa-test-notify', type: 'smoothstep' },
    ] as Edge[],
};

/**
 * All example journeys
 */
export const exampleJourneys = [
    ecommerceProductCatalogJourney,
    userAuthenticationJourney,
    notificationSystemJourney,
];

/**
 * Get example journey by ID
 */
export function getExampleJourney(id: string) {
    return exampleJourneys.find(j => j.id === id);
}
