import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerTelemetryRoutes } from "../routes";
import { TelemetryService } from "../telemetry-service";

describe("registerTelemetryRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const trackEventSpy = vi.spyOn(TelemetryService.prototype, "trackEvent");
  const trackBatchSpy = vi.spyOn(TelemetryService.prototype, "trackBatch");

  beforeEach(async () => {
    vi.clearAllMocks();

    trackEventSpy.mockResolvedValue({ id: "event-1" } as never);
    trackBatchSpy.mockResolvedValue({ created: 1 } as never);

    app = Fastify();
    registerTelemetryRoutes(app, { prisma: {} as never });

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

  it("rejects malformed single telemetry event payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/telemetry/events",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "learner",
      },
      payload: {
        eventType: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(trackEventSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed telemetry batch payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/telemetry/events/batch",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "learner",
      },
      payload: {
        events: [],
      },
    });

    expect(response.statusCode).toBe(400);
    expect(trackBatchSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed telemetry timestamp", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/telemetry/events",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "learner",
      },
      payload: {
        eventType: "search_query",
        occurredAt: "not-a-timestamp",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(trackEventSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only telemetry event type", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/telemetry/events",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "learner",
      },
      payload: {
        eventType: "   ",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(trackEventSpy).not.toHaveBeenCalled();
  });
});
