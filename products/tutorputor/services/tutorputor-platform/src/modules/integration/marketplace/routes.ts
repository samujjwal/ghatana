import type { FastifyPluginAsync } from "fastify";
import type {
  MarketplaceListing,
  ModuleId,
  MarketplaceListingId,
  SimulationTemplateDifficulty,
  TenantId,
  UserId,
} from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  getUserId,
  getUserRole,
  requireRole,
  respondWithErrors,
} from "../../../core/http/requestContext.js";
import { createMarketplaceService } from "./service.js";
import { z } from "zod";

const SIMULATION_TEMPLATE_DIFFICULTIES = [
  "BEGINNER",
  "INTERMEDIATE",
  "ADVANCED",
  "EXPERT",
] as const satisfies readonly SimulationTemplateDifficulty[];

function isSimulationTemplateDifficulty(
  value: string,
): value is SimulationTemplateDifficulty {
  return (SIMULATION_TEMPLATE_DIFFICULTIES as readonly string[]).includes(
    value,
  );
}

const listingQuerySchema = z.object({
  status: z.string().min(1).optional(),
  visibility: z.string().min(1).optional(),
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const createListingBodySchema = z.object({
  moduleId: z.string().min(1),
  priceCents: z.number().int().nonnegative(),
  visibility: z.string().min(1),
});

const listingIdParamsSchema = z.object({
  listingId: z.string().min(1),
});

const updateListingBodySchema = z.object({
  status: z.string().min(1).optional(),
  visibility: z.string().min(1).optional(),
  priceCents: z.number().int().nonnegative().optional(),
});

const templateQuerySchema = z.object({
  page: z.coerce.number().int().positive().optional(),
  pageSize: z.coerce.number().int().positive().max(100).optional(),
  domains: z.string().optional(),
  difficulties: z.string().optional(),
  tags: z.string().optional(),
  isPremium: z.enum(["true", "false"]).optional(),
  isVerified: z.enum(["true", "false"]).optional(),
  minRating: z.coerce.number().min(0).max(5).optional(),
  search: z.string().optional(),
  sortBy: z.string().optional(),
  sortOrder: z.enum(["asc", "desc"]).optional(),
});

/**
 * Marketplace routes.
 *
 * @doc.type module
 * @doc.purpose Marketplace HTTP Endpoints
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const marketplaceRoutes: FastifyPluginAsync<{
  service?: ReturnType<typeof createMarketplaceService>;
}> = async (app, options) => {
  const prisma = app.prisma;
  const marketplaceService = options.service ?? createMarketplaceService(prisma);

  // GET /listings
  app.get("/listings", async (req, reply) => {
    const tenantId = getTenantId(req);
    const queryResult = listingQuerySchema.safeParse(req.query ?? {});
    if (!queryResult.success) {
      return reply.code(400).send({
        error: "Invalid marketplace listing query",
        issues: queryResult.error.issues,
      });
    }
    const { status, visibility, cursor, limit } = queryResult.data;

    const listings = await marketplaceService.listListings({
      tenantId: tenantId as TenantId,
      ...(status ? { status } : {}),
      ...(visibility ? { visibility } : {}),
      ...(cursor ? { cursor } : {}),
      ...(limit ? { limit } : {}),
    });
    reply.send(listings);
  });

  // POST /listings
  app.post("/listings", async (req, reply) => {
    const tenantId = getTenantId(req);
    const creatorId = getUserId(req);
    requireRole(req, ["creator", "admin", "teacher"]);

    const payloadResult = createListingBodySchema.safeParse(req.body);
    if (!payloadResult.success) {
      return reply.code(400).send({
        error: "Invalid marketplace listing payload",
        issues: payloadResult.error.issues,
      });
    }
    const payload = payloadResult.data;

    await respondWithErrors(reply, () =>
      marketplaceService.createListing({
        tenantId: tenantId as TenantId,
        moduleId: payload.moduleId as ModuleId,
        creatorId: creatorId as UserId,
        priceCents: payload.priceCents,
        visibility: payload.visibility,
      }),
    );
  });

  // PATCH /listings/:listingId
  app.patch("/listings/:listingId", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    requireRole(req, ["creator", "admin", "teacher"]);
    const userRole = getUserRole(req);
    const paramsResult = listingIdParamsSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid listing id",
        issues: paramsResult.error.issues,
      });
    }
    const bodyResult = updateListingBodySchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid listing update payload",
        issues: bodyResult.error.issues,
      });
    }
    const { listingId } = paramsResult.data;
    const body = bodyResult.data as Partial<
      Pick<MarketplaceListing, "status" | "visibility" | "priceCents">
    >;

    await respondWithErrors(reply, async () => {
      if (userRole === "admin" || userRole === "superadmin") {
        return marketplaceService.adminUpdateListing({
          tenantId: tenantId as TenantId,
          listingId: listingId as MarketplaceListingId,
          ...(body.status ? { status: body.status } : {}),
          ...(body.visibility ? { visibility: body.visibility } : {}),
        });
      }

      return marketplaceService.updateListing({
        tenantId: tenantId as TenantId,
        listingId: listingId as MarketplaceListingId,
        userId: userId as UserId,
        ...(body.status ? { status: body.status } : {}),
        ...(body.visibility ? { visibility: body.visibility } : {}),
        ...(typeof body.priceCents === "number"
          ? { priceCents: body.priceCents }
          : {}),
      });
    });
  });

  // GET /templates
  app.get("/templates", async (req, reply) => {
    const tenantId = getTenantId(req);
    const queryResult = templateQuerySchema.safeParse(req.query ?? {});
    if (!queryResult.success) {
      return reply.code(400).send({
        error: "Invalid template query",
        issues: queryResult.error.issues,
      });
    }

    const {
      page,
      pageSize,
      domains,
      difficulties,
      tags,
      isPremium,
      isVerified,
      minRating,
      search,
      sortBy,
      sortOrder,
    } = queryResult.data;

    await respondWithErrors(reply, () =>
      marketplaceService.listSimulationTemplates({
        tenantId: tenantId as TenantId,
        page: page ? Number(page) : 1,
        pageSize: pageSize ? Number(pageSize) : 12,
        ...(domains ? { domains: (domains as string).split(",") } : {}),
        ...(difficulties
          ? {
              difficulties: (difficulties as string)
                .split(",")
                .map((value) => value.trim().toUpperCase())
                .filter(isSimulationTemplateDifficulty),
            }
          : {}),
        ...(tags ? { tags: (tags as string).split(",") } : {}),
        ...(isPremium === "true" ? { isPremium: true } : {}),
        ...(isVerified === "true" ? { isVerified: true } : {}),
        ...(minRating ? { minRating: Number(minRating) } : {}),
        ...(search ? { search } : {}),
        ...(sortBy ? { sortBy } : {}),
        ...(sortOrder ? { sortOrder } : {}),
      }),
    );
  });

  app.get("/health", async () => {
    return { status: "ok", component: "marketplace" };
  });
};
