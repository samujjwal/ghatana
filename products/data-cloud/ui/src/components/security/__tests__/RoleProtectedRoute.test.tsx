/**
 * Tests for Data Cloud RoleProtectedRoute
 *
 * Verifies that the route-level shell-role guard:
 * - Renders children when the shell role meets the minimum
 * - Redirects to "/" with accessDenied state when the shell role is insufficient
 * - Falls back to a custom element when the shell role is insufficient and a fallback is provided
 * - Permits access to routes not in the registry (graceful pass-through)
 *
 * @doc.type test
 * @doc.purpose Verify route-level shell-role access control for Data Cloud
 * @doc.layer frontend
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { RoleProtectedRoute } from '../RoleProtectedRoute';
import type { ShellRole } from '../../lib/auth/session';

// ---------------------------------------------------------------------------
// Mock session bootstrap
// ---------------------------------------------------------------------------

const mockShellRole = vi.fn<[], ShellRole>(() => 'primary-user');

vi.mock('../../lib/auth/session', () => ({
  default: {
    bootstrap: () => ({ shellRole: mockShellRole() }),
  },
  SHELL_ROLES: ['primary-user', 'operator', 'admin'] as const,
}));

// ---------------------------------------------------------------------------
// Mock route registry to return deterministic data without reading real files
// ---------------------------------------------------------------------------

vi.mock('../../lib/routing/RouteCapabilityRegistry', () => ({
  getRouteByPath: (path: string) => {
    const routes: Record<string, { path: string; minimumShellRole: 'primary-user' | 'operator' | 'admin' }> = {
      '/insights': { path: '/insights', minimumShellRole: 'operator' },
      '/operations': { path: '/operations', minimumShellRole: 'admin' },
      '/data': { path: '/data', minimumShellRole: 'primary-user' },
    };
    return routes[path];
  },
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderRoute(
  path: string,
  initialPath: string,
  props: { fallback?: React.ReactNode } = {}
) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path={path}
          element={
            <RoleProtectedRoute routePath={path} fallback={props.fallback}>
              <span>protected-content</span>
            </RoleProtectedRoute>
          }
        />
        <Route path="/" element={<span>home-redirected</span>} />
      </Routes>
    </MemoryRouter>
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('RoleProtectedRoute', () => {
  beforeEach(() => {
    mockShellRole.mockReturnValue('primary-user');
  });

  it('renders children when primary-user accesses a primary-user-minimum route', () => {
    mockShellRole.mockReturnValue('primary-user');
    renderRoute('/data', '/data');
    expect(screen.getByText('protected-content')).toBeInTheDocument();
  });

  it('redirects to "/" when primary-user accesses an operator-minimum route', () => {
    mockShellRole.mockReturnValue('primary-user');
    renderRoute('/insights', '/insights');
    expect(screen.getByText('home-redirected')).toBeInTheDocument();
    expect(screen.queryByText('protected-content')).not.toBeInTheDocument();
  });

  it('renders children when operator accesses an operator-minimum route', () => {
    mockShellRole.mockReturnValue('operator');
    renderRoute('/insights', '/insights');
    expect(screen.getByText('protected-content')).toBeInTheDocument();
  });

  it('redirects to "/" when operator accesses an admin-minimum route', () => {
    mockShellRole.mockReturnValue('operator');
    renderRoute('/operations', '/operations');
    expect(screen.getByText('home-redirected')).toBeInTheDocument();
  });

  it('renders children when admin accesses an admin-minimum route', () => {
    mockShellRole.mockReturnValue('admin');
    renderRoute('/operations', '/operations');
    expect(screen.getByText('protected-content')).toBeInTheDocument();
  });

  it('renders fallback instead of redirecting when provided and access is denied', () => {
    mockShellRole.mockReturnValue('primary-user');
    renderRoute('/insights', '/insights', {
      fallback: <span>access-denied-fallback</span>,
    });
    expect(screen.getByText('access-denied-fallback')).toBeInTheDocument();
    expect(screen.queryByText('protected-content')).not.toBeInTheDocument();
  });

  it('renders children for unknown routes (graceful pass-through)', () => {
    mockShellRole.mockReturnValue('primary-user');
    render(
      <MemoryRouter initialEntries={['/unknown-route']}>
        <Routes>
          <Route
            path="/unknown-route"
            element={
              <RoleProtectedRoute routePath="/unknown-route">
                <span>protected-content</span>
              </RoleProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );
    // Unknown routes have no registry entry → pass-through
    expect(screen.getByText('protected-content')).toBeInTheDocument();
  });
});
