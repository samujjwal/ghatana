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
} from "@ghatana/tutorputor-contracts/v1";
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

interface RouteContext {
  contentStudioService: any; // ContentStudioService;
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
  const { contentStudioService } = context;
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
      const tenantId = request.headers["x-tenant-id"] as string;
      if (!tenantId) {
        return reply.code(400).send({ error: "Tenant ID required" });
      }

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
      const tenantId = request.headers["x-tenant-id"] as string;

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
      const tenantId = request.headers["x-tenant-id"] as string;
      const authorId = request.headers["x-user-id"] as string;

      if (!tenantId || !authorId) {
        return reply
          .code(400)
          .send({ error: "Tenant ID and User ID required" });
      }

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
      const tenantId = request.headers["x-tenant-id"] as string;

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
      const tenantId = request.headers["x-tenant-id"] as string;

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
      const tenantId = request.headers["x-tenant-id"] as string;

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const claims = await contentStudioService.generateClaims(id, request.body);
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
        const tenantId = request.headers["x-tenant-id"] as string;

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
      const tenantId = request.headers["x-tenant-id"] as string;

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const refined = await contentStudioService.refineContent(id, request.body);
      return reply.send({ data: refined });
    });

    // Adapt experience for different grade level
    fastify.post<{
      Params: { id: string };
      Body: AdaptGradeRequest;
    }>(`${prefix}/experiences/:id/adapt-grade`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = request.headers["x-tenant-id"] as string;

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
      const tenantId = request.headers["x-tenant-id"] as string;

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
      const tenantId = request.headers["x-tenant-id"] as string;
      const userId = request.headers["x-user-id"] as string;

      const existing = await contentStudioService.getExperience(id);
      if (!existing) {
        return reply.code(404).send({ error: "Experience not found" });
      }

      if (tenantId && existing.tenantId !== tenantId) {
        return reply.code(403).send({ error: "Access denied" });
      }

      const published = await contentStudioService.publishExperience(id, userId);
      return reply.send({ data: published });
    });

    // Content generation progress
    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/progress`, async (request, reply) => {
      const { id } = request.params;
      const tenantId = request.headers["x-tenant-id"] as string | undefined;
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
      const queued = await contentStudioService.generateClaims(body.experienceId, body);
      return reply.send({ data: queued });
    });

    fastify.post(`${prefix}/ai/generate-simulation`, async (request, reply) => {
      const body = (request.body || {}) as any;
      return reply.send({
        data: {
          status: "queued",
          claimRef: body.claimRef,
          message: "Simulation generation will run from claim content needs pipeline",
        },
      });
    });

    fastify.post(`${prefix}/ai/generate`, async (request, reply) => {
      const tenantId = request.headers["x-tenant-id"] as string;
      const authorId = request.headers["x-user-id"] as string;
      const body = (request.body || {}) as any;

      if (!tenantId || !authorId) {
        return reply.code(400).send({ error: "Tenant ID and User ID required" });
      }

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

    fastify.get(`${prefix}/experiences/search-similar`, async (request, reply) => {
      const tenantId = request.headers["x-tenant-id"] as string;
      if (!tenantId) {
        return reply.code(400).send({ error: "Tenant ID required" });
      }

      const list = await contentStudioService.listExperiences({
        tenantId,
        limit: 10,
        offset: 0,
      });
      return reply.send({ data: list.experiences.slice(0, 5) });
    });

    fastify.get<{
      Params: { id: string };
    }>(`${prefix}/experiences/:id/analytics`, async (request, reply) => {
      const { id } = request.params;
      const analytics = await contentStudioService.getExperienceAnalytics(id);
      return reply.send({ data: analytics || {} });
    });

    fastify.post<{
      Params: { experienceId: string };
    }>(`${prefix}/review-queue/:experienceId/decision`, async (request, reply) => {
      const { experienceId } = request.params;
      const body = (request.body || {}) as any;

      if (body.decision === "approve") {
        const userId = (request.headers["x-user-id"] as string) || "reviewer";
        const published = await contentStudioService.publishExperience(experienceId, userId);
        return reply.send({ data: { decision: "approve", experience: published } });
      }

      await contentStudioService.unpublishExperience(experienceId, body.reason || "Rejected in review");
      return reply.send({ data: { decision: body.decision || "reject" } });
    });

    fastify.get(`${prefix}/templates`, async () => {
      return {
        data: [
          { id: "foundations", name: "Foundations", domain: "SCIENCE", gradeRange: "grade_6_8" },
          { id: "lab-guided", name: "Guided Lab", domain: "SCIENCE", gradeRange: "grade_9_12" },
          { id: "concept-practice", name: "Concept + Practice", domain: "MATH", gradeRange: "grade_6_8" },
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
  }
}
