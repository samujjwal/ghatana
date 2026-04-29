/**
 * Capability Registry Module
 *
 * Backend-driven feature flags for AI, simulations, VR based on tenant subscription and worker health.
 *
 * @doc.type module
 * @doc.purpose Capability registry for UI feature gating
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import type Redis from "ioredis";
import type { PrismaClient } from "@tutorputor/core/db";
import { z } from "zod";

const checkCapabilitySchema = z.object({
  capability: z.string(),
});

/**
 * Capability Registry Service
 */
class CapabilityRegistryService {
  constructor(private prisma: PrismaClient, private redis: Redis) {}

  /**
   * Check if a capability is enabled for a tenant
   */
  async isCapabilityEnabled(tenantId: string, capabilityName: string): Promise<boolean> {
    // Check cache first
    const cacheKey = `capability:${tenantId}:${capabilityName}`;
    try {
      const cached = await this.redis.get(cacheKey);
      if (cached !== null) {
        return cached === "true";
      }
    } catch {
      // Redis error, continue to database
    }

    // Check database
    const capability = await this.prisma.capability.findUnique({
      where: { name: capabilityName },
    });

    if (!capability || !capability.enabled) {
      await this.cacheCapability(tenantId, capabilityName, false);
      return false;
    }

    const tenantCapability = await this.prisma.tenantCapability.findUnique({
      where: {
        tenantId_capabilityId: {
          tenantId,
          capabilityId: capability.id,
        },
      } as any,
    });

    // If tenant-specific setting exists, use it
    if (tenantCapability) {
      const isEnabled = tenantCapability.enabled;
      await this.cacheCapability(tenantId, capabilityName, isEnabled);
      return isEnabled;
    }

    // Otherwise, check if capability requires subscription or worker health
    if (capability.requiresSubscription) {
      const tenant = await this.prisma.tenant.findUnique({
        where: { id: tenantId },
        select: { subscriptionTier: true },
      });

      if (!tenant || tenant.subscriptionTier === "FREE") {
        await this.cacheCapability(tenantId, capabilityName, false);
        return false;
      }
    }

    if (capability.requiresWorkerHealthy) {
      // Check if content worker is healthy
      try {
        const response = await fetch("http://localhost:3300/health", {
          method: "GET",
          signal: AbortSignal.timeout(5000),
        });
        const isHealthy = response.ok;
        await this.cacheCapability(tenantId, capabilityName, isHealthy);
        return isHealthy;
      } catch {
        await this.cacheCapability(tenantId, capabilityName, false);
        return false;
      }
    }

    await this.cacheCapability(tenantId, capabilityName, true);
    return true;
  }

  /**
   * Get all capabilities for a tenant
   */
  async getTenantCapabilities(tenantId: string): Promise<Record<string, boolean>> {
    const capabilities = await this.prisma.capability.findMany({
      where: { enabled: true },
      include: {
        tenantCapabilities: {
          where: { tenantId },
        },
      },
    });

    const result: Record<string, boolean> = {};

    for (const capability of capabilities) {
      const tenantCapability = capability.tenantCapabilities[0];
      const isEnabled = tenantCapability ? tenantCapability.enabled : true;

      // Check additional requirements
      if (capability.requiresSubscription) {
        const tenant = await this.prisma.tenant.findUnique({
          where: { id: tenantId },
          select: { subscriptionTier: true },
        });

        if (!tenant || tenant.subscriptionTier === "FREE") {
          result[capability.name] = false;
          continue;
        }
      }

      if (capability.requiresWorkerHealthy) {
        try {
          const response = await fetch("http://localhost:3300/health", {
            method: "GET",
            signal: AbortSignal.timeout(5000),
          });
          result[capability.name] = response.ok && isEnabled;
        } catch {
          result[capability.name] = false;
        }
        continue;
      }

      result[capability.name] = isEnabled;
    }

    return result;
  }

  /**
   * Enable a capability for a tenant
   */
  async enableCapability(tenantId: string, capabilityName: string): Promise<void> {
    const capability = await this.prisma.capability.findUnique({
      where: { name: capabilityName },
    });

    if (!capability) {
      throw new Error(`Capability not found: ${capabilityName}`);
    }

    await this.prisma.tenantCapability.upsert({
      where: {
        tenantId_capabilityId: {
          tenantId,
          capabilityId: capability.id,
        },
      } as any,
      create: {
        tenantId,
        capabilityId: capability.id,
        enabled: true,
      },
      update: {
        enabled: true,
        disabledAt: null,
        updatedAt: new Date(),
      },
    });

    // Invalidate cache
    const cacheKey = `capability:${tenantId}:${capabilityName}`;
    try {
      await this.redis.del(cacheKey);
    } catch {
      // Redis error, ignore
    }
  }

  /**
   * Disable a capability for a tenant
   */
  async disableCapability(tenantId: string, capabilityName: string): Promise<void> {
    const capability = await this.prisma.capability.findUnique({
      where: { name: capabilityName },
    });

    if (!capability) {
      throw new Error(`Capability not found: ${capabilityName}`);
    }

    await this.prisma.tenantCapability.upsert({
      where: {
        tenantId_capabilityId: {
          tenantId,
          capabilityId: capability.id,
        },
      } as any,
      create: {
        tenantId,
        capabilityId: capability.id,
        enabled: false,
      },
      update: {
        enabled: false,
        disabledAt: new Date(),
        updatedAt: new Date(),
      },
    });

    // Invalidate cache
    const cacheKey = `capability:${tenantId}:${capabilityName}`;
    try {
      await this.redis.del(cacheKey);
    } catch {
      // Redis error, ignore
    }
  }

  /**
   * Cache capability check result
   */
  private async cacheCapability(
    tenantId: string,
    capabilityName: string,
    enabled: boolean,
  ): Promise<void> {
    const cacheKey = `capability:${tenantId}:${capabilityName}`;
    try {
      await this.redis.set(cacheKey, String(enabled), "EX", 300); // 5 minutes
    } catch {
      // Redis error, ignore
    }
  }
}

/**
 * Capability Registry Module Plugin
 */
export const capabilityRegistryModule: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma as PrismaClient;
  const redis = app.redis as any;
  const capabilityService = new CapabilityRegistryService(prisma, redis);

  app.decorate("capabilityService", capabilityService);

  // GET /api/v1/capabilities - Get all capabilities for the authenticated tenant
  app.get("/api/v1/capabilities", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const capabilities = await capabilityService.getTenantCapabilities(tenantId);
    return reply.send({ capabilities });
  });

  // GET /api/v1/capabilities/:name - Check if a specific capability is enabled
  app.get("/api/v1/capabilities/:name", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const { capability } = checkCapabilitySchema.parse(request.params as { capability: string });
    const enabled = await capabilityService.isCapabilityEnabled(tenantId, capability);
    return reply.send({ capability, enabled });
  });

  // POST /api/v1/capabilities/:name/enable - Enable a capability (admin only)
  app.post("/api/v1/capabilities/:name/enable", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { capability } = checkCapabilitySchema.parse(request.params as { capability: string });
    await capabilityService.enableCapability(tenantId, capability);
    return reply.send({ success: true, capability, enabled: true });
  });

  // POST /api/v1/capabilities/:name/disable - Disable a capability (admin only)
  app.post("/api/v1/capabilities/:name/disable", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { capability } = checkCapabilitySchema.parse(request.params as { capability: string });
    await capabilityService.disableCapability(tenantId, capability);
    return reply.send({ success: true, capability, enabled: false });
  });

  app.log.info("✅ Capability registry module registered");
};
