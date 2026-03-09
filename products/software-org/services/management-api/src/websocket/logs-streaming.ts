/**
 * WebSocket handler for real-time logs streaming
 * 
 * Provides real-time log streaming to connected clients
 * with filtering and automatic reconnection support.
 */

import type { Server as SocketIOServer } from 'socket.io';
import type { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';

interface LogEntry {
    id: string;
    tenantId: string;
    timestamp: string;
    level: 'error' | 'warn' | 'info' | 'debug';
    source: string;
    message: string;
    metadata?: Record<string, unknown>;
}

/**
 * Setup WebSocket handlers for logs streaming
 */
export function setupLogsStreaming(io: SocketIOServer, fastify: FastifyInstance) {
    const logsNamespace = io.of('/observe/logs');

    logsNamespace.on('connection', (socket) => {
        fastify.log.info(`[Logs WS] Client connected: ${socket.id}`);

        let streamInterval: NodeJS.Timeout | null = null;
        let heartbeatInterval: NodeJS.Timeout | null = null;
        let clientTenantId = 'acme-payments-id';

        // Handle subscription to logs stream
        socket.on('subscribe', async (data: { 
            tenantId: string; 
            filters?: { level?: string; source?: string; search?: string } 
        }) => {
            fastify.log.info({ data }, `[Logs WS] Client ${socket.id} subscribed`);
            
            clientTenantId = data.tenantId || clientTenantId;
            const filters = data.filters || {};

            // Clear existing interval if any
            if (streamInterval) {
                clearInterval(streamInterval);
            }

            // Send initial batch of logs from database
            try {
                const where = {
                    tenantId: clientTenantId,
                    ...(filters.level ? { level: filters.level } : {}),
                    ...(filters.source ? { source: filters.source } : {}),
                    ...(filters.search
                        ? {
                            message: {
                                contains: filters.search,
                                mode: 'insensitive' as const,
                            },
                        }
                        : {}),
                };

                const logs = await prisma.logEntry.findMany({
                    where,
                    orderBy: { timestamp: 'desc' },
                    take: 50,
                });

                const initialLogs: LogEntry[] = logs.map((log) => ({
                    id: log.id,
                    tenantId: log.tenantId,
                    timestamp: log.timestamp.toISOString(),
                    level: log.level as LogEntry['level'],
                    source: log.source,
                    message: log.message,
                    metadata: log.metadata as Record<string, unknown> | undefined,
                }));

                socket.emit('logs:batch', { logs: initialLogs });
            } catch (error) {
                fastify.log.error(error, '[Logs WS] Error fetching initial logs');
                // Send empty batch on error
                socket.emit('logs:batch', { logs: [] });
            }

            // Start streaming new logs every 1-3 seconds
            streamInterval = setInterval(async () => {
                try {
                    // Query for recent logs (last 5 seconds)
                    const where = {
                        tenantId: clientTenantId,
                        timestamp: { gte: new Date(Date.now() - 5000) },
                        ...(filters.level ? { level: filters.level } : {}),
                        ...(filters.source ? { source: filters.source } : {}),
                        ...(filters.search
                            ? {
                                message: {
                                    contains: filters.search,
                                    mode: 'insensitive' as const,
                                },
                            }
                            : {}),
                    };

                    const recentLogs = await prisma.logEntry.findMany({
                        where,
                        orderBy: { timestamp: 'desc' },
                        take: 5,
                    });

                    for (const log of recentLogs) {
                        const newLog: LogEntry = {
                            id: log.id,
                            tenantId: log.tenantId,
                            timestamp: log.timestamp.toISOString(),
                            level: log.level as LogEntry['level'],
                            source: log.source,
                            message: log.message,
                            metadata: log.metadata as Record<string, unknown> | undefined,
                        };

                        socket.emit('logs:new', { log: newLog });
                        fastify.log.debug({ logId: newLog.id }, `[Logs WS] Sent log to ${socket.id}`);
                    }
                } catch (error) {
                    fastify.log.error(error, '[Logs WS] Error fetching new logs');
                }
            }, Math.random() * 2000 + 1000); // 1-3 seconds
        });

        // Handle filter updates
        socket.on('updateFilters', (filters: { level?: string; source?: string; search?: string }) => {
            fastify.log.info({ filters }, `[Logs WS] Client ${socket.id} updated filters`);
            // Re-subscribe with new filters
            socket.emit('filters:updated', { filters });
        });

        // Handle unsubscribe
        socket.on('unsubscribe', () => {
            fastify.log.info(`[Logs WS] Client ${socket.id} unsubscribed`);
            if (streamInterval) {
                clearInterval(streamInterval);
                streamInterval = null;
            }
        });

        // Heartbeat mechanism
        heartbeatInterval = setInterval(() => {
            socket.emit('heartbeat', { timestamp: new Date().toISOString() });
        }, 30000); // 30 seconds

        socket.on('pong', () => {
            fastify.log.debug(`[Logs WS] Received pong from ${socket.id}`);
        });

        // Handle disconnection
        socket.on('disconnect', (reason) => {
            fastify.log.info({ reason }, `[Logs WS] Client ${socket.id} disconnected`);
            
            if (streamInterval) {
                clearInterval(streamInterval);
            }
            if (heartbeatInterval) {
                clearInterval(heartbeatInterval);
            }
        });

        // Handle errors
        socket.on('error', (error) => {
            fastify.log.error(error, `[Logs WS] Socket error for ${socket.id}`);
        });
    });

    fastify.log.info('[Logs WS] WebSocket handler initialized');
}
