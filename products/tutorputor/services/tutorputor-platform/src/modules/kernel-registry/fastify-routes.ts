/**
 * Kernel Registry Fastify Routes
 * Fastify route registration for the kernel registry module
 *
 * @doc.type routes
 * @doc.purpose Kernel plugin registry API routes for Fastify
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { validatePluginMetadata } from "../validation/plugin-policy.js";
import { getTenantId } from "../../core/http/requestContext.js";

// =============================================================================
// Types
// =============================================================================

interface PluginMetadata {
  id: string;
  name: string;
  version: string;
  description?: string;
  author?: string;
  kernelType: string;
  capabilities: string[];
  dependencies?: Record<string, string>;
}

interface RegisterPluginRequest {
  Body: PluginMetadata;
}

interface GetPluginRequest {
  Params: {
    pluginId: string;
  };
}

interface ListPluginsRequest {
  Querystring: {
    kernelType?: string;
    capability?: string;
  };
}

// =============================================================================
// Route Handlers
// =============================================================================

/** Serialize PluginMetadata from a Prisma KernelPlugin record */
function rowToMetadata(row: any): PluginMetadata {
  return {
    id: row.pluginId,
    name: row.name,
    version: row.version,
    description: row.description ?? undefined,
    author: row.author ?? undefined,
    kernelType: row.kernelType,
    capabilities: JSON.parse(row.capabilities ?? "[]"),
    dependencies: row.dependencies ? JSON.parse(row.dependencies) : undefined,
  };
}

/**
 * Register a new plugin
 */
async function registerPlugin(
  request: FastifyRequest<RegisterPluginRequest>,
  reply: FastifyReply,
) {
  try {
    const metadata = request.body;
    const tenantId = getTenantId(request);
    const prisma = (request.server as FastifyInstance & { prisma: any }).prisma;

    // Validate plugin metadata
    const validation = validatePluginMetadata(metadata);
    if (!validation.valid) {
      return reply.code(400).send({
        error: "Invalid plugin metadata",
        details: validation.errors,
      });
    }

    // Check if plugin already exists
    const existing = await prisma.kernelPlugin.findUnique({
      where: { pluginId: metadata.id },
    });
    if (existing) {
      return reply.code(409).send({
        error: "Plugin already registered",
        pluginId: metadata.id,
      });
    }

    // Register plugin
    const row = await prisma.kernelPlugin.create({
      data: {
        tenantId,
        pluginId: metadata.id,
        name: metadata.name,
        version: metadata.version,
        description: metadata.description ?? null,
        author: metadata.author ?? null,
        kernelType: metadata.kernelType,
        capabilities: JSON.stringify(metadata.capabilities),
        dependencies: metadata.dependencies
          ? JSON.stringify(metadata.dependencies)
          : null,
      },
    });

    return reply.code(201).send({
      message: "Plugin registered successfully",
      plugin: rowToMetadata(row),
    });
  } catch (error) {
    request.log.error(error, "Error registering plugin");
    return reply.code(500).send({ error: "Internal server error" });
  }
}

/**
 * Get plugin by ID
 */
async function getPlugin(
  request: FastifyRequest<GetPluginRequest>,
  reply: FastifyReply,
) {
  try {
    const { pluginId } = request.params;
    const prisma = (request.server as FastifyInstance & { prisma: any }).prisma;

    const row = await prisma.kernelPlugin.findUnique({
      where: { pluginId },
    });
    if (!row) {
      return reply.code(404).send({ error: "Plugin not found", pluginId });
    }

    return reply.send(rowToMetadata(row));
  } catch (error) {
    request.log.error(error, "Error getting plugin");
    return reply.code(500).send({ error: "Internal server error" });
  }
}

/**
 * List all plugins with optional filters
 */
async function listPlugins(
  request: FastifyRequest<ListPluginsRequest>,
  reply: FastifyReply,
) {
  try {
    const { kernelType, capability } = request.query;
    const tenantId = getTenantId(request);
    const prisma = (request.server as FastifyInstance & { prisma: any }).prisma;

    const where: any = { tenantId };
    if (kernelType) where.kernelType = kernelType;

    const rows = await prisma.kernelPlugin.findMany({ where });
    let plugins = rows.map(rowToMetadata);

    // capability filter (JSON array field, done in-process)
    if (capability) {
      plugins = plugins.filter((p) => p.capabilities.includes(capability));
    }

    return reply.send({ plugins, count: plugins.length });
  } catch (error) {
    request.log.error(error, "Error listing plugins");
    return reply.code(500).send({ error: "Internal server error" });
  }
}

/**
 * Delete a plugin
 */
async function deletePlugin(
  request: FastifyRequest<GetPluginRequest>,
  reply: FastifyReply,
) {
  try {
    const { pluginId } = request.params;
    const prisma = (request.server as FastifyInstance & { prisma: any }).prisma;

    const existing = await prisma.kernelPlugin.findUnique({
      where: { pluginId },
    });
    if (!existing) {
      return reply.code(404).send({ error: "Plugin not found", pluginId });
    }

    await prisma.kernelPlugin.delete({ where: { pluginId } });
    return reply.code(204).send();
  } catch (error) {
    request.log.error(error, "Error deleting plugin");
    return reply.code(500).send({ error: "Internal server error" });
  }
}

/**
 * Update a plugin
 */
async function updatePlugin(
  request: FastifyRequest<RegisterPluginRequest & GetPluginRequest>,
  reply: FastifyReply,
) {
  try {
    const { pluginId } = request.params;
    const metadata = request.body;
    const prisma = (request.server as FastifyInstance & { prisma: any }).prisma;

    // Validate plugin metadata
    const validation = validatePluginMetadata(metadata);
    if (!validation.valid) {
      return reply.code(400).send({
        error: "Invalid plugin metadata",
        details: validation.errors,
      });
    }

    const existing = await prisma.kernelPlugin.findUnique({
      where: { pluginId },
    });
    if (!existing) {
      return reply.code(404).send({ error: "Plugin not found", pluginId });
    }

    const row = await prisma.kernelPlugin.update({
      where: { pluginId },
      data: {
        name: metadata.name,
        version: metadata.version,
        description: metadata.description ?? null,
        author: metadata.author ?? null,
        kernelType: metadata.kernelType,
        capabilities: JSON.stringify(metadata.capabilities),
        dependencies: metadata.dependencies
          ? JSON.stringify(metadata.dependencies)
          : null,
      },
    });

    return reply.send({
      message: "Plugin updated successfully",
      plugin: rowToMetadata(row),
    });
  } catch (error) {
    request.log.error(error, "Error updating plugin");
    return reply.code(500).send({ error: "Internal server error" });
  }
}

// =============================================================================
// Route Registration
// =============================================================================

/**
 * Register kernel registry routes with Fastify
 */
export async function registerKernelRegistryRoutes(fastify: FastifyInstance) {
  // Register plugin
  fastify.post("/api/v1/plugins", registerPlugin);

  // Get plugin by ID
  fastify.get("/api/v1/plugins/:pluginId", getPlugin);

  // List plugins
  fastify.get("/api/v1/plugins", listPlugins);

  // Update plugin
  fastify.put("/api/v1/plugins/:pluginId", updatePlugin);

  // Delete plugin
  fastify.delete("/api/v1/plugins/:pluginId", deletePlugin);

  // Health check for kernel registry
  fastify.get("/api/v1/plugins/health", async (request, reply) => {
    const prisma = (fastify as FastifyInstance & { prisma: any }).prisma;
    const pluginCount = await prisma.kernelPlugin.count();
    return reply.send({
      status: "ok",
      service: "kernel-registry",
      pluginCount,
    });
  });
}
