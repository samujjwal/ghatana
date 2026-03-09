/**
 * WebSocket handler for real-time alerts streaming
 * 
 * Provides real-time alert notifications to connected clients
 * with automatic reconnection and heartbeat support.
 */

import type { Server as SocketIOServer } from 'socket.io';
import type { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import { notificationOrchestrator } from '../services/notification-orchestrator.js';

interface Alert {
    id: string;
    tenantId: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'active' | 'acknowledged' | 'resolved';
    title: string;
    message: string;
    source: string;
    relatedIncidents: string[];
    createdAt: string;
}

/**
 * Setup WebSocket handlers for alerts streaming
 */
export function setupAlertsStreaming(io: SocketIOServer, fastify: FastifyInstance) {
    const alertsNamespace = io.of('/observe/alerts');

    alertsNamespace.on('connection', (socket) => {
        fastify.log.info(`[Alerts WS] Client connected: ${socket.id}`);

        let streamInterval: NodeJS.Timeout | null = null;
        let heartbeatInterval: NodeJS.Timeout | null = null;
        let clientTenantId = 'acme-payments-id';

        // Handle subscription to alerts stream
        socket.on('subscribe', async (data: { tenantId: string; filters?: { severity?: string } }) => {
            fastify.log.info({ data }, `[Alerts WS] Client ${socket.id} subscribed`);
            
            clientTenantId = data.tenantId || clientTenantId;
            const filters = data.filters || {};

            // Clear existing interval if any
            if (streamInterval) {
                clearInterval(streamInterval);
            }

            // Send initial batch of alerts from database
            try {
                const where = {
                    tenantId: clientTenantId,
                    ...(filters.severity ? { severity: filters.severity } : {}),
                };

                const alerts = await prisma.alert.findMany({
                    where,
                    orderBy: { createdAt: 'desc' },
                    take: 10,
                });

                const initialAlerts: Alert[] = alerts.map((alert) => ({
                    id: alert.id,
                    tenantId: alert.tenantId,
                    severity: alert.severity as Alert['severity'],
                    status: alert.status as Alert['status'],
                    title: alert.title || alert.message.substring(0, 100),
                    message: alert.message,
                    source: alert.source || 'unknown',
                    relatedIncidents: alert.relatedIncidents,
                    createdAt: alert.createdAt.toISOString(),
                }));

                socket.emit('alerts:batch', { alerts: initialAlerts });
            } catch (error) {
                fastify.log.error(error, '[Alerts WS] Error fetching initial alerts');
                // Send empty batch on error
                socket.emit('alerts:batch', { alerts: [] });
            }

            // Start streaming new alerts every 5-15 seconds
            streamInterval = setInterval(async () => {
                try {
                    // Query for recent alerts (last 30 seconds)
                    const recentAlerts = await prisma.alert.findMany({
                        where: {
                            tenantId: clientTenantId,
                            createdAt: { gte: new Date(Date.now() - 30000) },
                            ...(filters.severity ? { severity: filters.severity } : {}),
                        },
                        orderBy: { createdAt: 'desc' },
                        take: 1,
                    });

                    if (recentAlerts.length > 0) {
                        const newAlert: Alert = {
                            id: recentAlerts[0].id,
                            tenantId: recentAlerts[0].tenantId,
                            severity: recentAlerts[0].severity as Alert['severity'],
                            status: recentAlerts[0].status as Alert['status'],
                            title: recentAlerts[0].title || recentAlerts[0].message.substring(0, 100),
                            message: recentAlerts[0].message,
                            source: recentAlerts[0].source || 'unknown',
                            relatedIncidents: recentAlerts[0].relatedIncidents,
                            createdAt: recentAlerts[0].createdAt.toISOString(),
                        };

                        socket.emit('alerts:new', { alert: newAlert });
                        fastify.log.debug({ alertId: newAlert.id }, `[Alerts WS] Sent alert to ${socket.id}`);

                        // Send notifications for critical/high severity alerts
                        if (newAlert.severity === 'critical' || newAlert.severity === 'high') {
                            notificationOrchestrator
                                .notifyAlert(recentAlerts[0])
                                .catch((error) => {
                                    fastify.log.error(
                                        { err: error, alertId: newAlert.id },
                                        'Failed to send alert notification',
                                    );
                                });
                        }
                    }
                } catch (error) {
                    fastify.log.error(error, '[Alerts WS] Error fetching new alerts');
                }
            }, Math.random() * 10000 + 5000); // 5-15 seconds
        });

        // Handle unsubscribe
        socket.on('unsubscribe', () => {
            fastify.log.info(`[Alerts WS] Client ${socket.id} unsubscribed`);
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
            fastify.log.debug(`[Alerts WS] Received pong from ${socket.id}`);
        });

        // Handle disconnection
        socket.on('disconnect', (reason) => {
            fastify.log.info({ reason }, `[Alerts WS] Client ${socket.id} disconnected`);
            
            if (streamInterval) {
                clearInterval(streamInterval);
            }
            if (heartbeatInterval) {
                clearInterval(heartbeatInterval);
            }
        });

        // Handle errors
        socket.on('error', (error) => {
            fastify.log.error(error, `[Alerts WS] Socket error for ${socket.id}`);
        });
    });

    fastify.log.info('[Alerts WS] WebSocket handler initialized');
}
