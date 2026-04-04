/**
 * @doc.type test
 * @doc.purpose Unit tests for PaymentWebhookServiceImpl: signature verification, event dispatch, DB state mutations
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

import { PaymentWebhookServiceImpl } from "../service";
import type Stripe from "stripe";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const WEBHOOK_SECRET = "whsec_test_secret";
const STRIPE_EVENT_ID = "evt_test_001";

type PrismaClient = ConstructorParameters<typeof PaymentWebhookServiceImpl>[0];

type DeepPartial<T> = T extends object
  ? { [P in keyof T]?: DeepPartial<T[P]> }
  : T;

function makePrisma(overrides: DeepPartial<PrismaClient> = {}): PrismaClient {
  return {
    webhookEvent: {
      create: vi
        .fn()
        .mockResolvedValue({ id: "we-1", stripeEventId: STRIPE_EVENT_ID }),
      update: vi.fn().mockResolvedValue({}),
      findUnique: vi.fn(),
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
    },
    subscription: {
      updateMany: vi.fn().mockResolvedValue({ count: 1 }),
      findFirst: vi.fn(),
    },
    invoice: {
      updateMany: vi.fn().mockResolvedValue({ count: 1 }),
    },
    stripeCustomer: {
      findUnique: vi.fn().mockResolvedValue(null),
      findFirst: vi.fn().mockResolvedValue(null),
    },
    paymentMethod: {
      upsert: vi.fn().mockResolvedValue({}),
      deleteMany: vi.fn().mockResolvedValue({ count: 1 }),
    },
    $transaction: vi.fn(async (fn: (tx: PrismaClient) => Promise<unknown>) =>
      fn(makePrisma()),
    ),
    ...overrides,
  } as unknown as PrismaClient;
}

function makeStripe(overrides: Record<string, unknown> = {}): Stripe {
  return {
    webhooks: {
      constructEvent: vi.fn(),
    },
    events: {
      retrieve: vi.fn(),
    },
    ...overrides,
  } as unknown as Stripe;
}

function makeStripeSubscription(
  overrides: Record<string, unknown> = {},
): Stripe.Subscription {
  return {
    id: "sub_test_001",
    status: "active",
    metadata: { tenantId: "tenant-1" },
    cancel_at_period_end: false,
    canceled_at: null,
    trial_start: null,
    trial_end: null,
    current_period_start: Math.floor(Date.now() / 1000),
    current_period_end: Math.floor(Date.now() / 1000) + 30 * 24 * 3600,
    customer: "cus_001",
    items: { data: [{ id: "si_001", price: { id: "price_starter_monthly" } }] },
    ...overrides,
  } as unknown as Stripe.Subscription;
}

function makeStripeInvoice(
  overrides: Record<string, unknown> = {},
): Stripe.Invoice {
  return {
    id: "in_test_001",
    status: "paid",
    number: "INV-001",
    currency: "usd",
    subtotal: 2900,
    total: 2900,
    amount_paid: 2900,
    amount_due: 0,
    due_date: null,
    subscription: "sub_test_001",
    hosted_invoice_url: null,
    invoice_pdf: null,
    lines: { data: [] },
    ...overrides,
  } as unknown as Stripe.Invoice;
}

function makeEvent(
  type: string,
  dataObject: unknown,
  eventIdOverride?: string,
): Stripe.Event {
  return {
    id: eventIdOverride ?? STRIPE_EVENT_ID,
    type,
    data: { object: dataObject },
  } as unknown as Stripe.Event;
}

// ---------------------------------------------------------------------------
// Tests: processWebhook
// ---------------------------------------------------------------------------
describe("PaymentWebhookServiceImpl — processWebhook", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let stripe: ReturnType<typeof makeStripe>;
  let service: PaymentWebhookServiceImpl;

  beforeEach(() => {
    prisma = makePrisma();
    stripe = makeStripe();
    service = new PaymentWebhookServiceImpl(prisma, stripe, WEBHOOK_SECRET);
  });

  describe("signature verification", () => {
    it("rejects events with an invalid Stripe signature and returns processed:false", async () => {
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockImplementation(() => {
        throw new Error(
          "No signatures found matching the expected signature for payload",
        );
      });

      const result = await service.processWebhook({
        payload: "{}",
        signature: "bad-signature",
        provider: "stripe",
      });

      expect(result.processed).toBe(false);
      expect(result.eventType).toBe("unknown");
      expect(result.error).toMatch(/signature verification failed/i);
    });

    it("does NOT create a webhookEvent record when signature verification fails", async () => {
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockImplementation(() => {
        throw new Error("Invalid signature");
      });

      await service.processWebhook({
        payload: "{}",
        signature: "bad-sig",
        provider: "stripe",
      });

      expect(prisma.webhookEvent.create).not.toHaveBeenCalled();
    });
  });

  describe("audit trail", () => {
    it("stores a webhookEvent record BEFORE dispatching the handler (audit trail)", async () => {
      const event = makeEvent(
        "customer.subscription.updated",
        makeStripeSubscription(),
      );
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(prisma.webhookEvent.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            stripeEventId: STRIPE_EVENT_ID,
            type: "customer.subscription.updated",
            processed: false,
          }),
        }),
      );
    });

    it("marks webhookEvent.processed=true after successful event handling", async () => {
      const event = makeEvent(
        "customer.subscription.updated",
        makeStripeSubscription(),
      );
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(prisma.webhookEvent.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { stripeEventId: STRIPE_EVENT_ID },
          data: expect.objectContaining({ processed: true }),
        }),
      );
    });

    it("stores error string on webhookEvent when the event handler throws", async () => {
      (
        prisma.subscription.updateMany as ReturnType<typeof vi.fn>
      ).mockRejectedValue(new Error("DB connection lost"));
      const event = makeEvent(
        "customer.subscription.updated",
        makeStripeSubscription(),
      );
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      const result = await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(result.processed).toBe(false);
      expect(result.error).toMatch(/DB connection lost/);
      expect(prisma.webhookEvent.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { stripeEventId: STRIPE_EVENT_ID },
          data: expect.objectContaining({
            error: expect.stringContaining("DB connection lost"),
          }),
        }),
      );
    });
  });

  describe("customer.subscription.updated event", () => {
    it("updates subscription status and billing period in the database", async () => {
      const stripeSub = makeStripeSubscription({ status: "past_due" });
      const event = makeEvent("customer.subscription.updated", stripeSub);
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      const result = await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(result.processed).toBe(true);
      expect(result.eventType).toBe("customer.subscription.updated");
      expect(prisma.subscription.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { stripeSubscriptionId: "sub_test_001" },
          data: expect.objectContaining({
            status: "PAST_DUE",
            cancelAtPeriodEnd: false,
          }),
        }),
      );
    });

    it("updates cancelAtPeriodEnd when subscription is scheduled for cancellation", async () => {
      const stripeSub = makeStripeSubscription({
        cancel_at_period_end: true,
        canceled_at: Math.floor(Date.now() / 1000),
      });
      const event = makeEvent("customer.subscription.updated", stripeSub);
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(prisma.subscription.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ cancelAtPeriodEnd: true }),
        }),
      );
    });
  });

  describe("customer.subscription.deleted event", () => {
    it("marks the local subscription as CANCELED", async () => {
      const stripeSub = makeStripeSubscription({ status: "canceled" });
      const event = makeEvent("customer.subscription.deleted", stripeSub);
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      const result = await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(result.processed).toBe(true);
      expect(prisma.subscription.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { stripeSubscriptionId: "sub_test_001" },
          data: expect.objectContaining({ status: "CANCELED" }),
        }),
      );
    });
  });

  describe("invoice.paid event", () => {
    it("marks the local invoice as paid with a paidAt timestamp", async () => {
      const stripeInvoice = makeStripeInvoice({
        id: "in_paid_001",
        status: "paid",
        amount_paid: 2900,
        amount_due: 0,
      });
      const event = makeEvent("invoice.paid", stripeInvoice);
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      const result = await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(result.processed).toBe(true);
      expect(prisma.invoice.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { stripeInvoiceId: "in_paid_001" },
          data: expect.objectContaining({
            status: "paid",
            paidAt: expect.any(Date),
            amountPaidCents: 2900,
            amountDueCents: 0,
          }),
        }),
      );
    });
  });

  describe("invoice.payment_failed event", () => {
    it("marks the invoice as open and the active subscription as PAST_DUE in a transaction", async () => {
      const txPrisma = makePrisma();
      (prisma.$transaction as ReturnType<typeof vi.fn>).mockImplementation(
        async (fn: (tx: PrismaClient) => Promise<unknown>) => fn(txPrisma),
      );

      const stripeInvoice = makeStripeInvoice({
        id: "in_failed_001",
        status: "open",
        subscription: "sub_test_001",
        amount_paid: 0,
        amount_due: 2900,
      });
      const event = makeEvent("invoice.payment_failed", stripeInvoice);
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      const result = await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(result.processed).toBe(true);
      expect(txPrisma.invoice.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { stripeInvoiceId: "in_failed_001" },
          data: expect.objectContaining({ status: "open" }),
        }),
      );
      expect(txPrisma.subscription.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { stripeSubscriptionId: "sub_test_001", status: "ACTIVE" },
          data: expect.objectContaining({ status: "PAST_DUE" }),
        }),
      );
    });
  });

  describe("unknown event types", () => {
    it("processes unknown event types without error and returns processed:true", async () => {
      const event = makeEvent("payment_intent.created", {
        id: "pi_test_001",
        amount: 2900,
      });
      (
        stripe.webhooks.constructEvent as ReturnType<typeof vi.fn>
      ).mockReturnValue(event);

      const result = await service.processWebhook({
        payload: "{}",
        signature: "sig_valid",
        provider: "stripe",
      });

      expect(result.processed).toBe(true);
      expect(result.eventType).toBe("payment_intent.created");
    });
  });
});

// ---------------------------------------------------------------------------
// Tests: retryWebhook
// ---------------------------------------------------------------------------
describe("PaymentWebhookServiceImpl — retryWebhook", () => {
  it("returns success:false when the event is not found", async () => {
    const prisma = makePrisma({
      webhookEvent: {
        findUnique: vi.fn().mockResolvedValue(null),
      } as unknown as PrismaClient["webhookEvent"],
    });
    const stripe = makeStripe();
    const service = new PaymentWebhookServiceImpl(
      prisma,
      stripe,
      WEBHOOK_SECRET,
    );

    const result = await service.retryWebhook({ eventId: "nonexistent" });

    expect(result.success).toBe(false);
    expect(result.error).toBe("Event not found");
  });

  it("re-processes a previously failed event and marks it processed", async () => {
    const stripeSub = makeStripeSubscription();
    const stripeEvent = makeEvent("customer.subscription.updated", stripeSub);

    const prisma = makePrisma({
      webhookEvent: {
        findUnique: vi.fn().mockResolvedValue({
          id: "we-failed-1",
          stripeEventId: STRIPE_EVENT_ID,
          type: "customer.subscription.updated",
          processed: false,
        }),
        update: vi.fn().mockResolvedValue({}),
      } as unknown as PrismaClient["webhookEvent"],
    });
    const stripe = makeStripe({
      events: { retrieve: vi.fn().mockResolvedValue(stripeEvent) },
    });
    const service = new PaymentWebhookServiceImpl(
      prisma,
      stripe,
      WEBHOOK_SECRET,
    );

    const result = await service.retryWebhook({ eventId: "we-failed-1" });

    expect(result.success).toBe(true);
    expect(stripe.events.retrieve).toHaveBeenCalledWith(STRIPE_EVENT_ID);
    expect(prisma.webhookEvent.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "we-failed-1" },
        data: expect.objectContaining({ processed: true, error: null }),
      }),
    );
  });
});
