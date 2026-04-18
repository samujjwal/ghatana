/**
 * Read/Write Split Middleware
 *
 * Automatically routes read queries to read replicas and write queries to the primary database.
 * Improves performance by distributing read load across replicas.
 *
 * @doc.type middleware
 * @doc.purpose Automatic read/write query routing
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import { Prisma } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'ReadWriteSplitMiddleware' });

export interface ReadWriteSplitOptions {
  /**
   * Enable read/write split
   */
  enabled: boolean;

  /**
   * Replica datasource names (e.g., ['replica_0', 'replica_1'])
   */
  replicaDatasources: string[];

  /**
   * Strategy for selecting replica
   */
  strategy: 'round-robin' | 'random' | 'latency-based';

  /**
   * Force certain queries to primary
   */
  forcePrimaryPatterns?: string[];
}

/**
 * Detect if a query is a read query
 */
export function isReadQuery(params: Prisma.MiddlewareParams): boolean {
  const readActions = ['findMany', 'findFirst', 'findUnique', 'count', 'aggregate', 'groupBy'];
  return readActions.includes(params.action);
}

/**
 * Detect if a query should be forced to primary
 */
export function shouldForcePrimary(params: Prisma.MiddlewareParams, patterns: string[]): boolean {
  if (!patterns || patterns.length === 0) {
    return false;
  }

  const query = params.model?.toLowerCase() || '';
  
  return patterns.some(pattern => {
    const regex = new RegExp(pattern, 'i');
    return regex.test(query);
  });
}

/**
 * Round-robin replica selector
 */
export class RoundRobinSelector {
  private currentIndex = 0;

  select(replicas: string[]): string {
    const selected = replicas[this.currentIndex];
    this.currentIndex = (this.currentIndex + 1) % replicas.length;
    return selected;
  }

  reset(): void {
    this.currentIndex = 0;
  }
}

/**
 * Random replica selector
 */
export class RandomSelector {
  select(replicas: string[]): string {
    const index = Math.floor(Math.random() * replicas.length);
    return replicas[index];
  }
}

/**
 * Latency-based replica selector
 */
class LatencySelector {
  private latencies: Map<string, number> = new Map();
  private lastUpdate: number = 0;
  private updateInterval = 60000; // 1 minute

  select(replicas: string[]): string {
    const now = Date.now();
    
    // Update latencies periodically
    if (now - this.lastUpdate > this.updateInterval) {
      this.updateLatencies(replicas);
      this.lastUpdate = now;
    }

    // Select replica with lowest latency
    let selected = replicas[0];
    let lowestLatency = Infinity;

    for (const replica of replicas) {
      const latency = this.latencies.get(replica) || 1000;
      if (latency < lowestLatency) {
        lowestLatency = latency;
        selected = replica;
      }
    }

    return selected;
  }

  private updateLatencies(replicas: string[]): void {
    // In a real implementation, this would measure actual latency
    // For now, use random values for demonstration
    for (const replica of replicas) {
      this.latencies.set(replica, Math.random() * 100 + 10);
    }
  }

  recordLatency(replica: string, latency: number): void {
    this.latencies.set(replica, latency);
  }
}

/**
 * Create read/write split middleware
 */
export function createReadWriteSplitMiddleware(options: ReadWriteSplitOptions) {
  const selector = createSelector(options.strategy);
  
  logger.info({
    message: 'Read/write split middleware created',
    enabled: options.enabled,
    replicaCount: options.replicaDatasources.length,
    strategy: options.strategy,
  });

  return Prisma.defineExtension((prisma) => {
    return prisma.$use(async (params, next) => {
      // If disabled, proceed normally
      if (!options.enabled) {
        return next(params);
      }

      // If write query or forced to primary, use primary
      if (!isReadQuery(params) || shouldForcePrimary(params, options.forcePrimaryPatterns || [])) {
        return next(params);
      }

      // Select replica for read query
      const replica = selector.select(options.replicaDatasources);
      
      logger.debug({
        message: 'Routing read query to replica',
        model: params.model,
        action: params.action,
        replica,
      });

      // Execute query on replica
      const startTime = Date.now();
      
      try {
        const result = await next({
          ...params,
          datasources: { db: replica },
        });
        
        const latency = Date.now() - startTime;
        
        if (selector instanceof LatencySelector) {
          selector.recordLatency(replica, latency);
        }
        
        return result;
      } catch (error) {
        // On error, fallback to primary
        logger.warn({
          message: 'Replica query failed, falling back to primary',
          replica,
          error: error instanceof Error ? error.message : String(error),
        });
        
        return next(params);
      }
    });
  });
}

/**
 * Create selector based on strategy
 */
function createSelector(strategy: ReadWriteSplitOptions['strategy']) {
  switch (strategy) {
    case 'round-robin':
      return new RoundRobinSelector();
    case 'random':
      return new RandomSelector();
    case 'latency-based':
      return new LatencySelector();
    default:
      return new RoundRobinSelector();
  }
}

/**
 * Get read/write split options from environment
 */
export function getReadWriteSplitOptionsFromEnv(): ReadWriteSplitOptions {
  const replicaUrlsString = process.env.DATABASE_REPLICA_URLS || '';
  const replicaUrls = replicaUrlsString.split(',').map(url => url.trim()).filter(url => url);
  
  const replicaDatasources = replicaUrls.map((_, index) => `replica_${index}`);
  
  const forcePrimaryPatterns = process.env.DATABASE_FORCE_PRIMARY_PATTERNS
    ? process.env.DATABASE_FORCE_PRIMARY_PATTERNS.split(',').map(p => p.trim())
    : [];

  return {
    enabled: process.env.DATABASE_READ_SPLIT_ENABLED === 'true',
    replicaDatasources,
    strategy: (process.env.DATABASE_REPLICA_STRATEGY as ReadWriteSplitOptions['strategy']) || 'round-robin',
    forcePrimaryPatterns,
  };
}

/**
 * Get read/write split statistics
 */
export interface ReadWriteSplitStats {
  readQueries: number;
  writeQueries: number;
  replicaQueries: number;
  primaryQueries: number;
  replicaLatencies: Map<string, number>;
}

export class ReadWriteSplitStatsCollector {
  private stats: ReadWriteSplitStats = {
    readQueries: 0,
    writeQueries: 0,
    replicaQueries: 0,
    primaryQueries: 0,
    replicaLatencies: new Map(),
  };

  recordReadQuery(replica?: string, latency?: number): void {
    this.stats.readQueries++;
    
    if (replica) {
      this.stats.replicaQueries++;
      if (latency !== undefined) {
        this.stats.replicaLatencies.set(replica, latency);
      }
    } else {
      this.stats.primaryQueries++;
    }
  }

  recordWriteQuery(): void {
    this.stats.writeQueries++;
  }

  getStats(): ReadWriteSplitStats {
    return { ...this.stats };
  }

  reset(): void {
    this.stats = {
      readQueries: 0,
      writeQueries: 0,
      replicaQueries: 0,
      primaryQueries: 0,
      replicaLatencies: new Map(),
    };
  }
}

export const statsCollector = new ReadWriteSplitStatsCollector();
