/**
 * @doc.type utility
 * @doc.purpose Shared Prisma helper types for TutorPutor services
 * @doc.layer platform
 * @doc.pattern Type Safety
 */

import type { Prisma } from "../../generated/prisma/index.js";

export type { Prisma };

export interface PaginationArgs {
  cursor?: string;
  take?: number;
}

export interface PaginatedResult<T> {
  items: T[];
  hasMore: boolean;
  nextCursor?: string;
}

export type ModuleWhereInput = Prisma.ModuleWhereInput;
export type ThreadWhereInput = Prisma.ThreadWhereInput;
export type UserWhereInput = Prisma.UserWhereInput;
export type EnrollmentWhereInput = Prisma.EnrollmentWhereInput;

export function createTenantWhere<T extends object>(
  base: T,
  tenantId: string,
): T & { tenantId: string } {
  return { ...base, tenantId };
}
