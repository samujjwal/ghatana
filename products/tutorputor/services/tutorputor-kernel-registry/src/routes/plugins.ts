/**
 * Plugin Routes - API endpoints for plugin publishing and management.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for kernel plugin CRUD operations
 * @doc.layer product
 * @doc.pattern Route
 */

import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import {
  validatePluginSubmission,
  pluginSubmissionSchema,
  checkVersionCompatibility,
  scanForProhibitedDependencies,
  type PluginSubmission,
  type PolicyResult,
} from "../validation/plugin-policy";

// =============================================================================
// Types
// =============================================================================

export interface StoredPlugin {
  id: string;
  type: "kernel" | "promptPack" | "visualizer";
  metadata: PluginSubmission["metadata"];
  domain: string;
  language: string;
  bundleUrl: string;
  sourceMapUrl?: string;
  documentation?: string;
  resourceLimits: PluginSubmission["resourceLimits"];
  status: "pending" | "approved" | "rejected" | "deprecated";
  reviewNotes?: string;
  publisherId: string;
  publisherName: string;
  downloads: number;
  rating: number;
  ratingCount: number;
  createdAt: Date;
  updatedAt: Date;
  approvedAt?: Date;
  versions: Array<{
    version: string;
    bundleUrl: string;
    createdAt: Date;
  }>;
}

export interface PluginListFilters {
  type?: "kernel" | "promptPack" | "visualizer";
  domain?: string;
  status?: "pending" | "approved" | "rejected" | "deprecated";
  search?: string;
  publisherId?: string;
  minRating?: number;
  sort?: "newest" | "popular" | "rating" | "name";
  limit?: number;
  offset?: number;
}

// =============================================================================
// In-Memory Store (replace with database in production)
// =============================================================================

const pluginStore = new Map<string, StoredPlugin>();

// =============================================================================
// Route Handlers
// =============================================================================

const pluginRoutes = new Hono();

/**
 * List plugins with filters.
 * GET /plugins
 */
pluginRoutes.get(
  "/",
  zValidator(
    "query",
    z.object({
      type: z.enum(["kernel", "promptPack", "visualizer"]).optional(),
      domain: z.string().optional(),
      status: z.enum(["pending", "approved", "rejected", "deprecated"]).optional(),
      search: z.string().optional(),
      publisherId: z.string().optional(),
      minRating: z.coerce.number().min(0).max(5).optional(),
      sort: z.enum(["newest", "popular", "rating", "name"]).optional(),
      limit: z.coerce.number().int().min(1).max(100).default(20),
      offset: z.coerce.number().int().min(0).default(0),
    })
  ),
  async (c) => {
    const filters = c.req.valid("query");

    let plugins = Array.from(pluginStore.values());

    // Apply filters
    if (filters.type) {
      plugins = plugins.filter((p) => p.type === filters.type);
    }
    if (filters.domain) {
      plugins = plugins.filter((p) => p.domain === filters.domain);
    }
    if (filters.status) {
      plugins = plugins.filter((p) => p.status === filters.status);
    }
    if (filters.publisherId) {
      plugins = plugins.filter((p) => p.publisherId === filters.publisherId);
    }
    if (filters.minRating !== undefined) {
      plugins = plugins.filter((p) => p.rating >= filters.minRating!);
    }
    if (filters.search) {
      const searchLower = filters.search.toLowerCase();
      plugins = plugins.filter(
        (p) =>
          p.metadata.name.toLowerCase().includes(searchLower) ||
          p.metadata.description.toLowerCase().includes(searchLower) ||
          p.metadata.tags?.some((t) => t.toLowerCase().includes(searchLower))
      );
    }

    // Apply sorting
    switch (filters.sort) {
      case "popular":
        plugins.sort((a, b) => b.downloads - a.downloads);
        break;
      case "rating":
        plugins.sort((a, b) => b.rating - a.rating);
        break;
      case "name":
        plugins.sort((a, b) => a.metadata.name.localeCompare(b.metadata.name));
        break;
      case "newest":
      default:
        plugins.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
    }

    // Apply pagination
    const total = plugins.length;
    plugins = plugins.slice(filters.offset, filters.offset + filters.limit);

    return c.json({
      plugins: plugins.map((p) => ({
        id: p.id,
        type: p.type,
        metadata: p.metadata,
        domain: p.domain,
        status: p.status,
        downloads: p.downloads,
        rating: p.rating,
        ratingCount: p.ratingCount,
        createdAt: p.createdAt.toISOString(),
        updatedAt: p.updatedAt.toISOString(),
      })),
      total,
      hasMore: filters.offset + plugins.length < total,
    });
  }
);

/**
 * Get plugin by ID.
 * GET /plugins/:id
 */
pluginRoutes.get("/:id", async (c) => {
  const id = c.req.param("id");
  const plugin = pluginStore.get(id);

  if (!plugin) {
    return c.json({ error: "Plugin not found" }, 404);
  }

  return c.json({
    ...plugin,
    createdAt: plugin.createdAt.toISOString(),
    updatedAt: plugin.updatedAt.toISOString(),
    approvedAt: plugin.approvedAt?.toISOString(),
    versions: plugin.versions.map((v) => ({
      ...v,
      createdAt: v.createdAt.toISOString(),
    })),
  });
});

/**
 * Submit a new plugin.
 * POST /plugins
 */
pluginRoutes.post(
  "/",
  zValidator("json", pluginSubmissionSchema),
  async (c) => {
    const submission = c.req.valid("json");

    // Get publisher from auth context (simplified)
    const publisherId = c.req.header("X-Publisher-Id") || "anonymous";
    const publisherName = c.req.header("X-Publisher-Name") || "Anonymous";

    // Validate submission against policy
    const policyResult = validatePluginSubmission(submission);
    if (!policyResult.passed) {
      return c.json(
        {
          error: "Policy validation failed",
          violations: policyResult.violations,
          warnings: policyResult.warnings,
        },
        400
      );
    }

    // Check if plugin ID already exists
    if (pluginStore.has(submission.metadata.id)) {
      return c.json(
        {
          error: "Plugin ID already exists",
          message: "Use PUT /plugins/:id to update an existing plugin",
        },
        409
      );
    }

    // If bundle is provided as base64, scan for prohibited dependencies
    if (submission.bundleBase64) {
      const code = Buffer.from(submission.bundleBase64, "base64").toString("utf-8");
      const scanResult = scanForProhibitedDependencies(code);
      if (!scanResult.passed) {
        return c.json(
          {
            error: "Bundle security scan failed",
            violations: scanResult.violations,
          },
          400
        );
      }
    }

    // Create stored plugin
    const now = new Date();
    const storedPlugin: StoredPlugin = {
      id: submission.metadata.id,
      type: submission.type,
      metadata: submission.metadata,
      domain: submission.domain,
      language: submission.language,
      bundleUrl: submission.bundleUrl || `data:text/javascript;base64,${submission.bundleBase64}`,
      sourceMapUrl: submission.sourceMapUrl,
      documentation: submission.documentation,
      resourceLimits: submission.resourceLimits,
      status: "pending",
      publisherId,
      publisherName,
      downloads: 0,
      rating: 0,
      ratingCount: 0,
      createdAt: now,
      updatedAt: now,
      versions: [
        {
          version: submission.metadata.version,
          bundleUrl: submission.bundleUrl || `data:text/javascript;base64,${submission.bundleBase64}`,
          createdAt: now,
        },
      ],
    };

    pluginStore.set(storedPlugin.id, storedPlugin);

    return c.json(
      {
        id: storedPlugin.id,
        status: storedPlugin.status,
        message: "Plugin submitted for review",
        warnings: policyResult.warnings,
      },
      201
    );
  }
);

/**
 * Update an existing plugin (new version).
 * PUT /plugins/:id
 */
pluginRoutes.put(
  "/:id",
  zValidator("json", pluginSubmissionSchema),
  async (c) => {
    const id = c.req.param("id");
    const submission = c.req.valid("json");

    // Check publisher ownership
    const publisherId = c.req.header("X-Publisher-Id") || "anonymous";

    const existingPlugin = pluginStore.get(id);
    if (!existingPlugin) {
      return c.json({ error: "Plugin not found" }, 404);
    }

    if (existingPlugin.publisherId !== publisherId) {
      return c.json({ error: "Not authorized to update this plugin" }, 403);
    }

    // Validate submission
    const policyResult = validatePluginSubmission(submission);
    if (!policyResult.passed) {
      return c.json(
        {
          error: "Policy validation failed",
          violations: policyResult.violations,
        },
        400
      );
    }

    // Check version increment
    const currentVersion = existingPlugin.metadata.version;
    const newVersion = submission.metadata.version;
    const versionResult = checkVersionCompatibility(currentVersion, newVersion);
    if (!versionResult.passed) {
      return c.json(
        {
          error: "Version validation failed",
          violations: versionResult.violations,
        },
        400
      );
    }

    // Security scan
    if (submission.bundleBase64) {
      const code = Buffer.from(submission.bundleBase64, "base64").toString("utf-8");
      const scanResult = scanForProhibitedDependencies(code);
      if (!scanResult.passed) {
        return c.json(
          {
            error: "Bundle security scan failed",
            violations: scanResult.violations,
          },
          400
        );
      }
    }

    // Update plugin
    const now = new Date();
    const updatedPlugin: StoredPlugin = {
      ...existingPlugin,
      metadata: submission.metadata,
      bundleUrl: submission.bundleUrl || `data:text/javascript;base64,${submission.bundleBase64}`,
      sourceMapUrl: submission.sourceMapUrl,
      documentation: submission.documentation ?? existingPlugin.documentation,
      resourceLimits: submission.resourceLimits ?? existingPlugin.resourceLimits,
      status: existingPlugin.status === "approved" ? "approved" : "pending",
      updatedAt: now,
      versions: [
        ...existingPlugin.versions,
        {
          version: newVersion,
          bundleUrl: submission.bundleUrl || `data:text/javascript;base64,${submission.bundleBase64}`,
          createdAt: now,
        },
      ],
    };

    pluginStore.set(id, updatedPlugin);

    return c.json({
      id: updatedPlugin.id,
      version: newVersion,
      status: updatedPlugin.status,
      message: "Plugin updated successfully",
      warnings: [...policyResult.warnings, ...versionResult.warnings],
    });
  }
);

/**
 * Delete a plugin.
 * DELETE /plugins/:id
 */
pluginRoutes.delete("/:id", async (c) => {
  const id = c.req.param("id");
  const publisherId = c.req.header("X-Publisher-Id") || "anonymous";

  const plugin = pluginStore.get(id);
  if (!plugin) {
    return c.json({ error: "Plugin not found" }, 404);
  }

  if (plugin.publisherId !== publisherId) {
    return c.json({ error: "Not authorized to delete this plugin" }, 403);
  }

  pluginStore.delete(id);

  return c.json({ message: "Plugin deleted successfully" });
});

/**
 * Approve a plugin (admin only).
 * POST /plugins/:id/approve
 */
pluginRoutes.post("/:id/approve", async (c) => {
  const id = c.req.param("id");
  const isAdmin = c.req.header("X-Is-Admin") === "true";

  if (!isAdmin) {
    return c.json({ error: "Admin access required" }, 403);
  }

  const plugin = pluginStore.get(id);
  if (!plugin) {
    return c.json({ error: "Plugin not found" }, 404);
  }

  plugin.status = "approved";
  plugin.approvedAt = new Date();
  plugin.updatedAt = new Date();

  pluginStore.set(id, plugin);

  return c.json({ message: "Plugin approved", status: "approved" });
});

/**
 * Reject a plugin (admin only).
 * POST /plugins/:id/reject
 */
pluginRoutes.post(
  "/:id/reject",
  zValidator("json", z.object({ reason: z.string().min(1) })),
  async (c) => {
    const id = c.req.param("id");
    const { reason } = c.req.valid("json");
    const isAdmin = c.req.header("X-Is-Admin") === "true";

    if (!isAdmin) {
      return c.json({ error: "Admin access required" }, 403);
    }

    const plugin = pluginStore.get(id);
    if (!plugin) {
      return c.json({ error: "Plugin not found" }, 404);
    }

    plugin.status = "rejected";
    plugin.reviewNotes = reason;
    plugin.updatedAt = new Date();

    pluginStore.set(id, plugin);

    return c.json({ message: "Plugin rejected", status: "rejected", reason });
  }
);

/**
 * Download/fetch plugin bundle.
 * GET /plugins/:id/bundle
 */
pluginRoutes.get("/:id/bundle", async (c) => {
  const id = c.req.param("id");
  const version = c.req.query("version");

  const plugin = pluginStore.get(id);
  if (!plugin) {
    return c.json({ error: "Plugin not found" }, 404);
  }

  if (plugin.status !== "approved") {
    return c.json({ error: "Plugin not approved" }, 403);
  }

  // Increment download counter
  plugin.downloads++;
  pluginStore.set(id, plugin);

  // Get specific version or latest
  let bundleUrl: string;
  if (version) {
    const versionEntry = plugin.versions.find((v) => v.version === version);
    if (!versionEntry) {
      return c.json({ error: "Version not found" }, 404);
    }
    bundleUrl = versionEntry.bundleUrl;
  } else {
    bundleUrl = plugin.bundleUrl;
  }

  return c.json({
    id: plugin.id,
    version: plugin.metadata.version,
    bundleUrl,
    resourceLimits: plugin.resourceLimits,
  });
});

/**
 * Rate a plugin.
 * POST /plugins/:id/rate
 */
pluginRoutes.post(
  "/:id/rate",
  zValidator(
    "json",
    z.object({
      rating: z.number().int().min(1).max(5),
      review: z.string().max(2048).optional(),
    })
  ),
  async (c) => {
    const id = c.req.param("id");
    const { rating } = c.req.valid("json");

    const plugin = pluginStore.get(id);
    if (!plugin) {
      return c.json({ error: "Plugin not found" }, 404);
    }

    // Calculate new average rating
    const totalRating = plugin.rating * plugin.ratingCount + rating;
    plugin.ratingCount++;
    plugin.rating = totalRating / plugin.ratingCount;
    plugin.updatedAt = new Date();

    pluginStore.set(id, plugin);

    return c.json({
      rating: plugin.rating,
      ratingCount: plugin.ratingCount,
    });
  }
);

export { pluginRoutes };
export default pluginRoutes;
