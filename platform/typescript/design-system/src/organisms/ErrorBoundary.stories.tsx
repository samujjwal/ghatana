import type { Meta, StoryObj } from '@storybook/react';
import { ErrorBoundary, withErrorBoundary, type ErrorContext } from './ErrorBoundary';
import { useState } from 'react';

/**
 * ErrorBoundary catches JavaScript errors anywhere in the child component tree.
 * 
 * ## Features
 * 
 * - **Error Catching**: Catches errors in rendering, lifecycle methods, and constructors
 * - **Fallback UI**: Provides default fallback UI or custom fallback
 * - **Error Reporting**: Callback for logging/reporting errors
 * - **Reset/Reload**: Options to retry rendering or reload page
 * - **Development Mode**: Shows error details in development
 * - **Customizable**: Custom button text, visibility, and fallback UI
 * 
 * ## Usage
 * 
 * ```tsx
 * import { ErrorBoundary } from '@ghatana/design-system';
 * 
 * function App() {
 *   return (
 *     <ErrorBoundary
 *       onError={(errorContext) => {
 *         // Log to error tracking service
 *         logError(errorContext);
 *       }}
 *     >
 *       <YourApp />
 *     </ErrorBoundary>
 *   );
 * }
 * ```
 * 
 * ## Best Practices
 * 
 * 1. Use multiple error boundaries for different app sections
 * 2. Always provide onError callback for production error reporting
 * 3. Test error boundaries with intentional errors in development
 * 4. Consider custom fallbacks for critical vs. non-critical sections
 */
const meta = {
  title: 'Organisms/ErrorBoundary',
  component: ErrorBoundary,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'A React error boundary component for graceful error handling with customizable fallback UI.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    children: {
      description: 'Child components to wrap with error boundary',
      table: {
        type: { summary: 'ReactNode' },
      },
    },
    fallback: {
      description: 'Custom fallback UI to display when error occurs',
      table: {
        type: { summary: 'ReactNode | ((errorContext: ErrorContext) => ReactNode)' },
      },
    },
    onError: {
      description: 'Callback fired when error is caught',
      table: {
        type: { summary: '(errorContext: ErrorContext) => void' },
      },
    },
    logErrors: {
      control: 'boolean',
      description: 'Whether to log errors to console in development',
    },
    showErrorDetails: {
      control: 'boolean',
      description: 'Whether to show error details in development',
    },
    resetButtonText: {
      control: 'text',
      description: 'Custom reset button text',
    },
    reloadButtonText: {
      control: 'text',
      description: 'Custom reload button text',
    },
    showResetButton: {
      control: 'boolean',
      description: 'Whether to show the reset button',
    },
    showReloadButton: {
      control: 'boolean',
      description: 'Whether to show the reload button',
    },
  },
} satisfies Meta<typeof ErrorBoundary>;

export default meta;
type Story = StoryObj<typeof meta>;

// Component that throws an error
const BrokenComponent = () => {
  throw new Error('This component intentionally throws an error!');
};

// Working component
const WorkingComponent = () => (
  <div className="p-8">
    <h2 className="text-2xl font-bold mb-4">Everything is working fine!</h2>
    <p className="text-gray-600">This component renders without errors.</p>
  </div>
);

/**
 * Default error boundary with standard fallback UI.
 */
export const Default: Story = {
  render: () => (
    <ErrorBoundary>
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Error boundary showing error details (development mode).
 */
export const WithErrorDetails: Story = {
  render: () => (
    <ErrorBoundary showErrorDetails={true}>
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Error boundary with custom fallback UI.
 */
export const CustomFallback: Story = {
  render: () => (
    <ErrorBoundary
      fallback={
        <div className="min-h-screen bg-purple-50 flex items-center justify-center p-4">
          <div className="bg-white p-8 rounded-lg shadow-lg max-w-md">
            <h1 className="text-2xl font-bold text-purple-900 mb-4">Oops!</h1>
            <p className="text-purple-700">
              Something went wrong with this section. Don't worry, the rest of the app is still working.
            </p>
            <button
              onClick={() => window.location.reload()}
              className="mt-6 w-full px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700"
            >
              Reload Page
            </button>
          </div>
        </div>
      }
    >
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Error boundary with function fallback for accessing error details.
 */
export const FunctionFallback: Story = {
  render: () => (
    <ErrorBoundary
      fallback={({ error, errorInfo }: ErrorContext) => (
        <div className="min-h-screen bg-red-50 flex items-center justify-center p-4">
          <div className="bg-white p-8 rounded-lg shadow-lg max-w-2xl">
            <h1 className="text-2xl font-bold text-red-900 mb-4">Error Details</h1>
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-semibold text-red-800">Error Message:</h2>
                <p className="text-red-700 mt-1 font-mono text-sm">{error.message}</p>
              </div>
              <div>
                <h2 className="text-lg font-semibold text-red-800">Component Stack:</h2>
                <pre className="text-red-700 mt-1 font-mono text-xs overflow-auto max-h-40 bg-red-50 p-4 rounded">
                  {errorInfo.componentStack}
                </pre>
              </div>
            </div>
          </div>
        </div>
      )}
    >
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Error boundary with error logging callback.
 */
export const WithErrorLogging: Story = {
  render: () => {
    const handleError = (errorContext: ErrorContext) => {
      console.log('Error caught by boundary:', {
        message: errorContext.error.message,
        stack: errorContext.errorInfo.componentStack,
        context: errorContext.context,
      });
      alert(`Error logged: ${errorContext.error.message}`);
    };

    return (
      <ErrorBoundary onError={handleError}>
        <BrokenComponent />
      </ErrorBoundary>
    );
  },
};

/**
 * Error boundary with custom button text.
 */
export const CustomButtonText: Story = {
  render: () => (
    <ErrorBoundary
      resetButtonText="Retry"
      reloadButtonText="Refresh"
      showErrorDetails={true}
    >
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Error boundary with only reset button (no reload).
 */
export const ResetOnly: Story = {
  render: () => (
    <ErrorBoundary showReloadButton={false}>
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Error boundary with only reload button (no reset).
 */
export const ReloadOnly: Story = {
  render: () => (
    <ErrorBoundary showResetButton={false}>
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Error boundary with no buttons (custom handling required).
 */
export const NoButtons: Story = {
  render: () => (
    <ErrorBoundary showResetButton={false} showReloadButton={false}>
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Minimal error boundary (no details, no errors logged).
 */
export const Minimal: Story = {
  render: () => (
    <ErrorBoundary
      logErrors={false}
      showErrorDetails={false}
      showResetButton={false}
      showReloadButton={false}
    >
      <BrokenComponent />
    </ErrorBoundary>
  ),
};

/**
 * Interactive example with toggle to trigger/fix error.
 */
export const Interactive: Story = {
  render: () => {
    const [shouldError, setShouldError] = useState(false);

    const ProblematicComponent = () => {
      if (shouldError) {
        throw new Error('User-triggered error!');
      }
      return (
        <div className="p-8">
          <h2 className="text-2xl font-bold mb-4">Interactive Error Demo</h2>
          <p className="text-gray-600 mb-4">
            Click the button below to trigger an error. The error boundary will catch it.
          </p>
          <button
            onClick={() => setShouldError(true)}
            className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700"
          >
            Trigger Error
          </button>
        </div>
      );
    };

    return (
      <div className="p-4">
        <div className="mb-4 bg-blue-50 p-4 rounded-md">
          <p className="text-sm text-blue-800">
            <strong>Note:</strong> After triggering an error, use the "Try Again" button to reset the error state.
          </p>
        </div>
        <ErrorBoundary showErrorDetails={true}>
          <ProblematicComponent />
        </ErrorBoundary>
      </div>
    );
  },
};

/**
 * Normal rendering without errors.
 */
export const NoError: Story = {
  render: () => (
    <ErrorBoundary>
      <WorkingComponent />
    </ErrorBoundary>
  ),
};

/**
 * withErrorBoundary HOC example.
 */
export const WithHOC: Story = {
  render: () => {
    const SafeBrokenComponent = withErrorBoundary(BrokenComponent, {
      fallback: <div className="p-8 text-center text-red-600">HOC caught this error!</div>,
    });

    return <SafeBrokenComponent />;
  },
};

/**
 * Multiple error boundaries for different sections.
 */
export const MultipleBoundaries: Story = {
  render: () => (
    <div className="min-h-screen bg-gray-100 p-8">
      <h1 className="text-3xl font-bold mb-8">Dashboard with Multiple Error Boundaries</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <ErrorBoundary
          fallback={
            <div className="bg-white p-6 rounded-lg shadow">
              <p className="text-red-600">Analytics widget failed to load</p>
            </div>
          }
        >
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">Analytics (Working)</h2>
            <p className="text-gray-600">This section is working fine.</p>
          </div>
        </ErrorBoundary>

        <ErrorBoundary
          fallback={
            <div className="bg-white p-6 rounded-lg shadow">
              <p className="text-red-600">User stats widget failed to load</p>
            </div>
          }
        >
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">User Stats (Broken)</h2>
            <BrokenComponent />
          </div>
        </ErrorBoundary>

        <ErrorBoundary
          fallback={
            <div className="bg-white p-6 rounded-lg shadow">
              <p className="text-red-600">Reports widget failed to load</p>
            </div>
          }
        >
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">Reports (Working)</h2>
            <p className="text-gray-600">Reports are loading correctly.</p>
          </div>
        </ErrorBoundary>

        <ErrorBoundary
          fallback={
            <div className="bg-white p-6 rounded-lg shadow">
              <p className="text-red-600">Settings widget failed to load</p>
            </div>
          }
        >
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">Settings (Working)</h2>
            <p className="text-gray-600">Settings are accessible.</p>
          </div>
        </ErrorBoundary>
      </div>
    </div>
  ),
};

/**
 * Production-ready error boundary setup.
 */
export const ProductionSetup: Story = {
  render: () => {
    const handleError = (errorContext: ErrorContext) => {
      // In production, send to error tracking service
      console.log('Sending to error tracking service:', {
        message: errorContext.error.message,
        stack: errorContext.error.stack,
        componentStack: errorContext.errorInfo.componentStack,
        timestamp: errorContext.context?.timestamp,
        url: errorContext.context?.url,
        userAgent: errorContext.context?.userAgent,
      });
    };

    return (
      <ErrorBoundary
        onError={handleError}
        logErrors={false} // Don't log to console in production
        showErrorDetails={false} // Hide details in production
        fallback={
          <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
            <div className="max-w-md w-full bg-white rounded-lg shadow-lg p-6 text-center">
              <h1 className="text-xl font-semibold text-gray-900 mb-4">
                We're experiencing technical difficulties
              </h1>
              <p className="text-sm text-gray-600 mb-6">
                Our team has been notified and is working on a fix. Please try again later.
              </p>
              <button
                onClick={() => window.location.reload()}
                className="w-full px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
              >
                Reload Page
              </button>
              <p className="mt-4 text-xs text-gray-500">
                If this problem persists, please contact support@example.com
              </p>
            </div>
          </div>
        }
      >
        <BrokenComponent />
      </ErrorBoundary>
    );
  },
};
