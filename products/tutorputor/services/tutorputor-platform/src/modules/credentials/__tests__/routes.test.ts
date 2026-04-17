import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { credentialRoutes } from "../routes";

describe("credentialRoutes", () => {
  const service = {
    findByUser: vi.fn(),
    create: vi.fn(),
    getProgress: vi.fn(),
    hasCredential: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();

    service.findByUser.mockResolvedValue([]);
    service.create.mockResolvedValue({ id: "cred-1" });
    service.getProgress.mockResolvedValue({});
    service.hasCredential.mockResolvedValue(false);

    app = Fastify();
    await credentialRoutes(app, { service: service as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects invalid credential list filters", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/users/user-1/credentials?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.findByUser).not.toHaveBeenCalled();
  });

  it("rejects malformed credential issue payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/credentials",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "teacher-1",
        "x-user-role": "teacher",
      },
      payload: {
        type: "badge",
        userId: "student-1",
        tenantId: "tenant-1",
        name: "",
        description: "Issued manually",
        metadata: {},
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.create).not.toHaveBeenCalled();
  });

  it("accepts valid credential list filters", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/users/user-1/credentials?page=1&limit=20",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.findByUser).toHaveBeenCalledWith(
      "user-1",
      expect.objectContaining({ tenantId: "tenant-1", page: 1, limit: 20 }),
    );
  });
});
