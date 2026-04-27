import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createServer } from "../setup.js";
import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";

type ConsentRow = {
  tenantId: string;
  userId: string;
  category: string;
  granted: boolean;
};

/** Create a minimal Prisma stub with an in-memory userConsent store. */
function createConsentPrismaStub(): PrismaClient & {
  userConsent: {
    create: (args: { data: ConsentRow }) => Promise<ConsentRow>;
    createMany: (args: { data: ConsentRow[] }) => Promise<{ count: number }>;
    findMany: (args: {
      where: { tenantId: string; userId: string; granted: true };
      select: { category: true };
    }) => Promise<Array<{ category: string }>>;
  };
  _consentStore: ConsentRow[];
} {
  const store: ConsentRow[] = [];

  const userConsent = {
    create: vi.fn().mockImplementation(({ data }: { data: ConsentRow }) => {
      store.push({ ...data });
      return Promise.resolve({ ...data });
    }),
    createMany: vi.fn().mockImplementation(({ data }: { data: ConsentRow[] }) => {
      for (const row of data) {
        store.push({ ...row });
      }
      return Promise.resolve({ count: data.length });
    }),
    findMany: vi.fn().mockImplementation(({
      where,
    }: {
      where: { tenantId: string; userId: string; granted: true };
    }) => {
      const results = store.filter(
        (r) =>
          r.tenantId === where.tenantId &&
          r.userId === where.userId &&
          r.granted === true,
      );
      return Promise.resolve(results.map((r) => ({ category: r.category })));
    }),
  };

  return {
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
    $queryRaw: vi.fn().mockResolvedValue([{ 1: 1n }]),
    $transaction: vi.fn().mockImplementation(
      async (fn: (tx: unknown) => Promise<unknown>) => fn({}),
    ),
    userConsent,
    tenant: {
      findUnique: vi.fn().mockResolvedValue({ id: "tenant-1" }),
    },
    user: {
      findUnique: vi.fn().mockResolvedValue(null),
    },
    _consentStore: store,
  } as unknown as PrismaClient & {
    userConsent: typeof userConsent;
    _consentStore: ConsentRow[];
  };
}

function createMockRedis() {
  const storage = new Map<string, string>();
  return {
    get: vi.fn((key: string) => Promise.resolve(storage.get(key) ?? null)),
    set: vi.fn((key: string, value: string) => {
      storage.set(key, value);
      return Promise.resolve("OK");
    }),
    incr: vi.fn((key: string) => {
      const current = Number(storage.get(key) ?? "0") + 1;
      storage.set(key, String(current));
      return Promise.resolve(current);
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

describe("Consent enforcement integration tests", () => {
  let app: FastifyInstance;
  let prismaStub: ReturnType<typeof createConsentPrismaStub>;

  let savedStripeKey: string | undefined;

  beforeEach(async () => {
    savedStripeKey = process.env.STRIPE_SECRET_KEY;
    process.env.STRIPE_SECRET_KEY = "stripe_test_placeholder_secret";

    prismaStub = createConsentPrismaStub();
    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
      prisma: prismaStub as unknown as PrismaClient,
      redis: createMockRedis(),
    });
    await app.ready();
  });

  afterEach(async () => {
    await app?.close();
    if (savedStripeKey === undefined) {
      delete process.env.STRIPE_SECRET_KEY;
    } else {
      process.env.STRIPE_SECRET_KEY = savedStripeKey;
    }
  });

  it("should block AI endpoints without ai_processing consent", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    // Should return 451 (Unavailable For Legal Reasons) for missing consent
    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining(["ai_processing"]),
    });
  });

  it("should block analytics endpoints without analytics consent", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/analytics/dashboard",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining(["analytics"]),
    });
  });

  it("should block third-party integration routes without third_party_sharing consent", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/integration/marketplace/listings",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining(["third_party_sharing"]),
    });
  });

  it("should allow AI endpoints with ai_processing consent", async () => {
    // Create user consent record
    await prismaStub.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "user-1",
        category: "ai_processing",
        granted: true,
      },
    });

    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    // Should not be 451 (consent error) - might be 404 or other response depending on implementation
    expect(response.statusCode).not.toBe(451);
  });

  it("should allow analytics endpoints with analytics consent", async () => {
    await prismaStub.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "user-1",
        category: "analytics",
        granted: true,
      },
    });

    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/analytics/dashboard",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).not.toBe(451);
  });

  it("should exempt admin roles from consent requirements", async () => {
    const token = app.jwt.sign({
      sub: "admin-user",
      tenantId: "tenant-1",
      role: "admin",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    // Admin should not be blocked by consent requirements
    expect(response.statusCode).not.toBe(451);
  });

  it("should skip consent check for unauthenticated requests", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/analytics/dashboard",
    });

    // Should be 401 (unauthorized) not 451 (consent required)
    expect(response.statusCode).toBe(401);
  });

  it("should allow routes without consent requirements", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/modules",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    // Should not be 451 - modules route doesn't require consent
    expect(response.statusCode).not.toBe(451);
  });

  it("should require multiple consent categories for recommendations", async () => {
    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/recommendations/next",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining([
        "analytics",
        "ai_processing",
      ]),
    });
  });

  it("should allow recommendations with all required consents", async () => {
    await prismaStub.userConsent.createMany({
      data: [
        {
          tenantId: "tenant-1",
          userId: "user-1",
          category: "analytics",
          granted: true,
        },
        {
          tenantId: "tenant-1",
          userId: "user-1",
          category: "ai_processing",
          granted: true,
        },
      ],
    });

    const token = app.jwt.sign({
      sub: "user-1",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "GET",
      url: "/api/v1/recommendations/next",
      headers: {
        authorization: `Bearer ${token}`,
      },
    });

    expect(response.statusCode).not.toBe(451);
  });

  it("should block when consent has been revoked", async () => {
    await prismaStub.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "user-revoked",
        category: "ai_processing",
        granted: false,
      },
    });

    const token = app.jwt.sign({
      sub: "user-revoked",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining(["ai_processing"]),
    });
  });

  it("should block minors without guardian_consent", async () => {
    await prismaStub.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "minor-user",
        category: "ai_processing",
        granted: true,
      },
    });

    const token = app.jwt.sign({
      sub: "minor-user",
      tenantId: "tenant-1",
      role: "learner",
      isMinor: true,
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    expect(response.statusCode).toBe(451);
    expect(response.json()).toMatchObject({
      error: "Consent Required",
      missingConsent: expect.arrayContaining(["guardian_consent"]),
    });
  });

  it("should allow minors with guardian_consent and route consent", async () => {
    await prismaStub.userConsent.createMany({
      data: [
        {
          tenantId: "tenant-1",
          userId: "minor-user-ok",
          category: "ai_processing",
          granted: true,
        },
        {
          tenantId: "tenant-1",
          userId: "minor-user-ok",
          category: "guardian_consent",
          granted: true,
        },
      ],
    });

    const token = app.jwt.sign({
      sub: "minor-user-ok",
      tenantId: "tenant-1",
      role: "learner",
      ageGroup: "minor",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    expect(response.statusCode).not.toBe(451);
  });

  it("should use JWT sub claim for user identity", async () => {
    await prismaStub.userConsent.create({
      data: {
        tenantId: "tenant-1",
        userId: "user-with-sub",
        category: "ai_processing",
        granted: true,
      },
    });

    const token = app.jwt.sign({
      sub: "user-with-sub",
      tenantId: "tenant-1",
      role: "learner",
    });

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/ai/tutor/query",
      headers: {
        authorization: `Bearer ${token}`,
      },
      body: {
        query: "test question",
      },
    });

    expect(response.statusCode).not.toBe(451);
  });
});

