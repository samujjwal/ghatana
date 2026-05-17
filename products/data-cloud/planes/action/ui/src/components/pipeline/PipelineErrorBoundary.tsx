/**
 * PipelineErrorBoundary — React error boundary for the pipeline editor.
 *
 * Catches render errors in PipelineCanvas and its subtree and displays
 * a fallback UI instead of blanking the entire page.
 *
 * @doc.type component
 * @doc.purpose Error boundary for pipeline editor
 * @doc.layer frontend
 */
import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from '@ghatana/design-system';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class PipelineErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(_error: Error, _info: ErrorInfo) {
    // Rendering fallback UI is the fail-closed behavior for pipeline crashes.
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          className="flex flex-col items-center justify-center h-full gap-4 text-center p-8"
          data-testid="pipeline-error-fallback"
        >
          <div className="text-4xl">⚠️</div>
          <h2 className="text-lg font-semibold text-gray-800">Pipeline editor encountered an error</h2>
          <p className="text-sm text-gray-500 max-w-md">
            {this.state.error?.message ?? 'An unexpected rendering error occurred.'}
          </p>
          <Button
            onClick={this.handleReset}
            variant="primary"
            className="px-4 py-2 text-sm font-medium rounded bg-blue-600 text-white hover:bg-blue-700"
          >
            Try to recover
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}
