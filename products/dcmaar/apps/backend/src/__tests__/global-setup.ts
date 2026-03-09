/**
 * Vitest Global Setup
 *
 * This runs once before all test files.
 * It sets up the test database by:
 * 1. Dropping existing test database
 * 2. Creating fresh test database
 * 3. Running all migrations
 */

import { setupTestDatabase, cleanupTestDatabase } from './setup-test-db';

export async function setup() {
  console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('🔧 GLOBAL TEST SETUP - Setting up test database...');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

  try {
    await setupTestDatabase();
  } catch (error: any) {
    console.error('\n❌ Failed to setup test database:', error.message);
    console.error('\nMake sure PostgreSQL is running and credentials are correct.');
    console.error('Check your .env.test file for database configuration.\n');
    throw error;
  }
}

export async function teardown() {
  console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('🧹 GLOBAL TEST TEARDOWN - Cleaning up test database...');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

  try {
    await cleanupTestDatabase();
  } catch (error: any) {
    // Non-critical error during cleanup
    console.warn('⚠️  Warning: Failed to cleanup test database:', error.message);
  }
}
