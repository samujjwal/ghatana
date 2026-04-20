/**
 * Database Connection Pool Configuration
 *
 * Configures Prisma connection pool settings for optimal performance.
 * Includes pool size, timeout, and health monitoring.
 *
 * @doc.type config
 * @doc.purpose Database connection pool configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */

import { PrismaClient, createPrismaClientForUrl } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'ConnectionPoolConfig' });

export interface ConnectionPoolConfig {
  /**
   * Maximum number of connections in the pool
   */
  poolSize: number;

  /**
   * Timeout in seconds for acquiring a connection from the pool
   */
  poolTimeout: number;

  /**
   * Connection timeout in seconds
   */
  connectionTimeout: number;

  /**
   * Idle timeout in seconds - connections idle longer than this are closed
   */
  idleTimeout: number;

  /**
   * Maximum lifetime of a connection in seconds
   */
  maxLifetime: number;

  /**
   * Enable connection health checks
   */
  enableHealthChecks: boolean;

  /**
   * Health check interval in seconds
   */
  healthCheckInterval: number;
}

/**
 * Create Prisma client with connection pool configuration
 */
export function createPrismaWithPool(config: ConnectionPoolConfig, datasourceUrl?: string): PrismaClient {
  const url = datasourceUrl || process.env.DATABASE_URL;
  
  if (!url) {
    throw new Error('DATABASE_URL environment variable is required');
  }

  const prisma = createPrismaClientForUrl(url);

  logger.info({
    message: 'Prisma client configured with connection pool',
    poolSize: config.poolSize,
    poolTimeout: config.poolTimeout,
    connectionTimeout: config.connectionTimeout,
    idleTimeout: config.idleTimeout,
    maxLifetime: config.maxLifetime,
  });

  return prisma;
}

/**
 * Get connection pool configuration from environment
 */
export function getConnectionPoolConfigFromEnv(): ConnectionPoolConfig {
  return {
    poolSize: parseInt(process.env.DATABASE_POOL_SIZE || '10', 10),
    poolTimeout: parseInt(process.env.DATABASE_POOL_TIMEOUT || '10', 10),
    connectionTimeout: parseInt(process.env.DATABASE_CONNECT_TIMEOUT || '5', 10),
    idleTimeout: parseInt(process.env.DATABASE_IDLE_TIMEOUT || '600', 10),
    maxLifetime: parseInt(process.env.DATABASE_MAX_LIFETIME || '1800', 10),
    enableHealthChecks: process.env.DATABASE_HEALTH_CHECKS_ENABLED === 'true',
    healthCheckInterval: parseInt(process.env.DATABASE_HEALTH_CHECK_INTERVAL || '30', 10),
  };
}

/**
 * Validate connection pool configuration
 */
export function validateConnectionPoolConfig(config: ConnectionPoolConfig): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (config.poolSize < 1) {
    errors.push('Pool size must be at least 1');
  }

  if (config.poolSize > 100) {
    errors.push('Pool size should not exceed 100');
  }

  if (config.poolTimeout < 1) {
    errors.push('Pool timeout must be at least 1 second');
  }

  if (config.connectionTimeout < 1) {
    errors.push('Connection timeout must be at least 1 second');
  }

  if (config.idleTimeout < 60) {
    errors.push('Idle timeout should be at least 60 seconds');
  }

  if (config.maxLifetime < config.idleTimeout) {
    errors.push('Max lifetime must be greater than idle timeout');
  }

  if (config.healthCheckInterval < 10) {
    errors.push('Health check interval should be at least 10 seconds');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Get recommended pool size based on environment
 */
export function getRecommendedPoolSize(): number {
  const nodeEnv = process.env.NODE_ENV || 'development';
  const cpuCount = require('os').cpus().length;

  switch (nodeEnv) {
    case 'production':
      // Production: 2x CPU count, minimum 10, maximum 50
      return Math.min(Math.max(cpuCount * 2, 10), 50);
    case 'staging':
      // Staging: CPU count, minimum 5
      return Math.max(cpuCount, 5);
    default:
      // Development: 5 connections
      return 5;
  }
}

/**
 * Connection pool health monitor
 */
export class ConnectionPoolHealthMonitor {
  private prisma: PrismaClient;
  private config: ConnectionPoolConfig;
  private interval: NodeJS.Timeout | null = null;
  private healthStatus: Map<string, boolean> = new Map();

  constructor(prisma: PrismaClient, config: ConnectionPoolConfig) {
    this.prisma = prisma;
    this.config = config;
  }

  /**
   * Start health monitoring
   */
  start(): void {
    if (!this.config.enableHealthChecks) {
      logger.info({ message: 'Connection pool health checks disabled' });
      return;
    }

    if (this.interval) {
      logger.warn({ message: 'Health monitor already started' });
      return;
    }

    this.interval = setInterval(async () => {
      await this.checkHealth();
    }, this.config.healthCheckInterval * 1000);

    logger.info({
      message: 'Connection pool health monitor started',
      interval: this.config.healthCheckInterval,
    });
  }

  /**
   * Stop health monitoring
   */
  stop(): void {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
      logger.info({ message: 'Connection pool health monitor stopped' });
    }
  }

  /**
   * Check connection pool health
   */
  private async checkHealth(): Promise<void> {
    try {
      const start = Date.now();
      await this.prisma.$queryRaw`SELECT 1`;
      const latency = Date.now() - start;

      this.healthStatus.set('primary', true);
      
      logger.debug({
        message: 'Connection pool health check passed',
        latency,
      });
    } catch (error) {
      this.healthStatus.set('primary', false);
      
      logger.error({
        message: 'Connection pool health check failed',
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  /**
   * Get health status
   */
  getHealthStatus(): Map<string, boolean> {
    return new Map(this.healthStatus);
  }

  /**
   * Get pool statistics
   */
  async getPoolStats(): Promise<{
    activeConnections: number;
    idleConnections: number;
    totalConnections: number;
  }> {
    try {
      // Query PostgreSQL connection statistics
      const result = await this.prisma.$queryRaw`
        SELECT 
          COUNT(*) FILTER (WHERE state = 'active') as active,
          COUNT(*) FILTER (WHERE state = 'idle') as idle,
          COUNT(*) as total
        FROM pg_stat_activity
        WHERE datname = current_database()
      ` as Array<{ active: bigint; idle: bigint; total: bigint }>;

      const row = result[0];
      if (row) {
        return {
          activeConnections: Number(row.active),
          idleConnections: Number(row.idle),
          totalConnections: Number(row.total),
        };
      }

      return {
        activeConnections: 0,
        idleConnections: 0,
        totalConnections: 0,
      };
    } catch (error) {
      logger.error({ message: 'Failed to get pool stats', error });
      return {
        activeConnections: 0,
        idleConnections: 0,
        totalConnections: 0,
      };
    }
  }
}

/**
 * Create connection pool health monitor
 */
export function createConnectionPoolHealthMonitor(
  prisma: PrismaClient,
  config?: ConnectionPoolConfig
): ConnectionPoolHealthMonitor {
  const poolConfig = config || getConnectionPoolConfigFromEnv();
  return new ConnectionPoolHealthMonitor(prisma, poolConfig);
}
