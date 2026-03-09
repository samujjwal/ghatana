/**
 * Canvas Error Boundary
 * 
 * Catches rendering errors in canvas subsystems (nodes, panels, overlays)
 * and provides a graceful fallback UI instead of crashing the entire workspace.
 * 
 * @doc.type component
 * @doc.purpose Error isolation for canvas subsystems
 * @doc.layer product
 * @doc.pattern Error Boundary
 */

import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { Box, Typography } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

interface CanvasErrorBoundaryProps {
    /** Component label for error messages */
    label: string;
    /** Fallback UI to show on error. Defaults to inline error message. */
    fallback?: ReactNode;
    /** Callback when an error is caught */
    onError?: (error: Error, errorInfo: ErrorInfo) => void;
    /** Whether to show a reset button */
    showReset?: boolean;
    /** Children to render */
    children: ReactNode;
}

interface CanvasErrorBoundaryState {
    hasError: boolean;
    error: Error | null;
}

// ============================================================================
// Component
// ============================================================================

/**
 * Error boundary that wraps canvas subsystems to prevent cascading failures.
 * 
 * Usage:
 * ```tsx
 * <CanvasErrorBoundary label="Inspector Panel">
 *   <InspectorPanel ... />
 * </CanvasErrorBoundary>
 * ```
 */
export class CanvasErrorBoundary extends Component<CanvasErrorBoundaryProps, CanvasErrorBoundaryState> {
    constructor(props: CanvasErrorBoundaryProps) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error: Error): CanvasErrorBoundaryState {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
        console.error(`[CanvasErrorBoundary:${this.props.label}]`, error, errorInfo);
        this.props.onError?.(error, errorInfo);
    }

    private handleReset = () => {
        this.setState({ hasError: false, error: null });
    };

    render(): ReactNode {
        if (this.state.hasError) {
            if (this.props.fallback) {
                return this.props.fallback;
            }

            return (
                <Box
                    className="p-4 border border-red-200 dark:border-red-800 rounded-lg bg-red-50 dark:bg-red-950 text-center"
                    role="alert"
                    aria-live="assertive"
                >
                    <Typography as="p" className="text-sm font-medium text-red-700 dark:text-red-300">
                        {this.props.label} encountered an error
                    </Typography>
                    <Typography as="p" className="text-xs text-red-500 dark:text-red-400 mt-1">
                        {this.state.error?.message || 'Unknown error'}
                    </Typography>
                    {this.props.showReset !== false && (
                        <button
                            onClick={this.handleReset}
                            className="mt-2 px-3 py-1 text-xs rounded bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-300 hover:bg-red-200 dark:hover:bg-red-800 transition-colors"
                            type="button"
                        >
                            Retry
                        </button>
                    )}
                </Box>
            );
        }

        return this.props.children;
    }
}

export default CanvasErrorBoundary;
