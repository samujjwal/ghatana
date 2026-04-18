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
  
  private metrics: EnrichmentMetrics = {
    totalProcessed: 0,
    successful: 0,
    failed: 0,
    avgLatencyMs: 0,
    lastProcessedAt: null,
  };
  
  private processingQueue: Set<string> = new Set();

  constructor(config: EnrichmentWorkerConfig) {
    this.prisma = config.prisma;
    this.javaBackendUrl = config.javaBackendUrl;
    this.enabled = config.enabled;
    this.maxRetries = config.maxRetries;
    this.retryDelayMs = config.retryDelayMs;
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
    };
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
  });
}
