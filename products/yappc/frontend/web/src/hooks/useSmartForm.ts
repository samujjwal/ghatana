/**
 * Smart Form Hook
 *
 * React hook for AI-powered form prefill and intelligent field suggestions.
 * Provides optimistic updates and confidence-based prediction application.
 *
 * @doc.type hook
 * @doc.purpose AI-enhanced form management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { z } from 'zod';
import {
  generateFormPredictions,
  applyPredictions,
  getFieldConfidence,
  getPredictionReasoning,
  type FormPredictionContext,
  type FormFieldPrediction,
  type FormPredictionRequest,
} from '../services/ai/FormPredictionService';

// ============================================================================
// Types
// ============================================================================

export interface UseSmartFormOptions<T> {
  schema: z.ZodSchema<T>;
  initialValues?: Partial<T>;
  formType: string;
  context?: Partial<FormPredictionContext>;
  enableAIPrefill?: boolean;
  confidenceThreshold?: number;
  onPredictionsChange?: (predictions: FormFieldPrediction[]) => void;
}

export interface UseSmartFormResult<T> {
  // Form state
  values: T;
  setValues: (values: T | ((prev: T) => T)) => void;
  updateField: <K extends keyof T>(field: K, value: T[K]) => void;

  // Predictions
  predictions: FormFieldPrediction[];
  isLoadingPredictions: boolean;
  isApplyingPredictions: boolean;

  // Actions
  applyPredictionsToForm: () => void;
  refreshPredictions: () => void;

  // Utilities
  getFieldConfidence: (field: keyof T) => number;
  getPredictionReasoning: (field: keyof T) => string | undefined;
  hasPrediction: (field: keyof T) => boolean;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useSmartForm<T extends Record<string, unknown>>(
  options: UseSmartFormOptions<T>
): UseSmartFormResult<T> {
  const {
    schema,
    initialValues = {} as Partial<T>,
    formType,
    context = {},
    enableAIPrefill = true,
    confidenceThreshold = 0.5,
    onPredictionsChange,
  } = options;

  const queryClient = useQueryClient();
  const [values, setValues] = useState<T>(initialValues as T);
  const [isApplyingPredictions, setIsApplyingPredictions] = useState(false);

  // Query for predictions
  const {
    data: predictionResponse,
    isLoading: isLoadingPredictions,
    refetch: refreshPredictions,
  } = useQuery({
    queryKey: ['form-predictions', formType, context],
    queryFn: () =>
      generateFormPredictions({
        schema,
        context: {
          formType,
          ...context,
        } as FormPredictionContext,
      }),
    enabled: enableAIPrefill,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const predictions = predictionResponse?.predictions || [];

  // Notify parent of prediction changes
  useEffect(() => {
    if (onPredictionsChange && predictions.length > 0) {
      onPredictionsChange(predictions);
    }
  }, [predictions, onPredictionsChange]);

  // Apply predictions to form values
  const applyPredictionsToForm = useCallback(() => {
    setIsApplyingPredictions(true);
    try {
      const updatedValues = applyPredictions(
        values as Record<string, unknown>,
        predictions,
        confidenceThreshold
      );
      setValues(updatedValues as T);
    } finally {
      setIsApplyingPredictions(false);
    }
  }, [values, predictions, confidenceThreshold]);

  // Update a single field
  const updateField = useCallback(<K extends keyof T>(field: K, value: T[K]) => {
    setValues((prev) => ({ ...prev, [field]: value }));
  }, []);

  // Get confidence for a specific field
  const getFieldConfidenceForField = useCallback(
    (field: keyof T): number => {
      return getFieldConfidence(field as string, predictions);
    },
    [predictions]
  );

  // Get reasoning for a specific field
  const getPredictionReasoningForField = useCallback(
    (field: keyof T): string | undefined => {
      return getPredictionReasoning(field as string, predictions);
    },
    [predictions]
  );

  // Check if field has a prediction
  const hasPrediction = useCallback(
    (field: keyof T): boolean => {
      return predictions.some(p => p.fieldName === field as string);
    },
    [predictions]
  );

  return {
    values,
    setValues,
    updateField,
    predictions,
    isLoadingPredictions,
    isApplyingPredictions,
    applyPredictionsToForm,
    refreshPredictions,
    getFieldConfidence: getFieldConfidenceForField,
    getPredictionReasoning: getPredictionReasoningForField,
    hasPrediction,
  };
}
