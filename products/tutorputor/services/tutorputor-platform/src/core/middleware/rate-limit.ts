import { type FastifyInstance } from "fastify";
import rateLimit from "@fastify/rate-limit";
import type { Redis } from "ioredis";

export async function setupRateLimit(app: FastifyInstance) {
  const redis = (app as any).redis as Redis;

  await app.register(rateLimit, {
    global: true,
    max: parseInt(process.env.RATE_LIMIT_MAX || "100", 10), // 100 requests
    timeWindow: process.env.RATE_LIMIT_WINDOW || "1 minute",
    redis,
    nameSpace: "tutorputor:rate-limit:",

    // Custom key generator for tenant-aware rate limiting
    keyGenerator: (request: any) => {
      const tenantId = (request.headers["x-tenant-id"] as string) || "default";
      const userId = (request as any).user?.id || request.ip;
      return `${tenantId}:${userId}`;
    },

    // Custom error response
    errorResponseBuilder: (request: any, context: any) => {
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
    skip: (request: any) => {
      return request.url.startsWith("/health") || request.url === "/metrics";
    },
  } as any);

  // Add rate limit headers to responses
  app.addHook("onResponse", async (request, reply) => {
    const remaining = reply.getHeader("x-ratelimit-remaining");
    const limit = reply.getHeader("x-ratelimit-limit");
    const reset = reply.getHeader("x-ratelimit-reset");

    if (remaining) reply.header("X-RateLimit-Remaining", remaining);
    if (limit) reply.header("X-RateLimit-Limit", limit);
    if (reset) reply.header("X-RateLimit-Reset", reset);
  });
}
