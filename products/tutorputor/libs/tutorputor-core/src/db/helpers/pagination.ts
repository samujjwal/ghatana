/**
 * @doc.type utility
 * @doc.purpose Type-safe cursor pagination helper for Prisma-backed models
 * @doc.layer platform
 * @doc.pattern Database Helper
 */

import type { Prisma } from "../../../generated/prisma/index.js";

export interface PaginationArgs {
  cursor?: string;
  take?: number;
  skip?: number;
}

export interface PaginatedResult<T> {
  items: T[];
  hasMore: boolean;
  nextCursor?: string;
  totalCount?: number;
}

export interface PaginationOptions<T extends { id: string }> {
  orderBy?: Prisma.SortOrder;
  orderField?: Extract<keyof T, string>;
  includeTotalCount?: boolean;
}

interface PaginationModel<T extends { id: string }, TWhere> {
  findMany: (args: {
    where: TWhere;
    take: number;
    skip?: number;
    cursor?: { id: string };
    orderBy: Record<string, Prisma.SortOrder>;
  }) => Promise<T[]>;
  count?: (args: { where: TWhere }) => Promise<number>;
}

export async function paginate<T extends { id: string }, TWhere>(
  model: PaginationModel<T, TWhere>,
  where: TWhere,
  args: PaginationArgs,
  options: PaginationOptions<T> = {},
): Promise<PaginatedResult<T>> {
  const take = Math.max(1, Math.min(args.take ?? 20, 100));
  const requested = take + 1;
  const orderField = options.orderField ?? ("id" as Extract<keyof T, string>);
  const orderBy = { [orderField]: options.orderBy ?? "desc" };

  const [items, totalCount] = await Promise.all([
    model.findMany({
      where,
      take: requested,
      orderBy,
      ...(args.cursor ? { cursor: { id: args.cursor }, skip: args.skip ?? 1 } : {}),
    }),
    options.includeTotalCount && model.count
      ? model.count({ where })
      : Promise.resolve(undefined),
  ]);

  const hasMore = items.length === requested;
  const trimmed = hasMore ? items.slice(0, -1) : items;

  return {
    items: trimmed,
    hasMore,
    ...(hasMore && trimmed.at(-1)?.id ? { nextCursor: trimmed.at(-1)!.id } : {}),
    ...(totalCount !== undefined ? { totalCount } : {}),
  };
}
