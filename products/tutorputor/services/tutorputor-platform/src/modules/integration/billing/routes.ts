import type { FastifyPluginAsync } from "fastify";
import type { CheckoutSessionId, MarketplaceListingId, ModuleId, TenantId, UserId } from "@ghatana/tutorputor-contracts/v1/types";
import { getTenantId, getUserId, respondWithErrors } from "../../../core/http/requestContext.js";
import { createBillingService } from "./service.js";

/**
 * Billing routes - subscriptions and payments.
 * 
 * @doc.type module
 * @doc.purpose Billing HTTP Endpoints
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const billingRoutes: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma as any;
  const billingService = createBillingService(prisma);

  /**
   * POST /checkout
   * Create a checkout session
   */
  app.post("/checkout", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const { listingId, successUrl, cancelUrl } = req.body as {
      listingId: MarketplaceListingId;
      successUrl?: string;
      cancelUrl?: string;
    };

    if (!listingId) {
      return reply.code(400).send({ error: "listingId is required" });
    }

    await respondWithErrors(reply, () =>
      billingService.createCheckoutSession({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        listingId,
        successUrl,
        cancelUrl
      })
    );
  });

  /**
   * POST /verify
   * Verify payment status
   */
  app.post("/verify", async (req, reply) => {
    const tenantId = getTenantId(req);
    const { sessionId } = req.body as { sessionId: CheckoutSessionId };

    if (!sessionId) {
      return reply.code(400).send({ error: "sessionId is required" });
    }

    await respondWithErrors(reply, () =>
      billingService.verifyPayment({
        tenantId: tenantId as TenantId,
        sessionId
      })
    );
  });

  /**
   * GET /purchases
   * List user purchases
   */
  app.get("/purchases", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const { cursor, limit } = req.query as {
      cursor?: string;
      limit?: string;
    };

    await respondWithErrors(reply, () =>
      billingService.listPurchases({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        cursor,
        limit: limit ? parseInt(limit, 10) : 20
      })
    );
  });

  /**
   * GET /purchased/:moduleId
   * Check if module is purchased
   */
  app.get("/purchased/:moduleId", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const { moduleId } = req.params as { moduleId: ModuleId };

    const hasPurchased = await billingService.hasPurchased({
      tenantId: tenantId as TenantId,
      userId: userId as UserId,
      moduleId
    });

    return reply.send({ purchased: hasPurchased });
  });
};
