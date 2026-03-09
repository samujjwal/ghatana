/**
 * Study Groups Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for CRUD, membership, sessions, RSVP
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { StudyGroupServiceImpl } from '../study-groups';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        studyGroup: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        studyGroupMember: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
        },
        studyGroupJoinRequest: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        studyGroupInvite: {
            create: vi.fn(),
            findUnique: vi.fn(),
            update: vi.fn(),
        },
        studySession: {
            create: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        sessionRsvp: {
            upsert: vi.fn(),
        },
        socialActivity: { create: vi.fn() },
        socialNotification: { create: vi.fn() },
    } as unknown as PrismaClient;
}

function makeGroupRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'grp-1',
        tenantId: 't1',
        name: 'Physics Study Group',
        description: 'Study Newton\'s laws together',
        createdBy: 'u1',
        visibility: 'PUBLIC',
        subjects: JSON.stringify(['physics']),
        modules: null,
        maxMembers: 20,
        requireApproval: false,
        memberCount: 1,
        lastActivityAt: new Date(),
        status: 'ACTIVE',
        coverImageUrl: null,
        allowGuestView: false,
        archivedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
        _count: { members: 1 },
        ...overrides,
    };
}

function makeMemberRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'mem-1',
        groupId: 'grp-1',
        userId: 'u1',
        role: 'OWNER',
        joinedAt: new Date(),
        invitedBy: null,
        messagesCount: 0,
        lastActiveAt: null,
        notificationsEnabled: true,
        mutedUntil: null,
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

function makeSessionRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'sess-1',
        groupId: 'grp-1',
        title: 'Review Session',
        description: 'Go over chapter 5',
        createdBy: 'u1',
        scheduledAt: new Date(Date.now() + 86400000),
        duration: 60,
        timezone: 'UTC',
        type: 'REVIEW',
        meetingUrl: null,
        maxParticipants: 10,
        rsvpDeadline: null,
        moduleId: null,
        lessonIds: null,
        agenda: null,
        attachments: null,
        status: 'SCHEDULED',
        startedAt: null,
        endedAt: null,
        notes: null,
        recordingUrl: null,
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

describe('StudyGroupServiceImpl', () => {
    let service: StudyGroupServiceImpl;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        service = new StudyGroupServiceImpl({ prisma });
    });

    // =========================================================================
    // Group CRUD
    // =========================================================================
    describe('createGroup', () => {
        it('creates a group and adds creator as owner via nested create', async () => {
            (prisma.studyGroup.create as any).mockResolvedValue(makeGroupRow());

            const group = await service.createGroup({
                tenantId: 't1' as any,
                createdBy: 'u1' as any,
                name: 'Physics Study Group',
                description: 'Study Newton\'s laws together',
                visibility: 'public',
                subjects: ['physics'],
            });

            expect(group.name).toBe('Physics Study Group');
            expect(prisma.studyGroup.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({
                        members: { create: { userId: 'u1', role: 'OWNER' } },
                        memberCount: 1,
                    }),
                }),
            );
        });

        it('respects maxMembers setting', async () => {
            (prisma.studyGroup.create as any).mockResolvedValue(makeGroupRow({ maxMembers: 5 }));

            await service.createGroup({
                tenantId: 't1' as any,
                createdBy: 'u1' as any,
                name: 'Small Group',
                description: 'desc',
                visibility: 'public',
                subjects: ['math'],
                maxMembers: 5,
            });

            const callArgs = (prisma.studyGroup.create as any).mock.calls[0][0];
            expect(callArgs.data.maxMembers).toBe(5);
        });

        it('defaults maxMembers to 50', async () => {
            (prisma.studyGroup.create as any).mockResolvedValue(makeGroupRow());

            await service.createGroup({
                tenantId: 't1' as any,
                createdBy: 'u1' as any,
                name: 'Group',
                description: 'desc',
                visibility: 'public',
                subjects: ['math'],
            });

            const callArgs = (prisma.studyGroup.create as any).mock.calls[0][0];
            expect(callArgs.data.maxMembers).toBe(50);
        });
    });

    describe('getGroup', () => {
        it('returns group when found', async () => {
            (prisma.studyGroup.findFirst as any).mockResolvedValue(makeGroupRow());

            const group = await service.getGroup({ tenantId: 't1' as any, groupId: 'grp-1' });
            expect(group.name).toBe('Physics Study Group');
        });

        it('throws for non-existent group', async () => {
            (prisma.studyGroup.findFirst as any).mockResolvedValue(null);

            await expect(
                service.getGroup({ tenantId: 't1' as any, groupId: 'nonexistent' }),
            ).rejects.toThrow('Study group not found');
        });
    });

    describe('listGroups', () => {
        it('returns paginated groups', async () => {
            (prisma.studyGroup.findMany as any).mockResolvedValue([makeGroupRow()]);
            (prisma.studyGroup.count as any).mockResolvedValue(1);

            const result = await service.listGroups({
                tenantId: 't1' as any,
                pagination: { offset: 0, limit: 20 },
            });

            expect(result.items).toHaveLength(1);
            expect(result.totalCount).toBe(1);
        });

        it('filters by search query', async () => {
            (prisma.studyGroup.findMany as any).mockResolvedValue([makeGroupRow()]);
            (prisma.studyGroup.count as any).mockResolvedValue(1);

            await service.listGroups({
                tenantId: 't1' as any,
                searchQuery: 'physics',
                pagination: { offset: 0, limit: 20 },
            });

            const callArgs = (prisma.studyGroup.findMany as any).mock.calls[0][0];
            expect(callArgs.where.OR).toBeDefined();
        });
    });

    describe('updateGroup', () => {
        it('allows owner to update group', async () => {
            // requireRole calls findFirst for membership check
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow({ role: 'OWNER' }));
            (prisma.studyGroup.update as any).mockResolvedValue(makeGroupRow({ name: 'Updated Name' }));

            const group = await service.updateGroup({
                tenantId: 't1' as any,
                groupId: 'grp-1',
                userId: 'u1' as any,
                patch: { name: 'Updated Name' },
            });

            expect(group.name).toBe('Updated Name');
        });

        it('rejects non-owner/non-admin update', async () => {
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow({ role: 'MEMBER' }));

            await expect(
                service.updateGroup({
                    tenantId: 't1' as any,
                    groupId: 'grp-1',
                    userId: 'u2' as any,
                    patch: { name: 'Hacked' },
                }),
            ).rejects.toThrow('Insufficient permissions');
        });
    });

    describe('archiveGroup', () => {
        it('allows owner to archive group', async () => {
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow({ role: 'OWNER' }));
            (prisma.studyGroup.update as any).mockResolvedValue(makeGroupRow({ status: 'ARCHIVED' }));

            const group = await service.archiveGroup({
                tenantId: 't1' as any,
                groupId: 'grp-1',
                userId: 'u1' as any,
            });

            expect(prisma.studyGroup.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ status: 'ARCHIVED' }),
                }),
            );
        });

        it('rejects non-owner archive', async () => {
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow({ role: 'ADMIN' }));

            await expect(
                service.archiveGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u2' as any }),
            ).rejects.toThrow('Insufficient permissions');
        });
    });

    // =========================================================================
    // Membership
    // =========================================================================
    describe('joinGroup', () => {
        it('adds member to public group', async () => {
            (prisma.studyGroup.findFirst as any).mockResolvedValue(
                makeGroupRow({ visibility: 'PUBLIC', _count: { members: 5 } }),
            );
            (prisma.studyGroupMember.create as any).mockResolvedValue(makeMemberRow({ userId: 'u2', role: 'MEMBER' }));
            (prisma.studyGroup.update as any).mockResolvedValue({});
            (prisma.socialActivity.create as any).mockResolvedValue({});

            const member = await service.joinGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u2' as any });

            expect(prisma.studyGroupMember.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ role: 'MEMBER' }),
                }),
            );
        });

        it('rejects join on non-public group', async () => {
            (prisma.studyGroup.findFirst as any).mockResolvedValue(
                makeGroupRow({ visibility: 'PRIVATE', _count: { members: 5 } }),
            );

            await expect(
                service.joinGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u2' as any }),
            ).rejects.toThrow('Cannot join non-public group directly');
        });

        it('rejects join when group is full', async () => {
            (prisma.studyGroup.findFirst as any).mockResolvedValue(
                makeGroupRow({ visibility: 'PUBLIC', maxMembers: 2, _count: { members: 2 } }),
            );

            await expect(
                service.joinGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u3' as any }),
            ).rejects.toThrow('Group is full');
        });

        it('increments member count after join', async () => {
            (prisma.studyGroup.findFirst as any).mockResolvedValue(
                makeGroupRow({ visibility: 'PUBLIC', _count: { members: 3 } }),
            );
            (prisma.studyGroupMember.create as any).mockResolvedValue(makeMemberRow({ userId: 'u2', role: 'MEMBER' }));
            (prisma.studyGroup.update as any).mockResolvedValue({});
            (prisma.socialActivity.create as any).mockResolvedValue({});

            await service.joinGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u2' as any });

            expect(prisma.studyGroup.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ memberCount: { increment: 1 } }),
                }),
            );
        });
    });

    describe('leaveGroup', () => {
        it('removes member from group', async () => {
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow({ userId: 'u2', role: 'MEMBER' }));
            (prisma.studyGroupMember.delete as any).mockResolvedValue({});
            (prisma.studyGroup.update as any).mockResolvedValue({});

            await service.leaveGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u2' as any });

            expect(prisma.studyGroupMember.delete).toHaveBeenCalled();
        });

        it('prevents owner from leaving', async () => {
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow({ role: 'OWNER' }));

            await expect(
                service.leaveGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u1' as any }),
            ).rejects.toThrow('Owner cannot leave');
        });

        it('throws if not a member', async () => {
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(null);

            await expect(
                service.leaveGroup({ tenantId: 't1' as any, groupId: 'grp-1', userId: 'u5' as any }),
            ).rejects.toThrow('Not a member');
        });
    });

    describe('removeMember', () => {
        it('allows owner to remove members', async () => {
            // requireRole check for removedBy
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow({ role: 'OWNER' }));
            // findUnique for target member
            (prisma.studyGroupMember.findUnique as any).mockResolvedValue(makeMemberRow({ id: 'mem-2', userId: 'u2', role: 'MEMBER' }));
            (prisma.studyGroupMember.delete as any).mockResolvedValue({});
            (prisma.studyGroup.update as any).mockResolvedValue({});

            await service.removeMember({
                tenantId: 't1' as any,
                groupId: 'grp-1',
                memberId: 'mem-2',
                removedBy: 'u1' as any,
            });

            expect(prisma.studyGroupMember.delete).toHaveBeenCalled();
            expect(prisma.studyGroup.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: { memberCount: { decrement: 1 } },
                }),
            );
        });

        it('prevents removing the owner', async () => {
            (prisma.studyGroupMember.findUnique as any).mockResolvedValue(makeMemberRow({ role: 'OWNER' }));

            await expect(
                service.removeMember({
                    tenantId: 't1' as any,
                    groupId: 'grp-1',
                    memberId: 'mem-1',
                    removedBy: 'u1' as any,
                }),
            ).rejects.toThrow('Cannot remove group owner');
        });
    });

    // =========================================================================
    // Study Sessions
    // =========================================================================
    describe('scheduleSession', () => {
        it('creates a session for the group', async () => {
            // requireRole check
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(makeMemberRow());
            (prisma.studySession.create as any).mockResolvedValue(makeSessionRow());
            (prisma.sessionRsvp.upsert as any).mockResolvedValue({});
            // Auto-RSVP creator
            (prisma as any).sessionRsvp = { create: vi.fn().mockResolvedValue({}) };
            (prisma.studyGroup.findUnique as any).mockResolvedValue(makeGroupRow());
            (prisma.socialActivity.create as any).mockResolvedValue({});

            const session = await service.scheduleSession({
                tenantId: 't1' as any,
                groupId: 'grp-1',
                createdBy: 'u1' as any,
                title: 'Review Session',
                description: 'Go over chapter 5',
                scheduledAt: new Date(Date.now() + 86400000),
                duration: 60,
                type: 'review',
            });

            expect(session.title).toBe('Review Session');
            expect(prisma.studySession.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ status: 'SCHEDULED' }),
                }),
            );
        });

        it('rejects session creation by non-members', async () => {
            (prisma.studyGroupMember.findFirst as any).mockResolvedValue(null);

            await expect(
                service.scheduleSession({
                    tenantId: 't1' as any,
                    groupId: 'grp-1',
                    createdBy: 'u3' as any,
                    title: 'Session',
                    scheduledAt: new Date(),
                    duration: 60,
                    type: 'discussion',
                }),
            ).rejects.toThrow('Insufficient permissions');
        });
    });

    // =========================================================================
    // RSVP
    // =========================================================================
    describe('rsvpSession', () => {
        it('upserts RSVP for a session', async () => {
            (prisma as any).sessionRsvp = {
                upsert: vi.fn().mockResolvedValue({
                    sessionId: 'sess-1',
                    userId: 'u2',
                    status: 'ATTENDING',
                    createdAt: new Date(),
                }),
            };

            const rsvp = await service.rsvpSession({
                tenantId: 't1' as any,
                sessionId: 'sess-1',
                userId: 'u2' as any,
                status: 'attending',
            });

            expect((prisma as any).sessionRsvp.upsert).toHaveBeenCalled();
        });
    });
});
