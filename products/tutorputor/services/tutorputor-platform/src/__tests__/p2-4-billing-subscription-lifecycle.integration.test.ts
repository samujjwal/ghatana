/**
 * P2-4: Billing Subscription Lifecycle Integration Tests
 *
 * Validates the HTTP contract for the full subscription lifecycle:
 *   1. GET /api/v1/payments/plans — list available plans (no subscription required)
 *   2. POST /api/v1/payments/subscriptions — create a free-tier subscription
 *   3. GET /api/v1/payments/subscription — retrieve active subscription
 *   4. POST /api/v1/payments/subscription/change — upgrade plan
 *   5. POST /api/v1/payments/subscription/cancel — cancel at period end
 *   6. POST /api/v1/payments/portal — returns 503 (Stripe not configured on this deployment)
 *   7. Input validation guards: missing/invalid fields → 400
 *   8. Missing subscription → 404 on cancel / change
 *
 * Uses the real Fastify server via createServer() with a Prisma stub and a
 * vi.mock("stripe") so the Stripe SDK never makes real network calls.
 *
 * @doc.type test-suite
 * @doc.purpose Prove correctness of subscription lifecycle HTTP contract
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
import { createServer } from "../setup.js";

// ---------------------------------------------------------------------------
// Mock Stripe SDK — prevents real network calls during tests
// ---------------------------------------------------------------------------
vi.mock("stripe", () => {
  const mockCustomerCreate = vi.fn().mockResolvedValue({ id: "cus_test123" });
  const mockCustomerUpdate = vi.fn().mockResolvedValue({ id: "cus_test123" });
  const mockSubscriptionCreate = vi.fn().mockResolvedValue({
    id: "sub_test123",
    status: "active",
    current_period_start: Math.floor(Date.now() / 1000),
    current_period_end: Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60,
    trial_start: null,
    trial_end: null,
  });
  const mockSubscriptionUpdate = vi.fn().mockResolvedValue({
    id: "sub_test123",
    status: "active",
    items: { data: [{ id: "si_test", price: "price_new" }] },
  });
  const mockSubscriptionCancel = vi.fn().mockResolvedValue({
    id: "sub_test123",
    status: "canceled",
  });
  const mockSubscriptionRetrieve = vi.fn().mockResolvedValue({
    id: "sub_test123",
    status: "active",
    items: { data: [{ id: "si_test", price: "price_starter" }] },
  });

  class MockStripe {
    customers = {
      create: mockCustomerCreate,
      update: mockCustomerUpdate,
    };

    subscriptions = {
      create: mockSubscriptionCreate,
      update: mockSubscriptionUpdate,
      cancel: mockSubscriptionCancel,
      retrieve: mockSubscriptionRetrieve,
    };

    paymentMethods = {
      attach: vi.fn().mockResolvedValue({}),
    };

    invoices = {
      retrieveUpcoming: vi.fn().mockResolvedValue({ amount_due: 0 }),
    };

    constructor(_key: string, _options: Record<string, unknown>) {}
  }

  return { default: MockStripe };
});

// ---------------------------------------------------------------------------
// Prisma stub — in-memory subscription store
// ---------------------------------------------------------------------------

type SubscriptionRow = {
  id: string;
  tenantId: string;
  stripeCustomerId: string;
  stripeSubscriptionId: string | null;
  stripePriceId: string;
  tier: string;
  status: string;
  billingInterval: string;
  currentPeriodStart: Date;
  currentPeriodEnd: Date;
  cancelAtPeriodEnd: boolean;
  canceledAt: Date | null;
  trialStart: Date | null;
  trialEnd: Date | null;
  createdAt: Date;
  updatedAt: Date;
};

function createBillingPrismaStub(): PrismaClient {
  const subscriptions = new Map<string, SubscriptionRow>();
  const stripeCustomers = new Map<string, { id: string; tenantId: string; stripeCustomerId: string; email: string }>();
  let idCounter = 0;

  function nextId(prefix: string): string {
    return `${prefix}-${++idCounter}`;
  }

  const subscription = {
    create: vi.fn().mockImplementation(({ data }: { data: Record<string, unknown> }) => {
      const id = nextId("sub");
      const now = new Date();
      const row: SubscriptionRow = {
        id,
        tenantId: String(data.tenantId),
        stripeCustomerId: String(data.stripeCustomerId ?? ""),
        stripeSubscriptionId: typeof data.stripeSubscriptionId === "string" ? data.stripeSubscriptionId : null,
        stripePriceId: String(data.stripePriceId ?? ""),
        tier: String(data.tier ?? "FREE"),
        status: String(data.status ?? "ACTIVE"),
        billingInterval: String(data.billingInterval ?? "MONTHLY"),
        currentPeriodStart: data.currentPeriodStart instanceof Date ? data.currentPeriodStart : now,
        currentPeriodEnd: data.currentPeriodEnd instanceof Date ? data.currentPeriodEnd : new Date(now.getTime() + 365 * 24 * 60 * 60 * 1000),
        cancelAtPeriodEnd: Boolean(data.cancelAtPeriodEnd ?? false),
        canceledAt: data.canceledAt instanceof Date ? data.canceledAt : null,
        trialStart: data.trialStart instanceof Date ? data.trialStart : null,
        trialEnd: data.trialEnd instanceof Date ? data.trialEnd : null,
        createdAt: now,
        updatedAt: now,
      };
      subscriptions.set(id, row);
      return Promise.resolve({ ...row });
    }),

    findFirst: vi.fn().mockImplementation(({ where }: { where: Record<string, unknown> }) => {
      const tenantId = typeof where.tenantId === "string" ? where.tenantId : undefined;
      const excludedStatuses = (where.status as { notIn?: string[] } | undefined)?.notIn ?? [];

      const match = Array.from(subscriptions.values())
        .filter((s) => (!tenantId || s.tenantId === tenantId) && !excludedStatuses.includes(s.status))
        .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())[0];

      return Promise.resolve(match ? { ...match } : null);
    }),

    findUnique: vi.fn().mockImplementation(({ where }: { where: Record<string, unknown> }) => {
      const id = typeof where.id === "string" ? where.id : undefined;
      if (!id) return Promise.resolve(null);
      const row = subscriptions.get(id);
      return Promise.resolve(row ? { ...row } : null);
    }),

    update: vi.fn().mockImplementation(({ where, data }: { where: Record<string, unknown>; data: Record<string, unknown> }) => {
      const id = typeof where.id === "string" ? where.id : undefined;
      if (!id) return Promise.reject(new Error("Missing id in subscription.update"));
      const existing = subscriptions.get(id);
      if (!existing) return Promise.reject(new Error(`Subscription not found: ${id}`));
      const updated: SubscriptionRow = {
        ...existing,
        ...data,
        updatedAt: new Date(),
      } as SubscriptionRow;
      subscriptions.set(id, updated);
      return Promise.resolve({ ...updated });
    }),
  };

  const stripeCustomer = {
    findUnique: vi.fn().mockImplementation(({ where }: { where: Record<string, unknown> }) => {
      const tenantId = typeof where.tenantId === "string" ? where.tenantId : undefined;
      if (!tenantId) return Promise.resolve(null);
      const customer = stripeCustomers.get(tenantId);
      return Promise.resolve(customer ? { ...customer } : null);
    }),

    create: vi.fn().mockImplementation(({ data }: { data: Record<string, unknown> }) => {
      const id = nextId("sc");
      const record = {
        id,
        tenantId: String(data.tenantId),
        stripeCustomerId: String(data.stripeCustomerId ?? "cus_test123"),
        email: String(data.email ?? ""),
      };
      stripeCustomers.set(String(data.tenantId), record);
      return Promise.resolve({ ...record });
    }),
  };

  const paymentMethod = {
    findUnique: vi.fn().mockResolvedValue(null),
  };

  return {
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
    $queryRaw: vi.fn().mockResolvedValue([{ 1: 1n }]),
    subscription,
    stripeCustomer,
    paymentMethod,
    userConsent: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    tenant: {
      findUnique: vi.fn().mockResolvedValue({
        id: "tenant-1",
        name: "Test University",
        adminEmail: "admin@test.edu",
      }),
    },
    user: {
      findUnique: vi.fn().mockResolvedValue(null),
    },
  } as unknown as PrismaClient;
}

function createMockRedis() {
  const storage = new Map<string, string>();
  return {
    get: vi.fn((key: string) => Promise.resolve(storage.get(key) ?? null)),
    set: vi.fn((key: string, value: string) => {
      storage.set(key, value);
      return Promise.resolve("OK");
    }),
    expire: vi.fn(() => Promise.resolve(1)),
    del: vi.fn((key: string) => {
      storage.delete(key);
      return Promise.resolve(1);
    }),
    ping: vi.fn().mockResolvedValue("PONG"),
    connect: vi.fn().mockResolvedValue(undefined),
    disconnect: vi.fn().mockResolvedValue(undefined),
    duplicate: vi.fn().mockReturnValue({
      subscribe: vi.fn().mockResolvedValue(undefined),
      unsubscribe: vi.fn().mockResolvedValue(undefined),
      on: vi.fn(),
      removeAllListeners: vi.fn(),
      disconnect: vi.fn(),
    }),
    publish: vi.fn().mockResolvedValue(1),
  };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe("P2-4: Billing subscription lifecycle — HTTP contract", () => {
  let app: FastifyInstance;
  let prismaStub: PrismaClient;
  let savedStripeKey: string | undefined;

  beforeEach(async () => {
    savedStripeKey = process.env.STRIPE_SECRET_KEY;
    process.env.STRIPE_SECRET_KEY = "stripe_test_placeholder_secret";

    prismaStub = createBillingPrismaStub();
    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
      prisma: prismaStub,
      redis: createMockRedis() as never,
    });
    await app.ready();
  });

  afterEach(async () => {
    await app?.close();
    vi.clearAllMocks();
    if (savedStripeKey === undefined) {
      delete process.env.STRIPE_SECRET_KEY;
    } else {
      process.env.STRIPE_SECRET_KEY = savedStripeKey;
    }
  });

  // -------------------------------------------------------------------------
  // Auth helpers
  // -------------------------------------------------------------------------

  function tokenFor(
    role: string,
    tenantId: string = "tenant-1",
    sub: string = `user-${role}`,
  ): string {
    return app.jwt.sign({ sub, tenantId, role });
  }

  // -------------------------------------------------------------------------
  // 1. GET /api/v1/payments/plans
  // -------------------------------------------------------------------------

  describe("GET /api/v1/payments/plans", () => {
    it("returns the list of available subscription plans → 200", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/payments/plans",
        headers: { authorization: `Bearer ${token}` },
      });

      expect(res.statusCode).toBe(200);
      const body = res.json() as unknown[];
      expect(Array.isArray(body)).toBe(true);
      expect(body.length).toBeGreaterThan(0);
    });

    it("plan objects include required fields", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/payments/plans",
        headers: { authorization: `Bearer ${token}` },
      });

      expect(res.statusCode).toBe(200);
      const plans = res.json() as Array<Record<string, unknown>>;
      const freePlan = plans.find((p) => p["id"] === "plan_free");
      expect(freePlan).toBeDefined();
      expect(freePlan!["name"]).toBe("Free");
      expect(freePlan!["tier"]).toBe("free");
      expect(Array.isArray(freePlan!["pricing"])).toBe(true);
      expect(Array.isArray(freePlan!["features"])).toBe(true);
    });

    it("unauthenticated request → 401", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/payments/plans",
      });
      expect(res.statusCode).toBe(401);
    });
  });

  // -------------------------------------------------------------------------
  // 2. POST /api/v1/payments/subscriptions — create free subscription
  // -------------------------------------------------------------------------

  describe("POST /api/v1/payments/subscriptions", () => {
    it("creates a free-tier subscription → 201 with subscription data", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: {
          planId: "plan_free",
          billingInterval: "monthly",
        },
      });

      expect(res.statusCode).toBe(201);
      const body = res.json() as Record<string, unknown>;
      expect(body.id).toBeTruthy();
      expect(body.tenantId).toBe("tenant-1");
      expect(body.tier).toBe("free");
      expect(body.status).toBe("active");
    });

    it("returns 400 on missing planId", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { billingInterval: "monthly" },
      });
      expect(res.statusCode).toBe(400);
    });

    it("returns 400 on invalid billingInterval", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_free", billingInterval: "weekly" },
      });
      expect(res.statusCode).toBe(400);
    });

    it("returns 404 on unknown planId", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_nonexistent", billingInterval: "monthly" },
      });
      expect(res.statusCode).toBe(404);
    });

    it("unauthenticated request → 401", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        payload: { planId: "plan_free", billingInterval: "monthly" },
      });
      expect(res.statusCode).toBe(401);
    });
  });

  // -------------------------------------------------------------------------
  // 3. GET /api/v1/payments/subscription — retrieve active subscription
  // -------------------------------------------------------------------------

  describe("GET /api/v1/payments/subscription", () => {
    it("returns 404 when no active subscription exists", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/payments/subscription",
        headers: { authorization: `Bearer ${token}` },
      });
      expect(res.statusCode).toBe(404);
    });

    it("returns active subscription after creation", async () => {
      const token = tokenFor("admin");

      // Create a subscription first
      const createRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_free", billingInterval: "monthly" },
      });
      expect(createRes.statusCode).toBe(201);

      // Now retrieve it
      const getRes = await app.inject({
        method: "GET",
        url: "/api/v1/payments/subscription",
        headers: { authorization: `Bearer ${token}` },
      });
      expect(getRes.statusCode).toBe(200);
      const sub = getRes.json() as Record<string, unknown>;
      expect(sub.id).toBeTruthy();
      expect(sub.tenantId).toBe("tenant-1");
      expect(sub.status).toBe("active");
    });
  });

  // -------------------------------------------------------------------------
  // 4. POST /api/v1/payments/subscription/change — upgrade plan
  // -------------------------------------------------------------------------

  describe("POST /api/v1/payments/subscription/change", () => {
    it("returns 404 when no active subscription exists", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/change",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_starter", billingInterval: "monthly" },
      });
      expect(res.statusCode).toBe(404);
    });

    it("changes the subscription plan → 200 with updated tier", async () => {
      const token = tokenFor("admin");

      // Create free subscription
      const createRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_free", billingInterval: "monthly" },
      });
      expect(createRes.statusCode).toBe(201);

      // Change to starter plan
      const changeRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/change",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_starter", billingInterval: "monthly" },
      });
      expect(changeRes.statusCode).toBe(200);
      const body = changeRes.json() as Record<string, unknown>;
      expect(body.tier).toBe("starter");
    });

    it("returns 400 on missing planId", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/change",
        headers: { authorization: `Bearer ${token}` },
        payload: { billingInterval: "monthly" },
      });
      expect(res.statusCode).toBe(400);
    });
  });

  // -------------------------------------------------------------------------
  // 5. POST /api/v1/payments/subscription/cancel — cancel subscription
  // -------------------------------------------------------------------------

  describe("POST /api/v1/payments/subscription/cancel", () => {
    it("returns 404 when no active subscription exists", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/cancel",
        headers: { authorization: `Bearer ${token}` },
        payload: {},
      });
      expect(res.statusCode).toBe(404);
    });

    it("cancels at period end → 200 with cancelAtPeriodEnd=true", async () => {
      const token = tokenFor("admin");

      // Create free subscription
      const createRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_free", billingInterval: "monthly" },
      });
      expect(createRes.statusCode).toBe(201);

      // Cancel at period end (default atPeriodEnd=true)
      const cancelRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/cancel",
        headers: { authorization: `Bearer ${token}` },
        payload: { atPeriodEnd: true },
      });
      expect(cancelRes.statusCode).toBe(200);
      const body = cancelRes.json() as Record<string, unknown>;
      expect(body.cancelAtPeriodEnd).toBe(true);
    });

    it("cancels immediately → 200 with CANCELED status", async () => {
      const token = tokenFor("admin");

      // Create free subscription
      await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_free", billingInterval: "monthly" },
      });

      // Cancel immediately
      const cancelRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/cancel",
        headers: { authorization: `Bearer ${token}` },
        payload: { atPeriodEnd: false, reason: "Not needed anymore" },
      });
      expect(cancelRes.statusCode).toBe(200);
      const body = cancelRes.json() as Record<string, unknown>;
      expect(body.status).toBe("canceled");
    });
  });

  // -------------------------------------------------------------------------
  // 6. POST /api/v1/payments/portal — billing portal (Stripe not configured)
  // -------------------------------------------------------------------------

  describe("POST /api/v1/payments/portal", () => {
    it("returns 503 FeatureNotEnabled when Stripe portal is not fully configured", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/portal",
        headers: { authorization: `Bearer ${token}` },
        payload: { returnUrl: "https://app.example.com/billing" },
      });

      expect(res.statusCode).toBe(503);
      const body = res.json() as Record<string, unknown>;
      expect(body.error).toBe("FeatureNotEnabled");
      expect(typeof body.message).toBe("string");
    });

    it("returns 503 when no STRIPE_SECRET_KEY is set", async () => {
      const token = tokenFor("admin");
      // Temporarily remove key
      delete process.env.STRIPE_SECRET_KEY;

      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/portal",
        headers: { authorization: `Bearer ${token}` },
        payload: {},
      });

      expect(res.statusCode).toBe(503);
      const body = res.json() as Record<string, unknown>;
      expect(body.error).toBe("FeatureNotEnabled");
    });

    it("returns 400 on invalid returnUrl", async () => {
      const token = tokenFor("admin");
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/payments/portal",
        headers: { authorization: `Bearer ${token}` },
        payload: { returnUrl: "not-a-valid-url" },
      });
      expect(res.statusCode).toBe(400);
    });
  });

  // -------------------------------------------------------------------------
  // 7. Full subscription lifecycle: create → get → change → cancel
  // -------------------------------------------------------------------------

  describe("Full lifecycle: create → get → change plan → cancel", () => {
    it("completes the full billing lifecycle successfully", async () => {
      const token = tokenFor("admin");

      // Step 1: Create subscription
      const createRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscriptions",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_free", billingInterval: "monthly" },
      });
      expect(createRes.statusCode).toBe(201);
      const created = createRes.json() as Record<string, unknown>;
      expect(created.status).toBe("active");
      expect(created.tier).toBe("free");

      // Step 2: Get current subscription
      const getRes = await app.inject({
        method: "GET",
        url: "/api/v1/payments/subscription",
        headers: { authorization: `Bearer ${token}` },
      });
      expect(getRes.statusCode).toBe(200);
      const current = getRes.json() as Record<string, unknown>;
      expect(current.id).toBe(created.id);

      // Step 3: Upgrade to starter plan
      const changeRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/change",
        headers: { authorization: `Bearer ${token}` },
        payload: { planId: "plan_starter", billingInterval: "annual" },
      });
      expect(changeRes.statusCode).toBe(200);
      const upgraded = changeRes.json() as Record<string, unknown>;
      expect(upgraded.tier).toBe("starter");

      // Step 4: Cancel the subscription
      const cancelRes = await app.inject({
        method: "POST",
        url: "/api/v1/payments/subscription/cancel",
        headers: { authorization: `Bearer ${token}` },
        payload: { atPeriodEnd: true, reason: "End of academic year" },
      });
      expect(cancelRes.statusCode).toBe(200);
      const cancelled = cancelRes.json() as Record<string, unknown>;
      expect(cancelled.cancelAtPeriodEnd).toBe(true);
    });
  });
});
