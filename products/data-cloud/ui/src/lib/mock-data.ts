/**
 * Mock data for Collection Entity System UI
 *
 * Provides comprehensive demo data for all features with E2E capabilities.
 *
 * @doc.type module
 * @doc.purpose Mock data for UI demonstration
 * @doc.layer frontend
 */

 // Lightweight local id generator to avoid external runtime dependency in dev
 export function generateId(prefix = 'id'): string {
   return `${prefix}-${Math.random().toString(36).slice(2, 9)}-${Date.now().toString(36)}`;
 }

// ============================================================================
// TYPES
// ============================================================================

export interface MockCollection {
  id: string;
  name: string;
  description: string;
  entityCount: number;
  schema: MockSchema;
  createdAt: string;
  updatedAt: string;
  isActive: boolean;
}

export interface MockSchema {
  id: string;
  name: string;
  fields: MockField[];
  constraints: MockConstraint[];
}

export interface MockField {
  id: string;
  name: string;
  type: 'string' | 'number' | 'boolean' | 'date' | 'email' | 'url' | 'text';
  required: boolean;
  maxLength?: number;
  minLength?: number;
  pattern?: string;
  enum?: string[];
  description?: string;
}

export interface MockConstraint {
  id: string;
  name: string;
  type: 'unique' | 'foreign_key' | 'check' | 'not_null';
  fields: string[];
  description?: string;
}

export interface MockEntity {
  id: string;
  collectionId: string;
  data: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

export interface MockWorkflow {
  id: string;
  name: string;
  description: string;
  nodes: MockWorkflowNode[];
  edges: MockWorkflowEdge[];
  triggers: MockTrigger[];
  status: 'draft' | 'active' | 'paused' | 'archived';
  createdAt: string;
  updatedAt: string;
  executionCount: number;
  lastExecutedAt?: string;
}

export interface MockWorkflowNode {
  id: string;
  type:
    | 'start'
    | 'end'
    | 'query'
    | 'transform'
    | 'decision'
    | 'approval'
    | 'api_call'
    | 'notification';
  label: string;
  data: Record<string, any>;
  position: { x: number; y: number };
  status?: 'idle' | 'running' | 'completed' | 'failed';
}

export interface MockWorkflowEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  condition?: string;
}

export interface MockTrigger {
  id: string;
  // Added 'form' as a valid trigger type used in mock workflows (aligns sample data)
  type: 'event' | 'schedule' | 'webhook' | 'manual' | 'form';
  name: string;
  config: Record<string, any>;
  isActive: boolean;
}

export interface MockExecution {
  id: string;
  workflowId: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  nodeStatuses: Record<string, 'idle' | 'running' | 'completed' | 'failed'>;
  startedAt: string;
  completedAt?: string;
  duration?: number;
  logs: MockExecutionLog[];
  error?: string;
}

export interface MockExecutionLog {
  timestamp: string;
  nodeId: string;
  message: string;
  level: 'info' | 'warn' | 'error';
  data?: Record<string, any>;
}

// ============================================================================
// MOCK COLLECTIONS
// ============================================================================

const products: MockField[] = [
  {
    id: 'field-1',
    name: 'productId',
    type: 'string',
    required: true,
    description: 'Unique product identifier',
  },
  {
    id: 'field-2',
    name: 'name',
    type: 'string',
    required: true,
    maxLength: 255,
    description: 'Product name',
  },
  {
    id: 'field-3',
    name: 'description',
    type: 'text',
    required: false,
    description: 'Detailed product description',
  },
  {
    id: 'field-4',
    name: 'price',
    type: 'number',
    required: true,
    description: 'Product price in USD',
  },
  {
    id: 'field-5',
    name: 'category',
    type: 'string',
    required: true,
    enum: ['Electronics', 'Clothing', 'Books', 'Home', 'Sports'],
    description: 'Product category',
  },
  {
    id: 'field-6',
    name: 'inStock',
    type: 'boolean',
    required: true,
    description: 'Availability status',
  },
  {
    id: 'field-7',
    name: 'quantity',
    type: 'number',
    required: true,
    description: 'Quantity in inventory',
  },
  {
    id: 'field-8',
    name: 'createdDate',
    type: 'date',
    required: true,
    description: 'Product creation date',
  },
];

const users: MockField[] = [
  {
    id: 'field-1',
    name: 'userId',
    type: 'string',
    required: true,
    description: 'Unique user identifier',
  },
  {
    id: 'field-2',
    name: 'email',
    type: 'email',
    required: true,
    description: 'User email address',
  },
  {
    id: 'field-3',
    name: 'firstName',
    type: 'string',
    required: true,
    maxLength: 100,
    description: 'User first name',
  },
  {
    id: 'field-4',
    name: 'lastName',
    type: 'string',
    required: true,
    maxLength: 100,
    description: 'User last name',
  },
  {
    id: 'field-5',
    name: 'role',
    type: 'string',
    required: true,
    enum: ['admin', 'user', 'moderator'],
    description: 'User role',
  },
  {
    id: 'field-6',
    name: 'isActive',
    type: 'boolean',
    required: true,
    description: 'User active status',
  },
  {
    id: 'field-7',
    name: 'joinDate',
    type: 'date',
    required: true,
    description: 'User account creation date',
  },
];

const orders: MockField[] = [
  {
    id: 'field-1',
    name: 'orderId',
    type: 'string',
    required: true,
    description: 'Unique order identifier',
  },
  {
    id: 'field-2',
    name: 'userId',
    type: 'string',
    required: true,
    description: 'Associated user ID',
  },
  {
    id: 'field-3',
    name: 'items',
    type: 'string', // JSON array in real scenario
    required: true,
    description: 'Order items (JSON)',
  },
  {
    id: 'field-4',
    name: 'totalAmount',
    type: 'number',
    required: true,
    description: 'Total order amount',
  },
  {
    id: 'field-5',
    name: 'status',
    type: 'string',
    required: true,
    enum: ['pending', 'processing', 'shipped', 'delivered', 'cancelled'],
    description: 'Order status',
  },
  {
    id: 'field-6',
    name: 'shippingAddress',
    type: 'text',
    required: true,
    description: 'Shipping address',
  },
  {
    id: 'field-7',
    name: 'orderDate',
    type: 'date',
    required: true,
    description: 'Order placement date',
  },
  {
    id: 'field-8',
    name: 'notes',
    type: 'text',
    required: false,
    description: 'Order notes',
  },
];

export const MOCK_COLLECTIONS: MockCollection[] = [
  {
    id: 'col-001',
    name: 'Products',
    description:
      'Product catalog with pricing, inventory, and categorization information',
    entityCount: 1250,
    schema: {
      id: 'schema-001',
      name: 'ProductSchema',
      fields: products,
      constraints: [
        {
          id: 'c1',
          name: 'unique_productId',
          type: 'unique',
          fields: ['productId'],
        },
        {
          id: 'c2',
          name: 'price_positive',
          type: 'check',
          fields: ['price'],
        },
      ],
    },
    createdAt: '2025-01-15T10:30:00Z',
    updatedAt: '2025-11-08T14:20:00Z',
    isActive: true,
  },
  {
    id: 'col-002',
    name: 'Users',
    description: 'User accounts, profiles, roles, and authentication information',
    entityCount: 5420,
    schema: {
      id: 'schema-002',
      name: 'UserSchema',
      fields: users,
      constraints: [
        {
          id: 'c1',
          name: 'unique_email',
          type: 'unique',
          fields: ['email'],
        },
        {
          id: 'c2',
          name: 'unique_userId',
          type: 'unique',
          fields: ['userId'],
        },
      ],
    },
    createdAt: '2025-01-10T09:15:00Z',
    updatedAt: '2025-11-08T13:45:00Z',
    isActive: true,
  },
  {
    id: 'col-003',
    name: 'Orders',
    description: 'Customer orders with items, pricing, and fulfillment tracking',
    entityCount: 8750,
    schema: {
      id: 'schema-003',
      name: 'OrderSchema',
      fields: orders,
      constraints: [
        {
          id: 'c1',
          name: 'unique_orderId',
          type: 'unique',
          fields: ['orderId'],
        },
      ],
    },
    createdAt: '2025-01-20T11:00:00Z',
    updatedAt: '2025-11-08T15:30:00Z',
    isActive: true,
  },
];

// ============================================================================
// MOCK ENTITIES
// ============================================================================

export const MOCK_ENTITIES: Record<string, MockEntity[]> = {
  'col-001': [
    {
      id: 'ent-001',
      collectionId: 'col-001',
      data: {
        productId: 'PROD-001',
        name: 'Wireless Bluetooth Headphones',
        description: 'High-quality wireless headphones with noise cancellation',
        price: 199.99,
        category: 'Electronics',
        inStock: true,
        quantity: 45,
        createdDate: '2025-01-10',
      },
      createdAt: '2025-01-10T08:00:00Z',
      updatedAt: '2025-11-08T14:30:00Z',
    },
    {
      id: 'ent-002',
      collectionId: 'col-001',
      data: {
        productId: 'PROD-002',
        name: 'USB-C Fast Charger',
        description: '65W fast charging USB-C charger',
        price: 49.99,
        category: 'Electronics',
        inStock: true,
        quantity: 120,
        createdDate: '2025-01-12',
      },
      createdAt: '2025-01-12T09:30:00Z',
      updatedAt: '2025-11-08T14:30:00Z',
    },
    {
      id: 'ent-003',
      collectionId: 'col-001',
      data: {
        productId: 'PROD-003',
        name: 'Cotton T-Shirt',
        description: 'Comfortable 100% cotton t-shirt',
        price: 24.99,
        category: 'Clothing',
        inStock: true,
        quantity: 250,
        createdDate: '2025-01-15',
      },
      createdAt: '2025-01-15T10:00:00Z',
      updatedAt: '2025-11-08T14:30:00Z',
    },
  ],
  'col-002': [
    {
      id: 'ent-001',
      collectionId: 'col-002',
      data: {
        userId: 'USER-001',
        email: 'john.doe@example.com',
        firstName: 'John',
        lastName: 'Doe',
        role: 'admin',
        isActive: true,
        joinDate: '2025-01-05',
      },
      createdAt: '2025-01-05T08:00:00Z',
      updatedAt: '2025-11-08T14:30:00Z',
    },
    {
      id: 'ent-002',
      collectionId: 'col-002',
      data: {
        userId: 'USER-002',
        email: 'jane.smith@example.com',
        firstName: 'Jane',
        lastName: 'Smith',
        role: 'user',
        isActive: true,
        joinDate: '2025-01-08',
      },
      createdAt: '2025-01-08T09:15:00Z',
      updatedAt: '2025-11-08T14:30:00Z',
    },
    {
      id: 'ent-003',
      collectionId: 'col-002',
      data: {
        userId: 'USER-003',
        email: 'alex.johnson@example.com',
        firstName: 'Alex',
        lastName: 'Johnson',
        role: 'moderator',
        isActive: true,
        joinDate: '2025-01-12',
      },
      createdAt: '2025-01-12T10:45:00Z',
      updatedAt: '2025-11-08T14:30:00Z',
    },
  ],
};

// ============================================================================
// MOCK WORKFLOWS
// ============================================================================

export const MOCK_WORKFLOWS: MockWorkflow[] = [
  {
    id: 'wf-001',
    name: 'Process New Orders',
    description: 'Automatically process new customer orders and send confirmations',
    nodes: [
      {
        id: 'node-1',
        type: 'start',
        label: 'Order Received',
        data: { triggerType: 'webhook' },
        position: { x: 50, y: 100 },
        status: 'completed',
      },
      {
        id: 'node-2',
        type: 'query',
        label: 'Fetch Order Details',
        data: {
          collection: 'Orders',
          filter: 'status = pending',
        },
        position: { x: 250, y: 100 },
        status: 'completed',
      },
      {
        id: 'node-3',
        type: 'decision',
        label: 'Check Inventory',
        data: {
          condition: 'quantity > 0',
        },
        position: { x: 450, y: 100 },
        status: 'completed',
      },
      {
        id: 'node-4',
        type: 'api_call',
        label: 'Process Payment',
        data: {
          endpoint: 'https://api.payment.com/process',
          method: 'POST',
        },
        position: { x: 650, y: 50 },
        status: 'completed',
      },
      {
        id: 'node-5',
        type: 'notification',
        label: 'Send Confirmation',
        data: {
          channel: 'email',
          template: 'order_confirmation',
        },
        position: { x: 650, y: 150 },
        status: 'idle',
      },
      {
        id: 'node-6',
        type: 'end',
        label: 'Order Processed',
        data: {},
        position: { x: 850, y: 100 },
        status: 'idle',
      },
    ],
    edges: [
      { id: 'edge-1', source: 'node-1', target: 'node-2' },
      { id: 'edge-2', source: 'node-2', target: 'node-3' },
      { id: 'edge-3', source: 'node-3', target: 'node-4', condition: 'true' },
      { id: 'edge-4', source: 'node-4', target: 'node-5' },
      { id: 'edge-5', source: 'node-5', target: 'node-6' },
    ],
    triggers: [
      {
        id: 'trig-1',
        type: 'webhook',
        name: 'Order Webhook',
        config: {
          url: '/webhooks/orders',
          method: 'POST',
        },
        isActive: true,
      },
    ],
    status: 'active',
    createdAt: '2025-02-01T10:00:00Z',
    updatedAt: '2025-11-08T14:30:00Z',
    executionCount: 1250,
    lastExecutedAt: '2025-11-08T14:25:00Z',
  },
  {
    id: 'wf-002',
    name: 'User Onboarding',
    description: 'Automated user registration, verification, and welcome flow',
    nodes: [
      {
        id: 'node-1',
        type: 'start',
        label: 'User Registration',
        data: { triggerType: 'form' },
        position: { x: 50, y: 100 },
        status: 'completed',
      },
      {
        id: 'node-2',
        type: 'query',
        label: 'Validate Email',
        data: {
          collection: 'Users',
          filter: 'email exists',
        },
        position: { x: 250, y: 100 },
        status: 'completed',
      },
      {
        id: 'node-3',
        type: 'transform',
        label: 'Transform Data',
        data: {
          mapping: {
            firstName: 'first_name',
            lastName: 'last_name',
          },
        },
        position: { x: 450, y: 100 },
        status: 'completed',
      },
      {
        id: 'node-4',
        type: 'api_call',
        label: 'Send Verification Email',
        data: {
          endpoint: 'https://api.email.com/send',
          template: 'verification',
        },
        position: { x: 650, y: 100 },
        status: 'completed',
      },
      {
        id: 'node-5',
        type: 'end',
        label: 'Welcome Email Sent',
        data: {},
        position: { x: 850, y: 100 },
        status: 'idle',
      },
    ],
    edges: [
      { id: 'edge-1', source: 'node-1', target: 'node-2' },
      { id: 'edge-2', source: 'node-2', target: 'node-3' },
      { id: 'edge-3', source: 'node-3', target: 'node-4' },
      { id: 'edge-4', source: 'node-4', target: 'node-5' },
    ],
    triggers: [
      {
        id: 'trig-1',
        type: 'form',
        name: 'Registration Form',
        config: {
          formId: 'registration-form',
        },
        isActive: true,
      },
    ],
    status: 'active',
    createdAt: '2025-02-05T11:30:00Z',
    updatedAt: '2025-11-08T14:30:00Z',
    executionCount: 890,
    lastExecutedAt: '2025-11-08T13:50:00Z',
  },
  {
    id: 'wf-003',
    name: 'Data Validation Pipeline',
    description: 'Validates and cleanses product data before storage',
    nodes: [
      {
        id: 'node-1',
        type: 'start',
        label: 'Data Import',
        data: { source: 'csv' },
        position: { x: 50, y: 100 },
        status: 'idle',
      },
      {
        id: 'node-2',
        type: 'transform',
        label: 'Normalize Data',
        data: {
          operations: ['trim', 'lowercase', 'remove_duplicates'],
        },
        position: { x: 250, y: 100 },
        status: 'idle',
      },
      {
        id: 'node-3',
        type: 'query',
        label: 'Validate Schema',
        data: {
          schema: 'ProductSchema',
        },
        position: { x: 450, y: 100 },
        status: 'idle',
      },
      {
        id: 'node-4',
        type: 'decision',
        label: 'Check Validity',
        data: {
          condition: 'all_fields_valid',
        },
        position: { x: 650, y: 50 },
        status: 'idle',
      },
      {
        id: 'node-5',
        type: 'end',
        label: 'Store Valid Data',
        data: { action: 'save' },
        position: { x: 850, y: 50 },
        status: 'idle',
      },
    ],
    edges: [
      { id: 'edge-1', source: 'node-1', target: 'node-2' },
      { id: 'edge-2', source: 'node-2', target: 'node-3' },
      { id: 'edge-3', source: 'node-3', target: 'node-4' },
      { id: 'edge-4', source: 'node-4', target: 'node-5', condition: 'true' },
    ],
    triggers: [
      {
        id: 'trig-1',
        type: 'schedule',
        name: 'Daily Validation',
        config: {
          schedule: '0 2 * * *',
        },
        isActive: true,
      },
    ],
    status: 'draft',
    createdAt: '2025-02-10T09:00:00Z',
    updatedAt: '2025-11-08T14:30:00Z',
    executionCount: 5,
  },
];

// ============================================================================
// MOCK EXECUTIONS
// ============================================================================

export const MOCK_EXECUTIONS: MockExecution[] = [
  {
    id: 'exec-001',
    workflowId: 'wf-001',
    status: 'completed',
    nodeStatuses: {
      'node-1': 'completed',
      'node-2': 'completed',
      'node-3': 'completed',
      'node-4': 'completed',
      'node-5': 'completed',
      'node-6': 'completed',
    },
    startedAt: '2025-11-08T14:00:00Z',
    completedAt: '2025-11-08T14:02:30Z',
    duration: 150,
    logs: [
      {
        timestamp: '2025-11-08T14:00:00Z',
        nodeId: 'node-1',
        message: 'Order received from webhook',
        level: 'info',
        data: { orderId: 'ORD-12345', amount: 299.99 },
      },
      {
        timestamp: '2025-11-08T14:00:10Z',
        nodeId: 'node-2',
        message: 'Order details fetched successfully',
        level: 'info',
        data: { items: 3, status: 'pending' },
      },
      {
        timestamp: '2025-11-08T14:00:20Z',
        nodeId: 'node-3',
        message: 'Inventory check passed',
        level: 'info',
        data: { available: true },
      },
      {
        timestamp: '2025-11-08T14:00:30Z',
        nodeId: 'node-4',
        message: 'Payment processed successfully',
        level: 'info',
        data: { transactionId: 'TXN-98765', status: 'approved' },
      },
      {
        timestamp: '2025-11-08T14:01:00Z',
        nodeId: 'node-5',
        message: 'Confirmation email sent',
        level: 'info',
        data: { recipient: 'customer@example.com' },
      },
    ],
  },
  {
    id: 'exec-002',
    workflowId: 'wf-002',
    status: 'completed',
    nodeStatuses: {
      'node-1': 'completed',
      'node-2': 'completed',
      'node-3': 'completed',
      'node-4': 'completed',
      'node-5': 'completed',
    },
    startedAt: '2025-11-08T13:45:00Z',
    completedAt: '2025-11-08T13:48:15Z',
    duration: 195,
    logs: [
      {
        timestamp: '2025-11-08T13:45:00Z',
        nodeId: 'node-1',
        message: 'User registration submitted',
        level: 'info',
        data: { email: 'newuser@example.com' },
      },
      {
        timestamp: '2025-11-08T13:45:05Z',
        nodeId: 'node-2',
        message: 'Email validation passed',
        level: 'info',
        data: { emailExists: false },
      },
      {
        timestamp: '2025-11-08T13:45:15Z',
        nodeId: 'node-3',
        message: 'Data transformation completed',
        level: 'info',
      },
      {
        timestamp: '2025-11-08T13:45:25Z',
        nodeId: 'node-4',
        message: 'Verification email sent',
        level: 'info',
        data: { status: 'sent' },
      },
    ],
  },
  {
    id: 'exec-003',
    workflowId: 'wf-001',
    status: 'failed',
    nodeStatuses: {
      'node-1': 'completed',
      'node-2': 'completed',
      'node-3': 'completed',
      'node-4': 'failed',
      'node-5': 'idle',
      'node-6': 'idle',
    },
    startedAt: '2025-11-08T14:10:00Z',
    completedAt: '2025-11-08T14:11:30Z',
    duration: 90,
    logs: [
      {
        timestamp: '2025-11-08T14:10:00Z',
        nodeId: 'node-1',
        message: 'Order received',
        level: 'info',
      },
      {
        timestamp: '2025-11-08T14:10:10Z',
        nodeId: 'node-2',
        message: 'Order fetched',
        level: 'info',
      },
      {
        timestamp: '2025-11-08T14:10:20Z',
        nodeId: 'node-3',
        message: 'Inventory check failed - item out of stock',
        level: 'warn',
        data: { available: false },
      },
      {
        timestamp: '2025-11-08T14:10:30Z',
        nodeId: 'node-4',
        message: 'Payment processing failed',
        level: 'error',
        data: { error: 'Insufficient funds', code: 'PAYMENT_FAILED' },
      },
    ],
    error: 'Payment processing failed - Insufficient funds',
  },
];

// ============================================================================
// MOCK HELPER FUNCTIONS
// ============================================================================

/**
 * Get all collections
 */
export function getMockCollections(): MockCollection[] {
  return MOCK_COLLECTIONS;
}

/**
 * Get collection by ID
 */
export function getMockCollectionById(id: string): MockCollection | undefined {
  return MOCK_COLLECTIONS.find((c) => c.id === id);
}

/**
 * Get entities for collection
 */
export function getMockEntitiesForCollection(
  collectionId: string,
  skip = 0,
  limit = 10
): MockEntity[] {
  const entities = MOCK_ENTITIES[collectionId] || [];
  return entities.slice(skip, skip + limit);
}

/**
 * Get all workflows
 */
export function getMockWorkflows(): MockWorkflow[] {
  return MOCK_WORKFLOWS;
}

/**
 * Get workflow by ID
 */
export function getMockWorkflowById(id: string): MockWorkflow | undefined {
  return MOCK_WORKFLOWS.find((w) => w.id === id);
}

/**
 * Get executions for workflow
 */
export function getMockExecutionsForWorkflow(
  workflowId: string,
  skip = 0,
  limit = 10
): MockExecution[] {
  const executions = MOCK_EXECUTIONS.filter((e) => e.workflowId === workflowId);
  return executions.slice(skip, skip + limit);
}

/**
 * Get execution by ID
 */
export function getMockExecutionById(id: string): MockExecution | undefined {
  return MOCK_EXECUTIONS.find((e) => e.id === id);
}

/**
 * Create new mock entity
 */
export function createMockEntity(
  collectionId: string,
  data: Record<string, any>
): MockEntity {
  return {
    id: `ent-${generateId()}`,
    collectionId,
    data,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
}

/**
 * Create new mock workflow
 */
export function createMockWorkflow(
  name: string,
  description: string
): MockWorkflow {
  const newWorkflow: MockWorkflow = {
    id: `wf-${generateId()}`,
    name,
    description,
    nodes: [
      {
        id: 'start',
        type: 'start',
        label: 'Start',
        data: {},
        position: { x: 100, y: 100 },
      },
    ],
    edges: [],
    triggers: [],
    status: 'draft',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    executionCount: 0,
  };

  MOCK_WORKFLOWS.push(newWorkflow);
  return newWorkflow;
}

// Create a new mock collection
export function createMockCollection(
  name: string,
  description: string,
  schema: Omit<MockSchema, 'id' | 'constraints'>,
  isActive = true
): MockCollection {
  const newCollection: MockCollection = {
    id: `col-${generateId()}`,
    name,
    description,
    entityCount: 0,
    schema: {
      ...schema,
      id: `schema-${generateId()}`,
      constraints: [],
    },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    isActive,
  };

  MOCK_COLLECTIONS.push(newCollection);
  return newCollection;
}

// Update an existing mock collection
export function updateMockCollection(
  id: string,
  updates: Partial<Omit<MockCollection, 'id' | 'createdAt' | 'updatedAt'>>
): MockCollection | undefined {
  const index = MOCK_COLLECTIONS.findIndex((c) => c.id === id);
  if (index === -1) return undefined;

  const updatedCollection = {
    ...MOCK_COLLECTIONS[index],
    ...updates,
    updatedAt: new Date().toISOString(),
  };

  MOCK_COLLECTIONS[index] = updatedCollection;
  return updatedCollection;
}

// Delete a mock collection
export function deleteMockCollection(id: string): boolean {
  const index = MOCK_COLLECTIONS.findIndex((c) => c.id === id);
  if (index === -1) return false;

  MOCK_COLLECTIONS.splice(index, 1);
  return true;
}
