import type { FastifyPluginAsync } from "fastify";
import type { MarketplaceListing, ModuleId, MarketplaceListingId, TenantId, UserId } from "@ghatana/tutorputor-contracts/v1/types";
import { getTenantId, getUserId, requireRole, respondWithErrors } from "../../../core/http/requestContext.js";
import { createMarketplaceService } from "./service.js";

/**
 * Marketplace routes.
 * 
 * @doc.type module
 * @doc.purpose Marketplace HTTP Endpoints
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const marketplaceRoutes: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma as any;
  const marketplaceService = createMarketplaceService(prisma);

  // GET /listings
  app.get("/listings", async (req, reply) => {
    const tenantId = getTenantId(req);
    const { status, visibility, cursor, limit } = (req.query ?? {}) as any;

    const listings = await marketplaceService.listListings({
      tenantId: tenantId as TenantId,
      status,
      visibility,
      cursor,
      limit: limit ? Number(limit) : undefined
    });
    reply.send(listings);
  });

  // POST /listings
  app.post("/listings", async (req, reply) => {
    const tenantId = getTenantId(req);
    const creatorId = getUserId(req);
    requireRole(req, ["creator", "admin", "teacher"]);

    const payload = req.body as {
      moduleId: string;
      priceCents: number;
      visibility: MarketplaceListing["visibility"];
    };

    await respondWithErrors(reply, () =>
      marketplaceService.createListing({
        tenantId: tenantId as TenantId,
        moduleId: payload.moduleId as ModuleId,
        creatorId: creatorId as UserId,
        priceCents: payload.priceCents,
        visibility: payload.visibility
      })
    );
  });

  // PATCH /listings/:listingId
  app.patch("/listings/:listingId", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    requireRole(req, ["creator", "admin", "teacher"]);
    const { listingId } = req.params as { listingId: string };
    const body = req.body as Partial<Pick<MarketplaceListing, "status" | "visibility" | "priceCents">>;

    await respondWithErrors(reply, () =>
      marketplaceService.updateListing({
        tenantId: tenantId as TenantId,
        listingId: listingId as MarketplaceListingId,
        userId: userId as UserId,
        status: body.status,
        visibility: body.visibility,
        priceCents: body.priceCents
      })
    );
  });

  // GET /templates
  app.get("/templates", async (req, reply) => {
    const tenantId = getTenantId(req);
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
    } = (req.query ?? {}) as any;

    await respondWithErrors(reply, () =>
      marketplaceService.listSimulationTemplates({
        tenantId: tenantId as TenantId,
        page: page ? Number(page) : 1,
        pageSize: pageSize ? Number(pageSize) : 12,
        domains: domains ? (domains as string).split(",") : undefined,
        difficulties: difficulties ? (difficulties as string).split(",") as any[] : undefined,
        tags: tags ? (tags as string).split(",") : undefined,
        isPremium: isPremium === "true",
        isVerified: isVerified === "true",
        minRating: minRating ? Number(minRating) : undefined,
        search,
        sortBy,
        sortOrder
      })
    );
  });

  app.get("/health", async () => {
    return { status: "ok", component: "marketplace" };
  });
};
