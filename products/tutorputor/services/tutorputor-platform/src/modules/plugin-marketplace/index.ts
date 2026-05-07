/**
 * Plugin Marketplace Module
 *
 * Support third-party extensions with lifecycle hooks and migration orchestration.
 *
 * @doc.type module
 * @doc.purpose Plugin marketplace module for third-party extension management
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync, FastifyRequest } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
import { PluginMarketplaceService } from "./PluginMarketplaceService.js";
import { z } from "zod";

const installPluginSchema = z.object({
  pluginId: z.string(),
  configuration: z.record(z.string(), z.unknown()).optional(),
});

const activatePluginSchema = z.object({
  pluginId: z.string(),
});

const deactivatePluginSchema = z.object({
  pluginId: z.string(),
});

const uninstallPluginSchema = z.object({
  pluginId: z.string(),
});

const migratePluginSchema = z.object({
  pluginId: z.string(),
  targetVersion: z.string(),
});

const updateConfigSchema = z.object({
  pluginId: z.string(),
  configuration: z.record(z.string(), z.unknown()),
});

interface PluginMarketplaceUser {
  role?: string;
}

interface PluginMarketplaceRouteContext {
  tenantId?: string;
  user?: PluginMarketplaceUser;
}

type PluginMarketplaceRequest = FastifyRequest & PluginMarketplaceRouteContext;

function getRouteContext(
  request: FastifyRequest,
): PluginMarketplaceRouteContext {
  const authenticatedRequest = request as PluginMarketplaceRequest;
  const context: PluginMarketplaceRouteContext = {};
  if (authenticatedRequest.tenantId) {
    context.tenantId = authenticatedRequest.tenantId;
  }
  if (
    authenticatedRequest.user &&
    typeof authenticatedRequest.user === "object" &&
    !Buffer.isBuffer(authenticatedRequest.user)
  ) {
    context.user = authenticatedRequest.user;
  }
  return context;
}

function isAdmin(user: PluginMarketplaceUser): boolean {
  return user.role === "admin" || user.role === "superadmin";
}

export const pluginMarketplaceModule: FastifyPluginAsync = async (app) => {
  const marketplaceService = new PluginMarketplaceService(
    app.prisma as unknown as PrismaClient,
  );
  app.decorate("pluginMarketplaceService", marketplaceService);

  // GET /api/v1/plugin-marketplace/plugins - Get available plugins
  app.get("/api/v1/plugin-marketplace/plugins", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const plugins = await marketplaceService.getAvailablePlugins();

    return reply.send({ plugins });
  });

  // GET /api/v1/plugin-marketplace/plugins/:pluginId - Get plugin manifest
  app.get("/api/v1/plugin-marketplace/plugins/:pluginId", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const { pluginId } = request.params as { pluginId: string };
    const manifest = await marketplaceService.getPluginManifest(pluginId);

    if (!manifest) {
      return reply.code(404).send({ error: "Plugin not found" });
    }

    return reply.send(manifest);
  });

  // GET /api/v1/plugin-marketplace/installed - Get tenant installed plugins
  app.get("/api/v1/plugin-marketplace/installed", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!isAdmin(user)) {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const plugins = await marketplaceService.getTenantPlugins(tenantId);

    return reply.send({ plugins });
  });

  // POST /api/v1/plugin-marketplace/install - Install plugin for tenant
  app.post("/api/v1/plugin-marketplace/install", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!isAdmin(user)) {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = installPluginSchema.parse(request.body);
    const plugin = await marketplaceService.installPlugin(tenantId, body.pluginId, body.configuration);

    return reply.send(plugin);
  });

  // POST /api/v1/plugin-marketplace/activate - Activate plugin for tenant
  app.post("/api/v1/plugin-marketplace/activate", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!isAdmin(user)) {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = activatePluginSchema.parse(request.body);
    const plugin = await marketplaceService.activatePlugin(tenantId, body.pluginId);

    return reply.send(plugin);
  });

  // POST /api/v1/plugin-marketplace/deactivate - Deactivate plugin for tenant
  app.post("/api/v1/plugin-marketplace/deactivate", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!isAdmin(user)) {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = deactivatePluginSchema.parse(request.body);
    const plugin = await marketplaceService.deactivatePlugin(tenantId, body.pluginId);

    return reply.send(plugin);
  });

  // POST /api/v1/plugin-marketplace/uninstall - Uninstall plugin for tenant
  app.post("/api/v1/plugin-marketplace/uninstall", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!isAdmin(user)) {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = uninstallPluginSchema.parse(request.body);
    await marketplaceService.uninstallPlugin(tenantId, body.pluginId);

    return reply.send({ success: true });
  });

  // POST /api/v1/plugin-marketplace/migrate - Migrate plugin for tenant
  app.post("/api/v1/plugin-marketplace/migrate", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!isAdmin(user)) {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = migratePluginSchema.parse(request.body);
    const plugin = await marketplaceService.migratePlugin(tenantId, body.pluginId, body.targetVersion);

    return reply.send(plugin);
  });

  // PUT /api/v1/plugin-marketplace/config - Update plugin configuration
  app.put("/api/v1/plugin-marketplace/config", async (request, reply) => {
    const { tenantId, user } = getRouteContext(request);

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!isAdmin(user)) {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = updateConfigSchema.parse(request.body);
    const plugin = await marketplaceService.updatePluginConfiguration(tenantId, body.pluginId, body.configuration);

    return reply.send(plugin);
  });

  app.log.info("✅ Plugin marketplace module registered");
};
