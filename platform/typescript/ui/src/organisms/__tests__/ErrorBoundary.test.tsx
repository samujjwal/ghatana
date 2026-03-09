import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ErrorBoundary, withErrorBoundary, type ErrorContext } from '../ErrorBoundary';

// Test component that throws an error
const ThrowError = ({ shouldThrow = true }: { shouldThrow?: boolean }) => {
  if (shouldThrow) {
    throw new Error('Test error');
  }
  return <div>No error</div>;
};

// Suppress console.error for cleaner test output
const originalError = console.error;
beforeEach(() => {
  console.error = vi.fn();
});

afterEach(() => {
  console.error = originalError;
});

describe('ErrorBoundary', () => {
  describe('Normal Rendering', () => {
    it('should render children when no error occurs', () => {
      render(
        <ErrorBoundary>
          <div>Test content</div>
        </ErrorBoundary>
      );

      expect(screen.getByText('Test content')).toBeInTheDocument();
    });

    it('should not display fallback UI when no error', () => {
      render(
        <ErrorBoundary>
          <div>Test content</div>
        </ErrorBoundary>
      );

      expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('should catch errors and display default fallback UI', () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
      expect(screen.getByText(/We're sorry, but something unexpected happened/)).toBeInTheDocument();
    });

    it('should display custom fallback UI when provided', () => {
      const customFallback = <div>Custom error message</div>;

      render(
        <ErrorBoundary fallback={customFallback}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText('Custom error message')).toBeInTheDocument();
      expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();
    });

    it('should display function fallback with error context', () => {
      const fallbackFn = ({ error }: ErrorContext) => (
        <div>Error: {error.message}</div>
      );

      render(
        <ErrorBoundary fallback={fallbackFn}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText('Error: Test error')).toBeInTheDocument();
    });

    it('should call onError callback when error occurs', () => {
      const onError = vi.fn();

      render(
        <ErrorBoundary onError={onError}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(onError).toHaveBeenCalledTimes(1);
      expect(onError).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.any(Error),
          errorInfo: expect.objectContaining({
            componentStack: expect.any(String),
          }),
          context: expect.objectContaining({
            timestamp: expect.any(String),
          }),
        })
      );
    });
  });

  describe('Default Fallback UI', () => {
    it('should display error icon', () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>
      );

      const icon = screen.getByRole('img', { hidden: true });
      expect(icon).toBeInTheDocument();
    });

    it('should display error heading', () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument();
    });

    it('should display support message', () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText(/If this problem persists, please contact support/)).toBeInTheDocument();
    });
  });

  describe('Error Details', () => {
    it('should show error details in development when showErrorDetails=true', () => {
      render(
        <ErrorBoundary showErrorDetails={true}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText('Error Details')).toBeInTheDocument();
    });

    it('should hide error details when showErrorDetails=false', () => {
      render(
        <ErrorBoundary showErrorDetails={false}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.queryByText('Error Details')).not.toBeInTheDocument();
    });

    it('should display error message in details', () => {
      render(
        <ErrorBoundary showErrorDetails={true}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText(/Test error/)).toBeInTheDocument();
    });
  });

  describe('Reset Functionality', () => {
    it('should show reset button by default', () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByRole('button', { name: /Try to recover from error/ })).toBeInTheDocument();
    });

    it('should hide reset button when showResetButton=false', () => {
      render(
        <ErrorBoundary showResetButton={false}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.queryByRole('button', { name: /Try to recover from error/ })).not.toBeInTheDocument();
    });

    it('should use custom reset button text', () => {
      render(
        <ErrorBoundary resetButtonText="Retry Now">
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText('Retry Now')).toBeInTheDocument();
    });

    it('should reset error state when reset button is clicked', () => {
      const { rerender } = render(
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();

      const resetButton = screen.getByRole('button', { name: /Try to recover from error/ });
      fireEvent.click(resetButton);

      // After reset, re-render with non-throwing component
      rerender(
        <ErrorBoundary>
          <ThrowError shouldThrow={false} />
        </ErrorBoundary>
      );

      expect(screen.getByText('No error')).toBeInTheDocument();
    });
  });

  describe('Reload Functionality', () => {
    it('should show reload button by default', () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByRole('button', { name: /Reload the page/ })).toBeInTheDocument();
    });

    it('should hide reload button when showReloadButton=false', () => {
      render(
        <ErrorBoundary showReloadButton={false}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.queryByRole('button', { name: /Reload the page/ })).not.toBeInTheDocument();
    });

    it('should use custom reload button text', () => {
      render(
        <ErrorBoundary reloadButtonText="Refresh Page">
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.getByText('Refresh Page')).toBeInTheDocument();
    });

    it('should call window.location.reload when reload button is clicked', () => {
      const reloadMock = vi.fn();
      Object.defineProperty(window, 'location', {
        value: { reload: reloadMock },
        writable: true,
      });

      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>
      );

      const reloadButton = screen.getByRole('button', { name: /Reload the page/ });
      fireEvent.click(reloadButton);

      expect(reloadMock).toHaveBeenCalledTimes(1);
    });
  });

  describe('Customization Options', () => {
    it('should hide both buttons when both are disabled', () => {
      render(
        <ErrorBoundary showResetButton={false} showReloadButton={false}>
          <ThrowError />
        </ErrorBoundary>
      );

      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('should respect logErrors=false', () => {
      const consoleErrorSpy = vi.spyOn(console, 'error');

      render(
        <ErrorBoundary logErrors={false}>
          <ThrowError />
        </ErrorBoundary>
      );

      // Should not log to console even in development
      expect(consoleErrorSpy).not.toHaveBeenCalled();
    });
  });

  describe('Edge Cases', () => {
    it('should handle errors with no message', () => {
      const ThrowEmptyError = () => {
        throw new Error();
      };

      render(
        <ErrorBoundary showErrorDetails={true}>
          <ThrowEmptyError />
        </ErrorBoundary>
      );

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    });

    it('should handle errors in nested components', () => {
      const NestedComponent = () => (
        <div>
          <div>
            <ThrowError />
          </div>
        </div>
      );

      render(
        <ErrorBoundary>
          <NestedComponent />
        </ErrorBoundary>
      );

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    });

    it('should handle multiple children', () => {
      render(
        <ErrorBoundary>
          <div>Child 1</div>
          <ThrowError />
          <div>Child 3</div>
        </ErrorBoundary>
      );

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    });
  });
});

describe('withErrorBoundary', () => {
  describe('HOC Wrapping', () => {
    it('should wrap component with error boundary', () => {
      const SafeComponent = withErrorBoundary(ThrowError);

      render(<SafeComponent />);

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    });

    it('should render wrapped component normally when no error', () => {
      const NormalComponent = () => <div>Normal rendering</div>;
      const SafeComponent = withErrorBoundary(NormalComponent);

      render(<SafeComponent />);

      expect(screen.getByText('Normal rendering')).toBeInTheDocument();
    });

    it('should preserve component display name', () => {
      const MyComponent = () => <div>Test</div>;
      MyComponent.displayName = 'MyComponent';

      const SafeComponent = withErrorBoundary(MyComponent);

      expect(SafeComponent.displayName).toBe('withErrorBoundary(MyComponent)');
    });

    it('should use component name when displayName is not set', () => {
      const MyComponent = () => <div>Test</div>;

      const SafeComponent = withErrorBoundary(MyComponent);

      expect(SafeComponent.displayName).toBe('withErrorBoundary(MyComponent)');
    });

    it('should default to "Component" when name is not available', () => {
      const SafeComponent = withErrorBoundary(() => <div>Test</div>);

      expect(SafeComponent.displayName).toBe('withErrorBoundary(Component)');
    });
  });

  describe('HOC with Options', () => {
    it('should use custom fallback', () => {
      const SafeComponent = withErrorBoundary(ThrowError, {
        fallback: <div>HOC custom fallback</div>,
      });

      render(<SafeComponent />);

      expect(screen.getByText('HOC custom fallback')).toBeInTheDocument();
    });

    it('should call onError callback', () => {
      const onError = vi.fn();
      const SafeComponent = withErrorBoundary(ThrowError, { onError });

      render(<SafeComponent />);

      expect(onError).toHaveBeenCalledTimes(1);
    });

    it('should use function fallback', () => {
      const SafeComponent = withErrorBoundary(ThrowError, {
        fallback: ({ error }) => <div>HOC Error: {error.message}</div>,
      });

      render(<SafeComponent />);

      expect(screen.getByText('HOC Error: Test error')).toBeInTheDocument();
    });
  });

  describe('HOC Props Passing', () => {
    it('should pass props to wrapped component', () => {
      interface TestProps {
        message: string;
      }

      const TestComponent = ({ message }: TestProps) => <div>{message}</div>;
      const SafeComponent = withErrorBoundary(TestComponent);

      render(<SafeComponent message="Test message" />);

      expect(screen.getByText('Test message')).toBeInTheDocument();
    });

    it('should handle components with multiple props', () => {
      interface MultiProps {
        title: string;
        count: number;
        enabled: boolean;
      }

      const MultiPropComponent = ({ title, count, enabled }: MultiProps) => (
        <div>
          {title}: {count} ({enabled ? 'enabled' : 'disabled'})
        </div>
      );

      const SafeComponent = withErrorBoundary(MultiPropComponent);

      render(<SafeComponent title="Test" count={42} enabled={true} />);

      expect(screen.getByText('Test: 42 (enabled)')).toBeInTheDocument();
    });
  });

  describe('HOC Edge Cases', () => {
    it('should handle components with no props', () => {
      const NoPropsComponent = () => <div>No props</div>;
      const SafeComponent = withErrorBoundary(NoPropsComponent);

      render(<SafeComponent />);

      expect(screen.getByText('No props')).toBeInTheDocument();
    });

    it('should handle class components', () => {
      class ClassComponent extends React.Component {
        render() {
          return <div>Class component</div>;
        }
      }

      const SafeComponent = withErrorBoundary(ClassComponent);

      render(<SafeComponent />);

      expect(screen.getByText('Class component')).toBeInTheDocument();
    });
  });
});
