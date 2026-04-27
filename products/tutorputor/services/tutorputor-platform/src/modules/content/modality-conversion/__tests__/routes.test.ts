import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerModalityConversionRoutes } from "../routes";
import { ModalityConversionService } from "../service";

describe("registerModalityConversionRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const convertSpy = vi.spyOn(ModalityConversionService.prototype, "convertAsset");

  beforeEach(async () => {
    vi.clearAllMocks();

    convertSpy.mockResolvedValue({ status: "ok" } as never);

    app = Fastify();
    registerModalityConversionRoutes(app, { prisma: {} as never });

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

  it("rejects malformed conversion body", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/modality/assets/asset-1/convert",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
      payload: {
        targetModality: "video",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(convertSpy).not.toHaveBeenCalled();
  });
});
