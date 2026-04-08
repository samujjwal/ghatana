/**
 * State Boundary Component
 *
 * React component that renders different UI based on the current UI state.
 * Provides a declarative way to handle loading, error, success, and empty states.
 *
 * @doc.type component
 * @doc.purpose Declarative UI state rendering
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { ReactNode } from 'react';
import type { UiState } from '../../lib/stateMachine';

interface StateBoundaryProps {
  state: UiState;
  children: ReactNode;
  fallback?: ReactNode;
  loading?: ReactNode;
  error?: ReactNode;
  empty?: ReactNode;
  stale?: ReactNode;
  offline?: ReactNode;
  permissionDenied?: ReactNode;
  authFailure?: ReactNode;
  timeout?: ReactNode;
  rateLimit?: ReactNode;
  retry?: ReactNode;
}

/**
 * State Boundary component
 *
 * Renders children based on the current UI state, with optional fallbacks
 * for specific states.
 */
export function StateBoundary({
  state,
  children,
  fallback,
  loading,
  error,
  empty,
  stale,
  offline,
  permissionDenied,
  authFailure,
  timeout,
  rateLimit,
  retry,
}: StateBoundaryProps) {
  // Check for specific state fallbacks
  if (state === 'loading' && loading) return loading;
  if (state === 'empty' && empty) return empty;
  if (state === 'stale' && stale) return stale;
  if (state === 'offline' && offline) return offline;
  if (state === 'permission_denied' && permissionDenied) return permissionDenied;
  if (state === 'auth_failure' && authFailure) return authFailure;
  if (state === 'timeout' && timeout) return timeout;
  if (state === 'rate_limit' && rateLimit) return rateLimit;
  if (state === 'retry' && retry) return retry;

  // Check for error states
  if (
    ['validation_error', 'permission_denied', 'auth_failure', 'conflict', 'timeout', 'rate_limit', 'server_error', 'offline'].includes(state)
  ) {
    if (error) return error;
    if (fallback) return fallback;
  }

  // Check for loading states
  if (['loading', 'background_refresh', 'retry'].includes(state)) {
    if (fallback) return fallback;
  }

  // Render children for success/idle states
  return children;
}

interface StateMatchProps {
  state: UiState;
  children: ReactNode;
}

/**
 * State Match component
 *
 * Renders children only if the current state matches the expected state.
 */
export function StateMatch({ state, children }: StateMatchProps) {
  return <>{children}</>;
}

interface StateSwitchProps {
  state: UiState;
  children: ReactNode;
}

/**
 * State Switch component
 *
 * Renders the appropriate child based on the current state.
 */
export function StateSwitch({ state, children }: StateSwitchProps) {
  const childrenArray = Array.isArray(children) ? children : [children];
  
  for (const child of childrenArray) {
    if (React.isValidElement(child) && child.type === StateMatch) {
      const props = child.props as { state: UiState; children: ReactNode };
      const matchState = props.state;
      if (matchState === state) {
        return child;
      }
    }
  }

  return null;
}
