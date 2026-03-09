/**
 * Integration Test Suite for YAPPC Platform
 * 
 * Comprehensive integration testing covering API endpoints,
 * database operations, external service integrations,
 * and system component interactions.
 * 
 * @doc.type test
 * @doc.purpose Integration testing for production readiness
 * @doc.layer test
 * @doc.pattern Integration Testing
 */

import { test, expect } from '@playwright/test';
import { PrismaClient } from '../apps/api/src/generated/prisma';

// ============================================================================
// Test Configuration
// ============================================================================

const TEST_CONFIG = {
  // Use API Gateway (single entry point) for all API calls
  apiBase: process.env.API_BASE_URL || 'http://localhost:7002',
  databaseUrl: process.env.DATABASE_URL || 'postgresql://ghatana:ghatana123@localhost:5432/yappc_dev',
  timeout: 10000,
};

// ============================================================================
// Database Integration Tests
// ============================================================================

test.describe('Database Integration', () => {
  let prisma: PrismaClient;

  test.beforeAll(async () => {
    prisma = new PrismaClient({
      datasources: {
        db: {
          url: TEST_CONFIG.databaseUrl,
        },
      },
    });
  });

  test.afterAll(async () => {
    await prisma.$disconnect();
  });

  test('should connect to database', async () => {
    try {
      await prisma.$connect();
      expect(true).toBe(true); // Connection successful
    } catch (error) {
      throw new Error(`Database connection failed: ${error}`);
    }
  });

  test('should create and retrieve users', async () => {
    // Create user
    const user = await prisma.user.create({
      data: {
        email: 'test@example.com',
        name: 'Test User',
        role: 'EDITOR',
        passwordHash: 'hashedpassword',
      },
    });

    expect(user.id).toBeDefined();
    expect(user.email).toBe('test@example.com');
    expect(user.role).toBe('EDITOR');

    // Retrieve user
    const retrievedUser = await prisma.user.findUnique({
      where: { id: user.id },
    });

    expect(retrievedUser).toBeTruthy();
    expect(retrievedUser?.email).toBe('test@example.com');
  });

  test('should create workspaces with proper relations', async () => {
    // Create user first
    const user = await prisma.user.create({
      data: {
        email: 'workspace@example.com',
        name: 'Workspace User',
        role: 'OWNER',
        passwordHash: 'hashedpassword',
      },
    });

    // Create workspace
    const workspace = await prisma.workspace.create({
      data: {
        name: 'Test Workspace',
        description: 'A test workspace',
        ownerId: user.id,
        isDefault: true,
      },
    });

    expect(workspace.id).toBeDefined();
    expect(workspace.ownerId).toBe(user.id);
    expect(workspace.isDefault).toBe(true);

    // Verify relation
    const workspaceWithOwner = await prisma.workspace.findUnique({
      where: { id: workspace.id },
      include: { owner: true },
    });

    expect(workspaceWithOwner?.owner?.email).toBe('workspace@example.com');
  });

  test('should handle transactions properly', async () => {
    const user = await prisma.user.create({
      data: {
        email: 'transaction@example.com',
        name: 'Transaction User',
        role: 'EDITOR',
        passwordHash: 'hashedpassword',
      },
    });

    // Test transaction
    const result = await prisma.$transaction(async (tx) => {
      const workspace = await tx.workspace.create({
        data: {
          name: 'Transaction Workspace',
          ownerId: user.id,
          isDefault: false,
        },
      });

      await tx.workspaceMember.create({
        data: {
          userId: user.id,
          workspaceId: workspace.id,
          role: 'OWNER',
        },
      });

      return workspace;
    });

    expect(result.id).toBeDefined();
    expect(result.name).toBe('Transaction Workspace');

    // Verify both records were created
    const workspaceCount = await prisma.workspace.count({
      where: { ownerId: user.id },
    });
    expect(workspaceCount).toBe(1);
  });
});

// ============================================================================
// API Integration Tests
// ============================================================================

test.describe('API Integration', () => {
  test('should handle authentication endpoints', async ({ request }) => {
    // Test registration
    const registerResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/register`, {
      data: {
        email: 'api@example.com',
        password: 'password123',
        name: 'API User',
      },
    });

    expect(registerResponse.status()).toBe(201);
    const registerData = await registerResponse.json();
    expect(registerData.user.email).toBe('api@example.com');
    expect(registerData.tokens.accessToken).toBeDefined();

    // Test login
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'api@example.com',
        password: 'password123',
      },
    });

    expect(loginResponse.status()).toBe(200);
    const loginData = await loginResponse.json();
    expect(loginData.user.email).toBe('api@example.com');
    expect(loginData.tokens.accessToken).toBeDefined();
  });

  test('should handle workspace API endpoints', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'api@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Create workspace
    const createResponse = await request.post(`${TEST_CONFIG.apiBase}/api/workspaces`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      data: {
        name: 'API Workspace',
        description: 'Created via API',
      },
    });

    expect(createResponse.status()).toBe(201);
    const workspaceData = await createResponse.json();
    expect(workspaceData.name).toBe('API Workspace');

    // Get workspaces
    const listResponse = await request.get(`${TEST_CONFIG.apiBase}/api/workspaces`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    expect(listResponse.status()).toBe(200);
    const listData = await listResponse.json();
    expect(listData.data).toContainEqual(expect.arrayContaining(
      expect.objectContaining({ name: 'API Workspace' })
    ));
  });

  test('should handle AI service endpoints', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'ai@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Test AI suggestions
    const suggestionsResponse = await request.post(`${TEST_CONFIG.apiBase}/api/ai/suggest-artifacts`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      data: {
        context: {
          projectId: 'test-project',
          currentPhase: 'INTENT',
          existingArtifacts: [],
        },
        targetKinds: ['IDEA_BRIEF', 'RESEARCH_PACK'],
      },
    });

    expect(suggestionsResponse.status()).toBe(200);
    const suggestionsData = await suggestionsResponse.json();
    expect(Array.isArray(suggestionsData)).toBe(true);

    // Test AI metrics
    const metricsResponse = await request.get(`${TEST_CONFIG.apiBase}/api/ai/metrics`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    expect(metricsResponse.status()).toBe(200);
    const metricsData = await metricsResponse.json();
    expect(metricsData.totalRequests).toBeDefined();
    expect(metricsData.successfulRequests).toBeDefined();
  });

  test('should handle security endpoints', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'security@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Test audit logging
    const auditResponse = await request.get(`${TEST_CONFIG.apiBase}/api/audit/events`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    expect(auditResponse.status()).toBe(200);
    const auditData = await auditResponse.json();
    expect(Array.isArray(auditData)).toBe(true);

    // Test security alerts
    const alertsResponse = await request.get(`${TEST_CONFIG.apiBase}/api/security/alerts`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    expect(alertsResponse.status()).toBe(200);
    const alertsData = await alertsResponse.json();
    expect(Array.isArray(alertsData)).toBe(true);
  });
});

// ============================================================================
// Error Handling Tests
// ============================================================================

test.describe('Error Handling', () => {
  test('should handle invalid authentication', async ({ request }) => {
    // Test invalid credentials
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'invalid@example.com',
        password: 'wrongpassword',
      },
    });

    expect(response.status()).toBe(401);
    const errorData = await response.json();
    expect(errorData.error).toBe('Unauthorized');
  });

  test('should handle missing authorization', async ({ request }) => {
    // Test unauthorized access
    const response = await request.get(`${TEST_CONFIG.apiBase}/api/workspaces`);

    expect(response.status()).toBe(401);
    const errorData = await response.json();
    expect(errorData.error).toBe('Unauthorized');
  });

  test('should handle rate limiting', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'ratelimit@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Make rapid requests to trigger rate limiting
    const promises = Array(10).fill(null).map(() =>
      request.get(`${TEST_CONFIG.apiBase}/api/workspaces`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      })
    );

    const responses = await Promise.all(promises);
    const rateLimitedResponses = responses.filter(r => r.status() === 429);

    expect(rateLimitedResponses.length).toBeGreaterThan(0);
  });

  test('should handle malformed requests', async ({ request }) => {
    // Test malformed JSON
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/workspaces`, {
      headers: {
        'Content-Type': 'application/json',
      },
      data: 'invalid json',
    });

    expect(response.status()).toBe(400);
    const errorData = await response.json();
    expect(errorData.error).toBeDefined();
  });

  test('should handle database errors gracefully', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'dberror@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Test with invalid database ID
    const response = await request.get(`${TEST_CONFIG.apiBase}/api/workspaces/invalid-id`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    expect(response.status()).toBe(404);
    const errorData = await response.json();
    expect(errorData.error).toBe('Workspace not found');
  });
});

// ============================================================================
// Performance Tests
// ============================================================================

test.describe('Performance', () => {
  test('should handle concurrent requests', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'concurrent@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Make concurrent requests
    const promises = Array(20).fill(null).map((_, index) =>
      request.get(`${TEST_CONFIG.apiBase}/api/workspaces`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      })
    );

    const startTime = Date.now();
    const responses = await Promise.all(promises);
    const endTime = Date.now();

    // All requests should succeed
    expect(responses.every(r => r.status() === 200)).toBe(true);
    
    // Should complete within reasonable time
    expect(endTime - startTime).toBeLessThan(5000);
  });

  test('should handle large payloads', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'payload@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Create large workspace data
    const largeData = {
      name: 'Large Workspace',
      description: 'A'.repeat(10000), // 10KB description
      aiTags: Array(100).fill(null).map((_, i) => `tag-${i}`),
    };

    const startTime = Date.now();
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/workspaces`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      data: largeData,
    });
    const endTime = Date.now();

    expect(response.status()).toBe(201);
    expect(endTime - startTime).toBeLessThan(10000); // Should handle within 10s
  });

  test('should maintain response time under load', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'performance@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Test multiple endpoints
    const endpoints = [
      `${TEST_CONFIG.apiBase}/api/workspaces`,
      `${TEST_CONFIG.apiBase}/api/projects`,
      `${TEST_CONFIG.apiBase}/api/ai/metrics`,
      `${TEST_CONFIG.apiBase}/api/audit/events`,
    ];

    const promises = endpoints.map(endpoint =>
      request.get(endpoint, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      })
    );

    const startTime = Date.now();
    const responses = await Promise.all(promises);
    const endTime = Date.now();

    // All requests should complete quickly
    expect(responses.every(r => r.status() === 200)).toBe(true);
    expect(endTime - startTime).toBeLessThan(2000); // Should complete within 2s
  });
});

// ============================================================================
// Data Consistency Tests
// ============================================================================

test.describe('Data Consistency', () => {
  let prisma: PrismaClient;

  test.beforeAll(async () => {
    prisma = new PrismaClient({
      datasources: {
        db: {
          url: TEST_CONFIG.databaseUrl,
        },
      },
    });
  });

  test.afterAll(async () => {
    await prisma.$disconnect();
  });

  test('should maintain referential integrity', async () => {
    // Create user
    const user = await prisma.user.create({
      data: {
        email: 'consistency@example.com',
        name: 'Consistency User',
        role: 'OWNER',
        passwordHash: 'hashedpassword',
      },
    });

    // Create workspace
    const workspace = await prisma.workspace.create({
      data: {
        name: 'Consistency Workspace',
        ownerId: user.id,
        isDefault: true,
      },
    });

    // Create workspace member
    const member = await prisma.workspaceMember.create({
      data: {
        userId: user.id,
        workspaceId: workspace.id,
        role: 'OWNER',
      },
    });

    // Verify all relations exist
    const workspaceWithRelations = await prisma.workspace.findUnique({
      where: { id: workspace.id },
      include: {
        owner: true,
        members: {
          include: {
            user: true,
          },
        },
      },
    });

    expect(workspaceWithRelations?.owner?.id).toBe(user.id);
    expect(workspaceWithRelations?.members).toHaveLength(1);
    expect(workspaceWithRelations?.members[0]?.user?.id).toBe(user.id);
  });

  test('should handle cascading deletes properly', async () => {
    // Create user with workspace
    const user = await prisma.user.create({
      data: {
        email: 'cascade@example.com',
        name: 'Cascade User',
        role: 'OWNER',
        passwordHash: 'hashedpassword',
      },
    });

    const workspace = await prisma.workspace.create({
      data: {
        name: 'Cascade Workspace',
        ownerId: user.id,
        isDefault: true,
      },
    });

    // Delete user (should cascade to workspace)
    await prisma.user.delete({
      where: { id: user.id },
    });

    // Verify workspace is also deleted
    const deletedWorkspace = await prisma.workspace.findUnique({
      where: { id: workspace.id },
    });

    expect(deletedWorkspace).toBeNull();
  });

  test('should enforce unique constraints', async () => {
    // Create user
    await prisma.user.create({
      data: {
        email: 'unique@example.com',
        name: 'Unique User',
        role: 'EDITOR',
        passwordHash: 'hashedpassword',
      },
    });

    // Try to create user with same email
    await expect(
      prisma.user.create({
        data: {
          email: 'unique@example.com',
          name: 'Duplicate User',
          role: 'EDITOR',
          passwordHash: 'hashedpassword',
        },
      })
    ).rejects.toThrow('Unique constraint failed');
  });
});

// ============================================================================
// Security Integration Tests
// ============================================================================

test.describe('Security Integration', () => {
  test('should encrypt sensitive data', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'security@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Send sensitive data
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/security/encrypt`, {
      headers: {
        'Authorization': `Bearer ${accessToken}`,
      },
      data: {
        data: 'sensitive information',
      },
    });

    expect(response.status()).toBe(200);
    const encryptedData = await response.json();
    expect(encryptedData.encrypted).toBeDefined();
    expect(encryptedData.iv).toBeDefined();
    expect(encryptedData.salt).toBeDefined();
    expect(encryptedData.encrypted).not.toBe('sensitive information');
  });

  test('should detect and redact PII', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'pii@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Send text with PII
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/security/redact-pii`, {
      headers: {
        'Authorization: `Bearer ${accessToken}`,
      },
      data: {
        text: 'Contact john.doe@example.com or call 555-123-4567',
      },
    });

    expect(response.status()).toBe(200);
    const piiData = await response.json();
    expect(piiData.hasPII).toBe(true);
    expect(piiData.redacted).toContain('[EMAIL_REDACTED]');
    expect(piiData.redacted).toContain('[PHONE_REDACTED]');
  });

  test('should log security events', async ({ request }) => {
    // Create authenticated request
    const loginResponse = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: {
        email: 'audit@example.com',
        password: 'password123',
      },
    });
    const { accessToken } = await loginResponse.json();

    // Log security event
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/security/log`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      data: {
        action: 'login_attempt',
        resource: 'auth',
        outcome: 'success',
        details: {
          ipAddress: '127.0.0.1',
          userAgent: 'Test Agent',
        },
      },
    });

    expect(response.status()).toBe(200);
    const logData = await response.json();
    expect(logData.id).toBeDefined();
    expect(logData.action).toBe('login_attempt');
    expect(logData.outcome).toBe('success');
  });
});

// ============================================================================
// Test Utilities
// ============================================================================

export const integrationUtils = {
  async createAuthenticatedUser(request: unknown, email: string, password: string) {
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/auth/login`, {
      data: { email, password },
    });
    const { accessToken } = await response.json();
    return { accessToken, user: (await response.json()).user };
  },

  async createWorkspace(request: unknown, accessToken: string, workspaceData: unknown) {
    const response = await request.post(`${TEST_CONFIG.apiBase}/api/workspaces`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      data: workspaceData,
    });
    return await response.json();
  },

  async cleanupDatabase(prisma: PrismaClient) {
    // Clean up test data
    await prisma.workspaceMember.deleteMany({});
    await prisma.workspace.deleteMany({});
    await prisma.user.deleteMany({
      where: {
        email: {
          contains: '@example.com',
        },
      },
    });
  },
};
