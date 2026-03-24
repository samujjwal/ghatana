/**
 * Error Boundary Tests
 * 
 * @module ui/error
 * @doc.type test
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorBoundary } from '../ErrorBoundary';
import { CardErrorFallback, MinimalErrorFallback } from '../ErrorFallback';
import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';

// ============================================================================
// Test Components
// ============================================================================

/**
 * Component that throws an error
 */
function ThrowError({ shouldThrow }: { shouldThrow?: boolean }) {
  if (shouldThrow) {
    throw new Error('Test error');
  }
  return <div>No error</div>;
}

/**
 * Component that throws async error
 */
function ThrowAsyncError() {
  throw new Promise((_, reject) => {
    setTimeout(() => reject(new Error('Async error')), 100);
  });
}

// ============================================================================
// ErrorBoundary Tests
// ============================================================================

describe('ErrorBoundary', () => {
  it('renders children when no error', () => {
    render(
      <ErrorBoundary>
        <div>Test content</div>
      </ErrorBoundary>
    );
    
    expect(screen.getByText('Test content')).toBeInTheDocument();
  });
  
  it('renders default fallback when error occurs', () => {
    // Suppress console.error for this test
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    expect(screen.getByText(/test error/i)).toBeInTheDocument();
    
    consoleSpy.mockRestore();
  });
  
  it('renders custom fallback when provided', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    
    render(
      <ErrorBoundary fallback={<div>Custom fallback</div>}>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.getByText('Custom fallback')).toBeInTheDocument();
    
    consoleSpy.mockRestore();
  });
  
  it('calls onError callback when error occurs', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const onError = vi.fn();
    
    render(
      <ErrorBoundary onError={onError}>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({
        componentStack: expect.any(String),
      })
    );
    
    consoleSpy.mockRestore();
  });
  
  it('resets error boundary on retry', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const user = userEvent.setup();
    
    const { rerender } = render(
      <ErrorBoundary>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    
    const retryButton = screen.getByRole('button', { name: /try again/i });
    await user.click(retryButton);
    
    // Re-render without error
    rerender(
      <ErrorBoundary>
        <ThrowError shouldThrow={false} />
      </ErrorBoundary>
    );
    
    expect(screen.getByText('No error')).toBeInTheDocument();
    
    consoleSpy.mockRestore();
  });
  
  it('calls onReset callback on reset', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const onReset = vi.fn();
    const user = userEvent.setup();
    
    render(
      <ErrorBoundary onReset={onReset}>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    const retryButton = screen.getByRole('button', { name: /try again/i });
    await user.click(retryButton);
    
    expect(onReset).toHaveBeenCalledTimes(1);
    
    consoleSpy.mockRestore();
  });
  
  it('shows error details in development', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const originalEnv = process.env.NODE_ENV;
    process.env.NODE_ENV = 'development';
    
    render(
      <ErrorBoundary showDetails>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.getByText(/error details/i)).toBeInTheDocument();
    
    process.env.NODE_ENV = originalEnv;
    consoleSpy.mockRestore();
  });
  
  it('hides error details in production', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const originalEnv = process.env.NODE_ENV;
    process.env.NODE_ENV = 'production';
    
    render(
      <ErrorBoundary showDetails={false}>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.queryByText(/error details/i)).not.toBeInTheDocument();
    
    process.env.NODE_ENV = originalEnv;
    consoleSpy.mockRestore();
  });
  
  it('shows boundary name when provided', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    
    render(
      <ErrorBoundary boundaryName="TestBoundary">
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.getByText(/testboundary/i)).toBeInTheDocument();
    
    consoleSpy.mockRestore();
  });
  
  it('resets on props change when resetOnPropsChange is true', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    
    const { rerender } = render(
      <ErrorBoundary resetOnPropsChange resetKeys={['key1']}>
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    
    // Change reset key
    rerender(
      <ErrorBoundary resetOnPropsChange resetKeys={['key2']}>
        <ThrowError shouldThrow={false} />
      </ErrorBoundary>
    );
    
    expect(screen.getByText('No error')).toBeInTheDocument();
    
    consoleSpy.mockRestore();
  });
});

// ============================================================================
// Error Fallback Tests
// ============================================================================

describe('MinimalErrorFallback', () => {
  it('renders error message', () => {
    render(<MinimalErrorFallback message="Test error" />);
    
    expect(screen.getByText(/test error/i)).toBeInTheDocument();
  });
  
  it('renders retry button when onReset provided', async () => {
    const onReset = vi.fn();
    const user = userEvent.setup();
    
    render(<MinimalErrorFallback onReset={onReset} />);
    
    const retryButton = screen.getByRole('button', { name: /try again/i });
    await user.click(retryButton);
    
    expect(onReset).toHaveBeenCalledTimes(1);
  });
  
  it('does not render retry button when onReset not provided', () => {
    render(<MinimalErrorFallback />);
    
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('CardErrorFallback', () => {
  const mockError = new Error('Test error');
  
  it('renders error message', () => {
    render(<CardErrorFallback error={mockError} />);
    
    expect(screen.getByText(/test error/i)).toBeInTheDocument();
  });
  
  it('renders custom title', () => {
    render(<CardErrorFallback error={mockError} title="Custom Title" />);
    
    expect(screen.getByText('Custom Title')).toBeInTheDocument();
  });
  
  it('renders custom message', () => {
    render(<CardErrorFallback error={mockError} message="Custom message" />);
    
    expect(screen.getByText('Custom message')).toBeInTheDocument();
  });
  
  it('renders retry button when onReset provided', async () => {
    const onReset = vi.fn();
    const user = userEvent.setup();
    
    render(<CardErrorFallback error={mockError} onReset={onReset} />);
    
    const retryButton = screen.getByRole('button', { name: /retry/i });
    await user.click(retryButton);
    
    expect(onReset).toHaveBeenCalledTimes(1);
  });
});

// ============================================================================
// Integration Tests
// ============================================================================

describe('ErrorBoundary Integration', () => {
  it('works with custom fallback component', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const mockError = new Error('Test error');
    
    render(
      <ErrorBoundary
        fallback={(error) => <CardErrorFallback error={error} />}
      >
        <ThrowError shouldThrow />
      </ErrorBoundary>
    );
    
    expect(screen.getByText(/test error/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
    
    consoleSpy.mockRestore();
  });
  
  it('supports nested error boundaries', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    
    render(
      <ErrorBoundary boundaryName="Outer">
        <div>
          <ErrorBoundary boundaryName="Inner">
            <ThrowError shouldThrow />
          </ErrorBoundary>
        </div>
      </ErrorBoundary>
    );
    
    // Should catch at inner boundary
    expect(screen.getByText(/inner/i)).toBeInTheDocument();
    expect(screen.queryByText(/outer/i)).not.toBeInTheDocument();
    
    consoleSpy.mockRestore();
  });
});
