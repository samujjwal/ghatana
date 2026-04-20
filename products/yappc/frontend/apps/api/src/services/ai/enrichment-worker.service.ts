/**
 * AI Enrichment Worker Service
 *
 * Provides background enrichment of Item records with AI-generated fields:
 * - aiPriorityScore: AI-assigned priority (0-100)
 * - riskScore: Risk assessment (0-100)
 * - predictedDueDate: ML-predicted completion date
 * - sentimentScore: Sentiment analysis (-1 to 1)
 *
 * Calls the Java backend AI completion service for enrichment.
 * Designed to be called manually or via a separate event mechanism.
 *
 * @module services/ai
 * @doc.type class
 * @doc.purpose AI enrichment background worker
 * @doc.layer product
 * @doc.pattern Worker
 */

import { type PrismaClient } from '../../database/client';

/**
 * Enrichment result from AI backend
 */
export interface EnrichmentResult {
  aiPriorityScore: number;
  riskScore: number;
  predictedDueDate: string | null;
  sentimentScore: number;
}

/**
 * Enrichment worker configuration
 */
export interface EnrichmentWorkerConfig {
  prisma: PrismaClient;
  javaBackendUrl: string;
  enabled: boolean;
  maxRetries: number;
  retryDelayMs: number;
  // P3-7: DLQ and alerting configuration
  dlqEnabled?: boolean;
  alertThreshold?: number; // Alert if failure rate exceeds this (0-1)
}

/**
 * Enrichment metrics
 */
export interface EnrichmentMetrics {
  totalProcessed: number;
  successful: number;
  failed: number;
  avgLatencyMs: number;
  lastProcessedAt: Date | null;
  dlqCount?: number; // P3-7: DLQ entry count
}

/**
 * DLQ entry for failed enrichment (P3-7)
 */
export interface EnrichmentDlqEntry {
  id: string;
  itemId: string;
  error: string;
  retryCount: number;
  createdAt: Date;
  resolved: boolean;
  resolvedAt?: Date;
}

/**
 * Enrichment alert (P3-7)
 */
export interface EnrichmentAlert {
  type: 'HIGH_FAILURE_RATE' | 'DLQ_THRESHOLD_EXCEEDED' | 'WORKER_DISABLED';
  message: string;
  metrics: EnrichmentMetrics;
  timestamp: Date;
}

/**
 * AI Enrichment Worker
 *
 * Background service that enriches Item records with AI-generated fields.
 * Designed to be called manually or via a separate event mechanism.
 */
export class AiEnrichmentWorker {
  private prisma: PrismaClient;
  private javaBackendUrl: string;
  private enabled: boolean;
  private maxRetries: number;
  private retryDelayMs: number;
  private dlqEnabled: boolean;
  private alertThreshold: number;
  
  private metrics: EnrichmentMetrics = {
    totalProcessed: 0,
    successful: 0,
    failed: 0,
    avgLatencyMs: 0,
    lastProcessedAt: null,
    dlqCount: 0,
  };
  
  private processingQueue: Set<string> = new Set();
  private alertCallbacks: Set<(alert: EnrichmentAlert) => void> = new Set();

  constructor(config: EnrichmentWorkerConfig) {
    this.prisma = config.prisma;
    this.javaBackendUrl = config.javaBackendUrl;
    this.enabled = config.enabled;
    this.maxRetries = config.maxRetries;
    this.retryDelayMs = config.retryDelayMs;
    this.dlqEnabled = config.dlqEnabled ?? true;
    this.alertThreshold = config.alertThreshold ?? 0.1;
  }

  /**
   * Enrich a single item with AI-generated fields
   */
  async enrichItem(itemId: string, retryCount = 0): Promise<void> {
    if (!this.enabled) {
      console.warn('[EnrichmentWorker] Worker is disabled, skipping enrichment');
      return;
    }

    // Avoid duplicate processing if already in queue
    if (this.processingQueue.has(itemId)) {
      console.debug(`[EnrichmentWorker] Item ${itemId} already in queue, skipping`);
      return;
    }

    this.processingQueue.add(itemId);
    const startTime = Date.now();
    
    try {
      // Fetch item with context for enrichment
      const item = await this.prisma.item.findUnique({
        where: { id: itemId },
      });

      if (!item) {
        console.warn(`[EnrichmentWorker] Item ${itemId} not found`);
        return;
      }

      // Prepare enrichment payload
      const payload = {
        itemId: item.id,
        title: item.title,
        description: item.description || '',
        status: item.status,
        priority: item.priority,
        type: item.type,
        estimatedHours: item.estimatedHours,
        phaseId: item.phaseId,
      };

      // Call Java backend for enrichment
      const response = await this.callJavaBackend('/api/ai/enrich', payload);
      const enrichmentResult = response as EnrichmentResult;

      // Update item with enrichment results
      await this.prisma.item.update({
        where: { id: itemId },
        data: {
          aiPriorityScore: enrichmentResult.aiPriorityScore,
          riskScore: enrichmentResult.riskScore,
          predictedDueDate: enrichmentResult.predictedDueDate 
            ? new Date(enrichmentResult.predictedDueDate) 
            : null,
          sentimentScore: enrichmentResult.sentimentScore,
        },
      });

      // Update metrics
      const latency = Date.now() - startTime;
      this.metrics.totalProcessed++;
      this.metrics.successful++;
      this.metrics.avgLatencyMs = 
        (this.metrics.avgLatencyMs * (this.metrics.successful - 1) + latency) / 
        this.metrics.successful;
      this.metrics.lastProcessedAt = new Date();

      console.log(
        `[EnrichmentWorker] Enriched item ${itemId}: ` +
        `priority=${enrichmentResult.aiPriorityScore}, ` +
        `risk=${enrichmentResult.riskScore}, ` +
        `sentiment=${enrichmentResult.sentimentScore}`
      );

    } catch (error) {
      const latency = Date.now() - startTime;
      this.metrics.totalProcessed++;
      this.metrics.failed++;
      
      console.error(
        `[EnrichmentWorker] Error enriching item ${itemId} (attempt ${retryCount + 1}):`,
        error
      );

      // Retry logic
      if (retryCount < this.maxRetries) {
        await new Promise((resolve) => setTimeout(resolve, this.retryDelayMs));
        return this.enrichItem(itemId, retryCount + 1);
      }

      // P3-7: Publish to DLQ when retries are exhausted
      if (this.dlqEnabled) {
        await this.publishToDlq(itemId, error as Error, retryCount);
      }

      // P3-7: Check for alerting conditions
      this.checkAlertConditions();
    } finally {
      this.processingQueue.delete(itemId);
    }
  }

  /**
   * Call Java backend for enrichment
   */
  private async callJavaBackend(
    endpoint: string,
    payload: unknown
  ): Promise<unknown> {
    const response = await fetch(`${this.javaBackendUrl}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(
        `Java backend error: ${response.status} ${response.statusText} - ${errorText}`
      );
    }

    const raw = await response.text();
    if (!raw) {
      throw new Error(`${endpoint} returned an empty response`);
    }

    try {
      return JSON.parse(raw);
    } catch (error) {
      throw new Error(`${endpoint} returned invalid JSON: ${error}`);
    }
  }

  /**
   * Get current enrichment metrics
   */
  getMetrics(): EnrichmentMetrics {
    return { ...this.metrics };
  }

  /**
   * Get health status
   */
  getHealthStatus(): {
    healthy: boolean;
    enabled: boolean;
    queueSize: number;
    metrics: EnrichmentMetrics;
  } {
    const failureRate = this.metrics.totalProcessed > 0 
      ? this.metrics.failed / this.metrics.totalProcessed 
      : 0;
    
    return {
      healthy: this.enabled && failureRate < 0.1,
      enabled: this.enabled,
      queueSize: this.processingQueue.size,
      metrics: this.getMetrics(),
    };
  }

  /**
   * Reset metrics
   */
  resetMetrics(): void {
    this.metrics = {
      totalProcessed: 0,
      successful: 0,
      failed: 0,
      avgLatencyMs: 0,
      lastProcessedAt: null,
      dlqCount: 0,
    };
  }

  // P3-7: Alerting methods

  /**
   * Register an alert callback
   */
  onAlert(callback: (alert: EnrichmentAlert) => void): void {
    this.alertCallbacks.add(callback);
  }

  /**
   * Remove an alert callback
   */
  offAlert(callback: (alert: EnrichmentAlert) => void): void {
    this.alertCallbacks.delete(callback);
  }

  /**
   * Check alert conditions and trigger alerts if needed
   */
  private checkAlertConditions(): void {
    const failureRate = this.metrics.totalProcessed > 0
      ? this.metrics.failed / this.metrics.totalProcessed
      : 0;

    if (failureRate > this.alertThreshold) {
      this.triggerAlert({
        type: 'HIGH_FAILURE_RATE',
        message: `Enrichment failure rate ${(failureRate * 100).toFixed(1)}% exceeds threshold ${(this.alertThreshold * 100).toFixed(1)}%`,
        metrics: this.getMetrics(),
        timestamp: new Date(),
      });
    }

    if (this.metrics.dlqCount && this.metrics.dlqCount > 100) {
      this.triggerAlert({
        type: 'DLQ_THRESHOLD_EXCEEDED',
        message: `DLQ count ${this.metrics.dlqCount} exceeds threshold of 100`,
        metrics: this.getMetrics(),
        timestamp: new Date(),
      });
    }

    if (!this.enabled && this.metrics.totalProcessed > 0) {
      this.triggerAlert({
        type: 'WORKER_DISABLED',
        message: 'Enrichment worker is disabled but has pending work',
        metrics: this.getMetrics(),
        timestamp: new Date(),
      });
    }
  }

  /**
   * Trigger alert to all registered callbacks
   */
  private triggerAlert(alert: EnrichmentAlert): void {
    console.warn(`[EnrichmentWorker] ALERT: ${alert.type} - ${alert.message}`);
    this.alertCallbacks.forEach(callback => {
      try {
        callback(alert);
      } catch (error) {
        console.error('[EnrichmentWorker] Error in alert callback:', error);
      }
    });
  }

  // P3-7: DLQ methods

  /**
   * Publish failed enrichment to DLQ
   */
  private async publishToDlq(itemId: string, error: Error, retryCount: number): Promise<void> {
    try {
      // Check if enrichment_dlq table exists, create if not
      await this.ensureDlqTable();

      await this.prisma.$executeRaw`
        INSERT INTO enrichment_dlq (item_id, error, retry_count, created_at)
        VALUES (${itemId}, ${error.message}, ${retryCount}, NOW())
        ON CONFLICT (item_id) DO UPDATE SET
          error = EXCLUDED.error,
          retry_count = EXCLUDED.retry_count + 1,
          created_at = NOW()
      `;

      this.metrics.dlqCount = (this.metrics.dlqCount ?? 0) + 1;
      console.error(`[EnrichmentWorker] Published to DLQ: itemId=${itemId}, error=${error.message}`);
    } catch (dlqError) {
      console.error('[EnrichmentWorker] Failed to publish to DLQ:', dlqError);
    }
  }

  /**
   * Ensure DLQ table exists
   */
  private async ensureDlqTable(): Promise<void> {
    try {
      await this.prisma.$executeRaw`
        CREATE TABLE IF NOT EXISTS enrichment_dlq (
          id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
          item_id TEXT NOT NULL UNIQUE,
          error TEXT NOT NULL,
          retry_count INTEGER NOT NULL DEFAULT 0,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          resolved BOOLEAN NOT NULL DEFAULT FALSE,
          resolved_at TIMESTAMPTZ
        )
      `;
    } catch (error) {
      console.error('[EnrichmentWorker] Failed to create DLQ table:', error);
    }
  }

  /**
   * Get DLQ entries
   */
  async getDlqEntries(limit = 50): Promise<EnrichmentDlqEntry[]> {
    try {
      const entries = await this.prisma.$queryRaw<EnrichmentDlqEntry[]>`
        SELECT * FROM enrichment_dlq
        WHERE resolved = false
        ORDER BY created_at DESC
        LIMIT ${limit}
      `;
      return entries;
    } catch (error) {
      console.error('[EnrichmentWorker] Failed to fetch DLQ entries:', error);
      return [];
    }
  }

  /**
   * Retry DLQ entry
   */
  async retryDlqEntry(itemId: string): Promise<boolean> {
    try {
      // Mark as resolved
      await this.prisma.$executeRaw`
        UPDATE enrichment_dlq
        SET resolved = true, resolved_at = NOW()
        WHERE item_id = ${itemId}
      `;

      this.metrics.dlqCount = Math.max(0, (this.metrics.dlqCount ?? 0) - 1);

      // Retry enrichment
      await this.enrichItem(itemId);
      return true;
    } catch (error) {
      console.error(`[EnrichmentWorker] Failed to retry DLQ entry ${itemId}:`, error);
      return false;
    }
  }
}

/**
 * Create default enrichment worker instance
 */
export function createEnrichmentWorker(prisma: PrismaClient): AiEnrichmentWorker {
  return new AiEnrichmentWorker({
    prisma,
    javaBackendUrl: process.env.JAVA_AI_BACKEND_URL || 'http://localhost:7003',
    enabled: process.env.AI_ENRICHMENT_ENABLED === 'true',
    maxRetries: parseInt(process.env.AI_ENRICHMENT_MAX_RETRIES || '3', 10),
    retryDelayMs: parseInt(process.env.AI_ENRICHMENT_RETRY_DELAY_MS || '1000', 10),
    dlqEnabled: process.env.AI_ENRICHMENT_DLQ_ENABLED !== 'false',
    alertThreshold: parseFloat(process.env.AI_ENRICHMENT_ALERT_THRESHOLD || '0.1'),
  });
}
