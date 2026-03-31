import type { FastifyInstance } from "fastify";
import { z } from "zod";

import type { TenantId } from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  respondWithErrors,
} from "../../core/http/requestContext.js";
import type { SubscriptionServiceImpl } from "./service.js";

const CreateSubscriptionSchema = z.object({
  planId: z.string().min(1),
  billingInterval: z.enum(["monthly", "quarterly", "annual"]),
  paymentMethodId: z.string().min(1).optional(),
  trialDays: z.number().int().min(0).optional(),
});

type CreateSubscriptionDto = z.infer<typeof CreateSubscriptionSchema>;

/**
 * Subscription management routes.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for platform subscription management
 * @doc.layer product
 * @doc.pattern REST API
 */
export async function paymentRoutes(
  fastify: FastifyInstance,
  options: { service: SubscriptionServiceImpl },
) {
  const service = options.service;

  /**
   * GET /payments/plans
   * List available subscription plans.
   */
  fastify.get("/payments/plans", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    await respondWithErrors(reply, () => service.listPlans({ tenantId }));
  });

  /**
   * POST /payments/subscriptions
   * Create a new subscription for the tenant.
   */
  fastify.post<{ Body: CreateSubscriptionDto }>(
    "/payments/subscriptions",
    async (request, reply) => {
      const body = CreateSubscriptionSchema.parse(request.body);
      const tenantId = getTenantId(request) as TenantId;

      const subscription = await service.createSubscription({
        tenantId,
        planId: body.planId as any,
        billingInterval: body.billingInterval,
        ...(body.paymentMethodId ? { paymentMethodId: body.paymentMethodId as any } : {}),
        ...(typeof body.trialDays === "number" ? { trialDays: body.trialDays } : {}),
      });
      return reply.code(201).send(subscription);
    },
  );

  /**
   * GET /payments/subscription
   * Get the active subscription for the tenant.
   */
  fastify.get("/payments/subscription", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;

    const subscription = await service.getCurrentSubscription({ tenantId });
    if (!subscription) {
      return reply.code(404).send({ message: "No active subscription found" });
    }
    return subscription;
  });

  /**
   * POST /payments/subscription/cancel
   * Cancel the tenant's active subscription.
   */
  fastify.post<{ Body: { atPeriodEnd?: boolean; reason?: string } }>(
    "/payments/subscription/cancel",
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const { atPeriodEnd = true, reason } =
        (request.body as { atPeriodEnd?: boolean; reason?: string }) ?? {};

      await respondWithErrors(reply, async () => {
        const currentSub = await service.getCurrentSubscription({ tenantId });
        if (!currentSub) {
          throw Object.assign(new Error("No active subscription found"), {
            statusCode: 404,
            code: "NOT_FOUND",
          });
        }
        return service.cancelSubscription({
          tenantId,
          subscriptionId: currentSub.id,
          cancelImmediately: !atPeriodEnd,
          ...(reason ? { reason } : {}),
        });
      });
    },
  );

  /**
   * POST /payments/subscription/change
   * Upgrade or downgrade the tenant's plan.
   */
  fastify.post<{ Body: { planId: string; billingInterval: string } }>(
    "/payments/subscription/change",
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const { planId, billingInterval } = request.body as {
        planId: string;
        billingInterval: string;
      };

      if (!planId || !billingInterval) {
        return reply
          .code(400)
          .send({ error: "planId and billingInterval are required" });
      }

      await respondWithErrors(reply, async () => {
        const currentSub = await service.getCurrentSubscription({ tenantId });
        if (!currentSub) {
          throw Object.assign(new Error("No active subscription found"), {
            statusCode: 404,
            code: "NOT_FOUND",
          });
        }
        return service.changePlan({
          tenantId,
          subscriptionId: currentSub.id,
          newPlanId: planId,
          newBillingInterval: billingInterval as
            | "monthly"
            | "quarterly"
            | "annual",
        });
      });
    },
  );

  /**
   * POST /payments/portal
   * Create a Stripe billing portal session for self-service management.
   */
  fastify.post<{ Body: { returnUrl?: string } }>(
    "/payments/portal",
    async (_request, reply) => {
      return reply.code(501).send({
        message:
          "Billing portal sessions require Stripe configuration. Set STRIPE_SECRET_KEY to enable.",
      });
    },
  );
}
