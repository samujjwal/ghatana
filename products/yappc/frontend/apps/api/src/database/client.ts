/**
 * Prisma database client initialization and utilities.
 *
 * <p><b>Purpose</b><br>
 * Provides a singleton Prisma client instance with proper initialization,
 * connection pooling, and error handling.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { getPrismaClient } from '@/database/client';
 *
 * const prisma = getPrismaClient();
 * const user = await prisma.user.findUnique({ where: { id: 'user-123' } });
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Prisma client initialization
 * @doc.layer product
 * @doc.pattern Singleton
 */
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

export const Prisma = require('@prisma/client').Prisma;

let prismaClient: PrismaClient | null = null;
let pool: Pool | null = null;

/**
 * Gets or creates the Prisma client instance.
 *
 * <p><b>Purpose</b><br>
 * Ensures a single Prisma client instance is used throughout the application,
 * preventing connection pool exhaustion.
 *
 * @returns The Prisma client instance
 *
 * @doc.type function
 * @doc.purpose Get Prisma client
 * @doc.layer product
 * @doc.pattern Singleton
 */
export function getPrismaClient(): PrismaClient {
  if (!prismaClient) {
    // Prisma ORM v7 uses the query compiler by default, which requires either:
    // - a driver adapter (direct DB connection), or
    // - an Accelerate URL.
    if (!process.env.DATABASE_URL) {
      throw new Error('DATABASE_URL environment variable is not set');
    }

    pool = new Pool({ connectionString: process.env.DATABASE_URL });
    const adapter = new PrismaPg(pool);

    prismaClient = new PrismaClient({
      adapter,
      log:
        process.env.NODE_ENV === 'development'
          ? ['query', 'error', 'warn']
          : ['error'],
    });
  }
  return prismaClient;
}

/**
 * Initializes the Prisma client and verifies database connection.
 *
 * <p><b>Purpose</b><br>
 * Performs startup checks to ensure database connectivity before the application
 * starts accepting requests.
 *
 * @returns Promise that resolves when connection is verified
 * @throws Error if database connection fails
 *
 * @doc.type function
 * @doc.purpose Initialize database connection
 * @doc.layer product
 * @doc.pattern Initialization
 */
export async function initializeDatabase(): Promise<void> {
  const client = getPrismaClient();

  try {
    // Test database connection
    await client.$queryRaw`SELECT 1`;
    console.log('✓ Database connection verified');
  } catch (error) {
    console.error('✗ Database connection failed:', error);
    throw new Error('Failed to connect to database');
  }
}

/**
 * Disconnects the Prisma client.
 *
 * <p><b>Purpose</b><br>
 * Gracefully closes the database connection pool, useful for application shutdown.
 *
 * @returns Promise that resolves when disconnected
 *
 * @doc.type function
 * @doc.purpose Disconnect database
 * @doc.layer product
 * @doc.pattern Cleanup
 */
export async function disconnectDatabase(): Promise<void> {
  if (prismaClient) {
    await prismaClient.$disconnect();
    await pool?.end();
    pool = null;
    prismaClient = null;
    console.log('✓ Database disconnected');
  }
}

/**
 * Executes a database transaction.
 *
 * <p><b>Purpose</b><br>
 * Provides a convenient way to execute multiple database operations atomically.
 *
 * @param callback - Function containing database operations
 * @returns Result of the callback function
 * @throws Error if transaction fails
 *
 * @doc.type function
 * @doc.purpose Execute database transaction
 * @doc.layer product
 * @doc.pattern Transaction
 */
export async function withTransaction<T>(
  callback: (prisma: PrismaClient) => Promise<T>
): Promise<T> {
  const client = getPrismaClient();

  return client.$transaction(async (tx) => callback(tx as PrismaClient));
}
