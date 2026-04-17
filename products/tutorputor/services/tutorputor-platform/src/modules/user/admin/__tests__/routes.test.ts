import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { adminRoutes } from "../routes.js";

describe("adminRoutes", () => {
  const service = {
    getTenantSummary: vi.fn(),
    listTenantUsers: vi.fn(),
    getTenantUsage: vi.fn(),
    bulkImportUsers: vi.fn(),
    updateUserRole: vi.fn(),
    assignPathToClassroom: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();

    service.listTenantUsers.mockResolvedValue({
      items: [],
      totalCount: 0,
      hasMore: false,
    });
    service.assignPathToClassroom.mockResolvedValue({
      success: true,
      message: "Assigned",
    });

    app = Fastify();
    await app.register(adminRoutes, { service: service as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects invalid tenant user pagination inputs", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/tenant/users?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({ error: "Validation Error" });
    expect(service.listTenantUsers).not.toHaveBeenCalled();
  });

  it("passes parsed tenant user filters and pagination to the service", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/tenant/users?role=teacher&searchQuery=alice&limit=25&sortBy=displayName&sortOrder=desc",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.listTenantUsers).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      role: "teacher",
      searchQuery: "alice",
      pagination: {
        limit: 25,
        sortBy: "displayName",
        sortOrder: "desc",
      },
    });
  });

  it("rejects invalid date formats for usage metrics", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/tenant/usage?startDate=not-a-date&endDate=2026-04-17T00:00:00.000Z",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.getTenantUsage).not.toHaveBeenCalled();
  });

  it("rejects malformed import payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/tenant/users/import",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "admin-1",
        "x-user-role": "admin",
      },
      payload: {
        users: [],
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.bulkImportUsers).not.toHaveBeenCalled();
  });

  it("rejects role updates with an empty role", async () => {
    const response = await app.inject({
      method: "PUT",
      url: "/tenant/users/user-1/role",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "admin-1",
        "x-user-role": "admin",
      },
      payload: {
        newRole: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.updateUserRole).not.toHaveBeenCalled();
  });

  it("forwards valid classroom pathway assignments", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/classrooms/classroom-1/assign-path",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "admin-1",
        "x-user-role": "admin",
      },
      payload: {
        pathwayId: "pathway-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.assignPathToClassroom).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      classroomId: "classroom-1",
      pathwayId: "pathway-1",
      assignedBy: "admin-1",
    });
  });
});
