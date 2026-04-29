/**
 * Content Generation Throughput Benchmark Service
 *
 * Performance baseline for queue processing, gRPC throughput, and LLM latency.
 *
 * @doc.type service
 * @doc.purpose Benchmark and monitor content generation throughput
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export interface BenchmarkMetrics {
  queueProcessing: {
    avgProcessingTimeMs: number;
    throughputPerSecond: number;
    queueDepth: number;
    failedJobs: number;
    successRate: number;
  };
  grpcThroughput: {
    requestsPerSecond: number;
    avgLatencyMs: number;
    p50LatencyMs: number;
    p95LatencyMs: number;
    p99LatencyMs: number;
    errorRate: number;
  };
  llmLatency: {
    avgLatencyMs: number;
    p50LatencyMs: number;
    p95LatencyMs: number;
    p99LatencyMs: number;
    tokenThroughputPerSecond: number;
    modelBreakdown: Record<string, {
      avgLatencyMs: number;
      requestCount: number;
    }>;
  };
}

export interface BenchmarkResult {
  id: string;
  tenantId: string;
  timestamp: Date;
  metrics: BenchmarkMetrics;
  baseline: BenchmarkMetrics | null;
  regressionDetected: boolean;
  regressionDetails: string[];
}

export class ContentGenerationBenchmarkService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Run benchmark for content generation throughput
   */
  async runBenchmark(tenantId: string): Promise<BenchmarkResult> {
    const queueMetrics = await this.measureQueueProcessing(tenantId);
    const grpcMetrics = await this.measureGrpcThroughput(tenantId);
    const llmMetrics = await this.measureLLMLatency(tenantId);

    const metrics: BenchmarkMetrics = {
      queueProcessing: queueMetrics,
      grpcThroughput: grpcMetrics,
      llmLatency: llmMetrics,
    };

    const baseline = await this.getBaselineMetrics(tenantId);
    const { regressionDetected, regressionDetails } = this.detectRegression(metrics, baseline);

    const result = await this.saveBenchmarkResult(tenantId, metrics, baseline, regressionDetected, regressionDetails);

    return result;
  }

  /**
   * Measure queue processing metrics
   */
  private async measureQueueProcessing(tenantId: string): Promise<BenchmarkMetrics["queueProcessing"]> {
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);

    const jobs = await this.prisma.generationJob.findMany({
      where: {
        tenantId,
        createdAt: { gte: oneHourAgo },
      },
    });

    if (jobs.length === 0) {
      return {
        avgProcessingTimeMs: 0,
        throughputPerSecond: 0,
        queueDepth: 0,
        failedJobs: 0,
        successRate: 1,
      };
    }

    const completedJobs = jobs.filter((j) => (j as any).status === "COMPLETED");
    const failedJobs = jobs.filter((j) => (j as any).status === "FAILED");

    const processingTimes = completedJobs
      .filter((j) => (j as any).completedAt)
      .map((j) => {
        const startedAt = new Date((j as any).startedAt);
        const completedAt = new Date((j as any).completedAt);
        return completedAt.getTime() - startedAt.getTime();
      });

    const avgProcessingTimeMs = processingTimes.length > 0
      ? processingTimes.reduce((sum, time) => sum + time, 0) / processingTimes.length
      : 0;

    const throughputPerSecond = completedJobs.length / 3600; // jobs per hour / 3600 seconds
    const queueDepth = jobs.filter((j) => (j as any).status === "PENDING" || (j as any).status === "RUNNING").length;
    const successRate = jobs.length > 0 ? completedJobs.length / jobs.length : 1;

    return {
      avgProcessingTimeMs,
      throughputPerSecond,
      queueDepth,
      failedJobs: failedJobs.length,
      successRate,
    };
  }

  /**
   * Measure gRPC throughput metrics
   */
  private async measureGrpcThroughput(tenantId: string): Promise<BenchmarkMetrics["grpcThroughput"]> {
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);

    // This would typically come from gRPC metrics/observability
    // For now, we'll use a placeholder implementation
    const requestsPerSecond = 10; // Placeholder
    const avgLatencyMs = 50; // Placeholder
    const p50LatencyMs = 45; // Placeholder
    const p95LatencyMs = 100; // Placeholder
    const p99LatencyMs = 200; // Placeholder
    const errorRate = 0.01; // Placeholder

    return {
      requestsPerSecond,
      avgLatencyMs,
      p50LatencyMs,
      p95LatencyMs,
      p99LatencyMs,
      errorRate,
    };
  }

  /**
   * Measure LLM latency metrics
   */
  private async measureLLMLatency(tenantId: string): Promise<BenchmarkMetrics["llmLatency"]> {
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);

    const aiLogs = await this.prisma.aIAuditLog.findMany({
      where: {
        tenantId,
        createdAt: { gte: oneHourAgo },
        success: true,
      },
    });

    if (aiLogs.length === 0) {
      return {
        avgLatencyMs: 0,
        p50LatencyMs: 0,
        p95LatencyMs: 0,
        p99LatencyMs: 0,
        tokenThroughputPerSecond: 0,
        modelBreakdown: {},
      };
    }

    const latencies = aiLogs.map((log) => log.latencyMs || 0).filter((lat) => lat > 0);

    const sortedLatencies = latencies.sort((a, b) => a - b);
    const avgLatencyMs = latencies.reduce((sum, lat) => sum + lat, 0) / latencies.length;
    const p50LatencyMs = sortedLatencies[Math.floor(sortedLatencies.length * 0.5)] || 0;
    const p95LatencyMs = sortedLatencies[Math.floor(sortedLatencies.length * 0.95)] || 0;
    const p99LatencyMs = sortedLatencies[Math.floor(sortedLatencies.length * 0.99)] || 0;

    // Calculate token throughput (placeholder - would need actual token counts)
    const tokenThroughputPerSecond = 100; // Placeholder

    // Breakdown by model
    const modelBreakdown: Record<string, { avgLatencyMs: number; requestCount: number }> = {};
    const modelGroups = new Map<string, number[]>();

    for (const log of aiLogs) {
      const modelId = log.modelId || "unknown";
      const latency = log.latencyMs || 0;
      if (!modelGroups.has(modelId)) {
        modelGroups.set(modelId, []);
      }
      modelGroups.get(modelId)!.push(latency);
    }

    for (const [modelId, latencies] of modelGroups) {
      const avgLatency = latencies.reduce((sum, lat) => sum + lat, 0) / latencies.length;
      modelBreakdown[modelId] = {
        avgLatencyMs: avgLatency,
        requestCount: latencies.length,
      };
    }

    return {
      avgLatencyMs,
      p50LatencyMs,
      p95LatencyMs,
      p99LatencyMs,
      tokenThroughputPerSecond,
      modelBreakdown,
    };
  }

  /**
   * Get baseline metrics for comparison
   */
  private async getBaselineMetrics(tenantId: string): Promise<BenchmarkMetrics | null> {
    const baseline = await this.prisma.contentGenerationBenchmark.findFirst({
      where: {
        tenantId,
        isBaseline: true,
      },
      orderBy: { createdAt: "desc" },
    });

    if (!baseline) {
      return null;
    }

    return (baseline as any).metrics as BenchmarkMetrics;
  }

  /**
   * Detect regression in metrics
   */
  private detectRegression(
    current: BenchmarkMetrics,
    baseline: BenchmarkMetrics | null,
  ): { regressionDetected: boolean; regressionDetails: string[] } {
    const regressionDetails: string[] = [];

    if (!baseline) {
      return { regressionDetected: false, regressionDetails: [] };
    }

    // Queue processing regression (more than 20% degradation)
    if (current.queueProcessing.avgProcessingTimeMs > baseline.queueProcessing.avgProcessingTimeMs * 1.2) {
      regressionDetails.push(
        `Queue processing time degraded from ${baseline.queueProcessing.avgProcessingTimeMs}ms to ${current.queueProcessing.avgProcessingTimeMs}ms`,
      );
    }

    if (current.queueProcessing.successRate < baseline.queueProcessing.successRate * 0.9) {
      regressionDetails.push(
        `Queue success rate degraded from ${(baseline.queueProcessing.successRate * 100).toFixed(1)}% to ${(current.queueProcessing.successRate * 100).toFixed(1)}%`,
      );
    }

    // gRPC throughput regression (more than 20% degradation)
    if (current.grpcThroughput.avgLatencyMs > baseline.grpcThroughput.avgLatencyMs * 1.2) {
      regressionDetails.push(
        `gRPC latency degraded from ${baseline.grpcThroughput.avgLatencyMs}ms to ${current.grpcThroughput.avgLatencyMs}ms`,
      );
    }

    if (current.grpcThroughput.errorRate > baseline.grpcThroughput.errorRate * 1.5) {
      regressionDetails.push(
        `gRPC error rate increased from ${(baseline.grpcThroughput.errorRate * 100).toFixed(2)}% to ${(current.grpcThroughput.errorRate * 100).toFixed(2)}%`,
      );
    }

    // LLM latency regression (more than 20% degradation)
    if (current.llmLatency.avgLatencyMs > baseline.llmLatency.avgLatencyMs * 1.2) {
      regressionDetails.push(
        `LLM latency degraded from ${baseline.llmLatency.avgLatencyMs}ms to ${current.llmLatency.avgLatencyMs}ms`,
      );
    }

    return {
      regressionDetected: regressionDetails.length > 0,
      regressionDetails,
    };
  }

  /**
   * Save benchmark result
   */
  private async saveBenchmarkResult(
    tenantId: string,
    metrics: BenchmarkMetrics,
    baseline: BenchmarkMetrics | null,
    regressionDetected: boolean,
    regressionDetails: string[],
  ): Promise<BenchmarkResult> {
    const result = await this.prisma.contentGenerationBenchmark.create({
      data: {
        tenantId,
        metrics: metrics as any,
        baseline: baseline as any,
        regressionDetected,
        regressionDetails,
        isBaseline: false,
        createdAt: new Date(),
      },
    });

    return {
      id: result.id,
      tenantId: result.tenantId,
      timestamp: result.createdAt,
      metrics,
      baseline,
      regressionDetected,
      regressionDetails,
    };
  }

  /**
   * Set current metrics as baseline
   */
  async setAsBaseline(tenantId: string, benchmarkId: string): Promise<void> {
    // First, remove existing baseline for this tenant
    await this.prisma.contentGenerationBenchmark.updateMany({
      where: {
        tenantId,
        isBaseline: true,
      },
      data: {
        isBaseline: false,
      },
    });

    // Set new baseline
    await this.prisma.contentGenerationBenchmark.update({
      where: {
        id: benchmarkId,
      },
      data: {
        isBaseline: true,
      },
    });
  }

  /**
   * Get benchmark history
   */
  async getBenchmarkHistory(tenantId: string, limit: number = 50): Promise<BenchmarkResult[]> {
    const results = await this.prisma.contentGenerationBenchmark.findMany({
      where: { tenantId },
      orderBy: { createdAt: "desc" },
      take: limit,
    });

    return results.map((r) => ({
      id: r.id,
      tenantId: r.tenantId,
      timestamp: r.createdAt,
      metrics: JSON.parse(r.metrics as string) as BenchmarkMetrics,
      baseline: r.baseline ? JSON.parse(r.baseline as string) as BenchmarkMetrics : null,
      regressionDetected: r.regressionDetected,
      regressionDetails: r.regressionDetails as string[],
    }));
  }

  /**
   * Get benchmark statistics
   */
  async getBenchmarkStats(tenantId: string): Promise<{
    totalBenchmarks: number;
    regressionsDetected: number;
    avgQueueProcessingTime: number;
    avgLLMLatency: number;
    avgGrpcLatency: number;
  }> {
    const results = await this.prisma.contentGenerationBenchmark.findMany({
      where: { tenantId },
    });

    if (results.length === 0) {
      return {
        totalBenchmarks: 0,
        regressionsDetected: 0,
        avgQueueProcessingTime: 0,
        avgLLMLatency: 0,
        avgGrpcLatency: 0,
      };
    }

    const regressionsDetected = results.filter((r) => r.regressionDetected).length;

    const avgQueueProcessingTime = results.reduce(
      (sum, r) => sum + (JSON.parse(r.metrics as string) as BenchmarkMetrics).queueProcessing.avgProcessingTimeMs || 0,
      0,
    ) / results.length;

    const avgLLMLatency = results.reduce(
      (sum, r) => sum + (JSON.parse(r.metrics as string) as BenchmarkMetrics).llmLatency.avgLatencyMs || 0,
      0,
    ) / results.length;

    const avgGrpcLatency = results.reduce(
      (sum, r) => sum + (JSON.parse(r.metrics as string) as BenchmarkMetrics).grpcThroughput.avgLatencyMs || 0,
      0,
    ) / results.length;

    return {
      totalBenchmarks: results.length,
      regressionsDetected,
      avgQueueProcessingTime,
      avgLLMLatency,
      avgGrpcLatency,
    };
  }
}
