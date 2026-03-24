/**
 * Content Studio Routes (Migrated)
 *
 * Fastify routes for Content Studio learning experience management.
 * Provides CRUD operations, AI generation, and validation endpoints.
 *
 * Migrated from legacy standalone content studio service.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for Content Studio
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { getTenantId, getUserId } from "../../../core/http/requestContext.js";
import type {
  LearningExperience,
  LearningClaim,
  ExperienceTask,
  CreateExperienceRequest,
  // UpdateExperienceRequest,
  // GenerateClaimRequest,
  // GenerateTaskRequest,
  // RefineContentRequest,
  // AdaptGradeRequest,
  // ValidateExperienceRequest,
  // ExperienceFilter,
  // GradeLevel,
  ExperienceStatus,
  ValidationPillar,
} from "@tutorputor/contracts/v1";
import type { ContentStudioService } from "./service.js";

// =============================================================================
// Types
// =============================================================================

// Polyfill missing types
type UpdateExperienceRequest = any;
type GenerateClaimRequest = any;
type GenerateTaskRequest = any;
type RefineContentRequest = any;
type AdaptGradeRequest = any;
type ValidateExperienceRequest = any;
type ExperienceFilter = any;
type GradeLevel = any;

import type { AnimationContentIntegration } from "../animation-integration.js";

interface RouteContext {
  contentStudioService: any; // ContentStudioService;
  animationIntegration?: AnimationContentIntegration;
  prefixes?: string[];
}

// =============================================================================
// Route Registration
// =============================================================================

/**
 * Register Content Studio routes with Fastify.
 */
export function registerContentStudioRoutes(
  fastify: FastifyInstance,
  context: RouteContext,
): void {
  const { contentStudioService, animationIntegration } = context;
  const prefixes =
    context.prefixes && context.prefixes.length > 0
      ? context.prefixes
      : ["/content-studio"];

  for (const prefix of prefixes) {
    // =========================================================================
    // Experience CRUD Routes
    // =========================================================================

    // List experiences with filtering
    fastify.get<{
      Querystring: ExperienceFilter;
    }>(`${prefix}/experiences`, async (request, reply) => {
      const tenantId = getTenantId(request);

      const filter: ExperienceFilter = {
        ...(request.query as any),
        tenantId,
      };

      const result = await contentStudioService.listExperiences(filter);
      return reply.send({
        data: result.experiences,
        pagination: {
          page: filter.page || 1,
          limit: filter.limit || 20,
          total: result.total,
          totalPages: Math.ceil(result.total / (filter.limit || 20)),
        },
      });
    });

    // Get single experience by ID
    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const experience = await contentStudioService.getExperience(id);

      if (!experience) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      // Tenant isolation
      if (tenantId && experience.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      return reply.send({ data: experience });
    });

    // Create new experience
    fastify.post<{
      Body: CreateExperienceRequest;
    }>(`${prefix}/experiences`, async (request, reply) => {
      const tenantId = getTenantId(request);
      const authorId = getUserId(request);

      const experience = await contentStudioService.createExperience({
        ...request.body,
        tenantId,
        authorId,
      });

      return reply.code(201).send({ data: experience });
    });

    // Update experience
    fastify.put<{
      Params: { id: string };
      Body: UpdateExperienceRequest;
    }>(`${prefix}/experiences/:id`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const updated = await contentStudioService.updateExperience(
        id,
        request.body,
      );
      return reply.send({ data: updated });
    });

    // Delete experience
    fastify.delete<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      await contentStudioService.deleteExperience(id);
      return reply.code(204).send();
    });

    // =========================================================================
    // AI Generation Routes
    // =========================================================================

    // Generate claims for experience
    fastify.post<{
      Params: { id: string };
      Body: GenerateClaimRequest;
    }>(`${prefix}/experiences/:id/generate-claims`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const claims = await contentStudioService.generateClaims(
        id,
        request.body,
      );
      return reply.send({ data: claims });
    });

    // Generate tasks for claim
    fastify.post<{
      Params: { experienceId: string; claimId: string };
      Body: GenerateTaskRequest;
    }>(
      `${prefix}/experiences/:experienceId/claims/:claimId/generate-tasks`,
      async (request, reply) => {
        const { experienceId, claimId } = request.params;
        const tenantId = getTenantId(request);

        const existing = await contentStudioService.getExperience(experienceId);
        if (!existing) {
          return reply.code(404).send({ error: "Experience not found" });
        }

        if (tenantId && existing.tenantId !== tenantId) {
          return reply.code(403).send({ error: "Access denied" });
        }

        const tasks = await contentStudioService.generateTasks(
          experienceId,
          claimId,
          request.body,
        );
        return reply.send({ data: tasks });
      },
    );

    // Refine content with AI
    fastify.post<{
      Params: { id: string };
      Body: RefineContentRequest;
    }>(`${prefix}/experiences/:id/refine`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const refined = await contentStudioService.refineContent(
        id,
        request.body,
      );
      return reply.send({ data: refined });
    });

    // Adapt experience for different grade level
    fastify.post<{
      Params: { id: string };
      Body: AdaptGradeRequest;
    }>(`${prefix}/experiences/:id/adapt-grade`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const adapted = await contentStudioService.adaptGrade(id, request.body);
      return reply.send({ data: adapted });
    });

    // =========================================================================
    // Validation & Publishing Routes
    // =========================================================================

    // Validate experience
    fastify.post<{
      Params: { id: string };
      Body: ValidateExperienceRequest;
    }>(`${prefix}/experiences/:id/validate`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const validation = await contentStudioService.validateExperience(
        id,
        request.body,
      );
      return reply.send({ data: validation });
    });

    // Publish experience
    fastify.post<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/publish`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);
      const userId = getUserId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const published = await contentStudioService.publishExperience(
        id,
        userId,
      );
      return reply.send({ data: published });
    });

    // Content generation progress
    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/progress`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);
      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const progress = await contentStudioService.getGenerationProgress(id);
      return reply.send(progress);
    });

    // =========================================================================
    // Artifact Query Routes
    // =========================================================================

    // Comprehensive view: experience + all claim artifacts
    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/comprehensive`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const experience = await (
        fastify as any
      ).prisma.learningExperience.findFirst({
        where: { id, ...(tenantId ? { tenantId } : {}) },
        include: {
          claims: {
            include: {
              examples: true,
              simulations: { include: { simulationManifest: true } },
              animations: true,
              tasks: true,
            },
          },
        },
      });

      if (!experience) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      return reply.send({ data: experience });
    });

    // List examples for an experience (across all its claims)
    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/examples`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }
      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const examples = await (fastify as any).prisma.claimExample.findMany({
        where: { experienceId: id },
        orderBy: { createdAt: "asc" },
      });

      return reply.send({ data: examples });
    });

    // List simulations linked to an experience (across all its claims)
    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/simulations`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }
      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const simulations = await (
        fastify as any
      ).prisma.claimSimulation.findMany({
        where: { experienceId: id },
        include: { simulationManifest: true },
        orderBy: { createdAt: "asc" },
      });

      return reply.send({ data: simulations });
    });

    // Link a simulation manifest to a claim within the experience
    fastify.post<{
      Params: { id: string };
      Body: {
        claimRef: string;
        simulationManifestId: string;
        interactionType?: string;
        goal?: string;
        successCriteria?: Record<string, unknown>;
        estimatedMinutes?: number;
      };
    }>(`${prefix}/experiences/:id/simulations`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);
      const {
        claimRef,
        simulationManifestId,
        interactionType,
        goal,
        successCriteria,
        estimatedMinutes,
      } = request.body;

      if (!claimRef || !simulationManifestId) {
        return reply
          .code(400)
          .send({ error: "claimRef and simulationManifestId are required" });
      }

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }
      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const claim = await (fastify as any).prisma.learningClaim.findFirst({
        where: { experienceId: id, claimRef },
        select: { claimRef: true },
      });
      if (!claim) {
        return reply
          .code(404)
          .send({ error: "Claim not found for this experience" });
      }

      const linked = await (fastify as any).prisma.claimSimulation.upsert({
        where: { experienceId_claimRef: { experienceId: id, claimRef } },
        create: {
          experienceId: id,
          claimRef,
          simulationManifestId,
          interactionType: interactionType ?? "parameter_exploration",
          goal: goal ?? "",
          successCriteria: successCriteria ?? {},
          estimatedMinutes: estimatedMinutes ?? 10,
        },
        update: {
          simulationManifestId,
          interactionType: interactionType ?? "parameter_exploration",
          goal: goal ?? "",
          successCriteria: successCriteria ?? {},
          estimatedMinutes: estimatedMinutes ?? 10,
        },
      });

      return reply.code(201).send({ data: linked });
    });

    // =========================================================================
    // Animation Routes
    // =========================================================================

    // List animations for an experience
    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/animations`, async (request, reply) => {
      if (!animationIntegration) {
        return reply
          .code(501)
          .send({ error: "Animation integration not configured" });
      }
      const { id } = request.params;
      const tenantId = getTenantId(request);

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }
      if (existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const animations = await animationIntegration.getAnimationsForContent(id);
      return reply.send({ data: animations });
    });

    // Link an animation to a specific claim within an experience
    fastify.post<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/animations`, async (request, reply) => {
      if (!animationIntegration) {
        return reply
          .code(501)
          .send({ error: "Animation integration not configured" });
      }
      const { id } = request.params;
      const tenantId = getTenantId(request);
      const body = (request.body || {}) as any;

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }
      if (existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      await animationIntegration.linkAnimationToContent({
        contentId: id,
        claimId: body.claimId,
        animationId: body.animationId,
        animationType: body.animationType ?? "demonstration",
        autoGenerated: body.autoGenerated ?? false,
        metadata: {
          concept: body.concept ?? "",
          difficulty: body.difficulty ?? "medium",
          estimatedDuration: body.estimatedDuration ?? 30,
        },
      });

      return reply.code(201).send({ status: "linked" });
    });

    // =========================================================================
    // Health Routes
    // =========================================================================

    fastify.get(`${prefix}/health`, async () => ({
      status: "healthy",
      service: "content-studio",
      timestamp: new Date().toISOString(),
    }));

    // =========================================================================
    // AI & Authoring Helper Routes
    // =========================================================================

    fastify.post(`${prefix}/ai/analyze-intent`, async (request, reply) => {
      const body = (request.body || {}) as any;
      return reply.send({
        data: {
          summary: body.intent || body.prompt || "",
          recommendedDomain: body.domain || "TECH",
          recommendedGradeRange: body.gradeRange || "grade_6_8",
          confidence: 0.7,
        },
      });
    });

    fastify.post(`${prefix}/ai/generate-claims`, async (request, reply) => {
      const body = (request.body || {}) as any;
      if (!body.experienceId) {
        return reply.code(400).send({ error: "experienceId is required" });
      }
      const queued = await contentStudioService.generateClaims(
        body.experienceId,
        body,
      );
      return reply.send({ data: queued });
    });

    fastify.post(`${prefix}/ai/generate-simulation`, async (request, reply) => {
      const body = (request.body || {}) as any;
      return reply.send({
        data: {
          status: "queued",
          claimRef: body.claimRef,
          message:
            "Simulation generation will run from claim content needs pipeline",
        },
      });
    });

    fastify.post(`${prefix}/ai/generate`, async (request, reply) => {
      const tenantId = getTenantId(request);
      const authorId = getUserId(request);
      const body = (request.body || {}) as any;

      const created = await contentStudioService.createExperience({
        tenantId,
        authorId,
        title: body.title || body.topic || "Untitled Experience",
        description: body.description || body.prompt || "",
        gradeRange: body.gradeRange || "grade_6_8",
        moduleId: body.moduleId,
      });

      return reply.code(created.success ? 201 : 400).send({ data: created });
    });

    fastify.post(`${prefix}/ai/chat`, async (request, reply) => {
      const body = (request.body || {}) as any;
      return reply.send({
        data: {
          response:
            body.message ||
            "Provide a content goal and I can generate claims, tasks, and validation suggestions.",
        },
      });
    });

    // =========================================================================
    // Authoring Utility Routes
    // =========================================================================

    fastify.get(
      `${prefix}/experiences/search-similar`,
      async (request, reply) => {
        const tenantId = getTenantId(request);

        const list = await contentStudioService.listExperiences({
          tenantId,
          limit: 10,
          offset: 0,
        });
        return reply.send({ data: list.experiences.slice(0, 5) });
      },
    );

    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/analytics`, async (request, reply) => {
      const { id } = request.params;
      const analytics = await contentStudioService.getExperienceAnalytics(id);
      return reply.send({ data: analytics || {} });
    });

    fastify.post<{
      Params: { experienceId: string };
    }>(
      `${prefix}/review-queue/:experienceId/decision`,
      async (request, reply) => {
        const { experienceId } = request.params;
        const body = (request.body || {}) as any;

        if (body.decision === "approve") {
          const userId = getUserId(request);
          const published = await contentStudioService.publishExperience(
            experienceId,
            userId,
          );
          return reply.send({
            data: { decision: "approve", experience: published },
          });
        }

        await contentStudioService.unpublishExperience(
          experienceId,
          body.reason || "Rejected in review",
        );
        return reply.send({ data: { decision: body.decision || "reject" } });
      },
    );

    fastify.get(`${prefix}/templates`, async () => {
      return {
        data: [
          {
            id: "foundations",
            name: "Foundations",
            domain: "SCIENCE",
            gradeRange: "grade_6_8",
          },
          {
            id: "lab-guided",
            name: "Guided Lab",
            domain: "SCIENCE",
            gradeRange: "grade_9_12",
          },
          {
            id: "concept-practice",
            name: "Concept + Practice",
            domain: "MATH",
            gradeRange: "grade_6_8",
          },
        ],
      };
    });

    fastify.get(`${prefix}/templates/categories`, async () => {
      return {
        data: ["Foundational", "Inquiry", "Practice", "Assessment"],
      };
    });

    fastify.post<{
      Params: { templateId: string };
    }>(`${prefix}/templates/:templateId/apply`, async (request) => {
      const { templateId } = request.params;
      return {
        data: {
          templateId,
          title: `Template: ${templateId}`,
          description: "Template applied successfully",
        },
      };
    });

    // =========================================================================
    // Automation Rules
    // Per-experience automation rule CRUD.
    // =========================================================================

    /**
     * GET /experiences/:id/automation-rules
     * List automation rules for a learning experience.
     */
    fastify.get<{ Params: { id: string } }>(
      `${prefix}/experiences/:id/automation-rules`,
      async (request, reply) => {
        const tenantId = getTenantId(request);
        const { id: experienceId } = request.params;
        const prisma = (fastify as any).prisma;

        const rules = await prisma.automationRule.findMany({
          where: { tenantId, experienceId },
          orderBy: { createdAt: "asc" },
        });
        return reply.send({ data: rules });
      },
    );

    /**
     * POST /experiences/:id/automation-rules
     * Create a new automation rule for a learning experience.
     */
    fastify.post<{ Params: { id: string } }>(
      `${prefix}/experiences/:id/automation-rules`,
      async (request, reply) => {
        const tenantId = getTenantId(request);
        const { id: experienceId } = request.params;
        const body = request.body as any;
        const prisma = (fastify as any).prisma;

        const rule = await prisma.automationRule.create({
          data: {
            tenantId,
            experienceId,
            name: body.name,
            description: body.description ?? null,
            trigger:
              typeof body.trigger === "string"
                ? body.trigger
                : JSON.stringify(body.trigger),
            action:
              typeof body.action === "string"
                ? body.action
                : JSON.stringify(body.action),
            enabled: body.enabled ?? true,
          },
        });
        reply.code(201);
        return reply.send({ data: rule });
      },
    );

    /**
     * PUT /experiences/:id/automation-rules/:ruleId
     * Update an existing automation rule.
     */
    fastify.put<{ Params: { id: string; ruleId: string } }>(
      `${prefix}/experiences/:id/automation-rules/:ruleId`,
      async (request, reply) => {
        const tenantId = getTenantId(request);
        const { id: experienceId, ruleId } = request.params;
        const body = request.body as any;
        const prisma = (fastify as any).prisma;

        const existing = await prisma.automationRule.findFirst({
          where: { id: ruleId, tenantId, experienceId },
        });
        if (!existing) {
          return reply.code(404).send({ error: "Automation rule not found" });
        }

        const updated = await prisma.automationRule.update({
          where: { id: ruleId },
          data: {
            ...(body.name !== undefined && { name: body.name }),
            ...(body.description !== undefined && {
              description: body.description,
            }),
            ...(body.trigger !== undefined && {
              trigger:
                typeof body.trigger === "string"
                  ? body.trigger
                  : JSON.stringify(body.trigger),
            }),
            ...(body.action !== undefined && {
              action:
                typeof body.action === "string"
                  ? body.action
                  : JSON.stringify(body.action),
            }),
            ...(body.enabled !== undefined && { enabled: body.enabled }),
          },
        });
        return reply.send({ data: updated });
      },
    );

    /**
     * DELETE /experiences/:id/automation-rules/:ruleId
     * Delete an automation rule.
     */
    fastify.delete<{ Params: { id: string; ruleId: string } }>(
      `${prefix}/experiences/:id/automation-rules/:ruleId`,
      async (request, reply) => {
        const tenantId = getTenantId(request);
        const { id: experienceId, ruleId } = request.params;
        const prisma = (fastify as any).prisma;

        const existing = await prisma.automationRule.findFirst({
          where: { id: ruleId, tenantId, experienceId },
        });
        if (!existing) {
          return reply.code(404).send({ error: "Automation rule not found" });
        }

        await prisma.automationRule.delete({ where: { id: ruleId } });
        return reply.send({ success: true });
      },
    );
  }
}
