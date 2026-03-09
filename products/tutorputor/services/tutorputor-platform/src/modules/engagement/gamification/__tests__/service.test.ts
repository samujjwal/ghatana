/**
 * Gamification Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for points, badges, leaderboard, levels
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GamificationService } from '../service';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        badge: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findMany: vi.fn(),
            findUnique: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
        },
        badgeEarned: {
            findFirst: vi.fn(),
            findMany: vi.fn(),
            create: vi.fn(),
            delete: vi.fn(),
            groupBy: vi.fn(),
        },
        userPoints: {
            findFirst: vi.fn(),
            findMany: vi.fn(),
            create: vi.fn(),
            update: vi.fn(),
            count: vi.fn(),
        },
        enrollment: {
            count: vi.fn(),
        },
    } as unknown as PrismaClient;
}

describe('GamificationService', () => {
    let service: GamificationService;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        service = new GamificationService(prisma);
    });

    // =========================================================================
    // Badge Management
    // =========================================================================
    describe('createBadge', () => {
        it('creates a badge and returns it', async () => {
            (prisma.badge.create as any).mockResolvedValue({
                id: 'badge-1',
                tenantId: 't1',
                name: 'First Steps',
                description: 'Complete your first module',
                icon: '🏆',
                criteria: JSON.stringify({ type: 'modules_completed', threshold: 1 }),
            });

            const badge = await service.createBadge({
                tenantId: 't1' as any,
                name: 'First Steps',
                description: 'Complete your first module',
                icon: '🏆',
                criteria: { type: 'modules_completed', threshold: 1 },
            });

            expect(badge.id).toBe('badge-1');
            expect(badge.name).toBe('First Steps');
            expect(badge.criteria).toEqual({ type: 'modules_completed', threshold: 1 });
        });
    });

    describe('getBadge', () => {
        it('returns null for non-existent badge', async () => {
            (prisma.badge.findFirst as any).mockResolvedValue(null);

            const badge = await service.getBadge('t1' as any, 'nonexistent' as any);
            expect(badge).toBeNull();
        });

        it('returns badge when found', async () => {
            (prisma.badge.findFirst as any).mockResolvedValue({
                id: 'badge-1',
                name: 'Scholar',
                description: 'Achieve mastery',
                icon: '📚',
                criteria: JSON.stringify({ type: 'assessment_score', threshold: 90 }),
            });

            const badge = await service.getBadge('t1' as any, 'badge-1' as any);
            expect(badge).not.toBeNull();
            expect(badge!.name).toBe('Scholar');
        });
    });

    describe('listBadges', () => {
        it('returns all badges for tenant', async () => {
            (prisma.badge.findMany as any).mockResolvedValue([
                { id: 'b1', name: 'A', description: 'd', icon: '🎖️', criteria: '{"type":"custom","threshold":1}' },
                { id: 'b2', name: 'B', description: 'd', icon: '🏅', criteria: '{"type":"custom","threshold":2}' },
            ]);

            const badges = await service.listBadges('t1' as any);
            expect(badges).toHaveLength(2);
        });
    });

    describe('deleteBadge', () => {
        it('calls prisma delete', async () => {
            (prisma.badge.delete as any).mockResolvedValue({});

            await service.deleteBadge('t1' as any, 'badge-1' as any);
            expect(prisma.badge.delete).toHaveBeenCalledWith({ where: { id: 'badge-1' } });
        });
    });

    // =========================================================================
    // Achievement Awarding
    // =========================================================================
    describe('awardBadge', () => {
        it('creates a new achievement when not already earned', async () => {
            (prisma.badgeEarned.findFirst as any).mockResolvedValue(null);
            (prisma.badgeEarned.create as any).mockResolvedValue({
                id: 'earned-1',
                userId: 'u1',
                badgeId: 'b1',
                tenantId: 't1',
                earnedAt: new Date(),
                badge: { id: 'b1', name: 'First', description: 'desc', icon: '🏆', criteria: '{"type":"custom","threshold":1}' },
            });

            const achievement = await service.awardBadge({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                badgeId: 'b1' as any,
            });

            expect(achievement.id).toBe('earned-1');
            expect(prisma.badgeEarned.create).toHaveBeenCalled();
        });

        it('returns existing achievement if already earned (idempotent)', async () => {
            (prisma.badgeEarned.findFirst as any).mockResolvedValue({
                id: 'earned-1',
                userId: 'u1',
                badgeId: 'b1',
                tenantId: 't1',
                earnedAt: new Date(),
            });
            (prisma.badge.findUnique as any).mockResolvedValue({
                id: 'b1',
                name: 'First',
                description: 'desc',
                icon: '🏆',
                criteria: '{"type":"custom","threshold":1}',
            });

            const achievement = await service.awardBadge({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                badgeId: 'b1' as any,
            });

            expect(achievement.id).toBe('earned-1');
            expect(prisma.badgeEarned.create).not.toHaveBeenCalled();
        });
    });

    // =========================================================================
    // Points System
    // =========================================================================
    describe('awardPoints', () => {
        it('creates new points record if user has none', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue(null);
            (prisma.userPoints.create as any).mockResolvedValue({ id: 'up-1', totalPoints: 100, level: 1 });

            const result = await service.awardPoints({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                points: 100,
                reason: 'Module completed',
                sourceType: 'module_complete',
            });

            expect(result.totalPoints).toBe(100);
            expect(result.newLevel).toBe(2);
            expect(prisma.userPoints.create).toHaveBeenCalled();
        });

        it('updates existing points record', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue({ id: 'up-1', totalPoints: 200 });
            (prisma.userPoints.update as any).mockResolvedValue({ totalPoints: 300, level: 2 });

            const result = await service.awardPoints({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                points: 100,
                reason: 'Assessment bonus',
                sourceType: 'assessment',
            });

            expect(result.totalPoints).toBe(300);
            expect(prisma.userPoints.update).toHaveBeenCalled();
        });
    });

    // =========================================================================
    // Level Calculation
    // =========================================================================
    describe('level calculation', () => {
        it('level 1 at 0 points', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue({ id: 'up-1', totalPoints: 0 });
            (prisma.badgeEarned.findMany as any).mockResolvedValue([]);

            const progress = await service.getUserProgress('t1' as any, 'u1' as any);
            expect(progress.level).toBe(1);
        });

        it('level 2 at 100 points', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue({ id: 'up-1', totalPoints: 100 });
            (prisma.badgeEarned.findMany as any).mockResolvedValue([]);

            const progress = await service.getUserProgress('t1' as any, 'u1' as any);
            expect(progress.level).toBe(2);
        });

        it('level 5 at 1000 points', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue({ id: 'up-1', totalPoints: 1000 });
            (prisma.badgeEarned.findMany as any).mockResolvedValue([]);

            const progress = await service.getUserProgress('t1' as any, 'u1' as any);
            expect(progress.level).toBe(5);
        });

        it('calculates xpToNextLevel', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue({ id: 'up-1', totalPoints: 150 });
            (prisma.badgeEarned.findMany as any).mockResolvedValue([]);

            const progress = await service.getUserProgress('t1' as any, 'u1' as any);
            // Level 2 starts at 100, level 3 at 250 → xpToNextLevel = 250 - 150 = 100
            expect(progress.xpToNextLevel).toBe(100);
        });
    });

    // =========================================================================
    // Leaderboard
    // =========================================================================
    describe('getLeaderboard', () => {
        it('returns ranked entries sorted by points desc', async () => {
            (prisma.userPoints.findMany as any).mockResolvedValue([
                { userId: 'u1', totalPoints: 500 },
                { userId: 'u2', totalPoints: 300 },
            ]);
            (prisma.badgeEarned.groupBy as any).mockResolvedValue([
                { userId: 'u1', _count: { id: 3 } },
                { userId: 'u2', _count: { id: 1 } },
            ]);

            const leaderboard = await service.getLeaderboard({ tenantId: 't1' as any });

            expect(leaderboard).toHaveLength(2);
            expect(leaderboard[0].rank).toBe(1);
            expect(leaderboard[0].points).toBe(500);
            expect(leaderboard[0].badges).toBe(3);
            expect(leaderboard[1].rank).toBe(2);
        });

        it('respects limit and offset', async () => {
            (prisma.userPoints.findMany as any).mockResolvedValue([
                { userId: 'u3', totalPoints: 100 },
            ]);
            (prisma.badgeEarned.groupBy as any).mockResolvedValue([]);

            const leaderboard = await service.getLeaderboard({
                tenantId: 't1' as any,
                limit: 1,
                offset: 2,
            });

            expect(leaderboard).toHaveLength(1);
            expect(leaderboard[0].rank).toBe(3); // offset + 1
        });
    });

    // =========================================================================
    // getUserRank
    // =========================================================================
    describe('getUserRank', () => {
        it('returns -1 for users with no points', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue(null);

            const rank = await service.getUserRank('t1' as any, 'u1' as any);
            expect(rank).toBe(-1);
        });

        it('returns rank based on count of users with more points', async () => {
            (prisma.userPoints.findFirst as any).mockResolvedValue({ totalPoints: 500 });
            (prisma.userPoints.count as any).mockResolvedValue(4); // 4 users above

            const rank = await service.getUserRank('t1' as any, 'u1' as any);
            expect(rank).toBe(5);
        });
    });

    // =========================================================================
    // Auto-badge checks
    // =========================================================================
    describe('checkAndAwardBadges', () => {
        it('awards badge when module_completed threshold met', async () => {
            (prisma.badge.findMany as any).mockResolvedValue([
                { id: 'b1', criteria: JSON.stringify({ type: 'modules_completed', threshold: 5 }) },
            ]);
            (prisma.enrollment.count as any).mockResolvedValue(5);
            (prisma.badgeEarned.findFirst as any).mockResolvedValue(null);
            (prisma.badgeEarned.create as any).mockResolvedValue({
                id: 'earned-1',
                userId: 'u1',
                badgeId: 'b1',
                tenantId: 't1',
                earnedAt: new Date(),
                badge: { id: 'b1', name: 'Complete 5', description: 'd', icon: '🏆', criteria: '{"type":"modules_completed","threshold":5}' },
            });

            const awarded = await service.checkAndAwardBadges('t1' as any, 'u1' as any, 'module_completed', {});

            expect(awarded).toHaveLength(1);
        });

        it('does not re-award existing badge', async () => {
            (prisma.badge.findMany as any).mockResolvedValue([
                { id: 'b1', criteria: JSON.stringify({ type: 'modules_completed', threshold: 1 }) },
            ]);
            (prisma.enrollment.count as any).mockResolvedValue(3);
            (prisma.badgeEarned.findFirst as any).mockResolvedValue({ id: 'already-earned' });

            const awarded = await service.checkAndAwardBadges('t1' as any, 'u1' as any, 'module_completed', {});

            expect(awarded).toHaveLength(0);
        });

        it('awards badge on assessment_score threshold', async () => {
            (prisma.badge.findMany as any).mockResolvedValue([
                { id: 'b2', criteria: JSON.stringify({ type: 'assessment_score', threshold: 90 }) },
            ]);
            (prisma.badgeEarned.findFirst as any).mockResolvedValue(null);
            (prisma.badgeEarned.create as any).mockResolvedValue({
                id: 'earned-2',
                userId: 'u1',
                badgeId: 'b2',
                tenantId: 't1',
                earnedAt: new Date(),
                badge: { id: 'b2', name: 'High Scorer', description: 'd', icon: '🌟', criteria: '{"type":"assessment_score","threshold":90}' },
            });

            const awarded = await service.checkAndAwardBadges('t1' as any, 'u1' as any, 'assessment_completed', { score: 95 });

            expect(awarded).toHaveLength(1);
        });
    });
});
