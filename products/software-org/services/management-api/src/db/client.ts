import { PrismaClient } from '../../generated/prisma-client/index.js';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';
import { appConfig } from '../config/index.js';

/**
 * Creates a PostgreSQL connection pool and Prisma adapter.
 * 
 * @doc.type factory
 * @doc.purpose Database connection management
 * @doc.layer infrastructure
 */
const createPrismaClient = (): PrismaClient => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
        throw new Error('DATABASE_URL environment variable is required');
    }

    const pool = new Pool({ connectionString });
    globalThis.__pgPool = pool;
    const adapter = new PrismaPg(pool);

    return new PrismaClient({
        adapter,
        log: appConfig.isDevelopment ? ['query', 'error', 'warn'] : ['error'],
    });
};

declare global {
    var __prisma: PrismaClient | undefined;
    var __pgPool: Pool | undefined;
}

export const prisma = globalThis.__prisma ?? createPrismaClient();

if (appConfig.isDevelopment) {
    globalThis.__prisma = prisma;
}

// Graceful shutdown - disconnect Prisma and close pool
process.on('beforeExit', async () => {
    await prisma.$disconnect();
    if (globalThis.__pgPool) {
        await globalThis.__pgPool.end();
    }
});
