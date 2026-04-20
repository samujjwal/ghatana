/**
 * Replica Health Checker
 *
 * Monitors the health of database read replicas and automatically routes traffic
 * away from unhealthy replicas.
 *
 * @doc.type service
 * @doc.purpose Database replica health monitoring
 * @doc.layer platform
 * @doc.pattern Service
 */

import { PrismaClient, createPrismaClientForUrl } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'ReplicaHealthChecker' });

export interface ReplicaHealthStatus {
  url: string;
  healthy: boolean;
  latency: number;
  lastChecked: Date;
  errorCount: number;
  lastError?: string;
}

export interface ReplicaHealthCheckerOptions {
  /**
   * Health check interval in seconds
   */
  checkInterval: number;

  /**
   * Maximum acceptable latency in milliseconds
   */
  maxLatency: number;

  /**
   * Maximum consecutive failures before marking unhealthy
   */
  maxFailures: number;

  /**
   * Recovery check interval in seconds (for unhealthy replicas)
   */
  recoveryCheckInterval: number;
}

/**
 * Replica health checker
 */
export class ReplicaHealthChecker {
  private replicas: Map<string, ReplicaHealthStatus> = new Map();
  private primaryStatus: ReplicaHealthStatus | null = null;
  private options: ReplicaHealthCheckerOptions;
  private interval: NodeJS.Timeout | null = null;
  private recoveryIntervals: Map<string, NodeJS.Timeout> = new Map();

  constructor(options: Partial<ReplicaHealthCheckerOptions> = {}) {
    this.options = {
      checkInterval: options.checkInterval || 30,
      maxLatency: options.maxLatency || 1000,
      maxFailures: options.maxFailures || 3,
      recoveryCheckInterval: options.recoveryCheckInterval || 60,
    };
  }

  /**
   * Add a replica to monitor
   */
  addReplica(url: string): void {
    this.replicas.set(url, {
      url,
      healthy: true,
      latency: 0,
      lastChecked: new Date(),
      errorCount: 0,
    });

    logger.info({ message: 'Replica added to health checker', url: this.maskUrl(url) });
  }

  /**
   * Add primary database to monitor
   */
  addPrimary(url: string): void {
    this.primaryStatus = {
      url,
      healthy: true,
      latency: 0,
      lastChecked: new Date(),
      errorCount: 0,
    };

    logger.info({ message: 'Primary added to health checker', url: this.maskUrl(url) });
  }

  /**
   * Start health checking
   */
  start(): void {
    if (this.interval) {
      logger.warn({ message: 'Health checker already started' });
      return;
    }

    this.interval = setInterval(async () => {
      await this.checkAllReplicas();
    }, this.options.checkInterval * 1000);

    logger.info({
      message: 'Replica health checker started',
      interval: this.options.checkInterval,
      replicaCount: this.replicas.size,
    });
  }

  /**
   * Stop health checking
   */
  stop(): void {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }

    for (const [url, timeout] of this.recoveryIntervals) {
      clearTimeout(timeout);
      this.recoveryIntervals.delete(url);
    }

    logger.info({ message: 'Replica health checker stopped' });
  }

  /**
   * Check health of all replicas
   */
  private async checkAllReplicas(): Promise<void> {
    // Check primary
    if (this.primaryStatus) {
      await this.checkDatabase(this.primaryStatus.url, 'primary');
    }

    // Check replicas
    for (const [url, status] of this.replicas) {
      if (status.healthy) {
        await this.checkDatabase(url, 'replica');
      }
    }
  }

  /**
   * Check health of a single database
   */
  private async checkDatabase(url: string, type: 'primary' | 'replica'): Promise<void> {
    const start = Date.now();
    
    try {
      const prisma = createPrismaClientForUrl(url);
      
      await prisma.$queryRaw`SELECT 1`;
      await prisma.$disconnect();
      
      const latency = Date.now() - start;
      
      // Update status
      if (type === 'primary' && this.primaryStatus) {
        this.primaryStatus = {
          ...this.primaryStatus,
          healthy: latency <= this.options.maxLatency,
          latency,
          lastChecked: new Date(),
          errorCount: 0,
        };
      } else {
        const status = this.replicas.get(url);
        if (status) {
          this.replicas.set(url, {
            ...status,
            healthy: latency <= this.options.maxLatency,
            latency,
            lastChecked: new Date(),
            errorCount: 0,
          });
        }
      }

      logger.debug({
        message: `${type} health check passed`,
        url: this.maskUrl(url),
        latency,
      });
    } catch (error) {
      const latency = Date.now() - start;
      const errorMessage = error instanceof Error ? error.message : String(error);

      // Update status
      if (type === 'primary' && this.primaryStatus) {
        this.primaryStatus = {
          ...this.primaryStatus,
          healthy: false,
          latency,
          lastChecked: new Date(),
          errorCount: this.primaryStatus.errorCount + 1,
          lastError: errorMessage,
        };
      } else {
        const status = this.replicas.get(url);
        if (status) {
          const newErrorCount = status.errorCount + 1;
          const isHealthy = newErrorCount < this.options.maxFailures;

          this.replicas.set(url, {
            ...status,
            healthy: isHealthy,
            latency,
            lastChecked: new Date(),
            errorCount: newErrorCount,
            lastError: errorMessage,
          });

          // If marked unhealthy, start recovery checks
          if (!isHealthy && status.healthy) {
            this.startRecoveryCheck(url);
          }
        }
      }

      logger.warn({
        message: `${type} health check failed`,
        url: this.maskUrl(url),
        error: errorMessage,
      });
    }
  }

  /**
   * Start recovery check for unhealthy replica
   */
  private startRecoveryCheck(url: string): void {
    // Clear existing recovery check
    const existing = this.recoveryIntervals.get(url);
    if (existing) {
      clearTimeout(existing);
    }

    // Start new recovery check
    const timeout = setTimeout(async () => {
      await this.checkDatabase(url, 'replica');
      this.recoveryIntervals.delete(url);
    }, this.options.recoveryCheckInterval * 1000);

    this.recoveryIntervals.set(url, timeout);

    logger.info({
      message: 'Recovery check scheduled',
      url: this.maskUrl(url),
      interval: this.options.recoveryCheckInterval,
    });
  }

  /**
   * Get healthy replicas
   */
  getHealthyReplicas(): string[] {
    const healthy: string[] = [];
    
    for (const [url, status] of this.replicas) {
      if (status.healthy) {
        healthy.push(url);
      }
    }

    return healthy;
  }

  /**
   * Get all replica statuses
   */
  getAllStatuses(): {
    primary: ReplicaHealthStatus | null;
    replicas: ReplicaHealthStatus[];
  } {
    return {
      primary: this.primaryStatus ? { ...this.primaryStatus } : null,
      replicas: Array.from(this.replicas.values()).map(s => ({ ...s })),
    };
  }

  /**
   * Get overall health status
   */
  getOverallHealth(): 'healthy' | 'degraded' | 'unhealthy' {
    if (!this.primaryStatus || !this.primaryStatus.healthy) {
      return 'unhealthy';
    }

    const healthyReplicaCount = this.getHealthyReplicas().length;
    const totalReplicaCount = this.replicas.size;

    if (totalReplicaCount === 0) {
      return 'healthy';
    }

    const healthRatio = healthyReplicaCount / totalReplicaCount;

    if (healthRatio >= 0.7) {
      return 'healthy';
    } else if (healthRatio >= 0.3) {
      return 'degraded';
    } else {
      return 'unhealthy';
    }
  }

  /**
   * Mask sensitive parts of URL for logging
   */
  private maskUrl(url: string): string {
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
}

/**
 * Create replica health checker
 */
export function createReplicaHealthChecker(
  options?: Partial<ReplicaHealthCheckerOptions>
): ReplicaHealthChecker {
  return new ReplicaHealthChecker(options);
}

/**
 * Get replica health checker options from environment
 */
export function getReplicaHealthCheckerOptionsFromEnv(): ReplicaHealthCheckerOptions {
  return {
    checkInterval: parseInt(process.env.REPLICA_HEALTH_CHECK_INTERVAL || '30', 10),
    maxLatency: parseInt(process.env.REPLICA_MAX_LATENCY || '1000', 10),
    maxFailures: parseInt(process.env.REPLICA_MAX_FAILURES || '3', 10),
    recoveryCheckInterval: parseInt(process.env.REPLICA_RECOVERY_CHECK_INTERVAL || '60', 10),
  };
}
