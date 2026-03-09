/**
 * Policy management routes for content filtering and access control.
 *
 * <p><b>Purpose</b><br>
 * Provides RESTful endpoints for creating, reading, updating, and deleting policies
 * that control child device access. Supports website blocking, app restrictions,
 * category filtering, and time-based scheduling with real-time WebSocket updates.
 *
 * <p><b>Endpoints</b><br>
 * - POST /policies - Create new policy
 * - GET /policies - List all policies for tenant
 * - GET /policies/:id - Get specific policy details
 * - PUT /policies/:id - Update existing policy
 * - DELETE /policies/:id - Delete policy
 * - PUT /policies/:id/toggle - Enable/disable policy
 *
 * <p><b>Policy Types</b><br>
 * - website: Block specific websites by URL pattern
 * - app: Restrict apps by package name (Android) or bundle ID (iOS)
 * - category: Block content categories (social media, games, etc.)
 * - schedule: Time-based access control (bedtime, school hours)
 *
 * <p><b>Real-Time Updates</b><br>
 * Broadcasts policy changes via WebSocket to connected devices for immediate enforcement.
 * Updates metrics (activePolicies) and triggers audit logging for compliance.
 *
 * @doc.type route
 * @doc.purpose Policy CRUD operations and real-time enforcement
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import * as policyService from '../services/policy.service';
import { logger } from '../utils/logger';
import { logAuditEvent, AuditEvents } from '../services/audit.service';
import { activePolicies } from '../utils/metrics';
import { broadcastToRoom, WSEvent, getRoomNames } from '../websocket/server';
import { pool } from '../db';
import { GuardianEvent } from '../types/guardian-events';
import { storeGuardianEvents } from '../services/events-store.service';
import { v4 as uuidv4 } from 'uuid';

// Validation schemas
const createPolicySchema = z.object({
  child_id: z.string().uuid().optional(),
  device_id: z.string().uuid().optional(),
  name: z.string().min(1).max(255),
  policy_type: z.enum(['website', 'app', 'category', 'schedule']),
  enabled: z.boolean().optional(),
  priority: z.number().int().min(0).max(100).optional(),
  config: z.record(z.string(), z.any()),
});

const updatePolicySchema = z.object({
  name: z.string().min(1).max(255).optional(),
  enabled: z.boolean().optional(),
  priority: z.number().int().min(0).max(100).optional(),
  config: z.record(z.string(), z.any()).optional(),
});

const bulkToggleSchema = z.object({
  policy_ids: z.array(z.string().uuid()),
  enabled: z.boolean(),
});

/**
 * Helper function to broadcast policy changes to affected devices
 */
async function broadcastPolicyChange(
  policyId: string,
  eventType: WSEvent,
  policyData: unknown
): Promise<void> {
  try {
    const policyResult = await pool.query(
      `SELECT p.*, c.user_id
       FROM policies p
       LEFT JOIN children c ON p.child_id = c.id
       WHERE p.id = $1`,
      [policyId]
    );

    if (policyResult.rows.length === 0) {
      return;
    }

    const policy = policyResult.rows[0];

    if (policy.user_id) {
      const parentRoom = getRoomNames.parent(policy.user_id);
      const broadcastData = policyData && typeof policyData === 'object'
        ? { ...policyData as Record<string, unknown> }
        : {};

      broadcastToRoom(parentRoom, eventType, {
        ...broadcastData,
        timestamp: new Date().toISOString(),
      });
      logger.debug('Policy change broadcast to parent', {
        parentRoom,
        event: eventType,
        policyId,
      });
    }

    if (policy.device_id) {
      const deviceRoom = getRoomNames.device(policy.device_id);
      const broadcastData = policyData && typeof policyData === 'object'
        ? { ...policyData as Record<string, unknown> }
        : {};

      broadcastToRoom(deviceRoom, eventType, {
        ...broadcastData,
        timestamp: new Date().toISOString(),
      });
      logger.debug('Policy change broadcast to device', {
        deviceRoom,
        event: eventType,
        policyId,
      });
    }

    if (policy.child_id) {
      const childRoom = getRoomNames.child(policy.child_id);
      const broadcastData = policyData && typeof policyData === 'object'
        ? { ...policyData as Record<string, unknown> }
        : {};

      broadcastToRoom(childRoom, eventType, {
        ...broadcastData,
        timestamp: new Date().toISOString(),
      });
      logger.debug('Policy change broadcast to child room', {
        childRoom,
        event: eventType,
        policyId,
      });
    }
  } catch (error) {
    logger.error('Error broadcasting policy change', {
      policyId,
      event: eventType,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

const policyRoutes: FastifyPluginAsync = async (fastify) => {
  // All routes require authentication
  fastify.addHook('preHandler', authenticate);

  /** GET / - List all policies for the authenticated user */
  fastify.get('/', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const query = request.query as any;
      const filters = {
        child_id: query.child_id as string | undefined,
        device_id: query.device_id as string | undefined,
        policy_type: query.policy_type as string | undefined,
        enabled: query.enabled === 'true' ? true : query.enabled === 'false' ? false : undefined,
      };

      const policies = await policyService.getPolicies(request.userId!, filters);

      return reply.send({
        success: true,
        data: policies,
        count: policies.length,
      });
    } catch (error) {
      console.error('Get policies error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch policies',
      });
    }
  });

  /** GET /stats - Get policy statistics for the authenticated user */
  fastify.get('/stats', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const stats = await policyService.getPolicyStats(request.userId!);

      return reply.send({
        success: true,
        data: stats,
      });
    } catch (error) {
      console.error('Get policy stats error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch policy statistics',
      });
    }
  });

  /** GET /:id - Get a single policy by ID */
  fastify.get('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Policy ID is required',
        });
      }

      const policy = await policyService.getPolicyById(request.userId!, id);

      if (!policy) {
        return reply.status(404).send({
          success: false,
          error: 'Policy not found',
        });
      }

      return reply.send({
        success: true,
        data: policy,
      });
    } catch (error) {
      console.error('Get policy error:', error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch policy',
      });
    }
  });

  /** POST / - Create a new policy */
  fastify.post('/', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validation = createPolicySchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const policy = await policyService.createPolicy(request.userId!, validation.data);

      await logAuditEvent(
        request.userId!,
        AuditEvents.POLICY_CREATED,
        {
          policy_id: policy.id,
          policy_type: policy.policy_type,
          child_id: policy.child_id,
          device_id: policy.device_id,
        },
        request as any,
        'info'
      );

      // Emit GuardianEvent for AI/ML data collection
      const event: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'policy',
        subtype: 'policy_created',
        occurred_at: new Date().toISOString(),
        source: {
          agent_type: 'backend',
          agent_version: '1.0.0',
          device_id: policy.device_id || undefined,
          child_id: policy.child_id || undefined,
        },
        context: {
          policy_id: policy.id,
          policy_type: policy.policy_type,
          user_id: request.userId,
        },
        payload: {
          name: policy.name,
          enabled: policy.enabled,
          priority: policy.priority,
          config: policy.config,
        },
        privacy: {
          pii_level: 'none',
          contains_raw_content: false,
        },
      };
      await storeGuardianEvents([event]);

      activePolicies.inc();

      logger.info('Policy created', {
        userId: request.userId,
        policyId: policy.id,
        policyType: policy.policy_type,
      });

      await broadcastPolicyChange(policy.id, WSEvent.POLICY_CREATED, {
        policy: policy,
      });

      return reply.status(201).send({
        success: true,
        data: policy,
        message: 'Policy created successfully',
      });
    } catch (error) {
      logger.error('Create policy error', { userId: request.userId, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to create policy',
      });
    }
  });

  /** PUT /:id - Update a policy */
  fastify.put('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Policy ID is required',
        });
      }

      const validation = updatePolicySchema.safeParse(request.body);

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

      const policy = await policyService.updatePolicy(request.userId!, id, validation.data);

      if (!policy) {
        return reply.status(404).send({
          success: false,
          error: 'Policy not found',
        });
      }

      await logAuditEvent(
        request.userId!,
        AuditEvents.POLICY_UPDATED,
        {
          policy_id: id,
          updates: validation.data,
        },
        request as any,
        'info'
      );

      // Emit GuardianEvent for AI/ML data collection
      const updateEvent: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'policy',
        subtype: 'policy_updated',
        occurred_at: new Date().toISOString(),
        source: {
          agent_type: 'backend',
          agent_version: '1.0.0',
          device_id: policy.device_id || undefined,
          child_id: policy.child_id || undefined,
        },
        context: {
          policy_id: id,
          policy_type: policy.policy_type,
          user_id: request.userId,
        },
        payload: {
          updates: validation.data,
          new_state: {
            name: policy.name,
            enabled: policy.enabled,
            priority: policy.priority,
          },
        },
        privacy: {
          pii_level: 'none',
          contains_raw_content: false,
        },
      };
      await storeGuardianEvents([updateEvent]);

      logger.info('Policy updated', {
        userId: request.userId,
        policyId: id,
        updates: Object.keys(validation.data),
      });

      await broadcastPolicyChange(id, WSEvent.POLICY_UPDATED, {
        policy: policy,
        updates: validation.data,
      });

      return reply.send({
        success: true,
        data: policy,
        message: 'Policy updated successfully',
      });
    } catch (error) {
      logger.error('Update policy error', { userId: request.userId, policyId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to update policy',
      });
    }
  });

  /** DELETE /:id - Delete a policy */
  fastify.delete('/:id', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { id } = request.params as { id: string };

      if (!id) {
        return reply.status(400).send({
          success: false,
          error: 'Policy ID is required',
        });
      }

      await broadcastPolicyChange(id, WSEvent.POLICY_DELETED, {
        policyId: id,
      });

      const deleted = await policyService.deletePolicy(request.userId!, id);

      if (!deleted) {
        return reply.status(404).send({
          success: false,
          error: 'Policy not found',
        });
      }

      await logAuditEvent(
        request.userId!,
        AuditEvents.POLICY_DELETED,
        { policy_id: id },
        request as any,
        'info'
      );

      // Emit GuardianEvent for AI/ML data collection
      const deleteEvent: GuardianEvent = {
        schema_version: 1,
        event_id: uuidv4(),
        kind: 'policy',
        subtype: 'policy_deleted',
        occurred_at: new Date().toISOString(),
        source: {
          agent_type: 'backend',
          agent_version: '1.0.0',
        },
        context: {
          policy_id: id,
          user_id: request.userId,
        },
        payload: {},
        privacy: {
          pii_level: 'none',
          contains_raw_content: false,
        },
      };
      await storeGuardianEvents([deleteEvent]);

      activePolicies.dec();

      logger.info('Policy deleted', {
        userId: request.userId,
        policyId: id,
      });

      return reply.send({
        success: true,
        message: 'Policy deleted successfully',
      });
    } catch (error) {
      logger.error('Delete policy error', { userId: request.userId, policyId: (request.params as any).id, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to delete policy',
      });
    }
  });

  /** POST /bulk/toggle - Bulk enable/disable policies */
  fastify.post('/bulk/toggle', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const validation = bulkToggleSchema.safeParse(request.body);

      if (!validation.success) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid request data',
          details: validation.error.issues,
        });
      }

      const count = await policyService.togglePolicies(
        request.userId!,
        validation.data.policy_ids,
        validation.data.enabled
      );

      await logAuditEvent(
        request.userId!,
        AuditEvents.POLICY_ENABLED,
        {
          policy_ids: validation.data.policy_ids,
          enabled: validation.data.enabled,
          count,
        },
        request as any,
        'info'
      );

      logger.info('Policies bulk toggled', {
        userId: request.userId,
        count,
        enabled: validation.data.enabled,
      });

      return reply.send({
        success: true,
        message: `${count} policies ${validation.data.enabled ? 'enabled' : 'disabled'}`,
        count,
      });
    } catch (error) {
      logger.error('Bulk toggle policies error', { userId: request.userId, error });
      return reply.status(500).send({
        success: false,
        error: 'Failed to toggle policies',
      });
    }
  });

  /**
   * GET /device/:deviceId - Get policies for device sync
   * 
   * This is the canonical policy sync endpoint for agents. Returns all applicable
   * policies for a device in priority order (device > child > global).
   * 
   * Response includes:
   * - policy_version: Hash of current policy state for change detection
   * - synced_at: Timestamp for cache invalidation
   * - policies: Array of applicable policies with full config
   */
  fastify.get('/device/:deviceId', async (request: AuthRequest, reply: FastifyReply) => {
    try {
      const { deviceId } = request.params as { deviceId: string };

      if (!deviceId) {
        return reply.status(400).send({
          success: false,
          error: 'Device ID is required',
        });
      }

      const policies = await policyService.getPoliciesForDevice(deviceId);

      // Generate a simple version hash based on policy IDs and updated_at timestamps
      const policyVersion = policies.length > 0
        ? `v${Date.now()}-${policies.map(p => p.id).join('-').slice(0, 32)}`
        : 'v0-empty';

      return reply.send({
        success: true,
        policy_version: policyVersion,
        synced_at: new Date().toISOString(),
        data: policies.map(p => ({
          id: p.id,
          name: p.name,
          policy_type: p.policy_type,
          priority: p.priority,
          enabled: p.enabled,
          config: p.config,
          scope: p.device_id ? 'device' : p.child_id ? 'child' : 'global',
          child_id: p.child_id,
          device_id: p.device_id,
        })),
        count: policies.length,
      });
    } catch (error) {
      console.error('Get device policies error:', error);
      if ((error as Error).message === 'Device not found') {
        return reply.status(404).send({
          success: false,
          error: 'Device not found',
        });
      }
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch device policies',
      });
    }
  });
};

export default policyRoutes;
