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
import { FeatureFlagService } from './FeatureFlagService.js';

/**
 * Feature flags module plugin
 */
export const featureFlagsModule: FastifyPluginAsync = async (app) => {
  app.log.info('Initializing feature flags module...');

  const featureFlagService = new FeatureFlagService();
  app.decorate('featureFlagService', featureFlagService);

  // Admin endpoint to list all flags
  app.get('/api/v1/feature-flags', {
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

  // Admin endpoint to check a flag for current user
  app.get('/api/v1/feature-flags/:key', {
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

  // Admin endpoint to enable a flag
  app.post('/api/v1/feature-flags/:key/enable', {
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
    return reply.send({ success: true, key, enabled: true });
  });

  // Admin endpoint to disable a flag
  app.post('/api/v1/feature-flags/:key/disable', {
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
    return reply.send({ success: true, key, enabled: false });
  });

  // Admin endpoint to set rollout percentage
  app.post('/api/v1/feature-flags/:key/rollout', {
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
    return reply.send({ success: true, key, rolloutPercentage: percentage });
  });

  app.log.info('✅ Feature flags module routes registered');
};
