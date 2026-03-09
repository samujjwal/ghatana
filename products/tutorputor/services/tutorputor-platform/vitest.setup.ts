/**
 * Vitest test setup file
 *
 * @doc.type config
 * @doc.purpose Test environment setup
 * @doc.layer platform
 * @doc.pattern Config
 */
import { vi } from "vitest";

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
