/**
 * @doc.type service
 * @doc.purpose VR Multiplayer session management with real-time sync
 * @doc.layer product
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';
import Redis from 'ioredis';
import { v4 as uuidv4 } from 'uuid';
import type {
  VRMultiplayerService,
  TenantId,
  UserId,
  PaginatedResult,
  PaginationArgs,
} from '@ghatana/tutorputor-contracts/v1';
import type { VRMultiplayerSession, VRLabId, VRParticipant } from '@ghatana/tutorputor-contracts/v1';

export class VRMultiplayerServiceImpl implements VRMultiplayerService {
  private publisher: Redis;
  private subscriber: Redis;

  constructor(private prisma: PrismaClient, redisUrl: string) {
    this.publisher = new Redis(redisUrl);
    this.subscriber = new Redis(redisUrl);
  }

  async createSession(args: {
    tenantId: TenantId;
    hostUserId: UserId;
    labId: VRLabId;
    settings: {
      maxParticipants: number;
      voiceChatEnabled: boolean;
      spatialAudioEnabled: boolean;
    };
  }): Promise<VRMultiplayerSession> {
    const { tenantId, hostUserId, labId, settings } = args;

    // Get host user info
    const hostUser = await this.prisma.user.findUnique({
      where: { id: hostUserId },
    });

    if (!hostUser) {
      throw new Error('Host user not found');
    }

    const session = await this.prisma.vRMultiplayerSession.create({
      data: {
        id: uuidv4(),
        tenantId,
        labId,
        hostUserId,
        maxParticipants: settings.maxParticipants,
        voiceChatEnabled: settings.voiceChatEnabled,
        spatialAudioEnabled: settings.spatialAudioEnabled,
        status: 'lobby',
        participants: [
          {
            userId: hostUserId,
            displayName: hostUser.displayName || hostUser.email,
            avatarUrl: hostUser.avatarUrl,
            position: { x: 0, y: 0, z: 0 },
            rotation: { x: 0, y: 0, z: 0, w: 1 },
            isReady: false,
            isMuted: false,
            isHost: true,
            joinedAt: new Date().toISOString(),
          },
        ],
      },
    });

    return this.mapToVRMultiplayerSession(session);
  }

  async joinSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    displayName: string;
    avatarUrl?: string;
  }): Promise<VRMultiplayerSession> {
    const { tenantId, sessionId, userId, displayName, avatarUrl } = args;

    const session = await this.prisma.vRMultiplayerSession.findFirst({
      where: { id: sessionId, tenantId, status: 'lobby' },
    });

    if (!session) {
      throw new Error('Session not found or not in lobby');
    }

    const participants = session.participants as VRParticipant[];

    // Check if already joined
    if (participants.some((p) => p.userId === userId)) {
      throw new Error('User already in session');
    }

    // Check max participants
    if (participants.length >= session.maxParticipants) {
      throw new Error('Session is full');
    }

    // Add new participant
    participants.push({
      userId,
      displayName,
      avatarUrl,
      position: { x: 0, y: 0, z: 0 },
      rotation: { x: 0, y: 0, z: 0, w: 1 },
      isReady: false,
      isMuted: false,
      isHost: false,
      joinedAt: new Date().toISOString(),
    });

    const updated = await this.prisma.vRMultiplayerSession.update({
      where: { id: sessionId },
      data: { participants },
    });

    // Broadcast participant joined
    await this.broadcastSessionUpdate(sessionId, {
      type: 'participant_joined',
      participant: participants[participants.length - 1],
    });

    return this.mapToVRMultiplayerSession(updated);
  }

  async leaveSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
  }): Promise<void> {
    const { tenantId, sessionId, userId } = args;

    const session = await this.prisma.vRMultiplayerSession.findFirst({
      where: { id: sessionId, tenantId },
    });

    if (!session) {
      throw new Error('Session not found');
    }

    const participants = session.participants as VRParticipant[];
    const participant = participants.find((p) => p.userId === userId);

    if (!participant) {
      return; // Already left
    }

    // If host leaves and session is in lobby, end session
    if (participant.isHost && session.status === 'lobby') {
      await this.prisma.vRMultiplayerSession.update({
        where: { id: sessionId },
        data: { status: 'ended' },
      });

      await this.broadcastSessionUpdate(sessionId, {
        type: 'session_ended',
        reason: 'host_left',
      });
      return;
    }

    // Remove participant
    const updatedParticipants = participants.filter((p) => p.userId !== userId);

    // If host leaves during active session, assign new host
    if (participant.isHost && updatedParticipants.length > 0) {
      updatedParticipants[0].isHost = true;
    }

    await this.prisma.vRMultiplayerSession.update({
      where: { id: sessionId },
      data: { participants: updatedParticipants },
    });

    await this.broadcastSessionUpdate(sessionId, {
      type: 'participant_left',
      userId,
    });
  }

  async updateParticipant(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    state: {
      position?: { x: number; y: number; z: number };
      rotation?: { x: number; y: number; z: number; w: number };
      isMuted?: boolean;
      isReady?: boolean;
    };
  }): Promise<void> {
    const { sessionId, userId, state } = args;

    // For real-time updates, we use Redis pub/sub instead of database
    // This reduces DB writes and improves performance

    await this.publisher.publish(
      `vr:session:${sessionId}:state`,
      JSON.stringify({
        type: 'participant_update',
        userId,
        ...state,
        timestamp: Date.now(),
      })
    );

    // Periodically persist to database (every 5 seconds for position)
    // This is handled by a background job in production
    const shouldPersist = state.isMuted !== undefined || state.isReady !== undefined;

    if (shouldPersist) {
      const session = await this.prisma.vRMultiplayerSession.findFirst({
        where: { id: sessionId },
      });

      if (session) {
        const participants = session.participants as VRParticipant[];
        const participant = participants.find((p) => p.userId === userId);

        if (participant) {
          if (state.position) participant.position = state.position;
          if (state.rotation) participant.rotation = state.rotation;
          if (state.isMuted !== undefined) participant.isMuted = state.isMuted;
          if (state.isReady !== undefined) participant.isReady = state.isReady;

          await this.prisma.vRMultiplayerSession.update({
            where: { id: sessionId },
            data: { participants },
          });
        }
      }
    }
  }

  async startSession(args: {
    tenantId: TenantId;
    sessionId: string;
    hostUserId: UserId;
  }): Promise<VRMultiplayerSession> {
    const { tenantId, sessionId, hostUserId } = args;

    const session = await this.prisma.vRMultiplayerSession.findFirst({
      where: { id: sessionId, tenantId, hostUserId, status: 'lobby' },
    });

    if (!session) {
      throw new Error('Session not found or not authorized');
    }

    const participants = session.participants as VRParticipant[];

    // Check all participants are ready
    const allReady = participants.every((p) => p.isReady || p.isHost);
    if (!allReady) {
      throw new Error('Not all participants are ready');
    }

    const updated = await this.prisma.vRMultiplayerSession.update({
      where: { id: sessionId },
      data: { status: 'active' },
    });

    await this.broadcastSessionUpdate(sessionId, {
      type: 'session_started',
    });

    return this.mapToVRMultiplayerSession(updated);
  }

  async endSession(args: {
    tenantId: TenantId;
    sessionId: string;
    hostUserId: UserId;
  }): Promise<void> {
    const { tenantId, sessionId, hostUserId } = args;

    await this.prisma.vRMultiplayerSession.update({
      where: { id: sessionId, tenantId, hostUserId },
      data: { status: 'ended' },
    });

    await this.broadcastSessionUpdate(sessionId, {
      type: 'session_ended',
      reason: 'host_ended',
    });
  }

  async getSession(args: {
    tenantId: TenantId;
    sessionId: string;
  }): Promise<VRMultiplayerSession | null> {
    const { tenantId, sessionId } = args;

    const session = await this.prisma.vRMultiplayerSession.findFirst({
      where: { id: sessionId, tenantId },
    });

    return session ? this.mapToVRMultiplayerSession(session) : null;
  }

  async listSessions(args: {
    tenantId: TenantId;
    labId?: VRLabId;
    status?: 'lobby' | 'active';
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<VRMultiplayerSession>> {
    const { tenantId, labId, status, pagination } = args;
    const { page = 1, limit = 20 } = pagination;

    const where: any = { tenantId };
    if (labId) where.labId = labId;
    if (status) where.status = status;

    const [sessions, total] = await Promise.all([
      this.prisma.vRMultiplayerSession.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.vRMultiplayerSession.count({ where }),
    ]);

    return {
      items: sessions.map((s) => this.mapToVRMultiplayerSession(s)),
      total,
      page,
      limit,
      hasMore: page * limit < total,
    };
  }

  // ============================================
  // Real-time subscription methods
  // ============================================

  async subscribeToSession(
    sessionId: string,
    callback: (message: any) => void
  ): Promise<() => void> {
    const channel = `vr:session:${sessionId}:state`;

    const handler = (ch: string, message: string) => {
      if (ch === channel) {
        try {
          callback(JSON.parse(message));
        } catch (e) {
          console.error('Failed to parse message', e);
        }
      }
    };

    this.subscriber.on('message', handler);
    await this.subscriber.subscribe(channel);

    return async () => {
      this.subscriber.off('message', handler);
      await this.subscriber.unsubscribe(channel);
    };
  }

  // ============================================
  // Private helper methods
  // ============================================

  private async broadcastSessionUpdate(sessionId: string, event: any): Promise<void> {
    await this.publisher.publish(
      `vr:session:${sessionId}:events`,
      JSON.stringify({
        ...event,
        timestamp: Date.now(),
      })
    );
  }

  private mapToVRMultiplayerSession(session: any): VRMultiplayerSession {
    return {
      id: session.id,
      labId: session.labId,
      hostUserId: session.hostUserId,
      participants: session.participants,
      maxParticipants: session.maxParticipants,
      voiceChatEnabled: session.voiceChatEnabled,
      spatialAudioEnabled: session.spatialAudioEnabled,
      status: session.status,
      createdAt: session.createdAt.toISOString(),
    };
  }
}
