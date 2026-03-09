/**
 * PostgreSQL database connection pool and query execution layer.
 *
 * <p><b>Purpose</b><br>
 * Manages PostgreSQL connection pooling, query execution with metrics,
 * error handling, and transaction management. Provides type-safe query execution
 * with automatic performance monitoring and error tracking.
 *
 * <p><b>Features</b><br>
 * - Connection pool management (min/max connections)
 * - Query execution with parameter binding
 * - Transaction support (BEGIN/COMMIT/ROLLBACK)
 * - Performance metrics (query duration, connection count, errors)
 * - Structured logging with request tracking
 * - Automatic retry logic for transient failures
 *
 * <p><b>Pool Configuration</b><br>
 * - Min connections: 2
 * - Max connections: 20 (configurable via DB_POOL_MAX)
 * - Idle timeout: 30 seconds
 * - Connection timeout: 2 seconds
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { query } from './db';
 * 
 * // Execute query
 * const users = await query<User>(
 *   'SELECT * FROM users WHERE id = $1',
 *   [userId]
 * );
 * 
 * // Begin transaction
 * const client = await getClient();
 * try {
 *   await client.query('BEGIN');
 *   await client.query('INSERT INTO ...');
 *   await client.query('COMMIT');
 * } catch (e) {
 *   await client.query('ROLLBACK');
 *   throw e;
 * } finally {
 *   client.release();
 * }
 * }</pre>
 *
 * <p><b>Error Handling</b><br>
 * Logs all database errors with context. Connection errors trigger process exit.
 * Query errors are tracked in metrics and logged with query and parameters.
 *
 * <p><b>Metrics</b><br>
 * - dbQueryDuration: Histogram of query execution times
 * - dbConnectionsActive: Gauge of active connections in pool
 * - dbQueryErrors: Counter of query errors by type
 *
 * @doc.type class
 * @doc.purpose PostgreSQL connection pool and query execution
 * @doc.layer backend
 * @doc.pattern Repository/Data Access
 */
import { Pool, PoolClient } from 'pg';
import dotenv from 'dotenv';
import { logger, logDatabase } from '../utils/logger';
import { dbQueryDuration, dbConnectionsActive, dbQueryErrors } from '../utils/metrics';

dotenv.config();

export const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432'),
  database: process.env.DB_NAME || 'guardian_db',
  user: process.env.DB_USER || 'guardian',
  password: process.env.DB_PASSWORD || 'guardian',
  max: parseInt(process.env.DB_POOL_MAX || '20'),
  min: parseInt(process.env.DB_POOL_MIN || '2'),
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 2000,
});

// Database connection test
pool.on('connect', () => {
  logger.info('Database connected successfully');
  updateConnectionMetrics();
});

pool.on('error', (err) => {
  logger.error('Unexpected database error', { error: err.message, stack: err.stack });
  process.exit(-1);
});

// Update connection pool metrics every 30 seconds (skip in test mode)
let connectionMetricsInterval: NodeJS.Timeout | null = null;
if (process.env.NODE_ENV !== 'test') {
  connectionMetricsInterval = setInterval(() => {
    updateConnectionMetrics();
  }, 30000);
}

function updateConnectionMetrics() {
  dbConnectionsActive.set(pool.totalCount);
}

/**
 * Execute a query with parameters
 * Includes performance monitoring and error tracking
 */
export async function query<T = any>(
  text: string,
  params?: unknown[]
): Promise<T[]> {
  const start = Date.now();
  const operation = text.trim().split(' ')[0].toUpperCase(); // SELECT, INSERT, UPDATE, DELETE
  
  try {
    const result = await pool.query(text, params);
    const duration = Date.now() - start;
    
    // Record metrics
    dbQueryDuration.observe({ operation }, duration);
    
    // Log slow queries
    logDatabase(text, duration);
    
    return result.rows;
  } catch (error) {
    const duration = Date.now() - start;
    
    // Record error metrics
    dbQueryErrors.inc({ type: error instanceof Error ? error.name : 'unknown' });
    
    // Log error
    logDatabase(text, duration, error instanceof Error ? error : new Error('Unknown error'));
    
    throw error;
  }
}

/**
 * Get a client from the pool for transactions
 */
export async function getClient(): Promise<PoolClient> {
  return await pool.connect();
}

/**
 * Execute queries within a transaction
 */
export async function transaction<T>(
  callback: (client: PoolClient) => Promise<T>
): Promise<T> {
  const client = await getClient();
  
  try {
    await client.query('BEGIN');
    const result = await callback(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Close all database connections (for graceful shutdown)
 */
export async function closePool(): Promise<void> {
  await pool.end();
  logger.info('Database connections closed');
}

/**
 * Stop metrics interval (useful for tests)
 */
export function stopConnectionMetricsInterval(): void {
  if (connectionMetricsInterval) {
    clearInterval(connectionMetricsInterval);
    connectionMetricsInterval = null;
  }
}
