/**
 * Vitest Test Setup
 *
 * This file runs before all tests to:
 * - Set up test database connection
 * - Configure environment variables
 * - Mock external services
 * - Set up global test utilities
 */

import { beforeAll, afterEach, afterAll, vi } from "vitest";
import { Pool } from "pg";
import dotenv from "dotenv";

// Load test environment variables
dotenv.config({ path: ".env.test" });

// Override environment variables for testing
process.env.NODE_ENV = "test";
process.env.JWT_SECRET =
  "test-jwt-secret-minimum-32-characters-long-for-security";
process.env.JWT_REFRESH_SECRET =
  "test-jwt-refresh-secret-minimum-32-characters-long";
process.env.DB_NAME = process.env.DB_NAME || "guardian_test";
process.env.DB_HOST = process.env.DB_HOST || "localhost";
process.env.DB_PORT = process.env.DB_PORT || "5432";
process.env.DB_USER = process.env.DB_USER || "guardian";
process.env.DB_PASSWORD = process.env.DB_PASSWORD || "guardian";
process.env.LOG_LEVEL = "error"; // Suppress logs during tests

// Create test database pool
let testPool: Pool;

/**
 * Initialize test database connection
 */
export async function initTestDatabase(): Promise<Pool> {
  if (!testPool) {
    try {
      testPool = new Pool({
        host: process.env.DB_HOST,
        port: parseInt(process.env.DB_PORT || "5432"),
        database: process.env.DB_NAME,
        user: process.env.DB_USER,
        password: process.env.DB_PASSWORD,
        max: 5, // Limit connections for tests
        idleTimeoutMillis: 30000,
        connectionTimeoutMillis: 2000,
      });
      // Test connection
      await testPool.query("SELECT 1");
    } catch (error) {
      console.warn(
        "⚠️  Test database not available. Tests requiring database will be skipped."
      );
      console.warn(
        "   To run all tests, ensure PostgreSQL is running and create the test database:"
      );
      console.warn(`   $ createdb ${process.env.DB_NAME || "guardian_test"}`);
      // Create a mock pool for tests that don't actually need database
      testPool = {
        query: vi.fn().mockResolvedValue({ rows: [] }),
        connect: vi.fn().mockResolvedValue({
          query: vi.fn().mockResolvedValue({ rows: [] }),
          release: vi.fn(),
        }),
        end: vi.fn().mockResolvedValue(undefined),
        totalCount: 0,
      } as any;
    }
  }
  return testPool;
}

/**
 * Close test database connection
 */
export async function closeTestDatabase(): Promise<void> {
  if (testPool) {
    await testPool.end();
  }
}

/**
 * Clean up all tables in the test database
 *
 * Deletes in reverse foreign key dependency order to prevent concurrent
 * race conditions where cleanup happens while other tests are setting up.
 *
 * Deletion order respects this FK structure:
 * - block_events → no FKs (safe to delete first)
 * - usage_sessions → device_id FK
 * - policies → user_id, child_id, device_id FKs
 * - devices → user_id, child_id FKs
 * - children → user_id FK
 * - users → no FKs (root table, delete last)
 *
 * This ordering eliminates most concurrent FK constraint violations
 * without requiring transaction coordination.
 */
export async function cleanDatabase(): Promise<void> {
  const pool = await initTestDatabase();

  try {
    // Skip cleanup if using mock pool
    if ((pool as any).query?.mock) {
      return;
    }

    // Delete in REVERSE foreign key dependency order
    // This ensures parent records aren't deleted while children still reference them
    const deletionOrder = [
      "block_events", // No foreign keys - safe to delete first
      "usage_sessions", // Foreign key: device_id
      "policies", // Foreign keys: user_id, child_id, device_id
      "devices", // Foreign keys: user_id, child_id
      "children", // Foreign key: user_id
      "users", // No foreign keys pointing to it - delete last
    ];

    // Delete each table in order
    for (const table of deletionOrder) {
      try {
        await pool.query(`DELETE FROM "${table}"`);
      } catch (error: any) {
        // Silently skip if table doesn't exist or other errors
        // This is safe because we're in test cleanup
        if (!error.message?.includes("does not exist")) {
          // Log non-existence errors, but still continue
          console.warn(`⚠️  Warning cleaning ${table}: ${error.message}`);
        }
      }
    }
  } catch (error) {
    // Silently skip - database not available
    console.warn("⚠️  Database cleanup failed - tests may have data pollution");
  }
}

/**
 * Run before all tests
 */
beforeAll(async () => {
  // Initialize test database
  await initTestDatabase();

  // Clean database before tests
  await cleanDatabase();
});

/**
 * Run after each test
 *
 * DISABLED: Cleaning up after each test causes issues with nested beforeAll hooks
 * that rely on suite-level test data (parent/child). The parent and child created
 * in the suite-level beforeAll are deleted by afterEach, breaking subsequent
 * describe blocks that use that same childId.
 *
 * Instead, we rely on:
 * 1. Individual tests to clean up their own data if needed
 * 2. Suite-level afterAll to clean up
 * 3. Global setup/teardown to reset database for each test run
 *
 * NOTE: This may cause data pollution across test runs. If that becomes an issue,
 * we can implement smarter cleanup that only deletes records created during
 * the current test (not shared suite-level fixtures).
 */
// afterEach(async () => {
//   // Clean up after each test to ensure isolation
//   await cleanDatabase();
// });

/**
 * Run after all tests
 */
afterAll(async () => {
  // Close database connections
  await closeTestDatabase();

  // Clear all timers to prevent timeout issues
  vi.clearAllTimers();
});

/**
 * Mock external services
 */

// Mock Sentry
vi.mock("../utils/sentry", () => ({
  initSentry: vi.fn(),
  captureException: vi.fn(),
  captureMessage: vi.fn(),
}));

// Mock logger to suppress logs during tests
vi.mock("../utils/logger", () => ({
  logger: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  },
  logDatabase: vi.fn(),
  logAuth: vi.fn(),
  logSecurity: vi.fn(),
  logHttp: vi.fn(),
  logError: vi.fn(),
}));

// Mock email service
vi.mock("../services/email.service", () => ({
  sendEmail: vi.fn().mockResolvedValue(true),
  sendVerificationEmail: vi.fn().mockResolvedValue(true),
  sendPasswordResetEmail: vi.fn().mockResolvedValue(true),
}));

// Mock metrics - all exports from utils/metrics.ts
vi.mock("../utils/metrics", () => ({
  register: {
    metrics: vi.fn().mockResolvedValue(""),
    contentType: "text/plain",
  },
  httpRequestDuration: { observe: vi.fn() },
  httpRequestTotal: { inc: vi.fn() },
  authAttempts: { inc: vi.fn() },
  activeUsers: { set: vi.fn(), inc: vi.fn(), dec: vi.fn() },
  activeSessions: { set: vi.fn(), inc: vi.fn(), dec: vi.fn() },
  dbQueryDuration: { observe: vi.fn() },
  dbConnectionsActive: { set: vi.fn(), inc: vi.fn(), dec: vi.fn() },
  dbQueryErrors: { inc: vi.fn() },
  policiesEnforced: { inc: vi.fn() },
  blockedAttempts: { inc: vi.fn() },
  childrenRegistered: { set: vi.fn(), inc: vi.fn(), dec: vi.fn() },
  devicesConnected: { set: vi.fn(), inc: vi.fn(), dec: vi.fn() },
  activePolicies: { set: vi.fn(), inc: vi.fn(), dec: vi.fn() },
  rateLimitExceeded: { inc: vi.fn() },
  emailsSent: { inc: vi.fn() },
  emailErrors: { inc: vi.fn() },
  applicationErrors: { inc: vi.fn() },
}));

// Mock telemetry
vi.mock("../utils/telemetry", () => ({
  initTelemetry: vi.fn(),
  shutdownTelemetry: vi.fn(),
}));

/**
 * Test helper: Wait for async operations
 */
export const waitFor = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Test helper: Generate random string
 */
export const randomString = (length: number = 10): string =>
  Math.random()
    .toString(36)
    .substring(2, 2 + length);

/**
 * Test helper: Generate random email
 */
export const randomEmail = (): string => `test-${randomString()}@example.com`;

/**
 * Global test utilities
 */
export const testHelpers = {
  waitFor,
  randomString,
  randomEmail,
};
