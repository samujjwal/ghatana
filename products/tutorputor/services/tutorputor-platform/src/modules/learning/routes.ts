/**
 * Learning Routes
 *
 * Exposes core learning capabilities: Dashboard and Enrollments.
 *
 * @doc.type module
 * @doc.purpose HTTP API for learning domain
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance, FastifyReply } from "fastify";
import type { HealthAwareLearningService } from "./service";
import type { HealthAwarePathwaysService } from "./pathways-service";
import type { HealthAwareAssessmentService } from "./assessment-service";
import type { HealthAwareAnalyticsService } from "./analytics-service";
import type {
  ModuleId,
  TenantId,
  UserId,
  EnrollmentId,
  AssessmentId,
  AssessmentAttemptId,
  ClassroomId,
  AssessmentItemId,
  AssessmentStatus
} from "@ghatana/tutorputor-contracts/v1/types";

// =============================================================================
// Types
// =============================================================================

interface LearningRouteContext {
  learningService: HealthAwareLearningService;
  pathwaysService: HealthAwarePathwaysService;
  assessmentService: HealthAwareAssessmentService;
  analyticsService: HealthAwareAnalyticsService;
}

// =============================================================================
// Helpers
// =============================================================================

function getTenantId(request: any): TenantId {
  // In a real app, middleware guarantees this or throws
  return request.headers["x-tenant-id"] as unknown as TenantId;
}

function getUserId(request: any): UserId {
  return request.headers["x-user-id"] as unknown as UserId;
}

function getUserRole(request: any): string {
  return request.headers["x-user-role"] as string;
}

// =============================================================================
// Route Factory
// =============================================================================

export default async function learningRoutes(
  fastify: FastifyInstance,
  options: LearningRouteContext,
) {
  const {
    learningService,
    pathwaysService,
    assessmentService,
    analyticsService,
  } = options;

  // ---------------------------------------------------------------------------
  // Dashboard
  // ---------------------------------------------------------------------------

  fastify.get(
    "/dashboard",
    {
      schema: {
        description:
          "Get learner dashboard with active enrollments and recommendations",
        tags: ["Learning"],
        response: {
          200: {
            type: "object",
            properties: {
              user: { type: "object" },
              currentEnrollments: { type: "array" },
              recommendedModules: { type: "array" },
            },
          },
        },
      },
    },
    async (request, reply: FastifyReply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const dashboard = await learningService.getDashboard(tenantId, userId);
      return reply.send(dashboard);
    },
  );

  // ---------------------------------------------------------------------------
  // Enrollments
  // ---------------------------------------------------------------------------

  fastify.post<{ Body: { moduleId: string } }>(
    "/enrollments",
    {
      schema: {
        description: "Enroll current user in a module",
        tags: ["Learning"],
        body: {
          type: "object",
          required: ["moduleId"],
          properties: {
            moduleId: { type: "string" },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const { moduleId } = request.body;

      const enrollment = await learningService.enrollInModule(
        tenantId,
        userId,
        moduleId as ModuleId,
      );
      return reply.status(201).send(enrollment);
    },
  );

  fastify.patch<{
    Params: { id: string };
    Body: { progressPercent: number; timeSpentSecondsDelta: number };
  }>(
    "/enrollments/:id/progress",
    {
      schema: {
        description: "Update progress for an enrollment",
        tags: ["Learning"],
        params: {
          type: "object",
          properties: {
            id: { type: "string" },
          },
        },
        body: {
          type: "object",
          required: ["progressPercent"],
          properties: {
            progressPercent: { type: "number", minimum: 0, maximum: 100 },
            timeSpentSecondsDelta: { type: "number", minimum: 0 },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const enrollmentId = request.params.id as EnrollmentId;
      const { progressPercent, timeSpentSecondsDelta } = request.body;

      try {
        const updated = await learningService.updateProgress({
          tenantId,
          enrollmentId,
          progressPercent,
          timeSpentSecondsDelta: timeSpentSecondsDelta ?? 0,
        });
        return reply.send(updated);
      } catch (e: any) {
        if (e.message.includes("not found")) {
          return reply.status(404).send({ message: e.message });
        }
        throw e;
      }
    },
  );

  // ---------------------------------------------------------------------------
  // Pathways
  // ---------------------------------------------------------------------------

  fastify.post<{ Body: { goal: string; constraints?: any } }>(
    "/pathways/generate",
    {
      schema: {
        description: "Generate a personalized learning path based on a goal",
        tags: ["Pathways"],
        body: {
          type: "object",
          required: ["goal"],
          properties: {
            goal: { type: "string" },
            constraints: { type: "object" },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const { goal, constraints } = request.body;
      const result = await pathwaysService.generatePathway({
        tenantId,
        userId,
        goal,
        constraints,
      });
      return reply.send(result);
    },
  );

  fastify.get(
    "/pathways/active",
    {
      schema: {
        description: "Get the current active learning path for the user",
        tags: ["Pathways"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const path = await pathwaysService.getPathwayForUser({
        tenantId,
        userId,
      });
      if (!path) {
        return reply.status(404).send({ message: "No active pathway found" });
      }
      return reply.send(path);
    },
  );

  fastify.post<{ Body: { title: string; goal: string; moduleIds: string[] } }>(
    "/pathways",
    {
      schema: {
        description: "Create a new active learning path",
        tags: ["Pathways"],
        body: {
          type: "object",
          required: ["title", "goal", "moduleIds"],
          properties: {
            title: { type: "string" },
            goal: { type: "string" },
            moduleIds: { type: "array", items: { type: "string" } },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const { title, goal, moduleIds } = request.body;
      const path = await pathwaysService.createPathway({
        tenantId,
        userId,
        title,
        goal,
        moduleIds: moduleIds as ModuleId[],
      });
      return reply.status(201).send(path);
    },
  );

  fastify.post<{ Body: { completedModuleId: string } }>(
    "/pathways/advance",
    {
      schema: {
        description:
          "Advance the current pathway by marking a module as complete",
        tags: ["Pathways"],
        body: {
          type: "object",
          required: ["completedModuleId"],
          properties: {
            completedModuleId: { type: "string" },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const { completedModuleId } = request.body;

      try {
        const updated = await pathwaysService.advancePathway({
          tenantId,
          userId,
          completedModuleId: completedModuleId as ModuleId,
        });
        return reply.send(updated);
      } catch (e: any) {
        return reply.status(400).send({ message: e.message });
      }
    },
  );

  // ---------------------------------------------------------------------------
  // Assessments
  // ---------------------------------------------------------------------------

  fastify.get<{
    Querystring: {
      moduleId?: string;
      status?: string;
      limit?: number;
      cursor?: string;
    };
  }>(
    "/assessments",
    {
      schema: {
        description: "List assessments",
        tags: ["Assessments"],
        querystring: {
          moduleId: { type: "string" },
          status: { type: "string" },
          limit: { type: "number" },
          cursor: { type: "string" },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { moduleId, status, limit, cursor } = request.query;
      const result = await assessmentService.listAssessments({
        tenantId,
        moduleId: moduleId as ModuleId,
        status: status as AssessmentStatus,
        limit,
        cursor: cursor as AssessmentId,
      });
      return reply.send(result);
    },
  );

  fastify.get<{ Params: { id: string } }>(
    "/assessments/:id",
    {
      schema: {
        description: "Get assessment details",
        tags: ["Assessments"],
        params: { id: { type: "string" } },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      try {
        const assessment = await assessmentService.getAssessment({
          tenantId,
          assessmentId: request.params.id as AssessmentId,
          userId,
        });
        return reply.send(assessment);
      } catch (e: any) {
        if (e.message.includes("not found"))
          return reply.status(404).send({ message: e.message });
        throw e;
      }
    },
  );

  fastify.post<{
    Body: {
      moduleId: string;
      count?: number;
      difficulty?: string;
      objectiveIds?: string[];
    };
  }>(
    "/assessments/generate",
    {
      schema: {
        description: "Generate assessment items using AI",
        tags: ["Assessments"],
        body: {
          type: "object",
          required: ["moduleId"],
          properties: {
            moduleId: { type: "string" },
            count: { type: "number" },
            difficulty: { type: "string" },
            objectiveIds: { type: "array", items: { type: "string" } },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      const { moduleId, count, difficulty, objectiveIds } = request.body;
      const result = await assessmentService.generateAssessmentItems({
        tenantId,
        userId,
        moduleId: moduleId as ModuleId,
        count: count ?? 5,
        difficulty: (difficulty as any) ?? "INTERMEDIATE",
        objectiveIds: objectiveIds ?? [],
      });
      return reply.send(result);
    },
  );

  fastify.post<{ Params: { id: string } }>(
    "/assessments/:id/attempt",
    {
      schema: {
        description: "Start an assessment attempt",
        tags: ["Assessments"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      try {
        const attempt = await assessmentService.startAttempt({
          tenantId,
          assessmentId: request.params.id as AssessmentId,
          userId,
        });
        return reply.status(201).send(attempt);
      } catch (e: any) {
        if (e.message.includes("not found"))
          return reply.status(404).send({ message: e.message });
        if (e.message.includes("validation"))
          return reply.status(400).send({ message: e.message });
        throw e;
      }
    },
  );

  fastify.post<{ Params: { id: string }; Body: { responses: any } }>(
    "/attempts/:id/submit",
    {
      schema: {
        description: "Submit an assessment attempt for grading",
        tags: ["Assessments"],
        body: {
          type: "object",
          required: ["responses"],
          properties: { responses: { type: "object" } },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const userId = getUserId(request);
      try {
        const attempt = await assessmentService.submitAttempt({
          tenantId,
          attemptId: request.params.id as AssessmentAttemptId,
          userId,
          responses: request.body.responses,
        });
        return reply.send(attempt);
      } catch (e: any) {
        if (e.message.includes("not found"))
          return reply.status(404).send({ message: e.message });
        throw e;
      }
    },
  );

  // ---------------------------------------------------------------------------
  // Analytics
  // ---------------------------------------------------------------------------

  fastify.post<{ Body: { event: any } }>(
    "/events",
    {
      schema: {
        description: "Record a learning event",
        tags: ["Analytics"],
        body: {
          type: "object",
          required: ["event"],
          properties: {
            event: { type: "object" }, // Schema refinement recommended in production
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      await analyticsService.recordEvent({
        tenantId,
        event: request.body.event,
      });
      return reply.status(204).send();
    },
  );

  fastify.get<{ Querystring: { moduleId?: string } }>(
    "/analytics",
    {
      schema: {
        description: "Get analytics summary",
        tags: ["Analytics"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { moduleId } = request.query;
      const summary = await analyticsService.getSummary({ tenantId, moduleId: moduleId as ModuleId });
      return reply.send(summary);
    },
  );

  fastify.get<{ Querystring: { classroomId?: string; period?: string } }>(
    "/analytics/advanced",
    {
      schema: {
        description: "Get advanced analytics with predictions",
        tags: ["Analytics"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { classroomId, period } = request.query;
      const result = await analyticsService.getAdvancedAnalytics({
        tenantId,
        classroomId: classroomId as ClassroomId,
        period: (period as any) ?? "weekly",
      });
      return reply.send(result);
    },
  );

  fastify.get<{ Querystring: { classroomId?: string; minRiskLevel?: string } }>(
    "/analytics/risk",
    {
      schema: {
        description: "Identify at-risk students",
        tags: ["Analytics"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { classroomId, minRiskLevel } = request.query;
      const result = await analyticsService.getAtRiskStudents({
        tenantId,
        classroomId: classroomId as ClassroomId,
        minRiskLevel: (minRiskLevel as any) ?? "low",
      });
      return reply.send(result);
    },
  );

  // ---------------------------------------------------------------------------
  // Health
  // ---------------------------------------------------------------------------

  fastify.get("/health", async () => {
    const learningHealth = await learningService.checkHealth();
    const pathwaysHealth = await pathwaysService.checkHealth();
    const assessmentHealth = await assessmentService.checkHealth();
    const analyticsHealth = await analyticsService.checkHealth();
    return {
      status:
        learningHealth && pathwaysHealth && assessmentHealth && analyticsHealth
          ? "ok"
          : "degraded",
      learning: learningHealth,
      pathways: pathwaysHealth,
      assessment: assessmentHealth,
      analytics: analyticsHealth,
    };
  });
}
