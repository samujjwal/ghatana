/**
 * AI Grading Service Tests
 *
 * @doc.type test
 * @doc.purpose Test AI grading service for open-ended responses
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AIGradingService } from '../AIGradingService';

describe('AIGradingService', () => {
  let service: AIGradingService;

  beforeEach(() => {
    service = new AIGradingService();
  });

  describe('gradeOpenEndedResponse', () => {
    it('should grade a response and return result with confidence', async () => {
      const request = {
        tenantId: 'tenant-1',
        assessmentId: 'assessment-1',
        itemId: 'item-1',
        questionPrompt: 'Explain Newton\'s First Law of Motion',
        studentResponse: 'Newton\'s First Law states that an object at rest stays at rest and an object in motion stays in motion unless acted upon by an external force.',
        rubric: {
          criteria: [
            { name: 'Accuracy', description: 'Correctly states the law', maxPoints: 5 },
            { name: 'Explanation', description: 'Provides clear explanation', maxPoints: 5 },
          ],
        },
      };

      // Mock the AI client response
      vi.mock('../../../clients/ai-client', () => ({
        aiClient: {
          gradeResponse: vi.fn().mockResolvedValue({
            scorePercent: 90,
            earnedPoints: 9,
            maxPoints: 10,
            confidence: 0.85,
            strengths: ['Correctly states the law', 'Clear explanation'],
            improvements: ['Could add examples'],
            comments: 'Excellent response demonstrating understanding.',
            model: 'tutorputor-grading-v1',
          }),
        },
      }));

      const result = await service.gradeOpenEndedResponse(request);

      expect(result.itemId).toBe('item-1');
      expect(result.scorePercent).toBe(90);
      expect(result.confidence).toBe(0.85);
      expect(result.needsReview).toBe(false);
      expect(result.feedback.strengths).toContain('Correctly states the law');
    });

    it('should flag for review when confidence is below threshold', async () => {
      const request = {
        tenantId: 'tenant-1',
        assessmentId: 'assessment-1',
        itemId: 'item-1',
        questionPrompt: 'Explain Newton\'s First Law',
        studentResponse: 'I don\' know.',
      };

      vi.mock('../../../clients/ai-client', () => ({
        aiClient: {
          gradeResponse: vi.fn().mockResolvedValue({
            scorePercent: 0,
            earnedPoints: 0,
            maxPoints: 10,
            confidence: 0.5,
            strengths: [],
            improvements: ['Provide more detail'],
            comments: 'Response is too brief.',
            model: 'tutorputor-grading-v1',
          }),
        },
      }));

      const result = await service.gradeOpenEndedResponse(request);

      expect(result.needsReview).toBe(true);
      expect(result.confidence).toBe(0.5);
    });

    it('should fallback to teacher review on AI error', async () => {
      const request = {
        tenantId: 'tenant-1',
        assessmentId: 'assessment-1',
        itemId: 'item-1',
        questionPrompt: 'Explain Newton\'s First Law',
        studentResponse: 'Test response',
      };

      vi.mock('../../../clients/ai-client', () => ({
        aiClient: {
          gradeResponse: vi.fn().mockRejectedValue(new Error('AI service unavailable')),
        },
      }));

      const result = await service.gradeOpenEndedResponse(request);

      expect(result.needsReview).toBe(true);
      expect(result.confidence).toBe(0);
      expect(result.feedback.comments).toContain('AI grading unavailable');
    });
  });

  describe('gradeBatch', () => {
    it('should grade multiple responses in parallel', async () => {
      const requests = [
        {
          tenantId: 'tenant-1',
          assessmentId: 'assessment-1',
          itemId: 'item-1',
          questionPrompt: 'Question 1',
          studentResponse: 'Response 1',
        },
        {
          tenantId: 'tenant-1',
          assessmentId: 'assessment-1',
          itemId: 'item-2',
          questionPrompt: 'Question 2',
          studentResponse: 'Response 2',
        },
      ];

      vi.mock('../../../clients/ai-client', () => ({
        aiClient: {
          gradeResponse: vi.fn()
            .mockResolvedValueOnce({
              scorePercent: 85,
              earnedPoints: 8.5,
              maxPoints: 10,
              confidence: 0.9,
              strengths: ['Good'],
              improvements: [],
              comments: 'Good response',
              model: 'tutorputor-grading-v1',
            })
            .mockResolvedValueOnce({
              scorePercent: 75,
              earnedPoints: 7.5,
              maxPoints: 10,
              confidence: 0.8,
              strengths: ['Adequate'],
              improvements: ['Add detail'],
              comments: 'Adequate response',
              model: 'tutorputor-grading-v1',
            }),
        },
      }));

      const results = await service.gradeBatch(requests);

      expect(results).toHaveLength(2);
      expect(results[0].scorePercent).toBe(85);
      expect(results[1].scorePercent).toBe(75);
    });
  });

  describe('getGradingStats', () => {
    it('should calculate statistics from results', () => {
      const results = [
        {
          itemId: 'item-1',
          scorePercent: 90,
          earnedPoints: 9,
          maxPoints: 10,
          confidence: 0.85,
          needsReview: false,
          feedback: { strengths: [], improvements: [], comments: '' },
          metadata: { modelUsed: 'v1', processingTimeMs: 100, timestamp: '' },
        },
        {
          itemId: 'item-2',
          scorePercent: 70,
          earnedPoints: 7,
          maxPoints: 10,
          confidence: 0.65,
          needsReview: true,
          feedback: { strengths: [], improvements: [], comments: '' },
          metadata: { modelUsed: 'v1', processingTimeMs: 150, timestamp: '' },
        },
      ];

      const stats = service.getGradingStats(results);

      expect(stats.total).toBe(2);
      expect(stats.avgScorePercent).toBe(80);
      expect(stats.avgConfidence).toBe(0.75);
      expect(stats.needsReviewCount).toBe(1);
      expect(stats.avgProcessingTimeMs).toBe(125);
    });

    it('should return zeros for empty results', () => {
      const stats = service.getGradingStats([]);

      expect(stats.total).toBe(0);
      expect(stats.avgScorePercent).toBe(0);
      expect(stats.avgConfidence).toBe(0);
      expect(stats.needsReviewCount).toBe(0);
      expect(stats.avgProcessingTimeMs).toBe(0);
    });
  });
});
