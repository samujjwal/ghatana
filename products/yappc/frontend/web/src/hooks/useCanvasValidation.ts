/**
 * Canvas Validation Hook
 * 
 * Integrates ValidationAgent with Canvas Lifecycle
 * Automatically validates on phase transitions
 * 
 * @doc.type hook
 * @doc.purpose Integrate validation with lifecycle management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { validationAgent, type ValidationReport, type ValidationIssue } from '../services/canvas/agents/ValidationAgent';
import { LifecyclePhase } from '../types/lifecycle';
import type { CanvasState } from '../components/canvas/workspace/canvasAtoms';

// ============================================================================
// Types
// ============================================================================

export interface UseCanvasValidationOptions {
    canvasState: CanvasState;
    lifecyclePhase: LifecyclePhase;
    enabled?: boolean;
    autoValidate?: boolean;
    validateOnPhaseChange?: boolean;
}

export interface UseCanvasValidationResult {
    /** Current validation report */
    validationReport: ValidationReport | null;
    /** Is validation running */
    isValidating: boolean;
    /** Trigger validation manually */
    validate: () => Promise<ValidationReport>;
    /** Get issues by severity */
    getIssuesBySeverity: (severity: 'error' | 'warning' | 'info') => ValidationIssue[];
    /** Get issues by phase */
    getIssuesByPhase: (phase: LifecyclePhase) => ValidationIssue[];
    /** Check if canvas is valid for current phase */
    isValid: boolean;
    /** Validation score (0-100) */
    score: number;
    /** Error count */
    errorCount: number;
    /** Warning count */
    warningCount: number;
    /** Info count */
    infoCount: number;
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Canvas Validation Hook
 * 
 * Integrates validation with lifecycle phase management.
 * Automatically runs validation when phase changes or canvas state updates.
 */
export function useCanvasValidation(
    options: UseCanvasValidationOptions
): UseCanvasValidationResult {
    const {
        canvasState,
        lifecyclePhase,
        enabled = true,
        autoValidate = true,
        validateOnPhaseChange = true,
    } = options;

    const [validationReport, setValidationReport] = useState<ValidationReport | null>(null);
    const [isValidating, setIsValidating] = useState(false);
    const [lastPhase, setLastPhase] = useState<LifecyclePhase>(lifecyclePhase);

    /**
     * Trigger validation
     */
    const validate = useCallback(async (): Promise<ValidationReport> => {
        if (!enabled) {
            return {
                phase: lifecyclePhase,
                timestamp: Date.now(),
                valid: true,
                score: 100,
                issues: [],
                summary: { errors: 0, warnings: 0, info: 0 },
                gaps: [],
                risks: [],
            };
        }

        setIsValidating(true);

        try {
            const report = await validationAgent.validate({
                canvasState,
                lifecyclePhase,
            });

            setValidationReport(report);
            return report;
        } catch (error) {
            console.error('Validation failed:', error);
            // Return error report
            const errorReport: ValidationReport = {
                phase: lifecyclePhase,
                timestamp: Date.now(),
                valid: false,
                score: 0,
                issues: [
                    {
                        id: `validation-error-${Date.now()}`,
                        phase: lifecyclePhase,
                        severity: 'error',
                        category: 'system',
                        title: 'Validation Failed',
                        description: error instanceof Error ? error.message : 'Unknown error',
                        elementIds: [],
                        autoFixable: false,
                    },
                ],
                summary: { errors: 1, warnings: 0, info: 0 },
                gaps: [],
                risks: [],
            };
            setValidationReport(errorReport);
            return errorReport;
        } finally {
            setIsValidating(false);
        }
    }, [canvasState, lifecyclePhase, enabled]);

    /**
     * Auto-validate on canvas state changes
     */
    useEffect(() => {
        if (!enabled || !autoValidate) return;

        const timeoutId = setTimeout(() => {
            validate();
        }, 1000); // Debounce 1 second

        return () => clearTimeout(timeoutId);
    }, [canvasState.elements, canvasState.connections, enabled, autoValidate, validate]);

    /**
     * Validate on phase change
     */
    useEffect(() => {
        if (!enabled || !validateOnPhaseChange) return;

        if (lifecyclePhase !== lastPhase) {
            setLastPhase(lifecyclePhase);
            validate();
        }
    }, [lifecyclePhase, lastPhase, enabled, validateOnPhaseChange, validate]);

    /**
     * Helper: Get issues by severity
     */
    const getIssuesBySeverity = useCallback(
        (severity: 'error' | 'warning' | 'info'): ValidationIssue[] => {
            return validationReport?.issues.filter(issue => issue.severity === severity) || [];
        },
        [validationReport]
    );

    /**
     * Helper: Get issues by phase
     */
    const getIssuesByPhase = useCallback(
        (phase: LifecyclePhase): ValidationIssue[] => {
            return validationReport?.issues.filter(issue => issue.phase === phase) || [];
        },
        [validationReport]
    );

    /**
     * Computed values
     */
    const isValid = useMemo(
        () => validationReport?.valid ?? true,
        [validationReport]
    );

    const score = useMemo(
        () => validationReport?.score ?? 100,
        [validationReport]
    );

    const errorCount = useMemo(
        () => validationReport?.summary.errors ?? 0,
        [validationReport]
    );

    const warningCount = useMemo(
        () => validationReport?.summary.warnings ?? 0,
        [validationReport]
    );

    const infoCount = useMemo(
        () => validationReport?.summary.info ?? 0,
        [validationReport]
    );

    return {
        validationReport,
        isValidating,
        validate,
        getIssuesBySeverity,
        getIssuesByPhase,
        isValid,
        score,
        errorCount,
        warningCount,
        infoCount,
    };
}
