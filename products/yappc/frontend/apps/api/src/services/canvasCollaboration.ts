/**
 * Canvas Collaboration WebSocket Service
 * 
 * Manages real-time collaboration for canvas documents.
 * Handles presence, cursor tracking, selection awareness, and live updates.
 * 
 * @doc.type service
 * @doc.purpose Real-time canvas collaboration
 * @doc.layer product
 * @doc.pattern WebSocket Service
 */
import { FastifyInstance } from 'fastify';
import { WebSocket } from 'ws';

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

// Message types
export type ClientMessage =
    | { type: 'join'; userId: string; userName: string; userEmail: string }
    | { type: 'leave' }
    | { type: 'cursor-update'; x: number; y: number }
    | { type: 'selection-update'; nodeIds: string[] }
    | { type: 'node-update'; nodeId: string; updates: Record<string, unknown> }
    | { type: 'ping' };

export type ServerMessage =
    | { type: 'user-joined'; user: CollaboratorInfo }
    | { type: 'user-left'; userId: string }
    | { type: 'users-list'; users: Omit<CollaboratorInfo, 'ws'>[] }
    | { type: 'cursor-update'; userId: string; x: number; y: number }
    | { type: 'selection-update'; userId: string; nodeIds: string[] }
    | { type: 'node-update'; userId: string; nodeId: string; updates: Record<string, unknown> }
    | { type: 'pong' };

export class CanvasCollaborationService {
    private rooms = new Map<string, CanvasRoom>();
    private heartbeatInterval: NodeJS.Timeout | null = null;
    private cleanupInterval: NodeJS.Timeout | null = null;

    // User color palette for visual distinction
    private colorPalette = [
        '#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8',
        '#F7DC6F', '#BB8FCE', '#85C1E2', '#F8B739', '#52B788',
    ];

    constructor() {
        // Start heartbeat monitoring (every 30s)
        this.heartbeatInterval = setInterval(() => {
            this.checkHeartbeats();
        }, 30000);

        // Start cleanup of inactive rooms (every 5 minutes)
        this.cleanupInterval = setInterval(() => {
            this.cleanupInactiveRooms();
        }, 300000);
    }

    /**
     * Register WebSocket routes with Fastify
     */
    public registerRoutes(fastify: FastifyInstance) {
        // WebSocket endpoint for canvas collaboration
        fastify.register(async (instance) => {
            instance.get('/canvas/:projectId', { websocket: true }, (connection, req) => {
                const { projectId } = req.params as { projectId: string };
                this.handleConnection(projectId, connection.socket);
            });

            // Compatibility alias (legacy docs): /ws/canvas/:projectId
            instance.get('/ws/canvas/:projectId', { websocket: true }, (connection, req) => {
                const { projectId } = req.params as { projectId: string };
                this.handleConnection(projectId, connection.socket);
            });

            // Compatibility hint endpoint (legacy docs): /ws
            instance.get('/ws', { websocket: true }, (connection) => {
                try {
                    connection.socket.send(
                        JSON.stringify({
                            type: 'error',
                            message: 'Unsupported WebSocket endpoint. Use /ws/canvas/:projectId',
                        })
                    );
                } finally {
                    connection.socket.close(1008, 'Unsupported WebSocket endpoint');
                }
            });
        });
    }

    /**
     * Handle new WebSocket connection
     */
    private handleConnection(projectId: string, ws: WebSocket) {
        let userId: string | null = null;
        let room: CanvasRoom | null = null;

        ws.on('message', (data: Buffer) => {
            try {
                const message = JSON.parse(data.toString()) as ClientMessage;

                switch (message.type) {
                    case 'join':
                        userId = message.userId;
                        room = this.joinRoom(projectId, message.userId, message.userName, message.userEmail, ws);
                        this.broadcastUserJoined(room, message.userId);
                        this.sendUsersList(ws, room);
                        break;

                    case 'leave':
                        if (userId && room) {
                            this.leaveRoom(room, userId);
                        }
                        break;

                    case 'cursor-update':
                        if (userId && room) {
                            this.handleCursorUpdate(room, userId, message.x, message.y);
                        }
                        break;

                    case 'selection-update':
                        if (userId && room) {
                            this.handleSelectionUpdate(room, userId, message.nodeIds);
                        }
                        break;

                    case 'node-update':
                        if (userId && room) {
                            this.handleNodeUpdate(room, userId, message.nodeId, message.updates);
                        }
                        break;

                    case 'ping':
                        if (userId && room) {
                            this.handlePing(room, userId, ws);
                        }
                        break;
                }
            } catch (error) {
                console.error('Failed to process WebSocket message:', error);
            }
        });

        ws.on('close', () => {
            if (userId && room) {
                this.leaveRoom(room, userId);
            }
        });

        ws.on('error', (error) => {
            console.error('WebSocket error:', error);
            if (userId && room) {
                this.leaveRoom(room, userId);
            }
        });
    }

    /**
     * Add user to collaboration room
     */
    private joinRoom(
        projectId: string,
        userId: string,
        userName: string,
        userEmail: string,
        ws: WebSocket
    ): CanvasRoom {
        // Get or create room
        let room = this.rooms.get(projectId);
        if (!room) {
            room = {
                projectId,
                collaborators: new Map(),
                lastActivity: Date.now(),
            };
            this.rooms.set(projectId, room);
        }

        // Assign color (reuse if user was here before, else pick next)
        const existingColors = Array.from(room.collaborators.values()).map(c => c.color);
        const availableColors = this.colorPalette.filter(c => !existingColors.includes(c));
        const color = availableColors[0] || this.colorPalette[room.collaborators.size % this.colorPalette.length];

        // Add collaborator
        const collaborator: CollaboratorInfo & { ws: WebSocket } = {
            id: userId,
            name: userName,
            email: userEmail,
            color,
            cursor: null,
            selectedNodeIds: [],
            lastActive: Date.now(),
            ws,
        };

        room.collaborators.set(userId, collaborator);
        room.lastActivity = Date.now();

        return room;
    }

    /**
     * Remove user from collaboration room
     */
    private leaveRoom(room: CanvasRoom, userId: string) {
        const collaborator = room.collaborators.get(userId);
        if (!collaborator) return;

        room.collaborators.delete(userId);
        room.lastActivity = Date.now();

        // Broadcast user left
        this.broadcast(room, { type: 'user-left', userId }, userId);

        // Clean up empty rooms
        if (room.collaborators.size === 0) {
            this.rooms.delete(room.projectId);
        }
    }

    /**
     * Broadcast user joined event
     */
    private broadcastUserJoined(room: CanvasRoom, userId: string) {
        const collaborator = room.collaborators.get(userId);
        if (!collaborator) return;

        const { ws, ...userInfo } = collaborator;
        this.broadcast(room, { type: 'user-joined', user: userInfo }, userId);
    }

    /**
     * Send list of current users to a specific connection
     */
    private sendUsersList(ws: WebSocket, room: CanvasRoom) {
        const users = Array.from(room.collaborators.values()).map(({ ws: _, ...user }) => user);
        this.send(ws, { type: 'users-list', users });
    }

    /**
     * Handle cursor position update
     */
    private handleCursorUpdate(room: CanvasRoom, userId: string, x: number, y: number) {
        const collaborator = room.collaborators.get(userId);
        if (!collaborator) return;

        collaborator.cursor = { x, y };
        collaborator.lastActive = Date.now();
        room.lastActivity = Date.now();

        this.broadcast(room, { type: 'cursor-update', userId, x, y }, userId);
    }

    /**
     * Handle selection update
     */
    private handleSelectionUpdate(room: CanvasRoom, userId: string, nodeIds: string[]) {
        const collaborator = room.collaborators.get(userId);
        if (!collaborator) return;

        collaborator.selectedNodeIds = nodeIds;
        collaborator.lastActive = Date.now();
        room.lastActivity = Date.now();

        this.broadcast(room, { type: 'selection-update', userId, nodeIds }, userId);
    }

    /**
     * Handle node update
     */
    private handleNodeUpdate(room: CanvasRoom, userId: string, nodeId: string, updates: unknown) {
        const collaborator = room.collaborators.get(userId);
        if (!collaborator) return;

        collaborator.lastActive = Date.now();
        room.lastActivity = Date.now();

        this.broadcast(room, { type: 'node-update', userId, nodeId, updates }, userId);
    }

    /**
     * Handle ping (heartbeat)
     */
    private handlePing(room: CanvasRoom, userId: string, ws: WebSocket) {
        const collaborator = room.collaborators.get(userId);
        if (!collaborator) return;

        collaborator.lastActive = Date.now();
        room.lastActivity = Date.now();

        this.send(ws, { type: 'pong' });
    }

    /**
     * Broadcast message to all collaborators except sender
     */
    private broadcast(room: CanvasRoom, message: ServerMessage, excludeUserId?: string) {
        const data = JSON.stringify(message);
        room.collaborators.forEach((collaborator, userId) => {
            if (userId !== excludeUserId && collaborator.ws.readyState === WebSocket.OPEN) {
                collaborator.ws.send(data);
            }
        });
    }

    /**
     * Send message to specific WebSocket
     */
    private send(ws: WebSocket, message: ServerMessage) {
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(message));
        }
    }

    /**
     * Check heartbeats and remove stale connections
     */
    private checkHeartbeats() {
        const now = Date.now();
        const staleThreshold = 60000; // 1 minute

        this.rooms.forEach((room) => {
            const staleUsers: string[] = [];

            room.collaborators.forEach((collaborator, userId) => {
                if (now - collaborator.lastActive > staleThreshold) {
                    staleUsers.push(userId);
                }
            });

            // Remove stale users
            staleUsers.forEach(userId => {
                this.leaveRoom(room, userId);
            });
        });
    }

    /**
     * Clean up inactive rooms (no activity for 30 minutes)
     */
    private cleanupInactiveRooms() {
        const now = Date.now();
        const inactiveThreshold = 1800000; // 30 minutes

        const inactiveRooms: string[] = [];

        this.rooms.forEach((room, projectId) => {
            if (room.collaborators.size === 0 && now - room.lastActivity > inactiveThreshold) {
                inactiveRooms.push(projectId);
            }
        });

        inactiveRooms.forEach(projectId => {
            this.rooms.delete(projectId);
        });

        if (inactiveRooms.length > 0) {
            console.log(`Cleaned up ${inactiveRooms.length} inactive rooms`);
        }
    }

    /**
     * Shutdown service
     */
    public shutdown() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
        }
        if (this.cleanupInterval) {
            clearInterval(this.cleanupInterval);
        }

        // Close all connections
        this.rooms.forEach((room) => {
            room.collaborators.forEach((collaborator) => {
                collaborator.ws.close();
            });
        });

        this.rooms.clear();
    }
}
