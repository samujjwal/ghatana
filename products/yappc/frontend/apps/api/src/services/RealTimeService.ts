/**
 * Real-Time Service
 *
 * Unified WebSocket service for YAPPC.
 * Handles Canvas Collaboration, Notifications, and Activity Streams.
 *
 * <p>Redis-backed storage enables horizontal scaling. Canvas room state and
 * collaborator metadata are persisted in Redis, while WebSocket connections
 * remain local to each instance for efficient broadcasting.</p>
 *
 * @doc.type service
 * @doc.purpose Real-time communication with Redis-backed state
 * @doc.layer implementation
 */
import { FastifyInstance, FastifyRequest } from 'fastify';
import { WebSocket } from 'ws';
import { redisCanvasRoomStore } from './RedisCanvasRoomStore';
import type { CollaboratorInfo as RedisCollaboratorInfo } from './RedisCanvasRoomStore';

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
  | { type: 'auth'; token: string } // Added for Auth
  // P1-10: Realtime Version History
  | { type: 'save-version'; versionId: string; label?: string; description?: string }
  | { type: 'get-versions'; limit?: number }
  | { type: 'restore-version'; versionId: string };

export type ServerMessage =
  | { type: 'user-joined'; user: CollaboratorInfo }
  | { type: 'user-left'; userId: string }
  | { type: 'users-list'; users: Omit<CollaboratorInfo, 'ws'>[] }
  | { type: 'cursor-update'; userId: string; x: number; y: number }
  | { type: 'selection-update'; userId: string; nodeIds: string[] }
  | {
      type: 'node-update';
      userId: string;
      nodeId: string;
      updates: Record<string, unknown>;
    }
  | { type: 'crdt-update'; userId: string; data: string }
  | { type: 'pong' }
  | { type: 'error'; message: string }
  | { type: 'notification'; payload: Record<string, unknown> }
  // P1-10: Realtime Version History
  | { type: 'version-saved'; versionId: string; timestamp: number; savedBy: string }
  | { type: 'versions-list'; versions: VersionInfo[] }
  | { type: 'version-restored'; versionId: string; timestamp: number; restoredBy: string }
  | { type: 'version-error'; message: string };

// P1-10: Version History Types
export interface VersionInfo {
  versionId: string;
  timestamp: number;
  savedBy: string;
  savedByName: string;
  label?: string;
  description?: string;
  nodeCount: number;
  connectionCount: number;
}

// =============================================================================
// Service Implementation
// =============================================================================

export class RealTimeService {
  // Local WebSocket connections (cannot be serialized to Redis)
  private canvasConnections = new Map<string, Map<string, WebSocket>>();
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

  private connectRedisCanvasRoomStore(): void {
    void (async () => {
      try {
        await redisCanvasRoomStore.connect();
      } catch (err) {
        console.error('[RealTimeService] Failed to connect to Redis:', err);
      }
    })();
  }

  private persistNodeUpdateInBackground(
    projectId: string,
    nodeId: string,
    updates: Record<string, unknown>
  ): void {
    void (async () => {
      try {
        await this.persistNodeUpdate(projectId, nodeId, updates);
      } catch (err) {
        console.error(
          '[RealTimeService] Failed to persist node update:',
          err
        );
      }
    })();
  }

  constructor() {
    // Initialize Redis connection
    this.connectRedisCanvasRoomStore();

    // Start heartbeat monitoring (every 30s)
    this.heartbeatInterval = setInterval(() => this.checkHeartbeats(), 30000);
    // Start cleanup (every 5m)
    this.cleanupInterval = setInterval(
      () => this.cleanupInactiveRooms(),
      300000
    );
  }

  public async shutdown() {
    if (this.heartbeatInterval) clearInterval(this.heartbeatInterval);
    if (this.cleanupInterval) clearInterval(this.cleanupInterval);
    this.canvasConnections.clear();
    this.notificationClients.clear();
    await redisCanvasRoomStore.disconnect();
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
    let userEmail: string | null = null;
    let userName: string | null = null;
    let room: CanvasRoom | null = null;
    let isAuthenticated = false;

    ws.on('message', async (data: Buffer) => {
      try {
        const message = JSON.parse(data.toString()) as ClientMessage;

        switch (message.type) {
          case 'auth': {
            // Validate JWT token from client - REQUIRED before any other operations
            try {
              const jwt = await import('jsonwebtoken');
              const { getJwtAccessSecret } = await import('./auth/jwt-config');
              const secret = getJwtAccessSecret();
              const decoded = jwt.default.verify(message.token, secret) as {
                userId: string;
                email: string;
                role: string;
                name?: string;
              };
              userId = decoded.userId;
              userEmail = decoded.email;
              userName = decoded.name || decoded.email.split('@')[0];
              isAuthenticated = true;

              // Verify project access
              const { checkProjectAccess } = await import('../middleware/resource-auth.middleware');
              const access = await checkProjectAccess(userId, projectId);

              if (!access.allowed) {
                this.sendToClient(ws, {
                  type: 'error',
                  message: 'Access denied: you do not have permission to access this project',
                });
                ws.close(4003, 'Access denied');
                return;
              }

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

          case 'join': {
            // Require authentication before join
            if (!isAuthenticated || !userId) {
              this.sendToClient(ws, {
                type: 'error',
                message: 'Authentication required: send auth message first',
              });
              ws.close(4001, 'Authentication required');
              return;
            }

            // Derive identity from verified token, NOT from client input
            // Client can provide display name preference, but email comes from token
            const displayName = message.userName || userName || userEmail?.split('@')[0] || 'Unknown';
            const displayEmail = userEmail || message.userEmail || 'unknown@example.com';

            room = await this.joinCanvasRoom(
              projectId,
              userId, // Always use token-derived userId
              displayName,
              displayEmail,
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
          }

          case 'leave':
            if (userId && room) await this.leaveCanvasRoom(room, userId);
            break;

          case 'cursor-update':
            if (userId && room) {
              // Update local state
              const user = room.collaborators.get(userId);
              if (user) {
                user.cursor = { x: message.x, y: message.y };
                user.lastActive = Date.now();
                // Sync to Redis
                await redisCanvasRoomStore.updateCursor(
                  projectId,
                  userId,
                  { x: message.x, y: message.y }
                );
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
                // Sync to Redis
                await redisCanvasRoomStore.updateSelection(
                  projectId,
                  userId,
                  message.nodeIds
                );
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
              this.persistNodeUpdateInBackground(
                room.projectId,
                message.nodeId,
                message.updates
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

          // P1-10: Realtime Version History Handlers
          case 'save-version':
            if (userId && room && userEmail) {
              await this.handleSaveVersion(
                projectId,
                message.versionId,
                userId,
                userEmail,
                message.label,
                message.description,
                ws
              );
            }
            break;

          case 'get-versions':
            if (userId && room) {
              await this.handleGetVersions(projectId, message.limit ?? 10, ws);
            }
            break;

          case 'restore-version':
            if (userId && room && userEmail) {
              await this.handleRestoreVersion(
                projectId,
                message.versionId,
                userId,
                userEmail,
                ws
              );
            }
            break;
        }
      } catch (error) {
        console.error('WS Message Error:', error);
      }
    });

    ws.on('close', async () => {
      if (userId && room) await this.leaveCanvasRoom(room, userId);
    });
  }

  private async joinCanvasRoom(
    projectId: string,
    userId: string,
    name: string,
    email: string,
    ws: WebSocket
  ): Promise<CanvasRoom> {
    // Get or create local WebSocket connection map for this project
    if (!this.canvasConnections.has(projectId)) {
      this.canvasConnections.set(projectId, new Map());
    }
    const connections = this.canvasConnections.get(projectId)!;
    connections.set(userId, ws);

    // Get current room state from Redis
    const roomData = await redisCanvasRoomStore.getRoom(projectId);
    
    // Pick color based on existing collaborators
    const existingColors = roomData
      ? Object.values(roomData.collaborators).map((c) => c.color)
      : [];
    const availableColors = this.colorPalette.filter(
      (c) => !existingColors.includes(c)
    );
    const color =
      availableColors[0] ||
      this.colorPalette[(existingColors.length) % this.colorPalette.length];

    // Create collaborator info
    const collaborator: RedisCollaboratorInfo = {
      id: userId,
      name,
      email,
      color,
      cursor: null,
      selectedNodeIds: [],
      lastActive: Date.now(),
    };

    // Update Redis with new collaborator
    await redisCanvasRoomStore.addCollaborator(projectId, collaborator);

    // Return in-memory room structure for local operations
    const room: CanvasRoom = {
      projectId,
      collaborators: new Map(),
      lastActivity: Date.now(),
    };

    // Populate collaborators from Redis
    if (roomData) {
      for (const [id, info] of Object.entries(roomData.collaborators)) {
        // Add WebSocket connection if user is connected to this instance
        const userWs = connections.get(id);
        room.collaborators.set(id, {
          ...info,
          ws: userWs || (new WebSocket('') as any), // Placeholder for users on other instances
        });
      }
    }
    
    // Add current user with their WebSocket
    room.collaborators.set(userId, { ...collaborator, ws });

    return room;
  }

  private async leaveCanvasRoom(room: CanvasRoom, userId: string) {
    // Remove from local connections
    const connections = this.canvasConnections.get(room.projectId);
    if (connections) {
      connections.delete(userId);
      if (connections.size === 0) {
        this.canvasConnections.delete(room.projectId);
      }
    }

    // Remove from Redis
    await redisCanvasRoomStore.removeCollaborator(room.projectId, userId);

    // Broadcast to remaining local connections
    this.broadcastToRoom(room, { type: 'user-left', userId });
  }

  // =========================================================================
  // Notification Logic
  // =========================================================================

  // P2-10: Secure Notification WebSocket - track authenticated clients with user info
  private authenticatedNotificationClients = new Map<
    WebSocket,
    { userId: string; email: string; workspaceIds: string[] }
  >();

  private handleNotificationConnection(ws: WebSocket, req: FastifyRequest) {
    // P2-10: Authentication is required before adding to notification clients
    let isAuthenticated = false;
    let userId: string | null = null;
    let userEmail: string | null = null;
    let userWorkspaces: string[] = [];

    ws.on('message', async (data: Buffer) => {
      try {
        const msg = JSON.parse(data.toString()) as
          | { type: 'auth'; token: string }
          | { type: 'ping' }
          | { type: 'subscribe'; workspaceId: string };

        switch (msg.type) {
          case 'auth': {
            // P2-10: Validate JWT token before allowing notification access
            try {
              const jwt = await import('jsonwebtoken');
              const { getJwtAccessSecret } = await import('./auth/jwt-config');
              const secret = getJwtAccessSecret();
              const decoded = jwt.default.verify(msg.token, secret) as {
                userId: string;
                email: string;
                role: string;
              };

              userId = decoded.userId;
              userEmail = decoded.email;

              // Get user's workspaces for scoped notifications
              const { getPrismaClient } = await import('../database/client');
              const prisma = getPrismaClient();
              const memberships = await prisma.workspaceMember.findMany({
                where: { userId },
                select: { workspaceId: true },
              });
              userWorkspaces = memberships.map((m) => m.workspaceId);

              // Store authenticated client
              this.authenticatedNotificationClients.set(ws, {
                userId,
                email: userEmail,
                workspaceIds: userWorkspaces,
              });
              isAuthenticated = true;

              this.sendToClient(ws, {
                type: 'notification',
                payload: { message: 'Authentication successful' },
              });
            } catch (err) {
              this.sendToClient(ws, {
                type: 'error',
                message: 'Authentication failed: invalid or expired token',
              });
              ws.close(4001, 'Authentication required');
            }
            break;
          }

          case 'ping':
            if (isAuthenticated) {
              this.sendToClient(ws, { type: 'pong' });
            }
            break;

          case 'subscribe':
            // P2-10: Verify workspace access before subscribing
            if (!isAuthenticated || !userId) {
              this.sendToClient(ws, {
                type: 'error',
                message: 'Authentication required to subscribe',
              });
              return;
            }

            if (!userWorkspaces.includes(msg.workspaceId)) {
              this.sendToClient(ws, {
                type: 'error',
                message: 'Access denied: not a member of this workspace',
              });
              return;
            }

            // Subscription successful
            this.sendToClient(ws, {
              type: 'notification',
              payload: { message: `Subscribed to workspace ${msg.workspaceId}` },
            });
            break;
        }
      } catch {
        // Invalid message format
      }
    });

    ws.on('close', () => {
      this.authenticatedNotificationClients.delete(ws);
    });

    // P2-10: Require auth within 10 seconds or close connection
    setTimeout(() => {
      if (!isAuthenticated && ws.readyState === WebSocket.OPEN) {
        this.sendToClient(ws, {
          type: 'error',
          message: 'Authentication timeout',
        });
        ws.close(4001, 'Authentication timeout');
      }
    }, 10000);
  }

  /**
   * P2-10: Broadcast notification only to authenticated, authorized clients
   * @param payload - Notification payload
   * @param options - Optional filters (workspaceId, userId)
   */
  public broadcastNotification(
    payload: unknown,
    options?: { workspaceId?: string; userId?: string }
  ) {
    const msg = JSON.stringify({ type: 'notification', payload });

    for (const [client, userInfo] of this.authenticatedNotificationClients) {
      if (client.readyState !== WebSocket.OPEN) continue;

      // P2-10: Filter by workspace if specified
      if (options?.workspaceId) {
        if (!userInfo.workspaceIds.includes(options.workspaceId)) {
          continue;
        }
      }

      // P2-10: Filter by user if specified
      if (options?.userId && userInfo.userId !== options.userId) {
        continue;
      }

      client.send(msg);
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
            : (doc.content ?? {});
      } catch {
        content = {};
      }

      // Apply node update: store under nodes[nodeId]
      const nodes = (content.nodes as Record<string, unknown>) ?? {};
      nodes[nodeId] = {
        ...((nodes[nodeId] as Record<string, unknown>) ?? {}),
        ...updates,
      };
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

  private async cleanupInactiveRooms() {
    // Clean up inactive rooms in Redis (older than 1 hour)
    const deleted = await redisCanvasRoomStore.cleanupInactiveRooms(3600000);
    if (deleted > 0) {
      console.info(`[RealTimeService] Cleaned up ${deleted} inactive rooms from Redis`);
    }

    // Clean up local connection maps for projects with no local connections
    const now = Date.now();
    for (const [projectId, connections] of this.canvasConnections) {
      if (connections.size === 0) {
        this.canvasConnections.delete(projectId);
      }
    }
  }

  // ============================================================================
  // P1-10: Realtime Version History Methods
  // ============================================================================

  /**
   * Handle save-version message from client
   * Saves current canvas state as a version snapshot
   */
  private async handleSaveVersion(
    projectId: string,
    versionId: string,
    userId: string,
    userEmail: string,
    label?: string,
    description?: string,
    ws?: WebSocket
  ): Promise<void> {
    try {
      const { getPrismaClient } = await import('../database/client');
      const prisma = getPrismaClient();

      // Get current canvas document
      const doc = await (prisma as unknown).canvasDocument.findFirst({
        where: { projectId },
        orderBy: { updatedAt: 'desc' },
      });

      if (!doc) {
        if (ws) {
          this.sendToClient(ws, {
            type: 'version-error',
            message: 'No canvas document found for this project',
          });
        }
        return;
      }

      // Parse content to get node/connection counts
      let content: Record<string, unknown> = {};
      try {
        content = typeof doc.content === 'string' ? JSON.parse(doc.content) : (doc.content ?? {});
      } catch {
        content = {};
      }

      const nodes = (content.nodes as Record<string, unknown>) ?? {};
      const connections = (content.connections as unknown[]) ?? [];

      // Create version record in Redis for realtime access
      const versionInfo: VersionInfo = {
        versionId,
        timestamp: Date.now(),
        savedBy: userId,
        savedByName: userEmail.split('@')[0],
        label,
        description,
        nodeCount: Object.keys(nodes).length,
        connectionCount: connections.length,
      };

      // Store version metadata in Redis (content stays in database)
      await redisCanvasRoomStore.saveVersion(projectId, versionInfo, doc.content);

      // Notify all collaborators in the room
      const room = this.canvasRooms.get(projectId);
      if (room) {
        this.broadcastToRoom(
          room,
          {
            type: 'version-saved',
            versionId,
            timestamp: versionInfo.timestamp,
            savedBy: userId,
          },
          userId
        );
      }

      // Also notify the sender
      if (ws) {
        this.sendToClient(ws, {
          type: 'version-saved',
          versionId,
          timestamp: versionInfo.timestamp,
          savedBy: userId,
        });
      }

      console.info(`[RealTimeService] Version saved: ${versionId} for project ${projectId}`);
    } catch (error) {
      console.error('[RealTimeService] handleSaveVersion error:', error);
      if (ws) {
        this.sendToClient(ws, {
          type: 'version-error',
          message: 'Failed to save version',
        });
      }
    }
  }

  /**
   * Handle get-versions message from client
   * Returns list of saved versions for the project
   */
  private async handleGetVersions(
    projectId: string,
    limit: number,
    ws: WebSocket
  ): Promise<void> {
    try {
      // Get versions from Redis
      const versions = await redisCanvasRoomStore.getVersions(projectId, limit);

      this.sendToClient(ws, {
        type: 'versions-list',
        versions: versions || [],
      });
    } catch (error) {
      console.error('[RealTimeService] handleGetVersions error:', error);
      this.sendToClient(ws, {
        type: 'version-error',
        message: 'Failed to retrieve versions',
      });
    }
  }

  /**
   * Handle restore-version message from client
   * Restores canvas to a previous version
   */
  private async handleRestoreVersion(
    projectId: string,
    versionId: string,
    userId: string,
    userEmail: string,
    ws: WebSocket
  ): Promise<void> {
    try {
      const { getPrismaClient } = await import('../database/client');
      const prisma = getPrismaClient();

      // Get version content from Redis
      const versionData = await redisCanvasRoomStore.getVersion(projectId, versionId);

      if (!versionData) {
        this.sendToClient(ws, {
          type: 'version-error',
          message: `Version ${versionId} not found`,
        });
        return;
      }

      // Update canvas document with restored content
      await (prisma as unknown).canvasDocument.updateMany({
        where: { projectId },
        data: {
          content: versionData.content,
          updatedAt: new Date(),
        },
      });

      // Notify all collaborators about the restore
      const room = this.canvasRooms.get(projectId);
      if (room) {
        this.broadcastToRoom(
          room,
          {
            type: 'version-restored',
            versionId,
            timestamp: Date.now(),
            restoredBy: userId,
          },
          userId
        );
      }

      // Notify the restorer
      this.sendToClient(ws, {
        type: 'version-restored',
        versionId,
        timestamp: Date.now(),
        restoredBy: userId,
      });

      console.info(`[RealTimeService] Version restored: ${versionId} for project ${projectId}`);
    } catch (error) {
      console.error('[RealTimeService] handleRestoreVersion error:', error);
      this.sendToClient(ws, {
        type: 'version-error',
        message: 'Failed to restore version',
      });
    }
  }

  // In-memory storage for canvas rooms (used for broadcasting)
  private canvasRooms = new Map<string, CanvasRoom>();
}
