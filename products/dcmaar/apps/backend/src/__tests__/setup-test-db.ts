/**
 * Test Database Setup Script
 *
 * This script:
 * 1. Drops the test database if it exists
 * 2. Creates a fresh test database
 * 3. Runs all migrations
 *
 * Usage:
 *   tsx src/__tests__/setup-test-db.ts
 */

import { Pool } from 'pg';
import dotenv from 'dotenv';
import { applyBaseSchema, applyMigrations } from '../db/migrate';

// Load test environment variables - override any existing .env values
dotenv.config({ path: '.env.test', override: true });

const DB_NAME = process.env.DB_NAME || 'guardian_test';
const DB_HOST = process.env.DB_HOST || 'localhost';
const DB_PORT = parseInt(process.env.DB_PORT || '5432');
const DB_USER = process.env.DB_USER || 'guardian';
const DB_PASSWORD = process.env.DB_PASSWORD || 'guardian';

/**
 * Connect to PostgreSQL server (not specific database)
 */
function createAdminPool(): Pool {
  return new Pool({
    host: DB_HOST,
    port: DB_PORT,
    database: 'postgres', // Connect to default database
    user: DB_USER,
    password: DB_PASSWORD,
  });
}

/**
 * Connect to test database
 */
function createTestPool(): Pool {
  return new Pool({
    host: DB_HOST,
    port: DB_PORT,
    database: DB_NAME,
    user: DB_USER,
    password: DB_PASSWORD,
  });
}

/**
 * Drop test database if exists
 */
async function dropDatabase(adminPool: Pool): Promise<void> {
  console.log(`🗑️  Dropping database ${DB_NAME} if it exists...`);

  try {
    // Terminate all connections to the database
    await adminPool.query(`
      SELECT pg_terminate_backend(pg_stat_activity.pid)
      FROM pg_stat_activity
      WHERE pg_stat_activity.datname = $1
      AND pid <> pg_backend_pid()
    `, [DB_NAME]);

    // Drop the database
    await adminPool.query(`DROP DATABASE IF EXISTS ${DB_NAME}`);
    console.log(`✅ Database ${DB_NAME} dropped successfully`);
  } catch (error: any) {
    console.error(`❌ Error dropping database:`, error.message);
    throw error;
  }
}

/**
 * Create test database
 */
async function createDatabase(adminPool: Pool): Promise<void> {
  console.log(`📦 Creating database ${DB_NAME}...`);

  try {
    await adminPool.query(`CREATE DATABASE ${DB_NAME}`);
    console.log(`✅ Database ${DB_NAME} created successfully`);
  } catch (error: any) {
    console.error(`❌ Error creating database:`, error.message);
    throw error;
  }
}

/**
 * Run migrations on test database
 */
async function runMigrations(): Promise<void> {
  console.log(`🔧 Running migrations on ${DB_NAME}...`);

  const testPool = createTestPool();

  try {
    // Run migrations directly with test pool
    await applyBaseSchema(testPool);
    await applyMigrations(testPool);

    console.log(`✅ Migrations completed successfully`);
  } catch (error: any) {
    console.error(`❌ Error running migrations:`, error.message);
    throw error;
  } finally {
    await testPool.end();
  }
}

/**
 * Main setup function
 */
async function setupTestDatabase(): Promise<void> {
  const adminPool = createAdminPool();

  try {
    console.log('\n🚀 Setting up test database...\n');
    console.log(`Database: ${DB_NAME}`);
    console.log(`Host: ${DB_HOST}:${DB_PORT}`);
    console.log(`User: ${DB_USER}\n`);

    // Step 1: Drop existing database
    await dropDatabase(adminPool);

    // Step 2: Create fresh database
    await createDatabase(adminPool);

    // Step 3: Run migrations
    await runMigrations();

    console.log('\n✅ Test database setup complete!\n');
  } catch (error: any) {
    console.error('\n❌ Test database setup failed:', error.message);
    process.exit(1);
  } finally {
    await adminPool.end();
  }
}

/**
 * Cleanup test database
 */
export async function cleanupTestDatabase(): Promise<void> {
  const adminPool = createAdminPool();

  try {
    console.log('\n🧹 Cleaning up test database...\n');
    await dropDatabase(adminPool);
    console.log('\n✅ Test database cleanup complete!\n');
  } catch (error: any) {
    console.error('\n❌ Test database cleanup failed:', error.message);
    process.exit(1);
  } finally {
    await adminPool.end();
  }
}

// Run setup if called directly
if (require.main === module) {
  const command = process.argv[2];

  if (command === 'cleanup') {
    cleanupTestDatabase();
  } else {
    setupTestDatabase();
  }
}

export { setupTestDatabase };
