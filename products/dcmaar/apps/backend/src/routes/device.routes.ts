/**
 * Device management routes for child device registration and monitoring.
 *
 * <p><b>Purpose</b><br>
 * Provides RESTful endpoints for registering, managing, and monitoring child devices
 * across platforms (desktop, mobile, browser extension). Handles device pairing,
 * status tracking, and real-time metrics collection.
 *
 * <p><b>Endpoints</b><br>
 * - POST /devices/register - Register new device with pairing code
 * - GET /devices - List all devices for tenant
 * - GET /devices/:id - Get specific device details and status
 * - PUT /devices/:id - Update device configuration
 * - DELETE /devices/:id - Remove device registration
 * - PUT /devices/:id/toggle - Enable/disable device monitoring
 *
 * <p><b>Device Types</b><br>
 * - desktop: Windows/Mac agents with system-level monitoring
 * - mobile: Android/iOS apps with native API integration
 * - extension: Browser extensions for web-only monitoring
 *
 * <p><b>Metrics & Monitoring</b><br>
 * Tracks device connection status (devicesConnected metric), last seen timestamps,
 * and active monitoring state for real-time dashboard display.
 *
 * @doc.type route
 * @doc.purpose Device registration and lifecycle management
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import * as deviceService from '../services/device.service';
import { logger } from '../utils/logger';
import { logAuditEvent, AuditEvents } from '../services/audit.service';
import { devicesConnected, deviceCommandsEnqueued, agentSyncRequests } from '../utils/metrics';
import { getPendingCommandsForDevice, acknowledgeCommand, enqueueDeviceCommand } from '../services/command-queue.service';
import {
  validateAction,
  getActionKind,
  getSupportedImmediateActions,
  getAgentSyncPayload,
  isImmediateAction,
} from '../services/agent-sync.service';
import { GuardianEvent } from '../types/guardian-events';
import { storeGuardianEvents } from '../services/events-store.service';
import { v4 as uuidv4 } from 'uuid';

// Validation schemas
const registerDeviceSchema = z.object({
  child_id: z.string().uuid().optional(),
  device_type: z.enum(['desktop', 'mobile', 'extension']),
  device_name: z.string().min(1).max(255),
  device_fingerprint: z.string().optional(),
});

const updateDeviceSchema = z.object({
  child_id: z.string().uuid().nullable().optional(),
  device_name: z.string().min(1).max(255).optional(),
  is_active: z.boolean().optional(),
});

const pairDeviceSchema = z.object({
  child_id: z.string().uuid(),
});

const pairingCodeSchema = z.object({
  pairing_code: z.string().length(6).regex(/^\d{6}$/),
});

const deviceRoutes: FastifyPluginAsync = async (fastify) => {
  // All routes require authentication
  fastify.addHook('preHandler', authenticate);

  /** GET / - List all devices for the authenticated user */
  fastify.get('/', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const query = request.query as any;
      const filters = {
        child_id: query.child_id as string | undefined,
        device_type: query.device_type as string | undefined,
        is_active: query.is_active === 'true' ? true : query.is_active === 'false' ? false : undefined,
      };

      const devices = await deviceService.getDevices(request.userId!, filters);

      return reply.send({
        success: true,
        data: devices,
        count: devices.length,
      });
    } catch (error) {
      console.error('Get devices error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch devices',
      });
    }
  });

  /** GET /stats - Get device statistics */
  fastify.get('/stats', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const stats = await deviceService.getDeviceStats(request.userId!);

      return reply.send({
        success: true,
        data: stats,
      });
    } catch (error) {
      console.error('Get device stats error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch device statistics',
      });
    }
  });

  /** GET /:id - Get a single device by ID */
  fastify.get('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const device = await deviceService.getDeviceById(request.userId!, id);

      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      return reply.send({
        success: true,
        data: device,
      });
    } catch (error) {
      console.error('Get device error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch device',
      });
    }
  });

  /** POST /register - Register a new device */
  fastify.post('/register', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validation = registerDeviceSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const device = await deviceService.registerDevice(request.userId!, validation.data);

      await logAuditEvent(
        request.userId!,
        AuditEvents.DEVICE_PAIRED,
        {
          device_id: device.id,
          device_type: device.device_type,
          device_name: device.device_name,
        },
        request as any,
        'info'
      );

      devicesConnected.inc({ type: device.device_type });

      logger.info('Device registered', {
        userId: request.userId,
        deviceId: device.id,
        deviceType: device.device_type,
      });

      return reply.status(201).send({
        success: true,
        data: device,
        message: 'Device registered successfully',
      });
    } catch (error) {
      logger.error('Register device error', { userId: request.userId, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to register device',
      });
    }
  });

  /** PUT /:id - Update a device */
  fastify.put('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const validation = updateDeviceSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      // Check if at least one field is being updated
      if (Object.keys(validation.data).length === 0) {
        return reply.status(400).send({
          success: false,
          error: 'No fields to update',
        });
      }

      const device = await deviceService.updateDevice(request.userId!, id, validation.data);

      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      await logAuditEvent(
        request.userId!,
        AuditEvents.DEVICE_UPDATED,
        {
          device_id: id,
          updates: validation.data,
        },
        request as any,
        'info'
      );

      logger.info('Device updated', {
        userId: request.userId,
        deviceId: id,
        updates: Object.keys(validation.data),
      });

      return reply.send({
        success: true,
        data: device,
        message: 'Device updated successfully',
      });
    } catch (error) {
      logger.error('Update device error', { userId: request.userId, deviceId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to update device',
      });
    }
  });

  /** DELETE /:id - Delete a device (soft delete) */
  fastify.delete('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const deleted = await deviceService.deleteDevice(request.userId!, id);

      if (!deleted) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      await logAuditEvent(
        request.userId!,
        AuditEvents.DEVICE_DELETED,
        { device_id: id },
        request as any,
        'warning'
      );

      logger.warn('Device deleted', {
        userId: request.userId,
        deviceId: id,
      });

      return reply.send({
        success: true,
        message: 'Device deleted successfully',
      });
    } catch (error) {
      logger.error('Delete device error', { userId: request.userId, deviceId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to delete device',
      });
    }
  });

  /** POST /:id/pair - Pair device with child */
  fastify.post('/:id/pair', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const validation = pairDeviceSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const device = await deviceService.pairDeviceWithChild(
        request.userId!,
        id,
        validation.data.child_id
      );

      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      await logAuditEvent(
        request.userId!,
        AuditEvents.DEVICE_PAIRED,
        {
          device_id: id,
          child_id: validation.data.child_id,
        },
        request as any,
        'info'
      );

      logger.info('Device paired with child', {
        userId: request.userId,
        deviceId: id,
        childId: validation.data.child_id,
      });

      return reply.send({
        success: true,
        data: device,
        message: 'Device paired successfully',
      });
    } catch (error) {
      logger.error('Pair device error', { userId: request.userId, deviceId: (request.params as any).id, error });
      if ((error as Error).message === 'Child not found') {
        return reply.status(404).send({
          success: false,
          error: 'Child not found',
        });
      }
      return reply.status(500).send({
        success: false,
        error: 'Failed to pair device',
      });
    }
  });

  /** POST /:id/unpair - Unpair device from child */
  fastify.post('/:id/unpair', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const device = await deviceService.unpairDevice(request.userId!, id);

      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      return reply.send({
        success: true,
        data: device,
        message: 'Device unpaired successfully',
      });
    } catch (error) {
      console.error('Unpair device error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to unpair device',
      });
    }
  });

  /** POST /:id/heartbeat - Update device last seen timestamp */
  fastify.post('/:id/heartbeat', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const device = await deviceService.getDeviceById(request.userId!, id);
      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      await deviceService.updateDeviceLastSeen(id);

      return reply.send({
        success: true,
        message: 'Device heartbeat updated',
      });
    } catch (error) {
      console.error('Device heartbeat error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to update device heartbeat',
      });
    }
  });

  /** POST /:id/actions - Enqueue immediate device actions (e.g., lock_device) */
  fastify.post('/:id/actions', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };
      const { action, params } = request.body as { action?: string; params?: Record<string, unknown> };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      if (!action) {
        return reply.status(400).send({
          success: false,
          error: 'Action is required',
        });
      }

      // Use centralized action registry (no duplication)
      const validation = validateAction(action, params || {});
      if (!validation.valid) {
        return reply.status(400).send({
          success: false,
          error: validation.error,
          supported_actions: getSupportedImmediateActions(),
        });
      }

      // Only allow immediate actions via this endpoint
      if (!isImmediateAction(action)) {
        return reply.status(400).send({
          success: false,
          error: 'This endpoint only supports immediate actions',
          supported_actions: getSupportedImmediateActions(),
        });
      }

      const device = await deviceService.getDeviceById(request.userId!, id);

      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      const commandId = await enqueueDeviceCommand({
        deviceId: device.id,
        childId: device.child_id || undefined,
        orgId: undefined,
        kind: getActionKind(action),
        action,
        params: params || {},
        issuedByActorType: 'parent',
        issuedByUserId: request.userId!,
        expiresAt: undefined,
      });

      // Metrics: track enqueued device commands by kind/action/source
      try {
        deviceCommandsEnqueued.inc({
          kind: getActionKind(action),
          action,
          source: 'parent',
        });
      } catch {
        // metrics should never break the endpoint
      }

      const event: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'policy',
        subtype: action === 'lock_device' ? 'device_lock_requested' : 'device_action_requested',
        occurred_at: new Date().toISOString(),
        source: {
          agent_type: 'backend',
          agent_version: process.env.npm_package_version || '1.0.0',
          device_id: device.id,
          child_id: device.child_id || undefined,
        },
        context: {
          action,
          device_id: device.id,
          child_id: device.child_id,
        },
        payload: {
          command_id: commandId,
          params: params || {},
        },
      };

      await storeGuardianEvents([event]);

      logger.info('Device action command enqueued', {
        userId: request.userId,
        deviceId: device.id,
        action,
        commandId,
      });

      return reply.status(202).send({
        success: true,
        message: 'Device action enqueued',
        command_id: commandId,
      });
    } catch (error) {
      logger.error('Enqueue device action error', { userId: request.userId, deviceId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to enqueue device action',
      });
    }
  });

  fastify.get('/:id/commands', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const device = await deviceService.getDeviceById(request.userId!, id);

      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      const commands = await getPendingCommandsForDevice(id);

      return reply.send({
        success: true,
        data: commands,
      });
    } catch (error) {
      logger.error('Get device commands error', { userId: request.userId, deviceId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch device commands',
      });
    }
  });

  fastify.post('/:id/commands/ack', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };
      const { command_id, status } = request.body as any;

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      if (!command_id || !status) {
        return reply.status(400).send({
          success: false,
          error: 'Missing required fields: command_id, status',
        });
      }

      const device = await deviceService.getDeviceById(request.userId!, id);

      if (!device) {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      if (!['processed', 'failed', 'expired'].includes(status)) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid status value',
        });
      }

      const updated = await acknowledgeCommand(id, command_id, status);

      if (!updated) {
        return reply.status(404).send({
          success: false,
          error: 'Command not found or already acknowledged',
        });
      }

      const ackEvent: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'system',
        subtype: 'command_acknowledged',
        occurred_at: new Date().toISOString(),
        source: {
          agent_type: 'device_agent',
          agent_version: 'unknown',
          device_id: id,
          child_id: device.child_id || undefined,
        },
        context: {
          command_id,
          status,
        },
        payload: {
          device_id: id,
        },
      };

      await storeGuardianEvents([ackEvent]);

      logger.info('Device command acknowledgement received', {
        userId: request.userId,
        deviceId: id,
        commandId: command_id,
        status,
      });

      return reply.send({
        success: true,
        message: 'Command acknowledgement received',
      });
    } catch (error) {
      logger.error('Acknowledge device command error', { userId: request.userId, deviceId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to acknowledge device command',
      });
    }
  });

  /** POST /pairing/generate - Generate a pairing code for a child */
  fastify.post('/pairing/generate', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validation = z.object({ child_id: z.string().uuid() }).safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Validation failed',
          details: validation.error.format(),
        });
      }

      const { child_id } = validation.data;

      const result = await deviceService.generateDevicePairingCode(request.userId!, child_id);

      return reply.status(201).send({
        success: true,
        data: result,
      });
    } catch (error) {
      console.error('Generate pairing code error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to generate pairing code',
      });
    }
  });

  /** POST /:id/pair-with-code - Pair a device using pairing code */
  fastify.post('/:id/pair-with-code', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };
      const validation = pairingCodeSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid pairing code format',
          details: validation.error.format(),
        });
      }

      const { pairing_code } = validation.data;

      const result = await deviceService.pairDeviceWithCode(id, pairing_code);

      if (!result.success) {
        return reply.status(400).send({
          success: false,
          error: result.error,
        });
      }

      return reply.send({
        success: true,
        data: result.device,
      });
    } catch (error) {
      console.error('Pair device with code error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to pair device',
      });
    }
  });

  /** GET /pairing/:childId - Get active pairing code for a child */
  fastify.get('/pairing/:childId', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { childId } = request.params as { childId: string };

      const result = await deviceService.getActivePairingCode(request.userId!, childId);

      if (!result) {
        return reply.status(404).send({
          success: false,
          error: 'No active pairing code found',
        });
      }

      return reply.send({
        success: true,
        data: result,
      });
    } catch (error) {
      console.error('Get pairing code error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to get pairing code',
      });
    }
  });

  /**
   * GET /:id/sync - Unified agent sync endpoint
   * 
   * Returns a combined payload with policies and pending commands.
   * This is the canonical endpoint for device agents to sync state.
   * 
   * Response includes:
   * - policies: All applicable policies with version
   * - commands: Pending commands to execute
   * - next_sync_seconds: Recommended interval before next sync
   */
  fastify.get('/:id/sync', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const syncPayload = await getAgentSyncPayload(request.userId!, id);

      if (!syncPayload) {
        try {
          agentSyncRequests.inc({ result: 'device_not_found' });
        } catch { }
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }

      try {
        agentSyncRequests.inc({ result: 'success' });
      } catch { }

      return reply.send({
        success: true,
        data: syncPayload,
      });
    } catch (error) {
      logger.error('Agent sync error', { userId: request.userId, deviceId: (request.params as any).id, error });
      try {
        agentSyncRequests.inc({ result: 'error' });
      } catch { }
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch agent sync data',
      });
    }
  });
};

export default deviceRoutes;
