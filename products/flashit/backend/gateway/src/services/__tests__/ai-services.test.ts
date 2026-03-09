/**
 * AI Services Tests
 * 
 * Comprehensive test coverage for AI-powered services
 * 
 * @doc.type test
 * @doc.purpose AI services testing
 * @doc.layer testing
 * @doc.pattern IntegrationTest
 */

import { describe, it, expect, beforeAll, beforeEach, vi } from 'vitest';
import { ClassificationService } from '../../services/java-agents/classification-service';
import { getCircuitBreaker } from '../../lib/circuit-breaker';

// Mock Java agent client
vi.mock('../../services/java-agents/agent-client', () => ({
  callClassificationAgent: vi.fn(),
  callEmbeddingAgent: vi.fn(),
  callReflectionAgent: vi.fn(),
  callTranscriptionAgent: vi.fn(),
  callNLPAgent: vi.fn(),
}));

describe('AI Services', () => {
  const mockUserId = 'user-123';
  const mockContent = 'This is a test moment about work and productivity';
  const mockSpheres = [
    { id: 'sphere-1', name: 'Work', description: 'Work related', type: 'WORK' },
    { id: 'sphere-2', name: 'Personal', description: 'Personal thoughts', type: 'PERSONAL' },
    { id: 'sphere-3', name: 'Health', description: 'Health and fitness', type: 'HEALTH' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('ClassificationService', () => {
    let service: ClassificationService;

    beforeAll(() => {
      service = new ClassificationService();
    });

    describe('classifyMoment', () => {
      it('should classify moment using Java agent when available', async () => {
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockResolvedValue({
          sphereId: 'sphere-1',
          sphereName: 'Work',
          confidence: 0.85,
          reasoning: 'Keywords suggest work context',
          alternatives: [],
          processingTimeMs: 150,
          model: 'gpt-4',
        });

        const result = await service.classifyMoment({
          content: mockContent,
          contentType: 'text',
          emotions: ['productive'],
          tags: ['work', 'meeting'],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        expect(result.sphereId).toBe('sphere-1');
        expect(result.sphereName).toBe('Work');
        expect(result.confidence).toBe(0.85);
        expect(result.reasoning).toBe('Keywords suggest work context');
        expect(result.source).toBe('java-agent');
      });

      it('should fallback to heuristics when Java agent unavailable', async () => {
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockRejectedValue(
          new Error('Java agent service unavailable')
        );

        const result = await service.classifyMoment({
          content: 'Had a great meeting with the team today',
          contentType: 'text',
          emotions: ['happy'],
          tags: ['meeting', 'team'],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        expect(result.sphereId).toBe('sphere-1'); // Work sphere
        expect(result.sphereName).toBe('Work');
        expect(result.confidence).toBeGreaterThan(0.7);
        expect(result.source).toBe('heuristic');
      });

      it('should handle empty content gracefully', async () => {
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockRejectedValue(
          new Error('Java agent service unavailable')
        );

        const result = await service.classifyMoment({
          content: '',
          contentType: 'text',
          emotions: [],
          tags: [],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        expect(result.sphereId).toBeDefined();
        expect(result.sphereName).toBeDefined();
        expect(result.confidence).toBeGreaterThan(0);
      });

      it('should handle voice transcription content', async () => {
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockResolvedValue({
          sphereId: 'sphere-3',
          sphereName: 'Health',
          confidence: 0.92,
          reasoning: 'Transcript mentions fitness and health',
          alternatives: [],
          processingTimeMs: 200,
          model: 'gpt-4',
        });

        const result = await service.classifyMoment({
          content: 'Went for a 5km run this morning',
          contentType: 'voice',
          transcript: 'Went for a 5km run this morning',
          emotions: ['energetic'],
          tags: ['fitness', 'exercise'],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        expect(result.sphereId).toBe('sphere-3');
        expect(result.sphereName).toBe('Health');
        expect(result.confidence).toBe(0.92);
      });

      it('should respect confidence threshold', async () => {
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockResolvedValue({
          sphereId: 'sphere-1',
          sphereName: 'Work',
          confidence: 0.3, // Low confidence
          reasoning: 'Unclear context',
          alternatives: [
            { sphereId: 'sphere-2', sphereName: 'Personal', confidence: 0.25 },
            { sphereId: 'sphere-3', sphereName: 'Health', confidence: 0.2 },
          ],
          processingTimeMs: 100,
          model: 'gpt-4',
        });

        const result = await service.classifyMoment({
          content: 'Random content with no clear context',
          contentType: 'text',
          emotions: [],
          tags: [],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        expect(result.sphereId).toBe('sphere-1'); // Still returns top result
        expect(result.confidence).toBe(0.3);
        expect(result.alternatives).toHaveLength(3);
      });
    });

    describe('Circuit Breaker Behavior', () => {
      it('should use fallback when circuit breaker is open', async () => {
        // Get circuit breaker and force it open
        const circuitBreaker = getCircuitBreaker('classification-agent');
        
        // Simulate multiple failures to open circuit
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockRejectedValue(
          new Error('Service unavailable')
        );

        // Trigger multiple failures to open circuit
        for (let i = 0; i < 6; i++) {
          try {
            await service.classifyMoment({
              content: 'Test content',
              contentType: 'text',
              emotions: [],
              tags: [],
              userId: mockUserId,
              availableSpheres: mockSpheres,
            });
          } catch (error) {
            // Expected failures
          }
        }

        // Circuit should now be open, use fallback
        const result = await service.classifyMoment({
          content: 'Test content for fallback',
          contentType: 'text',
          emotions: [],
          tags: [],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        expect(result.source).toBe('heuristic');
        expect(result.sphereId).toBeDefined();
      });

      it('should recover when circuit breaker closes', async () => {
        const circuitBreaker = getCircuitBreaker('classification-agent');
        
        // Reset circuit breaker
        circuitBreaker.reset();

        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockResolvedValue({
          sphereId: 'sphere-1',
          sphereName: 'Work',
          confidence: 0.9,
          reasoning: 'Clear work context',
          alternatives: [],
          processingTimeMs: 100,
          model: 'gpt-4',
        });

        const result = await service.classifyMoment({
          content: 'Work-related content',
          contentType: 'text',
          emotions: ['productive'],
          tags: ['work'],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        expect(result.source).toBe('java-agent');
        expect(result.sphereId).toBe('sphere-1');
      });
    });

    describe('Performance Monitoring', () => {
      it('should track processing time', async () => {
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockResolvedValue({
          sphereId: 'sphere-1',
          sphereName: 'Work',
          confidence: 0.85,
          reasoning: 'Keywords suggest work context',
          alternatives: [],
          processingTimeMs: 150,
          model: 'gpt-4',
        });

        const startTime = Date.now();
        const result = await service.classifyMoment({
          content: mockContent,
          contentType: 'text',
          emotions: ['productive'],
          tags: ['work'],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });
        const endTime = Date.now();

        expect(result.processingTimeMs).toBeDefined();
        expect(result.processingTimeMs).toBeGreaterThan(0);
        expect(endTime - startTime).toBeLessThan(1000); // Should be fast
      });

      it('should log classification results', async () => {
        const consoleSpy = vi.spyOn(console, 'log');
        
        const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
        vi.mocked(callClassificationAgent).mockResolvedValue({
          sphereId: 'sphere-1',
          sphereName: 'Work',
          confidence: 0.85,
          reasoning: 'Keywords suggest work context',
          alternatives: [],
          processingTimeMs: 150,
          model: 'gpt-4',
        });

        await service.classifyMoment({
          content: mockContent,
          contentType: 'text',
          emotions: ['productive'],
          tags: ['work'],
          userId: mockUserId,
          availableSpheres: mockSpheres,
        });

        // Should log classification result
        expect(consoleSpy).toHaveBeenCalledWith(
          expect.stringContaining('Classification completed'),
          expect.any(Object)
        );

        consoleSpy.mockRestore();
      });
    });
  });

  describe('Error Handling', () => {
    let service: ClassificationService;

    beforeAll(() => {
      service = new ClassificationService();
    });

    it('should handle network timeouts', async () => {
      const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
      vi.mocked(callClassificationAgent).mockRejectedValue(
        new Error('Request timeout')
      );

      const result = await service.classifyMoment({
        content: mockContent,
        contentType: 'text',
        emotions: [],
        tags: [],
        userId: mockUserId,
        availableSpheres: mockSpheres,
      });

      expect(result.source).toBe('heuristic');
      expect(result.sphereId).toBeDefined();
    });

    it('should handle malformed responses', async () => {
      const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
      vi.mocked(callClassificationAgent).mockResolvedValue({
        // Missing required fields
        sphereId: 'sphere-1',
        // Missing confidence, reasoning, etc.
      });

      const result = await service.classifyMoment({
        content: mockContent,
        contentType: 'text',
        emotions: [],
        tags: [],
        userId: mockUserId,
        availableSpheres: mockSpheres,
      });

      // Should handle gracefully with defaults
      expect(result.sphereId).toBe('sphere-1');
      expect(result.confidence).toBeGreaterThan(0);
    });

    it('should handle empty spheres list', async () => {
      const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
      vi.mocked(callClassificationAgent).mockRejectedValue(
        new Error('Java agent service unavailable')
      );

      const result = await service.classifyMoment({
        content: mockContent,
        contentType: 'text',
        emotions: [],
        tags: [],
        userId: mockUserId,
        availableSpheres: [], // Empty spheres
      });

      // Should handle gracefully
      expect(result.sphereId).toBeDefined();
      expect(result.confidence).toBeGreaterThan(0);
    });
  });

  describe('Integration Tests', () => {
    it('should work with real-time classification workflow', async () => {
      const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
      
      // Mock successful classification
      vi.mocked(callClassificationAgent).mockResolvedValue({
        sphereId: 'sphere-1',
        sphereName: 'Work',
        confidence: 0.9,
        reasoning: 'Strong work context detected',
        alternatives: [],
        processingTimeMs: 120,
        model: 'gpt-4',
      });

      // Simulate real workflow
      const moments = [
        {
          content: 'Team meeting about Q4 goals',
          contentType: 'text',
          emotions: ['focused'],
          tags: ['meeting', 'goals'],
        },
        {
          content: 'Lunch with colleagues',
          contentType: 'text',
          emotions: ['social'],
          tags: ['lunch', 'colleagues'],
        },
        {
          content: 'Code review session',
          contentType: 'text',
          emotions: ['analytical'],
          tags: ['code', 'review'],
        },
      ];

      const results = await Promise.all(
        moments.map(moment =>
          service.classifyMoment({
            ...moment,
            userId: mockUserId,
            availableSpheres: mockSpheres,
          })
        )
      );

      expect(results).toHaveLength(3);
      
      // All should be classified as work-related
      results.forEach(result => {
        expect(result.sphereId).toBe('sphere-1');
        expect(result.sphereName).toBe('Work');
        expect(result.confidence).toBeGreaterThan(0.7);
      });
    });

    it('should handle mixed content types', async () => {
      const { callClassificationAgent } = await import('../../services/java-agents/agent-client');
      
      vi.mocked(callClassificationAgent).mockImplementation((request) => {
        if (request.contentType === 'image') {
          return Promise.resolve({
            sphereId: 'sphere-2',
            sphereName: 'Personal',
            confidence: 0.8,
            reasoning: 'Personal photo detected',
            alternatives: [],
            processingTimeMs: 200,
            model: 'gpt-4-vision',
          });
        } else {
          return Promise.resolve({
            sphereId: 'sphere-1',
            sphereName: 'Work',
            confidence: 0.85,
            reasoning: 'Work-related content',
            alternatives: [],
            processingTimeMs: 150,
            model: 'gpt-4',
          });
        }
      });

      const textResult = await service.classifyMoment({
        content: 'Meeting notes',
        contentType: 'text',
        emotions: [],
        tags: [],
        userId: mockUserId,
        availableSpheres: mockSpheres,
      });

      const imageResult = await service.classifyMoment({
        content: 'Family photo',
        contentType: 'image',
        emotions: [],
        tags: [],
        userId: mockUserId,
        availableSpheres: mockSpheres,
      });

      expect(textResult.sphereName).toBe('Work');
      expect(imageResult.sphereName).toBe('Personal');
    });
  });
});
