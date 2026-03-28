/**
 * Comprehensive Test Utilities
 * 
 * Provides test doubles, mocks, and helpers for testing
 * with Prisma, Redis, and external services.
 * 
 * @doc.type utility
 * @doc.purpose Test utilities with Prisma mocks and test doubles
 * @doc.layer platform
 */

import { vi } from 'vitest';

// =============================================================================
// Prisma Mock Utilities
// =============================================================================

/**
 * Creates a mock Prisma client with common operations
 */
export function createMockPrismaClient() {
  const mockPrisma = {
    $queryRaw: vi.fn(),
    $executeRaw: vi.fn(),
    $transaction: vi.fn(),
    $connect: vi.fn(),
    $disconnect: vi.fn(),
    
    // Common models
    user: createMockPrismaModel('user'),
    tenant: createMockPrismaModel('tenant'),
    module: createMockPrismaModel('module'),
    assessment: createMockPrismaModel('assessment'),
    learningEvent: createMockPrismaModel('learningEvent'),
    payment: createMockPrismaModel('payment'),
    subscription: createMockPrismaModel('subscription'),
  };

  return mockPrisma;
}

/**
 * Creates a mock Prisma model with CRUD operations
 */
export function createMockPrismaModel(modelName: string) {
  return {
    findUnique: vi.fn(),
    findFirst: vi.fn(),
    findMany: vi.fn(),
    create: vi.fn(),
    createMany: vi.fn(),
    update: vi.fn(),
    updateMany: vi.fn(),
    upsert: vi.fn(),
    delete: vi.fn(),
    deleteMany: vi.fn(),
    count: vi.fn(),
    aggregate: vi.fn(),
    groupBy: vi.fn(),
  };
}

/**
 * Creates a mock Prisma transaction
 */
export function createMockPrismaTransaction() {
  const mockClient = createMockPrismaClient();
  
  return {
    ...mockClient,
    $commit: vi.fn(),
    $rollback: vi.fn(),
  };
}

/**
 * Helper to setup Prisma mock responses
 */
export function setupPrismaMock(
  prisma: any,
  model: string,
  operation: string,
  response: any,
) {
  if (prisma[model] && prisma[model][operation]) {
    prisma[model][operation].mockResolvedValue(response);
  }
}

/**
 * Helper to verify Prisma mock calls
 */
export function verifyPrismaCall(
  prisma: any,
  model: string,
  operation: string,
  expectedArgs?: any,
) {
  const mock = prisma[model]?.[operation];
  expect(mock).toHaveBeenCalled();
  
  if (expectedArgs) {
    expect(mock).toHaveBeenCalledWith(
      expect.objectContaining(expectedArgs)
    );
  }
}

// =============================================================================
// Redis Mock Utilities
// =============================================================================

/**
 * Creates a mock Redis client
 */
export function createMockRedisClient() {
  const store = new Map<string, string>();
  
  return {
    get: vi.fn(async (key: string) => store.get(key) || null),
    set: vi.fn(async (key: string, value: string) => {
      store.set(key, value);
      return 'OK';
    }),
    setex: vi.fn(async (key: string, seconds: number, value: string) => {
      store.set(key, value);
      return 'OK';
    }),
    del: vi.fn(async (key: string) => {
      store.delete(key);
      return 1;
    }),
    exists: vi.fn(async (key: string) => store.has(key) ? 1 : 0),
    expire: vi.fn(async () => 1),
    ttl: vi.fn(async () => -1),
    keys: vi.fn(async (pattern: string) => Array.from(store.keys())),
    flushdb: vi.fn(async () => {
      store.clear();
      return 'OK';
    }),
    ping: vi.fn(async () => 'PONG'),
    quit: vi.fn(async () => 'OK'),
    publish: vi.fn(async () => 1),
    subscribe: vi.fn(async () => {}),
    unsubscribe: vi.fn(async () => {}),
    on: vi.fn(),
    off: vi.fn(),
  };
}

// =============================================================================
// HTTP Mock Utilities
// =============================================================================

/**
 * Creates a mock HTTP response
 */
export function createMockHttpResponse(
  status: number,
  body: any,
  headers: Record<string, string> = {},
) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: getStatusText(status),
    headers: new Map(Object.entries(headers)),
    json: vi.fn(async () => body),
    text: vi.fn(async () => JSON.stringify(body)),
    blob: vi.fn(async () => new Blob([JSON.stringify(body)])),
  };
}

/**
 * Creates a mock fetch function
 */
export function createMockFetch(responses: Map<string, any>) {
  return vi.fn(async (url: string, options?: any) => {
    const response = responses.get(url);
    if (!response) {
      return createMockHttpResponse(404, { error: 'Not found' });
    }
    return response;
  });
}

function getStatusText(status: number): string {
  const statusTexts: Record<number, string> = {
    200: 'OK',
    201: 'Created',
    204: 'No Content',
    400: 'Bad Request',
    401: 'Unauthorized',
    403: 'Forbidden',
    404: 'Not Found',
    500: 'Internal Server Error',
    503: 'Service Unavailable',
  };
  return statusTexts[status] || 'Unknown';
}

// =============================================================================
// Service Mock Utilities
// =============================================================================

/**
 * Creates a mock AI service
 */
export function createMockAIService() {
  return {
    handleTutorQuery: vi.fn(async (args: any) => ({
      answer: 'Mock AI response',
      confidence: 0.95,
      sources: [],
    })),
    generateConcept: vi.fn(async (args: any) => ({
      name: args.conceptName,
      description: 'Mock concept description',
      learningObjectives: [],
      prerequisites: [],
      competencies: [],
      keywords: [],
      level: 'INTERMEDIATE',
    })),
  };
}

/**
 * Creates a mock payment service
 */
export function createMockPaymentService() {
  return {
    createPaymentIntent: vi.fn(async (args: any) => ({
      id: 'pi_mock_123',
      clientSecret: 'mock_secret',
      status: 'requires_payment_method',
      amount: args.amount,
      currency: args.currency,
    })),
    confirmPayment: vi.fn(async (args: any) => ({
      id: args.paymentIntentId,
      status: 'succeeded',
    })),
    refundPayment: vi.fn(async (args: any) => ({
      id: 'ref_mock_123',
      status: 'succeeded',
      amount: args.amount,
    })),
  };
}

/**
 * Creates a mock email service
 */
export function createMockEmailService() {
  const sentEmails: any[] = [];
  
  return {
    sendEmail: vi.fn(async (args: any) => {
      sentEmails.push(args);
      return { messageId: 'mock_msg_123' };
    }),
    getSentEmails: () => sentEmails,
    clearSentEmails: () => sentEmails.splice(0, sentEmails.length),
  };
}

// =============================================================================
// Test Data Factories
// =============================================================================

/**
 * Creates test user data
 */
export function createTestUser(overrides: Partial<any> = {}) {
  return {
    id: 'user_test_123',
    tenantId: 'tenant_test_123',
    email: 'test@example.com',
    name: 'Test User',
    role: 'student',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

/**
 * Creates test tenant data
 */
export function createTestTenant(overrides: Partial<any> = {}) {
  return {
    id: 'tenant_test_123',
    name: 'Test Tenant',
    subdomain: 'test',
    adminEmail: 'admin@test.com',
    subscriptionTier: 'FREE',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

/**
 * Creates test module data
 */
export function createTestModule(overrides: Partial<any> = {}) {
  return {
    id: 'module_test_123',
    tenantId: 'tenant_test_123',
    title: 'Test Module',
    slug: 'test-module',
    domain: 'MATH',
    difficulty: 'INTERMEDIATE',
    status: 'published',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

/**
 * Creates test assessment data
 */
export function createTestAssessment(overrides: Partial<any> = {}) {
  return {
    id: 'assessment_test_123',
    tenantId: 'tenant_test_123',
    moduleId: 'module_test_123',
    title: 'Test Assessment',
    type: 'quiz',
    status: 'published',
    version: 1,
    passingScore: 70,
    attemptsAllowed: 3,
    timeLimitMinutes: 30,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

/**
 * Creates test payment data
 */
export function createTestPayment(overrides: Partial<any> = {}) {
  return {
    id: 'payment_test_123',
    tenantId: 'tenant_test_123',
    userId: 'user_test_123',
    amount: 1000,
    currency: 'usd',
    status: 'succeeded',
    provider: 'stripe',
    providerPaymentId: 'pi_test_123',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

// =============================================================================
// Test Helpers
// =============================================================================

/**
 * Waits for a condition to be true
 */
export async function waitFor(
  condition: () => boolean | Promise<boolean>,
  options: { timeout?: number; interval?: number } = {},
): Promise<void> {
  const { timeout = 5000, interval = 100 } = options;
  const startTime = Date.now();

  while (Date.now() - startTime < timeout) {
    if (await condition()) {
      return;
    }
    await new Promise(resolve => setTimeout(resolve, interval));
  }

  throw new Error('Timeout waiting for condition');
}

/**
 * Delays execution for testing async operations
 */
export function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Creates a spy that tracks all calls
 */
export function createSpy<T extends (...args: any[]) => any>(
  implementation?: T,
) {
  const calls: any[][] = [];
  const spy = vi.fn((...args: any[]) => {
    calls.push(args);
    return implementation ? implementation(...args) : undefined;
  });

  return {
    spy,
    calls,
    callCount: () => calls.length,
    lastCall: () => calls[calls.length - 1],
    reset: () => calls.splice(0, calls.length),
  };
}

/**
 * Creates a test context with common mocks
 */
export function createTestContext() {
  return {
    prisma: createMockPrismaClient(),
    redis: createMockRedisClient(),
    aiService: createMockAIService(),
    paymentService: createMockPaymentService(),
    emailService: createMockEmailService(),
  };
}

/**
 * Cleans up test context
 */
export async function cleanupTestContext(context: any) {
  if (context.prisma?.$disconnect) {
    await context.prisma.$disconnect();
  }
  if (context.redis?.quit) {
    await context.redis.quit();
  }
}

// =============================================================================
// Assertion Helpers
// =============================================================================

/**
 * Asserts that a value is defined
 */
export function assertDefined<T>(
  value: T | undefined | null,
  message?: string,
): asserts value is T {
  if (value === undefined || value === null) {
    throw new Error(message || 'Expected value to be defined');
  }
}

/**
 * Asserts that an error was thrown
 */
export async function assertThrows(
  fn: () => Promise<any>,
  expectedError?: string | RegExp,
): Promise<void> {
  try {
    await fn();
    throw new Error('Expected function to throw');
  } catch (error) {
    if (expectedError) {
      const message = error instanceof Error ? error.message : String(error);
      if (typeof expectedError === 'string') {
        expect(message).toContain(expectedError);
      } else {
        expect(message).toMatch(expectedError);
      }
    }
  }
}

/**
 * Asserts that a promise resolves
 */
export async function assertResolves<T>(
  promise: Promise<T>,
): Promise<T> {
  try {
    return await promise;
  } catch (error) {
    throw new Error(
      `Expected promise to resolve but it rejected: ${
        error instanceof Error ? error.message : String(error)
      }`
    );
  }
}
