/**
 * GenerateAnimation Service Implementation
 * Part of Execution Plan item #4: Complete GenerateAnimation Implementation
 * 
 * This service provides end-to-end contract alignment for animation generation,
 * integrating with AI platform to generate educational animations from natural language.
 */

import { FastifyInstance, FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { autoAnimationService } from '@tutorputor/animator/auto';
import type { AutoAnimationRequest } from '@tutorputor/animator/auto';

// Contract-aligned request schema
const GenerateAnimationRequestSchema = z.object({
  description: z.string().min(10).max(5000),
  target: z.string().optional(),
  purpose: z.enum(['educational', 'decorative', 'functional']).optional(),
  domain: z.enum(['physics', 'chemistry', 'biology', 'math', 'cs', 'general']).optional(),
  learningObjective: z.string().optional(),
  duration: z.number().int().min(100).max(30000).optional(),
  style: z.enum(['subtle', 'moderate', 'dramatic']).optional(),
  complexity: z.enum(['simple', 'medium', 'complex']).optional(),
  audience: z.enum(['beginner', 'intermediate', 'advanced']).optional(),
});

// Contract-aligned response schema
const GenerateAnimationResponseSchema = z.object({
  id: z.string(),
  tracks: z.array(z.object({
    id: z.string(),
    target: z.string(),
    property: z.string(),
    from: z.any(),
    to: z.any(),
    duration: z.number(),
    delay: z.number().optional(),
    easing: z.string().optional(),
    keyframes: z.array(z.object({
      time: z.number(),
      value: z.any(),
    })).optional(),
  })),
  explanation: z.string(),
  narration: z.string().optional(),
  educational: z.object({
    concepts: z.array(z.string()),
    prerequisites: z.array(z.string()),
    followUpQuestions: z.array(z.string()),
  }).optional(),
  confidence: z.number().min(0).max(1),
  generatedAt: z.string().datetime(),
});

type GenerateAnimationRequest = z.infer<typeof GenerateAnimationRequestSchema>;
type GenerateAnimationResponse = z.infer<typeof GenerateAnimationResponseSchema>;

/**
 * GenerateAnimation Service
 */
export class GenerateAnimationService {
  private readonly aiProxy: any; // Would be typed from @tutorputor/ai-proxy

  constructor(aiProxy: any) {
    this.aiProxy = aiProxy;
  }

  /**
   * Generate animation from request
   */
  async generate(request: GenerateAnimationRequest): Promise<GenerateAnimationResponse> {
    // Validate request
    const validated = GenerateAnimationRequestSchema.parse(request);

    // Convert to auto-animation request format
    const autoRequest: AutoAnimationRequest = {
      description: validated.description,
      target: validated.target || '.animated-element',
      purpose: validated.purpose || 'educational',
      domain: validated.domain,
      learningObjective: validated.learningObjective,
      duration: validated.duration,
      style: validated.style || 'moderate',
      complexity: validated.complexity || 'medium',
      audience: validated.audience,
    };

    // Generate animation using auto-animation service
    const result = await autoAnimationService.generateFromDescription(autoRequest);

    // Transform to contract-aligned response
    const response: GenerateAnimationResponse = {
      id: `anim-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      tracks: result.tracks.map(track => ({
        id: track.id,
        target: track.target,
        property: track.property,
        from: track.from,
        to: track.to,
        duration: track.duration,
        delay: track.delay,
        easing: track.easing,
        keyframes: track.keyframes?.map(kf => ({
          time: kf.time,
          value: kf.value,
        })),
      })),
      explanation: result.explanation,
      narration: result.narration,
      educational: result.educational,
      confidence: result.confidence,
      generatedAt: new Date().toISOString(),
    };

    // Validate response against contract
    return GenerateAnimationResponseSchema.parse(response);
  }

  /**
   * Validate request without generating
   */
  validate(request: unknown): { valid: boolean; errors?: string[] } {
    const result = GenerateAnimationRequestSchema.safeParse(request);
    if (result.success) {
      return { valid: true };
    }
    return {
      valid: false,
      errors: result.error.errors.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }
}

/**
 * Fastify plugin for GenerateAnimation routes
 */
export const generateAnimationRoutes: FastifyPluginAsync = async (fastify: FastifyInstance) => {
  const service = new GenerateAnimationService(fastify.aiProxy);

  // POST /generate/animation
  fastify.post<{ Body: GenerateAnimationRequest }>(
    '/generate/animation',
    {
      schema: {
        description: 'Generate educational animation from natural language description',
        tags: ['Content Generation'],
        body: {
          type: 'object',
          required: ['description'],
          properties: {
            description: { type: 'string', minLength: 10, maxLength: 5000 },
            target: { type: 'string' },
            purpose: { type: 'string', enum: ['educational', 'decorative', 'functional'] },
            domain: { type: 'string', enum: ['physics', 'chemistry', 'biology', 'math', 'cs', 'general'] },
            learningObjective: { type: 'string' },
            duration: { type: 'integer', minimum: 100, maximum: 30000 },
            style: { type: 'string', enum: ['subtle', 'moderate', 'dramatic'] },
            complexity: { type: 'string', enum: ['simple', 'medium', 'complex'] },
            audience: { type: 'string', enum: ['beginner', 'intermediate', 'advanced'] },
          },
        },
        response: {
          200: {
            description: 'Animation generated successfully',
            type: 'object',
            properties: {
              id: { type: 'string' },
              tracks: {
                type: 'array',
                items: {
                  type: 'object',
                  properties: {
                    id: { type: 'string' },
                    target: { type: 'string' },
                    property: { type: 'string' },
                    from: {},
                    to: {},
                    duration: { type: 'number' },
                    delay: { type: 'number' },
                    easing: { type: 'string' },
                  },
                },
              },
              explanation: { type: 'string' },
              narration: { type: 'string' },
              confidence: { type: 'number' },
              generatedAt: { type: 'string' },
            },
          },
          400: {
            description: 'Invalid request',
            type: 'object',
            properties: {
              error: { type: 'string' },
              message: { type: 'string' },
            },
          },
        },
      },
    },
    async (request, reply) => {
      try {
        const result = await service.generate(request.body);
        return reply.code(200).send(result);
      } catch (error) {
        if (error instanceof z.ZodError) {
          return reply.code(400).send({
            error: 'ValidationError',
            message: error.errors.map(e => `${e.path.join('.')}: ${e.message}`).join(', '),
          });
        }
        fastify.log.error(error);
        return reply.code(500).send({
          error: 'InternalError',
          message: 'Failed to generate animation',
        });
      }
    }
  );

  // POST /generate/animation/validate
  fastify.post<{ Body: unknown }>(
    '/generate/animation/validate',
    {
      schema: {
        description: 'Validate animation generation request without generating',
        tags: ['Content Generation'],
      },
    },
    async (request, reply) => {
      const result = service.validate(request.body);
      return reply.code(200).send(result);
    }
  );
};

export default generateAnimationRoutes;
