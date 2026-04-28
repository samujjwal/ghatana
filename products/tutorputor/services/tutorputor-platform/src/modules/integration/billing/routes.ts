import type { FastifyPluginAsync } from "fastify";
import type { Logger } from "pino";
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
import { createPaymentCircuitBreaker } from "../../../utils/circuit-breaker.js";
import { z } from "zod";

const checkoutBodySchema = z.object({
  listingId: z.string().min(1),
  successUrl: z.string().url().optional(),
  cancelUrl: z.string().url().optional(),
});

const verifyBodySchema = z.object({
  sessionId: z.string().min(1),
});

const purchasesQuerySchema = z.object({
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const purchasedParamsSchema = z.object({
  moduleId: z.string().min(1),
});

/**
 * Billing routes - subscriptions and payments.
 *
 * @doc.type module
 * @doc.purpose Billing HTTP Endpoints
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const billingRoutes: FastifyPluginAsync<{
  service?: ReturnType<typeof createBillingService> & {
    createBillingPortalSession: (args: {
      tenantId: TenantId;
      userId: UserId;
      returnUrl?: string;
    }) => Promise<{ url: string }>;
  };
}> = async (app, options) => {
  const prisma = app.prisma;
  const billingService = options.service ?? createBillingService(prisma);

  // Create circuit breakers for external services
  const stripeCircuitBreaker = createPaymentCircuitBreaker(
    "stripe-webhook",
    async (...args: unknown[]) => {
      const [stripeSecretKey, bodyString, signature, webhookSecret] = args as [
        string,
        string,
        string,
        string,
      ];
      const Stripe = (await import("stripe")).default;
      const stripe = new Stripe(stripeSecretKey, {
        apiVersion: "2026-03-25.dahlia",
        maxNetworkRetries: 3,
      });
      return stripe.webhooks.constructEvent(
        bodyString,
        signature,
        webhookSecret,
      );
    },
    app.log as unknown as Logger,
  );

  /**
   * POST /checkout
   * Create a checkout session
   */
  app.post("/checkout", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const bodyResult = checkoutBodySchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid checkout payload",
        issues: bodyResult.error.issues,
      });
    }
    const { listingId, successUrl, cancelUrl } = bodyResult.data;

    await respondWithErrors(reply, () =>
      billingService.createCheckoutSession({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        listingId: listingId as MarketplaceListingId,
        ...(successUrl ? { successUrl } : {}),
        ...(cancelUrl ? { cancelUrl } : {}),
      }),
    );
  });

  /**
   * POST /verify
   * Verify payment status
   */
  app.post("/verify", async (req, reply) => {
    const tenantId = getTenantId(req);
    const bodyResult = verifyBodySchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid verify payload",
        issues: bodyResult.error.issues,
      });
    }
    const { sessionId } = bodyResult.data;

    await respondWithErrors(reply, () =>
      billingService.verifyPayment({
        tenantId: tenantId as TenantId,
        sessionId: sessionId as CheckoutSessionId,
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
    const queryResult = purchasesQuerySchema.safeParse(req.query);
    if (!queryResult.success) {
      return reply.code(400).send({
        error: "Invalid purchases query",
        issues: queryResult.error.issues,
      });
    }
    const { cursor, limit } = queryResult.data;

    await respondWithErrors(reply, () =>
      billingService.listPurchases({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        ...(cursor ? { cursor } : {}),
        limit: limit ?? 20,
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
    const paramsResult = purchasedParamsSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid module id",
        issues: paramsResult.error.issues,
      });
    }
    const { moduleId } = paramsResult.data;

    const hasPurchased = await billingService.hasPurchased({
      tenantId: tenantId as TenantId,
      userId: userId as UserId,
      moduleId: moduleId as ModuleId,
    });

    return reply.send({ purchased: hasPurchased });
  });

  /**
   * GET /capabilities
   * Returns which billing features are available for the tenant.
   * Frontend uses this to conditionally render the billing portal CTA (F-016).
   */
  app.get("/capabilities", async (_req, reply) => {
    return reply.send({
      billingPortalEnabled: process.env["STRIPE_BILLING_PORTAL_ENABLED"] === "true",
    });
  });

  /**
   * POST /portal
   * Create a billing portal session for managing subscriptions
   */
  app.post("/portal", async (req, reply) => {
    const tenantId = getTenantId(req);
    const bodySchema = z.object({
      returnUrl: z.string().url(),
    });
    const bodyResult = bodySchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid portal payload",
        issues: bodyResult.error.issues,
      });
    }
    const { returnUrl } = bodyResult.data;

    try {
      const session = await billingService.createBillingPortalSession({
        tenantId: tenantId as TenantId,
        userId: getUserId(req) as UserId,
        returnUrl,
      });
      return reply.send(session);
    } catch (error) {
      app.log.error(error, "Failed to create billing portal session");
      return reply.code(500).send({
        error: "Failed to create billing portal session",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  // ===========================================================================
  // Stripe Webhook Handler
  // This route is public (no JWT guard). Authenticity is verified via
  // Stripe-Signature header — see config.public = true below.
  // ===========================================================================

  /**
   * POST /webhook
   * Receive and process Stripe events (checkout.session.completed, etc.).
   * Authenticity is verified via Stripe-Signature header.
   */
  app.post(
    "/webhook",
    {
      config: {
        public: true,
        rawBody: true,
        // Add rate limiting for webhooks to prevent abuse
        rateLimit: {
          max: 100,
          timeWindow: "1 minute",
        },
      },
    },
    async (req, reply) => {
      const webhookSecret = process.env.STRIPE_WEBHOOK_SECRET;
      if (!webhookSecret) {
        app.log.error(
          "STRIPE_WEBHOOK_SECRET is not set; webhook processing disabled",
        );
        return reply.code(500).send({
          error: "Webhook not configured",
          received: false,
        });
      }

      const signature = req.headers["stripe-signature"] as string | undefined;
      if (!signature) {
        app.log.warn("Webhook request missing stripe-signature header");
        return reply
          .code(400)
          .send({ error: "Missing stripe-signature header" });
      }

      // Retrieve the raw body - Fastify 5.x provides it via req.body when rawBody is enabled
      const rawBody = req.body as string | Buffer;

      if (!rawBody || rawBody.length === 0) {
        app.log.warn("Webhook request missing body");
        return reply.code(400).send({ error: "Request body is required" });
      }

      // Convert Buffer to string if needed
      const bodyString = Buffer.isBuffer(rawBody)
        ? rawBody.toString("utf8")
        : rawBody;

      let event: { id: string; type: string; data: { object: unknown } };
      try {
        const stripeSecretKey = process.env.STRIPE_SECRET_KEY;
        if (!stripeSecretKey) {
          app.log.error(
            "STRIPE_SECRET_KEY is not set; cannot validate Stripe webhook event",
          );
          return reply
            .code(500)
            .send({ error: "Stripe webhook is not configured" });
        }

        // Verify webhook signature using circuit breaker
        event = await stripeCircuitBreaker.execute(
          stripeSecretKey,
          bodyString,
          signature,
          webhookSecret,
        );

        app.log.info(
          { eventType: event.type, eventId: event.id },
          "Stripe webhook signature verified successfully",
        );
      } catch (err: unknown) {
        app.log.warn(
          {
            err: err instanceof Error ? err.message : String(err),
            signature: signature.substring(0, 20) + "...",
          },
          "Stripe webhook signature verification failed",
        );
        return reply
          .code(400)
          .send({ error: "Webhook signature verification failed" });
      }

      // Process the verified event
      try {
        switch (event.type) {
          case "checkout.session.completed": {
            const session = event.data.object as {
              id: string;
              metadata?: { tenantId?: string; sessionId?: string };
              payment_status: string;
              customer?: string;
              amount_total?: number;
            };

            const sessionId = session.metadata?.sessionId ?? session.id;
            const tenantId = session.metadata?.tenantId;

            if (!tenantId) {
              app.log.error(
                { sessionId, eventId: event.id },
                "Stripe webhook: missing tenantId in metadata",
              );
              break;
            }

            if (session.payment_status === "paid") {
              // Mark the checkout session as paid and create a purchase record
              const result = await prisma.checkoutSession.updateMany({
                where: { id: sessionId, tenantId, status: "PENDING" },
                data: {
                  status: "COMPLETED",
                  completedAt: new Date(),
                },
              });

              if (result.count === 0) {
                app.log.warn(
                  { sessionId, tenantId },
                  "Stripe webhook: no pending checkout session found",
                );
              } else {
                app.log.info(
                  { sessionId, tenantId, amount: session.amount_total },
                  "Stripe webhook: checkout.session.completed processed",
                );
              }
            } else {
              app.log.warn(
                { sessionId, tenantId, paymentStatus: session.payment_status },
                "Stripe webhook: payment not successful",
              );
            }
            break;
          }

          case "checkout.session.expired": {
            const session = event.data.object as {
              id: string;
              metadata?: { tenantId?: string; sessionId?: string };
            };

            const sessionId = session.metadata?.sessionId ?? session.id;
            const tenantId = session.metadata?.tenantId;

            if (tenantId && sessionId) {
              await prisma.checkoutSession.updateMany({
                where: { id: sessionId, tenantId, status: "PENDING" },
                data: { status: "CANCELLED" },
              });

              app.log.info(
                { sessionId, tenantId },
                "Stripe webhook: checkout.session.expired processed",
              );
            }
            break;
          }

          case "customer.subscription.created":
          case "customer.subscription.updated": {
            const sub = event.data.object as {
              id: string;
              metadata?: { tenantId?: string };
              status: string;
              current_period_start: number;
              current_period_end: number;
              trial_start?: number;
              trial_end?: number;
            };

            const tenantId = sub.metadata?.tenantId;
            if (tenantId) {
              await prisma.subscription.updateMany({
                where: { stripeSubscriptionId: sub.id },
                data: {
                  status: sub.status as "ACTIVE" | "PAST_DUE" | "CANCELED" | "INCOMPLETE" | "INCOMPLETE_EXPIRED" | "TRIALING" | "UNPAID",
                  currentPeriodStart: new Date(sub.current_period_start * 1000),
                  currentPeriodEnd: new Date(sub.current_period_end * 1000),
                  trialStart: sub.trial_start
                    ? new Date(sub.trial_start * 1000)
                    : null,
                  trialEnd: sub.trial_end
                    ? new Date(sub.trial_end * 1000)
                    : null,
                },
              });

              app.log.info(
                { tenantId, subscriptionId: sub.id, status: sub.status },
                `Stripe webhook: ${event.type} processed`,
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
              await prisma.subscription.updateMany({
                where: { tenantId },
                data: { status: "CANCELED", canceledAt: new Date() },
              });

              app.log.info(
                { tenantId, subscriptionId: sub.id },
                "Stripe webhook: customer.subscription.deleted processed",
              );
            }
            break;
          }

          case "invoice.payment_succeeded":
          case "invoice.payment_failed": {
            const invoice = event.data.object as {
              id: string;
              subscription?: string;
              metadata?: { tenantId?: string };
              status: string;
            };

            const tenantId = invoice.metadata?.tenantId;
            if (tenantId && invoice.subscription) {
              // Update subscription payment status
              await prisma.subscription.updateMany({
                where: {
                  tenantId,
                  stripeSubscriptionId: invoice.subscription,
                },
                data: {
                  updatedAt: new Date(),
                },
              });

              app.log.info(
                { tenantId, invoiceId: invoice.id, status: invoice.status },
                `Stripe webhook: ${event.type} processed`,
              );
            }
            break;
          }

          default:
            app.log.debug(
              { type: event.type, eventId: event.id },
              "Unhandled Stripe webhook event",
            );
        }
      } catch (processingErr: unknown) {
        app.log.error(
          {
            processingErr: processingErr instanceof Error ? processingErr.message : String(processingErr),
            eventType: event.type,
            eventId: event.id,
          },
          "Error processing Stripe webhook event",
        );
        // Return 200 to Stripe so it does not retry; log for manual investigation
      }

      return reply.code(200).send({ received: true });
    },
  );
};
