/**
 * Observability Routes
 *
 * Admin endpoints for performance metrics and alert management.
 *
 * @doc.type routes
 * @doc.purpose System performance metrics and alert API endpoints
 * @doc.layer product
 * @doc.pattern REST API
 */
import type { FastifyInstance } from "fastify";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { getTenantId, getUserId, roleGuard } from "../../core/http/requestContext.js";
import { buildSensitiveOperationAuditEntry } from "../policy/resource-access-helpers.js";
import { APIMetricsService } from "./api-metrics.js";
import { AlertService, type AlertSeverity } from "./alerts.js";

const adminGuard = roleGuard(["admin", "superadmin"]);

export function registerObservabilityRoutes(
  app: FastifyInstance,
  { prisma }: { prisma: TutorPrismaClient },
): void {
  const metricsService = new APIMetricsService();
  const alertService = new AlertService(
    prisma as unknown as ConstructorParameters<typeof AlertService>[0],
  );

  // All observability endpoints are admin-only.
  app.addHook("preHandler", adminGuard);

  /**
   * GET /observability/metrics - Get current metrics snapshot
   */
  app.get("/metrics", async (_request, reply) => {
    try {
      const snapshot = metricsService.getMetricsSnapshot();
      return reply.send(snapshot);
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch metrics",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /observability/metrics/slowest - Get slowest endpoints
   */
  app.get<{
    Querystring: { limit?: string };
  }>("/metrics/slowest", async (request, reply) => {
    const limit = request.query.limit ? parseInt(request.query.limit, 10) : 10;

    try {
      const endpoints = metricsService.getSlowestEndpoints(limit);
      return reply.send({ endpoints });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch slowest endpoints",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /observability/metrics/errors - Get error-prone endpoints
   */
  app.get<{
    Querystring: { limit?: string };
  }>("/metrics/errors", async (request, reply) => {
    const limit = request.query.limit ? parseInt(request.query.limit, 10) : 10;

    try {
      const endpoints = metricsService.getErrorProneEndpoints(limit);
      return reply.send({ endpoints });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch error-prone endpoints",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /observability/metrics/record - Record a request metric (for testing/external)
   */
  app.post<{
    Body: {
      endpoint: string;
      method: string;
      statusCode: number;
      latencyMs: number;
      tenantId?: string;
    };
  }>("/metrics/record", async (request, reply) => {
    try {
      metricsService.recordRequest(request.body);
      const audit = buildSensitiveOperationAuditEntry({
        actorId: getUserId(request),
        actorTenantId: getTenantId(request),
        targetResourceType: "metrics",
        targetResourceId: request.body.endpoint,
        operation: "record_metric",
        decision: "ALLOW",
        reason: "Admin recorded a request metric",
        correlationId: request.id,
        metadata: {
          method: request.body.method,
          statusCode: request.body.statusCode,
          latencyMs: request.body.latencyMs,
        },
      });
      app.log.info({ audit }, "Sensitive operation allowed");
      return reply.code(204).send();
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to record metric",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /observability/alerts - Get active alerts
   */
  app.get<{
    Querystring: { severity?: AlertSeverity };
  }>("/alerts", async (request, reply) => {
    try {
      const alerts = alertService.getActiveAlerts(request.query.severity);
      return reply.send({ alerts });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch alerts",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /observability/alerts/rules - Get all alert rules
   */
  app.get("/alerts/rules", async (_request, reply) => {
    try {
      const rules = alertService.getAlertRules();
      return reply.send({ rules });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch alert rules",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /observability/alerts/rules - Create/update alert rule
   */
  app.post("/alerts/rules", async (request, reply) => {
    try {
      alertService.setAlertRule(request.body as Parameters<typeof alertService.setAlertRule>[0]);
      return reply.code(201).send({ success: true });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to set alert rule",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /observability/alerts/:alertId/acknowledge - Acknowledge alert
   */
  app.post<{
    Params: { alertId: string };
    Body: { acknowledgedBy: string };
  }>("/alerts/:alertId/acknowledge", async (request, reply) => {
    const { alertId } = request.params;
    const { acknowledgedBy } = request.body;

    try {
      const alert = await alertService.acknowledgeAlert(alertId, acknowledgedBy);
      if (!alert) {
        return reply.code(404).send({ error: "Alert not found" });
      }
      return reply.send(alert);
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to acknowledge alert",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /observability/alerts/:alertId/resolve - Resolve alert
   */
  app.post<{
    Params: { alertId: string };
  }>("/alerts/:alertId/resolve", async (request, reply) => {
    const { alertId } = request.params;

    try {
      const alert = await alertService.resolveAlert(alertId);
      if (!alert) {
        return reply.code(404).send({ error: "Alert not found" });
      }
      return reply.send(alert);
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to resolve alert",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /observability/alerts/history - Get alert history
   */
  app.get<{
    Querystring: { limit?: string; severity?: AlertSeverity };
  }>("/alerts/history", async (request, reply) => {
    const limit = request.query.limit ? parseInt(request.query.limit, 10) : 50;

    try {
      const history = await alertService.getAlertHistory(limit, request.query.severity);
      return reply.send({ alerts: history });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch alert history",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  app.get("/health", async () => ({
    module: "observability",
    status: "healthy",
  }));
}
