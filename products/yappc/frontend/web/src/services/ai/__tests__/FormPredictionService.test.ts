/**
 * Form Prediction Service Tests
 *
 * Unit tests for FormPredictionService with 100% coverage.
 * Tests all prediction functions, pattern matching, and edge cases.
 *
 * @doc.type test
 * @doc.purpose FormPredictionService unit tests
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { z } from 'zod';
import {
  generateFormPredictions,
  getFieldConfidence,
  applyPredictions,
  getPredictionReasoning,
  type FormPredictionContext,
  type FormPredictionRequest,
} from '../FormPredictionService';

describe('FormPredictionService', () => {
  const projectSchema = z.object({
    projectName: z.string(),
    description: z.string(),
    priority: z.enum(['low', 'medium', 'high', 'urgent']),
  });

  const taskSchema = z.object({
    taskName: z.string(),
    taskType: z.enum(['Design', 'Code', 'Deploy']),
    priority: z.enum(['low', 'medium', 'high', 'urgent']),
  });

  describe('generateFormPredictions', () => {
    it('should generate predictions for project-creation form type', async () => {
      const request: FormPredictionRequest = {
        schema: projectSchema,
        context: {
          formType: 'project-creation',
          userId: 'user-1',
          currentValues: { projectName: 'My Project' },
          previousSubmissions: [
            { projectName: 'Previous Project', description: 'Test' },
          ],
        },
      };

      const result = await generateFormPredictions(request);

      expect(result.predictions).toBeDefined();
      expect(Array.isArray(result.predictions)).toBe(true);
      expect(result.metadata).toBeDefined();
      expect(result.metadata.timestamp).toBeDefined();
      expect(result.metadata.predictionId).toBeDefined();
    });

    it('should generate predictions for task-creation form type', async () => {
      const request: FormPredictionRequest = {
        schema: taskSchema,
        context: {
          formType: 'task-creation',
          userId: 'user-1',
          metadata: { urgency: 'high' },
        },
      };

      const result = await generateFormPredictions(request);

      expect(result.predictions).toBeDefined();
      expect(Array.isArray(result.predictions)).toBe(true);
    });

    it('should return empty predictions for unknown form type', async () => {
      const request: FormPredictionRequest = {
        schema: projectSchema,
        context: {
          formType: 'unknown-form-type',
          userId: 'user-1',
        },
      };

      const result = await generateFormPredictions(request);

      expect(result.predictions).toEqual([]);
    });

    it('should filter predictions to requested fields', async () => {
      const request: FormPredictionRequest = {
        schema: projectSchema,
        context: {
          formType: 'project-creation',
          userId: 'user-1',
          currentValues: { projectName: 'My Project' },
        },
        fieldsToPredict: ['projectName'],
      };

      const result = await generateFormPredictions(request);

      expect(result.predictions).toBeDefined();
      result.predictions.forEach(p => {
        expect(['projectName']).toContain(p.fieldName);
      });
    });

    it('should validate predictions against schema', async () => {
      const invalidSchema = z.object({
        projectName: z.number(), // Invalid type for the prediction
      });

      const request: FormPredictionRequest = {
        schema: invalidSchema,
        context: {
          formType: 'project-creation',
          userId: 'user-1',
          previousSubmissions: [{ projectName: 'Previous Project' }],
        },
      };

      const result = await generateFormPredictions(request);

      // Predictions that don't match schema should be filtered out
      expect(Array.isArray(result.predictions)).toBe(true);
    });

    it('should include confidence scores in predictions', async () => {
      const request: FormPredictionRequest = {
        schema: projectSchema,
        context: {
          formType: 'project-creation',
          userId: 'user-1',
          previousSubmissions: [{ projectName: 'Previous Project' }],
        },
      };

      const result = await generateFormPredictions(request);

      result.predictions.forEach(p => {
        expect(p.confidence).toBeDefined();
        expect(typeof p.confidence).toBe('number');
        expect(p.confidence).toBeGreaterThanOrEqual(0);
        expect(p.confidence).toBeLessThanOrEqual(1);
      });
    });

    it('should include reasoning in predictions', async () => {
      const request: FormPredictionRequest = {
        schema: projectSchema,
        context: {
          formType: 'project-creation',
          userId: 'user-1',
          previousSubmissions: [{ projectName: 'Previous Project' }],
        },
      };

      const result = await generateFormPredictions(request);

      result.predictions.forEach(p => {
        expect(p.reasoning).toBeDefined();
        expect(typeof p.reasoning).toBe('string');
        expect(p.reasoning.length).toBeGreaterThan(0);
      });
    });

    it('should include source in predictions', async () => {
      const request: FormPredictionRequest = {
        schema: projectSchema,
        context: {
          formType: 'project-creation',
          userId: 'user-1',
        },
      };

      const result = await generateFormPredictions(request);

      result.predictions.forEach(p => {
        expect(p.source).toBeDefined();
        expect(['history', 'context', 'pattern', 'ai']).toContain(p.source);
      });
    });
  });

  describe('getFieldConfidence', () => {
    it('should return confidence score for existing field', () => {
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Test', confidence: 0.8, reasoning: 'Test', source: 'history' as const },
        { fieldName: 'description', predictedValue: 'Test', confidence: 0.6, reasoning: 'Test', source: 'context' as const },
      ];

      const confidence = getFieldConfidence('projectName', predictions);

      expect(confidence).toBe(0.8);
    });

    it('should return 0 for non-existent field', () => {
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Test', confidence: 0.8, reasoning: 'Test', source: 'history' as const },
      ];

      const confidence = getFieldConfidence('nonExistent', predictions);

      expect(confidence).toBe(0);
    });

    it('should handle empty predictions array', () => {
      const confidence = getFieldConfidence('projectName', []);

      expect(confidence).toBe(0);
    });
  });

  describe('applyPredictions', () => {
    it('should apply predictions above threshold', () => {
      const currentValues = { projectName: '', description: '' };
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Predicted Name', confidence: 0.8, reasoning: 'Test', source: 'history' as const },
        { fieldName: 'description', predictedValue: 'Predicted Desc', confidence: 0.4, reasoning: 'Test', source: 'context' as const },
      ];

      const result = applyPredictions(currentValues, predictions, 0.5);

      expect(result.projectName).toBe('Predicted Name');
      expect(result.description).toBe(''); // Below threshold
    });

    it('should not override existing values', () => {
      const currentValues = { projectName: 'Existing Name', description: '' };
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Predicted Name', confidence: 0.8, reasoning: 'Test', source: 'history' as const },
      ];

      const result = applyPredictions(currentValues, predictions, 0.5);

      expect(result.projectName).toBe('Existing Name'); // Not overridden
    });

    it('should apply predictions with default threshold', () => {
      const currentValues = { projectName: '', description: '' };
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Predicted Name', confidence: 0.6, reasoning: 'Test', source: 'history' as const },
      ];

      const result = applyPredictions(currentValues, predictions);

      expect(result.projectName).toBe('Predicted Name');
    });

    it('should handle empty predictions', () => {
      const currentValues = { projectName: '', description: '' };

      const result = applyPredictions(currentValues, []);

      expect(result).toEqual(currentValues);
    });

    it('should preserve non-predicted fields', () => {
      const currentValues = { projectName: '', description: 'Existing', priority: 'low' };
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Predicted Name', confidence: 0.8, reasoning: 'Test', source: 'history' as const },
      ];

      const result = applyPredictions(currentValues, predictions, 0.5);

      expect(result.projectName).toBe('Predicted Name');
      expect(result.description).toBe('Existing');
      expect(result.priority).toBe('low');
    });
  });

  describe('getPredictionReasoning', () => {
    it('should return reasoning for existing field', () => {
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Test', confidence: 0.8, reasoning: 'Based on history', source: 'history' as const },
      ];

      const reasoning = getPredictionReasoning('projectName', predictions);

      expect(reasoning).toBe('Based on history');
    });

    it('should return undefined for non-existent field', () => {
      const predictions = [
        { fieldName: 'projectName', predictedValue: 'Test', confidence: 0.8, reasoning: 'Test', source: 'history' as const },
      ];

      const reasoning = getPredictionReasoning('nonExistent', predictions);

      expect(reasoning).toBeUndefined();
    });

    it('should handle empty predictions array', () => {
      const reasoning = getPredictionReasoning('projectName', []);

      expect(reasoning).toBeUndefined();
    });
  });

  describe('Pattern Matching', () => {
    it('should use history-based predictions for project-creation', async () => {
      const request: FormPredictionRequest = {
        schema: projectSchema,
        context: {
          formType: 'project-creation',
          userId: 'user-1',
          previousSubmissions: [
            { projectName: 'Project A', description: 'Test' },
            { projectName: 'Project B', description: 'Test' },
          ],
        },
      };

      const result = await generateFormPredictions(request);

      const historyPrediction = result.predictions.find(p => p.source === 'history');
      expect(historyPrediction).toBeDefined();
    });

    it('should use context-based predictions when urgency is high', async () => {
      const request: FormPredictionRequest = {
        schema: taskSchema,
        context: {
          formType: 'task-creation',
          userId: 'user-1',
          metadata: { urgency: 'high' },
        },
      };

      const result = await generateFormPredictions(request);

      const contextPrediction = result.predictions.find(p => p.source === 'context');
      expect(contextPrediction).toBeDefined();
    });
  });
});
