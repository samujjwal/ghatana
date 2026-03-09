/**
 * Real-Time Service
 *
 * Unified WebSocket service for YAPPC.
 * Handles Canvas Collaboration, Notifications, and Activity Streams.
 *
 * @doc.type service
 * @doc.purpose Real-time communication
 * @doc.layer implementation
 */
import { FastifyInstance, FastifyRequest } from 'fastify';
import { WebSocket } from 'ws';

// =============================================================================
// Canvas Types (Legacy + Enhanced)
// =============================================================================

export interface CollaboratorInfo {
  id: string;
  name: string;
  email: string;
  color: string;
  cursor: { x: number; y: number } | null;
  selectedNodeIds: string[];
  lastActive: number;
}

export interface CanvasRoom {
  projectId: string;
  collaborators: Map<string, CollaboratorInfo & { ws: WebSocket }>;
  lastActivity: number;
}

export type ClientMessage =
  | { type: 'join'; userId: string; userName: string; userEmail: string }
  | { type: 'leave' }
  | { type: 'cursor-update'; x: number; y: number }
  | { type: 'selection-update'; nodeIds: string[] }
  | { type: 'node-update'; nodeId: string; updates: Record<string, unknown> }
  | { type: 'ping' }
  | { type: 'crdt-update'; data: string } // Base64 Yjs update
  | { type: 'auth'; token: string }; // Added for Auth

export type ServerMessage =
  | { type: 'user-joined'; user: CollaboratorInfo }
  | { type: 'user-left'; userId: string }
  | { type: 'users-list'; users: Omit<CollaboratorInfo, 'ws'>[] }
  | { type: 'cursor-update'; userId: string; x: number; y: number }
  | { type: 'selection-update'; userId: string; nodeIds: string[] }
  | { type: 'node-update'; userId: string; nodeId: string; updates: Record<string, unknown> }
  | { type: 'crdt-update'; userId: string; data: string }
  | { type: 'pong' }
  | { type: 'error'; message: string }
  | { type: 'notification'; payload: Record<string, unknown> };

// =============================================================================
// Service Implementation
// =============================================================================

export class RealTimeService {
  private canvasRooms = new Map<string, CanvasRoom>();
  private notificationClients = new Set<WebSocket>();

  // Config
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private cleanupInterval: NodeJS.Timeout | null = null;
  private colorPalette = [
    '#FF6B6B',
    '#4ECDC4',
    '#45B7D1',
    '#FFA07A',
    '#98D8C8',
    '#F7DC6F',
    '#BB8FCE',
    '#85C1E2',
    '#F8B739',
    '#52B788',
  ];

  constructor() {
    // Start heartbeat monitoring (every 30s)
    this.heartbeatInterval = setInterval(() => this.checkHeartbeats(), 30000);
    // Start cleanup (every 5m)
    this.cleanupInterval = setInterval(
      () => this.cleanupInactiveRooms(),
      300000
    );
  }

  public shutdown() {
    if (this.heartbeatInterval) clearInterval(this.heartbeatInterval);
    if (this.cleanupInterval) clearInterval(this.cleanupInterval);
    this.canvasRooms.clear();
    this.notificationClients.clear();
  }

  /**
   * Register WebSocket routes
   */
  public registerRoutes(fastify: FastifyInstance) {
    fastify.register(async (instance) => {
      // Canvas Collaboration
      instance.get(
        '/ws/canvas/:projectId',
        { websocket: true },
        (connection, req) => {
          this.handleCanvasConnection(connection.socket, req);
        }
      );

      // Notifications / System Events
      instance.get(
        '/ws/notifications',
        { websocket: true },
        (connection, req) => {
          this.handleNotificationConnection(connection.socket, req);
        }
      );

      // Legacy aliases
      instance.get(
        '/canvas/:projectId',
        { websocket: true },
        (connection, req) => {
          this.handleCanvasConnection(connection.socket, req);
        }
      );
    });
  }

  // =========================================================================
  // Canvas Logic
  // =========================================================================

  private handleCanvasConnection(ws: WebSocket, req: FastifyRequest) {
    const params = req.params as { projectId: string };
    const projectId = params.projectId;

    let userId: string | null = null;
    let room: CanvasRoom | null = null;

    // Basic Auth Check (Placeholder - should validate token)
    // For Week 0/1, we allow connection but expect 'auth' message or 'join'

    ws.on('message', (data: Buffer) => {
      try {
        const message = JSON.parse(data.toString()) as ClientMessage;

        switch (message.type) {
          case 'auth': {
            // Validate JWT token from client
            try {
              const jwt = await import('jsonwebtoken');
              const secret = process.env.JWT_ACCESS_SECRET || 'your-access-secret';
              const decoded = jwt.default.verify(message.token, secret) as {
                userId: string;
                email: string;
                role: string;
              };
              userId = decoded.userId;
              this.sendToClient(ws, {
                type: 'users-list',
                users: [],
              });
            } catch (err) {
              this.sendToClient(ws, {
                type: 'error',
                message: 'Authentication failed: invalid or expired token',
              });
              ws.close(4001, 'Authentication failed');
            }
            break;
          }

          case 'join':
            userId = message.userId;
            room = this.joinCanvasRoom(
              projectId,
              message.userId,
              message.userName,
              message.userEmail,
              ws
            );
            this.broadcastToRoom(
              room,
              {
                type: 'user-joined',
                user: this.sanitizeUser(room.collaborators.get(userId)!),
              },
              userId
            );
            this.sendToClient(ws, {
              type: 'users-list',
              users: Array.from(room.collaborators.values()).map(
                this.sanitizeUser
              ),
            });
            break;

          case 'leave':
            if (userId && room) this.leaveCanvasRoom(room, userId);
            break;

          case 'cursor-update':
            if (userId && room) {
              // Update local state
              const user = room.collaborators.get(userId);
              if (user) {
                user.cursor = { x: message.x, y: message.y };
                user.lastActive = Date.now();
                // Broadcast (throttle?)
                this.broadcastToRoom(
                  room,
                  { type: 'cursor-update', userId, x: message.x, y: message.y },
                  userId
                );
              }
            }
            break;

          case 'selection-update':
            if (userId && room) {
              const user = room.collaborators.get(userId);
              if (user) {
                user.selectedNodeIds = message.nodeIds;
                user.lastActive = Date.now();
                this.broadcastToRoom(
                  room,
                  {
                    type: 'selection-update',
                    userId,
                    nodeIds: message.nodeIds,
                  },
                  userId
                );
              }
            }
            break;

          case 'node-update':
            if (userId && room) {
              // Persist node update to canvas document asynchronously
              this.persistNodeUpdate(
                room.projectId,
                message.nodeId,
                message.updates
              ).catch((err) =>
                console.error('[RealTimeService] Failed to persist node update:', err)
              );
              this.broadcastToRoom(
                room,
                {
                  type: 'node-update',
                  userId,
                  nodeId: message.nodeId,
                  updates: message.updates,
                },
                userId
              );
            }
            break;

          case 'crdt-update':
            if (userId && room) {
              this.broadcastToRoom(
                room,
                { type: 'crdt-update', userId, data: message.data },
                userId
              );
            }
            break;

          case 'ping':
            if (userId && room) {
              const user = room.collaborators.get(userId);
              if (user) user.lastActive = Date.now();
            }
            this.sendToClient(ws, { type: 'pong' });
            break;
        }
      } catch (error) {
        console.error('WS Message Error:', error);
      }
    });

    ws.on('close', () => {
      if (userId && room) this.leaveCanvasRoom(room, userId);
    });
  }

  private joinCanvasRoom(
    projectId: string,
    userId: string,
    name: string,
    email: string,
    ws: WebSocket
  ): CanvasRoom {
    let room = this.canvasRooms.get(projectId);
    if (!room) {
      room = { projectId, collaborators: new Map(), lastActivity: Date.now() };
      this.canvasRooms.set(projectId, room);
    }

    // Pick color
    const existingColors = Array.from(room.collaborators.values()).map(
      (c) => c.color
    );
    const availableColors = this.colorPalette.filter(
      (c) => !existingColors.includes(c)
    );
    const color =
      availableColors[0] ||
      this.colorPalette[room.collaborators.size % this.colorPalette.length];

    room.collaborators.set(userId, {
      id: userId,
      name,
      email,
      color,
      cursor: null,
      selectedNodeIds: [],
      lastActive: Date.now(),
      ws,
    });

    room.lastActivity = Date.now();
    return room;
  }

  private leaveCanvasRoom(room: CanvasRoom, userId: string) {
    if (room.collaborators.delete(userId)) {
      this.broadcastToRoom(room, { type: 'user-left', userId });
      if (room.collaborators.size === 0) {
        // Keep room for a bit? Or delete immediately?
        // Cleanup interval handles it.
      }
    }
  }

  // =========================================================================
  // Notification Logic
  // =========================================================================

  private handleNotificationConnection(ws: WebSocket, req: FastifyRequest) {
    this.notificationClients.add(ws);

    ws.on('message', (data) => {
      // Handle subscriptions, etc.
      try {
        const msg = JSON.parse(data.toString());
        if (msg.type === 'ping') ws.send(JSON.stringify({ type: 'pong' }));
      } catch {}
    });

    ws.on('close', () => {
      this.notificationClients.delete(ws);
    });
  }

  public broadcastNotification(payload: unknown) {
    const msg = JSON.stringify({ type: 'notification', payload });
    for (const client of this.notificationClients) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(msg);
      }
    }
  }

  // =========================================================================
  // Utilities
  // =========================================================================

  private broadcastToRoom(
    room: CanvasRoom,
    message: ServerMessage,
    excludeUserId?: string
  ) {
    const data = JSON.stringify(message);
    for (const [id, collaborator] of room.collaborators) {
      if (
        id !== excludeUserId &&
        collaborator.ws.readyState === WebSocket.OPEN
      ) {
        collaborator.ws.send(data);
      }
    }
  }

  private sendToClient(ws: WebSocket, message: ServerMessage) {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(message));
    }
  }

  private sanitizeUser(user: CollaboratorInfo): Omit<CollaboratorInfo, 'ws'> {
    const { id, name, email, color, cursor, selectedNodeIds, lastActive } =
      user;
    return { id, name, email, color, cursor, selectedNodeIds, lastActive };
  }

  /**
   * Persist a node update to the canvas document in the database.
   * Loads the current document content, patches the target node, and saves.
   */
  private async persistNodeUpdate(
    projectId: string,
    nodeId: string,
    updates: Record<string, unknown>
  ): Promise<void> {
    try {
      // Dynamic import to avoid circular dependency at module load time
      const { getPrismaClient } = await import('../database/client');
      const prisma = getPrismaClient();

      // Find the canvas document for this project
      const doc = await (prisma as unknown).canvasDocument.findFirst({
        where: { projectId },
        orderBy: { updatedAt: 'desc' },
      });

      if (!doc) {
        console.warn(
          `[RealTimeService] No canvas document found for project ${projectId}`
        );
        return;
      }

      // Parse existing content, patch the node, and save
      let content: Record<string, unknown> = {};
      try {
        content =
          typeof doc.content === 'string'
            ? JSON.parse(doc.content)
            : doc.content ?? {};
      } catch {
        content = {};
      }

      // Apply node update: store under nodes[nodeId]
      const nodes = (content.nodes as Record<string, unknown>) ?? {};
      nodes[nodeId] = { ...(nodes[nodeId] as Record<string, unknown> ?? {}), ...updates };
      content.nodes = nodes;

      await (prisma as unknown).canvasDocument.update({
        where: { id: doc.id },
        data: { content: JSON.stringify(content) },
      });
    } catch (error) {
      console.error('[RealTimeService] persistNodeUpdate error:', error);
      throw error;
    }
  }

  private checkHeartbeats() {
    // Ping logic...
  }

  private cleanupInactiveRooms() {
    const now = Date.now();
    for (const [projectId, room] of this.canvasRooms) {
      if (room.collaborators.size === 0 && now - room.lastActivity > 3600000) {
        // 1 hour inactive
        this.canvasRooms.delete(projectId);
      }
    }
  }
}
