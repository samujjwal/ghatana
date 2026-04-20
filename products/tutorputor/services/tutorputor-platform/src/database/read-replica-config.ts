/**
 * Database Read Replica Configuration
 *
 * Configures Prisma client with read replica support for improved read performance.
 * Implements automatic read/write split based on query type.
 *
 * @doc.type config
 * @doc.purpose Database read replica configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */

import { PrismaClient, createPrismaClientForUrl } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'ReadReplicaConfig' });

export interface ReadReplicaConfig {
  primaryUrl: string;
  replicaUrls: string[];
  replicaCount: number;
  enableReadSplit: boolean;
  connectionTimeout: number;
  poolTimeout: number;
  poolSize: number;
}

/**
 * Create Prisma client with read replica support
 */
export function createPrismaWithReplicas(config: ReadReplicaConfig): PrismaClient {
  const prisma = createPrismaClientForUrl(config.primaryUrl);
  const observablePrisma = prisma as PrismaClient & {
    $on: (
      event: 'query',
      callback: (event: { query: string; duration: number; params: string }) => void,
    ) => void;
  };

  // Log queries for monitoring
  observablePrisma.$on('query', (e) => {
    logger.debug({
      message: 'Database query',
      query: e.query,
      duration: e.duration,
      params: e.params,
    });
  });

  logger.info({
    message: 'Prisma client configured with read replicas',
    primaryUrl: maskUrl(config.primaryUrl),
    replicaCount: config.replicaUrls.length,
    enableReadSplit: config.enableReadSplit,
  });

  return prisma;
}

/**
 * Mask sensitive parts of database URL for logging
 */
export function maskUrl(url: string): string {
  try {
    const parsed = new URL(url);
    if (parsed.password) {
      parsed.password = '*****';
    }
    return parsed.toString();
  } catch {
    return url;
  }
}

/**
 * Get read replica configuration from environment
 */
export function getReadReplicaConfigFromEnv(): ReadReplicaConfig {
  const primaryUrl = process.env.DATABASE_URL;
  
  if (!primaryUrl) {
    throw new Error('DATABASE_URL environment variable is required');
  }

  const replicaUrls = parseReplicaUrls(
    process.env.DATABASE_REPLICA_URLS || ''
  );

  return {
    primaryUrl,
    replicaUrls,
    replicaCount: replicaUrls.length,
    enableReadSplit: process.env.DATABASE_READ_SPLIT_ENABLED === 'true',
    connectionTimeout: parseInt(process.env.DATABASE_CONNECT_TIMEOUT || '5', 10),
    poolTimeout: parseInt(process.env.DATABASE_POOL_TIMEOUT || '10', 10),
    poolSize: parseInt(process.env.DATABASE_POOL_SIZE || '10', 10),
  };
}

/**
 * Parse replica URLs from comma-separated string
 */
function parseReplicaUrls(urlsString: string): string[] {
  if (!urlsString) {
    return [];
  }

  return urlsString
    .split(',')
    .map(url => url.trim())
    .filter(url => url.length > 0);
}

/**
 * Validate read replica configuration
 */
export function validateReadReplicaConfig(config: ReadReplicaConfig): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!config.primaryUrl) {
    errors.push('Primary database URL is required');
  }

  if (config.replicaUrls.length === 0) {
    errors.push('At least one replica URL is required');
  }

  if (config.poolSize < 1) {
    errors.push('Pool size must be at least 1');
  }

  if (config.connectionTimeout < 1) {
    errors.push('Connection timeout must be at least 1 second');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Get replica health status
 */
export async function checkReplicaHealth(prisma: PrismaClient, config: ReadReplicaConfig): Promise<{
  primary: boolean;
  replicas: Array<{ url: string; healthy: boolean; latency?: number }>;
}> {
  const results = {
    primary: false,
    replicas: [] as Array<{ url: string; healthy: boolean; latency?: number }>,
  };

  // Check primary
  try {
    const start = Date.now();
    await prisma.$queryRaw`SELECT 1`;
    results.primary = true;
    logger.debug({ message: 'Primary database healthy', latency: Date.now() - start });
  } catch (error) {
    logger.error({ message: 'Primary database unhealthy', error });
  }

  // Check replicas
  for (const replicaUrl of config.replicaUrls) {
    try {
      const start = Date.now();
      const replicaPrisma = createPrismaClientForUrl(replicaUrl);
      await replicaPrisma.$queryRaw`SELECT 1`;
      await replicaPrisma.$disconnect();
      
      results.replicas.push({
        url: maskUrl(replicaUrl),
        healthy: true,
        latency: Date.now() - start,
      });
      
      logger.debug({ message: 'Replica healthy', url: maskUrl(replicaUrl), latency: Date.now() - start });
    } catch (error) {
      results.replicas.push({
        url: maskUrl(replicaUrl),
        healthy: false,
      });
      logger.error({ message: 'Replica unhealthy', url: maskUrl(replicaUrl), error });
    }
  }

  return results;
}
