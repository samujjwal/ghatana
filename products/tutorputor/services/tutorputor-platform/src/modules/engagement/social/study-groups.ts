/**
 * @doc.type module
 * @doc.purpose Study Group service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { Redis } from 'ioredis';
import type {
    StudyGroupService,
    TenantId,
    UserId,
    ModuleId,
    PaginationArgs,
    PaginatedResult,
} from '@ghatana/tutorputor-contracts';

import type {
    StudyGroup,
    StudyGroupVisibility,
    StudyGroupMember,
    StudyGroupRole,
    StudyGroupJoinRequest,
    StudyGroupInvite,
    StudySession,
    StudySessionType,
    SessionRsvp,
} from '@ghatana/tutorputor-contracts/v1/social';
import { nanoid } from 'nanoid';

/**
 * Configuration for StudyGroupServiceImpl
 */
export interface StudyGroupServiceConfig {
    prisma: PrismaClient;
    redis?: Redis;
    defaultMaxMembers?: number;
    inviteExpirationDays?: number;
}

/**
 * Implementation of the Study Group service.
 */
export class StudyGroupServiceImpl implements StudyGroupService {
    private readonly prisma: PrismaClient;
    private readonly redis?: Redis;
    private readonly defaultMaxMembers: number;
    private readonly inviteExpirationDays: number;

    constructor(config: StudyGroupServiceConfig) {
        this.prisma = config.prisma;
        this.redis = config.redis;
        this.defaultMaxMembers = config.defaultMaxMembers ?? 50;
        this.inviteExpirationDays = config.inviteExpirationDays ?? 7;
    }

    // ---------------------------------------------------------------------------
    // Group CRUD
    // ---------------------------------------------------------------------------

    async createGroup(args: {
        tenantId: TenantId;
        createdBy: UserId;
        name: string;
        description: string;
        visibility: StudyGroupVisibility;
        subjects: string[];
        moduleIds?: ModuleId[];
        maxMembers?: number;
        requireApproval?: boolean;
    }): Promise<StudyGroup> {
        const group = await this.prisma.studyGroup.create({
            data: {
                tenantId: args.tenantId,
                name: args.name,
                description: args.description,
                createdBy: args.createdBy,
                visibility: this.mapVisibilityToDb(args.visibility),
                subjects: JSON.stringify(args.subjects),
                modules: args.moduleIds ? JSON.stringify(args.moduleIds) : null,
                maxMembers: args.maxMembers ?? this.defaultMaxMembers,
                requireApproval: args.requireApproval ?? false,
                memberCount: 1,
                members: {
                    create: {
                        userId: args.createdBy,
                        role: 'OWNER',
                    },
                },
            },
            include: { members: true },
        });

        return this.mapGroupFromDb(group);
    }

    async getGroup(args: {
        tenantId: TenantId;
        groupId: string;
        userId?: UserId;
    }): Promise<StudyGroup & { membership?: StudyGroupMember }> {
        const group = await this.prisma.studyGroup.findFirst({
            where: {
                id: args.groupId,
                tenantId: args.tenantId,
            },
            include: {
                members: args.userId
                    ? { where: { userId: args.userId } }
                    : false,
            },
        });

        if (!group) {
            throw new Error('Study group not found');
        }

        const mapped = this.mapGroupFromDb(group);
        const membership = args.userId && (group as any).members?.[0]
            ? this.mapMemberFromDb((group as any).members[0])
            : undefined;

        return { ...mapped, membership };
    }

    async listGroups(args: {
        tenantId: TenantId;
        userId?: UserId;
        visibility?: StudyGroupVisibility;
        subject?: string;
        moduleId?: ModuleId;
        searchQuery?: string;
        memberOf?: boolean;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<StudyGroup>> {
        const where: any = {
            tenantId: args.tenantId,
            status: 'ACTIVE',
        };

        if (args.visibility) {
            where.visibility = this.mapVisibilityToDb(args.visibility);
        }

        if (args.searchQuery) {
            where.OR = [
                { name: { contains: args.searchQuery } },
                { description: { contains: args.searchQuery } },
            ];
        }

        if (args.memberOf && args.userId) {
            where.members = { some: { userId: args.userId } };
        }

        const [items, total] = await Promise.all([
            this.prisma.studyGroup.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: { lastActivityAt: 'desc' },
            }),
            this.prisma.studyGroup.count({ where }),
        ]);

        return {
            items: items.map((g: any) => this.mapGroupFromDb(g)),
            totalCount: total,
            total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async updateGroup(args: {
        tenantId: TenantId;
        groupId: string;
        userId: UserId;
        patch: Partial<Pick<StudyGroup, 'name' | 'description' | 'visibility' | 'maxMembers' | 'requireApproval' | 'subjects' | 'coverImageUrl'>>;
    }): Promise<StudyGroup> {
        await this.requireRole(args.groupId, args.userId, ['OWNER', 'ADMIN']);

        const data: any = {};
        if (args.patch.name !== undefined) data.name = args.patch.name;
        if (args.patch.description !== undefined) data.description = args.patch.description;
        if (args.patch.visibility !== undefined) data.visibility = this.mapVisibilityToDb(args.patch.visibility);
        if (args.patch.maxMembers !== undefined) data.maxMembers = args.patch.maxMembers;
        if (args.patch.requireApproval !== undefined) data.requireApproval = args.patch.requireApproval;
        if (args.patch.subjects !== undefined) data.subjects = JSON.stringify(args.patch.subjects);
        if (args.patch.coverImageUrl !== undefined) data.coverImageUrl = args.patch.coverImageUrl;

        const group = await this.prisma.studyGroup.update({
            where: { id: args.groupId },
            data,
        });

        return this.mapGroupFromDb(group);
    }

    async archiveGroup(args: {
        tenantId: TenantId;
        groupId: string;
        userId: UserId;
    }): Promise<StudyGroup> {
        await this.requireRole(args.groupId, args.userId, ['OWNER']);

        const group = await this.prisma.studyGroup.update({
            where: { id: args.groupId },
            data: {
                status: 'ARCHIVED',
                archivedAt: new Date(),
            },
        });

        return this.mapGroupFromDb(group);
    }

    // ---------------------------------------------------------------------------
    // Membership
    // ---------------------------------------------------------------------------

    async joinGroup(args: {
        tenantId: TenantId;
        groupId: string;
        userId: UserId;
    }): Promise<StudyGroupMember> {
        const group = await this.prisma.studyGroup.findFirst({
            where: { id: args.groupId, tenantId: args.tenantId },
            include: { _count: { select: { members: true } } },
        });

        if (!group) {
            throw new Error('Study group not found');
        }

        if (group.visibility !== 'PUBLIC') {
            throw new Error('Cannot join non-public group directly');
        }

        if (group._count.members >= group.maxMembers) {
            throw new Error('Group is full');
        }

        const member = await this.prisma.studyGroupMember.create({
            data: {
                groupId: args.groupId,
                userId: args.userId,
                role: 'MEMBER',
            },
        });

        await this.prisma.studyGroup.update({
            where: { id: args.groupId },
            data: {
                memberCount: { increment: 1 },
                lastActivityAt: new Date(),
            },
        });

        await this.publishActivity(args.tenantId, {
            type: 'JOINED_GROUP',
            actorId: args.userId,
            targetType: 'study_group',
            targetId: args.groupId,
            targetTitle: group.name,
            studyGroupId: args.groupId,
        });

        return this.mapMemberFromDb(member);
    }

    async requestJoin(args: {
        tenantId: TenantId;
        groupId: string;
        userId: UserId;
        message?: string;
    }): Promise<StudyGroupJoinRequest> {
        const existing = await this.prisma.studyGroupJoinRequest.findFirst({
            where: {
                groupId: args.groupId,
                userId: args.userId,
                status: 'PENDING',
            },
        });

        if (existing) {
            throw new Error('Join request already pending');
        }

        const request = await this.prisma.studyGroupJoinRequest.create({
            data: {
                groupId: args.groupId,
                userId: args.userId,
                message: args.message,
                status: 'PENDING',
            },
        });

        // Notify group admins
        const admins = await this.prisma.studyGroupMember.findMany({
            where: {
                groupId: args.groupId,
                role: { in: ['OWNER', 'ADMIN'] },
            },
        });

        for (const admin of admins) {
            await this.createNotification(args.tenantId, admin.userId, {
                type: 'GROUP_JOIN_REQUEST',
                title: 'New join request',
                body: `Someone wants to join your study group`,
                targetType: 'study_group',
                targetId: args.groupId,
                actorId: args.userId,
            });
        }

        return this.mapJoinRequestFromDb(request);
    }

    async handleJoinRequest(args: {
        tenantId: TenantId;
        requestId: string;
        reviewerId: UserId;
        approved: boolean;
        rejectionReason?: string;
    }): Promise<StudyGroupJoinRequest> {
        const request = await this.prisma.studyGroupJoinRequest.findUnique({
            where: { id: args.requestId },
        });

        if (!request) {
            throw new Error('Join request not found');
        }

        await this.requireRole(request.groupId, args.reviewerId, ['OWNER', 'ADMIN', 'MODERATOR']);

        const updated = await this.prisma.studyGroupJoinRequest.update({
            where: { id: args.requestId },
            data: {
                status: args.approved ? 'APPROVED' : 'REJECTED',
                reviewedBy: args.reviewerId,
                reviewedAt: new Date(),
                rejectionReason: args.rejectionReason,
            },
        });

        if (args.approved) {
            await this.prisma.studyGroupMember.create({
                data: {
                    groupId: request.groupId,
                    userId: request.userId,
                    role: 'MEMBER',
                },
            });

            await this.prisma.studyGroup.update({
                where: { id: request.groupId },
                data: { memberCount: { increment: 1 } },
            });
        }

        return this.mapJoinRequestFromDb(updated);
    }

    async listJoinRequests(args: {
        tenantId: TenantId;
        groupId: string;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<StudyGroupJoinRequest>> {
        const [items, total] = await Promise.all([
            this.prisma.studyGroupJoinRequest.findMany({
                where: {
                    groupId: args.groupId,
                    status: 'PENDING',
                },
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: { createdAt: 'desc' },
            }),
            this.prisma.studyGroupJoinRequest.count({
                where: {
                    groupId: args.groupId,
                    status: 'PENDING',
                },
            }),
        ]);

        return {
            items: items.map((r: any) => this.mapJoinRequestFromDb(r)),
            totalCount: total,
            total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async inviteMember(args: {
        tenantId: TenantId;
        groupId: string;
        invitedBy: UserId;
        invitedEmail: string;
    }): Promise<StudyGroupInvite> {
        await this.requireRole(args.groupId, args.invitedBy, ['OWNER', 'ADMIN', 'MODERATOR']);

        const expiresAt = new Date();
        expiresAt.setDate(expiresAt.getDate() + this.inviteExpirationDays);

        const invite = await this.prisma.studyGroupInvite.create({
            data: {
                groupId: args.groupId,
                invitedEmail: args.invitedEmail,
                invitedBy: args.invitedBy,
                expiresAt,
                status: 'PENDING',
            },
        });

        return this.mapInviteFromDb(invite);
    }

    async respondToInvite(args: {
        tenantId: TenantId;
        inviteId: string;
        userId: UserId;
        accepted: boolean;
    }): Promise<StudyGroupInvite> {
        const invite = await this.prisma.studyGroupInvite.findUnique({
            where: { id: args.inviteId },
        });

        if (!invite || invite.status !== 'PENDING') {
            throw new Error('Invite not found or already processed');
        }

        if (new Date() > invite.expiresAt) {
            await this.prisma.studyGroupInvite.update({
                where: { id: args.inviteId },
                data: { status: 'EXPIRED' },
            });
            throw new Error('Invite has expired');
        }

        const updated = await this.prisma.studyGroupInvite.update({
            where: { id: args.inviteId },
            data: {
                status: args.accepted ? 'ACCEPTED' : 'DECLINED',
                acceptedAt: args.accepted ? new Date() : null,
            },
        });

        if (args.accepted) {
            await this.prisma.studyGroupMember.create({
                data: {
                    groupId: invite.groupId,
                    userId: args.userId,
                    role: 'MEMBER',
                    invitedBy: invite.invitedBy,
                },
            });

            await this.prisma.studyGroup.update({
                where: { id: invite.groupId },
                data: { memberCount: { increment: 1 } },
            });
        }

        return this.mapInviteFromDb(updated);
    }

    async listMembers(args: {
        tenantId: TenantId;
        groupId: string;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<StudyGroupMember>> {
        const [items, total] = await Promise.all([
            this.prisma.studyGroupMember.findMany({
                where: { groupId: args.groupId },
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: [
                    { role: 'asc' }, // Owner first, then admin, etc.
                    { joinedAt: 'asc' },
                ],
            }),
            this.prisma.studyGroupMember.count({
                where: { groupId: args.groupId },
            }),
        ]);

        return {
            items: items.map((m: any) => this.mapMemberFromDb(m)),
            totalCount: total,
            total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async updateMemberRole(args: {
        tenantId: TenantId;
        groupId: string;
        memberId: string;
        updatedBy: UserId;
        newRole: StudyGroupRole;
    }): Promise<StudyGroupMember> {
        await this.requireRole(args.groupId, args.updatedBy, ['OWNER']);

        const member = await this.prisma.studyGroupMember.update({
            where: { id: args.memberId },
            data: { role: this.mapRoleToDb(args.newRole) },
        });

        return this.mapMemberFromDb(member);
    }

    async removeMember(args: {
        tenantId: TenantId;
        groupId: string;
        memberId: string;
        removedBy: UserId;
    }): Promise<void> {
        const member = await this.prisma.studyGroupMember.findUnique({
            where: { id: args.memberId },
        });

        if (!member) {
            throw new Error('Member not found');
        }

        if (member.role === 'OWNER') {
            throw new Error('Cannot remove group owner');
        }

        await this.requireRole(args.groupId, args.removedBy, ['OWNER', 'ADMIN', 'MODERATOR']);

        await this.prisma.studyGroupMember.delete({
            where: { id: args.memberId },
        });

        await this.prisma.studyGroup.update({
            where: { id: args.groupId },
            data: { memberCount: { decrement: 1 } },
        });
    }

    async leaveGroup(args: {
        tenantId: TenantId;
        groupId: string;
        userId: UserId;
    }): Promise<void> {
        const member = await this.prisma.studyGroupMember.findFirst({
            where: {
                groupId: args.groupId,
                userId: args.userId,
            },
        });

        if (!member) {
            throw new Error('Not a member of this group');
        }

        if (member.role === 'OWNER') {
            throw new Error('Owner cannot leave. Transfer ownership first.');
        }

        await this.prisma.studyGroupMember.delete({
            where: { id: member.id },
        });

        await this.prisma.studyGroup.update({
            where: { id: args.groupId },
            data: { memberCount: { decrement: 1 } },
        });
    }

    // ---------------------------------------------------------------------------
    // Study Sessions
    // ---------------------------------------------------------------------------

    async scheduleSession(args: {
        tenantId: TenantId;
        groupId: string;
        createdBy: UserId;
        title: string;
        description?: string;
        scheduledAt: Date;
        duration: number;
        type: StudySessionType;
        moduleId?: ModuleId;
        lessonIds?: string[];
        maxParticipants?: number;
    }): Promise<StudySession> {
        await this.requireRole(args.groupId, args.createdBy, ['OWNER', 'ADMIN', 'MODERATOR', 'MEMBER']);

        const session = await this.prisma.studySession.create({
            data: {
                groupId: args.groupId,
                title: args.title,
                description: args.description,
                createdBy: args.createdBy,
                scheduledAt: args.scheduledAt,
                duration: args.duration,
                type: this.mapSessionTypeToDb(args.type),
                moduleId: args.moduleId,
                lessonIds: args.lessonIds ? JSON.stringify(args.lessonIds) : null,
                maxParticipants: args.maxParticipants,
                status: 'SCHEDULED',
            },
        });

        // Auto-RSVP creator
        await this.prisma.sessionRsvp.create({
            data: {
                sessionId: session.id,
                userId: args.createdBy,
                status: 'ATTENDING',
            },
        });

        const group = await this.prisma.studyGroup.findUnique({
            where: { id: args.groupId },
        });

        await this.publishActivity(args.tenantId, {
            type: 'SCHEDULED_SESSION',
            actorId: args.createdBy,
            targetType: 'study_session',
            targetId: session.id,
            targetTitle: args.title,
            studyGroupId: args.groupId,
        });

        return this.mapSessionFromDb(session);
    }

    async listSessions(args: {
        tenantId: TenantId;
        groupId: string;
        includeCompleted?: boolean;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<StudySession>> {
        const where: any = { groupId: args.groupId };

        if (!args.includeCompleted) {
            where.status = { in: ['SCHEDULED', 'IN_PROGRESS'] };
        }

        const [items, total] = await Promise.all([
            this.prisma.studySession.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: { scheduledAt: 'asc' },
            }),
            this.prisma.studySession.count({ where }),
        ]);

        return {
            items: items.map((s: any) => this.mapSessionFromDb(s)),
            totalCount: total,
            total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async rsvpSession(args: {
        tenantId: TenantId;
        sessionId: string;
        userId: UserId;
        status: SessionRsvp['status'];
        note?: string;
    }): Promise<SessionRsvp> {
        const rsvp = await this.prisma.sessionRsvp.upsert({
            where: {
                sessionId_userId: {
                    sessionId: args.sessionId,
                    userId: args.userId,
                },
            },
            create: {
                sessionId: args.sessionId,
                userId: args.userId,
                status: this.mapRsvpStatusToDb(args.status),
                note: args.note,
            },
            update: {
                status: this.mapRsvpStatusToDb(args.status),
                note: args.note,
            },
        });

        return this.mapRsvpFromDb(rsvp);
    }

    async startSession(args: {
        tenantId: TenantId;
        sessionId: string;
        userId: UserId;
    }): Promise<StudySession> {
        const session = await this.prisma.studySession.findUnique({
            where: { id: args.sessionId },
        });

        if (!session) {
            throw new Error('Session not found');
        }

        await this.requireRole(session.groupId, args.userId, ['OWNER', 'ADMIN', 'MODERATOR']);

        const updated = await this.prisma.studySession.update({
            where: { id: args.sessionId },
            data: {
                status: 'IN_PROGRESS',
                startedAt: new Date(),
            },
        });

        return this.mapSessionFromDb(updated);
    }

    async endSession(args: {
        tenantId: TenantId;
        sessionId: string;
        userId: UserId;
        notes?: string;
        recordingUrl?: string;
    }): Promise<StudySession> {
        const session = await this.prisma.studySession.findUnique({
            where: { id: args.sessionId },
        });

        if (!session) {
            throw new Error('Session not found');
        }

        await this.requireRole(session.groupId, args.userId, ['OWNER', 'ADMIN', 'MODERATOR']);

        const updated = await this.prisma.studySession.update({
            where: { id: args.sessionId },
            data: {
                status: 'COMPLETED',
                endedAt: new Date(),
                notes: args.notes,
                recordingUrl: args.recordingUrl,
            },
        });

        await this.publishActivity(args.tenantId, {
            type: 'COMPLETED_SESSION',
            actorId: args.userId,
            targetType: 'study_session',
            targetId: args.sessionId,
            targetTitle: session.title,
            studyGroupId: session.groupId,
        });

        return this.mapSessionFromDb(updated);
    }

    async cancelSession(args: {
        tenantId: TenantId;
        sessionId: string;
        userId: UserId;
        reason?: string;
    }): Promise<StudySession> {
        const session = await this.prisma.studySession.findUnique({
            where: { id: args.sessionId },
        });

        if (!session) {
            throw new Error('Session not found');
        }

        await this.requireRole(session.groupId, args.userId, ['OWNER', 'ADMIN', 'MODERATOR']);

        const updated = await this.prisma.studySession.update({
            where: { id: args.sessionId },
            data: {
                status: 'CANCELLED',
                notes: args.reason,
            },
        });

        return this.mapSessionFromDb(updated);
    }

    // ---------------------------------------------------------------------------
    // Private Helpers
    // ---------------------------------------------------------------------------

    private async requireRole(
        groupId: string,
        userId: string,
        roles: string[]
    ): Promise<void> {
        const member = await this.prisma.studyGroupMember.findFirst({
            where: { groupId, userId },
        });

        if (!member || !roles.includes(member.role)) {
            throw new Error('Insufficient permissions');
        }
    }

    private async publishActivity(
        tenantId: string,
        activity: {
            type: string;
            actorId: string;
            targetType: string;
            targetId: string;
            targetTitle: string;
            studyGroupId?: string;
        }
    ): Promise<void> {
        await this.prisma.socialActivity.create({
            data: {
                tenantId,
                actorId: activity.actorId,
                actorName: '', // Would be populated from user service
                type: activity.type as any,
                targetType: activity.targetType,
                targetId: activity.targetId,
                targetTitle: activity.targetTitle,
                studyGroupId: activity.studyGroupId,
            },
        });

        // Redis usage commented out if not available, or keep if supported
        if (this.redis) {
            await this.redis.publish(
                `social:activity:${tenantId}`,
                JSON.stringify(activity)
            );
        }
    }

    private async createNotification(
        tenantId: string,
        userId: string,
        notification: {
            type: string;
            title: string;
            body: string;
            targetType?: string;
            targetId?: string;
            actorId?: string;
        }
    ): Promise<void> {
        await this.prisma.socialNotification.create({
            data: {
                tenantId,
                userId,
                type: notification.type as any,
                title: notification.title,
                body: notification.body,
                targetType: notification.targetType,
                targetId: notification.targetId,
                actorId: notification.actorId,
            },
        });

        if (this.redis) {
            await this.redis.publish(
                `social:notification:${userId}`,
                JSON.stringify(notification)
            );
        }
    }

    // Mapping helpers
    private mapVisibilityToDb(visibility: StudyGroupVisibility): string {
        const map: Record<StudyGroupVisibility, string> = {
            public: 'PUBLIC',
            private: 'PRIVATE',
            classroom_only: 'CLASSROOM_ONLY',
        };
        return map[visibility];
    }

    private mapVisibilityFromDb(visibility: string): StudyGroupVisibility {
        const map: Record<string, StudyGroupVisibility> = {
            PUBLIC: 'public',
            PRIVATE: 'private',
            CLASSROOM_ONLY: 'classroom_only',
        };
        return map[visibility] ?? 'public';
    }

    private mapRoleToDb(role: StudyGroupRole): string {
        const map: Record<StudyGroupRole, string> = {
            owner: 'OWNER',
            admin: 'ADMIN',
            moderator: 'MODERATOR',
            member: 'MEMBER',
        };
        return map[role];
    }

    private mapRoleFromDb(role: string): StudyGroupRole {
        const map: Record<string, StudyGroupRole> = {
            OWNER: 'owner',
            ADMIN: 'admin',
            MODERATOR: 'moderator',
            MEMBER: 'member',
        };
        return map[role] ?? 'member';
    }

    private mapSessionTypeToDb(type: StudySessionType): string {
        const map: Record<StudySessionType, string> = {
            discussion: 'DISCUSSION',
            review: 'REVIEW',
            quiz_practice: 'QUIZ_PRACTICE',
            video_call: 'VIDEO_CALL',
            collaborative: 'COLLABORATIVE',
        };
        return map[type];
    }

    private mapSessionTypeFromDb(type: string): StudySessionType {
        const map: Record<string, StudySessionType> = {
            DISCUSSION: 'discussion',
            REVIEW: 'review',
            QUIZ_PRACTICE: 'quiz_practice',
            VIDEO_CALL: 'video_call',
            COLLABORATIVE: 'collaborative',
        };
        return map[type] ?? 'discussion';
    }

    private mapRsvpStatusToDb(status: SessionRsvp['status']): string {
        const map: Record<SessionRsvp['status'], string> = {
            attending: 'ATTENDING',
            maybe: 'MAYBE',
            not_attending: 'NOT_ATTENDING',
        };
        return map[status];
    }

    private mapRsvpStatusFromDb(status: string): SessionRsvp['status'] {
        const map: Record<string, SessionRsvp['status']> = {
            ATTENDING: 'attending',
            MAYBE: 'maybe',
            NOT_ATTENDING: 'not_attending',
        };
        return map[status] ?? 'attending';
    }

    private mapGroupFromDb(group: any): StudyGroup {
        return {
            id: group.id,
            tenantId: group.tenantId,
            name: group.name,
            description: group.description,
            coverImageUrl: group.coverImageUrl ?? undefined,
            createdBy: group.createdBy,
            createdAt: group.createdAt,
            updatedAt: group.updatedAt,
            visibility: this.mapVisibilityFromDb(group.visibility),
            maxMembers: group.maxMembers,
            requireApproval: group.requireApproval,
            allowGuestView: group.allowGuestView,
            subjects: JSON.parse(group.subjects || '[]'),
            modules: JSON.parse(group.modules || '[]'),
            memberCount: group.memberCount,
            lastActivityAt: group.lastActivityAt,
            status: group.status.toLowerCase() as any,
            archivedAt: group.archivedAt ?? undefined,
        };
    }

    private mapMemberFromDb(member: any): StudyGroupMember {
        return {
            id: member.id,
            groupId: member.groupId,
            userId: member.userId,
            role: this.mapRoleFromDb(member.role),
            joinedAt: member.joinedAt,
            invitedBy: member.invitedBy ?? undefined,
            messagesCount: member.messagesCount,
            lastActiveAt: member.lastActiveAt,
            notificationsEnabled: member.notificationsEnabled,
            mutedUntil: member.mutedUntil ?? undefined,
        };
    }

    private mapJoinRequestFromDb(request: any): StudyGroupJoinRequest {
        return {
            id: request.id,
            groupId: request.groupId,
            userId: request.userId,
            message: request.message ?? undefined,
            createdAt: request.createdAt,
            status: request.status.toLowerCase() as any,
            reviewedBy: request.reviewedBy ?? undefined,
            reviewedAt: request.reviewedAt ?? undefined,
            rejectionReason: request.rejectionReason ?? undefined,
        };
    }

    private mapInviteFromDb(invite: any): StudyGroupInvite {
        return {
            id: invite.id,
            groupId: invite.groupId,
            invitedEmail: invite.invitedEmail,
            invitedBy: invite.invitedBy,
            createdAt: invite.createdAt,
            expiresAt: invite.expiresAt,
            status: invite.status.toLowerCase() as any,
            acceptedAt: invite.acceptedAt ?? undefined,
        };
    }

    private mapSessionFromDb(session: any): StudySession {
        return {
            id: session.id,
            groupId: session.groupId,
            title: session.title,
            description: session.description ?? undefined,
            createdBy: session.createdBy,
            scheduledAt: session.scheduledAt,
            duration: session.duration,
            timezone: session.timezone,
            type: this.mapSessionTypeFromDb(session.type),
            meetingUrl: session.meetingUrl ?? undefined,
            maxParticipants: session.maxParticipants ?? undefined,
            rsvpDeadline: session.rsvpDeadline ?? undefined,
            moduleId: session.moduleId ?? undefined,
            lessonIds: session.lessonIds ? JSON.parse(session.lessonIds) : undefined,
            agenda: session.agenda ?? undefined,
            attachments: session.attachments ? JSON.parse(session.attachments) : undefined,
            status: session.status.toLowerCase() as any,
            startedAt: session.startedAt ?? undefined,
            endedAt: session.endedAt ?? undefined,
            notes: session.notes ?? undefined,
            recordingUrl: session.recordingUrl ?? undefined,
        };
    }

    private mapRsvpFromDb(rsvp: any): SessionRsvp {
        return {
            sessionId: rsvp.sessionId,
            userId: rsvp.userId,
            status: this.mapRsvpStatusFromDb(rsvp.status),
            respondedAt: rsvp.createdAt,
            note: rsvp.note ?? undefined,
        };
    }
}
