import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { auditRoutes } from "../routes.js";

describe("auditRoutes", () => {
  const service = {
    queryAuditEvents: vi.fn(),
    getAuditSummary: vi.fn(),
    exportAuditLog: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.queryAuditEvents.mockResolvedValue({
      items: [],
      totalCount: 0,
      hasMore: false,
    });
    service.getAuditSummary.mockResolvedValue({
      totalEvents: 0,
      uniqueActors: 0,
      topActions: [],
      topResourceTypes: [],
      recentEvents: [],
    });

    app = Fastify();
    await app.register(auditRoutes, { service: service as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects invalid audit query limits", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.queryAuditEvents).not.toHaveBeenCalled();
  });

  it("forwards parsed audit query filters", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/?action=USER_CREATED&limit=20&sortOrder=desc",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.queryAuditEvents).toHaveBeenCalledWith(
      expect.objectContaining({
        tenantId: "tenant-1",
        action: "USER_CREATED",
        pagination: expect.objectContaining({
          limit: 20,
          sortOrder: "desc",
        }),
      }),
    );
  });

  it("rejects invalid summary day ranges", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/summary?days=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.getAuditSummary).not.toHaveBeenCalled();
  });

  it("rejects malformed audit export dates", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/export",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        startDate: "bad-date",
        endDate: "2026-04-16T00:00:00.000Z",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.exportAuditLog).not.toHaveBeenCalled();
  });
});
