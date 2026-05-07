/**
 * @doc.type module
 * @doc.purpose Peer Tutoring service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { Redis } from "ioredis";
import type {
  PeerTutoringService,
  TenantId,
  UserId,
  ModuleId,
  PaginationArgs,
  PaginatedResult,
} from "@tutorputor/contracts";

import type {
  TutorProfile,
  TutoringRequest,
  TutoringSession,
  TutoringReview,
} from "@tutorputor/contracts/v1/social";
import { createSocialNotification } from "../../notifications/delivery.js";

interface TutorProfileRow {
  id: string;
  userId: string;
  tenantId: string;
  displayName: string;
  bio: string;
  avatarUrl?: string | null;
  subjects?: string | null;
  modules?: string | null;
  qualifications?: string | null;
  isAvailable: boolean;
  availabilitySchedule?: string | null;
  timezone: string;
  responseTime: string;
  sessionTypes?: string | null;
  maxSessionsPerWeek: number;
  pricePerHour?: number | null;
  rating: number;
  reviewCount: number;
  sessionsCompleted: number;
  totalHelpedStudents: number;
  status: string;
  verifiedAt?: Date | null;
  verifiedBy?: string | null;
  createdAt: Date;
  updatedAt: Date;
}

interface TutoringRequestRow {
  id: string;
  studentId: string;
  tutorId?: string | null;
  tutor?: TutorProfileRow | null;
  tenantId: string;
  subject: string;
  moduleId?: string | null;
  lessonId?: string | null;
  title: string;
  description: string;
  attachments?: string | null;
  preferredTypes?: string | null;
  preferredTime?: Date | null;
  estimatedDuration: number;
  urgency: string;
  status: string;
  createdAt: Date;
  updatedAt: Date;
  acceptedAt?: Date | null;
  completedAt?: Date | null;
  cancelledAt?: Date | null;
  cancellationReason?: string | null;
}

interface TutoringSessionRow {
  id: string;
  requestId: string;
  studentId: string;
  tutorId: string;
  tutor?: TutorProfileRow | null;
  tenantId: string;
  type: string;
  scheduledAt: Date;
  duration: number;
  meetingUrl?: string | null;
  moduleId?: string | null;
  lessonId?: string | null;
  notes?: string | null;
  sharedResources?: string | null;
  status: string;
  startedAt?: Date | null;
  endedAt?: Date | null;
  actualDuration?: number | null;
  recordingUrl?: string | null;
  transcriptUrl?: string | null;
  createdAt: Date;
  updatedAt: Date;
}

interface TutoringReviewRow {
  id: string;
  sessionId: string;
  reviewerId: string;
  tutorId: string;
  tutor?: TutorProfileRow | null;
  rating: number;
  helpfulness: number;
  communication: number;
  knowledge: number;
  comment?: string | null;
  privateNote?: string | null;
  response?: string | null;
  respondedAt?: Date | null;
  isVisible: boolean;
  moderatedBy?: string | null;
  moderatedAt?: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

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

  constructor(config: PeerTutoringServiceConfig) {
    this.prisma = config.prisma;
    if (config.redis) {
      this.redis = config.redis;
    }
    this.defaultMaxSessionsPerWeek = config.defaultMaxSessionsPerWeek ?? 5;
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
    sessionTypes: TutoringSession["type"][];
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
        maxSessionsPerWeek:
          args.maxSessionsPerWeek ?? this.defaultMaxSessionsPerWeek,
        status: "ACTIVE",
      },
      update: {
        displayName: args.displayName,
        bio: args.bio,
        subjects: JSON.stringify(args.subjects),
        modules: args.moduleIds ? JSON.stringify(args.moduleIds) : null,
        sessionTypes: JSON.stringify(args.sessionTypes),
        timezone: args.timezone,
        maxSessionsPerWeek:
          args.maxSessionsPerWeek ?? this.defaultMaxSessionsPerWeek,
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
    sessionType?: TutoringSession["type"];
    minRating?: number;
    searchQuery?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutorProfile>> {
    const where: Record<string, unknown> = {
      tenantId: args.tenantId,
      status: "ACTIVE",
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
        orderBy: [{ rating: "desc" }, { sessionsCompleted: "desc" }],
      }),
      this.prisma.tutorProfile.count({ where }),
    ]);

    // Client-side filtering for JSON fields
    let filteredItems = items.map((p) => this.mapProfileFromDb(p));

    if (args.subject) {
      filteredItems = filteredItems.filter((p) =>
        p.subjects.includes(args.subject!),
      );
    }

    if (args.moduleId) {
      filteredItems = filteredItems.filter((p) =>
        p.modules.includes(args.moduleId!),
      );
    }

    if (args.sessionType) {
      filteredItems = filteredItems.filter((p) =>
        p.sessionTypes.includes(args.sessionType!),
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
    preferredTypes: TutoringSession["type"][];
    preferredTime?: Date;
    estimatedDuration?: number;
    urgency?: "low" | "medium" | "high";
  }): Promise<TutoringRequest> {
    const request = await this.prisma.tutoringRequest.create({
      data: {
        tenantId: args.tenantId,
        studentId: args.studentId,
        subject: args.subject,
        ...(args.moduleId ? { moduleId: args.moduleId } : {}),
        ...(args.lessonId ? { lessonId: args.lessonId } : {}),
        title: args.title,
        description: args.description,
        preferredTypes: JSON.stringify(args.preferredTypes),
        ...(args.preferredTime ? { preferredTime: args.preferredTime } : {}),
        estimatedDuration: args.estimatedDuration ?? 60,
        urgency: args.urgency ?? "medium",
        status: "OPEN",
      },
    });

    // Notify matching tutors
    const matchingTutors = await this.prisma.tutorProfile.findMany({
      where: {
        tenantId: args.tenantId,
        status: "ACTIVE",
        isAvailable: true,
        subjects: { contains: args.subject },
      },
      take: 10,
    });

    for (const tutor of matchingTutors) {
      await this.createNotification(args.tenantId, tutor.userId, {
        type: "TUTORING_REQUEST",
        title: "New tutoring request",
        body: `A student needs help with ${args.subject}: "${args.title}"`,
        targetType: "tutoring_request",
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
    const where: Record<string, unknown> = {
      tenantId: args.tenantId,
      status: "OPEN",
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
          { urgency: "desc" }, // High urgency first
          { createdAt: "desc" },
        ],
      }),
      this.prisma.tutoringRequest.count({ where }),
    ]);

    return {
      items: items.map((r) => this.mapRequestFromDb(r)),
      totalCount: total,
      hasMore: (args.pagination.offset ?? 0) + items.length < total,
    };
  }

  async getMyRequests(args: {
    tenantId: TenantId;
    userId: UserId;
    role: "student" | "tutor";
    status?: TutoringRequest["status"];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutoringRequest>> {
    const where: Record<string, unknown> = {
      tenantId: args.tenantId,
    };

    if (args.role === "student") {
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
        orderBy: { createdAt: "desc" },
      }),
      this.prisma.tutoringRequest.count({ where }),
    ]);

    return {
      items: items.map((r) => this.mapRequestFromDb(r)),
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
        status: "ACTIVE",
      },
    });

    if (!tutorProfile) {
      throw new Error("Tutor profile not found or inactive");
    }

    const request = await this.requireRequest(args.tenantId, args.requestId);

    if (request.status !== "OPEN") {
      throw new Error("Request not found or already matched");
    }

    const updated = await this.prisma.tutoringRequest.update({
      where: { id: args.requestId },
      data: {
        tutorId: tutorProfile.id,
        status: "MATCHED",
        acceptedAt: new Date(),
      },
    });

    // Notify student
    await this.createNotification(args.tenantId, request.studentId, {
      type: "TUTORING_REQUEST",
      title: "Tutor found!",
      body: `A tutor has accepted your request for "${request.title}"`,
      targetType: "tutoring_request",
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
    const request = await this.requireRequest(args.tenantId, args.requestId);

    if (request.studentId !== args.userId) {
      throw new Error("Only the student can cancel this request");
    }

    const updated = await this.prisma.tutoringRequest.update({
      where: { id: args.requestId },
      data: {
        status: "CANCELLED",
        cancelledAt: new Date(),
        ...(args.reason ? { cancellationReason: args.reason } : {}),
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
    type: TutoringSession["type"];
    meetingUrl?: string;
  }): Promise<TutoringSession> {
    const request = await this.requireRequest(
      args.tenantId,
      args.requestId,
      true,
    );

    if (!request.tutor || request.tutor.userId !== args.tutorId) {
      throw new Error("Request not found or tutor mismatch");
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
        ...(args.meetingUrl ? { meetingUrl: args.meetingUrl } : {}),
        ...(request.moduleId ? { moduleId: request.moduleId } : {}),
        ...(request.lessonId ? { lessonId: request.lessonId } : {}),
        status: "SCHEDULED",
      },
    });

    await this.prisma.tutoringRequest.update({
      where: { id: args.requestId },
      data: { status: "IN_PROGRESS" },
    });

    // Notify student
    await this.createNotification(args.tenantId, request.studentId, {
      type: "SESSION_REMINDER",
      title: "Session scheduled",
      body: `Your tutoring session is scheduled for ${args.scheduledAt.toLocaleString()}`,
      targetType: "tutoring_session",
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
      throw new Error("Session not found");
    }

    return this.mapSessionFromDb(session);
  }

  async listSessions(args: {
    tenantId: TenantId;
    userId: UserId;
    role?: "student" | "tutor";
    status?: TutoringSession["status"];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutoringSession>> {
    const where: Record<string, unknown> = {
      tenantId: args.tenantId,
    };

    if (args.role === "student") {
      where.studentId = args.userId;
    } else if (args.role === "tutor") {
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
        orderBy: { scheduledAt: "desc" },
      }),
      this.prisma.tutoringSession.count({ where }),
    ]);

    return {
      items: items.map((s) => this.mapSessionFromDb(s)),
      totalCount: total,
      hasMore: (args.pagination.offset ?? 0) + items.length < total,
    };
  }

  async startSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
  }): Promise<TutoringSession> {
    const session = await this.requireSession(
      args.tenantId,
      args.sessionId,
      true,
    );

    // Only tutor can start session
    if (session.tutor?.userId !== args.userId) {
      throw new Error("Only the tutor can start the session");
    }

    const updated = await this.prisma.tutoringSession.update({
      where: { id: args.sessionId },
      data: {
        status: "IN_PROGRESS",
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
    const session = await this.requireSession(
      args.tenantId,
      args.sessionId,
      true,
    );

    const now = new Date();
    const actualDuration = session.startedAt
      ? Math.round((now.getTime() - session.startedAt.getTime()) / 60000)
      : session.duration;

    const updated = await this.prisma.tutoringSession.update({
      where: { id: args.sessionId },
      data: {
        status: "COMPLETED",
        endedAt: now,
        actualDuration,
        ...(args.notes ? { notes: args.notes } : {}),
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
        status: "COMPLETED",
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
    await this.requireSession(args.tenantId, args.sessionId);

    const session = await this.prisma.tutoringSession.update({
      where: { id: args.sessionId },
      data: {
        status: "NO_SHOW",
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
    const session = await this.requireSession(
      args.tenantId,
      args.sessionId,
      true,
    );

    if (session.studentId !== args.reviewerId) {
      throw new Error("Only the student can review this session");
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
        ...(args.comment ? { comment: args.comment } : {}),
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
    await this.createNotification(
      args.tenantId,
      session.tutor?.userId ?? session.tutorId,
      {
        type: "REVIEW_RECEIVED",
        title: "New review received",
        body: `You received a ${args.rating}-star review`,
        targetType: "tutoring_review",
        targetId: review.id,
        actorId: args.reviewerId,
      },
    );

    return this.mapReviewFromDb(review);
  }

  async respondToReview(args: {
    tenantId: TenantId;
    reviewId: string;
    tutorId: UserId;
    response: string;
  }): Promise<TutoringReview> {
    const review = await this.requireReview(args.tenantId, args.reviewId, true);

    if (review.tutor?.userId !== args.tutorId) {
      throw new Error("Review not found or not authorized");
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
      throw new Error("Tutor profile not found");
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
        orderBy: { createdAt: "desc" },
      }),
      this.prisma.tutoringReview.count({ where }),
    ]);

    return {
      items: items.map((r) => this.mapReviewFromDb(r)),
      totalCount: total,
      hasMore: (args.pagination.offset ?? 0) + items.length < total,
    };
  }

  // ---------------------------------------------------------------------------
  // Private Helpers
  // ---------------------------------------------------------------------------

  private async requireRequest(
    tenantId: TenantId,
    requestId: string,
    includeTutor = false,
  ): Promise<TutoringRequestRow> {
    const request = await this.prisma.tutoringRequest.findUnique({
      where: { id: requestId },
      ...(includeTutor ? { include: { tutor: true } } : {}),
    });

    if (!request || request.tenantId !== tenantId) {
      throw new Error("Request not found");
    }

    return request as unknown as TutoringRequestRow;
  }

  private async requireSession(
    tenantId: TenantId,
    sessionId: string,
    includeTutor = false,
  ): Promise<TutoringSessionRow> {
    const session = await this.prisma.tutoringSession.findFirst({
      where: { id: sessionId, tenantId },
      ...(includeTutor ? { include: { tutor: true } } : {}),
    });

    if (!session) {
      throw new Error("Session not found");
    }

    return session as unknown as TutoringSessionRow;
  }

  private async requireReview(
    tenantId: TenantId,
    reviewId: string,
    includeTutor = false,
  ): Promise<TutoringReviewRow> {
    const review = await this.prisma.tutoringReview.findUnique({
      where: { id: reviewId },
      ...(includeTutor ? { include: { tutor: true } } : {}),
    });

    if (!review) {
      throw new Error("Review not found or not authorized");
    }

    const tutorProfile = await this.prisma.tutorProfile.findFirst({
      where: {
        id: review.tutorId,
        tenantId,
      },
    });

    if (!tutorProfile) {
      throw new Error("Review not found or not authorized");
    }

    return review as unknown as TutoringReviewRow;
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
    },
  ): Promise<void> {
    await createSocialNotification(
      this.prisma as unknown as Parameters<typeof createSocialNotification>[0],
      {
        tenantId,
        userId,
        type: notification.type,
        title: notification.title,
        body: notification.body,
        ...(notification.targetType
          ? { targetType: notification.targetType }
          : {}),
        ...(notification.targetId ? { targetId: notification.targetId } : {}),
        ...(notification.actorId ? { actorId: notification.actorId } : {}),
      },
    );

    if (this.redis) {
      await this.redis.publish(
        `social:notification:${userId}`,
        JSON.stringify(notification),
      );
    }
  }

  // Mapping helpers
  private mapRequestStatusToDb(status: TutoringRequest["status"]): string {
    const map: Record<TutoringRequest["status"], string> = {
      open: "OPEN",
      matched: "MATCHED",
      in_progress: "IN_PROGRESS",
      completed: "COMPLETED",
      cancelled: "CANCELLED",
      expired: "EXPIRED",
    };
    return map[status];
  }

  private mapRequestStatusFromDb(status: string): TutoringRequest["status"] {
    const map: Record<string, TutoringRequest["status"]> = {
      OPEN: "open",
      MATCHED: "matched",
      IN_PROGRESS: "in_progress",
      COMPLETED: "completed",
      CANCELLED: "cancelled",
      EXPIRED: "expired",
    };
    return map[status] ?? "open";
  }

  private mapSessionStatusToDb(status: TutoringSession["status"]): string {
    const map: Record<TutoringSession["status"], string> = {
      scheduled: "SCHEDULED",
      in_progress: "IN_PROGRESS",
      completed: "COMPLETED",
      no_show: "NO_SHOW",
      cancelled: "CANCELLED",
    };
    return map[status];
  }

  private mapSessionStatusFromDb(status: string): TutoringSession["status"] {
    const map: Record<string, TutoringSession["status"]> = {
      SCHEDULED: "scheduled",
      IN_PROGRESS: "in_progress",
      COMPLETED: "completed",
      NO_SHOW: "no_show",
      CANCELLED: "cancelled",
    };
    return map[status] ?? "scheduled";
  }

  private mapTutorStatusFromDb(status: string): TutorProfile["status"] {
    const map: Record<string, TutorProfile["status"]> = {
      ACTIVE: "active",
      PAUSED: "paused",
      INACTIVE: "inactive",
    };
    return map[status] ?? "inactive";
  }

  private mapUrgencyFromDb(urgency: string): TutoringRequest["urgency"] {
    const validUrgencies: TutoringRequest["urgency"][] = ["low", "medium", "high"];
    const normalizedUrgency = urgency.toLowerCase();
    return validUrgencies.includes(normalizedUrgency as TutoringRequest["urgency"])
      ? (normalizedUrgency as TutoringRequest["urgency"])
      : "medium";
  }

  private mapSessionTypeFromDb(type: string): TutoringSession["type"] {
    const validTypes: TutoringSession["type"][] = [
      "text_chat",
      "video_call",
      "screen_share",
      "collaborative_whiteboard",
    ];
    return validTypes.includes(type as TutoringSession["type"])
      ? (type as TutoringSession["type"])
      : "text_chat";
  }

  private mapProfileFromDb(profile: TutorProfileRow): TutorProfile {
    const mapped: TutorProfile = {
      id: profile.id,
      userId: profile.userId,
      tenantId: profile.tenantId,
      displayName: profile.displayName,
      bio: profile.bio,
      subjects: JSON.parse(profile.subjects || "[]"),
      modules: JSON.parse(profile.modules || "[]"),
      isAvailable: profile.isAvailable,
      timezone: profile.timezone,
      responseTime: profile.responseTime,
      sessionTypes: JSON.parse(profile.sessionTypes || "[]"),
      maxSessionsPerWeek: profile.maxSessionsPerWeek,
      rating: profile.rating,
      reviewCount: profile.reviewCount,
      sessionsCompleted: profile.sessionsCompleted,
      totalHelpedStudents: profile.totalHelpedStudents,
      status: this.mapTutorStatusFromDb(profile.status),
      createdAt: profile.createdAt,
      updatedAt: profile.updatedAt,
    };
    if (profile.avatarUrl) {
      mapped.avatarUrl = profile.avatarUrl;
    }
    if (profile.qualifications) {
      mapped.qualifications = JSON.parse(profile.qualifications) as NonNullable<TutorProfile["qualifications"]>;
    }
    if (profile.availabilitySchedule) {
      mapped.availabilitySchedule = JSON.parse(profile.availabilitySchedule) as NonNullable<TutorProfile["availabilitySchedule"]>;
    }
    if (typeof profile.pricePerHour === "number") {
      mapped.pricePerHour = profile.pricePerHour;
    }
    if (profile.verifiedAt) {
      mapped.verifiedAt = profile.verifiedAt;
    }
    if (profile.verifiedBy) {
      mapped.verifiedBy = profile.verifiedBy;
    }
    return mapped;
  }

  private mapRequestFromDb(request: TutoringRequestRow): TutoringRequest {
    const mapped: TutoringRequest = {
      id: request.id,
      studentId: request.studentId as UserId,
      tenantId: request.tenantId as TenantId,
      subject: request.subject,
      title: request.title,
      description: request.description,
      preferredTypes: JSON.parse(request.preferredTypes || "[]"),
      estimatedDuration: request.estimatedDuration,
      urgency: this.mapUrgencyFromDb(request.urgency),
      status: this.mapRequestStatusFromDb(request.status),
      createdAt: request.createdAt,
      updatedAt: request.updatedAt,
    };
    if (request.tutorId) {
      mapped.tutorId = request.tutorId as UserId;
    }
    if (request.moduleId) {
      mapped.moduleId = request.moduleId as ModuleId;
    }
    if (request.lessonId) {
      mapped.lessonId = request.lessonId;
    }
    if (request.attachments) {
      mapped.attachments = JSON.parse(request.attachments) as NonNullable<TutoringRequest["attachments"]>;
    }
    if (request.preferredTime) {
      mapped.preferredTime = request.preferredTime;
    }
    if (request.acceptedAt) {
      mapped.acceptedAt = request.acceptedAt;
    }
    if (request.completedAt) {
      mapped.completedAt = request.completedAt;
    }
    if (request.cancelledAt) {
      mapped.cancelledAt = request.cancelledAt;
    }
    if (request.cancellationReason) {
      mapped.cancellationReason = request.cancellationReason;
    }
    return mapped;
  }

  private mapSessionFromDb(session: TutoringSessionRow): TutoringSession {
    const mapped: TutoringSession = {
      id: session.id,
      requestId: session.requestId,
      studentId: session.studentId as UserId,
      tutorId: session.tutorId,
      tenantId: session.tenantId as TenantId,
      type: this.mapSessionTypeFromDb(session.type),
      scheduledAt: session.scheduledAt,
      duration: session.duration,
      status: this.mapSessionStatusFromDb(session.status),
      createdAt: session.createdAt,
      updatedAt: session.updatedAt,
    };
    if (session.meetingUrl) {
      mapped.meetingUrl = session.meetingUrl;
    }
    if (session.moduleId) {
      mapped.moduleId = session.moduleId as ModuleId;
    }
    if (session.lessonId) {
      mapped.lessonId = session.lessonId;
    }
    if (session.notes) {
      mapped.notes = session.notes;
    }
    if (session.sharedResources) {
      mapped.sharedResources = JSON.parse(session.sharedResources) as NonNullable<TutoringSession["sharedResources"]>;
    }
    if (session.startedAt) {
      mapped.startedAt = session.startedAt;
    }
    if (session.endedAt) {
      mapped.endedAt = session.endedAt;
    }
    if (typeof session.actualDuration === "number") {
      mapped.actualDuration = session.actualDuration;
    }
    if (session.recordingUrl) {
      mapped.recordingUrl = session.recordingUrl;
    }
    if (session.transcriptUrl) {
      mapped.transcriptUrl = session.transcriptUrl;
    }
    return mapped;
  }

  private mapReviewFromDb(review: TutoringReviewRow): TutoringReview {
    const mapped: TutoringReview = {
      id: review.id,
      sessionId: review.sessionId,
      reviewerId: review.reviewerId as UserId,
      revieweeId: review.tutorId,
      rating: review.rating,
      helpfulness: review.helpfulness,
      communication: review.communication,
      knowledge: review.knowledge,
      isVisible: review.isVisible,
      createdAt: review.createdAt,
      updatedAt: review.updatedAt,
    };
    if (review.comment) {
      mapped.comment = review.comment;
    }
    if (review.privateNote) {
      mapped.privateNote = review.privateNote;
    }
    if (review.response) {
      mapped.response = review.response;
    }
    if (review.respondedAt) {
      mapped.respondedAt = review.respondedAt;
    }
    if (review.moderatedBy) {
      mapped.moderatedBy = review.moderatedBy;
    }
    if (review.moderatedAt) {
      mapped.moderatedAt = review.moderatedAt;
    }
    return mapped;
  }
}
