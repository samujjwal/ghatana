import {
  type FastifyInstance,
  type FastifyRequest,
  type FastifyReply,
} from "fastify";
import rateLimit from "@fastify/rate-limit";
import type { Redis } from "ioredis";
import type { RequestWithContext } from "../../utils/request-helpers.js";

type RedisWithLuaCommands = Redis & {
  defineCommand?: unknown;
};

function hasLuaCommandSupport(redis: Redis): redis is RedisWithLuaCommands {
  return typeof (redis as RedisWithLuaCommands).defineCommand === "function";
}

export async function setupRateLimit(app: FastifyInstance) {
  const redis = app.redis;
  // Only use Redis store when the client supports defineCommand (real ioredis instance).
  // Falls back to in-memory store for test environments using mock redis clients.
  const redisStore = hasLuaCommandSupport(redis) ? redis : undefined;

  await app.register(
    rateLimit,
    {
      global: true,
      max: parseInt(process.env.RATE_LIMIT_MAX || "100", 10), // 100 requests
      timeWindow: process.env.RATE_LIMIT_WINDOW || "1 minute",
      redis: redisStore,
      nameSpace: "tutorputor:rate-limit:",

      // Custom key generator for tenant-aware rate limiting
      keyGenerator: (request: FastifyRequest) => {
        const tenantId =
          (request.headers["x-tenant-id"] as string) || "default";
        const user = (request as RequestWithContext).user;
        const userId =
          user && typeof user === "object" && !Buffer.isBuffer(user) && "id" in user
            ? String(user.id)
            : request.ip;
        return `${tenantId}:${userId}`;
      },

      // Custom error response
      errorResponseBuilder: (
        _request: FastifyRequest,
        context: { max: number; after: string; ttl?: number },
      ) => {
        return {
          error: "Too Many Requests",
          message: `Rate limit exceeded. Maximum ${context.max} requests per ${context.after}`,
          retryAfter: context.after,
          limit: context.max,
          remaining: 0,
          reset: Date.now() + (context.ttl || 0),
        };
      },

      // Skip rate limiting for health checks
      allowList: (request: FastifyRequest) => {
        return request.url.startsWith("/health") || request.url === "/metrics";
      },
    },
  );

  // Add rate limit headers to responses
  app.addHook(
    "onResponse",
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const remaining = reply.getHeader("x-ratelimit-remaining");
      const limit = reply.getHeader("x-ratelimit-limit");
      const reset = reply.getHeader("x-ratelimit-reset");

      if (remaining) reply.header("X-RateLimit-Remaining", remaining);
      if (limit) reply.header("X-RateLimit-Limit", limit);
      if (reset) reply.header("X-RateLimit-Reset", reset);
    },
  );
}
