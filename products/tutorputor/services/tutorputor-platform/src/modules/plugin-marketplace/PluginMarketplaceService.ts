/**
 * Plugin Marketplace Service
 *
 * Support third-party extensions with lifecycle hooks and migration orchestration.
 *
 * @doc.type service
 * @doc.purpose Plugin marketplace service for third-party extension management
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export type PluginStatus = "available" | "installed" | "active" | "inactive" | "error";

export interface PluginManifest {
  id: string;
  name: string;
  version: string;
  description: string;
  author: string;
  category: string;
  capabilities: string[];
  dependencies: string[];
  lifecycleHooks: {
    onInstall?: string;
    onActivate?: string;
    onDeactivate?: string;
    onUninstall?: string;
    onMigrate?: string;
  };
  permissions: string[];
}

export interface TenantPlugin {
  id: string;
  tenantId: string;
  pluginId: string;
  version: string;
  status: PluginStatus;
  configuration: Record<string, unknown>;
  installedAt: Date;
  activatedAt: Date | null;
  lastMigratedAt: Date | null;
}

export class PluginMarketplaceService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Get available plugins from marketplace
   */
  async getAvailablePlugins(): Promise<PluginManifest[]> {
    throw new Error('Plugin marketplace requires remote registry integration. Implement plugin marketplace API or local plugin registry.');
  }

  /**
   * Get plugin manifest by ID
   */
  async getPluginManifest(pluginId: string): Promise<PluginManifest | null> {
    const plugins = await this.getAvailablePlugins();
    return plugins.find((p) => p.id === pluginId) || null;
  }

  /**
   * Get installed plugins for tenant
   */
  async getTenantPlugins(tenantId: string): Promise<TenantPlugin[]> {
    const tenantPlugins = await this.prisma.tenantPlugin.findMany({
      where: { tenantId },
      orderBy: { installedAt: "desc" },
    });

    return tenantPlugins.map((tp) => ({
      id: tp.id,
      tenantId: tp.tenantId,
      pluginId: tp.pluginId,
      version: tp.version,
      status: tp.status as PluginStatus,
      configuration: tp.configuration ? JSON.parse(tp.configuration as string) : {},
      installedAt: tp.installedAt,
      activatedAt: tp.activatedAt,
      lastMigratedAt: tp.lastMigratedAt,
    }));
  }

  /**
   * Install plugin for tenant
   */
  async installPlugin(tenantId: string, pluginId: string, configuration: Record<string, unknown> = {}): Promise<TenantPlugin> {
    const manifest = await this.getPluginManifest(pluginId);

    if (!manifest) {
      throw new Error(`Plugin not found: ${pluginId}`);
    }

    // Check if already installed
    const existing = await this.prisma.tenantPlugin.findFirst({
      where: { tenantId, pluginId },
    });

    if (existing) {
      throw new Error(`Plugin already installed for tenant: ${pluginId}`);
    }

    // Check dependencies
    for (const dep of manifest.dependencies) {
      const depInstalled = await this.prisma.tenantPlugin.findFirst({
        where: { tenantId, pluginId: dep, status: "active" },
      });

      if (!depInstalled) {
        throw new Error(`Dependency not installed or active: ${dep}`);
      }
    }

    // Execute onInstall lifecycle hook (placeholder)
    await this.executeLifecycleHook(manifest.lifecycleHooks.onInstall, tenantId, configuration);

    // Create tenant plugin record
    const tenantPlugin = await this.prisma.tenantPlugin.create({
      data: {
        tenantId,
        pluginId,
        version: manifest.version,
        status: "installed",
        configuration: JSON.stringify(configuration),
        installedAt: new Date(),
        activatedAt: null,
        lastMigratedAt: null,
      },
    });

    return {
      id: tenantPlugin.id,
      tenantId: tenantPlugin.tenantId,
      pluginId: tenantPlugin.pluginId,
      version: tenantPlugin.version,
      status: tenantPlugin.status as PluginStatus,
      configuration: tenantPlugin.configuration ? JSON.parse(tenantPlugin.configuration as string) : {},
      installedAt: tenantPlugin.installedAt,
      activatedAt: tenantPlugin.activatedAt,
      lastMigratedAt: tenantPlugin.lastMigratedAt,
    };
  }

  /**
   * Activate plugin for tenant
   */
  async activatePlugin(tenantId: string, pluginId: string): Promise<TenantPlugin> {
    const manifest = await this.getPluginManifest(pluginId);

    if (!manifest) {
      throw new Error(`Plugin not found: ${pluginId}`);
    }

    const tenantPlugin = await this.prisma.tenantPlugin.findFirst({
      where: { tenantId, pluginId },
    });

    if (!tenantPlugin) {
      throw new Error(`Plugin not installed for tenant: ${pluginId}`);
    }

    if (tenantPlugin.status === "active") {
      throw new Error(`Plugin already active: ${pluginId}`);
    }

    // Execute onActivate lifecycle hook (placeholder)
    await this.executeLifecycleHook(manifest.lifecycleHooks.onActivate, tenantId, tenantPlugin.configuration ? JSON.parse(tenantPlugin.configuration as string) : {});

    // Update status to active
    const updated = await this.prisma.tenantPlugin.update({
      where: { id: tenantPlugin.id },
      data: {
        status: "active",
        activatedAt: new Date(),
      },
    });

    return {
      id: updated.id,
      tenantId: updated.tenantId,
      pluginId: updated.pluginId,
      version: updated.version,
      status: updated.status as PluginStatus,
      configuration: updated.configuration ? JSON.parse(updated.configuration as string) : {},
      installedAt: updated.installedAt,
      activatedAt: updated.activatedAt,
      lastMigratedAt: updated.lastMigratedAt,
    };
  }

  /**
   * Deactivate plugin for tenant
   */
  async deactivatePlugin(tenantId: string, pluginId: string): Promise<TenantPlugin> {
    const manifest = await this.getPluginManifest(pluginId);

    if (!manifest) {
      throw new Error(`Plugin not found: ${pluginId}`);
    }

    const tenantPlugin = await this.prisma.tenantPlugin.findFirst({
      where: { tenantId, pluginId },
    });

    if (!tenantPlugin) {
      throw new Error(`Plugin not installed for tenant: ${pluginId}`);
    }

    if (tenantPlugin.status !== "active") {
      throw new Error(`Plugin not active: ${pluginId}`);
    }

    // Execute onDeactivate lifecycle hook (placeholder)
    await this.executeLifecycleHook(manifest.lifecycleHooks.onDeactivate, tenantId, tenantPlugin.configuration ? JSON.parse(tenantPlugin.configuration as string) : {});

    // Update status to inactive
    const updated = await this.prisma.tenantPlugin.update({
      where: { id: tenantPlugin.id },
      data: {
        status: "inactive",
        activatedAt: null,
      },
    });

    return {
      id: updated.id,
      tenantId: updated.tenantId,
      pluginId: updated.pluginId,
      version: updated.version,
      status: updated.status as PluginStatus,
      configuration: updated.configuration ? JSON.parse(updated.configuration as string) : {},
      installedAt: updated.installedAt,
      activatedAt: updated.activatedAt,
      lastMigratedAt: updated.lastMigratedAt,
    };
  }

  /**
   * Uninstall plugin for tenant
   */
  async uninstallPlugin(tenantId: string, pluginId: string): Promise<void> {
    const manifest = await this.getPluginManifest(pluginId);

    if (!manifest) {
      throw new Error(`Plugin not found: ${pluginId}`);
    }

    const tenantPlugin = await this.prisma.tenantPlugin.findFirst({
      where: { tenantId, pluginId },
    });

    if (!tenantPlugin) {
      throw new Error(`Plugin not installed for tenant: ${pluginId}`);
    }

    // Deactivate if active
    if (tenantPlugin.status === "active") {
      await this.deactivatePlugin(tenantId, pluginId);
    }

    // Execute onUninstall lifecycle hook (placeholder)
    await this.executeLifecycleHook(manifest.lifecycleHooks.onUninstall, tenantId, tenantPlugin.configuration ? JSON.parse(tenantPlugin.configuration as string) : {});

    // Delete tenant plugin record
    await this.prisma.tenantPlugin.delete({
      where: { id: tenantPlugin.id },
    });
  }

  /**
   * Migrate plugin for tenant
   */
  async migratePlugin(tenantId: string, pluginId: string, targetVersion: string): Promise<TenantPlugin> {
    const manifest = await this.getPluginManifest(pluginId);

    if (!manifest) {
      throw new Error(`Plugin not found: ${pluginId}`);
    }

    const tenantPlugin = await this.prisma.tenantPlugin.findFirst({
      where: { tenantId, pluginId },
    });

    if (!tenantPlugin) {
      throw new Error(`Plugin not installed for tenant: ${pluginId}`);
    }

    // Execute onMigrate lifecycle hook (placeholder)
    await this.executeLifecycleHook(manifest.lifecycleHooks.onMigrate, tenantId, {
      ...(tenantPlugin.configuration ? JSON.parse(tenantPlugin.configuration as string) : {}),
      targetVersion,
    });

    // Update version and migration timestamp
    const updated = await this.prisma.tenantPlugin.update({
      where: { id: tenantPlugin.id },
      data: {
        version: targetVersion,
        lastMigratedAt: new Date(),
      },
    });

    return {
      id: updated.id,
      tenantId: updated.tenantId,
      pluginId: updated.pluginId,
      version: updated.version,
      status: updated.status as PluginStatus,
      configuration: updated.configuration ? JSON.parse(updated.configuration as string) : {},
      installedAt: updated.installedAt,
      activatedAt: updated.activatedAt,
      lastMigratedAt: updated.lastMigratedAt,
    };
  }

  /**
   * Update plugin configuration
   */
  async updatePluginConfiguration(tenantId: string, pluginId: string, configuration: Record<string, unknown>): Promise<TenantPlugin> {
    const tenantPlugin = await this.prisma.tenantPlugin.findFirst({
      where: { tenantId, pluginId },
    });

    if (!tenantPlugin) {
      throw new Error(`Plugin not installed for tenant: ${pluginId}`);
    }

    const updated = await this.prisma.tenantPlugin.update({
      where: { id: tenantPlugin.id },
      data: {
        configuration: JSON.stringify(configuration),
      },
    });

    return {
      id: updated.id,
      tenantId: updated.tenantId,
      pluginId: updated.pluginId,
      version: updated.version,
      status: updated.status as PluginStatus,
      configuration: updated.configuration ? JSON.parse(updated.configuration as string) : {},
      installedAt: updated.installedAt,
      activatedAt: updated.activatedAt,
      lastMigratedAt: updated.lastMigratedAt,
    };
  }

  /**
   * Execute lifecycle hook (placeholder implementation)
   */
  private async executeLifecycleHook(hookName: string | undefined, tenantId: string, context: Record<string, unknown>): Promise<void> {
    if (!hookName) {
      return;
    }

    // In a real implementation, this would:
    // 1. Load the plugin's hook implementation
    // 2. Execute it with the provided context
    // 3. Handle errors and rollbacks
    // For now, this is a placeholder that logs the hook execution

    console.log(`Executing lifecycle hook: ${hookName} for tenant: ${tenantId}`);
  }
}
