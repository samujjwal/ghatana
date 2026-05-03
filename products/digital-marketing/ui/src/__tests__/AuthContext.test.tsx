/**
 * Route guard tests for AuthContext (DMOS-P1-013)
 *
 * @doc.type test
 * @doc.purpose Verify session expiry, logout, and refresh behavior
 * @doc.layer frontend
 */
import { render, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import React from 'react';

describe('AuthContext - Session Management (DMOS-P1-013)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('should automatically logout on session expiry', async () => {
    const TestComponent = () => {
      const { isAuthenticated } = useAuth();
      return <div>{isAuthenticated ? 'authenticated' : 'not authenticated'}</div>;
    };

    const { getByText } = render(
      <AuthProvider initialToken="test-token" initialSessionId="session-123">
        <TestComponent />
      </AuthProvider>,
    );

    expect(getByText('authenticated')).toBeInTheDocument();

    // Fast-forward time to beyond session expiry
    vi.useFakeTimers();
    vi.advanceTimersByTime(31 * 60 * 1000); // 31 minutes

    await waitFor(() => {
      expect(getByText('not authenticated')).toBeInTheDocument();
    });

    vi.useRealTimers();
  });

  it('should clear all session data on logout', () => {
    const TestComponent = () => {
      const { login, logout, isAuthenticated } = useAuth();
      return (
        <div>
          <div>{isAuthenticated ? 'authenticated' : 'not authenticated'}</div>
          <button onClick={() => login('token', 'ws-1', 'tenant-1', 'user-1', 'session-1', ['admin'])}>
            Login
          </button>
          <button onClick={logout}>Logout</button>
        </div>
      );
    };

    const { getByText } = render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>,
    );

    getByText('Login').click();

    expect(sessionStorage.getItem('dmos_workspace_id')).toBe('ws-1');
    expect(sessionStorage.getItem('dmos_tenant_id')).toBe('tenant-1');
    expect(sessionStorage.getItem('dmos_principal_id')).toBe('user-1');
    expect(sessionStorage.getItem('dmos_session_id')).toBe('session-1');

    getByText('Logout').click();

    expect(sessionStorage.getItem('dmos_workspace_id')).toBeNull();
    expect(sessionStorage.getItem('dmos_tenant_id')).toBeNull();
    expect(sessionStorage.getItem('dmos_principal_id')).toBeNull();
    expect(sessionStorage.getItem('dmos_session_id')).toBeNull();
  });

  it('should refresh session periodically', async () => {
    const TestComponent = () => {
      const { isAuthenticated } = useAuth();
      return <div>{isAuthenticated ? 'authenticated' : 'not authenticated'}</div>;
    };

    const { getByText } = render(
      <AuthProvider initialToken="test-token" initialSessionId="session-123">
        <TestComponent />
      </AuthProvider>,
    );

    expect(getByText('authenticated')).toBeInTheDocument();

    // Fast-forward time to 5 minutes (before expiry)
    vi.useFakeTimers();
    vi.advanceTimersByTime(5 * 60 * 1000); // 5 minutes

    // Session should still be authenticated (refreshed)
    await waitFor(() => {
      expect(getByText('authenticated')).toBeInTheDocument();
    });

    vi.useRealTimers();
  });

  it('should not store sensitive token in localStorage', () => {
    const TestComponent = () => {
      const { login } = useAuth();
      return <button onClick={() => login('secret-token', 'ws-1', 'tenant-1', 'user-1', 'session-1', ['admin'])}>
        Login
      </button>;
    };

    const { getByText } = render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>,
    );

    getByText('Login').click();

    // Token should NOT be in localStorage or sessionStorage
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(sessionStorage.getItem('auth_token')).toBeNull();
  });

  it('should use sessionStorage for non-sensitive session data', () => {
    const TestComponent = () => {
      const { login } = useAuth();
      return <button onClick={() => login('token', 'ws-1', 'tenant-1', 'user-1', 'session-1', ['admin'])}>
        Login
      </button>;
    };

    const { getByText } = render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>,
    );

    getByText('Login').click();

    // Session data should be in sessionStorage (cleared on tab close)
    expect(sessionStorage.getItem('dmos_workspace_id')).toBe('ws-1');
    expect(sessionStorage.getItem('dmos_tenant_id')).toBe('tenant-1');
    expect(sessionStorage.getItem('dmos_principal_id')).toBe('user-1');
    expect(sessionStorage.getItem('dmos_session_id')).toBe('session-1');
  });
});
