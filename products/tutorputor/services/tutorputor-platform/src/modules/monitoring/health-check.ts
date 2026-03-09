/**
 * @doc.type service
 * @doc.purpose Production health check system
 * @doc.layer product
 * @doc.pattern Health Check Service
 */

import { EventEmitter } from "node:events";
import { statfsSync } from "node:fs";
import { availableParallelism, cpus, loadavg } from "node:os";

export interface HealthSummary {
  total: number;
  passed: number;
  failed: number;
  warnings: number;
  critical: number;
}

export interface HealthCheckResult {
  status: "healthy" | "unhealthy" | "degraded";
  timestamp: number;
  checks: HealthCheck[];
  summary: HealthSummary;
  duration: number;
}

export interface HealthCheck {
  name: string;
  status: "pass" | "fail" | "warning";
  message: string;
  details?: Record<string, unknown>;
  duration: number;
  timestamp: number;
}

export interface HealthCheckConfig {
  name: string;
  timeout: number;
  critical: boolean;
  enabled: boolean;
  check: () => Promise<Omit<HealthCheck, "duration" | "timestamp">>;
}

interface FastifyLike {
  get: (path: string, handler: (request: unknown, reply: any) => Promise<void> | void) => void;
}

/**
 * Production Health Check Service.
 * Monitors system health and provides comprehensive health reporting.
 */
export class HealthCheckService extends EventEmitter {
  private checks = new Map<string, HealthCheckConfig>();
  private lastResult: HealthCheckResult | null = null;
  private monitoringInterval: NodeJS.Timeout | null = null;
  private readonly checkIntervalMs = 30_000;

  constructor() {
    super();
    this.setupDefaultChecks();
    this.startMonitoring();
  }

  registerCheck(config: HealthCheckConfig): void {
    this.checks.set(config.name, config);
  }

  unregisterCheck(name: string): void {
    this.checks.delete(name);
  }

  async runAllChecks(): Promise<HealthCheckResult> {
    const startTime = Date.now();
    const checks: HealthCheck[] = [];

    for (const config of this.checks.values()) {
      if (!config.enabled) {
        continue;
      }
      checks.push(await this.runSingleCheck(config));
    }

    const summary = this.calculateSummary(checks);
    const result: HealthCheckResult = {
      status: this.calculateOverallStatus(summary),
      timestamp: Date.now(),
      checks,
      summary,
      duration: Date.now() - startTime,
    };

    this.lastResult = result;
    this.emit("healthCheck", result);
    return result;
  }

  async runSingleCheck(config: HealthCheckConfig): Promise<HealthCheck> {
    const startedAt = Date.now();
    const timeoutPromise = new Promise<never>((_, reject) => {
      const timer = setTimeout(() => {
        clearTimeout(timer);
        reject(new Error("Health check timeout"));
      }, config.timeout);
    });

    try {
      const check = await Promise.race([config.check(), timeoutPromise]);
      return {
        ...check,
        duration: Date.now() - startedAt,
        timestamp: Date.now(),
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown health check error";
      return {
        name: config.name,
        status: "fail",
        message,
        details: { critical: config.critical },
        duration: Date.now() - startedAt,
        timestamp: Date.now(),
      };
    }
  }

  async getCheckStatus(name: string): Promise<HealthCheck | null> {
    const config = this.checks.get(name);
    if (!config || !config.enabled) {
      return null;
    }

    return this.runSingleCheck(config);
  }

  getLastResults(): HealthCheckResult | null {
    return this.lastResult;
  }

  getSummary(): HealthSummary {
    return this.lastResult?.summary ?? {
      total: 0,
      passed: 0,
      failed: 0,
      warnings: 0,
      critical: 0,
    };
  }

  startMonitoring(): void {
    if (this.monitoringInterval) {
      return;
    }

    this.monitoringInterval = setInterval(() => {
      void this.runAllChecks().catch((error) => {
        this.emit("error", error);
      });
    }, this.checkIntervalMs);
  }

  stopMonitoring(): void {
    if (!this.monitoringInterval) {
      return;
    }

    clearInterval(this.monitoringInterval);
    this.monitoringInterval = null;
  }

  async getDetailedReport(): Promise<{
    timestamp: number;
    status: "healthy" | "unhealthy" | "degraded";
    uptime: number;
    checks: HealthCheck[];
    summary: HealthSummary;
    trends: {
      cpu: number[];
      memory: number[];
      responseTime: number[];
    };
  }> {
    const result = await this.runAllChecks();

    return {
      timestamp: result.timestamp,
      status: result.status,
      uptime: process.uptime(),
      checks: result.checks,
      summary: result.summary,
      trends: {
        cpu: [],
        memory: [],
        responseTime: [],
      },
    };
  }

  createHealthEndpoint(fastify: FastifyLike): void {
    fastify.get("/health", async (_request, reply) => {
      try {
        const result = await this.runAllChecks();
        reply.code(result.status === "healthy" ? 200 : 503).send(result);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Unknown health endpoint error";
        reply.code(503).send(this.getFailurePayload(message));
      }
    });

    fastify.get("/health/detailed", async (_request, reply) => {
      try {
        const report = await this.getDetailedReport();
        reply.code(200).send(report);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Unknown health endpoint error";
        reply.code(503).send(this.getFailurePayload(message));
      }
    });

    fastify.get("/health/check/:name", async (request, reply) => {
      try {
        const name = (request as { params?: { name?: string } }).params?.name;
        if (!name) {
          reply.code(400).send({ error: "Missing check name" });
          return;
        }

        const result = await this.getCheckStatus(name);
        if (!result) {
          reply.code(404).send({ error: `Health check '${name}' not found` });
          return;
        }

        reply.code(result.status === "pass" ? 200 : 503).send(result);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Unknown health endpoint error";
        reply.code(503).send({ error: message });
      }
    });
  }

  destroy(): void {
    this.stopMonitoring();
    this.checks.clear();
    this.lastResult = null;
    this.removeAllListeners();
  }

  private setupDefaultChecks(): void {
    this.registerCheck({
      name: "database",
      timeout: 5_000,
      critical: true,
      enabled: true,
      check: async () => {
        type PrismaLike = {
          $queryRaw: (query: TemplateStringsArray, ...values: unknown[]) => Promise<unknown>;
          $disconnect: () => Promise<void>;
        };

        const prismaModule = await import("@prisma/client");
        const PrismaCtor =
          (
            prismaModule as {
              PrismaClient?: new () => PrismaLike;
              default?: { PrismaClient?: new () => PrismaLike };
            }
          ).PrismaClient ??
          (prismaModule as { default?: { PrismaClient?: new () => PrismaLike } }).default?.PrismaClient;

        if (!PrismaCtor) {
          return {
            name: "database",
            status: "warning",
            message: "PrismaClient export unavailable; database check skipped",
          };
        }

        const prisma = new PrismaCtor();

        try {
          await prisma.$queryRaw`SELECT 1`;
          return {
            name: "database",
            status: "pass",
            message: "Database connectivity verified",
          };
        } finally {
          await prisma.$disconnect();
        }
      },
    });

    this.registerCheck({
      name: "redis",
      timeout: 3_000,
      critical: false,
      enabled: true,
      check: async () => {
        const redisUrl = process.env.REDIS_URL;
        if (!redisUrl) {
          return {
            name: "redis",
            status: "warning",
            message: "REDIS_URL is not configured; redis health check skipped",
          };
        }

        const { default: Redis } = await import("ioredis");
        const client = new Redis(redisUrl, {
          maxRetriesPerRequest: 1,
          enableOfflineQueue: false,
          lazyConnect: true,
        });

        try {
          await client.connect();
          const pong = await client.ping();
          return {
            name: "redis",
            status: pong === "PONG" ? "pass" : "fail",
            message: pong === "PONG" ? "Redis connectivity verified" : "Redis ping failed",
          };
        } finally {
          await client.quit();
        }
      },
    });

    this.registerCheck({
      name: "memory",
      timeout: 1_000,
      critical: true,
      enabled: true,
      check: async () => {
        const usage = process.memoryUsage();
        const heapUsedMb = usage.heapUsed / 1024 / 1024;
        const heapTotalMb = usage.heapTotal / 1024 / 1024;
        const percent = heapTotalMb === 0 ? 0 : (heapUsedMb / heapTotalMb) * 100;

        return {
          name: "memory",
          status: percent >= 95 ? "fail" : percent >= 85 ? "warning" : "pass",
          message: `Heap usage ${heapUsedMb.toFixed(1)}MB/${heapTotalMb.toFixed(1)}MB (${percent.toFixed(1)}%)`,
          details: {
            heapUsedMb,
            heapTotalMb,
            percent,
          },
        };
      },
    });

    this.registerCheck({
      name: "cpu",
      timeout: 1_000,
      critical: false,
      enabled: true,
      check: async () => {
        const oneMinute = loadavg()[0] ?? 0;
        const parallelism = typeof availableParallelism === "function" ? availableParallelism() : cpus().length;
        const loadPercent = parallelism === 0 ? 0 : (oneMinute / parallelism) * 100;

        return {
          name: "cpu",
          status: loadPercent >= 95 ? "fail" : loadPercent >= 80 ? "warning" : "pass",
          message: `CPU load ${loadPercent.toFixed(1)}% (${oneMinute.toFixed(2)} / ${parallelism})`,
          details: {
            oneMinuteLoad: oneMinute,
            parallelism,
            loadPercent,
          },
        };
      },
    });

    this.registerCheck({
      name: "disk",
      timeout: 1_000,
      critical: false,
      enabled: true,
      check: async () => {
        const fsStats = statfsSync(process.cwd());
        const total = fsStats.blocks * fsStats.bsize;
        const free = fsStats.bavail * fsStats.bsize;
        const used = total - free;
        const percent = total === 0 ? 0 : (used / total) * 100;

        return {
          name: "disk",
          status: percent >= 95 ? "fail" : percent >= 85 ? "warning" : "pass",
          message: `Disk usage ${(used / 1024 / 1024 / 1024).toFixed(2)}GB/${(total / 1024 / 1024 / 1024).toFixed(2)}GB (${percent.toFixed(1)}%)`,
          details: {
            totalBytes: total,
            freeBytes: free,
            usedBytes: used,
            percent,
          },
        };
      },
    });
  }

  private calculateSummary(checks: HealthCheck[]): HealthSummary {
    const summary: HealthSummary = {
      total: checks.length,
      passed: 0,
      failed: 0,
      warnings: 0,
      critical: 0,
    };

    for (const check of checks) {
      if (check.status === "pass") {
        summary.passed += 1;
      } else if (check.status === "fail") {
        summary.failed += 1;
      } else {
        summary.warnings += 1;
      }

      const cfg = this.checks.get(check.name);
      if (cfg?.critical && check.status === "fail") {
        summary.critical += 1;
      }
    }

    return summary;
  }

  private calculateOverallStatus(summary: HealthSummary): "healthy" | "unhealthy" | "degraded" {
    if (summary.critical > 0 || summary.failed > 0) {
      return "unhealthy";
    }

    if (summary.warnings > 0) {
      return "degraded";
    }

    return "healthy";
  }

  private getFailurePayload(message: string): {
    status: "unhealthy";
    timestamp: number;
    checks: never[];
    summary: HealthSummary;
    duration: number;
    error: string;
  } {
    return {
      status: "unhealthy",
      timestamp: Date.now(),
      checks: [],
      summary: {
        total: 0,
        passed: 0,
        failed: 0,
        warnings: 0,
        critical: 0,
      },
      duration: 0,
      error: message,
    };
  }
}

export const healthCheckService = new HealthCheckService();
