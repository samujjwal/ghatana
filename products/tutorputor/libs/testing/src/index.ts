/**
 * TutorPutor Testing Infrastructure
 *
 * Centralized test utilities and configuration for consistent testing
 * across all modules. Follows best practices for unit, integration, and E2E testing.
 *
 * @module @tutorputor/testing
 * @doc.layer testing
 */

/// <reference types="vitest" />

import { vi, beforeEach, afterEach, type Mock } from "vitest";

// ============================================================================
// Type Definitions
// ============================================================================

export interface TestUser {
  id: string;
  email: string;
  name: string;
  role: "learner" | "instructor" | "admin";
}

export interface TestSimulation {
  id: string;
  title: string;
  domain: string;
  status: "draft" | "published" | "archived";
}

export interface TestClaim {
  id: string;
  text: string;
  subject: string;
  gradeLevel: string;
}

// ============================================================================
// Mock Factories
// ============================================================================

export const createMockUser = (
  overrides: Partial<TestUser> = {},
): TestUser => ({
  id: `user-${Math.random().toString(36).substr(2, 9)}`,
  email: "test@example.com",
  name: "Test User",
  role: "learner",
  ...overrides,
});

export const createMockSimulation = (
  overrides: Partial<TestSimulation> = {},
): TestSimulation => ({
  id: `sim-${Math.random().toString(36).substr(2, 9)}`,
  title: "Test Simulation",
  domain: "PHYSICS",
  status: "published",
  ...overrides,
});

export const createMockClaim = (
  overrides: Partial<TestClaim> = {},
): TestClaim => ({
  id: `claim-${Math.random().toString(36).substr(2, 9)}`,
  text: "Test learning claim",
  subject: "PHYSICS",
  gradeLevel: "HIGH_SCHOOL",
  ...overrides,
});

// ============================================================================
// Vitest Utilities
// ============================================================================

/**
 * Creates a typed mock function with proper return type inference
 */
export function createMockFn<T extends (...args: any[]) => any>(): Mock<T> {
  return vi.fn() as Mock<T>;
}

/**
 * Mocks console methods for testing and restores after
 */
export function mockConsole() {
  const originalLog = console.log;
  const originalError = console.error;
  const originalWarn = console.warn;

  beforeEach(() => {
    console.log = vi.fn();
    console.error = vi.fn();
    console.warn = vi.fn();
  });

  afterEach(() => {
    console.log = originalLog;
    console.error = originalError;
    console.warn = originalWarn;
  });
}

/**
 * Creates a mock timer environment for time-based tests
 */
export function useMockTimers() {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });
}

// ============================================================================
// Async Testing Utilities
// ============================================================================

/**
 * Waits for a condition to be met with timeout
 */
export async function waitFor(
  condition: () => boolean | Promise<boolean>,
  timeout: number = 5000,
  interval: number = 100,
): Promise<void> {
  const startTime = Date.now();

  while (Date.now() - startTime < timeout) {
    if (await condition()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, interval));
  }

  throw new Error(`waitFor timeout after ${timeout}ms`);
}

/**
 * Flushes all pending promises
 */
export async function flushPromises(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

// ============================================================================
// Database Testing Utilities
// ============================================================================

export interface MockPrismaClient {
  $transaction: Mock;
  $queryRaw: Mock;
  $executeRaw: Mock;
  user: {
    findUnique: Mock;
    findMany: Mock;
    create: Mock;
    update: Mock;
    delete: Mock;
  };
  simulation: {
    findUnique: Mock;
    findMany: Mock;
    create: Mock;
    update: Mock;
    delete: Mock;
  };
}

/**
 * Creates a mock Prisma client for testing
 */
export function createMockPrismaClient(): MockPrismaClient {
  return {
    $transaction: vi.fn((fn) => fn()),
    $queryRaw: vi.fn(),
    $executeRaw: vi.fn(),
    user: {
      findUnique: vi.fn(),
      findMany: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    },
    simulation: {
      findUnique: vi.fn(),
      findMany: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    },
  };
}

// ============================================================================
// AI Testing Utilities
// ============================================================================

export interface MockAIResponse {
  content: string;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
}

/**
 * Creates a mock AI provider response
 */
export function createMockAIResponse(
  overrides: Partial<MockAIResponse> = {},
): MockAIResponse {
  return {
    content: "Mock AI response",
    usage: {
      promptTokens: 100,
      completionTokens: 50,
      totalTokens: 150,
    },
    ...overrides,
  };
}

/**
 * Mocks AI provider for consistent testing
 */
export function mockAIProvider(): {
  generate: Mock;
  stream: Mock;
  embed: Mock;
} {
  return {
    generate: vi.fn().mockResolvedValue(createMockAIResponse()),
    stream: vi.fn().mockResolvedValue(createMockAIResponse()),
    embed: vi.fn().mockResolvedValue(new Array(1536).fill(0)),
  };
}

// ============================================================================
// Export all utilities
// ============================================================================

export * from "vitest";
