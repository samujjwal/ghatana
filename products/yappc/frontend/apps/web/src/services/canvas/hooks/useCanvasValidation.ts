/**
 * useCanvasValidation Hook
 * 
 * Validates the canvas state against rules and constraints.
 */

import { useState, useCallback } from 'react';

export interface ValidationIssue {
    id: string;
    severity: 'error' | 'warning' | 'info';
    message: string;
    nodeId?: string;
}

export interface ValidationReport {
    isValid: boolean;
    issues: ValidationIssue[];
    score: number;
}

export function useCanvasValidation() {
    const [report, setReport] = useState<ValidationReport | null>(null);
    const [isValidating, setIsValidating] = useState(false);

    const validateCanvas = useCallback(async (nodes: unknown[], edges: unknown[]) => {
        setIsValidating(true);

        // Simulate validation
        await new Promise(resolve => setTimeout(resolve, 500));

        const issues: ValidationIssue[] = [];

        if (nodes.length === 0) {
            issues.push({
                id: 'empty-canvas',
                severity: 'warning',
                message: 'Canvas is empty. Add some nodes to get started.',
            });
        }

        const newReport: ValidationReport = {
            isValid: issues.length === 0,
            issues,
            score: Math.max(0, 100 - issues.length * 10),
        };

        setReport(newReport);
        setIsValidating(false);

        return newReport;
    }, []);

    return {
        validateCanvas,
        report,
        isValidating,
    };
}
