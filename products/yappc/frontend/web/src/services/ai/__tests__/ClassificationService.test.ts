/**
 * ClassificationService Tests
 */

import { describe, it, expect } from 'vitest';
import {
  classifyContent,
  extractTagsFromClassification,
  type ClassificationRequest,
  type ClassificationResult,
  type TagSuggestion,
} from '../ClassificationService';

describe('ClassificationService', () => {
  describe('classifyContent', () => {
    it('should classify content by keywords', async () => {
      const request: ClassificationRequest = {
        content: 'Implement user authentication with OAuth2 and JWT tokens',
        contentType: 'task',
      };
      const response = await classifyContent(request);

      expect(response).toBeDefined();
      expect(response.result).toBeDefined();
      expect(response.result.category).toBe('authentication');
      expect(response.result.confidence).toBeGreaterThan(0);
      expect(response.result.confidence).toBeLessThanOrEqual(1);
    });

    it('should classify development tasks', async () => {
      const request: ClassificationRequest = {
        content: 'Fix bug in login form validation',
        contentType: 'task',
      };
      const response = await classifyContent(request);

      expect(response.result.category).toBe('authentication');
    });

    it('should classify design tasks', async () => {
      const request: ClassificationRequest = {
        content: 'Create wireframes for dashboard layout',
        contentType: 'task',
      };
      const response = await classifyContent(request);

      expect(response.result.category).toBe('ui');
    });

    it('should classify documentation tasks', async () => {
      const request: ClassificationRequest = {
        content: 'Write API documentation for endpoints',
        contentType: 'task',
      };
      const response = await classifyContent(request);

      expect(response.result.category).toBe('api');
    });

    it('should return low confidence for ambiguous content', async () => {
      const request: ClassificationRequest = {
        content: 'Do some work on the project',
        contentType: 'task',
      };
      const response = await classifyContent(request);

      expect(response.result.confidence).toBeLessThan(0.5);
    });

    it('should suggest technology tags', async () => {
      const request: ClassificationRequest = {
        content: 'Implement React components with TypeScript',
        contentType: 'task',
      };
      const response = await classifyContent(request);

      expect(response.result.suggestedTags).toBeInstanceOf(Array);
      expect(response.result.suggestedTags.length).toBeGreaterThan(0);
      expect(response.result.suggestedTags[0]).toHaveProperty('tag');
      expect(response.result.suggestedTags[0]).toHaveProperty('confidence');
    });

    it('should include existing tags in consideration', async () => {
      const request: ClassificationRequest = {
        content: 'Add authentication and authorization to API',
        contentType: 'task',
        existingTags: ['authentication'],
      };
      const response = await classifyContent(request);

      const authTags = response.result.suggestedTags.filter((s: TagSuggestion) => s.tag === 'authentication');
      expect(authTags.length).toBe(0);
    });
  });

  describe('extractTagsFromClassification', () => {
    it('should extract tags above threshold', () => {
      const classification: ClassificationResult = {
        category: 'authentication',
        confidence: 0.8,
        suggestedTags: [
          { tag: 'react', confidence: 0.9, category: 'technology', reason: 'test' },
          { tag: 'typescript', confidence: 0.6, category: 'technology', reason: 'test' },
          { tag: 'low', confidence: 0.3, category: 'priority', reason: 'test' },
        ],
        reasoning: 'test',
      };

      const tags = extractTagsFromClassification(classification, 0.5);

      expect(tags).toEqual(['react', 'typescript']);
    });

    it('should use default threshold of 0.5', () => {
      const classification: ClassificationResult = {
        category: 'authentication',
        confidence: 0.8,
        suggestedTags: [
          { tag: 'react', confidence: 0.9, category: 'technology', reason: 'test' },
          { tag: 'low', confidence: 0.4, category: 'priority', reason: 'test' },
        ],
        reasoning: 'test',
      };

      const tags = extractTagsFromClassification(classification);

      expect(tags).toEqual(['react']);
    });

    it('should return empty array if no tags meet threshold', () => {
      const classification: ClassificationResult = {
        category: 'authentication',
        confidence: 0.8,
        suggestedTags: [
          { tag: 'low', confidence: 0.3, category: 'priority', reason: 'test' },
        ],
        reasoning: 'test',
      };

      const tags = extractTagsFromClassification(classification, 0.5);

      expect(tags).toEqual([]);
    });
  });
});
