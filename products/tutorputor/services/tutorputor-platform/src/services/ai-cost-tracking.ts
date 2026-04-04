/**
 * AI Cost Tracking Service
 *
 * Tracks token usage, costs, and performance metrics across all AI providers.
 * Provides cost optimization recommendations and budget alerts.
 *
 * @module @tutorputor/platform/ai-cost-tracking
 */

import { EventEmitter } from "events";
import type { FastifyRequest, FastifyReply } from "fastify";
import { createLogger } from "../utils/logger.js";

const logger = createLogger("ai-cost-tracking");

interface AICostTrackingRequestBody {
  provider?: string;
  model?: string;
  inputTokens?: number;
}

interface AICostTrackingRequestUser {
  tenantId?: string;
}

export interface AICostMetrics {
  provider: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  costUsd: number;
  latencyMs: number;
  timestamp: Date;
  operation: string;
  tenantId?: string;
  success: boolean;
  errorType?: string;
}

export interface AICostConfig {
  dailyBudgetUsd: number;
  monthlyBudgetUsd: number;
  alertThresholdPercent: number;
  enabled: boolean;
}

export interface CostAlert {
  type: "daily_threshold" | "monthly_threshold" | "spike" | "error_rate";
  severity: "warning" | "critical";
  message: string;
  currentSpend: number;
  budget: number;
  timestamp: Date;
}

/**
 * AI Cost Tracker - Monitors and tracks AI usage costs
 */
export class AICostTracker extends EventEmitter {
  private metrics: AICostMetrics[] = [];
  private config: AICostConfig;
  private dailySpend: number = 0;
  private monthlySpend: number = 0;

  // Pricing per 1K tokens (approximate, updated regularly)
  private static readonly PRICING: Record<
    string,
    Record<string, { input: number; output: number }>
  > = {
    openai: {
      "gpt-4": { input: 0.03, output: 0.06 },
      "gpt-4-turbo": { input: 0.01, output: 0.03 },
      "gpt-3.5-turbo": { input: 0.0005, output: 0.0015 },
    },
    anthropic: {
      "claude-3-opus": { input: 0.015, output: 0.075 },
      "claude-3-sonnet": { input: 0.003, output: 0.015 },
      "claude-3-haiku": { input: 0.00025, output: 0.00125 },
    },
    azure: {
      "gpt-4": { input: 0.03, output: 0.06 },
      "gpt-35-turbo": { input: 0.0005, output: 0.0015 },
    },
  };

  constructor(config: Partial<AICostConfig> = {}) {
    super();
    this.config = {
      dailyBudgetUsd: config.dailyBudgetUsd || 100,
      monthlyBudgetUsd: config.monthlyBudgetUsd || 2000,
      alertThresholdPercent: config.alertThresholdPercent || 80,
      enabled: config.enabled !== false,
    };

    // Reset daily spend at midnight
    this.scheduleDailyReset();

    // Reset monthly spend on 1st of month
    this.scheduleMonthlyReset();
  }

  /**
   * Track an AI API call
   */
  track(metrics: Omit<AICostMetrics, "costUsd" | "timestamp">): AICostMetrics {
    if (!this.config.enabled) {
      return { ...metrics, costUsd: 0, timestamp: new Date() } as AICostMetrics;
    }

    const cost = this.calculateCost(
      metrics.provider,
      metrics.model,
      metrics.inputTokens,
      metrics.outputTokens,
    );

    const fullMetrics: AICostMetrics = {
      ...metrics,
      costUsd: cost,
      timestamp: new Date(),
    };

    this.metrics.push(fullMetrics);
    this.dailySpend += cost;
    this.monthlySpend += cost;

    // Emit for real-time monitoring
    this.emit("metric", fullMetrics);

    // Check budget thresholds
    this.checkBudgetAlerts();

    // Log for debugging
    logger.debug(
      {
        provider: metrics.provider,
        model: metrics.model,
        cost: cost.toFixed(4),
        operation: metrics.operation,
      },
      "AI cost tracked",
    );

    return fullMetrics;
  }

  /**
   * Calculate cost based on provider pricing
   */
  private calculateCost(
    provider: string,
    model: string,
    inputTokens: number,
    outputTokens: number,
  ): number {
    const pricing = AICostTracker.PRICING[provider.toLowerCase()];
    if (!pricing) {
      logger.warn(
        { provider },
        `Unknown provider for cost calculation: ${provider}`,
      );
      return 0;
    }

    // Find matching model (partial match for versions like gpt-4-0613)
    const modelKey = Object.keys(pricing).find((key) =>
      model.toLowerCase().includes(key),
    );
    if (!modelKey) {
      logger.warn(
        { provider, model },
        `Unknown model for cost calculation: ${model}`,
      );
      return 0;
    }

    const rates = pricing[modelKey];
    if (!rates) {
      logger.warn(
        { provider, model },
        `Missing pricing rates for model: ${model}`,
      );
      return 0;
    }
    const inputCost = (inputTokens / 1000) * rates.input;
    const outputCost = (outputTokens / 1000) * rates.output;

    return Number((inputCost + outputCost).toFixed(6));
  }

  /**
   * Check budget thresholds and emit alerts
   */
  private checkBudgetAlerts(): void {
    const dailyThreshold =
      this.config.dailyBudgetUsd * (this.config.alertThresholdPercent / 100);
    const monthlyThreshold =
      this.config.monthlyBudgetUsd * (this.config.alertThresholdPercent / 100);

    if (
      this.dailySpend >= dailyThreshold &&
      this.dailySpend < this.config.dailyBudgetUsd
    ) {
      this.emitAlert({
        type: "daily_threshold",
        severity: "warning",
        message: `Daily AI spend at ${this.config.alertThresholdPercent}% of budget`,
        currentSpend: this.dailySpend,
        budget: this.config.dailyBudgetUsd,
        timestamp: new Date(),
      });
    }

    if (this.dailySpend >= this.config.dailyBudgetUsd) {
      this.emitAlert({
        type: "daily_threshold",
        severity: "critical",
        message: "Daily AI budget exceeded",
        currentSpend: this.dailySpend,
        budget: this.config.dailyBudgetUsd,
        timestamp: new Date(),
      });
    }

    if (
      this.monthlySpend >= monthlyThreshold &&
      this.monthlySpend < this.config.monthlyBudgetUsd
    ) {
      this.emitAlert({
        type: "monthly_threshold",
        severity: "warning",
        message: `Monthly AI spend at ${this.config.alertThresholdPercent}% of budget`,
        currentSpend: this.monthlySpend,
        budget: this.config.monthlyBudgetUsd,
        timestamp: new Date(),
      });
    }
  }

  /**
   * Emit cost alert
   */
  private emitAlert(alert: CostAlert): void {
    this.emit("alert", alert);
    logger.warn({ ...alert }, "AI cost alert");
  }

  /**
   * Get cost statistics
   */
  getStats(timeWindowHours: number = 24): {
    totalCost: number;
    totalTokens: number;
    requestCount: number;
    averageLatency: number;
    successRate: number;
    topOperations: Array<{ operation: string; cost: number; count: number }>;
  } {
    const cutoff = new Date(Date.now() - timeWindowHours * 60 * 60 * 1000);
    const windowMetrics = this.metrics.filter((m) => m.timestamp > cutoff);

    const totalCost = windowMetrics.reduce(
      (sum: number, m) => sum + m.costUsd,
      0,
    );
    const totalTokens = windowMetrics.reduce(
      (sum: number, m) => sum + m.totalTokens,
      0,
    );
    const requestCount = windowMetrics.length;
    const averageLatency =
      windowMetrics.reduce((sum: number, m) => sum + m.latencyMs, 0) /
        requestCount || 0;
    const successfulRequests = windowMetrics.filter((m) => m.success).length;
    const successRate =
      requestCount > 0 ? (successfulRequests / requestCount) * 100 : 0;

    // Aggregate by operation
    const operationCosts: Record<string, { cost: number; count: number }> = {};
    windowMetrics.forEach((m) => {
      if (!operationCosts[m.operation]) {
        operationCosts[m.operation] = { cost: 0, count: 0 };
      }
      const currentOperationCost = operationCosts[m.operation];
      if (!currentOperationCost) {
        return;
      }
      currentOperationCost.cost += m.costUsd;
      currentOperationCost.count++;
    });

    const topOperations = Object.entries(operationCosts)
      .map(([operation, data]) => ({ operation, ...data }))
      .sort((a, b) => b.cost - a.cost)
      .slice(0, 5);

    return {
      totalCost: Number(totalCost.toFixed(2)),
      totalTokens,
      requestCount,
      averageLatency: Math.round(averageLatency),
      successRate: Number(successRate.toFixed(1)),
      topOperations,
    };
  }

  /**
   * Get recommendations for cost optimization
   */
  getRecommendations(): Array<{
    type: "model_downgrade" | "cache" | "batch" | "reduce_context";
    description: string;
    potentialSavings: string;
    priority: "high" | "medium" | "low";
  }> {
    const recommendations: Array<{
      type: "model_downgrade" | "cache" | "batch" | "reduce_context";
      description: string;
      potentialSavings: string;
      priority: "high" | "medium" | "low";
    }> = [];
    const stats = this.getStats(24);

    // Check for expensive operations
    const expensiveOps = stats.topOperations.filter((op) => op.cost > 5);
    if (expensiveOps.length > 0) {
      recommendations.push({
        type: "model_downgrade",
        description: `Consider using cheaper models for ${expensiveOps.length} high-cost operations`,
        potentialSavings: "~30-50% on affected operations",
        priority: "high",
      });
    }

    // Check token usage
    if (stats.totalTokens > 1000000) {
      recommendations.push({
        type: "reduce_context",
        description:
          "High token usage detected. Consider truncating or summarizing context.",
        potentialSavings: "~20-40% on token costs",
        priority: "medium",
      });
    }

    // Cache recommendation
    recommendations.push({
      type: "cache",
      description: "Enable response caching for repeated similar queries",
      potentialSavings: "~15-25% on repeated operations",
      priority: "medium",
    });

    return recommendations;
  }

  /**
   * Schedule daily spend reset
   */
  private scheduleDailyReset(): void {
    const now = new Date();
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);
    tomorrow.setHours(0, 0, 0, 0);

    const msUntilMidnight = tomorrow.getTime() - now.getTime();

    setTimeout(() => {
      this.dailySpend = 0;
      this.scheduleDailyReset();
    }, msUntilMidnight);
  }

  /**
   * Schedule monthly spend reset
   */
  private scheduleMonthlyReset(): void {
    const now = new Date();
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);

    const msUntilFirst = nextMonth.getTime() - now.getTime();

    setTimeout(() => {
      this.monthlySpend = 0;
      this.scheduleMonthlyReset();
    }, msUntilFirst);
  }

  /**
   * Export metrics for external systems (e.g., Prometheus, DataDog)
   */
  exportMetrics(): string {
    const stats = this.getStats(1);

    return `# AI Cost Metrics
# TYPE ai_requests_total counter
ai_requests_total ${stats.requestCount}

# TYPE ai_cost_usd_total counter
ai_cost_usd_total ${stats.totalCost}

# TYPE ai_tokens_total counter
ai_tokens_total ${stats.totalTokens}

# TYPE ai_latency_ms gauge
ai_latency_ms ${stats.averageLatency}

# TYPE ai_success_rate gauge
ai_success_rate ${stats.successRate}
`;
  }

  /**
   * Get current budget status
   */
  getBudgetStatus(): {
    daily: {
      spent: number;
      budget: number;
      remaining: number;
      percentUsed: number;
    };
    monthly: {
      spent: number;
      budget: number;
      remaining: number;
      percentUsed: number;
    };
  } {
    return {
      daily: {
        spent: Number(this.dailySpend.toFixed(2)),
        budget: this.config.dailyBudgetUsd,
        remaining: Number(
          (this.config.dailyBudgetUsd - this.dailySpend).toFixed(2),
        ),
        percentUsed: Number(
          ((this.dailySpend / this.config.dailyBudgetUsd) * 100).toFixed(1),
        ),
      },
      monthly: {
        spent: Number(this.monthlySpend.toFixed(2)),
        budget: this.config.monthlyBudgetUsd,
        remaining: Number(
          (this.config.monthlyBudgetUsd - this.monthlySpend).toFixed(2),
        ),
        percentUsed: Number(
          ((this.monthlySpend / this.config.monthlyBudgetUsd) * 100).toFixed(1),
        ),
      },
    };
  }
}

// Singleton instance for global tracking
export const globalAICostTracker = new AICostTracker();

// Middleware for tracking AI calls in Fastify
export function aiCostTrackingMiddleware() {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const body = request.body as AICostTrackingRequestBody | undefined;
    if (!body?.provider || !body.model) {
      return;
    }

    const startTime = Date.now();
    const rawReply = reply.raw;
    const provider = body.provider;
    const model = body.model;

    rawReply.once("finish", () => {
      const inputTokens = body.inputTokens ?? 0;
      const latency = Date.now() - startTime;
      const requestUser = request.user as AICostTrackingRequestUser | undefined;

      globalAICostTracker.track({
        provider,
        model,
        inputTokens,
        outputTokens: 0,
        totalTokens: inputTokens,
        latencyMs: latency,
        operation: request.routeOptions.url || request.url || "unknown",
        tenantId: requestUser?.tenantId,
        success: reply.statusCode < 400,
      });
    });
  };
}
