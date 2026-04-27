/**
 * P1-5: ABAC Route Matrix Integration Tests
 *
 * Verifies role and tenant scoped behavior for generation route family:
 * - student / teacher denied for privileged actions
 * - admin / content_creator / superadmin allowed
 * - tenant isolation enforced on resource reads and mutations
 *
 * @doc.type test-suite
 * @doc.purpose Route-level ABAC/RBAC matrix validation for sensitive generation operations
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
import { createServer } from "../setup.js";

type GenerationRequestRow = {
  id: string;
  tenantId: string;
  title: string;
  description: string | null;
  domain: string;
  conceptId: string | null;
  targetGrades: string[] | null;
  requestedBy: string;
  requestConfig: Record<string, unknown> | null;
  status: string;
  plannedAssets: unknown | null;
  artifactNeeds: unknown | null;
  riskLevel: string | null;
  riskFactors: string[] | null;
  reviewPath: string | null;
  estimatedCost: unknown | null;
  routingDecision: unknown | null;
  totalJobs: number;
  completedJobs: number;
  failedJobs: number;
  plannedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

type GenerationJobRow = {
  id: string;
  requestId: string;
  jobType: string;
  targetRef: string | null;
  inputPrompt: string | null;
  parameters: Record<string, unknown> | null;
  status: string;
  progress: number;
  outputAssetId: string | null;
  outputData: Record<string, unknown> | null;
  diagnostics: Record<string, unknown> | null;
  errorMessage: string | null;
  retryCount: number;
  maxRetries: number;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

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

function createGenerationPrismaStub(): PrismaClient {
  const requests = new Map<string, GenerationRequestRow>();
  const jobs = new Map<string, GenerationJobRow>();
  let requestCounter = 0;
  let jobCounter = 0;

  const nowIso = (): string => new Date().toISOString();

  const generationRequest = {
    create: vi.fn().mockImplementation(({ data }: { data: Record<string, unknown> }) => {
      const now = nowIso();
      const id = `req-${++requestCounter}`;
      const row: GenerationRequestRow = {
        id,
        tenantId: String(data.tenantId),
        title: String(data.title),
        description: typeof data.description === "string" ? data.description : null,
        domain: String(data.domain),
        conceptId: typeof data.conceptId === "string" ? data.conceptId : null,
        targetGrades: Array.isArray(data.targetGrades) ? (data.targetGrades as string[]) : null,
        requestedBy: String(data.requestedBy),
        requestConfig: data.requestConfig && typeof data.requestConfig === "object"
          ? (data.requestConfig as Record<string, unknown>)
          : null,
        status: typeof data.status === "string" ? data.status : "DRAFT",
        plannedAssets: null,
        artifactNeeds: null,
        riskLevel: null,
        riskFactors: null,
        reviewPath: null,
        estimatedCost: null,
        routingDecision: null,
        totalJobs: 0,
        completedJobs: 0,
        failedJobs: 0,
        plannedAt: null,
        startedAt: null,
        completedAt: null,
        createdAt: now,
        updatedAt: now,
      };
      requests.set(id, row);
      return Promise.resolve({ ...row });
    }),

    findFirst: vi.fn().mockImplementation(({ where, include, select }: { where: Record<string, unknown>; include?: Record<string, boolean>; select?: Record<string, boolean> }) => {
      const id = typeof where.id === "string" ? where.id : undefined;
      const tenantId = typeof where.tenantId === "string" ? where.tenantId : undefined;

      const candidate = id ? requests.get(id) : undefined;
      if (!candidate) {
        return Promise.resolve(null);
      }

      if (tenantId && candidate.tenantId !== tenantId) {
        return Promise.resolve(null);
      }

      if (select) {
        const selected: Record<string, unknown> = {};
        for (const key of Object.keys(select)) {
          if (select[key]) {
            selected[key] = (candidate as unknown as Record<string, unknown>)[key];
          }
        }
        return Promise.resolve(selected);
      }

      if (include?.jobs) {
        const requestJobs = Array.from(jobs.values()).filter((job) => job.requestId === candidate.id);
        return Promise.resolve({ ...candidate, jobs: requestJobs });
      }

      return Promise.resolve({ ...candidate });
    }),

    findMany: vi.fn().mockImplementation(({ where = {}, take, skip }: { where?: Record<string, unknown>; take?: number; skip?: number }) => {
      const tenantId = typeof where.tenantId === "string" ? where.tenantId : undefined;
      const status = typeof where.status === "string" ? where.status : undefined;

      let rows = Array.from(requests.values());
      if (tenantId) {
        rows = rows.filter((r) => r.tenantId === tenantId);
      }
      if (status) {
        rows = rows.filter((r) => r.status === status);
      }

      const start = skip ?? 0;
      const end = take ? start + take : undefined;
      return Promise.resolve(rows.slice(start, end));
    }),

    count: vi.fn().mockImplementation(({ where = {} }: { where?: Record<string, unknown> }) => {
      const tenantId = typeof where.tenantId === "string" ? where.tenantId : undefined;
      const rows = Array.from(requests.values()).filter((r) => (tenantId ? r.tenantId === tenantId : true));
      return Promise.resolve(rows.length);
    }),

    update: vi.fn().mockImplementation(({ where, data }: { where: Record<string, unknown>; data: Record<string, unknown> }) => {
      const id = String(where.id);
      const existing = requests.get(id);
      if (!existing) {
        return Promise.reject(new Error(`Request not found: ${id}`));
      }

      const updated: GenerationRequestRow = {
        ...existing,
        ...data,
        updatedAt: nowIso(),
      } as GenerationRequestRow;
      requests.set(id, updated);
      return Promise.resolve({ ...updated });
    }),
  };

  const generationJob = {
    create: vi.fn().mockImplementation(({ data }: { data: Record<string, unknown> }) => {
      const now = nowIso();
      const id = `job-${++jobCounter}`;
      const row: GenerationJobRow = {
        id,
        requestId: String(data.requestId),
        jobType: String(data.jobType),
        targetRef: typeof data.targetRef === "string" ? data.targetRef : null,
        inputPrompt: typeof data.inputPrompt === "string" ? data.inputPrompt : null,
        parameters: data.parameters && typeof data.parameters === "object"
          ? (data.parameters as Record<string, unknown>)
          : null,
        status: typeof data.status === "string" ? data.status : "PENDING",
        progress: typeof data.progress === "number" ? data.progress : 0,
        outputAssetId: null,
        outputData: null,
        diagnostics: null,
        errorMessage: null,
        retryCount: 0,
        maxRetries: 3,
        startedAt: null,
        completedAt: null,
        createdAt: now,
        updatedAt: now,
      };
      jobs.set(id, row);
      return Promise.resolve({ ...row });
    }),

    updateMany: vi.fn().mockImplementation(({ where, data }: { where: Record<string, unknown>; data: Record<string, unknown> }) => {
      const requestId = typeof where.requestId === "string" ? where.requestId : undefined;
      const statuses = Array.isArray((where.status as { in?: string[] } | undefined)?.in)
        ? ((where.status as { in: string[] }).in)
        : undefined;

      let count = 0;
      for (const [id, row] of jobs) {
        if (requestId && row.requestId !== requestId) continue;
        if (statuses && !statuses.includes(row.status)) continue;

        jobs.set(id, {
          ...row,
          ...data,
          updatedAt: nowIso(),
        } as GenerationJobRow);
        count += 1;
      }

      return Promise.resolve({ count });
    }),
  };

  const prisma = {
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
    $queryRaw: vi.fn().mockResolvedValue([{ 1: 1n }]),
    $transaction: vi.fn().mockImplementation(async (fn: (tx: { generationJob: typeof generationJob; generationRequest: typeof generationRequest }) => Promise<unknown>) => {
      return fn({ generationJob, generationRequest });
    }),
    generationRequest,
    generationJob,
    userConsent: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    tenant: {
      findUnique: vi.fn().mockResolvedValue({ id: "tenant-1" }),
    },
    user: {
      findUnique: vi.fn().mockResolvedValue(null),
    },
  };

  return prisma as unknown as PrismaClient;
}

describe("P1-5 ABAC route matrix — generation routes", () => {
  let app: FastifyInstance;
  let prismaStub: PrismaClient;
  let savedStripeKey: string | undefined;

  beforeEach(async () => {
    savedStripeKey = process.env.STRIPE_SECRET_KEY;
    process.env.STRIPE_SECRET_KEY = "stripe_test_placeholder_secret";

    prismaStub = createGenerationPrismaStub();
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
    if (savedStripeKey === undefined) {
      delete process.env.STRIPE_SECRET_KEY;
    } else {
      process.env.STRIPE_SECRET_KEY = savedStripeKey;
    }
  });

  function tokenFor(role: string, tenantId: string = "tenant-1", sub: string = `${role}-user`): string {
    return app.jwt.sign({ sub, tenantId, role });
  }

  async function createRequestAs(role: string, tenantId: string = "tenant-1"): Promise<string> {
    const response = await app.inject({
      method: "POST",
      url: "/api/generation/requests",
      headers: { authorization: `Bearer ${tokenFor(role, tenantId)}` },
      payload: {
        title: "Kinematics Basics",
        domain: "physics",
        targetGrades: ["GRADE_9_12"],
      },
    });

    expect(response.statusCode).toBe(201);
    const body = response.json() as { id: string };
    return body.id;
  }

  it("student cannot create/list/read/plan/cancel/execute generation requests", async () => {
    const createRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests",
      headers: { authorization: `Bearer ${tokenFor("student")}` },
      payload: { title: "Newton", domain: "physics" },
    });
    expect(createRes.statusCode).toBe(403);

    const listRes = await app.inject({
      method: "GET",
      url: "/api/generation/requests",
      headers: { authorization: `Bearer ${tokenFor("student")}` },
    });
    expect(listRes.statusCode).toBe(403);

    const readRes = await app.inject({
      method: "GET",
      url: "/api/generation/requests/req-does-not-matter",
      headers: { authorization: `Bearer ${tokenFor("student")}` },
    });
    expect(readRes.statusCode).toBe(403);

    const planRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests/req-does-not-matter/plan",
      headers: { authorization: `Bearer ${tokenFor("student")}` },
    });
    expect(planRes.statusCode).toBe(403);

    const cancelRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests/req-does-not-matter/cancel",
      headers: { authorization: `Bearer ${tokenFor("student")}` },
    });
    expect(cancelRes.statusCode).toBe(403);

    const executeRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests/req-does-not-matter/execute",
      headers: { authorization: `Bearer ${tokenFor("student")}` },
    });
    expect(executeRes.statusCode).toBe(403);
  });

  it("teacher cannot create generation requests", async () => {
    const createRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests",
      headers: { authorization: `Bearer ${tokenFor("teacher")}` },
      payload: { title: "Chemistry", domain: "chemistry" },
    });

    expect(createRes.statusCode).toBe(403);
  });

  it("admin, content_creator, and superadmin can create generation requests", async () => {
    const adminRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests",
      headers: { authorization: `Bearer ${tokenFor("admin")}` },
      payload: { title: "Algebra", domain: "mathematics" },
    });
    expect(adminRes.statusCode).toBe(201);

    const creatorRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests",
      headers: { authorization: `Bearer ${tokenFor("content_creator")}` },
      payload: { title: "Biology", domain: "biology" },
    });
    expect(creatorRes.statusCode).toBe(201);

    const superadminRes = await app.inject({
      method: "POST",
      url: "/api/generation/requests",
      headers: { authorization: `Bearer ${tokenFor("superadmin")}` },
      payload: { title: "Economics", domain: "economics" },
    });
    expect(superadminRes.statusCode).toBe(201);
  });

  it("tenant isolation: request created in tenant-a is not accessible by tenant-b", async () => {
    const requestId = await createRequestAs("admin", "tenant-a");

    const readByOtherTenant = await app.inject({
      method: "GET",
      url: `/api/generation/requests/${requestId}`,
      headers: { authorization: `Bearer ${tokenFor("admin", "tenant-b")}` },
    });

    expect(readByOtherTenant.statusCode).toBe(404);

    const planByOtherTenant = await app.inject({
      method: "POST",
      url: `/api/generation/requests/${requestId}/plan`,
      headers: { authorization: `Bearer ${tokenFor("admin", "tenant-b")}` },
    });

    expect(planByOtherTenant.statusCode).toBe(400);
  });

  it("admin lifecycle path: create -> plan -> cancel follows privileged flow", async () => {
    const requestId = await createRequestAs("admin", "tenant-1");

    const planRes = await app.inject({
      method: "POST",
      url: `/api/generation/requests/${requestId}/plan`,
      headers: { authorization: `Bearer ${tokenFor("admin", "tenant-1")}` },
    });
    expect(planRes.statusCode).toBe(200);

    const cancelRes = await app.inject({
      method: "POST",
      url: `/api/generation/requests/${requestId}/cancel`,
      headers: { authorization: `Bearer ${tokenFor("admin", "tenant-1")}` },
    });
    expect(cancelRes.statusCode).toBe(200);
  });
});
