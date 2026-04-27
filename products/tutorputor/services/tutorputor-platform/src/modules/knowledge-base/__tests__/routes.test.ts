import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { registerKnowledgeBaseRoutes } from "../routes";

describe("knowledgeBase routes", () => {
  const service = {
    verifyFact: vi.fn(),
    searchConcept: vi.fn(),
    findExamples: vi.fn(),
    getCurriculumAlignment: vi.fn(),
    validateContent: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.verifyFact.mockResolvedValue({ verified: true, confidence: 0.9 });
    service.searchConcept.mockResolvedValue([]);
    service.findExamples.mockResolvedValue([]);
    service.getCurriculumAlignment.mockResolvedValue([]);
    service.validateContent.mockResolvedValue({ passed: true, score: 95 });

    app = Fastify();
    registerKnowledgeBaseRoutes(app, service as never);

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

  it("rejects malformed verify-fact payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/api/knowledge-base/verify-fact",
      payload: {
        claim: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.verifyFact).not.toHaveBeenCalled();
  });

  it("rejects concept search without domain", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/knowledge-base/concepts/search?query=fractions",
    });

    expect(response.statusCode).toBe(400);
    expect(service.searchConcept).not.toHaveBeenCalled();
  });

  it("forwards valid concept search", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/knowledge-base/concepts/search?query=fractions&domain=math",
    });

    expect(response.statusCode).toBe(200);
    expect(service.searchConcept).toHaveBeenCalledWith("fractions", "math");
  });
});
