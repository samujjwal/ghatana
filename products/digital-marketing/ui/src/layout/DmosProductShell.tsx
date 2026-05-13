import React, { useMemo } from 'react';
import {
  ProductHeaderUserMenu,
  ProductShell,
  ProductShellFooter,
  createProductRoleSelectorConfig,
  resolveHighestRole,
  useProductEntitlements,
  useProductShellConfig,
  type ProductShellConfig,
} from '@ghatana/product-shell';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { API_BASE_URL } from '@/lib/http-client';
import {
  DMOS_ROLE_ORDER,
  dmosRouteManifest,
  resolveDmosRoutePath,
} from '@/routeManifest';

interface DmosProductShellProps {
  children: React.ReactNode;
}

const roleLabels: ProductShellConfig['roleLabels'] = {
  viewer: 'Viewer',
  'brand-manager': 'Brand Manager',
  'marketing-director': 'Marketing Director',
  'exec-sponsor': 'Executive Sponsor',
  admin: 'Admin',
};

const roleDescriptions: ProductShellConfig['roleDescriptions'] = {
  viewer: 'Read-only workspace oversight.',
  'brand-manager': 'Campaign planning and execution workflows.',
  'marketing-director': 'Budget and approval orchestration.',
  'exec-sponsor': 'Executive review and strategic governance.',
  admin: 'Operational administration and platform support.',
};

const sidebarFooter = (
  <ProductShellFooter description="Workspace navigation, role visibility, and route disclosure come from the shared product-shell contract." />
);

const roleSelectorConfig = createProductRoleSelectorConfig({
  roleOrder: DMOS_ROLE_ORDER,
  roleLabels,
  roleDescriptions,
});

export function DmosProductShell({ children }: DmosProductShellProps): React.ReactElement {
  const navigate = useNavigate();
  const { workspaceId: routeWorkspaceId } = useParams<{ workspaceId: string }>();
  const { roles, token, workspaceId: authWorkspaceId, tenantId, principalId, sessionId, logout } = useAuth();

  const workspaceId = routeWorkspaceId ?? authWorkspaceId ?? 'workspace';
  const currentRole = resolveHighestRole(roles, DMOS_ROLE_ORDER, 'viewer');
  const entitlementEndpoint = useMemo(() => {
    const params = new URLSearchParams({
      role: currentRole,
      workspaceId,
      tenantId: tenantId ?? 'anonymous',
      principalId: principalId ?? 'anonymous',
    });
    return `${API_BASE_URL}/v1/route-entitlements?${params.toString()}`;
  }, [currentRole, principalId, tenantId, workspaceId]);
  const entitlementRequestInit = useMemo<RequestInit>(
    () => ({
      headers: {
        Accept: 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        'X-Role': currentRole,
        'X-Roles': roles.join(','),
        'X-Tenant-ID': tenantId ?? '',
        'X-Principal-ID': principalId ?? '',
        'X-Session-ID': sessionId ?? '',
      },
      credentials: 'include',
    }),
    [currentRole, principalId, roles, sessionId, tenantId, token],
  );
  const entitlements = useProductEntitlements({
    endpoint: entitlementEndpoint,
    fallbackRoutes: dmosRouteManifest,
    requestInit: entitlementRequestInit,
  });
  const shellRoutes = useMemo(
    () =>
      entitlements.routes.map((route) => ({
        ...route,
        path: resolveDmosRoutePath(route.path, workspaceId),
      })),
    [entitlements.routes, workspaceId],
  );

  const config = useProductShellConfig({
    productName: 'DMOS',
    logo: <span className="text-lg font-semibold tracking-tight text-sky-700">DM</span>,
    currentRole,
    ...roleSelectorConfig,
    routes: shellRoutes,
    headerActions: (
      <div className="flex items-center gap-3">
        <span className="hidden text-sm text-slate-600 sm:inline">
          Workspace {workspaceId}
        </span>
        <ProductHeaderUserMenu fallbackLabel="Signed in" onLogout={logout} />
      </div>
    ),
    sidebarFooter,
    onSearch: () => navigate(resolveDmosRoutePath('/workspaces/:workspaceId/dashboard', workspaceId)),
  });

  return (
    <ProductShell
      config={config}
      contentClassName="pt-20 p-6"
      mainContentId="main-content"
      mainContentTabIndex={-1}
      mainContentRole="main"
    >
      {children}
    </ProductShell>
  );
}
