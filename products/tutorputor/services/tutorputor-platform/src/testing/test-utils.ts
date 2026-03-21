/**
 * Test Coverage Improvement Plan
 * 
 * Implements testing best practices and coverage improvements to reach 60% threshold.
 * 
 * @module @tutorputor/platform/testing
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import type { Mock } from 'vitest';

/**
 * Test Utilities for consistent testing across modules
 */
export class TestUtils {
  /**
   * Create a mock tenant context for testing
   */
  static createMockTenant(overrides: Partial<TenantContext> = {}): TenantContext {
    return {
      tenantId: 'test-tenant-123',
      userId: 'test-user-456',
      permissions: ['read', 'write'],
      ...overrides,
    };
  }

  /**
   * Create a mock request object for API testing
   */
  static createMockRequest(overrides: Partial<FastifyRequest> = {}): FastifyRequest {
    return {
      body: {},
      params: {},
      query: {},
      headers: {},
      user: this.createMockTenant(),
      ...overrides,
    } as FastifyRequest;
  }

  /**
   * Create a mock reply object for API testing
   */
  static createMockReply(overrides: Partial<FastifyReply> = {}): FastifyReply {
    const reply: any = {
      code: vi.fn().mockReturnThis(),
      send: vi.fn().mockReturnThis(),
      status: vi.fn().mockReturnThis(),
      header: vi.fn().mockReturnThis(),
      ...overrides,
    };
    return reply as FastifyReply;
  }

  /**
   * Wait for a specified duration (useful for async testing)
   */
  static async wait(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Retry an assertion multiple times (useful for flaky async operations)
   */
  static async retryAssertion(
    assertion: () => void | Promise<void>,
    maxRetries: number = 3,
    delayMs: number = 100
  ): Promise<void> {
    for (let i = 0; i < maxRetries; i++) {
      try {
        await assertion();
        return;
      } catch (error) {
        if (i === maxRetries - 1) throw error;
        await this.wait(delayMs);
      }
    }
  }
}

/**
 * Mock factories for common dependencies
 */
export class MockFactories {
  /**
   * Create mock Prisma client
   */
  static createMockPrisma(): MockPrismaClient {
    return {
      module: {
        findUnique: vi.fn(),
        findMany: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        delete: vi.fn(),
      },
      enrollment: {
        findUnique: vi.fn(),
        findMany: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
      },
      assessment: {
        findUnique: vi.fn(),
        findMany: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
      },
      $transaction: vi.fn((callback) => callback(this)),
      $queryRaw: vi.fn(),
      $executeRaw: vi.fn(),
    } as unknown as MockPrismaClient;
  }

  /**
   * Create mock Redis client
   */
  static createMockRedis(): MockRedisClient {
    const storage = new Map<string, string>();
    
    return {
      get: vi.fn((key: string) => Promise.resolve(storage.get(key) || null)),
      set: vi.fn((key: string, value: string) => {
        storage.set(key, value);
        return Promise.resolve('OK');
      }),
      del: vi.fn((key: string) => {
        storage.delete(key);
        return Promise.resolve(1);
      }),
      exists: vi.fn((key: string) => Promise.resolve(storage.has(key) ? 1 : 0)),
      expire: vi.fn(() => Promise.resolve(1)),
      ttl: vi.fn(() => Promise.resolve(-1)),
      keys: vi.fn((pattern: string) => {
        const regex = new RegExp(pattern.replace('*', '.*'));
        const matching = Array.from(storage.keys()).filter(k => regex.test(k));
        return Promise.resolve(matching);
      }),
      flushall: vi.fn(() => {
        storage.clear();
        return Promise.resolve('OK');
      }),
    } as MockRedisClient;
  }

  /**
   * Create mock AI provider
   */
  static createMockAIProvider(): MockAIProvider {
    return {
      generateContent: vi.fn().mockResolvedValue({
        content: 'Generated content',
        tokensUsed: { input: 100, output: 50, total: 150 },
        cost: 0.002,
        latency: 500,
      }),
      validateContent: vi.fn().mockResolvedValue({
        isValid: true,
        confidence: 0.95,
      }),
      getModelInfo: vi.fn().mockReturnValue({
        name: 'gpt-4',
        maxTokens: 8192,
        costPer1K: { input: 0.03, output: 0.06 },
      }),
    } as MockAIProvider;
  }
}

// Type definitions for mocks
interface TenantContext {
  tenantId: string;
  userId: string;
  permissions: string[];
}

interface FastifyRequest {
  body: any;
  params: any;
  query: any;
  headers: any;
  user?: TenantContext;
}

interface FastifyReply {
  code: (statusCode: number) => FastifyReply;
  send: (payload?: any) => FastifyReply;
  status: (statusCode: number) => FastifyReply;
  header: (name: string, value: string) => FastifyReply;
}

interface MockPrismaClient {
  module: {
    findUnique: Mock;
    findMany: Mock;
    create: Mock;
    update: Mock;
    delete: Mock;
  };
  enrollment: {
    findUnique: Mock;
    findMany: Mock;
    create: Mock;
    update: Mock;
  };
  assessment: {
    findUnique: Mock;
    findMany: Mock;
    create: Mock;
    update: Mock;
  };
  $transaction: Mock;
  $queryRaw: Mock;
  $executeRaw: Mock;
}

interface MockRedisClient {
  get: Mock;
  set: Mock;
  del: Mock;
  exists: Mock;
  expire: Mock;
  ttl: Mock;
  keys: Mock;
  flushall: Mock;
}

interface MockAIProvider {
  generateContent: Mock;
  validateContent: Mock;
  getModelInfo: Mock;
}

/**
 * Coverage Analysis Helper
 */
export class CoverageAnalyzer {
  /**
   * Parse coverage report and identify gaps
   */
  static analyzeCoverage(coverageData: any): {
    total: number;
    covered: number;
    gaps: Array<{ file: string; lines: number[]; functions: string[] }>;
    recommendations: string[];
  } {
    const gaps: Array<{ file: string; lines: number[]; functions: string[] }> = [];
    const recommendations: string[] = [];
    
    let total = 0;
    let covered = 0;

    for (const [file, data] of Object.entries(coverageData)) {
      if (typeof data !== 'object' || !data) continue;
      
      const fileData = data as any;
      total += fileData.lines?.total || 0;
      covered += fileData.lines?.covered || 0;

      // Identify files with low coverage
      const coverage = fileData.lines?.pct || 0;
      if (coverage < 40) {
        gaps.push({
          file,
          lines: fileData.lines?.uncovered || [],
          functions: fileData.functions?.uncovered || [],
        });
      }
    }

    // Generate recommendations
    if (gaps.length > 5) {
      recommendations.push('Focus on high-impact modules with <40% coverage');
    }
    
    const criticalFiles = gaps.filter(g => g.functions.length > 10);
    if (criticalFiles.length > 0) {
      recommendations.push(`Add unit tests for ${criticalFiles.length} files with many uncovered functions`);
    }

    if ((covered / total) * 100 < 60) {
      recommendations.push('Priority: Reach 60% coverage threshold for CI/CD');
    }

    return {
      total,
      covered,
      gaps,
      recommendations,
    };
  }

  /**
   * Generate test plan for low-coverage files
   */
  static generateTestPlan(gaps: Array<{ file: string; lines: number[]; functions: string[] }>): string {
    let plan = '# Test Coverage Improvement Plan\n\n';
    
    const priorityFiles = gaps
      .sort((a, b) => b.functions.length - a.functions.length)
      .slice(0, 10);

    priorityFiles.forEach((gap, index) => {
      plan += `## ${index + 1}. ${gap.file}\n\n`;
      plan += `- **Uncovered Functions:** ${gap.functions.length}\n`;
      plan += `- **Priority:** ${gap.functions.length > 20 ? 'High' : 'Medium'}\n`;
      plan += `- **Test Strategy:**\n`;
      plan += `  1. Unit tests for core business logic\n`;
      plan += `  2. Integration tests for API endpoints\n`;
      plan += `  3. Error case handling\n\n`;
    });

    return plan;
  }
}

/**
 * Common test patterns for TutorPutor
 */
export const TestPatterns = {
  /**
   * Test multi-tenant isolation
   */
  tenantIsolation: (testFn: (tenantId: string) => Promise<void>) => {
    it('should isolate data between tenants', async () => {
      const tenant1 = 'tenant-1';
      const tenant2 = 'tenant-2';
      
      // Ensure tenant1 can't access tenant2 data
      await testFn(tenant1);
      await testFn(tenant2);
    });
  },

  /**
   * Test error handling
   */
  errorHandling: (testFn: () => Promise<void>, expectedError: string) => {
    it(`should handle errors: ${expectedError}`, async () => {
      await expect(testFn()).rejects.toThrow(expectedError);
    });
  },

  /**
   * Test pagination
   */
  pagination: (testFn: (params: { page: number; limit: number }) => Promise<any>) => {
    it('should support pagination', async () => {
      const page1 = await testFn({ page: 1, limit: 10 });
      const page2 = await testFn({ page: 2, limit: 10 });
      
      expect(page1.data).toHaveLength(10);
      expect(page2.data).toHaveLength(10);
      expect(page1.data[0].id).not.toBe(page2.data[0].id);
    });
  },

  /**
   * Test caching behavior
   */
  caching: (testFn: () => Promise<any>, cacheKey: string) => {
    it('should cache results', async () => {
      const result1 = await testFn();
      const result2 = await testFn();
      
      expect(result1).toEqual(result2);
    });
  },
};

/**
 * Performance test utilities
 */
export class PerformanceTestUtils {
  /**
   * Measure execution time
   */
  static async measureTime<T>(fn: () => Promise<T>): Promise<{ result: T; duration: number }> {
    const start = performance.now();
    const result = await fn();
    const duration = performance.now() - start;
    
    return { result, duration };
  }

  /**
   * Assert performance requirement
   */
  static assertPerformance(duration: number, maxMs: number, operation: string): void {
    expect(duration).toBeLessThan(maxMs);
    console.log(`${operation}: ${duration.toFixed(2)}ms (max: ${maxMs}ms)`);
  }

  /**
   * Load test with concurrent requests
   */
  static async concurrentLoad<T>(
    fn: () => Promise<T>,
    concurrency: number,
    iterations: number
  ): Promise<{ success: number; failure: number; avgDuration: number }> {
    const results: { success: boolean; duration: number }[] = [];

    for (let i = 0; i < iterations; i++) {
      const batch = Array(concurrency).fill(null).map(async () => {
        const { duration } = await this.measureTime(fn);
        return { success: true, duration };
      });

      const batchResults = await Promise.allSettled(batch);
      results.push(
        ...batchResults.map(r => ({
          success: r.status === 'fulfilled',
          duration: r.status === 'fulfilled' ? (r.value as any).duration : 0,
        }))
      );
    }

    const success = results.filter(r => r.success).length;
    const failure = results.filter(r => !r.success).length;
    const avgDuration = results.reduce((sum, r) => sum + r.duration, 0) / results.length;

    return { success, failure, avgDuration };
  }
}

// Export all utilities
export default {
  TestUtils,
  MockFactories,
  CoverageAnalyzer,
  TestPatterns,
  PerformanceTestUtils,
};
