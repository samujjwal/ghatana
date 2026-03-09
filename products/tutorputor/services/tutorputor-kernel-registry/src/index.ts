/**
 * Kernel Registry Service Entry Point
 *
 * @doc.type entry
 * @doc.purpose Main entry for kernel plugin registry service
 * @doc.layer product
 * @doc.pattern Entry
 */

import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import { serve } from "@hono/node-server";

import { pluginRoutes } from "./routes/plugins";

// =============================================================================
// App Setup
// =============================================================================

const app = new Hono();

// Middleware
app.use("*", logger());
app.use("*", cors());

// Health check
app.get("/health", (c) => c.json({ status: "ok", service: "kernel-registry" }));

// Mount routes
app.route("/api/v1/plugins", pluginRoutes);

// =============================================================================
// Exports
// =============================================================================

export { app };

// Validation exports
export * from "./validation/plugin-policy";

// Route exports
export { pluginRoutes } from "./routes/plugins";

// =============================================================================
// Server Startup
// =============================================================================

const port = parseInt(process.env.PORT || "3400", 10);

if (process.env.NODE_ENV !== "test") {
  console.log(`🔌 Kernel Registry starting on port ${port}`);
  serve({
    fetch: app.fetch,
    port,
  });
}
