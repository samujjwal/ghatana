import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { gamificationRoutes } from "../routes.js";

describe("gamificationRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    app = Fastify();
    app.decorate("prisma", {
      badge: {},
      badgeEarned: {},
      userPoints: {},
      userAchievement: { create: vi.fn() },
      learningStreak: { findFirst: vi.fn() },
    });
    await app.register(gamificationRoutes);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects invalid leaderboard limits", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/leaderboard?limit=0",
      headers: { "x-tenant-id": "tenant-1" },
    });

    expect(response.statusCode).toBe(400);
  });

  it("rejects malformed point award payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/points/award",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        userId: "",
        points: "abc",
      },
    });

    expect(response.statusCode).toBe(400);
  });

  it("rejects malformed unlock payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/achievements/unlock",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
      payload: {
        userId: "user-1",
        achievementId: "",
      },
    });

    expect(response.statusCode).toBe(400);
  });
});
