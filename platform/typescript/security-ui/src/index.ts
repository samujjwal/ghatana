/**
 * @ghatana/security-ui — Security UI platform stub.
 *
 * Stub implementation until the full platform package is built.
 *
 * @doc.type module
 * @doc.purpose Role-based access control UI components and hooks
 * @doc.layer platform
 * @doc.pattern Library
 */

import React from 'react';

// ── Types ──────────────────────────────────────────────────────────────────────

export interface RBACGuardProps {
  requiredRole?: string;
  permission?: string;
  resource?: string;
  action?: string;
  fallback?: React.ReactNode;
  loadingFallback?: React.ReactNode;
  endpoint?: string;
  children: React.ReactNode;
}

export interface UsePermissionReturn {
  hasPermission: boolean;
  isLoading: boolean;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

async function checkPermission(
  permission: string | undefined,
  resource: string | undefined,
  action: string | undefined,
  endpoint: string,
): Promise<boolean> {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ permission, resource, action }),
  });
  if (!response.ok) return false;
  const data = (await response.json()) as { granted?: boolean };
  return data.granted === true;
}

// ── Components ─────────────────────────────────────────────────────────────────

export const RBACGuard: React.FC<RBACGuardProps> = ({
  permission,
  resource,
  action,
  fallback = null,
  loadingFallback = null,
  endpoint = '/api/v1/permissions/check',
  children,
}) => {
  const [granted, setGranted] = React.useState<boolean | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    checkPermission(permission, resource, action, endpoint)
      .then((result) => { if (!cancelled) setGranted(result); })
      .catch(() => { if (!cancelled) setGranted(false); });
    return () => { cancelled = true; };
  }, [permission, resource, action, endpoint]);

  if (granted === null) return React.createElement(React.Fragment, null, loadingFallback);
  if (!granted) return React.createElement(React.Fragment, null, fallback);
  return React.createElement(React.Fragment, null, children);
};

// ── Hooks ──────────────────────────────────────────────────────────────────────

export function usePermission(
  permission: string,
  resource?: string,
  action?: string,
  endpoint = '/api/v1/permissions/check',
): UsePermissionReturn {
  const [hasPermission, setHasPermission] = React.useState(false);
  const [isLoading, setIsLoading] = React.useState(true);

  React.useEffect(() => {
    let cancelled = false;
    checkPermission(permission, resource, action, endpoint)
      .then((result) => {
        if (!cancelled) { setHasPermission(result); setIsLoading(false); }
      })
      .catch(() => {
        if (!cancelled) { setHasPermission(false); setIsLoading(false); }
      });
    return () => { cancelled = true; };
  }, [permission, resource, action, endpoint]);

  return { hasPermission, isLoading };
}
