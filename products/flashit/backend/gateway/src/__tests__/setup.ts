/**
 * Test setup file
 * Runs before all tests
 */

import { beforeAll, afterAll, beforeEach, afterEach } from 'vitest';

// Set test environment variables
process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgresql://flashit_test:flashit_test_password@localhost:5433/flashit_test';
process.env.REDIS_URL = process.env.REDIS_URL || 'redis://localhost:6379';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test-secret-key-for-testing-only';

// Global test setup
beforeAll(async () => {
    // Setup code that runs once before all tests
    console.log('Starting test suite...');
});

afterAll(async () => {
    // Cleanup code that runs once after all tests
    console.log('Test suite complete.');
});

beforeEach(async () => {
    // Setup code that runs before each test
});

afterEach(async () => {
    // Cleanup code that runs after each test
});
