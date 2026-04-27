import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { registerContentNeedsRoutes } from "../routes";

describe("content-needs routes", () => {
  const analyzer = {
    analyzeClaimNeeds: vi.fn(),
    analyzeExperienceNeeds: vi.fn(),
    generateContentForClaim: vi.fn(),
    getAnalysisHistory: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    analyzer.analyzeClaimNeeds.mockResolvedValue({ gaps: [] });
    analyzer.analyzeExperienceNeeds.mockResolvedValue([]);
    analyzer.generateContentForClaim.mockResolvedValue({ content: [] });
    analyzer.getAnalysisHistory.mockResolvedValue([]);

    app = Fastify();
    registerContentNeedsRoutes(app, analyzer as never);

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

  it("rejects malformed analyze-claim payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/analyze-claim",
      payload: {
        claim: { id: "c1", text: "", bloomLevel: "apply" },
      },
    });

    expect(response.statusCode).toBe(400);
    expect(analyzer.analyzeClaimNeeds).not.toHaveBeenCalled();
  });

  it("rejects malformed batch-analyze payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/batch-analyze",
      payload: {
        claims: [],
        context: {
          domain: "math",
          gradeRange: "6-8",
          subject: "math",
          topic: "fractions",
          prerequisites: [],
          learningObjectives: [],
        },
      },
    });

    expect(response.statusCode).toBe(400);
  });

  it("accepts valid analyze-claim payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/analyze-claim",
      payload: {
        claim: { id: "c1", text: "A fraction represents part of a whole", bloomLevel: "understand" },
        context: {
          domain: "math",
          gradeRange: "6-8",
          subject: "math",
          topic: "fractions",
          prerequisites: ["division"],
          learningObjectives: ["identify fractions"],
        },
      },
    });

    expect(response.statusCode).toBe(200);
    expect(analyzer.analyzeClaimNeeds).toHaveBeenCalledTimes(1);
  });
});
