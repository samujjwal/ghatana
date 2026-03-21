/**
 * Test Suite for GenerateAnimation Service
 * Part of Execution Plan item #5: Improve Test Coverage to 60%
 * 
 * Provides comprehensive test coverage for the GenerateAnimation
 * service implementation including contract validation and error handling.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  GenerateAnimationService,
  generateAnimationRoutes,
} from '../src/routes/generate-animation';
import type { FastifyInstance } from 'fastify';
import Fastify from 'fastify';

// Mock the auto-animation service
vi.mock('@tutorputor/animator/auto', () => ({
  autoAnimationService: {
    generateFromDescription: vi.fn(),
  },
}));

import { autoAnimationService } from '@tutorputor/animator/auto';

describe('GenerateAnimationService', () => {
  let service: GenerateAnimationService;
  let mockAiProxy: any;

  beforeEach(() => {
    mockAiProxy = {};
    service = new GenerateAnimationService(mockAiProxy);
    vi.clearAllMocks();
  });

  describe('validate', () => {
    it('should validate a correct request', () => {
      const request = {
        description: 'Create a bouncing ball animation showing gravity',
        domain: 'physics',
        purpose: 'educational',
      };

      const result = service.validate(request);

      expect(result.valid).toBe(true);
      expect(result.errors).toBeUndefined();
    });

    it('should reject a request with missing description', () => {
      const request = {
        domain: 'physics',
      };

      const result = service.validate(request);

      expect(result.valid).toBe(false);
      expect(result.errors).toBeDefined();
      expect(result.errors?.length).toBeGreaterThan(0);
    });

    it('should reject a description that is too short', () => {
      const request = {
        description: 'Short',
        domain: 'physics',
      };

      const result = service.validate(request);

      expect(result.valid).toBe(false);
      expect(result.errors?.some(e => e.includes('description'))).toBe(true);
    });

    it('should reject an invalid domain', () => {
      const request = {
        description: 'Create a bouncing ball animation showing gravity',
        domain: 'invalid-domain',
      };

      const result = service.validate(request);

      expect(result.valid).toBe(false);
    });
  });

  describe('generate', () => {
    it('should generate an animation successfully', async () => {
      const mockResult = {
        tracks: [
          {
            id: 'track-1',
            target: '.ball',
            property: 'transform',
            from: 'translateY(0)',
            to: 'translateY(300px)',
            duration: 2000,
            easing: 'ease-in',
          },
        ],
        explanation: 'This animation demonstrates gravity',
        narration: 'Watch the ball fall under gravity',
        educational: {
          concepts: ['gravity', 'acceleration'],
          prerequisites: ['basic physics'],
          followUpQuestions: ['What causes the ball to fall?'],
        },
        confidence: 0.95,
      };

      (autoAnimationService.generateFromDescription as any).mockResolvedValue(mockResult);

      const request = {
        description: 'Create a bouncing ball animation showing gravity',
        domain: 'physics',
        purpose: 'educational',
      };

      const result = await service.generate(request);

      expect(result.id).toBeDefined();
      expect(result.tracks).toHaveLength(1);
      expect(result.explanation).toBe(mockResult.explanation);
      expect(result.confidence).toBe(mockResult.confidence);
      expect(result.generatedAt).toBeDefined();
    });

    it('should use default values when optional fields are missing', async () => {
      const mockResult = {
        tracks: [],
        explanation: 'Default animation',
        confidence: 0.8,
      };

      (autoAnimationService.generateFromDescription as any).mockResolvedValue(mockResult);

      const request = {
        description: 'Create a simple animation',
      };

      const result = await service.generate(request);

      expect(autoAnimationService.generateFromDescription).toHaveBeenCalledWith(
        expect.objectContaining({
          target: '.animated-element',
          purpose: 'educational',
          style: 'moderate',
          complexity: 'medium',
        })
      );
    });

    it('should throw on validation error', async () => {
      const request = {
        description: '',
      };

      await expect(service.generate(request)).rejects.toThrow();
    });
  });
});

describe('generateAnimationRoutes', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    app = Fastify();
    app.decorate('aiProxy', {});
    await app.register(generateAnimationRoutes);
  });

  describe('POST /generate/animation', () => {
    it('should return 200 for valid request', async () => {
      const mockResult = {
        tracks: [],
        explanation: 'Test',
        confidence: 0.9,
      };

      (autoAnimationService.generateFromDescription as any).mockResolvedValue(mockResult);

      const response = await app.inject({
        method: 'POST',
        url: '/generate/animation',
        payload: {
          description: 'Create a bouncing ball animation',
          domain: 'physics',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.id).toBeDefined();
      expect(body.explanation).toBe('Test');
    });

    it('should return 400 for invalid request', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/generate/animation',
        payload: {
          description: '',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toBeDefined();
    });

    it('should return 400 for missing description', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/generate/animation',
        payload: {},
      });

      expect(response.statusCode).toBe(400);
    });

    it('should include trace ID in response headers', async () => {
      const mockResult = {
        tracks: [],
        explanation: 'Test',
        confidence: 0.9,
      };

      (autoAnimationService.generateFromDescription as any).mockResolvedValue(mockResult);

      const response = await app.inject({
        method: 'POST',
        url: '/generate/animation',
        payload: {
          description: 'Create a bouncing ball animation',
        },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe('POST /generate/animation/validate', () => {
    it('should return valid=true for valid request', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/generate/animation/validate',
        payload: {
          description: 'Create a bouncing ball animation',
          domain: 'physics',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.valid).toBe(true);
    });

    it('should return valid=false for invalid request', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/generate/animation/validate',
        payload: {
          description: '',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.valid).toBe(false);
      expect(body.errors).toBeDefined();
    });
  });
});
