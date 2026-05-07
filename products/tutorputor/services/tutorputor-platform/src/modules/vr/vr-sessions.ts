// @ts-nocheck
/**
 * @doc.type service
 * @doc.purpose VR Session management service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import { randomUUID } from "crypto";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type {
  VRSessionService,
  TenantId,
  UserId,
  PaginatedResult,
  PaginationArgs,
} from "@tutorputor/contracts/v1";
import type {
  VRSession,
  VRSessionId,
  VRSessionStatus,
  VRLabId,
  VRInteractionLog,
  VRPerformanceMetrics,
  StartVRSessionRequest,
  UpdateVRSessionRequest,
} from "@tutorputor/contracts/v1";

type MutableVRProgress = {
  completedObjectives: string[];
  currentObjectiveId?: string;
  totalPoints: number;
  maxPoints: number;
  scenesVisited: string[];
  interactionsLog: VRInteractionLog[];
};

function asVRProgress(value: unknown): MutableVRProgress {
  const record =
    value && typeof value === "object" ? (value as Record<string, unknown>) : {};
  return {
    completedObjectives: Array.isArray(record.completedObjectives)
      ? (record.completedObjectives as string[])
      : [],
    currentObjectiveId:
      typeof record.currentObjectiveId === "string"
        ? record.currentObjectiveId
        : undefined,
    totalPoints:
      typeof record.totalPoints === "number" ? record.totalPoints : 0,
    maxPoints: typeof record.maxPoints === "number" ? record.maxPoints : 0,
    scenesVisited: Array.isArray(record.scenesVisited)
      ? (record.scenesVisited as string[])
      : [],
    interactionsLog: Array.isArray(record.interactionsLog)
      ? (record.interactionsLog as VRInteractionLog[])
      : [],
  };
}

function asPerformanceMetrics(value: unknown): Partial<VRPerformanceMetrics> {
  return value && typeof value === "object"
    ? (value as Partial<VRPerformanceMetrics>)
    : {};
}

export class VRSessionServiceImpl implements VRSessionService {
  constructor(private prisma: TutorPrismaClient) {}

  async startSession(args: {
    tenantId: TenantId;
    userId: UserId;
    data: StartVRSessionRequest;
  }): Promise<VRSession> {
    const { tenantId, userId, data } = args;

    // Get lab to get first scene
    const lab = await this.prisma.vRLab.findFirst({
      where: { id: data.labId, tenantId, isPublished: true },
      include: {
        scenes: { orderBy: { order: "asc" }, take: 1 },
        objectives: true,
      },
    });

    if (!lab) {
      throw new Error("Lab not found or not published");
    }

    if (lab.scenes.length === 0) {
      throw new Error("Lab has no scenes");
    }

    const session = await this.prisma.vRSession.create({
      data: {
        id: randomUUID(),
        tenantId,
        userId,
        labId: data.labId,
        status: "initializing",
        currentSceneId: lab.scenes[0].id,
        deviceType: data.deviceType,
        deviceInfo: data.deviceInfo as Record<string, unknown>,
        progress: {
          completedObjectives: [],
          currentObjectiveId: lab.objectives[0]?.id,
          totalPoints: 0,
          maxPoints: lab.objectives.reduce((sum, o) => sum + o.points, 0),
          scenesVisited: [lab.scenes[0].id],
          interactionsLog: [],
        },
        startedAt: new Date(),
        lastActiveAt: new Date(),
        totalDuration: 0,
        performanceMetrics: {
          averageFps: 0,
          minFps: 0,
          loadTime: 0,
          memoryUsage: 0,
          latency: 0,
        },
      },
    });

    // Increment lab session count
    await this.prisma.vRLab.update({
      where: { id: data.labId },
      data: { totalSessions: { increment: 1 } },
    });

    return this.mapToVRSession(session);
  }

  async getSession(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
  }): Promise<VRSession | null> {
    const { tenantId, sessionId } = args;

    const session = await this.prisma.vRSession.findFirst({
      where: {
        id: sessionId,
        tenantId,
      },
    });

    return session ? this.mapToVRSession(session) : null;
  }

  async updateSession(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    userId: UserId;
    data: UpdateVRSessionRequest;
  }): Promise<VRSession> {
    const { tenantId, sessionId, userId, data } = args;

    const current = await this.prisma.vRSession.findFirst({
      where: { id: sessionId, tenantId, userId },
    });

    if (!current) {
      throw new Error("Session not found");
    }

    const progress = asVRProgress(current.progress);

    // Update scene visits if changing scene
    if (
      data.currentSceneId &&
      !progress.scenesVisited.includes(data.currentSceneId)
    ) {
      progress.scenesVisited.push(data.currentSceneId);
    }

    // Add interaction log if provided
    if (data.interactionLog) {
      progress.interactionsLog.push(data.interactionLog);
    }

    const session = await this.prisma.vRSession.update({
      where: { id: sessionId },
      data: {
        ...(data.status && { status: data.status }),
        ...(data.currentSceneId && { currentSceneId: data.currentSceneId }),
        progress,
        lastActiveAt: new Date(),
        totalDuration: Math.floor(
          (Date.now() - current.startedAt.getTime()) / 1000,
        ),
        ...(data.performanceMetrics && {
          performanceMetrics: {
            ...asPerformanceMetrics(current.performanceMetrics),
            ...data.performanceMetrics,
          },
        }),
      },
    });

    return this.mapToVRSession(session);
  }

  async endSession(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    userId: UserId;
  }): Promise<VRSession> {
    const { tenantId, sessionId, userId } = args;

    const session = await this.prisma.vRSession.update({
      where: {
        id: sessionId,
        tenantId,
        userId,
      },
      data: {
        status: "completed",
        endedAt: new Date(),
        lastActiveAt: new Date(),
      },
    });

    return this.mapToVRSession(session);
  }

  async listUserSessions(args: {
    tenantId: TenantId;
    userId: UserId;
    labId?: VRLabId;
    status?: VRSessionStatus;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<VRSession>> {
    const { tenantId, userId, labId, status, pagination } = args;
    const { page = 1, limit = 20 } = pagination;

    const where: Record<string, unknown> = { tenantId, userId };
    if (labId) where.labId = labId;
    if (status) where.status = status;

    const [sessions, total] = await Promise.all([
      this.prisma.vRSession.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { startedAt: "desc" },
      }),
      this.prisma.vRSession.count({ where }),
    ]);

    return {
      items: sessions.map((s) => this.mapToVRSession(s)),
      total,
      page,
      limit,
      hasMore: page * limit < total,
    };
  }

  async logInteraction(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    log: VRInteractionLog;
  }): Promise<void> {
    const { tenantId, sessionId, log } = args;

    const session = await this.prisma.vRSession.findFirst({
      where: { id: sessionId, tenantId },
    });

    if (!session) {
      throw new Error("Session not found");
    }

    const progress = asVRProgress(session.progress);
    progress.interactionsLog.push(log);

    await this.prisma.vRSession.update({
      where: { id: sessionId },
      data: {
        progress,
        lastActiveAt: new Date(),
      },
    });
  }

  async completeObjective(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    objectiveId: string;
    points: number;
  }): Promise<VRSession> {
    const { tenantId, sessionId, objectiveId, points } = args;

    const session = await this.prisma.vRSession.findFirst({
      where: { id: sessionId, tenantId },
    });

    if (!session) {
      throw new Error("Session not found");
    }

    const progress = asVRProgress(session.progress);

    // Don't double-complete objectives
    if (progress.completedObjectives.includes(objectiveId)) {
      return this.mapToVRSession(session);
    }

    progress.completedObjectives.push(objectiveId);
    progress.totalPoints += points;

    const updated = await this.prisma.vRSession.update({
      where: { id: sessionId },
      data: {
        progress,
        lastActiveAt: new Date(),
      },
    });

    return this.mapToVRSession(updated);
  }

  async updatePerformanceMetrics(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    metrics: Partial<VRPerformanceMetrics>;
  }): Promise<void> {
    const { tenantId, sessionId, metrics } = args;

    const session = await this.prisma.vRSession.findFirst({
      where: { id: sessionId, tenantId },
    });

    if (!session) {
      throw new Error("Session not found");
    }

    const currentMetrics = asPerformanceMetrics(session.performanceMetrics);

    await this.prisma.vRSession.update({
      where: { id: sessionId },
      data: {
        performanceMetrics: {
          ...currentMetrics,
          ...metrics,
        },
        lastActiveAt: new Date(),
      },
    });
  }

  async getSessionSummary(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
  }): Promise<{
    session: VRSession;
    totalPoints: number;
    maxPoints: number;
    objectivesCompleted: number;
    objectivesTotal: number;
    duration: number;
    rank?: string;
  }> {
    const { tenantId, sessionId } = args;

    const session = await this.prisma.vRSession.findFirst({
      where: { id: sessionId, tenantId },
    });

    if (!session) {
      throw new Error("Session not found");
    }

    const lab = await this.prisma.vRLab.findFirst({
      where: { id: session.labId },
      include: { objectives: true },
    });

    const progress = asVRProgress(session.progress);
    const maxPoints =
      lab?.objectives.reduce((sum, o) => sum + o.points, 0) || 0;
    const completionRate = maxPoints > 0 ? progress.totalPoints / maxPoints : 0;

    let rank: string | undefined;
    if (completionRate >= 0.9) rank = "A";
    else if (completionRate >= 0.8) rank = "B";
    else if (completionRate >= 0.7) rank = "C";
    else if (completionRate >= 0.6) rank = "D";
    else rank = "F";

    return {
      session: this.mapToVRSession(session),
      totalPoints: progress.totalPoints,
      maxPoints,
      objectivesCompleted: progress.completedObjectives.length,
      objectivesTotal: lab?.objectives.length || 0,
      duration: session.totalDuration,
      rank,
    };
  }

  // ============================================
  // Private helper methods
  // ============================================

  private mapToVRSession(session: Record<string, unknown>): VRSession {
    return {
      id: session.id as string,
      userId: session.userId as string,
      labId: session.labId as string,
      status: session.status as string,
      currentSceneId: session.currentSceneId as string,
      deviceType: session.deviceType as string,
      deviceInfo: session.deviceInfo as Record<string, unknown>,
      progress: session.progress as Record<string, unknown>,
      startedAt: (session.startedAt as Date).toISOString(),
      lastActiveAt: (session.lastActiveAt as Date).toISOString(),
      endedAt: (session.endedAt as Date | undefined)?.toISOString(),
      totalDuration: session.totalDuration as number,
      performanceMetrics: session.performanceMetrics as Record<string, unknown>,
    };
  }
}
