/**
 * Persona Sync WebSocket Handler
 *
 * Purpose:
 * Provides real-time synchronization of persona preferences across multiple tabs/devices.
 * When a user updates their persona configuration, all connected clients are notified.
 *
 * Features:
 * - JWT-based authentication
 * - Workspace-based rooms (only users in same workspace receive updates)
 * - Optimistic update pattern (sender excludes self from broadcast)
 * - Auto-reconnection support
 * - Error handling with structured logging
 *
 * Events:
 * - persona:updated - Emitted when persona preference is updated
 * - persona:deleted - Emitted when persona preference is deleted
 * - persona:sync-request - Client requests full sync (on reconnect)
 *
 * Architecture:
 * Client updates persona → HTTP POST to API → API broadcasts via Socket.IO
 * → All clients in workspace room receive update → React Query cache invalidated
 *
 * @doc.type module
 * @doc.purpose WebSocket handler for real-time persona sync
 * @doc.layer product
 * @doc.pattern Event-Driven
 */

import type { FastifyInstance } from 'fastify';
import type { Server as SocketIOServer, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import { appConfig } from '../config/index.js';

/**
 * Persona update event payload
 */
export interface PersonaUpdateEvent {
    workspaceId: string;
    userId: string;
    activeRoles: string[];
    preferences: Record<string, unknown>;
    timestamp: string;
}

/**
 * Persona delete event payload
 */
export interface PersonaDeleteEvent {
    workspaceId: string;
    userId: string;
    timestamp: string;
}

/**
 * JWT token payload (for authentication)
 */
interface TokenPayload {
    userId: string;
    email: string;
    iat: number;
    exp: number;
}

/**
 * Extended Socket with authenticated user info
 */
interface AuthenticatedSocket extends Socket {
    userId?: string;
    email?: string;
}

/**
 * Setup Socket.IO server for persona synchronization
 *
 * @param io Socket.IO server instance
 * @param fastify Fastify instance for logging
 */
export function setupPersonaSync(io: SocketIOServer, fastify: FastifyInstance) {
    // Create a namespace for persona sync to isolate authentication
    const personaNamespace = io.of('/persona');

    // Middleware: JWT authentication (only for persona namespace)
    personaNamespace.use((socket: AuthenticatedSocket, next) => {
        const token = socket.handshake.auth.token || socket.handshake.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return next(new Error('Authentication token required'));
        }

        try {
            const decoded = jwt.verify(token, appConfig.jwt.secret) as TokenPayload;
            socket.userId = decoded.userId;
            socket.email = decoded.email;
            next();
        } catch (error) {
            fastify.log.warn({ error }, 'WebSocket authentication failed');
            next(new Error('Invalid authentication token'));
        }
    });

    // Connection handler
    personaNamespace.on('connection', (socket: AuthenticatedSocket) => {
        fastify.log.info({ userId: socket.userId, socketId: socket.id }, 'Client connected to persona sync');

        // Join workspace room
        socket.on('persona:join-workspace', (workspaceId: string) => {
            socket.join(`workspace:${workspaceId}`);
            fastify.log.info(
                { userId: socket.userId, workspaceId, socketId: socket.id },
                'Client joined workspace room'
            );

            // Acknowledge join
            socket.emit('persona:workspace-joined', { workspaceId });
        });

        // Leave workspace room
        socket.on('persona:leave-workspace', (workspaceId: string) => {
            socket.leave(`workspace:${workspaceId}`);
            fastify.log.info(
                { userId: socket.userId, workspaceId, socketId: socket.id },
                'Client left workspace room'
            );
        });

        // Sync request (on reconnect)
        socket.on('persona:sync-request', (workspaceId: string) => {
            fastify.log.info(
                { userId: socket.userId, workspaceId },
                'Client requested persona sync'
            );

            // Client should refetch from API (cache invalidation)
            // This event just triggers the refetch, no data sent here
            socket.emit('persona:sync-response', { workspaceId, status: 'refetch' });
        });

        // Disconnection handler
        socket.on('disconnect', (reason: string) => {
            fastify.log.info(
                { userId: socket.userId, socketId: socket.id, reason },
                'Client disconnected from persona sync'
            );
        });

        // Error handler
        socket.on('error', (error: Error) => {
            fastify.log.error(
                { userId: socket.userId, socketId: socket.id, error },
                'WebSocket error'
            );
        });
    });

    fastify.log.info('Persona sync WebSocket handler initialized');
}

/**
 * Broadcast persona update to all clients in workspace (except sender)
 *
 * @param io Socket.IO server instance
 * @param event Persona update event
 * @param senderSocketId Socket ID of the sender (to exclude from broadcast)
 */
export function broadcastPersonaUpdate(
    io: SocketIOServer,
    event: PersonaUpdateEvent,
    senderSocketId?: string
) {
    const room = `workspace:${event.workspaceId}`;

    if (senderSocketId) {
        // Exclude sender (they already have optimistic update)
        io.to(room).except(senderSocketId).emit('persona:updated', event);
    } else {
        // Broadcast to all (server-side update)
        io.to(room).emit('persona:updated', event);
    }
}

/**
 * Broadcast persona deletion to all clients in workspace (except sender)
 *
 * @param io Socket.IO server instance
 * @param event Persona delete event
 * @param senderSocketId Socket ID of the sender (to exclude from broadcast)
 */
export function broadcastPersonaDelete(
    io: SocketIOServer,
    event: PersonaDeleteEvent,
    senderSocketId?: string
) {
    const room = `workspace:${event.workspaceId}`;

    if (senderSocketId) {
        io.to(room).except(senderSocketId).emit('persona:deleted', event);
    } else {
        io.to(room).emit('persona:deleted', event);
    }
}
