/**
 * Block event routes for policy enforcement and violation tracking.
 *
 * <p><b>Purpose</b><br>
 * Provides endpoints for recording and querying policy enforcement events when
 * devices block access to restricted content. Enables real-time notifications
 * and historical analysis of policy violations and compliance.
 *
 * <p><b>Endpoints</b><br>
 * - POST /blocks - Record block event from device
 * - GET /blocks - Query block history with filters
 * - GET /blocks/summary - Get aggregated block statistics by type/time
 *
 * <p><b>Block Event Data</b><br>
 * Captures policy violations including blocked URL/app, triggering policy,
 * device info, timestamp, and optional context (user action, category).
 * Used for compliance reporting and policy effectiveness analysis.
 *
 * <p><b>Real-Time Notifications</b><br>
 * Block events are broadcast via WebSocket to parent dashboards for immediate
 * awareness of policy violations and child activity patterns.
 *
 * <p><b>Authentication</b><br>
 * Uses optionalAuthenticate middleware - allows device reports without auth
 * but requires authentication for querying historical data.
 *
 * @doc.type route
 * @doc.purpose Policy enforcement event tracking and notifications
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { optionalAuthenticate, AuthRequest } from '../middleware/auth.middleware';
import * as blockService from '../services/block.service';
import { logger } from '../utils/logger';
import { broadcastToRoom, WSEvent, getRoomNames } from '../websocket/server';
import { pool } from '../db';
import { GuardianEvent } from '../types/guardian-events';
import { v4 as uuidv4 } from 'uuid';

/**
 * Helper function to broadcast block events to relevant rooms
 */
async function broadcastBlockEvent(blockEvent: blockService.BlockEvent) {
  try {
    const deviceResult = await pool.query(
      `SELECT d.id, d.user_id, d.child_id, d.device_name, d.device_type,
              c.name as child_name,
              p.name as policy_name, p.policy_type
       FROM devices d
       LEFT JOIN children c ON d.child_id = c.id
       LEFT JOIN policies p ON $1::uuid = p.id
       WHERE d.id = $2`,
      [blockEvent.policy_id, blockEvent.device_id]
    );

    if (deviceResult.rows.length === 0) {
      logger.warn('Device not found for block event broadcast', {
        deviceId: blockEvent.device_id,
      });
      return;
    }

    const device = deviceResult.rows[0];

    const payload = {
      blockEvent,
      device: {
        id: device.id,
        name: device.device_name,
        type: device.device_type,
      },
      child: device.child_id ? {
        id: device.child_id,
        name: device.child_name,
      } : null,
      policy: blockEvent.policy_id ? {
        id: blockEvent.policy_id,
        name: device.policy_name,
        type: device.policy_type,
      } : null,
    };

    const parentRoom = getRoomNames.parent(device.user_id);
    broadcastToRoom(parentRoom, WSEvent.BLOCK_EVENT, payload);
    logger.info('Broadcasting block event to parent', {
      room: parentRoom,
      event: WSEvent.BLOCK_EVENT,
      blockId: blockEvent.id,
      blockedItem: blockEvent.blocked_item,
    });

    if (device.child_id) {
      const childRoom = getRoomNames.child(device.child_id);
      broadcastToRoom(childRoom, WSEvent.BLOCK_EVENT, payload);
      logger.info('Broadcasting block event to child', {
        room: childRoom,
        event: WSEvent.BLOCK_EVENT,
        blockId: blockEvent.id,
      });
    }

    const deviceRoom = getRoomNames.device(device.id);
    broadcastToRoom(deviceRoom, WSEvent.BLOCK_EVENT, payload);
    logger.info('Broadcasting block event to device', {
      room: deviceRoom,
      event: WSEvent.BLOCK_EVENT,
      blockId: blockEvent.id,
    });
  } catch (error) {
    logger.error('Error broadcasting block event', {
      error,
      blockId: blockEvent.id,
    });
  }
}

const blockRoutes: FastifyPluginAsync = async (fastify) => {
  // Allow authenticated parents to include tokens
  fastify.addHook('preHandler', optionalAuthenticate);

  /** POST / - Report a blocked attempt */
  fastify.post('/', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const {
        device_id,
        policy_id,
        event_type,
        blocked_item,
        category,
        reason,
        timestamp,
      } = request.body as any;

      if (!device_id || !event_type || !blocked_item) {
        return reply.status(400).send({
          error: 'Missing required fields: device_id, event_type, blocked_item',
        });
      }

      if (!['website', 'app'].includes(event_type)) {
        return reply.status(400).send({
          error: 'event_type must be either "website" or "app"',
        });
      }

      const deviceResult = await pool.query(
        'SELECT user_id FROM devices WHERE id = $1',
        [device_id]
      );

      if (deviceResult.rows.length === 0) {
        return reply.status(404).send({ error: 'Device not found' });
      }

      const deviceUserId = deviceResult.rows[0].user_id;

      if (request.userId && request.userId !== deviceUserId) {
        return reply.status(403).send({
          error: 'Not authorized to report blocks for this device',
        });
      }

      const blockEvent = await blockService.createBlockEvent({
        device_id,
        policy_id,
        event_type,
        blocked_item,
        category,
        reason,
        timestamp: timestamp ? new Date(timestamp) : undefined,
      });

      const guardianEvent: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'block',
        subtype: event_type === 'app' ? 'app_blocked' : 'web_blocked',
        occurred_at: (timestamp ? new Date(timestamp) : new Date()).toISOString(),
        source: {
          agent_type: 'backend',
          agent_version: process.env.npm_package_version || '1.0.0',
          device_id,
        },
        context: {
          blocked_item,
          category,
          policy_id,
        },
        payload: {
          block_event_id: blockEvent.id,
          reason,
        },
      };

      logger.info('GuardianEvent (block)', {
        kind: guardianEvent.kind,
        subtype: guardianEvent.subtype,
        source: guardianEvent.source,
      });

      await broadcastBlockEvent(blockEvent);

      return reply.status(201).send(blockEvent);
    } catch (error) {
      logger.error('Error creating block event', { error });
      return reply.status(500).send({ error: 'Failed to create block event' });
    }
  });

  /** GET /device/:deviceId - Get block events for a specific device */
  fastify.get('/device/:deviceId', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { deviceId } = request.params as { deviceId: string };
      const limit = parseInt((request.query as any).limit as string) || 100;

      const deviceResult = await pool.query(
        'SELECT user_id FROM devices WHERE id = $1',
        [deviceId]
      );

      if (deviceResult.rows.length === 0) {
        return reply.status(404).send({ error: 'Device not found' });
      }

      const deviceUserId = deviceResult.rows[0].user_id;

      if (request.userId !== deviceUserId) {
        return reply.status(403).send({
          error: 'Not authorized to view block events for this device',
        });
      }

      const events = await blockService.getBlockEventsByDevice(deviceId, limit);

      return reply.send(events);
    } catch (error) {
      logger.error('Error fetching block events by device', { error });
      return reply.status(500).send({ error: 'Failed to fetch block events' });
    }
  });

  /** GET /child/:childId - Get block events for a specific child */
  fastify.get('/child/:childId', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { childId } = request.params as { childId: string };
      const limit = parseInt((request.query as any).limit as string) || 100;

      const childResult = await pool.query(
        'SELECT user_id FROM children WHERE id = $1',
        [childId]
      );

      if (childResult.rows.length === 0) {
        return reply.status(404).send({ error: 'Child not found' });
      }

      const childUserId = childResult.rows[0].user_id;

      if (request.userId !== childUserId) {
        return reply.status(403).send({
          error: 'Not authorized to view block events for this child',
        });
      }

      const events = await blockService.getBlockEventsByChild(childId, limit);

      return reply.send(events);
    } catch (error) {
      logger.error('Error fetching block events by child', { error });
      return reply.status(500).send({ error: 'Failed to fetch block events' });
    }
  });

  /** GET /child/:childId/stats - Get block event statistics for a child */
  fastify.get('/child/:childId/stats', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { childId } = request.params as { childId: string };
      const { start_date, end_date } = request.query as any;

      if (!start_date || !end_date) {
        return reply.status(400).send({
          error: 'Missing required query params: start_date, end_date',
        });
      }

      const childResult = await pool.query(
        'SELECT user_id FROM children WHERE id = $1',
        [childId]
      );

      if (childResult.rows.length === 0) {
        return reply.status(404).send({ error: 'Child not found' });
      }

      const childUserId = childResult.rows[0].user_id;

      if (request.userId !== childUserId) {
        return reply.status(403).send({
          error: 'Not authorized to view block statistics for this child',
        });
      }

      const stats = await blockService.getBlockEventStats(
        childId,
        new Date(start_date as string),
        new Date(end_date as string)
      );

      return reply.send(stats);
    } catch (error) {
      logger.error('Error fetching block event stats', { error });
      return reply.status(500).send({ error: 'Failed to fetch block event statistics' });
    }
  });
};

export default blockRoutes;
