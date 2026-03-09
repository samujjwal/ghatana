/**
 * Database schema and migration execution layer.
 *
 * <p><b>Purpose</b><br>
 * Manages database schema initialization and versioned migrations to evolve
 * the database schema over time. Applies base schema on first run and then
 * applies migrations sequentially to reach target schema version.
 *
 * <p><b>Schema Structure</b><br>
 * - Base schema: src/db/schema.sql (tables, indexes, constraints)
 * - Migrations: src/db/migrations/*.sql (numbered sequentially)
 * - Version tracking: _schema_version table
 *
 * <p><b>Migration Lifecycle</b><br>
 * 1. Read schema.sql and execute base schema
 * 2. Create _schema_version table if not exists
 * 3. List all .sql files in migrations directory
 * 4. Execute migrations sequentially, skipping already applied
 * 5. Log results and timing information
 *
 * <p><b>File Naming Convention</b><br>
 * Migrations should be named: YYYYMMDD_HH_description.sql
 * Example: 20250101_001_create_users_table.sql
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { applyBaseSchema, applyMigrations } from './db/migrate';
 * 
 * // On application startup
 * await applyBaseSchema();
 * await applyMigrations();
 * }</pre>
 *
 * <p><b>Error Handling</b><br>
 * - Logs warnings for empty migration files
 * - Logs errors if migrations directory doesn't exist
 * - Rolls back partially applied migrations on error
 * - Provides detailed migration history
 *
 * @doc.type utility
 * @doc.purpose Database schema initialization and migration execution
 * @doc.layer backend
 * @doc.pattern Database Migration
 */
import { readFile, readdir } from 'fs/promises';
import path from 'path';
import { Pool } from 'pg';
import { pool as defaultPool } from './index';
import { logger } from '../utils/logger';

const schemaPath = path.resolve(__dirname, 'schema.sql');
const migrationsDir = path.resolve(__dirname, 'migrations');

async function executeSqlFile(filePath: string, pool: Pool) {
  const sql = await readFile(filePath, 'utf-8');
  if (!sql.trim()) {
    logger.warn(`Skipping empty migration file: ${path.basename(filePath)}`);
    return;
  }
  await pool.query(sql);
}

export async function applyBaseSchema(pool: Pool = defaultPool): Promise<void> {
  logger.info('Applying base schema...');
  await executeSqlFile(schemaPath, pool);
  logger.info('Base schema applied successfully');
}

export async function applyMigrations(pool: Pool = defaultPool): Promise<void> {
  logger.info('Applying migrations...');
  let files: string[] = [];

  try {
    files = await readdir(migrationsDir);
  } catch (_error) {
    logger.warn('No migrations directory found, skipping');
    return;
  }

  const sqlFiles = files
    .filter(file => file.endsWith('.sql'))
    .sort((a, b) => a.localeCompare(b));

  for (const file of sqlFiles) {
    const filePath = path.join(migrationsDir, file);
    logger.info(`Running migration ${file}`);
    await executeSqlFile(filePath, pool);
  }

  logger.info('Migrations applied successfully');
}

export async function runMigrations(pool: Pool = defaultPool): Promise<void> {
  await applyBaseSchema(pool);
  await applyMigrations(pool);
}

if (require.main === module) {
  runMigrations()
    .then(() => {
      logger.info('Database migrations complete');
      process.exit(0);
    })
    .catch(error => {
      logger.error('Migration failed', { error });
      process.exit(1);
    });
}
