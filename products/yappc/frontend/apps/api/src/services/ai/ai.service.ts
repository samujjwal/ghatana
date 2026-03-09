/**
 * AI Service for managing AI-powered features.
 *
 * Provides high-level AI operations including insights, predictions,
 * anomaly detection, and copilot interactions.
 *
 * @module services/ai
 * @doc.type class
 * @doc.purpose AI service layer
 * @doc.layer product
 * @doc.pattern Service
 */

import { type PrismaClient, Prisma } from '../../database/client';

// Import model types from Prisma namespace
type AIInsight = Prisma.AIInsightGetPayload<{}>;
type Prediction = Prisma.PredictionGetPayload<{}>;
type AnomalyAlert = Prisma.AnomalyAlertGetPayload<{}>;
type CopilotSession = Prisma.CopilotSessionGetPayload<{}>;
type AIMetric = Prisma.AIMetricGetPayload<{}>;

/**
 * AI Service Configuration
 */
export interface AIServiceConfig {
  prisma: PrismaClient;
  javaBackendUrl: string;
  enableCaching: boolean;
  cacheTTL: number;
}

/**
 * Insight Filter Options
 */
export interface InsightFilter {
  type?: string[];
  category?: string[];
  severity?: string[];
  itemId?: string;
  limit?: number;
  offset?: number;
}

/**
 * Prediction Filter Options
 */
export interface PredictionFilter {
  type?: string[];
  phaseId?: string;
  minProbability?: number;
  limit?: number;
  offset?: number;
}

/**
 * Anomaly Filter Options
 */
export interface AnomalyFilter {
  type?: string[];
  severity?: string[];
  acknowledged?: boolean;
  limit?: number;
  offset?: number;
}

/**
 * Copilot Message Input
 */
export interface CopilotMessageInput {
  sessionId: string;
  userId: string;
  message: string;
  context?: Record<string, unknown>;
}

/**
 * AI Service implementation
 */
export class AIService {
  private prisma: PrismaClient;
  private javaBackendUrl: string;
  private enableCaching: boolean;
  private cacheTTL: number;
  private cache: Map<string, { data: unknown; expiry: number }>;

  constructor(config: AIServiceConfig) {
    this.prisma = config.prisma;
    this.javaBackendUrl = config.javaBackendUrl;
    this.enableCaching = config.enableCaching;
    this.cacheTTL = config.cacheTTL;
    this.cache = new Map();
  }

  /**
   * Get AI insights with filtering
   */
  async getInsights(filter: InsightFilter = {}): Promise<AIInsight[]> {
    const cacheKey = `insights:${JSON.stringify(filter)}`;

    if (this.enableCaching) {
      const cached = this.getFromCache(cacheKey);
      if (cached) return cached as AIInsight[];
    }

    const where: Record<string, unknown> = {};

    if (filter.type?.length) {
      where.type = { in: filter.type };
    }
    if (filter.category?.length) {
      where.category = { in: filter.category };
    }
    if (filter.severity?.length) {
      where.severity = { in: filter.severity };
    }
    if (filter.itemId) {
      where.itemId = filter.itemId;
    }

    // Only get non-expired insights
    where.OR = [{ expiresAt: null }, { expiresAt: { gt: new Date() } }];

    const insights = await this.prisma.aIInsight.findMany({
      where,
      take: filter.limit || 50,
      skip: filter.offset || 0,
      orderBy: { createdAt: 'desc' },
    });

    if (this.enableCaching) {
      this.setCache(cacheKey, insights);
    }

    return insights;
  }

  /**
   * Get predictions with filtering
   */
  async getPredictions(filter: PredictionFilter = {}): Promise<Prediction[]> {
    const cacheKey = `predictions:${JSON.stringify(filter)}`;

    if (this.enableCaching) {
      const cached = this.getFromCache(cacheKey);
      if (cached) return cached as Prediction[];
    }

    const where: Record<string, unknown> = {};

    if (filter.type?.length) {
      where.type = { in: filter.type };
    }
    if (filter.phaseId) {
      where.phaseId = filter.phaseId;
    }
    if (filter.minProbability) {
      where.probability = { gte: filter.minProbability };
    }

    // Only get non-expired predictions
    where.expiresAt = { gt: new Date() };

    const predictions = await this.prisma.prediction.findMany({
      where,
      take: filter.limit || 50,
      skip: filter.offset || 0,
      orderBy: { probability: 'desc' },
    });

    if (this.enableCaching) {
      this.setCache(cacheKey, predictions);
    }

    return predictions;
  }

  /**
   * Get anomaly alerts with filtering
   */
  async getAnomalies(filter: AnomalyFilter = {}): Promise<AnomalyAlert[]> {
    const cacheKey = `anomalies:${JSON.stringify(filter)}`;

    if (this.enableCaching) {
      const cached = this.getFromCache(cacheKey);
      if (cached) return cached as AnomalyAlert[];
    }

    const where: Record<string, unknown> = {};

    if (filter.type?.length) {
      where.type = { in: filter.type };
    }
    if (filter.severity?.length) {
      where.severity = { in: filter.severity };
    }
    if (filter.acknowledged !== undefined) {
      where.acknowledged = filter.acknowledged;
    }

    // Only get unresolved anomalies (unless explicitly filtering)
    if (filter.acknowledged === undefined) {
      where.resolvedAt = null;
    }

    const anomalies = await this.prisma.anomalyAlert.findMany({
      where,
      take: filter.limit || 50,
      skip: filter.offset || 0,
      orderBy: { detectedAt: 'desc' },
    });

    if (this.enableCaching) {
      this.setCache(cacheKey, anomalies);
    }

    return anomalies;
  }

  /**
   * Acknowledge an anomaly
   */
  async acknowledgeAnomaly(
    anomalyId: string,
    userId: string
  ): Promise<AnomalyAlert> {
    const anomaly = await this.prisma.anomalyAlert.update({
      where: { id: anomalyId },
      data: {
        acknowledged: true,
        acknowledgedAt: new Date(),
        acknowledgedBy: userId,
      },
    });

    // Invalidate cache
    this.invalidateCache('anomalies:');

    return anomaly;
  }

  /**
   * Send message to AI Copilot
   */
  async sendCopilotMessage(
    input: CopilotMessageInput
  ): Promise<{ sessionId: string; response: string; tokensUsed: number }> {
    // Fetch or create session
    let session = await this.prisma.copilotSession.findUnique({
      where: { id: input.sessionId },
    });

    if (!session) {
      session = await this.prisma.copilotSession.create({
        data: {
          id: input.sessionId,
          userId: input.userId,
          persona: (input.context?.persona as string) || 'developer',
          modelUsed: 'gpt-4',
          messages: [],
          context: (input.context || {}) as unknown,
        },
      });
    }

    // Call Java backend for AI processing
    const response = await this.callJavaBackend('/api/ai/copilot', {
      sessionId: input.sessionId,
      message: input.message,
      context: input.context,
      history: (session.messages as unknown[]) || [],
    });

    // Update session with new messages
    const messages = (session.messages as unknown[]) || [];
    messages.push(
      { role: 'user', content: input.message, timestamp: new Date() },
      {
        role: 'assistant',
        content: (response as unknown).message,
        timestamp: new Date(),
      }
    );

    await this.prisma.copilotSession.update({
      where: { id: input.sessionId },
      data: {
        messages: messages as unknown,
        tokensUsed: { increment: (response as unknown).tokensUsed || 0 },
        costUSD: { increment: (response as unknown).costUSD || 0 },
      },
    });

    return {
      sessionId: input.sessionId,
      response: (response as unknown).message,
      tokensUsed: (response as unknown).tokensUsed || 0,
    };
  }

  /**
   * Get copilot session
   */
  async getCopilotSession(sessionId: string): Promise<CopilotSession | null> {
    return this.prisma.copilotSession.findUnique({
      where: { id: sessionId },
    });
  }

  /**
   * Track AI metric
   */
  async trackMetric(metric: {
    agentName: string;
    model: string;
    operation: string;
    tokensUsed: number;
    latencyMs: number;
    costUSD: number;
    success: boolean;
    errorMessage?: string;
    userId?: string;
    sessionId?: string;
  }): Promise<AIMetric> {
    return this.prisma.aIMetric.create({
      data: metric,
    });
  }

  /**
   * Get AI metrics summary
   */
  async getMetricsSummary(timeRange: { start: Date; end: Date }): Promise<{
    totalRequests: number;
    successRate: number;
    totalTokens: number;
    totalCost: number;
    avgLatency: number;
  }> {
    const metrics = await this.prisma.aIMetric.findMany({
      where: {
        timestamp: {
          gte: timeRange.start,
          lte: timeRange.end,
        },
      },
    });

    const totalRequests = metrics.length;
    const successful = metrics.filter((m) => m.success).length;
    const totalTokens = metrics.reduce((sum, m) => sum + m.tokensUsed, 0);
    const totalCost = metrics.reduce((sum, m) => sum + m.costUSD, 0);
    const avgLatency =
      metrics.reduce((sum, m) => sum + m.latencyMs, 0) / totalRequests || 0;

    return {
      totalRequests,
      successRate: totalRequests > 0 ? successful / totalRequests : 0,
      totalTokens,
      totalCost,
      avgLatency,
    };
  }

  /**
   * Invalidate cache entries
   */
  private invalidateCache(prefix: string): void {
    for (const key of this.cache.keys()) {
      if (key.startsWith(prefix)) {
        this.cache.delete(key);
      }
    }
  }

  /**
   * Get from cache
   */
  private getFromCache(key: string): unknown | null {
    const entry = this.cache.get(key);
    if (!entry) return null;

    if (Date.now() > entry.expiry) {
      this.cache.delete(key);
      return null;
    }

    return entry.data;
  }

  /**
   * Set cache
   */
  private setCache(key: string, data: unknown): void {
    this.cache.set(key, {
      data,
      expiry: Date.now() + this.cacheTTL,
    });
  }

  /**
   * Call Java backend
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
      throw new Error(
        `Java backend error: ${response.status} ${response.statusText}`
      );
    }

    return response.json();
  }
}

/**
 * Create default AI service instance
 */
export function createAIService(prisma: PrismaClient): AIService {
  return new AIService({
    prisma,
    javaBackendUrl: process.env.JAVA_AI_BACKEND_URL || 'http://localhost:7003',
    enableCaching: true,
    cacheTTL: 30000, // 30 seconds
  });
}
