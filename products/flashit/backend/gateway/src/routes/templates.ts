/**
 * Templates API Routes
 * Routes for template management
 *
 * @doc.type routes
 * @doc.purpose Template CRUD and search operations
 * @doc.layer product
 * @doc.pattern RestAPI
 */

import { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { getTemplateEngine } from '../services/templateEngine';

// ============================================================================
// Schemas
// ============================================================================

const FieldValidationSchema = z.object({
  min: z.number().optional(),
  max: z.number().optional(),
  minLength: z.number().optional(),
  maxLength: z.number().optional(),
  pattern: z.string().optional(),
  message: z.string().optional(),
});

const TemplateFieldSchema = z.object({
  id: z.string(),
  type: z.enum(['text', 'textarea', 'number', 'date', 'time', 'select', 'multiselect', 'checkbox', 'rating', 'emotion']),
  label: z.string(),
  placeholder: z.string().optional(),
  required: z.boolean(),
  options: z.array(z.string()).optional(),
  defaultValue: z.unknown().optional(),
  validation: FieldValidationSchema.optional(),
});

const CreateTemplateSchema = z.object({
  name: z.string().min(1).max(100),
  description: z.string().min(1).max(500),
  category: z.enum(['journal', 'gratitude', 'reflection', 'goal', 'custom']),
  icon: z.string().optional(),
  color: z.string().optional(),
  fields: z.array(TemplateFieldSchema).min(1),
  tags: z.array(z.string()),
  isPublic: z.boolean(),
});

const UpdateTemplateSchema = CreateTemplateSchema.partial();

const ValidateInstanceSchema = z.object({
  templateId: z.string(),
  values: z.record(z.unknown()),
});

const RenderTemplateSchema = z.object({
  templateId: z.string(),
  values: z.record(z.unknown()),
});

// ============================================================================
// Routes Plugin
// ============================================================================

const templatesRoutes: FastifyPluginAsync = async (fastify) => {
  const engine = getTemplateEngine();

  /**
   * Get all templates
   */
  fastify.get('/', async (request, reply) => {
    try {
      const templates = engine.getAllTemplates();

      return {
        success: true,
        data: templates,
        count: templates.length,
      };
    } catch (error) {
      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch templates',
      });
    }
  });

  /**
   * Get templates by category
   */
  fastify.get('/category/:category', async (request, reply) => {
    try {
      const { category } = request.params as { category: string };

      if (!['journal', 'gratitude', 'reflection', 'goal', 'custom'].includes(category)) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid category',
        });
      }

      const templates = engine.getTemplatesByCategory(category as any);

      return {
        success: true,
        data: templates,
        count: templates.length,
      };
    } catch (error) {
      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch templates',
      });
    }
  });

  /**
   * Get template by ID
   */
  fastify.get('/:id', async (request, reply) => {
    try {
      const { id } = request.params as { id: string };
      const template = engine.getTemplate(id);

      if (!template) {
        return reply.status(404).send({
          success: false,
          error: 'Template not found',
        });
      }

      return {
        success: true,
        data: template,
      };
    } catch (error) {
      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch template',
      });
    }
  });

  /**
   * Create custom template
   */
  fastify.post(
    '/',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      try {
        const body = CreateTemplateSchema.parse(request.body);
        const userId = request.user!.id;

        const template = engine.createTemplate({
          ...body,
          createdBy: userId,
        });

        return reply.status(201).send({
          success: true,
          data: template,
        });
      } catch (error) {
        if (error instanceof z.ZodError) {
          return reply.status(400).send({
            success: false,
            error: 'Validation error',
            details: error.errors,
          });
        }

        fastify.log.error(error);
        return reply.status(500).send({
          success: false,
          error: 'Failed to create template',
        });
      }
    }
  );

  fastify.put('/:id', async (request, reply) => {
    try {
      const { id } = request.params as { id: string };
      const body = UpdateTemplateSchema.parse(request.body);

      const template = engine.updateTemplate(id, body);

      if (!template) {
        return reply.status(404).send({
          success: false,
          error: 'Template not found',
        });
      }

      return {
        success: true,
        data: template,
      };
    } catch (error) {
      if (error instanceof z.ZodError) {
        return reply.status(400).send({
          success: false,
          error: 'Validation error',
          details: error.errors,
        });
      }

      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to update template',
      });
    }
  });

  /**
   * Delete template
   */
  fastify.delete('/:id', async (request, reply) => {
    try {
      const { id } = request.params as { id: string };
      const deleted = engine.deleteTemplate(id);

      if (!deleted) {
        return reply.status(404).send({
          success: false,
          error: 'Template not found or cannot be deleted',
        });
      }

      return {
        success: true,
        message: 'Template deleted successfully',
      };
    } catch (error) {
      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to delete template',
      });
    }
  });

  /**
   * Validate template instance
   */
  fastify.post('/validate', async (request, reply) => {
    try {
      const body = ValidateInstanceSchema.parse(request.body);
      const errors = engine.validateInstance(body.templateId, body.values);

      return {
        success: errors.length === 0,
        valid: errors.length === 0,
        errors,
      };
    } catch (error) {
      if (error instanceof z.ZodError) {
        return reply.status(400).send({
          success: false,
          error: 'Validation error',
          details: error.errors,
        });
      }

      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to validate instance',
      });
    }
  });

  /**
   * Render template
   */
  fastify.post('/render', async (request, reply) => {
    try {
      const body = RenderTemplateSchema.parse(request.body);
      const rendered = engine.renderTemplate(body.templateId, body.values);

      if (!rendered) {
        return reply.status(404).send({
          success: false,
          error: 'Template not found',
        });
      }

      // Increment usage count
      engine.incrementUsageCount(body.templateId);

      return {
        success: true,
        data: {
          markdown: rendered,
        },
      };
    } catch (error) {
      if (error instanceof z.ZodError) {
        return reply.status(400).send({
          success: false,
          error: 'Validation error',
          details: error.errors,
        });
      }

      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to render template',
      });
    }
  });

  /**
   * Search templates
   */
  fastify.get('/search/:query', async (request, reply) => {
    try {
      const { query } = request.params as { query: string };
      const templates = engine.searchTemplates(query);

      return {
        success: true,
        data: templates,
        count: templates.length,
      };
    } catch (error) {
      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to search templates',
      });
    }
  });

  /**
   * Get popular templates
   */
  fastify.get('/popular/:limit?', async (request, reply) => {
    try {
      const { limit } = request.params as { limit?: string };
      const limitNum = limit ? parseInt(limit, 10) : 5;

      if (isNaN(limitNum) || limitNum < 1 || limitNum > 50) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid limit (must be between 1 and 50)',
        });
      }

      const templates = engine.getPopularTemplates(limitNum);

      return {
        success: true,
        data: templates,
        count: templates.length,
      };
    } catch (error) {
      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch popular templates',
      });
    }
  });

  /**
   * Get recent templates
   */
  fastify.get('/recent/:limit?', async (request, reply) => {
    try {
      const { limit } = request.params as { limit?: string };
      const limitNum = limit ? parseInt(limit, 10) : 5;

      if (isNaN(limitNum) || limitNum < 1 || limitNum > 50) {
        return reply.status(400).send({
          success: false,
          error: 'Invalid limit (must be between 1 and 50)',
        });
      }

      const templates = engine.getRecentTemplates(limitNum);

      return {
        success: true,
        data: templates,
        count: templates.length,
      };
    } catch (error) {
      fastify.log.error(error);
      return reply.status(500).send({
        success: false,
        error: 'Failed to fetch recent templates',
      });
    }
  });
};

export default templatesRoutes;
