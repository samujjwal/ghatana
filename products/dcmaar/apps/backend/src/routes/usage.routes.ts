/**
 * Usage tracking routes for collecting and querying device activity data.
 *
 * <p><b>Purpose</b><br>
 * Provides endpoints for recording and retrieving device usage data including
 * app launches, website visits, and screen time. Supports both authenticated
 * parent queries and device-originated usage reports with real-time broadcasting.
 *
 * <p><b>Endpoints</b><br>
 * - POST /usage - Record new usage session from device
 * - GET /usage - Query usage data with filters (date range, child, device, type)
 * - GET /usage/summary - Get aggregated usage statistics
 * - GET /usage/realtime - Get recent activity for live monitoring
 *
 * <p><b>Data Collection</b><br>
 * Devices report usage sessions containing app/website identifier, duration,
 * timestamps, and optional metadata. Sessions are stored in time-series format
 * for efficient querying and analytics.
 *
 * <p><b>Real-Time Broadcasting</b><br>
 * New usage data is broadcast via WebSocket to parent dashboards for live
 * activity monitoring and immediate policy enforcement feedback.
 *
 * <p><b>Authentication</b><br>
 * Uses optionalAuthenticate middleware - allows device reports without auth
 * but requires authentication for querying historical data.
 *
 * @doc.type route
 * @doc.purpose Usage data collection and real-time activity monitoring
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { optionalAuthenticate, AuthRequest } from '../middleware/auth.middleware';
import * as usageService from '../services/usage.service';
import { logger } from '../utils/logger';
import { broadcastToRoom, WSEvent, getRoomNames } from '../websocket/server';
import { pool } from '../db';
import { GuardianEvent } from '../types/guardian-events';
import { v4 as uuidv4 } from 'uuid';

/**
 * Helper function to broadcast usage data to relevant rooms
 */
async function broadcastUsageData(
  usageSession: usageService.UsageSession,
  eventType: WSEvent.USAGE_DATA | WSEvent.USAGE_UPDATED
) {
  try {
    const deviceResult = await pool.query(
      `SELECT d.id, d.user_id, d.child_id, d.device_name, d.device_type,
              c.name as child_name
       FROM devices d
       LEFT JOIN children c ON d.child_id = c.id
       WHERE d.id = $1`,
      [usageSession.device_id]
    );

    if (deviceResult.rows.length === 0) {
      logger.warn('Device not found for usage broadcast', {
        deviceId: usageSession.device_id,
      });
      return;
    }

    const device = deviceResult.rows[0];

    const payload = {
      usageSession,
      device: {
        id: device.id,
        name: device.device_name,
        type: device.device_type,
      },
      child: device.child_id ? {
        id: device.child_id,
        name: device.child_name,
      } : null,
    };

    const parentRoom = getRoomNames.parent(device.user_id);
    broadcastToRoom(parentRoom, eventType, payload);
    logger.info('Broadcasting usage data to parent', {
      room: parentRoom,
      event: eventType,
      usageId: usageSession.id,
    });

    if (device.child_id) {
      const childRoom = getRoomNames.child(device.child_id);
      broadcastToRoom(childRoom, eventType, payload);
      logger.info('Broadcasting usage data to child', {
        room: childRoom,
        event: eventType,
        usageId: usageSession.id,
      });
    }

    const deviceRoom = getRoomNames.device(device.id);
    broadcastToRoom(deviceRoom, eventType, payload);
    logger.info('Broadcasting usage data to device', {
      room: deviceRoom,
      event: eventType,
      usageId: usageSession.id,
    });
  } catch (error) {
    logger.error('Error broadcasting usage data', {
      error,
      usageId: usageSession.id,
    });
  }
}

const usageRoutes: FastifyPluginAsync = async (fastify) => {
  // Attach optional authentication
  fastify.addHook('preHandler', optionalAuthenticate);

  /** POST / - Create a new usage session */
  fastify.post('/', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const {
        device_id,
        session_type,
        item_name,
        category,
        start_time,
        end_time,
        duration_seconds,
      } = request.body as any;

      if (!device_id || !session_type || !item_name || !start_time) {
        return reply.status(400).send({
          error: 'Missing required fields: device_id, session_type, item_name, start_time',
        });
      }

      if (!['app', 'website'].includes(session_type)) {
        return reply.status(400).send({
          error: 'session_type must be either "app" or "website"',
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
          error: 'Not authorized to create usage data for this device',
        });
      }

      const usageSession = await usageService.createUsageSession({
        device_id,
        session_type,
        item_name,
        category,
        start_time: new Date(start_time),
        end_time: end_time ? new Date(end_time) : undefined,
        duration_seconds,
      });

      const guardianEvent: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'usage',
        subtype: session_type === 'app' ? 'app_usage_session' : 'web_usage_session',
        occurred_at: new Date(start_time).toISOString(),
        source: {
          agent_type: 'backend',
          agent_version: process.env.npm_package_version || '1.0.0',
          device_id,
        },
        context: {
          item_name,
          category,
        },
        payload: {
          usage_id: usageSession.id,
          session_type,
          duration_seconds: usageSession.duration_seconds,
          start_time: usageSession.start_time.toISOString(),
          end_time: usageSession.end_time ? usageSession.end_time.toISOString() : undefined,
        },
      };

      logger.info('GuardianEvent (usage)', {
        kind: guardianEvent.kind,
        subtype: guardianEvent.subtype,
        source: guardianEvent.source,
      });

      await broadcastUsageData(usageSession, WSEvent.USAGE_DATA);

      return reply.status(201).send(usageSession);
    } catch (error) {
      logger.error('Error creating usage session', { error });
      return reply.status(500).send({ error: 'Failed to create usage session' });
    }
  });

  /** PUT /:id - Update an existing usage session */
  fastify.put('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };
      const { end_time, duration_seconds } = request.body as any;

      const existingSession = await usageService.getUsageSessionById(id);

      if (!existingSession) {
        return reply.status(404).send({ error: 'Usage session not found' });
      }

      const deviceResult = await pool.query(
        'SELECT user_id FROM devices WHERE id = $1',
        [existingSession.device_id]
      );

      if (deviceResult.rows.length === 0) {
        return reply.status(404).send({ error: 'Device not found' });
      }

      const deviceUserId = deviceResult.rows[0].user_id;

      if (request.userId && request.userId !== deviceUserId) {
        return reply.status(403).send({
          error: 'Not authorized to update this usage session',
        });
      }

      const updatedSession = await usageService.updateUsageSession(id, {
        end_time: end_time ? new Date(end_time) : undefined,
        duration_seconds,
      });

      if (!updatedSession) {
        return reply.status(404).send({ error: 'Usage session not found' });
      }

      await broadcastUsageData(updatedSession, WSEvent.USAGE_UPDATED);

      return reply.send(updatedSession);
    } catch (error) {
      logger.error('Error updating usage session', { error });
      return reply.status(500).send({ error: 'Failed to update usage session' });
    }
  });

  /** GET /device/:deviceId - Get usage sessions for a specific device */
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
          error: 'Not authorized to view usage data for this device',
        });
      }

      const sessions = await usageService.getUsageSessionsByDevice(deviceId, limit);

      return reply.send(sessions);
    } catch (error) {
      logger.error('Error fetching usage sessions by device', { error });
      return reply.status(500).send({ error: 'Failed to fetch usage sessions' });
    }
  });

  /** GET /child/:childId - Get usage sessions for a specific child */
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
          error: 'Not authorized to view usage data for this child',
        });
      }

      const sessions = await usageService.getUsageSessionsByChild(childId, limit);

      return reply.send(sessions);
    } catch (error) {
      logger.error('Error fetching usage sessions by child', { error });
      return reply.status(500).send({ error: 'Failed to fetch usage sessions' });
    }
  });

  /** GET /child/:childId/summary - Get usage summary for a child */
  fastify.get('/child/:childId/summary', async (request: AuthRequest, reply: FastifyReply) => {
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
          error: 'Not authorized to view usage summary for this child',
        });
      }

      const summary = await usageService.getUsageSummaryByChild(
        childId,
        new Date(start_date as string),
        new Date(end_date as string)
      );

      return reply.send(summary);
    } catch (error) {
      logger.error('Error fetching usage summary', { error });
      return reply.status(500).send({ error: 'Failed to fetch usage summary' });
    }
  });
};

export default usageRoutes;
