/**
 * CanvasErrorBoundary - Error boundary for canvas rendering
 * 
 * @doc.type component
 * @doc.purpose Catch and handle canvas rendering errors
 * @doc.layer components
 * @doc.pattern ErrorBoundary
 */

import React, { Component, ErrorInfo, ReactNode } from 'react';
import { Box, Typography, Button } from '@ghatana/ui';

interface Props {
    children: ReactNode;
    fallback?: ReactNode;
    onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface State {
    hasError: boolean;
    error: Error | null;
    errorInfo: ErrorInfo | null;
}

export class CanvasErrorBoundary extends Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {
            hasError: false,
            error: null,
            errorInfo: null
        };
    }

    static getDerivedStateFromError(error: Error): Partial<State> {
        return {
            hasError: true,
            error
        };
    }

    componentDidCatch(error: Error, errorInfo: ErrorInfo) {
        console.error('[CanvasErrorBoundary] Caught error:', error, errorInfo);

        this.setState({
            error,
            errorInfo
        });

        // Call optional error handler
        this.props.onError?.(error, errorInfo);
    }

    handleReset = () => {
        this.setState({
            hasError: false,
            error: null,
            errorInfo: null
        });
    };

    render() {
        if (this.state.hasError) {
            // Custom fallback if provided
            if (this.props.fallback) {
                return this.props.fallback;
            }

            // Default error UI
            return (
                <Box
                    className="flex flex-col items-center justify-center h-full p-8 bg-white dark:bg-gray-900"
                >
                    <Typography variant="h4" color="error" gutterBottom>
                        ⚠️ Canvas Error
                    </Typography>
                    <Typography variant="body1" color="text.secondary" className="mb-4">
                        Something went wrong while rendering the canvas.
                    </Typography>
                    {this.state.error && (
                        <Box
                            className="p-4 mb-4 rounded text-sm overflow-auto bg-gray-100 dark:bg-gray-800 font-mono max-w-[600px]"
                        >
                            <Typography variant="body2" color="error">
                                {this.state.error.toString()}
                            </Typography>
                            {this.state.errorInfo && (
                                <Typography
                                    variant="caption"
                                    component="pre"
                                    className="mt-2 overflow-auto"
                                >
                                    {this.state.errorInfo.componentStack}
                                </Typography>
                            )}
                        </Box>
                    )}
                    <Button variant="contained" onClick={this.handleReset}>
                        Try Again
                    </Button>
                </Box>
            );
        }

        return this.props.children;
    }
}
