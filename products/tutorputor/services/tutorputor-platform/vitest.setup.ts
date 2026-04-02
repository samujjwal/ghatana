/**
 * Vitest test setup file
 *
 * @doc.type config
 * @doc.purpose Test environment setup
 * @doc.layer platform
 * @doc.pattern Config
 */
import { vi } from "vitest";

// ============================================================================
// ENVIRONMENT VARIABLES FOR TESTS
// ============================================================================

// Required for app startup
if (!process.env.DATABASE_URL) {
  process.env.DATABASE_URL = "postgresql://test:test@localhost:5432/tutorputor_test";
}

if (!process.env.REDIS_URL) {
  process.env.REDIS_URL = "redis://localhost:6379/1"; // Use DB 1 for tests
}

if (!process.env.JWT_SECRET) {
  process.env.JWT_SECRET = "test-jwt-secret-32-chars-minimum-abc123xyz789"; // 32+ chars
}

if (!process.env.NODE_ENV) {
  process.env.NODE_ENV = "test";
}

// Optional but helpful
process.env.LOG_LEVEL = "error"; // Suppress logs in tests

// Mock global fetch
global.fetch = vi.fn();

// Mock console methods to reduce noise in tests
vi.spyOn(console, "log").mockImplementation(() => {});
vi.spyOn(console, "info").mockImplementation(() => {});
vi.spyOn(console, "warn").mockImplementation(() => {});
// Keep console.error for debugging test failures
// vi.spyOn(console, 'error').mockImplementation(() => {});

// Reset mocks before each test
beforeEach(() => {
  vi.clearAllMocks();
});

// Clean up after each test
afterEach(() => {
  vi.restoreAllMocks();
});
