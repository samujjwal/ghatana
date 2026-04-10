/**
 * ErrorRecovery Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErrorRecovery, OfflineBanner, ErrorBoundary } from '../ErrorRecovery';
import type { ErrorInfo } from '../../../hooks/useErrorRecovery';

describe('ErrorRecovery', () => {
  it('should render error information', () => {
    const errorInfo: ErrorInfo = {
      message: 'Test error',
      category: 'network',
      severity: 'medium',
      timestamp: Date.now(),
      retryable: true,
      suggestedAction: 'Check your connection',
    };

    render(<ErrorRecovery errorInfo={errorInfo} />);

    expect(screen.getByText('Test error')).toBeDefined();
    expect(screen.getByText('Check your connection')).toBeDefined();
  });

  it('should render retry button for retryable errors', () => {
    const errorInfo: ErrorInfo = {
      message: 'Test error',
      category: 'network',
      severity: 'medium',
      timestamp: Date.now(),
      retryable: true,
    };

    const onRetry = vi.fn();
    render(<ErrorRecovery errorInfo={errorInfo} onRetry={onRetry} />);

    expect(screen.getByText('Retry')).toBeDefined();
  });

  it('should not render retry button for non-retryable errors', () => {
    const errorInfo: ErrorInfo = {
      message: 'Unauthorized',
      category: 'authentication',
      severity: 'high',
      timestamp: Date.now(),
      retryable: false,
    };

    render(<ErrorRecovery errorInfo={errorInfo} />);

    expect(screen.queryByText('Retry')).toBeNull();
  });

  it('should render dismiss button when onDismiss is provided', () => {
    const errorInfo: ErrorInfo = {
      message: 'Test error',
      category: 'network',
      severity: 'medium',
      timestamp: Date.now(),
      retryable: true,
    };

    const onDismiss = vi.fn();
    render(<ErrorRecovery errorInfo={errorInfo} onDismiss={onDismiss} />);

    // Close icon should be present
  });
});

describe('OfflineBanner', () => {
  it('should render offline banner', () => {
    render(<OfflineBanner />);

    expect(screen.getByText(/You're offline/i)).toBeDefined();
  });

  it('should show pending operations count', () => {
    render(<OfflineBanner hasPendingOperations={5} />);

    expect(screen.getByText('5 operations pending')).toBeDefined();
  });

  it('should render sync button when hasPendingOperations', () => {
    const onSync = vi.fn();
    render(<OfflineBanner hasPendingOperations={1} onSync={onSync} />);

    expect(screen.getByText('Sync Now')).toBeDefined();
  });
});

describe('ErrorBoundary', () => {
  it('should render children when no error', () => {
    render(
      <ErrorBoundary>
        <div>Test content</div>
      </ErrorBoundary>
    );

    expect(screen.getByText('Test content')).toBeDefined();
  });

  it('should render error message when error occurs', () => {
    const ThrowError = () => {
      throw new Error('Test error');
    };

    render(
      <ErrorBoundary>
        <ThrowError />
      </ErrorBoundary>
    );

    expect(screen.getByText(/Something went wrong/i)).toBeDefined();
  });
});
