/**
 * CMS Routes (Migrated)
 *
 * Content Management System routes for managing learning modules.
 * Provides CRUD operations for draft and published modules.
 *
 * Migrated from api-gateway/src/routes/cms.ts
 *
 * @doc.type module
 * @doc.purpose HTTP routes for CMS module management
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance, FastifyReply } from "fastify";
import type { CMSService } from "@tutorputor/contracts/v1/services";
import { z } from "zod";
import type {
  ModuleDraftInput,
  ModuleDraftPatch,
  ModuleId,
  ModuleSummary,
  TenantId,
  UserId,
} from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../../core/http/requestContext.js";

// =============================================================================
// Types
// =============================================================================

interface CMSRouteContext {
  cmsService: CMSService;
}

const listModulesQuerySchema = z.object({
  status: z.string().trim().min(1).optional(),
  cursor: z.string().trim().min(1).optional(),
  limit: z.coerce.number().int().positive().max(200).optional(),
});

const moduleIdParamsSchema = z.object({
  moduleId: z.string().trim().min(1),
});

const createModuleBodySchema = z.object({
  title: z.string().trim().min(1),
  description: z.string().trim().min(1).optional(),
  domain: z.string().trim().min(1),
  gradeRange: z.string().trim().min(1).optional(),
  tags: z.array(z.string().trim().min(1)).optional(),
  content: z.record(z.string(), z.unknown()).optional(),
});

const updateModuleBodySchema = z.object({
  title: z.string().trim().min(1).optional(),
  description: z.string().trim().min(1).optional(),
  status: z.string().trim().min(1).optional(),
  gradeRange: z.string().trim().min(1).optional(),
  tags: z.array(z.string().trim().min(1)).optional(),
  content: z.record(z.string(), z.unknown()).optional(),
});

const generateDraftBodySchema = z.object({
  intent: z.string().trim().min(1),
});

const validationErrorResponse = (issues: z.ZodIssue[]) => ({
  error: "Invalid request",
  details: issues,
});

async function respondWithErrors<T>(
  reply: FastifyReply,
  fn: () => Promise<T>,
): Promise<void> {
  try {
    const result = await fn();
    reply.send(result);
  } catch (error) {
    if (isDomainError(error)) {
      reply.code(error.statusCode ?? 500).send({
        error: error.message,
        code: error.code,
      });
    } else {
      reply.code(500).send({
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
}

function isDomainError(
  error: unknown,
): error is { code?: string; statusCode?: number; message: string } {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    "statusCode" in error &&
    "message" in error
  );
}

// =============================================================================
// Route Registration
// =============================================================================

/**
 * Register CMS routes with Fastify.
 */
export function registerCMSRoutes(
  fastify: FastifyInstance,
  context: CMSRouteContext,
): void {
  const { cmsService } = context;
  const prefix = "/v1/cms";

  // =========================================================================
  // Module Listing & Creation
  // =========================================================================

  /**
   * List modules with optional status filter.
   * GET /api/v1/cms/modules?status=DRAFT
   */
  fastify.get<{
    Querystring: { status?: string; cursor?: string; limit?: number };
  }>(`${prefix}/modules`, async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const queryResult = listModulesQuerySchema.safeParse(req.query);
    if (!queryResult.success) {
      return reply
        .code(400)
        .send(validationErrorResponse(queryResult.error.issues));
    }

    const { status, cursor, limit } = queryResult.data;

    await respondWithErrors(reply, () =>
      cmsService.listModules({
        tenantId,
        ...(status ? { status: status as ModuleSummary["status"] } : {}),
        ...(cursor ? { cursor: cursor as ModuleId | null } : {}),
        ...(typeof limit === "number" ? { limit } : {}),
      }),
    );
  });

  /**
   * Create a new module draft.
   * POST /api/v1/cms/modules
   */
  fastify.post<{
    Body: ModuleDraftInput;
  }>(`${prefix}/modules`, async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const userId = getUserId(req) as UserId;
    requireRole(req, ["teacher", "admin", "creator"]);

    const bodyResult = createModuleBodySchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply
        .code(400)
        .send(validationErrorResponse(bodyResult.error.issues));
    }

    const input = bodyResult.data as ModuleDraftInput;
    await respondWithErrors(reply, () =>
      cmsService.createModuleDraft({
        tenantId,
        authorId: userId,
        input,
      }),
    );
  });

  // =========================================================================
  // Module Updates & Publishing
  // =========================================================================

  /**
   * Update module draft.
   * PATCH /api/v1/cms/modules/:moduleId
   */
  fastify.patch<{
    Params: { moduleId: string };
    Body: ModuleDraftPatch;
  }>(`${prefix}/modules/:moduleId`, async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const userId = getUserId(req) as UserId;
    requireRole(req, ["teacher", "admin", "creator"]);

    const paramsResult = moduleIdParamsSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply
        .code(400)
        .send(validationErrorResponse(paramsResult.error.issues));
    }

    const bodyResult = updateModuleBodySchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply
        .code(400)
        .send(validationErrorResponse(bodyResult.error.issues));
    }

    const { moduleId } = paramsResult.data;
    const patch = bodyResult.data as ModuleDraftPatch;

    await respondWithErrors(reply, () =>
      cmsService.updateModuleDraft({
        tenantId,
        moduleId: moduleId as ModuleId,
        userId,
        patch,
      }),
    );
  });

  /**
   * Publish module (make it available to learners).
   * POST /api/v1/cms/modules/:moduleId/publish
   */
  fastify.post<{
    Params: { moduleId: string };
  }>(`${prefix}/modules/:moduleId/publish`, async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const userId = getUserId(req) as UserId;
    requireRole(req, ["teacher", "admin"]);

    const paramsResult = moduleIdParamsSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply
        .code(400)
        .send(validationErrorResponse(paramsResult.error.issues));
    }

    const { moduleId } = paramsResult.data;

    await respondWithErrors(reply, () =>
      cmsService.publishModule({
        tenantId,
        moduleId: moduleId as ModuleId,
        userId,
      }),
    );
  });

  // =========================================================================
  // AI-Assisted Draft Generation
  // =========================================================================

  /**
   * Generate draft module from AI intent.
   * POST /api/v1/cms/modules/generate
   */
  fastify.post<{
    Body: { intent: string };
  }>(`${prefix}/modules/generate`, async (req, reply) => {
    const tenantId = getTenantId(req) as TenantId;
    const userId = getUserId(req) as UserId;
    requireRole(req, ["teacher", "admin", "creator"]);

    const bodyResult = generateDraftBodySchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply
        .code(400)
        .send(validationErrorResponse(bodyResult.error.issues));
    }

    const { intent } = bodyResult.data;

    await respondWithErrors(reply, () =>
      cmsService.generateDraftFromIntent({
        tenantId,
        authorId: userId,
        intent,
      }),
    );
  });

  // =========================================================================
  // Health Check
  // =========================================================================

  fastify.get(`${prefix}/health`, async () => ({
    status: "healthy",
    service: "cms",
    timestamp: new Date().toISOString(),
  }));
}
