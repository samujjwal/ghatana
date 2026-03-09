/**
 * @doc.type service
 * @doc.purpose Real-time cursor tracking and collaboration
 * @doc.layer product
 * @doc.pattern Real-time Service
 */

import type { FastifyInstance } from 'fastify';
import { EventEmitter } from 'events';
import { WebSocket } from 'ws';

export interface CursorPosition {
    x: number;
    y: number;
    timestamp: number;
    userId: string;
    userName: string;
    userColor: string;
    cursorType: 'pointer' | 'text' | 'move' | 'crosshair';
}

export interface CollaborationSession {
    id: string;
    contentId: string;
    participants: Map<string, CollaborationParticipant>;
    cursors: Map<string, CursorPosition>;
    createdAt: Date;
    lastActivity: Date;
}

export interface CollaborationParticipant {
    userId: string;
    userName: string;
    userColor: string;
    joinedAt: Date;
    isActive: boolean;
    cursor: CursorPosition | null;
    permissions: {
        canEdit: boolean;
        canComment: boolean;
        canDelete: boolean;
    };
}

/**
 * Real-time Collaboration Service
 * Handles cursor tracking, participant management, and real-time updates
 */
export class RealTimeCollaboration extends EventEmitter {
    private sessions: Map<string, CollaborationSession> = new Map();
    private userSessions: Map<string, string> = new Map(); // userId -> sessionId
    private webSockets: Map<string, WebSocket> = new Map();
    private cleanupInterval: NodeJS.Timeout | null = null;
    private readonly SESSION_TIMEOUT = 30 * 60 * 1000; // 30 minutes
    private readonly CURSOR_THROTTLE = 50; // 50ms throttle
    private cursorTimers: Map<string, NodeJS.Timeout> = new Map();

    constructor() {
        super();
        this.startCleanupInterval();
    }

    /**
     * Create or join a collaboration session
     */
    async joinSession(
        sessionId: string,
        contentId: string,
        participant: Omit<CollaborationParticipant, 'joinedAt' | 'isActive' | 'cursor'>
    ): Promise<CollaborationSession> {
        let session = this.sessions.get(sessionId);

        if (!session) {
            session = this.createSession(sessionId, contentId);
        }

        const fullParticipant: CollaborationParticipant = {
            ...participant,
            joinedAt: new Date(),
            isActive: true,
            cursor: null,
        };

        session.participants.set(participant.userId, fullParticipant);
        session.lastActivity = new Date();
        this.userSessions.set(participant.userId, sessionId);

        // Broadcast participant join
        this.broadcastToSession(sessionId, {
            type: 'participant_joined',
            participant: fullParticipant,
        });

        return session;
    }

    /**
     * Leave a collaboration session
     */
    async leaveSession(userId: string): Promise<void> {
        const sessionId = this.userSessions.get(userId);
        if (!sessionId) return;

        const session = this.sessions.get(sessionId);
        if (!session) return;

        const participant = session.participants.get(userId);
        if (participant) {
            participant.isActive = false;
            session.cursors.delete(userId);
        }

        this.userSessions.delete(userId);

        // Broadcast participant leave
        this.broadcastToSession(sessionId, {
            type: 'participant_left',
            userId,
        });

        // Clean up empty sessions
        if (session.participants.size === 0) {
            this.sessions.delete(sessionId);
        }
    }

    /**
     * Update cursor position
     */
    async updateCursor(
        userId: string,
        position: Omit<CursorPosition, 'timestamp'>
    ): Promise<void> {
        const sessionId = this.userSessions.get(userId);
        if (!sessionId) return;

        const session = this.sessions.get(sessionId);
        if (!session) return;

        const participant = session.participants.get(userId);
        if (!participant || !participant.isActive) return;

        const cursor: CursorPosition = {
            ...position,
            timestamp: Date.now(),
        };

        session.cursors.set(userId, cursor);
        participant.cursor = cursor;
        session.lastActivity = new Date();

        // Throttle cursor updates
        this.throttledBroadcastCursor(sessionId, cursor);
    }

    /**
     * Get session state
     */
    async getSessionState(sessionId: string): Promise<CollaborationSession | null> {
        const session = this.sessions.get(sessionId);
        return session ? { ...session } : null;
    }

    /**
     * Get user's active sessions
     */
    async getUserSessions(userId: string): Promise<string[]> {
        const sessions: string[] = [];
        for (const [sessionId, session] of this.sessions) {
            if (session.participants.has(userId)) {
                sessions.push(sessionId);
            }
        }
        return sessions;
    }

    /**
     * Register WebSocket connection
     */
    registerWebSocket(userId: string, ws: WebSocket): void {
        this.webSockets.set(userId, ws);

        ws.on('close', () => {
            this.webSockets.delete(userId);
            this.leaveSession(userId);
        });

        ws.on('error', (error: Error) => {
            console.error(`WebSocket error for user ${userId}:`, error);
            this.webSockets.delete(userId);
        });
    }

    /**
     * Send message to specific user
     */
    sendToUser(userId: string, message: any): void {
        const ws = this.webSockets.get(userId);
        if (ws && ws.readyState === 1) { // WebSocket.OPEN = 1
            ws.send(JSON.stringify(message));
        }
    }

    /**
     * Get collaboration statistics
     */
    async getStats(): Promise<{
        totalSessions: number;
        totalParticipants: number;
        activeParticipants: number;
        averageSessionDuration: number;
    }> {
        const totalSessions = this.sessions.size;
        let totalParticipants = 0;
        let activeParticipants = 0;
        let totalDuration = 0;

        for (const session of this.sessions.values()) {
            totalParticipants += session.participants.size;
            for (const participant of session.participants.values()) {
                if (participant.isActive) {
                    activeParticipants++;
                }
                totalDuration += Date.now() - participant.joinedAt.getTime();
            }
        }

        const averageSessionDuration = totalParticipants > 0 ? totalDuration / totalParticipants : 0;

        return {
            totalSessions,
            totalParticipants,
            activeParticipants,
            averageSessionDuration,
        };
    }

    /**
     * Create new collaboration session
     */
    private createSession(sessionId: string, contentId: string): CollaborationSession {
        const session: CollaborationSession = {
            id: sessionId,
            contentId,
            participants: new Map(),
            cursors: new Map(),
            createdAt: new Date(),
            lastActivity: new Date(),
        };

        this.sessions.set(sessionId, session);
        return session;
    }

    /**
     * Broadcast message to all participants in a session
     */
    private broadcastToSession(sessionId: string, message: any): void {
        const session = this.sessions.get(sessionId);
        if (!session) return;

        for (const userId of session.participants.keys()) {
            this.sendToUser(userId, message);
        }
    }

    /**
     * Throttled cursor broadcasting
     */
    private throttledBroadcastCursor(sessionId: string, cursor: CursorPosition): void {
        const key = `cursor_${sessionId}`;

        if (!this.cursorTimers.has(key)) {
            const timer = setTimeout(() => {
                this.broadcastToSession(sessionId, {
                    type: 'cursor_update',
                    cursor,
                });
                this.cursorTimers.delete(key);
            }, this.CURSOR_THROTTLE);
            this.cursorTimers.set(key, timer);
        }
    }

    /**
     * Start cleanup interval for inactive sessions
     */
    private startCleanupInterval(): void {
        this.cleanupInterval = setInterval(() => {
            const now = Date.now();
            const sessionsToRemove: string[] = [];

            for (const [sessionId, session] of this.sessions) {
                if (now - session.lastActivity.getTime() > this.SESSION_TIMEOUT) {
                    sessionsToRemove.push(sessionId);
                }
            }

            for (const sessionId of sessionsToRemove) {
                // Remove all participants from session
                const session = this.sessions.get(sessionId);
                if (session) {
                    for (const userId of session.participants.keys()) {
                        this.leaveSession(userId);
                    }
                }
            }
        }, 5 * 60 * 1000); // Check every 5 minutes
    }

    /**
     * Generate unique user color
     */
    static generateUserColor(userId: string): string {
        const colors = [
            '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
            '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2',
            '#F8B739', '#52B788', '#E76F51', '#8E44AD', '#3498DB',
        ];

        let hash = 0;
        for (let i = 0; i < userId.length; i++) {
            hash = userId.charCodeAt(i) + ((hash << 5) - hash);
        }

        return colors[Math.abs(hash) % colors.length];
    }

    /**
     * Generate cursor type based on context
     */
    static getCursorType(context: string): CursorPosition['cursorType'] {
        const contextLower = context.toLowerCase();

        if (contextLower.includes('text') || contextLower.includes('input')) {
            return 'text';
        }
        if (contextLower.includes('move') || contextLower.includes('drag')) {
            return 'move';
        }
        if (contextLower.includes('crosshair') || contextLower.includes('precise')) {
            return 'crosshair';
        }

        return 'pointer';
    }

    /**
     * Cleanup resources
     */
    destroy(): void {
        if (this.cleanupInterval) {
            clearInterval(this.cleanupInterval);
            this.cleanupInterval = null;
        }

        // Clear cursor timers
        for (const timer of this.cursorTimers.values()) {
            clearTimeout(timer);
        }
        this.cursorTimers.clear();

        // Close all WebSocket connections
        for (const [userId, ws] of this.webSockets) {
            try {
                ws.close();
            } catch (error) {
                console.error(`Error closing WebSocket for user ${userId}:`, error);
            }
        }

        this.webSockets.clear();
        this.sessions.clear();
        this.userSessions.clear();
    }
}

/**
 * Singleton instance
 */
export const realTimeCollaboration = new RealTimeCollaboration();
