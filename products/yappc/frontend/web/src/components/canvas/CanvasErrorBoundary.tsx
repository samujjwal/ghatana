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
import { Box, Typography } from '@ghatana/design-system';

import { Button } from '../ui/Button';

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
                    className="p-4 border border-destructive-border dark:border-destructive-border rounded-lg bg-destructive-bg dark:bg-destructive-bg text-center"
                    role="alert"
                    aria-live="assertive"
                >
                    <Typography as="p" className="text-sm font-medium text-destructive dark:text-destructive">
                        {this.props.label} encountered an error
                    </Typography>
                    <Typography as="p" className="text-xs text-destructive dark:text-destructive mt-1">
                        {this.state.error?.message || 'Unknown error'}
                    </Typography>
                    {this.props.showReset !== false && (
                        <Button
                            variant="soft"
                            tone="danger"
                            size="small"
                            onClick={this.handleReset}
                            type="button"
                            className="mt-2"
                        >
                            Retry
                        </Button>
                    )}
                </Box>
            );
        }

        return this.props.children;
    }
}

export default CanvasErrorBoundary;
