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
import type { LearnerProfileService } from "./learner-profile-service";
import type { SessionAdaptationEngine } from "../adaptation/session-engine.js";
import type { ContentVariationService } from "../content/variation/service.js";
import { learningMetrics } from "./learning-metrics.js";
import {
  createSimulationAssessmentIntegration,
  scoreSimulationAssessmentResponse,
  summarizeSimulationAttempt,
} from "../assessment/simulation-integration/service.js";
import type {
  ModuleId,
  EnrollmentId,
  AssessmentId,
  AssessmentAttemptId,
  ClassroomId,
  AssessmentStatus,
  TenantId,
  UserId,
  Difficulty,
  RiskLevel,
  AssessmentFeedback,
  AssessmentAttempt,
  AssessmentResponse,
  PathwayConstraints,
  LearningEventInput,
} from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  getUserId,
  roleGuard,
  respondWithErrors,
} from "../../core/http/requestContext.js";
import { buildSensitiveOperationAuditEntry } from "../policy/resource-access-helpers.js";
import { writeSseEvent } from "../../core/http/sse.js";

// =============================================================================
// Types
// =============================================================================

interface LearningRouteContext {
  learningService: HealthAwareLearningService;
  pathwaysService: HealthAwarePathwaysService;
  assessmentService: HealthAwareAssessmentService;
  analyticsService: HealthAwareAnalyticsService;
  learnerProfileService: LearnerProfileService;
  contentVariationService: ContentVariationService;
  sessionAdaptationEngine: SessionAdaptationEngine;
}

// =============================================================================
// Route Factory
// =============================================================================

async function learningRoutes(
  fastify: FastifyInstance,
  options: LearningRouteContext,
) {
  const {
    learningService,
    pathwaysService,
    assessmentService,
    analyticsService,
    learnerProfileService,
    contentVariationService,
    sessionAdaptationEngine,
  } = options;
  const simulationAssessmentIntegration = createSimulationAssessmentIntegration(
    fastify.prisma,
  );

  // ---------------------------------------------------------------------------
  // Dashboard
  // ---------------------------------------------------------------------------

  fastify.get(
    "/learning/dashboard",
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const { moduleId } = request.body;

      const enrollment = await learningService.enrollInModule(
        tenantId,
        userId,
        moduleId as ModuleId,
      );
      learningMetrics.recordEnrollment(tenantId, "new");
      const enrollAudit = buildSensitiveOperationAuditEntry({
        actorId: userId,
        actorTenantId: tenantId,
        targetResourceType: "enrollment",
        targetResourceId: moduleId,
        operation: "enroll_in_module",
        decision: "ALLOW",
        reason: "User enrolled in module",
        correlationId: request.id,
        metadata: { moduleId },
      });
      fastify.log.info({ audit: enrollAudit }, "Sensitive operation allowed");
      return reply.status(201).send(enrollment);
    },
  );

  fastify.patch<{
    Params: { id: string };
    Body: { progressPercent: number; timeSpentSecondsDelta?: number };
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const enrollmentId = request.params.id as EnrollmentId;
      const { progressPercent, timeSpentSecondsDelta } = request.body;

      const snapshot = await learnerProfileService.getPersonalizationSnapshot(
        tenantId,
        userId,
      );

      await respondWithErrors(reply, async () => {
        const updated = await learningService.updateProgress({
          tenantId,
          userId,
          enrollmentId,
          progressPercent,
          timeSpentSecondsDelta: timeSpentSecondsDelta ?? 0,
          constraints: {
            preferredPacing: snapshot.preferredPacing,
            preferredSessionMinutes:
              snapshot.sessionPreferences.preferredSessionMinutes,
            adjustedDifficulty: snapshot.adjustedDifficulty,
          },
        });
        learningMetrics.recordProgressUpdate(tenantId);
        if (updated.progressPercent === 100) {
          learningMetrics.recordCompletion(tenantId);
        }
        return updated;
      });
    },
  );

  // ---------------------------------------------------------------------------
  // Learner Profile
  // ---------------------------------------------------------------------------

  fastify.get(
    "/learning/profile",
    {
      schema: {
        description: "Get or create the current learner profile",
        tags: ["Learning"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const profile = await learnerProfileService.getOrCreateProfile(
        tenantId,
        userId,
      );
      return reply.send(profile);
    },
  );

  fastify.patch<{
    Body: {
      preferredDifficulty?: "BEGINNER" | "EASY" | "MEDIUM" | "HARD" | "EXPERT";
      preferredModality?:
        | "VISUAL"
        | "AUDITORY"
        | "KINESTHETIC"
        | "READING"
        | "MIXED";
      preferredPacing?: "SELF_PACED" | "GUIDED" | "ADAPTIVE" | "INTENSIVE";
      preferredSessionMinutes?: number;
      notificationFrequency?: string;
      reason?: string;
    };
  }>(
    "/learning/profile/preferences",
    {
      schema: {
        description: "Update learner preferences with audit tracking",
        tags: ["Learning"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const updated = await learnerProfileService.updatePreferences(
        tenantId,
        userId,
        {
          ...request.body,
          changedBy: "user",
        },
      );
      return reply.send(updated);
    },
  );

  fastify.post<{
    Body: {
      conceptId: string;
      correct: boolean;
      confidence?: number;
      timeSpentSeconds?: number;
      hintsUsed?: number;
      attempts?: number;
      modalityUsed?: "VISUAL" | "AUDITORY" | "KINESTHETIC" | "READING";
      sessionStartedAt?: string;
    };
  }>(
    "/learning/profile/mastery",
    {
      schema: {
        description: "Record learner mastery evidence using Bayesian updates",
        tags: ["Learning"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const mastery = await learnerProfileService.updateMastery(
        tenantId,
        userId,
        request.body,
      );
      const masteryAudit = buildSensitiveOperationAuditEntry({
        actorId: userId,
        actorTenantId: tenantId,
        targetResourceType: "learner_mastery",
        targetResourceId: request.body.conceptId,
        operation: "record_mastery_evidence",
        decision: "ALLOW",
        reason: "Mastery evidence recorded",
        correlationId: request.id,
        metadata: { conceptId: request.body.conceptId, correct: request.body.correct },
      });
      fastify.log.info({ audit: masteryAudit }, "Sensitive operation allowed");
      return reply.status(201).send(mastery);
    },
  );

  fastify.post<{
    Body: {
      conceptId: string;
      prerequisiteId: string;
      severity?: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
      detectedBy?:
        | "ASSESSMENT"
        | "PREREQUISITE_CHECK"
        | "ADAPTIVE_ANALYSIS"
        | "LEARNER_REPORTED"
        | "AI_PREDICTION";
      evidence?: Record<string, unknown>;
    };
  }>(
    "/learning/profile/knowledge-gaps",
    {
      schema: {
        description: "Record a learner knowledge gap for personalization",
        tags: ["Learning"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const gap = await learnerProfileService.recordKnowledgeGap(
        tenantId,
        userId,
        request.body,
      );
      return reply.status(201).send(gap);
    },
  );

  fastify.get<{
    Querystring: {
      currentConceptId?: string;
      goalConceptId?: string;
      availableTimeMinutes?: string;
    };
  }>(
    "/learning/profile/recommendations",
    {
      schema: {
        description: "Get personalized learning recommendations",
        tags: ["Learning"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const recommendationContext: {
        currentConceptId?: string;
        goalConceptId?: string;
        availableTimeMinutes?: number;
      } = {};

      if (request.query.currentConceptId) {
        recommendationContext.currentConceptId = request.query.currentConceptId;
      }
      if (request.query.goalConceptId) {
        recommendationContext.goalConceptId = request.query.goalConceptId;
      }
      if (request.query.availableTimeMinutes !== undefined) {
        recommendationContext.availableTimeMinutes = Number(
          request.query.availableTimeMinutes,
        );
      }

      const recommendations = await learnerProfileService.getRecommendations(
        tenantId,
        userId,
        recommendationContext,
      );
      return reply.send({ recommendations });
    },
  );

  fastify.get<{
    Params: { learnerId: string };
    Querystring: { topic?: string };
  }>(
    "/learning/learners/:learnerId/personalization",
    {
      preHandler: [roleGuard(["admin", "superadmin", "service"])],
      schema: {
        description: "Internal personalization snapshot for AI agent consumers",
        tags: ["Learning"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const snapshot = await learnerProfileService.getPersonalizationSnapshot(
        tenantId,
        request.params.learnerId,
        request.query.topic,
      );
      return reply.send(snapshot);
    },
  );

  // ---------------------------------------------------------------------------
  // Pathways
  // ---------------------------------------------------------------------------

  fastify.post<{ Body: { goal: string; constraints?: PathwayConstraints } }>(
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const { goal, constraints } = request.body;
      const result = await pathwaysService.generatePathway({
        tenantId,
        userId,
        goal,
        ...(constraints !== undefined ? { constraints } : {}),
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const { title, goal, moduleIds } = request.body;
      const path = await pathwaysService.createPathway({
        tenantId,
        userId,
        title,
        goal,
        moduleIds: moduleIds as ModuleId[],
      });
      const pathAudit = buildSensitiveOperationAuditEntry({
        actorId: userId,
        actorTenantId: tenantId,
        targetResourceType: "learning_pathway",
        targetResourceId: title,
        operation: "create_pathway",
        decision: "ALLOW",
        reason: "Learning pathway created",
        correlationId: request.id,
        metadata: { title, moduleCount: moduleIds.length },
      });
      fastify.log.info({ audit: pathAudit }, "Sensitive operation allowed");
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const { completedModuleId } = request.body;

      try {
        const updated = await pathwaysService.advancePathway({
          tenantId,
          userId,
          completedModuleId: completedModuleId as ModuleId,
        });
        return reply.send(updated);
      } catch (e: unknown) {
        return reply
          .status(400)
          .send({ message: e instanceof Error ? e.message : String(e) });
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
          type: "object",
          properties: {
            moduleId: { type: "string" },
            status: { type: "string" },
            limit: { type: "number" },
            cursor: { type: "string" },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const { moduleId, status, limit, cursor } = request.query;
      const assessmentQuery: {
        tenantId: TenantId;
        moduleId?: ModuleId;
        status?: AssessmentStatus;
        limit?: number;
        cursor?: AssessmentId | null;
      } = { tenantId };

      if (moduleId) {
        assessmentQuery.moduleId = moduleId as ModuleId;
      }
      if (status) {
        assessmentQuery.status = status as AssessmentStatus;
      }
      if (limit !== undefined) {
        assessmentQuery.limit = limit;
      }
      if (cursor) {
        assessmentQuery.cursor = cursor as AssessmentId;
      }

      const result = await assessmentService.listAssessments(assessmentQuery);
      return reply.send(result);
    },
  );

  fastify.get<{ Params: { id: string } }>(
    "/assessments/:id",
    {
      schema: {
        description: "Get assessment details",
        tags: ["Assessments"],
        params: {
          type: "object",
          properties: {
            id: { type: "string" },
          },
        },
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      try {
        const assessment = await assessmentService.getAssessment({
          tenantId,
          assessmentId: request.params.id as AssessmentId,
          userId,
        });
        return reply.send(assessment);
      } catch (e: unknown) {
        if (e instanceof Error && e.message.includes("not found"))
          return reply
            .status(404)
            .send({ message: e instanceof Error ? e.message : String(e) });
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
      preHandler: [roleGuard(["teacher", "admin", "superadmin"])],
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const { moduleId, count, difficulty, objectiveIds } = request.body;
      await respondWithErrors(reply, async () =>
        assessmentService.generateAssessmentItems({
          tenantId,
          userId,
          moduleId: moduleId as ModuleId,
          count: count ?? 5,
          difficulty: (difficulty as Difficulty | undefined) ?? "INTERMEDIATE",
          objectiveIds: objectiveIds ?? [],
        }),
      );
    },
  );

  fastify.post<{
    Body: {
      moduleId: string;
      count?: number;
      difficulty?: "INTRO" | "INTERMEDIATE" | "ADVANCED";
      objectiveLabels?: string[];
    };
  }>(
    "/assessments/simulations/preview",
    {
      preHandler: [roleGuard(["teacher", "admin", "superadmin"])],
      schema: {
        description: "Preview simulation-backed assessment items for a module",
        tags: ["Assessments"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const items =
        await simulationAssessmentIntegration.createModuleAssessmentItems({
          tenantId,
          moduleId: request.body.moduleId,
          count: request.body.count ?? 2,
          difficulty: request.body.difficulty ?? "INTERMEDIATE",
          objectiveLabels: request.body.objectiveLabels ?? [],
        });
      return reply.send({ items, total: items.length });
    },
  );

  fastify.post<{
    Body: {
      item: {
        id: string;
        points: number;
        metadata?: Record<string, unknown>;
      };
      response: {
        type: "simulation_interaction";
        trace: {
          interactions: Array<{
            type: string;
            parameterId?: string;
            value?: string | number | boolean;
            predictedOutcome?: string;
            observedOutcome?: string;
            note?: string;
            timestampMs?: number;
          }>;
          durationMs?: number;
          summary?: string;
        };
      };
    };
  }>(
    "/assessments/simulations/score",
    {
      schema: {
        description:
          "Score a simulation interaction trace against a simulation assessment item",
        tags: ["Assessments"],
      },
    },
    async (request, reply) => {
      const scoring = scoreSimulationAssessmentResponse({
        item: request.body.item,
        response: request.body.response,
      });
      return reply.send(scoring);
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      try {
        const attempt = await assessmentService.startAttempt({
          tenantId,
          assessmentId: request.params.id as AssessmentId,
          userId,
        });
        return reply.status(201).send(attempt);
      } catch (e: unknown) {
        if (e instanceof Error && e.message.includes("not found"))
          return reply
            .status(404)
            .send({ message: e instanceof Error ? e.message : String(e) });
        if (e instanceof Error && e.message.includes("validation"))
          return reply
            .status(400)
            .send({ message: e instanceof Error ? e.message : String(e) });
        throw e;
      }
    },
  );

  fastify.post<{
    Params: { id: string };
    Body: { responses: Record<string, unknown> };
  }>(
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      try {
        const attempt = await assessmentService.submitAttempt({
          tenantId,
          attemptId: request.params.id as AssessmentAttemptId,
          userId,
          responses: request.body
            .responses as unknown as AssessmentAttempt["responses"],
        });
        return reply.send(attempt);
      } catch (e: unknown) {
        if (e instanceof Error && e.message.includes("not found"))
          return reply
            .status(404)
            .send({ message: e instanceof Error ? e.message : String(e) });
        throw e;
      }
    },
  );

  fastify.get<{ Params: { id: string } }>(
    "/attempts/:id/simulation-insights",
    {
      schema: {
        description:
          "Summarize simulation-backed responses for an assessment attempt",
        tags: ["Assessments"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const attempt = await fastify.prisma.assessmentAttempt.findFirst({
        where: { id: request.params.id, tenantId, userId },
        include: {
          assessment: {
            include: {
              items: true,
            },
          },
        },
      });

      if (!attempt) {
        return reply
          .status(404)
          .send({ message: "Attempt not found for this user." });
      }

      const responses =
        attempt.responses &&
        typeof attempt.responses === "object" &&
        !Array.isArray(attempt.responses)
          ? (attempt.responses as unknown as Record<
              string,
              AssessmentResponse | undefined
            >)
          : {};
      const feedback = Array.isArray(attempt.feedback)
        ? (attempt.feedback as unknown as AssessmentFeedback[])
        : undefined;

      const summary = summarizeSimulationAttempt({
        items: attempt.assessment.items.map(
          (item: { id: string; itemType: string; metadata?: unknown }) => ({
            id: item.id,
            type: item.itemType,
            ...(item.metadata &&
            typeof item.metadata === "object" &&
            !Array.isArray(item.metadata)
              ? { metadata: item.metadata as Record<string, unknown> }
              : {}),
          }),
        ),
        responses,
        ...(feedback ? { feedback } : {}),
      });

      return reply.send(summary);
    },
  );

  // ---------------------------------------------------------------------------
  // Analytics
  // ---------------------------------------------------------------------------

  fastify.post<{ Body: { event: Omit<LearningEventInput, "userId"> } }>(
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
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      await analyticsService.recordEvent({
        tenantId,
        event: {
          ...request.body.event,
          userId,
        },
      });
      return reply.status(204).send();
    },
  );

  // ---------------------------------------------------------------------------
  // Session Adaptation
  // ---------------------------------------------------------------------------

  fastify.post<{
    Params: { sessionId: string };
    Body: {
      assetId: string;
      eventType:
        | "ANSWER_SUBMITTED"
        | "HINT_REQUESTED"
        | "CONTENT_VIEWED"
        | "IDLE"
        | "CHECKPOINT";
      correct?: boolean;
      hintsUsed?: number;
      responseLatencyMs?: number;
      inactivityMs?: number;
      confidence?: number;
      occurredAt?: string;
    };
  }>(
    "/sessions/:sessionId/events",
    {
      schema: {
        description:
          "Process a learner session event and evaluate adaptation needs",
        tags: ["Learning", "Adaptation"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const decision = await sessionAdaptationEngine.processEvent({
        tenantId,
        userId,
        sessionId: request.params.sessionId,
        ...request.body,
      });

      if (decision.adapted) {
        const learningEvent: LearningEventInput = {
          type: "ai_tutor_message",
          userId,
          ...(request.body.occurredAt ? { timestamp: request.body.occurredAt } : {}),
          payload: {
            source: "session_adaptation",
            sessionId: request.params.sessionId,
            ...(request.body.assetId ? { assetId: request.body.assetId } : {}),
            ...(decision.trigger ? { trigger: decision.trigger } : {}),
            ...(decision.reason ? { reason: decision.reason } : {}),
            ...(decision.recommendation
              ? { recommendation: decision.recommendation }
              : {}),
            eventType: request.body.eventType,
            ...(decision.variant
              ? {
                  variant: {
                    variantId: decision.variant.variantId,
                    family: decision.variant.family,
                    key: decision.variant.key,
                    strategy: decision.variant.metadata.strategy,
                  },
                }
              : {}),
            observedSignals: decision.observedSignals,
            adapted: true,
          },
        };

        await analyticsService.recordEvent({
          tenantId,
          event: learningEvent,
        });
      }

      return reply.status(200).send(decision);
    },
  );

  fastify.get<{
    Params: { sessionId: string };
    Querystring: { assetId: string };
  }>(
    "/sessions/:sessionId/adaptation",
    {
      schema: {
        description:
          "Get the current adaptation decision for a session and asset",
        tags: ["Learning", "Adaptation"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const decision = await sessionAdaptationEngine.getCurrentAdaptation(
        tenantId,
        userId,
        request.params.sessionId,
        request.query.assetId,
      );
      if (!decision) {
        return reply.status(404).send({ message: "No adaptation available" });
      }
      return reply.send(decision);
    },
  );

  fastify.get<{
    Params: { sessionId: string };
    Querystring: { assetId: string };
  }>(
    "/sessions/:sessionId/adaptation/stream",
    {
      schema: {
        description:
          "Stream the current adaptation decision and adapted content blocks over SSE",
        tags: ["Learning", "Adaptation"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const decision = await sessionAdaptationEngine.getCurrentAdaptation(
        tenantId,
        userId,
        request.params.sessionId,
        request.query.assetId,
      );

      reply.hijack();
      reply.raw.statusCode = 200;
      reply.raw.setHeader("Content-Type", "text/event-stream");
      reply.raw.setHeader("Cache-Control", "no-cache, no-transform");
      reply.raw.setHeader("Connection", "keep-alive");

      writeSseEvent(
        reply.raw,
        "decision",
        decision ?? {
          sessionId: request.params.sessionId,
          assetId: request.query.assetId,
          adapted: false,
          reason: "No adaptation available",
          observedSignals: {
            recentEvents: 0,
            incorrectStreak: 0,
            hintRate: 0,
            rapidGuessCount: 0,
            inactivityMs: 0,
          },
          createdAt: new Date().toISOString(),
        },
      );

      if (decision?.variant) {
        for (const block of decision.variant.blocks ?? []) {
          writeSseEvent(reply.raw, "block", block);
        }
      }

      writeSseEvent(reply.raw, "done", {
        sessionId: request.params.sessionId,
        assetId: request.query.assetId,
      });
      reply.raw.end();
    },
  );

  fastify.get<{
    Params: { assetId: string };
    Querystring: { family?: "difficulty" | "modality" | "explanation" };
  }>(
    "/assets/:assetId/variations",
    {
      schema: {
        description:
          "Preview deterministic adaptive variants for a content asset",
        tags: ["Learning", "Adaptation"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const { assetId } = request.params;
      const family = request.query.family ?? "difficulty";

      switch (family) {
        case "difficulty":
          return reply.send(
            await contentVariationService.generateDifficultyVariants(
              tenantId,
              assetId,
            ),
          );
        case "modality":
          return reply.send(
            await contentVariationService.generateModalityVariants(
              tenantId,
              assetId,
            ),
          );
        case "explanation":
          return reply.send(
            await contentVariationService.generateExplanationVariants(
              tenantId,
              assetId,
            ),
          );
        default:
          return reply
            .status(400)
            .send({ message: "Unsupported variation family" });
      }
    },
  );

  fastify.get<{ Querystring: { moduleId?: string } }>(
    "/analytics",
    {
      preHandler: [roleGuard(["teacher", "admin", "superadmin"])],
      schema: {
        description: "Get analytics summary",
        tags: ["Analytics"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const { moduleId } = request.query;
      const summary = await analyticsService.getSummary({
        tenantId,
        moduleId: moduleId as ModuleId,
      });
      return reply.send(summary);
    },
  );

  fastify.get<{ Querystring: { classroomId?: string; period?: string } }>(
    "/analytics/advanced",
    {
      preHandler: [roleGuard(["teacher", "admin", "superadmin"])],
      schema: {
        description: "Get advanced analytics with predictions",
        tags: ["Analytics"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const { classroomId, period } = request.query;
      const result = await analyticsService.getAdvancedAnalytics({
        tenantId,
        classroomId: classroomId as ClassroomId,
        period:
          (period as "daily" | "weekly" | "monthly" | undefined) ?? "weekly",
      });
      return reply.send(result);
    },
  );

  fastify.get<{ Querystring: { classroomId?: string; minRiskLevel?: string } }>(
    "/analytics/risk",
    {
      preHandler: [roleGuard(["teacher", "admin", "superadmin"])],
      schema: {
        description: "Identify at-risk students",
        tags: ["Analytics"],
      },
    },
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const { classroomId, minRiskLevel } = request.query;
      const result = await analyticsService.getAtRiskStudents({
        tenantId,
        classroomId: classroomId as ClassroomId,
        minRiskLevel: (minRiskLevel as RiskLevel | undefined) ?? "low",
      });
      return reply.send(result);
    },
  );

  // ---------------------------------------------------------------------------
  // Health
  // ---------------------------------------------------------------------------

  fastify.get("/learning/health", async () => {
    const learningHealth = await learningService.checkHealth();
    const pathwaysHealth = await pathwaysService.checkHealth();
    const assessmentHealth = await assessmentService.checkHealth();
    const analyticsHealth = await analyticsService.checkHealth();
    const learnerProfileGrpc = fastify.learnerProfileGrpcRuntimeState ?? {
      enabled: false,
      status: "disabled",
    };
    return {
      status:
        learningHealth &&
        pathwaysHealth &&
        assessmentHealth &&
        analyticsHealth &&
        (!learnerProfileGrpc.enabled || learnerProfileGrpc.status === "running")
          ? "ok"
          : "degraded",
      learning: learningHealth,
      pathways: pathwaysHealth,
      assessment: assessmentHealth,
      analytics: analyticsHealth,
      learnerProfileGrpc,
    };
  });
}

export default learningRoutes;
