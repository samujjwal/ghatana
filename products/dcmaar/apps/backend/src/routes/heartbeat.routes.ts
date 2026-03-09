/**
 * Device heartbeat routes for health monitoring and status tracking.
 *
 * <p><b>Purpose</b><br>
 * Provides endpoints for devices to report periodic health status and for
 * parents to query device connectivity. Heartbeats include battery level,
 * network quality, uptime, and custom status indicators for real-time monitoring.
 *
 * <p><b>Endpoints</b><br>
 * - POST /heartbeats - Record device heartbeat with health metrics
 * - GET /heartbeats/device/:deviceId - Get recent heartbeats for device
 * - GET /heartbeats/device/:deviceId/latest - Get most recent heartbeat
 * - GET /heartbeats/stats - Get aggregate health statistics
 *
 * <p><b>Heartbeat Data</b><br>
 * Devices send periodic heartbeats (every 30-60 seconds) containing:
 * - Battery level (percentage)
 * - WiFi signal strength
 * - Network latency
 * - Uptime duration
 * - Custom status fields
 *
 * <p><b>Health Monitoring</b><br>
 * Stale heartbeats (>5 minutes old) trigger device offline alerts via WebSocket.
 * Used for connection status indicators and troubleshooting connectivity issues.
 *
 * @doc.type route
 * @doc.purpose Device health monitoring and connectivity tracking
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import * as heartbeatService from '../services/heartbeat.service';
import { logger } from '../utils/logger';
import { getIO } from '../websocket/server';

// Validation schemas
const heartbeatSchema = z.object({
  device_id: z.string().uuid(),
  battery_level: z.number().min(0).max(100).optional(),
  wifi_signal: z.number().optional(),
  network_latency: z.number().optional(),
  uptime: z.number().optional(),
  timestamp: z.string().datetime().optional(),
});

const heartbeatStatsSchema = z.object({
  start_date: z.string().datetime(),
  end_date: z.string().datetime(),
});

/**
 * Helper function to broadcast device status changes
 */
function broadcastDeviceStatus(
  userId: string,
  device: heartbeatService.DeviceStatus,
  status: 'online' | 'offline',
  reason: string
) {
  try {
    const io = getIO();

    const payload = {
      deviceStatus: {
        id: device.id,
        device_name: device.device_name,
        device_type: device.device_type,
        is_online: status === 'online',
        status,
        last_seen_at: device.last_seen_at,
        online_for: device.online_for,
        connection_quality: device.connection_quality,
        reason,
        timestamp: new Date().toISOString(),
      },
    };

    const eventName = status === 'online' ? 'device_online' : 'device_offline';

    io.to(`parent:${userId}`).emit(eventName, payload);
    io.to(`device:${device.id}`).emit(eventName, payload);

    logger.info('Device status broadcast', {
      userId,
      deviceId: device.id,
      eventName,
      status,
    });
  } catch (error) {
    logger.error('Failed to broadcast device status', {
      error,
      userId,
      deviceId: device.id,
    });
  }
}

/**
 * Broadcast heartbeat event to parent
 */
function broadcastHeartbeatEvent(
  userId: string,
  device: heartbeatService.DeviceStatus
) {
  try {
    const io = getIO();

    const payload = {
      deviceHeartbeat: {
        id: device.id,
        device_name: device.device_name,
        device_type: device.device_type,
        is_online: device.is_online,
        last_seen_at: device.last_seen_at,
        online_for: device.online_for,
        connection_quality: device.connection_quality,
        timestamp: new Date().toISOString(),
      },
    };

    io.to(`parent:${userId}`).emit('device_heartbeat', payload);
    io.to(`device:${device.id}`).emit('device_heartbeat', payload);

    logger.debug('Device heartbeat broadcast', {
      userId,
      deviceId: device.id,
    });
  } catch (error) {
    logger.error('Failed to broadcast device heartbeat', {
      error,
      userId,
      deviceId: device.id,
    });
  }
}

const heartbeatRoutes: FastifyPluginAsync = async (fastify) => {
  /** POST / - Device sends heartbeat to report online status */
  fastify.post('/', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validated = heartbeatSchema.parse(request.body);

      const { device, statusChanged, wasOffline } = await heartbeatService.updateHeartbeat(
        request.userId!,
        validated.device_id,
        {
          device_id: validated.device_id,
          battery_level: validated.battery_level,
          wifi_signal: validated.wifi_signal,
          network_latency: validated.network_latency,
          uptime: validated.uptime,
          timestamp: validated.timestamp ? new Date(validated.timestamp) : undefined,
        }
      );

      if (statusChanged) {
        broadcastDeviceStatus(request.userId!, device, 'online', 'restored');
      }

      if (device.is_online) {
        broadcastHeartbeatEvent(request.userId!, device);
      }

      return reply.send({
        success: true,
        data: {
          device,
          statusChanged,
          event: statusChanged ? 'DEVICE_CAME_ONLINE' : 'DEVICE_HEARTBEAT',
        },
      });
    } catch (error) {
      logger.error('Heartbeat error:', { error, userId: request.userId, body: request.body });

      if (error instanceof z.ZodError) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid heartbeat data',
          details: error.issues,
        });
      }

      return reply.status(500).send({
        success: false,
        error: 'Failed to process heartbeat',
      });
    }
  });

  /** GET /device/:deviceId - Get current status of a device */
  fastify.get('/device/:deviceId', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { deviceId } = request.params as { deviceId: string };

      const status = await heartbeatService.getDeviceStatus(request.userId!, deviceId);

      if (!status) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      return reply.send({
        success: true,
        data: status,
      });
    } catch (error) {
      logger.error('Get device status error:', { error, userId: request.userId });
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch device status',
      });
    }
  });

  /** GET /all - Get all devices and their status for the authenticated user */
  fastify.get('/all', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const statuses = await heartbeatService.getAllDeviceStatuses(request.userId!);

      return reply.send({
        success: true,
        data: statuses,
        count: statuses.length,
        online_count: statuses.filter((s) => s.is_online).length,
        offline_count: statuses.filter((s) => !s.is_online).length,
      });
    } catch (error) {
      logger.error('Get all device statuses error:', { error, userId: request.userId });
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch device statuses',
      });
    }
  });

  /** GET /device/:deviceId/stats - Get heartbeat statistics for a device */
  fastify.get('/device/:deviceId/stats', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { deviceId } = request.params as { deviceId: string };
      const validated = heartbeatStatsSchema.parse({
        start_date: (request.query as any).start_date as string,
        end_date: (request.query as any).end_date as string,
      });

      const stats = await heartbeatService.getHeartbeatStats(
        request.userId!,
        deviceId,
        new Date(validated.start_date),
        new Date(validated.end_date)
      );

      return reply.send({
        success: true,
        data: stats,
        period: {
          start: validated.start_date,
          end: validated.end_date,
        },
      });
    } catch (error) {
      logger.error('Get heartbeat stats error:', { error, userId: request.userId });

      if (error instanceof z.ZodError) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid parameters',
          details: error.issues,
        });
      }

      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch heartbeat stats',
      });
    }
  });

  /** POST /mark-offline/:deviceId - Manually mark a device as offline */
  fastify.post('/mark-offline/:deviceId', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { deviceId } = request.params as { deviceId: string };

      const status = await heartbeatService.markDeviceOffline(request.userId!, deviceId);

      if (!status) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found or already offline',
        });
      }

      broadcastDeviceStatus(request.userId!, status, 'offline', 'manual');

      return reply.send({
        success: true,
        data: status,
        event: 'DEVICE_WENT_OFFLINE',
      });
    } catch (error) {
      logger.error('Mark offline error:', { error, userId: request.userId });
      return reply.status(500).send({
        success: false,
        error: 'Failed to mark device offline',
      });
    }
  });
};

export default heartbeatRoutes;
