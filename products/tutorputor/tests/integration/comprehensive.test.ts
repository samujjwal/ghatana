/**
 * Comprehensive Integration Testing Framework
 * 
 * Provides integration testing for all services including API contracts,
 * database operations, authentication, and end-to-end workflows.
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach, afterEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { PrismaClient } from '@prisma/client';
import { createServer } from '../server.js';
import { createLogger } from '../utils/logger.js';

const logger = createLogger('integration-tests');

// ============================================================================
// Test Infrastructure
// ============================================================================

export interface TestEnvironment {
  server: FastifyInstance;
  db: PrismaClient;
  cleanup: () => Promise<void>;
}

export class IntegrationTestSuite {
  private server?: FastifyInstance;
  private db?: PrismaClient;
  private testDatabases: string[] = [];

  async setup(): Promise<TestEnvironment> {
    // Setup test database
    const testDbUrl = process.env.TEST_DATABASE_URL || 'postgresql://postgres:password@localhost:5432/tutorputor_test';
    this.db = new PrismaClient({
      datasources: {
        db: {
          url: testDbUrl,
        },
      },
    });

    // Clear test data
    await this.clearTestData();

    // Setup test server
    this.server = await createServer({
      logger: false, // Disable logging for tests
      db: this.db,
    });

    return {
      server: this.server!,
      db: this.db!,
      cleanup: () => this.cleanup(),
    };
  }

  async cleanup(): Promise<void> {
    await this.clearTestData();
    
    if (this.server) {
      await this.server.close();
    }
    
    if (this.db) {
      await this.db.$disconnect();
    }
  }

  private async clearTestData(): Promise<void> {
    if (!this.db) return;

    // Clear in order of dependencies
    await this.db.assessmentAttempt.deleteMany();
    await this.db.assessment.deleteMany();
    await this.db.enrollment.deleteMany();
    await this.db.module.deleteMany();
    await this.db.user.deleteMany();
  }

  async createTestUser(data: Partial<{
    email: string;
    firstName: string;
    lastName: string;
    role: string;
    tenantId: string;
  }> = {}): Promise<any> {
    const userData = {
      email: data.email || `test-${Date.now()}@example.com`,
      firstName: data.firstName || 'Test',
      lastName: data.lastName || 'User',
      role: data.role || 'student',
      tenantId: data.tenantId || 'test-tenant',
      isActive: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    return this.db!.user.create({ data: userData });
  }

  async createTestModule(data: Partial<{
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    instructorId: string;
    tenantId: string;
  }> = {}): Promise<any> {
    const moduleData = {
      title: data.title || `Test Module ${Date.now()}`,
      description: data.description || 'Test module description',
      domain: data.domain || 'MATHEMATICS',
      difficulty: data.difficulty || 'BEGINNER',
      estimatedTimeMinutes: 60,
      status: 'PUBLISHED',
      tenantId: data.tenantId || 'test-tenant',
      instructorId: data.instructorId,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    return this.db!.module.create({ data: moduleData });
  }

  async createTestAssessment(data: Partial<{
    title: string;
    moduleId: string;
    type: string;
    tenantId: string;
  }> = {}): Promise<any> {
    const assessmentData = {
      title: data.title || `Test Assessment ${Date.now()}`,
      description: 'Test assessment description',
      moduleId: data.moduleId,
      type: data.type || 'FORMATIVE',
      timeLimit: 30,
      maxAttempts: 3,
      passingScore: 70,
      tenantId: data.tenantId || 'test-tenant',
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    return this.db!.assessment.create({ data: assessmentData });
  }
}

// ============================================================================
// Authentication Integration Tests
// ============================================================================

describe('Authentication Integration Tests', () => {
  let testSuite: IntegrationTestSuite;
  let env: TestEnvironment;

  beforeAll(async () => {
    testSuite = new IntegrationTestSuite();
    env = await testSuite.setup();
  });

  afterAll(async () => {
    await env.cleanup();
  });

  describe('User Registration', () => {
    it('should register a new user successfully', async () => {
      const userData = {
        email: `test-${Date.now()}@example.com`,
        firstName: 'John',
        lastName: 'Doe',
        password: 'SecurePassword123!',
        role: 'student',
      };

      const response = await env.server.inject({
        method: 'POST',
        url: '/api/auth/register',
        payload: userData,
      });

      expect(response.statusCode).toBe(201);
      const result = response.json();
      expect(result.user.email).toBe(userData.email);
      expect(result.user.firstName).toBe(userData.firstName);
      expect(result.user.lastName).toBe(userData.lastName);
      expect(result.token).toBeDefined();
    });

    it('should reject duplicate email registration', async () => {
      const userData = {
        email: `test-${Date.now()}@example.com`,
        firstName: 'Jane',
        lastName: 'Doe',
        password: 'SecurePassword123!',
        role: 'student',
      };

      // First registration
      await env.server.inject({
        method: 'POST',
        url: '/api/auth/register',
        payload: userData,
      });

      // Second registration with same email
      const response = await env.server.inject({
        method: 'POST',
        url: '/api/auth/register',
        payload: userData,
      });

      expect(response.statusCode).toBe(400);
      expect(response.json().message).toContain('already exists');
    });

    it('should validate password requirements', async () => {
      const userData = {
        email: `test-${Date.now()}@example.com`,
        firstName: 'Bob',
        lastName: 'Doe',
        password: 'weak', // Too weak
        role: 'student',
      };

      const response = await env.server.inject({
        method: 'POST',
        url: '/api/auth/register',
        payload: userData,
      });

      expect(response.statusCode).toBe(400);
      expect(response.json().message).toContain('password');
    });
  });

  describe('User Login', () => {
    let testUser: any;

    beforeEach(async () => {
      testUser = await testSuite.createTestUser({
        email: `login-test-${Date.now()}@example.com`,
        password: 'SecurePassword123!',
      });
    });

    it('should login with valid credentials', async () => {
      const response = await env.server.inject({
        method: 'POST',
        url: '/api/auth/login',
        payload: {
          email: testUser.email,
          password: 'SecurePassword123!',
        },
      });

      expect(response.statusCode).toBe(200);
      const result = response.json();
      expect(result.user.email).toBe(testUser.email);
      expect(result.token).toBeDefined();
      expect(result.refreshToken).toBeDefined();
    });

    it('should reject invalid credentials', async () => {
      const response = await env.server.inject({
        method: 'POST',
        url: '/api/auth/login',
        payload: {
          email: testUser.email,
          password: 'wrongpassword',
        },
      });

      expect(response.statusCode).toBe(401);
      expect(response.json().message).toContain('credentials');
    });

    it('should reject non-existent user', async () => {
      const response = await env.server.inject({
        method: 'POST',
        url: '/api/auth/login',
        payload: {
          email: 'nonexistent@example.com',
          password: 'password',
        },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('Token Validation', () => {
    let authToken: string;
    let testUser: any;

    beforeEach(async () => {
      testUser = await testSuite.createTestUser({
        email: `token-test-${Date.now()}@example.com`,
        password: 'SecurePassword123!',
      });

      const loginResponse = await env.server.inject({
        method: 'POST',
        url: '/api/auth/login',
        payload: {
          email: testUser.email,
          password: 'SecurePassword123!',
        },
      });

      authToken = loginResponse.json().token;
    });

    it('should validate valid token', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: '/api/auth/me',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().user.email).toBe(testUser.email);
    });

    it('should reject invalid token', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: '/api/auth/me',
        headers: {
          authorization: 'Bearer invalid-token',
        },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should reject missing token', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: '/api/auth/me',
      });

      expect(response.statusCode).toBe(401);
    });
  });
});

// ============================================================================
// Module Management Integration Tests
// ============================================================================

describe('Module Management Integration Tests', () => {
  let testSuite: IntegrationTestSuite;
  let env: TestEnvironment;
  let instructorToken: string;
  let studentToken: string;
  let instructorUser: any;
  let studentUser: any;

  beforeAll(async () => {
    testSuite = new IntegrationTestSuite();
    env = await testSuite.setup();

    // Create test users
    instructorUser = await testSuite.createTestUser({
      email: `instructor-${Date.now()}@example.com`,
      role: 'instructor',
    });

    studentUser = await testSuite.createTestUser({
      email: `student-${Date.now()}@example.com`,
      role: 'student',
    });

    // Get auth tokens
    const instructorLogin = await env.server.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: {
        email: instructorUser.email,
        password: 'SecurePassword123!',
      },
    });

    const studentLogin = await env.server.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: {
        email: studentUser.email,
        password: 'SecurePassword123!',
      },
    });

    instructorToken = instructorLogin.json().token;
    studentToken = studentLogin.json().token;
  });

  afterAll(async () => {
    await env.cleanup();
  });

  describe('Module Creation', () => {
    it('should allow instructors to create modules', async () => {
      const moduleData = {
        title: 'Test Module',
        description: 'A test module for integration testing',
        domain: 'MATHEMATICS',
        difficulty: 'BEGINNER',
        estimatedTimeMinutes: 60,
        learningObjectives: ['Learn basic math'],
        tags: ['math', 'basic'],
      };

      const response = await env.server.inject({
        method: 'POST',
        url: '/api/modules',
        headers: {
          authorization: `Bearer ${instructorToken}`,
        },
        payload: moduleData,
      });

      expect(response.statusCode).toBe(201);
      const result = response.json();
      expect(result.title).toBe(moduleData.title);
      expect(result.instructorId).toBe(instructorUser.id);
    });

    it('should reject module creation by students', async () => {
      const moduleData = {
        title: 'Unauthorized Module',
        description: 'Should not be created',
        domain: 'SCIENCE',
        difficulty: 'BEGINNER',
      };

      const response = await env.server.inject({
        method: 'POST',
        url: '/api/modules',
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
        payload: moduleData,
      });

      expect(response.statusCode).toBe(403);
    });

    it('should validate module data', async () => {
      const invalidModuleData = {
        title: '', // Empty title
        description: 'Invalid module',
        domain: 'INVALID_DOMAIN',
        difficulty: 'BEGINNER',
      };

      const response = await env.server.inject({
        method: 'POST',
        url: '/api/modules',
        headers: {
          authorization: `Bearer ${instructorToken}`,
        },
        payload: invalidModuleData,
      });

      expect(response.statusCode).toBe(400);
    });
  });

  describe('Module Retrieval', () => {
    let testModule: any;

    beforeEach(async () => {
      testModule = await testSuite.createTestModule({
        instructorId: instructorUser.id,
      });
    });

    it('should allow authenticated users to view modules', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: `/api/modules/${testModule.id}`,
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().id).toBe(testModule.id);
    });

    it('should reject unauthenticated access', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: `/api/modules/${testModule.id}`,
      });

      expect(response.statusCode).toBe(401);
    });

    it('should return 404 for non-existent modules', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: '/api/modules/non-existent-id',
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
      });

      expect(response.statusCode).toBe(404);
    });
  });

  describe('Module Search', () => {
    beforeEach(async () => {
      // Create test modules for search
      await testSuite.createTestModule({
        title: 'Math Basics',
        domain: 'MATHEMATICS',
        difficulty: 'BEGINNER',
        instructorId: instructorUser.id,
      });

      await testSuite.createTestModule({
        title: 'Advanced Physics',
        domain: 'SCIENCE',
        difficulty: 'ADVANCED',
        instructorId: instructorUser.id,
      });
    });

    it('should search modules by query', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: '/api/modules/search?query=Math',
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const results = response.json();
      expect(results.modules).toHaveLength(1);
      expect(results.modules[0].title).toContain('Math');
    });

    it('should filter modules by domain', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: '/api/modules/search?domain=SCIENCE',
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const results = response.json();
      expect(results.modules).toHaveLength(1);
      expect(results.modules[0].domain).toBe('SCIENCE');
    });

    it('should paginate results', async () => {
      const response = await env.server.inject({
        method: 'GET',
        url: '/api/modules/search?page=1&limit=1',
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const results = response.json();
      expect(results.modules).toHaveLength(1);
      expect(results.pagination.page).toBe(1);
      expect(results.pagination.limit).toBe(1);
    });
  });
});

// ============================================================================
// Assessment Integration Tests
// ============================================================================

describe('Assessment Integration Tests', () => {
  let testSuite: IntegrationTestSuite;
  let env: TestEnvironment;
  let instructorToken: string;
  let studentToken: string;
  let instructorUser: any;
  let studentUser: any;
  let testModule: any;

  beforeAll(async () => {
    testSuite = new IntegrationTestSuite();
    env = await testSuite.setup();

    // Create test users
    instructorUser = await testSuite.createTestUser({
      email: `instructor-${Date.now()}@example.com`,
      role: 'instructor',
    });

    studentUser = await testSuite.createTestUser({
      email: `student-${Date.now()}@example.com`,
      role: 'student',
    });

    // Get auth tokens
    const instructorLogin = await env.server.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: {
        email: instructorUser.email,
        password: 'SecurePassword123!',
      },
    });

    const studentLogin = await env.server.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: {
        email: studentUser.email,
        password: 'SecurePassword123!',
      },
    });

    instructorToken = instructorLogin.json().token;
    studentToken = studentLogin.json().token;

    // Create test module
    testModule = await testSuite.createTestModule({
      instructorId: instructorUser.id,
    });
  });

  afterAll(async () => {
    await env.cleanup();
  });

  describe('Assessment Creation', () => {
    it('should allow instructors to create assessments', async () => {
      const assessmentData = {
        title: 'Math Quiz',
        description: 'Basic math assessment',
        moduleId: testModule.id,
        type: 'FORMATIVE',
        timeLimit: 30,
        maxAttempts: 3,
        passingScore: 70,
        questions: [
          {
            type: 'MULTIPLE_CHOICE',
            text: 'What is 2 + 2?',
            points: 10,
            options: [
              { text: '3', isCorrect: false },
              { text: '4', isCorrect: true },
              { text: '5', isCorrect: false },
            ],
          },
        ],
      };

      const response = await env.server.inject({
        method: 'POST',
        url: '/api/assessments',
        headers: {
          authorization: `Bearer ${instructorToken}`,
        },
        payload: assessmentData,
      });

      expect(response.statusCode).toBe(201);
      const result = response.json();
      expect(result.title).toBe(assessmentData.title);
      expect(result.moduleId).toBe(testModule.id);
    });

    it('should reject assessment creation by students', async () => {
      const assessmentData = {
        title: 'Unauthorized Assessment',
        moduleId: testModule.id,
        type: 'FORMATIVE',
        questions: [],
      };

      const response = await env.server.inject({
        method: 'POST',
        url: '/api/assessments',
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
        payload: assessmentData,
      });

      expect(response.statusCode).toBe(403);
    });
  });

  describe('Assessment Submission', () => {
    let testAssessment: any;

    beforeEach(async () => {
      testAssessment = await testSuite.createTestAssessment({
        moduleId: testModule.id,
      });
    });

    it('should allow students to submit assessments', async () => {
      const submissionData = {
        answers: {
          'q1': 'A',
        },
        timeSpent: 1200, // 20 minutes
      };

      const response = await env.server.inject({
        method: 'POST',
        url: `/api/assessments/${testAssessment.id}/submit`,
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
        payload: submissionData,
      });

      expect(response.statusCode).toBe(200);
      const result = response.json();
      expect(result.attemptId).toBeDefined();
      expect(result.score).toBeDefined();
    });

    it('should track assessment attempts', async () => {
      // Submit first attempt
      await env.server.inject({
        method: 'POST',
        url: `/api/assessments/${testAssessment.id}/submit`,
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
        payload: { answers: { 'q1': 'A' } },
      });

      // Submit second attempt
      const response = await env.server.inject({
        method: 'POST',
        url: `/api/assessments/${testAssessment.id}/submit`,
        headers: {
          authorization: `Bearer ${studentToken}`,
        },
        payload: { answers: { 'q1': 'B' } },
      });

      expect(response.statusCode).toBe(200);
      const result = response.json();
      expect(result.attemptNumber).toBe(2);
    });
  });
});

// ============================================================================
// Database Integration Tests
// ============================================================================

describe('Database Integration Tests', () => {
  let testSuite: IntegrationTestSuite;
  let env: TestEnvironment;

  beforeAll(async () => {
    testSuite = new IntegrationTestSuite();
    env = await testSuite.setup();
  });

  afterAll(async () => {
    await env.cleanup();
  });

  describe('Database Connections', () => {
    it('should maintain database connection', async () => {
      // Test basic database connectivity
      const result = await env.db.$queryRaw`SELECT 1 as test`;
      expect(result).toBeDefined();
    });

    it('should handle concurrent operations', async () => {
      const promises = Array.from({ length: 10 }, (_, i) =>
        testSuite.createTestUser({
          email: `concurrent-${i}-${Date.now()}@example.com`,
        })
      );

      const results = await Promise.all(promises);
      expect(results).toHaveLength(10);
      expect(results.every(user => user.id)).toBe(true);
    });
  });

  describe('Data Integrity', () => {
    it('should enforce foreign key constraints', async () => {
      // Try to create assessment with non-existent module
      await expect(
        env.db.assessment.create({
          data: {
            title: 'Orphan Assessment',
            moduleId: 'non-existent-module-id',
            type: 'FORMATIVE',
            tenantId: 'test-tenant',
          },
        })
      ).rejects.toThrow();
    });

    it('should maintain data consistency', async () => {
      const user = await testSuite.createTestUser();
      const module = await testSuite.createTestModule({
        instructorId: user.id,
      });

      // Verify relationships
      const retrievedModule = await env.db.module.findUnique({
        where: { id: module.id },
        include: { instructor: true },
      });

      expect(retrievedModule?.instructor.id).toBe(user.id);
    });
  });

  describe('Transaction Management', () => {
    it('should rollback on transaction failure', async () => {
      const initialUserCount = await env.db.user.count();

      try {
        await env.db.$transaction(async (tx) => {
          await tx.user.create({
            data: {
              email: `transaction-test-${Date.now()}@example.com`,
              firstName: 'Test',
              lastName: 'User',
              role: 'student',
              tenantId: 'test-tenant',
              isActive: true,
              createdAt: new Date(),
              updatedAt: new Date(),
            },
          });

          // Force an error
          throw new Error('Intentional error');
        });
      } catch (error) {
        // Expected error
      }

      const finalUserCount = await env.db.user.count();
      expect(finalUserCount).toBe(initialUserCount);
    });

    it('should commit successful transactions', async () => {
      const initialUserCount = await env.db.user.count();

      await env.db.$transaction(async (tx) => {
        await tx.user.create({
          data: {
            email: `transaction-success-${Date.now()}@example.com`,
            firstName: 'Success',
            lastName: 'User',
            role: 'student',
            tenantId: 'test-tenant',
            isActive: true,
            createdAt: new Date(),
            updatedAt: new Date(),
          },
        });
      });

      const finalUserCount = await env.db.user.count();
      expect(finalUserCount).toBe(initialUserCount + 1);
    });
  });
});

// ============================================================================
// Performance Integration Tests
// ============================================================================

describe('Performance Integration Tests', () => {
  let testSuite: IntegrationTestSuite;
  let env: TestEnvironment;

  beforeAll(async () => {
    testSuite = new IntegrationTestSuite();
    env = await testSuite.setup();
  });

  afterAll(async () => {
    await env.cleanup();
  });

  describe('Response Times', () => {
    it('should respond to health checks quickly', async () => {
      const start = Date.now();
      const response = await env.server.inject({
        method: 'GET',
        url: '/health',
      });
      const duration = Date.now() - start;

      expect(response.statusCode).toBe(200);
      expect(duration).toBeLessThan(1000); // Should respond within 1 second
    });

    it('should handle concurrent requests efficiently', async () => {
      const promises = Array.from({ length: 50 }, () =>
        env.server.inject({
          method: 'GET',
          url: '/health',
        })
      );

      const start = Date.now();
      const results = await Promise.all(promises);
      const duration = Date.now() - start;

      expect(results).toHaveLength(50);
      expect(results.every(r => r.statusCode === 200)).toBe(true);
      expect(duration).toBeLessThan(5000); // Should handle 50 requests within 5 seconds
    });
  });

  describe('Memory Usage', () => {
    it('should not leak memory during operations', async () => {
      const initialMemory = process.memoryUsage().heapUsed;

      // Perform multiple operations
      for (let i = 0; i < 100; i++) {
        await testSuite.createTestUser({
          email: `memory-test-${i}-${Date.now()}@example.com`,
        });
      }

      // Force garbage collection if available
      if (global.gc) {
        global.gc();
      }

      const finalMemory = process.memoryUsage().heapUsed;
      const memoryIncrease = finalMemory - initialMemory;

      // Memory increase should be reasonable (less than 50MB)
      expect(memoryIncrease).toBeLessThan(50 * 1024 * 1024);
    });
  });
});

// Export test utilities for use in other test files
export { IntegrationTestSuite };
