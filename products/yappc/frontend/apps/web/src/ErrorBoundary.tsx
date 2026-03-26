import { Component, ErrorInfo } from 'react';
import type { ReactNode } from 'react';

interface Props {
    children: ReactNode;
    fallback?: ReactNode;
}

interface State {
    hasError: boolean;
    error: Error | null;
}

/**
 * Top-level React error boundary that catches unhandled rendering errors
 * and prevents a full application crash.
 */
export class ErrorBoundary extends Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error: Error): State {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, info: ErrorInfo): void {
        // Log to console in development; replace with observability integration when available
        console.error('[ErrorBoundary] Unhandled render error:', error, info.componentStack);
    }

    render() {
        if (this.state.hasError) {
            if (this.props.fallback) {
                return this.props.fallback;
            }
            return (
                <div role="alert" style={{ padding: '2rem', textAlign: 'center' }}>
                    <h2>Something went wrong</h2>
                    <p>An unexpected error occurred. Please refresh the page or contact support.</p>
                    {import.meta.env.DEV && this.state.error && (
                        <pre style={{ textAlign: 'left', fontSize: '0.875rem', color: 'red' }}>
                            {this.state.error.message}
                        </pre>
                    )}
                    <button onClick={() => this.setState({ hasError: false, error: null })}>
                        Try again
                    </button>
                </div>
            );
        }

        return this.props.children;
    }
}
