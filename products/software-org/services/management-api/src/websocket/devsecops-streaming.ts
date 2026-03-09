/**
 * DevSecOps Real-Time Updates WebSocket Handler
 *
 * @doc.type websocket-handler
 * @doc.purpose Real-time streaming of DevSecOps stage updates
 * @doc.layer product
 * @doc.pattern WebSocket Handler
 */

import { Server as SocketIOServer, Socket } from 'socket.io';
import { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';

/**
 * Setup DevSecOps stage updates streaming via WebSocket
 *
 * Endpoint: ws://localhost:3101/devsecops/stages/{stageKey}/updates
 *
 * Example subscription:
 * const socket = io('http://localhost:3101');
 * socket.on('devsecops:stage-update', (data) => {
 *   console.log(`Stage ${data.stageKey} updated:`, data.items);
 * });
 */
export function setupDevSecOpsStreaming(io: SocketIOServer, fastify: FastifyInstance): void {
    fastify.log.info('Setting up DevSecOps WebSocket streaming...');

    // Namespace for DevSecOps updates
    const devsecopsNamespace = io.of('/devsecops');

    /**
     * Handle client connections to specific stage channels
     */
    devsecopsNamespace.on('connection', (socket: Socket) => {
        fastify.log.info(`[DevSecOps] Client connected: ${socket.id}`);

        /**
         * Subscribe to stage updates
         * Expected event: subscribe
         * Payload: { stageKey: string }
         */
        socket.on('subscribe', async (data: { stageKey: string }) => {
            const { stageKey } = data;
            fastify.log.info(`[DevSecOps] Client ${socket.id} subscribing to stage: ${stageKey}`);

            // Join a room for this stage (enables broadcasting to all clients interested in this stage)
            socket.join(`stage:${stageKey}`);

            try {
                // Send initial state of the stage's work items
                const workItems = await prisma.workItem.findMany({
                    where: { stageKey },
                    orderBy: { createdAt: 'desc' },
                });

                const items = workItems.map((item) => ({
                    id: item.id,
                    title: item.title,
                    description: item.description,
                    status: item.status,
                    priority: item.priority,
                    stageKey: item.stageKey,
                    createdAt: item.createdAt.toISOString(),
                    updatedAt: item.updatedAt.toISOString(),
                }));

                socket.emit('stage-snapshot', {
                    stageKey,
                    items,
                    timestamp: new Date().toISOString(),
                });

                fastify.log.debug(`[DevSecOps] Sent snapshot of ${items.length} items for stage ${stageKey}`);
            } catch (error) {
                fastify.log.error(error, `Failed to fetch stage items for ${stageKey}`);
                socket.emit('error', {
                    message: 'Failed to fetch stage items',
                    stageKey,
                });
            }
        });

        /**
         * Handle unsubscribe
         * Payload: { stageKey: string }
         */
        socket.on('unsubscribe', (data: { stageKey: string }) => {
            const { stageKey } = data;
            socket.leave(`stage:${stageKey}`);
            fastify.log.info(`[DevSecOps] Client ${socket.id} unsubscribed from stage: ${stageKey}`);
        });

        /**
         * Handle disconnection
         */
        socket.on('disconnect', () => {
            fastify.log.info(`[DevSecOps] Client disconnected: ${socket.id}`);
        });

        /**
         * Handle errors
         */
        socket.on('error', (error) => {
            fastify.log.error(error, `[DevSecOps] Socket error for client ${socket.id}`);
        });
    });

    fastify.log.info('DevSecOps WebSocket streaming configured');
}

/**
 * Broadcast stage update to all connected clients
 * This function should be called when work items in a stage are updated
 */
export function broadcastStageUpdate(
    io: SocketIOServer,
    stageKey: string,
    items: any[]
): void {
    io.of('/devsecops').to(`stage:${stageKey}`).emit('stage-update', {
        stageKey,
        items,
        timestamp: new Date().toISOString(),
    });
}
