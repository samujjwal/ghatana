/**
 * @doc.type module
 * @doc.purpose Peer Tutoring service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { Redis } from 'ioredis';
import type {
    PeerTutoringService,
    TenantId,
    UserId,
    ModuleId,
    PaginationArgs,
    PaginatedResult,
} from '@ghatana/tutorputor-contracts';

import type {
    TutorProfile,
    TutoringRequest,
    TutoringSession,
    TutoringReview,
} from '@ghatana/tutorputor-contracts/v1/social';

/**
 * Configuration for PeerTutoringServiceImpl
 */
export interface PeerTutoringServiceConfig {
    prisma: PrismaClient;
    redis?: Redis;
    defaultMaxSessionsPerWeek?: number;
    requestExpirationDays?: number;
}

/**
 * Implementation of the Peer Tutoring service.
 */
export class PeerTutoringServiceImpl implements PeerTutoringService {
    private readonly prisma: PrismaClient;
    private readonly redis?: Redis;
    private readonly defaultMaxSessionsPerWeek: number;
    private readonly requestExpirationDays: number;

    constructor(config: PeerTutoringServiceConfig) {
        this.prisma = config.prisma;
        this.redis = config.redis;
        this.defaultMaxSessionsPerWeek = config.defaultMaxSessionsPerWeek ?? 5;
        this.requestExpirationDays = config.requestExpirationDays ?? 7;
    }

    // ---------------------------------------------------------------------------
    // Tutor Profiles
    // ---------------------------------------------------------------------------

    async upsertTutorProfile(args: {
        tenantId: TenantId;
        userId: UserId;
        displayName: string;
        bio: string;
        subjects: string[];
        moduleIds?: ModuleId[];
        sessionTypes: TutoringSession['type'][];
        timezone: string;
        maxSessionsPerWeek?: number;
    }): Promise<TutorProfile> {
        const profile = await this.prisma.tutorProfile.upsert({
            where: {
                tenantId_userId: {
                    tenantId: args.tenantId,
                    userId: args.userId,
                },
            },
            create: {
                tenantId: args.tenantId,
                userId: args.userId,
                displayName: args.displayName,
                bio: args.bio,
                subjects: JSON.stringify(args.subjects),
                modules: args.moduleIds ? JSON.stringify(args.moduleIds) : null,
                sessionTypes: JSON.stringify(args.sessionTypes),
                timezone: args.timezone,
                maxSessionsPerWeek: args.maxSessionsPerWeek ?? this.defaultMaxSessionsPerWeek,
                status: 'ACTIVE',
            },
            update: {
                displayName: args.displayName,
                bio: args.bio,
                subjects: JSON.stringify(args.subjects),
                modules: args.moduleIds ? JSON.stringify(args.moduleIds) : null,
                sessionTypes: JSON.stringify(args.sessionTypes),
                timezone: args.timezone,
                maxSessionsPerWeek: args.maxSessionsPerWeek ?? this.defaultMaxSessionsPerWeek,
            },
        });

        return this.mapProfileFromDb(profile);
    }

    async getTutorProfile(args: {
        tenantId: TenantId;
        userId: UserId;
    }): Promise<TutorProfile | null> {
        const profile = await this.prisma.tutorProfile.findFirst({
            where: {
                tenantId: args.tenantId,
                userId: args.userId,
            },
        });

        return profile ? this.mapProfileFromDb(profile) : null;
    }

    async searchTutors(args: {
        tenantId: TenantId;
        subject?: string;
        moduleId?: ModuleId;
        sessionType?: TutoringSession['type'];
        minRating?: number;
        searchQuery?: string;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<TutorProfile>> {
        const where: any = {
            tenantId: args.tenantId,
            status: 'ACTIVE',
            isAvailable: true,
        };

        if (args.minRating) {
            where.rating = { gte: args.minRating };
        }

        if (args.searchQuery) {
            where.OR = [
                { displayName: { contains: args.searchQuery } },
                { bio: { contains: args.searchQuery } },
            ];
        }

        // Note: subject/moduleId/sessionType filtering would need raw query or client-side filter
        // for JSON array fields in SQLite. For production, consider PostgreSQL with jsonb

        const [items, total] = await Promise.all([
            this.prisma.tutorProfile.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: [
                    { rating: 'desc' },
                    { sessionsCompleted: 'desc' },
                ],
            }),
            this.prisma.tutorProfile.count({ where }),
        ]);

        // Client-side filtering for JSON fields
        let filteredItems = items.map((p: any) => this.mapProfileFromDb(p));

        if (args.subject) {
            filteredItems = filteredItems.filter((p: any) =>
                p.subjects.includes(args.subject!)
            );
        }

        if (args.moduleId) {
            filteredItems = filteredItems.filter((p: any) =>
                p.modules.includes(args.moduleId!)
            );
        }

        if (args.sessionType) {
            filteredItems = filteredItems.filter((p: any) =>
                p.sessionTypes.includes(args.sessionType!)
            );
        }

        return {
            items: filteredItems,
            totalCount: filteredItems.length,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async toggleAvailability(args: {
        tenantId: TenantId;
        userId: UserId;
        isAvailable: boolean;
    }): Promise<TutorProfile> {
        const profile = await this.prisma.tutorProfile.update({
            where: {
                tenantId_userId: {
                    tenantId: args.tenantId,
                    userId: args.userId,
                },
            },
            data: { isAvailable: args.isAvailable },
        });

        return this.mapProfileFromDb(profile);
    }

    // ---------------------------------------------------------------------------
    // Tutoring Requests
    // ---------------------------------------------------------------------------

    async createRequest(args: {
        tenantId: TenantId;
        studentId: UserId;
        subject: string;
        moduleId?: ModuleId;
        lessonId?: string;
        title: string;
        description: string;
        preferredTypes: TutoringSession['type'][];
        preferredTime?: Date;
        estimatedDuration?: number;
        urgency?: 'low' | 'medium' | 'high';
    }): Promise<TutoringRequest> {
        const request = await this.prisma.tutoringRequest.create({
            data: {
                tenantId: args.tenantId,
                studentId: args.studentId,
                subject: args.subject,
                moduleId: args.moduleId,
                lessonId: args.lessonId,
                title: args.title,
                description: args.description,
                preferredTypes: JSON.stringify(args.preferredTypes),
                preferredTime: args.preferredTime,
                estimatedDuration: args.estimatedDuration ?? 60,
                urgency: args.urgency ?? 'medium',
                status: 'OPEN',
            },
        });

        // Notify matching tutors
        const matchingTutors = await this.prisma.tutorProfile.findMany({
            where: {
                tenantId: args.tenantId,
                status: 'ACTIVE',
                isAvailable: true,
                subjects: { contains: args.subject },
            },
            take: 10,
        });

        for (const tutor of matchingTutors) {
            await this.createNotification(args.tenantId, tutor.userId, {
                type: 'TUTORING_REQUEST',
                title: 'New tutoring request',
                body: `A student needs help with ${args.subject}: "${args.title}"`,
                targetType: 'tutoring_request',
                targetId: request.id,
                actorId: args.studentId,
            });
        }

        return this.mapRequestFromDb(request);
    }

    async listOpenRequests(args: {
        tenantId: TenantId;
        subject?: string;
        moduleId?: ModuleId;
        tutorId?: UserId;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<TutoringRequest>> {
        const where: any = {
            tenantId: args.tenantId,
            status: 'OPEN',
        };

        if (args.subject) {
            where.subject = args.subject;
        }

        if (args.moduleId) {
            where.moduleId = args.moduleId;
        }

        const [items, total] = await Promise.all([
            this.prisma.tutoringRequest.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: [
                    { urgency: 'desc' }, // High urgency first
                    { createdAt: 'desc' },
                ],
            }),
            this.prisma.tutoringRequest.count({ where }),
        ]);

        return {
            items: items.map((r: any) => this.mapRequestFromDb(r)),
            totalCount: total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async getMyRequests(args: {
        tenantId: TenantId;
        userId: UserId;
        role: 'student' | 'tutor';
        status?: TutoringRequest['status'];
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<TutoringRequest>> {
        const where: any = {
            tenantId: args.tenantId,
        };

        if (args.role === 'student') {
            where.studentId = args.userId;
        } else {
            where.tutor = { userId: args.userId };
        }

        if (args.status) {
            where.status = this.mapRequestStatusToDb(args.status);
        }

        const [items, total] = await Promise.all([
            this.prisma.tutoringRequest.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: { createdAt: 'desc' },
            }),
            this.prisma.tutoringRequest.count({ where }),
        ]);

        return {
            items: items.map((r: any) => this.mapRequestFromDb(r)),
            totalCount: total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async acceptRequest(args: {
        tenantId: TenantId;
        requestId: string;
        tutorId: UserId;
    }): Promise<TutoringRequest> {
        const tutorProfile = await this.prisma.tutorProfile.findFirst({
            where: {
                tenantId: args.tenantId,
                userId: args.tutorId,
                status: 'ACTIVE',
            },
        });

        if (!tutorProfile) {
            throw new Error('Tutor profile not found or inactive');
        }

        const request = await this.prisma.tutoringRequest.findUnique({
            where: { id: args.requestId },
        });

        if (!request || request.status !== 'OPEN') {
            throw new Error('Request not found or already matched');
        }

        const updated = await this.prisma.tutoringRequest.update({
            where: { id: args.requestId },
            data: {
                tutorId: tutorProfile.id,
                status: 'MATCHED',
                acceptedAt: new Date(),
            },
        });

        // Notify student
        await this.createNotification(args.tenantId, request.studentId, {
            type: 'TUTORING_REQUEST',
            title: 'Tutor found!',
            body: `A tutor has accepted your request for "${request.title}"`,
            targetType: 'tutoring_request',
            targetId: args.requestId,
            actorId: args.tutorId,
        });

        return this.mapRequestFromDb(updated);
    }

    async cancelRequest(args: {
        tenantId: TenantId;
        requestId: string;
        userId: UserId;
        reason?: string;
    }): Promise<TutoringRequest> {
        const request = await this.prisma.tutoringRequest.findUnique({
            where: { id: args.requestId },
        });

        if (!request) {
            throw new Error('Request not found');
        }

        if (request.studentId !== args.userId) {
            throw new Error('Only the student can cancel this request');
        }

        const updated = await this.prisma.tutoringRequest.update({
            where: { id: args.requestId },
            data: {
                status: 'CANCELLED',
                cancelledAt: new Date(),
                cancellationReason: args.reason,
            },
        });

        return this.mapRequestFromDb(updated);
    }

    // ---------------------------------------------------------------------------
    // Sessions
    // ---------------------------------------------------------------------------

    async scheduleSession(args: {
        tenantId: TenantId;
        requestId: string;
        tutorId: UserId;
        scheduledAt: Date;
        duration: number;
        type: TutoringSession['type'];
        meetingUrl?: string;
    }): Promise<TutoringSession> {
        const request = await this.prisma.tutoringRequest.findUnique({
            where: { id: args.requestId },
            include: { tutor: true },
        });

        if (!request || !request.tutor || request.tutor.userId !== args.tutorId) {
            throw new Error('Request not found or tutor mismatch');
        }

        const session = await this.prisma.tutoringSession.create({
            data: {
                tenantId: args.tenantId,
                requestId: args.requestId,
                studentId: request.studentId,
                tutorId: request.tutor.id,
                type: args.type,
                scheduledAt: args.scheduledAt,
                duration: args.duration,
                meetingUrl: args.meetingUrl,
                moduleId: request.moduleId,
                lessonId: request.lessonId,
                status: 'SCHEDULED',
            },
        });

        await this.prisma.tutoringRequest.update({
            where: { id: args.requestId },
            data: { status: 'IN_PROGRESS' },
        });

        // Notify student
        await this.createNotification(args.tenantId, request.studentId, {
            type: 'SESSION_REMINDER',
            title: 'Session scheduled',
            body: `Your tutoring session is scheduled for ${args.scheduledAt.toLocaleString()}`,
            targetType: 'tutoring_session',
            targetId: session.id,
            actorId: args.tutorId,
        });

        return this.mapSessionFromDb(session);
    }

    async getSession(args: {
        tenantId: TenantId;
        sessionId: string;
    }): Promise<TutoringSession> {
        const session = await this.prisma.tutoringSession.findFirst({
            where: {
                id: args.sessionId,
                tenantId: args.tenantId,
            },
        });

        if (!session) {
            throw new Error('Session not found');
        }

        return this.mapSessionFromDb(session);
    }

    async listSessions(args: {
        tenantId: TenantId;
        userId: UserId;
        role?: 'student' | 'tutor';
        status?: TutoringSession['status'];
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<TutoringSession>> {
        const where: any = {
            tenantId: args.tenantId,
        };

        if (args.role === 'student') {
            where.studentId = args.userId;
        } else if (args.role === 'tutor') {
            where.tutor = { userId: args.userId };
        } else {
            where.OR = [
                { studentId: args.userId },
                { tutor: { userId: args.userId } },
            ];
        }

        if (args.status) {
            where.status = this.mapSessionStatusToDb(args.status);
        }

        const [items, total] = await Promise.all([
            this.prisma.tutoringSession.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: { scheduledAt: 'desc' },
            }),
            this.prisma.tutoringSession.count({ where }),
        ]);

        return {
            items: items.map((s: any) => this.mapSessionFromDb(s)),
            totalCount: total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async startSession(args: {
        tenantId: TenantId;
        sessionId: string;
        userId: UserId;
    }): Promise<TutoringSession> {
        const session = await this.prisma.tutoringSession.findFirst({
            where: { id: args.sessionId },
            include: { tutor: true },
        });

        if (!session) {
            throw new Error('Session not found');
        }

        // Only tutor can start session
        if (session.tutor.userId !== args.userId) {
            throw new Error('Only the tutor can start the session');
        }

        const updated = await this.prisma.tutoringSession.update({
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
    }): Promise<TutoringSession> {
        const session = await this.prisma.tutoringSession.findFirst({
            where: { id: args.sessionId },
            include: { tutor: true },
        });

        if (!session) {
            throw new Error('Session not found');
        }

        const now = new Date();
        const actualDuration = session.startedAt
            ? Math.round((now.getTime() - session.startedAt.getTime()) / 60000)
            : session.duration;

        const updated = await this.prisma.tutoringSession.update({
            where: { id: args.sessionId },
            data: {
                status: 'COMPLETED',
                endedAt: now,
                actualDuration,
                notes: args.notes,
            },
        });

        // Update tutor stats
        await this.prisma.tutorProfile.update({
            where: { id: session.tutorId },
            data: {
                sessionsCompleted: { increment: 1 },
                totalHelpedStudents: { increment: 1 },
            },
        });

        // Update request status
        await this.prisma.tutoringRequest.update({
            where: { id: session.requestId },
            data: {
                status: 'COMPLETED',
                completedAt: now,
            },
        });

        return this.mapSessionFromDb(updated);
    }

    async reportNoShow(args: {
        tenantId: TenantId;
        sessionId: string;
        reporterId: UserId;
    }): Promise<TutoringSession> {
        const session = await this.prisma.tutoringSession.update({
            where: { id: args.sessionId },
            data: {
                status: 'NO_SHOW',
                endedAt: new Date(),
            },
        });

        return this.mapSessionFromDb(session);
    }

    // ---------------------------------------------------------------------------
    // Reviews
    // ---------------------------------------------------------------------------

    async submitReview(args: {
        tenantId: TenantId;
        sessionId: string;
        reviewerId: UserId;
        rating: number;
        helpfulness: number;
        communication: number;
        knowledge: number;
        comment?: string;
    }): Promise<TutoringReview> {
        const session = await this.prisma.tutoringSession.findFirst({
            where: { id: args.sessionId },
            include: { tutor: true },
        });

        if (!session) {
            throw new Error('Session not found');
        }

        if (session.studentId !== args.reviewerId) {
            throw new Error('Only the student can review this session');
        }

        const review = await this.prisma.tutoringReview.create({
            data: {
                sessionId: args.sessionId,
                tutorId: session.tutorId,
                reviewerId: args.reviewerId,
                rating: args.rating,
                helpfulness: args.helpfulness,
                communication: args.communication,
                knowledge: args.knowledge,
                comment: args.comment,
            },
        });

        // Update tutor's average rating
        const allReviews = await this.prisma.tutoringReview.aggregate({
            where: {
                tutorId: session.tutorId,
                isVisible: true,
            },
            _avg: { rating: true },
            _count: { rating: true },
        });

        await this.prisma.tutorProfile.update({
            where: { id: session.tutorId },
            data: {
                rating: allReviews._avg.rating ?? 0,
                reviewCount: allReviews._count.rating,
            },
        });

        // Notify tutor
        await this.createNotification(args.tenantId, session.tutor.userId, {
            type: 'REVIEW_RECEIVED',
            title: 'New review received',
            body: `You received a ${args.rating}-star review`,
            targetType: 'tutoring_review',
            targetId: review.id,
            actorId: args.reviewerId,
        });

        return this.mapReviewFromDb(review);
    }

    async respondToReview(args: {
        tenantId: TenantId;
        reviewId: string;
        tutorId: UserId;
        response: string;
    }): Promise<TutoringReview> {
        const review = await this.prisma.tutoringReview.findUnique({
            where: { id: args.reviewId },
            include: { tutor: true },
        });

        if (!review || review.tutor.userId !== args.tutorId) {
            throw new Error('Review not found or not authorized');
        }

        const updated = await this.prisma.tutoringReview.update({
            where: { id: args.reviewId },
            data: {
                response: args.response,
                respondedAt: new Date(),
            },
        });

        return this.mapReviewFromDb(updated);
    }

    async listTutorReviews(args: {
        tenantId: TenantId;
        tutorId: UserId;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<TutoringReview>> {
        const tutorProfile = await this.prisma.tutorProfile.findFirst({
            where: {
                tenantId: args.tenantId,
                userId: args.tutorId,
            },
        });

        if (!tutorProfile) {
            throw new Error('Tutor profile not found');
        }

        const where = {
            tutorId: tutorProfile.id,
            isVisible: true,
        };

        const [items, total] = await Promise.all([
            this.prisma.tutoringReview.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: { createdAt: 'desc' },
            }),
            this.prisma.tutoringReview.count({ where }),
        ]);

        return {
            items: items.map((r: any) => this.mapReviewFromDb(r)),
            totalCount: total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    // ---------------------------------------------------------------------------
    // Private Helpers
    // ---------------------------------------------------------------------------

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
    private mapRequestStatusToDb(status: TutoringRequest['status']): string {
        const map: Record<TutoringRequest['status'], string> = {
            open: 'OPEN',
            matched: 'MATCHED',
            in_progress: 'IN_PROGRESS',
            completed: 'COMPLETED',
            cancelled: 'CANCELLED',
            expired: 'EXPIRED',
        };
        return map[status];
    }

    private mapRequestStatusFromDb(status: string): TutoringRequest['status'] {
        const map: Record<string, TutoringRequest['status']> = {
            OPEN: 'open',
            MATCHED: 'matched',
            IN_PROGRESS: 'in_progress',
            COMPLETED: 'completed',
            CANCELLED: 'cancelled',
            EXPIRED: 'expired',
        };
        return map[status] ?? 'open';
    }

    private mapSessionStatusToDb(status: TutoringSession['status']): string {
        const map: Record<TutoringSession['status'], string> = {
            scheduled: 'SCHEDULED',
            in_progress: 'IN_PROGRESS',
            completed: 'COMPLETED',
            no_show: 'NO_SHOW',
            cancelled: 'CANCELLED',
        };
        return map[status];
    }

    private mapSessionStatusFromDb(status: string): TutoringSession['status'] {
        const map: Record<string, TutoringSession['status']> = {
            SCHEDULED: 'scheduled',
            IN_PROGRESS: 'in_progress',
            COMPLETED: 'completed',
            NO_SHOW: 'no_show',
            CANCELLED: 'cancelled',
        };
        return map[status] ?? 'scheduled';
    }

    private mapProfileFromDb(profile: any): TutorProfile {
        return {
            id: profile.id,
            userId: profile.userId,
            tenantId: profile.tenantId,
            displayName: profile.displayName,
            bio: profile.bio,
            avatarUrl: profile.avatarUrl ?? undefined,
            subjects: JSON.parse(profile.subjects || '[]'),
            modules: JSON.parse(profile.modules || '[]'),
            qualifications: profile.qualifications
                ? JSON.parse(profile.qualifications)
                : undefined,
            isAvailable: profile.isAvailable,
            availabilitySchedule: profile.availabilitySchedule
                ? JSON.parse(profile.availabilitySchedule)
                : undefined,
            timezone: profile.timezone,
            responseTime: profile.responseTime,
            sessionTypes: JSON.parse(profile.sessionTypes || '[]'),
            maxSessionsPerWeek: profile.maxSessionsPerWeek,
            pricePerHour: profile.pricePerHour ?? undefined,
            rating: profile.rating,
            reviewCount: profile.reviewCount,
            sessionsCompleted: profile.sessionsCompleted,
            totalHelpedStudents: profile.totalHelpedStudents,
            status: profile.status.toLowerCase() as any,
            verifiedAt: profile.verifiedAt ?? undefined,
            verifiedBy: profile.verifiedBy ?? undefined,
            createdAt: profile.createdAt,
            updatedAt: profile.updatedAt,
        };
    }

    private mapRequestFromDb(request: any): TutoringRequest {
        return {
            id: request.id,
            studentId: request.studentId,
            tutorId: request.tutorId ?? undefined,
            tenantId: request.tenantId,
            subject: request.subject,
            moduleId: request.moduleId ?? undefined,
            lessonId: request.lessonId ?? undefined,
            title: request.title,
            description: request.description,
            attachments: request.attachments
                ? JSON.parse(request.attachments)
                : undefined,
            preferredTypes: JSON.parse(request.preferredTypes || '[]'),
            preferredTime: request.preferredTime ?? undefined,
            estimatedDuration: request.estimatedDuration,
            urgency: request.urgency as any,
            status: this.mapRequestStatusFromDb(request.status),
            createdAt: request.createdAt,
            updatedAt: request.updatedAt,
            acceptedAt: request.acceptedAt ?? undefined,
            completedAt: request.completedAt ?? undefined,
            cancelledAt: request.cancelledAt ?? undefined,
            cancellationReason: request.cancellationReason ?? undefined,
        };
    }

    private mapSessionFromDb(session: any): TutoringSession {
        return {
            id: session.id,
            requestId: session.requestId,
            studentId: session.studentId,
            tutorId: session.tutorId,
            tenantId: session.tenantId,
            type: session.type as any,
            scheduledAt: session.scheduledAt,
            duration: session.duration,
            meetingUrl: session.meetingUrl ?? undefined,
            moduleId: session.moduleId ?? undefined,
            lessonId: session.lessonId ?? undefined,
            notes: session.notes ?? undefined,
            sharedResources: session.sharedResources
                ? JSON.parse(session.sharedResources)
                : undefined,
            status: this.mapSessionStatusFromDb(session.status),
            startedAt: session.startedAt ?? undefined,
            endedAt: session.endedAt ?? undefined,
            actualDuration: session.actualDuration ?? undefined,
            recordingUrl: session.recordingUrl ?? undefined,
            transcriptUrl: session.transcriptUrl ?? undefined,
            createdAt: session.createdAt,
            updatedAt: session.updatedAt,
        };
    }

    private mapReviewFromDb(review: any): TutoringReview {
        return {
            id: review.id,
            sessionId: review.sessionId,
            reviewerId: review.reviewerId,
            revieweeId: review.tutorId,
            rating: review.rating,
            helpfulness: review.helpfulness,
            communication: review.communication,
            knowledge: review.knowledge,
            comment: review.comment ?? undefined,
            privateNote: review.privateNote ?? undefined,
            response: review.response ?? undefined,
            respondedAt: review.respondedAt ?? undefined,
            isVisible: review.isVisible,
            moderatedBy: review.moderatedBy ?? undefined,
            moderatedAt: review.moderatedAt ?? undefined,
            createdAt: review.createdAt,
            updatedAt: review.updatedAt,
        };
    }
}
