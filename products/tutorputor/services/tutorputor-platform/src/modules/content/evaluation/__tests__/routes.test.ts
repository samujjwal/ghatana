import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerEvaluationRoutes } from "../routes";
import { EvaluationService } from "../evaluation-service";

describe("registerEvaluationRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const evaluateSpy = vi.spyOn(
    EvaluationService.prototype,
    "evaluateGenerationRequest",
  );
  const listSpy = vi.spyOn(EvaluationService.prototype, "getEvaluationsByRequest");
  const getSpy = vi.spyOn(EvaluationService.prototype, "getEvaluation");

  beforeEach(async () => {
    vi.clearAllMocks();

    evaluateSpy.mockResolvedValue({ id: "eval-1" } as never);
    listSpy.mockResolvedValue([] as never);
    getSpy.mockResolvedValue({ id: "eval-1" } as never);

    app = Fastify();
    registerEvaluationRoutes(app, { prisma: {} as never });

    // Inject auth context so getTenantId/getUserId resolve without a real JWT stack.
    // The role is read from the x-user-role header (if present) so individual
    // tests can override it; admin is used as the safe default.
    app.addHook("preHandler", async (request) => {
      const roleHeader = request.headers["x-user-role"];
      const role = typeof roleHeader === "string" ? roleHeader : "admin";
      (
        request as typeof request & {
          user?: { id: string; sub: string; tenantId: string; role: string };
        }
      ).user = { id: "user-1", sub: "user-1", tenantId: "tenant-1", role };
    });

    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects blank request id path", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/evaluation/requests/%20/evaluate",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(evaluateSpy).not.toHaveBeenCalled();
  });

  it("rejects blank evaluation id path", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/evaluation/records/%20",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(getSpy).not.toHaveBeenCalled();
  });

  it("rejects blank request id on list route", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/evaluation/requests/%20/evaluations",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(listSpy).not.toHaveBeenCalled();
  });
});
