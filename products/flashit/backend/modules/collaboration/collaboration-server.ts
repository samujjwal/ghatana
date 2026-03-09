/**
 * Real-time Collaboration Service for Flashit
 * WebSocket-based real-time collaboration and presence system
 *
 * @doc.type service
 * @doc.purpose Real-time collaboration with presence and co-editing
 * @doc.layer product
 * @doc.pattern CollaborationService
 */

import { Server as SocketIOServer, Socket } from 'socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import Redis from 'ioredis';
import { PrismaClient } from '@prisma/client';
import { verify } from 'jsonwebtoken';

// Redis clients for Socket.IO adapter
const pubClient = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  password: process.env.REDIS_PASSWORD,
});

const subClient = pubClient.duplicate();

// Prisma client
const prisma = new PrismaClient();

// Collaboration event types
export type CollaborationEventType =
  | 'sphere_shared' | 'sphere_unshared' | 'sphere_joined' | 'sphere_left'
  | 'moment_created' | 'moment_edited' | 'moment_deleted' | 'moment_commented'
  | 'comment_created' | 'comment_edited' | 'comment_deleted'
  | 'user_followed' | 'user_unfollowed' | 'presence_update';

// Permission levels for collaboration
export type PermissionLevel = 'viewer' | 'commenter' | 'editor' | 'admin';

// Presence data interface
export interface PresenceData {
  userId: string;
  displayName: string;
  avatar?: string;
  status: 'active' | 'idle' | 'away';
  currentLocation?: {
    type: 'sphere' | 'moment' | 'search' | 'analytics';
    id?: string;
    label?: string;
  };
  cursor?: {
    momentId: string;
    position: number;
    selection?: [number, number];
  };
  lastActivity: string;
}

// Real-time session interface
export interface RealtimeSession {
  id: string;
  userId: string;
  sphereId: string;
  momentId?: string;
  sessionType: 'sphere_browse' | 'moment_edit' | 'moment_view' | 'search' | 'analytics';
  connectionId: string;
  presence: PresenceData;
  joinedAt: Date;
  lastActivity: Date;
}

// Collaborative edit operation
export interface EditOperation {
  type: 'insert' | 'delete' | 'retain' | 'format';
  position: number;
  content?: string;
  length?: number;
  attributes?: Record<string, any>;
  userId: string;
  timestamp: number;
}

// Socket authentication data
interface SocketAuth {
  userId: string;
  email: string;
  displayName: string;
}

/**
 * Real-time collaboration server
 */
export class CollaborationServer {
  private io: SocketIOServer;
  private activeSessions = new Map<string, RealtimeSession>();
  private sphereConnections = new Map<string, Set<string>>(); // sphereId -> connectionIds
  private momentConnections = new Map<string, Set<string>>(); // momentId -> connectionIds

  constructor(httpServer: any) {
    this.io = new SocketIOServer(httpServer, {
      cors: {
        origin: process.env.FRONTEND_URL || 'http://localhost:2901',
        credentials: true,
      },
      adapter: createAdapter(pubClient, subClient),
    });

    this.setupAuthentication();
    this.setupEventHandlers();
    this.startCleanupTask();
  }

  /**
   * Setup Socket.IO authentication middleware
   */
  private setupAuthentication() {
    this.io.use(async (socket, next) => {
      try {
        const token = socket.handshake.auth.token || socket.handshake.headers.authorization?.replace('Bearer ', '');

        if (!token) {
          return next(new Error('Authentication token required'));
        }

        // Verify JWT token
        const payload = verify(token, process.env.JWT_SECRET!) as any;

        // Get user details
        const user = await prisma.user.findUnique({
          where: { id: payload.userId },
          select: { id: true, email: true, displayName: true },
        });

        if (!user) {
          return next(new Error('User not found'));
        }

        socket.data.auth = {
          userId: user.id,
          email: user.email,
          displayName: user.displayName,
        } as SocketAuth;

        next();
      } catch (error) {
        console.error('Socket authentication failed:', error);
        next(new Error('Authentication failed'));
      }
    });
  }

  /**
   * Setup Socket.IO event handlers
   */
  private setupEventHandlers() {
    this.io.on('connection', (socket) => {
      console.log(`User ${socket.data.auth.userId} connected`);

      // Handle sphere joining
      socket.on('join_sphere', async (data) => {
        await this.handleJoinSphere(socket, data);
      });

      // Handle sphere leaving
      socket.on('leave_sphere', async (data) => {
        await this.handleLeaveSphere(socket, data);
      });

      // Handle moment editing
      socket.on('start_editing_moment', async (data) => {
        await this.handleStartEditingMoment(socket, data);
      });

      // Handle collaborative edit operations
      socket.on('edit_operation', async (data) => {
        await this.handleEditOperation(socket, data);
      });

      // Handle presence updates
      socket.on('update_presence', async (data) => {
        await this.handlePresenceUpdate(socket, data);
      });

      // Handle comments
      socket.on('create_comment', async (data) => {
        await this.handleCreateComment(socket, data);
      });

      // Handle reactions
      socket.on('add_reaction', async (data) => {
        await this.handleAddReaction(socket, data);
      });

      // Handle typing indicators
      socket.on('typing_start', (data) => {
        this.handleTypingIndicator(socket, data, true);
      });

      socket.on('typing_stop', (data) => {
        this.handleTypingIndicator(socket, data, false);
      });

      // Handle disconnection
      socket.on('disconnect', async () => {
        await this.handleDisconnection(socket);
      });
    });
  }

  /**
   * Handle user joining a sphere
   */
  private async handleJoinSphere(socket: Socket, data: { sphereId: string; sessionType?: string }) {
    const { userId } = socket.data.auth;
    const { sphereId, sessionType = 'sphere_browse' } = data;

    try {
      // Check permissions
      const hasPermission = await this.checkSpherePermission(userId, sphereId, 'viewer');
      if (!hasPermission) {
        socket.emit('error', { message: 'Insufficient permissions for sphere' });
        return;
      }

      // Join sphere room
      await socket.join(`sphere:${sphereId}`);

      // Track sphere connections
      if (!this.sphereConnections.has(sphereId)) {
        this.sphereConnections.set(sphereId, new Set());
      }
      this.sphereConnections.get(sphereId)!.add(socket.id);

      // Update presence in database
      const sessionId = await this.updateUserPresence(
        userId,
        sphereId,
        undefined,
        sessionType as any,
        socket.id
      );

      // Create session record
      const session: RealtimeSession = {
        id: sessionId,
        userId,
        sphereId,
        sessionType: sessionType as any,
        connectionId: socket.id,
        presence: {
          userId,
          displayName: socket.data.auth.displayName,
          status: 'active',
          currentLocation: { type: 'sphere', id: sphereId },
          lastActivity: new Date().toISOString(),
        },
        joinedAt: new Date(),
        lastActivity: new Date(),
      };

      this.activeSessions.set(socket.id, session);

      // Notify sphere members of new presence
      socket.to(`sphere:${sphereId}`).emit('user_joined_sphere', {
        userId,
        displayName: socket.data.auth.displayName,
        presence: session.presence,
      });

      // Send current sphere presence to new user
      const spherePresence = await this.getSpherePresence(sphereId);
      socket.emit('sphere_presence', spherePresence);

      // Create collaboration event
      await this.createCollaborationEvent('sphere_joined', userId, sphereId);

      socket.emit('sphere_joined', { sphereId, sessionId });

    } catch (error) {
      console.error('Failed to join sphere:', error);
      socket.emit('error', { message: 'Failed to join sphere' });
    }
  }

  /**
   * Handle user leaving a sphere
   */
  private async handleLeaveSphere(socket: Socket, data: { sphereId: string }) {
    const { userId } = socket.data.auth;
    const { sphereId } = data;

    try {
      // Leave sphere room
      await socket.leave(`sphere:${sphereId}`);

      // Update sphere connections
      this.sphereConnections.get(sphereId)?.delete(socket.id);

      // Remove session
      this.activeSessions.delete(socket.id);

      // Update presence in database
      await prisma.collaboration.realtimeSession.updateMany({
        where: {
          userId,
          sphereId,
          connectionId: socket.id,
        },
        data: {
          status: 'disconnected',
          leftAt: new Date(),
        },
      });

      // Notify sphere members of departure
      socket.to(`sphere:${sphereId}`).emit('user_left_sphere', {
        userId,
        displayName: socket.data.auth.displayName,
      });

      // Create collaboration event
      await this.createCollaborationEvent('sphere_left', userId, sphereId);

      socket.emit('sphere_left', { sphereId });

    } catch (error) {
      console.error('Failed to leave sphere:', error);
      socket.emit('error', { message: 'Failed to leave sphere' });
    }
  }

  /**
   * Handle starting moment editing session
   */
  private async handleStartEditingMoment(socket: Socket, data: { momentId: string; sphereId: string }) {
    const { userId } = socket.data.auth;
    const { momentId, sphereId } = data;

    try {
      // Check edit permissions
      const hasPermission = await this.checkSpherePermission(userId, sphereId, 'editor');
      if (!hasPermission) {
        socket.emit('error', { message: 'Insufficient permissions to edit moment' });
        return;
      }

      // Join moment editing room
      await socket.join(`moment:${momentId}`);

      // Track moment connections
      if (!this.momentConnections.has(momentId)) {
        this.momentConnections.set(momentId, new Set());
      }
      this.momentConnections.get(momentId)!.add(socket.id);

      // Create editing session
      const editingSession = await prisma.collaboration.editingSession.create({
        data: {
          momentId,
          userId,
          sessionToken: `session-${socket.id}-${Date.now()}`,
          expiresAt: new Date(Date.now() + 2 * 60 * 60 * 1000), // 2 hours
        },
      });

      // Update session with moment editing
      const session = this.activeSessions.get(socket.id);
      if (session) {
        session.momentId = momentId;
        session.sessionType = 'moment_edit';
        session.presence.currentLocation = { type: 'moment', id: momentId };
      }

      // Get current editors
      const currentEditors = await this.getMomentEditors(momentId);

      // Notify other editors
      socket.to(`moment:${momentId}`).emit('editor_joined', {
        userId,
        displayName: socket.data.auth.displayName,
        sessionToken: editingSession.sessionToken,
      });

      // Send current editing state to new editor
      socket.emit('editing_session_started', {
        momentId,
        sessionToken: editingSession.sessionToken,
        currentEditors,
      });

    } catch (error) {
      console.error('Failed to start editing moment:', error);
      socket.emit('error', { message: 'Failed to start editing session' });
    }
  }

  /**
   * Handle collaborative edit operation
   */
  private async handleEditOperation(socket: Socket, data: {
    momentId: string;
    operation: EditOperation;
    sessionToken: string;
  }) {
    const { userId } = socket.data.auth;
    const { momentId, operation, sessionToken } = data;

    try {
      // Verify editing session
      const editingSession = await prisma.collaboration.editingSession.findFirst({
        where: {
          momentId,
          userId,
          sessionToken,
          expiresAt: { gt: new Date() },
        },
      });

      if (!editingSession) {
        socket.emit('error', { message: 'Invalid or expired editing session' });
        return;
      }

      // Store edit operation
      await prisma.collaboration.editOperation.create({
        data: {
          editingSessionId: editingSession.id,
          momentId,
          userId,
          operationType: operation.type,
          position: operation.position,
          content: operation.content,
          length: operation.length,
          operationData: operation.attributes ? JSON.stringify(operation.attributes) : null,
        },
      });

      // Update session activity
      await prisma.collaboration.editingSession.update({
        where: { id: editingSession.id },
        data: {
          lastEditAt: new Date(),
          cursorPosition: operation.position,
        },
      });

      // Broadcast operation to other editors
      socket.to(`moment:${momentId}`).emit('edit_operation', {
        operation: { ...operation, userId },
        sessionToken,
      });

      // Update moment content (simplified - in production, use operational transform)
      if (operation.type === 'insert' && operation.content) {
        await this.applyEditOperation(momentId, operation);
      }

    } catch (error) {
      console.error('Failed to handle edit operation:', error);
      socket.emit('error', { message: 'Failed to apply edit operation' });
    }
  }

  /**
   * Handle presence updates
   */
  private async handlePresenceUpdate(socket: Socket, data: { presence: Partial<PresenceData> }) {
    const session = this.activeSessions.get(socket.id);
    if (!session) return;

    try {
      // Update session presence
      session.presence = {
        ...session.presence,
        ...data.presence,
        lastActivity: new Date().toISOString(),
      };
      session.lastActivity = new Date();

      // Update database presence
      await this.updateUserPresence(
        session.userId,
        session.sphereId,
        session.momentId,
        session.sessionType,
        socket.id,
        session.presence
      );

      // Broadcast presence update to sphere
      socket.to(`sphere:${session.sphereId}`).emit('presence_update', {
        userId: session.userId,
        presence: session.presence,
      });

    } catch (error) {
      console.error('Failed to update presence:', error);
    }
  }

  /**
   * Handle comment creation
   */
  private async handleCreateComment(socket: Socket, data: {
    momentId: string;
    sphereId: string;
    content: string;
    parentCommentId?: string;
    mentions?: string[];
  }) {
    const { userId } = socket.data.auth;
    const { momentId, sphereId, content, parentCommentId, mentions = [] } = data;

    try {
      // Check comment permissions
      const hasPermission = await this.checkSpherePermission(userId, sphereId, 'commenter');
      if (!hasPermission) {
        socket.emit('error', { message: 'Insufficient permissions to comment' });
        return;
      }

      // Create comment
      const comment = await prisma.collaboration.momentComment.create({
        data: {
          momentId,
          userId,
          parentCommentId,
          content,
          mentions: JSON.stringify(mentions),
        },
        include: {
          user: {
            select: { displayName: true, email: true },
          },
        },
      });

      // Broadcast comment to sphere members
      this.io.to(`sphere:${sphereId}`).emit('comment_created', {
        comment: {
          id: comment.id,
          momentId,
          content: comment.content,
          author: {
            id: userId,
            displayName: comment.user.displayName,
          },
          parentCommentId,
          mentions,
          createdAt: comment.createdAt.toISOString(),
        },
      });

      // Create collaboration event
      await this.createCollaborationEvent('comment_created', userId, sphereId, momentId);

      // Send notifications for mentions
      for (const mentionedUserId of mentions) {
        // TODO: Send notification to mentioned user
      }

    } catch (error) {
      console.error('Failed to create comment:', error);
      socket.emit('error', { message: 'Failed to create comment' });
    }
  }

  /**
   * Handle reaction addition
   */
  private async handleAddReaction(socket: Socket, data: {
    momentId: string;
    sphereId: string;
    reactionType: string;
  }) {
    const { userId } = socket.data.auth;
    const { momentId, sphereId, reactionType } = data;

    try {
      // Check permissions
      const hasPermission = await this.checkSpherePermission(userId, sphereId, 'viewer');
      if (!hasPermission) {
        socket.emit('error', { message: 'Insufficient permissions to react' });
        return;
      }

      // Add or toggle reaction
      const existingReaction = await prisma.collaboration.momentReaction.findFirst({
        where: { momentId, userId, reactionType },
      });

      if (existingReaction) {
        // Remove reaction
        await prisma.collaboration.momentReaction.delete({
          where: { id: existingReaction.id },
        });

        this.io.to(`sphere:${sphereId}`).emit('reaction_removed', {
          momentId,
          reactionType,
          userId,
        });
      } else {
        // Add reaction
        await prisma.collaboration.momentReaction.create({
          data: { momentId, userId, reactionType },
        });

        this.io.to(`sphere:${sphereId}`).emit('reaction_added', {
          momentId,
          reactionType,
          userId,
          displayName: socket.data.auth.displayName,
        });
      }

    } catch (error) {
      console.error('Failed to handle reaction:', error);
      socket.emit('error', { message: 'Failed to handle reaction' });
    }
  }

  /**
   * Handle typing indicator
   */
  private handleTypingIndicator(socket: Socket, data: { momentId: string; sphereId: string }, isTyping: boolean) {
    const { userId } = socket.data.auth;
    const { momentId, sphereId } = data;

    socket.to(`sphere:${sphereId}`).emit('typing_indicator', {
      momentId,
      userId,
      displayName: socket.data.auth.displayName,
      isTyping,
    });
  }

  /**
   * Handle user disconnection
   */
  private async handleDisconnection(socket: Socket) {
    const { userId } = socket.data.auth;
    const session = this.activeSessions.get(socket.id);

    if (session) {
      try {
        // Update session status
        await prisma.collaboration.realtimeSession.updateMany({
          where: {
            userId,
            connectionId: socket.id,
          },
          data: {
            status: 'disconnected',
            leftAt: new Date(),
          },
        });

        // Clean up connections
        this.sphereConnections.get(session.sphereId)?.delete(socket.id);
        if (session.momentId) {
          this.momentConnections.get(session.momentId)?.delete(socket.id);
        }

        // Notify sphere members
        socket.to(`sphere:${session.sphereId}`).emit('user_disconnected', {
          userId,
          displayName: socket.data.auth.displayName,
        });

        // Remove session
        this.activeSessions.delete(socket.id);

      } catch (error) {
        console.error('Failed to handle disconnection:', error);
      }
    }

    console.log(`User ${userId} disconnected`);
  }

  /**
   * Helper methods
   */

  private async checkSpherePermission(userId: string, sphereId: string, requiredPermission: PermissionLevel): Promise<boolean> {
    try {
      const result = await prisma.$queryRaw`
        SELECT collaboration.check_collaboration_permission(${userId}::uuid, ${sphereId}::uuid, ${requiredPermission}) as has_permission
      ` as any[];

      return result[0]?.has_permission || false;
    } catch (error) {
      console.error('Failed to check permission:', error);
      return false;
    }
  }

  private async updateUserPresence(
    userId: string,
    sphereId: string,
    momentId?: string,
    sessionType?: string,
    connectionId?: string,
    presenceData?: any
  ): Promise<string> {
    const result = await prisma.$queryRaw`
      SELECT collaboration.update_user_presence(
        ${userId}::uuid,
        ${sphereId}::uuid,
        ${momentId ? momentId : null}::uuid,
        ${sessionType || 'sphere_browse'},
        ${connectionId},
        ${JSON.stringify(presenceData || {})}::jsonb
      ) as session_id
    ` as any[];

    return result[0].session_id;
  }

  private async createCollaborationEvent(
    eventType: CollaborationEventType,
    userId: string,
    sphereId: string,
    momentId?: string,
    targetUserId?: string,
    eventData: any = {}
  ): Promise<string> {
    const result = await prisma.$queryRaw`
      SELECT collaboration.create_collaboration_event(
        ${eventType},
        ${userId}::uuid,
        ${sphereId}::uuid,
        ${momentId ? momentId : null}::uuid,
        ${targetUserId ? targetUserId : null}::uuid,
        ${JSON.stringify(eventData)}::jsonb
      ) as event_id
    ` as any[];

    return result[0].event_id;
  }

  private async getSpherePresence(sphereId: string): Promise<PresenceData[]> {
    const sessions = await prisma.collaboration.realtimeSession.findMany({
      where: {
        sphereId,
        status: 'active',
        lastActivityAt: { gt: new Date(Date.now() - 5 * 60 * 1000) }, // Last 5 minutes
      },
      include: {
        user: {
          select: { displayName: true },
        },
      },
    });

    return sessions.map(session => ({
      userId: session.userId,
      displayName: session.user.displayName,
      status: session.status as any,
      currentLocation: {
        type: session.sessionType.includes('moment') ? 'moment' : 'sphere',
        id: session.momentId || sphereId,
      },
      lastActivity: session.lastActivityAt.toISOString(),
    }));
  }

  private async getMomentEditors(momentId: string): Promise<Array<{ userId: string; displayName: string; sessionToken: string }>> {
    const sessions = await prisma.collaboration.editingSession.findMany({
      where: {
        momentId,
        expiresAt: { gt: new Date() },
      },
      include: {
        user: {
          select: { displayName: true },
        },
      },
    });

    return sessions.map(session => ({
      userId: session.userId,
      displayName: session.user.displayName,
      sessionToken: session.sessionToken,
    }));
  }

  private async applyEditOperation(momentId: string, operation: EditOperation): Promise<void> {
    // Simplified implementation - in production, use proper operational transformation
    if (operation.type === 'insert' && operation.content) {
      const moment = await prisma.moment.findUnique({
        where: { id: momentId },
        select: { contentText: true },
      });

      if (moment) {
        const currentContent = moment.contentText;
        const newContent =
          currentContent.slice(0, operation.position) +
          operation.content +
          currentContent.slice(operation.position);

        await prisma.moment.update({
          where: { id: momentId },
          data: { contentText: newContent },
        });
      }
    }
  }

  /**
   * Start periodic cleanup task
   */
  private startCleanupTask() {
    setInterval(async () => {
      try {
        // Clean up old sessions
        await prisma.collaboration.realtimeSession.updateMany({
          where: {
            lastActivityAt: { lt: new Date(Date.now() - 10 * 60 * 1000) }, // 10 minutes ago
            status: { in: ['active', 'idle'] },
          },
          data: {
            status: 'disconnected',
            leftAt: new Date(),
          },
        });

        // Clean up expired editing sessions
        await prisma.collaboration.editingSession.deleteMany({
          where: {
            expiresAt: { lt: new Date() },
          },
        });

      } catch (error) {
        console.error('Cleanup task failed:', error);
      }
    }, 5 * 60 * 1000); // Every 5 minutes
  }

  /**
   * Get server statistics
   */
  public getStats() {
    return {
      activeSessions: this.activeSessions.size,
      sphereConnections: this.sphereConnections.size,
      momentConnections: this.momentConnections.size,
      connectedSockets: this.io.engine.clientsCount,
    };
  }
}

export default CollaborationServer;
