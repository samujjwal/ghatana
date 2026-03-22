/**
 * Kernel Registry Fastify Routes
 * Converted from Hono to Fastify
 *
 * @doc.type routes
 * @doc.purpose Kernel plugin registry API routes for Fastify
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { validatePluginMetadata } from '../validation/plugin-policy.js';

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
// In-Memory Plugin Registry (TODO: Replace with database)
// =============================================================================

const pluginRegistry = new Map<string, PluginMetadata>();

// =============================================================================
// Route Handlers
// =============================================================================

/**
 * Register a new plugin
 */
async function registerPlugin(
  request: FastifyRequest<RegisterPluginRequest>,
  reply: FastifyReply
) {
  try {
    const metadata = request.body;

    // Validate plugin metadata
    const validation = validatePluginMetadata(metadata);
    if (!validation.valid) {
      return reply.code(400).send({
        error: 'Invalid plugin metadata',
        details: validation.errors,
      });
    }

    // Check if plugin already exists
    if (pluginRegistry.has(metadata.id)) {
      return reply.code(409).send({
        error: 'Plugin already registered',
        pluginId: metadata.id,
      });
    }

    // Register plugin
    pluginRegistry.set(metadata.id, metadata);

    return reply.code(201).send({
      message: 'Plugin registered successfully',
      plugin: metadata,
    });
  } catch (error) {
    request.log.error(error, 'Error registering plugin');
    return reply.code(500).send({
      error: 'Internal server error',
    });
  }
}

/**
 * Get plugin by ID
 */
async function getPlugin(
  request: FastifyRequest<GetPluginRequest>,
  reply: FastifyReply
) {
  try {
    const { pluginId } = request.params;

    const plugin = pluginRegistry.get(pluginId);
    if (!plugin) {
      return reply.code(404).send({
        error: 'Plugin not found',
        pluginId,
      });
    }

    return reply.send(plugin);
  } catch (error) {
    request.log.error(error, 'Error getting plugin');
    return reply.code(500).send({
      error: 'Internal server error',
    });
  }
}

/**
 * List all plugins with optional filters
 */
async function listPlugins(
  request: FastifyRequest<ListPluginsRequest>,
  reply: FastifyReply
) {
  try {
    const { kernelType, capability } = request.query;

    let plugins = Array.from(pluginRegistry.values());

    // Filter by kernel type
    if (kernelType) {
      plugins = plugins.filter((p) => p.kernelType === kernelType);
    }

    // Filter by capability
    if (capability) {
      plugins = plugins.filter((p) => p.capabilities.includes(capability));
    }

    return reply.send({
      plugins,
      count: plugins.length,
    });
  } catch (error) {
    request.log.error(error, 'Error listing plugins');
    return reply.code(500).send({
      error: 'Internal server error',
    });
  }
}

/**
 * Delete a plugin
 */
async function deletePlugin(
  request: FastifyRequest<GetPluginRequest>,
  reply: FastifyReply
) {
  try {
    const { pluginId } = request.params;

    const existed = pluginRegistry.delete(pluginId);
    if (!existed) {
      return reply.code(404).send({
        error: 'Plugin not found',
        pluginId,
      });
    }

    return reply.code(204).send();
  } catch (error) {
    request.log.error(error, 'Error deleting plugin');
    return reply.code(500).send({
      error: 'Internal server error',
    });
  }
}

/**
 * Update a plugin
 */
async function updatePlugin(
  request: FastifyRequest<RegisterPluginRequest & GetPluginRequest>,
  reply: FastifyReply
) {
  try {
    const { pluginId } = request.params;
    const metadata = request.body;

    // Validate plugin metadata
    const validation = validatePluginMetadata(metadata);
    if (!validation.valid) {
      return reply.code(400).send({
        error: 'Invalid plugin metadata',
        details: validation.errors,
      });
    }

    // Check if plugin exists
    if (!pluginRegistry.has(pluginId)) {
      return reply.code(404).send({
        error: 'Plugin not found',
        pluginId,
      });
    }

    // Update plugin
    pluginRegistry.set(pluginId, { ...metadata, id: pluginId });

    return reply.send({
      message: 'Plugin updated successfully',
      plugin: pluginRegistry.get(pluginId),
    });
  } catch (error) {
    request.log.error(error, 'Error updating plugin');
    return reply.code(500).send({
      error: 'Internal server error',
    });
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
  fastify.post('/api/v1/plugins', registerPlugin);

  // Get plugin by ID
  fastify.get('/api/v1/plugins/:pluginId', getPlugin);

  // List plugins
  fastify.get('/api/v1/plugins', listPlugins);

  // Update plugin
  fastify.put('/api/v1/plugins/:pluginId', updatePlugin);

  // Delete plugin
  fastify.delete('/api/v1/plugins/:pluginId', deletePlugin);

  // Health check for kernel registry
  fastify.get('/api/v1/plugins/health', async (request, reply) => {
    return reply.send({
      status: 'ok',
      service: 'kernel-registry',
      pluginCount: pluginRegistry.size,
    });
  });
}

// Export for testing
export { pluginRegistry };
