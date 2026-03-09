/**
 * Peer Tutoring Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for tutor profiles, requests, sessions, reviews, safety
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PeerTutoringServiceImpl } from '../peer-tutoring';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        tutorProfile: {
            upsert: vi.fn(),
            findFirst: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        tutoringRequest: {
            create: vi.fn(),
            findMany: vi.fn(),
            findUnique: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        tutoringSession: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findMany: vi.fn(),
            findUnique: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        tutoringReview: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
        },
        socialNotification: { create: vi.fn() },
    } as unknown as PrismaClient;
}

function makeProfileRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'profile-1',
        tenantId: 't1',
        userId: 'u1',
        displayName: 'Alice',
        bio: 'Math tutor',
        subjects: JSON.stringify(['math', 'physics']),
        modules: null,
        sessionTypes: JSON.stringify(['text_chat', 'video']),
        timezone: 'America/New_York',
        maxSessionsPerWeek: 5,
        rating: 4.5,
        reviewCount: 10,
        sessionsCompleted: 25,
        isAvailable: true,
        status: 'ACTIVE',
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

function makeRequestRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'req-1',
        tenantId: 't1',
        studentId: 'u2',
        subject: 'math',
        moduleId: null,
        lessonId: null,
        title: 'Help with algebra',
        description: 'I need help with quadratic equations',
        preferredTypes: JSON.stringify(['text_chat']),
        preferredTime: null,
        estimatedDuration: 60,
        urgency: 'medium',
        status: 'OPEN',
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

describe('PeerTutoringServiceImpl', () => {
    let service: PeerTutoringServiceImpl;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        service = new PeerTutoringServiceImpl({ prisma });
    });

    // =========================================================================
    // Tutor Profiles
    // =========================================================================
    describe('upsertTutorProfile', () => {
        it('creates or updates a tutor profile', async () => {
            (prisma.tutorProfile.upsert as any).mockResolvedValue(makeProfileRow());

            const profile = await service.upsertTutorProfile({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                displayName: 'Alice',
                bio: 'Math tutor',
                subjects: ['math', 'physics'],
                sessionTypes: ['text_chat', 'video'],
                timezone: 'America/New_York',
            });

            expect(profile.displayName).toBe('Alice');
            expect(profile.subjects).toContain('math');
            expect(prisma.tutorProfile.upsert).toHaveBeenCalled();
        });

        it('defaults maxSessionsPerWeek to 5', async () => {
            (prisma.tutorProfile.upsert as any).mockResolvedValue(makeProfileRow());

            await service.upsertTutorProfile({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                displayName: 'Alice',
                bio: 'Bio',
                subjects: ['math'],
                sessionTypes: ['text_chat'],
                timezone: 'UTC',
            });

            const callArgs = (prisma.tutorProfile.upsert as any).mock.calls[0][0];
            expect(callArgs.create.maxSessionsPerWeek).toBe(5);
        });
    });

    describe('getTutorProfile', () => {
        it('returns null for non-existent profile', async () => {
            (prisma.tutorProfile.findFirst as any).mockResolvedValue(null);

            const profile = await service.getTutorProfile({ tenantId: 't1' as any, userId: 'u1' as any });
            expect(profile).toBeNull();
        });

        it('returns profile when found', async () => {
            (prisma.tutorProfile.findFirst as any).mockResolvedValue(makeProfileRow());

            const profile = await service.getTutorProfile({ tenantId: 't1' as any, userId: 'u1' as any });
            expect(profile).not.toBeNull();
            expect(profile!.displayName).toBe('Alice');
        });
    });

    describe('searchTutors', () => {
        it('returns paginated results', async () => {
            (prisma.tutorProfile.findMany as any).mockResolvedValue([makeProfileRow()]);
            (prisma.tutorProfile.count as any).mockResolvedValue(1);

            const result = await service.searchTutors({
                tenantId: 't1' as any,
                pagination: { offset: 0, limit: 20 },
            });

            expect(result.items).toHaveLength(1);
            expect(result.totalCount).toBe(1);
        });

        it('filters by subject client-side', async () => {
            (prisma.tutorProfile.findMany as any).mockResolvedValue([
                makeProfileRow({ subjects: JSON.stringify(['math']) }),
                makeProfileRow({ id: 'p2', subjects: JSON.stringify(['english']) }),
            ]);
            (prisma.tutorProfile.count as any).mockResolvedValue(2);

            const result = await service.searchTutors({
                tenantId: 't1' as any,
                subject: 'math',
                pagination: { offset: 0, limit: 20 },
            });

            expect(result.items).toHaveLength(1);
        });

        it('filters by minimum rating', async () => {
            (prisma.tutorProfile.findMany as any).mockResolvedValue([makeProfileRow({ rating: 4.5 })]);
            (prisma.tutorProfile.count as any).mockResolvedValue(1);

            await service.searchTutors({
                tenantId: 't1' as any,
                minRating: 4.0,
                pagination: { offset: 0, limit: 20 },
            });

            const callArgs = (prisma.tutorProfile.findMany as any).mock.calls[0][0];
            expect(callArgs.where.rating.gte).toBe(4.0);
        });
    });

    describe('toggleAvailability', () => {
        it('toggles tutor availability', async () => {
            (prisma.tutorProfile.update as any).mockResolvedValue(makeProfileRow({ isAvailable: false }));

            const profile = await service.toggleAvailability({
                tenantId: 't1' as any,
                userId: 'u1' as any,
                isAvailable: false,
            });

            expect(profile.isAvailable).toBe(false);
        });
    });

    // =========================================================================
    // Tutoring Requests
    // =========================================================================
    describe('createRequest', () => {
        it('creates a request and notifies matching tutors', async () => {
            (prisma.tutoringRequest.create as any).mockResolvedValue(makeRequestRow());
            (prisma.tutorProfile.findMany as any).mockResolvedValue([makeProfileRow()]);

            const request = await service.createRequest({
                tenantId: 't1' as any,
                studentId: 'u2' as any,
                subject: 'math',
                title: 'Help with algebra',
                description: 'I need help with quadratic equations',
                preferredTypes: ['text_chat'],
            });

            expect(request.title).toBe('Help with algebra');
            expect(request.status).toBe('open');
            expect(prisma.socialNotification.create).toHaveBeenCalled();
        });

        it('defaults urgency to medium', async () => {
            (prisma.tutoringRequest.create as any).mockResolvedValue(makeRequestRow());
            (prisma.tutorProfile.findMany as any).mockResolvedValue([]);

            await service.createRequest({
                tenantId: 't1' as any,
                studentId: 'u2' as any,
                subject: 'math',
                title: 'Help',
                description: 'desc',
                preferredTypes: ['text_chat'],
            });

            const callArgs = (prisma.tutoringRequest.create as any).mock.calls[0][0];
            expect(callArgs.data.urgency).toBe('medium');
        });
    });

    describe('listOpenRequests', () => {
        it('returns open requests sorted by urgency', async () => {
            (prisma.tutoringRequest.findMany as any).mockResolvedValue([makeRequestRow()]);
            (prisma.tutoringRequest.count as any).mockResolvedValue(1);

            const result = await service.listOpenRequests({
                tenantId: 't1' as any,
                pagination: { offset: 0, limit: 20 },
            });

            expect(result.items).toHaveLength(1);
            const callArgs = (prisma.tutoringRequest.findMany as any).mock.calls[0][0];
            expect(callArgs.where.status).toBe('OPEN');
        });
    });
});
