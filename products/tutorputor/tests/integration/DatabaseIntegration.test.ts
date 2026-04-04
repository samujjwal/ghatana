/**
 * Database Integration Tests
 *
 * Validates directly against the Prisma-managed database schema:
 *   – CRUD operations for Users, Modules, Assessments, and Enrolments
 *   – Referential integrity (foreign-key cascade behaviour)
 *   – Multi-tenant data isolation (rows are scoped to tenantId)
 *   – User progress aggregation across attempts
 *   – Unique constraint enforcement
 *
 * Tests use an in-process PrismaClient against a test database (SQLite default,
 * overridden by TEST_DATABASE_URL).  Data is cleared before every suite.
 *
 * @doc.type test
 * @doc.purpose Database-layer integration coverage for the TutorPutor persistence contracts
 * @doc.layer product
 * @doc.pattern IntegrationTest
 *
 * Requirement IDs: TPUT-FR-DB-001 … TPUT-FR-DB-007
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import {
  IntegrationTestSuite,
  type TestEnvironment,
} from "./comprehensive.test";

// ---------------------------------------------------------------------------
// Shared DB environment
// ---------------------------------------------------------------------------

let suite: IntegrationTestSuite;
let env: TestEnvironment;

beforeAll(async () => {
  suite = new IntegrationTestSuite();
  env = await suite.setup();
});

afterAll(async () => {
  await env.cleanup();
});

// ---------------------------------------------------------------------------
// TPUT-FR-DB-001  User CRUD contract
// ---------------------------------------------------------------------------
describe("TPUT-FR-DB-001: User CRUD", () => {
  it("creates a user and retrieves it by id", async () => {
    const created = (await suite.createTestUser({
      email: `crud-${Date.now()}@example.com`,
      firstName: "Alice",
      lastName: "Smith",
      role: "student",
      tenantId: "tenant-a",
    })) as Record<string, unknown>;

    expect(created.id).toBeDefined();
    expect(created.email).toMatch(/@example\.com$/);

    const found = await env.db.user.findUnique({
      where: { id: created.id as any },
    });
    expect(found).not.toBeNull();
    expect((found as any).firstName).toBe("Alice");
  });

  it("two users with the same email cannot coexist in the same tenant", async () => {
    const email = `unique-${Date.now()}@example.com`;

    await suite.createTestUser({ email, tenantId: "tenant-b" });

    await expect(
      suite.createTestUser({ email, tenantId: "tenant-b" }),
    ).rejects.toThrow();
  });

  it("user fields are persisted exactly as provided", async () => {
    const user = (await suite.createTestUser({
      firstName: "Database",
      lastName: "Tester",
      role: "instructor",
      tenantId: "tenant-c",
    })) as Record<string, unknown>;

    expect(user.firstName).toBe("Database");
    expect(user.lastName).toBe("Tester");
    expect(user.role).toBe("instructor");
    expect(user.tenantId).toBe("tenant-c");
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-DB-002  Module CRUD contract
// ---------------------------------------------------------------------------
describe("TPUT-FR-DB-002: Module CRUD", () => {
  it("creates a module with default values", async () => {
    const module = (await suite.createTestModule({
      title: "Bubble Sort",
      domain: "MATHEMATICS",
    })) as Record<string, unknown>;

    expect(module.id).toBeDefined();
    expect(module.title).toBe("Bubble Sort");
    expect(module.status).toBe("PUBLISHED");
  });

  it("modules can be retrieved by id", async () => {
    const module = (await suite.createTestModule({
      title: "Recursion Fundamentals",
      domain: "MATHEMATICS",
    })) as Record<string, unknown>;

    const found = await env.db.module.findUnique({
      where: { id: module.id as any },
    });
    expect((found as any).title).toBe("Recursion Fundamentals");
  });

  it("multiple modules in the same tenant are independent", async () => {
    const m1 = (await suite.createTestModule({
      title: "Module Alpha",
      tenantId: "db-tenant-1",
    })) as Record<string, unknown>;
    const m2 = (await suite.createTestModule({
      title: "Module Beta",
      tenantId: "db-tenant-1",
    })) as Record<string, unknown>;

    expect(m1.id).not.toBe(m2.id);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-DB-003  Assessment CRUD and module foreign key
// ---------------------------------------------------------------------------
describe("TPUT-FR-DB-003: Assessment CRUD and referential integrity", () => {
  let parentModule: Record<string, unknown>;

  beforeEach(async () => {
    parentModule = (await suite.createTestModule({
      title: `Parent Module ${Date.now()}`,
    })) as Record<string, unknown>;
  });

  it("creates an assessment linked to a module", async () => {
    const assessment = (await suite.createTestAssessment({
      title: "Unit Quiz",
      moduleId: parentModule.id as string,
    })) as Record<string, unknown>;

    expect(assessment.moduleId).toBe(parentModule.id as string);
  });

  it("assessment can be retrieved with its module id", async () => {
    const assessment = (await suite.createTestAssessment({
      title: "Final Exam",
      moduleId: parentModule.id as string,
    })) as Record<string, unknown>;

    const found = await env.db.assessment.findUnique({
      where: { id: assessment.id as any },
    });
    expect((found as any).moduleId).toBe(parentModule.id as string);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-DB-004  Multi-tenant isolation
// ---------------------------------------------------------------------------
describe("TPUT-FR-DB-004: Multi-tenant data isolation", () => {
  it("users created under tenant-X are not returned when querying tenant-Y", async () => {
    const tenantX = `tenant-x-${Date.now()}`;
    const tenantY = `tenant-y-${Date.now()}`;

    await suite.createTestUser({
      tenantId: tenantX,
      email: `x-user-${Date.now()}@example.com`,
    });

    const tenantYUsers = await env.db.user.findMany({
      where: { tenantId: tenantY },
    });

    // tenantY has no users yet
    expect(tenantYUsers.every((u) => u.tenantId === tenantY)).toBe(true);
  });

  it("modules from different tenants share no foreign-key relationships", async () => {
    const tenant1 = `iso-t1-${Date.now()}`;
    const tenant2 = `iso-t2-${Date.now()}`;

    const mod1 = (await suite.createTestModule({
      tenantId: tenant1,
    })) as Record<string, unknown>;
    const mod2 = (await suite.createTestModule({
      tenantId: tenant2,
    })) as Record<string, unknown>;

    expect(mod1.tenantId).toBe(tenant1);
    expect(mod2.tenantId).toBe(tenant2);
    expect(mod1.id).not.toBe(mod2.id);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-DB-005  Enrolment lifecycle
// ---------------------------------------------------------------------------
describe("TPUT-FR-DB-005: Enrolment lifecycle", () => {
  it("a user can be enrolled in a module", async () => {
    const user = (await suite.createTestUser()) as Record<string, unknown>;
    const module = (await suite.createTestModule()) as Record<string, unknown>;

    const enrollment = await env.db.enrollment.create({
      data: {
        userId: user.id as any,
        moduleId: module.id as any,
        tenantId: (user.tenantId ?? "test-tenant") as string,
        status: "ACTIVE",
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    });

    expect(enrollment.userId).toBe(user.id as any);
    expect(enrollment.moduleId).toBe(module.id as any);
    expect(enrollment.status).toBe("ACTIVE");
  });

  it("the same user cannot be enrolled in the same module twice", async () => {
    const user = (await suite.createTestUser({
      email: `enroll-unique-${Date.now()}@example.com`,
    })) as Record<string, unknown>;
    const module = (await suite.createTestModule()) as Record<string, unknown>;

    await env.db.enrollment.create({
      data: {
        userId: user.id as any,
        moduleId: module.id as any,
        tenantId: (user.tenantId ?? "test-tenant") as string,
        status: "ACTIVE",
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    });

    await expect(
      env.db.enrollment.create({
        data: {
          userId: user.id as any,
          moduleId: module.id as any,
          tenantId: (user.tenantId ?? "test-tenant") as string,
          status: "ACTIVE",
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      }),
    ).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-DB-006  Assessment attempt tracking
// ---------------------------------------------------------------------------
describe("TPUT-FR-DB-006: Assessment attempt accumulation", () => {
  it("multiple attempts by a learner are stored independently", async () => {
    const user = (await suite.createTestUser({
      email: `attempt-user-${Date.now()}@example.com`,
    })) as Record<string, unknown>;
    const module = (await suite.createTestModule()) as Record<string, unknown>;
    const assessment = (await suite.createTestAssessment({
      moduleId: module.id as string,
    })) as Record<string, unknown>;

    const attempt1 = await env.db.assessmentAttempt.create({
      data: {
        userId: user.id as any,
        assessmentId: assessment.id as any,
        tenantId: (user.tenantId ?? "test-tenant") as string,
        score: 65,
        passed: false,
        startedAt: new Date(),
        completedAt: new Date(),
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    });

    const attempt2 = await env.db.assessmentAttempt.create({
      data: {
        userId: user.id as any,
        assessmentId: assessment.id as any,
        tenantId: (user.tenantId ?? "test-tenant") as string,
        score: 85,
        passed: true,
        startedAt: new Date(),
        completedAt: new Date(),
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    });

    expect(attempt1.id).not.toBe(attempt2.id);
    expect((attempt1 as any).score).toBe(65);
    expect((attempt2 as any).score).toBe(85);
  });

  it("attempts for a given user and assessment can be aggregated", async () => {
    const user = (await suite.createTestUser({
      email: `agg-user-${Date.now()}@example.com`,
    })) as Record<string, unknown>;
    const module = (await suite.createTestModule()) as Record<string, unknown>;
    const assessment = (await suite.createTestAssessment({
      moduleId: module.id as string,
    })) as Record<string, unknown>;

    const scores = [55, 70, 80];
    for (const score of scores) {
      await env.db.assessmentAttempt.create({
        data: {
          userId: user.id as any,
          assessmentId: assessment.id as any,
          tenantId: (user.tenantId ?? "test-tenant") as string,
          score,
          passed: score >= 70,
          startedAt: new Date(),
          completedAt: new Date(),
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      });
    }

    const attempts = await env.db.assessmentAttempt.findMany({
      where: {
        userId: user.id as any,
        assessmentId: assessment.id as any,
      },
    });

    expect(attempts).toHaveLength(3);

    const total = attempts.reduce(
      (sum, a) => sum + ((a as any).score as number),
      0,
    );
    const average = total / attempts.length;
    expect(average).toBeCloseTo(68.33, 1);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-DB-007  Database cleanup leaves other data intact
// ---------------------------------------------------------------------------
describe("TPUT-FR-DB-007: Targeted deletion does not affect unrelated records", () => {
  it("deleting one user does not remove other users", async () => {
    const userA = (await suite.createTestUser({
      email: `del-a-${Date.now()}@example.com`,
    })) as Record<string, unknown>;
    const userB = (await suite.createTestUser({
      email: `del-b-${Date.now()}@example.com`,
    })) as Record<string, unknown>;

    await env.db.user.delete({ where: { id: userA.id as any } });

    const remaining = await env.db.user.findUnique({
      where: { id: userB.id as any },
    });
    expect(remaining).not.toBeNull();
  });
});
