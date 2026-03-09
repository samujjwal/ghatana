import { useCallback, useEffect } from 'react';
import { useAtom } from 'jotai';
import { workflowAtom, validationErrorsAtom } from '@/stores/workflow.store';
import { validateWorkflow } from '@/lib/services/validationService';
import type { ValidationError, ValidationWarning } from '@/types/workflow.types';

/**
 * Hook for workflow validation.
 *
 * <p><b>Purpose</b><br>
 * Provides workflow validation functionality with automatic validation on workflow changes.
 * Stores validation errors in Jotai state for display in the ValidationPanel.
 *
 * <p><b>Features</b><br>
 * - Automatic validation on workflow changes
 * - Manual validation trigger
 * - Error and warning categorization
 * - Auto-fix suggestions
 * - Performance optimized with debouncing
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useValidation } from '@/components/hooks/useValidation';
 *
 * export function WorkflowEditor() {
 *   const { errors, warnings, validate, isValid } = useValidation();
 *
 *   return (
 *     <div>
 *       {!isValid && <div>Workflow has errors</div>}
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Workflow validation hook
 * @doc.layer frontend
 * @returns validation hook interface
 */

export interface UseValidationReturn {
  /**
   * Validation errors
   */
  errors: ValidationError[];

  /**
   * Validation warnings
   */
  warnings: ValidationWarning[];

  /**
   * Whether workflow is valid
   */
  isValid: boolean;

  /**
   * Manually trigger validation
   */
  validate: () => void;

  /**
   * Clear validation errors
   */
  clear: () => void;

  /**
   * Get error count
   */
  getErrorCount: () => number;

  /**
   * Get warning count
   */
  getWarningCount: () => number;
}

/**
 * Hook for workflow validation.
 *
 * @returns validation hook interface
 */
export function useValidation(): UseValidationReturn {
  const [workflow] = useAtom(workflowAtom);
  const [validationErrors, setValidationErrors] = useAtom(validationErrorsAtom);

  // Parse stored errors
  const errors = validationErrors
    .map((err) => {
      try {
        return JSON.parse(err) as ValidationError;
      } catch {
        return { code: 'PARSE_ERROR', message: err } as ValidationError;
      }
    })
    .filter((e) => e.code.includes('ERROR'));

  const warnings = validationErrors
    .map((err) => {
      try {
        return JSON.parse(err) as ValidationWarning;
      } catch {
        return { code: 'PARSE_ERROR', message: err } as ValidationWarning;
      }
    })
    .filter((e) => !e.code.includes('ERROR'));

  // Validate workflow
  const validate = useCallback(() => {
    if (!workflow) {
      setValidationErrors([]);
      return;
    }

    const result = validateWorkflow(workflow);
    const allErrors = [...result.errors, ...result.warnings];
    setValidationErrors(allErrors.map((e) => JSON.stringify(e)));
  }, [workflow, setValidationErrors]);

  // Auto-validate on workflow changes
  useEffect(() => {
    const timer = setTimeout(() => {
      validate();
    }, 500); // Debounce validation

    return () => clearTimeout(timer);
  }, [workflow, validate]);

  // Clear validation errors
  const clear = useCallback(() => {
    setValidationErrors([]);
  }, [setValidationErrors]);

  // Get error count
  const getErrorCount = useCallback(() => {
    return errors.length;
  }, [errors]);

  // Get warning count
  const getWarningCount = useCallback(() => {
    return warnings.length;
  }, [warnings]);

  return {
    errors,
    warnings,
    isValid: errors.length === 0,
    validate,
    clear,
    getErrorCount,
    getWarningCount,
  };
}
