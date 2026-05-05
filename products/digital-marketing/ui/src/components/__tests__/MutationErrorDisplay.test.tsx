/**
 * P1-030: Tests for MutationErrorDisplay component.
 *
 * @doc.type test
 * @doc.purpose Unit tests for error display component (P1-030)
 * @doc.layer test
 */

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MutationErrorDisplay } from '../MutationErrorDisplay';
import type { ApiError } from '@/hooks/useMutationError';

describe('P1-030: MutationErrorDisplay', () => {
  it('should not render when error is null', () => {
    const { container } = render(<MutationErrorDisplay error={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('should render error code', () => {
    const error: ApiError = {
      status: 500,
      code: 'SERVER_ERROR',
      message: 'Internal server error',
      correlationId: 'corr-123',
      retryable: false,
    };

    render(<MutationErrorDisplay error={error} />);

    expect(screen.getByTestId('error-code')).toHaveTextContent('SERVER_ERROR');
  });

  it('should render error message', () => {
    const error: ApiError = {
      status: 400,
      code: 'VALIDATION',
      message: 'Invalid input provided',
      correlationId: 'corr-456',
      retryable: false,
    };

    render(<MutationErrorDisplay error={error} />);

    expect(screen.getByTestId('error-message')).toHaveTextContent('Invalid input provided');
  });

  it('should render correlation ID', () => {
    const error: ApiError = {
      status: 500,
      code: 'ERROR',
      message: 'Something went wrong',
      correlationId: 'support-id-12345',
      retryable: false,
    };

    render(<MutationErrorDisplay error={error} />);

    expect(screen.getByTestId('correlation-id')).toHaveTextContent('support-id-12345');
  });

  it('should render retry button for retryable errors', () => {
    const onRetry = vi.fn();
    const error: ApiError = {
      status: 503,
      code: 'SERVICE_UNAVAILABLE',
      message: 'Service temporarily unavailable',
      correlationId: 'corr-789',
      retryable: true,
    };

    render(<MutationErrorDisplay error={error} onRetry={onRetry} />);

    expect(screen.getByTestId('retry-button')).toBeInTheDocument();
  });

  it('should not render retry button for non-retryable errors', () => {
    const onRetry = vi.fn();
    const error: ApiError = {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Session expired',
      correlationId: 'corr-abc',
      retryable: false,
    };

    render(<MutationErrorDisplay error={error} onRetry={onRetry} />);

    expect(screen.queryByTestId('retry-button')).not.toBeInTheDocument();
  });

  it('should not render retry button when onRetry is not provided', () => {
    const error: ApiError = {
      status: 503,
      code: 'SERVICE_UNAVAILABLE',
      message: 'Service temporarily unavailable',
      correlationId: 'corr-789',
      retryable: true,
    };

    render(<MutationErrorDisplay error={error} />);

    expect(screen.queryByTestId('retry-button')).not.toBeInTheDocument();
  });

  it('should call onRetry when retry button clicked', () => {
    const onRetry = vi.fn();
    const error: ApiError = {
      status: 503,
      code: 'SERVICE_UNAVAILABLE',
      message: 'Service temporarily unavailable',
      correlationId: 'corr-789',
      retryable: true,
    };

    render(<MutationErrorDisplay error={error} onRetry={onRetry} />);

    fireEvent.click(screen.getByTestId('retry-button'));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('should have correct ARIA role', () => {
    const error: ApiError = {
      status: 500,
      code: 'ERROR',
      message: 'Error occurred',
      correlationId: 'corr-123',
      retryable: false,
    };

    render(<MutationErrorDisplay error={error} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('should apply custom className', () => {
    const error: ApiError = {
      status: 500,
      code: 'ERROR',
      message: 'Error occurred',
      correlationId: 'corr-123',
      retryable: false,
    };

    render(<MutationErrorDisplay error={error} className="custom-class" />);

    expect(screen.getByRole('alert')).toHaveClass('custom-class');
  });

  it('should render different styles for different error types', () => {
    const authError: ApiError = {
      status: 401,
      code: 'AUTH',
      message: 'Session expired',
      correlationId: 'corr-123',
      retryable: false,
    };

    const { rerender } = render(<MutationErrorDisplay error={authError} />);

    // Auth errors should have warning colors (yellow)
    const authAlert = screen.getByRole('alert');
    expect(authAlert).toHaveClass('bg-yellow-50', 'border-yellow-400');

    const serverError: ApiError = {
      status: 500,
      code: 'SERVER',
      message: 'Server error',
      correlationId: 'corr-456',
      retryable: false,
    };

    rerender(<MutationErrorDisplay error={serverError} />);

    // Server errors should have error colors (red)
    const serverAlert = screen.getByRole('alert');
    expect(serverAlert).toHaveClass('bg-red-50', 'border-red-400');
  });

  it('should render network error with appropriate styling', () => {
    const error: ApiError = {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Network error occurred',
      correlationId: 'unknown',
      retryable: true,
    };

    render(<MutationErrorDisplay error={error} />);

    const alert = screen.getByRole('alert');
    expect(alert).toHaveClass('bg-blue-50', 'border-blue-400');
    expect(screen.getByTestId('retry-button')).toBeInTheDocument();
  });

  it('should render validation error with appropriate styling', () => {
    const error: ApiError = {
      status: 422,
      code: 'VALIDATION',
      message: 'Validation failed',
      correlationId: 'corr-val',
      retryable: false,
    };

    render(<MutationErrorDisplay error={error} />);

    const alert = screen.getByRole('alert');
    expect(alert).toHaveClass('bg-orange-50', 'border-orange-400');
  });
});
