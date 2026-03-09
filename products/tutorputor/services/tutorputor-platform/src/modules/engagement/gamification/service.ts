import { type BadgeCategory } from "@ghatana/tutorputor-contracts/v1/types";
import type { PrismaClient } from "@ghatana/tutorputor-db";
import type {
    TenantId,
    UserId,
    ModuleId,
    Badge,
    BadgeId,
    Achievement
} from "@ghatana/tutorputor-contracts/v1/types";

/**
 * Badge creation input.
 */
export interface CreateBadgeInput {
    tenantId: TenantId;
    name: string;
    description: string;
    icon: string;
    criteria: BadgeCriteria;
}

/**
 * Criteria for earning a badge.
 */
export interface BadgeCriteria {
    type: "modules_completed" | "streak_days" | "points_earned" | "assessment_score" | "custom";
    threshold: number;
    customRule?: string;
}

/**
 * Achievement creation input.
 */
export interface AwardBadgeInput {
    tenantId: TenantId;
    userId: UserId;
    badgeId: BadgeId;
    reason?: string;
}

/**
 * Leaderboard entry.
 */
export interface LeaderboardEntry {
    rank: number;
    userId: UserId;
    displayName: string;
    points: number;
    badges: number;
    streak: number;
}

/**
 * Leaderboard query options.
 */
export interface LeaderboardOptions {
    tenantId: TenantId;
    period?: "daily" | "weekly" | "monthly" | "allTime";
    moduleId?: ModuleId;
    limit?: number;
    offset?: number;
}

/**
 * User progress for gamification.
 */
export interface UserGamificationProgress {
    userId: UserId;
    totalPoints: number;
    currentStreak: number;
    longestStreak: number;
    badges: Achievement[];
    level: number;
    xpToNextLevel: number;
}

/**
 * Points award input.
 */
export interface AwardPointsInput {
    tenantId: TenantId;
    userId: UserId;
    points: number;
    reason: string;
    sourceType: "module_complete" | "assessment" | "streak" | "daily_login" | "bonus";
    sourceId?: string;
}

export class GamificationService {
    private prisma: PrismaClient;
    // Level XP thresholds
    private readonly LEVEL_XP = [0, 100, 250, 500, 1000, 2000, 4000, 8000, 16000, 32000];

    constructor(prisma: PrismaClient) {
        this.prisma = prisma;
    }

    private calculateLevel(points: number): { level: number; xpToNextLevel: number } {
        let level = 1;
        for (let i = 0; i < this.LEVEL_XP.length; i++) {
            if (points >= this.LEVEL_XP[i]!) {
                level = i + 1;
            } else {
                break;
            }
        }
        // @ts-ignore
        const nextLevelXp = this.LEVEL_XP[level] ?? this.LEVEL_XP[this.LEVEL_XP.length - 1]! * 2;
        return { level, xpToNextLevel: nextLevelXp - points };
    }

    // Badge management
    async createBadge(input: CreateBadgeInput): Promise<Badge> {
        const badge = await this.prisma.badge.create({
            data: {
                tenantId: input.tenantId,
                name: input.name,
                description: input.description,
                icon: input.icon,
                criteria: JSON.stringify(input.criteria)
            }
        });

        return {
            id: badge.id as BadgeId,
            name: badge.name,
            description: badge.description,
            icon: badge.icon,
            category: "LEARNING",
            criteria: JSON.parse(badge.criteria)
        } as unknown as Badge;
    }

    async getBadge(tenantId: TenantId, badgeId: BadgeId): Promise<Badge | null> {
        const badge = await this.prisma.badge.findFirst({
            where: { id: badgeId, tenantId }
        });

        if (!badge) return null;

        return {
            id: badge.id as BadgeId,
            name: badge.name,
            description: badge.description,
            icon: badge.icon,
            category: "LEARNING", // Default or stored in DB?
            criteria: JSON.parse(badge.criteria)
        } as unknown as Badge;
    }

    async listBadges(tenantId: TenantId): Promise<Badge[]> {
        const badges = await this.prisma.badge.findMany({
            where: { tenantId },
            orderBy: { name: "asc" }
        });

        return badges.map((badge: any) => ({
            id: badge.id as BadgeId,
            name: badge.name,
            description: badge.description,
            icon: badge.icon,
            category: "LEARNING",
            criteria: JSON.parse(badge.criteria)
        })) as unknown as Badge[];
    }

    async updateBadge(
        tenantId: TenantId,
        badgeId: BadgeId,
        updates: Partial<CreateBadgeInput>
    ): Promise<Badge> {
        const badge = await this.prisma.badge.update({
            where: { id: badgeId },
            data: {
                ...(updates.name && { name: updates.name }),
                ...(updates.description && { description: updates.description }),
                ...(updates.icon && { icon: updates.icon }),
                ...(updates.criteria && { criteria: JSON.stringify(updates.criteria) })
            }
        });

        return {
            id: badge.id as BadgeId,
            name: badge.name,
            description: badge.description,
            icon: badge.icon,
            category: "LEARNING",
            criteria: JSON.parse(badge.criteria)
        } as unknown as Badge;
    }

    async deleteBadge(tenantId: TenantId, badgeId: BadgeId): Promise<void> {
        await this.prisma.badge.delete({
            where: { id: badgeId }
        });
    }

    // Achievement awarding
    async awardBadge(input: AwardBadgeInput): Promise<Achievement> {
        // Check if already earned
        const existing = await this.prisma.badgeEarned.findFirst({
            where: {
                tenantId: input.tenantId,
                userId: input.userId,
                badgeId: input.badgeId
            }
        });

        if (existing) {
            const badge = await this.prisma.badge.findUnique({
                where: { id: input.badgeId }
            });
            return {
                id: existing.id,
                userId: existing.userId as UserId,
                badgeId: existing.badgeId as BadgeId,
                tenantId: existing.tenantId as TenantId,
                badge: {
                    id: badge!.id as BadgeId,
                    name: badge!.name,
                    description: badge!.description,
                    // icon: badge!.icon,
                    criteria: JSON.parse(badge!.criteria),
                    tenantId: input.tenantId as TenantId,
                    category: "general" as BadgeCategory,
                    points: 10
                },
                earnedAt: existing.earnedAt
            };
        }

        const earned = await this.prisma.badgeEarned.create({
            data: {
                tenantId: input.tenantId,
                userId: input.userId,
                badgeId: input.badgeId
            },
            include: { badge: true }
        });

        return {
            id: earned.id,
            userId: earned.userId as UserId,
            badgeId: earned.badgeId as BadgeId,
            tenantId: earned.tenantId as TenantId,
            badge: {
                id: earned.badge.id as BadgeId,
                name: earned.badge.name,
                description: earned.badge.description,
                // icon: earned.badge.icon,
                criteria: JSON.parse(earned.badge.criteria),
                tenantId: input.tenantId as TenantId,
                category: "general" as BadgeCategory,
                points: 10
            },
            earnedAt: earned.earnedAt
        };
    }

    async revokeBadge(tenantId: TenantId, achievementId: string): Promise<void> {
        await this.prisma.badgeEarned.delete({
            where: { id: achievementId }
        });
    }

    async getUserAchievements(tenantId: TenantId, userId: UserId): Promise<Achievement[]> {
        const earned = await this.prisma.badgeEarned.findMany({
            where: { tenantId, userId },
            include: { badge: true },
            orderBy: { earnedAt: "desc" }
        });

        return earned.map((e: any) => ({
            id: e.id,
            badge: {
                id: e.badge.id as BadgeId,
                name: e.badge.name,
                description: e.badge.description,
                icon: e.badge.icon,
                category: "LEARNING",
                criteria: JSON.parse(e.badge.criteria)
            } as unknown as Badge,
            earnedAt: e.earnedAt
        }));
    }

    // Points system
    async awardPoints(input: AwardPointsInput): Promise<{ totalPoints: number; newLevel?: number }> {
        // In a real implementation we would add a record to a points log table
        // and update the aggregate
        // For now, we update the aggregate if it exists, or calculate

        // Persist aggregated points in userPoints to match route-level usage.

        const current = await this.prisma.userPoints.findFirst({
            where: { tenantId: input.tenantId, userId: input.userId }
        });

        let totalPoints = (current?.totalPoints ?? 0) + input.points;
        const { level } = this.calculateLevel(totalPoints);

        if (current) {
            await this.prisma.userPoints.update({
                where: { id: current.id },
                data: { totalPoints, level }
            });
        } else {
            await this.prisma.userPoints.create({
                data: {
                    tenantId: input.tenantId,
                    userId: input.userId,
                    totalPoints,
                    level
                }
            });
        }

        // A transaction log table can be added later without changing this API.

        return {
            totalPoints,
            newLevel: level
        };
    }

    async getUserProgress(tenantId: TenantId, userId: UserId): Promise<UserGamificationProgress> {
        // Use userPoints table
        const userPoints = await this.prisma.userPoints.findFirst({
            where: { tenantId, userId }
        });

        const totalPoints = userPoints?.totalPoints ?? 0;
        const { level, xpToNextLevel } = this.calculateLevel(totalPoints);

        // Get badges
        const achievements = await this.prisma.badgeEarned.findMany({
            where: { tenantId, userId },
            include: { badge: true }
        });

        return {
            userId,
            totalPoints,
            currentStreak: 0, // Would track in separate table
            longestStreak: 0,
            badges: achievements.map((a: any) => ({
                id: a.id,
                badge: {
                    id: a.badge.id as BadgeId,
                    name: a.badge.name,
                    description: a.badge.description,
                    icon: a.badge.icon,
                    category: "LEARNING",
                    criteria: JSON.parse(a.badge.criteria)
                } as unknown as Badge,
                earnedAt: a.earnedAt
            })),
            level,
            xpToNextLevel
        };
    }

    // Leaderboards
    async getLeaderboard(options: LeaderboardOptions): Promise<LeaderboardEntry[]> {
        const { tenantId, limit = 10, offset = 0 } = options;

        // Use userPoints for leaderboard
        const pointsEntries = await this.prisma.userPoints.findMany({
            where: { tenantId },
            orderBy: { totalPoints: 'desc' },
            take: limit,
            skip: offset
        });

        // Warning: This implementation doesn't join with User table for names
        // In a real sys we need display names.
        // Legacy code used 'userId' as name if join wasn't possible or simple.

        // Get badge counts
        const userIds = pointsEntries.map((p: any) => p.userId);
        const badgeCounts = await this.prisma.badgeEarned.groupBy({
            by: ["userId"],
            where: { tenantId, userId: { in: userIds } },
            _count: { id: true }
        });
        const badgeMap = new Map(badgeCounts.map((b: any) => [b.userId, b._count.id]));

        return pointsEntries.map((p: any, index: number) => ({
            rank: offset + index + 1,
            userId: p.userId as UserId,
            displayName: p.userId, // Todo: fetch names
            points: p.totalPoints,
            badges: badgeMap.get(p.userId) ?? 0,
            streak: 0
        }));
    }

    async getUserRank(tenantId: TenantId, userId: UserId, _period?: string): Promise<number> {
        // Simple rank via count of people with more points
        const userPoint = await this.prisma.userPoints.findFirst({
            where: { tenantId, userId }
        });

        if (!userPoint) return -1;

        const count = await this.prisma.userPoints.count({
            where: {
                tenantId,
                totalPoints: { gt: userPoint.totalPoints }
            }
        });

        return count + 1;
    }

    // Streak management
    async updateStreak(tenantId: TenantId, userId: UserId): Promise<{ currentStreak: number; bonusPoints?: number }> {
        // Placeholder
        return {
            currentStreak: 1,
            bonusPoints: 10
        };
    }

    // Auto-badge checks
    async checkAndAwardBadges(
        tenantId: TenantId,
        userId: UserId,
        event: string,
        data: Record<string, unknown>
    ): Promise<Achievement[]> {
        const awarded: Achievement[] = [];
        const badges = await this.prisma.badge.findMany({ where: { tenantId } });

        for (const badge of badges) {
            const criteria = JSON.parse(badge.criteria) as BadgeCriteria;
            let shouldAward = false;

            if (event === "module_completed" && criteria.type === "modules_completed") {
                const completedCount = await this.prisma.enrollment.count({
                    where: { tenantId, userId, progress: 100 }
                });
                shouldAward = completedCount >= criteria.threshold;
            }

            if (event === "assessment_completed" && criteria.type === "assessment_score") {
                const score = data.score as number;
                shouldAward = score >= criteria.threshold;
            }

            if (shouldAward) {
                // Check not already earned
                const existing = await this.prisma.badgeEarned.findFirst({
                    where: { tenantId, userId, badgeId: badge.id }
                });

                if (!existing) {
                    const achievement = await this.awardBadge({
                        tenantId,
                        userId,
                        badgeId: badge.id as BadgeId
                    });
                    awarded.push(achievement);
                }
            }
        }

        return awarded;
    }
}
