import type { FastifyInstance } from "fastify";
import { z } from "zod";
import type Stripe from "stripe";

import type { TenantId } from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  respondWithErrors,
} from "../../core/http/requestContext.js";
import type { SubscriptionServiceImpl } from "./service.js";

const IDEMPOTENCY_TTL_SECONDS = 24 * 60 * 60;

/**
 * F-017: Resolve idempotency for a billing mutation.
 * Returns the cached response if the key was seen before, otherwise null.
 * Stores the result for IDEMPOTENCY_TTL_SECONDS after first execution.
 */
async function resolveIdempotency(
  redis: { get: (k: string) => Promise<string | null>; set: (k: string, v: string, ex: string, ttl: number) => Promise<unknown> } | undefined,
  tenantId: string,
  operation: string,
  key: string,
): Promise<{ cached: true; body: unknown } | { cached: false; storeResult: (result: unknown) => Promise<void> }> {
  if (!redis || !key) {
    return { cached: false, storeResult: async () => {} };
  }
  const redisKey = `billing:idempotency:${tenantId}:${operation}:${key.slice(0, 128)}`;
  const existing = await redis.get(redisKey);
  if (existing !== null) {
    return { cached: true, body: JSON.parse(existing) as unknown };
  }
  return {
    cached: false,
    storeResult: async (result: unknown) => {
      await redis.set(redisKey, JSON.stringify(result), "EX", IDEMPOTENCY_TTL_SECONDS);
    },
  };
}

const CreateSubscriptionSchema = z.object({
  planId: z.string().min(1),
  billingInterval: z.enum(["monthly", "quarterly", "annual"]),
  paymentMethodId: z.string().min(1).optional(),
  trialDays: z.number().int().min(0).optional(),
});

const CancelSubscriptionSchema = z.object({
  atPeriodEnd: z.boolean().optional(),
  reason: z.string().min(1).optional(),
});

const ChangeSubscriptionSchema = z.object({
  planId: z.string().min(1),
  billingInterval: z.enum(["monthly", "quarterly", "annual"]),
});

const BillingPortalSchema = z.object({
  returnUrl: z.string().url().optional(),
});

type CreateSubscriptionDto = z.infer<typeof CreateSubscriptionSchema>;

function createValidationErrorResponse(error: z.ZodError) {
  const primaryIssue = error.issues[0];
  return {
    error: "Validation Error",
    message: primaryIssue?.message ?? "Invalid request payload",
  };
}

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
      const parseResult = CreateSubscriptionSchema.safeParse(request.body);
      if (!parseResult.success) {
        return reply.code(400).send(createValidationErrorResponse(parseResult.error));
      }

      const body = parseResult.data;
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
   * F-017: Accepts `Idempotency-Key` header; repeated requests with the same key return cached result.
   */
  fastify.post<{ Body: { atPeriodEnd?: boolean; reason?: string } }>(
    "/payments/subscription/cancel",
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const parseResult = CancelSubscriptionSchema.safeParse(request.body ?? {});
      if (!parseResult.success) {
        return reply.code(400).send(createValidationErrorResponse(parseResult.error));
      }

      const idempotencyKey = request.headers["idempotency-key"] as string | undefined ?? "";
      const redis = (fastify as unknown as { redis?: { get: (k: string) => Promise<string | null>; set: (k: string, v: string, ex: string, ttl: number) => Promise<unknown> } }).redis;
      const idempotency = await resolveIdempotency(redis, tenantId, "cancel", idempotencyKey);
      if (idempotency.cached) {
        return reply.code(200).send(idempotency.body);
      }

      const { atPeriodEnd = true, reason } = parseResult.data;

      await respondWithErrors(reply, async () => {
        const currentSub = await service.getCurrentSubscription({ tenantId });
        if (!currentSub) {
          throw Object.assign(new Error("No active subscription found"), {
            statusCode: 404,
            code: "NOT_FOUND",
          });
        }
        const result = await service.cancelSubscription({
          tenantId,
          subscriptionId: currentSub.id,
          cancelImmediately: !atPeriodEnd,
          ...(reason ? { reason } : {}),
        });
        if (idempotencyKey) {
          await idempotency.storeResult(result);
        }
        return result;
      });
    },
  );

  /**
   * POST /payments/subscription/change
   * Upgrade or downgrade the tenant's plan.
   * F-017: Accepts `Idempotency-Key` header; repeated requests with the same key return cached result.
   */
  fastify.post<{ Body: { planId: string; billingInterval: string } }>(
    "/payments/subscription/change",
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const parseResult = ChangeSubscriptionSchema.safeParse(request.body);
      if (!parseResult.success) {
        return reply.code(400).send(createValidationErrorResponse(parseResult.error));
      }

      const idempotencyKey = request.headers["idempotency-key"] as string | undefined ?? "";
      const redis = (fastify as unknown as { redis?: { get: (k: string) => Promise<string | null>; set: (k: string, v: string, ex: string, ttl: number) => Promise<unknown> } }).redis;
      const idempotency = await resolveIdempotency(redis, tenantId, "change", idempotencyKey);
      if (idempotency.cached) {
        return reply.code(200).send(idempotency.body);
      }

      const { planId, billingInterval } = parseResult.data;

      await respondWithErrors(reply, async () => {
        const currentSub = await service.getCurrentSubscription({ tenantId });
        if (!currentSub) {
          throw Object.assign(new Error("No active subscription found"), {
            statusCode: 404,
            code: "NOT_FOUND",
          });
        }
        const result = await service.changePlan({
          tenantId,
          subscriptionId: currentSub.id,
          newPlanId: planId,
          newBillingInterval: billingInterval as
            | "monthly"
            | "quarterly"
            | "annual",
        });
        if (idempotencyKey) {
          await idempotency.storeResult(result);
        }
        return result;
      });
    },
  );

  /**
   * POST /payments/portal
   * Create a Stripe billing portal session for self-service management.
   */
  fastify.post<{ Body: { returnUrl?: string } }>(
    "/payments/portal",
    async (request, reply) => {
      const parseResult = BillingPortalSchema.safeParse(request.body ?? {});
      if (!parseResult.success) {
        return reply.code(400).send(createValidationErrorResponse(parseResult.error));
      }

      if (!process.env.STRIPE_SECRET_KEY) {
        return reply.code(503).send({
          error: "FeatureNotEnabled",
          message:
            "Billing portal is not available on this deployment. Contact your platform operator to enable Stripe integration.",
        });
      }

      const tenantId = getTenantId(request) as TenantId;
      const { prisma } = fastify as unknown as { prisma: { stripeCustomer: { findUnique: (args: { where: { tenantId: string } }) => Promise<{ stripeCustomerId: string } | null> } } };
      const stripeCustomer = await prisma.stripeCustomer.findUnique({ where: { tenantId } });

      if (!stripeCustomer) {
        return reply.code(404).send({
          error: "CustomerNotFound",
          message: "No Stripe customer found for this tenant. Complete onboarding first.",
        });
      }

      const Stripe = (await import("stripe")).default;
      const stripe = new Stripe(process.env.STRIPE_SECRET_KEY, { apiVersion: "2026-03-25.dahlia" });
      const sessionParams: Stripe.BillingPortal.SessionCreateParams = {
        customer: stripeCustomer.stripeCustomerId,
      };
      if (parseResult.data.returnUrl) {
        sessionParams.return_url = parseResult.data.returnUrl;
      }
      const session = await stripe.billingPortal.sessions.create(sessionParams);

      return reply.code(200).send({ url: session.url });
    },
  );
}
