/**
 * Global Test Setup
 * Runs before all tests
 */

export default async (): Promise<void> => {
  // Set up test environment
  process.env.NODE_ENV = 'test';

  // Initialize performance monitoring for tests
  (global as unknown).__TEST_START_TIME__ = Date.now();

  // Set up test databases/storage if needed
  // (For now, we're using mocks)

  console.log('🚀 Canvas refactoring test suite starting...');
};
