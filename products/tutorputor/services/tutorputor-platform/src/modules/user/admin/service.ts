/**
 * @doc.type module
 * @doc.purpose Institution admin service
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'InstitutionAdminService' });
import type {
    InstitutionAdminService,
    TenantId,
    UserId,
    TenantSummary,
    UsageMetrics,
    UserSummary,
    PaginatedResult,
    ModuleId,
} from '@tutorputor/contracts';

interface ServiceConfig {
    defaultPageSize: number;
    maxPageSize: number;
}

interface CursorPagination {
    cursor?: string;
    limit?: number;
    sortBy?: string;
    sortOrder?: 'asc' | 'desc';
}

interface DateRangeInput {
    start: string;
    end: string;
}

const DEFAULT_CONFIG: ServiceConfig = {
    defaultPageSize: 50,
    maxPageSize: 200,
};

function parsePagination(input: unknown): CursorPagination {
    if (!input || typeof input !== 'object') {
        return {};
    }

    const value = input as Record<string, unknown>;
    const pagination: CursorPagination = {};
    if (typeof value.cursor === 'string') {
        pagination.cursor = value.cursor;
    }
    if (typeof value.limit === 'number') {
        pagination.limit = value.limit;
    }
    if (typeof value.sortBy === 'string') {
        pagination.sortBy = value.sortBy;
    }
    if (value.sortOrder === 'asc' || value.sortOrder === 'desc') {
        pagination.sortOrder = value.sortOrder;
    }

    return pagination;
}

function parseDateRange(input: unknown): DateRangeInput {
    if (!input || typeof input !== 'object') {
        throw new Error('Invalid date range');
    }

    const value = input as Record<string, unknown>;
    if (typeof value.start !== 'string' || typeof value.end !== 'string') {
        throw new Error('Invalid date range');
    }

    return {
        start: value.start,
        end: value.end,
    };
}

export class InstitutionAdminServiceImpl implements InstitutionAdminService {
    private readonly config: ServiceConfig;

    constructor(
        private readonly prisma: PrismaClient,
        config: Partial<ServiceConfig> = {}
    ) {
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    async getTenantSummary(args: { tenantId: TenantId }): Promise<TenantSummary> {
        const { tenantId } = args;

        const [tenant, userCounts, moduleCounts, classroomCount, activeUserCount] =
            await Promise.all([
                this.prisma.tenant.findUnique({
                    where: { id: tenantId },
                }),
                this.prisma.user.count({
                    where: { tenantId },
                }),
                this.prisma.module.count({
                    where: { tenantId, status: 'PUBLISHED' },
                }),
                this.prisma.classroom.count({
                    where: { tenantId },
                }),
                // Active users = users with events in last 30 days
                this.prisma.learningEvent.groupBy({
                    by: ['userId'],
                    where: {
                        tenantId,
                        timestamp: {
                            gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
                        },
                    },
                }),
            ]);

        if (!tenant) {
            throw new Error(`Tenant not found: ${tenantId}`);
        }

        return {
            tenantId: tenant.id as TenantId,
            name: tenant.name,
            totalUsers: userCounts,
            activeUsers: activeUserCount.length,
            totalModules: moduleCounts,
            totalClassrooms: classroomCount,
            createdAt: tenant.createdAt.toISOString(),
            subscriptionTier: (tenant as any).subscriptionTier ?? 'free',
        };
    }

    async listTenantUsers(args: {
        tenantId: TenantId;
        role?: string;
        searchQuery?: string;
        pagination: unknown;
    }): Promise<PaginatedResult<UserSummary>> {
        const { tenantId, role, searchQuery, pagination } = args;
        const parsedPagination = parsePagination(pagination);
        const {
            cursor,
            limit = this.config.defaultPageSize,
            sortBy,
            sortOrder,
        } = parsedPagination;
        const take = Math.min(limit, this.config.maxPageSize);

        const where: Record<string, unknown> = { tenantId };

        if (role) {
            where.role = role;
        }

        if (searchQuery) {
            where.OR = [
                { email: { contains: searchQuery } }, // removed mode: 'insensitive' as sqlite might not support it, or rely on provider default
                { displayName: { contains: searchQuery } },
            ];
        }

        const orderBy: Record<string, unknown> = {};
        if (sortBy) {
            orderBy[sortBy] = sortOrder ?? 'asc';
        } else {
            orderBy.displayName = 'asc';
        }

        const queryArgs: Record<string, unknown> = {
            where,
            take: take + 1,
            skip: cursor ? 1 : 0,
            orderBy,
            select: {
                id: true,
                email: true,
                displayName: true,
                role: true,
            },
        };
        if (cursor) {
            queryArgs.cursor = { id: cursor };
        }

        const [users, totalCount] = await Promise.all([
            this.prisma.user.findMany(queryArgs as any),
            this.prisma.user.count({ where }),
        ]);

        const hasMore = users.length > take;
        const items = hasMore ? users.slice(0, -1) : users;
        const nextCursor = hasMore ? items[items.length - 1]?.id : undefined;

        const result: PaginatedResult<UserSummary> = {
            items: items.map((u) => ({
                id: u.id as UserId,
                email: u.email,
                displayName: u.displayName,
                role: u.role as UserSummary['role'],
            })),
            totalCount,
            hasMore,
        };
        if (nextCursor) {
            result.nextCursor = nextCursor;
        }

        return result;
    }

    async getTenantUsage(args: {
        tenantId: TenantId;
        dateRange: unknown;
    }): Promise<UsageMetrics> {
        const { tenantId, dateRange } = args;
        const parsedDateRange = parseDateRange(dateRange);
        const startDate = new Date(parsedDateRange.start);
        const endDate = new Date(parsedDateRange.end);

        const [
            dailyActiveUsers,
            weeklyActiveUsers,
            monthlyActiveUsers,
            totalEvents,
            assessmentAttempts,
            topModules,
            avgSessionDuration,
            completionStats,
        ] = await Promise.all([
            this.prisma.learningEvent.groupBy({
                by: ['userId'],
                where: {
                    tenantId,
                    timestamp: {
                        gte: new Date(endDate.getTime() - 24 * 60 * 60 * 1000),
                        lte: endDate,
                    },
                },
            }),
            this.prisma.learningEvent.groupBy({
                by: ['userId'],
                where: {
                    tenantId,
                    timestamp: {
                        gte: new Date(endDate.getTime() - 7 * 24 * 60 * 60 * 1000),
                        lte: endDate,
                    },
                },
            }),
            this.prisma.learningEvent.groupBy({
                by: ['userId'],
                where: {
                    tenantId,
                    timestamp: {
                        gte: new Date(endDate.getTime() - 30 * 24 * 60 * 60 * 1000),
                        lte: endDate,
                    },
                },
            }),
            this.prisma.learningEvent.count({
                where: {
                    tenantId,
                    timestamp: { gte: startDate, lte: endDate },
                },
            }),
            this.prisma.assessmentAttempt.count({
                where: {
                    tenantId,
                    startedAt: { gte: startDate, lte: endDate },
                },
            }),
            this.prisma.enrollment.groupBy({
                by: ['moduleId'],
                where: {
                    tenantId,
                    startedAt: { gte: startDate, lte: endDate },
                },
                _count: { moduleId: true },
                orderBy: { _count: { moduleId: 'desc' } },
                take: 10,
            }),
            this.prisma.enrollment.aggregate({
                where: {
                    tenantId,
                    startedAt: { gte: startDate, lte: endDate },
                },
                _avg: { timeSpentSeconds: true },
            }),
            this.prisma.enrollment.groupBy({
                by: ['status'],
                where: {
                    tenantId,
                    startedAt: { gte: startDate, lte: endDate },
                },
                _count: { status: true },
            }),
        ]);

        const moduleIds = topModules.map((m) => m.moduleId).filter(Boolean);
        const modules =
            moduleIds.length > 0
                ? await this.prisma.module.findMany({
                    where: { id: { in: moduleIds as string[] } },
                    select: { id: true, title: true },
                })
                : [];

        const moduleMap = new Map(modules.map((m) => [m.id, m.title]));

        const totalEnrollments = completionStats.reduce(
            (sum: number, s) => sum + s._count.status,
            0
        );
        const completedEnrollments =
            completionStats.find((s) => s.status === 'COMPLETED')?._count.status ?? 0;
        const completionRate =
            totalEnrollments > 0
                ? (completedEnrollments / totalEnrollments) * 100
                : 0;

        return {
            tenantId: tenantId as TenantId,
            dateRange: {
                start: parsedDateRange.start,
                end: parsedDateRange.end,
            },
            dailyActiveUsers: dailyActiveUsers.length,
            weeklyActiveUsers: weeklyActiveUsers.length,
            monthlyActiveUsers: monthlyActiveUsers.length,
            totalLearningEvents: totalEvents,
            totalAssessmentAttempts: assessmentAttempts,
            averageSessionDurationMinutes: Math.round(
                (avgSessionDuration._avg.timeSpentSeconds ?? 0) / 60
            ),
            moduleCompletionRate: Math.round(completionRate * 10) / 10,
            topModules: topModules
                .filter((m) => m.moduleId)
                .map((m) => ({
                    moduleId: m.moduleId as ModuleId,
                    title: moduleMap.get(m.moduleId as string) ?? 'Unknown Module',
                    enrollments: m._count.moduleId,
                })),
        };
    }

    async bulkImportUsers(args: {
        tenantId: TenantId;
        importedBy: UserId;
        users: Array<{ email: string; role: string; displayName?: string; classroomIds?: string[] }>;
        sendInvites?: boolean;
    }): Promise<{
        imported: number;
        failed: number;
        errors: Array<{ email: string; reason: string }>;
    }> {
        const { tenantId, users, sendInvites } = args;
        const results = {
            imported: 0,
            failed: 0,
            errors: [] as Array<{ email: string; reason: string }>,
        };

        for (const userData of users) {
            try {
                const existing = await this.prisma.user.findFirst({
                    where: {
                        tenantId,
                        email: userData.email,
                    },
                });

                if (existing) {
                    results.failed++;
                    results.errors.push({
                        email: userData.email,
                        reason: 'User already exists',
                    });
                    continue;
                }

                const validRoles = ['student', 'teacher', 'creator', 'admin'];
                if (!validRoles.includes(userData.role)) {
                    results.failed++;
                    results.errors.push({
                        email: userData.email,
                        reason: `Invalid role: ${userData.role}`,
                    });
                    continue;
                }

                const newUser = await this.prisma.user.create({
                    data: {
                        tenantId,
                        email: userData.email,
                        displayName: userData.displayName,
                        role: userData.role,
                    },
                });

                if (userData.classroomIds && userData.classroomIds.length > 0) {
                    for (const classroomId of userData.classroomIds) {
                        try {
                            // Note: Using ClassroomMember as typical for admin actions, 
                            // though Teacher service uses ClassroomStudent. Discrepancy noted.
                            await this.prisma.classroomMember.create({
                                data: {
                                    classroomId,
                                    userId: newUser.id,
                                    role: userData.role === 'teacher' ? 'teacher' : 'student',
                                },
                            });
                        } catch (e) {
                            logger.warn({
                                message: 'Failed to add user to classroom',
                                email: userData.email,
                                classroomId,
                                error: e instanceof Error ? e.message : String(e),
                            });
                        }
                    }
                }

                results.imported++;

                if (sendInvites) {
                    // Send invite logic
                }
            } catch (error) {
                results.failed++;
                results.errors.push({
                    email: userData.email,
                    reason: error instanceof Error ? error.message : 'Unknown error',
                });
            }
        }

        return results;
    }

    async assignPathToClassroom(args: {
        tenantId: TenantId;
        classroomId: string;
        pathwayId: string;
        assignedBy: UserId;
    }): Promise<{ assignedCount: number }> {
        const { tenantId, classroomId, pathwayId } = args;
        const members = await this.prisma.classroomMember.findMany({
            where: {
                classroomId,
                role: 'student',
            },
            select: { userId: true },
        });

        let assignedCount = 0;

        for (const member of members) {
            try {
                const existing = await this.prisma.learningPathEnrollment.findFirst({
                    where: {
                        pathId: pathwayId,
                        userId: member.userId,
                    },
                });

                if (!existing) {
                    await this.prisma.learningPathEnrollment.create({
                        data: {
                            pathId: pathwayId,
                            userId: member.userId,
                            tenantId,
                            currentNodeIndex: 0,
                        },
                    });
                    assignedCount++;
                }
            } catch (e) {
                logger.warn({
                    message: 'Failed to assign pathway to user',
                    userId: member.userId,
                    pathwayId,
                    error: e instanceof Error ? e.message : String(e),
                });
            }
        }

        return { assignedCount };
    }

    async updateUserRole(args: {
        tenantId: TenantId;
        userId: UserId;
        newRole: string;
        updatedBy: UserId;
    }): Promise<UserSummary> {
        const { tenantId, userId, newRole } = args;
        const user = await this.prisma.user.findUnique({
            where: { id: userId },
        });

        if (!user || user.tenantId !== tenantId) {
            throw new Error(
                `User not found or does not belong to tenant: ${userId}`
            );
        }

        const validRoles = ['student', 'teacher', 'creator', 'admin'];
        if (!validRoles.includes(newRole)) {
            throw new Error(`Invalid role: ${newRole}`);
        }

        const updatedUser = await this.prisma.user.update({
            where: { id: userId },
            data: { role: newRole },
            select: {
                id: true,
                email: true,
                displayName: true,
                role: true,
            },
        });

        return {
            id: updatedUser.id as UserId,
            email: updatedUser.email,
            displayName: updatedUser.displayName,
            role: updatedUser.role as UserSummary['role'],
        };
    }

    async checkHealth() {
        await this.prisma.$queryRaw`SELECT 1`;
        return true;
    }
}
