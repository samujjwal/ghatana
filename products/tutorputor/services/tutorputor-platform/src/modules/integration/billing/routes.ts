import type { FastifyPluginAsync } from "fastify";
import type {
  CheckoutSessionId,
  MarketplaceListingId,
  ModuleId,
  TenantId,
  UserId,
} from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  getUserId,
  respondWithErrors,
} from "../../../core/http/requestContext.js";
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
        cancelUrl,
      }),
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
        sessionId,
      }),
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
        limit: limit ? parseInt(limit, 10) : 20,
      }),
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
      moduleId,
    });

    return reply.send({ purchased: hasPurchased });
  });

  // ===========================================================================
  // Stripe Webhook Handler
  // This route is intentionally excluded from the global JWT guard (see setup.ts
  // exemption list). Authenticity is verified via Stripe-Signature header.
  // ===========================================================================

  /**
   * POST /webhook
   * Receive and process Stripe events (checkout.session.completed, etc.).
   * Must be registered with raw body parsing – add `rawBody: true` to Fastify
   * instance options to enable stripe signature verification.
   */
  app.post(
    "/webhook",
    {
      config: { rawBody: true },
    },
    async (req, reply) => {
      const webhookSecret = process.env.STRIPE_WEBHOOK_SECRET;
      if (!webhookSecret) {
        // Stripe webhook secret not configured – log and ignore
        app.log.warn("STRIPE_WEBHOOK_SECRET is not set; webhook ignored");
        return reply.code(200).send({ received: true });
      }

      const signature = req.headers["stripe-signature"] as string | undefined;
      if (!signature) {
        return reply
          .code(400)
          .send({ error: "Missing stripe-signature header" });
      }

      // Retrieve the raw body written by fastify-raw-body or the built-in rawBody option.
      const rawBody: Buffer | string | undefined =
        (req as any).rawBody ?? (req as any).body;

      if (!rawBody) {
        return reply.code(400).send({ error: "Raw request body unavailable" });
      }

      let event: { type: string; data: { object: any } };
      try {
        // Dynamic require avoids a hard Stripe SDK dependency if the key is absent.
        const Stripe = (await import("stripe")).default;
        const stripe = new Stripe(process.env.STRIPE_SECRET_KEY ?? "", {
          apiVersion: "2023-10-16" as any,
        });
        event = stripe.webhooks.constructEvent(
          rawBody,
          signature,
          webhookSecret,
        ) as any;
      } catch (err) {
        app.log.warn({ err }, "Stripe webhook signature verification failed");
        return reply
          .code(400)
          .send({ error: "Webhook signature verification failed" });
      }

      try {
        switch (event.type) {
          case "checkout.session.completed": {
            const session = event.data.object as {
              id: string;
              metadata?: { tenantId?: string; sessionId?: string };
              payment_status: string;
            };
            const sessionId = session.metadata?.sessionId ?? session.id;
            const tenantId = session.metadata?.tenantId;

            if (tenantId && sessionId && session.payment_status === "paid") {
              // Mark the checkout session as paid and create a purchase record
              await prisma.checkoutSession.updateMany({
                where: { id: sessionId, tenantId },
                data: { status: "COMPLETED" },
              });
              app.log.info(
                { sessionId, tenantId },
                "Stripe webhook: checkout.session.completed processed",
              );
            }
            break;
          }

          case "customer.subscription.deleted": {
            const sub = event.data.object as {
              id: string;
              metadata?: { tenantId?: string };
            };
            const tenantId = sub.metadata?.tenantId;
            if (tenantId) {
              await prisma.subscription
                ?.updateMany?.({
                  where: { stripeSubscriptionId: sub.id, tenantId },
                  data: { status: "canceled" },
                })
                .catch((err: unknown) => {
                  app.log.warn({ err }, "subscription model not available");
                });
            }
            break;
          }

          default:
            app.log.debug(
              { type: event.type },
              "Unhandled Stripe webhook event",
            );
        }
      } catch (processingErr) {
        app.log.error(
          { processingErr, eventType: event.type },
          "Error processing Stripe webhook event",
        );
        // Return 200 to Stripe so it does not retry; log for manual investigation.
      }

      return reply.code(200).send({ received: true });
    },
  );
};
