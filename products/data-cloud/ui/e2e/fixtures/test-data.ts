/**
 * Test Data Fixtures
 * 
 * Provides mock data for E2E tests.
 * 
 * @doc.type fixture
 * @doc.purpose Test data for E2E testing
 * @doc.layer testing
 */

export const mockCollections = [
  {
    id: 'col-1',
    name: 'Products',
    description: 'Product catalog collection',
    schemaType: 'entity',
    status: 'active',
    entityCount: 1250,
    schema: {
      fields: [
        { name: 'name', type: 'string', required: true },
        { name: 'price', type: 'number', required: true },
        { name: 'description', type: 'string', required: false },
      ],
    },
    tags: ['catalog', 'products'],
    createdAt: new Date('2024-01-01').toISOString(),
    updatedAt: new Date('2024-01-15').toISOString(),
    createdBy: 'user-1',
  },
  {
    id: 'col-2',
    name: 'Customers',
    description: 'Customer data collection',
    schemaType: 'entity',
    status: 'active',
    entityCount: 5420,
    schema: {
      fields: [
        { name: 'email', type: 'email', required: true },
        { name: 'name', type: 'string', required: true },
        { name: 'phone', type: 'string', required: false },
      ],
    },
    tags: ['crm', 'customers'],
    createdAt: new Date('2024-01-05').toISOString(),
    updatedAt: new Date('2024-01-20').toISOString(),
    createdBy: 'user-1',
  },
];

export const mockWorkflows = [
  {
    id: 'wf-1',
    name: 'Data Export',
    description: 'Export data to external system',
    status: 'active',
    executionCount: 125,
    lastExecutedAt: new Date('2024-01-20T10:30:00').toISOString(),
    createdAt: new Date('2024-01-01').toISOString(),
    updatedAt: new Date('2024-01-15').toISOString(),
  },
  {
    id: 'wf-2',
    name: 'Data Sync',
    description: 'Sync data between systems',
    status: 'active',
    executionCount: 342,
    lastExecutedAt: new Date('2024-01-21T14:15:00').toISOString(),
    createdAt: new Date('2024-01-03').toISOString(),
    updatedAt: new Date('2024-01-18').toISOString(),
  },
];

export const mockExecutions = [
  {
    id: 'exec-1',
    workflowId: 'wf-1',
    status: 'completed',
    startedAt: new Date('2024-01-20T10:30:00').toISOString(),
    completedAt: new Date('2024-01-20T10:30:15').toISOString(),
    duration: 15000,
  },
  {
    id: 'exec-2',
    workflowId: 'wf-1',
    status: 'failed',
    startedAt: new Date('2024-01-19T09:15:00').toISOString(),
    completedAt: new Date('2024-01-19T09:15:30').toISOString(),
    duration: 30000,
    error: 'Connection timeout',
  },
];

export const mockEntities = [
  {
    id: 'ent-1',
    collectionId: 'col-1',
    data: {
      name: 'Product A',
      price: 99.99,
      description: 'High quality product',
    },
    createdAt: new Date('2024-01-10').toISOString(),
    updatedAt: new Date('2024-01-10').toISOString(),
  },
  {
    id: 'ent-2',
    collectionId: 'col-1',
    data: {
      name: 'Product B',
      price: 149.99,
      description: 'Premium product',
    },
    createdAt: new Date('2024-01-11').toISOString(),
    updatedAt: new Date('2024-01-11').toISOString(),
  },
];
