/**
 * Smart Form Component
 *
 * AI-enhanced form component with intelligent field suggestions and prefill.
 * Provides adaptive form behavior based on user context and prediction confidence.
 *
 * @doc.type component
 * @doc.purpose AI-enhanced form with intelligent prefill
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React, { ReactNode } from 'react';
import { Sparkles as AutoAwesome, Lightbulb as LightBulbIcon } from 'lucide-react';
import { Typography, Button, Chip, Box } from '@ghatana/design-system';
import { z } from 'zod';
import { useSmartForm, type UseSmartFormOptions, type UseSmartFormResult } from '../../hooks/useSmartForm';
import type { FormFieldPrediction } from '../../services/ai/FormPredictionService';

// ============================================================================
// Types
// ============================================================================

export interface SmartFormProps<T extends Record<string, unknown>> {
  schema: z.ZodSchema<T>;
  initialValues?: Partial<T>;
  formType: string;
  context?: UseSmartFormOptions<T>['context'];
  enableAIPrefill?: boolean;
  confidenceThreshold?: number;
  onPredictionsChange?: (predictions: FormFieldPrediction[]) => void;
  onSubmit: (values: T) => Promise<void> | void;
  onCancel?: () => void;
  isSubmitting?: boolean;
  children: (form: UseSmartFormResult<T>) => ReactNode;
}

export interface SmartFormFieldProps {
  fieldName: string;
  label: string;
  confidence?: number;
  reasoning?: string;
  onAcceptSuggestion?: () => void;
  onRejectSuggestion?: () => void;
  children: ReactNode;
}

// ============================================================================
// Field Component
// ============================================================================

/**
 * Smart field wrapper with suggestion display
 */
export function SmartFormField({
  fieldName,
  label,
  confidence,
  reasoning,
  onAcceptSuggestion,
  onRejectSuggestion,
  children,
}: SmartFormFieldProps): ReactNode {
  const hasSuggestion = confidence !== undefined && confidence > 0.5;

  return (
    <div className="mb-4">
      <div className="flex items-center justify-between mb-1">
        <Typography className="font-medium">{label}</Typography>
        {hasSuggestion && (
          <Chip
            size="sm"
            icon={<AutoAwesome className="w-3 h-3" />}
            label={`${Math.round(confidence * 100)}% confident`}
            color="info"
          />
        )}
      </div>

      {reasoning && hasSuggestion && (
        <div className="mb-2 flex items-start gap-2 p-2 bg-info-bg dark:bg-info-bg/20 rounded-md">
          <LightBulbIcon className="w-4 h-4 text-info-color dark:text-info-color mt-0.5 flex-shrink-0" />
          <Typography className="text-sm text-info-color dark:text-info-color">
            {reasoning}
          </Typography>
        </div>
      )}

      {children}

      {hasSuggestion && (
        <div className="mt-2 flex gap-2">
          <Button
            size="sm"
            variant="text"
            onClick={onAcceptSuggestion}
            className="text-xs"
          >
            Accept
          </Button>
          <Button
            size="sm"
            variant="text"
            onClick={onRejectSuggestion}
            className="text-xs text-fg-muted"
          >
            Dismiss
          </Button>
        </div>
      )}
    </div>
  );
}

// ============================================================================
// Smart Form Component
// ============================================================================

/**
 * AI-enhanced form component
 */
export function SmartForm<T extends Record<string, unknown>>({
  schema,
  initialValues,
  formType,
  context,
  enableAIPrefill = true,
  confidenceThreshold = 0.5,
  onPredictionsChange,
  onSubmit,
  onCancel,
  isSubmitting = false,
  children,
}: SmartFormProps<T>): ReactNode {
  const form = useSmartForm<T>({
    schema,
    initialValues,
    formType,
    context,
    enableAIPrefill,
    confidenceThreshold,
    onPredictionsChange,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit(form.values);
  };

  const handleApplyPredictions = () => {
    form.applyPredictionsToForm();
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {/* Prediction Banner */}
      {form.predictions.length > 0 && !isSubmitting && (
        <Box className="p-4 bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20 rounded-lg border border-info-border dark:border-info-border">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AutoAwesome className="w-5 h-5 text-info-color dark:text-info-color" />
              <div>
                <Typography className="font-medium text-info-color dark:text-info-color">
                  Suggestions Available
                </Typography>
                <Typography className="text-sm text-info-color dark:text-info-color">
                  {form.predictions.length} field{form.predictions.length !== 1 ? 's' : ''} predicted
                </Typography>
              </div>
            </div>
            <Button
              size="sm"
              onClick={handleApplyPredictions}
              disabled={form.isApplyingPredictions}
              variant="contained"
            >
              {form.isApplyingPredictions ? 'Applying...' : 'Apply Suggestions'}
            </Button>
          </div>
        </Box>
      )}

      {/* Form Fields */}
      {children(form)}

      {/* Form Actions */}
      <div className="flex justify-end gap-2 pt-4">
        {onCancel && (
          <Button
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            variant="outlined"
          >
            Cancel
          </Button>
        )}
        <Button
          type="submit"
          disabled={isSubmitting}
          variant="contained"
        >
          {isSubmitting ? 'Submitting...' : 'Submit'}
        </Button>
      </div>
    </form>
  );
}
