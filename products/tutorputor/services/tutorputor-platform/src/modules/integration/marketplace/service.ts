import type { MarketplaceService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
    MarketplaceListing,
    MarketplaceListingId,
    TenantId,
    UserId,
    ModuleId,
    SimulationTemplate,
    SimulationTemplateStatus,
    SimulationTemplateDifficulty,
    SimulationTemplateLicense
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";

type TemplateSortField =
    | "popularity"
    | "rating"
    | "newest"
    | "mostUsed"
    | "alphabetical";

type TemplateSortOrder = "asc" | "desc";

interface ListTemplatesArgs {
    tenantId: TenantId;
    domains?: string[];
    difficulties?: SimulationTemplate["difficulty"][];
    tags?: string[];
    isPremium?: boolean;
    isVerified?: boolean;
    minRating?: number;
    status?: SimulationTemplateStatus;
    search?: string;
    sortBy?: TemplateSortField;
    sortOrder?: TemplateSortOrder;
    page?: number;
    pageSize?: number;
}

/**
 * Service for managing simulation templates and listings.
 * 
 * @doc.type class
 * @doc.purpose Manage the marketplace for simulation templates
 * @doc.layer product
 * @doc.pattern Service
 */
export function createMarketplaceService(
    prisma: TutorPrismaClient
): MarketplaceService & { checkHealth: () => Promise<boolean> } {
    async function listSimulationTemplatesInternal(
        args: ListTemplatesArgs
    ): Promise<{
        templates: SimulationTemplate[];
        total: number;
        page: number;
        pageSize: number;
        hasMore: boolean;
    }> {
        const {
            tenantId,
            domains,
            difficulties,
            tags,
            isPremium,
            isVerified,
            minRating,
            status,
            search,
            sortBy = "popularity",
            sortOrder = "desc",
            page = 1,
            pageSize = 12
        } = args;

        const take = Math.min(pageSize, 50);
        const currentPage = Math.max(page, 1);
        const skip = (currentPage - 1) * take;

        const where: any = { tenantId };

        if (domains && domains.length > 0) {
            where.domain = { in: domains };
        }

        if (difficulties && difficulties.length > 0) {
            where.difficulty = {
                in: difficulties
            };
        }

        if (typeof isPremium === "boolean") {
            where.isPremium = isPremium;
        }

        if (typeof isVerified === "boolean") {
            where.isVerified = isVerified;
        }

        if (typeof minRating === "number") {
            where.statsRating = { gte: minRating };
        }

        if (tags && tags.length > 0) {
            const tagFilters = tags.map((tag) => ({
                tags: { contains: `"${tag}"` }
            }));
            where.AND = [...(where.AND ?? []), ...tagFilters];
        }

        if (status) {
            where.status = status;
            if (status === "PUBLISHED") {
                where.publishedAt = { not: null };
            }
        } else {
            // Default browse behavior only returns published templates.
            where.status = "PUBLISHED";
            where.publishedAt = { not: null };
        }

        if (search && search.trim().length > 0) {
            const query = search.trim();
            where.OR = [
                { title: { contains: query, mode: "insensitive" } },
                { description: { contains: query, mode: "insensitive" } }
            ];
        }

        const orderBy = buildTemplatesOrderBy(sortBy, sortOrder);

        const total = await prisma.simulationTemplate.count({ where });
        const records = await prisma.simulationTemplate.findMany({
            where,
            orderBy,
            skip,
            take
        });

        const templates = records.map(mapDbTemplateToContract);
        const hasMore = currentPage * take < total;

        return {
            templates,
            total,
            page: currentPage,
            pageSize: take,
            hasMore
        };
    }

    return {
        async listListings({ tenantId, status, visibility, cursor, limit = 20 }) {
            const take = Math.min(limit, 50);
            const where: any = { tenantId };
            if (status) where.status = status;
            if (visibility) where.visibility = visibility;

            const records = await prisma.marketplaceListing.findMany({
                where,
                take: take + 1,
                orderBy: { updatedAt: "desc" },
                ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {})
            });

            const hasMore = records.length > take;
            const trimmed = records.slice(0, take);

            return {
                items: trimmed.map(mapListing),
                nextCursor: hasMore ? records[records.length - 1]?.id ?? null : null
            };
        },

        async createListing({ tenantId, moduleId, creatorId, priceCents, visibility }) {
            const record = await prisma.marketplaceListing.create({
                data: {
                    tenantId,
                    moduleId,
                    creatorId,
                    priceCents,
                    visibility,
                    status: "ACTIVE",
                    publishedAt: new Date()
                }
            });
            return mapListing(record);
        },

        async updateListing({ tenantId, listingId, userId, status, visibility, priceCents }) {
            const listing = await prisma.marketplaceListing.findFirst({
                where: { id: listingId, tenantId }
            });
            if (!listing) {
                throw notFoundError("Listing not found");
            }
            if (listing.creatorId !== userId) {
                throw forbiddenError("Only the creator can update the listing");
            }

            const record = await prisma.marketplaceListing.update({
                where: { id: listingId },
                data: {
                    status: status ?? listing.status,
                    visibility: visibility ?? listing.visibility,
                    priceCents: priceCents ?? listing.priceCents,
                    publishedAt: status === "ACTIVE" ? new Date() : listing.publishedAt
                }
            });
            return mapListing(record);
        },

        async listSimulationTemplates(args) {
            return listSimulationTemplatesInternal(args);
        },

        async createTemplate({ tenantId, createdBy, input }) {
            const slug = await ensureUniqueSlug(
                prisma,
                tenantId,
                (input.slug ?? slugify(input.title)).toLowerCase()
            );

            const status = input.status ?? "DRAFT";
            const now = new Date();

            const record = await prisma.simulationTemplate.create({
                data: {
                    tenantId,
                    slug,
                    title: input.title,
                    description: input.description,
                    domain: input.domain,
                    difficulty: input.difficulty,
                    tags: JSON.stringify(input.tags ?? []),
                    thumbnailUrl: input.thumbnailUrl ?? null,
                    license: input.license ?? "FREE",
                    isPremium: input.isPremium ?? false,
                    isVerified: input.isVerified ?? false,
                    version: input.version ?? "1.0.0",
                    authorId: createdBy,
                    authorName: input.authorName,
                    authorAvatarUrl: input.authorAvatarUrl,
                    organization: input.organization,
                    statsViews: 0,
                    statsUses: 0,
                    statsFavorites: 0,
                    statsRating: 0,
                    statsRatingCount: 0,
                    statsCompletionRate: 0,
                    statsAvgTimeMinutes: 0,
                    status,
                    publishedAt: status === "PUBLISHED" ? now : null,
                    moduleId: input.moduleId ?? null,
                    manifestId: input.manifestId ?? null,
                    conceptId: input.conceptId ?? null
                }
            });

            return mapDbTemplateToContract(record);
        },

        async updateTemplate({ tenantId, templateId, updatedBy, patch }) {
            const existing = await prisma.simulationTemplate.findFirst({
                where: { id: templateId, tenantId }
            });
            if (!existing) {
                throw notFoundError("Template not found");
            }

            const data: any = {
                ...(patch.title ? { title: patch.title } : {}),
                ...(patch.description ? { description: patch.description } : {}),
                ...(patch.difficulty ? { difficulty: patch.difficulty } : {}),
                ...(patch.tags ? { tags: JSON.stringify(patch.tags) } : {}),
                ...(patch.thumbnailUrl !== undefined ? { thumbnailUrl: patch.thumbnailUrl } : {}),
                ...(patch.license ? { license: patch.license } : {}),
                ...(patch.isPremium !== undefined ? { isPremium: patch.isPremium } : {}),
                ...(patch.isVerified !== undefined ? { isVerified: patch.isVerified } : {}),
                ...(patch.version ? { version: patch.version } : {}),
                ...(patch.manifestId !== undefined ? { manifestId: patch.manifestId } : {}),
                ...(patch.moduleId !== undefined ? { moduleId: patch.moduleId } : {}),
                ...(patch.conceptId !== undefined ? { conceptId: patch.conceptId } : {}),
                ...(patch.authorName !== undefined ? { authorName: patch.authorName } : {}),
                ...(patch.authorAvatarUrl !== undefined ? { authorAvatarUrl: patch.authorAvatarUrl } : {}),
                ...(patch.organization !== undefined ? { organization: patch.organization } : {})
            };

            if (patch.status && patch.status !== existing.status) {
                data.status = patch.status;
                data.publishedAt = patch.status === "PUBLISHED" ? new Date() : null;
            }

            if (Object.keys(data).length === 0) {
                return mapDbTemplateToContract(existing);
            }

            const updated = await prisma.simulationTemplate.update({
                where: { id: templateId },
                data
            });
            void updatedBy; // reserved for future audit logging within service
            return mapDbTemplateToContract(updated);
        },

        async changeTemplateStatus({ tenantId, templateId, status }) {
            const record = await prisma.simulationTemplate.update({
                where: { id: templateId, tenantId },
                data: {
                    status,
                    publishedAt: status === "PUBLISHED" ? new Date() : null
                }
            });
            return mapDbTemplateToContract(record);
        },

        async deleteTemplate({ tenantId, templateId }) {
            const existing = await prisma.simulationTemplate.findFirst({
                where: { id: templateId, tenantId }
            });
            if (!existing) {
                throw notFoundError("Template not found");
            }

            const warnings: string[] = [];
            if (existing.status === "PUBLISHED") {
                warnings.push("Deleting a published template will remove it from the marketplace.");
            }

            await prisma.simulationTemplate.delete({
                where: { id: templateId }
            });

            return { deleted: true, warnings: warnings.length ? warnings : undefined };
        },

        async adminUpdateListing({ tenantId, listingId, status, visibility }) {
            const listing = await prisma.marketplaceListing.findFirst({
                where: { id: listingId, tenantId }
            });
            if (!listing) {
                throw notFoundError("Listing not found");
            }

            const record = await prisma.marketplaceListing.update({
                where: { id: listingId },
                data: {
                    ...(status ? { status } : {}),
                    ...(visibility ? { visibility } : {}),
                    ...(status === "ACTIVE" ? { publishedAt: new Date() } : {})
                }
            });
            return mapListing(record);
        },

        async getMarketplaceStats({ tenantId }) {
            const [statusCounts, totalRevenue, topListings] = await Promise.all([
                prisma.marketplaceListing.groupBy({
                    by: ["status"],
                    where: { tenantId },
                    _count: { id: true }
                }),
                prisma.purchase.aggregate({
                    where: { tenantId },
                    _sum: { amountCents: true }
                }),
                prisma.purchase.groupBy({
                    by: ["listingId"],
                    where: { tenantId },
                    _count: { id: true },
                    orderBy: { _count: { id: "desc" } },
                    take: 10
                })
            ]);

            const stats = statusCounts.reduce((acc: Record<string, number>, s: any) => {
                acc[s.status.toLowerCase()] = s._count.id;
                return acc;
            }, {} as Record<string, number>);

            return {
                totalListings: (Object.values(stats) as number[]).reduce((a: number, b: number) => a + b, 0),
                activeListings: stats["active"] ?? 0,
                draftListings: stats["draft"] ?? 0,
                archivedListings: stats["archived"] ?? 0,
                totalRevenueCents: totalRevenue._sum.amountCents ?? 0,
                topListings: topListings.map((l: any) => ({
                    listingId: l.listingId,
                    purchaseCount: l._count.id
                }))
            };
        },

        async getTemplateStats({ tenantId }) {
            const [totalCount, verifiedCount, premiumCount, domainCounts] = await Promise.all([
                prisma.simulationTemplate.count({ where: { tenantId } }),
                prisma.simulationTemplate.count({ where: { tenantId, isVerified: true } }),
                prisma.simulationTemplate.count({ where: { tenantId, isPremium: true } }),
                prisma.simulationTemplate.groupBy({
                    by: ["domain"],
                    where: { tenantId },
                    _count: { id: true }
                })
            ]);

            return {
                totalTemplates: totalCount,
                verifiedTemplates: verifiedCount,
                premiumTemplates: premiumCount,
                byDomain: domainCounts.reduce((acc: Record<string, number>, d: any) => {
                    acc[d.domain] = d._count.id;
                    return acc;
                }, {} as Record<string, number>)
            };
        },

        async adminUpdateTemplate({ tenantId, templateId, isVerified, isPremium }) {
            const template = await prisma.simulationTemplate.update({
                where: { id: templateId, tenantId },
                data: {
                    ...(isVerified !== undefined ? { isVerified } : {}),
                    ...(isPremium !== undefined ? { isPremium } : {})
                }
            });
            return mapDbTemplateToContract(template);
        },

        async checkHealth() {
            await prisma.$queryRaw`SELECT 1`;
            return true;
        }
    };
}

function mapListing(record: any): MarketplaceListing {
    return {
        id: record.id as MarketplaceListingId,
        tenantId: record.tenantId as TenantId,
        moduleId: record.moduleId as ModuleId,
        creatorId: record.creatorId as UserId,
        status: record.status as MarketplaceListing["status"],
        visibility: record.visibility as MarketplaceListing["visibility"],
        priceCents: record.priceCents,
        createdAt: record.createdAt.toISOString(),
        updatedAt: record.updatedAt.toISOString(),
        publishedAt: record.publishedAt?.toISOString(),
        performance: undefined
    };
}

function notFoundError(message: string): Error & { code: string; statusCode: number } {
    const error = new Error(message) as Error & { code: string; statusCode: number };
    error.code = "MARKETPLACE_NOT_FOUND";
    error.statusCode = 404;
    return error;
}

function forbiddenError(message: string): Error & { code: string; statusCode: number } {
    const error = new Error(message) as Error & { code: string; statusCode: number };
    error.code = "MARKETPLACE_FORBIDDEN";
    error.statusCode = 403;
    return error;
}

function safeParseTags(raw: unknown): string[] {
    if (!raw) return [];
    try {
        const value = typeof raw === "string" ? raw : String(raw);
        const parsed = JSON.parse(value);
        return Array.isArray(parsed) ? parsed.map(String) : [];
    } catch {
        return [];
    }
}

function buildTemplatesOrderBy(sortBy: TemplateSortField, sortOrder: TemplateSortOrder): any[] {
    const direction = sortOrder === "asc" ? "asc" : "desc";

    switch (sortBy) {
        case "rating":
            return [{ statsRating: direction }, { statsRatingCount: direction }];
        case "newest":
            return [{ publishedAt: direction }];
        case "mostUsed":
            return [{ statsUses: direction }];
        case "alphabetical":
            return [{ title: direction }];
        case "popularity":
        default:
            return [{ statsUses: direction }, { statsViews: direction }];
    }
}

function mapDbTemplateToContract(record: any): SimulationTemplate {
    const tags = safeParseTags(record.tags);

    return {
        id: record.id,
        tenantId: record.tenantId,
        slug: record.slug,
        title: record.title,
        description: record.description,
        domain: record.domain,
        difficulty: record.difficulty,
        tags,
        thumbnailUrl: record.thumbnailUrl ?? undefined,
        license: record.license,
        isPremium: Boolean(record.isPremium),
        isVerified: Boolean(record.isVerified),
        version: record.version,
        authorId: record.authorId,
        authorName: record.authorName ?? undefined,
        authorAvatarUrl: record.authorAvatarUrl ?? undefined,
        organization: record.organization ?? undefined,
        stats: {
            views: record.statsViews ?? 0,
            uses: record.statsUses ?? 0,
            favorites: record.statsFavorites ?? 0,
            rating: record.statsRating ?? 0,
            ratingCount: record.statsRatingCount ?? 0,
            completionRate: record.statsCompletionRate ?? 0,
            avgTimeMinutes: record.statsAvgTimeMinutes ?? 0
        },
        status: record.status,
        publishedAt: record.publishedAt?.toISOString(),
        createdAt: record.createdAt.toISOString(),
        updatedAt: record.updatedAt.toISOString(),
        moduleId: record.moduleId ?? undefined,
        manifestId: record.manifestId ?? undefined,
        conceptId: record.conceptId ?? undefined
    };
}

async function ensureUniqueSlug(prisma: TutorPrismaClient, tenantId: TenantId, baseSlug: string) {
    let candidate = baseSlug.replace(/[^a-z0-9-]/g, "-").replace(/-{2,}/g, "-").replace(/^-|-$/g, "");
    if (!candidate) {
        candidate = "template";
    }

    let suffix = 1;
    while (true) {
        const existing = await prisma.simulationTemplate.findUnique({
            where: { tenantId_slug: { tenantId, slug: candidate } }
        });
        if (!existing) {
            return candidate;
        }
        candidate = `${baseSlug}-${suffix++}`;
    }
}

function slugify(input: string) {
    return input
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "");
}
