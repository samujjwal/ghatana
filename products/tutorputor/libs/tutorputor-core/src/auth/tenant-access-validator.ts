/**
 * @doc.type service
 * @doc.purpose Shared tenant access validation helpers for product services
 * @doc.layer platform
 * @doc.pattern Access Control
 */

import type { PrismaClient } from "../../generated/prisma/index.js";
import { ForbiddenError, NotFoundError } from "../errors/index.js";

export interface AccessContext {
  tenantId: string;
  userId?: string;
  roles?: string[];
}

export class TenantAccessValidator {
  constructor(private readonly prisma: PrismaClient) {}

  async validateEntityAccess<T extends { id: string; tenantId: string; userId?: string | null }>(
    entityName: string,
    findFirst: (args: { where: Record<string, unknown> }) => Promise<T | null>,
    entityId: string,
    context: AccessContext,
  ): Promise<T> {
    const where: Record<string, unknown> = {
      id: entityId,
      tenantId: context.tenantId,
    };

    if (context.userId) {
      where.userId = context.userId;
    }

    const record = await findFirst({ where });
    if (!record) {
      throw new NotFoundError(entityName, entityId);
    }

    if (record.tenantId !== context.tenantId) {
      throw new ForbiddenError("Entity does not belong to tenant");
    }

    return record;
  }

  async validateTenant(tenantId: string): Promise<void> {
    const tenant = await this.prisma.tenant.findUnique({
      where: { id: tenantId },
      select: {
        id: true,
        subscriptionTier: true,
      },
    });

    if (!tenant) {
      throw new NotFoundError("Tenant", tenantId);
    }
  }

  validateRole(context: AccessContext, requiredRoles: string[]): void {
    if (!context.roles || context.roles.length === 0) {
      throw new ForbiddenError("Roles not provided");
    }

    const hasRole = requiredRoles.some((role) => context.roles?.includes(role));
    if (!hasRole) {
      throw new ForbiddenError(`Requires one of: ${requiredRoles.join(", ")}`);
    }
  }
}
