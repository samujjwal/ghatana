/**
 * Adoption & Engagement API Routes
 * Week 14 Day 68 - Smart suggestions and usage nudges
 * 
 * @doc.type route
 * @doc.purpose Personalized suggestions and onboarding progress
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { requireAuth } from '../lib/auth.js';
import { AdoptionLoopsService } from '../services/adoption/adoption-loops-service.js';

// Schemas
const suggestionInteractionSchema = z.object({
  suggestionType: z.enum(['moment_reminder', 'link_suggestion', 'reflection_prompt', 'expansion_opportunity']),
  action: z.enum(['viewed', 'clicked', 'dismissed']),
});

export default async function adoptionRoutes(fastify: FastifyInstance) {
  /**
   * Get personalized suggestions
   * GET /api/adoption/suggestions
   */
  fastify.get('/suggestions', {
    onRequest: [requireAuth],
  }, async (request, reply) => {
    const userId = (request.user as any).userId;

    try {
      const suggestions = await AdoptionLoopsService.generateSuggestions(userId);

      return {
        suggestions,
        count: suggestions.length,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      fastify.log.error(error, 'Failed to generate suggestions');
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to generate suggestions',
      });
    }
  });

  /**
   * Get onboarding checklist
   * GET /api/adoption/onboarding
   */
  fastify.get('/onboarding', {
    onRequest: [requireAuth],
  }, async (request, reply) => {
    const userId = (request.user as any).userId;

    try {
      const steps = await AdoptionLoopsService.getOnboardingStatus(userId);
      const completed = steps.filter((s) => s.completed).length;
      const total = steps.length;
      const progress = Math.round((completed / total) * 100);

      return {
        steps,
        progress: {
          completed,
          total,
          percentage: progress,
        },
        isComplete: completed === total,
      };
    } catch (error) {
      fastify.log.error(error, 'Failed to get onboarding status');
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get onboarding status',
      });
    }
  });

  /**
   * Record suggestion interaction
   * POST /api/adoption/suggestions/interactions
   */
  fastify.post('/suggestions/interactions', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(suggestionInteractionSchema),
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { suggestionType, action } = suggestionInteractionSchema.parse(request.body);

    try {
      await AdoptionLoopsService.recordSuggestionInteraction(userId, suggestionType, action);

      return { success: true };
    } catch (error) {
      fastify.log.error('Failed to record suggestion interaction:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to record interaction',
      });
    }
  });

  /**
   * Get user engagement metrics
   * GET /api/adoption/engagement
   */
  fastify.get('/engagement', {
    onRequest: [requireAuth],
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const engagement = await AdoptionLoopsService.getUserEngagement(userId);

      return {
        ...engagement,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      fastify.log.error('Failed to get user engagement:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get engagement metrics',
      });
    }
  });

  /**
   * Get recommended reminder settings
   * GET /api/adoption/reminder-settings
   */
  fastify.get('/reminder-settings', {
    onRequest: [requireAuth],
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const settings = await AdoptionLoopsService.getRecommendedReminderCadence(userId);

      return {
        ...settings,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      fastify.log.error('Failed to get reminder settings:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get reminder settings',
      });
    }
  });
}
