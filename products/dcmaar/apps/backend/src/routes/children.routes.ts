/**
 * Children profile management routes for Guardian application.
 *
 * <p><b>Purpose</b><br>
 * Provides CRUD endpoints for managing child profiles including personal information,
 * device associations, and profile settings. Each child profile groups devices and
 * policies for consolidated monitoring and reporting.
 *
 * <p><b>Endpoints</b><br>
 * - POST /children - Create new child profile
 * - GET /children - List all children for tenant
 * - GET /children/:id - Get specific child profile with devices
 * - PUT /children/:id - Update child profile information
 * - DELETE /children/:id - Remove child profile (soft delete)
 *
 * <p><b>Profile Management</b><br>
 * Child profiles store name, birth date, avatar, and metadata. Profiles are
 * linked to devices for monitoring and policies for content control. Supports
 * multiple children per parent account with independent settings.
 *
 * <p><b>Metrics</b><br>
 * Tracks total registered children (childrenRegistered metric) for tenant
 * analytics and usage reporting.
 *
 * @doc.type route
 * @doc.purpose Child profile lifecycle management
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import * as childrenService from '../services/children.service';
import * as deviceService from '../services/device.service';
import { logger } from '../utils/logger';
import { logAuditEvent, AuditEvents } from '../services/audit.service';
import { childrenRegistered } from '../utils/metrics';
import { GuardianEvent } from '../types/guardian-events';
import { storeGuardianEvents } from '../services/events-store.service';
import { enqueueDeviceCommand } from '../services/command-queue.service';
import { v4 as uuidv4 } from 'uuid';

// Validation schemas
const createChildSchema = z.object({
  name: z.string().min(1).max(255),
  birth_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  avatar_url: z.string().url().optional(),
});

const updateChildSchema = z.object({
  name: z.string().min(1).max(255).optional(),
  birth_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  avatar_url: z.string().url().optional(),
  is_active: z.boolean().optional(),
});

const createChildRequestSchema = z.object({
  type: z.enum(['extend_session', 'unblock']),
  device_id: z.string().uuid().optional(),
  session_id: z.string().optional(),
  minutes: z.number().int().positive().max(360).optional(),
  resource: z
    .object({
      app_id: z.string().optional(),
      domain: z.string().optional(),
    })
    .optional(),
});

const childRequestDecisionSchema = z.object({
  type: z.enum(['extend_session', 'unblock']),
  decision: z.enum(['approved', 'denied']),
  minutes_granted: z.number().int().positive().max(360).optional(),
  device_id: z.string().uuid().optional(),
  session_id: z.string().optional(),
});

const childrenRoutes: FastifyPluginAsync = async (fastify) => {
  // All routes require authentication
  fastify.addHook('preHandler', authenticate);

  /**
   * GET /
   * List all children for the authenticated user
   */
  fastify.get('/', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const activeOnly: boolean = request.query ? (request.query as any).active === 'true' : false;
      const children = await childrenService.getChildren(request.userId!, activeOnly);

      // Add age to each child
      const childrenWithAge = children.map((child) => ({
        ...child,
        age: childrenService.calculateAge(child.birth_date),
      }));

      return reply.send({
        success: true,
        data: childrenWithAge,
        count: children.length,
      });
    } catch (error) {
      console.error('Get children error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch children',
      });
    }
  });

  /**
   * GET /:id
   * Get a single child by ID
   */
  fastify.get('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Child ID is required',
        });
      }

      // Validate UUID format
      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      if (!uuidRegex.test(id)) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid child ID format',
        });
      }

      const child = await childrenService.getChildById(request.userId!, id);

      if (!child) {
        return reply.status(404).send({
          success: false,
          error: 'Child not found',
        });
      }

      return reply.send({
        success: true,
        data: {
          ...child,
          age: childrenService.calculateAge(child.birth_date),
        },
      });
    } catch (error) {
      console.error('Get child error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch child',
      });
    }
  });

  /**
   * POST /:id/requests
   * Create a child request (extend_session or unblock)
   */
  fastify.post('/:id/requests', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Child ID is required',
        });
      }

      const child = await childrenService.getChildById(request.userId!, id);
      if (!child) {
        return reply.status(404).send({
          success: false,
          error: 'Child not found',
        });
      }

      const validation = createChildRequestSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const { type, device_id, session_id, minutes, resource } = validation.data;

      if (type === 'extend_session' && !minutes) {
        return reply.status(400).send({
          success: false,
          error: 'minutes is required for extend_session requests',
        });
      }

      let device = null;
      if (device_id) {
        device = await deviceService.getDeviceById(request.userId!, device_id);
        if (!device || device.child_id !== id) {
          return reply.status(403).send({
            success: false,
            error: 'Device does not belong to this child or user',
          });
        }
      }

      const requestId = uuidv4();

      const event: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'policy',
        subtype: type === 'extend_session' ? 'extend_time_requested' : 'unblock_requested',
        occurred_at: new Date().toISOString(),
        source: {
          agent_type: 'child_app',
          agent_version: 'unknown',
          device_id: device_id,
          child_id: id,
        },
        context: {
          type,
          device_id,
          child_id: id,
        },
        payload: {
          request_id: requestId,
          session_id,
          minutes,
          resource,
        },
      };

      await storeGuardianEvents([event]);

      logger.info('Child request created', {
        userId: request.userId,
        childId: id,
        type,
        deviceId: device_id,
        requestId,
      });

      return reply.status(201).send({
        success: true,
        request_id: requestId,
      });
    } catch (error) {
      logger.error('Create child request error', { userId: request.userId, childId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to create child request',
      });
    }
  });

  /**
   * GET /stats/batch
   * Get statistics for multiple children (batch operation)
   */
  fastify.get('/stats/batch', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const idsParam = (request.query as any).ids as string;

      if (!idsParam) {
        return reply.status(400).send({
          success: false,
          error: 'Child IDs are required (comma-separated)',
        });
      }

      const childIds = idsParam.split(',').map(id => id.trim());

      if (childIds.length === 0) {
        return reply.send({
          success: true,
          data: {},
        });
      }

      const stats = await childrenService.getChildrenBatchStats(request.userId!, childIds);

      return reply.send({
        success: true,
        data: stats,
      });
    } catch (error) {
      console.error('Get batch child stats error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch children statistics',
      });
    }
  });

  /**
   * POST /:id/requests/:requestId/decision
   * Record parent decision on a child request and optionally enqueue a command
   */
  fastify.post('/:id/requests/:requestId/decision', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id, requestId } = request.params as { id: string; requestId: string };

      if (!id || !requestId) {
        return reply.status(400).send({
          success: false,
          error: 'Child ID and requestId are required',
        });
      }

      const child = await childrenService.getChildById(request.userId!, id);
      if (!child) {
        return reply.status(404).send({
          success: false,
          error: 'Child not found',
        });
      }

      const validation = childRequestDecisionSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const { type, decision, minutes_granted, device_id, session_id } = validation.data;

      if (type === 'extend_session' && decision === 'approved' && !minutes_granted) {
        return reply.status(400).send({
          success: false,
          error: 'minutes_granted is required when approving extend_session requests',
        });
      }

      let device = null;
      if (device_id) {
        device = await deviceService.getDeviceById(request.userId!, device_id);
        if (!device || device.child_id !== id) {
          return reply.status(403).send({
            success: false,
            error: 'Device does not belong to this child or user',
          });
        }
      }

      let commandId: string | undefined;

      if (decision === 'approved' && device) {
        if (type === 'extend_session') {
          commandId = await enqueueDeviceCommand({
            deviceId: device.id,
            childId: device.child_id || undefined,
            orgId: undefined,
            kind: 'session_request',
            action: 'extend_session',
            params: {
              request_id: requestId,
              minutes_granted,
              session_id,
            },
            issuedByActorType: 'parent',
            issuedByUserId: request.userId!,
            expiresAt: undefined,
          });
        } else if (type === 'unblock') {
          commandId = await enqueueDeviceCommand({
            deviceId: device.id,
            childId: device.child_id || undefined,
            orgId: undefined,
            kind: 'session_request',
            action: 'temporary_unblock',
            params: {
              request_id: requestId,
              session_id,
            },
            issuedByActorType: 'parent',
            issuedByUserId: request.userId!,
            expiresAt: undefined,
          });
        }
      }

      const subtype = type === 'extend_session' ? 'extend_time_decision' : 'unblock_decision';

      const event: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'policy',
        subtype,
        occurred_at: new Date().toISOString(),
        source: {
          agent_type: 'backend',
          agent_version: process.env.npm_package_version || '1.0.0',
          device_id: device_id,
          child_id: id,
        },
        context: {
          type,
          device_id,
          child_id: id,
        },
        payload: {
          request_id: requestId,
          decision,
          minutes_granted,
          session_id,
          command_id: commandId,
        },
      };

      await storeGuardianEvents([event]);

      logger.info('Child request decision recorded', {
        userId: request.userId,
        childId: id,
        requestId,
        type,
        decision,
        deviceId: device_id,
        commandId,
      });

      return reply.send({
        success: true,
        message: 'Request decision recorded',
        command_id: commandId,
      });
    } catch (error) {
      logger.error('Child request decision error', { userId: request.userId, childId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to record request decision',
      });
    }
  });

  /**
   * GET /:id/stats
   * Get statistics for a child
   */
  fastify.get('/:id/stats', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Child ID is required',
        });
      }

      // Verify child belongs to user
      const child = await childrenService.getChildById(request.userId!, id);
      if (!child) {
        return reply.status(404).send({
          success: false,
          error: 'Child not found',
        });
      }

      const stats = await childrenService.getChildStats(request.userId!, id);

      return reply.send({
        success: true,
        data: stats,
      });
    } catch (error) {
      console.error('Get child stats error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch child statistics',
      });
    }
  });

  /**
   * POST /
   * Create a new child profile
   */
  fastify.post('/', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validation = createChildSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const child = await childrenService.createChild(request.userId!, validation.data);

      // Audit logging
      await logAuditEvent(
        request.userId!,
        AuditEvents.CHILD_CREATED,
        {
          child_id: child.id,
          name: child.name,
          birth_date: child.birth_date,
        },
        request as any,
        'info'
      );

      // Update metrics
      childrenRegistered.inc();

      // Structured logging
      logger.info('Child profile created', {
        userId: request.userId,
        childId: child.id,
        name: child.name,
      });

      return reply.status(201).send({
        success: true,
        data: {
          ...child,
          age: childrenService.calculateAge(child.birth_date),
        },
        message: 'Child profile created successfully',
      });
    } catch (error) {
      logger.error('Create child error', { userId: request.userId, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to create child profile',
      });
    }
  });

  /**
   * PUT /:id
   * Update a child profile
   */
  fastify.put('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Child ID is required',
        });
      }

      const validation = updateChildSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const child = await childrenService.updateChild(request.userId!, id, validation.data);

      if (!child) {
        return reply.status(404).send({
          success: false,
          error: 'Child not found',
        });
      }

      // Audit logging
      await logAuditEvent(
        request.userId!,
        AuditEvents.CHILD_UPDATED,
        {
          child_id: id,
          updates: validation.data,
        },
        request as any,
        'info'
      );

      // Structured logging
      logger.info('Child profile updated', {
        userId: request.userId,
        childId: id,
        updates: Object.keys(validation.data),
      });

      return reply.send({
        success: true,
        data: {
          ...child,
          age: childrenService.calculateAge(child.birth_date),
        },
        message: 'Child profile updated successfully',
      });
    } catch (error) {
      logger.error('Update child error', { userId: request.userId, childId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to update child profile',
      });
    }
  });

  /**
   * DELETE /:id
   * Delete a child profile (soft delete)
   */
  fastify.delete('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Child ID is required',
        });
      }

      const deleted = await childrenService.deleteChild(request.userId!, id);

      if (!deleted) {
        return reply.status(404).send({
          success: false,
          error: 'Child not found',
        });
      }

      // Audit logging
      await logAuditEvent(
        request.userId!,
        AuditEvents.CHILD_DELETED,
        { child_id: id },
        request as any,
        'warning'
      );

      // Update metrics
      childrenRegistered.dec();

      // Structured logging
      logger.warn('Child profile deleted', {
        userId: request.userId,
        childId: id,
      });

      return reply.send({
        success: true,
        message: 'Child profile deleted successfully',
      });
    } catch (error) {
      logger.error('Delete child error', { userId: request.userId, childId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to delete child profile',
      });
    }
  });
};

export default childrenRoutes;
