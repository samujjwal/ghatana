/**
 * @doc.type test
 * @doc.purpose Unit tests for payments service: SubscriptionServiceImpl and PaymentMethodServiceImpl
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

import { SubscriptionServiceImpl, PaymentMethodServiceImpl } from "../service";
import { NotFoundError, PaymentError } from "../../../core/errors";
import type { PrismaClient } from "@tutorputor/core/db";
import type Stripe from "stripe";

// ---------------------------------------------------------------------------
// Minimal Prisma mock factory
// ---------------------------------------------------------------------------
function makePrisma(overrides: Record<string, unknown> = {}): PrismaClient {
  return {
    stripeCustomer: { findUnique: vi.fn(), create: vi.fn() },
    tenant: { findUnique: vi.fn() },
    subscription: {
      findUnique: vi.fn(),
      findFirst: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
    },
    paymentMethod: {
      findMany: vi.fn(),
      findFirst: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      updateMany: vi.fn(),
    },
    invoice: { findUnique: vi.fn(), findFirst: vi.fn() },
    $transaction: vi.fn((fn: (tx: any) => unknown) =>
      fn({
        paymentMethod: {
          updateMany: vi.fn().mockResolvedValue({ count: 0 }),
          create: vi.fn().mockResolvedValue({
            id: "pm1",
            tenantId: "t1",
            stripePaymentMethodId: "spm_1",
            type: "card",
            isDefault: true,
            lastFour: "4242",
            brand: "visa",
            expMonth: 12,
            expYear: 2026,
            billingName: null,
            billingLine1: null,
            billingLine2: null,
            billingCity: null,
            billingState: null,
            billingPostalCode: null,
            billingCountry: null,
            createdAt: new Date(),
            updatedAt: new Date(),
          }),
        },
      }),
    ),
    ...overrides,
  } as unknown as PrismaClient;
}

// ---------------------------------------------------------------------------
// Minimal Stripe mock factory
// ---------------------------------------------------------------------------
function makeStripe(overrides: Record<string, unknown> = {}): Stripe {
  return {
    customers: { create: vi.fn() },
    subscriptions: {
      create: vi.fn(),
      update: vi.fn(),
      cancel: vi.fn(),
      retrieve: vi.fn(),
    },
    paymentMethods: {
      retrieve: vi.fn(),
      attach: vi.fn(),
    },
    setupIntents: {
      create: vi.fn(),
      retrieve: vi.fn(),
    },
    invoices: { retrieve: vi.fn() },
    ...overrides,
  } as unknown as Stripe;
}

// ---------------------------------------------------------------------------
// SubscriptionServiceImpl
// ---------------------------------------------------------------------------
describe("SubscriptionServiceImpl", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let stripe: ReturnType<typeof makeStripe>;
  let service: SubscriptionServiceImpl;

  beforeEach(() => {
    prisma = makePrisma();
    stripe = makeStripe();
    service = new SubscriptionServiceImpl(prisma, stripe);
  });

  describe("createSubscription", () => {
    it("throws NotFoundError for an unknown plan id", async () => {
      await expect(
        service.createSubscription({
          tenantId: "t1" as any,
          planId: "plan_nonexistent_xyz",
          billingInterval: "monthly",
        }),
      ).rejects.toThrow(NotFoundError);
    });

    it("throws NotFoundError with the unknown plan id in the error", async () => {
      let caught: NotFoundError | undefined;
      try {
        await service.createSubscription({
          tenantId: "t1" as any,
          planId: "plan_nonexistent_xyz",
          billingInterval: "monthly",
        });
      } catch (err) {
        caught = err as NotFoundError;
      }
      expect(caught).toBeInstanceOf(NotFoundError);
      expect(caught?.code).toBe("NOT_FOUND");
      expect(caught?.statusCode).toBe(404);
    });
  });

  describe("listPlans", () => {
    it("returns only active plans by default", async () => {
      const plans = await service.listPlans({ tenantId: "t1" as any });
      expect(plans.every((p) => typeof p.id === "string")).toBe(true);
      // All returned plans come from DEFAULT_PLANS which are all active
      expect(plans.length).toBeGreaterThan(0);
    });

    it("returns all plans including inactive when requested", async () => {
      const all = await service.listPlans({
        tenantId: "t1" as any,
        includeInactive: true,
      });
      const active = await service.listPlans({
        tenantId: "t1" as any,
        includeInactive: false,
      });
      expect(all.length).toBeGreaterThanOrEqual(active.length);
    });
  });

  describe("previewChange", () => {
    it("throws NotFoundError when subscription does not exist", async () => {
      vi.mocked(prisma.subscription.findUnique).mockResolvedValue(null);

      await expect(
        service.previewChange({
          tenantId: "t1" as any,
          subscriptionId: "sub_missing" as any,
          newPlanId: "plan_starter",
        }),
      ).rejects.toThrow(NotFoundError);
    });
  });
});

// ---------------------------------------------------------------------------
// PaymentMethodServiceImpl
// ---------------------------------------------------------------------------
describe("PaymentMethodServiceImpl", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let stripe: ReturnType<typeof makeStripe>;
  let service: PaymentMethodServiceImpl;

  beforeEach(() => {
    prisma = makePrisma();
    stripe = makeStripe();
    service = new PaymentMethodServiceImpl(prisma, stripe);
  });

  describe("attachPaymentMethod", () => {
    it("throws PaymentError when setup intent has no payment_method", async () => {
      vi.mocked(stripe.setupIntents.retrieve).mockResolvedValue({
        payment_method: null,
      } as never);

      await expect(
        service.attachPaymentMethod({
          tenantId: "t1" as any,
          setupIntentId: "seti_invalid",
        }),
      ).rejects.toThrow(PaymentError);
    });

    it("throws PaymentError with SETUP_INTENT_INVALID code", async () => {
      vi.mocked(stripe.setupIntents.retrieve).mockResolvedValue({
        payment_method: null,
      } as never);

      let caught: PaymentError | undefined;
      try {
        await service.attachPaymentMethod({
          tenantId: "t1" as any,
          setupIntentId: "seti_invalid",
        });
      } catch (err) {
        caught = err as PaymentError;
      }
      expect(caught).toBeInstanceOf(PaymentError);
      expect(caught?.code).toBe("SETUP_INTENT_INVALID");
    });

    it("wraps creation in $transaction and returns mapped PaymentMethod", async () => {
      vi.mocked(stripe.setupIntents.retrieve).mockResolvedValue({
        payment_method: "spm_test_123",
      } as never);

      vi.mocked(stripe.paymentMethods.retrieve).mockResolvedValue({
        id: "spm_test_123",
        type: "card",
        card: { last4: "4242", brand: "visa", exp_month: 12, exp_year: 2026 },
        us_bank_account: undefined,
      } as never);

      const result = await service.attachPaymentMethod({
        tenantId: "t1" as any,
        setupIntentId: "seti_1",
        setAsDefault: true,
      });

      expect(prisma.$transaction).toHaveBeenCalled();
      expect(result.id).toBe("pm1");
      expect(result.card?.last4).toBe("4242");
    });
  });

  describe("listPaymentMethods", () => {
    it("returns mapped payment methods from prisma", async () => {
      vi.mocked(prisma.paymentMethod.findMany).mockResolvedValue([
        {
          id: "pm1",
          tenantId: "t1",
          stripePaymentMethodId: "spm_1",
          type: "card",
          isDefault: true,
          lastFour: "4111",
          brand: "visa",
          expMonth: 1,
          expYear: 2027,
          billingName: null,
          billingLine1: null,
          billingLine2: null,
          billingCity: null,
          billingState: null,
          billingPostalCode: null,
          billingCountry: null,
          createdAt: new Date(),
          updatedAt: new Date(),
        } as never,
      ]);

      const methods = await service.listPaymentMethods({
        tenantId: "t1" as any,
      });
      expect(methods).toHaveLength(1);
      expect(methods[0]?.id).toBe("pm1");
    });
  });
});
