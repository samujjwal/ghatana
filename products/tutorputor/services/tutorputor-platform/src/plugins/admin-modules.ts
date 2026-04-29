import type { FastifyPluginAsync } from "fastify";
import { notificationRoutes } from "../modules/notifications/index.js";
import { featureFlagsModule } from "../modules/feature-flags/index.js";
import { tenantModule } from "../modules/tenant/index.js";
import { complianceModule } from "../modules/compliance/index.js";
import { auditModule } from "../modules/audit/index.js";

/**
 * Admin / platform-ops module plugin.
 * Registers notifications, feature flags, tenant, compliance, and audit modules.
 *
 * @doc.type module
 * @doc.purpose Register admin/platform-ops modules (notifications, feature flags, tenant, compliance, audit)
 * @doc.layer platform
 * @doc.pattern Plugin
 */
export const setupAdminModules: FastifyPluginAsync = async (app) => {
  // Register notifications module
  await notificationRoutes(app);

  // Register feature flags module
  await app.register(featureFlagsModule, { prefix: "/api/v1/feature-flags" });

  // Register tenant module
  await app.register(tenantModule, { prefix: "/api/v1/tenants" });

  // Register compliance module
  await app.register(complianceModule, { prefix: "/api/v1/compliance" });

  // Register audit module
  await app.register(auditModule, { prefix: "/api/v1/audit" });

  app.log.info("✅ Admin/platform-ops modules registered");
};
