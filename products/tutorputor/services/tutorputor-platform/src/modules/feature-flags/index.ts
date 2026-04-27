/**
 * Feature Flags Module
 *
 * Provides feature flag management for controlled rollouts.
 *
 * @doc.type module
 * @doc.purpose Feature flag endpoints and management
 * @doc.layer platform
 * @doc.pattern Module
 */

import type { FastifyPluginAsync } from 'fastify';
import { getTenantId, getUserId, roleGuard } from '../../core/http/requestContext.js';
import { buildSensitiveOperationAuditEntry } from '../policy/resource-access-helpers.js';
import { FeatureFlagService } from './FeatureFlagService.js';

const adminGuard = roleGuard(['admin', 'superadmin']);

/**
 * Feature flags module plugin
 *
 * Registered under prefix /api/v1/admin, so routes here are relative:
 *   /feature-flags       → GET  /api/v1/admin/feature-flags
 *   /feature-flags/:key  → GET  /api/v1/admin/feature-flags/:key
 *   /feature-flags/:key/enable  → POST /api/v1/admin/feature-flags/:key/enable
 *   etc.
 */
export const featureFlagsModule: FastifyPluginAsync = async (app) => {
  app.log.info('Initializing feature flags module...');

  const featureFlagService = new FeatureFlagService();
  app.decorate('featureFlagService', featureFlagService);

  // Admin-only: list all flags
  app.get('/feature-flags', {
    preHandler: [adminGuard],
    schema: {
      tags: ['Feature Flags'],
      description: 'List all feature flags',
      response: {
        200: {
          type: 'object',
          properties: {
            flags: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  key: { type: 'string' },
                  enabled: { type: 'boolean' },
                  description: { type: 'string' },
                  rolloutPercentage: { type: 'number' },
                },
              },
            },
          },
        },
      },
    },
  }, async (request, reply) => {
    const flags = featureFlagService.getAllFlags();
    return reply.send({ flags });
  });

  // Admin-only: check a specific flag
  app.get('/feature-flags/:key', {
    preHandler: [adminGuard],
    schema: {
      tags: ['Feature Flags'],
      description: 'Check if a feature flag is enabled',
      params: {
        type: 'object',
        properties: {
          key: { type: 'string' },
        },
      },
      response: {
        200: {
          type: 'object',
          properties: {
            enabled: { type: 'boolean' },
            flag: { type: 'object' },
          },
        },
      },
    },
  }, async (request, reply) => {
    const { key } = request.params as { key: string };
    const userId = (request as any).user?.id;
    const enabled = featureFlagService.isEnabled(key, userId);
    const flag = featureFlagService.getFlag(key);
    return reply.send({ enabled, flag });
  });

  // Admin-only: enable a flag
  app.post('/feature-flags/:key/enable', {
    preHandler: [adminGuard],
    schema: {
      tags: ['Feature Flags'],
      description: 'Enable a feature flag',
      params: {
        type: 'object',
        properties: {
          key: { type: 'string' },
        },
      },
    },
  }, async (request, reply) => {
    const { key } = request.params as { key: string };
    featureFlagService.enable(key);
    const actorId = getUserId(request);
    const actorTenantId = getTenantId(request);
    const audit = buildSensitiveOperationAuditEntry({
      actorId,
      actorTenantId,
      targetResourceType: 'feature_flag',
      targetResourceId: key,
      operation: 'enable',
      decision: 'ALLOW',
      reason: 'Feature flag enabled by admin',
      correlationId: request.id,
      metadata: { key },
    });
    app.log.info({ audit }, 'Sensitive operation allowed');
    return reply.send({ success: true, key, enabled: true });
  });

  // Admin-only: disable a flag
  app.post('/feature-flags/:key/disable', {
    preHandler: [adminGuard],
    schema: {
      tags: ['Feature Flags'],
      description: 'Disable a feature flag',
      params: {
        type: 'object',
        properties: {
          key: { type: 'string' },
        },
      },
    },
  }, async (request, reply) => {
    const { key } = request.params as { key: string };
    featureFlagService.disable(key);
    const actorId = getUserId(request);
    const actorTenantId = getTenantId(request);
    const audit = buildSensitiveOperationAuditEntry({
      actorId,
      actorTenantId,
      targetResourceType: 'feature_flag',
      targetResourceId: key,
      operation: 'disable',
      decision: 'ALLOW',
      reason: 'Feature flag disabled by admin',
      correlationId: request.id,
      metadata: { key },
    });
    app.log.info({ audit }, 'Sensitive operation allowed');
    return reply.send({ success: true, key, enabled: false });
  });

  // Admin-only: set rollout percentage
  app.post('/feature-flags/:key/rollout', {
    preHandler: [adminGuard],
    schema: {
      tags: ['Feature Flags'],
      description: 'Set rollout percentage for a feature flag',
      params: {
        type: 'object',
        properties: {
          key: { type: 'string' },
        },
      },
      body: {
        type: 'object',
        properties: {
          percentage: { type: 'number', minimum: 0, maximum: 100 },
        },
        required: ['percentage'],
      },
    },
  }, async (request, reply) => {
    const { key } = request.params as { key: string };
    const { percentage } = request.body as { percentage: number };
    featureFlagService.setRolloutPercentage(key, percentage);
    const actorId = getUserId(request);
    const actorTenantId = getTenantId(request);
    const audit = buildSensitiveOperationAuditEntry({
      actorId,
      actorTenantId,
      targetResourceType: 'feature_flag',
      targetResourceId: key,
      operation: 'set_rollout',
      decision: 'ALLOW',
      reason: 'Feature flag rollout percentage updated by admin',
      correlationId: request.id,
      metadata: { key, percentage },
    });
    app.log.info({ audit }, 'Sensitive operation allowed');
    return reply.send({ success: true, key, rolloutPercentage: percentage });
  });

  app.log.info('✅ Feature flags module routes registered');
};
