/**
 * Content Generation Metrics Service (Admin App)
 *
 * Fetches real metrics from the platform service API.
 * Replaces fake/random metrics with actual production data.
 *
 * @doc.type module
 * @doc.purpose Fetch real content generation metrics from platform API
 * @doc.layer product
 * @doc.pattern Service
 */

export interface ContentStats {
  totalContent: number;
  byType: {
    examples: number;
    simulations: number;
    animations: number;
    assessments: number;
    explanations: number;
  };
  byDomain: {
    physics: number;
    chemistry: number;
    mathematics: number;
    biology: number;
    computerScience: number;
  };
  byQuality: {
    auto: number;
    reviewed: number;
    manual: number;
  };
}

export interface InfrastructureStatus {
  aiModels: {
    gpt4: { status: string; requests: number; latency: number; cost: number };
    claude: { status: string; requests: number; latency: number; cost: number };
    gemini: { status: string; requests: number; latency: number; cost: number };
  };
  databases: {
    postgresql: {
      status: string;
      connections: number;
      size: string;
      performance: number;
    };
    redis: { status: string; memory: string; keys: number; hitRate: number };
    minio: {
      status: string;
      storage: string;
      objects: number;
      bandwidth: number;
    };
  };
  templates: {
    total: number;
    byDomain: Record<string, number>;
    usage: Array<{ template: string; uses: number; successRate: number }>;
  };
}

const PLATFORM_API_BASE = process.env.PLATFORM_API_URL || 'http://localhost:3001';

class ContentGenerationMetricsService {
  private metricsCache: ContentStats | null = null;
  private infrastructureCache: InfrastructureStatus | null = null;
  private cacheExpiry: number = 0;
  private readonly CACHE_TTL_MS = 30000; // 30 seconds

  /**
   * Fetch content statistics from platform API
   */
  async getContentStats(): Promise<ContentStats> {
    if (this.metricsCache && Date.now() < this.cacheExpiry) {
      return this.metricsCache;
    }

    try {
      const response = await fetch(`${PLATFORM_API_BASE}/api/metrics/content-stats`, {
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch content stats: ${response.status}`);
      }

      const stats = await response.json();
      this.metricsCache = stats;
      this.cacheExpiry = Date.now() + this.CACHE_TTL_MS;
      return stats;
    } catch (error) {
      console.error('Failed to fetch content stats:', error);
      // Return degraded state (zeros) instead of fake random values
      return this.getDegradedStats();
    }
  }

  /**
   * Fetch infrastructure status from platform API
   */
  async getInfrastructureStatus(): Promise<InfrastructureStatus> {
    if (this.infrastructureCache && Date.now() < this.cacheExpiry) {
      return this.infrastructureCache;
    }

    try {
      const response = await fetch(`${PLATFORM_API_BASE}/api/metrics/infrastructure`, {
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch infrastructure status: ${response.status}`);
      }

      const status = await response.json();
      this.infrastructureCache = status;
      this.cacheExpiry = Date.now() + this.CACHE_TTL_MS;
      return status;
    } catch (error) {
      console.error('Failed to fetch infrastructure status:', error);
      // Return degraded state instead of fake random values
      return this.getDegradedInfrastructure();
    }
  }

  /**
   * Clear cache to force refresh
   */
  clearCache(): void {
    this.metricsCache = null;
    this.infrastructureCache = null;
    this.cacheExpiry = 0;
  }

  /**
   * Get degraded stats (zeros) for error state
   */
  private getDegradedStats(): ContentStats {
    return {
      totalContent: 0,
      byType: {
        examples: 0,
        simulations: 0,
        animations: 0,
        assessments: 0,
        explanations: 0,
      },
      byDomain: {
        physics: 0,
        chemistry: 0,
        mathematics: 0,
        biology: 0,
        computerScience: 0,
      },
      byQuality: {
        auto: 0,
        reviewed: 0,
        manual: 0,
      },
    };
  }

  /**
   * Get degraded infrastructure (unknown status) for error state
   */
  private getDegradedInfrastructure(): InfrastructureStatus {
    return {
      aiModels: {
        gpt4: { status: 'unknown', requests: 0, latency: 0, cost: 0 },
        claude: { status: 'unknown', requests: 0, latency: 0, cost: 0 },
        gemini: { status: 'unknown', requests: 0, latency: 0, cost: 0 },
      },
      databases: {
        postgresql: {
          status: 'unknown',
          connections: 0,
          size: '0 GB',
          performance: 0,
        },
        redis: { status: 'unknown', memory: '0 MB', keys: 0, hitRate: 0 },
        minio: {
          status: 'unknown',
          storage: '0 GB',
          objects: 0,
          bandwidth: 0,
        },
      },
      templates: {
        total: 0,
        byDomain: {},
        usage: [],
      },
    };
  }
}

// Singleton instance
let serviceInstance: ContentGenerationMetricsService | null = null;

export function getContentGenerationMetricsService(): ContentGenerationMetricsService {
  if (!serviceInstance) {
    serviceInstance = new ContentGenerationMetricsService();
  }
  return serviceInstance;
}
