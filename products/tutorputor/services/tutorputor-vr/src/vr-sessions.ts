/**
 * @doc.type service
 * @doc.purpose VR Session management service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';
import { v4 as uuidv4 } from 'uuid';
import type {
  VRSessionService,
  TenantId,
  UserId,
  PaginatedResult,
  PaginationArgs,
} from '@ghatana/tutorputor-contracts/v1';
import type {
  VRSession,
  VRSessionId,
  VRSessionStatus,
  VRLabId,
  VRInteractionLog,
  VRPerformanceMetrics,
  StartVRSessionRequest,
  UpdateVRSessionRequest,
} from '@ghatana/tutorputor-contracts/v1';

export class VRSessionServiceImpl implements VRSessionService {
  constructor(private prisma: PrismaClient) {}

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
        scenes: { orderBy: { order: 'asc' }, take: 1 },
        objectives: true,
      },
    });

    if (!lab) {
      throw new Error('Lab not found or not published');
    }

    if (lab.scenes.length === 0) {
      throw new Error('Lab has no scenes');
    }

    const session = await this.prisma.vRSession.create({
      data: {
        id: uuidv4(),
        tenantId,
        userId,
        labId: data.labId,
        status: 'initializing',
        currentSceneId: lab.scenes[0].id,
        deviceType: data.deviceType,
        deviceInfo: data.deviceInfo as any,
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
      throw new Error('Session not found');
    }

    const progress = current.progress as any;

    // Update scene visits if changing scene
    if (data.currentSceneId && !progress.scenesVisited.includes(data.currentSceneId)) {
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
        totalDuration: Math.floor((Date.now() - current.startedAt.getTime()) / 1000),
        ...(data.performanceMetrics && {
          performanceMetrics: {
            ...(current.performanceMetrics as any),
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
        status: 'completed',
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

    const where: any = { tenantId, userId };
    if (labId) where.labId = labId;
    if (status) where.status = status;

    const [sessions, total] = await Promise.all([
      this.prisma.vRSession.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { startedAt: 'desc' },
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
      throw new Error('Session not found');
    }

    const progress = session.progress as any;
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
      throw new Error('Session not found');
    }

    const progress = session.progress as any;

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
      throw new Error('Session not found');
    }

    const currentMetrics = session.performanceMetrics as any;

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
      throw new Error('Session not found');
    }

    const lab = await this.prisma.vRLab.findFirst({
      where: { id: session.labId },
      include: { objectives: true },
    });

    const progress = session.progress as any;
    const maxPoints = lab?.objectives.reduce((sum, o) => sum + o.points, 0) || 0;
    const completionRate = maxPoints > 0 ? progress.totalPoints / maxPoints : 0;

    let rank: string | undefined;
    if (completionRate >= 0.9) rank = 'A';
    else if (completionRate >= 0.8) rank = 'B';
    else if (completionRate >= 0.7) rank = 'C';
    else if (completionRate >= 0.6) rank = 'D';
    else rank = 'F';

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

  private mapToVRSession(session: any): VRSession {
    return {
      id: session.id,
      userId: session.userId,
      labId: session.labId,
      status: session.status,
      currentSceneId: session.currentSceneId,
      deviceType: session.deviceType,
      deviceInfo: session.deviceInfo,
      progress: session.progress,
      startedAt: session.startedAt.toISOString(),
      lastActiveAt: session.lastActiveAt.toISOString(),
      endedAt: session.endedAt?.toISOString(),
      totalDuration: session.totalDuration,
      performanceMetrics: session.performanceMetrics,
    };
  }
}
