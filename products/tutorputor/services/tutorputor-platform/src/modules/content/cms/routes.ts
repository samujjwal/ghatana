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
import type { CMSService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
  ModuleDraftInput,
  ModuleDraftPatch,
  ModuleId,
  ModuleSummary,
  TenantId,
  UserId,
} from "@ghatana/tutorputor-contracts/v1/types";

// =============================================================================
// Types
// =============================================================================

interface CMSRouteContext {
  cmsService: CMSService;
}

// =============================================================================
// Helper Functions
// =============================================================================

function getTenantId(request: any): TenantId {
  return (request.headers["x-tenant-id"] as string) as TenantId;
}

function getUserId(request: any): UserId {
  return (request.headers["x-user-id"] as string) as UserId;
}

function requireRole(request: any, roles: string[]): void {
  const userRole = request.headers["x-user-role"] as string;
  if (!roles.includes(userRole)) {
    throw new Error(`Insufficient permissions. Required: ${roles.join(", ")}`);
  }
}

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
    const tenantId = getTenantId(req);
    const { status, cursor, limit } = req.query;

    await respondWithErrors(reply, () =>
      cmsService.listModules({
        tenantId,
        status: status as ModuleSummary["status"] | undefined,
        cursor: cursor as ModuleId | null | undefined,
        limit,
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
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    requireRole(req, ["teacher", "admin", "creator"]);

    const input = req.body;
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
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    requireRole(req, ["teacher", "admin", "creator"]);

    const { moduleId } = req.params;
    const patch = req.body;

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
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    requireRole(req, ["teacher", "admin"]);

    const { moduleId } = req.params;

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
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    requireRole(req, ["teacher", "admin", "creator"]);

    const { intent } = req.body;

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
