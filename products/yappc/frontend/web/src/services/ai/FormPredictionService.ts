/**
 * Form Prediction Service
 *
 * Provides AI-powered form field predictions based on user history and context.
 * Generates intelligent field suggestions with confidence scores.
 *
 * @doc.type service
 * @doc.purpose AI-powered form field prediction
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { z } from 'zod';

// ============================================================================
// Types
// ============================================================================

export interface FormFieldPrediction {
  fieldName: string;
  predictedValue: unknown;
  confidence: number; // 0-1
  reasoning: string;
  source: 'history' | 'context' | 'pattern' | 'ai';
}

export interface FormPredictionContext {
  formType: string;
  userId?: string;
  projectId?: string;
  previousSubmissions?: Record<string, unknown>[];
  currentValues?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

export interface FormPredictionRequest {
  schema: z.ZodSchema<Record<string, unknown>>;
  context: FormPredictionContext;
  fieldsToPredict?: string[];
}

export interface FormPredictionResponse {
  predictions: FormFieldPrediction[];
  metadata: {
    timestamp: string;
    predictionId: string;
    modelVersion: string;
  };
}

// ============================================================================
// Pattern-Based Predictions
// ============================================================================

const COMMON_PATTERNS: Record<string, (context: FormPredictionContext) => FormFieldPrediction[]> = {
  'project-creation': (context) => {
    const predictions: FormFieldPrediction[] = [];
    const { previousSubmissions, currentValues } = context;

    // Predict project name based on previous submissions
    if (previousSubmissions && previousSubmissions.length > 0) {
      const lastProject = previousSubmissions[0];
      predictions.push({
        fieldName: 'projectName',
        predictedValue: lastProject.projectName || '',
        confidence: 0.6,
        reasoning: 'Based on your previous project naming pattern',
        source: 'history',
      });
    }

    // Predict description based on current values
    if (currentValues?.projectName) {
      predictions.push({
        fieldName: 'description',
        predictedValue: `Project for ${currentValues.projectName}`,
        confidence: 0.4,
        reasoning: 'Generated from project name',
        source: 'context',
      });
    }

    return predictions;
  },

  'task-creation': (context) => {
    const predictions: FormFieldPrediction[] = [];
    const { previousSubmissions, currentValues } = context;

    // Predict task type based on previous submissions
    if (previousSubmissions && previousSubmissions.length > 0) {
      const taskTypes = previousSubmissions.map(s => s.taskType as string);
      const mostCommon = taskTypes.sort((a, b) =>
        taskTypes.filter(v => v === a).length - taskTypes.filter(v => v === b).length
      )[0];

      predictions.push({
        fieldName: 'taskType',
        predictedValue: mostCommon,
        confidence: 0.7,
        reasoning: 'Most commonly used task type in your history',
        source: 'history',
      });
    }

    // Predict priority based on context
    if (context.metadata?.urgency === 'high') {
      predictions.push({
        fieldName: 'priority',
        predictedValue: 'urgent',
        confidence: 0.8,
        reasoning: 'Context indicates high urgency',
        source: 'context',
      });
    }

    return predictions;
  },
};

// ============================================================================
// Service Implementation
// ============================================================================

/**
 * Generate predictions for form fields
 */
export async function generateFormPredictions(
  request: FormPredictionRequest
): Promise<FormPredictionResponse> {
  const { schema, context, fieldsToPredict } = request;

  // Get pattern-based predictions
  const patternPredictions = COMMON_PATTERNS[context.formType]?.(context) || [];

  // Filter to requested fields if specified
  const filteredPredictions = fieldsToPredict
    ? patternPredictions.filter(p => fieldsToPredict.includes(p.fieldName))
    : patternPredictions;

  // Validate predictions against schema
  const validatedPredictions = filteredPredictions.filter(prediction => {
    try {
      if (schema instanceof z.ZodObject) {
        const fieldSchema = schema.shape[prediction.fieldName];
        return fieldSchema ? fieldSchema.safeParse(prediction.predictedValue).success : true;
      }
      return true;
    } catch {
      return false;
    }
  });

  return {
    predictions: validatedPredictions,
    metadata: {
      timestamp: new Date().toISOString(),
      predictionId: crypto.randomUUID(),
      modelVersion: '1.0.0',
    },
  };
}

/**
 * Get confidence score for a specific field
 */
export function getFieldConfidence(
  fieldName: string,
  predictions: FormFieldPrediction[]
): number {
  const prediction = predictions.find(p => p.fieldName === fieldName);
  return prediction?.confidence || 0;
}

/**
 * Apply predictions to form values
 */
export function applyPredictions(
  currentValues: Record<string, unknown>,
  predictions: FormFieldPrediction[],
  threshold: number = 0.5
): Record<string, unknown> {
  const result = { ...currentValues };

  predictions
    .filter(p => p.confidence >= threshold)
    .forEach(prediction => {
      // Only apply if field is empty or undefined
      if (!result[prediction.fieldName]) {
        result[prediction.fieldName] = prediction.predictedValue;
      }
    });

  return result;
}

/**
 * Get prediction reasoning for a field
 */
export function getPredictionReasoning(
  fieldName: string,
  predictions: FormFieldPrediction[]
): string | undefined {
  const prediction = predictions.find(p => p.fieldName === fieldName);
  return prediction?.reasoning;
}
