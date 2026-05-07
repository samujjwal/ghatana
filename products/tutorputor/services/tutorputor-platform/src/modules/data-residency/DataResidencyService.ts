/**
 * Data Residency Service
 *
 * Enforce tenant data residency policies in runtime routing and storage.
 *
 * @doc.type service
 * @doc.purpose Enforce tenant data residency policies for multi-region compliance
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export type Region = "US" | "EU" | "APAC" | "GLOBAL";

export interface TenantResidencyConfig {
  tenantId: string;
  primaryRegion: Region;
  allowedRegions: Region[];
  dataRetentionDays: number;
  crossBorderTransferAllowed: boolean;
  complianceFrameworks: string[];
}

interface TenantResidencySettingsRecord {
  tenantId: string;
  dataResidencyRegion?: string | null;
  allowedRegions?: string | null;
  dataRetentionDays?: number | null;
  crossBorderTransferAllowed?: boolean | null;
  complianceFrameworks?: string | null;
}

export class DataResidencyService {
  private residencyCache = new Map<string, TenantResidencyConfig>();

  constructor(private prisma: PrismaClient) {}

  /**
   * Get tenant residency configuration
   */
  async getTenantResidencyConfig(tenantId: string): Promise<TenantResidencyConfig> {
    // Check cache first
    if (this.residencyCache.has(tenantId)) {
      return this.residencyCache.get(tenantId)!;
    }

    // Fetch from database
    const tenant = await this.prisma.tenant.findUnique({
      where: { id: tenantId },
      include: { settings: true },
    });

    if (!tenant) {
      throw new Error(`Tenant not found: ${tenantId}`);
    }

    const settings = tenant.settings as unknown as TenantResidencySettingsRecord | null;

    const config: TenantResidencyConfig = {
      tenantId,
      primaryRegion: this.parsePrimaryRegion(settings?.dataResidencyRegion),
      allowedRegions: this.parseAllowedRegions(settings?.allowedRegions ?? undefined),
      dataRetentionDays: settings?.dataRetentionDays || 365,
      crossBorderTransferAllowed: settings?.crossBorderTransferAllowed ?? false,
      complianceFrameworks: this.parseComplianceFrameworks(settings?.complianceFrameworks ?? undefined),
    };

    // Cache the configuration
    this.residencyCache.set(tenantId, config);

    return config;
  }

  /**
   * Validate data access request against residency policy
   */
  async validateDataAccess(
    tenantId: string,
    requestRegion: Region,
    operation: "read" | "write" | "delete",
  ): Promise<{ allowed: boolean; reason?: string }> {
    const config = await this.getTenantResidencyConfig(tenantId);

    // Check if request region is allowed
    if (!config.allowedRegions.includes(requestRegion) && requestRegion !== config.primaryRegion) {
      return {
        allowed: false,
        reason: `Region ${requestRegion} is not allowed for tenant ${tenantId}. Allowed regions: ${config.allowedRegions.join(", ")}`,
      };
    }

    // Check cross-border transfer restrictions
    if (!config.crossBorderTransferAllowed && requestRegion !== config.primaryRegion && operation === "read") {
      return {
        allowed: false,
        reason: `Cross-border data transfer not allowed for tenant ${tenantId}. Request from ${requestRegion} to ${config.primaryRegion} denied.`,
      };
    }

    return { allowed: true };
  }

  /**
   * Set tenant residency configuration
   */
  async setTenantResidencyConfig(config: Partial<TenantResidencyConfig>): Promise<void> {
    const { tenantId, primaryRegion, allowedRegions, dataRetentionDays, crossBorderTransferAllowed, complianceFrameworks } = config;

    if (!tenantId) {
      throw new Error("tenantId is required");
    }

    // Update tenant settings
    const tenant = await this.prisma.tenant.findUnique({ where: { id: tenantId } });

    if (!tenant) {
      throw new Error(`Tenant not found: ${tenantId}`);
    }

    const tenantSettings = await this.prisma.tenantSettings.findUnique({ where: { tenantId } });
    const settings: TenantResidencySettingsRecord = tenantSettings
      ? (tenantSettings as unknown as TenantResidencySettingsRecord)
      : { tenantId };

    if (primaryRegion) {
      settings.dataResidencyRegion = primaryRegion;
    }

    if (allowedRegions) {
      settings.allowedRegions = allowedRegions.join(",");
    }

    if (dataRetentionDays !== undefined) {
      settings.dataRetentionDays = dataRetentionDays;
    }

    if (crossBorderTransferAllowed !== undefined) {
      settings.crossBorderTransferAllowed = crossBorderTransferAllowed;
    }

    if (complianceFrameworks) {
      settings.complianceFrameworks = complianceFrameworks.join(",");
    }

    if (tenantSettings) {
      await this.prisma.tenantSettings.update({
        where: { tenantId },
        data: settings as unknown as Parameters<typeof this.prisma.tenantSettings.update>[0]["data"],
      });
    } else {
      await this.prisma.tenantSettings.create({
        data: settings as unknown as Parameters<typeof this.prisma.tenantSettings.create>[0]["data"],
      });
    }

    // Invalidate cache
    this.residencyCache.delete(tenantId);
  }

  /**
   * Get data retention policy for tenant
   */
  async getDataRetentionPolicy(tenantId: string): Promise<{ retentionDays: number; deletionDate: Date | null }> {
    const config = await this.getTenantResidencyConfig(tenantId);
    const deletionDate = new Date();
    deletionDate.setDate(deletionDate.getDate() + config.dataRetentionDays);

    return {
      retentionDays: config.dataRetentionDays,
      deletionDate,
    };
  }

  /**
   * Check if data should be deleted based on retention policy
   */
  async shouldDeleteData(tenantId: string, dataCreatedAt: Date): Promise<boolean> {
    const policy = await this.getDataRetentionPolicy(tenantId);
    const now = new Date();
    const ageInDays = (now.getTime() - dataCreatedAt.getTime()) / (1000 * 60 * 60 * 24);

    return ageInDays > policy.retentionDays;
  }

  /**
   * Get compliance frameworks for tenant
   */
  async getComplianceFrameworks(tenantId: string): Promise<string[]> {
    const config = await this.getTenantResidencyConfig(tenantId);
    return config.complianceFrameworks;
  }

  /**
   * Validate compliance with specific framework
   */
  async validateCompliance(tenantId: string, framework: string): Promise<{ compliant: boolean; issues: string[] }> {
    const config = await this.getTenantResidencyConfig(tenantId);
    const issues: string[] = [];

    if (!config.complianceFrameworks.includes(framework)) {
      issues.push(`Tenant is not configured for ${framework} compliance`);
    }

    // Framework-specific validations
    if (framework === "GDPR") {
      if (config.dataRetentionDays > 365) {
        issues.push(`Data retention period (${config.dataRetentionDays} days) exceeds GDPR recommendation (365 days)`);
      }
      if (!config.crossBorderTransferAllowed && config.allowedRegions.length > 1) {
        issues.push(`Cross-border transfer is disabled but multiple regions are configured`);
      }
    }

    if (framework === "CCPA") {
      if (config.dataRetentionDays > 365) {
        issues.push(`Data retention period (${config.dataRetentionDays} days) may violate CCPA (should not exceed 365 days)`);
      }
    }

    return {
      compliant: issues.length === 0,
      issues,
    };
  }

  /**
   * Clear residency cache
   */
  clearCache(tenantId?: string): void {
    if (tenantId) {
      this.residencyCache.delete(tenantId);
    } else {
      this.residencyCache.clear();
    }
  }

  /**
   * Parse allowed regions from comma-separated string
   */
  private parseAllowedRegions(regionsStr?: string): Region[] {
    if (!regionsStr) {
      return ["US"];
    }

    const regions = regionsStr.split(",").map((r) => r.trim().toUpperCase());
    const validRegions: Region[] = ["US", "EU", "APAC", "GLOBAL"];

    return regions.filter((r) => validRegions.includes(r as Region)) as Region[];
  }

  private parsePrimaryRegion(region?: string | null): Region {
    const validRegions: Region[] = ["US", "EU", "APAC", "GLOBAL"];
    const normalizedRegion = region?.trim().toUpperCase();
    return validRegions.includes(normalizedRegion as Region) ? (normalizedRegion as Region) : "US";
  }

  /**
   * Parse compliance frameworks from comma-separated string
   */
  private parseComplianceFrameworks(frameworksStr?: string): string[] {
    if (!frameworksStr) {
      return [];
    }

    return frameworksStr.split(",").map((f) => f.trim());
  }
}
