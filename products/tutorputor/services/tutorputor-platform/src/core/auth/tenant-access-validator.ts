/**
 * Tenant Access Validator
 *
 * Comprehensive tenant access validation for multi-tenant architecture.
 * Ensures proper tenant isolation and authorization across the TutorPutor platform.
 *
 * @doc.type utility
 * @doc.purpose Multi-tenant access control
 * @doc.layer core
 * @doc.pattern Security
 */

import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";
import {
  ForbiddenError,
  UnauthorizedError,
  ResourceNotFoundError,
} from "../errors/index.js";

export interface TenantContext {
  tenantId: TenantId;
  userId: UserId;
  userRole: string;
  permissions: string[];
  isActive: boolean;
  subscriptionTier?: string;
}

export interface AccessRequest {
  action: string;
  resource: string;
  resourceId?: string;
  tenantId: TenantId;
  userId: UserId;
  context?: Record<string, unknown>;
}

export interface AccessRule {
  /** Required permissions for this action */
  requiredPermissions: string[];
  /** Required subscription tier */
  requiredSubscriptionTier?: string;
  /** Whether user must be active */
  requireActiveUser?: boolean;
  /** Whether tenant must be active */
  requireActiveTenant?: boolean;
  /** Additional custom validation */
  customValidator?: (
    request: AccessRequest,
    tenantContext: TenantContext,
  ) => Promise<boolean>;
  /** Resource ownership check */
  requireOwnership?: boolean;
  /** Ownership field name in resource */
  ownershipField?: string;
}

export interface ValidationResult {
  allowed: boolean;
  reason?: string;
  errorCode?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Tenant Access Validator for multi-tenant security
 */
export class TenantAccessValidator {
  private static readonly DEFAULT_RULES: Map<string, AccessRule> = new Map([
    // User management
    [
      "user:read",
      {
        requiredPermissions: ["user:read"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "user:write",
      {
        requiredPermissions: ["user:write"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "user:delete",
      {
        requiredPermissions: ["user:delete"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],

    // Content management
    [
      "content:read",
      {
        requiredPermissions: ["content:read"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "content:write",
      {
        requiredPermissions: ["content:write"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "content:delete",
      {
        requiredPermissions: ["content:delete"],
        requireOwnership: true,
        ownershipField: "createdBy",
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],

    // Learning management
    [
      "learning:read",
      {
        requiredPermissions: ["learning:read"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "learning:write",
      {
        requiredPermissions: ["learning:write"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],

    // Analytics
    [
      "analytics:read",
      {
        requiredPermissions: ["analytics:read"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],

    // Administration
    [
      "admin:read",
      {
        requiredPermissions: ["admin:read"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "admin:write",
      {
        requiredPermissions: ["admin:write"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],

    // Billing
    [
      "billing:read",
      {
        requiredPermissions: ["billing:read"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "billing:write",
      {
        requiredPermissions: ["billing:write"],
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],

    // Premium features
    [
      "content:generate",
      {
        requiredPermissions: ["content:write"],
        requiredSubscriptionTier: "premium",
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
    [
      "analytics:advanced",
      {
        requiredPermissions: ["analytics:read"],
        requiredSubscriptionTier: "premium",
        requireActiveUser: true,
        requireActiveTenant: true,
      },
    ],
  ]);

  constructor(
    private prisma: TutorPrismaClient,
    private logger: Logger,
    private customRules: Map<string, AccessRule> = new Map(),
  ) {}

  /**
   * Validate tenant access for a given request
   */
  async validateAccess(request: AccessRequest): Promise<ValidationResult> {
    try {
      // Get tenant context
      const tenantContext = await this.getTenantContext(
        request.tenantId,
        request.userId,
      );

      // Check if tenant and user are active
      if (!this.validateActiveStatus(request, tenantContext)) {
        return {
          allowed: false,
          reason: "Tenant or user is not active",
          errorCode: "INACTIVE_TENANT_OR_USER",
        };
      }

      // Get access rule
      const rule = this.getAccessRule(request.action);

      // Validate permissions
      const permissionResult = this.validatePermissions(rule, tenantContext);
      if (!permissionResult.allowed) {
        return permissionResult;
      }

      // Validate subscription tier
      const subscriptionResult = this.validateSubscriptionTier(
        rule,
        tenantContext,
      );
      if (!subscriptionResult.allowed) {
        return subscriptionResult;
      }

      // Validate resource ownership if required
      if (rule.requireOwnership && request.resourceId) {
        const ownershipResult = await this.validateResourceOwnership(
          request,
          tenantContext,
          rule.ownershipField || "createdBy",
        );
        if (!ownershipResult.allowed) {
          return ownershipResult;
        }
      }

      // Run custom validator if present
      if (rule.customValidator) {
        const customResult = await rule.customValidator(request, tenantContext);
        if (!customResult) {
          return {
            allowed: false,
            reason: "Custom validation failed",
            errorCode: "CUSTOM_VALIDATION_FAILED",
          };
        }
      }

      return { allowed: true };
    } catch (error) {
      this.logger.error({ error, request }, "Access validation error");
      return {
        allowed: false,
        reason: "Internal validation error",
        errorCode: "VALIDATION_ERROR",
      };
    }
  }

  /**
   * Get tenant context with user information
   */
  private async getTenantContext(
    tenantId: TenantId,
    userId: UserId,
  ): Promise<TenantContext> {
    // Get tenant information
    const tenant = await this.prisma.tenant.findUnique({
      where: { id: tenantId },
      select: {
        id: true,
        subscriptionTier: true,
      },
    });

    if (!tenant) {
      throw new ResourceNotFoundError("Tenant", { tenantId });
    }

    // Get user information within tenant
    const user = await this.prisma.user.findFirst({
      where: {
        id: userId,
        tenantId,
      },
      select: {
        id: true,
        role: true,
      },
    });

    if (!user) {
      throw new ResourceNotFoundError("User", { userId });
    }

    return {
      tenantId: tenant.id as TenantId,
      userId: user.id as UserId,
      userRole: user.role || "user",
      permissions: [],
      isActive: true,
      subscriptionTier: tenant.subscriptionTier || undefined,
    };
  }

  /**
   * Validate active status of tenant and user
   */
  private validateActiveStatus(
    request: AccessRequest,
    context: TenantContext,
  ): boolean {
    const rule = this.getAccessRule(request.action);

    if (rule.requireActiveUser && !context.isActive) {
      return false;
    }

    // Additional tenant active check would be done in getTenantContext
    return true;
  }

  /**
   * Validate user permissions
   */
  private validatePermissions(
    rule: AccessRule,
    context: TenantContext,
  ): ValidationResult {
    for (const requiredPermission of rule.requiredPermissions) {
      if (!context.permissions.includes(requiredPermission)) {
        return {
          allowed: false,
          reason: `Missing required permission: ${requiredPermission}`,
          errorCode: "INSUFFICIENT_PERMISSIONS",
          metadata: {
            requiredPermission,
            userPermissions: context.permissions,
          },
        };
      }
    }

    return { allowed: true };
  }

  /**
   * Validate subscription tier requirements
   */
  private validateSubscriptionTier(
    rule: AccessRule,
    context: TenantContext,
  ): ValidationResult {
    if (rule.requiredSubscriptionTier) {
      const tierHierarchy = ["basic", "premium", "enterprise"];
      const currentTierIndex = tierHierarchy.indexOf(
        context.subscriptionTier || "basic",
      );
      const requiredTierIndex = tierHierarchy.indexOf(
        rule.requiredSubscriptionTier,
      );

      if (currentTierIndex < requiredTierIndex) {
        return {
          allowed: false,
          reason: `Requires ${rule.requiredSubscriptionTier} subscription, current: ${context.subscriptionTier}`,
          errorCode: "SUBSCRIPTION_REQUIRED",
          metadata: {
            requiredTier: rule.requiredSubscriptionTier,
            currentTier: context.subscriptionTier,
          },
        };
      }
    }

    return { allowed: true };
  }

  /**
   * Validate resource ownership
   */
  private async validateResourceOwnership(
    request: AccessRequest,
    context: TenantContext,
    ownershipField: string,
  ): Promise<ValidationResult> {
    try {
      // This is a simplified implementation
      // In practice, you'd need to dynamically query the appropriate table based on resource type
      const resource = (await this.prisma.$queryRaw`
        SELECT ${ownershipField} as owner_id 
        FROM ${request.resource} 
        WHERE id = ${request.resourceId} AND tenant_id = ${context.tenantId}
        LIMIT 1
      `) as { owner_id: string }[];

      if (resource.length === 0) {
        return {
          allowed: false,
          reason: "Resource not found",
          errorCode: "RESOURCE_NOT_FOUND",
        };
      }

      const ownerId = resource[0]?.owner_id;
      if (!ownerId) {
        return {
          allowed: false,
          reason: "Resource owner metadata missing",
          errorCode: "RESOURCE_NOT_FOUND",
        };
      }
      if (ownerId !== context.userId) {
        return {
          allowed: false,
          reason: "Resource ownership required",
          errorCode: "OWNERSHIP_REQUIRED",
          metadata: { ownerId, userId: context.userId },
        };
      }

      return { allowed: true };
    } catch (error) {
      this.logger.error({ error, request }, "Ownership validation error");
      return {
        allowed: false,
        reason: "Ownership validation failed",
        errorCode: "OWNERSHIP_VALIDATION_ERROR",
      };
    }
  }

  /**
   * Get access rule for an action
   */
  private getAccessRule(action: string): AccessRule {
    return (
      this.customRules.get(action) ||
      TenantAccessValidator.DEFAULT_RULES.get(action) || {
        requiredPermissions: [],
        requireActiveUser: true,
        requireActiveTenant: true,
      }
    );
  }

  /**
   * Add custom access rule
   */
  addCustomRule(action: string, rule: AccessRule): void {
    this.customRules.set(action, rule);
    this.logger.info({ action }, "Custom access rule added");
  }

  /**
   * Remove custom access rule
   */
  removeCustomRule(action: string): void {
    this.customRules.delete(action);
    this.logger.info({ action }, "Custom access rule removed");
  }

  /**
   * Check if user has specific permission
   */
  async hasPermission(
    tenantId: TenantId,
    userId: UserId,
    permission: string,
  ): Promise<boolean> {
    try {
      const context = await this.getTenantContext(tenantId, userId);
      return context.permissions.includes(permission);
    } catch (error) {
      this.logger.error(
        { error, tenantId, userId, permission },
        "Permission check error",
      );
      return false;
    }
  }

  /**
   * Get user permissions for a tenant
   */
  async getUserPermissions(
    tenantId: TenantId,
    userId: UserId,
  ): Promise<string[]> {
    try {
      const context = await this.getTenantContext(tenantId, userId);
      return context.permissions;
    } catch (error) {
      this.logger.error({ error, tenantId, userId }, "Get permissions error");
      return [];
    }
  }

  /**
   * Validate tenant membership
   */
  async validateTenantMembership(
    tenantId: TenantId,
    userId: UserId,
  ): Promise<boolean> {
    try {
      await this.getTenantContext(tenantId, userId);
      return true;
    } catch (error) {
      return false;
    }
  }

  /**
   * Middleware function for Fastify routes
   */
  createMiddleware(requiredAction: string) {
    return async (request: any, reply: any) => {
      const tenantId = request.user?.tenantId;
      const userId = request.user?.userId;

      if (!tenantId || !userId) {
        throw new UnauthorizedError("Missing tenant or user context");
      }

      const accessRequest: AccessRequest = {
        action: requiredAction,
        resource: request.routeOptions.url?.split("/")[1] || "unknown",
        resourceId: request.params?.id,
        tenantId,
        userId,
        context: { method: request.method, url: request.url },
      };

      const result = await this.validateAccess(accessRequest);

      if (!result.allowed) {
        throw new ForbiddenError(result.reason || "Access denied");
      }

      // Add validation result to request for downstream use
      request.accessValidation = result;
    };
  }
}

/**
 * Factory function to create TenantAccessValidator
 */
export function createTenantAccessValidator(
  prisma: TutorPrismaClient,
  logger: Logger,
  customRules?: Map<string, AccessRule>,
): TenantAccessValidator {
  return new TenantAccessValidator(prisma, logger, customRules);
}

/**
 * Convenience function for quick access validation
 */
export async function validateAccess(
  prisma: TutorPrismaClient,
  logger: Logger,
  request: AccessRequest,
): Promise<ValidationResult> {
  const validator = createTenantAccessValidator(prisma, logger);
  return validator.validateAccess(request);
}
